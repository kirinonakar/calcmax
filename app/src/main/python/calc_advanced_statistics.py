"""Public dispatch for portable advanced statistics (binary64 numerics).

Analysis implementations live in the Bayesian, inference, survival, longitudinal,
regression, resampling and learning modules. calc_advanced_common owns shared
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
from calc_advanced_glm import calculate as glm


# Function names, argument limits and handlers share one registry.
_ANALYSES = {
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
    'mixedmodel': (1, 4, longitudinal),
    'glmm': (1, 6, glmm),
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
    return convert(result)


def calculate(engine, name, a):
    """Route raw results; precision and conversion belong to advanced()."""
    analysis = _ANALYSES.get(name)
    if analysis is None:
        raise MathError('Unknown advanced analysis')
    return analysis[2](engine, name, a)
