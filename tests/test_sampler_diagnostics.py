"""Portable sampler diagnostics against an independent ArviZ reference."""
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'app/src/main/python'))
from calc_nuts import rank_diagnostics, sample


class SamplerDiagnosticsTests(unittest.TestCase):
    def test_rank_folded_rhat_and_ess_match_arviz(self):
        fixture=json.loads((ROOT/'tests/fixtures/sampler_diagnostics_reference.json').read_text())
        for case in fixture['cases']:
            with self.subTest(case=case['name']):
                actual=rank_diagnostics(case['chains'])
                for key,value in case['expected'].items():
                    self.assertAlmostEqual(actual[key],value,delta=1e-8*max(1,value),msg=key)
        self.assertEqual(rank_diagnostics([[1.]*100,[1.]*100]),{'rHat':None,'bulkEss':0.,'tailEss':0.,'ess':0.})

    def test_all_doublings_contribute_to_adaptation_and_reported_acceptance(self):
        def tree(target,state,log_slice,direction,depth,epsilon,initial_joint,rng):
            # First expansion: .2 / 1; second expansion: 1.8 / 3.
            return state,state,state,1,depth==0,.2 if depth==0 else 1.8,1 if depth==0 else 3,False
        with patch('calc_nuts._build_tree',tree),patch('calc_nuts._no_u_turn',return_value=True):
            _,report=sample(lambda q:(sum(v*v for v in q)/2,list(q)),1,100,50,2)
        self.assertAlmostEqual(report['meanAcceptanceProbability'],.5)
        self.assertTrue(all(abs(c['meanAcceptanceProbability']-.5)<1e-14 for c in report['chainDiagnostics']))
        self.assertEqual(report['meanLeapfrogSteps'],4)


if __name__=='__main__': unittest.main()
