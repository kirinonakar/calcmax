"""Probability distributions, statistical tests, and regression."""
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
    raise MathError("Unknown distribution: " + name)

def statistical_test(engine, name, a, nodes):
    digits = engine.precision
    tail, args = _tail_argument(a, nodes)
    if tail != "both": engine.note = "One-tailed probability (" + tail + " tail)."
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
