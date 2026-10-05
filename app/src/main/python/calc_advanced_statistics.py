"""Portable advanced statistics (binary64 numerics; no native dependencies).

Models intentionally expose their specification in result notes: Breslow Cox,
proportional odds, NB2, Gaussian random intercept ML, independence-working GEE.
"""
import math
import random
import statistics
import mpmath as mp
import sympy as s
from calc_shared import MathError, require
from calc_statistics import _chisq_sf, _f_sf, _normal_sf

FUNCTIONS = set('padjust cohend eta2 levene bartlett mcnemar kaplanmeier logrank cox repeatedanova mixedmodel gee multinomial ordinal poissonreg nbreg bootstrapci testpower samplesize kstest crossvalidate pca kmeans impute'.split())


def number(x):
    try:
        value = float(x)
    except (ValueError, TypeError, OverflowError):
        raise MathError('Enter finite numeric data')
    require(math.isfinite(value), 'Enter finite numeric data')
    return value


def vector(x, minimum=1):
    require(isinstance(x, (list, tuple)), 'Enter a data list')
    values = [number(v) for v in x]
    require(minimum <= len(values) <= 5000, 'Enter enough observations (limit: 5000)')
    return values


def table(x, minimum=2, columns=1):
    require(isinstance(x, (list, tuple)) and len(x) >= minimum, 'Enter a data table with enough rows')
    rows = [vector(row, columns) for row in x]
    require(all(len(row) == len(rows[0]) for row in rows), 'Rows must have equal column counts')
    require(len(rows) <= 5000 and len(rows[0]) <= 20, 'Limit: 5000 rows and 20 columns')
    return rows


def integer(x, low, high):
    v = number(x)
    require(v.is_integer() and low <= v <= high, 'Integer option out of range')
    return int(v)


def option(a, i, default):
    return str(a[i]) if len(a) > i else default


def convert(value):
    if isinstance(value, dict): return {k: convert(v) for k, v in value.items()}
    if isinstance(value, (list, tuple)): return [convert(v) for v in value]
    if isinstance(value, str): return value
    if value is None: return 'unavailable'
    if isinstance(value, int): return s.Integer(value)
    require(not math.isnan(float(value)), 'Numerical result is undefined')
    return s.Float(value, 15) if math.isfinite(float(value)) else s.oo if value > 0 else -s.oo


def inverse(a):
    try: return mp.matrix(a)**-1
    except (ZeroDivisionError, ValueError): raise MathError('Singular model: remove collinear predictors or add observations')


def dot(a, b): return sum(x*y for x, y in zip(a, b))


def mean(x): return sum(x)/len(x)


def variance(x): return sum((v-mean(x))**2 for v in x)/(len(x)-1)


def normal_p(z): return float(2*_normal_sf(abs(z)))


def derivatives(f, beta, hessian=False):
    steps = [1e-4*max(1, abs(b)) for b in beta]
    f0 = f(beta)
    grad = []
    hess = [[0.0]*len(beta) for _ in beta]
    for i, step in enumerate(steps):
        plus, minus = beta[:], beta[:]
        plus[i] += step; minus[i] -= step
        fp, fm = f(plus), f(minus)
        grad.append((fp-fm)/(2*step))
        if hessian:
            hess[i][i] = (fp-2*f0+fm)/step**2
            for j in range(i):
                vals = []
                for si, sj in ((1,1),(1,-1),(-1,1),(-1,-1)):
                    b = beta[:]; b[i] += si*step; b[j] += sj*steps[j]
                    vals.append(f(b))
                hess[i][j] = hess[j][i] = (vals[0]-vals[1]-vals[2]+vals[3])/(4*step*steps[j])
    return grad, hess


def optimize(f, start):
    """BFGS with numerical gradient and Armijo backtracking; reject failed fits."""
    beta = start[:]; n = len(beta)
    require(n <= 30, 'Model limit: 30 parameters')
    H = [[float(i == j) for j in range(n)] for i in range(n)]
    value = f(beta); grad, _ = derivatives(f, beta)
    for iteration in range(300):
        if max(map(abs, grad)) < 2e-5:
            # A vanishing score alone also occurs when an estimate tends to
            # infinity. Check the information-scaled step before declaring
            # convergence (separation, monotone Cox, zero-mean count strata).
            _,information=derivatives(f,beta,True)
            require(min(float(v) for v in mp.eigsy(mp.matrix(information),eigvals_only=True))>1e-8,'Model information is singular; possible separation or boundary estimate')
            newton=inverse(information)*mp.matrix(grad)
            if max(abs(float(v)) for v in newton)<1e-3: break
        direction = [-dot(row, grad) for row in H]
        if dot(direction, grad) >= 0:
            H = [[float(i == j) for j in range(n)] for i in range(n)]
            direction = [-g for g in grad]
        step = 1.0
        for _ in range(40):
            candidate = [b+step*d for b,d in zip(beta,direction)]
            trial = f(candidate)
            if math.isfinite(trial) and trial <= value+1e-4*step*dot(grad,direction): break
            step *= .5
        else: raise MathError('Model did not converge; check separation, scaling and identifiability')
        newgrad, _ = derivatives(f, candidate)
        delta = [a-b for a,b in zip(candidate,beta)]
        change = [a-b for a,b in zip(newgrad,grad)]
        curvature = dot(delta,change)
        if curvature > 1e-12:
            hy = [dot(row,change) for row in H]; yhy = dot(change,hy)
            H = [[H[i][j]+(curvature+yhy)*delta[i]*delta[j]/curvature**2-(hy[i]*delta[j]+delta[i]*hy[j])/curvature for j in range(n)] for i in range(n)]
        beta, value, grad = candidate, trial, newgrad
    else: raise MathError('Model did not converge in 300 iterations')
    require(max(map(abs,beta)) < 50, 'Unbounded estimates: possible separation or non-identifiability')
    _, hess = derivatives(f,beta,True)
    require(min(float(v) for v in mp.eigsy(mp.matrix(hess),eigvals_only=True)) > 1e-8, 'Model information is singular; estimates are not identifiable')
    return beta, inverse(hess), value, iteration+1


def inference(beta, covariance, names, ratio=False):
    rows = []
    for i,b in enumerate(beta):
        se = math.sqrt(max(0,float(covariance[i,i])))
        row = {'term':names[i], 'estimate':b, 'SE':se, 'p':normal_p(b/se) if se else None, 'CI95':[b-1.95996398454*se,b+1.95996398454*se] if se else None}
        if ratio: row['exp(coef)'] = math.exp(b) if b<709.782712893384 else math.inf
        rows.append(row)
    return rows


def groups(a): return [vector(v,2) for v in a]


def oneway(g):
    require(len(g) >= 2, 'Enter at least two groups')
    n = sum(map(len,g)); overall = mean(sum(g,[]))
    between = sum(len(x)*(mean(x)-overall)**2 for x in g)
    within = sum(sum((v-mean(x))**2 for v in x) for x in g)
    require(within > 0, 'Within-group variation is required')
    f = (between/(len(g)-1))/(within/(n-len(g)))
    return {'F':f,'df1':len(g)-1,'df2':n-len(g),'p':float(_f_sf(f,len(g)-1,n-len(g))), 'eta2':between/(between+within)}


def survival(rows):
    require(all(r[0] >= 0 and r[1] in (0,1) for r in rows), 'Survival rows: nonnegative time, event 0/1, then predictors')


def regression_data(rows):
    x = [[1.0]+r[:-1] for r in rows]; y = [r[-1] for r in rows]
    require(len(x) > len(x[0]), 'More observations than coefficients are required')
    require(s.Matrix(x).rank() == len(x[0]), 'Predictors are collinear')
    return x,y


def standardized_design(x):
    """Center/scale predictors during fitting and transform inference back."""
    p=len(x[0]); centers=[mean(c) for c in zip(*x)][1:]; scales=[math.sqrt(variance(c)) for c in list(zip(*x))[1:]]
    require(all(v>0 for v in scales),'Predictors must vary')
    design=[[1.0]+[(v-centers[j])/scales[j] for j,v in enumerate(r[1:])] for r in x]
    transform=mp.eye(p)
    for j in range(1,p): transform[0,j]=-centers[j-1]/scales[j-1]; transform[j,j]=1/scales[j-1]
    return design,transform,centers,scales


def softplus(z): return max(z,0)+math.log1p(math.exp(-abs(z)))


def logistic(z): return math.exp(-softplus(-z))


def model(rows, mode):
    x,y = regression_data(rows); p = len(x[0]); names = ['Intercept']+['x'+str(i) for i in range(1,p)]
    x,transform,centers,scales=standardized_design(x)
    if mode in ('poissonreg','nbreg'):
        require(all(v >= 0 and v.is_integer() for v in y) and sum(y)>0, 'Response must be nonnegative integer counts with at least one event')
        start = [math.log(mean(y))]+[0.0]*(p-1)+([0.0] if mode=='nbreg' else [])
        def objective(b):
            try:
                total = 0.0
                r = math.exp(-b[-1]) if mode=='nbreg' else None
                for row,v in zip(x,y):
                    z = dot(row,b[:p]); mu = math.exp(z)
                    total += (mu-v*z+math.lgamma(v+1) if r is None else
                              math.lgamma(r)-math.lgamma(v+r)+math.lgamma(v+1)+r*math.log1p(mu/r)+v*(math.log(r+mu)-z))
                return total
            except (OverflowError,ValueError): return math.inf
        b,cov,ll,it = optimize(objective,start)
        coefficients=list(map(float,transform*mp.matrix(b[:p])))
        coefficient_cov=transform*cov[:p,:p]*transform.T
        result = {'coefficients':inference(coefficients,coefficient_cov,names,True),'log likelihood':-ll,'AIC':2*len(b)+2*ll,'iterations':it}
        if mode=='nbreg': result['dispersion alpha (NB2)'] = math.exp(b[-1])
        return result
    categories = sorted(set(y)); k = len(categories)
    require(2 <= k <= 10, 'Enter 2 to 10 numeric response categories')
    labels = [categories.index(v) for v in y]
    if mode == 'multinomial':
        def objective(b):
            total = 0.0
            for row,c in zip(x,labels):
                z = [0.0]+[dot(row,b[j*p:(j+1)*p]) for j in range(k-1)]
                top = max(z); total += top+math.log(sum(math.exp(v-top) for v in z))-z[c]
            return total
        b,cov,ll,it = optimize(objective,[0.0]*((k-1)*p))
        margins=[]
        for row,c in zip(x,labels):
            logits=[0.0]+[dot(row,b[j*p:(j+1)*p]) for j in range(k-1)]
            margins.append(logits[c]-max(v for j,v in enumerate(logits) if j!=c))
        require(not (min(margins)>=-1e-8 and max(margins)>1e-8), 'Complete or quasi separation: multinomial MLE is not finite')
        terms = [f'{categories[j+1]} vs {categories[0]}: {name}' for j in range(k-1) for name in names]
        full=mp.zeros(len(b))
        for block in range(k-1):
            for i in range(p):
                for j in range(p): full[block*p+i,block*p+j]=transform[i,j]
        coefficients=list(map(float,full*mp.matrix(b)))
        return {'reference category':categories[0], 'coefficients':inference(coefficients,full*cov*full.T,terms,True),'log likelihood':-ll,'AIC':2*len(b)+2*ll,'iterations':it}
    # Ordered cumulative logit: P(Y<=j)=logistic(cut_j-X beta), no intercept.
    x = [row[1:] for row in x]; p -= 1
    def cuts(b):
        result = [b[p]]
        for z in b[p+1:]: result.append(result[-1]+math.exp(z))
        return result
    def objective(b):
        try:
            threshold = cuts(b); total = 0.0
            for row,c in zip(x,labels):
                z = dot(row,b[:p]); lo = logistic(threshold[c-1]-z) if c else 0
                hi = logistic(threshold[c]-z) if c<k-1 else 1
                if hi <= lo: return math.inf
                total -= math.log(hi-lo)
            return total
        except OverflowError: return math.inf
    b,cov,ll,it = optimize(objective,[0.0]*p+[-1.0]+[0.0]*(k-2))
    threshold=cuts(b)
    margins=[min((dot(r,b[:p])-threshold[c-1] if c else math.inf),(threshold[c]-dot(r,b[:p]) if c<k-1 else math.inf)) for r,c in zip(x,labels)]
    require(not (min(margins)>=-1e-8 and max(margins)>1e-8),'Complete or quasi separation: ordinal MLE is not finite')
    coefficients=[v/scale for v,scale in zip(b[:p],scales)]; slope_cov=mp.matrix([[cov[i,j]/(scales[i]*scales[j]) for j in range(p)] for i in range(p)])
    return {'ordered categories':categories,'coefficients':inference(coefficients,slope_cov,names[1:],True),'cutpoints':[v+dot(coefficients,centers) for v in threshold],'log likelihood':-ll,'AIC':2*len(b)+2*ll,'iterations':it}


def advanced(engine, name, a):
    """Entry point shared by calculator expressions and Python catalog."""
    engine.note = 'Numerical statistics use binary64 precision.'
    arities = {'padjust':(1,3),'cohend':(2,3),'eta2':(2,20),'levene':(2,20),'bartlett':(2,20),'mcnemar':(1,2),
               'kaplanmeier':(1,2),'logrank':(2,2),'cox':(1,1),'repeatedanova':(1,1),'mixedmodel':(1,1),'gee':(1,2),
               'multinomial':(1,1),'ordinal':(1,1),'poissonreg':(1,1),'nbreg':(1,1),'bootstrapci':(1,5),
               'testpower':(2,4),'samplesize':(1,4),'kstest':(2,4),'crossvalidate':(1,3),'pca':(1,3),'kmeans':(2,3),'impute':(1,2)}
    low,high = arities[name]; require(low <= len(a) <= high, name+' argument count mismatch')
    with mp.workdps(25):
        result = calculate(engine,name,a)
    return convert(result)


def calculate(engine,name,a):
    if name == 'padjust':
        vals = vector(a[0]); method = option(a,1,'holm'); alpha = number(a[2]) if len(a)>2 else .05
        require(all(0 <= p <= 1 for p in vals) and 0<alpha<1, 'p values must lie in [0,1]; alpha in (0,1)')
        require(method in ('bonferroni','holm','fdr','bh','by'), 'Use bonferroni, holm, fdr (BH), or by')
        n = len(vals); order = sorted(range(n),key=vals.__getitem__); adj = [0.0]*n
        if method=='bonferroni': adj = [min(1,n*p) for p in vals]
        elif method=='holm':
            running = 0
            for rank,i in enumerate(order): running=max(running,min(1,(n-rank)*vals[i])); adj[i]=running
        else:
            running = 1; factor = sum(1/i for i in range(1,n+1)) if method=='by' else 1
            for rank in range(n-1,-1,-1):
                i=order[rank]; running=min(running,vals[i]*n*factor/(rank+1)); adj[i]=running
        engine.note += ' FDR uses Benjamini–Hochberg (BH); BY supports arbitrary dependence.'
        return {'raw p':vals,'adjusted p':adj,'reject (1=yes)': [int(p<=alpha) for p in adj], 'alpha':alpha,'method':method}
    if name=='cohend':
        x,y = vector(a[0],2),vector(a[1],2); paired = option(a,2,'independent')=='paired'
        require(option(a,2,'independent') in ('independent','paired'), 'Use independent or paired')
        if paired:
            require(len(x)==len(y), 'Paired samples require equal lengths')
            dif = [u-v for u,v in zip(x,y)]; sd = math.sqrt(variance(dif)); dmean = mean(dif); df=len(x)-1
        else:
            df=len(x)+len(y)-2; sd=math.sqrt(((len(x)-1)*variance(x)+(len(y)-1)*variance(y))/df); dmean=mean(x)-mean(y)
        require(sd>0, 'Effect size requires variation')
        d=dmean/sd
        return {'Cohen dz' if paired else 'Cohen d':d,'Hedges g (approximate)':d*(1-3/(4*df-1)), 'mean difference':dmean}
    if name=='eta2':
        g=groups(a); all_values=sum(g,[]); grand=mean(all_values)
        between=sum(len(x)*(mean(x)-grand)**2 for x in g); total=sum((v-grand)**2 for v in all_values)
        require(total>0,'Eta squared requires variation')
        return {'eta2':between/total}
    if name=='levene':
        g=groups(a); engine.note += ' Levene with median centers (Brown–Forsythe).'
        return oneway([[abs(v-statistics.median(x)) for v in x] for x in g])
    if name=='bartlett':
        g=groups(a); ns=[len(x) for x in g]; vs=[variance(x) for x in g]
        require(min(vs)>0, 'Each group needs positive variance')
        df=sum(ns)-len(g); pooled=sum((n-1)*v for n,v in zip(ns,vs))/df
        chi=(df*math.log(pooled)-sum((n-1)*math.log(v) for n,v in zip(ns,vs)))/(1+(sum(1/(n-1) for n in ns)-1/df)/(3*(len(g)-1)))
        return {'chi2':max(0,chi),'df':len(g)-1,'p':float(_chisq_sf(max(0,chi),len(g)-1))}
    if name=='mcnemar':
        rows=table(a[0],2,2); require(len(rows)==2 and len(rows[0])==2 and all(v>=0 and v.is_integer() for r in rows for v in r), 'Enter a 2×2 table of paired nonnegative integer counts')
        b,c=rows[0][1],rows[1][0]; n=int(b+c); method=option(a,1,'exact')
        require(method in ('exact','corrected','asymptotic'),'Use exact, corrected, or asymptotic')
        chi=(max(0,abs(b-c)-(1 if method=='corrected' else 0)))**2/n if n else 0
        p=min(1,float(2*sum(mp.binomial(n,j) for j in range(int(min(b,c))+1))/mp.mpf(2)**n)) if n and method=='exact' else float(_chisq_sf(chi,1))
        engine.note += ' McNemar tests paired counts; exact two-sided binomial is the default.'
        return {'discordant pairs':n,'chi2':chi,'p':p,'method':method}
    if name=='kaplanmeier':
        rows=table(a[0],2,2); require(len(rows[0])==2,'Rows are [time,event]'); survival(rows)
        level=number(a[1]) if len(a)>1 else .95; require(0<level<1,'Confidence level must lie in (0,1)')
        z=statistics.NormalDist().inv_cdf((1+level)/2); prob=1; greenwood=0; curve=[]; median=None
        for time in sorted(set(r[0] for r in rows)):
            risk=sum(r[0]>=time for r in rows); events=sum(r[0]==time and r[1]==1 for r in rows); cens=sum(r[0]==time and r[1]==0 for r in rows)
            prob *= 1-events/risk
            if events and risk>events: greenwood+=events/(risk*(risk-events))
            lo=hi=prob
            if 0<prob<1:
                center=math.log(-math.log(prob)); se=math.sqrt(greenwood)/abs(math.log(prob)); lo=math.exp(-math.exp(center+z*se)); hi=math.exp(-math.exp(center-z*se))
            curve.append([time,risk,events,cens,prob,lo,hi])
            if prob<=.5 and median is None: median=time
        engine.note += ' Kaplan–Meier: events precede censoring at tied times; Greenwood log-log CI. Columns: time, at risk, events, censored, survival, lower, upper.'
        return {'survival table':curve,'median survival':median,'confidence level':level}
    if name=='logrank':
        x,y=table(a[0],2,2),table(a[1],2,2); survival(x); survival(y)
        require(len(x[0])==len(y[0])==2,'Rows are [time,event]')
        observed=expected=var=0.0
        for time in sorted(set(r[0] for r in x+y if r[1])):
            n1=sum(r[0]>=time for r in x); n2=sum(r[0]>=time for r in y); n=n1+n2
            d1=sum(r[0]==time and r[1] for r in x); d2=sum(r[0]==time and r[1] for r in y); d=d1+d2
            observed+=d1; expected+=d*n1/n
            if n>1: var+=n1*n2*d*(n-d)/(n*n*(n-1))
        require(var>0,'Log-rank needs informative events in both risk sets')
        chi=(observed-expected)**2/var
        return {'chi2':chi,'df':1,'p':float(_chisq_sf(chi,1)),'observed group 1':observed,'expected group 1':expected}
    if name=='cox':
        rows=table(a[0],3,3); survival(rows); x=[r[2:] for r in rows]; p=len(x[0]); require(sum(r[1] for r in rows)>p,'More events than predictors are required')
        design,_,_,scales=standardized_design([[1]+r for r in x]); x=[r[1:] for r in design]
        times=sorted(set(r[0] for r in rows if r[1]))
        def objective(b):
            z=[dot(row,b) for row in x]; total=0
            for time in times:
                events=[i for i,r in enumerate(rows) if r[0]==time and r[1]]; risk=[z[i] for i,r in enumerate(rows) if r[0]>=time]; top=max(risk)
                total+=len(events)*(top+math.log(sum(math.exp(v-top) for v in risk)))-sum(z[i] for i in events)
            return total
        b,cov,ll,it=optimize(objective,[0.0]*p)
        b=[v/scale for v,scale in zip(b,scales)]; cov=mp.matrix([[cov[i,j]/(scales[i]*scales[j]) for j in range(p)] for i in range(p)])
        engine.note += ' Cox proportional hazards, Breslow ties; no intercept, no left truncation. exp(coef) is hazard ratio.'
        return {'coefficients':inference(b,cov,['x'+str(i+1) for i in range(p)],True),'partial log likelihood':-ll,'iterations':it}
    if name=='repeatedanova':
        rows=table(a[0],2,2); n=len(rows); k=len(rows[0]); overall=mean(sum(rows,[])); cols=list(zip(*rows))
        total=sum((v-overall)**2 for r in rows for v in r); sscondition=n*sum((mean(c)-overall)**2 for c in cols); sssubject=k*sum((mean(r)-overall)**2 for r in rows); error=total-sscondition-sssubject
        require(error>1e-12, 'Repeated-measures residual variation is required')
        df1=k-1; df2=(n-1)*(k-1); f=(sscondition/df1)/(error/df2)
        # Greenhouse–Geisser epsilon from double-centered covariance.
        cov=[[sum((rows[t][i]-mean(cols[i]))*(rows[t][j]-mean(cols[j])) for t in range(n))/(n-1) for j in range(k)] for i in range(k)]
        cm=[mean(r) for r in cov]; gm=mean(cm); centered=[[cov[i][j]-cm[i]-cm[j]+gm for j in range(k)] for i in range(k)]
        epsilon=min(1,max(1/df1,sum(centered[i][i] for i in range(k))**2/(df1*sum(v*v for r in centered for v in r))))
        engine.note += ' Balanced one-factor repeated measures: rows=subjects, columns=conditions. Includes Greenhouse–Geisser correction.'
        return {'F':f,'df1':df1,'df2':df2,'p':float(_f_sf(f,df1,df2)),'partial eta2':sscondition/(sscondition+error),'GG epsilon':epsilon,'GG p':float(_f_sf(f,df1*epsilon,df2*epsilon))}
    if name in ('multinomial','ordinal','poissonreg','nbreg'):
        engine.note += {'ordinal':' Proportional-odds cumulative logit; ascending numeric categories.', 'multinomial':' Multinomial logit; smallest category is reference.', 'poissonreg':' Poisson log-link regression; exp(coef) is incidence rate ratio.', 'nbreg':' Negative binomial NB2 log-link; dispersion alpha is jointly estimated.'}[name]+' Rows: predictors then response. Wald 95% CI.'
        return model(table(a[0],3,2),name)
    if name in ('mixedmodel','gee'): return clustered(engine,name,a)
    if name in ('bootstrapci','testpower','samplesize','kstest'): return resampling(engine,name,a)
    if name in ('crossvalidate','pca','kmeans','impute'): return learning(engine,name,a)
    raise MathError('Unknown advanced analysis')


def clustered(engine,name,a):
    rows=table(a[0],4,3); ids=sorted(set(r[0] for r in rows)); clusters=[[i for i,r in enumerate(rows) if r[0]==id_] for id_ in ids]
    require(len(clusters)>=3,'At least three subject/cluster IDs are required')
    x,y=regression_data([r[1:] for r in rows]); n=len(y); p=len(x[0]); X=mp.matrix(x); Y=mp.matrix(y)
    if name=='mixedmodel':
        require(n<=300,'Random-intercept model limit: 300 observations')
        require(any(len(c)>1 for c in clusters),'Random intercept requires repeated subjects')
        def fit(ratio):
            W=mp.eye(n); logdet=0.0
            for c in clusters:
                factor=ratio/(1+len(c)*ratio); logdet+=math.log1p(len(c)*ratio)
                for i in c:
                    for j in c: W[i,j]-=factor
            cov=inverse(X.T*W*X); b=cov*X.T*W*Y; residual=Y-X*b; rss=float((residual.T*W*residual)[0]); require(rss>1e-12,'Mixed model requires residual variation')
            objective=n*math.log(rss/n)+logdet
            return objective,b,cov,rss/n
        # Search variance ratio including exact zero boundary; avoids n×n inversion.
        def objective(z): return fit(math.exp(z))[0]
        lo,hi=-16.0,16.0; golden=(math.sqrt(5)-1)/2
        u=hi-golden*(hi-lo); v=lo+golden*(hi-lo); fu,fv=objective(u),objective(v)
        for _ in range(60):
            if fu<fv: hi,v,fv=v,u,fu; u=hi-golden*(hi-lo); fu=objective(u)
            else: lo,u,fu=u,v,fv; v=lo+golden*(hi-lo); fv=objective(v)
        ratio=math.exp((lo+hi)/2); chosen=fit(ratio); zero=fit(0)
        if zero[0]<=chosen[0]: ratio=0; chosen=zero
        obj,b,cov,sigma=chosen
        engine.note += ' Gaussian random-intercept mixed model, maximum likelihood (ML), Wald inference. Rows: subject ID, predictors, response.'
        return {'coefficients':inference(list(map(float,b)),cov*sigma,['Intercept']+['x'+str(i) for i in range(1,p)]),'residual variance':sigma,'random intercept variance':ratio*sigma,'ICC':ratio/(1+ratio),'subjects':len(ids)}
    family=option(a,1,'gaussian'); require(family in ('gaussian','binomial','poisson'),'GEE family: gaussian, binomial, or poisson')
    x,transform,_,_=standardized_design(x); X=mp.matrix(x)
    if family=='binomial': require(all(v in (0,1) for v in y),'Binomial GEE response must be 0/1')
    if family=='poisson': require(all(v>=0 and v.is_integer() for v in y),'Poisson GEE response must be integer counts')
    if family=='gaussian': b=list(map(float,inverse(X.T*X)*X.T*Y)); mu=[dot(r,b) for r in x]; weights=[1.0]*n
    else:
        def objective(b):
            try: return sum(softplus(dot(r,b))-v*dot(r,b) if family=='binomial' else math.exp(dot(r,b))-v*dot(r,b) for r,v in zip(x,y))
            except OverflowError: return math.inf
        b,_,_,_=optimize(objective,[0.0]*p); mu=[logistic(dot(r,b)) if family=='binomial' else math.exp(dot(r,b)) for r in x]; weights=[v*(1-v) if family=='binomial' else v for v in mu]
        if family=='binomial':
            margins=[(2*v-1)*dot(r,b) for r,v in zip(x,y)]
            require(not (min(margins)>=-1e-8 and max(margins)>1e-8),'Complete or quasi separation: binomial GEE estimates are not finite')
    bread=inverse([[sum(weights[t]*x[t][i]*x[t][j] for t in range(n)) for j in range(p)] for i in range(p)]); meat=mp.zeros(p)
    for c in clusters:
        score=mp.matrix([sum(x[t][i]*(y[t]-mu[t]) for t in c) for i in range(p)]); meat+=score*score.T
    cov=bread*meat*bread
    b=list(map(float,transform*mp.matrix(b))); cov=transform*cov*transform.T
    engine.note += ' GEE: independent working correlation, cluster sandwich covariance, asymptotic Wald inference. Rows: cluster ID, predictors, response. Zero robust SE leaves p/CI unavailable.'
    return {'coefficients':inference(b,cov,['Intercept']+['x'+str(i) for i in range(1,p)],family!='gaussian'),'clusters':len(ids),'family':family}


def resampling(engine,name,a):
    if name=='bootstrapci':
        x=vector(a[0],2); statistic=option(a,1,'mean'); level=number(a[2]) if len(a)>2 else .95; count=integer(a[3],100,20000) if len(a)>3 else 2000; seed=integer(a[4],0,2**32-1) if len(a)>4 else 0
        require(statistic in ('mean','median','stdev') and 0<level<1,'Statistic: mean, median, stdev; confidence level in (0,1)')
        require(len(x)*count<=2000000,'Bootstrap limit: two million sampled values')
        f={'mean':mean,'median':statistics.median,'stdev':statistics.stdev}[statistic]; rng=random.Random(seed); draws=sorted(f(rng.choices(x,k=len(x))) for _ in range(count))
        def quantile(q): pos=q*(count-1); j=int(pos); return draws[j]+(pos-j)*(draws[min(j+1,count-1)]-draws[j])
        engine.note += ' Nonparametric percentile bootstrap CI; IID observations, deterministic seed.'
        return {'estimate':f(x),'lower':quantile((1-level)/2),'upper':quantile((1+level)/2),'confidence level':level,'resamples':count,'seed':seed}
    if name in ('testpower','samplesize'):
        effect=abs(number(a[0])); alpha=number(a[2]) if len(a)>2 else .05; kind=option(a,3,'independent')
        require(effect>0 and 0<alpha<1 and kind in ('independent','paired','onesample'),'Positive Cohen d, alpha in (0,1), and independent/paired/onesample required')
        z=statistics.NormalDist().inv_cdf(1-alpha/2)
        def power(n):
            shift=effect*math.sqrt(n/(2 if kind=='independent' else 1)); return float(_normal_sf(z-shift)+_normal_sf(z+shift))
        engine.note += ' Two-sided normal-approximation power for standardized mean differences; equal independent groups. n is per group or number of pairs. This is not exact noncentral-t power.'
        if name=='testpower':
            n=integer(a[1],2,10000000); return {'power':power(n),'n per group / pairs':n,'alpha':alpha}
        target=number(a[1]) if len(a)>1 else .8
        # samplesize(d,power,alpha,kind), unlike testpower(d,n,alpha,kind).
        alpha=number(a[2]) if len(a)>2 else .05; kind=option(a,3,'independent'); require(0<target<1 and 0<alpha<1 and kind in ('independent','paired','onesample'),'Invalid target power, alpha or design')
        z=statistics.NormalDist().inv_cdf(1-alpha/2); low,high=2,10000000; require(power(high)>=target,'Required sample size exceeds limit')
        while low<high:
            mid=(low+high)//2
            if power(mid)>=target: high=mid
            else: low=mid+1
        return {'n per group / pairs':low,'total n':low*2 if kind=='independent' else low,'achieved power':power(low),'target power':target,'alpha':alpha}
    x=sorted(vector(a[0],2))
    if isinstance(a[1],(list,tuple)):
        require(len(a)==2,'Two-sample KS takes two lists')
        y=sorted(vector(a[1],2)); n=len(x); m=len(y); d=max(abs(sum(u<=v for u in x)/n-sum(u<=v for u in y)/m) for v in sorted(set(x+y)))
        # Exact lattice probability for continuous data; strict interior counts complement >= D.
        if len(set(x+y))==n+m and n*m<=250000:
            threshold=round(d*n*m); ways=[0]*(m+1)
            for i in range(n+1):
                for j in range(m+1):
                    if abs(i*m-j*n)>=threshold: ways[j]=0
                    elif i==j==0: ways[j]=1
                    else: ways[j]=(ways[j] if i else 0)+(ways[j-1] if j else 0)
            paths=math.comb(n+m,n)
            p=float(mp.mpf(paths-ways[m])/paths); method='exact continuous two-sample'
        else:
            p=ks_asymptotic(d,math.sqrt(n*m/(n+m))); method='asymptotic; ties invalidate continuous-null calibration'
    else:
        require(str(a[1]) in ('normal','uniform'),'One-sample KS distribution: normal or uniform')
        loc=number(a[2]) if len(a)>2 else 0; scale=number(a[3]) if len(a)>3 else 1; require(scale>0,'Scale must be positive')
        cdf=(lambda v: statistics.NormalDist(loc,scale).cdf(v)) if str(a[1])=='normal' else lambda v: min(1,max(0,(v-loc)/scale))
        n=len(x); d=max(max((i+1)/n-cdf(v),cdf(v)-i/n) for i,v in enumerate(x)); p=ks_asymptotic(d,math.sqrt(n)); method='one-sample asymptotic, specified parameters'
        engine.note += ' Specify distribution parameters independently of data; fitted parameters need a different calibration.'
    engine.note += ' KS assumes continuous distributions. '+method+'.'
    return {'D':d,'p':max(0,min(1,p)),'method':method}


def ks_asymptotic(d, effective):
    if d<=0: return 1.0
    z=d*(effective+.12+.11/effective)
    if z<1.18:
        return 1-math.sqrt(2*math.pi)/z*sum(math.exp(-(2*j-1)**2*math.pi**2/(8*z*z)) for j in range(1,50))
    return 2*sum((-1)**(j-1)*math.exp(-2*j*j*z*z) for j in range(1,50))


def learning(engine,name,a):
    if name=='impute':
        require(isinstance(a[0],list) and len(a[0])>=2 and all(isinstance(r,list) for r in a[0]),'Enter a rectangular table; use NA for missing cells')
        rows=a[0]; require(all(len(r)==len(rows[0]) for r in rows) and len(rows[0])>0,'Enter a nonempty rectangular table')
        method=option(a,1,'mean'); require(method in ('mean','median','mode'),'Use mean, median, or mode')
        missing=lambda v: str(v) in ('NA','nan')
        fills=[]
        for col in zip(*rows):
            vals=[number(v) for v in col if not missing(v)]; require(vals,'Cannot impute a completely missing column')
            fills.append(mean(vals) if method=='mean' else statistics.median(vals) if method=='median' else statistics.multimode(vals)[0])
        engine.note += ' Single columnwise imputation; does not account for imputation uncertainty. NA denotes missing. Mode ties use first appearance.'
        return {'data':[[fills[i] if missing(v) else number(v) for i,v in enumerate(r)] for r in rows],'fill values':fills,'imputed cells':sum(missing(v) for r in rows for v in r)}
    rows=table(a[0]); n=len(rows); p=len(rows[0])
    if name=='crossvalidate':
        require(p>=2,'Rows: predictors then response'); k=integer(a[1],2,n) if len(a)>1 else min(5,n); seed=integer(a[2],0,2**32-1) if len(a)>2 else 0
        order=list(range(n)); random.Random(seed).shuffle(order); errors=[]; predictions=[0.0]*n
        for fold in range(k):
            test=order[fold::k]; train=[i for i in order if i not in test]; x,y=regression_data([rows[i] for i in train]); X=mp.matrix(x); b=inverse(X.T*X)*X.T*mp.matrix(y)
            for i in test: predictions[i]=float(dot([1]+rows[i][:-1],b))
            errors.append(mean([(rows[i][-1]-predictions[i])**2 for i in test]))
        engine.note += ' Shuffled k-fold ordinary least-squares regression. Fits use training rows only. Not suitable for grouped or time-series data.'
        return {'out-of-fold predictions':predictions,'fold MSE':errors,'MSE':mean([(r[-1]-pred)**2 for r,pred in zip(rows,predictions)]),'RMSE':math.sqrt(mean([(r[-1]-pred)**2 for r,pred in zip(rows,predictions)])),'folds':k,'seed':seed}
    centers=[mean(c) for c in zip(*rows)]
    if name=='pca':
        count=integer(a[1],1,p) if len(a)>1 else p; standard=integer(a[2],0,1) if len(a)>2 else 1; scales=[math.sqrt(variance(c)) if standard else 1 for c in zip(*rows)]
        require(min(scales)>0,'Standardized PCA requires nonconstant columns')
        X=mp.matrix([[(v-centers[j])/scales[j] for j,v in enumerate(r)] for r in rows]); vals,vecs=mp.eigsy(X.T*X/(n-1)); order=list(range(p-1,-1,-1)); total=sum(vals); require(total>0,'PCA requires variation')
        V=mp.matrix([[vecs[i,j] for j in order[:count]] for i in range(p)])
        for j in range(count):
            dominant=max(range(p),key=lambda i:abs(V[i,j]))
            if V[dominant,j]<0:
                for i in range(p): V[i,j]=-V[i,j]
        engine.note += ' PCA: covariance eigendecomposition, sample-SD standardization by default. Loadings rows=features, columns=components.'
        return {'eigenvalues':[max(0,float(vals[j])) for j in order[:count]],'explained variance ratio':[max(0,float(vals[j]/total)) for j in order[:count]],'loadings':V.tolist(),'scores':(X*V).tolist(),'centers':centers,'scales':scales}
    k=integer(a[1],1,n); seed=integer(a[2],0,2**32-1) if len(a)>2 else 0; rng=random.Random(seed); distinct=list(dict.fromkeys(tuple(r) for r in rows)); require(len(distinct)>=k,'k exceeds number of distinct observations')
    require(n*k*p<=1000000,'Clustering size limit exceeded')
    def distance(x,y): return sum((u-v)**2 for u,v in zip(x,y))
    best=None
    for restart in range(10):
        centroids=[list(rng.choice(distinct))]
        while len(centroids)<k:
            weights=[min(distance(r,c) for c in centroids) for r in rows]; target=rng.random()*sum(weights); cumulative=0
            for r,w in zip(rows,weights):
                cumulative+=w
                if cumulative>target: centroids.append(r[:]); break
        converged=False
        for iteration in range(200):
            labels=[min(range(k),key=lambda j:distance(r,centroids[j])) for r in rows]; new=[]
            for j in range(k):
                members=[r for r,l in zip(rows,labels) if l==j]
                new.append([mean(c) for c in zip(*members)] if members else rows[max(range(n),key=lambda i:distance(rows[i],centroids[labels[i]]))][:])
            if sum(distance(u,v) for u,v in zip(new,centroids))<1e-12: converged=True; centroids=new; break
            centroids=new
        if not converged: continue
        labels=[min(range(k),key=lambda j:distance(r,centroids[j])) for r in rows]; inertia=sum(distance(r,centroids[l]) for r,l in zip(rows,labels))
        if len(set(labels))<k: continue
        if best is None or inertia<best['inertia']: best={'labels (1-based)':[l+1 for l in labels],'centroids':centroids,'inertia':inertia,'iterations':iteration+1,'seed':seed}
    require(best is not None,'K-means did not converge')
    engine.note += ' K-means++ initialization, 10 restarts, Euclidean distance on raw columns; scale features before clustering if needed.'
    return best
