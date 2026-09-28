"""Validated expression AST evaluation and calculator functions."""
import random
import statistics
import math
import sympy as s
from sympy.core.relational import Relational
from sympy.core.function import AppliedUndef
from sympy.calculus.util import continuous_domain, function_range
from quantities import Quantity, quantity, convert_quantity
from calc_shared import (CONSTANTS, UNITS, MathError, canonical_function_name,
                         coordinates, discrete_fourier, dms_parts, flatten,
                         initial_conditions, inverse_mellin_transform,
                         inverse_z_transform, matrix, mellin_transform,
                         numeric_derivative, ode_equation, require, z_transform)
from calc_statistics import distribution_value, fit_regression, statistical_test
from calc_finance import finance_value

MAX_EXACT_DIGITS = 100000
MAX_NUMERIC_EXPONENT = 100000

def oversized_rational_power(base, exponent):
    """Estimate the larger exact numerator/denominator before SymPy expands it."""
    if not (base.is_Rational and exponent.is_Integer): return False
    if base in (0, 1, -1): return False
    magnitude = max(abs(int(base.p)), int(base.q))
    log_magnitude=math.log10(magnitude)
    power=abs(exponent)
    if power > MAX_EXACT_DIGITS/log_magnitude: return True
    return int(power)*log_magnitude + 1 > MAX_EXACT_DIGITS

def has_oversized_power(value):
    return isinstance(value,s.Basic) and any(
        oversized_rational_power(power.base,power.exp)
        for power in value.atoms(s.Pow) if power.base.is_Rational and power.exp.is_Integer)

class Engine:
    def __init__(self, request):
        self.request = request
        self.precision = max(3, min(200, int(request.get("precision", 30))))
        # Display digits limit only what the result view shows; the numeric work keeps self.precision.
        self.display_digits = max(1, min(self.precision, int(request.get("displayDigits", self.precision))))
        self.angle = request.get("angle", "RAD")
        self.variables = request.get("variables", {})
        self.functions = request.get("functions", {})
        self.resolving = set()
        self.symbols = {}
        self.visited = 0
        self.note = ""
        self.conditions = []
        self.bindings = {}
        self.allow_sequence_calls = False
        self.assumptions = request.get("assumptions", {})
    def symbol(self, name):
        if name not in self.symbols:
            options = {k: True for k in self.assumptions.get(name, []) if k in ("real", "positive", "negative", "integer", "nonzero")}
            self.symbols[name] = s.Symbol(name, **options)
        return self.symbols[name]
    def has_explicit_angle(self, node, seen=()):
        # 사용자가 쓴 각도 표시(π, °, ʳ, ᵍ)만 인정한다. 안쪽 DEG/GRAD 변환이 값을 만들며
        # 끼워 넣은 π를 명시적 라디안으로 오인하면 중첩 호출에서 모드 변환이 누락된다.
        if not isinstance(node, dict): return False
        if node.get("value") in ("degree", "pi", "rad", "gradian"): return True
        if node.get("kind") == "symbol":
            name = node.get("value")
            stored = self.variables.get(name) if isinstance(self.variables, dict) else None
            return (name not in self.bindings and name not in seen and name not in ("e", "i", "I", "oo", "true", "false")
                    and name not in CONSTANTS and isinstance(stored, dict) and self.has_explicit_angle(stored, seen + (name,)))
        return any(self.has_explicit_angle(child, seen) for child in node.get("args", []))
    def build(self, node, depth=0):
        self.visited += 1
        require(depth < 100 and self.visited <= 12000, "Expression complexity limit")
        kind, value = node["kind"], node.get("value", "")
        args = node.get("args", [])
        build = lambda a: self.build(a, depth + 1)
        if kind == "number":
            require(len(value.lstrip("-")) <= MAX_EXACT_DIGITS, "Number too large")
            if "e" in value.lower():
                mantissa, exponent = value.lower().split("e", 1)
                exponent = int(exponent)
                if abs(exponent) > MAX_NUMERIC_EXPONENT:
                    raise MathError("Decimal exponent limit: 100000")
                coefficient = s.Rational(mantissa)
                if oversized_rational_power(s.Integer(10), s.Integer(exponent)):
                    power=s.Pow(10, exponent, evaluate=False)
                    return power if coefficient == 1 else s.Mul(coefficient, power, evaluate=False)
            number = s.Rational(value)
            max_bits=math.ceil(MAX_EXACT_DIGITS*math.log2(10))
            require(abs(number.p).bit_length() <= max_bits and number.q.bit_length() <= max_bits, "Number size limit")
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
        if kind == "sexagesimal":
            require(len(args) == 3, "DMS input requires degrees, minutes and seconds")
            values=[build(a) for a in args]
            require(all(getattr(item,"is_number",False) and not item.has(s.I) for item in values), "DMS fields must be real numbers")
            return values[0]+values[1]/60+values[2]/3600
        if kind in ("binary", "relation"):
            require(value != ":=", "Use STO for variables or the Variables editor for functions")
            a, b = map(build, args)
            if isinstance(a, list): a = matrix(a)
            if isinstance(b, list): b = matrix(b)
            if value == "+":
                if has_oversized_power(a) or has_oversized_power(b):
                    return s.Add(a,b,evaluate=False)
                return a+b
            if value == "-":
                if has_oversized_power(a) or has_oversized_power(b):
                    return s.Add(a,s.Mul(-1,b,evaluate=False),evaluate=False)
                return a-b
            if value == "*":
                if has_oversized_power(a) or has_oversized_power(b):
                    return s.Mul(a,b,evaluate=False)
                return a*b
            if value == "∠": return self.call("polar",[a,b],args)
            if value == "/":
                require(b != 0, "Division by zero")
                if getattr(b, "free_symbols", None): self.conditions.append(s.Ne(b, 0, evaluate=False))
                return a/b
            if value == "^":
                require(not (a == 0 and b == 0), "Undefined: 0^0")
                if getattr(a, "is_Rational", False) and getattr(b, "is_Integer", False):
                    if oversized_rational_power(a, b): return s.Pow(a, b, evaluate=False)
                elif getattr(a, "is_number", False) and getattr(b, "is_number", False):
                    require(abs(b) <= MAX_NUMERIC_EXPONENT, "Exponent limit: 100000")
                return a**b
            if value == "mod": require(b != 0, "Division by zero"); return s.Mod(a,b)
            relations = {"=": s.Eq, "==": s.Eq, "!=": s.Ne, "<": s.Lt, ">": s.Gt, "<=": s.Le, ">=": s.Ge, "->": s.Eq}
            if value in relations: return relations[value](a,b,evaluate=False)
            raise MathError("Unknown operator")
        require(kind == "call", "Unknown AST node")
        # Preserve bound variable identity even if the user stored x previously.
        scoped = value in ("diff", "integrate", "limit", "series", "taylor", "sum", "product", "solve", "nsolve", "nintegrate", "nderivative", "minimum", "maximum", "collect", "subs", "domain", "range", "coeff", "quo", "rem", "resultant", "discriminant", "charpoly", "roots", "real_roots", "rsolve", "gradient", "divergence", "curl", "hessian", "jacobian", "laplacian", "dsolve", "desolve", "laplace", "ilaplace", "fourier", "ifourier", "ztrans", "invztrans", "mellin", "invmellin", "pdsolve") and len(args) > 1
        old = self.bindings.copy()
        if value=="solve" and len(args)==1: self.bindings["x"]=self.symbol("x")
        if scoped:
            if value in ("dsolve", "desolve", "laplace", "fourier", "ilaplace", "ifourier",
                         "ztrans", "invztrans", "mellin", "invmellin"):
                candidates = list(args[1:3])
            else:
                varnode = args[1]
                candidates = varnode.get("args", []) if varnode["kind"] == "list" else [varnode]
                if varnode["kind"] == "tuple": candidates = varnode["args"][:1]
                if varnode["kind"] == "relation": candidates = [varnode["args"][0]]
                if value in ("rsolve", "pdsolve") and varnode["kind"] == "call": candidates = varnode.get("args", [])
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
        # arcsin/arccos/arctan 계열 별칭은 사용자 정의 함수가 없을 때만 정식 이름으로 정규화한다.
        if name not in self.functions:
            name = canonical_function_name(name)
        if self.allow_sequence_calls and (name == "u" or name in ("u1", "u2", "u3", "u4", "u5", "u6")):
            require(len(a) == 1, "Sequence references take one integer index")
            return s.Function(name)(a[0])
        if name=="rnd":
            require(not a,"rnd expects no arguments")
            return s.Float(str(random.random()),self.precision)
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
        if name=="sexagesimal":
            require(len(a) == 3, "DMS input requires degrees, minutes and seconds")
            return a[0]+a[1]/60+a[2]/3600
        if name=="dms":
            if len(a)==3:return self.call("sexagesimal",a,nodes)
            return dms_parts(a[0])
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
            if not self.has_explicit_angle(nodes[1]) and not theta.free_symbols: theta *= {"DEG":s.pi/180,"GRAD":s.pi/200}.get(self.angle,1)
            return a[0]*(s.cos(theta)+s.I*s.sin(theta))
        if name in ("sin", "cos", "tan"):
            arg = a[0]
            if not self.has_explicit_angle(nodes[0]) and not getattr(arg, "free_symbols", set()):
                arg *= {"DEG": s.pi/180, "GRAD": s.pi/200}.get(self.angle, 1)
            return getattr(s,name)(arg)
        if name=="atan2":
            result=s.atan2(a[0],a[1])
            return result * ({"DEG":180/s.pi,"GRAD":200/s.pi}.get(self.angle,1) if not result.free_symbols else 1)
        if name in ("asin", "acos", "atan"):
            result = getattr(s,name)(*a)
            return result * ({"DEG": 180/s.pi, "GRAD":200/s.pi}.get(self.angle, 1) if not result.free_symbols else 1)
        if name in ("round","roundh"):
            require(len(a) in (1,2),name+" expects a number and optional decimal places")
            require(a[0].is_number and a[0].is_real,name+" requires a real number")
            require(len(a)==1 or a[1].is_Integer,name+" requires integer decimal places")
            places=int(a[1]) if len(a)==2 else 0
            require(abs(places)<=200,name+" decimal places must be between -200 and 200")
            # Scale an exact rational before rounding so the result does not inherit
            # SymPy's low-precision Float from Number.round().
            number=a[0] if isinstance(a[0],s.Rational) else s.Rational(str(s.N(a[0],max(self.precision,abs(places)+5))))
            scale=s.Integer(10)**places
            scaled=number*scale
            units=(s.sign(scaled)*s.floor(s.Abs(scaled)+s.Rational(1,2)) if name=="roundh"
                   else round(scaled))
            rounded=s.Rational(units,1)/scale
            return rounded if rounded.is_Integer else s.Float(rounded,max(self.precision,15,len(str(abs(rounded.p)))))
        basic = {"sqrt": s.sqrt, "cbrt": lambda x: s.real_root(x,3), "nthroot": s.root, "abs": s.Abs,
                 "floor": s.floor, "ceil": s.ceiling, "iPart": s.floor, "frac": s.frac,
                 "sign": s.sign, "gamma": s.gamma,
                 "erf":s.erf,"erfc":s.erfc,"Ei":s.Ei,"Si":s.Si,"Ci":s.Ci,"zeta":s.zeta,
                 "lambertw": s.LambertW, "beta": s.beta, "digamma": s.digamma, "polygamma": s.polygamma,
                 "fibonacci": s.fibonacci, "lucas": s.lucas, "bernoulli": s.bernoulli, "harmonic": s.harmonic,
                 "subfactorial": s.subfactorial, "totient": s.totient, "divisor_sigma": s.divisor_sigma,
                 "primepi": s.primepi, "nextprime": s.nextprime, "prevprime": s.prevprime,
                 "besselj": s.besselj, "bessely": s.bessely, "besseli": s.besseli, "besselk": s.besselk,
                 "ln": s.log, "log": lambda x, b=10: s.log(x,b), "exp": s.exp,
                 "sinc": s.sinc, "sinh": s.sinh, "cosh": s.cosh, "tanh": s.tanh, "asinh": s.asinh, "acosh": s.acosh, "atanh": s.atanh,
                 "conj": s.conjugate, "re": s.re, "im": s.im, "arg": s.arg,
                 "simplify": s.simplify, "expand": s.expand, "factor": s.factor, "collect": s.collect,
                 "diff": s.diff, "gcd": s.gcd, "lcm": s.lcm, "nCr": s.binomial,
                 "percent": lambda x: x/100, "degree": lambda x: x*s.pi/180,
                 "rad":lambda x:x,"gradian":lambda x:x*s.pi/200,
                 "quotient": lambda x,y: s.floor(x/y), "remainder": s.Mod,
                 "polar": lambda r,t: r*(s.cos(t)+s.I*s.sin(t)), "rectpolar": lambda z: [s.Abs(z),s.arg(z)]}
        if name in ("log","ln"): require(a[0]!=0,"Domain ERROR: logarithm of zero")
        if name=="log" and len(a)>1: require(a[1] not in (0,1),"Domain ERROR: invalid logarithm base")
        if name in basic: return basic[name](*a)
        if name in ("mod","divmod"):
            require(len(a)==2,name+" expects two arguments")
            require(a[1]!=0,"Division by zero")
            if name=="mod": return s.Mod(a[0],a[1])
            return [s.floor(a[0]/a[1]),s.Mod(a[0],a[1])]
        if name in ("prime", "isprime"):
            require(len(a)==1, name+" expects one integer")
            require(a[0].is_Integer, name+" requires an integer")
            if name=="prime":
                require(1<=a[0]<=100000, "prime index must be between 1 and 100000")
                return s.Integer(s.prime(int(a[0])))
            require(abs(a[0])<=10**15, "isprime input outside supported range")
            return s.true if s.isprime(a[0]) else s.false
        if name == "factorial" and getattr(a[0], "is_Integer", None) is not True:
            # A symbolic factorial (for example the Z-transform of 1/n!) stays unevaluated
            # instead of being rejected, while non-integer numeric input remains an error.
            require(getattr(a[0], "is_integer", False) is not False, "factorial requires an integer argument")
            return s.factorial(a[0])
        if name in ("factorial", "nPr", "factorint", "divisors"):
            require(a[0].is_Integer and 0 <= a[0] <= (10000 if name in ("factorial", "nPr") else 10**15), "Number theory input outside supported range")
            if name == "factorial": return s.factorial(a[0])
            if name == "nPr":
                require(a[1].is_Integer and 0 <= a[1] <= a[0], "nPr requires 0 ≤ r ≤ n")
                return s.factorial(a[0])/s.factorial(a[0]-a[1])
            require(a[0]>0,"Factorization and divisors require a positive integer")
            if name == "factorint":
                factors=[s.Pow(s.Integer(p),s.Integer(k),evaluate=False) if k>1 else s.Integer(p)
                         for p,k in s.factorint(a[0]).items()]
                return s.Mul(*factors,evaluate=False)
            return s.divisors(a[0])
        if name == "subs": return a[0].subs(a[1],a[2])
        if name in ("apart","partfrac"):
            require(len(a)==2, name+" expects an expression and variable")
            return s.apart(a[0],a[1])
        if name in ("together","cancel","trigsimp","trigexpand","powsimp","powdenest","hyperexpand"):
            transforms={"together":s.together,"cancel":s.cancel,"trigsimp":s.trigsimp,
                        "trigexpand":s.expand_trig,"powsimp":s.powsimp,"powdenest":s.powdenest,
                        "hyperexpand":s.hyperexpand}
            require(len(a)==1, name+" expects one expression")
            return transforms[name](a[0])
        if name=="nsimplify":
            require(len(a)==1,"nsimplify expects one expression")
            return s.nsimplify(a[0])
        if name=="taylor":
            require(len(a)==4,"taylor expects expression, variable, point and order")
            return s.series(a[0],a[1],a[2],int(a[3]))
        if name in ("comDenom","numden"):
            numerator,denominator=s.fraction(s.together(a[0]))
            return denominator if name=="comDenom" else [numerator,denominator]
        if name=="coeff":
            require(len(a) in (2,3),"coeff expects an expression, variable and optional power")
            return a[0].coeff(a[1]) if len(a)==2 else a[0].coeff(a[1],int(a[2]))
        if name in ("quo","rem"):
            require(len(a)==3,name+" expects two polynomials and a variable")
            quotient,remainder=s.div(a[0],a[1],a[2])
            return quotient if name=="quo" else remainder
        if name=="resultant":
            require(len(a)==3,"resultant expects two expressions and a variable")
            return s.resultant(a[0],a[1],a[2])
        if name=="discriminant":
            require(len(a)==2,"discriminant expects a polynomial and variable")
            return s.discriminant(a[0],a[1])
        if name=="domain":
            require(len(a)==2,"domain expects an expression and variable")
            return continuous_domain(a[0],a[1],s.S.Reals)
        if name=="range":
            require(len(a)==2,"range expects an expression and variable")
            return function_range(a[0],a[1],s.S.Reals)
        if name in ("roots","real_roots"):
            require(len(a)==2,"roots expects a polynomial and a variable")
            if name=="roots":
                solutions=s.roots(a[0],a[1])
                if not solutions: self.note="No rational roots were found."
                return [[root,s.Integer(multiplicity)] for root,multiplicity in sorted(solutions.items(),key=lambda item:str(item[0]))]
            return list(s.real_roots(a[0],a[1]))
        if name=="rsolve":
            require(len(a) in (2,3),"rsolve expects an equation, a sequence such as y(n), and optional initial conditions")
            dependent=a[1]
            require(isinstance(dependent,AppliedUndef),"rsolve needs a sequence term such as y(n)")
            equation=ode_equation(a[0])
            if len(a)==3:
                items=a[2] if isinstance(a[2],(list,tuple)) else [a[2]]
                initial={}
                for item in items:
                    require(isinstance(item,s.Equality),"Initial conditions must be equations")
                    initial[item.lhs]=item.rhs
                return s.rsolve(equation,dependent,initial)
            return s.rsolve(equation,dependent)
        if name == "integrate":
            require(len(a) in (2,4), "integrate expects a variable or integration bounds")
            spec = a[1] if len(a)==2 else (a[1],a[2],a[3])
            result = s.integrate(a[0],spec)
            if result.has(s.Integral): self.note = "Symbolic solution not found for the remaining integral."
            elif len(a)==2 and not isinstance(a[1], (list,tuple)): result += self.symbol("C")
            return result
        if name in ("dsolve","desolve"):
            require(len(a) in (3,4), "dsolve expects equation, dependent function and independent variable")
            require(isinstance(a[2], s.Symbol), "dsolve independent variable must be a symbol")
            equation=ode_equation(a[0]); dependent=a[1]
            function=dependent.func if isinstance(dependent, AppliedUndef) else dependent
            conditions=initial_conditions(a[3],dependent,a[2]) if len(a)==4 else None
            return s.dsolve(equation, fun=function, x=a[2], ics=conditions) if conditions else s.dsolve(equation, fun=function, x=a[2])
        if name in ("laplace","fourier"):
            require(len(a)==3, name+" expects an expression, time variable and transform variable")
            result=(s.laplace_transform if name=="laplace" else s.fourier_transform)(a[0],a[1],a[2])
            if isinstance(result, tuple):
                value, conditions = result[0], result[1:]
            else:
                value, conditions = result, ()
            meaningful=[str(condition) for condition in conditions if condition not in (s.true,0)]
            if meaningful: self.note="Transform conditions: "+", ".join(meaningful)
            return value
        if name in ("ilaplace","ifourier"):
            require(len(a)==3, name+" expects a transformed expression, transform variable and time variable")
            result=(s.inverse_laplace_transform if name=="ilaplace" else s.inverse_fourier_transform)(a[0],a[1],a[2])
            if isinstance(result, tuple):
                value, conditions = result[0], result[1:]
            else:
                value, conditions = result, ()
            meaningful=[str(condition) for condition in conditions if condition not in (s.true,0)]
            if meaningful: self.note="Transform conditions: "+", ".join(meaningful)
            return value
        if name in ("fft","ifft"):
            require(len(a)==1, name+" expects a list of samples")
            return discrete_fourier(a[0], inverse=name=="ifft")
        if name in ("ztrans","invztrans"):
            require(len(a)==3, name+" expects an expression, its index and the transform variable")
            require(isinstance(a[1],s.Symbol) and isinstance(a[2],s.Symbol), name+" variables must be symbols")
            if name=="ztrans":
                value,note=z_transform(a[0],a[1],a[2])
                if note: self.note=note
                return value
            return inverse_z_transform(a[0],a[1],a[2])
        if name in ("mellin","invmellin"):
            require(len(a) in (3,5), name+" expects an expression, its variable and the transform variable, with an optional strip")
            require(isinstance(a[1],s.Symbol) and isinstance(a[2],s.Symbol), name+" variables must be symbols")
            if name=="mellin":
                value,note=mellin_transform(a[0],a[1],a[2])
                if note: self.note=note
                return value
            strip=(a[3],a[4]) if len(a)==5 else None
            value,used=inverse_mellin_transform(a[0],a[1],a[2],strip)
            self.note="Convergence strip: "+str(used)
            return value
        if name=="pdsolve":
            require(len(a) in (2,3), "pdsolve expects an equation, a function such as u(x,y) and an optional hint")
            require(isinstance(a[1],AppliedUndef), "pdsolve needs a function such as u(x,y)")
            equation=ode_equation(a[0])
            try:
                result=s.pdsolve(equation,a[1],hint=str(a[2])) if len(a)==3 else s.pdsolve(equation,a[1])
            except NotImplementedError:
                raise MathError("This partial differential equation is outside the supported solver")
            if isinstance(result,dict):
                solutions=list(result.values())
                require(solutions,"No solution was found for this partial differential equation")
                return solutions[0] if len(solutions)==1 else solutions
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
                result=s.nsolve(expr,a[1],(a[2],a[3]),solver="bisect",prec=max(20,self.precision+10),maxsteps=max(100,4*(self.precision+10)))
            else: result=s.nsolve(expr,a[1],a[2],prec=max(20,self.precision+10),maxsteps=max(100,4*(self.precision+10)))
            require(all(c.subs(a[1],result)!=s.false for c in self.conditions),"No solution found in the expression domain")
            return s.N(result,self.precision)
        if name=="nderivative":
            require(len(a) in (3,4), "nderivative expects an expression, variable and point")
            return numeric_derivative(a[0],a[1],a[2],self.precision,a[3] if len(a)==4 else None)
        if name in ("nintegrate", "minimum", "maximum"):
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
        if name=="gradient":
            coords=coordinates(a[1])
            return [s.diff(a[0],var) for var in coords]
        if name in ("divergence","curl"):
            coords=coordinates(a[1]); field=matrix(a[0])
            if field.rows==1 and field.cols==len(coords): field=field.T
            require(field.cols==1 and field.rows==len(coords), "Vector field dimension does not match coordinates")
            if name=="divergence":
                return s.Add(*(s.diff(field[index],coords[index]) for index in range(len(coords))))
            if field.rows==2:
                return s.diff(field[1],coords[0])-s.diff(field[0],coords[1])
            require(field.rows==3,"Curl supports two or three dimensional vector fields")
            return s.Matrix([s.diff(field[2],coords[1])-s.diff(field[1],coords[2]),
                             s.diff(field[0],coords[2])-s.diff(field[2],coords[0]),
                             s.diff(field[1],coords[0])-s.diff(field[0],coords[1])])
        if name=="hessian":
            coords=coordinates(a[1])
            return s.Matrix([[s.diff(a[0],coords[row],coords[column]) for column in range(len(coords))] for row in range(len(coords))])
        if name=="jacobian":
            coords=coordinates(a[1])
            return matrix(a[0]).jacobian(coords)
        if name=="laplacian":
            coords=coordinates(a[1])
            return s.Add(*(s.diff(a[0],var,var) for var in coords))
        if name=="charpoly":
            require(len(a) in (1,2), "charpoly expects a matrix and optional variable")
            m=matrix(a[0]); variable=a[1] if len(a)==2 else s.Symbol("lambda")
            return m.charpoly(variable).as_expr()
        if name=="identity":
            require(len(a)==1 and a[0].is_Integer and 0<a[0]<=32, "identity size must be an integer from 1 to 32")
            return s.eye(int(a[0]))
        if name=="diag":
            require(len(a)==1 and isinstance(a[0],(list,tuple)), "diag expects a list of diagonal entries")
            return s.diag(*a[0])
        matrix_ops = {"det": lambda m: m.det(), "inverse": lambda m: m.inv(), "transpose": lambda m: m.T,
                      "rank": lambda m: s.Integer(m.rank()), "trace": lambda m: m.trace(), "rref": lambda m: m.rref()[0],
                      "ref": lambda m: m.echelon_form(), "lu": lambda m: list(m.LUdecomposition()),
                      "eigenvalues": lambda m: [[k,s.Integer(v)] for k,v in m.eigenvals().items()],
                      "eigenvectors": lambda m: [[v,s.Integer(k),vec] for v,k,vec in m.eigenvects()],
                      "norm": lambda m: m.norm(), "qr": lambda m: list(m.QRdecomposition()),
                      "cholesky": lambda m: m.cholesky(hermitian=False), "nullspace": lambda m: list(m.nullspace()),
                      "cofactor": lambda m: m.cofactor_matrix(), "adjugate": lambda m: m.adjugate(),
                      "rowspace": lambda m: list(m.rowspace()), "singularvalues": lambda m: list(m.singular_values()),
                      "frob": lambda m: s.sqrt(sum(item*item for item in m)),
                      "jordan": lambda m: list(m.jordan_form()), "dim": lambda m: [m.rows,m.cols],
                      "pinv": lambda m: m.pinv(), "ctranspose": lambda m: m.H,
                      "svd": lambda m: list(m.singular_value_decomposition())}
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
        if name in ("covariance","correlation"):
            require(len(a)==2, name+" expects two data lists")
            xs=flatten(a[0]); ys=flatten(a[1])
            require(len(xs)==len(ys),"Covariance and correlation require equal data lengths")
            n=len(xs)
            require(n>0,"Enter paired data")
            mx=sum(xs)/n; my=sum(ys)/n
            covariance=sum((x-mx)*(y-my) for x,y in zip(xs,ys))/n
            if name=="covariance": return covariance
            vx=sum((x-mx)**2 for x in xs)/n; vy=sum((y-my)**2 for y in ys)/n
            require(vx*vy!=0,"Correlation requires variation in both data sets")
            return s.simplify(covariance/s.sqrt(vx*vy))
        if name == "regression":
            rows = a[0]; mode = str(a[1]) if len(a)>1 else "linear"
            return fit_regression(self, rows, mode)
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
        if name in ("normpdf", "normcdf", "invnorm", "tpdf", "tcdf", "invt", "chi2pdf", "chi2cdf", "fpdf", "fcdf",
                    "binompdf", "binomcdf", "poissonpdf", "poissoncdf", "geometpdf", "geometcdf",
                    "exppdf", "expcdf", "unifpdf", "unifcdf", "gammapdf", "gammacdf", "betapdf", "betacdf",
                    "lognormpdf", "lognormcdf"):
            return distribution_value(self, name, a)
        if name in ("ttest", "ttest2", "ttestpaired", "ztest", "ztest2", "chi2test", "chi2independence", "fisherexact", "anova", "shapiro", "tinterval", "zinterval"):
            return statistical_test(self, name, a, nodes)
        if name in ("tvmfv", "tvmpv", "tvmpmt", "tvmn", "tvmrate", "npv", "irr", "amort", "cagr"):
            return finance_value(self, name, a, nodes)
        if name in self.functions:
            function = self.functions[name]
            require(len(function["parameters"])==len(a),"Function argument count mismatch")
            require(name not in self.resolving,"Recursive function definition")
            old = self.bindings.copy(); self.bindings.update(zip(function["parameters"],a)); self.resolving.add(name)
            try: return self.build(function["body"])
            finally: self.bindings = old; self.resolving.remove(name)
        if name.isidentifier():
            return s.Function(name)(*a)
        raise MathError("Unknown function: " + name)
