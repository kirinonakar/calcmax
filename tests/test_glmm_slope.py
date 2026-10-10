"""Independent Laplace likelihood fits and original-unit covariance contracts."""
import json
import unittest
from unittest.mock import patch
from test_advanced_statistics import ROOT, run, evaluate
from calc_shared import MathError


class GlmmSlopeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.cases=json.loads((ROOT/'tests/fixtures/glmm_slope_reference.json').read_text())

    def test_independent_scipy_laplace_reference_fits(self):
        for case in self.cases:
            with self.subTest(case=case['name']):
                result=run('glmm',*case['arguments'])
                for path,expected in case['expected']:
                    actual=result
                    for key in path: actual=actual[key]
                    self.assertAlmostEqual(float(actual),expected,delta=case['tolerance']*max(1,abs(expected)),msg=str(path))
                self.assertEqual('ML (two-dimensional Laplace)',result['estimation'])
                self.assertLess(abs(float(result['random intercept-slope correlation'])),1)

    def test_covariance_and_modes_transform_to_original_predictor_units(self):
        args=self.cases[0]['arguments']; base=run('glmm',*args)
        shifted=[[r[0],10+2*r[1],r[2]] for r in args[0]]
        other=run('glmm',shifted,*args[1:])
        self.assertAlmostEqual(float(base['log likelihood']),float(other['log likelihood']),places=8)
        vi=float(base['random intercept variance']); vs=float(base['random slope variance']); cov=float(base['random intercept-slope covariance'])
        self.assertAlmostEqual(vs/4,float(other['random slope variance']),places=7)
        self.assertAlmostEqual(cov/2-2.5*vs,float(other['random intercept-slope covariance']),places=7)
        self.assertAlmostEqual(vi-10*cov+25*vs,float(other['random intercept variance']),places=7)
        for before,after in zip(base['subject random effects'],other['subject random effects']):
            self.assertAlmostEqual(float(before['conditional slope mode'])/2,float(after['conditional slope mode']),places=7)

    def test_slope_position_and_fixed_columns_survive_predictor_reordering(self):
        args=self.cases[0]['arguments']
        rows=[[r[0],r[1],(i%3)-1,r[2]] for i,r in enumerate(args[0])]
        first=run('glmm',rows,*args[1:])
        swapped=[[r[0],r[2],r[1],r[3]] for r in rows]
        other=run('glmm',swapped,*args[1:-1],2)
        self.assertAlmostEqual(float(first['log likelihood']),float(other['log likelihood']),places=8)
        for key in ('random intercept variance','random slope variance','random intercept-slope covariance'):
            self.assertAlmostEqual(float(first[key]),float(other[key]),places=7)
        for i,j in ((0,0),(1,2),(2,1)):
            self.assertAlmostEqual(float(first['coefficients'][i]['estimate']),float(other['coefficients'][j]['estimate']),places=8)

    def test_public_dispatch_reports_slopes_and_withholds_unidentified_inference(self):
        args=self.cases[0]['arguments']
        source='glmm('+str(args[0])+',poisson,1,'+str(args[3])+',offset,likelihood,1)'
        result=evaluate(source)
        self.assertEqual('glmm',result['statisticsReport']['analysis'])
        self.assertIn('random slope variance',result['exact'])
        with patch('calc_advanced_glmm_slope.observed_information',side_effect=MathError('not identifiable')):
            raw=run('glmm',*args)
        self.assertEqual('unavailable',raw['diagnostics']['inference'])
        self.assertTrue(all(row['SE']=='unavailable' and row['p']=='unavailable' and row['CI95']=='unavailable' for row in raw['coefficients']))
        self.assertEqual(0,raw['singular fit'])

    def test_slope_options_reject_unsupported_quadrature_and_unidentifiable_designs(self):
        args=self.cases[0]['arguments']
        with self.assertRaises(MathError): run('glmm',args[0],'poisson',15,[],'offset','likelihood',1)
        with self.assertRaises(MathError): run('glmm',args[0],'poisson',1,[],'offset','refit',1)
        with self.assertRaises(MathError): run('glmm',args[0],'poisson',1,[],'offset','likelihood',2)
        rows=[[i,i,1+(i%3)] for i in range(10)]
        with self.assertRaisesRegex(MathError,'within-subject'): run('glmm',rows,'poisson',1,[],'offset','likelihood',1)


if __name__=='__main__': unittest.main()
