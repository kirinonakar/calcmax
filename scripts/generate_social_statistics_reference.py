"""Development-only SciPy/statsmodels/sklearn/semopy numeric references.

python scripts/generate_social_statistics_reference.py build/social-reference-deps
These packages are not shipped in either application runtime.
"""
import sys
from pathlib import Path
if len(sys.argv)>1: sys.path.insert(0,sys.argv[1])
import json
import numpy as np
from scipy import stats
from scipy.optimize import minimize
from scipy.cluster.hierarchy import linkage, fcluster
import statsmodels.api as sm
from statsmodels.multivariate.manova import MANOVA
from statsmodels.stats.inter_rater import cohens_kappa
from statsmodels.stats.multitest import multipletests
from sklearn.discriminant_analysis import LinearDiscriminantAnalysis, QuadraticDiscriminantAnalysis
from factor_analyzer.rotator import Rotator
from semopy import Model, calc_stats
import pandas as pd
import numdifftools as nd
from statistics_social_schema import survey,social

ROOT=Path(__file__).resolve().parents[1]; cases=[]
def add(name,args,expected,tolerance=2e-6):
    cases.append(dict(function=name,arguments=args,expected=expected,tolerance=tolerance))

counts=np.array([[25,4,2],[3,20,5],[1,6,24]])
chi,p,df,_=stats.chi2_contingency(counts,correction=False)
add('cramerv',[counts.tolist()],[( ["Cramér’s V"],np.sqrt(chi/(counts.sum()*2))),(['p'],p)])
two=np.array([[20,5],[7,18]])
chi,p,_,_=stats.chi2_contingency(two,correction=False)
add('phi',[two.tolist()],[(['phi'],np.sqrt(chi/two.sum())),(['p'],p)])
for mode in ('unweighted','linear','quadratic'):
    fit=cohens_kappa(counts,weights=None if mode=='unweighted' else np.arange(3),wt=None if mode=='unweighted' else 'linear' if mode=='linear' else 'quadratic')
    add('cohenkappa',[counts.tolist(),mode],[(['Cohen κ'],fit.kappa),(['SE'],np.sqrt(fit.var_kappa))])

x=np.array(survey); cov=np.cov(x,rowvar=False); k=x.shape[1]; total=cov.sum()
alpha=k/(k-1)*(1-np.trace(cov)/total)
expected=[(['Cronbach α'],alpha)]
for j in range(k):
    rest=[i for i in range(k) if i!=j]; sub=cov[np.ix_(rest,rest)]; cross=cov[j,rest].sum()
    expected.extend([(['Item diagnostics',j,'Corrected item-total correlation'],cross/np.sqrt(cov[j,j]*sub.sum())),(['Item diagnostics',j,'Alpha if deleted'],(k-1)/(k-2)*(1-np.trace(sub)/sub.sum()))])
add('cronbach',[survey,'raw'],expected)
# Independent NumPy principal-axis iteration and package varimax rotation.
corr=np.corrcoef(x,rowvar=False); inv=np.linalg.inv(corr); h=1-1/np.diag(inv)
for _ in range(2000):
    reduced=corr.copy(); np.fill_diagonal(reduced,h); values,vectors=np.linalg.eigh(reduced)
    loadings=vectors[:,-2:][:,::-1]*np.sqrt(values[-2:][::-1]); updated=(loadings**2).sum(axis=1)
    if np.max(abs(updated-h))<1e-10: break
    h=updated
loadings=Rotator(method='varimax',normalize=True,tol=1e-10,max_iter=1000).fit_transform(loadings)
for j in range(2):
    if loadings[np.argmax(abs(loadings[:,j])),j]<0: loadings[:,j]*=-1
expected=[(['Item diagnostics',i,'Communality'],v) for i,v in enumerate(updated)]
expected += [(['loadings',i,j],loadings[i,j]) for i in range(k) for j in range(2)]
add('efa',[survey,2,'varimax'],expected,tolerance=3e-5)

for function,paths in (('cfa',''),('sem','\nf2 ~ f1')):
    model=Model('f1 =~ v1+v2+v3\nf2 =~ v4+v5+v6'+paths)
    model.fit(pd.DataFrame(x,columns=['v'+str(i) for i in range(1,7)]),solver='SLSQP',options=dict(ftol=1e-12,maxiter=2000))
    fit=calc_stats(model).iloc[0]; sigma=model.calc_sigma()[0]
    expected=[(['χ²'],fit['chi2']),(['df'],fit['DoF']),(['CFI'],fit['CFI']),(['RMSEA'],fit['RMSEA'])]
    expected += [(['Implied covariance',i,j],sigma[i,j]) for i in range(6) for j in range(6)]
    inspected=model.inspect(information='observed')
    for i in range(6):
        row=inspected[(inspected.lval=='v'+str(i+1))&(inspected.op=='~')].iloc[0]
        expected.append((['Loadings',i,'estimate'],row['Estimate']))
        if i not in (0,3): expected.append((['Loadings',i,'SE'],row['Std. Err']))
    if function=='sem':
        path=inspected[(inspected.lval=='f2')&(inspected.op=='~')&(inspected.rval=='f1')].iloc[0]
        expected.extend([(['Structural paths',0,'estimate'],path['Estimate']),(['Structural paths',0,'SE'],path['Std. Err'])])
    args=[survey,[1,1,1,2,2,2]]+([[[1,2]]] if function=='sem' else [])
    add(function,args,expected,tolerance=3e-5)

rows=[[i//8+1,r[0],r[3]] for i,r in enumerate(survey[:24])]; frame=pd.DataFrame(rows,columns=['g','a','b'])
test=MANOVA.from_formula('a+b ~ C(g)',frame).mv_test().results['C(g)']['stat']
expected=[]
for i,label in enumerate(["Pillai's trace","Wilks' lambda"]):
    for key,column in [('Statistic','Value'),('F','F Value'),('df1','Num DF'),('df2','Den DF'),('p','Pr > F')]: expected.append((['Multivariate tests',i,key],float(test.loc[label,column])))
add('manova',[rows],expected)

groups=[[1,1,2,4],[2,3,3,5,7],[4,5,7,8]]; ranks=stats.rankdata(sum(groups,[])); n=len(ranks); ties=np.unique(sum(groups,[]),return_counts=True)[1]
pooled=n*(n+1)/12-(ties**3-ties).sum()/(12*(n-1)); starts=np.cumsum([0]+list(map(len,groups))); means=[ranks[starts[i]:starts[i+1]].mean() for i in range(3)]
z=[(means[i]-means[j])/np.sqrt(pooled*(1/len(groups[i])+1/len(groups[j]))) for i in range(3) for j in range(i+1,3)]
pv=2*stats.norm.sf(np.abs(z)); adj=multipletests(pv,method='holm')[1]
add('dunn',[groups,'holm'],[(['Comparisons',i,key],v) for i in range(3) for key,v in [('z',z[i]),('Raw p value',pv[i]),('Adjusted p value',adj[i])]])

data=np.array(social); x,m,y=data.T; a=sm.OLS(m,sm.add_constant(x)).fit(); b=sm.OLS(y,np.column_stack([np.ones(len(x)),x,m])).fit(); c=sm.OLS(y,sm.add_constant(x)).fit()
add('mediation',[social,100,7],[(['a (X → M)'],a.params[1]),(['b (M → Y | X)'],b.params[2]),(['Indirect effect a×b'],a.params[1]*b.params[2]),(['Direct effect c′'],b.params[1]),(['Total effect c'],c.params[1])])
xc=x-x.mean(); mc=m-m.mean(); fit=sm.OLS(y,np.column_stack([np.ones(len(x)),xc,mc,xc*mc])).fit()
add('moderation',[social],[(['coefficients',i,key],v) for i in range(4) for key,v in [('estimate',fit.params[i]),('SE',fit.bse[i]),('p',fit.pvalues[i])]])

rng=np.random.default_rng(543)
features=np.concatenate([rng.normal([0,0],.7,(16,2)),rng.normal([2,2],1.,(19,2))]); classes=[1]*16+[2]*19; rows=np.column_stack([features,classes]).tolist()
for method,cls in [('lda',LinearDiscriminantAnalysis),('qda',QuadraticDiscriminantAnalysis)]:
    fit=cls().fit(features,classes); pred=fit.predict(features)
    # sklearn 1.9 uses ML (biased) covariances. Our explicitly documented
    # pooled/per-class unbiased covariances use the normal density directly.
    groups=[features[np.array(classes)==c] for c in (1,2)]
    pooled=sum(np.cov(g,rowvar=False)*(len(g)-1) for g in groups)/(len(features)-2)
    logs=[stats.multivariate_normal.logpdf([.2,.4],mean=g.mean(0),cov=pooled if method=='lda' else np.cov(g,rowvar=False))+np.log(len(g)/len(features)) for g in groups]
    posterior=np.exp(logs[0]-np.logaddexp(*logs))
    add('discriminantanalysis',[rows,method,'empirical',[[.2,.4],[1.8,2.1]]],[(['Training accuracy'],float((pred==classes).mean())),(['Predictions',0,'P(class 1.0)'],posterior)])

features=np.arange(80)/10-4; y=1+1.5*features+rng.normal(size=80); rows=np.column_stack([features,y]).tolist()
for q in (.25,.5,.75):
    fit=sm.QuantReg(y,sm.add_constant(features)).fit(q=q,kernel='gau',bandwidth='hsheather',max_iter=10000,p_tol=1e-10)
    add('quantreg',[rows,q],[(['coefficients',i,key],v) for i in range(2) for key,v in [('estimate',fit.params[i]),('SE',fit.bse[i])]],tolerance=3e-4)

latent=.5+1.1*features+rng.normal(0,.8,len(features)); observed=np.clip(latent,0,4); design=sm.add_constant(features)
def censored(parameters):
    mu=design@parameters[:-1]; sd=np.exp(parameters[-1]); left=observed==0; right=observed==4; free=~(left|right)
    return -stats.norm.logcdf((0-mu[left])/sd).sum()-stats.norm.logsf((4-mu[right])/sd).sum()-stats.norm.logpdf(observed[free],mu[free],sd).sum()
fit=minimize(censored,[.5,1.,np.log(.8)],method='BFGS',options=dict(gtol=1e-6)); covariance=np.linalg.inv(nd.Hessian(censored)(fit.x)); sd=np.exp(fit.x[-1]); se=np.sqrt(np.diag(covariance))*[1,1,sd]
add('tobit',[np.column_stack([features,observed]).tolist(),0,4],[(['coefficients',i,'estimate'],v) for i,v in enumerate(list(fit.x[:-1])+[sd])]+[(['coefficients',i,'SE'],v) for i,v in enumerate(se)]+[(['log likelihood'],-fit.fun)])

for family in ('poisson','nbinom'):
    x=np.tile(np.linspace(-1.5,1.5,8),20); mu=np.exp(.6+.5*x); pi=stats.logistic.cdf(-.5+.2*x)
    counts=rng.poisson(mu if family=='poisson' else rng.gamma(1/.7,mu*.7)); counts[rng.uniform(size=len(x))<pi]=0
    exog=sm.add_constant(x)
    cls=sm.ZeroInflatedPoisson if family=='poisson' else sm.ZeroInflatedNegativeBinomialP
    fit=cls(counts,exog,exog_infl=exog).fit(method='bfgs',maxiter=3000,disp=False,gtol=1e-8)
    # Differentiate the complete mixture log likelihood independently;
    # library analytic Hessians need not agree with joint observed information.
    joint_se=np.sqrt(np.diag(np.linalg.inv(nd.Hessian(lambda b:-fit.model.loglike(b))(fit.params))))
    order=[2,3,0,1]+([4] if family=='nbinom' else [])
    add('zeroinflated',[np.column_stack([x,counts]).tolist(),family,'same'],[(['coefficients',i,key],v) for i,at in enumerate(order) for key,v in [('estimate',fit.params[at]),('SE',joint_se[at])]]+[(['log likelihood'],fit.llf)],tolerance=3e-5)

features=[[0,1],[1,0],[1.1,2],[7,8],[9,7],[8,9.5]]
for method in ('single','complete','average','ward'):
    tree=linkage(features,method=method)
    add('hcluster',[features,2,method,0],[(['Merge tree',i,'Distance'],v) for i,v in enumerate(tree[:,2])])
output=dict(sources=['SciPy','statsmodels','scikit-learn','NumPy principal-axis iteration + factor_analyzer varimax','semopy covariance ML','SciPy censored-normal likelihood + numdifftools Hessian'],cases=cases)
(ROOT/'tests/fixtures/social_statistics_reference.json').write_text(json.dumps(output,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
# The Kruskal companion now uses Dunn, so refresh its existing family fixture.
path=ROOT/'tests/fixtures/statistics_group_reference.json'; group_ref=json.loads(path.read_text(encoding='utf8'))
groups=group_ref['samples']; ranks=stats.rankdata(sum(groups,[])); n=len(ranks); ties=np.unique(sum(groups,[]),return_counts=True)[1]
variance=n*(n+1)/12-(ties**3-ties).sum()/(12*(n-1)); starts=np.cumsum([0]+list(map(len,groups)))
means=[ranks[starts[i]:starts[i+1]].mean() for i in range(len(groups))]
z=[(means[i]-means[j])/np.sqrt(variance*(1/len(groups[i])+1/len(groups[j]))) for i in range(len(groups)) for j in range(i+1,len(groups))]
raw=2*stats.norm.sf(np.abs(z)); group_ref['posthoc']['kruskal']={'raw':raw.tolist(),'holm':multipletests(raw,method='holm')[1].tolist()}
if '; pooled-midrank Dunn' not in group_ref['source']: group_ref['source']+='; pooled-midrank Dunn with tie correction and SciPy normal tails'
path.write_text(json.dumps(group_ref,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print('Wrote',len(cases),'independent reference cases')
