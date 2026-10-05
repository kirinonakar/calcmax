"""Probability distributions, statistical tests, and regression."""
import math
from statistics import NormalDist
import mpmath as mp
import sympy as s
from sympy.core.relational import Relational
from calc_shared import MathError, flatten, require

def _real_value(value, message):
    require(getattr(value, "is_number", False) and not value.has(s.I), message)
    return value

def _real_or_infinite(value, message):
    if value in (s.oo, -s.oo): return value
    return _real_value(value, message)

def _positive(value, message):
    require(getattr(value, "is_number", False) and value > 0, message)
    return value

def _mpf(value, digits):
    if isinstance(value, s.Rational): return mp.mpf(int(value.p))/mp.mpf(int(value.q))
    return mp.mpf(str(s.N(value, digits + 10)))

def _mp_result(value, engine):
    if value == 0: value = mp.mpf(0)
    return s.Float(str(value), engine.precision)

def _normal_cdf(x):
    return (1 + mp.erf(x/mp.sqrt(2)))/2

def _normal_sf(x):
    return mp.erfc(x/mp.sqrt(2))/2

def _t_tail(t, df):
    """P(T > t) for t >= 0, the accurate half of the symmetric t tail."""
    return mp.betainc(df/2, mp.mpf(1)/2, 0, df/(df + t*t), regularized=True)/2

def _t_cdf(t, df):
    return _t_tail(-t, df) if t <= 0 else 1 - _t_tail(t, df)

def _t_sf(t, df):
    return _t_tail(t, df) if t >= 0 else 1 - _t_tail(-t, df)

def _chisq_cdf(x, df):
    if x <= 0: return mp.mpf(0)
    return mp.gammainc(df/2, 0, x/2, regularized=True)

def _chisq_sf(x, df):
    if x <= 0: return mp.mpf(1)
    return mp.gammainc(df/2, x/2, mp.inf, regularized=True)

def _f_cdf(x, d1, d2):
    if x <= 0: return mp.mpf(0)
    return mp.betainc(d1/2, d2/2, 0, d1*x/(d1*x + d2), regularized=True)

def _f_sf(x, d1, d2):
    if x <= 0: return mp.mpf(1)
    return mp.betainc(d2/2, d1/2, 0, d2/(d2 + d1*x), regularized=True)

def _studentized_range_sf(q, groups, df):
    """Studentized-range survival probability by deterministic normal/chi-square quadrature."""
    if q <= 0: return 1.0
    normal_steps = 120
    normal_step = 16.0/normal_steps
    normal_grid = []
    for index in range(normal_steps + 1):
        z = -8.0 + index*normal_step
        weight = (1 if index in (0, normal_steps) else 4 if index % 2 else 2)*normal_step/3
        normal_grid.append((z, weight*math.exp(-z*z/2)/math.sqrt(2*math.pi), (1+math.erf(z/math.sqrt(2)))/2))
    spread = math.sqrt(2.0/df)
    low, high = max(0.0, 1.0-9*spread), 1.0+9*spread
    variance_steps = 256
    variance_step = (high-low)/variance_steps
    log_scale = math.log(2) + df/2*math.log(df/2) - math.lgamma(df/2)
    total = normalization = 0.0
    for index in range(variance_steps + 1):
        scale = low + index*variance_step
        if scale == 0: continue
        weight = (1 if index in (0, variance_steps) else 4 if index % 2 else 2)*variance_step/3
        density = math.exp(log_scale + (df-1)*math.log(scale) - df*scale*scale/2)
        if density == 0: continue
        interval = q*scale
        cdf = groups*sum(normal_weight*max(0.0, (1+math.erf((z+interval)/math.sqrt(2)))/2 - normal_cdf)**(groups-1)
                         for z, normal_weight, normal_cdf in normal_grid)
        total += weight*density*max(0.0, 1.0-cdf)
        normalization += weight*density
    return min(1.0, max(0.0, total/normalization))

def _bound_survival(sf, bound, digits):
    if bound == s.oo: return mp.mpf(0)
    if bound == -s.oo: return mp.mpf(1)
    return sf(_mpf(bound, digits))

def _quantile(cdf, probability, engine, lower, upper):
    """Invert a monotone cumulative distribution with bracket expansion and bisection."""
    target = _mpf(probability, engine.precision)
    low, high = mp.mpf(lower), mp.mpf(upper)
    width = high - low
    for _ in range(200):
        if cdf(low) < target <= cdf(high): break
        width *= 2
        if cdf(low) >= target: high = low; low = high - width
        else: low = high; high = low + width
    else:
        raise MathError("Numeric quantile did not converge")
    tolerance = mp.mpf(10)**(-(engine.precision + 4))*max(1, abs(low), abs(high))
    while high - low > tolerance:
        middle = (low + high)/2
        if cdf(middle) < target: low = middle
        else: high = middle
    return (low + high)/2

def _tail_probability(sf, statistic, tail):
    """Two-sided p value by default; sf(x) is P(X >= x) for a symmetric distribution."""
    if tail == "left": return min(mp.mpf(1), sf(-statistic))
    if tail == "right": return sf(statistic)
    return min(mp.mpf(1), 2*sf(abs(statistic)))

def _tail_argument(a, nodes):
    if nodes and isinstance(nodes[-1], dict) and nodes[-1].get("kind") == "symbol" and nodes[-1].get("value") in ("left", "right", "both"):
        return nodes[-1]["value"], a[:-1]
    return "both", a

def _sample_statistics(data):
    values = flatten(data)
    require(len(values) >= 2, "Enter at least two data values")
    for value in values: _real_value(value, "Sample values must be real numbers")
    n = s.Integer(len(values))
    mean = s.Add(*values)/n
    sd = s.sqrt(s.Add(*[(value - mean)**2 for value in values])/(n - 1))
    require(sd > 0, "The sample needs some variation")
    return mean, sd, n

def _sample_mean_variance(data):
    values = flatten(data)
    require(len(values) >= 2, "Enter at least two values in each sample")
    for value in values: _real_value(value, "Sample values must be real numbers")
    n = s.Integer(len(values))
    mean = s.Add(*values)/n
    variance = s.Add(*[(value - mean)**2 for value in values])/(n - 1)
    return mean, variance, n

def _data_center(samples):
    values = flatten(samples)
    require(values, "Enter at least one data value")
    for value in values: _real_value(value, "Sample values must be real numbers")
    return s.Add(*values)/s.Integer(len(values)), s.Integer(len(values))

def _confidence_level(value):
    _real_value(value, "The confidence level must be a number")
    level = value/100 if value > 1 else value
    require(0 < level < 1, "The confidence level must be between 0 and 1, or between 1 and 100 percent")
    return level

def _shapiro_wilk(data):
    """Shapiro-Wilk W with Royston's AS R94 p-value approximation (3 <= n <= 5000)."""
    values = flatten(data)
    n = len(values)
    require(3 <= n <= 5000, "Shapiro-Wilk needs 3 to 5000 values")
    for value in values: _real_value(value, "Shapiro-Wilk values must be real numbers")
    try:
        ordered = sorted(float(value) for value in values)
    except (ValueError, OverflowError, TypeError):
        raise MathError("Shapiro-Wilk values must be finite numbers")
    require(all(math.isfinite(value) for value in ordered), "Shapiro-Wilk values must be finite numbers")
    spread = ordered[-1] - ordered[0]
    require(math.isfinite(spread) and spread > 0, "Shapiro-Wilk needs a finite range with variation")
    # Affine scaling keeps the centered sum of squares stable for small or large units.
    scaled = [(value - ordered[0])/spread for value in ordered]
    average = math.fsum(scaled)/n
    denominator = math.fsum((value - average)**2 for value in scaled)
    half = n//2
    if n == 3:
        coefficients = [math.sqrt(0.5)]
    else:
        normal = NormalDist()
        scores = [normal.inv_cdf((i + 0.625)/(n + 0.25)) for i in range(half)]
        norm = math.sqrt(2*math.fsum(score*score for score in scores))
        inv_root_n = 1/math.sqrt(n)
        def poly(coefficients, x):
            result = coefficients[-1]
            for coefficient in reversed(coefficients[:-1]): result = result*x + coefficient
            return result
        first = -scores[0]/norm + poly([0, .221157, -.147981, -2.07119, 4.434685, -2.706056], inv_root_n)
        if n <= 5:
            start = 1
            factor = math.sqrt((2*math.fsum(score*score for score in scores[1:]))/(1 - 2*first*first))
            coefficients = [first]
        else:
            second = -scores[1]/norm + poly([0, .042981, -.293762, -1.752461, 5.682633, -3.582633], inv_root_n)
            start = 2
            factor = math.sqrt((2*math.fsum(score*score for score in scores[2:]))/(1 - 2*first*first - 2*second*second))
            coefficients = [first, second]
        coefficients.extend(-score/factor for score in scores[start:])
    numerator = math.fsum(coefficient*(scaled[n - 1 - i] - scaled[i]) for i, coefficient in enumerate(coefficients))
    w = min(1.0, max(0.0, numerator*numerator/denominator))
    if n == 3:
        w = max(.75, w)
        p = 1 - 6/math.pi*math.acos(math.sqrt(w))
    else:
        log_one_minus_w = math.log1p(-w) if w < 1 else -math.inf
        if n <= 11:
            gamma = -2.273 + .459*n
            if log_one_minus_w >= gamma:
                p = 1e-19
            else:
                transformed = -math.log(gamma - log_one_minus_w)
                mean = poly([.544, -.39978, .025054, -.0006714], n)
                scale = math.exp(poly([1.3822, -.77857, .062767, -.0020322], n))
                p = math.erfc((transformed - mean)/(scale*math.sqrt(2)))/2
        else:
            log_n = math.log(n)
            mean = poly([-1.5861, -.31082, -.083751, .0038915], log_n)
            scale = math.exp(poly([-.4803, -.082676, .0030302], log_n))
            p = math.erfc((log_one_minus_w - mean)/(scale*math.sqrt(2)))/2
    return w, min(1.0, max(0.0, p)), n

def _binom_term(n, p, k):
    return s.binomial(n, k)*p**k*(1 - p)**(n - k)

def distribution_value(engine, name, a):
    digits = engine.precision
    if name in ("cauchypdf", "cauchycdf", "invcauchy"):
        interval = name == "cauchycdf" and len(a) in (2,4)
        require(len(a) in ((1,2,3,4) if name == "cauchycdf" else (1,3)),
                name + " takes a value with optional location x₀ and scale γ" + (", or two bounds" if name == "cauchycdf" else ""))
        location, scale = a[-2:] if len(a) in (3,4) else (s.Integer(0),s.Integer(1))
        require(location.is_real is True and location.is_finite is True, "Cauchy location must be a finite real number")
        require(scale.is_real is True and scale.is_finite is True and scale > 0, "Cauchy scale must be finite and positive")
        def cdf(x):
            if x == -s.oo: return s.Integer(0)
            if x == s.oo: return s.Integer(1)
            return s.atan2(scale,location-x)/s.pi
        def sf(x):
            if x == -s.oo: return s.Integer(1)
            if x == s.oo: return s.Integer(0)
            return s.atan2(scale,x-location)/s.pi
        x = _real_or_infinite(a[0], name + " requires a real value")
        require(x in (s.oo,-s.oo) or x.is_real is True and x.is_finite is True, name + " requires a real value")
        if name == "invcauchy":
            require(0 <= x <= 1, "invcauchy requires a probability between 0 and 1")
            if x == 0: return -s.oo
            if x == 1: return s.oo
            if x == s.Rational(1,2): return location
            return location-scale*s.cot(s.pi*x) if x < s.Rational(1,2) else location+scale*s.cot(s.pi*(1-x))
        if name == "cauchypdf":
            if x in (s.oo,-s.oo): return s.Integer(0)
            return 1/(s.pi*scale*(1+((x-location)/scale)**2))
        if not interval: return cdf(x)
        high = _real_or_infinite(a[1], "cauchycdf requires real bounds")
        require(high in (s.oo,-s.oo) or high.is_real is True and high.is_finite is True, "cauchycdf requires real bounds")
        require(x <= high, "The lower bound must not be above the upper bound")
        return sf(x)-sf(high) if x >= location else cdf(high)-cdf(x)
    if name == "normpdf":
        require(len(a) in (1, 3), "normpdf takes x, or x with μ and σ")
        x, mu, sigma = (a[0], s.Integer(0), s.Integer(1)) if len(a) == 1 else a
        _real_value(x, "normpdf requires numeric arguments")
        _real_value(mu, "normpdf requires numeric arguments")
        _positive(sigma, "Standard deviation must be positive")
        z = (x - mu)/sigma
        return s.exp(-z**2/2)/(sigma*s.sqrt(2*s.pi))
    if name == "normcdf":
        require(len(a) in (1, 2, 4), "normcdf takes one bound, two bounds, or two bounds with μ and σ")
        if len(a) == 1:
            x = _real_or_infinite(a[0], "normcdf requires a numeric bound")
            if x == s.oo: return s.Integer(1)
            if x == -s.oo: return s.Integer(0)
            return (s.erf(x/s.sqrt(2)) + 1)/2
        low = _real_or_infinite(a[0], "normcdf requires real bounds")
        high = _real_or_infinite(a[1], "normcdf requires real bounds")
        require(high >= low, "The lower bound must not be above the upper bound")
        if len(a) == 2: mu, sigma = s.Integer(0), s.Integer(1)
        else: mu, sigma = _real_value(a[2], "normcdf requires a numeric μ"), _positive(a[3], "Standard deviation must be positive")
        return (s.erf((high - mu)/(sigma*s.sqrt(2))) - s.erf((low - mu)/(sigma*s.sqrt(2))))/2
    if name == "invnorm":
        require(len(a) in (1, 3), "invnorm takes a probability, or a probability with μ and σ")
        p = a[0]
        mu, sigma = (s.Integer(0), s.Integer(1)) if len(a) == 1 else (a[1], a[2])
        require(getattr(p, "is_number", False) and not p.has(s.I) and 0 < p < 1, "invnorm requires a probability between 0 and 1")
        _real_value(mu, "invnorm requires a numeric μ")
        _positive(sigma, "Standard deviation must be positive")
        if p == s.Rational(1, 2): return mu
        with mp.workdps(digits + 10):
            return _mp_result(_mpf(mu, digits) + _mpf(sigma, digits)*_quantile(_normal_cdf, p, engine, -2, 2), engine)
    if name == "tpdf":
        require(len(a) == 2, "tpdf takes x and the degrees of freedom")
        x, df = a
        _real_value(x, "tpdf requires numeric arguments")
        _positive(df, "Degrees of freedom must be positive")
        return s.gamma((df + 1)/2)/(s.sqrt(df*s.pi)*s.gamma(df/2))*(1 + x**2/df)**(-(df + 1)/2)
    if name == "tcdf":
        require(len(a) in (2, 3), "tcdf takes a bound and df, or two bounds and df")
        if len(a) == 2:
            x = _real_or_infinite(a[0], "tcdf requires a real bound")
            df = _positive(a[1], "Degrees of freedom must be positive")
            if x == s.oo: return s.Integer(1)
            if x == -s.oo: return s.Integer(0)
            with mp.workdps(digits + 10): return _mp_result(_t_cdf(_mpf(x, digits), _mpf(df, digits)), engine)
        low = _real_or_infinite(a[0], "tcdf requires real bounds")
        high = _real_or_infinite(a[1], "tcdf requires real bounds")
        df = _positive(a[2], "Degrees of freedom must be positive")
        require(high >= low, "The lower bound must not be above the upper bound")
        with mp.workdps(digits + 10):
            df_mp = _mpf(df, digits)
            def survival(bound): return _bound_survival(lambda x: _t_sf(x, df_mp), bound, digits)
            return _mp_result(survival(low) - survival(high), engine)
    if name == "invt":
        require(len(a) == 2, "invt takes a probability and the degrees of freedom")
        p, df = a
        require(getattr(p, "is_number", False) and not p.has(s.I) and 0 < p < 1, "invt requires a probability between 0 and 1")
        _positive(df, "Degrees of freedom must be positive")
        if p == s.Rational(1, 2): return s.Integer(0)
        with mp.workdps(digits + 10):
            df_mp = _mpf(df, digits)
            return _mp_result(_quantile(lambda x: _t_cdf(x, df_mp), p, engine, -2, 2), engine)
    if name == "chi2pdf":
        require(len(a) == 2, "chi2pdf takes x and the degrees of freedom")
        x, df = a
        _real_value(x, "chi2pdf requires numeric arguments")
        _positive(df, "Degrees of freedom must be positive")
        require(x >= 0, "chi2pdf is defined for x ≥ 0")
        require(x > 0 or df >= 2, "chi2pdf is not finite at x = 0 below df = 2")
        return x**(df/2 - 1)*s.exp(-x/2)/(2**(df/2)*s.gamma(df/2))
    if name == "chi2cdf":
        require(len(a) in (2, 3), "chi2cdf takes a bound and df, or two bounds and df")
        if len(a) == 2:
            x = _real_or_infinite(a[0], "chi2cdf requires a real bound")
            df = _positive(a[1], "Degrees of freedom must be positive")
            if x == s.oo: return s.Integer(1)
            if x == -s.oo: return s.Integer(0)
            with mp.workdps(digits + 10): return _mp_result(_chisq_cdf(_mpf(x, digits), _mpf(df, digits)), engine)
        low = _real_or_infinite(a[0], "chi2cdf requires real bounds")
        high = _real_or_infinite(a[1], "chi2cdf requires real bounds")
        df = _positive(a[2], "Degrees of freedom must be positive")
        require(high >= low, "The lower bound must not be above the upper bound")
        with mp.workdps(digits + 10):
            df_mp = _mpf(df, digits)
            def survival(bound): return _bound_survival(lambda x: _chisq_sf(x, df_mp), bound, digits)
            return _mp_result(survival(low) - survival(high), engine)
    if name == "fpdf":
        require(len(a) == 3, "fpdf takes x and the two degrees of freedom")
        x, d1, d2 = a
        _real_value(x, "fpdf requires numeric arguments")
        _positive(d1, "Degrees of freedom must be positive")
        _positive(d2, "Degrees of freedom must be positive")
        require(x > 0, "fpdf is defined for x > 0")
        return s.sqrt((d1*x)**d1*d2**d2/(d1*x + d2)**(d1 + d2))/(x*s.beta(d1/2, d2/2))
    if name == "fcdf":
        require(len(a) in (3, 4), "fcdf takes a bound and two degrees of freedom, or two bounds")
        if len(a) == 3:
            x = _real_or_infinite(a[0], "fcdf requires a real bound")
            d1 = _positive(a[1], "Degrees of freedom must be positive")
            d2 = _positive(a[2], "Degrees of freedom must be positive")
            if x == s.oo: return s.Integer(1)
            if x == -s.oo: return s.Integer(0)
            with mp.workdps(digits + 10): return _mp_result(_f_cdf(_mpf(x, digits), _mpf(d1, digits), _mpf(d2, digits)), engine)
        low = _real_or_infinite(a[0], "fcdf requires real bounds")
        high = _real_or_infinite(a[1], "fcdf requires real bounds")
        d1 = _positive(a[2], "Degrees of freedom must be positive")
        d2 = _positive(a[3], "Degrees of freedom must be positive")
        require(high >= low, "The lower bound must not be above the upper bound")
        with mp.workdps(digits + 10):
            d1_mp, d2_mp = _mpf(d1, digits), _mpf(d2, digits)
            def survival(bound): return _bound_survival(lambda x: _f_sf(x, d1_mp, d2_mp), bound, digits)
            return _mp_result(survival(low) - survival(high), engine)
    if name in ("binompdf", "binomcdf"):
        require(len(a) in (2, 3), name + " takes n, p and optionally k")
        n, p = a[0], a[1]
        require(n.is_Integer and 0 < n <= 1000, "binom n must be an integer from 1 to 1000")
        require(getattr(p, "is_number", False) and 0 <= p <= 1, "binom p must be a probability")
        count = int(n)
        if len(a) == 3:
            k = a[2]
            require(k.is_Integer and 0 <= k <= n, "binom k must be an integer from 0 to n")
            if name == "binompdf": return _binom_term(count, p, int(k))
            return s.Add(*[_binom_term(count, p, index) for index in range(int(k) + 1)])
        require(count <= 100, "Use a k value for a single probability when n is above 100")
        probabilities = [_binom_term(count, p, index) for index in range(count + 1)]
        if name == "binompdf": return probabilities
        running, cumulative = s.Integer(0), []
        for term in probabilities:
            running = running + term
            cumulative.append(running)
        return cumulative
    if name in ("poissonpdf", "poissoncdf"):
        require(len(a) == 2, name + " takes the mean μ and k")
        mu, k = a
        _positive(mu, "The Poisson mean must be positive")
        require(k.is_Integer and 0 <= k <= 10000, "Poisson k must be an integer from 0 to 10000")
        count = int(k)
        if name == "poissonpdf": return s.exp(-mu)*mu**count/s.factorial(count)
        if count <= 200: return s.exp(-mu)*s.Add(*[mu**index/s.factorial(index) for index in range(count + 1)])
        with mp.workdps(digits + 10):
            return _mp_result(mp.gammainc(count + 1, _mpf(mu, digits), mp.inf, regularized=True), engine)
    if name in ("geometpdf", "geometcdf"):
        require(len(a) == 2, name + " takes p and k")
        p, k = a
        require(getattr(p, "is_number", False) and 0 < p <= 1, "geomet p must be a probability above 0")
        require(k.is_Integer and 1 <= k <= 10**6, "geomet k must be a positive integer")
        if name == "geometpdf": return (1 - p)**(int(k) - 1)*p
        return 1 - (1 - p)**int(k)
    if name in ("nbinompdf", "nbinomcdf"):
        require(len(a) == 3, name + " takes required successes r, probability p and failures k")
        r, p, k = a
        require(r.is_Integer and 1 <= r <= 100000, "nbinom r must be an integer from 1 to 100000")
        require(getattr(p, "is_number", False) and p.is_real and 0 < p <= 1, "nbinom p must be a probability above 0")
        _real_or_infinite(k, name + " requires a real count")
        engine.note = "Negative binomial X counts failures before the r-th success (starting at 0). Total trials = X + r."
        if k < 0 or name == "nbinompdf" and (k in (s.oo,-s.oo) or not k.is_Integer): return s.Integer(0)
        if name == "nbinomcdf" and k == s.oo: return s.Integer(1)
        if p == 1: return s.Integer(1 if name == "nbinomcdf" or k == 0 else 0)
        count = int(s.floor(k))
        if count <= 200 and r <= 200:
            if name == "nbinompdf": return s.binomial(count+r-1,count)*p**r*(1-p)**count
            return s.Add(*[s.binomial(i+r-1,i)*p**r*(1-p)**i for i in range(count+1)])
        from calc_probability import probability
        result = probability(dict(distribution="negativeBinomial", operation="eq" if name == "nbinompdf" else "le", precision=digits, preview=False,
                                  values={"r":str(r),"p":str(s.N(p,digits+10)),"x":str(count)}))
        return s.Float(result["value"], digits)
    if name in ("hgeompdf", "hgeomcdf"):
        require(len(a) == 4, name + " takes population N, success items K, draws n and count k")
        population, successes, draws, k = a
        require(all(v.is_Integer for v in (population,successes,draws)) and 1 <= population <= 10000 and 0 <= successes <= population and 0 <= draws <= population,
                "hgeom requires 1 ≤ N ≤ 10000 and integer 0 ≤ K, n ≤ N")
        _real_or_infinite(k, name + " requires a real count")
        lo, hi = max(0,draws-(population-successes)), min(draws,successes)
        if k < lo: return s.Integer(0)
        if name == "hgeomcdf" and k >= hi: return s.Integer(1)
        if name == "hgeompdf" and (k > hi or not k.is_Integer): return s.Integer(0)
        denominator = math.comb(int(population),int(draws))
        def mass(i): return math.comb(int(successes),i)*math.comb(int(population-successes),int(draws)-i)
        if name == "hgeompdf": numerator = mass(int(k))
        else:
            count = int(s.floor(k))
            numerator = sum(mass(i) for i in range(int(lo),count+1)) if count-lo <= hi-count else denominator-sum(mass(i) for i in range(count+1,int(hi)+1))
        return s.Rational(numerator,denominator)
    if name in ("weibullpdf", "weibullcdf"):
        require(len(a) in (2,3), name + " takes x and shape k, with an optional scale λ")
        x, shape = a[:2]
        scale = a[2] if len(a) == 3 else s.Integer(1)
        _real_or_infinite(x, name + " requires a real bound")
        _positive(shape, "The shape must be positive")
        _positive(scale, "The scale must be positive")
        require(shape.is_finite and scale.is_finite, "Weibull parameters must be finite")
        if x < 0: return s.Integer(0)
        if x == s.oo: return s.Integer(1 if name == "weibullcdf" else 0)
        if x == 0:
            if name == "weibullcdf" or shape > 1: return s.Integer(0)
            return 1/scale if shape == 1 else s.oo
        power = (x/scale)**shape
        if name == "weibullcdf": return 1-s.exp(-power)
        return shape/scale*(x/scale)**(shape-1)*s.exp(-power)
    if name in ("exppdf", "expcdf"):
        require(len(a) in (1, 2), name + " takes x, or x with the rate λ")
        x, rate = (a[0], s.Integer(1)) if len(a) == 1 else a
        _real_value(x, name + " requires a numeric argument")
        _positive(rate, "The rate must be positive")
        require(x >= 0, name + " is defined for x ≥ 0")
        if name == "exppdf": return rate*s.exp(-rate*x)
        if x == 0: return s.Integer(0)
        return 1 - s.exp(-rate*x)
    if name in ("unifpdf", "unifcdf"):
        require(len(a) in (1, 3), name + " takes x, or x with the bounds a and b")
        x = _real_value(a[0], name + " requires a numeric argument")
        low, high = (s.Integer(0), s.Integer(1)) if len(a) == 1 else (a[1], a[2])
        _real_value(low, name + " requires numeric bounds")
        _real_value(high, name + " requires numeric bounds")
        require(high > low, "The upper bound must be above the lower bound")
        if name == "unifpdf": return 1/(high - low) if low <= x <= high else s.Integer(0)
        if x <= low: return s.Integer(0)
        if x >= high: return s.Integer(1)
        return (x - low)/(high - low)
    if name in ("gammapdf", "gammacdf"):
        require(len(a) in (2, 3), name + " takes x and the shape k, with an optional scale θ")
        x, shape = a[0], a[1]
        scale = a[2] if len(a) == 3 else s.Integer(1)
        _real_value(x, name + " requires numeric arguments")
        _positive(shape, "The shape must be positive")
        _positive(scale, "The scale must be positive")
        require(x >= 0, name + " is defined for x ≥ 0")
        if name == "gammapdf":
            require(x > 0 or shape >= 1, name + " is not finite at x = 0 below shape 1")
            return x**(shape - 1)*s.exp(-x/scale)/(s.gamma(shape)*scale**shape)
        if x == 0: return s.Integer(0)
        with mp.workdps(digits + 10):
            return _mp_result(mp.gammainc(_mpf(shape, digits), 0, _mpf(x/scale, digits), regularized=True), engine)
    if name in ("betapdf", "betacdf"):
        require(len(a) == 3, name + " takes x and the two shape parameters α and β")
        x, alpha, beta_shape = a
        _real_value(x, name + " requires a numeric argument")
        _positive(alpha, "The first shape must be positive")
        _positive(beta_shape, "The second shape must be positive")
        require(0 <= x <= 1, name + " is defined on 0 ≤ x ≤ 1")
        if name == "betapdf":
            require(0 < x < 1 or (alpha > 1 and beta_shape > 1), name + " is not finite at the endpoints")
            return x**(alpha - 1)*(1 - x)**(beta_shape - 1)/s.beta(alpha, beta_shape)
        if x == 0: return s.Integer(0)
        if x == 1: return s.Integer(1)
        with mp.workdps(digits + 10):
            return _mp_result(mp.betainc(_mpf(alpha, digits), _mpf(beta_shape, digits), 0, _mpf(x, digits), regularized=True), engine)
    if name in ("lognormpdf", "lognormcdf"):
        require(len(a) in (1, 3), name + " takes x, or x with μ and σ")
        x, mu, sigma = (a[0], s.Integer(0), s.Integer(1)) if len(a) == 1 else a
        _real_value(x, name + " requires numeric arguments")
        _real_value(mu, name + " requires a numeric μ")
        _positive(sigma, "Standard deviation must be positive")
        require(x > 0, name + " is defined for x > 0")
        shift = (s.log(x) - mu)/sigma
        if name == "lognormpdf": return s.exp(-shift**2/2)/(x*sigma*s.sqrt(2*s.pi))
        return (s.erf(shift/s.sqrt(2)) + 1)/2
    raise MathError("Unknown distribution: " + name)

def statistical_test(engine, name, a, nodes):
    digits = engine.precision
    tail, args = _tail_argument(a, nodes)
    if name in ("wilcoxon", "mannwhitney", "kruskal"):
        from calc_inference import rank_test
        return rank_test(engine, name, args, tail)
    if tail != "both": engine.note = "One-tailed probability (" + tail + " tail)."
    if name == "shapiro":
        require(len(args) == 1 and isinstance(args[0], (list, tuple)), "shapiro takes one data list")
        w, p, n = _shapiro_wilk(args[0])
        precision = min(engine.precision, 15)
        return {"W": s.Float(str(w), precision), "p value": s.Float(str(p), precision), "n": s.Integer(n)}
    if name == "ttest":
        require(len(args) in (2, 4), "ttest takes μ0 and data, or μ0, x̄, s and n")
        mu0 = _real_value(args[0], "ttest requires a numeric μ0")
        if len(args) == 2:
            require(isinstance(args[1], (list, tuple)), "ttest data must be a list")
            mean, sd, n = _sample_statistics(args[1])
        else:
            mean, sd, n = args[1], args[2], args[3]
            _real_value(mean, "ttest requires numeric summary values")
            _positive(sd, "The sample SD must be positive")
            require(n.is_Integer and n >= 2, "ttest n must be an integer of at least 2")
        with mp.workdps(digits + 10):
            statistic = (_mpf(mean, digits) - _mpf(mu0, digits))/(_mpf(sd, digits)/mp.sqrt(_mpf(n, digits)))
            probability = _tail_probability(lambda t: _t_sf(t, _mpf(n - 1, digits)), statistic, tail)
            return {"t": _mp_result(statistic, engine), "df": s.Integer(n - 1), "p value": _mp_result(probability, engine),
                    "sample mean": mean, "sample SD": sd, "n": s.Integer(n)}
    if name in ("ttest2", "ttestpaired"):
        require(len(args) == 3, name + " takes Δ0, x and y data lists")
        delta = _real_value(args[0], "The hypothesized difference must be real")
        require(isinstance(args[1], (list, tuple)) and isinstance(args[2], (list, tuple)), "x and y must be data lists")
        if name == "ttestpaired":
            xs, ys = flatten(args[1]), flatten(args[2])
            require(len(xs) == len(ys) and len(xs) >= 2, "Paired t test needs at least two complete pairs")
            mean, sd, n = _sample_statistics([x - y for x, y in zip(xs, ys)])
            df = n - 1
            standard_error = sd/s.sqrt(n)
        else:
            mean_x, variance_x, nx = _sample_mean_variance(args[1])
            mean_y, variance_y, ny = _sample_mean_variance(args[2])
            mean = mean_x - mean_y
            standard_error_squared = variance_x/nx + variance_y/ny
            require(standard_error_squared > 0, "The samples need some variation")
            standard_error = s.sqrt(standard_error_squared)
            df = standard_error_squared**2/((variance_x/nx)**2/(nx - 1) + (variance_y/ny)**2/(ny - 1))
        with mp.workdps(digits + 10):
            statistic = (_mpf(mean, digits) - _mpf(delta, digits))/_mpf(standard_error, digits)
            probability = _tail_probability(lambda t: _t_sf(t, _mpf(df, digits)), statistic, tail)
            result = {"t": _mp_result(statistic, engine), "df": df, "p value": _mp_result(probability, engine),
                      "mean difference": mean}
            if name == "ttestpaired": result["pairs"] = n
            else: result.update({"n x": nx, "n y": ny})
            return result
    if name == "ztest":
        require(len(args) in (3, 4), "ztest takes μ0, σ and data, or μ0, σ, x̄ and n")
        mu0 = _real_value(args[0], "ztest requires a numeric μ0")
        sigma = _positive(args[1], "σ must be positive")
        if len(args) == 3:
            require(isinstance(args[2], (list, tuple)), "ztest data must be a list")
            mean, n = _data_center(args[2])
        else:
            mean, n = args[2], args[3]
            _real_value(mean, "ztest requires numeric summary values")
            require(n.is_Integer and n >= 1, "ztest n must be a positive integer")
        with mp.workdps(digits + 10):
            statistic = (_mpf(mean, digits) - _mpf(mu0, digits))/(_mpf(sigma, digits)/mp.sqrt(_mpf(n, digits)))
            probability = _tail_probability(_normal_sf, statistic, tail)
            return {"z": _mp_result(statistic, engine), "p value": _mp_result(probability, engine),
                    "sample mean": mean, "n": s.Integer(n)}
    if name == "ztest2":
        require(len(args) == 5, "ztest2 takes Δ0, σx, σy, x and y data lists")
        delta = _real_value(args[0], "The hypothesized difference must be real")
        sigma_x = _positive(args[1], "σx must be positive")
        sigma_y = _positive(args[2], "σy must be positive")
        require(isinstance(args[3], (list, tuple)) and isinstance(args[4], (list, tuple)), "x and y must be data lists")
        mean_x, nx = _data_center(args[3])
        mean_y, ny = _data_center(args[4])
        with mp.workdps(digits + 10):
            statistic = (_mpf(mean_x - mean_y - delta, digits)/
                         mp.sqrt(_mpf(sigma_x**2/nx + sigma_y**2/ny, digits)))
            probability = _tail_probability(_normal_sf, statistic, tail)
            return {"z": _mp_result(statistic, engine), "p value": _mp_result(probability, engine),
                    "mean difference": mean_x - mean_y, "n x": nx, "n y": ny}
    if name == "chi2test":
        require(len(args) == 2, "chi2test takes observed and expected counts")
        observed, expected = flatten(args[0]), flatten(args[1])
        require(len(observed) == len(expected) and len(observed) >= 2, "chi2test needs two lists of equal length with at least two counts")
        for value in observed + expected: _real_value(value, "Counts must be real numbers")
        require(all(value > 0 for value in expected), "Expected counts must be positive")
        statistic = s.Add(*[((o - e)**2)/e for o, e in zip(observed, expected)])
        df = len(observed) - 1
        with mp.workdps(digits + 10):
            probability = _chisq_sf(_mpf(statistic, digits), _mpf(s.Integer(df), digits))
            return {"chi-square": statistic, "df": s.Integer(df), "p value": _mp_result(probability, engine)}
    if name == "chi2independence":
        require(len(args) in (2, 3), "chi2independence takes x and y category lists, optionally correction (1 or 0)")
        correction = args[2] if len(args) == 3 else s.Integer(1)
        require(correction in (s.Integer(0), s.Integer(1)), "Yates correction must be 1 (on) or 0 (off)")
        xs, ys = flatten(args[0]), flatten(args[1])
        require(len(xs) == len(ys) and len(xs) >= 2, "χ² independence needs at least two complete pairs")
        for value in xs + ys: _real_value(value, "Categories must be real numbers")
        x_categories, y_categories = sorted(set(xs)), sorted(set(ys))
        require(len(x_categories) >= 2 and len(y_categories) >= 2, "Each category column needs at least two distinct values")
        counts = [[s.Integer(sum(x == xc and y == yc for x, y in zip(xs, ys))) for yc in y_categories] for xc in x_categories]
        row_totals = [sum(row) for row in counts]
        column_totals = [sum(row[j] for row in counts) for j in range(len(y_categories))]
        n = s.Integer(len(xs))
        df = s.Integer((len(x_categories) - 1)*(len(y_categories) - 1))
        corrected = correction == 1 and df == 1
        engine.note = ("Yates continuity correction applied (2×2 table)." if corrected else
                       "Yates continuity correction only applies to 2×2 tables; Pearson χ² used." if correction == 1 else
                       "Pearson χ² without continuity correction.")
        if any(row_total*column_total/n < 5 for row_total in row_totals for column_total in column_totals):
            engine.note += " Some expected counts are below 5; the χ² approximation may be inaccurate."
        terms = []
        for i in range(len(x_categories)):
            for j in range(len(y_categories)):
                expected = row_totals[i]*column_totals[j]/n
                difference = abs(counts[i][j] - expected)
                if corrected:
                    difference = max(s.Integer(0), difference - s.Rational(1, 2))
                terms.append(difference**2/expected)
        statistic = s.Add(*terms)
        with mp.workdps(digits + 10):
            probability = _chisq_sf(_mpf(statistic, digits), _mpf(df, digits))
            return {"chi-square": statistic, "df": df, "p value": _mp_result(probability, engine),
                    "Yates correction": s.Integer(int(corrected)), "observed": counts, "n": n}
    if name == "fisherexact":
        require(len(args) == 2, "fisherexact takes x and y category lists")
        require(isinstance(args[0], (list, tuple)) and isinstance(args[1], (list, tuple)), "x and y must be category lists")
        xs, ys = flatten(args[0]), flatten(args[1])
        require(len(xs) == len(ys) and len(xs) >= 2, "Fisher exact test needs at least two complete pairs")
        for value in xs + ys: _real_value(value, "Categories must be real numbers")
        x_categories, y_categories = sorted(set(xs)), sorted(set(ys))
        require(len(x_categories) == 2 and len(y_categories) == 2, "Fisher exact test needs exactly two categories in each column")
        a = sum(x == x_categories[0] and y == y_categories[0] for x, y in zip(xs, ys))
        b = sum(x == x_categories[0] and y == y_categories[1] for x, y in zip(xs, ys))
        c = sum(x == x_categories[1] and y == y_categories[0] for x, y in zip(xs, ys))
        d = sum(x == x_categories[1] and y == y_categories[1] for x, y in zip(xs, ys))
        row_first, column_first, total = a + b, a + c, len(xs)
        denominator = math.comb(total, row_first)
        def probability(top_left):
            return s.Rational(math.comb(column_first, top_left)*math.comb(total - column_first, row_first - top_left), denominator)
        observed_probability = probability(a)
        support = range(max(0, row_first + column_first - total), min(row_first, column_first) + 1)
        if tail == "left": selected = (value for value in support if value <= a)
        elif tail == "right": selected = (value for value in support if value >= a)
        else: selected = (value for value in support if probability(value) <= observed_probability)
        p = sum((probability(value) for value in selected), s.Integer(0))
        odds = s.oo if b*c == 0 else s.Rational(a*d, b*c)
        return {"odds ratio": odds, "p value": p, "observed": [[a, b], [c, d]], "n": s.Integer(total)}
    if name in ("anova", "tukey"):
        require(len(args) >= 2, name + " takes two or more data lists")
        groups = []
        for group in args:
            require(isinstance(group, (list, tuple)), name + " arguments must be data lists")
            values = flatten(group)
            require(len(values) >= 2, "Each " + name + " group needs at least two values")
            for value in values: _real_value(value, "Sample values must be real numbers")
            groups.append(values)
        total = sum(len(group) for group in groups)
        means = [s.Add(*group)/s.Integer(len(group)) for group in groups]
        grand = s.Add(*[value for group in groups for value in group])/s.Integer(total)
        between = s.Add(*[s.Integer(len(group))*(mean - grand)**2 for group, mean in zip(groups, means)])
        within = s.Add(*[s.Add(*[(value - mean)**2 for value in group]) for group, mean in zip(groups, means)])
        require(within != 0, name + " needs variation inside the groups")
        count = len(groups)
        if name == "tukey":
            degrees = total-count
            mse = within/degrees
            result = {}
            labels = ["x", "y", "z"] + ["group " + str(index+1) for index in range(3, count)]
            for left in range(count):
                for right in range(left+1, count):
                    difference = means[left]-means[right]
                    standard_error = s.sqrt(mse*(s.Rational(1, len(groups[left]))+s.Rational(1, len(groups[right])))/2)
                    q = abs(float(s.N(difference/standard_error, min(digits+4, 30))))
                    require(math.isfinite(q), "Tukey statistic is outside the numeric range")
                    key = labels[left] + "-" + labels[right]
                    result[key + " mean difference"] = difference
                    result[key + " adjusted p value"] = s.Float(format(_studentized_range_sf(q, count, degrees), ".8g"), digits)
            engine.note = "Tukey-Kramer pairwise comparisons using pooled variance. Adjusted p values account for all groups."
            return result
        statistic = (between/(count - 1))/(within/(total - count))
        with mp.workdps(digits + 10):
            probability = _f_sf(_mpf(statistic, digits), _mpf(s.Integer(count - 1), digits), _mpf(s.Integer(total - count), digits))
            return {"F": statistic, "df numerator": s.Integer(count - 1), "df denominator": s.Integer(total - count), "p value": _mp_result(probability, engine)}
    if name in ("tinterval", "zinterval"):
        if name == "tinterval":
            require(len(args) in (2, 4), "tinterval takes a confidence level and data, or a level, x̄, s and n")
            level = _confidence_level(args[0])
            if len(args) == 2:
                require(isinstance(args[1], (list, tuple)), "tinterval data must be a list")
                mean, sd, n = _sample_statistics(args[1])
            else:
                mean, sd, n = args[1], args[2], args[3]
                _real_value(mean, "tinterval requires numeric summary values")
                _positive(sd, "The sample SD must be positive")
                require(n.is_Integer and n >= 2, "tinterval n must be an integer of at least 2")
            with mp.workdps(digits + 10):
                critical = _quantile(lambda x: _t_cdf(x, _mpf(n - 1, digits)), (1 + level)/2, engine, -2, 2)
                margin = critical*_mpf(sd, digits)/mp.sqrt(_mpf(n, digits))
                center = _mpf(mean, digits)
                return {"confidence interval": [_mp_result(center - margin, engine), _mp_result(center + margin, engine)],
                        "sample mean": mean, "sample SD": sd, "n": s.Integer(n), "df": s.Integer(n - 1)}
        require(len(args) in (3, 4), "zinterval takes a confidence level, σ and data, or a level, σ, x̄ and n")
        level = _confidence_level(args[0])
        sigma = _positive(args[1], "σ must be positive")
        if len(args) == 3:
            require(isinstance(args[2], (list, tuple)), "zinterval data must be a list")
            mean, n = _data_center(args[2])
        else:
            mean, n = args[2], args[3]
            _real_value(mean, "zinterval requires numeric summary values")
            require(n.is_Integer and n >= 1, "zinterval n must be a positive integer")
        with mp.workdps(digits + 10):
            critical = _quantile(_normal_cdf, (1 + level)/2, engine, -2, 2)
            margin = critical*_mpf(sigma, digits)/mp.sqrt(_mpf(n, digits))
            center = _mpf(mean, digits)
            return {"confidence interval": [_mp_result(center - margin, engine), _mp_result(center + margin, engine)],
                    "sample mean": mean, "n": s.Integer(n)}
    raise MathError("Unknown statistical test: " + name)

def pearson_correlation(xs, ys):
    require(len(xs)==len(ys) and len(xs)>0,"Correlation requires paired data")
    n=len(xs)
    mx=sum(xs)/n; my=sum(ys)/n
    dx=[x-mx for x in xs]; dy=[y-my for y in ys]
    vx=sum(value**2 for value in dx); vy=sum(value**2 for value in dy)
    require(vx*vy!=0,"Correlation requires variation in both data sets")
    return s.simplify(sum(x*y for x,y in zip(dx,dy))/s.sqrt(vx*vy))

def fit_regression(engine, rows, mode, degree=None):
    if mode in ("ridge", "lasso", "elasticnet", "logisticridge", "logisticlasso", "logisticelasticnet", "randomforest", "randomforestclassifier", "randomforestregressor"):
        from calc_machine_learning import fit_regularized, fit_random_forest
        if not mode.startswith("randomforest"):
            return fit_regularized(engine, rows, mode, degree)
        return fit_random_forest(engine, rows, degree, {"randomforestclassifier": "classification", "randomforestregressor": "regression"}.get(mode, "auto"))
    from calc_inference import regression_report, fit_multivariate, _numbers
    if mode in ("multiple", "logistic"):
        return fit_multivariate(engine, rows, logistic=mode=="logistic")
    require(len(rows)>=2 and all(len(row)==2 for row in rows),"Regression requires x,y pairs")
    for row in rows: _numbers(row)
    xs,ys = zip(*rows)
    require(mode in ("linear","quadratic","polynomial","logarithmic","exponential","power"),"Unknown regression type")
    if mode in ("logarithmic", "exponential", "power"):
        return _fit_transformed_regression(engine, xs, ys, mode)
    if mode == "polynomial":
        require(degree is not None and getattr(degree,"is_Integer",False) and 1<=degree<=10, "Polynomial degree must be an integer from 1 to 10")
        degree = int(degree)
    else: degree = 2 if mode == "quadratic" else 1
    require(len(rows)>=degree+1 and len(set(xs))>=degree+1,"Use at least degree + 1 distinct x values")
    if mode == "polynomial":
        from calc_inference import _covariance
        with mp.workdps(engine.precision+30):
            # Fit a centered, scaled Vandermonde matrix, then convert coefficients
            # and covariance back to powers of the user's original x.
            origin = xs[0]
            offsets = [_mpf(xx-origin,engine.precision) for xx in xs]
            center = mp.fsum(offsets)/len(xs)
            scale = max(abs(xx-center) for xx in offsets)
            require(scale>0,"Regression requires variation in x values")
            normalized = [(xx-center)/scale for xx in offsets]
            design = [[xx**i for i in range(degree+1)] for xx in normalized]
            inverse = _covariance(design)
            beta,_ = mp.qr_solve(mp.matrix(design),mp.matrix([_mpf(yy,engine.precision) for yy in ys]))
            base = _mpf(origin,engine.precision)+center
            transform = mp.matrix(degree+1)
            for j in range(degree+1):
                for i in range(j+1): transform[i,j]=math.comb(j,i)*(-base)**(j-i)/scale**j
            coef = transform*beta
            x = engine.symbol("x")
            result = sum(_mp_result(c,engine)*x**i for i,c in enumerate(coef))
            predictions = [mp.fsum(c*xx**i for i,c in enumerate(beta)) for xx in normalized]
            original = [_mpf(xx,engine.precision) for xx in xs]
            regression_report(engine,ys,predictions,[[xx**i for i in range(degree+1)] for xx in original],
                              ["b"+str(i) for i in range(degree+1)],list(coef),
                              information_inverse=transform*inverse*transform.T)
            return result
    design = s.Matrix([[x**i for i in range(degree+1)] for x in xs]); target = s.Matrix(ys)
    # QR avoids squaring the condition number. Exact rational inputs stay exact.
    coef = design.QRsolve(target)
    x = engine.symbol("x")
    result = sum(c*x**i for i,c in enumerate(coef))
    regression_report(engine,ys,[result.subs(x,xx) for xx in xs],design.tolist(),
                      ["b"+str(i) for i in range(degree+1)],list(coef))
    return result

def _fit_transformed_regression(engine, xs, ys, mode):
    """Log-linear least squares without constructing symbolic log matrices."""
    log_x = mode in ("logarithmic", "power")
    log_y = mode in ("exponential", "power")
    for value in (*xs, *ys):
        _real_value(value, "Regression values must be real numbers")
        require(value.is_finite is True, "Regression values must be finite numbers")
    if log_x: require(all(x > 0 for x in xs), "Logarithmic x values must be positive")
    if log_y: require(all(y > 0 for y in ys), "Logarithmic y values must be positive")
    with mp.workdps(engine.precision + 10):
        # Subtract the origin before conversion so large offsets do not erase
        # small differences in exact inputs. log1p preserves close log ratios.
        def centered(values, logarithmic):
            origin = values[0]
            base = _mpf(origin, engine.precision)
            offsets = [_mpf(value - origin, engine.precision) for value in values]
            if logarithmic:
                offsets = [mp.log1p(offset / base) if abs(offset) < abs(base)/2
                           else mp.log(_mpf(value, engine.precision)) - mp.log(base)
                           for value, offset in zip(values, offsets)]
                base = mp.log(base)
            return base, offsets
        x_origin, tx = centered(xs, log_x)
        y_origin, ty = centered(ys, log_y)
        mx, my = mp.fsum(tx)/len(tx), mp.fsum(ty)/len(ty)
        dx, dy = [value - mx for value in tx], [value - my for value in ty]
        variance = mp.fsum(value*value for value in dx)
        require(variance > 0, "Regression requires variation in x values")
        slope = mp.fsum(x*y for x, y in zip(dx, dy))/variance
        intercept = y_origin + my - slope*(x_origin + mx)
        a = _mp_result(mp.exp(intercept) if log_y else intercept, engine)
        b = _mp_result(slope, engine)
    x = engine.symbol("x")
    result = a+b*s.log(x) if mode=="logarithmic" else a*s.exp(b*x) if mode=="exponential" else a*x**b
    from calc_inference import regression_report
    with mp.workdps(engine.precision+15):
        tx = [mp.log(_mpf(xx,engine.precision)) if log_x else _mpf(xx,engine.precision) for xx in xs]
        ty = [mp.log(_mpf(yy,engine.precision)) if log_y else _mpf(yy,engine.precision) for yy in ys]
        intercept = mp.log(_mpf(a,engine.precision)) if log_y else _mpf(a,engine.precision)
        slope = _mpf(b,engine.precision)
        predictions = [_mpf(result.subs(x,xx),engine.precision) for xx in xs]
        regression_report(engine,ys,predictions,[[1,xx] for xx in tx],["A","b"] if log_y else ["a","b"],
                          [intercept,slope],inference_y=ty if log_y else None,
                          inference_predicted=[intercept+slope*xx for xx in tx] if log_y else None,
                          transform=["exp",None] if log_y else None)
    return result

def _regression_qr(design, target, damping=0.0):
    """Column-scaled least squares, with reorthogonalization and optional LM rows.

    Avoid forming J.T*J: that squares the condition number and loses small
    parameter directions when, for example, amplitude and lifetime have very
    different units. Only standard Python is needed by Android and Pyodide.
    """
    count = len(design[0])
    columns = [[row[i] for row in design] for i in range(count)]
    scales = [math.hypot(*column) for column in columns]
    if any(not math.isfinite(scale) or scale == 0 for scale in scales): return None
    columns = [[v/scale for v in column] for column, scale in zip(columns, scales)]
    if damping:
        root = math.sqrt(damping)
        columns = [column + [root if i == j else 0.0 for j in range(count)]
                   for i, column in enumerate(columns)]
        target = target + [0.0]*count
    orthogonal = []
    triangular = [[0.0]*count for _ in range(count)]
    for j, column in enumerate(columns):
        for _ in range(2):
            for i, basis in enumerate(orthogonal):
                projection = math.fsum(a*b for a, b in zip(basis, column))
                triangular[i][j] += projection
                column = [a - projection*b for a, b in zip(column, basis)]
        norm = math.hypot(*column)
        if norm < 1e-12: return None
        triangular[j][j] = norm
        orthogonal.append([v/norm for v in column])
    rhs = [math.fsum(a*b for a, b in zip(column, target)) for column in orthogonal]
    solution = [0.0]*count
    for i in reversed(range(count)):
        solution[i] = (rhs[i] - math.fsum(triangular[i][j]*solution[j]
                                         for j in range(i+1, count)))/triangular[i][i]
    result = [v/scale for v, scale in zip(solution, scales)]
    return result if all(math.isfinite(v) for v in result) else None


def _regression_starts(expression, independent, parameters, xs, ys, start, limits,
                       evaluate):
    """A bounded deterministic search over starting scales, not parameter names."""
    # Jointly affine parameters (e.g. A and C) can be initialized by linear
    # least squares for each nonlinear seed. Exclude products such as A*B.
    affine = []
    symbolic_derivatives = [s.diff(expression, p) for p in parameters]
    for i, parameter in enumerate(parameters):
        derivative = symbolic_derivatives[i]
        if not derivative.has(parameter) and all(not derivative.has(parameters[j]) for j in affine):
            affine.append(i)
    nonlinear = [i for i in range(len(parameters)) if i not in affine]
    x_scale = max(max(xs)-min(xs), max(abs(x) for x in xs))
    natural = start[:]
    # Recognize both exp(-k*x) and exp(-x/tau), including renamed parameters
    # and constant unit factors. Bounds, rather than guessed names, set domains.
    for exponential in sorted(expression.atoms(s.exp), key=str):
        coefficient = s.diff(exponential.args[0], independent)
        for i in nonlinear:
            parameter = parameters[i]
            if coefficient.free_symbols != {parameter}: continue
            power = s.cancel(parameter*s.diff(coefficient, parameter)/coefficient)
            if power not in (s.Integer(1), s.Integer(-1)): continue
            factor = float(coefficient.subs(parameter, 1))
            if not math.isfinite(factor) or factor == 0: continue
            natural[i] = (1/(abs(factor)*x_scale) if power == 1 else abs(factor)*x_scale)

    def prepare(seed):
        seed = [min(max(value, lower), upper) for value, (lower, upper) in zip(seed, limits)]
        if not affine: return seed
        zeroed = seed[:]
        for i in affine: zeroed[i] = 0.0
        current = evaluate(zeroed, True)
        if current is not None:
            _, residual, jacobian = current
            solution = _regression_qr([[row[i] for i in affine] for row in jacobian], residual)
            if solution is not None:
                for i, value in zip(affine, solution):
                    seed[i] = min(max(value, limits[i][0]), limits[i][1])
        return seed

    candidates = []
    seen = set()
    def add(seed):
        seed = prepare(seed)
        key = tuple(seed)
        if key in seen: return
        seen.add(key)
        current = evaluate(seed, True)
        if current is not None: candidates.append((current[0], seed, current))

    add(start)
    add(natural)
    # A small beam limits work even for models with several nonlinear
    # parameters, while combining useful scales instead of changing one seed.
    for i in nonlinear:
        bases = [item[1] for item in sorted(candidates, key=lambda item: item[0])[:3]] or [natural]
        base = natural[i] or 1.0
        guesses = [base*factor for factor in (0.01, 0.1, 1.0, 10.0, 100.0, -0.1, -1.0, -10.0)]
        lower, upper = limits[i]
        if math.isfinite(lower) and math.isfinite(upper):
            guesses += [lower + (upper-lower)*fraction for fraction in (0.1, 0.5, 0.9)]
        for seed in bases:
            for guess in guesses:
                trial = seed[:]
                trial[i] = guess
                add(trial)
        candidates = sorted(candidates, key=lambda item: item[0])[:8]
    return sorted(candidates, key=lambda item: item[0])[:6]


def _regression_optimize(seed, current, limits, evaluate):
    """Scaled LM with projected gradients and explicit convergence status."""
    values = seed[:]
    damping = 1e-3
    for _ in range(160):
        cost, residual, jacobian = current
        if cost <= 1e-26: return values, current, True
        norms = [math.hypot(*(row[i] for row in jacobian)) for i in range(len(values))]
        gradient = [math.fsum(row[i]*r for row, r in zip(jacobian, residual)) for i in range(len(values))]
        active = [i for i, (value, (lower, upper)) in enumerate(zip(values, limits))
                  if not (value <= lower and gradient[i] <= 0 or value >= upper and gradient[i] >= 0)]
        if any(norms[i] == 0 for i in active): return values, current, False
        if not active or max(abs(gradient[i])/norms[i] for i in active) <= 1e-8*math.sqrt(cost) + 1e-14*math.sqrt(len(residual)):
            return values, current, True
        design = [[row[i] for i in active] for row in jacobian]
        improved = False
        for _ in range(16):
            step = _regression_qr(design, residual, damping)
            if step is None: return values, current, False
            trial = values[:]
            for i, delta in zip(active, step):
                trial[i] = min(max(values[i]+delta, limits[i][0]), limits[i][1])
            candidate = evaluate(trial, True)
            if candidate is not None and candidate[0] < cost:
                values, current = trial, candidate
                damping = max(damping/3, 1e-12)
                improved = True
                break
            damping *= 10
        if not improved: return values, current, False
    return values, current, False


def fit_custom_regression(engine, rows, expression, independent, options=None):
    """Fit an arbitrary real y(x) with damped nonlinear least squares.

    options is a list of [parameter, initial, lower?, upper?] rows. Omitted
    initials use a scale-aware multi-start search; omitted bounds are unbounded.
    """
    require(isinstance(independent, s.Symbol), "Choose an independent variable")
    require(isinstance(expression, s.Expr) and not isinstance(expression, Relational),
            "Custom model must be an expression for y")
    require(len(rows) >= 2 and all(len(row) == 2 for row in rows), "Regression requires x,y pairs")
    require(not expression.has(s.I, s.oo, s.zoo, s.nan), "Custom model must be real and finite")
    parameters = sorted(expression.free_symbols - {independent}, key=str)
    require(parameters, "Custom model needs at least one parameter")
    require(len(parameters) <= 8, "Custom model supports up to 8 parameters")
    require(len(rows) >= len(parameters) + 1, "Add more data points than fit parameters")
    try:
        xs = [float(row[0]) for row in rows]
        ys = [float(row[1]) for row in rows]
        require(all(math.isfinite(v) for v in xs + ys), "Regression data must be finite")
    except (TypeError, ValueError, OverflowError):
        raise MathError("Regression data must be real numbers")
    span = max(xs) - min(xs)
    require(math.isfinite(span) and span > 0 and len(set(xs)) >= len(parameters) + 1, "Use more distinct x values")
    scale = max(abs(v) for v in ys) or 1.0
    start = []
    limits = []
    precise_limits = []
    for parameter in parameters:
        name = str(parameter).lower()
        if name in ("s0", "a", "amplitude"): guess = ys[xs.index(min(xs))]
        elif name == "f": guess = 0.2
        elif name in ("dstar", "d_fast", "dslow"): guess = 5.0/span
        elif name in ("d", "adc", "rate", "k"): guess = 1.0/span
        else: guess = scale if expression.subs(parameter, 0) == 0 else 1.0
        start.append(float(guess))
        limits.append((-math.inf, math.inf))
        precise_limits.append((-s.oo, s.oo))
    if options is not None:
        require(isinstance(options, (list, tuple)), "Initial values must be parameter rows")
        seen = set()
        for option in options:
            require(isinstance(option, (list, tuple)) and 2 <= len(option) <= 4,
                    "Use [parameter, initial, lower?, upper?]")
            parameter = option[0]
            require(parameter in parameters and parameter not in seen, "Unknown or repeated fit parameter")
            seen.add(parameter)
            index = parameters.index(parameter)
            try:
                values = [float(v) for v in option[1:]]
            except (TypeError, ValueError, OverflowError):
                raise MathError("Initial values and bounds must be real numbers")
            require(all(math.isfinite(v) for v in values), "Initial values and bounds must be finite")
            start[index] = values[0]
            lower = values[1] if len(values) > 1 else -math.inf
            upper = values[2] if len(values) > 2 else math.inf
            require(lower < upper and lower <= start[index] <= upper, "Initial value must lie within bounds")
            limits[index] = (lower, upper)
            precise_limits[index] = (option[2] if len(option) > 2 else -s.oo,
                                     option[3] if len(option) > 3 else s.oo)
    try:
        model = s.lambdify([independent] + parameters, expression, modules="math", cse=True, docstring_limit=0)
        derivatives = [s.lambdify([independent] + parameters, s.diff(expression, p), modules="math", cse=True, docstring_limit=0) for p in parameters]
    except Exception:
        raise MathError("Custom model contains an unsupported function")

    def evaluate(values, with_jacobian=False):
        try:
            predicted = [float(model(x, *values)) for x in xs]
            if not all(math.isfinite(v) for v in predicted): return None
            residual = [y/scale - predicted[i]/scale for i, y in enumerate(ys)]
            cost = math.fsum(v*v for v in residual)
            if not math.isfinite(cost): return None
            if not with_jacobian: return cost
            jacobian = [[float(derivative(x, *values))/scale for derivative in derivatives] for x in xs]
            if not all(math.isfinite(v) for row in jacobian for v in row): return None
            return cost, residual, jacobian
        except (ArithmeticError, TypeError, ValueError, OverflowError):
            return None

    starts = _regression_starts(expression, independent, parameters, xs, ys, start, limits,
                                evaluate)
    require(starts, "Initial values are outside the model domain")
    best = None
    for _, seed, candidate in starts:
        fitted_values, fitted_current, converged = _regression_optimize(seed, candidate, limits, evaluate)
        if not converged: continue
        # A plateau with zero/rank-deficient sensitivities does not determine
        # all requested parameters, even if its residual happens to be small.
        if _regression_qr(fitted_current[2], fitted_current[1]) is None: continue
        if best is None or fitted_current[0] < best[1][0]: best = fitted_values, fitted_current
        if best[1][0] <= 1e-26: break
    require(best is not None, "Custom fitting did not converge to identifiable parameters. Try initial values or bounds, or simplify the model.")
    values, current = best
    def fitted_expression():
        # The float fit finds a basin quickly. Refine against the original data so
        # parameter values and the fitted expression use internal precision.
        # Near a nonzero least-squares minimum, objective improvements are
        # quadratic in the parameter error. Extra working digits keep those
        # improvements visible through the requested parameter precision.
        with mp.workdps(2*engine.precision + 12):
            mp_xs = [_mpf(row[0], engine.precision) for row in rows]
            mp_scale = _mpf(s.Float(repr(scale)), engine.precision)
            mp_ys = [_mpf(row[1], engine.precision)/mp_scale for row in rows]
            mp_limits = [(None if lower == -s.oo else _mpf(lower, engine.precision),
                          None if upper == s.oo else _mpf(upper, engine.precision))
                         for lower, upper in precise_limits]
            mp_model = s.lambdify([independent] + parameters, expression, modules="mpmath", cse=True, docstring_limit=0)
            mp_derivatives = [s.lambdify([independent] + parameters, s.diff(expression, p),
                                        modules="mpmath", cse=True, docstring_limit=0) for p in parameters]
            refined = [mp.mpf(repr(value)) for value in values]

            def evaluate_precise(coefficients):
                try:
                    predicted = [mp.mpf(mp_model(x, *coefficients))/mp_scale for x in mp_xs]
                    residual = [y - p for y, p in zip(mp_ys, predicted)]
                    jacobian = [[mp.mpf(derivative(x, *coefficients))/mp_scale for derivative in mp_derivatives]
                                for x in mp_xs]
                    if not all(mp.isfinite(v) for v in predicted + residual + [v for row in jacobian for v in row]):
                        return None
                    return mp.fsum(r*r for r in residual), residual, jacobian
                except (ArithmeticError, TypeError, ValueError, OverflowError):
                    return None

            current_precise = evaluate_precise(refined)
            require(current_precise is not None, "Numerical fitting failed")
            damping_precise = mp.mpf("0.001")
            tolerance = mp.power(10, -engine.precision - 2)
            for _ in range(max(60, 2*engine.precision)):
                cost, residual, jacobian = current_precise
                if cost == 0: break
                raw_gradient = [mp.fsum(row[i]*r for row, r in zip(jacobian, residual))
                                for i in range(len(parameters))]
                active = [i for i, (value, (lower, upper)) in enumerate(zip(refined, mp_limits))
                          if not (lower is not None and value <= lower and raw_gradient[i] <= 0
                                  or upper is not None and value >= upper and raw_gradient[i] >= 0)]
                if not active: break
                column_scales = [mp.sqrt(mp.fsum(row[i]**2 for row in jacobian)) for i in active]
                if any(value == 0 for value in column_scales): break
                scaled_jacobian = [[row[i]/scale for i, scale in zip(active, column_scales)] for row in jacobian]
                normal = mp.matrix([[mp.fsum(row[i]*row[j] for row in scaled_jacobian)
                                     for j in range(len(active))] for i in range(len(active))])
                gradient = mp.matrix([mp.fsum(row[i]*r for row, r in zip(scaled_jacobian, residual))
                                      for i in range(len(active))])
                improved = False
                for _ in range(12):
                    matrix = normal.copy()
                    for i in range(len(active)):
                        matrix[i, i] += damping_precise
                    try: step = mp.lu_solve(matrix, gradient)
                    except (ValueError, ZeroDivisionError): break
                    trial = refined[:]
                    for j, i in enumerate(active): trial[i] += step[j]/column_scales[j]
                    trial = [max(lower, value) if lower is not None else value
                             for value, (lower, _) in zip(trial, mp_limits)]
                    trial = [min(upper, value) if upper is not None else value
                             for value, (_, upper) in zip(trial, mp_limits)]
                    candidate = evaluate_precise(trial)
                    if candidate is not None and candidate[0] < cost:
                        change = max(abs(a-b)/max(abs(a), abs(b), mp.mpf("1e-100")) for a, b in zip(trial, refined))
                        refined, current_precise = trial, candidate
                        damping_precise = max(damping_precise/3, tolerance)
                        improved = True
                        break
                    damping_precise *= 10
                if not improved or change < tolerance: break
            fitted = [s.Float(str(value), engine.precision) for value in refined]
        engine.regression_parameters = [[str(parameter), str(value)]
                                        for parameter, value in zip(parameters, fitted)]
        from calc_inference import expression_report
        expression_report(engine, rows, expression, independent, parameters, fitted, precise_limits)
        return expression.subs(dict(zip(parameters, fitted)))
    return fitted_expression()
