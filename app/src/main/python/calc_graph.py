"""Graph sampling, parameters, shading, and curve analysis."""
import math
from functools import lru_cache
import sympy as s
from sympy.core.function import AppliedUndef
from calc_shared import MathError, require
from calc_display import readable


@lru_cache(maxsize=64)
def _compiled_graph(axes, expression):
    """Bounded cache of immutable symbolic programs, never engine/request state."""
    return s.lambdify(axes, expression, modules="math", cse=True, docstring_limit=0)


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
    count=min(600,max(120,int(request.get("samples",240))))
    x=next(iter(value.free_symbols),engine.symbol("x"))
    function=s.lambdify(x,value,modules="math",cse=True,docstring_limit=0)
    curve=[]
    for index in range(count+1):
        at=start+(end-start)*index/count
        try: y=float(function(at))
        except (TypeError,ValueError,ZeroDivisionError,OverflowError): continue
        if math.isfinite(y) and abs(y)<1e100: curve.append([at,y])
    return curve

def graph(engine, request):
    kind = request.get("graphKind","cartesian")
    trees = request.get("trees",[])
    start,end = float(request.get("min",-10)),float(request.get("max",10))
    require(math.isfinite(start) and math.isfinite(end) and end>start,"Invalid graph range")
    if kind == "implicit":
        return graph_implicit(engine, request, trees, start, end)
    if kind == "sequence":
        return graph_sequence(engine, request, trees, start, end)
    if kind == "surface":
        return graph_surface(engine, request, trees, start, end)
    if kind == "differential":
        return graph_differential(engine, request, trees, start, end)
    var = engine.symbol(request.get("variable","x")); engine.bindings[str(var)] = var
    expressions = [engine.build(t) for t in trees]
    shade_items = (request.get("shadings") or []) if kind == "cartesian" else []
    shade_expressions = [[engine.build(t) for t in (item.get("trees") or [])] for item in shade_items]
    all_expressions = expressions+[expression for group in shade_expressions for expression in group]
    names = parameter_names(all_expressions, {str(var)})
    sliders = resolved_parameters(engine, request, all_expressions, {str(var)})
    count = min(1600,max(100,int(request.get("samples",500))))
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

def graph_implicit(engine, request, trees, xmin, xmax):
    """Contour F(x,y)=0 with finite, independently separated line segments."""
    ymin, ymax = float(request.get("yMin", -3)), float(request.get("yMax", 3))
    require(math.isfinite(ymin) and math.isfinite(ymax) and ymax > ymin, "Invalid implicit y range")
    require(1 <= len(trees) <= 6, "Enter one to six implicit equations")
    x, y = engine.symbol("x"), engine.symbol("y")
    engine.bindings.update({"x": x, "y": y})
    expressions = []
    for tree in trees:
        expression = engine.build(tree)
        if isinstance(expression, s.Equality):
            expression = expression.lhs-expression.rhs
        require(isinstance(expression, s.Expr), "Enter an equation F(x,y)=0")
        expressions.append(expression)
    names = parameter_names(expressions, {"x", "y"})
    sliders = resolved_parameters(engine, request, expressions, {"x", "y"})
    # Coordinate names remain axes even if a previous graph stored slider values for them.
    sliders = {key: value for key, value in sliders.items() if key not in (x, y)}
    count = min(240, max(80, int(math.sqrt(max(1, int(request.get("samples", 500))))*8)))
    curves = []
    for expression in expressions:
        expression = substitute_parameters(expression, sliders)
        require(expression != 0, "Equation is true everywhere; enter a curve equation")
        # Repeated polynomial factors have the same zero set but no sign change.
        if expression.is_polynomial(x, y):
            polynomial = s.Poly(expression, x, y)
            if polynomial.total_degree() <= 12:
                expression = polynomial.sqf_part().as_expr()
        function = graph_function(expression, (x, y))
        curves.append(implicit_samples(function, xmin, xmax, ymin, ymax, count))
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
    count = min(1200,max(200,int(request.get("samples",500))))
    def endpoint(item, key, default):
        tree = item.get(key)
        if tree is None: return default
        number = _finite_real(s.N(substitute_parameters(engine.build(tree),sliders),engine.precision))
        require(number is not None,"Shading intervals must be finite numbers")
        return number
    def samples(expression, a, b):
        function = graph_function(expression, (x,), sliders)
        points = []
        for index in range(count+1):
            at = a+(b-a)*index/count
            value = _finite_real(function(at))
            points.append([at,value] if value is not None else None)
        return points
    shadings = []
    for item,group in zip(items,groups):
        mode = item.get("mode")
        a,b = endpoint(item,"a",xmin),endpoint(item,"b",xmax)
        require(a < b,"Shading intervals must be increasing")
        if mode == "halfplane":
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
            runs=[]; run=[]
            for index in range(len(first)):
                low = None if first[index] is None else first[index][1]
                high = None if second[index] is None else second[index][1]
                if low is None or high is None:
                    if len(run) >= 2: runs.append(run)
                    run=[]
                    continue
                run.append([first[index][0],clamp(low),clamp(high)])
            if len(run) >= 2: runs.append(run)
            polygons = []
            for segment in runs:
                top = [[at,max(low,high)] for at,low,high in segment]
                bottom = [[at,min(low,high)] for at,low,high in segment]
                polygons.append(top+list(reversed(bottom)))
            shadings.append({"mode":mode,"boundary":[first,second if len(group) == 2 else None],"fill":polygons})
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


def adaptive_samples(function, start, end, base_count, kind="cartesian"):
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
    intervals = min(512, max(100, base_count))
    values = {start + (end-start)*i/intervals: None for i in range(intervals+1)}
    for at in values:
        values[at] = point(at)
    max_points = min(1800, max(base_count+1, 1200))
    def refine(left, right, depth):
        if depth >= 4 or len(values) >= max_points:
            return
        middle = (left+right)/2
        actual = point(middle)
        a, b = values[left], values[right]
        split = a is None or b is None or actual is None
        if a is not None and b is not None and actual is not None:
            linear = ((a[0]+b[0])/2, (a[1]+b[1])/2)
            span = max(abs(b[0]-a[0]), abs(b[1]-a[1]), 1e-9)
            error = max(abs(actual[0]-linear[0]), abs(actual[1]-linear[1]))/span
            split = error > 0.012 or abs(b[1]-a[1]) > 0.22*max(abs(end-start),1e-9)
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
            for _ in range(20):
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
    require(start >= 0 and end <= 2000, "Sequence range must be between 0 and 2000")
    first, last = math.ceil(start), math.floor(end)
    require(last >= first and last-first <= 1200, "Sequence range is too large")
    n = engine.symbol("n"); engine.bindings["n"] = n
    engine.allow_sequence_calls = True
    expressions = [engine.build(tree) for tree in trees]
    names = parameter_names(expressions, {"n"})
    sliders = resolved_parameters(engine, request, expressions, {"n"})
    expressions = [substitute_parameters(expression,sliders) for expression in expressions]
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

def graph_surface(engine, request, trees, xmin, xmax):
    require(len(trees) == 1, "Enter one surface expression z=f(x,y)")
    ymin, ymax = float(request.get("surfaceYMin", -3)), float(request.get("surfaceYMax", 3))
    require(math.isfinite(ymin) and math.isfinite(ymax) and ymax > ymin, "Invalid surface y range")
    x, y = engine.symbol("x"), engine.symbol("y")
    engine.bindings.update({"x":x, "y":y})
    expression = engine.build(trees[0])
    names = parameter_names([expression], {"x","y"})
    fn = graph_function(expression, (x,y), resolved_parameters(engine, request, [expression], {"x","y"}))
    count = min(96, max(12, int(request.get("surfaceSamples", 26))))
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
    expression = engine.build(trees[0])
    names = parameter_names([expression], {"t","y"})
    fn = graph_function(expression, (t,y), resolved_parameters(engine, request, [expression], {"t","y"}))
    ymin, ymax = float(request.get("yMin", -5)), float(request.get("yMax", 5))
    t0 = float(request.get("t0", 0))
    require(math.isfinite(ymin) and math.isfinite(ymax) and ymax > ymin, "Invalid solution y range")
    require(math.isfinite(t0) and start <= t0 <= end, "Initial time must be inside the t range")
    initials = request.get("initialValues", [1])
    require(1 <= len(initials) <= 6, "Enter between one and six initial y values")
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

def graph_analysis(engine, request):
    kind = request.get("graphKind","cartesian")
    trees = request.get("trees",[])
    require(kind in ("cartesian","parametric","polar"), "Analysis supports Cartesian, parametric and polar curves")
    selected = int(request.get("selected", 0)); other = int(request.get("other", 1))
    require(0 <= selected < len(trees), "Select a function")
    action = request.get("analysis", "root")
    require(action in ("root","intersection","minimum","maximum","inflection","derivative","tangent","integral","arclength"), "Unknown graph analysis")
    require(action != "intersection" or kind == "cartesian", "Intersections need two Cartesian functions")
    if action == "intersection": require(0 <= other < len(trees) and other != selected, "Select two different functions")
    a = float(request.get("a", -10)); b = float(request.get("b", 10))
    singled = action in ("derivative", "tangent")
    require(math.isfinite(a) and math.isfinite(b) and (singled or a < b) and abs(b-a) <= 1e9, "Invalid analysis range")
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
        x = engine.symbol("x"); engine.bindings["x"] = x
        raw = [engine.build(tree) for tree in trees]
        sliders = resolved_parameters(engine, request, raw, {"x"})
        expressions = [substitute_parameters(expression, sliders) for expression in raw]
        require(all(isinstance(expression, s.Expr) for expression in expressions), "Enter Cartesian functions")
        expression = expressions[selected]
        target = expression-expressions[other] if action == "intersection" else expression
        value = numeric(expression, x)
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
            result = s.Integral(s.sqrt(1+slope**2), (x, s.Float(a), s.Float(b))).evalf(engine.precision, strict=True)
            require(result.is_real and result.is_finite, "Numerical convergence failed")
            return {"analysis":action,"points":[],"value":float(result)}
        if action == "integral":
            result = s.Integral(expression, (x, s.Float(a), s.Float(b))).evalf(engine.precision, strict=True)
            require(result.is_real and result.is_finite, "Numerical convergence failed")
            return {"analysis":action,"points":[],"value":float(result)}
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
        result = s.Integral(integrand, (variable, s.Float(a), s.Float(b))).evalf(engine.precision, strict=True)
        require(result.is_real and result.is_finite, "Numerical convergence failed")
        return {"analysis":action,"points":[],"value":float(result)}
    else:
        result = s.Integral(s.sqrt(dfirst**2+dsecond**2), (variable, s.Float(a), s.Float(b))).evalf(engine.precision, strict=True)
        require(result.is_real and result.is_finite, "Numerical convergence failed")
        return {"analysis":action,"points":[],"value":float(result)}
    found = [item for item in points if item is not None]
    points = found[:80]
    return {"analysis":action,"points":points,"count":len(points),"truncated":len(found)>80}
