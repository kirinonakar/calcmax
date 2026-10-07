"""Count, multinomial and ordinal regression with analytic model inference."""
import math
import mpmath as mp
from calc_shared import MathError, require
from calc_advanced_common import (
    dot, inference, logistic, mean, newton, regression_data,
    standardized_design, table,
)


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


def calculate(engine, name, a):
    if name in ('multinomial','ordinal','poissonreg','nbreg'):
        engine.note += {'ordinal':' Proportional-odds cumulative logit; ascending numeric categories; analytic score and observed information.', 'multinomial':' Multinomial logit; smallest category is reference.', 'poissonreg':' Poisson log-link regression; exp(coef) is incidence rate ratio.', 'nbreg':' Negative binomial NB2 log-link; dispersion alpha is jointly estimated; analytic score and observed information.'}[name]+' Rows: predictors then response. Wald 95% CI.'
        return model(table(a[0],3,2),name)
    raise MathError('Unknown advanced analysis')
