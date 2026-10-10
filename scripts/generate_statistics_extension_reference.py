"""Development-only NumPy/statsmodels/factor_analyzer/semopy/SciPy references.

Run with build/social-reference-deps. None of these packages enter the runtime.
"""
import sys
from pathlib import Path
sys.path.insert(0,sys.argv[1] if len(sys.argv)>1 else 'build/social-reference-deps')
import json
import random
import numpy as np
import pandas as pd
from scipy.optimize import minimize
from statsmodels.multivariate.manova import MANOVA
from factor_analyzer.rotator import Rotator
from semopy import Model, ModelMeans
from statistics_social_schema import survey

ROOT=Path(__file__).resolve().parents[1]; cases=[]; x=np.array(survey)
def add(name,args,expected,tolerance=5e-5):
    cases.append(dict(function=name,arguments=args,expected=expected,tolerance=tolerance))

corr=np.corrcoef(x,rowvar=False); inv=np.linalg.inv(corr); h=1-1/np.diag(inv)
for _ in range(2000):
    reduced=corr.copy(); np.fill_diagonal(reduced,h); values,vectors=np.linalg.eigh(reduced)
    pa=vectors[:,-2:][:,::-1]*np.sqrt(values[-2:][::-1]); updated=(pa**2).sum(axis=1)
    if np.max(abs(updated-h))<1e-10: break
    h=updated
for extraction in ('pa','pca'):
    values,vectors=np.linalg.eigh(corr)
    initial=pa if extraction=='pa' else vectors[:,-2:][:,::-1]*np.sqrt(values[-2:][::-1])
    for rotation in ('promax','oblimin'):
        rotator=Rotator(method=rotation,tol=1e-8,max_iter=3000)
        pattern=rotator.fit_transform(initial); phi=rotator.phi_
        signs=np.array([1 if pattern[np.argmax(abs(pattern[:,j])),j]>=0 else -1 for j in range(2)])
        pattern*=signs; phi*=signs[:,None]*signs[None,:]; structure=pattern@phi
        expected=[(['loadings',i,j],pattern[i,j]) for i in range(6) for j in range(2)]
        expected += [(['Factor correlations',i,j],phi[i,j]) for i in range(2) for j in range(2)]
        expected += [(['Structure loadings',i,j],structure[i,j]) for i in range(6) for j in range(2)]
        add('efa',[survey,2,rotation,extraction],expected)
rng=random.Random(7); null=[]
for _ in range(30):
    sim=np.array([[rng.gauss(0,1) for j in range(6)] for i in range(48)])
    null.append(np.linalg.eigvalsh(np.corrcoef(sim,rowvar=False))[::-1])
threshold=np.quantile(null,.95,axis=0)
add('efa',[survey,2,'none','pca',30,7,.95],[(['Parallel analysis',j,'Null percentile eigenvalue'],threshold[j]) for j in range(6)])

def mv_expected(stats,prefix=[]):
    keys=['Pillai\'s trace','Wilks\' lambda','Hotelling-Lawley trace','Roy\'s greatest root']
    return [(prefix+['Multivariate tests',i,key],float(stats.loc[label,column])) for i,label in enumerate(keys) for key,column in [('Statistic','Value'),('F','F Value'),('df1','Num DF'),('df2','Den DF'),('p','Pr > F')]]
rng=np.random.default_rng(1011); rows=[]
for a in range(3):
    for b in range(2):
        for _ in range(9+a+b):
            noise=rng.multivariate_normal([0,0],[[1,.3],[.3,1.5]])
            rows.append([a+1,b+1,float(a+b+.5*a*b+noise[0]),float(.4*a-b+noise[1])])
frame=pd.DataFrame(rows,columns=['a','b','y','z'])
fit=MANOVA.from_formula('y+z ~ C(a, Sum)*C(b, Sum)',frame).mv_test()
expected=[]
for block,label in enumerate(['C(a, Sum)','C(b, Sum)','C(a, Sum):C(b, Sum)']):
    for path,value in mv_expected(fit.results[label]['stat']): path[1]+=block*4; expected.append((path,value))
add('manova',[rows,'factorial',2,2],expected)
wide=rng.normal(size=(40,6)); wide[:,2:4]+=.3; wide[:,4:6]+=.8
contrasts=wide[:,:4]-np.tile(wide[:,4:6],(1,2))
fit=MANOVA(contrasts,np.ones((40,1))).mv_test().results['x0']['stat']
add('manova',[wide.tolist(),'repeated',3],mv_expected(fit))

description='f1 =~ v1+v2+v3\nf2 =~ v4+v5+v6\nf2 =~ v2'
for function,paths in [('cfa',''),('sem','\nf2 ~ f1')]:
    model=Model(description+paths)
    opt=model.fit(pd.DataFrame(x,columns=['v'+str(i) for i in range(1,7)]),options=dict(ftol=1e-12,maxiter=3000))
    assert opt.success
    sigma=model.calc_sigma()[0]
    expected=[(['Implied covariance',i,j],sigma[i,j]) for i in range(6) for j in range(6)]
    args=[survey,[1,1,1,2,2,2]]+([[[1,2]]] if function=='sem' else [])+[[[2,2]]]
    add(function,args,expected)

masked=x.copy()
for i in range(len(masked)):
    if i%3==0: masked[i,i%6]=np.nan
    if i%5==0: masked[i,(i+2)%6]=np.nan
model=ModelMeans('f1 =~ v1+v2+v3\nf2 =~ v4+v5+v6')
opt=model.fit(pd.DataFrame(masked,columns=['v'+str(i) for i in range(1,7)]),options=dict(ftol=1e-10,maxiter=3000)); assert opt.success
sigma=model.calc_sigma()[0]; means=model.mx_gamma2[:,0]
data=[['NA' if np.isnan(v) else float(v) for v in row] for row in masked]
expected=[(['Implied covariance',i,j],sigma[i,j]) for i in range(6) for j in range(6)]
expected += [(['Indicator means',i,'Mean'],means[i]) for i in range(6)]
patterns={}
for row in masked:
    at=tuple(np.where(~np.isnan(row))[0]); patterns.setdefault(at,[]).append(row[list(at)])
def observed_nll(mean,cov):
    value=0.
    for at,values in patterns.items():
        sub=cov[np.ix_(at,at)]; residual=np.array(values)-mean[list(at)]
        value+=len(values)*np.linalg.slogdet(sub)[1]/2+np.einsum('ij,ij->',residual@np.linalg.inv(sub),residual)/2
    return value
triangle=np.tril_indices(6)
def saturated_model(params):
    c=np.zeros((6,6)); c[triangle]=params[6:]; np.fill_diagonal(c,np.exp(np.diag(c)))
    return params[:6],c@c.T
initial_mean=np.nanmean(masked,axis=0); filled=np.where(np.isnan(masked),initial_mean,masked)
c=np.linalg.cholesky(np.cov(filled,rowvar=False,bias=True)); np.fill_diagonal(c,np.log(np.diag(c)))
fit=minimize(lambda v:observed_nll(*saturated_model(v)),np.r_[initial_mean,c[triangle]],method='BFGS',options=dict(gtol=1e-6,maxiter=3000))
assert np.max(abs(fit.jac))<1e-4
expected.append((['χ²'],2*(observed_nll(means,sigma)-fit.fun)))
add('cfa',[data,[1,1,1,2,2,2],[],'fiml'],expected,tolerance=2e-4)

# Independent joint raw-unit covariance ML with shared metric loadings.
second=x*1.1+1.; second[:,1]*=1.3; second[:,4]*=.9
combined=np.vstack([x,second]); samples=[np.cov(g,rowvar=False,bias=True) for g in (x,second)]
def unpack(values,group):
    load=np.zeros((6,2)); load[0,0]=load[3,1]=1
    load[[1,2],0]=values[:2]; load[[4,5],1]=values[2:4]
    at=4+group*9; errors=np.exp(values[at:at+6]); c=np.array([[np.exp(values[at+6]),0],[values[at+7],np.exp(values[at+8])]])
    return load@(c@c.T)@load.T+np.diag(errors)
def objective(values):
    return sum(len(x)*(np.linalg.slogdet(unpack(values,g))[1]+np.trace(np.linalg.solve(unpack(values,g),sample))-np.linalg.slogdet(sample)[1]-6)/2 for g,sample in enumerate(samples))
initial=[.8,1.2,.9,1.1]+([np.log(.3)]*6+[0,.4,0])*2
fit=minimize(objective,initial,method='BFGS',options=dict(gtol=1e-6,maxiter=3000))
assert np.max(abs(fit.jac))<1e-4
expected=[(['χ²'],2*fit.fun),(['df'],20)]
expected.append((['RMSEA'],np.sqrt(max(2*fit.fun-20,0)*2/(20*(96-2)))))
for g in range(2):
    sigma=unpack(fit.x,g)
    expected += [(['Implied covariance group '+str(g+1),i,j],sigma[i,j]) for i in range(6) for j in range(6)]
add('cfa',[combined.tolist(),[1,1,1,2,2,2],[],'complete',[1]*48+[2]*48,'metric'],expected,tolerance=2e-4)
fixture={'sources':['NumPy eigenvalues and seeded Horn simulation','factor_analyzer Rotator','statsmodels MANOVA','semopy covariance and mean ML','SciPy joint multi-group covariance ML'], 'cases':cases}
(ROOT/'tests/fixtures/statistics_extension_reference.json').write_text(json.dumps(fixture,indent=2,allow_nan=False)+'\n',encoding='utf8')
print('Wrote',len(cases),'independent reference cases')
