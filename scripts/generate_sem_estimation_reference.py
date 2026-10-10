"""Independent development-only SciPy quadrature/optimization and NumPy references.

python scripts/generate_sem_estimation_reference.py build/social-reference-deps
No application estimator is imported. WLSMV T3 follows lavaan's scaled.shifted
test: a = sqrt(df/tr((U Gamma)^2)); T3 = a * T + df - a * tr(U Gamma).
"""
import json
import sys
from pathlib import Path
sys.path.insert(0,sys.argv[1] if len(sys.argv)>1 else 'build/social-reference-deps')
import numpy as np
from scipy.integrate import quad
from scipy.optimize import minimize, minimize_scalar, least_squares
from scipy.optimize._numdiff import approx_derivative
from scipy.stats import norm, chi2

ROOT=Path(__file__).resolve().parents[1]
cases=[]


def add(name,args,expected,tolerance=3e-4):
    cases.append(dict(function=name,arguments=args,expected=expected,tolerance=tolerance))


def probability(left,right,rho):
    def cdf(x,y):
        if x==-np.inf or y==-np.inf: return 0.
        if x==np.inf: return norm.cdf(y)
        if y==np.inf: return norm.cdf(x)
        return quad(lambda v:norm.pdf(v)*norm.cdf((y-rho*v)/np.sqrt(1-rho*rho)),-np.inf,x,epsabs=2e-11)[0]
    corners=np.array([[cdf(x,y) for y in np.r_[-np.inf,right,np.inf]] for x in np.r_[-np.inf,left,np.inf]])
    return np.diff(np.diff(corners,axis=0),axis=1)


def ordinal_stats(rows):
    n,p=rows.shape; thresholds=[norm.ppf(np.cumsum(np.bincount(rows[:,i]))[:-1]/n) for i in range(p)]
    values=np.concatenate(thresholds).tolist(); influence=[]; indices=[]
    for i,cuts in enumerate(thresholds):
        indices.append(list(range(len(influence),len(influence)+len(cuts))))
        influence.extend([(norm.cdf(t)-(rows[:,i]<=j))/norm.pdf(t) for j,t in enumerate(cuts)])
    pairs=[]
    for i in range(p):
        for j in range(i):
            left,right=thresholds[i],thresholds[j]; counts=np.zeros((len(left)+1,len(right)+1))
            np.add.at(counts,(rows[:,i],rows[:,j]),1)
            fit=minimize_scalar(lambda r:-np.sum(counts*np.log(np.maximum(probability(left,right,r),1e-15))),bounds=(-.98,.98),method='bounded',options=dict(xatol=1e-10))
            rho=fit.x; probs=probability(left,right,rho); step=1e-5
            dr=(probability(left,right,rho+step)-probability(left,right,rho-step))/(2*step)
            derivative=[]
            for t in range(len(left)+len(right)):
                cuts=np.r_[left,right]; lo=cuts.copy(); hi=cuts.copy(); lo[t]-=step; hi[t]+=step
                derivative.append((probability(hi[:len(left)],hi[len(left):],rho)-probability(lo[:len(left)],lo[len(left):],rho))/(2*step))
            info=np.sum(dr*dr/probs); cross=np.array([np.sum(dr*d/probs) for d in derivative])
            scores=(dr/probs)[rows[:,i],rows[:,j]]
            influence.append((scores-cross@np.array(influence)[indices[i]+indices[j]])/info)
            values.append(rho); pairs.append((i,j))
    gamma=np.cov(np.array(influence),bias=True)
    return np.array(values),gamma,thresholds,pairs


def t3(raw,u,gamma,df):
    ug=u@gamma; scale=np.sqrt(np.trace(ug@ug)/df); shift=df-np.trace(ug)/scale
    return max(0,raw/scale+shift),scale,shift


def ordinal_reference(rows,assignment,paths=[],cross=[]):
    n,p=rows.shape; k=max(assignment); markers=[assignment.index(i+1) for i in range(k)]
    sample,gamma,cuts,pairs=ordinal_stats(rows); w=np.diag(1/np.diag(gamma))
    exogenous=set(range(k))-{b-1 for a,b in paths}
    specs=[('loading',i,f-1) for i,f in enumerate(assignment) if i not in markers]+[('loading',i-1,j-1) for i,j in cross]
    specs += [('diagonal',i,i) for i in range(k)]+[('covariance',i,j) for i in range(k) for j in range(i) if i in exogenous and j in exogenous]+[('path',b-1,a-1) for a,b in paths]
    specs += [('threshold',i,j) for i,ts in enumerate(cuts) for j in range(len(ts))]
    initial=[1. if kind=='loading' else sample[sum(map(len,cuts[:i]))+j]*np.sqrt(2) if kind=='threshold' else 0. for kind,i,j in specs]
    def model(x):
        load=np.zeros((p,k)); load[markers,range(k)]=1; chol=np.zeros((k,k)); path=np.zeros((k,k)); thresholds=[v.copy() for v in cuts]
        for value,(kind,i,j) in zip(x,specs):
            if kind=='loading': load[i,j]=value
            elif kind=='diagonal': chol[i,j]=np.exp(value)
            elif kind=='covariance': chol[i,j]=value
            elif kind=='path': path[i,j]=value
            else: thresholds[i][j]=value
        prop=np.linalg.inv(np.eye(k)-path); latent=prop@chol@chol.T@prop.T; sigma=load@latent@load.T+np.eye(p); sd=np.sqrt(np.diag(sigma))
        implied=np.r_[np.concatenate([v/sd[i] for i,v in enumerate(thresholds)]),[sigma[i,j]/sd[i]/sd[j] for i,j in pairs]]
        return implied,load,path,latent,sigma
    fit=least_squares(lambda x:np.sqrt(np.diag(w))*(model(x)[0]-sample),initial,xtol=1e-11,ftol=1e-11,gtol=1e-11,max_nfev=3000)
    assert fit.success
    jac=approx_derivative(lambda x:model(x)[0],fit.x,method='3-point'); inv=np.linalg.inv(jac.T@w@jac); cov=inv@jac.T@w@gamma@w@jac@inv/n
    df=len(sample)-len(specs); raw=n*np.dot(fit.fun,fit.fun); u=w-w@jac@inv@jac.T@w
    adjusted,scale,shift=t3(raw,u,gamma,df)
    expected=[(['χ²'],adjusted),(['df'],df),(['p'],chi2.sf(adjusted,df)),(['Unadjusted DWLS χ²'],raw),(['Scaling factor'],scale),(['Shift parameter'],shift)]
    _,load,path,latent,sigma=model(fit.x)
    expected += [(['Indicator R²',i,'R²'],1-1/sigma[i,i]) for i in range(p)]
    expected += [(['Implied covariance',i,j],sigma[i,j]) for i in range(p) for j in range(p)]
    for i in range(p):
        expected.append((['Loadings',i,'Standardized loading'],load[i,assignment[i]-1]*np.sqrt(latent[assignment[i]-1,assignment[i]-1]/sigma[i,i])))
    def standardized(x):
        _,loading,beta,total,implied=model(x)
        return np.r_[[loading[i,assignment[i]-1]*np.sqrt(total[assignment[i]-1,assignment[i]-1]/implied[i,i]) for i in range(p)],
                     [beta[target-1,source-1]*np.sqrt(total[source-1,source-1]/total[target-1,target-1]) for source,target in paths]]
    if not cross:
        standardized_jac=approx_derivative(standardized,fit.x,method='3-point')
        standardized_se=np.sqrt(np.diag(standardized_jac@cov@standardized_jac.T)); standardized_est=standardized(fit.x)
        for i,(estimate,se) in enumerate(zip(standardized_est,standardized_se)):
            at=['Loadings',i] if i<p else ['Structural paths',i-p]
            expected += [(at+['Standardized SE'],se),(at+['Standardized CI95',0],estimate-norm.ppf(.975)*se),(at+['Standardized CI95',1],estimate+norm.ppf(.975)*se)]
    disturbance=(np.eye(k)-path)@latent@(np.eye(k)-path).T
    for target in sorted({target-1 for source,target in paths}):
        expected.append((['Latent R²',target,'R²'],1-disturbance[target,target]/latent[target,target]))
    for pos,(kind,i,j) in enumerate(specs):
        if kind=='loading' and not cross:
            expected += [(['Loadings',i,'estimate'],fit.x[pos]),(['Loadings',i,'SE'],np.sqrt(cov[pos,pos]))]
        if kind=='path': expected += [(['Structural paths',0,'estimate'],fit.x[pos]),(['Structural paths',0,'SE'],np.sqrt(cov[pos,pos]))]
    add('sem' if paths else 'cfa',[rows.tolist(),assignment]+([paths] if paths else [])+[cross,'complete',[],'configural','wlsmv'],expected)


def continuous_reference(rows,ids,invariance):
    p=rows.shape[1]; labels=np.unique(ids); means=[rows[ids==g].mean(axis=0) for g in labels]; samples=[np.cov(rows[ids==g],rowvar=False,bias=True) for g in labels]; ns=[sum(ids==g) for g in labels]
    strict=invariance=='strict'
    # One marker-identified factor: loadings/intercepts shared; group variances
    # and non-reference means free. Strict also shares raw response errors.
    initial=np.r_[np.ones(p-1),np.mean(means,axis=0),np.zeros(p*(1 if strict else len(labels))),np.zeros(len(labels)),np.zeros(len(labels)-1)]
    def unpack(x,g):
        load=np.r_[1.,x[:p-1]]; intercept=x[p-1:2*p-1]; at=2*p-1
        errors=np.exp(x[at+(0 if strict else g*p):at+(0 if strict else g*p)+p]); at+=p*(1 if strict else len(labels))
        variance=np.exp(x[at+g]); latentmean=0. if not g else x[at+len(labels)+g-1]
        return intercept+load*latentmean,np.outer(load,load)*variance+np.diag(errors),intercept,latentmean,errors
    sat=sum(n*(np.linalg.slogdet(s)[1]+p)/2 for n,s in zip(ns,samples))
    def objective(x):
        out=0.
        for g,(mean,s,n) in enumerate(zip(means,samples,ns)):
            mu,cov,*_=unpack(x,g); delta=mean-mu
            out+=n*(np.linalg.slogdet(cov)[1]+np.trace(np.linalg.solve(cov,s))+delta@np.linalg.solve(cov,delta))/2
        return out-sat
    fit=minimize(objective,initial,method='BFGS',options=dict(gtol=1e-5,maxiter=3000))
    assert np.max(abs(fit.jac))<1e-3
    def gradient(x):
        score=np.zeros(len(x))
        for g,(mean,s,n) in enumerate(zip(means,samples,ns)):
            mu,cov,*_=unpack(x,g); delta=mu-mean; inv=np.linalg.inv(cov)
            jac=approx_derivative(lambda v:np.r_[unpack(v,g)[0],unpack(v,g)[1].ravel()],x,method='3-point')
            gc=n*(inv-inv@(s+np.outer(delta,delta))@inv)/2
            score+=jac.T@np.r_[n*inv@delta,gc.ravel()]
        return score
    parameter_cov=np.linalg.inv(approx_derivative(gradient,fit.x,method='3-point'))
    def standardized(x):
        load=np.r_[1.,x[:p-1]]
        return np.concatenate([load*np.sqrt((unpack(x,g)[1][0,0]-unpack(x,g)[4][0])/np.diag(unpack(x,g)[1])) for g in range(len(labels))])
    stdjac=approx_derivative(standardized,fit.x,method='3-point'); stdse=np.sqrt(np.diag(stdjac@parameter_cov@stdjac.T)); stdest=standardized(fit.x)
    df=len(labels)*(p*(p+1)//2+p)-len(initial); expected=[(['χ²'],2*fit.fun),(['df'],df)]
    for i,(estimate,se) in enumerate(zip(stdest,stdse)):
        expected += [(['Loadings',i,'Standardized SE'],se),(['Loadings',i,'Standardized CI95',0],estimate-norm.ppf(.975)*se),(['Loadings',i,'Standardized CI95',1],estimate+norm.ppf(.975)*se)]
    for g in range(len(labels)):
        mu,cov,intercept,latentmean,errors=unpack(fit.x,g)
        expected += [(['Implied covariance group '+str(g+1),i,j],cov[i,j]) for i in range(p) for j in range(p)]
        expected += [(['Indicator means',g*p+i,'Mean'],mu[i]) for i in range(p)]
        expected += [(['Indicator intercepts',g*p+i,'Intercept'],intercept[i]) for i in range(p)]
        expected += [(['Residual variances',g*p+i,'Variance'],errors[i]) for i in range(p)]
        expected += [(['Indicator R²',g*p+i,'R²'],1-errors[i]/cov[i,i]) for i in range(p)]
        expected += [(['Latent means',g,'estimate'],latentmean)]
    add('cfa',[rows.tolist(),[1]*p,[],'complete',ids.tolist(),invariance],expected)


def ordinal_group_reference(rows,ids,invariance):
    groups=[rows[ids==g] for g in np.unique(ids)]; n,p=rows.shape; strict=invariance=='strict'
    stats=[ordinal_stats(g) for g in groups]; size=sum(len(s[0]) for s in stats)
    sample=np.concatenate([s[0] for s in stats]); w=np.zeros((size,size)); gamma=np.zeros((size,size)); offset=0
    for g,(values,cov,_,_) in zip(groups,stats):
        m=len(values); w[offset:offset+m,offset:offset+m]=np.diag(len(g)/n/np.diag(cov))
        gamma[offset:offset+m,offset:offset+m]=cov*n/len(g); offset+=m
    cuts=stats[0][2]; pairs=stats[0][3]; count=sum(map(len,cuts))
    initial=np.r_[np.ones(p-1),np.zeros(2),0.,np.concatenate(cuts)*np.sqrt(2),[] if strict else np.zeros(p)]
    def model(x):
        load=np.r_[1.,x[:p-1]]; variances=np.exp(x[p-1:p+1]); mean=x[p+1]; thresholds=x[p+2:p+2+count]
        errors=[np.ones(p),np.ones(p) if strict else np.exp(x[-p:])]; out=[]; matrices=[]
        for g in range(2):
            sigma=np.outer(load,load)*variances[g]+np.diag(errors[g]); sd=np.sqrt(np.diag(sigma)); mu=load*(mean if g else 0.)
            implied=np.r_[np.concatenate([(thresholds[sum(map(len,cuts[:i])):sum(map(len,cuts[:i+1]))]-mu[i])/sd[i] for i in range(p)]),[sigma[i,j]/sd[i]/sd[j] for i,j in pairs]]
            out.extend(implied); matrices.append(sigma)
        return np.array(out),matrices,errors
    fit=least_squares(lambda x:np.sqrt(np.diag(w))*(model(x)[0]-sample),initial,xtol=1e-11,ftol=1e-11,gtol=1e-11,max_nfev=3000); assert fit.success
    jac=approx_derivative(lambda x:model(x)[0],fit.x,method='3-point'); inv=np.linalg.inv(jac.T@w@jac)
    cov=inv@jac.T@w@gamma@w@jac@inv/n; df=size-len(initial); raw=n*np.dot(fit.fun,fit.fun)
    statistic,scale,shift=t3(raw,w-w@jac@inv@jac.T@w,gamma,df)
    expected=[(['χ²'],statistic),(['df'],df),(['Unadjusted DWLS χ²'],raw),(['Scaling factor'],scale),(['Shift parameter'],shift),(['Latent means',1,'Mean'],fit.x[p+1])]
    _,matrices,errors=model(fit.x)
    for g in range(2):
        expected += [(['Implied covariance group '+str(g+1),i,j],matrices[g][i,j]) for i in range(p) for j in range(p)]
        expected += [(['Residual variances',g*p+i,'Variance'],errors[g][i]) for i in range(p)]
        expected += [(['Indicator R²',g*p+i,'R²'],1-errors[g][i]/matrices[g][i,i]) for i in range(p)]
        expected += [(['Loadings',g*p+i,'SE'],np.sqrt(cov[i-1,i-1])) for i in range(1,p)]
    add('cfa',[rows.tolist(),[1]*p,[],'complete',ids.tolist(),invariance,'wlsmv'],expected,tolerance=5e-4)


rng=np.random.default_rng(20261011)
latent=rng.normal(size=180); continuous=latent[:,None]*[1,.9,1.1,.8]+rng.normal(size=(180,4))
ordinal=np.digitize(continuous,[-.8,.1,1.]); ordinal_reference(ordinal,[1]*4)
ordinal_reference((continuous>0).astype(int),[1]*4)
latent=rng.multivariate_normal([0,0],[[1,.5],[.5,1]],size=220)
continuous=latent[:,[0,0,0,1,1,1]]*[1,.8,1.1,1,.9,1.2]+rng.normal(size=(220,6))
ordinal_reference(np.digitize(continuous,[-.7,.2,1.1]),[1,1,1,2,2,2],[[1,2]])
latent=rng.normal(size=140); base=latent[:,None]*[1,.9,1.1,.8]+rng.normal(size=(140,4))
second=base*[1,1.05,.95,1.1]+np.array([.5,.7,.3,.6]); rows=np.vstack([base,second]); ids=np.r_[np.ones(140,dtype=int),np.full(140,2)]
for invariance in ('scalar','strict'): continuous_reference(rows,ids,invariance)
for invariance in ('scalar','strict'): ordinal_group_reference(np.digitize(rows,[-.8,.1,1.]),ids,invariance)
(ROOT/'tests/fixtures/sem_estimation_reference.json').write_text(json.dumps({'sources':['SciPy normal CDF quadrature and bounded polychoric likelihood optimization','NumPy full influence covariance and SciPy numerical-Jacobian DWLS sandwich','lavaan scaled.shifted T3 formula','SciPy joint raw-unit mean/covariance ML'], 'cases':cases},indent=2,allow_nan=False)+'\n',encoding='utf8')
print('Wrote',len(cases),'independent SEM references')
