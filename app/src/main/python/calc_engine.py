"""Offline math adapter. Only a validated AST is accepted, never executable source.

SymPy owns exact arithmetic and symbolic transformations. This module is independent
of Android and is exercised by the desktop test suite as well as on-device tests.
"""
import json
import math
import time
import sys
import statistics
import random
import sympy as s
import mpmath as mp
from sympy.core.relational import Relational
from quantities import Quantity, quantity, convert_quantity

class MathError(ValueError):
    pass

class Budget:
    def __init__(self, seconds=8, steps=1500000):
        self.deadline = time.monotonic() + seconds
        self.steps = steps
    def trace(self, frame, event, arg):
        self.steps -= 1
        if self.steps % 1024 == 0 and (self.steps <= 0 or time.monotonic() > self.deadline):
            raise MathError("Computation limit reached. Reduce expression complexity.")
        return self.trace

# Dimension order: length, mass, time, temperature, current, amount, data, angle.
UNITS = {}
def unit(names, dim, scale, offset=0):
    for name in names.split():
        UNITS[name] = (dim, s.Rational(str(scale)), s.Rational(str(offset)))
unit("m", "length", 1); unit("km", "length", 1000); unit("cm", "length", '.01'); unit("mm", "length", '.001')
unit("in inch", "length", '.0254'); unit("ft", "length", '.3048'); unit("yd", "length", '.9144'); unit("mi", "length", '1609.344')
unit("m2", "area", 1); unit("cm2", "area", '.0001'); unit("km2", "area", 1000000); unit("ha", "area", 10000); unit("acre", "area", '4046.8564224')
unit("m3", "volume", 1); unit("L", "volume", '.001'); unit("mL", "volume", '.000001'); unit("galUS", "volume", '.003785411784')
unit("kg", "mass", 1); unit("g", "mass", '.001'); unit("mg", "mass", '.000001'); unit("lb", "mass", '.45359237'); unit("oz", "mass", '.028349523125')
unit("K", "temperature", 1); unit("degC", "temperature", 1, '273.15'); unit("degF", "temperature", s.Rational(5,9), s.Rational(45967,180))
unit("s sec", "time", 1); unit("min", "time", 60); unit("h hr", "time", 3600); unit("day", "time", 86400); unit("ms", "time", '.001')
unit("mps", "speed", 1); unit("kph", "speed", s.Rational(5,18)); unit("mph", "speed", '.44704'); unit("knot", "speed", s.Rational(463,900))
unit("mps2", "acceleration", 1); unit("g0", "acceleration", '9.80665')
unit("Pa", "pressure", 1); unit("kPa", "pressure", 1000); unit("bar", "pressure", 100000); unit("atm", "pressure", 101325)
unit("N", "force", 1); unit("kN", "force", 1000); unit("lbf", "force", '4.4482216152605')
unit("J", "energy", 1); unit("kJ", "energy", 1000); unit("cal", "energy", '4.184'); unit("kWh", "energy", 3600000); unit("eV", "energy", '1.602176634e-19')
unit("W", "power", 1); unit("kW", "power", 1000)
unit("Hz", "frequency", 1); unit("kHz", "frequency", 1000); unit("MHz", "frequency", 1000000)
unit("bit", "data", 1); unit("byte", "data", 8); unit("kB", "data", 8000); unit("KiB", "data", 8192); unit("MB", "data", 8000000); unit("MiB", "data", 8388608); unit("GB", "data", 8000000000)
unit("rad", "angle", 1); UNITS["deg"] = ("angle", s.pi/180, 0); UNITS["grad"] = ("angle", s.pi/200, 0)

CONSTANTS = {
    "c0": ("Speed of light", "299792458", "m/s", True),
    "hP": ("Planck constant", "6.62607015e-34", "J s", True),
    "hbar": ("Reduced Planck constant", None, "J s", True),
    "G": ("Newtonian gravitational constant", "6.67430e-11", "m³ kg⁻¹ s⁻²", False),
    "qe": ("Elementary charge", "1.602176634e-19", "C", True),
    "NA": ("Avogadro constant", "6.02214076e23", "mol⁻¹", True),
    "kB0": ("Boltzmann constant", "1.380649e-23", "J/K", True),
    "me": ("Electron mass", "9.1093837139e-31", "kg", False),
    "mp0": ("Proton mass", "1.67262192595e-27", "kg", False),
}

def require(condition, message):
    if not condition:
        raise MathError(message)

def matrix(a):
    if isinstance(a, s.MatrixBase): return a
    require(isinstance(a, (list, tuple)), "Expected a vector or matrix")
    require(len(a) <= 32 and all(not isinstance(row,(list,tuple)) or len(row)<=32 for row in a), "Matrix size limit: 32 × 32")
    return s.Matrix(a)

def flatten(a):
    return list(a) if isinstance(a, (list, tuple, s.MatrixBase, s.Tuple)) else [a]

class Engine:
    def __init__(self, request):
        self.request = request
        self.precision = max(3, min(200, int(request.get("precision", 30))))
        self.angle = request.get("angle", "RAD")
        self.variables = request.get("variables", {})
        self.functions = request.get("functions", {})
        self.resolving = set()
        self.symbols = {}
        self.visited = 0
        self.note = ""
        self.conditions = []
        self.bindings = {}
        self.assumptions = request.get("assumptions", {})
    def symbol(self, name):
        if name not in self.symbols:
            options = {k: True for k in self.assumptions.get(name, []) if k in ("real", "positive", "negative", "integer", "nonzero")}
            self.symbols[name] = s.Symbol(name, **options)
        return self.symbols[name]
    def build(self, node, depth=0):
        self.visited += 1
        require(depth < 100 and self.visited <= 12000, "Expression complexity limit")
        kind, value = node["kind"], node.get("value", "")
        args = node.get("args", [])
        build = lambda a: self.build(a, depth + 1)
        if kind == "number":
            require(len(value) <= 1000, "Number too large")
            if "e" in value.lower(): require(abs(int(value.lower().split("e")[1])) <= 10000, "Decimal exponent limit: 10000")
            number = s.Rational(value)
            require(abs(number.p).bit_length() < 100000 and number.q.bit_length() < 100000, "Number size limit")
            return number
        if kind == "symbol":
            if value in self.bindings: return self.bindings[value]
            constants = {"pi": s.pi, "e": s.E, "i": s.I, "I": s.I, "oo": s.oo, "true": s.true, "false": s.false}
            if value in constants: return constants[value]
            if value in CONSTANTS:
                entry=CONSTANTS[value]
                if not entry[3]:
                    self.note="Uses a measured CODATA 2022 value with published precision."
                    return s.Float(entry[1],12)
                return s.Rational(entry[1]) if value != "hbar" else s.Rational(CONSTANTS["hP"][1])/(2*s.pi)
            if value in self.variables:
                require(value not in self.resolving, "Cyclic variable definition")
                self.resolving.add(value)
                result = build(self.variables[value])
                self.resolving.remove(value)
                return result
            if value=="Ans": raise MathError("Ans has no reusable result yet")
            return self.symbol(value)
        if kind == "constant":
            return {"pi":s.pi,"E":s.E,"I":s.I,"oo":s.oo,"-oo":-s.oo,"EmptySet":s.S.EmptySet,"True":s.true,"False":s.false}[value]
        if kind == "snapshot_symbol": return self.symbol(value)
        if kind == "float": return s.Float(value,self.precision)
        if kind == "restricted":
            result=build(args[0])
            for guard in args[1:]:
                condition=build(guard)
                require(condition!=s.false,"Domain ERROR: excluded value")
                if condition!=s.true: self.conditions.append(condition)
            return result
        if kind == "quantity": return Quantity(build(args[0]),tuple(node["dimensions"]),node.get("absolute",False))
        if kind == "frozen_call":
            old_angle=self.angle
            self.angle="RAD"
            try: return self.call(value,[build(a) for a in args],args)
            finally: self.angle=old_angle
        if kind == "set": return s.FiniteSet(*(build(a) for a in args))
        if kind == "mapping": return {build(pair["args"][0]):build(pair["args"][1]) for pair in args}
        if kind == "group": return build(args[0])
        if kind == "list": return [build(a) for a in args]
        if kind == "tuple": return tuple(build(a) for a in args)
        if kind == "unary": return (-1 if value == "-" else 1)*build(args[0])
        if kind in ("binary", "relation"):
            require(value != ":=", "Use STO for variables or the Variables editor for functions")
            a, b = map(build, args)
            if isinstance(a, list): a = matrix(a)
            if isinstance(b, list): b = matrix(b)
            if value == "+": return a+b
            if value == "-": return a-b
            if value == "*": return a*b
            if value == "∠": return self.call("polar",[a,b],args)
            if value == "/":
                require(b != 0, "Division by zero")
                if getattr(b, "free_symbols", None): self.conditions.append(s.Ne(b, 0, evaluate=False))
                return a/b
            if value == "^":
                if getattr(b, "is_number", False): require(abs(b) <= 10000, "Exponent limit: 10000")
                require(not (a == 0 and b == 0), "Undefined: 0^0")
                return a**b
            if value == "mod": require(b != 0, "Division by zero"); return s.Mod(a,b)
            relations = {"=": s.Eq, "==": s.Eq, "!=": s.Ne, "<": s.Lt, ">": s.Gt, "<=": s.Le, ">=": s.Ge, "->": s.Eq}
            if value in relations: return relations[value](a,b,evaluate=False)
            raise MathError("Unknown operator")
        require(kind == "call", "Unknown AST node")
        # Preserve bound variable identity even if the user stored x previously.
        scoped = value in ("diff", "integrate", "limit", "series", "sum", "product", "solve", "nsolve", "nintegrate", "nderivative", "minimum", "maximum", "collect", "subs") and len(args) > 1
        old = self.bindings.copy()
        if value=="solve" and len(args)==1: self.bindings["x"]=self.symbol("x")
        if scoped:
            varnode = args[1]
            candidates = varnode.get("args", []) if varnode["kind"] == "list" else [varnode]
            if varnode["kind"] == "tuple": candidates = varnode["args"][:1]
            if varnode["kind"] == "relation": candidates = [varnode["args"][0]]
            for n in candidates:
                if n["kind"] == "symbol": self.bindings[n["value"]] = self.symbol(n["value"])
        try:
            condition_start=len(self.conditions)
            values = [build(a) for a in args]
            if value=="subs" and len(values)==3:
                updated=[condition.subs(values[1],values[2]) for condition in self.conditions[condition_start:]]
                require(all(c!=s.false for c in updated),"Domain ERROR: substitution at an excluded value")
                self.conditions[condition_start:]=[c for c in updated if c!=s.true]
                return values[0].subs(values[1],values[2])
            return self.call(value, values, args)
        finally:
            self.bindings = old
    def call(self, name, a, nodes):
        if name=="rnd":return s.N(a[0],self.precision)
        if name=="eng":return a[0]
        if name=="pol":
            z=a[0]+s.I*a[1];angle=s.arg(z)*{"DEG":180/s.pi,"GRAD":200/s.pi}.get(self.angle,1)
            return [s.Abs(z),angle]
        if name=="rec":
            z=self.call("polar",a,nodes)
            return [s.re(z),s.im(z)]
        if name=="randInt":
            require(len(a)==2 and all(x.is_Integer for x in a) and a[0]<=a[1],"Enter integer lower and upper bounds")
            return s.Integer(random.randint(int(a[0]),int(a[1])))
        if name=="dms":
            if len(a)==3:return a[0]+a[1]/60+a[2]/3600
            whole=s.floor(s.Abs(a[0]));minutes=s.floor((s.Abs(a[0])-whole)*60);seconds=s.simplify((s.Abs(a[0])-whole-minutes/60)*3600)
            return [s.sign(a[0])*whole,minutes,seconds]
        if name=="qty": return quantity(a[0],nodes[1]["value"],UNITS)
        if name=="mixed":
            require(all(v.is_Integer for v in a) and a[2]>0 and 0<=a[1],"Mixed fractions require integer parts and a positive denominator")
            return a[0]+(-1 if a[0]<0 else 1)*a[1]/a[2]
        if name in ("arg","rectpolar"):
            angle=s.arg(a[0])
            if not angle.free_symbols: angle *= {"DEG":180/s.pi,"GRAD":200/s.pi}.get(self.angle,1)
            return angle if name=="arg" else [s.Abs(a[0]),angle]
        if name=="polar":
            theta=a[1]
            explicit=theta.has(s.pi) or any(n.get("value")=="degree" for n in walk(nodes[1]))
            if not explicit and not theta.free_symbols: theta *= {"DEG":s.pi/180,"GRAD":s.pi/200}.get(self.angle,1)
            return a[0]*(s.cos(theta)+s.I*s.sin(theta))
        if name in ("sin", "cos", "tan"):
            arg = a[0]
            explicit = bool(arg.has(s.pi)) or any(n.get("value") in ("degree", "pi", "rad", "gradian") for n in walk(nodes[0]))
            if not explicit and not getattr(arg, "free_symbols", set()):
                arg *= {"DEG": s.pi/180, "GRAD": s.pi/200}.get(self.angle, 1)
            return getattr(s,name)(arg)
        if name in ("asin", "acos", "atan"):
            result = getattr(s,name)(*a)
            return result * ({"DEG": 180/s.pi, "GRAD": 200/s.pi}.get(self.angle, 1) if not result.free_symbols else 1)
        basic = {"sqrt": s.sqrt, "cbrt": lambda x: s.real_root(x,3), "nthroot": s.root, "abs": s.Abs,
                 "floor": s.floor, "ceil": s.ceiling, "sign": s.sign, "gamma": s.gamma,
                 "erf":s.erf,"erfc":s.erfc,"Ei":s.Ei,"Si":s.Si,"Ci":s.Ci,"zeta":s.zeta,
                 "ln": s.log, "log": lambda x, b=10: s.log(x,b), "exp": s.exp,
                 "sinc": s.sinc, "sinh": s.sinh, "cosh": s.cosh, "tanh": s.tanh, "asinh": s.asinh, "acosh": s.acosh, "atanh": s.atanh,
                 "conj": s.conjugate, "re": s.re, "im": s.im, "arg": s.arg,
                 "simplify": s.simplify, "expand": s.expand, "factor": s.factor, "collect": s.collect,
                 "diff": s.diff, "gcd": s.gcd, "lcm": s.lcm, "nCr": s.binomial,
                 "percent": lambda x: x/100, "degree": lambda x: x*s.pi/180,
                 "rad":lambda x:x,"gradian":lambda x:x*s.pi/200,
                 "round": lambda x, n=0: x.round(int(n)), "quotient": lambda x,y: s.floor(x/y), "remainder": s.Mod,
                 "polar": lambda r,t: r*(s.cos(t)+s.I*s.sin(t)), "rectpolar": lambda z: [s.Abs(z),s.arg(z)]}
        if name in ("log","ln"): require(a[0]!=0,"Domain ERROR: logarithm of zero")
        if name=="log" and len(a)>1: require(a[1] not in (0,1),"Domain ERROR: invalid logarithm base")
        if name in basic: return basic[name](*a)
        if name in ("factorial", "nPr", "prime", "factorization", "divisors"):
            require(a[0].is_Integer and 0 <= a[0] <= (10000 if name in ("factorial", "nPr") else 10**15), "Number theory input outside supported range")
            if name == "factorial": return s.factorial(a[0])
            if name == "nPr":
                require(a[1].is_Integer and 0 <= a[1] <= a[0], "nPr requires 0 ≤ r ≤ n")
                return s.factorial(a[0])/s.factorial(a[0]-a[1])
            if name == "prime": return s.true if s.isprime(a[0]) else s.false
            require(a[0]>0,"Factorization and divisors require a positive integer")
            if name == "factorization": return [[s.Integer(p), s.Integer(k)] for p,k in s.factorint(a[0]).items()]
            return s.divisors(a[0])
        if name == "subs": return a[0].subs(a[1],a[2])
        if name == "integrate":
            require(len(a) in (2,4), "integrate expects a variable or integration bounds")
            spec = a[1] if len(a)==2 else (a[1],a[2],a[3])
            result = s.integrate(a[0],spec)
            if result.has(s.Integral): self.note = "Symbolic solution not found for the remaining integral."
            elif len(a)==2 and not isinstance(a[1], (list,tuple)): result += self.symbol("C")
            return result
        if name == "limit":
            if isinstance(a[1],Relational): var,point = a[1].lhs,a[1].rhs; direction = str(a[2]) if len(a)>2 else "+-"
            else: var,point = a[1],a[2]; direction = str(a[3]) if len(a)>3 else "+-"
            direction = {"left":"-","right":"+","both":"+-"}.get(direction,direction)
            return s.limit(a[0],var,point,dir=direction)
        if name == "series": return s.series(a[0],a[1],a[2] if len(a)>2 else 0,int(a[3]) if len(a)>3 else 6)
        if name in ("sum", "product"):
            spec = tuple(a[1]) if len(a)==2 else tuple(a[1:])
            return (s.summation if name=="sum" else s.product)(a[0],spec)
        if name == "piecewise": return s.Piecewise(*(tuple(x) for x in a))
        if name == "solve":
            symbols=set().union(*(e.free_symbols for e in a[0])) if isinstance(a[0],list) else a[0].free_symbols
            var = a[1] if len(a)>1 else sorted(symbols,key=str)
            if isinstance(var,list) and len(var)==1: var = var[0]
            if isinstance(a[0],list): return s.solve(a[0],var,dict=True)
            if isinstance(a[0],Relational) and not isinstance(a[0],s.Equality): return s.reduce_inequalities(a[0],var)
            expr = a[0].lhs-a[0].rhs if isinstance(a[0],s.Equality) else a[0]
            if isinstance(var,list): return s.solve(expr,var,dict=True)
            domain=s.S.Integers if var.is_integer else s.S.Reals if var.is_real else s.S.Complexes
            if var.is_positive: domain=domain.intersect(s.Interval.open(0,s.oo))
            elif var.is_negative: domain=domain.intersect(s.Interval.open(-s.oo,0))
            if var.is_nonzero: domain=domain-s.FiniteSet(0)
            result = s.solveset(expr,var,domain=domain)
            if isinstance(result,s.FiniteSet):
                result=s.FiniteSet(*(root for root in result if all(condition.subs(var,root)!=s.false for condition in self.conditions)))
            if isinstance(result,s.ConditionSet): self.note = "Symbolic solution not found. Try nsolve with a bracket."
            return result
        if name == "nsolve":
            expr = a[0].lhs-a[0].rhs if isinstance(a[0],s.Equality) else a[0]
            if len(a)==4:
                # Bracketing does not lose convergence merely due to a zero derivative.
                result=s.nsolve(expr,a[1],(a[2],a[3]),solver="bisect",prec=max(20,self.precision+10))
            else: result=s.nsolve(expr,a[1],a[2],prec=max(20,self.precision+10))
            require(all(c.subs(a[1],result)!=s.false for c in self.conditions),"No solution found in the expression domain")
            return s.N(result,self.precision)
        if name in ("nintegrate", "nderivative", "minimum", "maximum"):
            if name == "nderivative": return s.N(s.diff(a[0],a[1]).subs(a[1],a[2]),self.precision)
            if name in ("minimum", "maximum"):
                return (s.minimum if name=="minimum" else s.maximum)(a[0],a[1],s.Interval(a[2],a[3]))
            # SymPy evalf uses adaptive quadrature and arbitrary precision.
            result = s.Integral(a[0],(a[1],a[2],a[3])).evalf(self.precision, strict=True)
            require(not result.has(s.Integral), "Numerical convergence failed")
            return result
        if name=="normalize":
            vector=matrix(a[0]);length=vector.norm()
            require(length!=0,"Domain ERROR: zero vector cannot be normalized")
            return vector/length
        matrix_ops = {"det": lambda m: m.det(), "inverse": lambda m: m.inv(), "transpose": lambda m: m.T,
                      "rank": lambda m: s.Integer(m.rank()), "trace": lambda m: m.trace(), "rref": lambda m: m.rref()[0],
                      "ref": lambda m: m.echelon_form(), "lu": lambda m: list(m.LUdecomposition()),
                      "eigenvalues": lambda m: [[k,s.Integer(v)] for k,v in m.eigenvals().items()],
                      "eigenvectors": lambda m: [[v,s.Integer(k),vec] for v,k,vec in m.eigenvects()],
                      "norm": lambda m: m.norm()}
        if name in matrix_ops: return matrix_ops[name](matrix(a[0]))
        if name in ("dot", "cross", "angle", "projection", "linsolve"):
            u,v = matrix(a[0]),matrix(a[1])
            if name == "dot": return u.dot(v)
            if name == "cross": return u.cross(v)
            if name == "angle":
                require(u.norm()!=0 and v.norm()!=0,"Domain ERROR: zero vector has no direction")
                result=s.acos(u.dot(v)/(u.norm()*v.norm()))
                return result if result.free_symbols else result*{"DEG":180/s.pi,"GRAD":200/s.pi}.get(self.angle,1)
            if name == "projection": return v*(u.dot(v)/v.dot(v))
            return u.inv()*v
        if name in ("mean", "median", "variance", "stdev", "sumdata", "quartiles", "stats"):
            data = flatten(a[0]); n = len(data)
            require(n>0,"Enter at least one data value")
            avg = sum(data)/n; var = sum((x-avg)**2 for x in data)/n
            ordered = sorted(data)
            med = s.Rational(1,2)*(ordered[(n-1)//2]+ordered[n//2])
            if name == "mean": return avg
            if name == "median": return med
            if name == "variance": return var
            if name == "stdev": return s.sqrt(var)
            if name == "sumdata": return sum(data)
            quartiles = [s.Rational(str(x)) for x in statistics.quantiles(ordered,method="inclusive")] if n>1 else [data[0]]*3
            if name == "quartiles": return quartiles
            return {"n": s.Integer(n), "sum": sum(data), "mean": avg, "median": med, "population variance": var,
                    "population SD": s.sqrt(var), "sample variance": var*n/(n-1) if n>1 else s.nan,
                    "sample SD": s.sqrt(var*n/(n-1)) if n>1 else s.nan, "quartiles (inclusive)": quartiles}
        if name == "regression":
            rows = a[0]; mode = str(a[1]) if len(a)>1 else "linear"
            require(len(rows)>=2 and all(len(row)==2 for row in rows),"Regression requires x,y pairs")
            xs,ys = zip(*rows)
            require(mode in ("linear","quadratic","logarithmic","exponential","power"),"Unknown regression type")
            if mode in ("logarithmic","power"): require(all(x>0 for x in xs),"Logarithmic x values must be positive"); xs = tuple(s.log(x) for x in xs)
            if mode in ("exponential","power"): require(all(y>0 for y in ys),"Logarithmic y values must be positive"); ys = tuple(s.log(y) for y in ys)
            degree = 2 if mode == "quadratic" else 1
            design = s.Matrix([[x**i for i in range(degree+1)] for x in xs]); target = s.Matrix(ys)
            coef = (design.T*design).inv()*design.T*target
            x = self.symbol("x")
            result = sum(c*x**i for i,c in enumerate(coef))
            if mode == "logarithmic": result = result.subs(x,s.log(x))
            if mode == "exponential": result = s.exp(result)
            if mode == "power": result = s.exp(coef[0])*x**coef[1]
            return result
        if name == "convert":
            if len(a)==2 and isinstance(a[0],Quantity):
                self.note="Result in "+nodes[1]["value"]
                return convert_quantity(a[0],nodes[1]["value"],UNITS)
            src,dst = nodes[1]["value"],nodes[2]["value"]
            require(src in UNITS and dst in UNITS,"Unknown unit")
            d1,f1,o1 = UNITS[src]; d2,f2,o2 = UNITS[dst]
            require(d1==d2,"Unit dimension mismatch")
            base = a[0]*f1+o1
            if d1=="temperature": require(base>=0,"Temperature below absolute zero")
            self.note = "Result in " + dst
            return s.simplify((base-o2)/f2)
        if name in self.functions:
            function = self.functions[name]
            require(len(function["parameters"])==len(a),"Function argument count mismatch")
            require(name not in self.resolving,"Recursive function definition")
            old = self.bindings.copy(); self.bindings.update(zip(function["parameters"],a)); self.resolving.add(name)
            try: return self.build(function["body"])
            finally: self.bindings = old; self.resolving.remove(name)
        raise MathError("Unknown function: " + name)

def walk(node):
    yield node
    for child in node.get("args",[]): yield from walk(child)

def display_tree(x):
    def t(kind,value="",args=()): return {"kind":kind,"value":value,"args":list(args)}
    if isinstance(x,Quantity): return t("quantity",x.unit_text(),[display_tree(x.base)])
    if isinstance(x,dict): return t("rows",args=[t("row",str(k),[display_tree(v)]) for k,v in x.items()])
    if isinstance(x,(list,tuple,s.Tuple)): return t("list",args=[display_tree(v) for v in x])
    if isinstance(x,s.MatrixBase): return t("matrix",args=[t("list",args=[display_tree(x[i,j]) for j in range(x.cols)]) for i in range(x.rows)])
    if isinstance(x,s.Rational) and x.q != 1: return t("fraction",args=[t("text",str(x.p)),t("text",str(x.q))])
    if isinstance(x,s.Pow):
        if x.exp == s.Rational(1,2): return t("root",args=[display_tree(x.base)])
        if x.exp.is_negative: return t("fraction",args=[t("text","1"),display_tree(x.base**(-x.exp))])
        return t("power",args=[display_tree(x.base),display_tree(x.exp)])
    if isinstance(x,s.Add): return t("sum",args=[t("unary","-",[display_tree(-a)]) if a.could_extract_minus_sign() else display_tree(a) for a in x.as_ordered_terms()])
    if isinstance(x,s.Mul):
        if x.could_extract_minus_sign(): return t("unary","-",[display_tree(-x)])
        num,den = s.fraction(x)
        if den != 1: return t("fraction",args=[display_tree(num),display_tree(den)])
        return t("product",args=[display_tree(a) for a in x.as_ordered_factors()])
    if isinstance(x,s.FiniteSet): return t("set",args=[display_tree(a) for a in sorted(x,key=s.default_sort_key)])
    if isinstance(x,Relational): return t("relation",x.rel_op,[display_tree(x.lhs),display_tree(x.rhs)])
    if isinstance(x,s.Function): return t("function",x.func.__name__,[display_tree(a) for a in x.args])
    if isinstance(x,s.Symbol): return t("symbol",readable(x))
    if isinstance(x,s.Number): return t("number",readable(x))
    return t("text",readable(x))

def readable(x):
    if isinstance(x,Quantity): return readable(x.base)+" "+x.unit_text()
    if isinstance(x,dict): return "\n".join(str(k)+": "+readable(v) for k,v in x.items())
    if isinstance(x,(list,tuple)): return "["+", ".join(readable(v) for v in x)+"]"
    from sympy.printing.str import StrPrinter
    class CompactPrinter(StrPrinter):
        def _print_Float(self,expr):
            text=super()._print_Float(expr)
            parts=text.lower().split("e")
            mantissa=parts[0].rstrip("0").rstrip(".") if "." in parts[0] else parts[0]
            return mantissa+("e"+parts[1] if len(parts)>1 else "")
    return CompactPrinter().doprint(x)

def approximate(x, digits):
    if isinstance(x,Quantity): return Quantity(s.N(x.base,digits),x.dimensions,x.absolute_temperature)
    if isinstance(x,dict): return {k:approximate(v,digits) for k,v in x.items()}
    if isinstance(x,(list,tuple)): return [approximate(v,digits) for v in x]
    if isinstance(x,s.Set):
        return [s.N(v,digits) for v in sorted(x,key=s.default_sort_key)] if isinstance(x,s.FiniteSet) else x
    if x in (s.true,s.false): return x
    return s.N(x,digits) if hasattr(x,"evalf") else x

def result_ast(x):
    """Lossless result transfer for Ans and STO; never reparse printed mathematics."""
    def node(kind,value="",args=()): return {"kind":kind,"value":value,"args":list(args)}
    if isinstance(x,Quantity): return {**node("quantity",args=[result_ast(x.base)]),"dimensions":list(x.dimensions),"absolute":x.absolute_temperature}
    if isinstance(x,s.MatrixBase): return node("list",args=[node("list",args=[result_ast(x[i,j]) for j in range(x.cols)]) for i in range(x.rows)])
    if isinstance(x,(list,tuple,s.Tuple)): return node("list",args=[result_ast(v) for v in x])
    if isinstance(x,dict): return node("mapping",args=[node("pair",args=[result_ast(k),result_ast(v)]) for k,v in x.items()])
    if isinstance(x,s.FiniteSet): return node("set",args=[result_ast(v) for v in x])
    if str(x) in ("pi","E","I","oo","-oo","EmptySet","True","False"): return node("constant",str(x))
    if isinstance(x,s.Symbol): return node("snapshot_symbol",str(x))
    if isinstance(x,s.Float): return node("float",readable(x))
    if isinstance(x,s.Integer): return node("number",str(x))
    if isinstance(x,s.Rational): return node("binary","/",[node("number",str(x.p)),node("number",str(x.q))])
    if isinstance(x,(s.Add,s.Mul)):
        op="+" if isinstance(x,s.Add) else "*"
        result=result_ast(x.args[0])
        for v in x.args[1:]: result=node("binary",op,[result,result_ast(v)])
        return result
    if isinstance(x,s.Pow): return node("binary","^",[result_ast(x.base),result_ast(x.exp)])
    if isinstance(x,Relational): return node("relation",x.rel_op,[result_ast(x.lhs),result_ast(x.rhs)])
    if isinstance(x,s.Function): return node("frozen_call",{"log":"ln","Abs":"abs","conjugate":"conj"}.get(x.func.__name__,x.func.__name__),[result_ast(v) for v in x.args])
    raise MathError("This result cannot be stored as a reusable expression")

def graph(engine, request):
    var = engine.symbol(request.get("variable","x")); engine.bindings[str(var)] = var
    kind = request.get("graphKind","cartesian")
    trees = request.get("trees",[])
    expressions = [engine.build(t) for t in trees]
    start,end = float(request.get("min",-10)),float(request.get("max",10))
    require(math.isfinite(start) and math.isfinite(end) and end>start,"Invalid graph range")
    count = min(1600,max(100,int(request.get("samples",500))))
    curves=[]
    for expression in expressions:
        function=s.lambdify(var,expression,modules="math",cse=True,docstring_limit=0)
        # Numeric substitution evaluates the very same expression; no second parser.
        samples=[]
        for k in range(count+1):
            t = start+(end-start)*k/count
            try:
                if kind=="parametric":
                    require(isinstance(expression,list) and len(expression)==2,"Parametric graph requires [x(t),y(t)]")
                    x,y = map(float,function(t))
                else:
                    y = float(function(t)); x = t
                    if kind=="polar": x,y = y*math.cos(t),y*math.sin(t)
                samples.append([x,y] if math.isfinite(x) and math.isfinite(y) else None)
            except (TypeError,ValueError,ZeroDivisionError,OverflowError): samples.append(None)
        curves.append(samples)
    return {"curves":curves}

def programmer(request):
    width = int(request.get("width",32)); require(width in (8,16,32,64),"Invalid word size")
    base = int(request.get("base",10)); require(base in (2,8,10,16),"Invalid base")
    mask=(1<<width)-1
    def parse(text):
        require(len(str(text))<=128,"Integer too long")
        return int(str(text),base)&mask
    a=parse(request.get("a","0")); op=request.get("op","")
    b=parse(request.get("b","0")) if op else 0
    if op=="AND": a &= b
    elif op=="OR": a |= b
    elif op=="XOR": a ^= b
    elif op=="NOT": a = ~a
    elif op=="NAND": a = ~(a&b)
    elif op=="NOR": a = ~(a|b)
    elif op in ("<<",">>"):
        require(b<width,"Shift count must be less than word size")
        if op=="<<": a <<= b
        else:
            if request.get("signed",False) and a&(1<<(width-1)): a-=1<<width
            a >>= b
    elif op not in ("",): raise MathError("Unknown bit operation")
    a &= mask
    signed=a-(1<<width) if request.get("signed",False) and a&(1<<(width-1)) else a
    return {"exact":str(signed),"decimal":str(signed),"bases":{"BIN":format(a,f"0{width}b"),"OCT":format(a,"o"),"DEC":str(signed),"HEX":format(a,f"0{width//4}X")},"tree":display_tree(s.Integer(signed))}

def dispatch(payload):
    request=json.loads(payload)
    tree=request.get("tree",{})
    integration=tree.get("kind")=="call" and tree.get("value")=="integrate"
    budget=Budget(float(request.get("budget",8)),steps=6000000 if integration else 1500000)
    try:
        sys.settrace(budget.trace)
        engine=Engine(request)
        action=request.get("action","evaluate")
        if action=="constants":
            entries=[{"symbol":"pi","name":"Pi","value":"3.141592653589793…","unit":"","exact":True},{"symbol":"e","name":"Euler's number","value":"2.718281828459045…","unit":"","exact":True}]
            entries += [{"symbol":key,"name":v[0],"value":v[1] or "h / (2π)","unit":v[2],"exact":v[3]} for key,v in CONSTANTS.items()]
            result={"constants":entries,"source":"NIST CODATA 2022"}
        elif action=="graph": result=graph(engine,request)
        elif action=="programmer": result=programmer(request)
        else:
            value=engine.build(request["tree"])
            if getattr(value,"is_number",False) and value.has(s.I): value=s.expand_complex(value)
            if isinstance(value,list) and value and all(isinstance(row,list) for row in value): value=matrix(value)
            if getattr(value,"has",lambda *_:False)(s.zoo,s.nan): raise MathError("Undefined or division by zero")
            exact=readable(value)
            require(len(exact)<=40000,"Result exceeds display size limit")
            result={"exact":exact,"decimal":readable(approximate(value,engine.precision)),"tree":display_tree(value),"note":engine.note,
                    "conditions":[readable(c.lhs)+" ≠ "+readable(c.rhs) if isinstance(c,s.Unequality) else str(c) for c in dict.fromkeys(engine.conditions)],"symbolic":bool(getattr(value,"free_symbols",False))}
            result["approximate"]=bool(getattr(value,"has",lambda *_:False)(s.Float))
            result["decimalTree"]=display_tree(approximate(value,engine.precision))
            if request["tree"].get("value")=="eng" and getattr(value,"is_number",False):
                offset=engine.build(request["tree"]["args"][1]) if len(request["tree"]["args"])>1 else 0
                require(-300<=offset<=300,"Engineering exponent limit")
                exponent=(int(s.floor(s.log(s.Abs(value),10)/3))*3 if value!=0 else 0)+int(offset)
                mantissa=s.N(value/s.Integer(10)**exponent,engine.precision)
                power={"kind":"power","args":[{"kind":"text","value":"10"},{"kind":"text","value":str(exponent)}]}
                result["tree"]=result["decimalTree"]={"kind":"product","args":[display_tree(mantissa),power]}
            if request["tree"].get("value")=="dms" and isinstance(value,list):result["tree"]=result["decimalTree"]={"kind":"dms","args":[display_tree(x) for x in value]}
            try:
                ast=result_ast(value)
                symbols=getattr(value,"free_symbols",set())
                guards=[c for c in dict.fromkeys(engine.conditions) if c.free_symbols & symbols]
                result["resultAst"]={"kind":"restricted","args":[ast]+[result_ast(c) for c in guards]} if guards else ast
            except (MathError,TypeError,AttributeError): result["reusable"]=False
        return json.dumps({"ok":True,**result},ensure_ascii=False,allow_nan=False)
    except Exception as exc:
        message=str(exc) or type(exc).__name__
        if "NonInvertible" in type(exc).__name__: message="Singular matrix"
        elif "Shape" in type(exc).__name__: message="Matrix dimension mismatch"
        elif "Could not find root" in message: message="Numerical convergence failed. Try a different bracket or initial guess."
        return json.dumps({"ok":False,"error":message[:600]},ensure_ascii=False)
    finally:
        sys.settrace(None)
