"""Development-only SciPy references; SciPy is not a runtime dependency."""
import json
from pathlib import Path
import numpy as np
import scipy
from scipy import special, stats

ROOT = Path(__file__).resolve().parents[1]
cases = []


def add(name, arguments, expected):
    cases.append(dict(function=name, arguments=arguments, expected=expected, source=f'SciPy {scipy.__version__}', tolerance=2e-9))


for successes, trials, alpha, beta, level, threshold in [
    (7, 10, 1, 1, .95, .5), (0, 100, .5, .5, .99, .1),
    (50000, 100000, 2, 3, .95, .5), (0, 1, .05, .1, .95, .2),
]:
    a, b = alpha+successes, beta+trials-successes
    distribution = stats.beta(a, b)
    log_bf = special.betaln(a, b)-special.betaln(alpha, beta)-successes*np.log(threshold)-(trials-successes)*np.log1p(-threshold)
    add('bayesproportion', [[[successes, trials]], alpha, beta, level, threshold],
        {'posterior mean':float(distribution.mean()), 'posterior SD':float(distribution.std()),
         'credible interval':list(map(float, distribution.interval(level))),
         'P(p > threshold)':float(distribution.sf(threshold)), 'log BF10':float(log_bf)})

for count, exposure, shape, rate, level, threshold in [
    (8, 5, 1, 1, .95, 1), (0, .5, .1, 2, .99, .2),
    (10000, 2500, 2, 3, .95, 4),
]:
    a, b = shape+count, rate+exposure
    distribution = stats.gamma(a, scale=1/b)
    add('bayesrate', [[[count, exposure]], shape, rate, level, threshold],
        {'posterior mean':float(distribution.mean()), 'posterior SD':float(distribution.std()),
         'credible interval':list(map(float, distribution.interval(level))),
         'P(rate > threshold)':float(distribution.sf(threshold)),
         'predictive count SD':float(np.sqrt(a*(b+1))/b)})

for data, mu, kappa, alpha, beta, level, threshold in [
    ([1,2,3,4,5], 0, 1, 2, 1, .95, 0),
    ([10,12,8,11], 10, .5, 2, 10, .9, 11),
    ([2], 0, 1, .1, 1, .99, 3),
    ([3,3,3], 3, 2, 1, 1, .95, 3),
]:
    n, average = len(data), np.mean(data)
    k, a = kappa+n, alpha+n/2
    m = (kappa*mu+n*average)/k
    b = beta+sum((v-average)**2 for v in data)/2+kappa*n*(average-mu)**2/(2*k)
    scale = np.sqrt(b/(a*k))
    distribution = stats.t(2*a, loc=m, scale=scale)
    predictive = stats.t(2*a, loc=m, scale=scale*np.sqrt(k+1))
    expected = {'posterior mean':float(distribution.mean()), 'mean posterior t scale':float(scale),
                'credible interval':list(map(float, distribution.interval(level))),
                'P(mean > threshold)':float(distribution.sf(threshold)),
                'predictive interval':list(map(float, predictive.interval(level)))}
    if a > 1: expected['posterior SD'] = float(distribution.std())
    add('bayesmean', [data, mu, kappa, alpha, beta, level, threshold], expected)

(ROOT/'tests/fixtures/bayesian_statistics_reference.json').write_text(json.dumps(cases, indent=2)+'\n', encoding='utf-8')
