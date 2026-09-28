"""Probability distributions, statistical tests, and regression."""
import math
from statistics import NormalDist
import mpmath as mp
import sympy as s
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
        require(len(args) == 2, "chi2independence takes x and y category lists")
        xs, ys = flatten(args[0]), flatten(args[1])
        require(len(xs) == len(ys) and len(xs) >= 2, "χ² independence needs at least two complete pairs")
        for value in xs + ys: _real_value(value, "Categories must be real numbers")
        x_categories, y_categories = sorted(set(xs)), sorted(set(ys))
        require(len(x_categories) >= 2 and len(y_categories) >= 2, "Each category column needs at least two distinct values")
        counts = [[s.Integer(sum(x == xc and y == yc for x, y in zip(xs, ys))) for yc in y_categories] for xc in x_categories]
        row_totals = [sum(row) for row in counts]
        column_totals = [sum(row[j] for row in counts) for j in range(len(y_categories))]
        n = s.Integer(len(xs))
        if any(row_total*column_total/n < 5 for row_total in row_totals for column_total in column_totals):
            engine.note = "Some expected counts are below 5; the χ² approximation may be inaccurate."
        statistic = s.Add(*[(counts[i][j] - row_totals[i]*column_totals[j]/n)**2/(row_totals[i]*column_totals[j]/n)
                            for i in range(len(x_categories)) for j in range(len(y_categories))])
        df = s.Integer((len(x_categories) - 1)*(len(y_categories) - 1))
        with mp.workdps(digits + 10):
            probability = _chisq_sf(_mpf(statistic, digits), _mpf(df, digits))
            return {"chi-square": statistic, "df": df, "p value": _mp_result(probability, engine),
                    "observed": counts, "n": n}
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
    if name == "anova":
        require(len(args) >= 2, "anova takes two or more data lists")
        groups = []
        for group in args:
            require(isinstance(group, (list, tuple)), "anova arguments must be data lists")
            values = flatten(group)
            require(len(values) >= 2, "Each anova group needs at least two values")
            for value in values: _real_value(value, "Sample values must be real numbers")
            groups.append(values)
        total = sum(len(group) for group in groups)
        means = [s.Add(*group)/s.Integer(len(group)) for group in groups]
        grand = s.Add(*[value for group in groups for value in group])/s.Integer(total)
        between = s.Add(*[s.Integer(len(group))*(mean - grand)**2 for group, mean in zip(groups, means)])
        within = s.Add(*[s.Add(*[(value - mean)**2 for value in group]) for group, mean in zip(groups, means)])
        require(within != 0, "anova needs variation inside the groups")
        count = len(groups)
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

def fit_regression(engine, rows, mode):
    require(len(rows)>=2 and all(len(row)==2 for row in rows),"Regression requires x,y pairs")
    xs,ys = zip(*rows)
    require(mode in ("linear","quadratic","logarithmic","exponential","power"),"Unknown regression type")
    if mode in ("logarithmic","power"): require(all(x>0 for x in xs),"Logarithmic x values must be positive"); xs = tuple(s.log(x) for x in xs)
    if mode in ("exponential","power"): require(all(y>0 for y in ys),"Logarithmic y values must be positive"); ys = tuple(s.log(y) for y in ys)
    degree = 2 if mode == "quadratic" else 1
    design = s.Matrix([[x**i for i in range(degree+1)] for x in xs]); target = s.Matrix(ys)
    coef = (design.T*design).inv()*design.T*target
    x = engine.symbol("x")
    result = sum(c*x**i for i,c in enumerate(coef))
    if mode == "logarithmic": result = result.subs(x,s.log(x))
    if mode == "exponential": result = s.exp(result)
    if mode == "power": result = s.exp(coef[0])*x**coef[1]
    return result
