"""Numerical probability tools shared by Android and WASM. No SciPy required.

Tails are evaluated directly; subtracting a rounded CDF loses rare-event accuracy.
Geometric X counts trials through the first success (support 1, 2, ...).
Negative binomial X counts failures before the r-th success (support 0, 1, ...).
"""
import math
import re
import mpmath
from fractions import Fraction
from calc_shared import MathError, require

DECIMAL = r"[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?"
NUMBER = re.compile(rf"{DECIMAL}(?:\s*/\s*{DECIMAL})?\Z")


def probability(request):
    mp = mpmath.mp.clone()
    mp.dps = max(30, min(100, int(request.get("precision", 30)))) + 15
    raw = request.get("values", {})
    require(isinstance(raw, dict), "Invalid probability inputs")

    def beta_fraction(a,b,x):
        # Modified Lentz continued fraction. Unlike the hypergeometric series,
        # this converges near the mean even for large binomial trial counts.
        tiny = mp.power(10,-2*mp.dps)
        c, d = mp.mpf(1), 1-(a+b)*x/(a+1)
        if abs(d)<tiny:d=tiny
        d=1/d;h=d
        for m in range(1,10001):
            for term in (m*(b-m)*x/((a+2*m-1)*(a+2*m)), -(a+m)*(a+b+m)*x/((a+2*m)*(a+2*m+1))):
                d=1+term*d;c=1+term/c
                if abs(d)<tiny:d=tiny
                if abs(c)<tiny:c=tiny
                d=1/d;delta=d*c;h*=delta
            if abs(delta-1)<mp.eps*16:return h
        require(False,"Numerical convergence failed")

    def beta_cdf(a,b,x):
        a,b,x=mp.mpf(a),mp.mpf(b),mp.mpf(x)
        if x<=0:return mp.mpf(0)
        if x>=1:return mp.mpf(1)
        front=mp.exp(mp.loggamma(a+b)-mp.loggamma(a)-mp.loggamma(b)+a*mp.log(x)+b*mp.log1p(-x))
        if x<(a+1)/(a+b+2):return front*beta_fraction(a,b,x)/a
        return 1-front*beta_fraction(b,a,1-x)/b

    def number(key, infinite=False):
        text = str(raw.get(key, "")).strip()
        require(len(text) <= 200, "Numeric input is too long")
        if infinite and text.lower() in ("inf", "+inf", "infinity", "∞", "+∞", "-inf", "-infinity", "-∞"):
            return -mp.inf if text.startswith("-") else mp.inf
        percent = text.endswith("%")
        if percent:
            text = text[:-1].strip()
        require(bool(NUMBER.fullmatch(text)), "Enter a number, fraction, or percentage" + f" ({key})")
        parts = text.split("/")
        require(all(abs(int(part.lower().split("e")[1])) <= 1000 for part in parts if "e" in part.lower()), "Numeric exponent is too large")
        value = mp.mpf(parts[0])
        if len(parts) == 2:
            divisor = mp.mpf(parts[1])
            require(divisor != 0, "Division by zero in input")
            value /= divisor
        require(mp.isfinite(value), "Enter a finite number")
        return value / 100 if percent else value

    def integer(key, maximum=100000):
        value = number(key)
        require(value == mp.floor(value) and 0 <= value <= maximum, "Enter an integer within the allowed range" + f" ({key})")
        return int(value)

    def chance(key):
        value = number(key)
        require(0 <= value <= 1, "Probabilities must be between 0 and 1" + f" ({key})")
        return value

    def normal_z(q):
        # Invert erfc on the smaller tail, including q much smaller than mp.eps.
        if q == mp.mpf('0.5'): return mp.mpf(0)
        tail = min(q,1-q)
        left, right = mp.mpf(0), mp.mpf(1)
        while mp.erfc(right/mp.sqrt(2))/2 > tail: right *= 2
        for _ in range(mp.prec+8):
            middle = (left+right)/2
            if mp.erfc(middle/mp.sqrt(2))/2 > tail: left = middle
            else: right = middle
        return (left+right)/2 * (1 if q > mp.mpf('0.5') else -1)

    def positive(key):
        value = number(key)
        require(value > 0, "This parameter must be greater than zero" + f" ({key})")
        return value

    def shown(value):
        if value is None:
            return "undefined"
        if isinstance(value, int):
            return str(value)
        if mp.isinf(value):
            return "−∞" if value < 0 else "∞"
        return mp.nstr(value, min(100, max(15, int(request.get("precision", 30)))), strip_zeros=True)

    def rational(key):
        # Only called after numeric validation. Preserve exact user fractions.
        text = str(raw[key]).strip()
        percent = text.endswith("%")
        parts = text.rstrip("%").strip().split("/")
        value = Fraction(parts[0].strip())
        if len(parts) == 2: value /= Fraction(parts[1].strip())
        return value/100 if percent else value

    def hypergeometric_counts(population, successes, draws):
        lower, upper = max(0, draws-(population-successes)), min(draws, successes)
        def count(k):
            return math.comb(successes,k)*math.comb(population-successes,draws-k) if lower <= k <= upper else 0
        return lower, upper, math.comb(population,draws), count

    category = request.get("category", "distribution")
    operation = request.get("operation", "le")
    details = []
    formula = ""
    note = ""
    is_probability = True
    plot = None
    exact = None
    if category == "basic":
        favorable, total = integer("favorable",1000000000), integer("total",1000000000)
        require(total > 0 and favorable <= total, "Favorable outcomes must not exceed a positive total")
        require(operation == "ratio", "Unsupported probability operation")
        exact = Fraction(favorable,total)
        value, formula = mp.mpf(favorable)/total, f"{favorable} / {total}"
    elif category == "dice":
        dice, sides, x = integer("dice",20), integer("sides",100), integer("x",2000)
        require(dice > 0 and sides > 0, "Dice count and sides must be positive")
        require(operation in ("eq","le","ge"), "Unsupported probability operation")
        # Sliding-window convolution gives exact integer counts, without enumerating rolls.
        counts = [1]
        for _ in range(dice):
            next_counts = [0]*(len(counts)+sides)
            window = 0
            for i in range(len(next_counts)):
                if i-1 >= 0 and i-1 < len(counts): window += counts[i-1]
                if i-sides-1 >= 0 and i-sides-1 < len(counts): window -= counts[i-sides-1]
                next_counts[i] = window
            counts = next_counts
        favorable = (counts[x] if x < len(counts) else 0) if operation == "eq" else sum(counts[:x+1]) if operation == "le" else sum(counts[x:])
        total = sides**dice
        exact, value = Fraction(favorable,total), mp.mpf(favorable)/total
        symbol = {"eq":"=","le":"≤","ge":"≥"}[operation]
        formula = f"P(X {symbol} {x}) = {favorable} / {total}"
        details = [("Favorable outcomes",favorable),("Total outcomes",total)]
    elif category == "draw":
        population, draws, marked = integer("population",1000), integer("draws",1000), integer("marked",1000)
        require(population > 0 and draws <= population and marked <= population, "Draws and specified items must not exceed total")
        require(operation in ("allMarked", "exactly", "atLeast", "atMost", "atLeastOne"), "Unsupported probability operation")
        lower, upper, total, count = hypergeometric_counts(population,marked,draws)
        k = marked if operation == "allMarked" else 1 if operation == "atLeastOne" else integer("k",1000)
        if operation in ("allMarked", "exactly"):
            favorable = count(k)
        elif operation in ("atLeast", "atLeastOne"):
            favorable = sum(count(i) for i in range(max(lower,k),upper+1))
        else:
            favorable = sum(count(i) for i in range(lower,min(upper,k)+1))
        exact, value = Fraction(favorable,total), mp.mpf(favorable)/total
        symbol = "=" if operation in ("allMarked", "exactly") else "≤" if operation == "atMost" else "≥"
        formula = f"P(X {symbol} {k}) = {favorable} / {total}"
        details = [("Favorable outcomes",favorable),("Total outcomes",total)]
        note = "X counts selected specified items; drawing is without replacement."
    elif category == "counting":
        n, r = integer("n", 1000), integer("r", 1000)
        if operation in ("combination", "permutation"):
            require(r <= n, "Selection count must not exceed item count")
            value = math.comb(n, r) if operation == "combination" else math.perm(n, r)
            formula = f"C({n}, {r})" if operation == "combination" else f"P({n}, {r})"
        elif operation == "replacement":
            value, formula = n ** r, f"{n}^{r}"
        elif operation == "multicombination":
            require(n > 0 or r == 0, "Cannot select from zero items")
            value, formula = (math.comb(n+r-1, r) if r else 1), f"C({n+r-1}, {r})" if r else "1"
        else:
            require(False, "Unsupported probability operation")
        is_probability = False
    elif category == "events" and operation == "conditionalCounts":
        joint, condition = integer("jointCount",1000000000), integer("conditionCount",1000000000)
        require(condition > 0, "Conditioning event has zero probability")
        require(joint <= condition, "Favorable outcomes must not exceed a positive total")
        exact, value = Fraction(joint,condition), mp.mpf(joint)/condition
        formula = f"P(A | B) = {joint} / {condition}"
    elif category == "events":
        from calc_event_probability import solve_events, LABELS
        independent = request.get("independent") is True
        known = {}
        for key in raw:
            require(key in LABELS, "Invalid probability inputs")
            chance(key)
            known[key] = rational(key)
            require(0 <= known[key] <= 1, "Probabilities must be between 0 and 1" + f" ({key})")
        if independent:
            a, b = chance("pa"), chance("pb")
            fa, fb = rational("pa"), rational("pb")
            fab = fa*fb
            if operation in ("conditional", "reverse"):
                require(fb > 0 if operation == "conditional" else fa > 0, "Conditioning event has zero probability")
            choices = {"intersection": fab, "union": fa+fb-fab, "conditional": fa,
                       "reverse": fb, "onlyA": fa-fab, "neither": 1-fa-fb+fab}
            require(operation in choices, "Unsupported probability operation")
            exact = choices[operation]
            note = "Independent events: P(A ∩ B) = P(A) × P(B)."
            formula = LABELS[operation]
            details = [("P(A)", a), ("P(B)", b)]
        else:
            exact = solve_events(known, operation)
            formula = LABELS[operation] + " · " + ", ".join(LABELS[key] + " = " + shown(mp.mpf(v.numerator)/v.denominator) for key, v in known.items())
        value = mp.mpf(exact.numerator)/exact.denominator
    elif category == "bayes":
        prior, likelihood = chance("prior"), chance("likelihood")
        require(operation in ("posterior", "negative", "posteriorSpecificity", "negativeSpecificity"), "Unsupported probability operation")
        false_positive = 1-chance("specificity") if operation.endswith("Specificity") else chance("falsePositive")
        negative = operation.startswith("negative")
        if negative:
            likelihood, false_positive = 1-likelihood, 1-false_positive
        evidence = prior*likelihood + (1-prior)*false_positive
        require(evidence > 0, "Conditioning event has zero probability")
        value = prior*likelihood/evidence
        event = "Bᶜ" if negative else "B"
        formula = f"P(A | {event}) = P(A)P({event} | A) / P({event})"
        details = [(f"P({event})", evidence), (f"P(A ∩ {event})", prior*likelihood)]
    elif category == "repeat":
        n, p = integer("n"), chance("p")
        if operation == "atLeastOne":
            value = -mp.expm1(n*mp.log1p(-p)) if n and p < 1 else mp.mpf(bool(n))
            formula = f"P(X ≥ 1) = 1 − (1 − p)^{n}"
        elif operation == "all":
            value, formula = p**n, f"P(X = {n}) = p^{n}"
        elif operation == "none":
            value, formula = (1-p)**n, f"P(X = 0) = (1 − p)^{n}"
        elif operation == "exactly":
            k = integer("k")
            require(k <= n, "Success count must not exceed trial count")
            value, formula = mp.binomial(n,k)*p**k*(1-p)**(n-k), f"P(X = {k}) = C({n}, {k}) p^{k} (1 − p)^{n-k}"
        elif operation in ("atLeast", "atMost", "between"):
            # Reuse the same direct-tail binomial engine and inclusive bounds.
            values = {"n": raw["n"], "p": raw["p"]}
            if operation == "between":
                lower, upper = integer("lower"), integer("upper")
                require(lower <= upper, "Lower bound must not exceed upper bound")
                values.update(lower=str(lower), upper=str(upper))
            else:
                k = integer("k")
                values["x"] = str(k)
            result = probability({**request, "category":"distribution", "distribution":"binomial",
                                  "operation":{"atLeast":"ge", "atMost":"le", "between":"between"}[operation],
                                  "values":values, "preview":False})
            result["note"] = "Trials are independent with the same success probability."
            if n <= 200:
                fp = rational("p")
                start, end = (k,n) if operation == "atLeast" else (0,min(k,n)) if operation == "atMost" else (lower,min(upper,n))
                fraction = sum((math.comb(n,i)*fp**i*(1-fp)**(n-i) for i in range(start,end+1)), Fraction(0))
                if fraction.numerator.bit_length() < 650 and fraction.denominator.bit_length() < 650:
                    result["fraction"] = str(fraction)
            return result
        else:
            require(False, "Unsupported probability operation")
        details = [("E[X]", n*p), ("Var[X]", n*p*(1-p)), ("SD[X]", mp.sqrt(n*p*(1-p)))]
        note = "Trials are independent with the same success probability."
        if n <= 200:
            fp = rational("p")
            exact = 1-(1-fp)**n if operation == "atLeastOne" else fp**n if operation == "all" else (1-fp)**n if operation == "none" else math.comb(n,k)*fp**k*(1-fp)**(n-k)
    elif category == "normalSolver":
        require(operation in ("muLe", "muGe", "sigmaLe", "sigmaGe"), "Unsupported probability operation")
        x, q = number("x"), chance("q")
        require(0 < q < 1, "Normal solver requires 0 < q < 1")
        z = normal_z(q)
        if operation.endswith("Ge"): z = -z
        if operation.startswith("mu"):
            sigma = positive("sigma")
            mu = value = x-sigma*z
            note = "μ = x − σz, where z is the standard normal quantile."
        else:
            mu = number("mu")
            if z == 0:
                require(False, "σ is not uniquely determined when q = 0.5 and x = μ" if x == mu else "No positive σ satisfies these inputs")
            sigma = value = (x-mu)/z
            require(sigma > 0, "No positive σ satisfies these inputs")
            note = "σ = (x − μ) / z, where z is the standard normal quantile."
        symbol = "≥" if operation.endswith("Ge") else "≤"
        formula = f"{'μ' if operation.startswith('mu') else 'σ'} · P(X {symbol} {shown(x)}) = {shown(q)}"
        details = [("μ",mu),("σ",sigma),("z",z)]
        is_probability = False
    elif category == "distribution":
        kind = request.get("distribution", "normal")
        discrete = kind in ("binomial", "poisson", "geometric", "negativeBinomial", "hypergeometric")
        lo, hi = -mp.inf, mp.inf
        mean, variance = None, None
        # Every distribution supplies mass/density, CDF, and survival directly.
        if kind == "normal":
            mu, sigma = number("mu"), positive("sigma")
            mean, variance = mu, sigma*sigma
            cdf = lambda x: mp.erfc(-(x-mu)/(sigma*mp.sqrt(2)))/2
            sf = lambda x: mp.erfc((x-mu)/(sigma*mp.sqrt(2)))/2
            pdf = lambda x: mp.exp(-((x-mu)/sigma)**2/2)/(sigma*mp.sqrt(2*mp.pi))
        elif kind == "cauchy":
            location, scale = number("location"), positive("scale")
            # atan2 evaluates the smaller tail directly, even far from the center.
            cdf = lambda x: mp.atan2(scale,location-x)/mp.pi
            sf = lambda x: mp.atan2(scale,x-location)/mp.pi
            pdf = lambda x: 1/(mp.pi*scale*(1+((x-location)/scale)**2))
        elif kind == "binomial":
            n, p = integer("n"), chance("p")
            lo, hi = (n, n) if p == 1 else (0, 0) if p == 0 else (0, n)
            mean, variance = n*p, n*p*(1-p)
            pdf = lambda k: mp.binomial(n,k)*p**k*(1-p)**(n-k)
            cdf = lambda k: beta_cdf(n-k,k+1,1-p)
            sf = lambda k: beta_cdf(k+1,n-k,p)
        elif kind == "poisson":
            rate = number("rate")
            require(0 <= rate <= 100000, "Average count must be between 0 and 100000")
            lo, hi = 0, 0 if rate == 0 else mp.inf
            mean, variance = rate, rate
            pdf = lambda k: mp.exp(-rate)*rate**k/mp.factorial(k)
            cdf = lambda k: mp.gammainc(k+1,rate,mp.inf,regularized=True)
            sf = lambda k: mp.gammainc(k+1,0,rate,regularized=True)
        elif kind == "geometric":
            p = chance("p")
            require(p > 0, "Success probability must be greater than zero")
            lo, hi = 1, 1 if p == 1 else mp.inf
            mean, variance = 1/p, (1-p)/p**2
            pdf = lambda k: p*(1-p)**(k-1)
            cdf = lambda k: -mp.expm1(k*mp.log1p(-p))
            sf = lambda k: (1-p)**k
            note = "Geometric X counts trials through the first success (starting at 1)."
        elif kind == "hypergeometric":
            population, successes, draws = integer("population",10000), integer("successes",10000), integer("draws",10000)
            require(population > 0 and successes <= population and draws <= population, "Draws and success items must not exceed population")
            lo, hi, denominator, count = hypergeometric_counts(population,successes,draws)
            mean = mp.mpf(draws)*successes/population
            variance = mp.mpf(draws)*successes/population*(1-mp.mpf(successes)/population)*(population-draws)/(population-1) if population > 1 else mp.mpf(0)
            pdf = lambda k: mp.mpf(count(int(k)))/denominator
            cdf = lambda k: mp.fsum(pdf(i) for i in range(lo,int(k)+1))
            sf = lambda k: mp.fsum(pdf(i) for i in range(int(k)+1,hi+1))
        elif kind == "negativeBinomial":
            r, p = integer("r"), chance("p")
            require(r > 0, "Required successes must be greater than zero")
            require(p > 0, "Success probability must be greater than zero")
            lo, hi = 0, 0 if p == 1 else mp.inf
            mean, variance = r*(1-p)/p, r*(1-p)/p**2
            pdf = lambda k: mp.binomial(k+r-1,k)*p**r*(1-p)**k
            cdf = lambda k: beta_cdf(r,k+1,p)
            sf = lambda k: beta_cdf(k+1,r,1-p)
            note = "Negative binomial X counts failures before the r-th success (starting at 0). Total trials = X + r."
        elif kind == "uniform":
            lo, hi = number("a"), number("b")
            require(hi > lo, "Maximum must be greater than minimum")
            mean, variance = (lo+hi)/2, (hi-lo)**2/12
            cdf = lambda x: (x-lo)/(hi-lo)
            sf = lambda x: (hi-x)/(hi-lo)
            pdf = lambda x: 1/(hi-lo)
        elif kind == "exponential":
            rate = positive("rate")
            lo, mean, variance = 0, 1/rate, 1/rate**2
            cdf = lambda x: -mp.expm1(-rate*x)
            sf = lambda x: mp.exp(-rate*x)
            pdf = lambda x: rate*mp.exp(-rate*x)
        elif kind == "gamma":
            shape, scale = positive("shape"), positive("scale")
            lo, mean, variance = 0, shape*scale, shape*scale**2
            cdf = lambda x: mp.gammainc(shape,0,x/scale,regularized=True)
            sf = lambda x: mp.gammainc(shape,x/scale,mp.inf,regularized=True)
            def pdf(x):
                if x == 0: return mp.inf if shape < 1 else 1/scale if shape == 1 else mp.mpf(0)
                return mp.exp((shape-1)*mp.log(x/scale)-x/scale-mp.loggamma(shape))/scale
        elif kind == "beta":
            alpha, beta = positive("alpha"), positive("beta")
            lo, hi = 0, 1
            mean = alpha/(alpha+beta)
            variance = alpha*beta/((alpha+beta)**2*(alpha+beta+1))
            cdf = lambda x: beta_cdf(alpha,beta,x)
            sf = lambda x: beta_cdf(beta,alpha,1-x)
            def pdf(x):
                if x == 0: return mp.inf if alpha < 1 else beta if alpha == 1 else mp.mpf(0)
                if x == 1: return mp.inf if beta < 1 else alpha if beta == 1 else mp.mpf(0)
                return mp.exp((alpha-1)*mp.log(x)+(beta-1)*mp.log1p(-x)-mp.loggamma(alpha)-mp.loggamma(beta)+mp.loggamma(alpha+beta))
        elif kind == "lognormal":
            mu, sigma = number("mu"), positive("sigma")
            lo, mean = 0, mp.exp(mu+sigma**2/2)
            variance = mp.expm1(sigma**2)*mp.exp(2*mu+sigma**2)
            cdf = lambda x: mp.erfc(-(mp.log(x)-mu)/(sigma*mp.sqrt(2)))/2 if x > 0 else mp.mpf(0)
            sf = lambda x: mp.erfc((mp.log(x)-mu)/(sigma*mp.sqrt(2)))/2 if x > 0 else mp.mpf(1)
            pdf = lambda x: mp.exp(-((mp.log(x)-mu)/sigma)**2/2)/(x*sigma*mp.sqrt(2*mp.pi)) if x > 0 else mp.mpf(0)
        elif kind == "weibull":
            shape, scale = positive("shape"), positive("scale")
            lo, mean = 0, scale*mp.gamma(1+1/shape)
            variance = scale**2*(mp.gamma(1+2/shape)-mp.gamma(1+1/shape)**2)
            cdf = lambda x: -mp.expm1(-(x/scale)**shape)
            sf = lambda x: mp.exp(-(x/scale)**shape)
            def pdf(x):
                if x == 0: return mp.inf if shape < 1 else 1/scale if shape == 1 else mp.mpf(0)
                return shape/scale*(x/scale)**(shape-1)*mp.exp(-(x/scale)**shape)
        elif kind == "t":
            df = positive("df")
            mean = mp.mpf(0) if df > 1 else None
            variance = df/(df-2) if df > 2 else mp.inf if df > 1 else None
            def tail(x):
                return beta_cdf(df/2,mp.mpf('0.5'),df/(df+x*x))/2
            cdf = lambda x: tail(x) if x < 0 else 1-tail(x)
            sf = lambda x: tail(x) if x >= 0 else 1-tail(x)
            pdf = lambda x: mp.gamma((df+1)/2)/(mp.sqrt(df*mp.pi)*mp.gamma(df/2))*(1+x*x/df)**(-(df+1)/2)
        elif kind == "chi2":
            df = positive("df")
            lo, mean, variance = 0, df, 2*df
            cdf = lambda x: mp.gammainc(df/2,0,x/2,regularized=True)
            sf = lambda x: mp.gammainc(df/2,x/2,mp.inf,regularized=True)
            def pdf(x):
                if x == 0:
                    return mp.inf if df < 2 else mp.mpf('0.5') if df == 2 else mp.mpf(0)
                return mp.exp((df/2-1)*mp.log(x)-x/2-(df/2)*mp.log(2)-mp.loggamma(df/2))
        elif kind == "f":
            d1, d2 = positive("df1"), positive("df2")
            lo = 0
            mean = d2/(d2-2) if d2 > 2 else mp.inf
            variance = 2*d2*d2*(d1+d2-2)/(d1*(d2-2)**2*(d2-4)) if d2 > 4 else mp.inf if d2 > 2 else None
            cdf = lambda x: beta_cdf(d1/2,d2/2,d1*x/(d1*x+d2))
            sf = lambda x: beta_cdf(d2/2,d1/2,d2/(d1*x+d2))
            def pdf(x):
                if x == 0:
                    return mp.inf if d1 < 2 else mp.mpf(1) if d1 == 2 else mp.mpf(0)
                return mp.exp((d1/2)*mp.log(d1/d2)+(d1/2-1)*mp.log(x)-((d1+d2)/2)*mp.log1p(d1*x/d2)-mp.log(mp.beta(d1/2,d2/2)))
        else:
            require(False, "Unsupported probability distribution")

        original_cdf, original_sf, original_pdf = cdf, sf, pdf
        distribution_note = note

        def cdf(x):
            if discrete: x = mp.floor(x)
            if x < lo: return mp.mpf(0)
            if x >= hi: return mp.mpf(1)
            return original_cdf(x)

        def sf(x):
            if discrete: x = mp.floor(x)
            if x < lo: return mp.mpf(1)
            if x >= hi: return mp.mpf(0)
            return original_sf(x)

        def pdf(x):
            if x < lo or x > hi or not mp.isfinite(x) or discrete and x != mp.floor(x): return mp.mpf(0)
            if lo == hi: return mp.mpf(1)
            return original_pdf(x)

        def quantile(q):
            def reached(x): return sf(x) <= 1-q if q > mp.mpf('0.5') else cdf(x) >= q
            if q == 0 or q == 1 or lo == hi:
                value = lo if q == 0 else hi
            elif kind in ("normal", "lognormal"):
                z = normal_z(q)
                value = mu+sigma*z if kind == "normal" else mp.exp(mu+sigma*z)
            elif kind == "uniform": value = lo+(hi-lo)*q
            elif kind == "cauchy":
                value = location if q == mp.mpf('0.5') else location-scale/mp.tan(mp.pi*q) if q < mp.mpf('0.5') else location+scale/mp.tan(mp.pi*(1-q))
            elif kind == "exponential": value = -mp.log1p(-q)/rate
            elif kind == "weibull": value = scale*(-mp.log1p(-q))**(1/shape)
            elif kind in ("gamma", "beta"):
                # Search log(X) so tiny scales and very skewed distributions do
                # not lose all significant digits in a fixed linear bisection.
                center = mp.log(mean)
                limit = mp.log(hi) if mp.isfinite(hi) else mp.inf
                step = mp.mpf(1)
                left, right = center-step, min(limit,center+step)
                for _ in range(512):
                    if not reached(mp.exp(left)) and reached(mp.exp(right)): break
                    step *= 2
                    left, right = center-step, min(limit,center+step)
                require(not reached(mp.exp(left)) and reached(mp.exp(right)), "Quantile exceeds numerical range")
                for _ in range(mp.prec+8):
                    middle = (left+right)/2
                    if reached(mp.exp(middle)): right = middle
                    else: left = middle
                value = mp.exp((left+right)/2)
            else:
                # Monotone search uses the smaller tail and preserves discrete quantiles.
                left = lo if mp.isfinite(lo) else -1
                right = hi if mp.isfinite(hi) else (max(1,mean) if mean is not None and mp.isfinite(mean) else mp.mpf(1))
                for _ in range(512):
                    if not reached(left) or mp.isfinite(lo): break
                    left *= 2
                for _ in range(512):
                    if reached(right): break
                    right *= 2
                require(reached(right) and (not reached(left) or mp.isfinite(lo)), "Quantile exceeds numerical range")
                if discrete:
                    left, right = int(left), int(mp.ceil(right))
                    while left < right:
                        middle = (left+right)//2
                        if reached(middle): right = middle
                        else: left = middle+1
                    value = left
                else:
                    left, right = mp.mpf(left), mp.mpf(right)
                    for _ in range(mp.prec+8):
                        middle = (left+right)/2
                        if reached(middle): right = middle
                        else: left = middle
                    value = (left+right)/2
            return value

        if operation == "quantile":
            q = chance("q")
            value = quantile(q)
            formula = f"min {{x ∈ support : P(X ≤ x) ≥ {shown(q)}}}" if discrete else f"P(X ≤ x) = {shown(q)}"
            is_probability = False
            note = "Smallest supported integer with P(X ≤ x) ≥ q." if discrete else "Inverse cumulative probability."
        elif operation == "between":
            lower, upper = number("lower",True), number("upper",True)
            require(lower <= upper, "Lower bound must not exceed upper bound")
            a = mp.ceil(lower)-1 if discrete else lower
            # Use SF in the upper tail, CDF in the lower tail.
            value = sf(a)-sf(upper) if cdf(a) > mp.mpf('0.5') else cdf(upper)-cdf(a)
            formula = f"P({shown(lower)} ≤ X ≤ {shown(upper)})"
        else:
            x = number("x",True)
            if operation in ("eq", "density"):
                require((operation == "eq") == discrete, "Use density for continuous distributions")
                value = pdf(x)
                formula = f"P(X = {shown(x)})" if discrete else f"f({shown(x)})"
                is_probability = discrete
                if not discrete: note = "Density is not a probability and may exceed 1. For continuous X, P(X = x) = 0."
            elif operation == "le": value, formula = cdf(x), f"P(X ≤ {shown(x)})"
            elif operation == "lt": value, formula = cdf(mp.ceil(x)-1 if discrete else x), f"P(X < {shown(x)})"
            elif operation == "ge": value, formula = sf(mp.ceil(x)-1 if discrete else x), f"P(X ≥ {shown(x)})"
            elif operation == "gt": value, formula = sf(x), f"P(X > {shown(x)})"
            else: require(False, "Unsupported probability operation")
        details.extend([("E[X]",mean),("Var[X]",variance),("SD[X]",mp.sqrt(variance) if variance is not None else None)])
        if kind == "negativeBinomial" and note != distribution_note: note = distribution_note + " " + note
        if kind == "binomial" and operation == "eq" and n <= 200 and mp.isfinite(x) and x == mp.floor(x) and 0 <= x <= n:
            fp, k = rational("p"), int(x)
            exact = math.comb(n,k)*fp**k*(1-fp)**(n-k)
        # Quantile bounds remain useful with skew, infinite moments and large counts.
        # Preview precision is independent of the requested answer precision.
        if request.get("preview", True) and lo != hi:
            try:
                with mp.workdps(18):
                    plot_lo, plot_hi = quantile(mp.mpf('0.001')), quantile(mp.mpf('0.999'))
                if discrete:
                    start, end = int(plot_lo), int(plot_hi)
                    # Sample integer masses rather than dropping wide distributions.
                    xs = sorted(set(start+(end-start)*i//80 for i in range(81)))
                    if operation == "eq":
                        threshold = number("x",True)
                        if mp.isfinite(threshold) and threshold == mp.floor(threshold) and start <= threshold <= end:
                            xs = sorted(set(xs+[int(threshold)]))
                else:
                    xs = [plot_lo+(plot_hi-plot_lo)*i/80 for i in range(81)]
                    if kind == "cauchy":
                        # Wide heavy tails otherwise leave too few samples near the peak.
                        xs = sorted(set(xs+[quantile(mp.mpf(i)/100) for i in range(1,100)]))
                    # Extra log-spaced samples resolve peaks in right-skewed densities.
                    if kind in ("gamma", "beta", "lognormal", "weibull", "f") and plot_lo > 0 and plot_hi > plot_lo:
                        xs = sorted(set(xs+[mp.exp(mp.log(plot_lo)+(mp.log(plot_hi)-mp.log(plot_lo))*i/80) for i in range(81)]))
                points = []
                for x in xs:
                    y = pdf(x)
                    if not mp.isfinite(y): continue
                    selected = False
                    if operation == "between": selected = lower <= x <= upper
                    elif operation == "quantile": selected = x <= value
                    elif operation in ("le","lt","ge","gt","eq"):
                        threshold = number("x",True)
                        selected = {"le":x<=threshold,"lt":x<threshold,"ge":x>=threshold,"gt":x>threshold,"eq":x==threshold}[operation]
                    xf, yf = float(x), float(y)
                    if math.isfinite(xf) and math.isfinite(yf): points.append([xf,yf,selected])
                if len(points) > 1: plot = {"discrete":discrete,"points":points,"event":operation!="density",
                                                  "sampled":discrete and end-start > 80,"range":"0.1%–99.9%"}
            except (MathError, ArithmeticError, ValueError):
                # A preview outside numerical/float range must not discard a valid answer.
                plot = None
    else:
        require(False, "Unsupported probability category")

    if is_probability:
        value = max(mp.mpf(0),min(mp.mpf(1),value))
    result = {"value":shown(value), "formula":formula, "isProbability":is_probability,
              "details":[{"label":label,"value":shown(v)} for label,v in details], "note":note}
    if is_probability: result["percent"] = shown(value*100) + "%"
    if exact is not None and exact.numerator.bit_length() < 650 and exact.denominator.bit_length() < 650:
        result["fraction"] = str(exact)
    if plot: result["plot"] = plot
    return result
