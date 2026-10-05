"""Regression inference and rank tests, shared by Android and Pyodide.

No SciPy dependency: high precision QR and probability tails use mpmath.
"""
import math
import mpmath as mp
import sympy as s
from calc_shared import MathError, flatten, require


def _numbers(data):
    values = flatten(data)
    require(values, "Enter at least one data value")
    require(all(getattr(v, "is_number", False) and v.is_real is True and v.is_finite is True
                for v in values), "Statistical values must be finite real numbers")
    return values


def _ranks(values):
    order = sorted(range(len(values)), key=lambda i: values[i])
    ranks, ties = [s.Rational(0)]*len(values), []
    start = 0
    while start < len(order):
        end = start + 1
        while end < len(order) and values[order[end]] == values[order[start]]: end += 1
        for i in order[start:end]: ranks[i] = s.Rational(start + 1 + end, 2)
        ties.append(end-start)
        start = end
    return ranks, ties


def rank_test(engine, name, args, tail):
    from calc_statistics import _normal_sf, _chisq_sf, _mp_result, _mpf
    require(all(isinstance(data,(list,tuple)) for data in args), "Rank tests require data lists")
    with mp.workdps(engine.precision + 10):
        if name == "wilcoxon":
            require(len(args) in (1, 2), "wilcoxon takes differences, or paired x and y lists")
            xs = _numbers(args[0])
            if len(args) == 2:
                ys = _numbers(args[1])
                require(len(xs) == len(ys), "Wilcoxon requires equal paired lengths")
                xs = [x-y for x, y in zip(xs, ys)]
            differences = [v for v in xs if v != 0]
            require(differences, "Wilcoxon needs at least one nonzero difference")
            ranks, _ = _ranks([abs(v) for v in differences])
            positive = sum(r for r, v in zip(ranks, differences) if v > 0)
            total = sum(ranks)
            statistic = min(positive, total-positive) if tail == "both" else positive
            if len(ranks) <= 50:
                # Conditional sign permutation; doubled midranks handle ties exactly.
                counts = [1]
                for rank in ranks:
                    step = int(2*rank)
                    updated = counts + [0]*step
                    for i, count in enumerate(counts): updated[i+step] += count
                    counts = updated
                observed = int(2*positive)
                left = mp.mpf(sum(counts[:observed+1]))/2**len(ranks)
                right = mp.mpf(sum(counts[observed:]))/2**len(ranks)
                p = left if tail == "left" else right if tail == "right" else min(1, 2*min(left, right))
                engine.note = "Exact conditional sign permutation; zero differences omitted."
            else:
                mean = _mpf(total, engine.precision)/2
                sd = mp.sqrt(_mpf(sum(r*r for r in ranks), engine.precision)/4)
                delta = _mpf(positive, engine.precision)-mean
                correction = -mp.mpf('.5') if tail == "left" else mp.mpf('.5') if tail == "right" else mp.sign(delta)*mp.mpf('.5')
                z = (delta-correction)/sd
                p = _normal_sf(-z) if tail == "left" else _normal_sf(z) if tail == "right" else 2*_normal_sf(max(0,(abs(delta)-mp.mpf('.5'))/sd))
                engine.note = "Normal approximation with tie and continuity corrections; zeros omitted."
            return {"W": statistic, "p value": _mp_result(p, engine), "n": s.Integer(len(ranks)),
                    "zero differences": s.Integer(len(xs)-len(ranks))}
        if name == "mannwhitney":
            require(len(args) == 2, "mannwhitney takes two independent data lists")
            xs, ys = _numbers(args[0]), _numbers(args[1])
            n, m = len(xs), len(ys)
            ranks, ties = _ranks(xs+ys)
            u = sum(ranks[:n])-s.Rational(n*(n+1), 2)
            if max(ties) == 1 and min(n, m) <= 8 and n+m <= 100:
                k = min(n, m)
                counts = [dict() for _ in range(k+1)]
                counts[0][0] = 1
                for rank in range(1, n+m+1):
                    for j in range(min(k, rank), 0, -1):
                        for subtotal, count in list(counts[j-1].items()):
                            counts[j][subtotal+rank] = counts[j].get(subtotal+rank, 0)+count
                distribution = {r-k*(k+1)//2: count for r, count in counts[k].items()}
                denominator = math.comb(n+m, k)
                left = mp.mpf(sum(c for r, c in distribution.items() if r <= u))/denominator
                right = mp.mpf(sum(c for r, c in distribution.items() if r >= u))/denominator
                p = left if tail == "left" else right if tail == "right" else min(1, 2*min(left, right))
                engine.note = "Exact Mann–Whitney distribution (no ties)."
            else:
                size = n+m
                variance = mp.mpf(n*m)/12*(size+1-mp.mpf(sum(t**3-t for t in ties))/(size*(size-1)))
                delta = _mpf(u, engine.precision)-mp.mpf(n*m)/2
                correction = -mp.mpf('.5') if tail == "left" else mp.mpf('.5') if tail == "right" else mp.sign(delta)*mp.mpf('.5')
                z = (delta-correction)/mp.sqrt(variance) if variance > 0 else mp.mpf(0)
                p = (mp.mpf(1) if variance == 0 else _normal_sf(-z) if tail == "left" else
                     _normal_sf(z) if tail == "right" else 2*_normal_sf(max(0,(abs(delta)-mp.mpf('.5'))/mp.sqrt(variance))))
                engine.note = "Normal approximation with tie and continuity corrections."
            return {"U": u, "p value": _mp_result(p, engine), "nx": s.Integer(n), "ny": s.Integer(m)}
        require(len(args) >= 2 and tail == "both", "kruskal takes at least two independent groups")
        groups = [_numbers(data) for data in args]
        ranks, ties = _ranks([v for group in groups for v in group])
        n, start, terms = len(ranks), 0, s.Integer(0)
        for group in groups:
            terms += sum(ranks[start:start+len(group)])**2/len(group)
            start += len(group)
        correction = 1-s.Rational(sum(t**3-t for t in ties), n**3-n)
        require(correction > 0, "Kruskal–Wallis needs variation between values")
        h = (12*terms/(n*(n+1))-3*(n+1))/correction
        engine.note = "Tie-corrected chi-square approximation; use at least five observations per group."
        return {"H": h, "df": s.Integer(len(groups)-1), "p value": _mp_result(_chisq_sf(_mpf(h, engine.precision), len(groups)-1), engine)}


def _covariance(design):
    """Column-scaled QR inverse of X'X; retain small parameter directions."""
    matrix = mp.matrix(design)
    p = matrix.cols
    scales = [mp.sqrt(mp.fsum(matrix[i,j]**2 for i in range(matrix.rows))) for j in range(p)]
    require(all(v > 0 for v in scales), "Regression parameters are not identifiable")
    scaled = mp.matrix([[matrix[i,j]/scales[j] for j in range(p)] for i in range(matrix.rows)])
    q, r = mp.qr(scaled, mode="skinny")
    require(all(abs(r[j,j]) > mp.mpf('1e-12') for j in range(p)), "Regression parameters are not identifiable")
    inverse = r**-1
    normalized = inverse*inverse.T
    return mp.matrix([[normalized[i,j]/(scales[i]*scales[j]) for j in range(p)] for i in range(p)])


def regression_report(engine, ys, predicted, design, names, coefficients,
                      inference_y=None, inference_predicted=None, transform=None, warning=None, approximate=False, information_inverse=None):
    from calc_statistics import _mpf, _quantile, _t_cdf, _t_sf, _shapiro_wilk
    with mp.workdps(engine.precision+15):
        number = lambda v: _mpf(v, engine.precision) if isinstance(v, s.Basic) else mp.mpf(v)
        out = lambda v: None if v is None or not mp.isfinite(v) else mp.nstr(v, engine.precision)
        y, fitted = list(map(number, ys)), list(map(number, predicted))
        residual = [a-b for a,b in zip(y, fitted)]
        n, p = len(y), len(names)
        df = n-p
        sse = mp.fsum(v*v for v in residual)
        average = mp.fsum(y)/n
        sst = mp.fsum((v-average)**2 for v in y)
        r2 = 1-sse/sst if sst > 0 else None
        report = {"n": n, "df": df, "confidence": .95, "rSquared": out(r2),
                  "adjustedRSquared": out(1-(1-r2)*(n-1)/df) if df>0 and r2 is not None else None,
                  "sse": out(sse), "rmse": out(mp.sqrt(sse/n)), "approximate": approximate,
                  "fitScale": "log(y)" if inference_y is not None else "y", "coefficients": [], "warnings": []}
        if sst == 0: report["warnings"].append("R² is undefined for a constant response.")
        if warning: report["warnings"].append(warning)
        if df <= 0: report["warnings"].append("Add more observations than coefficients for inference.")
        iy = list(map(number, inference_y)) if inference_y is not None else y
        ip = list(map(number, inference_predicted)) if inference_predicted is not None else fitted
        ir = [a-b for a,b in zip(iy,ip)]
        ise = mp.fsum(v*v for v in ir)
        if ise == 0: report["warnings"].append("Zero residual variance: coefficient tests are unavailable.")
        matrix = [[number(v) for v in row] for row in design]
        inverse = None
        if df > 0 and not warning:
            try: inverse = information_inverse if information_inverse is not None else _covariance(matrix)
            except (MathError, ValueError, ZeroDivisionError):
                report["warnings"].append("Regression parameters are not identifiable; inference unavailable.")
        sigma2 = ise/df if df>0 else None
        report["residualSE"] = out(mp.sqrt(sigma2)) if sigma2 is not None else None
        critical = _quantile(lambda t: _t_cdf(t, df), s.Rational(975,1000), engine, 0, 4) if inverse is not None else None
        for i, (name, value) in enumerate(zip(names, coefficients)):
            value = number(value)
            se = mp.sqrt(max(0, sigma2*inverse[i,i])) if inverse is not None else None
            low, high = (value-critical*se, value+critical*se) if se is not None else (None,None)
            pv = min(1, 2*_t_sf(abs(value/se), df)) if se is not None and se>0 else None
            if transform and transform[i] == "exp":
                # Intercept fitted in log units; delta-method SE, exponentiated CI.
                se = mp.exp(value)*se if se is not None else None
                value, low, high = mp.exp(value), mp.exp(low) if low is not None else None, mp.exp(high) if high is not None else None
                pv = None  # log-intercept test is A=1, not A=0.
            report["coefficients"].append({"name": str(name), "estimate": out(value), "se": out(se), "low": out(low), "high": out(high), "p": out(pv)})
        report["residuals"] = []
        for i, (actual, prediction, raw, error, row) in enumerate(zip(y,fitted,residual,ir,matrix)):
            leverage = (mp.matrix([row])*inverse*mp.matrix(row))[0] if inverse is not None else None
            denominator = sigma2*(1-leverage) if leverage is not None else None
            standard = error/mp.sqrt(denominator) if denominator is not None and denominator>0 else None
            cook = standard**2*leverage/(p*(1-leverage)) if standard is not None and leverage<1 else None
            report["residuals"].append({"row": i+1, "observed": out(actual), "fitted": out(prediction), "residual": out(raw),
                                        "standardized": out(standard), "leverage": out(leverage), "cook": out(cook)})
        report["durbinWatson"] = out(mp.fsum((a-b)**2 for a,b in zip(ir[1:],ir[:-1]))/ise) if ise>0 else None
        report["shapiroP"] = None
        if 3<=n<=5000 and ise>0:
            try: report["shapiroP"] = str(_shapiro_wilk([s.Float(str(v),15) for v in ir])[1])
            except (MathError, ValueError, OverflowError): pass
        engine.regression_report = report
        engine.regression_parameters = [[c["name"],c["estimate"]] for c in report["coefficients"]]


def expression_report(engine, rows, model, independent, names, coefficients, bounds=None):
    derivatives = [s.diff(model, parameter).subs(dict(zip(names, coefficients))) for parameter in names]
    fitted = model.subs(dict(zip(names,coefficients)))
    predicted = [fitted.subs(independent,row[0]) for row in rows]
    design = [[d.subs(independent,row[0]) for d in derivatives] for row in rows]
    bounded = bounds and any(lower != -s.oo or upper != s.oo for lower,upper in bounds)
    regression_report(engine, [row[1] for row in rows], predicted, design, names, coefficients,
                      warning="Bounded fit: ordinary coefficient inference is unavailable." if bounded else None,
                      approximate=True)


def _binary_roc(target, scores):
    """Empirical ROC and concordance AUC, grouping tied decision scores.

    Logits preserve score order even when probabilities round to zero or one.
    Linear interpolation through each tied block gives half credit to ties.
    """
    require(len(target)==len(scores) and target and all(y in (0,1) for y in target), "ROC requires paired binary labels and scores")
    positives=int(sum(target)); negatives=len(target)-positives
    require(positives>0 and negatives>0, "ROC requires both 0 and 1")
    require(all(mp.isfinite(score) for score in scores), "ROC scores must be finite numbers")
    order=sorted(range(len(scores)),key=lambda i:scores[i],reverse=True)
    points=[[mp.mpf(0),mp.mpf(0)]]
    true_positives=false_positives=start=0
    while start<len(order):
        end=start+1
        while end<len(order) and scores[order[end]]==scores[order[start]]: end+=1
        for index in order[start:end]:
            if target[index]==1:true_positives+=1
            else:false_positives+=1
        points.append([mp.mpf(false_positives)/negatives,mp.mpf(true_positives)/positives])
        start=end
    area=mp.fsum((right[0]-left[0])*(right[1]+left[1])/2 for left,right in zip(points,points[1:]))
    return area,points


def fit_multivariate(engine, rows, logistic=False):
    from calc_statistics import _mpf, _mp_result, _normal_sf, _quantile, _normal_cdf, _chisq_sf
    require(isinstance(rows,(list,tuple)) and len(rows)>=3 and all(isinstance(row,(list,tuple)) for row in rows), "Enter regression data rows")
    width = len(rows[0])
    require(2<=width<=9 and all(len(row)==width for row in rows), "Use one to eight predictors and the response in the last column")
    for row in rows: _numbers(row)
    require(len(rows)>width, "Add more data points than fit parameters")
    with mp.workdps(engine.precision+20):
        values = [[_mpf(v,engine.precision) for v in row] for row in rows]
        n, p = len(rows), width
        centers = [mp.fsum(row[j] for row in values)/n for j in range(p-1)]
        scales = [mp.sqrt(mp.fsum((row[j]-centers[j])**2 for row in values)/n) for j in range(p-1)]
        require(all(v>0 for v in scales), "Regression parameters are not identifiable")
        design = [[mp.mpf(1)]+[(row[j]-centers[j])/scales[j] for j in range(p-1)] for row in values]
        predictor_inverse = _covariance(design)
        target = [row[-1] for row in values]
        transform = mp.eye(p)
        for j in range(1,p): transform[0,j]=-centers[j-1]/scales[j-1]; transform[j,j]=1/scales[j-1]
        if logistic:
            require(all(v in (0,1) for v in target) and 0<sum(target)<n, "Logistic response must contain both 0 and 1")
            sigmoid = lambda v: 1/(1+mp.exp(-v)) if v>=0 else mp.exp(v)/(1+mp.exp(v))
            softplus = lambda v: max(v,0)+mp.log1p(mp.exp(-abs(v)))
            def evaluate(beta):
                linear = [mp.fsum(a*b for a,b in zip(row,beta)) for row in design]
                probabilities = [sigmoid(v) for v in linear]
                loss = mp.fsum(softplus(v)-y*v for v,y in zip(linear,target))
                return linear,probabilities,loss
            beta = mp.matrix([mp.log(sum(target)/(n-sum(target)))]+[0]*(p-1))
            tolerance = mp.power(10,-min(engine.precision,30))
            converged = False
            for _ in range(150):
                linear, probabilities, loss = evaluate(beta)
                # Finite fits may have extreme logits at distant observations.
                # Separation is rejected by rank loss or failure of Newton steps
                # to converge, rather than by an arbitrary probability cutoff.
                weighted = [[mp.sqrt(prob*(1-prob))*v for v in row] for row,prob in zip(design,probabilities)]
                covariance = _covariance(weighted)
                gradient = mp.matrix([mp.fsum(row[j]*(y-prob) for row,y,prob in zip(design,target,probabilities)) for j in range(p)])
                step = covariance*gradient
                if max(abs(v) for v in step)<tolerance:
                    converged=True; break
                rate = mp.mpf(1)
                while rate>mp.mpf('1e-10') and evaluate(beta+rate*step)[2]>=loss: rate/=2
                if rate<=mp.mpf('1e-10'):
                    if max(abs(v) for v in gradient)<mp.sqrt(tolerance): converged=True
                    break
                beta += rate*step
            require(converged, "Logistic fit is separated or did not converge; finite inference unavailable")
            linear,probabilities,loss = evaluate(beta)
            covariance = transform*_covariance([[mp.sqrt(prob*(1-prob))*v for v in row] for row,prob in zip(design,probabilities)])*transform.T
            coefficients = transform*beta
            critical = _quantile(_normal_cdf,s.Rational(975,1000),engine,0,4)
            mean = sum(target)/n
            null_loss = -mp.fsum(y*mp.log(mean)+(1-y)*mp.log(1-mean) for y in target)
            lr = max(0,2*(null_loss-loss))
            report = {"n":n,"df":n-p,"confidence":.95,"fitScale":"binomial","approximate":True,"warnings":[],
                      "pseudoRSquared":mp.nstr(1-loss/null_loss,engine.precision), "deviance":mp.nstr(2*loss,engine.precision),
                      "aic":mp.nstr(2*loss+2*p,engine.precision),"likelihoodRatio":mp.nstr(lr,engine.precision),
                      "likelihoodP":mp.nstr(_chisq_sf(lr,p-1),engine.precision),"coefficients":[],"residuals":[]}
            auc,roc=_binary_roc(target,linear)
            report["auc"]=mp.nstr(auc,engine.precision)
            report["roc"]=[[mp.nstr(x,engine.precision),mp.nstr(y,engine.precision)] for x,y in roc]
            for j,value in enumerate(coefficients):
                se=mp.sqrt(covariance[j,j]); low=value-critical*se; high=value+critical*se
                out=lambda v: mp.nstr(v,engine.precision)
                report["coefficients"].append({"name":"b0" if j==0 else "b"+str(j),"estimate":out(value),"se":out(se),
                                                "low":out(low),"high":out(high),"p":out(2*_normal_sf(abs(value/se))),
                                                "oddsRatio":out(mp.exp(value)),"oddsLow":out(mp.exp(low)),"oddsHigh":out(mp.exp(high))})
            for i,(y,prob,logit) in enumerate(zip(target,probabilities,linear)):
                out=lambda v:mp.nstr(v,engine.precision)
                dev=mp.sqrt(2*softplus(-logit)) if y==1 else -mp.sqrt(2*softplus(logit))
                pearson=mp.exp(-logit/2) if y==1 else -mp.exp(logit/2)
                report["residuals"].append({"row":i+1,"observed":out(y),"fitted":out(prob),"residual":out(y-prob),
                                            "standardized":out(pearson),"deviance":out(dev),"leverage":None,"cook":None})
            engine.regression_report=report
            engine.regression_parameters=[[c["name"],c["estimate"]] for c in report["coefficients"]]
        else:
            beta,_ = mp.qr_solve(mp.matrix(design),mp.matrix(target))
            coefficients=transform*beta
            predicted=[mp.fsum(a*b for a,b in zip(row,beta)) for row in design]
            # Original design provides covariance in the original predictor units.
            regression_report(engine,target,predicted,[[1]+row[:-1] for row in values],
                              ["b"+str(i) for i in range(p)],list(coefficients),
                              information_inverse=transform*predictor_inverse*transform.T)
        # Standardized predictors have centered sum of squares n. Therefore
        # diag((X'X)^-1)*n = 1/(1-R_j^2), using unweighted predictor OLS
        # for both linear and logistic models. The intercept has no VIF.
        for j, coefficient in enumerate(engine.regression_report["coefficients"]):
            coefficient["vif"] = None if j == 0 else mp.nstr(max(mp.mpf(1), n*predictor_inverse[j,j]),engine.precision)
        variables=[engine.symbol("x" if p==2 else "x"+str(j)) for j in range(1,p)]
        expression=_mp_result(coefficients[0],engine)+sum(_mp_result(coefficients[j],engine)*variables[j-1] for j in range(1,p))
        return 1/(1+s.exp(-expression)) if logistic else expression
