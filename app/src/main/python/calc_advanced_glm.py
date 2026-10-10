"""Portable GLMs: Fisher scoring or joint NB2 ML, covariance and diagnostics."""
import math
import mpmath as mp
from calc_shared import MathError, require
from calc_advanced_common import dot, inference, logistic, mean, number, option, standardized_design, table
from calc_advanced_linear import least_squares


LINKS = {'gaussian':('identity','log'), 'binomial':('logit','probit','cloglog'),
         'poisson':('log',), 'gamma':('log','inverse'),
         'inversegaussian':('log','inverse_squared'), 'nbinom':('log',)}


def inverse_link(eta, link):
    if link == 'identity': return eta, 1.0
    if link == 'log':
        mu = math.exp(eta)
        return mu, mu
    if link == 'logit':
        mu = logistic(eta)
        density = math.exp(-abs(eta))/(1+math.exp(-abs(eta)))**2
        return mu, density
    if link == 'probit': return .5*math.erfc(-eta/math.sqrt(2)), math.exp(-eta*eta/2)/math.sqrt(2*math.pi)
    if link == 'cloglog':
        value = math.exp(eta)
        return -math.expm1(-value), math.exp(eta-value)
    if eta <= 0: raise ValueError('Inverse link requires a positive linear predictor')
    if link == 'inverse': return 1/eta, -1/(eta*eta)
    return 1/math.sqrt(eta), -.5/eta**1.5


def variance(mu, family, alpha):
    if family == 'gaussian': return 1.0
    if family == 'binomial': return mu*(1-mu)
    if family == 'poisson': return mu
    if family == 'gamma': return mu*mu
    if family == 'inversegaussian': return mu**3
    return mu+alpha*mu*mu


def deviance(y, mu, family, alpha):
    if family == 'nbinom' and alpha == 0: return deviance(y, mu, 'poisson', 0)
    if family == 'gaussian': return (y-mu)**2
    if family == 'binomial': return -2*(math.log(mu) if y else math.log1p(-mu))
    if family == 'poisson': return 2*((y*math.log(y/mu) if y else 0)-(y-mu))
    if family == 'gamma': return 2*((y-mu)/mu-math.log(y/mu))
    if family == 'inversegaussian': return (y-mu)**2/(y*mu*mu)
    r = 1/alpha
    return 2*((y*math.log(y/mu) if y else 0)-(y+r)*math.log((y+r)/(mu+r)))


def fit(x, y, offsets, family, link, alpha):
    average = mean(y)
    if link == 'identity': initial = average
    elif link == 'log': initial = math.log(average)
    elif link == 'logit': initial = math.log(average/(1-average))
    elif link == 'probit': initial = float(math.sqrt(2)*mp.erfinv(2*average-1))
    elif link == 'cloglog': initial = math.log(-math.log1p(-average))
    elif link == 'inverse': initial = 1/average
    else: initial = 1/average**2
    # Fit a feasible start even when offsets span many orders of magnitude.
    initial -= mean(offsets)
    if link in ('inverse','inverse_squared'): initial = max(initial, 1e-3-min(offsets))
    beta = [initial]+[0.0]*(len(x[0])-1)

    def evaluate(b):
        mus, derivatives = zip(*(inverse_link(dot(row, b)+off, link) for row, off in zip(x, offsets)))
        if not all(math.isfinite(v) for v in mus): raise ValueError('Invalid fitted mean')
        if family == 'binomial':
            if not all(0 < mu < 1 for mu in mus): raise ValueError('Binomial mean reached a boundary')
        elif family != 'gaussian' or link == 'log':
            if not all(mu > 0 for mu in mus): raise ValueError('Fitted means must be positive')
        weights = [d*d/variance(mu, family, alpha) for mu, d in zip(mus, derivatives)]
        if not all(math.isfinite(w) and w > 1e-18 for w in weights): raise ValueError('Model information reached a boundary')
        value = max(0.0, math.fsum(deviance(v, mu, family, alpha) for v, mu in zip(y, mus)))
        if not math.isfinite(value): raise ValueError('Non-finite deviance')
        return value, list(mus), list(derivatives), weights

    try:
        value, mus, derivatives, weights = evaluate(beta)
        for iteration in range(1, 101):
            working = [dot(row, beta)+(v-mu)/derivative for row, v, mu, derivative in zip(x, y, mus, derivatives)]
            candidate, _, _, _ = least_squares(x, working, weights)
            direction = [a-b for a, b in zip(candidate, beta)]
            if max(abs(v) for v in direction) < 1e-9*(1+max(map(abs, beta))): break
            rate = 1.0
            for _ in range(40):
                trial = [b+rate*d for b, d in zip(beta, direction)]
                try: next_value, next_mus, next_derivatives, next_weights = evaluate(trial)
                except (OverflowError, ValueError, ZeroDivisionError): next_value = math.inf
                if next_value <= value+1e-12*max(1, value): break
                rate *= .5
            else: raise MathError('GLM did not converge; check response, link, offsets and scaling')
            beta = trial
            value, mus, derivatives, weights = next_value, next_mus, next_derivatives, next_weights
        else: raise MathError('GLM did not converge in 100 iterations')
        _, covariance, _, _ = least_squares(x, y, weights)
    except (OverflowError, ValueError, ZeroDivisionError):
        raise MathError('GLM is separated or numerically unidentifiable; finite inference unavailable')
    if family == 'binomial':
        margins = [(2*v-1)*(dot(row, beta)+off) for row, v, off in zip(x, y, offsets)]
        require(not (min(margins) >= -1e-8 and max(margins) > 1e-8), 'Complete or quasi separation: binomial GLM MLE is not finite')
    return beta, covariance, mus, value, iteration


def log_likelihood(y, mus, family, scale, alpha, link):
    if family == 'nbinom' and alpha == 0: return log_likelihood(y, mus, 'poisson', scale, 0, link)
    if family == 'gaussian':
        if link != 'identity': return -.5*math.fsum((v-mu)**2/scale+math.log(2*math.pi*scale) for v, mu in zip(y, mus))
        # Concentrated Gaussian likelihood uses the ML residual variance.
        mle_scale = math.fsum((v-mu)**2 for v, mu in zip(y, mus))/len(y)
        return -.5*len(y)*(math.log(2*math.pi*mle_scale)+1)
    if family == 'binomial': return math.fsum(math.log(mu) if v else math.log1p(-mu) for v, mu in zip(y, mus))
    if family == 'poisson': return math.fsum(v*math.log(mu)-mu-math.lgamma(v+1) for v, mu in zip(y, mus))
    if family == 'gamma':
        shape = 1/scale
        return math.fsum((shape-1)*math.log(v)-v/(mu*scale)-shape*math.log(mu*scale)-math.lgamma(shape) for v, mu in zip(y, mus))
    if family == 'inversegaussian': return math.fsum(-.5*(math.log(2*math.pi*scale)+3*math.log(v)+(v-mu)**2/(scale*v*mu*mu)) for v, mu in zip(y, mus))
    r = 1/alpha
    return math.fsum(math.lgamma(v+r)-math.lgamma(r)-math.lgamma(v+1)+r*math.log(r/(r+mu))+v*math.log(mu/(r+mu)) for v, mu in zip(y, mus))


def calculate(engine, name, a):
    rows = table(a[0], 3, 2)
    family = option(a, 1, 'gaussian')
    require(family in LINKS, 'Choose gaussian, binomial, poisson, gamma, inversegaussian or nbinom')
    link = option(a, 2, 'auto')
    if link == 'auto': link = LINKS[family][0]
    require(link in LINKS[family], 'Choose a link supported by the selected GLM family')
    estimate_alpha = family == 'nbinom' and option(a, 3, '1') in ('estimate', 'auto')
    alpha = 1.0 if estimate_alpha else number(a[3]) if len(a) > 3 else 1.0
    require(alpha > 0, 'NB2 dispersion alpha must be positive, or use estimate')
    y = [row[-1] for row in rows]
    if family == 'binomial': require(all(v in (0, 1) for v in y) and 0 < sum(y) < len(y), 'Binomial response must contain both 0 and 1')
    elif family in ('poisson','nbinom'): require(all(v >= 0 and v.is_integer() for v in y) and sum(y) > 0, 'Response must be nonnegative integer counts with at least one event')
    elif family in ('gamma','inversegaussian'): require(all(v > 0 for v in y), 'This GLM family requires positive responses')
    if link == 'log': require(mean(y) > 0, 'Log-link GLM requires a positive response mean')
    offsets = [number(v) for v in a[4]] if len(a) > 4 and isinstance(a[4], (list, tuple)) else [0.0]*len(y)
    require(len(a) <= 4 or isinstance(a[4], (list, tuple)), 'Enter a row-aligned offset/exposure list')
    require(len(offsets) == len(y), 'Offset/exposure must have one value per observation')
    adjustment = option(a, 5, 'offset')
    require(adjustment in ('offset','exposure'), 'Choose offset or exposure')
    if adjustment == 'exposure':
        require(link == 'log' and all(v > 0 for v in offsets), 'Exposure requires a log link and positive values')
        offsets = [math.log(v) for v in offsets]
    require(all(abs(v) < 700 for v in offsets), 'Offset exceeds the numeric range')
    x, transform, _, _ = standardized_design([[1.0]+row[:-1] for row in rows])
    estimated = None
    boundary = False
    if estimate_alpha:
        from calc_advanced_regression import model
        try:
            estimated = model(rows, 'nbreg', offsets)
            alpha = estimated['dispersion alpha (NB2)']
        except MathError:
            # A finite NB2 MLE may be on the Poisson boundary. Never replace an
            # interior fitting failure with a boundary result without a check.
            beta, cov, mus, dev, iterations = fit(x, y, offsets, 'poisson', link, 0)
            require(math.fsum((v-mu)**2-v for v, mu in zip(y, mus)) <= 0,
                    'NB2 dispersion estimation did not converge to an identifiable estimate')
            alpha = 0.0
            boundary = True
    if estimated is not None:
        coefficients = [row['estimate'] for row in estimated['coefficients']]
        mus = [math.exp(dot([1.0]+row[:-1], coefficients)+off) for row, off in zip(rows, offsets)]
        dev = math.fsum(deviance(v, mu, family, alpha) for v, mu in zip(y, mus))
        iterations = estimated['iterations']
        beta = coefficients
    elif not boundary:
        beta, cov, mus, dev, iterations = fit(x, y, offsets, family, link, alpha)
    df = len(y)-len(beta)
    pearson = math.fsum((v-mu)**2/variance(mu, family, alpha) for v, mu in zip(y, mus))
    scale = 1.0 if family in ('binomial','poisson','nbinom') else pearson/df
    require(scale > 0 and math.isfinite(scale), 'Positive residual variation is required for inference')
    if estimated is None:
        coefficients = list(map(float, transform*mp.matrix(beta)))
        coefficient_cov = transform*cov*transform.T*scale
    ll = log_likelihood(y, mus, family, scale, alpha, link)
    result = {'family':family, 'link':link, 'n':len(y), 'df residual':df, 'dispersion':scale,
              'deviance':dev, 'Pearson chi2':pearson, 'log likelihood':ll, 'AIC':2*(len(beta)+int(estimate_alpha))-2*ll,
              'iterations':iterations, 'fitted preview rows':min(len(y), 50), 'coefficients':estimated['coefficients'] if estimated else inference(coefficients, coefficient_cov, ['Intercept']+['x'+str(i) for i in range(1, len(beta))], link in ('log','logit')),
              'Fitted observations':[{'row':i+1, 'observed':v, 'fitted':mu, 'residual':v-mu,
                                      'Pearson residual':(v-mu)/math.sqrt(variance(mu, family, alpha)),
                                      'Deviance residual':math.copysign(math.sqrt(max(0, deviance(v, mu, family, alpha))), v-mu)} for i, (v, mu) in enumerate(zip(y[:50], mus[:50]))]}
    try:
        _, _, _, null_deviance, _ = fit([[1.0] for _ in y], y, offsets, family, link, alpha)
        result['null deviance'] = null_deviance
    except MathError:
        result['null deviance'] = None
    if family == 'nbinom':
        result['dispersion alpha (NB2)' if estimate_alpha else 'dispersion alpha (NB2, fixed)'] = alpha
        result['NB2 dispersion estimation'] = 'ML (Poisson boundary)' if boundary else 'joint ML' if estimate_alpha else 'fixed'
        if estimate_alpha: result['null dispersion alpha (NB2, held at full fit)'] = alpha
    engine.note += (' GLM NB2 by joint ML with analytic observed information including dispersion uncertainty.' if estimated else ' GLM by damped Fisher scoring; independent observations, model-based SE and Wald z 95% intervals.')
    engine.note += ' Gaussian/Gamma/inverse Gaussian use Pearson dispersion; other scales are 1. Gamma/inverse Gaussian likelihood and AIC plug in the Pearson dispersion.'
    if family == 'nbinom': engine.note += ' NB2 alpha is estimated.' if estimate_alpha else ' NB2 alpha is fixed, not estimated.'
    if boundary: engine.note += ' NB2 dispersion is zero (Poisson boundary); coefficient inference uses the limiting Poisson model.'
    if estimate_alpha: engine.note += ' Null deviance holds alpha at the full-model estimate; AIC counts the estimated dispersion parameter.'
    if len(y) > 50: engine.note += ' Fitted observations show the first 50 rows; model statistics use all observations.'
    return result
