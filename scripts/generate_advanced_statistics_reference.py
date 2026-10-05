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
rows=data('cox'); arr=np.asarray(rows,float); fit=sm.PHReg(arr[:,0],arr[:,2:],status=arr[:,1],ties='breslow').fit()
add('cox',[rows],[[['coefficients',0,'estimate'],float(fit.params[0])],[['coefficients',0,'SE'],float(fit.bse[0])],[['partial log likelihood'],float(fit.llf)]],1e-5)
rows=data('mixedmodel'); arr=np.asarray(rows,float); x=sm.add_constant(arr[:,1:-1]); y=arr[:,-1]
fit=sm.MixedLM(y,x,groups=arr[:,0]).fit(reml=False,method='powell',disp=False)
add('mixedmodel',[rows],[[['coefficients',i,'estimate'],float(v)] for i,v in enumerate(fit.fe_params)]+[[['residual variance'],float(fit.scale)],[['random intercept variance'],float(fit.cov_re[0,0])]],2e-4)
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
path=ROOT/'tests/fixtures/advanced_statistics_reference.json';path.parent.mkdir(exist_ok=True)
path.write_text(json.dumps(fixtures,indent=2)+'\n',encoding='utf-8')
print(f'Wrote {len(fixtures)} independent references')
