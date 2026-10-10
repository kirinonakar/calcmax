"""Proportion score-test references and public-dispatch boundary checks."""
import json
import math
import unittest
from test_advanced_statistics import evaluate, tree
from calc_engine import dispatch
from calc_evaluator import Engine
import symvacas_catalog as catalog


class ProportionTests(unittest.TestCase):
    def raw(self, source):
        return Engine({'precision':40}).build(tree(source))

    def test_single_sample_null_variance_counts_binary_and_tails(self):
        # z = (.6-.5)/sqrt(.5*.5/100) = 2, not the sample-based Wald SE.
        result=self.raw('propztest(0.5,60,100)')
        self.assertAlmostEqual(float(result['z']),2,places=13)
        self.assertAlmostEqual(float(result['p value']),math.erfc(math.sqrt(2)),places=13)
        self.assertEqual(str(result['sample proportion']),'3/5')
        self.assertEqual(result['n'],100)
        self.assertAlmostEqual(float(catalog.propztest(.5,60,100,catalog.right)['p value']),math.erfc(math.sqrt(2))/2,places=13)
        self.assertAlmostEqual(float(self.raw('propztest(0.5,60,100,left)')['p value']),1-math.erfc(math.sqrt(2))/2,places=13)
        self.assertEqual(self.raw('propztest(0.5,[[30,50],[30,50]])')['sample proportion'],result['sample proportion'])
        self.assertEqual(self.raw('propztest(0.5,[1,1,1,0,0])')['sample proportion'],result['sample proportion'])
        report=evaluate('propztest(0.5,60,100)')['statisticsReport']
        self.assertEqual(report['title'],'One-sample proportion z test')
        self.assertTrue(any(section['title']=='Normal approximation checks' for section in report['sections']))
        self.assertFalse(any(plot['kind']=='qq' for plot in report['plots']))

    def test_two_samples_pooled_variance_and_direction(self):
        # Independently computed pooled=.525, z=.15/sqrt(.525*.475*.02).
        z=.15/math.sqrt(.525*.475*.02)
        result=self.raw('propztest2(60,100,45,100)')
        self.assertAlmostEqual(float(result['z']),z,places=13)
        self.assertAlmostEqual(float(result['p value']),math.erfc(z/math.sqrt(2)),places=13)
        self.assertEqual(str(result['pooled proportion']),'21/40')
        reversed_=self.raw('propztest2(45,100,60,100)')
        self.assertAlmostEqual(float(reversed_['z']),-z,places=13)
        self.assertEqual(result['p value'],reversed_['p value'])
        lists=self.raw('propztest2([[60,100]],[[45,100]])')
        self.assertEqual(lists['z'],result['z'])
        self.assertAlmostEqual(float(self.raw('propztest2([1,1,0],[0,0])')['proportion difference (A − B)']),2/3,places=13)

    def test_small_counts_degenerate_samples_and_invalid_inputs(self):
        for source in ('propztest(0.5,0,5)','propztest(0.5,5,5)','propztest2(0,5,3,5)'):
            result=evaluate(source)
            self.assertTrue(any('below 10' in note for note in result['statisticsReport']['notes']))
        for source in ('propztest(0,1,10)','propztest(1,1,10)','propztest(0.5,-1,10)',
                       'propztest(0.5,11,10)','propztest(0.5,1.5,10)','propztest(0.5,1,0)',
                       'propztest(0.5,[0,2])','propztest(0.5,[])','propztest(0.5,[[1,2,3]])',
                       'propztest2(0,10,0,20)','propztest2(10,10,20,20)','propztest2(1,2,3)',
                       'propztest2([0,2],[1,0])'):
            with self.subTest(source=source):
                result=json.loads(dispatch(json.dumps({'tree':tree(source)})))
                self.assertFalse(result['ok'])
