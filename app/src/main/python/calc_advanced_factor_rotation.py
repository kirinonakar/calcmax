"""Oblique rotations and seeded Horn parallel analysis, using portable matrices."""
import math
import random
import mpmath as mp
from calc_shared import require, MathError


def oblique(loadings, method, varimax):
    p,k=loadings.rows,loadings.cols
    if k==1: return loadings,mp.eye(k)
    if method=='promax':
        norms=[float(mp.norm(loadings[i,:])) for i in range(p)]
        scaled=mp.matrix([[loadings[i,j]/(norms[i] or 1.) for j in range(k)] for i in range(p)])
        base=varimax(scaled)
        target=mp.matrix([[v*abs(v)**3 for v in base[i,:]] for i in range(p)])
        transform=(base.T*base)**-1*base.T*target
        metric=(transform.T*transform)**-1
        transform=transform*mp.diag([mp.sqrt(metric[j,j]) for j in range(k)])
        pattern=base*transform
        pattern=mp.matrix([[pattern[i,j]*norms[i] for j in range(k)] for i in range(p)])
        inv=transform**-1
        return pattern,inv*inv.T
    # Direct oblimin with delta=0 (quartimin), unit-column gradient projection.
    rotation=mp.eye(k); step=1.
    def criterion(pattern):
        sums=[sum(pattern[i,j]**2 for j in range(k)) for i in range(p)]
        gradient=mp.matrix([[pattern[i,j]*(sums[i]-pattern[i,j]**2) for j in range(k)] for i in range(p)])
        value=float(sum(pattern[i,j]**2*(sums[i]-pattern[i,j]**2) for i in range(p) for j in range(k)))/4
        return value,gradient
    for _ in range(2000):
        inv=rotation**-1; pattern=loadings*inv.T; value,g=criterion(pattern)
        gradient=-(pattern.T*g*inv).T
        tangent=gradient-rotation*mp.diag([(rotation.T*gradient)[j,j] for j in range(k)])
        norm=float(mp.norm(tangent))
        if norm<1e-7: return pattern,rotation.T*rotation
        step=min(step*2,100.)
        for _ in range(40):
            trial=rotation-step*tangent
            trial=trial*mp.diag([1/mp.norm(trial[:,j]) for j in range(k)])
            try: newvalue=criterion(loadings*(trial**-1).T)[0]
            except ZeroDivisionError: newvalue=math.inf
            if newvalue<=value-.5*step*norm**2: break
            step/=2
        else: raise MathError('Oblimin rotation failed to converge')
        rotation=trial
    raise MathError('Oblimin rotation did not converge')


def parallel_analysis(rows, corr, extraction, samples, seed, percentile):
    from calc_advanced_survey import correlation
    n,p=len(rows),len(rows[0]); rng=random.Random(seed)
    require(n>p,'Parallel analysis requires more rows than variables')
    def roots(matrix):
        if extraction=='pa':
            matrix=matrix.copy(); inv=matrix**-1
            for i in range(p): matrix[i,i]=1-1/inv[i,i]
        return list(reversed([float(v) for v in mp.eigsy(matrix,eigvals_only=True)]))
    observed=roots(corr); simulations=[[] for _ in range(p)]
    for _ in range(samples):
        null=correlation([[rng.gauss(0,1) for _ in range(p)] for _ in range(n)])[0]
        for values,value in zip(simulations,roots(null)): values.append(value)
    thresholds=[]
    for values in simulations:
        values.sort(); at=(samples-1)*percentile; left=int(at); right=min(left+1,samples-1)
        thresholds.append(values[left]+(values[right]-values[left])*(at-left))
    count=0
    for value,threshold in zip(observed,thresholds):
        if value<=max(0.,threshold): break
        count+=1
    return {'Parallel analysis':[{'Number':i+1,'Observed eigenvalue':v,'Null percentile eigenvalue':t,'Retain':int(i<count)} for i,(v,t) in enumerate(zip(observed,thresholds))],
            'Suggested factors':count,'Parallel samples':samples,'Parallel seed':seed,'Parallel percentile':percentile,
            'Parallel method':'Horn normal simulation; '+('SMC-reduced common roots' if extraction=='pa' else 'correlation component roots')}
