"""One-factor ANCOVA with multiple covariates and partial (Type II) tests."""
import math
import mpmath as mp
from calc_shared import MathError, require
from calc_statistics import _f_sf, _quantile, _t_cdf, _t_sf
from calc_advanced_common import dot, integer, mean, number, table
from calc_advanced_linear import least_squares


def calculate(engine, name, a):
    rows = table(a[0], 4, 3)
    level = number(a[1]) if len(a) > 1 else .95
    check = integer(a[2], 0, 1) if len(a) > 2 else 1
    require(0 < level < 1, 'Confidence level must be between 0 and 1')
    groups = sorted(set(row[0] for row in rows))
    require(len(groups) >= 2, 'Choose at least two groups')
    n, k, c = len(rows), len(groups), len(rows[0])-2
    centers = [mean(values) for values in zip(*(row[1:-1] for row in rows))]
    scales = [math.sqrt(math.fsum((row[j+1]-center)**2 for row in rows)/n) for j, center in enumerate(centers)]
    require(all(v > 0 for v in scales), 'Covariates must vary')
    z = [[(v-center)/scale for v, center, scale in zip(row[1:-1], centers, scales)] for row in rows]
    design = [[1.0]+[float(row[0] == group) for group in groups[1:]]+values for row, values in zip(rows, z)]
    ymean = mean([row[-1] for row in rows])
    y = [row[-1]-ymean for row in rows]
    beta, unscaled_cov, sse, _ = least_squares(design, y)
    p, df = len(beta), n-len(beta)
    total = math.fsum(v*v for v in y)
    require(total > 0 and sse > 1e-24*total, 'Positive residual variation is required for inference')
    mse = sse/df
    covariance = unscaled_cov*mse
    with mp.workdps(max(25, engine.precision+10)):
        critical = float(_quantile(lambda t: _t_cdf(t, df), (1+level)/2, engine, 0, 4))

    def effect(term, reduced, effect_df):
        _, _, reduced_sse, _ = least_squares(reduced, y)
        ss = max(0.0, reduced_sse-sse)
        f = ss/effect_df/mse
        return {'term':term, 'SS':ss, 'df':effect_df, 'MS':ss/effect_df, 'F':f,
                'p':float(_f_sf(f, effect_df, df)), 'partial eta2':ss/(ss+sse)}

    effects = [effect('Group', [[row[0]]+row[k:] for row in design], k-1)]
    for j in range(c):
        at = k+j
        effects.append(effect('x'+str(j+1), [row[:at]+row[at+1:] for row in design], 1))
    effects.append({'term':'Residual', 'SS':sse, 'df':df, 'MS':mse})
    adjusted = []
    for group in groups:
        at = [1.0]+[float(group == other) for other in groups[1:]]+[0.0]*c
        estimate = ymean+dot(at, beta)
        se = math.sqrt(max(0, float((mp.matrix([at])*covariance*mp.matrix(at))[0])))
        sample = [row[-1] for row in rows if row[0] == group]
        adjusted.append({'group':'group:'+format(group, '.15g'), 'n':len(sample), 'raw mean':mean(sample),
                         'adjusted mean':estimate, 'SE':se, 'Lower CI':estimate-critical*se, 'Upper CI':estimate+critical*se})
    coefficients = []
    for j in range(c):
        estimate, se = beta[k+j]/scales[j], math.sqrt(float(covariance[k+j, k+j]))/scales[j]
        coefficients.append({'term':'x'+str(j+1), 'estimate':estimate, 'SE':se,
                             'p':float(2*_t_sf(abs(estimate/se), df)),
                             'Lower CI':estimate-critical*se, 'Upper CI':estimate+critical*se})
    result = {'n':n, 'groups':k, 'covariates':c, 'df residual':df, 'confidence level':level,
              'R2':1-sse/total, 'ANCOVA table':effects, 'Adjusted means':adjusted,
              'Covariate coefficients':coefficients,
              'Covariate reference values':[{'term':'x'+str(j+1), 'mean':v} for j, v in enumerate(centers)]}
    if check:
        full = [row+[row[g]*row[k+j] for g in range(1, k) for j in range(c)] for row in design]
        try:
            _, _, interaction_sse, _ = least_squares(full, y)
            interaction_df = n-len(full[0])
            added = (k-1)*c
            ss = max(0.0, sse-interaction_sse)
            f = (ss/added)/(interaction_sse/interaction_df) if interaction_sse > 1e-24*total else math.inf
            pv = float(_f_sf(f, added, interaction_df)) if math.isfinite(f) else 0.0
            result['Slope homogeneity'] = {'F':f, 'df1':added, 'df2':interaction_df, 'p':pv,
                                          'status':'unequal slopes' if pv < 1-level else 'not rejected'}
        except MathError as error:
            result['Slope homogeneity'] = {'status':'unavailable', 'reason':str(error)}
        engine.note += ' Slope homogeneity compares the additive model with all group × covariate interactions.'
    engine.note += ' One categorical factor; common covariate slopes. Type II partial F tests; adjusted means at pooled covariate means. Independent observations and normal, equal-variance errors are assumed.'
    return result
