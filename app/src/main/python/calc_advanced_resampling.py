"""Bootstrap intervals, noncentral-t power and sample size, and KS tests."""
from calc_limits import within_limit
import math
import random
import statistics
import mpmath as mp
from calc_shared import require
from calc_advanced_common import (
    integer, mean, number, option, vector,
)


def t_quantile(level,df):
    """Central Student-t quantile by bisection on the regularized incomplete beta."""
    with mp.workdps(30):
        target=mp.mpf(level); nu=mp.mpf(df)
        def cdf(value): return 1-mp.betainc(nu/2,mp.mpf('.5'),0,nu/(nu+value*value),regularized=True)/2
        low,high=mp.mpf(0),mp.mpf(4)
        while cdf(high)<target: high*=2
        for _ in range(70):
            middle=(low+high)/2
            if cdf(middle)<target: low=middle
            else: high=middle
        return float((low+high)/2)


def t_test_power(effect,size,alpha,kind,sides):
    """Exact noncentral-t power integrated over the chi-square mass of the scale mixture."""
    independent=kind=='independent'
    degrees=2*size-2 if independent else size-1
    shift=effect*math.sqrt(size/2 if independent else size)
    critical=t_quantile(1-alpha/2 if sides=='two' else 1-alpha,degrees)
    with mp.workdps(30):
        nu=mp.mpf(degrees); delta=mp.mpf(shift); cutoff=mp.mpf(critical)
        low=max(mp.mpf(0),nu-12*mp.sqrt(2*nu)-50); high=nu+12*mp.sqrt(2*nu)+50
        constant=mp.power(2,nu/2)*mp.gamma(nu/2)
        def integrand(weight):
            density=mp.power(weight,nu/2-1)*mp.exp(-weight/2)/constant
            scaled=mp.sqrt(weight/nu)
            if sides=='two': return (mp.ncdf(-cutoff*scaled-delta)+mp.ncdf(delta-cutoff*scaled))*density
            if sides=='greater': return mp.ncdf(delta-cutoff*scaled)*density
            return mp.ncdf(-cutoff*scaled-delta)*density
        return float(mp.quad(integrand,[low,high],maxdegree=8))


def bayesian_statistic(values,statistic):
    """Weighted empirical functional; row order retains paired-weight identity."""
    origin=min(values); scale=max(abs(v-origin) for v in values)
    require(math.isfinite(scale),'Data range is too large')
    z=[(v-origin)/scale if scale else 0.0 for v in values]
    order=sorted(range(len(values)),key=values.__getitem__)
    def weighted(weights):
        mass=math.fsum(weights)
        if statistic=='median':
            cumulative=0.0
            for i in order:
                cumulative+=weights[i]
                if cumulative>=mass/2: return values[i]
            return values[order[-1]]
        center=math.fsum(v*w for v,w in zip(z,weights))/mass
        if statistic=='mean': return origin+scale*center
        sd=scale*math.sqrt(math.fsum(w*(v-center)**2 for v,w in zip(z,weights))/mass)
        return sd if statistic=='stdev' else sd*sd
    return weighted


def calculate(engine,name,a):
    if name=='bayesbootstrap':
        compare=len(a)>1 and isinstance(a[1],(list,tuple))
        require(compare or len(a)<=5,'Single-sample Bayesian bootstrap accepts at most five arguments')
        shift=1 if compare else 0
        statistic=option(a,1+shift,'mean')
        level=number(a[2+shift]) if len(a)>2+shift else .95
        count=integer(a[3+shift],100,20000,capacity=True) if len(a)>3+shift else 10000
        seed=integer(a[4+shift],0,2**32-1) if len(a)>4+shift else 0
        mode=option(a,6,'independent') if compare else 'single'
        require(not compare or mode in ('independent','paired'),'Comparison: independent or paired')
        x=vector(a[0],2); y=vector(a[1],2) if compare else []
        require(mode!='paired' or len(x)==len(y),'Paired comparison requires equal sample lengths')
        if mode!='paired': x.sort(); y.sort()
        require(statistic in ('mean','median','variance','stdev') and 0<level<1,
                'Statistic: mean, median, variance, stdev; credible level in (0,1)')
        require(within_limit((len(x)+len(y))*count,2000000),'Bootstrap limit: two million weighted values')
        weighted=bayesian_statistic(x,statistic)
        other=bayesian_statistic(y,statistic) if compare else None
        paired_mean=bayesian_statistic([v-u for u,v in zip(x,y)],'mean') if mode=='paired' and statistic=='mean' else None
        def sample_estimate(values,function):
            if statistic!='median': return function([1.0]*len(values))
            ordered=sorted(values);middle=len(ordered)//2
            if len(ordered)%2: return ordered[middle]
            # The data range was checked above; this midpoint avoids overflow
            # when averaging two large finite middle observations.
            return ordered[middle-1]+(ordered[middle]-ordered[middle-1])/2
        first_estimate=sample_estimate(x,weighted)
        second_estimate=sample_estimate(y,other) if compare else None
        estimate=paired_mean([1.0]*len(x)) if paired_mean else second_estimate-first_estimate if compare else first_estimate
        rng=random.Random(seed)
        draws=[]
        for _ in range(count):
            weights=[rng.expovariate(1.0) for _ in x]
            first=weighted(weights)
            if compare:
                second=other(weights if mode=='paired' else [rng.expovariate(1.0) for _ in y])
                draws.append(paired_mean(weights) if paired_mean else second-first)
            else: draws.append(first)
        draws.sort()
        require(all(math.isfinite(v) for v in draws),'Statistic exceeds the numeric range')
        def quantile(q):
            pos=q*(count-1); j=int(pos)
            return draws[j]+(pos-j)*(draws[min(j+1,count-1)]-draws[j])
        lower,upper=quantile((1-level)/2),quantile((1+level)/2)
        posterior=math.fsum(v/count for v in draws); sd=statistics.stdev(draws)
        require(math.isfinite(posterior) and math.isfinite(sd),'Posterior summary exceeds the numeric range')
        # Keep plotted draws out of the reusable answer; histograms retain all draws.
        low,high=draws[0],draws[-1]
        width=(high-low)/32 if high>low else 1.0
        bins=[0]*32 if high>low else [count]
        if high>low:
            for value in draws: bins[min(31,int((value-low)/width))]+=1
        edges=[low+i*width for i in range(len(bins)+1)]
        if high==low:
            padding=max(.5,abs(low)*.05)
            edges=[low-padding,high+padding]
        require(all(math.isfinite(v) for v in edges),'Histogram range exceeds the numeric range')
        engine.statistics_plots=[{'kind':'histogram','title':'Bayesian bootstrap posterior',
                                 'edges':edges,'counts':bins,'interval':[lower,upper],
                                 'estimate':estimate,'level':level,'statistic':'Difference (B − A)' if compare else statistic}]
        engine.note += (' Bayesian bootstrap with Dirichlet(1,…,1) weights on observed values; IID observations. '
                        'Equal-tailed simulated credible interval; deterministic seed. '
                        'Median estimate: ordinary sample median (average of the two middle values for even n). '
                        'Median posterior draws: Lower weighted quantile = inf{t: weighted empirical CDF(t) >= 0.5}; '
                        'variance/stdev describe the weighted population (no n−1 correction).')
        result={'statistic':statistic,'estimate':estimate,
                'posterior mean':posterior,'posterior SD':sd,'posterior mean MCSE':sd/math.sqrt(count),
                'credible interval':[lower,upper],'credible level':level,'draws':count,'seed':seed}
        if statistic=='median':
            result['estimate median definition']='Sample median (midpoint for even n)'
            result['posterior median definition']='Lower weighted quantile'
        if compare:
            labels=engine.request.get('statisticsTermLabels',{})
            engine.statistics_plots[0]['groupLabels']=[labels.get('sample:A','Group A'),labels.get('sample:B','Group B')]
            probability=sum(v>0 for v in draws)/count
            result.update({'comparison':mode,'contrast':'B − A','n A':len(x),'n B':len(y),
                           'estimate A':first_estimate,'estimate B':second_estimate,
                           'P(difference > 0)':probability,'P(difference < 0)':sum(v<0 for v in draws)/count,
                           'P(difference = 0)':sum(v==0 for v in draws)/count,
                           'probability MCSE':math.sqrt(probability*(1-probability)/count)})
            engine.note += (' Comparison is statistic(B) − statistic(A). '+
                            ('Paired rows share one Dirichlet weight vector; the two marginal statistics are compared (not the statistic of row differences).' if mode=='paired'
                             else 'Independent groups use independent Dirichlet weights; sample sizes may differ.'))
        return result
    if name=='bootstrapci':
        x=vector(a[0],2); statistic=option(a,1,'mean'); level=number(a[2]) if len(a)>2 else .95; count=integer(a[3],100,20000,capacity=True) if len(a)>3 else 2000; seed=integer(a[4],0,2**32-1) if len(a)>4 else 0
        require(statistic in ('mean','median','stdev') and 0<level<1,'Statistic: mean, median, stdev; confidence level in (0,1)')
        require(within_limit(len(x)*count,2000000),'Bootstrap limit: two million sampled values')
        f={'mean':mean,'median':statistics.median,'stdev':statistics.stdev}[statistic]; rng=random.Random(seed); draws=sorted(f(rng.choices(x,k=len(x))) for _ in range(count))
        def quantile(q): pos=q*(count-1); j=int(pos); return draws[j]+(pos-j)*(draws[min(j+1,count-1)]-draws[j])
        engine.note += ' Nonparametric percentile bootstrap CI; IID observations, deterministic seed.'
        return {'estimate':f(x),'lower':quantile((1-level)/2),'upper':quantile((1+level)/2),'confidence level':level,'resamples':count,'seed':seed}
    if name in ('testpower','samplesize'):
        effect=abs(number(a[0])); alpha=number(a[2]) if len(a)>2 else .05; kind=option(a,3,'independent'); sides=option(a,4,'two')
        require(effect>0 and 0<alpha<1 and kind in ('independent','paired','onesample'),'Positive Cohen d, alpha in (0,1), and independent/paired/onesample required')
        require(sides in ('two','greater','less'),'Alternative: two, greater, or less')
        engine.note += ' Exact noncentral-t power for standardized mean differences; equal independent groups. n is per group or number of pairs.'
        if name=='testpower':
            n=integer(a[1],2,10000000,capacity=True); return {'power':t_test_power(effect,n,alpha,kind,sides),'n per group / pairs':n,'alpha':alpha,'alternative':sides}
        target=number(a[1]) if len(a)>1 else .8
        # samplesize(d,target,alpha,kind,alternative), unlike testpower(d,n,alpha,kind,alternative).
        require(0<target<1,'Target power must lie in (0,1)')
        z=statistics.NormalDist().inv_cdf(1-alpha/(2 if sides=='two' else 1)); zpower=statistics.NormalDist().inv_cdf(target)
        estimate=int(math.ceil(2*((z+zpower)/effect)**2 if kind=='independent' else ((z+zpower)/effect)**2))
        require(within_limit(estimate,10000000),'Required sample size exceeds limit')
        low=max(2,estimate-8); high=max(2,estimate+8)
        if t_test_power(effect,low,alpha,kind,sides)>=target:
            low,high=2,max(2,estimate)
        else:
            while t_test_power(effect,high,alpha,kind,sides)<target:
                low=high+1; high*=2
                require(within_limit(high,20000000),'Required sample size exceeds limit')
        while low<high:
            middle=(low+high)//2
            if t_test_power(effect,middle,alpha,kind,sides)>=target: high=middle
            else: low=middle+1
        return {'n per group / pairs':low,'total n':low*2 if kind=='independent' else low,'achieved power':t_test_power(effect,low,alpha,kind,sides),'target power':target,'alpha':alpha,'alternative':sides}
    x=sorted(vector(a[0],2))
    if isinstance(a[1],(list,tuple)):
        require(len(a)==2,'Two-sample KS takes two lists')
        y=sorted(vector(a[1],2)); n=len(x); m=len(y); d=max(abs(sum(u<=v for u in x)/n-sum(u<=v for u in y)/m) for v in sorted(set(x+y)))
        # Exact lattice probability for continuous data; strict interior counts complement >= D.
        if len(set(x+y))==n+m and n*m<=250000:
            threshold=round(d*n*m); ways=[0]*(m+1)
            for i in range(n+1):
                for j in range(m+1):
                    if abs(i*m-j*n)>=threshold: ways[j]=0
                    elif i==j==0: ways[j]=1
                    else: ways[j]=(ways[j] if i else 0)+(ways[j-1] if j else 0)
            paths=math.comb(n+m,n)
            p=float(mp.mpf(paths-ways[m])/paths); method='exact continuous two-sample'
        else:
            p=ks_asymptotic(d,math.sqrt(n*m/(n+m))); method='asymptotic; ties invalidate continuous-null calibration'
    else:
        require(str(a[1]) in ('normal','uniform'),'One-sample KS distribution: normal or uniform')
        loc=number(a[2]) if len(a)>2 else 0; scale=number(a[3]) if len(a)>3 else 1; require(scale>0,'Scale must be positive')
        cdf=(lambda v: statistics.NormalDist(loc,scale).cdf(v)) if str(a[1])=='normal' else lambda v: min(1,max(0,(v-loc)/scale))
        n=len(x); d=max(max((i+1)/n-cdf(v),cdf(v)-i/n) for i,v in enumerate(x)); p=ks_asymptotic(d,math.sqrt(n)); method='one-sample asymptotic, specified parameters'
        engine.note += ' Specify distribution parameters independently of data; fitted parameters need a different calibration.'
    engine.note += ' KS assumes continuous distributions. '+method+'.'
    return {'D':d,'p':max(0,min(1,p)),'method':method}


def ks_asymptotic(d, effective):
    if d<=0: return 1.0
    z=d*(effective+.12+.11/effective)
    if z<1.18:
        return 1-math.sqrt(2*math.pi)/z*sum(math.exp(-(2*j-1)**2*math.pi**2/(8*z*z)) for j in range(1,50))
    return 2*sum((-1)**(j-1)*math.exp(-2*j*j*z*z) for j in range(1,50))
