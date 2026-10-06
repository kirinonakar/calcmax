"""Portable advanced statistics (binary64 numerics; no native dependencies).

Models intentionally expose their specification in result notes: Efron/Breslow
Cox with optional left truncation and a Grambsch–Therneau PH check,
proportional odds and NB2, Gaussian random intercept/slope ML, and
working-correlation GEE. Count, multinomial, ordinal and Cox fits report
covariance from analytic scores and observed information.
"""
import math
import random
import statistics
import mpmath as mp
import sympy as s
from calc_shared import MathError, require
from calc_statistics import _chisq_sf, _f_sf, _normal_sf

FUNCTIONS = set('padjust cohend eta2 levene bartlett mcnemar kaplanmeier logrank cox survivalanalysis repeatedanova mixedmodel gee multinomial ordinal poissonreg nbreg bootstrapci testpower samplesize kstest crossvalidate pca kmeans impute'.split())


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


def newton(start, exact):
    """Damped Newton with an exact score and observed information.

    exact(beta) returns (objective, score, information). Poisson, multinomial
    logit, ordinal, NB2 and Cox partial-likelihood models use this so the
    estimate and the reported covariance come from analytic derivatives
    instead of differences.
    """
    beta = start[:]; n = len(beta)
    require(n <= 30, 'Model limit: 30 parameters')
    try: value, grad, info = exact(beta)
    except (OverflowError, ValueError): raise MathError('Model did not converge; check separation, scaling and identifiability')
    require(math.isfinite(value), 'Model did not converge; check separation, scaling and identifiability')
    for iteration in range(100):
        require(all(math.isfinite(v) for v in grad), 'Model did not converge; check separation, scaling and identifiability')
        scaled = inverse(info)*mp.matrix(grad)
        if max(map(abs, grad)) < 2e-5:
            # A vanishing score with positive information and a small
            # information-scaled step separates a stationary point from a
            # boundary estimate (separation, monotone Cox).
            require(min(float(v) for v in mp.eigsy(mp.matrix(info),eigvals_only=True)) > 1e-8, 'Model information is singular; possible separation or boundary estimate')
            if max(abs(float(v)) for v in scaled) < 1e-3: break
        direction = [-float(v) for v in scaled]
        step = 1.0
        for _ in range(40):
            candidate = [b+step*d for b, d in zip(beta, direction)]
            try: trial, trial_grad, trial_information = exact(candidate)
            except (OverflowError, ValueError): trial = math.inf
            if math.isfinite(trial) and trial <= value+1e-4*step*dot(grad, direction): break
            step *= .5
        else: raise MathError('Model did not converge; check separation, scaling and identifiability')
        value, grad, info = trial, trial_grad, trial_information
        beta = candidate
    else: raise MathError('Model did not converge in 100 iterations')
    require(max(map(abs, beta)) < 50, 'Unbounded estimates: possible separation or non-identifiability')
    require(min(float(v) for v in mp.eigsy(mp.matrix(info),eigvals_only=True)) > 1e-8, 'Model information is singular; estimates are not identifiable')
    return beta, inverse(info), value, iteration+1


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


def survival(rows, entry=-1):
    require(all(r[0] >= 0 and r[1] in (0,1) for r in rows), 'Survival rows: nonnegative time, event 0/1, then predictors')
    if entry >= 0:
        require(entry < len(rows[0]), 'Entry column is out of range')
        require(all(0 <= r[entry] < r[0] for r in rows), 'Entry time must be nonnegative and earlier than the exit time')


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
        if mode == 'poissonreg':
            def exact(b):
                # Log-link Poisson: the score and the observed information
                # (equal to the Fisher information for the canonical link) are
                # analytic, so standard errors come from X'WX, not differences.
                value = 0.0; score = [0.0]*p; information = [[0.0]*p for _ in range(p)]
                for row,v in zip(x,y):
                    z = dot(row,b); mu = math.exp(z)
                    value += mu-v*z+math.lgamma(v+1)
                    for j in range(p):
                        score[j] += row[j]*(mu-v)
                        for k in range(j,p): information[j][k] += row[j]*row[k]*mu
                for j in range(1,p):
                    for k in range(j): information[j][k] = information[k][j]
                return value,score,information
            b,cov,ll,it = newton(start,exact)
        else:
            def exact(b):
                # NB2 log-link in a log-dispersion coordinate. Counts are
                # integers, so the digamma differences in the score reduce to
                # finite reciprocal sums and the estimate and covariance come
                # from analytic derivatives.
                r = math.exp(-b[-1])
                if not r > 0: raise ValueError('Negative binomial dispersion underflowed')
                count = len(b)
                value = 0.0; score = [0.0]*count; information = [[0.0]*count for _ in range(count)]
                psi_r = psi1_r = None
                for row,v in zip(x,y):
                    z = dot(row,b[:p]); mu = math.exp(z)
                    value += math.lgamma(r)-math.lgamma(v+r)+math.lgamma(v+1)+r*math.log1p(mu/r)+v*(math.log(r+mu)-z)
                    if v <= 64:
                        harmonic = 0.0; squared = 0.0
                        for k in range(int(v)):
                            reciprocal = 1.0/(r+k); harmonic += reciprocal; squared += reciprocal*reciprocal
                    else:
                        if psi_r is None:
                            psi_r = float(mp.digamma(r)); psi1_r = float(mp.polygamma(1,r))
                        harmonic = float(mp.digamma(r+v))-psi_r
                        squared = psi1_r-float(mp.polygamma(1,r+v))
                    denominator = r+mu; share = r/denominator; log_ratio = math.log1p(mu/r)
                    score[-1] += r*(harmonic-log_ratio+(mu-v)/denominator)
                    information[-1][-1] += r*(log_ratio-harmonic+(v-mu)/denominator)+r*r*(squared-(mu/denominator)/r-(v-mu)/denominator**2)
                    for j in range(p):
                        score[j] += row[j]*(mu-v)*share
                        information[j][-1] += row[j]*share*mu*(v-mu)/denominator
                        for k in range(j,p): information[j][k] += row[j]*row[k]*share*mu*(r+v)/denominator
                for j in range(count):
                    for k in range(j): information[j][k] = information[k][j]
                return value,score,information
            b,cov,ll,it = newton(start,exact)
        coefficients=list(map(float,transform*mp.matrix(b[:p])))
        coefficient_cov=transform*cov[:p,:p]*transform.T
        result = {'coefficients':inference(coefficients,coefficient_cov,names,True),'log likelihood':-ll,'AIC':2*len(b)+2*ll,'iterations':it}
        if mode=='nbreg': result['dispersion alpha (NB2)'] = math.exp(b[-1])
        return result
    categories = sorted(set(y)); k = len(categories)
    require(2 <= k <= 10, 'Enter 2 to 10 numeric response categories')
    labels = [categories.index(v) for v in y]
    if mode == 'multinomial':
        def exact(b):
            # Softmax cross-entropy: the score and block observed information
            # are analytic, so standard errors avoid finite differences.
            value = 0.0; score = [0.0]*len(b); information = [[0.0]*len(b) for _ in b]
            for row,c in zip(x,labels):
                z = [0.0]+[dot(row,b[j*p:(j+1)*p]) for j in range(k-1)]
                top = max(z); weights = [math.exp(v-top) for v in z]; total = math.fsum(weights)
                value += top+math.log(total)-z[c]
                probabilities = [v/total for v in weights]
                for j in range(k-1):
                    delta = probabilities[j+1]-(1.0 if c==j+1 else 0.0)
                    for t in range(p): score[j*p+t] += row[t]*delta
                    for l in range(k-1):
                        factor = probabilities[j+1]*(1-probabilities[j+1]) if j==l else -probabilities[j+1]*probabilities[l+1]
                        if factor == 0.0: continue
                        for a in range(p):
                            for d in range(p): information[j*p+a][l*p+d] += factor*row[a]*row[d]
            return value,score,information
        b,cov,ll,it = newton([0.0]*((k-1)*p),exact)
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
    def exact(b):
        # Proportional-odds cumulative logit with log-increment thresholds.
        # The score and observed information are analytic, including the
        # chain rule from the threshold increments, so inference avoids
        # finite differences.
        threshold = cuts(b); count = len(b); levels = k-1
        value = 0.0; score = [0.0]*count; information = [[0.0]*count for _ in range(count)]
        slope_information = [[0.0]*p for _ in range(p)]; cross_information = [[0.0]*levels for _ in range(p)]
        threshold_information = [[0.0]*levels for _ in range(levels)]
        for row,c in zip(x,labels):
            z = dot(row,b[:p])
            lower = logistic(threshold[c-1]-z) if c else 0.0
            upper = logistic(threshold[c]-z) if c<k-1 else 1.0
            probability = upper-lower
            if not probability > 0: raise ValueError('Ordinal cell probability underflowed')
            value -= math.log(probability); inverse_probability = 1/probability
            low_density = lower*(1-lower); high_density = upper*(1-upper)
            low_score = low_density*inverse_probability; high_score = -high_density*inverse_probability
            low_curvature = (low_density*(1-2*lower)*probability+low_density*low_density)*inverse_probability*inverse_probability
            high_curvature = (high_density*high_density-high_density*(1-2*upper)*probability)*inverse_probability*inverse_probability
            mixed_curvature = -low_density*high_density*inverse_probability*inverse_probability
            linear = -low_score-high_score
            for j in range(p):
                score[j] += linear*row[j]
                for m in range(j,p): slope_information[j][m] += (low_curvature+2*mixed_curvature+high_curvature)*row[j]*row[m]
            if c:
                score[p+c-1] += low_score; threshold_information[c-1][c-1] += low_curvature
                for j in range(p): cross_information[j][c-1] -= row[j]*(low_curvature+mixed_curvature)
            if c<k-1:
                score[p+c] += high_score; threshold_information[c][c] += high_curvature
                for j in range(p): cross_information[j][c] -= row[j]*(mixed_curvature+high_curvature)
            if 0<c<k-1:
                threshold_information[c-1][c] += mixed_curvature; threshold_information[c][c-1] += mixed_curvature
        factor = [1.0]+[math.exp(b[p+m]) for m in range(1,levels)]
        for m in range(levels):
            score[p+m] = factor[m]*math.fsum(score[p+j] for j in range(m,levels))
        for m in range(levels):
            for l in range(levels):
                total = math.fsum(threshold_information[j][n] for j in range(m,levels) for n in range(l,levels))
                information[p+m][p+l] = factor[m]*factor[l]*total+(score[p+m] if m==l and m else 0.0)
        for i in range(p):
            for l in range(levels):
                information[i][p+l] = factor[l]*math.fsum(cross_information[i][j] for j in range(l,levels))
        for j in range(p):
            for m in range(j,p): information[j][m] = slope_information[j][m]
        for j in range(count):
            for m in range(j): information[j][m] = information[m][j]
        return value,score,information
    b,cov,ll,it = newton([0.0]*p+[-1.0]+[0.0]*(k-2),exact)
    threshold=cuts(b)
    margins=[min((dot(r,b[:p])-threshold[c-1] if c else math.inf),(threshold[c]-dot(r,b[:p]) if c<k-1 else math.inf)) for r,c in zip(x,labels)]
    require(not (min(margins)>=-1e-8 and max(margins)>1e-8),'Complete or quasi separation: ordinal MLE is not finite')
    coefficients=[v/scale for v,scale in zip(b[:p],scales)]; slope_cov=mp.matrix([[cov[i,j]/(scales[i]*scales[j]) for j in range(p)] for i in range(p)])
    return {'ordered categories':categories,'coefficients':inference(coefficients,slope_cov,names[1:],True),'cutpoints':[v+dot(coefficients,centers) for v in threshold],'log likelihood':-ll,'AIC':2*len(b)+2*ll,'iterations':it}


def advanced(engine, name, a):
    """Entry point shared by calculator expressions and Python catalog."""
    engine.note = 'Numerical statistics use binary64 precision.'
    arities = {'padjust':(1,3),'cohend':(2,3),'eta2':(2,20),'levene':(2,20),'bartlett':(2,20),'mcnemar':(1,2),
               'kaplanmeier':(1,3),'logrank':(2,2),'cox':(1,4),'survivalanalysis':(1,5),'repeatedanova':(1,2),'mixedmodel':(1,3),'gee':(1,4),
               'multinomial':(1,1),'ordinal':(1,1),'poissonreg':(1,1),'nbreg':(1,1),'bootstrapci':(1,5),
               'testpower':(2,5),'samplesize':(1,5),'kstest':(2,4),'crossvalidate':(1,6),'pca':(1,3),'kmeans':(2,3),'impute':(1,3)}
    low,high = arities[name]; require(low <= len(a) <= high, name+' argument count mismatch')
    with mp.workdps(25):
        result = calculate(engine,name,a)
    return convert(result)


def calculate(engine,name,a):
    if name=='survivalanalysis': return survival_analysis(engine,a)
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
        rows=table(a[0],1,2); level=number(a[1]) if len(a)>1 else .95; require(0<level<1,'Confidence level must lie in (0,1)')
        entry=integer(a[2],-1,19) if len(a)>2 else -1
        require(entry>=0 or len(rows[0])==2,'Rows are [time,event]')
        survival(rows,entry); starts=[r[entry] for r in rows] if entry>=0 else None
        z=statistics.NormalDist().inv_cdf((1+level)/2); prob=1; greenwood=0; curve=[]; median=None
        for time in sorted(set(r[0] for r in rows)):
            risk=sum(r[0]>=time and (starts is None or starts[i]<time) for i,r in enumerate(rows)); events=sum(r[0]==time and r[1]==1 for r in rows); cens=sum(r[0]==time and r[1]==0 for r in rows)
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
        rows=table(a[0],3,3)
        ties=option(a,1,'efron'); require(ties in ('breslow','efron'),'Cox ties: breslow or efron')
        entry=integer(a[2],-1,19) if len(a)>2 else -1
        check=integer(a[3],0,1) if len(a)>3 else 1
        survival(rows,entry)
        columns=[j for j in range(len(rows[0])) if j not in (0,1) and j!=entry]
        require(columns,'Choose at least one predictor')
        x=[[r[j] for j in columns] for r in rows]; p=len(x[0]); require(sum(r[1] for r in rows)>p,'More events than predictors are required')
        ends=[r[0] for r in rows]; observed=[r[1] for r in rows]; starts=[r[entry] for r in rows] if entry>=0 else None
        design,_,_,scales=standardized_design([[1]+r for r in x]); x=[r[1:] for r in design]
        times=sorted(set(t for t,e in zip(ends,observed) if e))
        def risk_at(time): return [i for i in range(len(rows)) if ends[i]>=time and (starts is None or starts[i]<time)]
        def accumulate(beta,transform=None):
            """Partial log-likelihood derivatives at beta.

            Returns the negative partial log likelihood, the score and the
            observed information. A time transform adds the Grambsch–Therneau
            blocks of a time-varying coefficient: the weighted score residual
            vector and the g- and g^2-weighted information terms.
            """
            z=[dot(row,beta) for row in x]
            value=0.0; score=[0.0]*p; information=[[0.0]*p for _ in range(p)]
            if transform is not None: residual=[0.0]*p; cross=[[0.0]*p for _ in range(p)]; square=[[0.0]*p for _ in range(p)]
            for time in times:
                risk=risk_at(time); events=[i for i in risk if ends[i]==time and observed[i]]; top=max(z[i] for i in risk)
                members=[(math.exp(z[i]-top),i) for i in risk]; selected=[(math.exp(z[i]-top),i) for i in events]
                count=len(selected); factor=count if ties=='breslow' else 1
                s0=math.fsum(w for w,_ in members); s1=[math.fsum(w*x[i][j] for w,i in members) for j in range(p)]; s2=[[math.fsum(w*x[i][j]*x[i][k] for w,i in members) for k in range(p)] for j in range(p)]
                e0=math.fsum(w for w,_ in selected); e1=[math.fsum(w*x[i][j] for w,i in selected) for j in range(p)]; e2=[[math.fsum(w*x[i][j]*x[i][k] for w,i in selected) for k in range(p)] for j in range(p)]
                value+=count*top-math.fsum(z[i] for i in events)
                at=[math.fsum(x[i][j] for i in events) for j in range(p)]; bt=[[0.0]*p for _ in range(p)]
                for draw in range(1 if ties=='breslow' else count):
                    share=0.0 if ties=='breslow' else draw/count
                    denominator=s0-share*e0; numerator=[s1[j]-share*e1[j] for j in range(p)]
                    value+=factor*math.log(denominator)
                    for j in range(p):
                        at[j]-=factor*numerator[j]/denominator
                        for k in range(p): bt[j][k]+=factor*((s2[j][k]-share*e2[j][k])/denominator-numerator[j]*numerator[k]/denominator**2)
                for j in range(p):
                    score[j]-=at[j]
                    for k in range(p): information[j][k]+=bt[j][k]
                if transform is not None:
                    g=transform[time]
                    for j in range(p):
                        residual[j]+=g*at[j]
                        for k in range(p): cross[j][k]+=g*bt[j][k]; square[j][k]+=g*g*bt[j][k]
            if transform is not None: return value,score,information,residual,cross,square
            return value,score,information
        b,cov,ll,it=newton([0.0]*p,lambda beta:accumulate(beta))
        engine.note += ' Cox proportional hazards, '+('Efron' if ties=='efron' else 'Breslow')+' ties; no intercept'+(', left truncation at the entry column' if entry>=0 else '')+'. Analytic score and observed information (Newton-Raphson); exp(coef) is hazard ratio.'
        ph={}
        if check:
            try:
                ranks={time:rank+1 for rank,time in enumerate(times)}
                counts={}
                for t,e in zip(ends,observed):
                    if e: counts[t]=counts.get(t,0)+1
                centre=sum(ranks[t]*counts[t] for t in times)/sum(counts.values())
                _,_,_,residual,cross,square=accumulate(b,{time:ranks[time]-centre for time in times})
                schur=mp.matrix(square)-mp.matrix(cross)*cov*mp.matrix(cross).T
                require(min(float(v) for v in mp.eigsy(schur,eigvals_only=True))>1e-12,'Proportional-hazards check is not estimable for this data')
                statistic=mp.matrix(residual); chi=max(0.0,float((statistic.T*inverse(schur)*statistic)[0]))
                ph={'PH test chi2':chi,'PH test df':p,'PH test p':float(_chisq_sf(chi,p)),
                    'PH test per covariate':[{'term':'x'+str(j+1),'chi2':max(0.0,float(residual[j])**2/float(schur[j,j])),'df':1,'p':float(_chisq_sf(max(0.0,float(residual[j])**2/float(schur[j,j])),1))} for j in range(p)]}
                engine.note += ' Proportional-hazards check: Grambsch–Therneau scaled-Schoenfeld score test on event-time ranks.'
            except (MathError,OverflowError,ValueError) as exc: ph={'PH test':'unavailable: '+str(exc)}
        b=[v/scale for v,scale in zip(b,scales)]; cov=mp.matrix([[cov[i,j]/(scales[i]*scales[j]) for j in range(p)] for i in range(p)])
        result={'coefficients':inference(b,cov,['x'+str(i+1) for i in range(p)],True),'partial log likelihood':-ll,'iterations':it}
        result.update(ph)
        return result
    if name=='repeatedanova':
        rows=table(a[0],2,2); n=len(rows); k=len(rows[0])
        factor2=integer(a[1],1,20) if len(a)>1 else 1
        if factor2==1:
            overall=mean(sum(rows,[])); cols=list(zip(*rows))
            total=sum((v-overall)**2 for r in rows for v in r); sscondition=n*sum((mean(c)-overall)**2 for c in cols); sssubject=k*sum((mean(r)-overall)**2 for r in rows); error=total-sscondition-sssubject
            require(error>1e-12, 'Repeated-measures residual variation is required')
            df1=k-1; df2=(n-1)*(k-1); f=(sscondition/df1)/(error/df2)
            # Greenhouse–Geisser epsilon from double-centered covariance.
            cov=[[sum((rows[t][i]-mean(cols[i]))*(rows[t][j]-mean(cols[j])) for t in range(n))/(n-1) for j in range(k)] for i in range(k)]
            cm=[mean(r) for r in cov]; gm=mean(cm); centered=[[cov[i][j]-cm[i]-cm[j]+gm for j in range(k)] for i in range(k)]
            epsilon=min(1,max(1/df1,sum(centered[i][i] for i in range(k))**2/(df1*sum(v*v for r in centered for v in r))))
            engine.note += ' Balanced one-factor repeated measures: rows=subjects, columns=conditions. Includes Greenhouse–Geisser correction.'
            return {'F':f,'df1':df1,'df2':df2,'p':float(_f_sf(f,df1,df2)),'partial eta2':sscondition/(sscondition+error),'GG epsilon':epsilon,'GG p':float(_f_sf(f,df1*epsilon,df2*epsilon))}
        require(k%factor2==0,'Condition count must be divisible by the second-factor levels')
        second=k//factor2; require(second>=2 and factor2>=2,'Two-way repeated measures need two or more levels per factor')
        overall=mean(sum(rows,[]))
        cells=[[mean([rows[s][i*factor2+j] for s in range(n)]) for j in range(factor2)] for i in range(second)]
        first_means=[mean(cells[i]) for i in range(second)]; second_means=[mean([cells[i][j] for i in range(second)]) for j in range(factor2)]
        subject_means=[mean(r) for r in rows]; subject_first=[[mean(rows[s][i*factor2:(i+1)*factor2]) for i in range(second)] for s in range(n)]
        subject_second=[[mean(rows[s][j::factor2]) for j in range(factor2)] for s in range(n)]
        total=sum((v-overall)**2 for r in rows for v in r); subject_ss=k*sum((v-overall)**2 for v in subject_means)
        first_ss=n*factor2*sum((v-overall)**2 for v in first_means); second_ss=n*second*sum((v-overall)**2 for v in second_means)
        interaction_ss=n*sum((cells[i][j]-first_means[i]-second_means[j]+overall)**2 for i in range(second) for j in range(factor2))
        first_error=factor2*sum((subject_first[s][i]-first_means[i]-subject_means[s]+overall)**2 for s in range(n) for i in range(second))
        second_error=second*sum((subject_second[s][j]-second_means[j]-subject_means[s]+overall)**2 for s in range(n) for j in range(factor2))
        within=total-subject_ss; interaction_error=within-first_ss-second_ss-interaction_ss-first_error-second_error
        require(min(first_error,second_error,interaction_error)>1e-12,'Repeated-measures residual variation is required')
        def epsilon(variables,dimension):
            size=len(variables[0]); columns=list(zip(*variables))
            centered=[[sum((variables[t][i]-mean(columns[i]))*(variables[t][j]-mean(columns[j])) for t in range(len(variables)))/(len(variables)-1) for j in range(size)] for i in range(size)]
            rows_mean=[mean(r) for r in centered]; grand=mean(rows_mean)
            starred=[[centered[i][j]-rows_mean[i]-rows_mean[j]+grand for j in range(size)] for i in range(size)]
            require(sum(v*v for r in starred for v in r)>0,'Greenhouse–Geisser correction needs contrast variation')
            return min(1,max(1/dimension,sum(starred[i][i] for i in range(size))**2/(dimension*sum(v*v for r in starred for v in r))))
        def helmert(levels): return [[1/math.sqrt(u*(u+1)) if j<u else -u/math.sqrt(u*(u+1)) if j==u else 0.0 for j in range(levels)] for u in range(1,levels)]
        contrasts_first,contrasts_second=helmert(second),helmert(factor2)
        residual_rows=[[rows[s][i*factor2+j]-subject_first[s][i]-subject_second[s][j]+subject_means[s] for i in range(second) for j in range(factor2)] for s in range(n)]
        interaction_variables=[[sum(contrasts_first[u][i]*contrasts_second[v][j]*row[i*factor2+j] for i in range(second) for j in range(factor2)) for v in range(factor2-1) for u in range(second-1)] for row in residual_rows]
        effects={'A':(first_ss,second-1,first_error,(n-1)*(second-1)),'B':(second_ss,factor2-1,second_error,(n-1)*(factor2-1)),'AB':(interaction_ss,(second-1)*(factor2-1),interaction_error,(n-1)*(second-1)*(factor2-1))}
        adjustments={'A':epsilon(subject_first,second-1),'B':epsilon(subject_second,factor2-1),'AB':epsilon(interaction_variables,(second-1)*(factor2-1))}
        result={'first factor levels':second,'second factor levels':factor2,'subject df':n-1}
        for key,(effect,degree1,error,degree2) in effects.items():
            value=(effect/degree1)/(error/degree2)
            result[key+' F']=value; result[key+' df1']=degree1; result[key+' df2']=degree2; result[key+' p']=float(_f_sf(value,degree1,degree2))
            result[key+' GG epsilon']=adjustments[key]; result[key+' GG p']=float(_f_sf(value,degree1*adjustments[key],degree2*adjustments[key]))
        engine.note += ' Balanced two-way within-subjects ANOVA: rows=subjects, columns list the first factor (slowest) crossed with the second factor. Greenhouse–Geisser corrections per effect.'
        return result
    if name in ('multinomial','ordinal','poissonreg','nbreg'):
        engine.note += {'ordinal':' Proportional-odds cumulative logit; ascending numeric categories; analytic score and observed information.', 'multinomial':' Multinomial logit; smallest category is reference.', 'poissonreg':' Poisson log-link regression; exp(coef) is incidence rate ratio.', 'nbreg':' Negative binomial NB2 log-link; dispersion alpha is jointly estimated; analytic score and observed information.'}[name]+' Rows: predictors then response. Wald 95% CI.'
        return model(table(a[0],3,2),name)
    if name in ('mixedmodel','gee'): return clustered(engine,name,a)
    if name in ('bootstrapci','testpower','samplesize','kstest'): return resampling(engine,name,a)
    if name in ('crossvalidate','pca','kmeans','impute'): return learning(engine,name,a)
    raise MathError('Unknown advanced analysis')


def survival_analysis(engine,a):
    """Rows: time, event, optional entry time, numeric group ID, covariates.

    The UI encodes labels in first-occurrence order. Cox includes treatment
    dummies (first group as reference), plus the selected covariates. Left
    truncation, tied-event handling and the proportional-hazards check follow
    the Cox options. Subtest failures preserve valid KM curves.
    """
    rows=table(a[0],2,3); fit=integer(a[1],0,1) if len(a)>1 else 0
    ties=option(a,2,'efron'); require(ties in ('breslow','efron'),'Cox ties: breslow or efron')
    entry=integer(a[3],-1,19) if len(a)>3 else -1; require(entry in (-1,2),'Entry time follows the event column')
    check=integer(a[4],0,1) if len(a)>4 else 1
    survival(rows,entry)
    base=3 if entry>=0 else 2
    ids=list(dict.fromkeys(r[base] for r in rows))
    require(len(ids)<=20,'Survival analysis limit: 20 groups')
    def truncated(r,time): return entry<0 or r[entry]<time
    groups=[]
    for label in ids:
        sample=[r[:2]+([r[entry]] if entry>=0 else []) for r in rows if r[base]==label]
        km=calculate(engine,'kaplanmeier',[sample,.95]+([entry] if entry>=0 else []))
        groups.append({'id':label,'n':len(sample),'events':int(sum(r[1] for r in sample)),
                       'median':km['median survival'],'curve':km['survival table']})
    report={'groups':groups,'level':.95,'logrank':None,'cox':None,'ties':ties,'truncation':entry>=0,'ph':bool(check and fit)}
    if len(ids)>1:
        try:
            k=len(ids); score=[0.0]*k; covariance=[[0.0]*k for _ in ids]
            for time in sorted(set(r[0] for r in rows if r[1])):
                risk=[sum(r[0]>=time and truncated(r,time) and r[base]==label for r in rows) for label in ids]
                events=[sum(r[0]==time and r[1] and r[base]==label for r in rows) for label in ids]
                n=sum(risk); d=sum(events)
                for i in range(k):
                    score[i]+=events[i]-d*risk[i]/n
                    for j in range(k):
                        if n>1: covariance[i][j]+=d*(n-d)/(n-1)*((risk[i]/n if i==j else 0)-risk[i]*risk[j]/n**2)
            reduced=mp.matrix([r[:-1] for r in covariance[:-1]]); v=mp.matrix(score[:-1])
            require(min(float(x) for x in mp.eigsy(reduced,eigvals_only=True))>1e-12,'Log-rank needs informative events in all risk sets')
            chi=max(0,float((v.T*inverse(reduced)*v)[0]))
            report['logrank']={'chi2':chi,'df':k-1,'p':float(_chisq_sf(chi,k-1))}
        except MathError as exc: report['logrank']={'error':str(exc)}
    if fit:
        try:
            coxrows=[r[:2]+([r[entry]] if entry>=0 else [])+[float(r[base]==label) for label in ids[1:]]+list(r[base+1:]) for r in rows]
            require(len(coxrows[0])>2,'Choose Cox predictors or at least two groups')
            report['cox']=calculate(engine,'cox',[coxrows,ties,2 if entry>=0 else -1,check])
            for index,term in enumerate(report['cox']['coefficients']):
                term['term']='group:'+str(index+1) if index<len(ids)-1 else 'predictor:'+str(index-len(ids)+1)
                term['HR']=term['exp(coef)']
                term['HR CI95']=[math.exp(v) if v<709 else math.inf for v in term['CI95']] if term['CI95'] else None
        except MathError as exc: report['cox']={'error':str(exc)}
    # Raw JSON metadata is separate from the compact reusable calculator result.
    def json_numbers(value):
        if isinstance(value,dict): return {k:json_numbers(v) for k,v in value.items()}
        if isinstance(value,list): return [json_numbers(v) for v in value]
        if isinstance(value,float) and not math.isfinite(value): return None
        return value
    engine.survival_report=json_numbers(report)
    engine.note += ' Survival analysis: pointwise Greenwood log-log 95% CI; log-rank'+(' with left-truncated risk sets' if entry>=0 else '')+'; Cox '+('Efron' if ties=='efron' else 'Breslow')+' ties, first group as reference'+(', proportional-hazards check by scaled-Schoenfeld score test' if check and fit else '')+'.'
    return {'groups':len(ids),'observations':len(rows),'events':int(sum(r[1] for r in rows)),
            'log-rank':report['logrank'] or 'one group','Cox':report['cox'] or 'off'}


def interaction_pairs(value, width):
    """Normalize [i,j] predictor-pair interactions into distinct ascending pairs."""
    if value is None:
        return []
    require(isinstance(value, (list, tuple)) and len(value) >= 1, 'Interactions must be a list of [i,j] predictor pairs')
    pairs = []
    for item in value:
        require(isinstance(item, (list, tuple)) and len(item) == 2, 'Interactions must be a list of [i,j] predictor pairs')
        first, second = integer(item[0], 1, width), integer(item[1], 1, width)
        pairs.append((min(first, second), max(first, second)))
    require(len(set(pairs)) == len(pairs), 'Interaction pairs must be distinct')
    return pairs


def nelder_mead(objective,start,step,iterations=160):
    """Deterministic direct search for small profile objectives with boundaries."""
    size=len(start); simplex=[list(start)]
    for i in range(size):
        point=list(start); point[i]+=step[i]; simplex.append(point)
    values=[objective(point) for point in simplex]
    for _ in range(iterations):
        order=sorted(range(size+1),key=lambda i:values[i]); simplex=[simplex[i] for i in order]; values=[values[i] for i in order]
        if max(abs(simplex[i][j]-simplex[0][j]) for i in range(1,size+1) for j in range(size))<1e-7: break
        centroid=[sum(simplex[i][j] for i in range(size))/size for j in range(size)]
        reflected=[2*centroid[j]-simplex[size][j] for j in range(size)]; value=objective(reflected)
        if value<values[0]:
            expanded=[centroid[j]+2*(reflected[j]-centroid[j]) for j in range(size)]; trial=objective(expanded)
            if trial<value: simplex[size],values[size]=expanded,trial
            else: simplex[size],values[size]=reflected,value
        elif value<values[size-1]: simplex[size],values[size]=reflected,value
        else:
            contracted=[centroid[j]+.5*(simplex[size][j]-centroid[j]) for j in range(size)]; trial=objective(contracted)
            if trial<values[size]: simplex[size],values[size]=contracted,trial
            else:
                for i in range(1,size+1):
                    simplex[i]=[(simplex[i][j]+simplex[0][j])/2 for j in range(size)]; values[i]=objective(simplex[i])
    return simplex[0],values[0]


def numeric_rank(values,tolerance=1e-10):
    """Column rank of a numeric design by scaled Gaussian elimination (no symbolic cost)."""
    matrix=[[float(v) for v in row] for row in values]; height=len(matrix); width=len(matrix[0])
    scales=[max(abs(row[j]) for row in matrix) or 1.0 for j in range(width)]
    matrix=[[row[j]/scales[j] for j in range(width)] for row in matrix]
    rank=0; position=0
    for column in range(width):
        if position>=height: break
        pivot=max(range(position,height),key=lambda i:abs(matrix[i][column]))
        if abs(matrix[pivot][column])<=tolerance: continue
        matrix[position],matrix[pivot]=matrix[pivot],matrix[position]
        leading=matrix[position]
        for i in range(position+1,height):
            row=matrix[i]; factor=row[column]/leading[column]
            if factor:
                for j in range(column,width): row[j]-=factor*leading[j]
        rank+=1; position+=1
    return rank


def clustered_design(rows):
    """Intercept design and response for clustered models with a numeric collinearity check."""
    design=[[1.0]+[float(v) for v in row[1:-1]] for row in rows]; response=[float(row[-1]) for row in rows]
    require(len(design)>len(design[0]),'More observations than coefficients are required')
    require(numeric_rank(design)==len(design[0]),'Predictors are collinear')
    return design,response


def small_logdet(values):
    """log|A| of a small positive-definite matrix by elimination with partial pivoting."""
    size=len(values); work=[[float(values[i][j]) for j in range(size)] for i in range(size)]; total=0.0
    for column in range(size):
        pivot=max(range(column,size),key=lambda i:abs(work[i][column]))
        if abs(work[pivot][column])<=1e-300: return None
        if pivot!=column: work[column],work[pivot]=work[pivot],work[column]
        total+=math.log(abs(work[column][column]))
        for i in range(column+1,size):
            factor=work[i][column]/work[column][column]
            for j in range(column+1,size): work[i][j]-=factor*work[column][j]
    return total


def small_inverse(values):
    """Inverse of a small matrix by Gauss-Jordan elimination with partial pivoting."""
    size=len(values)
    work=[[float(values[i][j]) for j in range(size)]+[1.0 if i==j else 0.0 for j in range(size)] for i in range(size)]
    for column in range(size):
        pivot=max(range(column,size),key=lambda i:abs(work[i][column]))
        if abs(work[pivot][column])<=1e-300: return None
        work[column],work[pivot]=work[pivot],work[column]
        divisor=work[column][column]
        work[column]=[v/divisor for v in work[column]]
        for i in range(size):
            if i==column: continue
            factor=work[i][column]
            if factor: work[i]=[v-factor*w for v,w in zip(work[i],work[column])]
    return [[work[i][size+j] for j in range(size)] for i in range(size)]


def intercept_fit(x,y,clusters,method):
    """Gaussian random-intercept ML/REML fit with per-cluster algebra (no n x n matrices)."""
    n=len(y); p=len(x[0])
    gram=[[math.fsum(r[i]*r[j] for r in x) for j in range(p)] for i in range(p)]
    right=[math.fsum(r[i]*v for r,v in zip(x,y)) for i in range(p)]
    energy=math.fsum(v*v for v in y)
    pieces=[(len(cluster),[math.fsum(x[i][j] for i in cluster) for j in range(p)],math.fsum(y[i] for i in cluster)) for cluster in clusters]
    def fit(ratio):
        matrix=[row[:] for row in gram]; vector=list(right); total=energy; logdet=0.0
        for size,moment,outcome in pieces:
            factor=ratio/(1+size*ratio); logdet+=math.log1p(size*ratio)
            total-=factor*outcome*outcome
            for i in range(p):
                vector[i]-=factor*moment[i]*outcome
                for j in range(i,p):
                    matrix[i][j]-=factor*moment[i]*moment[j]
                    if i!=j: matrix[j][i]=matrix[i][j]
        information=mp.matrix(matrix); beta=inverse(information)*mp.matrix(vector)
        rss=total-float((mp.matrix(vector).T*beta)[0])
        require(rss>1e-12,'Mixed model requires residual variation')
        if method=='reml':
            extra=small_logdet(matrix); require(extra is not None,'Mixed model information is singular')
            objective=(n-p)*math.log(rss/(n-p))+logdet+extra
        else: objective=n*math.log(rss/n)+logdet
        return objective,beta,information,rss,ratio
    def objective(z): return fit(math.exp(z))[0]
    low,high=-16.0,16.0; golden=(math.sqrt(5)-1)/2
    u=high-golden*(high-low); v=low+golden*(high-low); fu,fv=objective(u),objective(v)
    for _ in range(60):
        if fu<fv: high,v,fv=v,u,fu; u=high-golden*(high-low); fu=objective(u)
        else: low,u,fu=u,v,fv; v=low+golden*(high-low); fv=objective(v)
    chosen=fit(math.exp((low+high)/2)); boundary=fit(0)
    return boundary if boundary[0]<=chosen[0] else chosen


def mixed_intercept(engine,x,y,clusters,ids,method):
    _,beta,information,rss,ratio=intercept_fit(x,y,clusters,method)
    sigma=rss/(len(y)-len(x[0])) if method=='reml' else rss/len(y)
    engine.note += ' Gaussian random-intercept mixed model, '+('restricted maximum likelihood (REML)' if method=='reml' else 'maximum likelihood (ML)')+', Wald inference. Rows: subject ID, predictors, response.'
    return {'coefficients':inference(list(map(float,beta)),inverse(information)*sigma,['Intercept']+['x'+str(i) for i in range(1,len(x[0]))]),'residual variance':sigma,'random intercept variance':ratio*sigma,'ICC':ratio/(1+ratio),'subjects':len(ids),'estimation':method.upper()}


def mixed_random_effects(engine,x,y,clusters,ids,slopes,method):
    """Gaussian random intercept plus up to three random slopes (ML or REML)."""
    n=len(y); p=len(x[0]); count=1+len(slopes); pairs=[(i,j) for i in range(count) for j in range(i+1)]
    blocks=[]
    for cluster in clusters:
        members=list(cluster); design=[[1.0]+[x[i][s] for s in slopes] for i in members]
        products=[[math.fsum(design[t][a]*design[t][b] for t in range(len(members))) for b in range(count)] for a in range(count)]
        cross=[[math.fsum(design[t][a]*x[i][j] for t,i in enumerate(members)) for j in range(p)] for a in range(count)]
        outcome=[math.fsum(design[t][a]*y[i] for t,i in enumerate(members)) for a in range(count)]
        gram=[[math.fsum(x[i][a]*x[i][b] for i in members) for b in range(p)] for a in range(p)]
        moments=[math.fsum(x[i][a]*y[i] for i in members) for a in range(p)]
        blocks.append((products,cross,outcome,gram,moments,math.fsum(y[i]*y[i] for i in members)))
    def evaluate(parameters):
        if max(abs(v) for v in parameters)>8: return None
        lower=mp.zeros(count); position=0
        for i in range(count):
            for j in range(i+1):
                lower[i,j]=mp.exp(parameters[position]) if i==j else parameters[position]; position+=1
        theta=lower*lower.T
        try: theta_inverse=inverse(theta)
        except MathError: return None
        logdet=len(blocks)*2*math.fsum(math.log(float(lower[i,i])) for i in range(count))
        information=[[0.0]*p for _ in range(p)]; vector=[0.0]*p; total=0.0
        for products,cross,outcome,gram,moments,energy in blocks:
            core=[[float(theta_inverse[a,b])+products[a][b] for b in range(count)] for a in range(count)]
            values=small_inverse(core)
            if values is None: return None
            if any(not math.isfinite(v) for row in values for v in row): return None
            entry=small_logdet(core)
            if entry is None: return None
            logdet+=entry
            for a in range(p):
                for b in range(a,p):
                    subtotal=gram[a][b]
                    for u in range(count):
                        for v in range(count): subtotal-=cross[u][a]*values[u][v]*cross[v][b]
                    information[a][b]+=subtotal
                    if a!=b: information[b][a]+=subtotal
                adjustment=moments[a]
                for u in range(count):
                    for v in range(count): adjustment-=cross[u][a]*values[u][v]*outcome[v]
                vector[a]+=adjustment
            quadratic=0.0
            for u in range(count):
                for v in range(count): quadratic+=outcome[u]*values[u][v]*outcome[v]
            total+=energy-quadratic
        matrix=mp.matrix(information); beta=inverse(matrix)*mp.matrix(vector)
        rss=total-float((mp.matrix(vector).T*beta)[0])
        if not math.isfinite(rss) or rss<=1e-12: return None
        if method=='reml':
            extra=small_logdet(information)
            if extra is None: return None
            objective=(n-p)*math.log(rss/(n-p))+logdet+extra
        else: objective=n*math.log(rss/n)+logdet
        return objective,beta,matrix,rss,theta
    def objective(parameters):
        result=evaluate(parameters)
        return math.inf if result is None else result[0]
    seed=intercept_fit(x,y,clusters,method)[4]
    start=0.5*math.log(max(1e-3,seed))
    best=None
    for scale in (start,math.log(.25),math.log(.1)):
        parameters=[start if i==0 and j==0 else scale if i==j else 0.0 for i,j in pairs]
        candidate,value=nelder_mead(objective,parameters,[.4]*len(parameters),160+80*max(0,len(parameters)-3))
        if math.isfinite(value) and (best is None or value<best[1]): best=(candidate,value)
    require(best is not None,'Random-slope model did not converge')
    fields=evaluate(best[0]); require(fields is not None,'Random-slope model did not converge')
    _,beta,information,rss,theta=fields
    sigma=rss/(n-p) if method=='reml' else rss/n
    result={'coefficients':inference(list(map(float,beta)),inverse(information)*sigma,['Intercept']+['x'+str(i) for i in range(1,p)]),'residual variance':sigma,'random intercept variance':sigma*float(theta[0,0])}
    for index,slope in enumerate(slopes):
        result['random slope variance x'+str(slope)]=sigma*float(theta[index+1,index+1])
        result['intercept-slope correlation x'+str(slope)]=float(theta[0,index+1])/math.sqrt(float(theta[0,0])*float(theta[index+1,index+1]))
    if count==2:
        result['random slope variance']=result['random slope variance x'+str(slopes[0])]
        result['intercept-slope correlation']=result['intercept-slope correlation x'+str(slopes[0])]
    result['ICC']=float(theta[0,0])/(1+float(theta[0,0]))
    result['subjects']=len(ids); result['estimation']=method.upper()
    engine.note += ' Gaussian random intercept with random slopes on '+', '.join('x'+str(s) for s in slopes)+', '+('restricted maximum likelihood (REML)' if method=='reml' else 'maximum likelihood (ML)')+', Wald inference. Rows: subject ID, predictors, response.'
    return result


def clustered(engine,name,a):
    rows=table(a[0],4,3)
    grouped={}
    for index,row in enumerate(rows): grouped.setdefault(row[0],[]).append(index)
    ids=sorted(grouped); clusters=[grouped[id_] for id_ in ids]
    require(len(clusters)>=3,'At least three subject/cluster IDs are required')
    pairs=[]; names=[]; terms=''
    if name=='gee':
        family=option(a,1,'gaussian'); require(family in ('gaussian','binomial','poisson'),'GEE family: gaussian, binomial, or poisson')
        corr=option(a,2,'independence'); require(corr in ('independence','exchangeable','ar1'),'GEE working correlation: independence, exchangeable, or ar1')
        width=len(rows[0])-2; require(width>=1,'Choose at least one predictor')
        pairs=interaction_pairs(a[3] if len(a)>3 else None,width)
        names=['Intercept']+['x'+str(i) for i in range(1,width+1)]+[('x'+str(i)+'^2') if i==j else ('x'+str(i)+':x'+str(j)) for i,j in pairs]
        terms=', '.join(('x'+str(i)+'^2') if i==j else ('x'+str(i)+':x'+str(j)) for i,j in pairs)
        if pairs: rows=[row[:1]+row[1:-1]+[row[i]*row[j] for i,j in pairs]+row[-1:] for row in rows]
    x,y=clustered_design(rows); n=len(y); p=len(x[0]); Y=mp.matrix(y)
    if name=='mixedmodel':
        argument=a[1] if len(a)>1 else 0
        if isinstance(argument,(list,tuple)):
            slopes=[integer(v,1,19) for v in argument]
            require(slopes and len(slopes)<=3 and len(set(slopes))==len(slopes),'Use 0, a predictor position, or up to three distinct positions such as [1,2]')
        else:
            selected=integer(argument,0,19); slopes=[] if selected==0 else [selected]
        method=option(a,2,'ml'); require(method in ('ml','reml'),'Estimation: ml or reml')
        require(any(len(c)>1 for c in clusters),'Random intercept requires repeated subjects')
        for slope in slopes: require(1<=slope<p,'Random-slope predictor position is out of range')
        if not slopes: return mixed_intercept(engine,x,y,clusters,ids,method)
        require(len(clusters)*(p*(len(slopes)+1))**2<=20000,'Random-slope model is too large; reduce predictors, random effects, or subjects')
        return mixed_random_effects(engine,x,y,clusters,ids,slopes,method)
    x,transform,_,_=standardized_design(x); X=mp.matrix(x)
    if pairs: engine.note += ' GEE interactions: '+terms+'.'
    if family=='binomial': require(all(v in (0,1) for v in y),'Binomial GEE response must be 0/1')
    if family=='poisson': require(all(v>=0 and v.is_integer() for v in y),'Poisson GEE response must be integer counts')
    if family=='gaussian': b=list(map(float,inverse(X.T*X)*X.T*Y)); mu=[dot(r,b) for r in x]; weights=[1.0]*n
    else:
        def exact(b):
            # Canonical-link GLM start: the score and the Fisher information
            # are analytic, so the working-correlation iteration avoids
            # finite differences.
            value = 0.0; score = [0.0]*p; information = [[0.0]*p for _ in range(p)]
            for row,v in zip(x,y):
                z = dot(row,b)
                if family=='binomial':
                    fitted = logistic(z); value += softplus(z)-v*z; weight = fitted*(1-fitted)
                else:
                    fitted = math.exp(z); value += fitted-v*z; weight = fitted
                for j in range(p):
                    score[j] += row[j]*(fitted-v)
                    for k in range(j,p): information[j][k] += row[j]*row[k]*weight
            for j in range(1,p):
                for k in range(j): information[j][k] = information[k][j]
            return value,score,information
        b,_,_,_=newton([0.0]*p,exact); mu=[logistic(dot(r,b)) if family=='binomial' else math.exp(dot(r,b)) for r in x]; weights=[v*(1-v) if family=='binomial' else v for v in mu]
        if family=='binomial':
            margins=[(2*v-1)*dot(r,b) for r,v in zip(x,y)]
            require(not (min(margins)>=-1e-8 and max(margins)>1e-8),'Complete or quasi separation: binomial GEE estimates are not finite')
    if corr=='independence':
        bread=inverse([[sum(weights[t]*x[t][i]*x[t][j] for t in range(n)) for j in range(p)] for i in range(p)]); meat=mp.zeros(p)
        for c in clusters:
            score=mp.matrix([sum(x[t][i]*(y[t]-mu[t]) for t in c) for i in range(p)]); meat+=score*score.T
        cov=bread*meat*bread
        b=list(map(float,transform*mp.matrix(b))); cov=transform*cov*transform.T
        engine.note += ' GEE: independent working correlation, cluster sandwich covariance, asymptotic Wald inference. Rows: cluster ID, predictors, response. Zero robust SE leaves p/CI unavailable.'
        return {'coefficients':inference(b,cov,names,family!='gaussian'),'clusters':len(ids),'family':family}
    largest=max(len(c) for c in clusters)
    def moments(alpha,beta):
        mean_values=[]; variances=[]; derivatives=[]
        for row in x:
            linear=dot(row,beta)
            if family=='gaussian': mean_values.append(linear); variances.append(1.0); derivatives.append(1.0)
            else:
                value=logistic(linear) if family=='binomial' else math.exp(linear)
                mean_values.append(value); variances.append(value*(1-value) if family=='binomial' else value); derivatives.append(value*(1-value) if family=='binomial' else value)
        require(all(math.isfinite(v) for v in mean_values),'GEE mean function exceeded the numeric range')
        correlation=alpha
        if corr=='exchangeable' and largest>1: correlation=max(correlation,-1/(largest-1)+1e-6)
        fisher=mp.zeros(p); scores=[]; standardized=[0.0]*n
        for c in clusters:
            size=len(c); scaled=[]; residual=[]
            for i in c:
                divided=derivatives[i]/math.sqrt(variances[i])
                scaled.append(mp.matrix([x[i][j]*divided for j in range(p)]))
                residual.append((y[i]-mean_values[i])/math.sqrt(variances[i])); standardized[i]=residual[-1]
            if corr=='exchangeable':
                first=1/(1-correlation); second=correlation/((1-correlation)*(1+(size-1)*correlation))
                columns_sum=mp.matrix([mp.fsum(scaled[k][j,0] for k in range(size)) for j in range(p)])
                gram=mp.zeros(p)
                for k in range(size): gram+=scaled[k]*scaled[k].T
                score=first*mp.matrix([mp.fsum(scaled[k][j,0]*residual[k] for k in range(size)) for j in range(p)])-second*math.fsum(residual)*columns_sum
                fisher+=first*gram-second*columns_sum*columns_sum.T
            else:
                if size==1: coefficient=1.0; diagonals=[1.0]
                else:
                    coefficient=1/(1-correlation*correlation); diagonals=[1.0]*size
                    for k in range(1,size-1): diagonals[k]=1+correlation*correlation
                vector=mp.matrix([mp.fsum(diagonals[k]*scaled[k][j,0]*residual[k] for k in range(size)) for j in range(p)])
                gram=mp.zeros(p)
                for k in range(size): gram+=diagonals[k]*(scaled[k]*scaled[k].T)
                if size>1:
                    for k in range(size-1):
                        vector-=correlation*mp.matrix([scaled[k][j,0]*residual[k+1]+scaled[k+1][j,0]*residual[k] for j in range(p)])
                        gram-=correlation*(scaled[k]*scaled[k+1].T+scaled[k+1]*scaled[k].T)
                score=coefficient*vector; fisher+=coefficient*gram
            scores.append(score)
        return fisher,scores,standardized
    def update(standardized):
        numerator=0.0; denominator=0.0
        if corr=='exchangeable':
            for c in clusters:
                values=[standardized[i] for i in c]; numerator+=math.fsum(values[i]*values[j] for i in range(len(values)) for j in range(i+1,len(values))); denominator+=len(values)*(len(values)-1)/2
        else:
            for c in clusters:
                values=[standardized[i] for i in c]; numerator+=math.fsum(values[k]*values[k+1] for k in range(len(values)-1)); denominator+=max(0,len(values)-1)
        if denominator<=0: return 0.0
        lower=-1/(largest-1)+1e-6 if corr=='exchangeable' and largest>1 else -0.9999
        return max(lower,min(0.9999,numerator/denominator))
    alpha=0.0
    for iteration in range(200):
        fisher,scores,standardized=moments(alpha,b)
        alpha=update(standardized)
        total=mp.zeros(p,1)
        for vector in scores: total+=vector
        step=inverse(fisher)*total
        if max(abs(float(step[j,0])) for j in range(p))<1e-9: break
        b=[b[j]+float(step[j,0]) for j in range(p)]
        require(max(abs(v) for v in b)<40,'GEE estimates diverged; simplify the working correlation')
    else: raise MathError('GEE did not converge; simplify the working correlation')
    fisher,scores,standardized=moments(alpha,b)
    bread=inverse(fisher); meat=mp.zeros(p)
    for vector in scores: meat+=vector*vector.T
    cov=bread*meat*bread
    b=list(map(float,transform*mp.matrix(b))); cov=transform*cov*transform.T
    engine.note += ' GEE: '+corr+' working correlation'+(' (moment estimate alpha='+format(alpha,'.4g')+')' if corr!='independence' else '')+', cluster sandwich covariance, asymptotic Wald inference. Rows: cluster ID, predictors, response'+('; AR(1) uses the within-cluster row order as the time order' if corr=='ar1' else '')+'. Zero robust SE leaves p/CI unavailable.'
    return {'coefficients':inference(b,cov,names,family!='gaussian'),'clusters':len(ids),'family':family,'working correlation':corr,'alpha':alpha}


def t_quantile(level,df):
    """Central Student-t quantile by bisection on the regularized incomplete beta."""
    with mp.workdps(30):
        target=mp.mpf(level); nu=mp.mpf(df)
        def cdf(value): return 1-mp.betainc(nu/2,mp.mpf('.5'),0,nu/(nu+value*value),regularized=True)/2
        low,high=mp.mpf(0),mp.mpf(4)
        while cdf(high)<target: high*=2
        for _ in range(70):
            middle=(low+high)/2
            if cdf(middle)<target: low=middle
            else: high=middle
        return float((low+high)/2)


def t_test_power(effect,size,alpha,kind,sides):
    """Exact noncentral-t power integrated over the chi-square mass of the scale mixture."""
    independent=kind=='independent'
    degrees=2*size-2 if independent else size-1
    shift=effect*math.sqrt(size/2 if independent else size)
    critical=t_quantile(1-alpha/2 if sides=='two' else 1-alpha,degrees)
    with mp.workdps(30):
        nu=mp.mpf(degrees); delta=mp.mpf(shift); cutoff=mp.mpf(critical)
        low=max(mp.mpf(0),nu-12*mp.sqrt(2*nu)-50); high=nu+12*mp.sqrt(2*nu)+50
        constant=mp.power(2,nu/2)*mp.gamma(nu/2)
        def integrand(weight):
            density=mp.power(weight,nu/2-1)*mp.exp(-weight/2)/constant
            scaled=mp.sqrt(weight/nu)
            if sides=='two': return (mp.ncdf(-cutoff*scaled-delta)+mp.ncdf(delta-cutoff*scaled))*density
            if sides=='greater': return mp.ncdf(delta-cutoff*scaled)*density
            return mp.ncdf(-cutoff*scaled-delta)*density
        return float(mp.quad(integrand,[low,high],maxdegree=8))


def least_squares(design,target):
    """Normal-equation least squares with ridge escalation for degenerate imputation designs."""
    size=len(design[0])
    gram=[[math.fsum(row[i]*row[j] for row in design) for j in range(size)] for i in range(size)]
    right=[math.fsum(row[i]*v for row,v in zip(design,target)) for i in range(size)]
    for ridge in (0.0,1e-10,1e-7):
        matrix=mp.matrix([[gram[i][j]+(ridge*gram[j][j] if i==j else 0.0) for j in range(size)] for i in range(size)])
        try: return [float(v) for v in inverse(matrix)*mp.matrix(right)]
        except MathError: continue
    raise MathError('Imputation predictors are collinear; remove duplicate columns')


def regression_imputation(values):
    """Iterated conditional-mean (single) regression imputation."""
    n=len(values); columns=len(values[0])
    filled=[list(row) for row in values]
    for j in range(columns):
        fill=mean([row[j] for row in values if row[j] is not None])
        for i in range(n):
            if filled[i][j] is None: filled[i][j]=fill
    incomplete=[j for j in range(columns) if any(row[j] is None for row in values)]
    sweeps=0
    for sweep in range(40):
        largest=0.0
        for j in incomplete:
            predictors=[c for c in range(columns) if c!=j]
            observed=[i for i in range(n) if values[i][j] is not None]
            require(len(observed)>len(predictors),'Regression imputation needs more observed rows than predictors')
            centers=[]; scales=[]
            for c in predictors:
                sample=[filled[i][c] for i in observed]
                centers.append(mean(sample)); scales.append(math.sqrt(variance(sample)) if len(sample)>1 else 0.0)
            def standardize(i): return [1.0]+[(filled[i][c]-center)/scale if scale else 0.0 for c,center,scale in zip(predictors,centers,scales)]
            beta=least_squares([standardize(i) for i in observed],[values[i][j] for i in observed])
            for i in range(n):
                if values[i][j] is not None: continue
                prediction=dot(standardize(i),beta)
                require(math.isfinite(prediction),'Regression imputation prediction is outside the numeric range')
                largest=max(largest,abs(prediction-filled[i][j])); filled[i][j]=prediction
        sweeps=sweep+1
        if largest<1e-12: break
    return filled,sweeps


def neighbor_imputation(values,neighbors):
    """k-nearest-neighbour (single) imputation on standardized observed coordinates."""
    n=len(values); columns=len(values[0])
    require(sum(1 for row in values for v in row if v is None)*n*columns<=8000000,'k-NN imputation is too large; use regression or mean for this table')
    centers=[]; scales=[]
    for j in range(columns):
        sample=[row[j] for row in values if row[j] is not None]
        centers.append(mean(sample)); scales.append(math.sqrt(variance(sample)) if len(sample)>1 else 0.0)
    standardized=[[None if v is None else (v-centers[j])/scales[j] if scales[j] else 0.0 for j,v in enumerate(row)] for row in values]
    filled=[list(row) for row in values]
    for i in range(n):
        for j in range(columns):
            if values[i][j] is not None: continue
            candidates=[]
            for h in range(n):
                if values[h][j] is None: continue
                distance=0.0; shared=False
                for c in range(columns):
                    if c==j: continue
                    a=standardized[i][c]; b=standardized[h][c]
                    if a is None or b is None: continue
                    distance+=(a-b)**2; shared=True
                candidates.append((0 if shared else 1,distance,h))
            require(candidates,'k-NN imputation needs observed values in every column')
            candidates.sort()
            filled[i][j]=mean([values[h][j] for _,_,h in candidates[:neighbors]])
    return filled,neighbors


def machine_fit(train,model,alpha,ratio):
    """Penalized linear or logistic fit on standardized columns; returns a predictor for new rows."""
    from calc_machine_learning import _linear_core, _logistic_core
    xs=[[float(v) for v in row[:-1]] for row in train]; ys=[float(row[-1]) for row in train]
    size=len(xs); width=len(xs[0])
    require(size>=2 and width>=1,'Cross-validation needs complete predictor rows')
    origins=xs[0]
    offsets=[[row[j]-origins[j] for j in range(width)] for row in xs]
    centers=[math.fsum(row[j]/size for row in offsets) for j in range(width)]
    centered=[[row[j]-centers[j] for row in offsets] for j in range(width)]
    scales=[]
    for column in centered:
        extreme=max(map(abs,column))
        scales.append(extreme*math.sqrt(math.fsum((v/extreme)**2/size for v in column)) if extreme else 0.0)
    require(all(math.isfinite(v) for v in centers+scales),'Regression data range is too large')
    standardized=[[v/scale for v in column] if scale else [0.0]*size for column,scale in zip(centered,scales)]
    active=[scale>0 for scale in scales]
    def design(row): return [(row[j]-origins[j]-centers[j])/scales[j] if scales[j] else 0.0 for j in range(width)]
    if model=='logistic':
        require(set(ys)<={0.0,1.0} and 0<sum(ys)<size,'Each logistic fold needs both 0 and 1 responses')
        intercept,beta,_,_,converged=_logistic_core(standardized,active,ys,0.0,alpha)
        require(converged,'Logistic fit did not converge; increase the penalty')
        def predict(row):
            linear=intercept+math.fsum(b*v for b,v in zip(beta,design(row)))
            return 1.0/(1.0+math.exp(-max(-30.0,min(30.0,linear))))
        return predict
    yorigin=ys[0]; shifted=[v-yorigin for v in ys]; ymean=math.fsum(v/size for v in shifted)
    target=[v-ymean for v in shifted]; yscale=max(map(abs,target)) or 1.0
    residual=[v/yscale for v in target]
    beta,_,_,converged=_linear_core(standardized,active,residual,alpha*ratio/yscale,alpha*(1-ratio))
    require(converged,'Regularized regression did not converge; increase the penalty')
    def predict(row): return yorigin+ymean+yscale*math.fsum(b*v for b,v in zip(beta,design(row)))
    return predict


def binary_auc(labels,scores):
    """Rank-based AUC with average ranks for ties; None when a class is missing."""
    positive=sum(1 for v in labels if v); count=len(labels)-positive
    if not positive or not count: return None
    order=sorted(range(len(labels)),key=scores.__getitem__)
    ranks=[0.0]*len(order); position=0
    while position<len(order):
        end=position
        while end+1<len(order) and scores[order[end+1]]==scores[order[position]]: end+=1
        average=(position+end)/2+1
        for index in range(position,end+1): ranks[order[index]]=average
        position=end+1
    return (sum(ranks[i] for i in range(len(labels)) if labels[i])-positive*(positive+1)/2)/(positive*count)


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
        effect=abs(number(a[0])); alpha=number(a[2]) if len(a)>2 else .05; kind=option(a,3,'independent'); sides=option(a,4,'two')
        require(effect>0 and 0<alpha<1 and kind in ('independent','paired','onesample'),'Positive Cohen d, alpha in (0,1), and independent/paired/onesample required')
        require(sides in ('two','greater','less'),'Alternative: two, greater, or less')
        engine.note += ' Exact noncentral-t power for standardized mean differences; equal independent groups. n is per group or number of pairs.'
        if name=='testpower':
            n=integer(a[1],2,10000000); return {'power':t_test_power(effect,n,alpha,kind,sides),'n per group / pairs':n,'alpha':alpha,'alternative':sides}
        target=number(a[1]) if len(a)>1 else .8
        # samplesize(d,target,alpha,kind,alternative), unlike testpower(d,n,alpha,kind,alternative).
        require(0<target<1,'Target power must lie in (0,1)')
        z=statistics.NormalDist().inv_cdf(1-alpha/(2 if sides=='two' else 1)); zpower=statistics.NormalDist().inv_cdf(target)
        estimate=int(math.ceil(2*((z+zpower)/effect)**2 if kind=='independent' else ((z+zpower)/effect)**2))
        require(estimate<=10000000,'Required sample size exceeds limit')
        low=max(2,estimate-8); high=max(2,estimate+8)
        if t_test_power(effect,low,alpha,kind,sides)>=target:
            low,high=2,max(2,estimate)
        else:
            while t_test_power(effect,high,alpha,kind,sides)<target:
                low=high+1; high*=2
                require(high<=20000000,'Required sample size exceeds limit')
        while low<high:
            middle=(low+high)//2
            if t_test_power(effect,middle,alpha,kind,sides)>=target: high=middle
            else: low=middle+1
        return {'n per group / pairs':low,'total n':low*2 if kind=='independent' else low,'achieved power':t_test_power(effect,low,alpha,kind,sides),'target power':target,'alpha':alpha,'alternative':sides}
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
        method=option(a,1,'mean'); require(method in ('mean','median','mode','regression','knn'),'Use mean, median, mode, regression, or knn')
        neighbors=integer(a[2],1,100) if len(a)>2 else 5
        missing=lambda v: str(v) in ('NA','nan')
        values=[[None if missing(v) else number(v) for v in row] for row in rows]
        for column in zip(*values): require(any(v is not None for v in column),'Cannot impute a completely missing column')
        count=sum(v is None for row in values for v in row)
        if method in ('mean','median','mode'):
            fills=[]
            for column in zip(*values):
                observed=[v for v in column if v is not None]
                fills.append(mean(observed) if method=='mean' else statistics.median(observed) if method=='median' else statistics.multimode(observed)[0])
            engine.note += ' Single columnwise imputation; does not account for imputation uncertainty. NA denotes missing. Mode ties use first appearance.'
            return {'data':[[fills[i] if v is None else v for i,v in enumerate(row)] for row in values],'fill values':fills,'imputed cells':count,'method':method}
        if method=='regression':
            filled,sweeps=regression_imputation(values)
            engine.note += ' Single regression imputation with iterated conditional means (chained equations); does not account for imputation uncertainty.'
            return {'data':filled,'imputed cells':count,'sweeps':sweeps,'method':method}
        filled,neighbors=neighbor_imputation(values,neighbors)
        engine.note += ' Single k-nearest-neighbour imputation on standardized observed coordinates; does not account for imputation uncertainty.'
        return {'data':filled,'imputed cells':count,'neighbors':neighbors,'method':method}
    rows=table(a[0]); n=len(rows); p=len(rows[0])
    if name=='crossvalidate':
        require(p>=2,'Rows: predictors then response')
        k=integer(a[1],2,n) if len(a)>1 else min(5,n); seed=integer(a[2],0,2**32-1) if len(a)>2 else 0
        split=option(a,3,'random'); require(split in ('random','blocked','stratified'),'Split: random, blocked, or stratified')
        model=option(a,4,'linear'); require(model in ('linear','ridge','lasso','elasticnet','logistic'),'Model: linear, ridge, lasso, elasticnet, or logistic')
        alpha,ratio=0.0,0.0
        if model in ('ridge','lasso','logistic'):
            alpha=number(a[5]) if len(a)>5 else .1; require(alpha>0,'Penalty alpha must be positive')
        elif model=='elasticnet':
            if len(a)>5:
                require(isinstance(a[5],(list,tuple)) and len(a[5])==2,'Elastic net options are [alpha,l1 ratio]')
                alpha=number(a[5][0]); ratio=number(a[5][1])
            else: alpha,ratio=.1,.5
            require(alpha>0 and 0<=ratio<=1,'Elastic net needs positive alpha and an L1 ratio from 0 to 1')
        responses=[row[-1] for row in rows]
        if split=='stratified': require(set(responses)<={0.0,1.0},'Stratified folds require a 0/1 response')
        generator=random.Random(seed)
        if split=='blocked':
            bounds=[f*n//k for f in range(k+1)]; folds=[list(range(bounds[f],bounds[f+1])) for f in range(k)]
        elif split=='stratified':
            assignment=[0]*n
            for label in (0.0,1.0):
                group=[i for i in range(n) if responses[i]==label]; generator.shuffle(group)
                for position,i in enumerate(group): assignment[i]=position%k
            folds=[[i for i in range(n) if assignment[i]==f] for f in range(k)]
        else:
            order=list(range(n)); generator.shuffle(order); folds=[order[f::k] for f in range(k)]
        predictions=[0.0]*n; errors=[]
        for fold in range(k):
            test=folds[fold]; member=set(test); train=[i for i in range(n) if i not in member]
            require(len(train)>p,'Each training fold needs more rows than predictors')
            if model=='linear':
                x,y=regression_data([rows[i] for i in train]); X=mp.matrix(x); b=inverse(X.T*X)*X.T*mp.matrix(y)
                for i in test: predictions[i]=float(dot([1]+rows[i][:-1],b))
                errors.append(mean([(rows[i][-1]-predictions[i])**2 for i in test]))
                continue
            fit=machine_fit([rows[i] for i in train],model,alpha,ratio)
            for i in test: predictions[i]=fit(rows[i])
            errors.append(mean([-(math.log(max(1e-12,predictions[i])) if rows[i][-1] else math.log(max(1e-12,1-predictions[i]))) for i in test]) if model=='logistic' else mean([(rows[i][-1]-predictions[i])**2 for i in test]))
        if model=='logistic':
            clipped=[min(1-1e-12,max(1e-12,v)) for v in predictions]
            result={'out-of-fold probabilities':predictions,'fold log loss':errors,'log loss':mean([-(math.log(clipped[i]) if responses[i] else math.log(1-clipped[i])) for i in range(n)]),'accuracy':mean([(v>=.5)==bool(responses[i]) for i,v in enumerate(predictions)]),'Brier score':mean([(responses[i]-predictions[i])**2 for i in range(n)]),'model':model,'split':split,'folds':k,'seed':seed}
            score=binary_auc(responses,predictions)
            if score is not None: result['AUC']=score
            engine.note += ' Out-of-fold validation of an L2-penalized logistic regression; folds use training rows only. Reports accuracy, AUC, log loss and the Brier score.'
            return result
        residuals=[(rows[i][-1]-predictions[i])**2 for i in range(n)]
        center=mean(responses); total=sum((value-center)**2 for value in responses)
        result={'out-of-fold predictions':predictions,'fold MSE':errors,'MSE':mean(residuals),'RMSE':math.sqrt(mean(residuals)),'MAE':mean([abs(rows[i][-1]-predictions[i]) for i in range(n)]),'model':model,'split':split,'folds':k,'seed':seed}
        if total>0: result['R2']=1-sum(residuals)/total
        engine.note += ' k-fold out-of-fold validation with training-only fits; blocked splits keep the row order. Grouped data still needs cluster-aware splits.'
        return result
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
