"""Development-only statsmodels references; never an application dependency."""
import json
from pathlib import Path
import sys
if len(sys.argv) > 1: sys.path.insert(0, sys.argv[1])
import numpy as np
import pandas as pd
import scipy
import statsmodels
import statsmodels.api as sm
import statsmodels.formula.api as smf

ROOT = Path(__file__).resolve().parents[1]
fixtures = []


def add(name, function, arguments, expected, tolerance=2e-6):
    fixtures.append(dict(name=name, function=function, arguments=arguments, expected=expected,
                         tolerance=tolerance, source=f'statsmodels {statsmodels.__version__}; SciPy {scipy.__version__}'))


rng = np.random.default_rng(1008)
for c in (1, 2):
    rows = []
    for group, count in [(1, 8), (2, 11), (3, 7)]:
        for _ in range(count):
            covariates = rng.normal(group*.4, 1.2, c)
            y = 2+group*.7+covariates @ np.arange(1, c+1)+rng.normal(0, .6)
            rows.append([group]+covariates.tolist()+[float(y)])
    columns = ['group']+['x'+str(j+1) for j in range(c)]+['y']
    frame = pd.DataFrame(rows, columns=columns)
    formula = 'y ~ C(group) + '+' + '.join(columns[1:-1])
    fit = smf.ols(formula, frame).fit()
    effects = sm.stats.anova_lm(fit, typ=2)
    expected = [[['R2'],float(fit.rsquared)]]
    for i, term in enumerate(['C(group)']+columns[1:-1]):
        for key, ref in [('SS','sum_sq'),('df','df'),('F','F'),('p','PR(>F)')]:
            expected.append([['ANCOVA table',i,key],float(effects.loc[term,ref])])
    at = pd.DataFrame([dict(group=g, **{column:frame[column].mean() for column in columns[1:-1]}) for g in (1,2,3)])
    prediction = fit.get_prediction(at).summary_frame(alpha=.05)
    for i in range(3):
        for key, ref in [('adjusted mean','mean'),('SE','mean_se'),('Lower CI','mean_ci_lower'),('Upper CI','mean_ci_upper')]:
            expected.append([['Adjusted means',i,key],float(prediction.iloc[i][ref])])
    interaction = smf.ols('y ~ C(group) * ('+' + '.join(columns[1:-1])+')', frame).fit()
    f, pv, _ = interaction.compare_f_test(fit)
    expected += [[['Slope homogeneity','F'],float(f)],[['Slope homogeneity','p'],float(pv)]]
    add(f'ANCOVA unbalanced {c} covariates', 'ancova', [rows], expected)

families = {'gaussian':sm.families.Gaussian, 'binomial':sm.families.Binomial,
            'poisson':sm.families.Poisson, 'gamma':sm.families.Gamma,
            'inversegaussian':sm.families.InverseGaussian, 'nbinom':sm.families.NegativeBinomial}
links = {'identity':sm.families.links.Identity, 'log':sm.families.links.Log,
         'logit':sm.families.links.Logit, 'probit':sm.families.links.Probit,
         'cloglog':sm.families.links.CLogLog, 'inverse':sm.families.links.InversePower,
         'inverse_squared':sm.families.links.InverseSquared}
cases = [('gaussian','identity'),('gaussian','log'),('binomial','logit'),('binomial','probit'),
         ('binomial','cloglog'),('poisson','log'),('gamma','log'),('gamma','inverse'),
         ('inversegaussian','log'),('inversegaussian','inverse_squared'),('nbinom','log')]
for family, link in cases:
    x = rng.uniform(-.8, .8, (80, 2))
    design = sm.add_constant(x)
    eta = 1+.2*x[:,0]-.3*x[:,1]
    mu = links[link]().inverse(eta)
    if family == 'gaussian': y = mu+rng.normal(0,.2,80)
    elif family == 'binomial': y = rng.binomial(1,mu)
    elif family == 'poisson': y = rng.poisson(mu)
    elif family == 'gamma': y = rng.gamma(8,mu/8)
    elif family == 'inversegaussian': y = rng.wald(mu, 12)
    else: y = rng.negative_binomial(2, 2/(2+mu))
    alpha = .5
    args = {'link':links[link]()}
    if family == 'nbinom': args['alpha'] = alpha
    distribution = families[family](**args)
    fit = sm.GLM(y, design, family=distribution).fit(tol=1e-12, maxiter=200)
    expected = [[['deviance'],float(fit.deviance)],[['Pearson chi2'],float(fit.pearson_chi2)],
                [['dispersion'],float(fit.scale)],[['log likelihood'],float(fit.llf)],
                [['AIC'],float(fit.aic)],[['null deviance'],float(fit.null_deviance)]]
    for i in range(3):
        expected += [[['coefficients',i,'estimate'],float(fit.params[i])],
                     [['coefficients',i,'SE'],float(fit.bse[i])],
                     [['coefficients',i,'p'],float(fit.pvalues[i])]]
    for i in (0, 25, 49): expected.append([['Fitted observations',i,'fitted'],float(fit.fittedvalues[i])])
    add(f'GLM {family} {link}', 'glm', [np.column_stack([x,y]).tolist(),family,link,alpha], expected)

# Row-aligned exposure changes the mean, but is excluded from coefficient count.
x = rng.normal(0,.6,(50,1))
exposure = rng.uniform(.5,4,50)
y = rng.poisson(exposure*np.exp(.3+.4*x[:,0]))
fit = sm.GLM(y,sm.add_constant(x),family=sm.families.Poisson(),exposure=exposure).fit(tol=1e-12)
add('GLM Poisson exposure','glm',[np.column_stack([x,y]).tolist(),'poisson','log',1,exposure.tolist(),'exposure'],
    [[['deviance'],float(fit.deviance)],[['null deviance'],float(fit.null_deviance)]]+
    [[['coefficients',i,key],float(values[i])] for key,values in [('estimate',fit.params),('SE',fit.bse)] for i in range(2)])
(ROOT/'tests/fixtures/ancova_glm_reference.json').write_text(json.dumps(fixtures,indent=2)+'\n',encoding='utf-8')
print(f'Wrote {len(fixtures)} independent reference cases')
