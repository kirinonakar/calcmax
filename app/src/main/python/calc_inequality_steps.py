"""Exact sign charts for one-variable rational inequalities over the reals."""
import sympy as s
from calc_display import display_tree, readable


def inequality_steps(source, variable, conditions=()):
    if not isinstance(variable, s.Symbol) or source.rel_op not in ('<', '<=', '>', '>=', '!='):
        return None
    expression = source.lhs-source.rhs
    numerator, denominator = s.fraction(s.together(expression))
    try:
        polys = [s.Poly(term, variable) for term in (numerator, denominator)]
        if any(poly.degree() > 6 or not all(c.is_Rational for c in poly.all_coeffs()) for poly in polys):
            return None
        zeros = list(dict.fromkeys(s.real_roots(polys[0]))) if not polys[0].is_zero else []
        poles = list(dict.fromkeys(s.real_roots(polys[1])))
        # The evaluator may already have canceled a denominator. Its original
        # exclusions still split the chart and must survive endpoint selection.
        for condition in conditions:
            if isinstance(condition, s.Unequality) and condition.free_symbols <= {variable}:
                guard=s.Poly(condition.lhs-condition.rhs,variable)
                if guard.degree() <= 6 and all(c.is_Rational for c in guard.all_coeffs()):
                    poles.extend(s.real_roots(guard))
    except (s.PolynomialError, NotImplementedError):
        return None
    critical = sorted(set(zeros+poles), key=lambda value: s.N(value, 40))
    if len(critical) > 12: return None
    edges = [-s.oo, *critical, s.oo]
    intervals, tests = [], []
    for left, right in zip(edges, edges[1:]):
        sample = (s.S.Zero if left == -s.oo and right == s.oo else right-1 if left == -s.oo
                  else left+1 if right == s.oo else (left+right)/2)
        value = s.simplify(expression.subs(variable, sample))
        sign = s.sign(value)
        if sign not in (-1, 0, 1): return None
        comparison = source.func(sign, 0)
        if comparison not in (s.true, s.false): return None
        interval = s.Interval.open(left, right)
        tests.append(s.Tuple(interval, s.Eq(variable, sample, evaluate=False),
                             s.Eq(expression.subs(variable, sample), value, evaluate=False),
                             s.Eq(s.Symbol('sign'), sign, evaluate=False)))
        if comparison == s.true: intervals.append(interval)
    endpoints = [root for root in zeros if root not in poles and source.func(0, 0) == s.true]
    selected = s.Union(*intervals, s.FiniteSet(*endpoints))
    for condition in conditions:
        if condition.free_symbols <= {variable}:
            try: selected=s.Intersection(selected,condition.as_set())
            except (AttributeError,NotImplementedError,ValueError): pass
    steps = []
    def add(title, explanation, *formulas):
        steps.append({'title': title, 'explanation': explanation,
                      'equations': [{'exact': readable(value), 'tree': display_tree(value)} for value in formulas]})
    add('Move all terms to the left', 'Subtract the right side without changing the inequality direction.', source.func(expression, 0, evaluate=False))
    factored = s.factor(numerator)/s.factor(denominator)
    add('Factor for a sign chart', 'Keep the denominator and analyze its sign; multiplying by an unknown sign could reverse the inequality.', source.func(factored, 0, evaluate=False))
    if poles:
        add('Exclude zero denominators', 'The expression is undefined at these values, even for a non-strict inequality.', *[s.Ne(variable, root, evaluate=False) for root in poles])
    add('Find the sign-chart boundaries', 'Numerator zeros and denominator zeros divide the real line into intervals where the sign cannot change.', s.FiniteSet(*critical))
    add('Test the sign in each interval', 'Substitute one point from each open interval. The sign is constant there because there are no zeros or poles inside.', *tests)
    add('Select intervals and check endpoints', 'Keep intervals satisfying the original inequality. Include numerator zeros only for a non-strict inequality; exclude every pole.', selected)
    return {'method': 'Sign chart method', 'steps': steps}
