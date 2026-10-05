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


def fit_firth(design, target, precision):
    """Maximize log L + 0.5 log|X'WX| with adjusted-score Fisher scoring.

    Returns normalized coefficients, inverse Fisher information, penalized
    log likelihood and iteration count. Intervals computed by callers are Wald
    approximations, not profile penalized-likelihood intervals.
    """
    from calc_inference import _covariance
    with mp.workdps(max(40, precision+20)):
        n, p = len(design), len(design[0])
        tolerance = mp.power(10, -min(max(precision, 12), 24))

        def evaluate(beta):
            logits = [mp.fsum(x*b for x, b in zip(row, beta)) for row in design]
            exp = [mp.exp(-abs(z)) for z in logits]
            probabilities = [1/(1+e) if z >= 0 else e/(1+e) for z, e in zip(logits, exp)]
            weights = [e/(1+e)**2 for e in exp]
            inverse = _covariance([[mp.sqrt(w)*x for x in row] for row, w in zip(design, weights)])
            determinant = mp.det(inverse)
            require(determinant > 0, "Firth information matrix is singular")
            loss = mp.fsum(max(z, 0)-y*z+mp.log1p(e) for z, y, e in zip(logits, target, exp))
            objective = -loss-mp.log(determinant)/2
            leverage = [w*(mp.matrix([row])*inverse*mp.matrix(row))[0]
                        for row, w in zip(design, weights)]
            adjusted = [y-prob+h*(mp.mpf('.5')-prob)
                        for y, prob, h in zip(target, probabilities, leverage)]
            score = mp.matrix([mp.fsum(row[j]*a for row, a in zip(design, adjusted)) for j in range(p)])
            return objective, inverse, score

        prevalence = mp.fsum(target)/n
        beta = mp.matrix([mp.log(prevalence/(1-prevalence))]+[0]*(p-1))
        current = evaluate(beta)
        for iteration in range(400):
            objective, inverse, score = current
            if max(abs(v) for v in score) <= tolerance:
                return beta, inverse, objective, iteration+1
            step = inverse*score
            size = max(abs(v) for v in step)
            if size > 5:
                step *= 5/size
            rate = mp.mpf(1)
            while rate >= mp.mpf('1e-12'):
                candidate = beta+rate*step
                try:
                    next_value = evaluate(candidate)
                except (MathError, ValueError, ZeroDivisionError):
                    next_value = None
                if next_value is not None and next_value[0] >= objective:
                    break
                rate /= 2
            require(rate >= mp.mpf('1e-12'), "Firth logistic regression did not converge")
            beta, current = candidate, next_value
        raise MathError("Firth logistic regression did not converge")
