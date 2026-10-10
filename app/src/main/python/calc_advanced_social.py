"""Single-mediator and single-moderator OLS models with optional covariates."""
import math
import random
from calc_shared import require, MathError
from calc_advanced_common import table, integer, inference, mean, variance, normal_p
from calc_advanced_linear import least_squares


def ols(x,y,names):
    fit=least_squares(x,y); beta,cov,sse,fitted=fit; df=len(y)-len(beta)
    require(sse>1e-20,'Residual variance must be positive')
    return beta,cov*(sse/df),inference(beta,cov*(sse/df),names,df=df),fit


def quantile(values,q):
    values=sorted(values); at=(len(values)-1)*q; i=int(at); return values[i]+(at-i)*(values[min(i+1,len(values)-1)]-values[i])


def calculate(engine,name,a):
    rows=table(a[0],5,3); n=len(rows); covariates=len(rows[0])-3
    x=[r[0] for r in rows]; w=[r[1] for r in rows]; y=[r[-1] for r in rows]; c=[r[2:-1] for r in rows]
    names=['Intercept','x1','x2']+['x'+str(i+3) for i in range(covariates)]
    if name=='moderation':
        mx,mw=mean(x),mean(w); sx,sw=math.sqrt(variance(x)),math.sqrt(variance(w))
        require(sx>0 and sw>0,'Predictor and moderator must vary')
        design=[[1.,xx-mx,ww-mw]+cc+[(xx-mx)*(ww-mw)] for xx,ww,cc in zip(x,w,c)]
        beta,cov,coef,fit=ols(design,y,names+['x1:x2']); slopes=[]
        for shift in (-sw,0.,sw):
            contrast=[0.,1.,shift]+[0.]*covariates+[shift]
            # The moderator main effect is not part of dY/dX.
            contrast[2]=0.
            estimate=beta[1]+shift*beta[-1]
            import mpmath as mp
            h=mp.matrix(contrast); uncertainty=float((h.T*cov*h)[0])
            require(uncertainty>0,'Conditional slope variance is undefined')
            se=math.sqrt(uncertainty); from calc_statistics import _t_sf
            interval=inference([estimate],[[uncertainty]],['Slope'],df=n-len(beta))[0]['CI95']
            slopes.append({'Moderator value':mw+shift,'estimate':estimate,'SE':se,'p':float(2*_t_sf(abs(estimate/se),n-len(beta))),'CI95':interval})
        return {'n':n,'Predictor center':mx,'Moderator center':mw,'coefficients':coef,'Conditional slopes':slopes,'R²':1-fit[2]/sum((v-mean(y))**2 for v in y),
                'Assumptions':'OLS with centered predictor and moderator, their product, and additive covariates; independent homoscedastic normal errors for t inference. Slopes shown at moderator mean ± sample SD. Association alone does not establish causation.'}
    samples=integer(a[1],100,20000,capacity=True) if len(a)>1 else 2000; seed=integer(a[2],0,2**32-1) if len(a)>2 else 0
    ax=[[1.,xx]+cc for xx,cc in zip(x,c)]
    bx=[[1.,xx,mm]+cc for xx,mm,cc in zip(x,w,c)]
    ab,ac,acoef,_=ols(ax,w,['Intercept','x1']+names[3:]); bb,bc,bcoef,_=ols(bx,y,names)
    total,tc,tcoef,_=ols(ax,y,['Intercept','x1']+names[3:])
    effect=ab[1]*bb[2]; se=math.sqrt(bb[2]**2*float(ac[1,1])+ab[1]**2*float(bc[2,2]))
    rng=random.Random(seed); draws=[]; failures=0
    for _ in range(samples):
        indices=[rng.randrange(n) for _ in rows]
        try:
            aa=least_squares([ax[i] for i in indices],[w[i] for i in indices])[0][1]
            b=least_squares([bx[i] for i in indices],[y[i] for i in indices])[0][2]
            draws.append(aa*b)
        except MathError: failures+=1
    require(len(draws)>=.9*samples,'Too many singular bootstrap samples; add observations or simplify covariates')
    return {'n':n,'a (X → M)':ab[1],'b (M → Y | X)':bb[2],'Direct effect c′':bb[1],'Total effect c':total[1],'Indirect effect a×b':effect,
            'Bootstrap percentile CI95':[quantile(draws,.025),quantile(draws,.975)],'Bootstrap successful':len(draws),'Bootstrap singular':failures,'Seed':seed,
            'Sobel SE':se,'Sobel p (normal approximation)':normal_p(effect/se) if se>0 else None,'Mediator coefficients':acoef,'Outcome coefficients':bcoef,'Total-effect coefficients':tcoef,
            'Assumptions':'One continuous mediator; independent rows, linear additive OLS, same covariates in all equations. Row bootstrap percentile CI (not BCa). Causal mediation needs temporal order and no unmeasured confounding; this model alone cannot establish it.'}
