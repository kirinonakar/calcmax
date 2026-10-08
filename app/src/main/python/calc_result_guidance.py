"""Actionable, bounded guidance without claiming a numerical search is complete."""
import sympy as s
from sympy.calculus.util import continuous_domain
from calc_display import display_tree, readable, display_rounded

UNRESOLVED = "A complete symbolic solution was not found."
PARTIAL = "The known roots are partial results. Other real or complex roots may exist. Use numerical solving on a continuous interval or try a different starting value."
NUMERICAL_PARTIAL = "Automatic real-root search interval: [−10, 10]. Displayed roots are approximate partial results; roots outside this range, missed roots and complex roots may exist. Use nsolve to search another interval."
INTEGRAL = "An antiderivative was not found. This does not prove that no closed form exists. For a numerical value, supply a finite integration interval."


def automatic_real_roots(engine, expr, variable, domain):
    """Use graph candidates, then refine and check in the original domain."""
    from calc_evaluator import Engine
    from calc_graph import graph_analysis
    if domain == s.S.EmptySet: return []
    # Remove a constant scale so the graph's binary64 thresholds do not
    # mistake a very small nonzero function for a row of roots.
    coefficient, target = expr.as_independent(variable, as_Add=False)
    if coefficient == 0: return []
    real_variable = s.Dummy('real_root_argument', real=True)
    if target.xreplace({variable:real_variable}).is_zero is False: return []
    graph_engine = Engine({"precision": engine.precision})
    graph_variable = graph_engine.symbol("x")
    candidates = graph_analysis(graph_engine, {"analysis":"root", "a":-10, "b":10},
                                (target.xreplace({variable:graph_variable}),))["points"]
    roots = []
    precision = engine.precision+15
    tolerance = s.Float(10, precision)**(-engine.precision)
    for candidate, _ in candidates[:16]:
        seed = s.Rational(str(candidate))
        try:
            if target.subs(variable, seed) == 0:
                root = seed
            else:
                root = s.nsolve(target, variable, seed, prec=precision, tol=tolerance**2,
                                maxsteps=100, verify=True)
            if root.is_real is not True or root.is_finite is not True or not -10 <= root <= 10:
                continue
            if domain.contains(root) != s.true: continue
            residual = abs(target.subs(variable, root).evalf(precision))
            if residual.is_finite is not True or residual > tolerance: continue
            if target.is_Add:
                scale = sum(abs(term.subs(variable,root).evalf(precision)) for term in target.args)
                if scale != 0 and residual > tolerance*scale: continue
            if any(abs(root-old).evalf(precision) < s.Float('1e-7') for old in roots): continue
            roots.append(s.N(root, engine.precision) if not root.is_Rational else root)
        except (ValueError, TypeError, NotImplementedError, ZeroDivisionError):
            continue
    return sorted(roots, key=lambda root: float(root))


def result_guidance(engine, method, values, answer):
    if method == "solve" and getattr(answer, "has", lambda *_: False)(s.ConditionSet):
        equation, variable = values[:2]
        if isinstance(equation, list) or not isinstance(variable, s.Symbol):
            return {"status":"unresolved_equation", "message":UNRESOLVED, "detail":PARTIAL, "suggestions":[]}
        expr = equation.lhs-equation.rhs if isinstance(equation, s.Equality) else equation
        guidance = {"status":"unresolved_equation", "message":UNRESOLVED, "detail":PARTIAL, "suggestions":[]}
        if expr.free_symbols - {variable} or s.count_ops(expr) > 40: return guidance
        zero_allowed = not (variable.is_positive or variable.is_negative or variable.is_nonzero)
        if zero_allowed and expr.subs(variable, 0) == 0 and all(condition.subs(variable, 0) == s.true for condition in engine.conditions):
            guidance["knownRoots"] = {"exact":"{0}", "tree":display_tree(s.FiniteSet(0))}
        try:
            domain = continuous_domain(expr, variable, s.S.Reals)
        except (NotImplementedError, ValueError, TypeError):
            domain = s.S.EmptySet
        if variable.is_positive: domain = domain.intersect(s.Interval.open(0, s.oo))
        elif variable.is_negative: domain = domain.intersect(s.Interval.open(-s.oo, 0))
        if variable.is_nonzero: domain = domain-s.FiniteSet(0)
        if variable.is_integer: domain = domain.intersect(s.S.Integers)
        integer_domain=variable.is_integer is True or len(values)>2 and str(values[2])=="integer"
        if integer_domain: domain=domain.intersect(s.S.Integers)
        for condition in engine.conditions:
            try:
                real_variable=s.Dummy("real_argument",real=True)
                restricted=condition.xreplace({variable:real_variable})
                domain=domain.intersect(restricted.as_set())
            except (NotImplementedError,ValueError,TypeError):
                domain=s.S.EmptySet;break
        source = readable(expr).replace("**", "^").replace("log(", "ln(")
        points = [-10, -5, -3, -2, -1, 0, 1, 2, 3, 5, 10]
        for left, right in zip(points, points[1:]):
            if domain.is_superset(s.Interval(left, right)) is not True: continue
            ends = [expr.subs(variable, point) for point in (left, right)]
            if all(value.is_real is True and value.is_finite is True for value in ends) and (ends[0]*ends[1]).is_negative is True:
                guidance["suggestions"].append({"label":"Find a numerical root", "detail":f"{left}…{right}", "command":f"nsolve({source},{variable},{left},{right})"})
                if len(guidance["suggestions"]) == 3: break
        if not guidance["suggestions"] and not integer_domain:
            guidance["suggestions"].append({"label":"Try a starting value", "detail":"1", "command":f"nsolve({source},{variable},1)"})
        if not integer_domain:
            try:
                roots = automatic_real_roots(engine, expr, variable, domain)
            except (ValueError, TypeError, NotImplementedError, AttributeError, NameError, OverflowError):
                roots = []
            if roots:
                known = display_rounded(s.FiniteSet(*roots), engine.precision)
                guidance["knownRoots"] = {"exact":readable(known), "tree":display_tree(known), "approximate":True}
                guidance["detail"] = NUMERICAL_PARTIAL
                guidance["searchRange"] = [-10, 10]
        return guidance
    if method == "integrate" and getattr(answer, "has", lambda *_: False)(s.Integral):
        return {"status":"unresolved_integral", "message":INTEGRAL, "detail":"Numerical integration needs bounds in the integrand's domain. Check discontinuities and singularities before choosing an interval.", "suggestions":[]}
    if method == "integrate" and getattr(engine, "integral_strategy", None) == "log_arctan_polylog":
        return {"status":"special_function", "message":"This answer uses the dilogarithm Li₂, a special function, on the positive real branch.", "detail":"The complex terms combine to a real antiderivative for x > 0. Differentiating the answer gives the original integrand.", "suggestions":[]}
    return None
