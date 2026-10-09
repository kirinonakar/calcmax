"""Inference guards, likelihood profiles and cluster-corrected reference checks."""
import json
import math
from pathlib import Path
import sys
import unittest
from unittest.mock import patch
import mpmath as mp
import sympy as s

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'app/src/main/python'))
from calc_advanced_statistics import advanced
from calc_advanced_common import inference,validated_covariance
from calc_advanced_longitudinal import clustered_design,random_effects_fit
from calc_evaluator import Engine
from calc_shared import MathError
from calc_statistics_report import statistics_report


def calculate(name,*args): return advanced(Engine({}),name,[s.sympify(arg) for arg in args])


class ModelDiagnosticsTests(unittest.TestCase):
    def test_covariance_rejects_invalid_matrices_and_repairs_only_roundoff(self):
        for matrix,message in [([[-.01,0],[0,1]],'negative variance'),([[1,.2],[.3,1]],'symmetric'),([[1,2],[2,1]],'semidefinite'),([[math.inf,0],[0,1]],'finite')]:
            with self.subTest(matrix=matrix),self.assertRaisesRegex(MathError,message): validated_covariance(matrix)
        corrected=validated_covariance([[-1e-14,0],[0,1]])
        self.assertEqual(corrected[0,0],0)
        rows=inference([1,2],corrected,['a','b'])
        self.assertIsNone(rows[0]['p']);self.assertIsNone(rows[0]['CI95'])
        singular=validated_covariance([[1,1+1e-12],[1+1e-12,1]])
        self.assertGreaterEqual(min(mp.eigsy(singular,eigvals_only=True)),-1e-14)
        for scale in (1e-20,1e20):
            with self.assertRaises(MathError): validated_covariance(mp.matrix([[1,2],[2,1]])*scale)
        held=inference([1,2],mp.eye(2),['a','b'],reliable=False)
        self.assertTrue(all(row['SE'] is None and row['p'] is None and row['CI95'] is None for row in held))

    def test_mixed_likelihood_profile_and_bootstrap_match_independent_references(self):
        fixture=json.loads((ROOT/'tests/fixtures/model_diagnostics_reference.json').read_text()); rows=fixture['mixed rows']
        for method in ('ml','reml'):
            result=calculate('mixedmodel',rows,0,method)
            likelihood='log likelihood' if method=='ml' else 'restricted log likelihood'
            self.assertAlmostEqual(float(result[likelihood]),fixture['mixed'][method]['log likelihood'],places=6)
            self.assertAlmostEqual(float(result['AIC']),-2*float(result[likelihood])+2*int(result['likelihood parameters']))
            self.assertAlmostEqual(float(result['BIC']),-2*float(result[likelihood])+math.log(len(rows))*int(result['likelihood parameters']))
            if method=='reml': self.assertTrue(any('identical fixed-effect' in warning for warning in result['diagnostics']['warnings']))
        for mode,settings in [('profile','profile'),('bootstrap',['bootstrap',100,7])]:
            result=calculate('mixedmodel',rows,0,'reml' if mode=='profile' else 'ml',settings)
            for row,expected in zip(result['coefficients'],fixture['mixed'][mode]):
                self.assertEqual(row['p'],'unavailable')
                for value,reference in zip(row['CI95'],expected): self.assertAlmostEqual(float(value),reference,delta=2e-5)
            report=statistics_report('mixedmodel',result,15)
            self.assertEqual(report['sections'][0]['title'],'Model diagnostics')

    def test_small_sample_gee_matches_statsmodels_bias_reduced_covariance_and_t(self):
        fixture=json.loads((ROOT/'tests/fixtures/model_diagnostics_reference.json').read_text())
        for case in fixture['gee']:
            with self.subTest(family=case['family'],corr=case['correlation']):
                result=calculate('gee',case['rows'],case['family'],case['correlation'],[],'small')
                self.assertEqual(result['covariance correction'],'Mancl-DeRouen');self.assertEqual(int(result['inference df']),len({r[0] for r in case['rows']})-2)
                for i,row in enumerate(result['coefficients']):
                    for key,expected_key in [('estimate','estimates'),('SE','SE'),('p','p')]:
                        self.assertAlmostEqual(float(row[key]),case[expected_key][i],delta=2e-5*max(1,abs(case[expected_key][i])))
                    for value,expected in zip(row['CI95'],case['CI95'][i]): self.assertAlmostEqual(float(value),expected,delta=2e-5*max(1,abs(expected)))

    def test_nonconverged_slopes_keep_estimates_but_withhold_wald_inference(self):
        fixtures=json.loads((ROOT/'tests/fixtures/advanced_statistics_reference.json').read_text())
        rows=next(c['arguments'][0] for c in fixtures if c['name']=='Mixed random slope ml')
        x,y=clustered_design(rows); ids=sorted(set(row[0] for row in rows)); clusters=[[i for i,row in enumerate(rows) if row[0]==g] for g in ids]
        fields,best=random_effects_fit(x,y,clusters,[1],'ml')
        with patch('calc_advanced_longitudinal.random_effects_fit',return_value=(fields,(*best[:3],False))):
            result=calculate('mixedmodel',rows,1,'ml')
        self.assertEqual(result['diagnostics']['inference'],'unavailable')
        self.assertTrue(all(row['SE']=='unavailable' and row['p']=='unavailable' and row['CI95']=='unavailable' for row in result['coefficients']))
        self.assertTrue(all(math.isfinite(float(row['estimate'])) for row in result['coefficients']))

    def test_quadrature_checks_laplace_and_max_points_and_withholds_unstable_ci(self):
        from calc_advanced_longitudinal import nelder_mead
        fixtures=json.loads((ROOT/'tests/fixtures/advanced_statistics_reference.json').read_text())
        rows=next(c['arguments'][0] for c in fixtures if c['name']=='GLMM binomial QUADPACK')
        for points,check in ((1,7),(31,21)):
            result=calculate('glmm',rows,'binomial',points,[],'offset','refit')
            self.assertEqual(int(result['quadrature check points']),check)
            self.assertEqual(int(result['quadrature refit converged']),1)
            self.assertIn('quadrature refit coefficients',result)
        def changed(objective,start,step,iterations=160,diagnostics=False):
            if step==[.1]*len(start):
                point=start[:];point[1]+=1
                return point,objective(point),1,True
            return nelder_mead(objective,start,step,iterations,diagnostics)
        with patch('calc_advanced_glmm.nelder_mead',changed):
            result=calculate('glmm',rows,'binomial',15,[],'offset','refit')
        self.assertGreater(float(result['quadrature max coefficient change / SE']),.1)
        self.assertEqual(result['diagnostics']['inference'],'unavailable')
        self.assertTrue(all(row['p']=='unavailable' and row['CI95']=='unavailable' for row in result['coefficients']))


if __name__=='__main__': unittest.main()
