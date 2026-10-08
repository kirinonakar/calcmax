"""Independent normal two-sample comparison with proper NIG priors.

H1: means are conditionally independent N(mu0, variance/kappa0).
Equal mode shares one InvGamma(alpha0,beta0) variance; unequal mode has
independent variances with that prior. H0 is the H1 prior conditioned on
Delta = muB-muA = 0, including its induced nuisance-parameter prior.
Thus BF01 = posterior_density(Delta=0) / prior_density(Delta=0).
This is not the JZS/Cauchy Bayesian t-test. No MCMC or SciPy is needed.
"""
import math
import random
import mpmath as mp
from calc_shared import require
from calc_advanced_common import integer, number, option, vector
from calc_advanced_resampling import t_quantile
from calc_statistics import _t_sf


def posterior(data, mu, kappa, alpha, beta):
    n = len(data)
    average = sum(data)/n
    strength = kappa+n
    location = (kappa*mu+n*average)/strength
    rate = beta+sum((x-average)**2 for x in data)/2+kappa*n*(average-mu)**2/(2*strength)
    return location, strength, alpha+mp.mpf(n)/2, rate


def t_logpdf(x, location, scale, alpha):
    z = (x-location)/scale
    return (mp.loggamma(alpha+mp.mpf('.5'))-mp.loggamma(alpha)
            -mp.log(2*alpha*mp.pi)/2-mp.log(scale)
            -(alpha+mp.mpf('.5'))*mp.log1p(z*z/(2*alpha)))


def difference_log_density(first, second):
    """Student-t convolution at zero, integrating in standardized coordinates.

    Split at both centers and scales to resolve narrow or separated modes.
    The common coordinate shift avoids precision loss with large locations.
    """
    ma, ka, aa, ba = first
    mb, kb, ab, bb = second
    sa, sb = mp.sqrt(ba/(aa*ka)), mp.sqrt(bb/(ab*kb))
    center, ratio = (mb-ma)/sa, sb/sa
    ca = mp.exp(t_logpdf(0, 0, 1, aa))
    cb = mp.exp(t_logpdf(0, 0, ratio, ab))
    def density(z):
        return ca*cb*(1+z*z/(2*aa))**(-aa-mp.mpf('.5'))*(1+((z-center)/ratio)**2/(2*ab))**(-ab-mp.mpf('.5'))
    points = sorted(set([-mp.inf, -1, 0, 1, center-ratio, center, center+ratio, mp.inf]))
    value, error = mp.quad(density, points, error=True, maxdegree=8)
    require(value > 0 and error <= value*mp.mpf('1e-8'),
            'Bayes factor integration did not converge; check data and prior scale')
    return mp.log(value)-mp.log(sa)


def quantile(sorted_values, q):
    position = q*(len(sorted_values)-1)
    index = int(position)
    return sorted_values[index]+(position-index)*(sorted_values[min(index+1, len(sorted_values)-1)]-sorted_values[index])


def mcse(values):
    n = len(values)
    average = math.fsum(values)/n
    return math.sqrt(math.fsum((x-average)**2 for x in values)/(n*(n-1)))


def quantile_mcse(sorted_values, q):
    """Approximate quantile SE from a local quantile slope (IID binomial ranks)."""
    width = math.sqrt(q*(1-q)/len(sorted_values))
    return (quantile(sorted_values,min(1,q+width))-quantile(sorted_values,max(0,q-width)))/2


def calculate(engine, name, args):
    a, b = [[mp.mpf(x) for x in vector(arg, 2)] for arg in args[:2]]
    mode = option(args, 2, 'equal')
    require(mode in ('equal', 'unequal'), 'Variance model: equal or unequal')
    defaults = (0, '.01', 2, 1, '.95')
    mu, kappa, alpha, beta, level = [mp.mpf(number(args[i+3])) if len(args)>i+3 else mp.mpf(value) for i,value in enumerate(defaults)]
    require(kappa > 0 and alpha > 0 and beta > 0, 'Prior kappa, alpha and beta must be positive')
    require(0 < level < 1, 'Credible level must lie in (0,1)')
    samples = integer(args[8], 2000, 100000) if len(args)>8 else 20000
    seed = integer(args[9], 0, 2**32-1) if len(args)>9 else 0
    require(samples*(1-level)/2 >= 10, 'Increase posterior draws or reduce credible level (at least 10 expected draws per tail)')
    pa, pb = posterior(a, mu, kappa, alpha, beta), posterior(b, mu, kappa, alpha, beta)
    ma, ka, aa, ba = pa
    mb, kb, ab, bb = pb
    difference, weight = mb-ma, 1/ka+1/kb
    tail = (1-level)/2
    if mode == 'equal':
        shape, rate = alpha+mp.mpf(len(a)+len(b))/2, ba+bb-beta
        pa, pb = (ma,ka,shape,rate), (mb,kb,shape,rate)
        scale = mp.sqrt(rate*weight/shape)
        critical = t_quantile((1+level)/2, 2*shape)
        interval = [difference-critical*scale, difference+critical*scale]
        probability = _t_sf(-difference/scale, 2*shape)
        sd = mp.sqrt(rate*weight/(shape-1))
        log_prior = t_logpdf(0, 0, mp.sqrt(2*beta/(alpha*kappa)), alpha)
        log_post = t_logpdf(0, difference, scale, shape)
        effect_mean = difference*mp.exp(mp.loggamma(shape+mp.mpf('.5'))-mp.loggamma(shape))/mp.sqrt(rate)
    else:
        # Prior means have identical independent Student-t marginals.
        # Integral of the squared t density is available in closed form.
        log_prior = (2*(mp.loggamma(alpha+mp.mpf('.5'))-mp.loggamma(alpha))
                     +mp.loggamma(2*alpha+mp.mpf('.5'))-mp.loggamma(2*alpha+1)
                     -mp.log(2*mp.pi*beta/kappa)/2)
        log_post = difference_log_density(pa, pb)
        sd = mp.sqrt(ba/((aa-1)*ka)+bb/((ab-1)*kb))
    log_bf = log_prior-log_post
    rng = random.Random(seed)
    differences, effects, probabilities, effect_locations = [], [], [], []
    fa, fb = [tuple(map(float, p)) for p in (pa,pb)]
    d = float(difference)
    for _ in range(samples):
        va = fa[3]/rng.gammavariate(fa[2], 1)
        vb = va if mode == 'equal' else fb[3]/rng.gammavariate(fb[2], 1)
        conditional_sd = math.sqrt(va/fa[1]+vb/fb[1])
        delta = d+conditional_sd*rng.gauss(0,1)
        standardizer = math.sqrt((va+vb)/2)
        differences.append(delta)
        effects.append(delta/standardizer)
        effect_locations.append(d/standardizer)
        probabilities.append(math.erfc(-d/conditional_sd/math.sqrt(2))/2)
    require(all(math.isfinite(x) for x in differences+effects+effect_locations), 'Posterior simulation exceeds numeric range; rescale data and prior')
    effects.sort()
    effect_interval = [quantile(effects,float(tail)),quantile(effects,float(1-tail))]
    if mode == 'unequal':
        differences.sort()
        interval = [quantile(differences,float(tail)),quantile(differences,float(1-tail))]
        probability = math.fsum(probabilities)/samples
        effect_mean = math.fsum(effect_locations)/samples
    engine.note += (' Independent normal samples; difference = B - A. Proper normal-inverse-gamma H1 prior: '
                    'muA,muB | variances ~ independent Normal(mu0,variance/kappa0); '
                    'variance ~ InvGamma(alpha0,beta0), shared in equal mode, independent in unequal mode. '
                    'H0: muB-muA=0, with nuisance prior induced by conditioning H1 on this restriction. '
                    'BF10 uses the Savage-Dickey density ratio (analytic equal variance, numerical Student-t convolution unequal variance); '
                    'it is not a posterior hypothesis probability or a JZS/Cauchy t-test. '
                    'Intervals are equal-tailed H1 posterior credible intervals. Effect size = (muB-muA)/sqrt((varianceA+varianceB)/2). '
                    'Direct IID posterior simulation (no MCMC): effect interval in both modes; difference interval and probability in unequal mode. '
                    'MCSE measures simulation error, not posterior uncertainty. Priors depend on measurement units; adjust them to your scale.')
    return {'n A':len(a), 'n B':len(b), 'Mean A':sum(a)/len(a), 'Mean B':sum(b)/len(b),
            'variance model':mode, 'prior mean':mu, 'prior kappa':kappa, 'prior alpha':alpha, 'prior beta':beta,
            'posterior mean A':ma, 'posterior mean B':mb, 'Posterior Mean Difference (B - A)':difference,
            'difference posterior SD':sd, 'credible level':level, 'difference credible interval':interval,
            'difference interval method':'analytic Student t' if mode=='equal' else 'IID posterior simulation',
            'difference interval MCSE': [0,0] if mode=='equal' else [quantile_mcse(differences,float(tail)),quantile_mcse(differences,float(1-tail))],
            'P(μB > μA)':probability, 'probability MCSE':0 if mode=='equal' else mcse(probabilities),
            'Posterior Effect Size':effect_mean, 'effect mean MCSE':0 if mode=='equal' else mcse(effect_locations),
            'effect credible interval':effect_interval, 'effect interval method':'IID posterior simulation',
            'effect interval MCSE':[quantile_mcse(effects,float(tail)),quantile_mcse(effects,float(1-tail))],
            'BF10':mp.exp(log_bf), 'BF01':mp.exp(-log_bf), 'log BF10':log_bf,
            'BF method':'Savage-Dickey; analytic' if mode=='equal' else 'Savage-Dickey; numerical convolution',
            'posterior samples':samples, 'seed':seed}
