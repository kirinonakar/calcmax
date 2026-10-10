"""Two-sample posterior inference and compatible-null Bayes factors."""
import json
import unittest
from test_advanced_statistics import ROOT, run
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

    def test_invalid_data_and_prior_options(self):
        for args in [([], [1,2]),([1],[1,2]),([1,2],[3,4],'paired'),
                     ([1,2],[3,4],'equal',0,0),([1,2],[3,4],'equal',0,1,0),
                     ([1,2],[3,4],'equal',0,1,2,-1),([1,2],[3,4],'equal',0,1,2,1,1),
                     ([1,2],[3,4],'equal',0,1,2,1,.95,1),
                     ([1,2],[3,4],'equal',0,1,2,1,.99999,2000),
                     ([1,2],[3,4],'equal',0,1,2,1,.95,2000,-1)]:
            with self.subTest(args=args),self.assertRaises(MathError):run('bayescompare',*args)

if __name__=='__main__':unittest.main()
