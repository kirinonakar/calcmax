"""Conjugate Bayesian inference without SciPy, shared by Android and WASM.

Gamma parameters use shape/rate; normal data use a proper normal-inverse-gamma
prior: variance ~ InvGamma(alpha, beta), mean | variance ~ N(mu, variance/kappa).
Intervals are equal-tailed credible intervals, not frequentist confidence intervals.
"""
import mpmath as mp
from calc_shared import MathError, require
from calc_advanced_common import number, table, vector
from calc_advanced_resampling import t_quantile
from calc_statistics import _t_sf


def parameter(args, index, default):
    return mp.mpf(number(args[index])) if len(args) > index else mp.mpf(default)


def beta_cdf(a, b, x):
    """Incomplete beta via Lentz's fraction, including large count posteriors."""
    if x <= 0: return mp.mpf(0)
    if x >= 1: return mp.mpf(1)
    def fraction(a, b, x):
        tiny = mp.eps**2
        c, d = mp.mpf(1), 1-(a+b)*x/(a+1)
        if abs(d) < tiny: d = tiny
        d = 1/d
        h = d
        for m in range(1, 10001):
            for term in (m*(b-m)*x/((a+2*m-1)*(a+2*m)), -(a+m)*(a+b+m)*x/((a+2*m)*(a+2*m+1))):
                d, c = 1+term*d, 1+term/c
                if abs(d) < tiny: d = tiny
                if abs(c) < tiny: c = tiny
                d = 1/d
                delta = d*c
                h *= delta
            if abs(delta-1) < mp.eps*16: return h
        raise MathError('Bayesian beta calculation did not converge')
    front = mp.exp(mp.loggamma(a+b)-mp.loggamma(a)-mp.loggamma(b)+a*mp.log(x)+b*mp.log1p(-x))
    if x < (a+1)/(a+b+2): return front*fraction(a, b, x)/a
    return 1-front*fraction(b, a, 1-x)/b


def positive_quantile(cdf, q, upper):
    """Invert in log coordinates so highly skewed priors retain small quantiles."""
    lo, hi = mp.mpf(-1), mp.log(upper)
    while cdf(mp.exp(lo)) > q: lo *= 2
    for _ in range(100):
        middle = (lo+hi)/2
        if cdf(mp.exp(middle)) < q: lo = middle
        else: hi = middle
    return mp.exp((lo+hi)/2)


def beta_quantile(a, b, q):
    if q > mp.mpf('.5'): return 1-beta_quantile(b, a, 1-q)
    return positive_quantile(lambda x: beta_cdf(a, b, x), q, 1)


def gamma_probability(shape, x, survival=False):
    """Regularized gamma via series/fraction; avoids mpmath's large-shape series."""
    if x <= 0: return mp.mpf(1 if survival else 0)
    front = mp.exp(shape*mp.log(x)-x-mp.loggamma(shape))
    if x < shape+1:
        term = total = 1/shape
        for i in range(1, 10001):
            term *= x/(shape+i)
            total += term
            if abs(term) < abs(total)*mp.eps*16:
                p = front*total
                return 1-p if survival else p
    else:
        tiny = mp.eps**2
        b = x+1-shape
        c, d = 1/tiny, 1/b
        total = d
        for i in range(1, 10001):
            term = -i*(i-shape)
            b += 2
            d, c = term*d+b, b+term/c
            if abs(d) < tiny: d = tiny
            if abs(c) < tiny: c = tiny
            d = 1/d
            delta = d*c
            total *= delta
            if abs(delta-1) < mp.eps*16:
                q = front*total
                return q if survival else 1-q
    raise MathError('Bayesian gamma calculation did not converge')


def gamma_quantile(shape, q):
    # Invert a unit-rate gamma; scale only after inversion.
    cdf = lambda x: gamma_probability(shape, x)
    upper = max(mp.mpf(1), shape)
    while cdf(upper) < q: upper *= 2
    return positive_quantile(cdf, q, upper)


def count_data(data, proportion=False):
    require(isinstance(data, (list, tuple)) and len(data) > 0, 'Enter Bayesian observations or a count table')
    if isinstance(data[0], (list, tuple)):
        rows = table(data, 1, 2)
        require(all(len(row) == 2 for row in rows), 'Use two columns: successes/trials or count/exposure')
        require(all(count >= 0 and count.is_integer() and exposure >= 0 for count, exposure in rows), 'Counts must be nonnegative integers; exposure must be nonnegative')
        if proportion:
            require(all(total.is_integer() and count <= total for count, total in rows), 'Successes must not exceed nonnegative integer trials')
        else:
            require(all(exposure > 0 for _, exposure in rows), 'Each exposure must be positive')
        count = sum(mp.mpf(row[0]) for row in rows)
        total = sum(mp.mpf(row[1]) for row in rows)
        require(total > 0, 'Positive total trials or exposure required')
    else:
        values = vector(data)
        require(all(v >= 0 and v.is_integer() and (not proportion or v <= 1) for v in values), 'Use binary 0/1 observations or nonnegative integer counts')
        count, total = sum(mp.mpf(v) for v in values), mp.mpf(len(values))
    return count, total


def calculate(engine, name, args):
    if name in ('bayesproportion', 'bayesrate'):
        alpha, beta = parameter(args, 1, 1), parameter(args, 2, 1)
        level = parameter(args, 3, '.95')
        threshold = parameter(args, 4, '.5' if name == 'bayesproportion' else 1)
        require(alpha > 0 and beta > 0, 'Prior parameters must be positive')
        require(0 < level < 1, 'Credible level must lie in (0,1)')
        count, total = count_data(args[0], name == 'bayesproportion')
        tail = (1-level)/2
        if name == 'bayesproportion':
            require(0 < threshold < 1, 'Proportion threshold must lie in (0,1)')
            post_a, post_b = alpha+count, beta+total-count
            average = post_a/(post_a+post_b)
            sd = mp.sqrt(post_a*post_b/((post_a+post_b)**2*(post_a+post_b+1)))
            log_bf = mp.loggamma(post_a)+mp.loggamma(post_b)-mp.loggamma(post_a+post_b)
            log_bf -= mp.loggamma(alpha)+mp.loggamma(beta)-mp.loggamma(alpha+beta)
            log_bf -= count*mp.log(threshold)+(total-count)*mp.log1p(-threshold)
            engine.note += ' Beta-Binomial; equal-tailed credible interval. BF10 compares H1: p ~ the chosen Beta prior with H0: p = threshold; it is not a posterior hypothesis probability.'
            return {'successes':count, 'trials':total, 'prior alpha':alpha, 'prior beta':beta,
                    'posterior alpha':post_a, 'posterior beta':post_b, 'posterior mean':average, 'posterior SD':sd,
                    'credible level':level, 'credible interval':[beta_quantile(post_a, post_b, tail), beta_quantile(post_a, post_b, 1-tail)],
                    'threshold':threshold, 'P(p > threshold)':beta_cdf(post_b, post_a, 1-threshold),
                    'next success probability':average, 'BF10':mp.exp(log_bf), 'log BF10':log_bf}
        require(threshold >= 0, 'Rate threshold must be nonnegative')
        post_a, post_b = alpha+count, beta+total
        engine.note += ' Gamma-Poisson; Gamma uses shape/rate. Equal-tailed credible interval; predictive count is for one unit of exposure.'
        return {'events':count, 'exposure':total, 'prior shape':alpha, 'prior rate':beta,
                'posterior shape':post_a, 'posterior rate':post_b, 'posterior mean':post_a/post_b, 'posterior SD':mp.sqrt(post_a)/post_b,
                'credible level':level, 'credible interval':[gamma_quantile(post_a, tail)/post_b, gamma_quantile(post_a, 1-tail)/post_b],
                'threshold':threshold, 'P(rate > threshold)':gamma_probability(post_a, post_b*threshold, survival=True),
                'predictive count mean':post_a/post_b, 'predictive count SD':mp.sqrt(post_a*(post_b+1))/post_b}
    if name == 'bayesmean':
        data = [mp.mpf(v) for v in vector(args[0])]
        mu, kappa = parameter(args, 1, 0), parameter(args, 2, 1)
        alpha, beta = parameter(args, 3, 2), parameter(args, 4, 1)
        level, threshold = parameter(args, 5, '.95'), parameter(args, 6, 0)
        require(kappa > 0 and alpha > 0 and beta > 0, 'Prior kappa, alpha and beta must be positive')
        require(0 < level < 1, 'Credible level must lie in (0,1)')
        n = len(data)
        average = sum(data)/n
        post_k, post_a = kappa+n, alpha+mp.mpf(n)/2
        post_mu = (kappa*mu+n*average)/post_k
        post_b = beta+sum((v-average)**2 for v in data)/2+kappa*n*(average-mu)**2/(2*post_k)
        scale = mp.sqrt(post_b/(post_a*post_k))
        predictive_scale = mp.sqrt(post_b*(post_k+1)/(post_a*post_k))
        critical = t_quantile((1+level)/2, 2*post_a)
        engine.note += ' Normal data, unknown variance; normal-inverse-gamma prior. Mean and next-observation intervals use Student t with 2*posterior alpha degrees of freedom. Intervals are equal-tailed; posterior moments are unavailable when they do not exist.'
        return {'n':n, 'sample mean':average, 'prior mean':mu, 'prior kappa':kappa, 'prior alpha':alpha, 'prior beta':beta,
                'posterior location':post_mu, 'posterior mean':post_mu if post_a > mp.mpf('.5') else None,
                'posterior kappa':post_k, 'posterior alpha':post_a, 'posterior beta':post_b,
                'posterior SD':mp.sqrt(post_b/((post_a-1)*post_k)) if post_a > 1 else None,
                'mean posterior t scale':scale, 'posterior df':2*post_a, 'credible level':level,
                'credible interval':[post_mu-critical*scale, post_mu+critical*scale], 'threshold':threshold,
                'P(mean > threshold)':_t_sf((threshold-post_mu)/scale, 2*post_a),
                'posterior variance mean':post_b/(post_a-1) if post_a > 1 else None,
                'predictive interval':[post_mu-critical*predictive_scale, post_mu+critical*predictive_scale]}
    raise MathError('Unknown Bayesian analysis')
