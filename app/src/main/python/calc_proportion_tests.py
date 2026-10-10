"""Score z tests for one proportion or two independent proportions."""
import mpmath as mp
import sympy as s
from calc_shared import require
from calc_statistics import _real_value, _mpf, _mp_result, _normal_sf, _tail_probability


def counts(successes, trials):
    successes, trials = s.sympify(successes), s.sympify(trials)
    require(successes.is_Integer and trials.is_Integer and trials > 0 and 0 <= successes <= trials,
            'Successes and trials must be integers with 0 ≤ successes ≤ trials and trials > 0')
    return successes, trials


def sample_counts(data):
    require(isinstance(data, (list, tuple)) and len(data) > 0,
            'Enter binary observations or success/trial count rows')
    if any(isinstance(row, (list, tuple)) for row in data):
        require(all(isinstance(row, (list, tuple)) and len(row) == 2 for row in data),
                'Count rows must contain successes and trials')
        pairs = [counts(*row) for row in data]
        return sum((x for x, _ in pairs), s.Integer(0)), sum((n for _, n in pairs), s.Integer(0))
    require(all(value in (s.Integer(0), s.Integer(1)) for value in data),
            'Proportion observations must be binary 0/1')
    return sum(data, s.Integer(0)), s.Integer(len(data))


def calculate(engine, name, args):
    tail = 'both'
    if args and str(args[-1]) in ('left', 'right', 'both'):
        tail, args = str(args[-1]), args[:-1]
    engine.note = 'Normal approximation without continuity correction.'
    if name == 'propztest':
        require(len(args) in (2, 3), 'propztest takes p0 and data, or p0, successes and trials')
        p0 = _real_value(args[0], 'The null proportion must be numeric')
        require(0 < p0 < 1, 'The null proportion must lie in (0,1)')
        x, n = sample_counts(args[1]) if len(args) == 2 else counts(*args[1:])
        estimate = x/n
        variance = p0*(1-p0)/n
        difference = estimate-p0
        samples = [('Sample', n, p0)]
        result = {'sample proportion': estimate, 'null proportion': p0, 'successes': x, 'n': n}
    else:
        require(len(args) in (2, 4), 'propztest2 takes two data lists, or successes A, trials A, successes B, trials B')
        (xa, na), (xb, nb) = ([sample_counts(data) for data in args] if len(args) == 2
                              else [counts(*args[:2]), counts(*args[2:])])
        pooled = (xa+xb)/(na+nb)
        require(0 < pooled < 1, 'The pooled proportion must lie in (0,1); the z statistic is undefined')
        difference = xa/na-xb/nb
        variance = pooled*(1-pooled)*(1/na+1/nb)
        samples = [('Group A', na, pooled), ('Group B', nb, pooled)]
        result = {'proportion A': xa/na, 'proportion B': xb/nb, 'proportion difference (A − B)': difference,
                  'pooled proportion': pooled, 'successes A': xa, 'n A': na, 'successes B': xb, 'n B': nb}
    with mp.workdps(engine.precision+10):
        se = mp.sqrt(_mpf(variance, engine.precision))
        z = _mpf(difference, engine.precision)/se
        result = {'z': _mp_result(z, engine), 'p value': _mp_result(_tail_probability(_normal_sf, z, tail), engine),
                  'standard error': _mp_result(se, engine), 'alternative': tail, **result}
    result['Normal approximation checks'] = [
        {'Sample': label, 'Expected successes': n*p, 'Expected failures': n*(1-p),
         'Status': 'Adequate (both counts ≥ 10)' if min(n*p, n*(1-p)) >= 10 else 'Small expected counts (< 10)'}
        for label, n, p in samples]
    return result
