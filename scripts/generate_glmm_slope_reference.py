"""Development-only independent NumPy/SciPy Laplace-ML references.

No application modules are imported. The latent conditional modes and outer
parameters are optimized independently of the portable implementation.
"""
import json
from pathlib import Path
import numpy as np
import scipy
from scipy.optimize import minimize, root
from scipy.special import expit, gammaln

ROOT=Path(__file__).resolve().parents[1]
rng=np.random.default_rng(101021)
fixtures=[]
for family in ('poisson','binomial','nbinom'):
    rows=[]; offsets=[]
    for subject in range(32):
        intercept,slope=rng.multivariate_normal([0,0],[[.5,.15],[.15,.7]])
        for x in np.linspace(-1.5,1.5,9):
            off=float(rng.uniform(-.3,.3)) if family!='binomial' else 0.0
            eta=.4+.25*x+intercept+slope*x+off
            if family=='binomial': y=int(rng.binomial(1,expit(eta)))
            elif family=='poisson': y=int(rng.poisson(np.exp(eta)))
            else: y=int(rng.negative_binomial(2,2/(2+np.exp(eta))))
            rows.append([subject,float(x),y]); offsets.append(off)
    data=np.array(rows); design=np.column_stack([np.ones(len(data)),data[:,1]])
    groups=[np.flatnonzero(data[:,0]==id_) for id_ in range(32)]
    response=data[:,2]; offset=np.array(offsets)
    def objective(point):
        beta=point[:2]; factor=np.array([[np.exp(point[2]),0],[point[3],np.exp(point[4])]])
        alpha=np.exp(point[5]) if family=='nbinom' else 0
        base=design@beta+offset; total=0.0
        for indices in groups:
            z=design[indices]@factor; y=response[indices]
            def conditional(latent,details=False):
                eta=base[indices]+z@latent
                if family=='binomial':
                    mu=expit(eta); ll=y*eta-np.logaddexp(0,eta); score=y-mu; curvature=mu*(1-mu)
                elif alpha==0:
                    mu=np.exp(eta); ll=y*eta-mu-gammaln(y+1); score=y-mu; curvature=mu
                else:
                    mu=np.exp(eta); r=1/alpha
                    ll=gammaln(y+r)-gammaln(r)-gammaln(y+1)+y*eta-(y+r)*np.log1p(mu/r)-y*np.log(r)
                    score=(y-mu)/(1+alpha*mu); curvature=mu*(1+alpha*y)/(1+alpha*mu)**2
                value=.5*latent@latent-np.sum(ll)
                gradient=latent-z.T@score
                hessian=np.eye(2)+z.T@(curvature[:,None]*z)
                return (value,gradient,hessian) if details else value
            mode=minimize(lambda u:conditional(u,True)[0],np.zeros(2),jac=lambda u:conditional(u,True)[1],
                          hess=lambda u:conditional(u,True)[2],method='trust-exact',options={'gtol':1e-9})
            stationary=root(lambda u:conditional(u,True)[1],mode.x,jac=lambda u:conditional(u,True)[2],tol=1e-11)
            value,gradient,hessian=conditional(stationary.x,True)
            if np.linalg.norm(gradient)>1e-6: raise RuntimeError('Conditional mode is not stationary')
            total+=value+.5*np.linalg.slogdet(hessian)[1]
        return float(total)
    start=[.4,.25,-.3,0,-.3]+([-.7] if family=='nbinom' else [])
    fit=minimize(objective,start,method='Nelder-Mead',options={'maxiter':2400,'xatol':1e-8,'fatol':1e-9})
    if not fit.success: raise RuntimeError(f'{family}: {fit.message}')
    point=fit.x; factor=np.array([[np.exp(point[2]),0],[point[3],np.exp(point[4])]])
    covariance=factor@factor.T
    expected=[[['coefficients',i,'estimate'],float(point[i])] for i in range(2)]
    expected += [[['log likelihood'],-float(fit.fun)], [['random intercept variance'],float(covariance[0,0])],
                 [['random slope variance'],float(covariance[1,1])], [['random intercept-slope covariance'],float(covariance[0,1])]]
    # Independently differentiate the Laplace likelihood in log-Cholesky
    # coordinates, retaining uncertainty in all nuisance covariance parameters.
    dimension=len(point); steps=2e-4*np.maximum(1,np.abs(point)); information=np.zeros((dimension,dimension))
    center=objective(point)
    for i in range(dimension):
        ei=np.zeros(dimension); ei[i]=steps[i]
        information[i,i]=(objective(point+ei)-2*center+objective(point-ei))/steps[i]**2
        for j in range(i):
            ej=np.zeros(dimension); ej[j]=steps[j]
            information[i,j]=information[j,i]=(objective(point+ei+ej)-objective(point+ei-ej)-objective(point-ei+ej)+objective(point-ei-ej))/(4*steps[i]*steps[j])
    if np.min(np.linalg.eigvalsh(information))<=0: raise RuntimeError('Reference information is not positive definite')
    joint_covariance=np.linalg.inv(information)
    expected += [[['coefficients',i,'SE'],float(np.sqrt(joint_covariance[i,i]))] for i in range(2)]
    if family=='nbinom': expected.append([['dispersion alpha (NB2)'],float(np.exp(point[5]))])
    fixtures.append(dict(name=f'GLMM correlated slope {family} independent Laplace',arguments=[rows,family,1,offsets,'offset','likelihood',1],
                         expected=expected,tolerance=3e-5,source=f'NumPy {np.__version__}; SciPy {scipy.__version__}; independent trust-exact conditional modes and log-Cholesky Nelder-Mead ML'))
(ROOT/'tests/fixtures/glmm_slope_reference.json').write_text(json.dumps(fixtures,indent=2)+'\n',encoding='utf-8')
print(f'Wrote {len(fixtures)} independent GLMM slope references')
