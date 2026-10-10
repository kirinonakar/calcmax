"""Public dispatch for portable advanced statistics (binary64 numerics).

Analysis implementations live in the Bayesian, inference, survival, longitudinal,
regression, resampling, learning, survey, SEM and social-science modules. calc_advanced_common owns shared
validation, numerical tools and SymPy result conversion. Keep this entry point
shared by calculator expressions and the Python catalog.
"""
import mpmath as mp
from calc_shared import MathError, require
from calc_advanced_common import convert
from calc_advanced_inference import calculate as inference
from calc_advanced_survival import calculate as survival
from calc_advanced_longitudinal import calculate as longitudinal
from calc_advanced_glmm import calculate as glmm
from calc_advanced_regression import calculate as regression
from calc_advanced_resampling import calculate as resampling
from calc_advanced_learning import calculate as learning
from calc_advanced_bayesian import calculate as bayesian
from calc_advanced_two_sample import calculate as two_sample
from calc_advanced_ancova import calculate as ancova
from calc_advanced_factorial import calculate as factorial
from calc_advanced_glm import calculate as glm
from calc_proportion_tests import calculate as proportion
from calc_advanced_survey import calculate as survey
from calc_advanced_association import calculate as association
from calc_advanced_multivariate import calculate as multivariate
from calc_advanced_social import calculate as social
from calc_advanced_limited import calculate as limited
from calc_advanced_sem import calculate as sem
from calc_posthoc import dunn_comparisons


def dunn(engine, name, a):
    return dunn_comparisons(a[0],engine,str(a[1]) if len(a)>1 else 'holm')


# Function names, argument limits and handlers share one registry.
_ANALYSES = {
    'cronbach': (1, 2, survey),
    'efa': (1, 7, survey),
    'cfa': (1, 6, sem),
    'sem': (2, 7, sem),
    'manova': (1, 4, multivariate),
    'mediation': (1, 3, social),
    'moderation': (1, 1, social),
    'cramerv': (1, 1, association),
    'phi': (1, 1, association),
    'cohenkappa': (1, 2, association),
    'dunn': (1, 2, dunn),
    'discriminantanalysis': (1, 4, multivariate),
    'quantreg': (1, 2, limited),
    'zeroinflated': (1, 3, limited),
    'tobit': (1, 3, limited),
    'hcluster': (1, 4, multivariate),
    'propztest': (2, 4, proportion),
    'propztest2': (2, 5, proportion),
    'ancova': (1, 3, ancova),
    'glm': (1, 6, glm),
    'bayesproportion': (1, 5, bayesian),
    'bayesmean': (1, 7, bayesian),
    'bayescompare': (2, 10, two_sample),
    'bayesrate': (1, 5, bayesian),
    'padjust': (1, 3, inference),
    'cohend': (2, 3, inference),
    'eta2': (2, 20, inference),
    'levene': (2, 20, inference),
    'bartlett': (2, 20, inference),
    'mcnemar': (1, 2, inference),
    'kaplanmeier': (1, 3, survival),
    'logrank': (2, 2, survival),
    'cox': (1, 4, survival),
    'survivalanalysis': (1, 5, survival),
    'repeatedanova': (1, 2, longitudinal),
    'friedman': (1, 1, inference),
    'twowayanova': (1, 2, factorial),
    'linearmodel': (1, 5, factorial),
    'mixedmodel': (1, 4, longitudinal),
    'glmm': (1, 7, glmm),
    'gee': (1, 5, longitudinal),
    'multinomial': (1, 1, regression),
    'ordinal': (1, 1, regression),
    'poissonreg': (1, 3, regression),
    'nbreg': (1, 3, regression),
    'bootstrapci': (1, 5, resampling),
    'bayesbootstrap': (1, 7, resampling),
    'testpower': (2, 5, resampling),
    'samplesize': (1, 5, resampling),
    'kstest': (2, 4, resampling),
    'crossvalidate': (1, 6, learning),
    'pca': (1, 3, learning),
    'kmeans': (2, 3, learning),
    'impute': (1, 3, learning),
}
FUNCTIONS = set(_ANALYSES)


def advanced(engine, name, a):
    """Entry point shared by calculator expressions and Python catalog."""
    engine.note = 'Numerical statistics use binary64 precision.'
    low, high, _ = _ANALYSES[name]
    require(low <= len(a) <= high, name + ' argument count mismatch')
    with mp.workdps(25):
        result = calculate(engine, name, a)
    return result if name in ('propztest','propztest2') else convert(result)


def calculate(engine, name, a):
    """Route raw results; precision and conversion belong to advanced()."""
    analysis = _ANALYSES.get(name)
    if analysis is None:
        raise MathError('Unknown advanced analysis')
    return analysis[2](engine, name, a)
