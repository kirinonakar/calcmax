"""ANCOVA and GLM accuracy, domain validation and calculator integration."""
import json
import unittest
from test_advanced_statistics import ROOT, run
from calc_shared import MathError

class AncovaGlmTests(unittest.TestCase):
    def test_estimated_nb2_matches_count_regression_and_retains_fixed_alpha(self):
        rows=[[x,y] for x,counts in enumerate([[0,0,1,8],[0,1,3,15],[0,2,5,23],[1,3,10,35]]) for y in counts]
        estimate=run('glm',rows,'nbinom','log','estimate')
        reference=run('nbreg',rows)
        self.assertAlmostEqual(float(reference['dispersion alpha (NB2)']),float(estimate['dispersion alpha (NB2)']),places=10)
        self.assertAlmostEqual(float(reference['AIC']),float(estimate['AIC']),places=9)
        self.assertEqual('joint ML',estimate['NB2 dispersion estimation'])
        fixed=run('glm',rows,'nbinom','log',.5)
        self.assertEqual(.5,float(fixed['dispersion alpha (NB2, fixed)']))
        self.assertEqual('fixed',fixed['NB2 dispersion estimation'])
        for i,row in enumerate(reference['coefficients']):
            self.assertAlmostEqual(float(row['SE']),float(estimate['coefficients'][i]['SE']),places=9)
        # Underdispersed data have an alpha=0 boundary, not a negative estimate.
        rows=[[i%2,2+i%2] for i in range(30)]
        boundary=run('glm',rows,'nbinom','log','estimate')
        poisson=run('glm',rows,'poisson')
        self.assertEqual(0,float(boundary['dispersion alpha (NB2)']))
        self.assertEqual('ML (Poisson boundary)',boundary['NB2 dispersion estimation'])
        self.assertAlmostEqual(float(poisson['log likelihood']),float(boundary['log likelihood']),places=9)

    def test_independent_statsmodels_references(self):
        cases = json.loads((ROOT/'tests/fixtures/ancova_glm_reference.json').read_text())
        for case in cases:
            with self.subTest(case=case['name']):
                result = run(case['function'], *case['arguments'])
                for path, expected in case['expected']:
                    actual = result
                    for key in path: actual = actual[key]
                    self.assertAlmostEqual(float(actual), expected, delta=case['tolerance']*max(1, abs(expected)), msg=str(path))

    def test_ancova_preserves_adjusted_means_under_covariate_shift_and_scale(self):
        case = json.loads((ROOT/'tests/fixtures/ancova_glm_reference.json').read_text())[1]
        rows = case['arguments'][0]
        base = run('ancova', rows)
        changed = run('ancova', [[r[0], 1e6+100*r[1], -.002*r[2], r[3]] for r in rows])
        for i in range(3):
            self.assertAlmostEqual(float(base['Adjusted means'][i]['adjusted mean']), float(changed['Adjusted means'][i]['adjusted mean']), places=8)
        self.assertAlmostEqual(float(base['ANCOVA table'][0]['F']), float(changed['ANCOVA table'][0]['F']), places=8)

    def test_glm_offsets_match_log_exposure_and_gaussian_is_ols(self):
        rows = [[0,2],[1,4],[2,3],[3,8],[4,7],[5,9]]
        gaussian = run('glm', rows)
        self.assertAlmostEqual(float(gaussian['coefficients'][1]['estimate']), 1.4, places=9)
        exposures = [1,2,3,1,2,3]
        import math
        log_offsets = [math.log(v) for v in exposures]
        first = run('glm', rows, 'poisson','log',1,exposures,'exposure')
        second = run('glm', rows, 'poisson','auto',1,log_offsets,'offset')
        self.assertAlmostEqual(float(first['deviance']),float(second['deviance']),places=10)
        self.assertEqual(first['df residual'],4)

    def test_invalid_models_fail_without_finite_inference(self):
        cases = [('ancova', [[[1,1,2],[1,2,3],[1,3,5],[1,4,4]]]),
                 ('ancova', [[[1,1,2],[1,1,3],[2,2,4],[2,2,5]]]),
                 ('ancova', [[[1,1,2],[1,2,4],[2,1,3],[2,2,5]],1]),
                 ('ancova', [[[1,1,2],[1,2,4],[2,1,3],[2,2,5]],.95,2]),
                 ('glm', [[[0,0],[1,0],[2,1],[3,1]],'binomial']),
                 ('glm', [[[0,0],[1,0],[2,0],[3,0]],'poisson']),
                 ('glm', [[[0,2],[1,-1],[2,3],[3,4]],'gamma']),
                 ('glm', [[[0,2],[1,.5],[2,3],[3,4]],'poisson']),
                 ('glm', [[[0,2],[1,3],[2,4],[3,4]],'poisson','identity']),
                 ('glm', [[[0,2],[1,3],[2,4],[3,4]],'nbinom','log',0]),
                 ('glm', [[[0,2],[1,3],[2,4],[3,4]],'gaussian','identity',1,[1,2,3,4],'exposure']),
                 ('glm', [[[0,2],[1,3],[2,4],[3,4]],'poisson','log',1,[1,2]]),
                 ('glm', [[[0,2],[1,3],[2,4],[3,4]],'poisson','log',1,[1,0,1,1],'exposure']),
                 ('glm', [[[1,2],[1,3],[1,4],[1,5]],'gaussian'])]
        for name, args in cases:
            with self.subTest(name=name,args=args):
                with self.assertRaises(MathError): run(name,*args)

if __name__=='__main__': unittest.main()
