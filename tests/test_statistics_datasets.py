"""Statistics UI-sized literal tables through the public evaluation boundary."""
import json
import math
import pathlib
import random
import sys
import unittest

sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]/'app/src/main/python'))
from calc_engine import dispatch, Engine
from calc_shared import MathError
import sympy as s


def number(value): return {'kind':'number','value':str(value)}
def listing(values): return {'kind':'list','args':values}
def table(rows): return listing([listing([number(cell) for cell in row]) for row in rows])
def call(name,*args): return {'kind':'call','value':name,'args':list(args)}
def symbol(name): return {'kind':'symbol','value':name}
def evaluate(tree,**options): return json.loads(dispatch(json.dumps({'tree':tree,'precision':15,**options})))


class StatisticsDatasetTests(unittest.TestCase):
    def test_five_thousand_row_mixed_model_uses_the_public_literal_dispatch(self):
        rng=random.Random(17)
        rows=[]
        for i in range(5000):
            x=(i%17)/17
            rows.append([i%20,x,2+0.3*(i%20)+1.5*x+rng.gauss(0,0.2)])
        result=evaluate(call('mixedmodel',table(rows)))
        self.assertTrue(result['ok'],result.get('error'))
        coefficients=next(section for section in result['statisticsReport']['sections'] if section['title']=='coefficients')
        estimates=[float(row[1]['exact']) for row in coefficients['rows']]
        self.assertLess(abs(estimates[1]-1.5),0.03)

    def test_five_predictor_logistic_with_fifteen_hundred_rows(self):
        rng=random.Random(17)
        rows=[]
        for _ in range(1500):
            xs=[rng.gauss(0,1) for _ in range(5)]
            rows.append(xs+[int(rng.random()<1/(1+math.exp(-0.4*xs[0]+0.3*xs[1])))])
        result=evaluate(call('regression',table(rows),symbol('logistic')))
        self.assertTrue(result['ok'],result.get('error'))
        self.assertEqual(6,len(result['regression']['coefficients']))
        self.assertLess(abs(float(result['regression']['coefficients'][1]['estimate'])-0.4),0.15)

    def test_numeric_lists_keep_exact_values_and_variable_references(self):
        values=listing([number('0.1'),{'kind':'unary','value':'-','args':[number('0.3')]},number('2e-1')])
        direct=evaluate(call('mean',values))
        stored=evaluate(call('mean',symbol('D1')),variables={'D1':values})
        self.assertEqual('0',direct['exact'])
        self.assertEqual(direct['resultAst'],stored['resultAst'])
        engine=Engine({})
        engine.prepare_statistics_dataset(values)
        self.assertEqual(s.Rational(-3,10),engine.build(values)[1])
        packed=evaluate(call('mean',symbol('Data')),statisticsDatasets={'Data':['0.1','-0.3','2e-1']})
        self.assertTrue(packed['ok'],packed)
        self.assertEqual(direct['resultAst'],packed['resultAst'])
        invalid=evaluate(call('mean',symbol('Data')),statisticsDatasets={'Data':['1','sin(x)']})
        self.assertFalse(invalid['ok'])

    def test_dataset_bounds_and_formula_complexity_are_separate(self):
        for rows in [[[1]]*5001,[[1]*102]]:
            result=evaluate(call('mixedmodel',table(rows)))
            self.assertFalse(result['ok'])
            self.assertIn('Statistics dataset limit',result['error'])
        # Expressions inside cells must not bypass the normal node budget.
        formula={'kind':'binary','value':'+','args':[number(1),number(2)]}
        result=evaluate(call('mean',listing([formula]*4500)))
        self.assertFalse(result['ok'])
        self.assertIn('Expression complexity limit',result['error'])
        # A normal matrix/list also keeps the expression limit.
        with self.assertRaisesRegex(MathError,'Expression complexity limit'):
            Engine({}).build(listing([number(1)]*12001))
        too_large=evaluate(call('mean',listing([number('1e100001')])))
        self.assertFalse(too_large['ok'])
        self.assertIn('Decimal exponent limit',too_large['error'])


if __name__=='__main__': unittest.main()
