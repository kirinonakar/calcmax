"""Shared validation, result conversion and numerical tools for advanced statistics."""
import math
import mpmath as mp
import sympy as s
from calc_shared import MathError, require
from calc_statistics import _normal_sf


def number(x):
    try:
        value = float(x)
    except (ValueError, TypeError, OverflowError):
        raise MathError('Enter finite numeric data')
    require(math.isfinite(value), 'Enter finite numeric data')
    return value


def vector(x, minimum=1):
    require(isinstance(x, (list, tuple)), 'Enter a data list')
    values = [number(v) for v in x]
    require(minimum <= len(values) <= 5000, 'Enter enough observations (limit: 5000)')
    return values


def table(x, minimum=2, columns=1):
    require(isinstance(x, (list, tuple)) and len(x) >= minimum, 'Enter a data table with enough rows')
    rows = [vector(row, columns) for row in x]
    require(all(len(row) == len(rows[0]) for row in rows), 'Rows must have equal column counts')
    require(len(rows) <= 5000 and len(rows[0]) <= 20, 'Limit: 5000 rows and 20 columns')
    return rows


def integer(x, low, high):
    v = number(x)
    require(v.is_integer() and low <= v <= high, 'Integer option out of range')
    return int(v)


def option(a, i, default):
    return str(a[i]) if len(a) > i else default


def convert(value):
    if isinstance(value, dict): return {k: convert(v) for k, v in value.items()}
    if isinstance(value, (list, tuple)): return [convert(v) for v in value]
    if isinstance(value, str): return value
    if value is None: return 'unavailable'
    if isinstance(value, int): return s.Integer(value)
    require(not math.isnan(float(value)), 'Numerical result is undefined')
    return s.Float(value, 15) if math.isfinite(float(value)) else s.oo if value > 0 else -s.oo


def inverse(a):
    try: return mp.matrix(a)**-1
    except (ZeroDivisionError, ValueError): raise MathError('Singular model: remove collinear predictors or add observations')


def dot(a, b): return sum(x*y for x, y in zip(a, b))


def mean(x): return sum(x)/len(x)


def variance(x): return sum((v-mean(x))**2 for v in x)/(len(x)-1)


def normal_p(z): return float(2*_normal_sf(abs(z)))


def newton(start, exact):
    """Damped Newton with an exact score and observed information.

    exact(beta) returns (objective, score, information). Poisson, multinomial
    logit, ordinal, NB2 and Cox partial-likelihood models use this so the
    estimate and the reported covariance come from analytic derivatives
    instead of differences.
    """
    beta = start[:]; n = len(beta)
    require(n <= 30, 'Model limit: 30 parameters')
    try: value, grad, info = exact(beta)
    except (OverflowError, ValueError): raise MathError('Model did not converge; check separation, scaling and identifiability')
    require(math.isfinite(value), 'Model did not converge; check separation, scaling and identifiability')
    for iteration in range(100):
        require(all(math.isfinite(v) for v in grad), 'Model did not converge; check separation, scaling and identifiability')
        scaled = inverse(info)*mp.matrix(grad)
        if max(map(abs, grad)) < 2e-5:
            # A vanishing score with positive information and a small
            # information-scaled step separates a stationary point from a
            # boundary estimate (separation, monotone Cox).
            require(min(float(v) for v in mp.eigsy(mp.matrix(info),eigvals_only=True)) > 1e-8, 'Model information is singular; possible separation or boundary estimate')
            if max(abs(float(v)) for v in scaled) < 1e-3: break
        direction = [-float(v) for v in scaled]
        step = 1.0
        for _ in range(40):
            candidate = [b+step*d for b, d in zip(beta, direction)]
            try: trial, trial_grad, trial_information = exact(candidate)
            except (OverflowError, ValueError): trial = math.inf
            if math.isfinite(trial) and trial <= value+1e-4*step*dot(grad, direction): break
            step *= .5
        else: raise MathError('Model did not converge; check separation, scaling and identifiability')
        value, grad, info = trial, trial_grad, trial_information
        beta = candidate
    else: raise MathError('Model did not converge in 100 iterations')
    require(max(map(abs, beta)) < 50, 'Unbounded estimates: possible separation or non-identifiability')
    require(min(float(v) for v in mp.eigsy(mp.matrix(info),eigvals_only=True)) > 1e-8, 'Model information is singular; estimates are not identifiable')
    return beta, inverse(info), value, iteration+1


def inference(beta, covariance, names, ratio=False):
    rows = []
    for i,b in enumerate(beta):
        se = math.sqrt(max(0,float(covariance[i,i])))
        row = {'term':names[i], 'estimate':b, 'SE':se, 'p':normal_p(b/se) if se else None, 'CI95':[b-1.95996398454*se,b+1.95996398454*se] if se else None}
        if ratio: row['exp(coef)'] = math.exp(b) if b<709.782712893384 else math.inf
        rows.append(row)
    return rows


def regression_data(rows):
    x = [[1.0]+r[:-1] for r in rows]; y = [r[-1] for r in rows]
    require(len(x) > len(x[0]), 'More observations than coefficients are required')
    require(s.Matrix(x).rank() == len(x[0]), 'Predictors are collinear')
    return x,y


def standardized_design(x):
    """Center/scale predictors during fitting and transform inference back."""
    p=len(x[0]); centers=[mean(c) for c in zip(*x)][1:]; scales=[math.sqrt(variance(c)) for c in list(zip(*x))[1:]]
    require(all(v>0 for v in scales),'Predictors must vary')
    design=[[1.0]+[(v-centers[j])/scales[j] for j,v in enumerate(r[1:])] for r in x]
    transform=mp.eye(p)
    for j in range(1,p): transform[0,j]=-centers[j-1]/scales[j-1]; transform[j,j]=1/scales[j-1]
    return design,transform,centers,scales


def softplus(z): return max(z,0)+math.log1p(math.exp(-abs(z)))


def logistic(z): return math.exp(-softplus(-z))
