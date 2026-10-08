"""Actionable, bounded guidance without claiming a numerical search is complete."""
import sympy as s
from sympy.calculus.util import continuous_domain
from calc_display import display_tree, readable

UNRESOLVED = "A complete symbolic solution was not found."
PARTIAL = "The known roots are partial results. Other real or complex roots may exist. Use numerical solving on a continuous interval or try a different starting value."
INTEGRAL = "An antiderivative was not found. This does not prove that no closed form exists. For a numerical value, supply a finite integration interval."


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
        return guidance
    if method == "integrate" and getattr(answer, "has", lambda *_: False)(s.Integral):
        return {"status":"unresolved_integral", "message":INTEGRAL, "detail":"Numerical integration needs bounds in the integrand's domain. Check discontinuities and singularities before choosing an interval.", "suggestions":[]}
    if method == "integrate" and getattr(engine, "integral_strategy", None) == "log_arctan_polylog":
        return {"status":"special_function", "message":"This answer uses the dilogarithm Li₂, a special function, on the positive real branch.", "detail":"The complex terms combine to a real antiderivative for x > 0. Differentiating the answer gives the original integrand.", "suggestions":[]}
    return None
