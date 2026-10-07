"""Bayesian posterior contracts and independently generated SciPy references."""
import json
import math
import unittest
from test_advanced_statistics import ROOT, run, evaluate
from calc_shared import MathError


class BayesianStatisticsTests(unittest.TestCase):
    def test_independent_distribution_references(self):
        fixtures = json.loads((ROOT/'tests/fixtures/bayesian_statistics_reference.json').read_text())
        for case in fixtures:
            with self.subTest(function=case['function'], arguments=case['arguments']):
                result = run(case['function'], *case['arguments'])
                for key, expected in case['expected'].items():
                    pairs = zip(result[key], expected) if isinstance(expected, list) else [(result[key], expected)]
                    for actual, reference in pairs:
                        self.assertTrue(math.isclose(float(actual), reference, rel_tol=case['tolerance'], abs_tol=1e-12), (key, actual, reference))

    def test_proportion_closed_form_and_grouped_counts(self):
        result = run('bayesproportion', [1])  # Posterior Beta(2,1), CDF(x)=x².
        self.assertAlmostEqual(float(result['posterior mean']), 2/3)
        self.assertAlmostEqual(float(result['P(p > threshold)']), .75)
        self.assertAlmostEqual(float(result['BF10']), 1)
        for actual, q in zip(result['credible interval'], [.025, .975]):
            self.assertAlmostEqual(float(actual), math.sqrt(q), places=12)
        self.assertEqual(run('bayesproportion', [1,0,1]), run('bayesproportion', [[1,2],[1,1]]))
        self.assertGreater(float(run('bayesproportion', [1,0,1], 20, 1)['posterior mean']), float(run('bayesproportion', [1,0,1])['posterior mean']))

    def test_rate_closed_form_and_exposure(self):
        result = run('bayesrate', [0])  # Posterior Exponential(rate=2).
        self.assertAlmostEqual(float(result['posterior mean']), .5)
        self.assertAlmostEqual(float(result['P(rate > threshold)']), math.exp(-2), places=12)
        for actual, q in zip(result['credible interval'], [.025, .975]):
            self.assertAlmostEqual(float(actual), -math.log1p(-q)/2, places=12)
        self.assertEqual(run('bayesrate', [1,0,2]), run('bayesrate', [[3,3]]))
        self.assertEqual(float(run('bayesrate', [[0,.5]], 1, 1, .95, 0)['P(rate > threshold)']), 1)

    def test_mean_prior_update_prediction_and_undefined_moments(self):
        result = run('bayesmean', [1,3], 2, 2, 1, 1, .95, 2)
        for key, value in [('posterior mean',2), ('posterior kappa',4), ('posterior alpha',2), ('posterior beta',2), ('mean posterior t scale',.5), ('P(mean > threshold)',.5)]:
            self.assertEqual(float(result[key]), value)
        self.assertGreater(float(result['predictive interval'][1]-result['predictive interval'][0]), float(result['credible interval'][1]-result['credible interval'][0]))
        heavy = run('bayesmean', [1], 0, 1, .1, 1)
        self.assertEqual(heavy['posterior SD'], 'unavailable')
        self.assertEqual(heavy['posterior variance mean'], 'unavailable')
        constant = run('bayesmean', [3,3,3])
        self.assertLess(float(constant['credible interval'][0]), float(constant['posterior mean']))
        shifted = run('bayesmean', [101,103], 102, 2, 1, 1, .95, 102)
        self.assertAlmostEqual(float(shifted['credible interval'][0]-result['credible interval'][0]), 100)

    def test_invalid_inputs_and_selected_model_constraints(self):
        invalid = [('bayesproportion', [[2]]), ('bayesproportion', [[1,-1]]),
                   ('bayesproportion', [[[3,2]]]), ('bayesproportion', [[[0,0]]]),
                   ('bayesproportion', [[1],0]), ('bayesproportion', [[1],1,1,.95,1]),
                   ('bayesrate', [[.5]]), ('bayesrate', [[[1,0]]]),
                   ('bayesrate', [[[1,-1]]]), ('bayesrate', [[[1,2,3]]]),
                   ('bayesrate', [[0],1,-1]), ('bayesrate', [[0],1,1,1]),
                   ('bayesmean', [[]]), ('bayesmean', [[1],0,0]),
                   ('bayesmean', [[1],0,1,-1]), ('bayesmean', [[1],0,1,2,0])]
        for name, args in invalid:
            with self.subTest(name=name,args=args), self.assertRaises(MathError): run(name,*args)

    def test_public_dispatch_exposes_intervals_and_catalog_functions(self):
        import symvacas_catalog as catalog
        self.assertAlmostEqual(float(catalog.bayesproportion([1])['posterior mean']), 2/3)
        for expression in ('bayesproportion([[7,10]])', 'bayesmean([1,2,3])', 'bayesrate([[3,2.5]])'):
            result = evaluate(expression)
            self.assertIn('credible interval', result['exact'])
            self.assertIn('equal-tailed', result['note'].lower())
