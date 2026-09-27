"""Graph sampling, parameters, shading, and curve analysis."""
import math
import sympy as s
from sympy.core.function import AppliedUndef
from calc_shared import MathError, require

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
    for expression in expressions:
        function=s.lambdify(var,substitute_parameters(expression,sliders),modules="math",cse=True,docstring_limit=0)
        samples = adaptive_samples(function, start, end, count, kind)
        curves.append(samples)
    result = {"curves":curves,"parameters":sorted(names)}
    if kind == "cartesian" and shade_items:
        result["shadings"] = graph_shading(engine, request, shade_items, shade_expressions, sliders, start, end)
    return result

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
        function = s.lambdify(x,substitute_parameters(expression,sliders),modules="math",cse=True,docstring_limit=0)
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
    return [values[at] for at in sorted(values)]

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
    expression = substitute_parameters(expression, resolved_parameters(engine, request, [expression], {"x","y"}))
    fn = s.lambdify((x,y), expression, modules="math", cse=True, docstring_limit=0)
    count = min(40, max(12, int(request.get("surfaceSamples", 26))))
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
    expression = substitute_parameters(expression, resolved_parameters(engine, request, [expression], {"t","y"}))
    fn = s.lambdify((t,y), expression, modules="math", cse=True, docstring_limit=0)
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
