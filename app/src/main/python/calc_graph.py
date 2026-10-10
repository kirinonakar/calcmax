"""Graph sampling, parameters, shading, and curve analysis."""
from calc_limits import within_limit, capped
import math
import json
from collections import OrderedDict
from functools import lru_cache
import sympy as s
from sympy.core.function import AppliedUndef
from calc_shared import MathError, numeric_integral, require
from calc_display import readable

_graph_programs = OrderedDict()


def graph_expressions(engine, trees, axes):
    """Reuse symbolic programs across frames; all evaluation context is in the key."""
    context = {"trees": trees, "axes": axes, "precision": engine.precision,
               "displayDigits": engine.display_digits, "angle": engine.angle,
               "variables": engine.variables, "functions": engine.functions,
               "assumptions": engine.assumptions, "sequence": engine.allow_sequence_calls,
               "bindings": {name:s.srepr(value) for name,value in engine.bindings.items()}}
    key = json.dumps(context, sort_keys=True, separators=(",", ":"))
    # Random calls must run anew, including those inside stored definitions.
    cacheable = not any('"value":"'+name+'"' in key for name in ("rnd", "rand", "randInt"))
    if cacheable and key in _graph_programs:
        _graph_programs.move_to_end(key)
        return _graph_programs[key]
    def freeze(value):
        return tuple(freeze(item) for item in value) if isinstance(value, (list, tuple)) else value
    expressions = tuple(freeze(engine.build(tree)) for tree in trees)
    if cacheable:
        _graph_programs[key] = expressions
        if len(_graph_programs) > 32: _graph_programs.popitem(last=False)
    return expressions


@lru_cache(maxsize=64)
def _compiled_graph(axes, expression):
    """Bounded cache of immutable symbolic programs, never engine/request state."""
    return s.lambdify(axes, expression, modules="math", cse=True, docstring_limit=0)


@lru_cache(maxsize=64)
def _square_free_graph(expression, x, y):
    if expression.is_polynomial(x, y):
        polynomial = s.Poly(expression, x, y)
        if polynomial.total_degree() <= 12: return polynomial.sqf_part().as_expr()
    return expression


def graph_function(expression, axes, sliders=None):
    # Keep slider values as numeric arguments so animation doesn't recompile.
    if isinstance(expression, (list, tuple)):
        expression = tuple(expression)
        symbols = set().union(*(item.free_symbols for item in expression))
    else:
        symbols = expression.free_symbols
    parameters = tuple(sorted(symbols-set(axes), key=str))
    if not parameters:
        return _compiled_graph(tuple(axes), expression)
    values = tuple(float((sliders or {})[symbol]) for symbol in parameters)
    raw = _compiled_graph(tuple(axes)+parameters, expression)
    return lambda *args: raw(*args, *values)

def regression_samples(engine, value, rows, request):
    xs = [point for point in (_finite_real(row[0]) for row in rows) if point is not None]
    require(len(xs)>=2,"Regression requires x,y pairs")
    low,high = min(xs),max(xs)
    if high<=low: low-=1.0; high+=1.0
    padding=(high-low)*0.05
    start,end=low-padding,high+padding
    count=capped(max(120,int(request.get("samples",240))),600)
    if getattr(engine, "regression_predict", None) is not None:
        function = lambda x: engine.regression_predict([x])
    else:
        x=next(iter(value.free_symbols),engine.symbol("x"))
        function=s.lambdify(x,value,modules="math",cse=True,docstring_limit=0)
    curve=[]
    for index in range(count+1):
        at=start+(end-start)*index/count
        try: y=float(function(at))
        except (TypeError,ValueError,ZeroDivisionError,OverflowError): continue
        if math.isfinite(y) and abs(y)<1e100: curve.append([at,y])
    return curve

def bind_graph_parameters(engine, request):
    # A visible graph parameter takes precedence over a separately stored variable.
    for name in request.get("parameters",{}): engine.bindings[str(name)]=engine.symbol(str(name))

def graph(engine, request):
    bind_graph_parameters(engine,request)
    kind = request.get("graphKind","cartesian")
    trees = request.get("trees",[])
    start,end = float(request.get("min",-10)),float(request.get("max",10))
    require(math.isfinite(start) and math.isfinite(end) and end>start,"Invalid graph range")
    if kind == "implicit":
        return graph_implicit(engine, request, trees, start, end)
    if kind == "cartesian":
        return graph_cartesian(engine, request, trees, start, end)
    if kind == "sequence":
        return graph_sequence(engine, request, trees, start, end)
    if kind == "surface":
        return graph_surface(engine, request, trees, start, end)
    if kind == "space":
        return graph_space(engine,request,trees,start,end)
    if kind == "differential":
        return graph_differential(engine, request, trees, start, end)
    var = engine.symbol(request.get("variable","x")); engine.bindings[str(var)] = var
    expressions = graph_expressions(engine, trees, (str(var),))
    shade_items = (request.get("shadings") or []) if kind == "cartesian" else []
    shade_expressions = [graph_expressions(engine, item.get("trees") or [], (str(var),)) for item in shade_items]
    all_expressions = list(expressions)+[expression for group in shade_expressions for expression in group]
    names = parameter_names(all_expressions, {str(var)})
    sliders = resolved_parameters(engine, request, all_expressions, {str(var)})
    count = capped(max(100,int(request.get("samples",500))),1600)
    curves=[]
    curve_parameters=[]
    for expression in expressions:
        function=graph_function(expression, (var,), sliders)
        samples, sample_parameters = adaptive_samples(function, start, end, count, kind)
        samples, sample_parameters = simplify_samples(samples, sample_parameters, request, start, end)
        curves.append(samples)
        if kind in ("parametric","polar"): curve_parameters.append(sample_parameters)
    result = {"curves":curves,"parameters":sorted(names)}
    if curve_parameters: result["curveParameters"] = curve_parameters
    derivative_index = request.get("derivativeCurveIndex")
    if kind == "cartesian" and isinstance(derivative_index, int) and 0 <= derivative_index < len(expressions):
        result["derivativeExpression"] = readable(expressions[derivative_index])
    if kind == "cartesian" and shade_items:
        result["shadings"] = graph_shading(engine, request, shade_items, shade_expressions, sliders, start, end)
    return result

@lru_cache(maxsize=128)
def cartesian_curve(expression, x, y):
    """A bare f(x) means y=f(x); equations and expressions using y define contours."""
    if isinstance(expression, s.Equality):
        residual = expression.lhs-expression.rhs
    else:
        require(isinstance(expression, s.Expr), "Enter a function y=f(x) or an equation F(x,y)=0")
        if not expression.has(y): return expression, y-expression
        residual = expression
    require(isinstance(residual, s.Expr) and residual != 0, "Equation is true everywhere; enter a curve equation")
    # Keep ordinary equations on the fast function sampler and analysis path.
    try:
        polynomial = s.Poly(residual, y)
        if polynomial.degree() == 1:
            coefficient, constant = polynomial.all_coeffs()
            return -constant/coefficient, residual
    except s.PolynomialError: pass
    return None, residual

@lru_cache(maxsize=64)
def cartesian_branches(residual, y):
    try:
        return tuple(branch for branch in s.solve(residual, y) if isinstance(branch, s.Expr) and not branch.has(y))
    except (NotImplementedError, ValueError, TypeError): return ()

def contour_curve(expression, x, y, sliders, xmin, xmax, ymin, ymax, count):
    numeric = substitute_parameters(expression, sliders)
    require(numeric != 0, "Equation is true everywhere; enter a curve equation")
    program = _square_free_graph(expression, x, y)
    bound = substitute_parameters(program, sliders)
    if bound.is_polynomial(x, y):
        polynomial = s.Poly(bound, x, y)
        if polynomial.total_degree() <= 12:
            square_free = polynomial.sqf_part()
            if square_free.total_degree() != polynomial.total_degree():
                program, sliders = square_free.as_expr(), None
    return implicit_samples(graph_function(program, (x, y), sliders), xmin, xmax, ymin, ymax, count)

def graph_cartesian(engine, request, trees, xmin, xmax):
    x, y = engine.symbol("x"), engine.symbol("y")
    engine.bindings.update({"x": x, "y": y})
    raw = graph_expressions(engine, trees, ("x", "y"))
    curvespecs = [cartesian_curve(expression, x, y) for expression in raw]
    shade_items = request.get("shadings") or []
    shade_expressions = [graph_expressions(engine, item.get("trees") or [], ("x", "y")) for item in shade_items]
    all_expressions = [residual for _, residual in curvespecs]+[expression for group in shade_expressions for expression in group]
    bound_trees = [bound["tree"] for item in shade_items for key in ("xBounds","yBounds") for bound in item.get(key,[])]
    all_expressions += list(graph_expressions(engine,bound_trees,("x","y")))
    names = parameter_names(all_expressions, {"x", "y"})
    sliders = {symbol: value for symbol, value in resolved_parameters(engine, request, all_expressions, {"x", "y"}).items() if symbol not in (x, y)}
    count = capped(max(100, int(request.get("samples", 500))),1600)
    contour_count = capped(max(80, int(math.sqrt(count))*8),240)
    ymin, ymax = float(request.get("yMin", -5)), float(request.get("yMax", 5))
    require(math.isfinite(ymin) and math.isfinite(ymax) and ymax > ymin, "Invalid implicit y range")
    curves, implicit = [], []
    def sample(expression):
        function=graph_function(expression, (x,), sliders)
        # Split at explicit branch boundaries, including same-sign jumps and
        # narrow restricted intervals that a uniform grid can entirely miss.
        boundaries=set()
        resolved=substitute_parameters(expression,sliders)
        for piece in resolved.atoms(s.Piecewise):
            for _,condition in piece.args:
                for relation in condition.atoms(s.core.relational.Relational):
                    difference=relation.lhs-relation.rhs
                    if difference.free_symbols-set((x,)): continue
                    try:
                        polynomial=s.Poly(difference,x)
                        if not 1<=polynomial.degree()<=4: continue
                        for root in s.solve(difference,x):
                            value=_finite_real(root)
                            if value is not None and xmin<value<xmax: boundaries.add(value)
                    except (s.PolynomialError,NotImplementedError,ValueError): pass
        if not boundaries:
            points, parameters = adaptive_samples(function, xmin, xmax, count, "cartesian",screen_bounds=(xmin,xmax,ymin,ymax))
        else:
            breaks=[xmin]+sorted(boundaries)+[xmax];points=[];parameters=[]
            for low,high in zip(breaks,breaks[1:]):
                left=math.nextafter(low,high) if low in boundaries else low
                right=math.nextafter(high,low) if high in boundaries else high
                part,positions=adaptive_samples(function,left,right,max(100,int(count*(high-low)/(xmax-xmin))),"cartesian",screen_bounds=(xmin,xmax,ymin,ymax))
                for edge,index in ((low,0),(high,-1)):
                    if edge not in boundaries or part[index] is None: continue
                    try: actual=_finite_real(function(edge))
                    except (ValueError,TypeError,ZeroDivisionError,OverflowError): actual=None
                    if actual is not None and abs(actual-part[index][1])<=1e-8*max(1,abs(actual)):
                        part[index]=[edge,actual];positions[index]=edge
                if points:
                    try: exact=_finite_real(function(low))
                    except (ValueError,TypeError,ZeroDivisionError,OverflowError): exact=None
                    continuous=(exact is not None and points[-1] is not None and part[0] is not None
                                and abs(points[-1][1]-exact)<=1e-8*max(1,abs(exact))
                                and abs(part[0][1]-exact)<=1e-8*max(1,abs(exact)))
                    if not continuous: points.append(None);parameters.append(low)
                points.extend(part);parameters.extend(positions)
        return simplify_samples(points, parameters, request, xmin, xmax)[0]
    for function, residual in curvespecs:
        # A parameter may make a linear equation degenerate (e.g. a*y=x at a=0).
        if function is not None and not substitute_parameters(function, sliders).has(s.zoo, s.nan, s.oo, -s.oo):
            curves.append(sample(function)); implicit.append(False)
        else:
            curves.append(contour_curve(residual, x, y, sliders, xmin, xmax, ymin, ymax, contour_count)); implicit.append(True)
    result = {"curves": curves, "parameters": sorted(names), "implicitCurves": implicit}
    for order, prefix in ((1, "derivative"), (2, "secondDerivative")):
        selected = request.get(prefix+"Selected")
        if not isinstance(selected, int) or not 0 <= selected < len(curvespecs):
            continue
        function, residual = curvespecs[selected]
        branches = (function,) if function is not None else cartesian_branches(residual, y)
        require(branches, "Derivative graph requires branches expressible as y=f(x)")
        derivatives = tuple(s.diff(branch, x, order) for branch in branches)
        points = []
        for derivative in derivatives:
            if points: points.append(None)
            points.extend(sample(derivative))
        result.update({prefix+"CurveIndex": len(curves), prefix+"Selected": selected,
                       prefix+"Expression": readable(derivatives[0] if len(derivatives)==1 else s.Tuple(*derivatives))})
        curves.append(points)
    if "derivativeCurveIndex" not in result:
        index = request.get("derivativeCurveIndex")
        if isinstance(index, int) and 0 <= index < len(curvespecs):
            result["derivativeExpression"] = readable(curvespecs[index][0])
    if shade_items:
        result["shadings"] = graph_shading(engine, request, shade_items, shade_expressions, sliders, xmin, xmax)
    return result

def graph_implicit(engine, request, trees, xmin, xmax):
    """Contour F(x,y)=0 with finite, independently separated line segments."""
    ymin, ymax = float(request.get("yMin", -3)), float(request.get("yMax", 3))
    require(math.isfinite(ymin) and math.isfinite(ymax) and ymax > ymin, "Invalid implicit y range")
    require(1 <= len(trees) <= 20, "Enter one to twenty implicit equations")
    x, y = engine.symbol("x"), engine.symbol("y")
    engine.bindings.update({"x": x, "y": y})
    expressions = []
    for expression in graph_expressions(engine, trees, ("x", "y")):
        if isinstance(expression, s.Equality):
            expression = expression.lhs-expression.rhs
        require(isinstance(expression, s.Expr), "Enter an equation F(x,y)=0")
        expressions.append(expression)
    names = parameter_names(expressions, {"x", "y"})
    sliders = resolved_parameters(engine, request, expressions, {"x", "y"})
    # Coordinate names remain axes even if a previous graph stored slider values for them.
    sliders = {key: value for key, value in sliders.items() if key not in (x, y)}
    count = capped(max(80, int(math.sqrt(max(1, int(request.get("samples", 500))))*8)),240)
    curves = []
    for expression in expressions:
        curves.append(contour_curve(expression, x, y, sliders, xmin, xmax, ymin, ymax, count))
    return {"curves": curves, "implicit": True, "parameters": sorted(names)}

def implicit_samples(function, xmin, xmax, ymin, ymax, count):
    """Refine a quadtree near the contour, then march its smallest triangles.

    Probe edge midpoints and centers as well as corners to catch closed loops
    and domain boundaries. Cache shared vertices and root refinements.
    """
    def value(point):
        try:
            return _finite_real(function(*point))
        except (TypeError, ValueError, ZeroDivisionError, OverflowError):
            return None
    # Eight fine cells per coarse cell: retain requested density without
    # rounding an entire plane up to the next power of two.
    resolution = max(8, int(math.ceil(count/8))*8)
    xs = [xmin+(xmax-xmin)*i/resolution for i in range(resolution+1)]
    ys = [ymin+(ymax-ymin)*i/resolution for i in range(resolution+1)]
    values = {}
    def sample(point):
        if point not in values:
            values[point] = value((xs[point[0]], ys[point[1]]))
        return values[point]
    edges = {}
    def crossing(a, b):
        va, vb = sample(a), sample(b)
        if va is None or vb is None or va != 0 and vb != 0 and (va < 0) == (vb < 0): return None
        key = tuple(sorted((a, b)))
        if key in edges: return edges[key]
        pa, pb = [xs[a[0]], ys[a[1]]], [xs[b[0]], ys[b[1]]]
        root = None
        if va is not None and vb is not None:
            if va == 0: root = pa
            elif vb == 0: root = pb
            elif (va < 0) != (vb < 0):
                scale = max(abs(va), abs(vb))
                for iteration in range(20):
                    # Secant interpolation converges quickly on smooth edges;
                    # periodic bisection safeguards very unbalanced brackets.
                    fraction = .5 if iteration % 3 == 2 else max(.001, min(.999, abs(va)/(abs(va)+abs(vb))))
                    middle = [pa[0]+(pb[0]-pa[0])*fraction, pa[1]+(pb[1]-pa[1])*fraction]
                    vm = value(middle)
                    if vm is None: break
                    if abs(vm) <= scale*1e-7:
                        root = middle
                        break
                    if (va < 0) == (vm < 0): pa, va = middle, vm
                    else: pb, vb = middle, vm
                else:
                    if abs(vm) <= scale*1e-4: root = middle
        edges[key] = root
        return root
    curve = []
    def cell(col, row, width):
        a, b, c, d = (col,row), (col+width,row), (col+width,row+width), (col,row+width)
        corners = (a,b,c,d)
        if width > 1:
            half = width//2
            probes = corners+((col+half,row), (col+width,row+half),
                              (col+half,row+width), (col,row+half), (col+half,row+half))
            samples = [sample(p) for p in probes]
            finite = [v for v in samples if v is not None]
            if not finite: return
            low, high = min(finite), max(finite)
            # Uniform, far-from-zero cells need no more evaluations. Nearby
            # same-sign cells still descend, including loops inside a cell.
            if len(finite) == len(probes) and (low > 0 or high < 0):
                if min(abs(low), abs(high)) > 1.5*(high-low): return
            for dx, dy in ((0,0), (half,0), (half,half), (0,half)):
                cell(col+dx, row+dy, half)
            return
        for triangle in ((a,b,c), (a,c,d)):
            samples = [sample(p) for p in triangle]
            if None in samples or min(samples) > 0 or max(samples) < 0: continue
            roots = []
            for i in range(3):
                root = crossing(triangle[i], triangle[(i+1)%3])
                if root is not None and root not in roots: roots.append(root)
            if len(roots) == 2: curve.extend([roots[0], roots[1], None])
    width = 8
    for row in range(0, resolution, width):
        for col in range(0, resolution, width):
            cell(col, row, width)
    return curve

def _finite_real(value):
    try:
        value = float(value)
        return value if math.isfinite(value) and abs(value) < 1e100 else None
    except (TypeError, ValueError, ZeroDivisionError, OverflowError):
        return None

def parameter_values(engine, request):
    """Slider values for free graph parameters such as a, b and c."""
    sliders = {}
    for name, value in (request.get("parameters") or {}).items():
        number = _finite_real(value)
        if number is not None:
            sliders[engine.symbol(str(name))] = s.Float(number, engine.precision)
    return sliders

def resolved_parameters(engine, request, expressions, excluded):
    """Slider values plus unit defaults so a curve still plots before its sliders move."""
    sliders = parameter_values(engine, request)
    for name in parameter_names(expressions, excluded):
        sliders.setdefault(engine.symbol(name), s.Float(1.0, engine.precision))
    return sliders

def parameter_names(expressions, excluded):
    """Free symbols that the graphing panel can expose as sliders."""
    names = set()
    for expression in expressions:
        if isinstance(expression, (list, tuple)):
            names |= parameter_names(expression, excluded)
            continue
        for symbol in getattr(expression, "free_symbols", set()):
            if str(symbol) not in excluded: names.add(str(symbol))
    return names

def substitute_parameters(expression, sliders):
    if not sliders: return expression
    if isinstance(expression, (list, tuple)):
        return [substitute_parameters(item, sliders) for item in expression]
    return expression.subs(sliders) if getattr(expression, "subs", None) else expression

def graph_shading(engine, request, items, groups, sliders, xmin, xmax):
    """[shade] regions: half-plane inequalities and bands between one or two curves."""
    ymin,ymax = float(request.get("yMin",-5)),float(request.get("yMax",5))
    require(math.isfinite(ymin) and math.isfinite(ymax) and ymax>ymin,"Shading needs a valid y range")
    span = ymax-ymin; low_edge,high_edge = ymin-span,ymax+span
    def clamp(value): return min(max(value,low_edge),high_edge)
    x = engine.symbol("x"); engine.bindings["x"] = x
    count = capped(max(200,int(request.get("samples",500))),1200)
    def endpoint(item, key, default):
        tree = item.get(key)
        if tree is None: return default
        number = _finite_real(s.N(substitute_parameters(engine.build(tree),sliders),engine.precision))
        require(number is not None,"Shading intervals must be finite numbers")
        return number
    def evaluate(function, at):
        try: return _finite_real(function(at))
        except (TypeError, ValueError, ZeroDivisionError, OverflowError): return None
    def samples(expression, a, b):
        function = graph_function(expression, (x,), sliders)
        points = []
        for index in range(count+1):
            at = a+(b-a)*index/count
            value = evaluate(function,at)
            points.append([at,value] if value is not None else None)
        return points
    shadings = []
    for item,group in zip(items,groups):
        mode = item.get("mode")
        a,b = endpoint(item,"a",xmin),endpoint(item,"b",xmax)
        require(a < b,"Shading intervals must be increasing")
        for bound in item.get("xBounds",[]):
            require(bound.get("side") in ("lower","upper"), "Invalid shading x bound")
            expression = engine.build(bound["tree"])
            require(not expression.has(x,engine.symbol("y")), "Shading x bounds must be constant")
            at = endpoint({"bound":bound["tree"]},"bound",0)
            if bound["side"] == "lower": a = max(a,at)
            else: b = min(b,at)
        if a >= b:
            shadings.append({"mode":mode,"boundary":[],"fill":[]})
            continue
        if mode == "region":
            constraints = item.get("constraints") or []
            require(len(group) == len(constraints) and group, "A shaded region needs inequalities")
            lower, upper, vertical = [], [], []
            left, right = max(a,xmin), min(b,xmax)
            for expression, constraint in zip(group,constraints):
                axis, side = constraint.get("axis"), constraint.get("side")
                require(axis in ("x","y") and side in ("lower","upper"), "Invalid shading constraint")
                require(not expression.has(engine.symbol("y")), "Shading boundaries cannot depend on y")
                if axis == "x":
                    require(not expression.has(x), "Shading x bounds must be constant")
                    at = _finite_real(substitute_parameters(expression,sliders).evalf(engine.precision))
                    require(at is not None, "Shading x bounds must be finite")
                    if side == "lower": left = max(left,at)
                    else: right = min(right,at)
                    vertical.append(at)
                else:
                    (lower if side == "lower" else upper).append(graph_function(expression,(x,),sliders))
            polygons, boundary, run = [], [], []
            if left < right:
                # Intersect every bound at each x; a contradictory region stays empty.
                for index in range(count+2):
                    at = left+(right-left)*min(index,count)/count
                    lows = [evaluate(fn,at) for fn in lower]
                    highs = [evaluate(fn,at) for fn in upper]
                    low = max([ymin]+lows) if all(v is not None for v in lows) else None
                    high = min([ymax]+highs) if all(v is not None for v in highs) else None
                    if index > count or low is None or high is None or low >= high:
                        if len(run) >= 2:
                            top = [[px,hi] for px,lo,hi in run]
                            bottom = [[px,lo] for px,lo,hi in run]
                            polygons.append(top+list(reversed(bottom)))
                        run = []
                    else: run.append([at,low,high])
                for expression, constraint in zip(group,constraints):
                    if constraint["axis"] == "y" and polygons:
                        line = []
                        for point in samples(expression,left,right):
                            if point is not None:
                                at,value = point
                                lows,highs = [evaluate(fn,at) for fn in lower],[evaluate(fn,at) for fn in upper]
                                if not all(v is not None for v in lows+highs) or not max([ymin]+lows)-1e-9 <= value <= min([ymax]+highs)+1e-9: point = None
                            line.append(point)
                        boundary.append(line)
                for at in vertical:
                    if left <= at <= right:
                        lows = [evaluate(fn,at) for fn in lower]
                        highs = [evaluate(fn,at) for fn in upper]
                        if all(v is not None for v in lows+highs):
                            lo,hi = max([ymin]+lows),min([ymax]+highs)
                            if lo < hi: boundary.append([[at,lo],[at,hi]])
            shadings.append({"mode":mode,"boundary":boundary,"fill":polygons})
        elif mode == "halfplane":
            require(len(group) == 1,"A shaded inequality needs one boundary curve")
            boundary = samples(group[0],a,b)
            edge = low_edge if item.get("side") == "below" else high_edge
            polygons=[]; run=[]
            for point in boundary+[None]:
                value = None if point is None else point[1]
                if value is None:
                    if len(run) >= 2: polygons.append(run+[[run[-1][0],edge],[run[0][0],edge]])
                    run=[]
                    continue
                run.append([point[0],clamp(value)])
            shadings.append({"mode":mode,"boundary":[boundary],"fill":polygons})
        elif mode == "band":
            require(1 <= len(group) <= 2,"[shade] takes one or two functions")
            first = samples(group[0],a,b)
            second = samples(group[1],a,b) if len(group) == 2 else [[point[0],0.0] if point else None for point in first]
            lower,upper = [],[]
            for bound in item.get("yBounds",[]):
                require(bound.get("side") in ("lower","upper"), "Invalid shading y bound")
                expression = engine.build(bound["tree"])
                require(not expression.has(engine.symbol("y")), "Shading boundaries cannot depend on y")
                (lower if bound["side"] == "lower" else upper).append(graph_function(expression,(x,),sliders))
            runs=[]; run=[]; previous=None
            for index in range(len(first)):
                low = None if first[index] is None else first[index][1]
                high = None if second[index] is None else second[index][1]
                at = a+(b-a)*index/count
                lows,highs = [evaluate(fn,at) for fn in lower],[evaluate(fn,at) for fn in upper]
                if low is None or high is None or any(v is None for v in lows+highs):
                    if len(run) >= 2: runs.append(run)
                    run=[]; previous=None
                    continue
                low,high = max([min(low,high)]+lows),min([max(low,high)]+highs)
                current = [at,low,high]
                if previous is not None and (previous[2]-previous[1])*(high-low)<0:
                    # Retain the entry/exit point when clipping changes within a sample.
                    delta = previous[2]-previous[1]
                    fraction = delta/(delta-(high-low))
                    px = previous[0]+(at-previous[0])*fraction
                    py = previous[1]+(low-previous[1])*fraction
                    run.append([px,clamp(py),clamp(py)])
                if low <= high:
                    run.append([at,clamp(low),clamp(high)])
                else:
                    if len(run) >= 2: runs.append(run)
                    run=[]
                previous = current
            if len(run) >= 2: runs.append(run)
            polygons = []; boundaries = [[],[]]
            for segment in runs:
                top = [[at,max(low,high)] for at,low,high in segment]
                bottom = [[at,min(low,high)] for at,low,high in segment]
                polygons.append(top+list(reversed(bottom)))
                for line,points in zip(boundaries,(bottom,top)):
                    if line: line.append(None)
                    line.extend(points)
            shadings.append({"mode":mode,"boundary":boundaries if lower or upper else [first,second if len(group) == 2 else None],"fill":polygons})
        else:
            raise MathError("Unknown shading mode")
    return shadings

def simplify_samples(points, parameters, request, start, end):
    """Collapse collinear samples within 0.3 screen pixels; retain breaks/features."""
    xmin, xmax = float(request.get("xMin", start)), float(request.get("xMax", end))
    ymin, ymax = float(request.get("yMin", -5)), float(request.get("yMax", 5))
    if not all(map(math.isfinite, (xmin, xmax, ymin, ymax))) or xmax <= xmin or ymax <= ymin:
        return points, parameters
    sx, sy = 800/(xmax-xmin), 800/(ymax-ymin)
    keep = set()
    def reduce_run(first, last):
        keep.update((first, last))
        if request.get("graphKind", "cartesian") == "cartesian":
            # A slope corridor gives a linear-time, bounded vertical error
            # for monotone x. Each omitted point constrains the final segment.
            anchor, low, high = first, -math.inf, math.inf
            for i in range(first+1, last+1):
                dx = (points[i][0]-points[anchor][0])*sx
                dy = (points[i][1]-points[anchor][1])*sy
                if dx <= 0: keep.add(i); anchor, low, high = i, -math.inf, math.inf; continue
                slope = dy/dx
                if slope < low or slope > high:
                    keep.add(i-1)
                    anchor, low, high = i-1, -math.inf, math.inf
                    dx = (points[i][0]-points[anchor][0])*sx
                    dy = (points[i][1]-points[anchor][1])*sy
                low, high = max(low, (dy-.3)/dx), min(high, (dy+.3)/dx)
            return
        stack = [(first, last)]
        while stack:
            left, right = stack.pop()
            if right-left < 2: continue
            a, b = points[left], points[right]
            dx, dy = (b[0]-a[0])*sx, (b[1]-a[1])*sy
            length2 = dx*dx+dy*dy
            best, index = .3*.3, None
            for i in range(left+1, right):
                px, py = (points[i][0]-a[0])*sx, (points[i][1]-a[1])*sy
                at = max(0, min(1, (px*dx+py*dy)/length2)) if length2 else 0
                distance2 = (px-at*dx)**2+(py-at*dy)**2
                if distance2 > best: best, index = distance2, i
            if index is not None:
                keep.add(index)
                stack.extend(((left,index), (index,right)))
    first = None
    for i, point in enumerate(points):
        if point is None:
            keep.add(i)
            if first is not None: reduce_run(first, i-1)
            first = None
            continue
        if first is None: first = i
        # Preserve exact axis hits and sampled extrema for tracing and fitting.
        feature = any(value == 0 and (
            i > 0 and points[i-1] is not None and points[i-1][axis] != 0 or
            i+1 < len(points) and points[i+1] is not None and points[i+1][axis] != 0
        ) for axis, value in enumerate(point))
        if i > 0 and i+1 < len(points) and points[i-1] is not None and points[i+1] is not None:
            feature |= any((point[axis]-points[i-1][axis])*(points[i+1][axis]-point[axis]) < 0 for axis in (0,1))
        if feature:
            reduce_run(first, i)
            first = i
    if first is not None: reduce_run(first, len(points)-1)
    indices = sorted(keep)
    return [points[i] for i in indices], [parameters[i] for i in indices]


def adaptive_samples(function, start, end, base_count, kind="cartesian",screen_bounds=None):
    """Sample coarsely first, then add points where the curve bends or breaks."""
    def point(at):
        try:
            if kind == "parametric":
                x, y = map(float, function(at))
            else:
                y = float(function(at)); x = at
                if kind == "polar": x, y = y*math.cos(at), y*math.sin(at)
            return [x, y] if math.isfinite(x) and math.isfinite(y) and abs(x) < 1e100 and abs(y) < 1e100 else None
        except (TypeError, ValueError, ZeroDivisionError, OverflowError):
            return None
    intervals = capped(max(100, base_count),512)
    values = {start + (end-start)*i/intervals: None for i in range(1,intervals)}
    # Preserve nextafter endpoints: interpolation can round an open endpoint
    # back onto the excluded boundary, especially when that boundary is zero.
    values.update({start:None,end:None})
    for at in values:
        values[at] = point(at)
    max_points = capped(max(base_count+1, 1200),1800)
    def refine(left, right, depth):
        # A fixed seven levels loses narrow visible sections when zoomed out
        # (e.g. x**23-4 in a million-unit range). Offscreen pruning keeps deeper
        # viewport refinement bounded by the existing point budget.
        if depth >= (48 if screen_bounds else 4) or len(values) >= max_points:
            return
        middle = (left+right)/2
        if middle==left or middle==right: return
        actual = point(middle)
        a, b = values[left], values[right]
        # Skip all-undefined probes and monotone probes well outside the view.
        # Keep a full viewport of margin and still refine turns near reentry.
        if screen_bounds and a is None and b is None and actual is None:
            return
        if screen_bounds and a is not None and b is not None and actual is not None:
            _,_,view_low,view_high=screen_bounds
            margin=view_high-view_low
            monotone=min(a[1],b[1])<=actual[1]<=max(a[1],b[1])
            if monotone and (max(a[1],b[1],actual[1])<view_low-margin or min(a[1],b[1],actual[1])>view_high+margin):
                return
        split = a is None or b is None or actual is None
        if a is not None and b is not None and actual is not None:
            linear = ((a[0]+b[0])/2, (a[1]+b[1])/2)
            span = max(abs(b[0]-a[0]), abs(b[1]-a[1]), 1e-9)
            error = max(abs(actual[0]-linear[0]), abs(actual[1]-linear[1]))/span
            split = error > 0.012 or abs(b[1]-a[1]) > 0.22*max(abs(end-start),1e-9)
            if screen_bounds:
                _,_,view_low,view_high=screen_bounds
                if min(a[1],b[1],actual[1])<=view_high and max(a[1],b[1],actual[1])>=view_low:
                    split=split or abs(actual[1]-linear[1])*800/(view_high-view_low)>.2
        if split:
            values[middle] = actual
            refine(left, middle, depth+1)
            refine(middle, right, depth+1)
    coarse = sorted(values)
    for left, right in zip(coarse, coarse[1:]):
        refine(left, right, 0)
    positions = sorted(values)
    if kind == "cartesian":
        # A long segment may be perfectly linear after simplification. Encode
        # discontinuities explicitly instead of making renderers discard every
        # steep segment (which would also hide straight lines and tangents).
        for left, right in zip(positions, positions[1:]):
            a, b = values[left], values[right]
            if a is None or b is None or (a[1] < 0) == (b[1] < 0) or abs(b[1]-a[1]) <= .22*max(end-start, 1e-9): continue
            lo, hi, low = left, right, a[1]
            tolerance = max(min(abs(a[1]), abs(b[1]))*1e-6, 1e-12)
            for _ in range(48):
                middle = (lo+hi)/2
                actual = point(middle)
                if actual is None: break
                if abs(actual[1]) <= tolerance: break
                if (actual[1] < 0) == (low < 0): lo, low = middle, actual[1]
                else: hi = middle
            else:
                actual = None
            if actual is None: values[(left+right)/2] = None
        positions = sorted(values)
    return [values[at] for at in positions], positions

def graph_sequence(engine, request, trees, start, end):
    require(start >= 0 and within_limit(end,2000), "Sequence range must be between 0 and 2000")
    first, last = math.ceil(start), math.floor(end)
    require(last >= first and within_limit(last-first,1200), "Sequence range is too large")
    n = engine.symbol("n"); engine.bindings["n"] = n
    engine.allow_sequence_calls = True
    expressions = graph_expressions(engine, trees, ("n",))
    names = parameter_names(expressions, {"n"})
    sliders = resolved_parameters(engine, request, expressions, {"n"})
    seed_trees = request.get("initialTrees", [])
    seeds = []
    for tree in seed_trees[:20]:
        value = engine.build(tree)
        numeric = _finite_real(s.N(value, engine.precision))
        require(numeric is not None, "Initial sequence values must be finite real numbers")
        seeds.append(numeric)
    require(expressions, "Enter a sequence rule")
    curves = []
    for curve_index, expression in enumerate(expressions):
        if not expression.atoms(AppliedUndef):
            function = graph_function(expression, (n,), sliders)
            curve = []
            for index in range(last+1):
                try: value = _finite_real(function(index))
                except (TypeError, ValueError, ZeroDivisionError, OverflowError): value = None
                require(value is not None, "Sequence rule did not produce a finite real value")
                if index >= first: curve.append([index, value])
            curves.append(curve)
            continue
        expression = substitute_parameters(expression, sliders)
        function_name = "u" if len(expressions) == 1 else "u%d" % (curve_index+1)
        sequence = {}
        recursive_calls = expression.atoms(AppliedUndef)
        for index, value in enumerate(seeds): sequence[index] = value
        for index in range(last+1):
            if recursive_calls and index in sequence:
                pass
            else:
                current = expression.subs(n, s.Integer(index))
                replacements = {}
                for call in current.atoms(AppliedUndef):
                    name = call.func.__name__
                    require(name in ("u", function_name), "A sequence rule may only refer to its own previous terms")
                    require(len(call.args) == 1 and call.args[0].is_Integer, "Sequence references need integer indices")
                    previous_index = int(call.args[0])
                    require(previous_index < index and previous_index in sequence,
                            "Provide enough initial values for every previous-term reference")
                    replacements[call] = s.Float(sequence[previous_index], engine.precision)
                value = _finite_real(s.N(current.xreplace(replacements), engine.precision))
                require(value is not None, "Sequence rule did not produce a finite real value")
                sequence[index] = value
        curves.append([[index, sequence[index]] for index in range(first, last+1) if index in sequence])
    return {"curves": curves, "discrete": True, "parameters": sorted(names)}

def graph_space(engine,request,trees,start,end):
    from calc_graph3d import space_coordinate_trees,space_curve_samples
    require(1<=len(trees)<=20,"Enter one to twenty 3D curves [x(t),y(t),z(t)]")
    coordinates=[space_coordinate_trees(tree) for tree in trees]
    require(all(coordinates),"Enter a 3D curve [x(t),y(t),z(t)] or C(t)=(x(t),y(t),z(t))")
    t=engine.symbol('t');engine.bindings['t']=t
    flat=graph_expressions(engine,[coordinate for group in coordinates for coordinate in group],('t',))
    names=parameter_names(flat,{'t'});sliders=resolved_parameters(engine,request,flat,{'t'})
    bounds=tuple((float(request.get(low,default[0])),float(request.get(high,default[1]))) for low,high,default in (
        ('xMin','xMax',(-5,5)),('yMin','yMax',(-5,5)),('surfaceZMin','surfaceZMax',(-5,5))))
    require(all(math.isfinite(low) and math.isfinite(high) and high>low for low,high in bounds),'Enter finite increasing ranges')
    curves=[];parameters=[]
    for i in range(0,len(flat),3):
        curve,positions=space_curve_samples(graph_function(tuple(flat[i:i+3]),(t,),sliders),start,end,int(request.get('samples',500)),bounds)
        curves.append(curve);parameters.append(positions)
    zs=[p[2] for curve in curves for p in curve if p]
    return {'surface':[],'spaceCurves':curves,'curveParameters':parameters,'parameters':sorted(names),
            'zMin':min(zs) if zs else -1,'zMax':max(zs) if zs else 1}


def graph_surface(engine, request, trees, xmin, xmax):
    require(len(trees) == 1, "Enter one surface expression z=f(x,y) or F(x,y,z)=0")
    ymin, ymax = float(request.get("surfaceYMin", -3)), float(request.get("surfaceYMax", 3))
    require(math.isfinite(ymin) and math.isfinite(ymax) and ymax > ymin, "Invalid surface y range")
    x, y, z = engine.symbol("x"), engine.symbol("y"), engine.symbol("z")
    engine.bindings.update({"x":x, "y":y, "z":z})
    expression = graph_expressions(engine, trees, ("x", "y", "z"))[0]
    if isinstance(expression,s.Equality):
        implicit=not (expression.lhs==z and not expression.rhs.has(z))
        expression=expression.lhs-expression.rhs if implicit else expression.rhs
    else:
        implicit=isinstance(expression,s.Expr) and expression.has(z)
    require(isinstance(expression,s.Expr),"Enter one surface expression z=f(x,y) or F(x,y,z)=0")
    if implicit:
        from calc_graph3d import implicit_surface_samples
        require(expression!=0,"Equation is true everywhere; enter a surface equation")
        zmin,zmax=float(request.get('surfaceZMin',ymin)),float(request.get('surfaceZMax',ymax))
        require(math.isfinite(zmin) and math.isfinite(zmax) and zmax>zmin,"Invalid surface z range")
        axes={"x","y","z"}
        names=parameter_names([expression],axes)
        sliders=resolved_parameters(engine,request,[expression],axes)
        fn=graph_function(expression,(x,y,z),sliders)
        vertices,triangles,count,normals=implicit_surface_samples(fn,((xmin,xmax),(ymin,ymax),(zmin,zmax)),int(request.get('surfaceSamples',26)))
        return {"surface":[],"surfaceVertices":vertices,"surfaceTriangles":triangles,"surfaceNormals":normals,"implicitSurface":True,
                "zMin":zmin,"zMax":zmax,"surfaceSamples":count,"parameters":sorted(names)}
    names = parameter_names([expression], {"x","y"})
    fn = graph_function(expression, (x,y), resolved_parameters(engine, request, [expression], {"x","y"}))
    count = capped(max(12, int(request.get("surfaceSamples", 26))),96)
    mesh = []
    for row in range(count+1):
        yy = ymin+(ymax-ymin)*row/count
        points = []
        for col in range(count+1):
            xx = xmin+(xmax-xmin)*col/count
            try: z = _finite_real(fn(xx,yy))
            except (TypeError, ValueError, ZeroDivisionError, OverflowError): z = None
            points.append([xx,yy,z] if z is not None else None)
        mesh.append(points)
    values = [point[2] for row in mesh for point in row if point is not None]
    require(values, "Surface has no finite values in this range")
    return {"surface":mesh,"zMin":min(values),"zMax":max(values),"surfaceSamples":count,"parameters":sorted(names)}

def graph_differential(engine, request, trees, start, end):
    require(len(trees) == 1, "Enter one derivative rule dy/dt=f(t,y)")
    t, y = engine.symbol("t"), engine.symbol("y")
    engine.bindings.update({"t":t, "y":y})
    expression = graph_expressions(engine, trees, ("t", "y"))[0]
    names = parameter_names([expression], {"t","y"})
    fn = graph_function(expression, (t,y), resolved_parameters(engine, request, [expression], {"t","y"}))
    ymin, ymax = float(request.get("yMin", -5)), float(request.get("yMax", 5))
    t0 = float(request.get("t0", 0))
    require(math.isfinite(ymin) and math.isfinite(ymax) and ymax > ymin, "Invalid solution y range")
    require(math.isfinite(t0) and start <= t0 <= end, "Initial time must be inside the t range")
    initials = request.get("initialValues", [1])
    require(1 <= len(initials) <= 20, "Enter between one and twenty initial y values")
    def slope(at, value):
        try: return _finite_real(fn(at,value))
        except (TypeError, ValueError, ZeroDivisionError, OverflowError): return None
    curves=[]
    for initial in initials:
        y0 = _finite_real(initial)
        require(y0 is not None, "Initial y values must be finite real numbers")
        def integrate(bound):
            distance = bound-t0
            steps = max(40, min(500, int(240*abs(distance)/(end-start))+40))
            h = distance/steps
            points = [[t0,y0]]
            at, value = t0, y0
            for _ in range(steps):
                k1=slope(at,value)
                k2=slope(at+h/2,value+h*k1/2) if k1 is not None else None
                k3=slope(at+h/2,value+h*k2/2) if k2 is not None else None
                k4=slope(at+h,value+h*k3) if k3 is not None else None
                if None in (k1,k2,k3,k4): break
                value += h*(k1+2*k2+2*k3+k4)/6
                at += h
                if not math.isfinite(value) or abs(value)>1e100: break
                points.append([at,value])
            return points
        left=integrate(start); right=integrate(end)
        curves.append(list(reversed(left[1:]))+[[t0,y0]]+right[1:])
    fields=[]
    nx, ny = 17, 11
    for ix in range(nx):
        at=start+(end-start)*(ix+0.5)/nx
        for iy in range(ny):
            value=ymin+(ymax-ymin)*(iy+0.5)/ny
            dy=slope(at,value)
            if dy is not None: fields.append([at,value,dy])
    return {"curves":curves,"fields":fields,"differential":True,"parameters":sorted(names)}

def integral_fill(expression, variable, a, b):
    """Sample the integration interval independently of the simplified viewport curve."""
    points, _ = adaptive_samples(graph_function(expression,(variable,)),a,b,400,"cartesian")
    polygons, segment = [], []
    def flush():
        if len(segment)>1: polygons.append([[segment[0][0],0.0],*segment,[segment[-1][0],0.0]])
        segment.clear()
    for point in points:
        if point is None: flush()
        else: segment.append(point)
    flush()
    return polygons

def polynomial_real_roots(expression, variable):
    """Isolate real roots numerically without constructing quartic radical formulas.

    Exact rational coefficients retain repeated/tangent roots and avoid nroots'
    convergence failures at multiple roots. None means a non-polynomial target.
    """
    try:
        numerator = s.together(expression).as_numer_denom()[0]
        polynomial = s.Poly(numerator, variable)
        if polynomial.degree()>32 or any(coefficient.free_symbols for coefficient in polynomial.all_coeffs()): return None
        require(not polynomial.is_zero, "Select two different curves")
        return [float((low+high)/2) for (low,high),_ in polynomial.to_exact().intervals(eps=s.Rational(1,10**20))]
    except (s.PolynomialError, s.polys.polyerrors.DomainError, NotImplementedError): return None

def implicit_analysis(engine, request, curves, x, y, numeric, zeroes, tangent_point):
    """Analyze all branches for searches; use the traced point for a local branch."""
    selected, other = int(request.get("selected", 0)), int(request.get("other", 1))
    action = request.get("analysis", "root")
    a, b = float(request.get("a", -10)), float(request.get("b", 10))
    function, residual = curves[selected]
    residual = _square_free_graph(residual, x, y)
    def finite(expression):
        try: return _finite_real(expression.evalf(engine.precision))
        except (TypeError, ValueError, OverflowError): return None
    def solutions(expression, variable):
        try: return s.solve(expression, variable)
        except (NotImplementedError, ValueError, TypeError): return []
    def valid(px, py, expressions=(residual,)):
        for expression in expressions:
            value = finite(expression.subs({x: px, y: py}))
            if value is None or abs(value)>1e-6: return False
        return True
    def collect(points):
        found = []
        for point in sorted(points):
            if all(math.hypot(point[0]-old[0], point[1]-old[1])>1e-6 for old in found): found.append(point)
        return {"analysis": action, "points": found[:80], "count": min(80,len(found)), "truncated": len(found)>80, "implicit": True}
    if action == "root":
        target = residual.subs(y, 0)
        require(target != 0, "The curve lies on the x axis; roots are not isolated")
        positions = zeroes(numeric(target, x))
        positions += [value for value in (finite(root) for root in solutions(target, x)) if value is not None and a-1e-9 <= value <= b+1e-9]
        return collect([[px, 0.0] for px in positions if valid(px, 0)])
    if action == "yintercept":
        target = residual.subs(x, 0)
        require(target != 0, "The curve lies on the y axis; intercepts are not isolated")
        ymin, ymax = float(request.get("yMin",-5)),float(request.get("yMax",5))
        require(math.isfinite(ymin) and math.isfinite(ymax) and ymin < ymax, "Invalid y range")
        # Reuse the numerical root search on the y viewport, then include exact roots.
        search = graph_analysis(engine,{**request,"trees":[],"selected":0,"analysis":"root","a":ymin,"b":ymax},(target.subs(y,x),))
        positions = [point[0] for point in search["points"]]
        positions += [value for value in (finite(root) for root in solutions(target,y)) if value is not None]
        return collect([[0.0,py] for py in positions if valid(0,py)])
    if action == "intersection":
        target = curves[other][1]
        # Substitute a known function first: a parabola against a circle becomes
        # one quartic whose real roots can be isolated quickly at current sliders.
        explicit = function if function is not None else curves[other][0]
        if explicit is not None:
            reduced = (target if function is not None else residual).subs(y,explicit)
            positions = polynomial_real_roots(reduced,x)
            if positions is not None:
                value=numeric(explicit,x)
                return collect([[px,py] for px in positions for py in [value(px)] if py is not None and a-1e-9 <= px <= b+1e-9 and valid(px,py,(residual,target))])
        require(residual-target != 0, "Select two different curves")
        try: pairs = s.solve((residual, target), (x, y), dict=True)
        except (NotImplementedError, ValueError, TypeError): pairs = []
        points = []
        for pair in pairs:
            px, py = finite(pair.get(x,s.nan)), finite(pair.get(y,s.nan))
            if px is not None and py is not None and a-1e-9 <= px <= b+1e-9 and valid(px,py,(residual,target)): points.append([px,py])
        if points: return collect(points)
        # Numerical branch searches also cover transcendental intersections.
        first = (function,) if function is not None else cartesian_branches(residual, y)
        second = (curves[other][0],) if curves[other][0] is not None else cartesian_branches(target, y)
        require(first and second or pairs, "These curves cannot be analyzed as y=f(x) branches")
        for left in first:
            for right in second:
                response = graph_analysis(engine, {**request,"selected":0,"other":1}, (left,right))
                points.extend(point for point in response["points"] if valid(*point,(residual,target)))
        return collect(points)
    if action in ("derivative", "tangent"):
        ordinates = [value for value in (finite(root) for root in solutions(residual.subs(x,s.Float(a)), y)) if value is not None and valid(a,value)]
        require(ordinates, "Curve is undefined at this x coordinate")
        hint = request.get("tracePoint")
        require(len(ordinates)==1 or isinstance(hint,(list,tuple)) and len(hint)==2, "Tap a point on the curve to choose a branch")
        py = min(ordinates, key=lambda value:abs(value-float(hint[1]))) if hint else ordinates[0]
        horizontal, vertical = finite(s.diff(residual,x).subs({x:a,y:py})), finite(s.diff(residual,y).subs({x:a,y:py}))
        require(horizontal is not None and vertical is not None and math.hypot(horizontal,vertical)>1e-12, "Tangent is undefined at this point")
        slope = -horizontal/vertical if abs(vertical)>1e-12 else None
        if action == "tangent": return {**tangent_point(a,py,slope,(vertical,-horizontal)),"implicit":True}
        payload = {"analysis":action,"points":[[a,py]],"implicit":True}
        if slope is None: payload["vertical"]=True
        else: payload["value"]=slope
        return payload
    branches = (function,) if function is not None else cartesian_branches(residual, y)
    require(branches, "This curve cannot be analyzed as y=f(x) branches")
    if action in ("integral", "arclength"):
        hint = request.get("tracePoint")
        if len(branches)>1:
            require(isinstance(hint,(list,tuple)) and len(hint)==2, "Tap a point on the curve to choose a branch")
            candidates = [(abs(value-float(hint[1])),branch) for branch in branches for value in [finite(branch.subs(x,float(hint[0])))] if value is not None]
            require(candidates, "Curve is undefined at this x coordinate")
            branches = (min(candidates,key=lambda pair:pair[0])[1],)
        return {**graph_analysis(engine,{**request,"selected":0},branches),"implicit":True}
    points = []
    for branch in branches:
        try:
            response = graph_analysis(engine,{**request,"selected":0},(branch,))
            points.extend(point for point in response["points"] if valid(*point))
        except MathError:
            # A branch may have no real values inside the requested interval.
            continue
    if action in ("minimum", "maximum"):
        require(points, "No finite values in this range")
        limit = (min if action=="minimum" else max)(py for _,py in points)
        points = [point for point in points if abs(point[1]-limit)<=max(1e-8,abs(limit)*1e-8)]
    return collect(points)

def graph_analysis(engine, request, _expressions=None, _derivative_primitive=None):
    bind_graph_parameters(engine,request)
    kind = request.get("graphKind","cartesian")
    trees = request.get("trees",[])
    selected_order = request.get("selectedDerivativeOrder", 0)
    other_order = request.get("otherDerivativeOrder", 0) if request.get("analysis") == "intersection" else 0
    require(isinstance(selected_order,int) and selected_order in (0,1,2) and isinstance(other_order,int) and other_order in (0,1,2), "Invalid derivative order")
    if _expressions is None and (selected_order or other_order):
        require(kind == "cartesian", "Derivative analysis requires Cartesian curves")
        selected, other = int(request.get("selected",0)), int(request.get("other",1))
        require(0 <= selected < len(trees), "Select a function")
        if request.get("analysis") == "intersection": require(0 <= other < len(trees), "Select two different functions")
        x, y = engine.symbol("x"), engine.symbol("y")
        engine.bindings.update({"x":x,"y":y})
        raw = list(graph_expressions(engine,trees,("x","y")))
        targets = {}
        primitives = {}
        def target(index, order):
            if not order: return index
            key = (index,order)
            if key not in targets:
                function, residual = cartesian_curve(raw[index],x,y)
                branches = (function,) if function is not None else cartesian_branches(residual,y)
                require(branches, "Derivative graph requires branches expressible as y=f(x)")
                derivative_primitives = {s.diff(branch,x,order):s.diff(branch,x,order-1) for branch in branches}
                derivatives = tuple(derivative_primitives)
                if index == selected and order == selected_order and len(derivatives)>1 and request.get("analysis") in ("derivative","tangent","integral","arclength"):
                    hint = request.get("tracePoint")
                    require(isinstance(hint,(list,tuple)) and len(hint)==2, "Tap a point on the curve to choose a branch")
                    hint_x,hint_y = map(float,hint)
                    require(math.isfinite(hint_x) and math.isfinite(hint_y), "Invalid trace point")
                    sliders = {symbol:value for symbol,value in resolved_parameters(engine,request,raw,{"x","y"}).items() if symbol not in (x,y)}
                    candidates = []
                    for branch in derivatives:
                        value = _finite_real(substitute_parameters(branch,sliders).subs(x,hint_x).evalf(engine.precision))
                        if value is not None: candidates.append((abs(value-hint_y),branch))
                    candidates.sort(key=lambda pair:pair[0])
                    require(candidates, "Curve is undefined at this x coordinate")
                    require(len(candidates)==1 or candidates[1][0]-candidates[0][0]>1e-8, "Tap a point on the curve to choose a branch")
                    derivatives = (candidates[0][1],)
                # A product retains every y branch for intersection/search analysis;
                # local tangents and integrals use the existing traced-branch rule.
                expression = derivatives[0] if len(derivatives)==1 else s.prod(y-branch for branch in derivatives)
                targets[key] = len(raw)
                raw.append(expression)
                if len(derivatives)==1: primitives[targets[key]] = derivative_primitives[derivatives[0]]
            return targets[key]
        resolved = {key:value for key,value in request.items() if key not in ("selectedDerivativeOrder","otherDerivativeOrder")}
        resolved["selected"] = target(selected,selected_order)
        if request.get("analysis") == "intersection": resolved["other"] = target(other,other_order)
        response = graph_analysis(engine,resolved,tuple(raw),primitives.get(resolved["selected"]))
        response.update(selected=selected,selectedDerivativeOrder=selected_order)
        if request.get("analysis") == "intersection": response.update(other=other,otherDerivativeOrder=other_order)
        return response
    require(kind in ("cartesian","parametric","polar"), "Analysis supports Cartesian, parametric and polar curves")
    selected = int(request.get("selected", 0)); other = int(request.get("other", 1))
    size = len(trees) if _expressions is None else len(_expressions)
    require(0 <= selected < size, "Select a function")
    action = request.get("analysis", "root")
    require(action in ("root","yintercept","intersection","minimum","maximum","inflection","derivative","tangent","integral","arclength"), "Unknown graph analysis")
    require(action != "intersection" or kind == "cartesian", "Intersections need two Cartesian functions")
    if action == "intersection": require(0 <= other < size and other != selected, "Select two different functions")
    fixed_intercept = action == "yintercept" and kind == "cartesian"
    a = 0.0 if fixed_intercept else float(request.get("a", -10)); b = a if fixed_intercept else float(request.get("b", 10))
    singled = action in ("derivative", "tangent") or fixed_intercept
    require(math.isfinite(a) and math.isfinite(b) and (singled or a < b) and within_limit(abs(b-a),1e9), "Invalid analysis range")
    def numeric(expr, variable):
        raw = s.lambdify(variable, expr, modules="math", cse=True, docstring_limit=0)
        def value(at):
            try:
                result = float(raw(at))
                return result if math.isfinite(result) else None
            except (TypeError, ValueError, ZeroDivisionError, OverflowError): return None
        return value
    def zeroes(fn):
        count = 1200
        xs = [a+(b-a)*i/count for i in range(count+1)]
        ys = [fn(at) for at in xs]
        roots = []
        def add(at):
            if not roots or all(abs(at-old)>max(1e-8,abs(b-a)*1e-6) for old in roots): roots.append(at)
        for i in range(count):
            left,right = xs[i],xs[i+1]; yl,yr = ys[i],ys[i+1]
            if yl is None or yr is None: continue
            if abs(yl) < 1e-9: add(left)
            if yl*yr < 0:
                lo,hi = left,right; low = yl
                for _ in range(55):
                    mid = (lo+hi)/2; middle = fn(mid)
                    if middle is None: break
                    if low*middle <= 0: hi=mid
                    else: lo=mid; low=middle
                root=(lo+hi)/2; residual=fn(root)
                if residual is not None and abs(residual) < 1e-6: add(root)
        if ys[-1] is not None and abs(ys[-1]) < 1e-9: add(b)
        return sorted(roots)
    def tangent_point(px, py, slope, direction=None):
        xmin=float(request.get("xMin",-10)); xmax=float(request.get("xMax",10))
        ymin=float(request.get("yMin",-10)); ymax=float(request.get("yMax",10))
        if not all(math.isfinite(value) for value in (xmin,xmax,ymin,ymax)) or xmax <= xmin or ymax <= ymin: xmin,xmax,ymin,ymax = -10,10,-10,10
        payload = {"analysis":action,"points":[[px,py]]}
        if slope is None: payload["vertical"]=True
        else: payload["value"]=slope
        if direction is not None:
            dx,dy = direction
            speed = math.hypot(dx,dy)
            if speed > 1e-12:
                length = 1.6*max(xmax-xmin,ymax-ymin)
                payload["line"] = [[px-dx/speed*length,py-dy/speed*length],[px+dx/speed*length,py+dy/speed*length]]
        elif slope is None or abs(slope) > 1e6:
            payload["line"] = [[px,ymin],[px,ymax]]
        else:
            payload["line"] = [[xmin,py+slope*(xmin-px)],[xmax,py+slope*(xmax-px)]]
        return payload
    if kind == "cartesian":
        x, y = engine.symbol("x"), engine.symbol("y")
        engine.bindings.update({"x":x,"y":y})
        raw = graph_expressions(engine, trees, ("x","y")) if _expressions is None else _expressions
        sliders = {symbol:value for symbol,value in resolved_parameters(engine,request,raw,{"x","y"}).items() if symbol not in (x,y)}
        curves = [cartesian_curve(substitute_parameters(expression,sliders),x,y) for expression in raw]
        if curves[selected][0] is None or action=="intersection" and curves[other][0] is None:
            return implicit_analysis(engine,request,curves,x,y,numeric,zeroes,tangent_point)
        expressions = [function for function,_ in curves]
        expression = expressions[selected]
        target = expression-expressions[other] if action == "intersection" else expression
        if action == "intersection": require(target != 0, "The curves coincide; intersections are not isolated")
        if action == "root": require(expression != 0, "The curve lies on the x axis; roots are not isolated")
        value = numeric(expression, x)
        if action == "yintercept":
            intercept = value(0)
            points = [[0.0,intercept]] if intercept is not None else []
            return {"analysis":action,"points":points,"count":len(points)}
        if action == "derivative":
            derivative = numeric(s.diff(expression, x), x)(a)
            require(derivative is not None and value(a) is not None, "Derivative is undefined at this point")
            return {"analysis":action,"points":[[a,value(a)]],"value":derivative}
        if action == "tangent":
            require(value(a) is not None, "Tangent is undefined at this point")
            return tangent_point(a, value(a), numeric(s.diff(expression, x), x)(a))
        if action == "inflection":
            second = numeric(s.diff(expression, x, 2), x)
            step = max(1e-7,(b-a)*1e-4)
            positions = []
            for at in zeroes(second):
                left,right = second(at-step),second(at+step)
                if left is not None and right is not None and left*right < 0: positions.append(at)
            points = [[at,value(at)] for at in positions if value(at) is not None][:80]
            return {"analysis":action,"points":points,"count":len(points),"truncated":len(positions)>80}
        if action == "arclength":
            slope = s.diff(expression, x)
            result = numeric_integral(s.sqrt(1+slope**2), x, a, b, engine.precision)
            require(result.is_real and result.is_finite, "Numerical convergence failed")
            return {"analysis":action,"points":[],"value":float(result)}
        if action == "integral":
            result = None
            if _derivative_primitive is not None:
                from sympy.calculus.util import continuous_domain
                primitive = substitute_parameters(_derivative_primitive,sliders)
                continuous = primitive_continuous = None
                try:
                    continuous = (s.Interval.open(a,b)-continuous_domain(expression,x,s.S.Reals)).is_empty
                    primitive_continuous = (s.Interval(a,b)-continuous_domain(primitive,x,s.S.Reals)).is_empty
                except (NotImplementedError, ValueError, TypeError): pass
                require(continuous is not False, "Integral crosses a discontinuity; choose a continuous interval")
                if continuous is True and primitive_continuous is True:
                    # The stored primitive avoids relative-accuracy failure for
                    # cancelling integrals, after checking for domain breaks.
                    exact = primitive.xreplace({value:s.Rational(value) for value in primitive.atoms(s.Float)})
                    result = (exact.subs(x,s.Rational(b))-exact.subs(x,s.Rational(a))).evalf(engine.precision,strict=True)
            if result is None: result = numeric_integral(expression, x, a, b, engine.precision)
            require(result.is_real and result.is_finite, "Numerical convergence failed")
            return {"analysis":action,"points":[],"value":float(result),"integralFill":integral_fill(expression,x,a,b)}
        tested = numeric(target, x)
        if action in ("root", "intersection"):
            positions = zeroes(tested)
            # A tangent intersection has no sign change. Its derivative identifies a zero minimum.
            try:
                for at in zeroes(numeric(s.diff(target, x), x)):
                    residual=tested(at)
                    if residual is not None and abs(residual) < 1e-7 and all(abs(at-old)>max(1e-8,abs(b-a)*1e-6) for old in positions): positions.append(at)
            except (TypeError,ValueError): pass
            positions.sort()
        else:
            positions = [a,b]
            try: positions += zeroes(numeric(s.diff(expression, x), x))
            except (TypeError,ValueError): pass
            entries = [(at,value(at)) for at in positions]
            entries = [(at,y) for at,y in entries if y is not None]
            require(entries, "No finite values in this range")
            limit = (min if action == "minimum" else max)(y for _,y in entries)
            positions = [at for at,y in entries if abs(y-limit) <= max(1e-8,abs(limit)*1e-8)]
        points = [[at,value(at)] for at in positions if value(at) is not None][:80]
        return {"analysis":action,"points":points,"count":len(points),"truncated":len(positions)>80}
    variable = engine.symbol(request.get("variable","t")); engine.bindings[str(variable)] = variable
    raw = engine.build(trees[selected])
    sliders = resolved_parameters(engine, request, [raw], {str(variable)})
    if kind == "polar":
        radius = substitute_parameters(raw, sliders)
        require(isinstance(radius, s.Expr), "Enter a polar radius r(t)")
        first, second = radius*s.cos(variable), radius*s.sin(variable)
    else:
        pair = substitute_parameters(raw, sliders)
        require(isinstance(pair,(list,tuple)) and len(pair)==2, "Parametric curves are [x(t), y(t)] pairs")
        first, second = pair
    dfirst, dsecond = s.diff(first, variable), s.diff(second, variable)
    xvalue, yvalue = numeric(first, variable), numeric(second, variable)
    dxvalue, dyvalue = numeric(dfirst, variable), numeric(dsecond, variable)
    def point(at):
        px,py = xvalue(at),yvalue(at)
        return [px,py] if px is not None and py is not None else None
    if action == "root":
        points = [point(at) for at in zeroes(yvalue)]
    elif action == "yintercept":
        require(first != 0, "The curve lies on the y axis; intercepts are not isolated")
        positions = zeroes(xvalue)
        for at in zeroes(dxvalue):
            value = xvalue(at)
            if value is not None and abs(value) < 1e-7 and all(abs(at-old)>1e-6 for old in positions): positions.append(at)
        points = [[0.0, yvalue(at)] for at in sorted(positions) if yvalue(at) is not None]
        points = [item for i,item in enumerate(points) if all(abs(item[1]-old[1])>1e-6 for old in points[:i])]
    elif action in ("minimum", "maximum"):
        target = radius if kind == "polar" else second
        target_value = numeric(target, variable)
        positions = [a,b]
        try: positions += zeroes(numeric(s.diff(target, variable), variable))
        except (TypeError,ValueError): pass
        entries = [(at,target_value(at)) for at in positions]
        entries = [(at,y) for at,y in entries if y is not None]
        require(entries, "No finite values in this range")
        limit = (min if action == "minimum" else max)(y for _,y in entries)
        points = [point(at) for at,y in entries if abs(y-limit) <= max(1e-8,abs(limit)*1e-8)]
    elif action == "inflection":
        curvature = dfirst*s.diff(dsecond, variable)-dsecond*s.diff(dfirst, variable)
        points = [point(at) for at in zeroes(numeric(curvature, variable))]
    elif action in ("derivative", "tangent"):
        current = point(a)
        require(current is not None, "Curve is undefined at this parameter")
        horizontal, vertical = dxvalue(a), dyvalue(a)
        slope = None if horizontal is None or vertical is None or abs(horizontal) < 1e-12 else vertical/horizontal
        if action == "derivative":
            payload = {"analysis":action,"points":[current]}
            if slope is None: payload["vertical"]=True
            else: payload["value"]=slope
            return payload
        direction = None if horizontal is None or vertical is None else (horizontal,vertical)
        return tangent_point(current[0], current[1], slope, direction)
    elif action == "integral":
        integrand = radius**2/2 if kind == "polar" else second*dfirst
        result = numeric_integral(integrand, variable, a, b, engine.precision)
        require(result.is_real and result.is_finite, "Numerical convergence failed")
        return {"analysis":action,"points":[],"value":float(result)}
    else:
        result = numeric_integral(s.sqrt(dfirst**2+dsecond**2), variable, a, b, engine.precision)
        require(result.is_real and result.is_finite, "Numerical convergence failed")
        return {"analysis":action,"points":[],"value":float(result)}
    found = [item for item in points if item is not None]
    points = found[:80]
    return {"analysis":action,"points":points,"count":len(points),"truncated":len(found)>80}
