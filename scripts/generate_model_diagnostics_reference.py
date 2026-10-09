"""Independent statsmodels/SciPy references for LMM profiles and small GEE."""
import json
import math
import random
from pathlib import Path
import numpy as np
import statsmodels.api as sm
from scipy.optimize import minimize_scalar, brentq
from scipy.stats import t

ROOT=Path(__file__).resolve().parents[1]
existing=json.loads((ROOT/'tests/fixtures/advanced_statistics_reference.json').read_text())
case=next(c for c in existing if c['name']=='Mixed random intercept ml')
rows=case['arguments'][0]; array=np.asarray(rows,float)
x=sm.add_constant(array[:,1:-1]); y=array[:,-1]; groups=array[:,0]; p=x.shape[1]; n=len(y)
same=(groups[:,None]==groups[None,:]).astype(float)

def fit(outcomes,reml=False,fixed=None):
    def objective(log_ratio,details=False):
        ratio=0 if log_ratio is None else np.exp(log_ratio)
        covariance=np.eye(n)+ratio*same
        vinvx=np.linalg.solve(covariance,x); vinvy=np.linalg.solve(covariance,outcomes)
        info=x.T@vinvx; right=x.T@vinvy
        beta=np.linalg.solve(info,right)
        if fixed is not None:
            index,value=fixed; free=[j for j in range(p) if j!=index]
            beta[index]=value
            beta[free]=np.linalg.solve(info[np.ix_(free,free)],right[free]-info[free,index]*value)
        residual=outcomes-x@beta; rss=residual@np.linalg.solve(covariance,residual)
        degree=n-p if reml else n
        value=degree*np.log(rss/degree)+np.linalg.slogdet(covariance)[1]
        if reml: value+=np.linalg.slogdet(info)[1]
        return (value,beta,rss/degree,ratio) if details else value
    solution=minimize_scalar(objective,bounds=(-16,16),method='bounded',options={'xatol':1e-10})
    return min([objective(None,True),objective(solution.x,True)],key=lambda v:v[0])

mixed={}
for method in ('ml','reml'):
    result=sm.MixedLM(y,x,groups=groups).fit(reml=method=='reml',method='powell',disp=False)
    mixed[method]={'log likelihood':result.llf}
base,beta,sigma,ratio=fit(y)
intervals=[]
for index in range(p):
    def crossing(value): return fit(y,fixed=(index,value))[0]-base-3.841458820694124
    bounds=[]
    for direction in (-1,1):
        distance=1.0
        while crossing(beta[index]+direction*distance)<0: distance*=2
        bounds.append(brentq(crossing,*sorted([beta[index],beta[index]+direction*distance])))
    intervals.append(bounds)
mixed['profile']=intervals
rng=random.Random(7); replicates=[]
for _ in range(100):
    outcomes=np.zeros(n)
    for group in sorted(set(groups)):
        effect=rng.gauss(0,1)*math.sqrt(ratio*sigma)
        for i in np.flatnonzero(groups==group): outcomes[i]=x[i]@beta+effect+rng.gauss(0,math.sqrt(sigma))
    replicates.append(fit(outcomes)[1])
mixed['bootstrap']=np.quantile(np.asarray(replicates),[.025,.975],axis=0).T.tolist()

gee=[]
for family in ('gaussian','binomial','poisson'):
    data=[[g,k,1+.2*k+.3*g+.4*((g+k)%3)] for g in range(1,9) for k in range(3)] if family=='gaussian' else [[g,k,(g+k)%2 if family=='binomial' else (g+k)%4+1] for g in range(1,9) for k in range(3)]
    arr=np.asarray(data,float); design=sm.add_constant(arr[:,1:-1]); outcome=arr[:,-1]
    if family=='binomial':
        binary_rng=random.Random(109)
        data=[]
        for group in range(1,13):
            effect=binary_rng.gauss(0,.8)
            for k in range(5): data.append([group,k,int(binary_rng.random()<1/(1+math.exp(-(-1+.4*k+effect))))])
        arr=np.asarray(data,float); design=sm.add_constant(arr[:,1:-1]); outcome=arr[:,-1]
    fam={'gaussian':sm.families.Gaussian,'binomial':sm.families.Binomial,'poisson':sm.families.Poisson}[family]()
    for correlation in ('independence','exchangeable','ar1'):
        structure={'independence':sm.cov_struct.Independence,'exchangeable':sm.cov_struct.Exchangeable,'ar1':lambda:sm.cov_struct.Autoregressive(grid=True)}[correlation]()
        result=sm.GEE(outcome,design,groups=arr[:,0],family=fam,cov_struct=structure).fit(cov_type='bias_reduced',maxiter=200,ctol=1e-10)
        df=len(set(arr[:,0]))-design.shape[1]
        gee.append({'family':family,'correlation':correlation,'rows':data,'estimates':result.params.tolist(),'SE':result.bse.tolist(),
            'CI95':[[b-t.ppf(.975,df)*se,b+t.ppf(.975,df)*se] for b,se in zip(result.params,result.bse)],
            'p':[2*t.sf(abs(b/se),df) for b,se in zip(result.params,result.bse)]})
destination=ROOT/'tests/fixtures/model_diagnostics_reference.json'
destination.write_text(json.dumps({'source':'statsmodels '+sm.__version__+' / SciPy dense Gaussian profiles','mixed rows':rows,'mixed':mixed,'gee':gee},indent=2)+'\n')
