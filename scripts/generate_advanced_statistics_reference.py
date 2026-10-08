"""Optional development-only reference generation (requires scipy/statsmodels).

Run with a directory of reference dependencies as the first argument, or use
an environment where they are installed. Neither is an application dependency.
"""
import ast
import json
from pathlib import Path
import sys
if len(sys.argv)>1: sys.path.insert(0,sys.argv[1])
import numpy as np
import scipy
from scipy import stats
from scipy.integrate import quad
from scipy.optimize import minimize, minimize_scalar
from scipy.special import expit, gammaln
import statsmodels
import statsmodels.api as sm
from statsmodels.stats.multitest import multipletests
from statsmodels.stats.contingency_tables import mcnemar
from statsmodels.miscmodels.ordinal_model import OrderedModel
from statsmodels.stats.anova import AnovaRM
import pandas as pd

ROOT=Path(__file__).resolve().parents[1]
schema=json.loads((ROOT/'app/src/main/assets/advanced_statistics.json').read_text(encoding='utf-8'))
examples={item['id']:item['example'] for item in schema}
fixtures=[]

def data(id_): return ast.literal_eval(ast.parse(examples[id_],mode='eval').body.args[0])
def add(function,arguments,expected,tolerance=1e-6,name=None):
    fixtures.append({'name':name or function,'function':function,'arguments':arguments,'expected':expected,'tolerance':tolerance,'source':f'SciPy {scipy.__version__}; statsmodels {statsmodels.__version__}'})

g=[[1,2,4,5],[2,3,5,8]]
for name in ('levene','bartlett'):
    r=getattr(stats,name)(*[np.asarray(v,dtype=float) for v in g]); add(name,g,[[['F' if name=='levene' else 'chi2'],float(r.statistic)],[['p'],float(r.pvalue)]],1e-10)
for method,ref in [('bonferroni','bonferroni'),('holm','holm'),('fdr','fdr_bh'),('by','fdr_by')]:
    vals=[.04,.01,.03,.2]; adj=multipletests(vals,method=ref)[1]
    add('padjust',[vals,method],[[['adjusted p',i],float(v)] for i,v in enumerate(adj)],1e-12,name='padjust '+method)
for method in ('exact','corrected','asymptotic'):
    r=mcnemar([[20,8],[2,15]],exact=method=='exact',correction=method=='corrected'); add('mcnemar',[[[20,8],[2,15]],method],[[['p'],float(r.pvalue)]],1e-12,name='McNemar '+method)
for x,y in [([1,2,3],[4,5,6]),([1,3,6,8],[2,4,5,7,9])]:
    r=stats.ks_2samp(x,y,method='exact'); add('kstest',[x,y],[[['D'],float(r.statistic)],[['p'],float(r.pvalue)]],1e-12,name='KS exact '+str(len(y)))
for name in ('poissonreg','nbreg','multinomial','ordinal'):
    rows=data(name); arr=np.asarray(rows,float); x=sm.add_constant(arr[:,:-1]); y=arr[:,-1]
    if name=='poissonreg': fit=sm.Poisson(y,x).fit(disp=False)
    elif name=='nbreg': fit=sm.NegativeBinomial(y,x,loglike_method='nb2').fit(disp=False,maxiter=1000)
    elif name=='multinomial': fit=sm.MNLogit(y,x).fit(disp=False,maxiter=1000)
    else: fit=OrderedModel(y,arr[:,:-1],distr='logit').fit(method='bfgs',disp=False,maxiter=1000,gtol=1e-9)
    b=fit.params.T.reshape(-1) if name=='multinomial' else fit.params
    se=fit.bse.T.reshape(-1) if name=='multinomial' else fit.bse
    count=(x.shape[1]*(len(np.unique(y))-1) if name=='multinomial' else arr.shape[1]-1 if name=='ordinal' else x.shape[1])
    expected=[]
    for i in range(count):
        expected += [[['coefficients',i,'estimate'],float(b[i])],[['coefficients',i,'SE'],float(se[i])]]
    expected += [[['log likelihood'],float(fit.llf)]]
    if name=='nbreg': expected += [[['dispersion alpha (NB2)'],float(fit.params[-1])]]
    if name=='ordinal':
        cuts=fit.model.transform_threshold_params(fit.params)[1:-1]
        expected += [[['cutpoints',i],float(v)] for i,v in enumerate(cuts)]
    add(name,[rows],expected,2e-4)
rows=data('cox'); arr=np.asarray(rows,float); fit=sm.PHReg(arr[:,0],arr[:,2:],status=arr[:,1],ties='efron').fit()
add('cox',[rows],[[['coefficients',0,'estimate'],float(fit.params[0])],[['coefficients',0,'SE'],float(fit.bse[0])],[['partial log likelihood'],float(fit.llf)]],1e-5)
rows=data('mixedmodel'); arr=np.asarray(rows,float); x=sm.add_constant(arr[:,1:-1]); y=arr[:,-1]
for method in ('ml','reml'):
    fit=sm.MixedLM(y,x,groups=arr[:,0]).fit(reml=method=='reml',method='powell',disp=False)
    arguments=[rows,0,'ml'] if method=='ml' else [rows]
    add('mixedmodel',arguments,[[['coefficients',i,'estimate'],float(v)] for i,v in enumerate(fit.fe_params)]+[[['residual variance'],float(fit.scale)],[['random intercept variance'],float(fit.cov_re[0,0])]],2e-4,name='Mixed random intercept '+method)
for family in ('gaussian','binomial','poisson'):
    if family=='binomial':
        rows=[[g,x,(g+x)%2] for g in range(1,9) for x in range(3)]
    elif family=='poisson':
        rows=[[g,x,(g+x)%4+1] for g in range(1,9) for x in range(3)]
    else: rows=data('gee')
    arr=np.asarray(rows,float); x=sm.add_constant(arr[:,1:-1]); y=arr[:,-1]
    family_class={'gaussian':sm.families.Gaussian,'binomial':sm.families.Binomial,'poisson':sm.families.Poisson}[family]
    fit=sm.GEE(y,x,arr[:,0],family=family_class(),cov_struct=sm.cov_struct.Independence()).fit()
    expected=[]
    for i in range(len(fit.params)): expected += [[['coefficients',i,'estimate'],float(fit.params[i])],[['coefficients',i,'SE'],float(fit.bse[i])]]
    add('gee',[rows,family],expected,2e-5,name='GEE '+family)
rows=data('repeatedanova'); frame=pd.DataFrame([(i,j,v) for i,r in enumerate(rows) for j,v in enumerate(r)],columns=['subject','condition','value'])
result=AnovaRM(frame,'value','subject',within=['condition']).fit().anova_table.iloc[0]
add('repeatedanova',[rows],[[['F'],float(result['F Value'])],[['p'],float(result['Pr > F'])]],1e-10)
# Covariance structures not covered by the original independence fixtures.
rng=np.random.default_rng(1042)
rows=[]
for group in range(18):
    b=rng.multivariate_normal([0,0],[[.7,.12],[.12,.2]])
    for point in range(6):
        x0=(point-2.5)/2
        rows.append([group,x0,float(2+.4*x0+b[0]+b[1]*x0+rng.normal(0,.35))])
arr=np.asarray(rows);x=sm.add_constant(arr[:,1:-1]);y=arr[:,-1]
for method in ('ml','reml'):
    fit=sm.MixedLM(y,x,groups=arr[:,0],exog_re=x).fit(reml=method=='reml',method='bfgs',disp=False,gtol=1e-9,maxiter=2000)
    expected=[[['coefficients',i,'estimate'],float(v)] for i,v in enumerate(fit.fe_params)]
    expected += [[['residual variance'],float(fit.scale)],[['random intercept variance'],float(fit.cov_re[0,0])],[['random slope variance'],float(fit.cov_re[1,1])],[['intercept-slope correlation'],float(fit.cov_re[0,1]/np.sqrt(fit.cov_re[0,0]*fit.cov_re[1,1]))]]
    for i,group in enumerate(sorted(fit.random_effects)):
        expected += [[['subject random effects (BLUP)',i,'Intercept'],float(fit.random_effects[group].iloc[0])],[['subject random effects (BLUP)',i,'x1'],float(fit.random_effects[group].iloc[1])]]
    add('mixedmodel',[rows,1,method],expected,3e-4,name='Mixed random slope '+method)
for family in ('gaussian','binomial','poisson'):
    rng=np.random.default_rng(241)
    rows=[]
    for group in range(16):
        intercept=rng.normal(0,.65)
        for point in range(3+group%3):
            x0=(point-2)/2; eta=.2+.35*x0+intercept
            value=rng.normal(eta,.5) if family=='gaussian' else rng.binomial(1,expit(eta)) if family=='binomial' else rng.poisson(np.exp(eta))
            rows.append([group,x0,float(value)])
    arr=np.asarray(rows);x=sm.add_constant(arr[:,1:-1]);y=arr[:,-1]
    for structure in ('exchangeable','ar1'):
        covariance=sm.cov_struct.Exchangeable() if structure=='exchangeable' else sm.cov_struct.Autoregressive(grid=True)
        fit=sm.GEE(y,x,arr[:,0],family={'gaussian':sm.families.Gaussian,'binomial':sm.families.Binomial,'poisson':sm.families.Poisson}[family](),cov_struct=covariance).fit(maxiter=500,ctol=1e-10)
        expected=[[['alpha'],float(fit.cov_struct.dep_params)]]
        for i in range(len(fit.params)): expected += [[['coefficients',i,'estimate'],float(fit.params[i])],[['coefficients',i,'SE'],float(fit.bse[i])]]
        add('gee',[rows,family,structure],expected,3e-5,name='GEE '+family+' '+structure)

# Count offsets are checked against externally fitted likelihoods.
for name in ('poissonreg','nbreg'):
    rows=data(name); arr=np.asarray(rows); x=sm.add_constant(arr[:,:-1]);y=arr[:,-1]
    exposure=[1+.3*(i%4) for i in range(len(rows))];offset=np.log(exposure)
    fit=(sm.Poisson(y,x,offset=offset) if name=='poissonreg' else sm.NegativeBinomial(y,x,offset=offset)).fit(disp=False,maxiter=1000)
    expected=[[['log likelihood'],float(fit.llf)]]
    for i in range(x.shape[1]): expected += [[['coefficients',i,'estimate'],float(fit.params[i])],[['coefficients',i,'SE'],float(fit.bse[i])]]
    if name=='nbreg': expected += [[['dispersion alpha (NB2)'],float(fit.params[-1])]]
    add(name,[rows,exposure,'exposure'],expected,3e-4,name=name+' exposure')

# Independent GLMM oracle: QUADPACK integrates the standard-normal random
# effect directly over [-12,12], with SciPy optimization. It neither imports
# application code nor uses Gauss-Hermite nodes/conditional-mode adaptation.
for family in ('binomial','poisson','nbinom'):
    rng=np.random.default_rng(178 if family=='nbinom' else 17)
    rows=[];exposure=[]
    for group in range(12):
        intercept=rng.normal(0,.9)
        for point in range(5):
            x0=(point-2)/2; off=0 if family=='binomial' else np.log(1+.25*(point%3));eta=.25+.5*x0+intercept+off
            value=rng.binomial(1,expit(eta)) if family=='binomial' else rng.poisson(np.exp(eta)) if family=='poisson' else rng.negative_binomial(2,2/(2+np.exp(eta)))
            rows.append([group,x0,float(value)]);exposure.append(float(np.exp(off)))
    arr=np.asarray(rows);x=sm.add_constant(arr[:,1:-1]);y=arr[:,-1];offset=np.log(exposure)
    pieces=[np.flatnonzero(arr[:,0]==group) for group in range(12)];p=x.shape[1]
    def objective(parameters):
        beta=parameters[:p];sd=parameters[p];alpha=np.exp(parameters[-1]) if family=='nbinom' else 0
        total=0
        for cluster in pieces:
            def logdensity(u):
                eta=x[cluster]@beta+offset[cluster]+sd*u;values=y[cluster]
                if family=='binomial':ll=values*eta-np.logaddexp(0,eta)
                elif family=='poisson':ll=values*eta-np.exp(eta)-gammaln(values+1)
                else:
                    r=1/alpha;ll=gammaln(values+r)-gammaln(r)-gammaln(values+1)+values*eta-(values+r)*np.logaddexp(np.log(r),eta)+r*np.log(r)
                return float(ll.sum()-.5*u*u-.5*np.log(2*np.pi))
            # A constant shift prevents density underflow without adapting nodes.
            shift=-minimize_scalar(lambda u:-logdensity(u),bounds=(-12,12),method='bounded').fun
            mass,error=quad(lambda u:np.exp(logdensity(u)-shift),-12,12,epsabs=1e-10,epsrel=1e-10,limit=150)
            if mass<=0:return np.inf
            total-=shift+np.log(mass)
        return total
    starts=[np.array([0,.3,sd]+([-1.] if family=='nbinom' else [])) for sd in (.4,1.2)]
    fits=[minimize(objective,start,method='L-BFGS-B',bounds=[(-10,10)]*p+[(0,8)]+([(-8,4)] if family=='nbinom' else []),options={'ftol':1e-13,'gtol':1e-6,'maxiter':1000}) for start in starts]
    fit=min(fits,key=lambda f:f.fun)
    if not fit.success:raise RuntimeError(family+': '+fit.message)
    point=fit.x;h=5e-4;info=np.zeros((len(point),len(point)));base=objective(point)
    for i in range(len(point)):
        ei=np.eye(len(point))[i]*h
        info[i,i]=(objective(point+ei)-2*base+objective(point-ei))/h**2
        for j in range(i):
            ej=np.eye(len(point))[j]*h
            info[i,j]=info[j,i]=(objective(point+ei+ej)-objective(point+ei-ej)-objective(point-ei+ej)+objective(point-ei-ej))/(4*h*h)
    covariance=np.linalg.inv(info);expected=[]
    for i in range(p):expected += [[['coefficients',i,'estimate'],float(point[i])],[['coefficients',i,'SE'],float(np.sqrt(covariance[i,i]))]]
    expected += [[['random intercept variance'],float(point[p]**2)],[['log likelihood'],-float(fit.fun)]]
    if family=='nbinom':expected += [[['dispersion alpha (NB2)'],float(np.exp(point[-1]))]]
    arguments=[rows,family,25]+([exposure,'exposure'] if family!='binomial' else [])
    add('glmm',arguments,expected,7e-4,name='GLMM '+family+' QUADPACK')
    fixtures[-1]['source']=f'SciPy {scipy.__version__}: independent QUADPACK integration and L-BFGS-B ML'

path=ROOT/'tests/fixtures/advanced_statistics_reference.json';path.parent.mkdir(exist_ok=True)
path.write_text(json.dumps(fixtures,indent=2)+'\n',encoding='utf-8')
print(f'Wrote {len(fixtures)} independent references')
