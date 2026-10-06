"""Firth bias reduction for full-rank binary logistic models."""
import mpmath as mp
import sympy as s
from calc_shared import MathError, require


def complete_separation(rows, design, target, beta, transform):
    """A strictly separating coefficient vector certifies complete separation.

    Verify rational/decimal input in exact arithmetic to avoid declaring a tiny
    rounding residual on a separation boundary to be a strictly positive margin.
    """
    margins = [(2*y-1)*mp.fsum(x*b for x, b in zip(row, beta))
               for row, y in zip(design, target)]
    if not all(margin > 0 for margin in margins):
        return False
    if all(v.is_Rational or v.is_Float for row in rows for v in row):
        coefficients = [s.Rational(mp.nstr(v, mp.mp.dps)) for v in transform*beta]
        return all((2*s.Rational(row[-1])-1)*sum(b*x for b, x in zip(coefficients, [s.Integer(1)]+[s.Rational(x) for x in row[:-1]])) > 0
                   for row in rows)
    # Irrational numeric inputs use a conservative precision-scaled margin.
    epsilon = mp.power(10, -max(12, min(mp.mp.dps-10, 25)))
    return all(margin > epsilon*(1+mp.fsum(abs(x*b) for x, b in zip(row, beta)))
               for row, margin in zip(design, margins))


def penalized(design, target, beta):
    """Firth penalized objective, inverse information and adjusted score at beta."""
    from calc_inference import _covariance
    logits = [mp.fsum(x*b for x, b in zip(row, beta)) for row in design]
    exp = [mp.exp(-abs(z)) for z in logits]
    probabilities = [1/(1+e) if z >= 0 else e/(1+e) for z, e in zip(logits, exp)]
    weights = [e/(1+e)**2 for e in exp]
    covariance = _covariance([[mp.sqrt(w)*x for x in row] for row, w in zip(design, weights)])
    determinant = mp.det(covariance)
    require(determinant > 0, "Firth information matrix is singular")
    loss = mp.fsum(max(z, 0)-y*z+mp.log1p(e) for z, y, e in zip(logits, target, exp))
    objective = -loss-mp.log(determinant)/2
    leverage = [w*(mp.matrix([row])*covariance*mp.matrix(row))[0]
                for row, w in zip(design, weights)]
    adjusted = [y-prob+h*(mp.mpf('.5')-prob)
                for y, prob, h in zip(target, probabilities, leverage)]
    score = mp.matrix([mp.fsum(row[j]*a for row, a in zip(design, adjusted)) for j in range(len(design[0]))])
    return objective, covariance, score


def fit_firth(design, target, precision):
    """Maximize log L + 0.5 log|X'WX| with adjusted-score Fisher scoring.

    Returns normalized coefficients, inverse Fisher information, penalized
    log likelihood and iteration count. Profile penalized-likelihood intervals
    are computed separately by profile_intervals.
    """
    with mp.workdps(max(40, precision+20)):
        n, p = len(design), len(design[0])
        tolerance = mp.power(10, -min(max(precision, 12), 24))
        prevalence = mp.fsum(target)/n
        beta = mp.matrix([mp.log(prevalence/(1-prevalence))]+[0]*(p-1))
        current = penalized(design, target, beta)
        for iteration in range(400):
            objective, covariance, score = current
            if max(abs(v) for v in score) <= tolerance:
                return beta, covariance, objective, iteration+1
            step = covariance*score
            size = max(abs(v) for v in step)
            if size > 5:
                step *= 5/size
            rate = mp.mpf(1)
            while rate >= mp.mpf('1e-12'):
                candidate = beta+rate*step
                try:
                    next_value = penalized(design, target, candidate)
                except (MathError, ValueError, ZeroDivisionError):
                    next_value = None
                if next_value is not None and next_value[0] >= objective:
                    break
                rate /= 2
            require(rate >= mp.mpf('1e-12'), "Firth logistic regression did not converge")
            beta, current = candidate, next_value
        raise MathError("Firth logistic regression did not converge")


def fixed_fit(design, target, index, value, precision, start, tolerance=None):
    """Maximize the Firth penalized likelihood with one coefficient fixed."""
    with mp.workdps(max(40, precision+20)):
        p = len(design[0])
        tolerance = mp.power(10, -min(max(precision, 10), 14)) if tolerance is None else tolerance
        beta = mp.matrix(start)
        beta[index, 0] = value
        free = [j for j in range(p) if j != index]
        objective, covariance, score = penalized(design, target, beta)
        for iteration in range(200):
            if max(abs(score[j, 0]) for j in free) <= tolerance:
                return beta, objective
            information = covariance**-1
            reduced = mp.matrix([[information[i, j] for j in free] for i in free])
            step = mp.lu_solve(reduced, mp.matrix([score[j, 0] for j in free]))
            rate = mp.mpf(1)
            next_value = None
            while rate >= mp.mpf('1e-12'):
                candidate = mp.matrix(beta)
                for position, j in enumerate(free):
                    candidate[j, 0] = beta[j, 0]+rate*step[position, 0]
                try:
                    next_value = penalized(design, target, candidate)
                except (MathError, ValueError, ZeroDivisionError):
                    next_value = None
                if next_value is not None and next_value[0] <= objective:
                    break
                rate /= 2
            require(rate >= mp.mpf('1e-12'), "Firth profile likelihood did not converge")
            beta, current = candidate, next_value
            objective, covariance, score = current
        raise MathError("Firth profile likelihood did not converge")


def profile_intervals(design, transform, target, beta, precision, critical=None):
    """Penalized profile-likelihood intervals for original-unit coefficients.

    transform maps standardized coefficients to original predictor units, so the
    likelihood is re-evaluated on the reparameterized design where every reported
    coefficient is a single coordinate. A linear reparameterization only shifts
    the Firth penalty by a constant, so the two parameterizations share the same
    optimum and the profile deviance stays comparable. Returns one [low, high]
    list per coefficient; unbracketed intervals are None.
    """
    critical = mp.mpf('3.84145882069412') if critical is None else critical
    with mp.workdps(max(40, precision+20)):
        original_design = (mp.matrix(design)*(transform**-1)).tolist()
        start = transform*beta
        _, covariance, _ = penalized(design, target, beta)
        spread = transform*covariance*transform.T
        base = penalized(original_design, target, start)[0]
        intervals = []
        for index in range(len(start)):
            state = {'beta': mp.matrix(start)}
            def deviance(value, index=index, state=state):
                fixed, objective = fixed_fit(original_design, target, index, value, precision, state['beta'])
                state['beta'] = fixed
                return 2*(objective-base)
            estimate = start[index, 0]
            width = mp.sqrt(max(mp.mpf(0), spread[index, index]))
            if not width > 0:
                intervals.append(None)
                continue
            bounds = []
            for direction in (mp.mpf(-1), mp.mpf(1)):
                step = width
                edge = estimate+direction*step
                for _ in range(30):
                    if deviance(edge) >= critical:
                        break
                    step *= mp.mpf('1.7')
                    edge = estimate+direction*step
                else:
                    bounds.append(None)
                    continue
                inner, outer = estimate, edge
                for _ in range(60):
                    middle = (inner+outer)/2
                    if deviance(middle) >= critical:
                        outer = middle
                    else:
                        inner = middle
                    if abs(outer-inner) <= mp.mpf('1e-9')*(1+abs(estimate)):
                        break
                bounds.append(outer)
            intervals.append(None if bounds[0] is None or bounds[1] is None else [bounds[0], bounds[1]])
        return intervals
