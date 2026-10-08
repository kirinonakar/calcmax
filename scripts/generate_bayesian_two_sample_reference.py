"""Development-only independent SciPy convolution/evidence references."""
import json
from pathlib import Path
import numpy as np
import scipy
from scipy import integrate, optimize, special, stats

ROOT = Path(__file__).resolve().parents[1]
cases = []


def nig(data, mu, kappa, alpha, beta):
    n, mean = len(data), np.mean(data)
    k = kappa+n
    return ((kappa*mu+n*mean)/k, k, alpha+n/2,
            beta+np.sum((np.array(data)-mean)**2)/2+kappa*n*(mean-mu)**2/(2*k))


def evidence(groups, mu, kappa, alpha, beta):
    post = [nig(g,mu,kappa,alpha,beta) for g in groups]
    n = sum(map(len,groups))
    a = alpha+n/2
    b = sum(p[3]-beta for p in post)+beta
    return (-n*np.log(2*np.pi)/2+sum(np.log(kappa/p[1])/2 for p in post)
            +alpha*np.log(beta)-a*np.log(b)+special.gammaln(a)-special.gammaln(alpha))


for name, a, b, mode, mu, kappa, alpha, beta in [
    ('example equal', [10,11,9,10,12], [13,14,12,15,13], 'equal', 0, .01, 2, 1),
    ('null equal', [3,3,3], [3,3,3,3], 'equal', 3, .5, 2, 1),
    ('informative equal', [-2,0,1], [1,2,2,4], 'equal', 1, 2, .25, 3),
    ('example unequal', [10,11,9,10,12], [13,14,12,15,13], 'unequal', 0, .01, 2, 1),
    ('heterogeneous unequal', [-8,0,8], [2,3,4,3,3,2,4], 'unequal', 1, .2, 3, 2),
]:
    pa, pb = nig(a,mu,kappa,alpha,beta), nig(b,mu,kappa,alpha,beta)
    d = pb[0]-pa[0]
    expected = {'Mean A':float(np.mean(a)), 'Mean B':float(np.mean(b)),
                'Posterior Mean Difference (B - A)':float(d)}
    if mode == 'equal':
        shape, rate = alpha+(len(a)+len(b))/2, pa[3]+pb[3]-beta
        dist = stats.t(2*shape,loc=d,scale=np.sqrt(rate*(1/pa[1]+1/pb[1])/shape))
        # Independent evidence calculation under the induced H0:
        # variance ~ IG(alpha+.5,beta), common mean | variance ~ N(mu,variance/(2*kappa)).
        logbf = evidence([a,b],mu,kappa,alpha,beta)-evidence([a+b],mu,2*kappa,alpha+.5,beta)
        expected['Posterior Effect Size'] = float(d*np.exp(special.gammaln(shape+.5)-special.gammaln(shape))/np.sqrt(rate))
        probability, interval, sd = dist.sf(0), dist.interval(.95), dist.std()
    else:
        da, db = [stats.t(2*p[2],loc=p[0],scale=np.sqrt(p[3]/(p[2]*p[1]))) for p in (pa,pb)]
        prior = stats.t(2*alpha,loc=mu,scale=np.sqrt(beta/(alpha*kappa)))
        prior_density = integrate.quad(lambda x: prior.pdf(x)**2,-np.inf,np.inf,epsabs=1e-12)[0]
        post_density = integrate.quad(lambda x: da.pdf(x)*db.pdf(x),-np.inf,np.inf,epsabs=1e-12)[0]
        logbf = np.log(prior_density/post_density)
        cdf = lambda v: integrate.quad(lambda x: da.pdf(x)*db.cdf(x+v),-np.inf,np.inf,epsabs=1e-11)[0]
        probability = 1-cdf(0)
        sd = np.sqrt(da.var()+db.var())
        interval = [optimize.brentq(lambda v: cdf(v)-q,d-100*sd,d+100*sd) for q in (.025,.975)]
    expected.update({'difference posterior SD':float(sd), 'P(μB > μA)':float(probability),
                     'difference credible interval':list(map(float,interval)), 'log BF10':float(logbf),
                     'BF10':float(np.exp(logbf)), 'BF01':float(np.exp(-logbf))})
    cases.append(dict(name=name,arguments=[a,b,mode,mu,kappa,alpha,beta,.95,20000,0],
                      expected=expected,source=f'SciPy {scipy.__version__}; marginal evidence / Student-t convolution'))

(ROOT/'tests/fixtures/bayesian_two_sample_reference.json').write_text(json.dumps(cases,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
