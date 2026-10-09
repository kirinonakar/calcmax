"""Proper-prior regression shared by Android and Pyodide, using mpmath only.

Priors are on the centered, RMS-standardized design, including the intercept.
Linear: beta|sigma² ~ N(0, sd² sigma² I), sigma² ~ InvGamma(shape, scale).
Logistic: beta ~ N(0, sd² I); Gaussian Laplace posterior at the MAP.
"""
from calc_limits import within_limit, limits_removed
import math
import mpmath as mp
import sympy as s
from calc_shared import MathError, require


def fit_bayesian(engine, rows, mode, options=None):
    from calc_inference import _numbers, _binary_roc
    from calc_statistics import _mpf, _t_sf, _quantile
    binary = mode == 'bayeslogistic'
    require(isinstance(rows, (list, tuple)) and len(rows) >= 2 and
            all(isinstance(row, (list, tuple)) and len(row) >= 2 for row in rows),
            'Regression requires at least two complete predictor/response rows')
    require(within_limit(len(rows),5000) and within_limit(len(rows[0]),21) and
            all(len(row) == len(rows[0]) for row in rows),
            'Bayesian regression supports 5000 rows and 20 predictors with equal column counts')
    for row in rows: _numbers(row)
    args = [] if options is None else list(options) if isinstance(options, (list, tuple)) else [options]
    sampler = args.pop() if args and isinstance(args[-1], (list, tuple)) else None
    if sampler is not None:
        require(1 <= len(sampler) <= 6 and str(sampler[0]) == 'nuts',
                'Sampler options must be [nuts, samples, warmup, max_depth, seed, chains]')
        from calc_advanced_common import integer
        bounds = [(100,5000),(50,5000),(1,10),(0,2147483647),(2,4)]
        nuts_options = [integer(v,*bound) for v,bound in zip(sampler[1:],bounds)]
        nuts_options += [500,500,8,0,2][len(nuts_options):]
    require(len(args) <= (2 if binary else 4),
            'Use [priorSD, credibleLevel] or linear [priorSD, credibleLevel, varianceShape, varianceScale]')
    _numbers(args or [s.Integer(1)])
    with mp.workdps(max(40, engine.precision+10)):
        settings = [_mpf(v, engine.precision+10) for v in args]
        sd, level, shape, scale = (settings+[mp.mpf(v) for v in ('2.5', '.95', '2', '1')[len(settings):]])
        require(sd > 0 and (limits_removed() or mp.mpf('1e-6') <= sd <= mp.mpf('1e6')), 'Prior SD must be between 0.000001 and 1000000')
        require(0 < level < 1, 'Credible level must be between 0 and 1')
        require(shape > 0 and scale > 0, 'Variance prior shape and scale must be positive')
        data = [[_mpf(v, engine.precision+10) for v in row] for row in rows]
        n, p = len(data), len(data[0])
        ys = [row[-1] for row in data]
        centers = [mp.fsum(row[j] for row in data)/n for j in range(p-1)]
        scales = [mp.sqrt(mp.fsum((row[j]-centers[j])**2 for row in data)/n) or mp.mpf(1) for j in range(p-1)]
        design = mp.matrix([[1]+[(row[j]-centers[j])/scales[j] for j in range(p-1)] for row in data])
        transform = mp.eye(p)
        for j in range(1, p):
            transform[0,j] = -centers[j-1]/scales[j-1]
            transform[j,j] = 1/scales[j-1]
        precision = 1/sd**2
        out = lambda v: mp.nstr(v, engine.precision)
        report = {'model': mode, 'bayesian': True, 'n': n, 'df': None,
                  'credibleLevel': float(level), 'priorSD': out(sd),
                  'fitScale': 'binomial' if binary else 'y',
                  'method': 'laplace' if binary else 'conjugate',
                  'approximate': binary, 'coefficients': [], 'residuals': [], 'warnings': []}
        if binary:
            require(set(ys) == {0, 1}, 'Bayesian logistic response must contain both 0 and 1')
            def evaluate(beta):
                logits = list(design*beta)
                probs = [mp.exp(-max(-z, 0))/(1+mp.exp(-abs(z))) for z in logits]
                loss = mp.fsum(max(z, 0)-y*z+mp.log1p(mp.exp(-abs(z))) for z,y in zip(logits,ys))
                return logits, probs, loss+precision*mp.fsum(b*b for b in beta)/2
            beta = mp.zeros(p, 1)
            for iteration in range(100):
                logits, fitted, objective = evaluate(beta)
                weights = [mp.exp(-abs(z))/(1+mp.exp(-abs(z)))**2 for z in logits]
                information = design.T*mp.matrix([[weights[i]*design[i,j] for j in range(p)] for i in range(n)])+precision*mp.eye(p)
                score = design.T*mp.matrix([prob-y for prob,y in zip(fitted,ys)])+precision*beta
                step = mp.lu_solve(information, score)
                if max(abs(v) for v in step) < mp.mpf('1e-12')*(1+max(abs(v) for v in beta)): break
                fraction = mp.mpf(1)
                for _ in range(60):
                    candidate = beta-fraction*step
                    if evaluate(candidate)[2] <= objective-mp.mpf('0.0001')*fraction*(score.T*step)[0]: break
                    fraction /= 2
                else: raise MathError('Bayesian logistic regression did not converge')
                beta = candidate
            else: raise MathError('Bayesian logistic regression did not converge in 100 iterations')
            covariance = information**-1
            coefficient_covariance = transform*covariance*transform.T
            critical = mp.sqrt(2)*mp.erfinv(level)
            report['iterations'] = iteration+1
        else:
            covariance = (design.T*design+precision*mp.eye(p))**-1
            beta = covariance*design.T*mp.matrix(ys)
            fitted = list(design*beta)
            # Positive residual form avoids cancellation in y'y-beta'V^-1 beta.
            posterior_shape = shape+mp.mpf(n)/2
            posterior_scale = scale+(mp.fsum((y-f)**2 for y,f in zip(ys,fitted))+precision*mp.fsum(b*b for b in beta))/2
            variance_mean = posterior_scale/(posterior_shape-1)
            coefficient_covariance = transform*covariance*transform.T
            critical = _quantile(lambda x: 1-_t_sf(x, 2*posterior_shape), s.Float(str((1+level)/2), engine.precision+10), engine, 0, 4)
            report.update(varianceShape=out(shape), varianceScale=out(scale),
                          posteriorVarianceShape=out(posterior_shape), posteriorVarianceScale=out(posterior_scale),
                          posteriorVarianceMean=out(variance_mean), posteriorDF=out(2*posterior_shape))
        exact_beta = beta.copy()
        sampled_coefficients = None
        if sampler is not None:
            from calc_nuts import sample, diagnostics, quantile
            samples,warmup,max_depth,seed,chains = nuts_options
            require(within_limit(n*p*chains*(samples+warmup),200000000),
                    'NUTS workload exceeds 200 million row/parameter gradient evaluations; reduce data or sampler settings')
            # Whiten with the local covariance; the sampled target still uses
            # the full posterior, not the Gaussian approximation.
            local_covariance = covariance if binary else covariance*variance_mean
            cholesky = mp.cholesky(local_covariance)
            lower = [[float(cholesky[i,j]) for j in range(p)] for i in range(p)]
            location = list(map(float,beta)); xs = [[float(v) for v in row] for row in design.tolist()]
            targets = list(map(float,ys)); prior_precision = float(precision)
            def unwhiten(z):
                return [location[i]+math.fsum(lower[i][j]*z[j] for j in range(i+1)) for i in range(p)]
            if not binary:
                factor = float(posterior_shape)+p/2
                radial_scale = float(posterior_shape)-1
            def target(z):
                if not binary:
                    # Whitening the exact covariance makes the Student-t target
                    # radial, avoiding cancellation for collinear predictors.
                    quadratic = math.fsum(v*v for v in z)
                    return factor*math.log1p(quadratic/(2*radial_scale)), [factor*v/(radial_scale+quadratic/2) for v in z]
                b = unwhiten(z)
                value = prior_precision*math.fsum(v*v for v in b)/2
                gradient = [prior_precision*v for v in b]
                for row,y in zip(xs,targets):
                    eta = math.fsum(v*w for v,w in zip(row,b))
                    value += max(eta,0)-y*eta+math.log1p(math.exp(-abs(eta)))
                    probability = math.exp(-max(-eta,0))/(1+math.exp(-abs(eta)))
                    for j,v in enumerate(row): gradient[j] += v*(probability-y)
                return value,[math.fsum(lower[i][j]*gradient[i] for i in range(j,p)) for j in range(p)]
            draws, nuts = sample(target,p,*nuts_options,max_evaluations=200000000//(n*p))
            # Diagnostics and intervals use coefficients in the visible units.
            transformation = [[float(v) for v in row] for row in transform.tolist()]
            beta_chains = [[unwhiten(z) for z in chain] for chain in draws]
            coefficient_chains = [[[math.fsum(v*w for v,w in zip(row,b)) for row in transformation]
                                   for b in chain] for chain in beta_chains]
            sampled_coefficients = []
            for j in range(p):
                parameter_chains = [[row[j] for row in chain] for chain in coefficient_chains]
                values = [v for chain in parameter_chains for v in chain]
                average = math.fsum(values)/len(values)
                deviation = math.sqrt(math.fsum((v-average)**2 for v in values)/(len(values)-1))
                rhat,ess = diagnostics(parameter_chains)
                sampled_coefficients.append({'name':'b'+str(j),'estimate':out(average),'posteriorSD':out(deviation),
                    'low':out(quantile(values,float((1-level)/2))), 'high':out(quantile(values,float((1+level)/2))),
                    'probabilityPositive':out(sum(v>0 for v in values)/len(values)),
                    'rHat':None if rhat is None else out(rhat),'ess':out(ess),'mcse':out(deviation/math.sqrt(ess)) if ess else None})
            beta = mp.matrix([math.fsum(b[j] for chain in beta_chains for b in chain)/(samples*chains) for j in range(p)])
            report.update(method='nuts',approximate=True,nuts=nuts)
            if nuts['maxTreeDepthHits']: report['warnings'].append('NUTS reached max tree depth; increase max tree depth and inspect mixing.')
            if nuts['divergences']: report['warnings'].append('NUTS divergences detected; posterior summaries may be unreliable.')
            if any(c['rHat'] is None or float(c['rHat']) > 1.05 for c in sampled_coefficients):
                report['warnings'].append('NUTS split R-hat exceeds 1.05 or is unavailable; increase warmup and samples.')
            if any(float(c['ess']) < 100 for c in sampled_coefficients):
                report['warnings'].append('NUTS effective sample size is below 100; increase samples and inspect mixing.')
            if binary:
                logits, fitted, _ = evaluate(beta)
            else: fitted = list(design*beta)
        if binary:
            auc,roc = _binary_roc(ys,logits)
            loss = mp.fsum(max(z,0)-y*z+mp.log1p(mp.exp(-abs(z))) for z,y in zip(logits,ys))
            counts = [[0,0],[0,0]]
            for y,z in zip(ys,logits): counts[int(y)][int(z>=0)] += 1
            tn,fp = counts[0]; fn,tp = counts[1]
            report.update(auc=out(auc),roc=[[out(x),out(y)] for x,y in roc],logLoss=out(loss/n),deviance=out(2*loss),
                          confusionMatrix=counts,threshold=.5,accuracy=out(mp.mpf(tn+tp)/n),
                          sensitivity=out(mp.mpf(tp)/(tp+fn)),specificity=out(mp.mpf(tn)/(tn+fp)))
        coefficients = transform*beta
        for j,b in enumerate(coefficients):
            if sampled_coefficients is not None:
                c = sampled_coefficients[j]
                low,high = mp.mpf(c['low']),mp.mpf(c['high'])
            elif binary:
                posterior_sd = interval_scale = mp.sqrt(coefficient_covariance[j,j])
                probability = (1+mp.erf(b/posterior_sd/mp.sqrt(2)))/2
            else:
                interval_scale = mp.sqrt(posterior_scale/posterior_shape*coefficient_covariance[j,j])
                posterior_sd = mp.sqrt(variance_mean*coefficient_covariance[j,j])
                probability = _t_sf(-b/interval_scale, 2*posterior_shape)
            if sampled_coefficients is None:
                low,high = b-critical*interval_scale,b+critical*interval_scale
                c = {'name': 'b'+str(j), 'estimate': out(b), 'posteriorSD': out(posterior_sd),
                     'low': out(low), 'high': out(high), 'probabilityPositive': out(probability)}
            if binary: c.update(oddsRatio=out(mp.exp(b)), oddsLow=out(mp.exp(low)), oddsHigh=out(mp.exp(high)))
            report['coefficients'].append(c)
        for i,(y,f) in enumerate(zip(ys,fitted)):
            residual = {'row': i+1, 'observed': out(y), 'fitted': out(f), 'residual': out(y-f)}
            if not binary:
                row = design[i,:]
                width = critical*mp.sqrt(posterior_scale/posterior_shape*(1+(row*covariance*row.T)[0]))
                exact_fitted = (row*exact_beta)[0]
                residual.update(predictiveLow=out(exact_fitted-width), predictiveHigh=out(exact_fitted+width))
            report['residuals'].append(residual)
        if binary:
            from calc_logistic_diagnostics import logistic_diagnostics
            if sampler is not None:
                weights = [mp.exp(-abs(z))/(1+mp.exp(-abs(z)))**2 for z in logits]
                covariance = (design.T*mp.matrix([[weights[i]*design[i,j] for j in range(p)] for i in range(n)])+precision*mp.eye(p))**-1
            logistic_diagnostics(report, design.tolist(), ys, logits, engine.precision, inverse=covariance)
            report['influenceMethod'] = 'bayesian-posterior-mean-glm-approximate' if sampler is not None else 'bayesian-map-glm-approximate'
        else:
            sse = mp.fsum((y-f)**2 for y,f in zip(ys,fitted))
            average = mp.fsum(ys)/n
            total = mp.fsum((y-average)**2 for y in ys)
            report.update(rSquared=out(1-sse/total) if total else None, rmse=out(mp.sqrt(sse/n)))
            if not total: report['warnings'].append('R² is undefined for a constant response.')
        engine.regression_report = report
        engine.regression_parameters = [[c['name'],c['estimate']] for c in report['coefficients']]
        # Preserve centered evaluation when original predictors have large offsets.
        engine.regression_predict = lambda xs: float((lambda z: mp.exp(-max(-z,0))/(1+mp.exp(-abs(z))) if binary else z)(
            beta[0]+mp.fsum(beta[j+1]*(mp.mpf(v)-centers[j])/scales[j] for j,v in enumerate(xs))))
        variables = [engine.symbol('x' if p == 2 else 'x'+str(j)) for j in range(1,p)]
        expression = s.Float(out(coefficients[0]),engine.precision)+sum(s.Float(out(b),engine.precision)*x for b,x in zip(list(coefficients)[1:],variables))
        engine.note = ('Bayesian logistic regression: Gaussian Laplace approximation at the MAP; training probabilities evaluated at the MAP.' if binary else
                       'Bayesian linear regression: normal-inverse-gamma prior; exact Student-t marginal posterior and posterior predictive intervals.')+' Zero-mean priors include the intercept on the centered, RMS-standardized design; coefficients are in original units. Equal-tailed credible intervals.'
        if sampler is not None:
            engine.note = 'Bayesian regression: slice NUTS with recursive doubling, U-turn termination, warmup-only dual averaging and multiple seeded chains; empirical equal-tailed credible intervals. Classical split R-hat and autocorrelation ESS. Priors include the intercept on centered, RMS-standardized predictors; coefficients in original units. Training predictions evaluated at posterior mean coefficients.'
            if not binary: engine.note += ' Error variance integrated out; predictive intervals from the exact conjugate posterior.'
        return 1/(1+s.exp(-expression)) if binary else expression
