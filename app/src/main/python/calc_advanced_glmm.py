"""Portable random-intercept GLMM: ML by adaptive Gauss-Hermite quadrature.

No SciPy/NumPy dependency. Conditional mode derivatives are analytic; the
joint marginal observed information uses central differences of the integrated
log likelihood. Coefficients describe subject-specific conditional effects.
"""
import math
from functools import lru_cache
import mpmath as mp
from calc_shared import require
from calc_advanced_common import (
    dot, inference, integer, inverse, logistic, mean, newton, option,
    softplus, standardized_design, table, count_offsets,
)
from calc_advanced_longitudinal import clustered_design, nelder_mead


@lru_cache(maxsize=8)
def hermite_rule(size):
    with mp.workdps(25):
        nodes,weights=mp.gauss_quadrature(size,'hermite')
        return tuple(map(float,nodes)),tuple(math.log(float(w)) for w in weights)


def observed_information(objective,point):
    """Central observed information including nuisance-parameter uncertainty."""
    size=len(point); steps=[2e-4*max(1,abs(v)) for v in point]
    base=objective(point); matrix=mp.zeros(size); gradient=[]
    for i in range(size):
        plus=point[:]; minus=point[:]; plus[i]+=steps[i]; minus[i]-=steps[i]
        high,low=objective(plus),objective(minus)
        gradient.append((high-low)/(2*steps[i]))
        matrix[i,i]=(high-2*base+low)/steps[i]**2
        for j in range(i):
            values=[]
            for si,sj in ((1,1),(1,-1),(-1,1),(-1,-1)):
                trial=point[:]; trial[i]+=si*steps[i]; trial[j]+=sj*steps[j]
                values.append(objective(trial))
            matrix[i,j]=matrix[j,i]=(values[0]-values[1]-values[2]+values[3])/(4*steps[i]*steps[j])
    require(all(math.isfinite(float(v)) for v in matrix),'GLMM information is not finite')
    require(min(map(float,mp.eigsy(matrix,eigvals_only=True)))>1e-7,
            'GLMM information is singular; check separation or add subjects')
    covariance=inverse(matrix)
    require(max(abs(float(v)) for v in covariance*mp.matrix(gradient))<.02,
            'GLMM optimizer did not reach a stationary estimate')
    return covariance


def calculate(engine,name,a):
    rows=table(a[0],6,3); family=option(a,1,'binomial')
    require(family in ('binomial','poisson','nbinom'),'GLMM family: binomial, poisson, or nbinom (NB2)')
    nodes_count=integer(a[2],1,31) if len(a)>2 else 15
    require(nodes_count==1 or nodes_count>=7,'Use 1 (Laplace) or 7-31 quadrature points')
    x,y=clustered_design(rows); n=len(y); p=len(x[0])
    require(n<=1500 and p<=8,'GLMM limit: 1500 rows and 8 fixed coefficients')
    offsets=count_offsets(a,3,n)
    require(family!='binomial' or not any(offsets),'GLMM offsets are supported for count families only')
    if family=='binomial':
        require(all(v in (0,1) for v in y) and len(set(y))==2,'Binomial GLMM response must contain both 0 and 1')
    else:
        require(all(v>=0 and v.is_integer() for v in y) and sum(y)>0,'Response must be nonnegative integer counts with at least one event')
    grouped={}
    for i,row in enumerate(rows): grouped.setdefault(row[0],[]).append(i)
    ids=sorted(grouped); clusters=[grouped[id_] for id_ in ids]
    require(len(ids)>=3 and any(len(c)>1 for c in clusters),'GLMM requires at least three subjects and repeated observations')
    x,transform,_,_=standardized_design(x)
    nodes,logweights=hermite_rule(nodes_count)
    factorials=[math.lgamma(v+1) for v in y]

    def conditional(eta,value,alpha,index):
        if family=='binomial':
            mu=logistic(eta)
            return value*eta-softplus(eta),value-mu,mu*(1-mu)
        mu=math.exp(eta)
        if alpha==0:
            return value*eta-mu-factorials[index],value-mu,mu
        r=1/alpha
        # Avoid cancellation in gamma differences when alpha is near zero.
        gamma=math.fsum(math.log1p(k/r) for k in range(int(value))) if value<=100 else math.lgamma(r+value)-math.lgamma(r)-value*math.log(r)
        ll=gamma-factorials[index]+value*eta-(value+r)*math.log1p(mu/r)
        return ll,(value-mu)/(1+alpha*mu),mu*(1+alpha*value)/(1+alpha*mu)**2

    def marginal(beta,sd,alpha,details=False,rule=None):
        active_nodes,active_logweights=rule if rule is not None else (nodes,logweights)
        base=[dot(row,beta)+off for row,off in zip(x,offsets)]
        total=0.0; effects=[]
        for id_,cluster in zip(ids,clusters):
            def joint(u):
                value=-u*u/2; score=-u; curvature=1.0
                for i in cluster:
                    ll,g,h=conditional(base[i]+sd*u,y[i],alpha,i)
                    value+=ll; score+=sd*g; curvature+=sd*sd*h
                return value,score,curvature
            mode=0.0
            for _ in range(80):
                value,score,curvature=joint(mode); step=score/curvature
                if abs(step)<1e-10: break
                for _ in range(40):
                    trial=mode+step
                    if joint(trial)[0]>=value-1e-12: break
                    step*=.5
                else: raise ValueError('GLMM conditional mode failed')
                mode=trial
            else: raise ValueError('GLMM conditional mode did not converge')
            _,_,curvature=joint(mode)
            locations=[mode+math.sqrt(2/curvature)*node for node in active_nodes]
            terms=[logw+node*node+joint(u)[0] for node,logw,u in zip(active_nodes,active_logweights,locations)]
            top=max(terms); weights=[math.exp(v-top) for v in terms]; mass=math.fsum(weights)
            total+=top+math.log(mass)-.5*math.log(math.pi*curvature)
            if details:
                posterior=sd*math.fsum(w*u for w,u in zip(weights,locations))/mass
                effects.append({'subject':id_,'conditional mode':sd*mode,'posterior mean':posterior})
        return (-total,effects) if details else -total

    def initial_exact(beta):
        score=[0.0]*p; info=[[0.0]*p for _ in range(p)]; value=0.0
        for i,row in enumerate(x):
            ll,g,h=conditional(dot(row,beta)+offsets[i],y[i],0,i); value-=ll
            for j in range(p):
                score[j]-=row[j]*g
                for k in range(p): info[j][k]+=row[j]*row[k]*h
        return value,score,info
    top=max(offsets)
    intercept=math.log(mean(y)/(1-mean(y))) if family=='binomial' else math.log(sum(y))-top-math.log(math.fsum(math.exp(v-top) for v in offsets))
    start,_,_,_=newton([intercept]+[0.0]*(p-1),initial_exact)
    is_nb=family=='nbinom'
    def objective(point,random=True,nb=is_nb):
        beta=point[:p]; sd=abs(point[p]) if random else 0.0
        coordinate=point[-1] if nb else 0.0
        if max(map(abs,beta))>=50 or sd>30 or (nb and not -12<=coordinate<=8): return math.inf
        try: return marginal(beta,sd,math.exp(coordinate) if nb else 0.0)
        except (OverflowError,ValueError,ZeroDivisionError): return math.inf

    def fit(random,nb):
        best=None; fn=lambda point:objective(point,random,nb)
        for sd in ((.3,1.0) if random else (0,)):
            seed=start+([sd] if random else [])+([-1.0] if nb else [])
            point,value,it,converged=nelder_mead(fn,seed,[.15]*len(seed),1200+150*p,True)
            if best is None or value<best[1]: best=(point,value,it,converged,random,nb)
        return best
    best=fit(True,is_nb); boundary=fit(False,is_nb)
    if boundary[1]<=best[1]+1e-7: best=boundary
    if is_nb:
        poisson=fit(True,False); poisson_boundary=fit(False,False)
        if poisson_boundary[1]<=poisson[1]+1e-7: poisson=poisson_boundary
        if poisson[1]<=best[1]+1e-7: best=poisson
    point,value,it,converged,random,nb=best
    require(math.isfinite(value) and converged,'GLMM did not converge; reduce predictors or check separation')
    sd=abs(point[p]) if random else 0.0; alpha=math.exp(point[-1]) if nb else 0.0
    # At zero variance, exclude that boundary coordinate from Wald information.
    covariance=observed_information(lambda b:objective(b,random,nb),point)
    coefficients=list(map(float,transform*mp.matrix(point[:p])))
    coefficient_cov=transform*covariance[:p,:p]*transform.T
    _,effects=marginal(point[:p],sd,alpha,True)
    result={'coefficients':inference(coefficients,coefficient_cov,['Intercept']+['x'+str(i) for i in range(1,p)],True),
            'family':family,'random intercept variance':sd*sd,'subjects':len(ids),
            'log likelihood':-value,'AIC':2*(p+1+int(is_nb))+2*value,
            'quadrature points':nodes_count,'estimation':'ML (Laplace)' if nodes_count==1 else 'ML (adaptive Gauss-Hermite)',
            'iterations':it,'optimizer converged':1,'singular fit':int(sd*sd<1e-6),
            'subject random effects':effects}
    if is_nb: result['dispersion alpha (NB2)']=alpha
    if family=='binomial': result['latent ICC']=sd*sd/(sd*sd+math.pi**2/3)
    if 1<nodes_count<31:
        check_points=min(31,nodes_count+10)
        difference=abs(marginal(point[:p],sd,alpha,rule=hermite_rule(check_points))-value)
        result['quadrature check points']=check_points
        result['quadrature log likelihood difference']=difference
        if difference>1e-3: engine.note += ' Quadrature accuracy warning: increase the point count and compare estimates.'
    engine.note += ' Random-intercept GLMM; '+family+' '+('logit' if family=='binomial' else 'log')+' link; subject-specific coefficients. '+result['estimation']+'. Joint marginal observed information by central differences; asymptotic Wald 95% CI.'
    if len(ids)<20: engine.note += ' Few subjects: asymptotic Wald inference may be unreliable.'
    if result['singular fit']: engine.note += ' Singular fit: random-intercept variance is on or near zero.'
    if is_nb and alpha==0: engine.note += ' NB2 dispersion is zero (Poisson boundary).'
    if nodes_count==1: engine.note += ' Laplace approximation; increase quadrature points to assess integration accuracy.'
    return result
