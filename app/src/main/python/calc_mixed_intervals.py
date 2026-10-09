"""Optional fixed-effect ML profile and parametric bootstrap intervals for LMMs.

Profiles refit variance components at every fixed coefficient. Bootstrap draws
both subject random effects and observation noise, preserving the design and
cluster sizes. Neither method supplies a Wald p-value disguised as a new test.
"""
import math
import random
import mpmath as mp
from calc_shared import require
from calc_advanced_common import integer, dot
from calc_nuts import quantile


def options(value):
    if str(value)=='wald': return None
    if str(value)=='profile': return ('profile',)
    require(isinstance(value,(list,tuple)) and 1<=len(value)<=3 and str(value[0])=='bootstrap',
            'Mixed-model CI: wald, profile, or [bootstrap,replicates,seed]')
    samples=integer(value[1],100,2000,capacity=True) if len(value)>1 else 200
    seed=integer(value[2],0,2147483647) if len(value)>2 else 0
    return 'bootstrap',samples,seed


def _fit(x,y,clusters,slopes,method,fixed=None):
    from calc_advanced_longitudinal import intercept_fit, random_effects_fit
    if slopes:
        fields,best=random_effects_fit(x,y,clusters,slopes,method,fixed)
        objective,beta,information,rss,theta=fields; converged=best[3]
    else:
        objective,beta,information,rss,ratio=intercept_fit(x,y,clusters,method,fixed)
        theta=mp.matrix([[ratio]]); converged=True
    sigma=rss/(len(y)-len(x[0]) if method=='reml' else len(y))
    return objective,list(map(float,beta)),sigma,theta,converged


def intervals(engine,result,x,y,clusters,slopes,method,settings):
    mode=settings[0]; result['CI method']='ML profile likelihood (fixed effects)' if mode=='profile' else 'Parametric bootstrap percentile (fixed effects)'
    rows=result['coefficients']
    for row in rows: row['p']=None; row['CI95']=None
    if not result['optimizer converged']:
        result['diagnostics']['warnings'].append('Alternative intervals unavailable because the original optimizer did not converge.')
        return
    if mode=='profile':
        reference,center,_,_,converged=_fit(x,y,clusters,slopes,'ml')
        require(converged,'ML reference fit did not converge; profile intervals unavailable')
        cutoff=3.841458820694124  # chi-square(1), 95% likelihood-ratio region.
        failed=0
        for index,row in enumerate(rows):
            def bound(direction):
                distance=max(float(row['SE'] or 0),abs(center[index])*1e-3,1e-6)
                inside=center[index]
                for _ in range(24):
                    outside=center[index]+direction*distance
                    objective,_,_,_,ok=_fit(x,y,clusters,slopes,'ml',(index,outside))
                    if not ok: return None
                    if objective-reference>=cutoff: break
                    inside=outside; distance*=2
                else: return None
                for _ in range(24):
                    middle=(inside+outside)/2
                    objective,_,_,_,ok=_fit(x,y,clusters,slopes,'ml',(index,middle))
                    if not ok: return None
                    if objective-reference>=cutoff: outside=middle
                    else: inside=middle
                return (inside+outside)/2
            low,high=bound(-1),bound(1)
            if low is None or high is None: failed+=1
            else: row['CI95']=[low,high]
        result['profile ML estimates']=center
        result['profile unavailable intervals']=failed
        engine.note+=' Fixed-effect 95% profile likelihood intervals reoptimize variance components using ML, including when the coefficient table uses REML. Wald p-values are omitted. Variance-component intervals are not computed.'
        if failed:
            warning='Some profile bounds could not be bracketed with converged fits; those intervals are unavailable.'
            result['diagnostics']['warnings'].append(warning); engine.note+=' '+warning
    else:
        _,beta,sigma,theta,converged=_fit(x,y,clusters,slopes,method)
        require(converged,'Bootstrap reference fit did not converge')
        count=theta.rows; eigenvalues,vectors=mp.eigsy(theta*sigma)
        root=vectors*mp.diag([mp.sqrt(max(0,v)) for v in eigenvalues])
        rng=random.Random(settings[2]); draws=[]
        for _ in range(settings[1]):
            outcomes=[0.0]*len(y)
            for cluster in clusters:
                effects=list(map(float,root*mp.matrix([rng.gauss(0,1) for _ in range(count)])))
                for i in cluster:
                    design=[1.0]+[x[i][s] for s in slopes]
                    outcomes[i]=dot(x[i],beta)+dot(design,effects)+rng.gauss(0,math.sqrt(sigma))
            _,estimate,_,_,ok=_fit(x,outcomes,clusters,slopes,method)
            if ok: draws.append(estimate)
        result['bootstrap requested']=settings[1]; result['bootstrap successful']=len(draws); result['bootstrap seed']=settings[2]
        if len(draws)>=math.ceil(.9*settings[1]):
            for index,row in enumerate(rows):
                values=[draw[index] for draw in draws]
                row['CI95']=[quantile(values,.025),quantile(values,.975)]
        else:
            warning='Fewer than 90% of bootstrap refits converged; bootstrap intervals are unavailable.'
            result['diagnostics']['warnings'].append(warning); engine.note+=' '+warning
        engine.note+=' Fixed-effect 95% parametric bootstrap percentile intervals simulate Gaussian subject random effects and observation noise, then refit with '+method.upper()+'. Wald p-values are omitted; simulation error remains, so increase replicates for final inference.'
    result['diagnostics']['inference']=result['CI method'] if all(row['CI95'] is not None for row in rows) else 'alternative intervals partly or fully unavailable'
