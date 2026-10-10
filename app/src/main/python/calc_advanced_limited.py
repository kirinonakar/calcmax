"""Quantile, censored-normal and zero-inflated count regression."""
import math
import mpmath as mp
from calc_shared import require, MathError
from calc_advanced_common import table, number, option, regression_data, standardized_design, inference, dot, logistic
from calc_advanced_linear import least_squares
from calc_advanced_optimize import minimize, information
from calc_advanced_social import quantile


def quantreg(rows,a):
    q=number(a[1]) if len(a)>1 else .5; require(0<q<1,'Quantile must lie in (0,1)')
    x,y=regression_data(rows); x,transform,_,_=standardized_design(x); n=len(y); p=len(x[0])
    # Smooth IRLS is followed by a dual-feasibility certificate, so tiny steps
    # alone do not certify convergence at a nonoptimal kink.
    beta=least_squares(x,y)[0]; scale=max(quantile(y,.75)-quantile(y,.25),math.sqrt(sum((v-sum(y)/n)**2 for v in y)/n),1e-8)
    epsilon=1e-8*scale
    for iteration in range(5000):
        residual=[v-dot(row,beta) for row,v in zip(x,y)]
        weights=[(q if r>=0 else 1-q)/max(abs(r),epsilon) for r in residual]
        nextbeta=least_squares(x,y,weights)[0]
        delta=max(abs(v-b) for v,b in zip(nextbeta,beta)); beta=nextbeta
        if delta<1e-9*max(scale,max(map(abs,beta)),1): break
    else: raise MathError('Quantile regression did not converge')
    residual=[v-dot(row,beta) for row,v in zip(x,y)]
    # Solve for subgradients of active zero residuals; require their legal bounds.
    active=[i for i,r in enumerate(residual) if abs(r)<1e-5*scale]
    fixed=[i for i in range(n) if i not in active]
    rhs=mp.matrix([-sum(x[i][j]*(q if residual[i]>0 else q-1) for i in fixed) for j in range(p)])
    design=mp.matrix([[x[i][j] for i in active] for j in range(p)])
    require(len(active)>=p,'Quantile solution did not reach a certifiable optimum')
    try: dual=design.T*(design*design.T)**-1*rhs
    except ZeroDivisionError: raise MathError('Quantile solution is numerically unidentified')
    require(all(q-1-2e-5<=v<=q+2e-5 for v in dual),'Quantile solution failed the optimality check')
    require(max(abs(v) for v in design*dual-rhs)<1e-5,'Quantile solution failed the optimality check')
    loss=sum(q*r if r>=0 else (q-1)*r for r in residual)
    # Gaussian-kernel sandwich with Hall-Sheather bandwidth, as in QuantReg.
    z=float(mp.sqrt(2)*mp.erfinv(2*q-1)); density=math.exp(-z*z/2)/math.sqrt(2*math.pi)
    probability_band=n**(-1/3)*1.95996398454**(2/3)*(1.5*density*density/(2*z*z+1))**(1/3)
    bandwidth=None; covariance=None
    if 0<q-probability_band<q+probability_band<1:
        sd=math.sqrt(sum((v-sum(y)/n)**2 for v in y)/(n-1)); iqr=quantile(residual,.75)-quantile(residual,.25)
        bandwidth=min(sd,iqr/1.34)*(float(mp.sqrt(2)*mp.erfinv(2*(q+probability_band)-1))-float(mp.sqrt(2)*mp.erfinv(2*(q-probability_band)-1)))
        if bandwidth>epsilon:
            f0=sum(math.exp(-.5*(r/bandwidth)**2)/math.sqrt(2*math.pi) for r in residual)/(n*bandwidth)
            xx=mp.matrix(x); bread=(xx.T*xx)**-1
            meat=xx.T*mp.diag([(q if r>0 else 1-q)**2/f0**2 for r in residual])*xx
            covariance=transform*bread*meat*bread*transform.T
    original=list(map(float,transform*mp.matrix(beta))); names=['Intercept']+['x'+str(j) for j in range(1,p)]
    coefficients=inference(original,covariance,names) if covariance is not None else [{'term':key,'estimate':v,'SE':None,'p':None,'CI95':None} for key,v in zip(names,original)]
    return {'n':n,'Quantile':q,'Check loss':loss,'coefficients':coefficients,'Iterations':iteration+1,'Bandwidth':bandwidth,
            'Inference':'Asymptotic Gaussian-kernel sandwich / Hall–Sheather' if covariance is not None else 'Unavailable: insufficient residual density / bandwidth',
            'Assumptions':'Independent rows; linear conditional quantile. Full-rank design and checked subgradient optimum. SEs are asymptotic; small samples and discrete outcomes may not support density-based inference.'}


def logcdf(z):
    if z>-8: return math.log(.5*math.erfc(-z/math.sqrt(2)))
    return float(mp.log(mp.erfc(-mp.mpf(z)/mp.sqrt(2))/2))


def tobit(rows,a):
    lower=None if option(a,1,'0')=='none' else number(a[1]) if len(a)>1 else 0.
    upper=None if option(a,2,'none')=='none' else number(a[2])
    require(lower is not None or upper is not None,'Specify at least one censoring bound')
    require(lower is None or upper is None or lower<upper,'Lower bound must be below upper bound')
    x,y=regression_data(rows); x,transform,_,_=standardized_design(x); n=len(y); p=len(x[0])
    require(all((lower is None or v>=lower) and (upper is None or v<=upper) for v in y),'Observed responses must lie within the censoring bounds')
    uncensored=[i for i,v in enumerate(y) if (lower is None or v>lower) and (upper is None or v<upper)]
    require(len(uncensored)>p,'More uncensored observations than coefficients are required')
    start=least_squares([x[i] for i in uncensored],[y[i] for i in uncensored]); sd=math.sqrt(start[2]/len(uncensored))
    require(sd>0,'Uncensored residual variance must be positive')
    def objective(parameters):
        beta=parameters[:-1]; sigma=math.exp(parameters[-1]); value=0.; grad=[0.]*(p+1)
        for row,v in zip(x,y):
            mu=dot(row,beta)
            if lower is not None and v==lower:
                z=(lower-mu)/sigma; ll=logcdf(z); mills=math.exp(-z*z/2-.5*math.log(2*math.pi)-ll)
                value-=ll; gb=mills/sigma; gs=mills*z
            elif upper is not None and v==upper:
                z=(mu-upper)/sigma; ll=logcdf(z); mills=math.exp(-z*z/2-.5*math.log(2*math.pi)-ll)
                value-=ll; gb=-mills/sigma; gs=mills*z
            else:
                z=(v-mu)/sigma; value+=.5*z*z+parameters[-1]+.5*math.log(2*math.pi); gb=-z/sigma; gs=1-z*z
            for j in range(p): grad[j]+=gb*row[j]/n
            grad[-1]+=gs/n
        return value/n,grad
    parameters,value,iterations=minimize(start[0]+[math.log(sd)],objective)
    cov=information(parameters,objective,n); sigma=math.exp(parameters[-1]); t=mp.eye(p+1)
    for i in range(p):
        for j in range(p): t[i,j]=transform[i,j]
    t[p,p]=sigma; original=list(map(float,transform*mp.matrix(parameters[:-1])))+[sigma]
    return {'n':n,'Uncensored n':len(uncensored),'Left censored n':sum(v==lower for v in y),'Right censored n':sum(v==upper for v in y),'Lower bound':lower,'Upper bound':upper,
            'coefficients':inference(original,t*cov*t.T,['Intercept']+['x'+str(j) for j in range(1,p)]+['sigma']),
            'log likelihood':-n*value,'AIC':2*(p+1)+2*n*value,'Iterations':iterations,
            'Assumptions':'Type-I censored-normal regression, constant latent SD; responses at specified bounds are censored. Coefficients predict the latent response, not observed marginal effects. Truncated samples and selection models are not supported.'}


def zeroinflated(rows,a):
    family=option(a,1,'poisson'); inflation=option(a,2,'intercept')
    require(family in ('poisson','nbinom'),'Choose Poisson or NB2 counts')
    require(inflation in ('intercept','same'),'Inflation predictors: intercept or same as count predictors')
    x,y=regression_data(rows); require(all(v>=0 and v.is_integer() for v in y),'Counts must be nonnegative integers')
    require(any(v==0 for v in y) and any(v>0 for v in y),'Zero-inflated models require both zero and positive counts')
    x,transform,_,_=standardized_design(x); z=x if inflation=='same' else [[1.] for _ in x]; p=len(x[0]); k=len(z[0]); n=len(y)
    require(n>p+k+(family=='nbinom'),'More observations than model parameters are required')
    def objective(parameters):
        beta=parameters[:p]; gamma=parameters[p:p+k]; alpha=math.exp(parameters[-1]) if family=='nbinom' else None
        value=0.; grad=[0.]*len(parameters); cache={}
        for row,zi,v in zip(x,z,y):
            eta=dot(row,beta); require(abs(eta)<500,'Count predictor exceeds the numeric range')
            mu=math.exp(eta); inflation_eta=dot(zi,gamma); pi=logistic(inflation_eta)
            require(abs(inflation_eta)<35,'Inflation probabilities reached an unidentified boundary')
            if alpha is None:
                ll=v*eta-mu-math.lgamma(v+1); ll0=-mu; gb=mu-v
            else:
                r=1/alpha; d=1+alpha*mu; ld=math.log1p(alpha*mu)
                if v not in cache: cache[v]=(math.lgamma(v+r)-math.lgamma(r)-math.lgamma(v+1),float(mp.digamma(v+r)-mp.digamma(r)))
                ll=cache[v][0]+v*(math.log(alpha)+eta)-(v+r)*ld; ll0=-r*ld
                gb=(mu-v)/d; ga=r*(cache[v][1]-ld)-v+(v+r)*alpha*mu/d
            structural=0.
            if v==0:
                # Stable log-sum-exp mixture likelihood and posterior membership.
                left=math.log(pi); right=math.log1p(-pi)+ll0; maximum=max(left,right)
                mix=maximum+math.log(math.exp(left-maximum)+math.exp(right-maximum)); structural=math.exp(left-mix); value-=mix
            else: value-=math.log1p(-pi)+ll
            for j in range(p): grad[j]+=(1-structural)*gb*row[j]/n
            for j in range(k): grad[p+j]+=(pi-structural)*zi[j]/n
            if alpha is not None: grad[-1]+=(1-structural)*ga/n
        return value/n,grad
    candidates=[]
    for gamma in (-1.,0.,1.):
        start=[math.log(sum(y)/n)]+[0.]*(p-1)+[gamma]+[0.]*(k-1)+([math.log(.5)] if family=='nbinom' else [])
        try: candidates.append(minimize(start,objective))
        except MathError: pass
    require(candidates,'Zero-inflated model did not converge; possible separation or unidentified mixture')
    parameters,value,iterations=min(candidates,key=lambda fit:fit[1]); cov=information(parameters,objective,n)
    t=mp.eye(len(parameters))
    for i in range(p):
        for j in range(p): t[i,j]=transform[i,j]
    if inflation=='same':
        for i in range(k):
            for j in range(k): t[p+i,p+j]=transform[i,j]
    original=list(map(float,transform*mp.matrix(parameters[:p])))+list(map(float,(transform if inflation=='same' else mp.eye(1))*mp.matrix(parameters[p:p+k])))
    if family=='nbinom': original+=[math.exp(parameters[-1])]; t[len(parameters)-1,len(parameters)-1]=original[-1]
    names=['Count Intercept']+['Count: x'+str(j) for j in range(1,p)]+['Inflation Intercept']+['Inflation: x'+str(j) for j in range(1,k)]+(['alpha'] if family=='nbinom' else [])
    fitted=[{'Observation':i+1,'Structural-zero probability':logistic(dot(zi,parameters[p:p+k])),'Expected count':(1-logistic(dot(zi,parameters[p:p+k])))*math.exp(dot(row,parameters[:p]))} for i,(row,zi) in enumerate(zip(x,z))]
    return {'n':n,'Family':'ZIP' if family=='poisson' else 'ZINB2','coefficients':inference(original,t*cov*t.T,names),'log likelihood':-n*value,'AIC':2*len(parameters)+2*n*value,
            'Iterations':iterations,'Fitted observations':fitted,'Assumptions':'Independent count observations; log count mean and logit structural-zero probability. Inflation uses an intercept or the same predictors. Joint ML observed-information Wald inference; mixture boundary / singular information is rejected. No automatic Vuong test.'}


def calculate(engine,name,a):
    rows=table(a[0],4,2)
    return {'quantreg':quantreg,'tobit':tobit,'zeroinflated':zeroinflated}[name](rows,a)
