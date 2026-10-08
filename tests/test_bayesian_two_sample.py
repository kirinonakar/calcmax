"""Two-sample posterior inference and compatible-null Bayes factors."""
import json
import unittest
from test_advanced_statistics import ROOT, run, evaluate
from calc_shared import MathError


class BayesianTwoSampleTests(unittest.TestCase):
    def test_independent_convolution_and_marginal_evidence_references(self):
        cases=json.loads((ROOT/'tests/fixtures/bayesian_two_sample_reference.json').read_text(encoding='utf-8'))
        for case in cases:
            with self.subTest(case=case['name']):
                actual=run('bayescompare',*case['arguments'])
                unequal=case['arguments'][2]=='unequal'
                for key,expected in case['expected'].items():
                    if key=='difference credible interval':
                        for i,target in enumerate(expected):
                            error=6*float(actual['difference interval MCSE'][i])+1e-6 if unequal else 1e-8
                            self.assertAlmostEqual(float(actual[key][i]),target,delta=error)
                    elif key=='P(μB > μA)' and unequal:
                        self.assertAlmostEqual(float(actual[key]),expected,delta=6*float(actual['probability MCSE'])+1e-6)
                    else:
                        self.assertAlmostEqual(float(actual[key]),expected,delta=1e-8*max(1,abs(expected)))
                self.assertAlmostEqual(float(actual['BF10']*actual['BF01']),1,places=12)

    def test_direction_seed_and_measurement_unit_invariance(self):
        a,b=[1,2,4],[2,3,6,7]
        for mode in ('equal','unequal'):
            first=run('bayescompare',a,b,mode,2,.2,2,3,.95,4000,7)
            repeat=run('bayescompare',a,b,mode,2,.2,2,3,.95,4000,7)
            self.assertEqual(first,repeat)
            reverse=run('bayescompare',b,a,mode,2,.2,2,3,.95,4000,7)
            self.assertAlmostEqual(float(first['Posterior Mean Difference (B - A)']),-float(reverse['Posterior Mean Difference (B - A)']),places=12)
            self.assertAlmostEqual(float(first['BF10']),float(reverse['BF10']),places=10)
            scaled=run('bayescompare',[3*x+100 for x in a],[3*x+100 for x in b],mode,106,.2,2,27,.95,4000,7)
            self.assertAlmostEqual(float(first['BF10']),float(scaled['BF10']),places=10)
            self.assertAlmostEqual(float(first['Posterior Effect Size']),float(scaled['Posterior Effect Size']),places=10)
            for i in range(2):
                self.assertAlmostEqual(float(scaled['difference credible interval'][i]),3*float(first['difference credible interval'][i]),places=10)
        null=run('bayescompare',[3,3,3],[3,3,3],'unequal',3)
        self.assertEqual(float(null['P(μB > μA)']),.5)
        self.assertLess(float(null['BF10']),1)

    def test_invalid_data_and_prior_options(self):
        for args in [([], [1,2]),([1],[1,2]),([1,2],[3,4],'paired'),
                     ([1,2],[3,4],'equal',0,0),([1,2],[3,4],'equal',0,1,0),
                     ([1,2],[3,4],'equal',0,1,2,-1),([1,2],[3,4],'equal',0,1,2,1,1),
                     ([1,2],[3,4],'equal',0,1,2,1,.95,1),
                     ([1,2],[3,4],'equal',0,1,2,1,.99999,2000),
                     ([1,2],[3,4],'equal',0,1,2,1,.95,2000,-1)]:
            with self.subTest(args=args),self.assertRaises(MathError):run('bayescompare',*args)

    def test_expression_report_and_catalog_route(self):
        result=evaluate('bayescompare([10,11,9,10,12],[13,14,12,15,13])')
        report=result['statisticsReport']
        self.assertEqual(report['title'],'Bayesian Two-Sample Comparison')
        for key in ('difference credible interval','effect credible interval'):
            self.assertEqual(next(section for section in report['sections'] if section['title']==key)['columns'],['Lower','Upper'])
        self.assertIn('H0: muB-muA=0',result['note'])
        self.assertIn('not a posterior hypothesis probability',result['note'])
        import symvacas_catalog
        self.assertTrue(callable(symvacas_catalog.bayescompare))


if __name__=='__main__':unittest.main()
