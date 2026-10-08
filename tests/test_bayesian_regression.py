"""Independent conjugate/Laplace references and full-posterior NUTS checks."""
import json
import math
import random
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'app/src/main/python'))
import symvacas_catalog as catalog
import sympy as s
from calc_evaluator import Engine
from calc_statistics import fit_regression
from calc_shared import MathError
from calc_nuts import sample, diagnostics, _leapfrog, _build_tree, _joint

REFERENCE = json.loads((ROOT/'tests/fixtures/bayesian_regression_reference.json').read_text())


class BayesianRegressionTests(unittest.TestCase):
    def test_conjugate_and_laplace_match_independent_references(self):
        for mode,key in [('bayeslinear','linear'),('bayeslogistic','laplace')]:
            result = catalog.regression_report(REFERENCE['rows'],mode,REFERENCE['options'])
            for actual,expected in zip(result['coefficients'],REFERENCE[key]):
                for field,value in expected.items():
                    self.assertAlmostEqual(float(actual[field]),value,places=9)
                self.assertNotIn('p',actual)
                self.assertNotIn('se',actual)
            self.assertIsNone(result['df'])
            self.assertNotIn('aic',result)
            self.assertNotIn('likelihoodP',result)
            if mode == 'bayeslinear':
                for row in result['residuals']:
                    self.assertLess(float(row['predictiveLow']),float(row['fitted']))
                    self.assertGreater(float(row['predictiveHigh']),float(row['fitted']))
            else:
                self.assertEqual(float(result['auc']),1)
                for c in result['coefficients']:
                    self.assertAlmostEqual(float(c['oddsLow']),math.exp(float(c['low'])))

    def test_nuts_matches_exact_linear_and_quadrature_logistic_posteriors(self):
        with self.subTest(scenario='nuts_matches_exact_linear_and_quadrature_logistic_posteriors'):
            for mode,key in [('bayeslinear','linear'),('bayeslogistic','logisticQuadrature')]:
                options = [2.5,.95]+([2,1] if mode == 'bayeslinear' else [])+[['nuts',2000,600,8,7,4]]
                result = catalog.regression_report(REFERENCE['rows'],mode,options)
                self.assertEqual(result['method'],'nuts')
                self.assertEqual(result['nuts']['totalSamples'],8000)
                self.assertEqual(len(result['nuts']['chainDiagnostics']),4)
                self.assertEqual(result['nuts']['divergences'],0)
                for actual,expected in zip(result['coefficients'],REFERENCE[key]):
                    self.assertLess(abs(float(actual['estimate'])-expected['estimate']),5*float(actual['mcse'])+.015)
                    self.assertLess(abs(float(actual['posteriorSD'])-expected['posteriorSD']),.08*expected['posteriorSD'])
                    self.assertLess(abs(float(actual['probabilityPositive'])-expected['probabilityPositive']),.035)
                    self.assertLess(float(actual['rHat']),1.05)
                    self.assertGreater(float(actual['ess']),100)
                    for bound in (field for field in ('low','high') if field in expected):
                        self.assertLess(abs(float(actual[bound])-expected[bound]),.2*expected['posteriorSD'])
                if mode == 'bayeslogistic':
                    # The separated posterior is asymmetric; NUTS must not merely
                    # resample the Gaussian Laplace approximation.
                    self.assertGreater(float(result['coefficients'][1]['estimate']),REFERENCE['laplace'][1]['estimate']+.3)
        with self.subTest(scenario='sampler_reproduces_seed_and_preserves_gaussian_target'):
            target = lambda q: (sum(v*v for v in q)/2,list(q))
            chains,summary = sample(target,2,1000,300,8,19,2)
            again,repeat = sample(target,2,1000,300,8,19,2)
            self.assertEqual(chains,again); self.assertEqual(summary,repeat)
            self.assertEqual([len(c) for c in chains],[1000,1000])
            for j in range(2):
                values = [row[j] for chain in chains for row in chain]
                self.assertLess(abs(sum(values)/len(values)),.1)
                self.assertAlmostEqual(sum(v*v for v in values)/len(values),1,delta=.12)
            self.assertEqual(diagnostics([[1.]*100,[1.]*100]),(None,0.0))

    def test_nuts_trajectory_limits_divergences_and_workload_failure(self):
        target = lambda q: (sum(v*v for v in q)/2,list(q))
        state = ([.3,-.7],[1.2,.4],.29,[.3,-.7])
        forward = _leapfrog(target,state,.2)
        backward = _leapfrog(target,forward,-.2)
        for actual,expected in zip(backward[0]+backward[1],state[0]+state[1]):
            self.assertAlmostEqual(actual,expected,places=14)
        tree = _build_tree(target,state,_joint(state)-1,1,4,1e200,_joint(state),random.Random(0))
        self.assertEqual(tree[3],0);self.assertFalse(tree[4]);self.assertTrue(tree[7])
        self.assertEqual(tree[6],1,'stop expanding immediately after numerical failure')
        draws,limited = sample(target,2,300,100,1,19,2)
        self.assertGreater(limited['maxTreeDepthHits'],0)
        self.assertEqual(limited['meanLeapfrogSteps'],1)
        _,adaptive = sample(target,2,300,100,8,19,2)
        self.assertGreater(adaptive['meanLeapfrogSteps'],1)
        self.assertLess(adaptive['meanLeapfrogSteps'],255)
        self.assertEqual(adaptive['maxTreeDepthHits'],0)
        self.assertTrue(all(math.isfinite(v) for chain in draws for row in chain for v in row))
        with self.assertRaisesRegex(MathError,'workload'):
            sample(target,2,100,50,8,19,2,max_evaluations=20)
        report = catalog.regression_report(REFERENCE['rows'],'bayeslinear',[2.5,.95,2,1,['nuts',100,50,1]])
        self.assertTrue(any('max tree depth' in warning for warning in report['warnings']))

    def test_nuts_preserves_curved_non_gaussian_target(self):
        # x ~ Normal(0,1); y|x ~ Normal(.7*(x²-1),1). Independent exact moments.
        def target(q):
            x,y = q; residual = y-.7*(x*x-1)
            return (x*x+residual*residual)/2,[x-1.4*x*residual,residual]
        draws,summary = sample(target,2,3000,600,8,37,4)
        for j,second_moment in [(0,1),(1,1.98)]:
            chains = [[row[j] for row in chain] for chain in draws]
            values = [v for chain in chains for v in chain]
            self.assertLess(abs(sum(values)/len(values)),.12)
            self.assertAlmostEqual(sum(v*v for v in values)/len(values),second_moment,delta=.2)
            rhat,ess = diagnostics(chains)
            self.assertLess(rhat,1.05);self.assertGreater(ess,100)
        self.assertEqual(summary['divergences'],sum(chain['divergences'] for chain in summary['chainDiagnostics']))


    def test_proper_priors_handle_collinearity_and_original_coordinate_transform(self):
        rows = [[-2,0],[-1,0],[1,1],[2,1]]
        for mode in ('bayeslinear','bayeslogistic'):
            report = catalog.regression_report(rows,mode)
            shifted = catalog.regression_report([[10**9+2*x,y] for x,y in rows],mode)
            self.assertAlmostEqual(float(shifted['coefficients'][1]['estimate'])*2,float(report['coefficients'][1]['estimate']),places=8)
            self.assertAlmostEqual(float(shifted['coefficients'][1]['posteriorSD'])*2,float(report['coefficients'][1]['posteriorSD']),places=8)
            singular = catalog.regression_report([[x,2*x,1,y] for x,y in rows],mode)
            self.assertEqual(len(singular['coefficients']),4)
            self.assertTrue(all(float(c['posteriorSD'])>0 for c in singular['coefficients']))
            engine = Engine({'precision':40})
            expression = fit_regression(engine,[[s.sympify(v) for v in row] for row in rows],mode)
            for x in [-1.5,0,1.5]:
                self.assertAlmostEqual(engine.regression_predict([x]),float(expression.subs(s.Symbol('x'),x)),places=10)
        for rows in ([[1,3],[1,3]],[[1,2,3],[2,4,5]]):
            self.assertEqual(catalog.regression_report(rows,'bayeslinear')['method'],'conjugate')
        narrow = catalog.regression_report(REFERENCE['rows'],'bayeslinear',[.1,.8])
        wide = catalog.regression_report(REFERENCE['rows'],'bayeslinear',[.1,.99])
        self.assertLess(abs(float(narrow['coefficients'][1]['estimate'])),abs(REFERENCE['linear'][1]['estimate']))
        self.assertLess(float(narrow['coefficients'][1]['high']),float(wide['coefficients'][1]['high']))

    def test_invalid_data_prior_and_sampler_settings_are_rejected(self):
        for mode in ('bayeslinear','bayeslogistic'):
            for rows in ([],[[1,0]],[[1,0],[1,2,0]],[[1,s.oo],[2,1]]):
                with self.assertRaises(MathError): catalog.regression_report(rows,mode)
            for options in ([0,.95],[2.5,0],[2.5,1],[s.oo,.95],[2.5,.95,['nuts',99]],
                            [2.5,.95,['nuts',100,49]],[2.5,.95,['nuts',100,50,0]],
                            [2.5,.95,['nuts',100,50,10,-1]],[2.5,.95,['nuts',100,50,10,0,1]],
                            [2.5,.95,['hmc']], [2.5,.95,['nuts',100,50,11]]):
                with self.assertRaises(MathError): catalog.regression_report(REFERENCE['rows'],mode,options)
        with self.assertRaises(MathError): catalog.regression_report([[1,0],[2,0]],'bayeslogistic')
        with self.assertRaises(MathError): catalog.regression_report(REFERENCE['rows'],'bayeslinear',[2.5,.95,0,1])


if __name__ == '__main__': unittest.main()
