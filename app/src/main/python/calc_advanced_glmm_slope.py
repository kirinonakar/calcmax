"""One correlated random slope plus intercept, by two-dimensional Laplace ML.

The covariance is parameterized by a Cholesky factor in centered/scaled
predictor units and transformed back to the user's units for reporting.
"""
import math
import mpmath as mp
from calc_limits import within_limit
from calc_shared import MathError, require
from calc_advanced_common import dot, inference, integer, option, standardized_design, table, count_offsets
from calc_advanced_longitudinal import clustered_design, nelder_mead
from calc_advanced_glmm import conditional_response, observed_information
from calc_advanced_glm import fit


def laplace_marginal(x,y,offsets,clusters,family,beta,factor,alpha,details=False):
    """Integrate standard-normal latent coordinates with analytic 2D Hessians."""
    l00,l10,l11=factor
    base=[dot(row,beta)+off for row,off in zip(x,offsets)]
    # x's final column is the standardized random-slope predictor; the fixed
    # design passed to this helper can include other predictors before it.
    directions=[(l00+l10*row[-1],l11*row[-1]) for row in x]
    factorials=[math.lgamma(value+1) for value in y]
    total=0.0; modes=[]
    for cluster in clusters:
        def joint(u,v):
            value=-(u*u+v*v)/2; g0,g1=-u,-v; h00=h11=1.0; h01=0.0
            for i in cluster:
                z0,z1=directions[i]
                ll,g,h=conditional_response(base[i]+z0*u+z1*v,y[i],alpha,family,factorials[i])
                value+=ll; g0+=z0*g; g1+=z1*g
                h00+=z0*z0*h; h01+=z0*z1*h; h11+=z1*z1*h
            return value,g0,g1,h00,h01,h11
        u=v=0.0
        for _ in range(80):
            value,g0,g1,h00,h01,h11=joint(u,v)
            determinant=h00*h11-h01*h01
            require(determinant>0,'GLMM conditional information is not positive definite')
            du,dv=(h11*g0-h01*g1)/determinant,(h00*g1-h01*g0)/determinant
            if max(abs(du),abs(dv))<1e-10: break
            rate=1.0
            for _ in range(40):
                trial=(u+rate*du,v+rate*dv)
                if joint(*trial)[0]>=value-1e-12: break
                rate*=.5
            else: raise ValueError('GLMM conditional mode failed')
            u,v=trial
        else: raise ValueError('GLMM conditional mode did not converge')
        value,_,_,h00,h01,h11=joint(u,v)
        total+=value-.5*math.log(h00*h11-h01*h01)
        if details: modes.append((l00*u,l10*u+l11*v))
    return (-total,modes) if details else -total


def calculate(engine,name,a):
    rows=table(a[0],6,3); family=option(a,1,'binomial')
    require(family in ('binomial','poisson','nbinom'),'GLMM family: binomial, poisson, or nbinom (NB2)')
    require(integer(a[2],1,31)==1,'GLMM random slopes require 1 (Laplace); adaptive quadrature supports random intercepts only')
    sensitivity=option(a,5,'likelihood')
    require(sensitivity=='likelihood','Quadrature refit is unavailable for random slopes; use likelihood with Laplace')
    design,y=clustered_design(rows); n=len(y); p=len(design[0])
    require(within_limit(n,1500) and within_limit(p,8),'GLMM limit: 1500 rows and 8 fixed coefficients')
    slope=integer(a[6],1,p-1)
    offsets=[0.0]*n if a[3]==[] and str(a[4])=='offset' else count_offsets(a,3,n)
    require(family!='binomial' or not any(offsets),'GLMM offsets are supported for count families only')
    if family=='binomial':
        require(all(v in (0,1) for v in y) and len(set(y))==2,'Binomial GLMM response must contain both 0 and 1')
    else:
        require(all(v>=0 and v.is_integer() for v in y) and sum(y)>0,'Response must be nonnegative integer counts with at least one event')
    grouped={}
    for i,row in enumerate(rows): grouped.setdefault(row[0],[]).append(i)
    ids=sorted(grouped); clusters=[grouped[id_] for id_ in ids]
    require(len(ids)>=3 and sum(len({design[i][slope] for i in cluster})>1 for cluster in clusters)>=3,
            'A random slope requires within-subject predictor variation in at least three subjects')
    x,transform,centers,scales=standardized_design(design)
    # Move the slope's fixed column to the end for the marginal helper and
    # reverse that permutation before fixed-effect coefficient reporting.
    order=[j for j in range(p) if j!=slope]+[slope]
    x=[[row[j] for j in order] for row in x]
    start,_,_,_,_=fit(x,y,offsets,'binomial' if family=='binomial' else 'poisson','logit' if family=='binomial' else 'log',0)
    is_nb=family=='nbinom'
    def objective(point,nb=is_nb):
        beta=point[:p]; factor=point[p:p+3]
        if max(map(abs,beta))>=50 or max(map(abs,factor))>30 or (nb and not -12<=point[-1]<=8): return math.inf
        try: return laplace_marginal(x,y,offsets,clusters,family,beta,factor,math.exp(point[-1]) if nb else 0)
        except (MathError,OverflowError,ValueError,ZeroDivisionError): return math.inf
    candidates=[]
    for nb in ([True,False] if is_nb else [False]):
        for seed_factor in ([.5,0,.5],[1,.3,1]):
            seed=start+seed_factor+([-1.0] if nb else [])
            point,value,it,ok=nelder_mead(lambda b:objective(b,nb),seed,[.15]*len(seed),2400+200*p,True)
            if ok and math.isfinite(value): candidates.append((point,value,it,nb))
    require(candidates,'GLMM random-slope fit did not converge; reduce predictors or check separation')
    point,value,it,nb=min(candidates,key=lambda item:item[1])
    alpha=math.exp(point[-1]) if nb else 0.0
    l00,l10,l11=point[p:p+3]
    random_cov=mp.matrix([[l00*l00,l00*l10],[l00*l10,l10*l10+l11*l11]])
    back=mp.matrix([[1,-centers[slope-1]/scales[slope-1]],[0,1/scales[slope-1]]])
    random_cov=back*random_cov*back.T
    beta=[0.0]*p
    for j,original in enumerate(order): beta[original]=point[j]
    coefficients=list(map(float,transform*mp.matrix(beta)))
    warnings=['Random slopes use a Laplace approximation; multidimensional quadrature sensitivity is unavailable.']
    singular=min(map(float,mp.eigsy(mp.matrix([[l00*l00,l00*l10],[l00*l10,l10*l10+l11*l11]]),eigvals_only=True)))<1e-6
    if singular: warnings.append('Singular random-effects covariance; Wald inference is unavailable.')
    reliable=not singular
    if reliable:
        try:
            covariance=observed_information(lambda b:objective(b,nb),point)
            permutation=mp.zeros(p)
            for j,original in enumerate(order): permutation[original,j]=1
            coefficient_cov=transform*permutation*covariance[:p,:p]*permutation.T*transform.T
            coefficient_rows=inference(coefficients,coefficient_cov,['Intercept']+['x'+str(j) for j in range(1,p)],True)
        except MathError:
            reliable=False
            warnings.append('Joint observed information is not identifiable or stationary; Wald inference is unavailable.')
    if not reliable:
        coefficient_rows=[{'term':term,'estimate':coef,'SE':None,'p':None,'CI95':None,'exp(coef)':math.exp(coef) if coef<709 else math.inf} for term,coef in zip(['Intercept']+['x'+str(j) for j in range(1,p)],coefficients)]
    _,modes=laplace_marginal(x,y,offsets,clusters,family,point[:p],point[p:p+3],alpha,True)
    effects=[]
    for id_,mode in zip(ids,modes):
        transformed=list(map(float,back*mp.matrix(mode)))
        effects.append({'subject':id_,'conditional intercept mode':transformed[0],'conditional slope mode':transformed[1]})
    count=p+3+int(is_nb)
    vi,vs=float(random_cov[0,0]),float(random_cov[1,1])
    result={'coefficients':coefficient_rows,'family':family,'subjects':len(ids),'n':n,
            'random intercept variance':vi,'random slope predictor':'x'+str(slope),
            'random slope variance':vs,'random intercept-slope covariance':float(random_cov[0,1]),
            'random intercept-slope correlation':float(random_cov[0,1])/math.sqrt(vi*vs) if vi*vs>0 else None,
            'log likelihood':-value,'AIC':2*count+2*value,'BIC':math.log(n)*count+2*value,
            'quadrature points':1,'estimation':'ML (two-dimensional Laplace)','iterations':it,
            'optimizer converged':1,'singular fit':int(singular),'subject random effects':effects,
            'quadrature check':'unavailable for random slopes'}
    if is_nb: result['dispersion alpha (NB2)']=alpha
    if family=='binomial': result['latent ICC at x=0']=vi/(vi+math.pi**2/3)
    if len(ids)<20: warnings.append('Few subjects; asymptotic Wald inference may be unreliable.')
    if is_nb and not nb: warnings.append('NB2 dispersion is on the Poisson boundary.')
    result['diagnostics']={'convergence':'converged','random effects':'singular' if singular else 'interior',
                           'inference':'unavailable' if not reliable else 'caution','warnings':warnings}
    engine.note+=' GLMM with correlated random intercept and one random slope; ML by two-dimensional Laplace approximation. Conditional effects; covariance and modes are in original predictor units. ICC is evaluated at x=0. Joint observed information includes covariance-parameter uncertainty.'
    for warning in warnings: engine.note+=' '+warning
    return result
