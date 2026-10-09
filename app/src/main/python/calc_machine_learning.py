"""Portable regression models shared by Chaquopy and Pyodide (no native dependencies)."""
from calc_limits import within_limit
import math
import random

import mpmath as mp
import sympy as s
from calc_shared import require


def _data(rows):
    from calc_inference import _numbers
    require(len(rows) >= 2 and len(rows[0]) >= 2 and
            all(len(row) == len(rows[0]) for row in rows),
            "Regression requires at least two complete predictor/response rows")
    require(within_limit(len(rows),5000) and within_limit(len(rows[0]),101),
            "Use at most 5000 observations and 100 predictors")
    for row in rows:
        _numbers(row)
    values = [[float(v) for v in row] for row in rows]
    require(all(math.isfinite(v) for row in values for v in row),
            "Regression data must be finite real numbers")
    return [row[:-1] for row in values], [row[-1] for row in values]


def _report(engine, ys, fitted, mode, coefficients=()):
    # These models do not have ordinary OLS standard errors or degrees of freedom.
    with mp.workdps(30):
        y, predictions = list(map(mp.mpf, ys)), list(map(mp.mpf, fitted))
        errors = [a-b for a, b in zip(y, predictions)]
        sse = mp.fsum(e*e for e in errors)
        mean = mp.fsum(y)/len(y)
        sst = mp.fsum((v-mean)**2 for v in y)
        out = lambda v: None if v is None else mp.nstr(v, 16)
        report = {"model": mode, "fitScale": mode, "n": len(y), "df": None,
                  "rSquared": out(1-sse/sst) if sst else None,
                  "rmse": out(mp.sqrt(sse/len(y))), "sse": out(sse),
                  "coefficients": [{"name": "b"+str(i), "estimate": out(v)}
                                   for i, v in enumerate(coefficients)],
                  "residuals": [{"row": i+1, "observed": out(a), "fitted": out(b),
                                 "residual": out(e)}
                                for i, (a, b, e) in enumerate(zip(y, predictions, errors))],
                  "warnings": []}
        if not sst:
            report["warnings"].append("R² is undefined for a constant response.")
    engine.regression_report = report
    engine.regression_parameters = [[c["name"], c["estimate"]] for c in report["coefficients"]]
    return report


def _linear_core(columns, active, target, penalty, ridge, beta=None, residual=None, tolerance=1e-8, limit=10000):
    """Cyclic coordinate descent for the standardized elastic-net linear problem."""
    size=len(target); values=[0.0]*len(columns) if beta is None else list(beta)
    errors=list(target) if residual is None else list(residual)
    converged=False
    for iteration in range(limit):
        for j, col in enumerate(columns):
            if not active[j]:
                continue
            correlation = math.fsum(v*r/size for v, r in zip(col, errors))+values[j]
            updated = math.copysign(max(abs(correlation)-penalty, 0.0), correlation)/(1+ridge)
            delta = updated-values[j]
            if delta:
                errors = [r-delta*v for r, v in zip(errors, col)]
                values[j] = updated
        violation = 0.0
        for b, col in zip(values, columns):
            gradient = math.fsum(v*r/size for v, r in zip(col, errors))
            violation = max(violation, abs(gradient-ridge*b-math.copysign(penalty, b)) if b
                            else max(abs(gradient)-penalty, 0.0))
        if violation <= tolerance:
            converged = True
            break
    return values, errors, iteration+1, converged


def _logistic_core(columns, active, ys, l1, l2, intercept=None, beta=None, tolerance=1e-8, limit=10000):
    """Cyclic coordinate descent for the standardized elastic-net logistic problem."""
    n, p = len(ys), len(columns)
    if intercept is None:
        prevalence = math.fsum(ys)/n
        intercept = math.log(prevalence/(1-prevalence))
    values = [0.0]*p if beta is None else list(beta)
    eta = [intercept+math.fsum(columns[j][i]*values[j] for j in range(p)) for i in range(n)]
    for iteration in range(limit):
        for j in range(-1, p):
            if j >= 0 and not active[j]:
                continue
            col = [1.0]*n if j < 0 else columns[j]
            old = intercept if j < 0 else values[j]
            gradient = math.fsum(v*(y-_sigmoid(z))/n for v, y, z in zip(col, ys, eta))
            candidate = old+gradient/0.25
            updated = candidate if j < 0 else math.copysign(max(abs(candidate)-l1/0.25, 0), candidate)/(1+l2/0.25)
            if updated != old:
                eta = [z+(updated-old)*v for z, v in zip(eta, col)]
                if j < 0: intercept = updated
                else: values[j] = updated
        residual = [y-_sigmoid(z) for y, z in zip(ys, eta)]
        violation = abs(math.fsum(residual)/n)
        for b, col in zip(values, columns):
            gradient = math.fsum(v*r/n for v, r in zip(col, residual))-l2*b
            violation = max(violation, abs(gradient-math.copysign(l1, b)) if b else max(abs(gradient)-l1, 0))
        if violation <= tolerance:
            return intercept, values, eta, iteration+1, True
    return intercept, values, eta, limit, False


def _select_alpha(xs, ys, columns, scales, ratio, penalty_mode, logistic):
    """Choose the penalty by shuffled five-fold cross-validation over a geometric path."""
    n, p = len(xs), len(columns)
    require(within_limit(n*p,20000), "Cross-validated alpha supports at most 20000 rows x predictors")
    folds = 5 if n >= 10 else 2
    order = list(range(n)); random.Random(0).shuffle(order)
    assignment = [0]*n
    for position, index in enumerate(order): assignment[index] = position % folds
    test_sets = [[i for i in range(n) if assignment[i] == fold] for fold in range(folds)]
    train_sets = [[i for i in range(n) if assignment[i] != fold] for fold in range(folds)]
    active = [scale > 0 for scale in scales]
    if logistic:
        mean = math.fsum(ys)/n; scaling = 1.0
        gradient = [math.fsum(columns[j][i]*(ys[i]-mean) for i in range(n))/n for j in range(p)]
    else:
        origin = ys[0]; offset = [v-origin for v in ys]; center = math.fsum(offset)/n
        target = [v-center for v in offset]; scaling = max(map(abs, target)) or 1.0
        scaled = [v/scaling for v in target]
        gradient = [math.fsum(columns[j][i]*scaled[i] for i in range(n))/n for j in range(p)]
    peak = max(map(abs, gradient)) or 1.0
    alphas = [peak*scaling/max(ratio, .05)*10**(-step/4) for step in range(17)]
    warm = {}; scores = []
    for alpha in alphas:
        l1 = alpha*ratio if logistic else alpha*ratio/scaling
        l2 = alpha*(1-ratio)
        total = 0.0; count = 0; failed = False
        for fold in range(folds):
            train = train_sets[fold]; test = test_sets[fold]
            training = [[column[i] for i in train] for column in columns]
            state = warm.get(fold)
            if logistic:
                values_target = [ys[i] for i in train]
                intercept, values, eta, steps, converged = _logistic_core(training, active, values_target, l1, l2,
                    intercept=None if state is None else state[0], beta=None if state is None else state[1], tolerance=1e-7)
                if not converged: failed = True; break
                warm[fold] = (intercept, list(values))
                for i in test:
                    linear = intercept+math.fsum(columns[j][i]*values[j] for j in range(p))
                    probability = min(1-1e-12, max(1e-12, _sigmoid(linear)))
                    total += -math.log(probability) if ys[i] else -math.log(1-probability)
                    count += 1
            else:
                fold_mean = math.fsum(ys[i] for i in train)/len(train)
                fitted = [(ys[i]-fold_mean)/scaling for i in train]
                residual = fitted if state is None else [fitted[k]-math.fsum(training[j][k]*state[j] for j in range(p)) for k in range(len(train))]
                values, errors, steps, converged = _linear_core(training, active, fitted, l1, l2,
                    beta=state, residual=residual, tolerance=1e-7)
                if not converged: failed = True; break
                warm[fold] = list(values)
                shift = math.fsum(values[j]*math.fsum(columns[j][i] for i in train)/len(train) for j in range(p))
                for i in test:
                    predicted = fold_mean+scaling*(math.fsum(columns[j][i]*values[j] for j in range(p))-shift)
                    total += (ys[i]-predicted)**2; count += 1
        scores.append([alpha, math.inf if failed or not count else total/count])
    usable = [entry for entry in scores if math.isfinite(entry[1])]
    require(usable, "Cross-validation did not converge for any penalty; increase the data or use a fixed alpha")
    chosen = min(usable, key=lambda entry: (entry[1], entry[0]))
    return chosen[0], {'alphaSelection': "cross-validation", 'cvFolds': folds, 'cvMetric': "deviance" if logistic else "mse",
                       'cvScores': [[entry[0], None if not math.isfinite(entry[1]) else entry[1]] for entry in scores]}


def fit_regularized(engine, rows, mode, options=None):
    logistic = mode.startswith("logistic")
    penalty_mode = mode.removeprefix("logistic")
    alpha, ratio = s.Rational(1, 10), s.Rational(1, 2)
    if options is not None:
        if penalty_mode == "elasticnet":
            require(isinstance(options, list) and len(options) == 2,
                    "Elastic Net options must be [alpha,l1_ratio]")
            alpha, ratio = options
        else:
            alpha = options
    xs, ys = _data(rows)
    cross_validated = str(alpha) == "cv"
    require(cross_validated or (getattr(alpha, "is_real", False) and alpha.is_finite and alpha > 0),
            "Regularization alpha must be a positive finite number or cv")
    alpha = 0.0 if cross_validated else float(alpha)
    require(cross_validated or (math.isfinite(alpha) and alpha > 0), "Regularization alpha must be a positive finite number or cv")
    require(getattr(ratio, "is_real", False) and ratio.is_finite and 0 <= ratio <= 1,
            "Elastic Net L1 ratio must be from 0 to 1")
    ratio = 0.0 if penalty_mode == "ridge" else 1.0 if penalty_mode == "lasso" else float(ratio)
    n, p = len(xs), len(xs[0])
    # Shift before centering to preserve small variation around large offsets.
    origins = xs[0]
    offsets = [[row[j]-origins[j] for j in range(p)] for row in xs]
    means = [math.fsum(row[j]/n for row in offsets) for j in range(p)]
    columns = [[row[j]-means[j] for row in offsets] for j in range(p)]
    scales = [max(map(abs, col)) for col in columns]
    scales = [scale*math.sqrt(math.fsum((v/scale)**2/n for v in col)) if scale else 0
              for col, scale in zip(columns, scales)]
    require(all(math.isfinite(v) for v in means+scales), "Regression data range is too large")
    columns = [[v/scale for v in col] if scale else [0.0]*n
               for col, scale in zip(columns, scales)]
    if cross_validated:
        alpha, cv = _select_alpha(xs, ys, columns, scales, ratio, penalty_mode, logistic)
    if logistic:
        expression = _fit_logistic(engine, xs, ys, columns, origins, means, scales, mode, alpha, ratio)
        if cross_validated: engine.regression_report.update(cv)
        return expression
    yorigin = ys[0]
    yoffset = [v-yorigin for v in ys]
    ymean = math.fsum(v/n for v in yoffset)
    target = [v-ymean for v in yoffset]
    yscale = max(map(abs, target)) or 1.0
    require(math.isfinite(yscale) and math.isfinite(ymean), "Regression data range is too large")
    residual = [v/yscale for v in target]
    penalty = alpha*ratio/yscale
    ridge = alpha*(1-ratio)
    beta, residual, iterations, converged = _linear_core(columns, [scale > 0 for scale in scales], residual, penalty, ridge)
    require(converged, "Regularized regression did not converge; increase alpha or remove nearly duplicate predictors")
    coefficients = [b*yscale/scale if scale else 0.0 for b, scale in zip(beta, scales)]
    intercept = math.fsum([yorigin, ymean]+[-b*(origin+mean)
                          for b, origin, mean in zip(coefficients, origins, means)])
    require(all(math.isfinite(v) for v in [intercept]+coefficients), "Regression coefficients exceed numeric range")
    fitted = [yorigin+ymean+(t-r)*yscale for t, r in zip([v/yscale for v in target], residual)]
    report = _report(engine, ys, fitted, mode, [intercept]+coefficients)
    report.update(alpha=alpha, l1Ratio=ratio, standardized=True, iterations=iterations,
                  selectedPredictors=sum(b != 0 for b in coefficients))
    if cross_validated: report.update(cv)
    variables = [engine.symbol("x" if p == 1 else "x"+str(j+1)) for j in range(p)]
    return s.Float(intercept, 16)+sum(s.Float(b, 16)*x for b, x in zip(coefficients, variables))


def _sigmoid(value):
    if value >= 0:
        return 1/(1+math.exp(-value))
    exp = math.exp(value)
    return exp/(1+exp)


def _fit_logistic(engine, xs, ys, columns, origins, means, scales, mode, alpha, ratio):
    from calc_inference import _binary_roc
    require(set(ys) == {0.0, 1.0}, "Logistic response must contain both 0 and 1")
    n, p = len(xs), len(columns)
    l1, l2 = alpha*ratio, alpha*(1-ratio)
    intercept, beta, eta, iterations, converged = _logistic_core(columns, [scale > 0 for scale in scales], ys, l1, l2)
    require(converged, "Regularized logistic regression did not converge; increase alpha")
    coefficients = [b/scale if scale else 0.0 for b, scale in zip(beta, scales)]
    original_intercept = math.fsum([intercept]+[-b*(origin+mean) for b, origin, mean in zip(coefficients, origins, means)])
    fitted = [_sigmoid(z) for z in eta]
    report = _report(engine, ys, fitted, mode, [original_intercept]+coefficients)
    # Predictor ORs use coefficients in original units. The intercept is baseline
    # odds, not a predictor odds ratio; penalization does not supply Wald intervals.
    with mp.workdps(30):
        for coefficient in report["coefficients"][1:]:
            coefficient["oddsRatio"] = mp.nstr(mp.exp(mp.mpf(coefficient["estimate"])), 16)
    report.update(alpha=alpha, l1Ratio=ratio, standardized=True, iterations=iterations,
                  selectedPredictors=sum(b != 0 for b in coefficients),
                  logLoss=math.fsum((max(z, 0)-y*z+math.log1p(math.exp(-abs(z))))/n for z, y in zip(eta, ys)),
                  accuracy=sum((v >= .5) == bool(y) for v, y in zip(fitted, ys))/n)
    report.pop("rSquared", None)
    auc, roc = _binary_roc(list(map(mp.mpf, ys)), list(map(mp.mpf, eta)))
    report.update(auc=str(auc), roc=[[str(x), str(y)] for x, y in roc])
    from calc_logistic_diagnostics import logistic_diagnostics
    active = [j for j in range(p) if scales[j] and (ratio == 0 or beta[j] != 0)]
    design = [[1]+[columns[j][i] for j in active] for i in range(n)]
    logistic_diagnostics(report, design, ys, eta, 16, l2=l2)
    report["influenceMethod"] = "glm-penalized-approximate"
    report["warnings"].append("Penalized influence diagnostics are local approximations with selected predictors held fixed.")
    variables = [engine.symbol("x" if p == 1 else "x"+str(j+1)) for j in range(p)]
    expression = s.Float(original_intercept, 16)+sum(s.Float(b, 16)*x for b, x in zip(coefficients, variables))
    return 1/(1+s.exp(-expression))


def predict_tree(tree, row):
    while len(tree) != 1:
        feature, threshold, left, right = tree
        tree = left if row[feature] <= threshold else right
    return tree[0]


def classification_metrics(labels, probabilities):
    """Binary discrimination and threshold metrics; undefined ratios stay None."""
    from calc_inference import _binary_roc
    tn = fp = fn = tp = 0
    for actual, probability in zip(labels, probabilities):
        predicted = probability >= .5
        if actual == 1:
            if predicted: tp += 1
            else: fn += 1
        else:
            if predicted: fp += 1
            else: tn += 1
    count = len(labels)
    result = {"n": count, "threshold": .5, "positiveClass": 1,
              "confusionMatrix": [[tn, fp], [fn, tp]],
              "sensitivity": tp/(tp+fn) if tp+fn else None,
              "specificity": tn/(tn+fp) if tn+fp else None,
              "accuracy": (tp+tn)/count if count else None,
              "auc": None, "cStatistic": None, "roc": []}
    if set(labels) == {0, 1}:
        with mp.workdps(30):
            auc, roc = _binary_roc(list(map(mp.mpf, labels)), list(map(mp.mpf, probabilities)))
            result.update(auc=str(auc), cStatistic=str(auc), roc=[[str(x), str(y)] for x, y in roc])
    return result


def fit_random_forest(engine, rows, options=None, task="auto"):
    xs, ys = _data(rows)
    classification = task == "classification" or task == "auto" and set(ys).issubset({0, 1})
    if classification:
        require(set(ys) == {0, 1}, "Random Forest classification requires both 0 and 1 in the response")
    options = [s.Integer(100), s.Integer(10), s.Integer(0)] if options is None else options
    require(isinstance(options, list) and len(options) == 3,
            "Random Forest options must be [trees,max_depth,seed]")
    require(all(getattr(v, "is_Integer", False) for v in options),
            "Random Forest options must be integers")
    trees, depth, seed = map(int, options)
    require(1 <= trees and within_limit(trees,200) and 1 <= depth and within_limit(depth,20) and 0 <= seed <= 2147483647,
            "Use 1–200 trees, depth 1–20, and seed 0–2147483647")
    n, p = len(xs), len(xs[0])
    origin = 0.0 if classification else ys[0]
    offsets = [v-origin for v in ys]
    center = 0.0 if classification else math.fsum(v/n for v in offsets)
    scale = 1.0 if classification else max(abs(v-center) for v in offsets) or 1.0
    require(math.isfinite(center) and math.isfinite(scale), "Regression data range is too large")
    target = [(v-center)/scale for v in offsets]
    rng = random.Random(seed)
    importance = [0.0]*p
    features_per_split = max(1, math.isqrt(p))

    def grow(indices, level):
        total = math.fsum(target[i] for i in indices)
        count = len(indices)
        mean = total/count
        # For binary targets, squared-error decrease is half the Gini decrease.
        # It selects the same split, and leaf means are class-1 probabilities.
        parent = math.fsum((target[i]-mean)**2 for i in indices)
        if level >= depth or count < 2 or parent <= 1e-15:
            return (mean,)
        best = None
        gain = 0.0
        # If the sampled features are all constant, try remaining features.
        order = rng.sample(range(p), p)
        for position, feature in enumerate(order):
            sorted_rows = sorted(indices, key=lambda i: xs[i][feature])
            left_sum = left_square = 0.0
            squares = math.fsum(target[i]**2 for i in indices)
            for split in range(1, count):
                i, following = sorted_rows[split-1], sorted_rows[split]
                left_sum += target[i]
                left_square += target[i]**2
                if xs[i][feature] == xs[following][feature]:
                    continue
                right_count = count-split
                error = max(0.0, left_square-left_sum**2/split)+max(
                    0.0, squares-left_square-(total-left_sum)**2/right_count)
                improvement = parent-error
                if improvement > gain+1e-15:
                    threshold = xs[i][feature]/2+xs[following][feature]/2
                    # A rounded midpoint must still separate distinct values.
                    if threshold >= xs[following][feature]:
                        threshold = xs[i][feature]
                    best = (feature, threshold, sorted_rows, split)
                    gain = improvement
            if position+1 >= features_per_split and best is not None:
                break
        if best is None:
            return (mean,)
        feature, threshold, sorted_rows, split = best
        importance[feature] += gain
        return (feature, threshold, grow(sorted_rows[:split], level+1), grow(sorted_rows[split:], level+1))

    forest = []
    bags = []
    oob_sum, oob_count = [0.0]*n, [0]*n
    for _ in range(trees):
        indices = [rng.randrange(n) for _ in range(n)]
        tree = grow(indices, 0)
        forest.append(tree)
        sampled = set(indices)
        bags.append(sampled)
        for i in range(n):
            if i not in sampled:
                oob_sum[i] += predict_tree(tree, xs[i])
                oob_count[i] += 1

    def predict(row):
        return origin+center+scale*math.fsum(predict_tree(tree, row)/trees for tree in forest)

    fitted = [predict(row) for row in xs]
    report = _report(engine, ys, fitted, "randomforest")
    total_importance = math.fsum(importance)
    report.update(trees=trees, maxDepth=depth, seed=seed, maxFeatures=features_per_split,
                  task="classification" if classification else "regression",
                  featureImportance=[{"name": "b"+str(i+1), "estimate": v/total_importance if total_importance else 0.0}
                                     for i, v in enumerate(importance)])
    covered = [i for i, count in enumerate(oob_count) if count]
    report["oobN"] = len(covered)
    with mp.workdps(30):
        actual = [mp.mpf(ys[i]) for i in covered]
        estimates = [mp.mpf(origin)+center+scale*oob_sum[i]/oob_count[i] for i in covered]
        error = mp.fsum((a-b)**2 for a, b in zip(actual, estimates))
        average = mp.fsum(actual)/len(actual) if actual else 0
        variance = mp.fsum((v-average)**2 for v in actual)
        report["oobRMSE"] = mp.nstr(mp.sqrt(error/len(actual)), 16) if actual else None
        report["oobRSquared"] = mp.nstr(1-error/variance, 16) if len(actual) >= 2 and variance else None
    if classification:
        report.update(classification_metrics(ys, fitted))
        report["oobClassification"] = classification_metrics([ys[i] for i in covered],
                                                           [oob_sum[i]/oob_count[i] for i in covered])
        oob = report["oobClassification"]
        report.update(oobAuc=oob["auc"], oobCStatistic=oob["cStatistic"],
                      oobSensitivity=oob["sensitivity"], oobSpecificity=oob["specificity"], oobAccuracy=oob["accuracy"])
        for key in ("rSquared", "rmse", "sse", "oobRSquared", "oobRMSE"):
            report.pop(key, None)
        if oob["auc"] is None:
            report["warnings"].append("OOB ROC/AUC requires both classes among observations with OOB predictions; increase tree count.")
    if len(covered) < n:
        report["warnings"].append("OOB metrics exclude observations with no out-of-bag prediction; increase tree count.")
    # Measure decrease in OOB R², reusing only trees that did not train on each row.
    # A deterministic sample caps work on mobile and in WASM.
    permutation_rng = random.Random(seed+1)
    evaluation = sorted(permutation_rng.sample(covered, min(200, len(covered))))
    if classification and evaluation and set(ys[i] for i in evaluation) != {0, 1} and set(ys[i] for i in covered) == {0, 1}:
        missing = 1-ys[evaluation[0]]
        evaluation[-1] = permutation_rng.choice([i for i in covered if ys[i] == missing])
        evaluation.sort()
    eligible = {i: [tree for tree, bag in zip(forest, bags) if i not in bag] for i in evaluation}
    baseline = math.fsum((target[i]-oob_sum[i]/oob_count[i])**2 for i in evaluation)
    average = math.fsum(target[i] for i in evaluation)/len(evaluation) if evaluation else 0
    variance = math.fsum((target[i]-average)**2 for i in evaluation)
    if classification and variance:
        sample_labels = [ys[i] for i in evaluation]
        baseline_auc = float(classification_metrics(sample_labels, [oob_sum[i]/oob_count[i] for i in evaluation])["auc"])
    permutation = []
    for feature in range(p):
        errors = []
        if variance:
            for _ in range(3):
                shuffled = [xs[i][feature] for i in evaluation]
                permutation_rng.shuffle(shuffled)
                error = 0.0
                predictions = []
                for i, value in zip(evaluation, shuffled):
                    row = xs[i].copy()
                    row[feature] = value
                    prediction = math.fsum(predict_tree(tree, row)/len(eligible[i]) for tree in eligible[i])
                    predictions.append(prediction)
                    error += (target[i]-prediction)**2
                errors.append(baseline_auc-float(classification_metrics(sample_labels, predictions)["auc"])
                              if classification else (error-baseline)/variance)
        permutation.append({"name": "b"+str(feature+1), "estimate": math.fsum(errors)/3 if errors else None})
    report.update(permutationImportance=permutation, permutationN=len(evaluation), permutationRepeats=3,
                  permutationMetric="auc" if classification else "rSquared")
    engine.regression_predict = predict
    # Forests are displayed as model summaries, never as a reusable algebraic expression.
    return s.Symbol("RandomForest")
