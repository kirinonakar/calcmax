"""Numerical probability tools shared by Android and WASM. No SciPy required.

Tails are evaluated directly; subtracting a rounded CDF loses rare-event accuracy.
Geometric X counts trials through the first success (support 1, 2, ...).
Negative binomial X counts failures before the r-th success (support 0, 1, ...).
"""
import math
import re
import mpmath
from fractions import Fraction
from calc_shared import require

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

    def positive(key):
        value = number(key)
        require(value > 0, "This parameter must be greater than zero" + f" ({key})")
        return value

    def shown(value):
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
        a, b = chance("pa"), chance("pb")
        independent = request.get("independent") is True
        ab = a*b if independent else chance("intersection")
        fa, fb = rational("pa"), rational("pb")
        fab = fa*fb if independent else rational("intersection")
        require(max(0, fa+fb-1) <= fab <= min(fa, fb), "Intersection is inconsistent with P(A) and P(B)")
        if operation == "conditional":
            require(b > 0, "Conditioning event has zero probability")
            value, formula = ab/b, "P(A | B) = P(A ∩ B) / P(B)"
        elif operation == "reverse":
            require(a > 0, "Conditioning event has zero probability")
            value, formula = ab/a, "P(B | A) = P(A ∩ B) / P(A)"
        else:
            choices = {"intersection": (ab, "P(A ∩ B)"), "union": (a+b-ab, "P(A ∪ B) = P(A) + P(B) − P(A ∩ B)"),
                       "onlyA": (a-ab, "P(A ∩ Bᶜ) = P(A) − P(A ∩ B)"), "neither": (1-a-b+ab, "P(Aᶜ ∩ Bᶜ) = 1 − P(A ∪ B)")}
            require(operation in choices, "Unsupported probability operation")
            value, formula = choices[operation]
        details = [("P(A ∩ B)", ab), ("P(A ∪ B)", a+b-ab), ("P(Aᶜ ∩ Bᶜ)", 1-a-b+ab)]
        exact = {"intersection":fab,"union":fa+fb-fab,"onlyA":fa-fab,"neither":1-fa-fb+fab}.get(operation)
        if operation == "conditional": exact = fab/fb
        if operation == "reverse": exact = fab/fa
        if independent:
            note = "Independent events: P(A ∩ B) = P(A) × P(B)."
    elif category == "bayes":
        prior, likelihood, false_positive = chance("prior"), chance("likelihood"), chance("falsePositive")
        require(operation in ("posterior", "negative"), "Unsupported probability operation")
        if operation == "negative":
            likelihood, false_positive = 1-likelihood, 1-false_positive
        evidence = prior*likelihood + (1-prior)*false_positive
        require(evidence > 0, "Conditioning event has zero probability")
        value = prior*likelihood/evidence
        event = "Bᶜ" if operation == "negative" else "B"
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
        else:
            require(False, "Unsupported probability operation")
        details = [("E[X]", n*p), ("SD[X]", mp.sqrt(n*p*(1-p)))]
        note = "Trials are independent with the same success probability."
        if n <= 200:
            fp = rational("p")
            exact = 1-(1-fp)**n if operation == "atLeastOne" else fp**n if operation == "all" else (1-fp)**n if operation == "none" else math.comb(n,k)*fp**k*(1-fp)**(n-k)
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
            mean, variance = (mp.mpf(0) if df > 1 else None), (df/(df-2) if df > 2 else None)
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
            mean = d2/(d2-2) if d2 > 2 else None
            variance = 2*d2*d2*(d1+d2-2)/(d1*(d2-2)**2*(d2-4)) if d2 > 4 else None
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

        if operation == "quantile":
            q = chance("q")
            def reached(x): return sf(x) <= 1-q if q > mp.mpf('0.5') else cdf(x) >= q
            if q == 0 or q == 1 or lo == hi:
                value = lo if q == 0 else hi
            elif kind in ("gamma", "beta", "lognormal", "weibull"):
                # Search log(X) so tiny scales and very skewed distributions do
                # not lose all significant digits in a fixed linear bisection.
                center = mu if kind == "lognormal" else mp.log(mean)
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
                right = hi if mp.isfinite(hi) else max(1,mean or 1)
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
            formula = f"P(X ≤ x) = {shown(q)}"
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
        if mean is not None: details.append(("E[X]",mean))
        if variance is not None: details.append(("SD[X]",mp.sqrt(variance)))
        if kind == "negativeBinomial" and note != distribution_note: note = distribution_note + " " + note
        if kind == "binomial" and operation == "eq" and n <= 200 and mp.isfinite(x) and x == mp.floor(x) and 0 <= x <= n:
            fp, k = rational("p"), int(x)
            exact = math.comb(n,k)*fp**k*(1-fp)**(n-k)
        # A bounded preview of the mass/density, independent of the calculation's range.
        # Omit singular boundary points and distributions without finite variance.
        if mean is not None and variance is not None and variance > 0:
            scale = mp.sqrt(variance)
            plot_lo, plot_hi = max(lo,mean-4*scale), min(hi,mean+4*scale)
            if discrete:
                start, end = int(mp.ceil(plot_lo)), int(mp.floor(plot_hi))
                xs = list(range(start,end+1)) if end-start <= 80 else []
            else:
                xs = [plot_lo+(plot_hi-plot_lo)*i/80 for i in range(81)]
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
            if len(points) > 1: plot = {"discrete":discrete,"points":points,"event":operation!="density"}
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
