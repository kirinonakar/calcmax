"""Bounded, checked limit transformations for the shared explanation engine."""
import sympy as s


SUMMARY = "A detailed derivation is not available for this expression. The computed result is shown below."


def limit_steps(engine, expression, variable, point, direction, answer, add):
    approach = {"both": "+-", "left": "-", "right": "+"}.get(direction, direction)

    def eq(left, right):
        return s.Eq(left, right, evaluate=False)

    def same(left, right):
        return left == right or s.simplify(left-right) == 0

    def finite(value):
        return not value.has(s.nan, s.zoo, s.oo, -s.oo, s.Limit)

    def limit(value, side=approach):
        return s.limit(value, variable, point, dir=side)

    def substitution_allowed(value):
        # Matching the computed answer does not prove continuity: SymPy
        # substitutes 0**0 as 1, and floor/ceiling can jump at the point.
        return (point.is_finite is True
                and not value.has(s.floor, s.ceiling, s.sign, s.Piecewise)
                and not any(power.exp.has(variable) for power in value.atoms(s.Pow)))

    add("Identify the approach", "Follow the selected approach to the point. Left and right limits may differ.", s.Limit(expression, variable, point, dir=approach))
    substituted = expression.subs(variable, point)
    if substitution_allowed(expression) and finite(substituted) and same(substituted, answer):
        if any(condition.subs(variable, point) == s.false for condition in engine.conditions):
            add("Evaluate the continuous extension", "The original expression is undefined at the point. Use its simplified form on nearby allowed values to find the limit.", substituted)
        else:
            add("Direct substitution", "When the expression is continuous at the approach point, substitute the point directly.", substituted)
        return ""

    simplified = s.cancel(expression) if expression.is_rational_function(variable) else expression
    if simplified != expression:
        add("Cancel a removable factor", "Cancel common factors away from the approach point. The simplified expression has the same limit there.", eq(expression, simplified))
        candidate = simplified.subs(variable, point)
        if substitution_allowed(simplified) and finite(candidate) and same(candidate, answer):
            add("Evaluate the continuous extension", "The original expression is undefined at the point. Use its simplified form on nearby allowed values to find the limit.", candidate)
            return ""

    # Conjugate multiplication also handles a square-root difference in the numerator.
    numerator, denominator = s.fraction(s.together(simplified))
    for part in (numerator, denominator):
        if not part.is_Add or len(part.args) != 2:
            continue
        if not any(power.exp == s.Rational(1, 2) and power.base.has(variable) for power in part.atoms(s.Pow)):
            continue
        conjugate = part.args[0]-part.args[1]
        if conjugate == 0:
            continue
        reduced = s.cancel(s.expand(numerator*conjugate)/s.expand(denominator*conjugate))
        candidate = reduced.subs(variable, point)
        if substitution_allowed(reduced) and s.count_ops(reduced) <= 60 and finite(candidate) and same(candidate, answer) and same(reduced, expression):
            add("Rationalize with the conjugate", "Multiply the numerator and denominator by the conjugate. The difference of squares removes the square-root difference on nearby allowed values.", eq(expression, reduced))
            add("Direct substitution", "When the expression is continuous at the approach point, substitute the point directly.", candidate)
            return ""

    if point.is_infinite and expression.is_rational_function(variable):
        top, bottom = s.Poly(numerator, variable), s.Poly(denominator, variable)
        power = max(top.degree(), bottom.degree())
        scaled = s.Mul(s.expand(numerator/variable**power), s.Pow(s.expand(denominator/variable**power), -1, evaluate=False), evaluate=False)
        add("Compare highest powers", "For a rational function at infinity, the highest powers determine whether the ratio tends to 0, a finite coefficient ratio, or infinity.", eq(expression, scaled))
        return ""

    if not expression.has(s.Abs, s.Piecewise, s.sign, s.floor, s.ceiling) and s.count_ops(expression) <= 40:
        pending = []
        try:
            for _ in range(6):
                nvalue, dvalue = (numerator.subs(variable, point), denominator.subs(variable, point))
                if point.is_infinite or nvalue.has(s.zoo, s.nan) or dvalue.has(s.zoo, s.nan):
                    nvalue, dvalue = limit(numerator), limit(denominator)
                zero_form = nvalue == 0 and dvalue == 0
                infinite_form = nvalue in (s.oo, -s.oo) and dvalue in (s.oo, -s.oo)
                if not (zero_form or infinite_form):
                    break
                dn, dd = s.diff(numerator, variable), s.diff(denominator, variable)
                if dd == 0 or s.count_ops(dn/dd) > 60:
                    break
                # Oscillating/piecewise derivatives cannot certify a nonzero denominator nearby.
                dlimit = limit(dd)
                if dlimit.has(s.nan, s.zoo, s.AccumBounds) or (point.is_infinite and dd.has(s.sin, s.cos, s.tan)):
                    break
                if dlimit == 0:
                    h = s.Dummy("h", positive=True)
                    shifted = dd.subs(variable, point+(-h if approach == "-" else h))
                    leading = shifted.as_leading_term(h)
                    coefficient, exponent = leading.as_coeff_exponent(h)
                    if coefficient.has(h) or coefficient.is_zero is not False or not exponent.is_Rational:
                        break
                pending.append((zero_form, numerator/denominator, dn/dd))
                numerator, denominator = dn, dd
                candidate = (dn/dd).subs(variable, point)
                if substitution_allowed(pending[-1][2]) and finite(candidate) and same(candidate, answer):
                    break
            if pending and same(limit(pending[-1][2]), answer):
                for zero_form, before, after in pending:
                    title = "L'Hôpital's rule for 0/0" if zero_form else "L'Hôpital's rule for infinity/infinity"
                    explanation = ("For this differentiable 0/0 form, differentiate the numerator and denominator separately, then evaluate their ratio along the same approach." if zero_form else
                                   "For this differentiable infinity/infinity form, differentiate the numerator and denominator separately along the same approach. The transformed limit has been checked.")
                    add(title, explanation, eq(s.Limit(before, variable, point, dir=approach), s.Limit(after, variable, point, dir=approach)))
                candidate = pending[-1][2].subs(variable, point)
                if substitution_allowed(pending[-1][2]) and finite(candidate) and same(candidate, answer):
                    add("Direct substitution", "When the expression is continuous at the approach point, substitute the point directly.", candidate)
                return ""
        except (ValueError, TypeError, NotImplementedError, s.PolynomialError):
            pass

    if point.is_finite and expression.has(s.Abs, s.Piecewise, s.sign, s.floor, s.ceiling):
        try:
            sides = [approach] if approach in ("+", "-") else ["-", "+"]
            limits = [(side, limit(expression, side)) for side in sides]
            if all(same(value, answer) for _, value in limits):
                for side, value in limits:
                    add("Evaluate the one-sided limit", "Use the expression on the selected side of the point. A two-sided limit exists only when the left and right limits agree.", eq(s.Limit(expression, variable, point, dir=side), value))
                return ""
        except (ValueError, TypeError, NotImplementedError):
            pass

    # A bounded oscillating factor times a vanishing multiplier is a useful squeeze case.
    if answer == 0 and expression.is_Mul:
        for factor in expression.args:
            if factor.func not in (s.sin, s.cos):
                continue
            real = s.Dummy("r", real=True, nonzero=True)
            if factor.args[0].subs(variable, real).is_real is not True:
                continue
            bound = s.Abs(s.cancel(expression/factor))
            try:
                if limit(bound) == 0:
                    add("Bound the oscillating factor", "For a real argument, sine and cosine lie between −1 and 1. Bound the absolute value of the product by its remaining multiplier.", s.Le(s.Abs(expression), bound, evaluate=False))
                    add("Squeeze theorem", "The absolute-value bound tends to zero, so the bounded expression also tends to zero along the selected approach.", eq(s.Limit(bound, variable, point, dir=approach), 0))
                    return ""
            except (ValueError, TypeError, NotImplementedError):
                pass

    # Transform each side to h -> 0+ before taking a local series; this preserves signs.
    try:
        sides = [approach] if approach in ("+", "-") or point.is_infinite else ["-", "+"]
        expansions = []
        used = {str(symbol) for symbol in expression.free_symbols}
        name = "h"
        while name in used: name += "1"
        h = s.Symbol(name, positive=True)
        for side in sides:
            replacement = (1/h if point == s.oo else -1/h) if point.is_infinite else point+(-h if side == "-" else h)
            shifted = expression.subs(variable, replacement)
            series = s.series(shifted, h, 0, 4)
            if not series.has(s.Order) or series.removeO() == 0 or s.count_ops(series) > 60:
                return SUMMARY
            leading = series.removeO().as_leading_term(h)
            coefficient, exponent = leading.as_coeff_exponent(h)
            if coefficient.has(h) or not exponent.is_Rational or not same(s.limit(leading, h, 0, dir="+"), answer):
                return SUMMARY
            expansions.append((replacement, shifted, series, leading))
        for replacement, shifted, series, leading in expansions:
            add("Expand near the approach point", "Use a local series after expressing the selected approach as h tending to 0 from the right. The remainder records the omitted higher powers.", eq(variable, replacement), eq(shifted, series))
            add("Use the leading term", "The first nonzero term determines this limit; higher powers vanish relative to it. Both sides are checked for a two-sided approach.", eq(s.Limit(leading, h, 0, dir="+"), answer))
        return ""
    except (ValueError, TypeError, NotImplementedError, s.PolynomialError):
        return SUMMARY
