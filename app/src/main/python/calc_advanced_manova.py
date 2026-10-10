"""Type III factorial MANOVA and wide within-subject multivariate contrasts."""
import math
import mpmath as mp
from calc_shared import require
from calc_advanced_common import table, integer, option
from calc_advanced_linear import factorial_design, least_squares
from calc_statistics import _f_sf


def multivariate_tests(e,h,q,v):
    from calc_advanced_multivariate import positive
    positive(e); p=e.rows
    require(v>=p and q>0,'MANOVA needs enough residual degrees of freedom')
    values,vectors=mp.eigsy(e+h)
    white=vectors*mp.diag([1/mp.sqrt(t) for t in values])*vectors.T
    roots=[max(0.,min(1-1e-14,float(t))) for t in mp.eigsy(white*h*white,eigvals_only=True)]
    s=min(p,q); m=(abs(p-q)-1)/2; z=(v-p-1)/2
    pillai=sum(roots); wilks=math.prod(1-t for t in roots)
    hotelling=sum(t/(1-t) for t in roots); roy=max(t/(1-t) for t in roots)
    def test(label,value,df1,df2,f):
        valid=df1>0 and df2>0 and f is not None and math.isfinite(f)
        return dict(Test=label,Statistic=value,F=f if valid else None,df1=df1,df2=df2,p=float(_f_sf(f,df1,df2)) if valid else None)
    df1=s*(2*m+s+1); df2=s*(2*z+s+1)
    result=[test('Pillai trace',pillai,df1,df2,df2/df1*pillai/(s-pillai))]
    t=math.sqrt((p*p*q*q-4)/(p*p+q*q-5)) if p*p+q*q>5 else 1.
    df1=p*q; df2=(v-(p-q+1)/2)*t-(p*q-2)/2
    powered=wilks**(1/t)
    result.append(test('Wilks lambda (Rao F)',wilks,df1,df2,(1-powered)/powered*df2/df1))
    if z>1:
        b=(p+2*z)*(q+2*z)/(2*(2*z+1)*(z-1))
        df1=p*q; df2=4+(p*q+2)/(b-1); c=(df2-2)/(2*z)
        f=df2/df1*hotelling/c
    elif z<=0:
        df1=s*(2*m+s+1); df2=s*(s*z+1); f=df2/df1*hotelling/s
    else: df1=p*q; df2=0; f=None
    result.append(test('Hotelling–Lawley trace',hotelling,df1,df2,f))
    df1=max(p,q); df2=v-df1+q
    result.append(test('Roy largest root (upper-bound F)',roy,df1,df2,df2/df1*roy))
    return result


def calculate(engine,a):
    rows=table(a[0],4,2); n=len(rows); mode=option(a,1,'oneway')
    require(mode in ('factorial','repeated'),'Choose factorial or repeated MANOVA')
    if mode=='factorial':
        count=integer(a[2],1,len(rows[0])-2) if len(a)>2 else 2
        order=integer(a[3],1,count) if len(a)>3 else count
        design,terms,_,_=factorial_design([r[:count] for r in rows],list(range(count)),order)
        responses=[r[count:] for r in rows]; p=len(responses[0])
        require(p>=2,'Select at least two responses')
        fits=[least_squares(design,list(y)) for y in zip(*responses)]
        beta=mp.matrix([fit[0] for fit in fits]).T; cov=fits[0][1]; v=n-beta.rows
        residual=mp.matrix([[responses[i][j]-fits[j][3][i] for j in range(p)] for i in range(n)])
        e=residual.T*residual; tests=[]
        labels=engine.request.get('statisticsTermLabels') or {}
        for variables,indices in terms:
            contrast=mp.zeros(len(indices),beta.rows)
            for i,j in enumerate(indices): contrast[i,j]=1
            estimate=contrast*beta; h=estimate.T*(contrast*cov*contrast.T)**-1*estimate
            label=' × '.join(labels.get('factor:'+str(j+1),'Factor '+str(j+1)) for j in variables)
            tests.extend(dict(Term=label,**test) for test in multivariate_tests(e,h,len(indices),v))
        return {'n':n,'responses':p,'df residual':v,'Design':'Factorial / Type III / sum contrasts','Interaction order':order,
                'Multivariate tests':tests,'Error SSCP':e.tolist(),
                'Assumptions':'Independent rows; categorical factors first, then responses. Type III coefficient-block tests with sum contrasts, including hierarchical interactions. Full-rank design, multivariate normal errors and equal covariance. Interpret interactions before main effects. Roy F is an upper bound.'}
    occasions=integer(a[2],2,len(rows[0])) if len(a)>2 else len(rows[0])
    require(len(rows[0])%occasions==0,'Use equally sized response blocks in occasion order')
    p=len(rows[0])//occasions
    # Each subject is one wide row. Differences to the last occasion span the
    # zero-sum within-subject hypothesis; Hotelling's test is basis invariant.
    transformed=[[r[t*p+j]-r[(occasions-1)*p+j] for t in range(occasions-1) for j in range(p)] for r in rows]
    centers=[math.fsum(c)/n for c in zip(*transformed)]
    residual=mp.matrix([[v-centers[j] for j,v in enumerate(r)] for r in transformed])
    e=residual.T*residual; mu=mp.matrix(centers); h=n*mu*mu.T
    return {'n':n,'responses per occasion':p,'Occasions':occasions,'df residual':n-1,'Design':'Repeated / wide within-subject contrasts',
            'Multivariate tests':multivariate_tests(e,h,1,n-1),'Error SSCP':e.tolist(),'Hypothesis SSCP':h.tolist(),
            'Assumptions':'One independent subject per wide row, equal response blocks ordered by occasion. Tests equality of response means across occasions jointly using within-subject contrasts; no sphericity assumption. Complete observations and nonsingular contrast covariance required. No between-subject factors, multi-factor within-subject design or irregular visits. Roy F is an upper bound.'}
