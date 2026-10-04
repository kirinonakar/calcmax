import json
import math
import pathlib
import sys
import unittest

sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]/'app/src/main/python'))
import calc_engine
import symvacas_catalog as calc
import sympy as s


class CauchyCatalogTests(unittest.TestCase):
    def test_exact_values_defaults_and_location_scale(self):
        self.assertEqual(calc.cauchypdf(0),1/s.pi)
        self.assertEqual(calc.cauchypdf(3,3,2),1/(2*s.pi))
        self.assertEqual(calc.cauchycdf(1),s.Rational(3,4))
        self.assertEqual(calc.cauchycdf(-1,1),s.Rational(1,2))
        self.assertEqual(calc.cauchycdf(5,3,2),s.Rational(3,4))
        self.assertEqual(calc.cauchycdf(1,5,3,2),s.Rational(1,2))
        self.assertEqual(calc.invcauchy(s.Rational(3,4)),1)
        self.assertEqual(calc.invcauchy(s.Rational(1,4),3,2),1)
        self.assertEqual(calc.invcauchy(s.Rational(1,2),3,2),3)
        for function,reference in ((calc.cauchypdf,calc.tpdf),(calc.cauchycdf,calc.tcdf)):
            self.assertAlmostEqual(float(function(2).evalf()),float(reference(2,1).evalf()))

    def test_infinities_and_extreme_tails(self):
        self.assertEqual(calc.cauchycdf(-s.oo),0)
        self.assertEqual(calc.cauchycdf(s.oo),1)
        self.assertEqual(calc.cauchycdf(-s.oo,s.oo,3,2),1)
        self.assertEqual(calc.cauchypdf(s.oo),0)
        self.assertEqual(calc.invcauchy(0),-s.oo)
        self.assertEqual(calc.invcauchy(1),s.oo)
        tail=calc.cauchycdf(-10**60)
        self.assertAlmostEqual(float(tail.evalf())/(1e-60/math.pi),1,places=13)
        interval=calc.cauchycdf(10**60,2*10**60)
        self.assertAlmostEqual(float(interval.evalf())/(5e-61/math.pi),1,places=13)
        q=s.Rational(1,10**60)
        self.assertAlmostEqual(float((calc.cauchycdf(calc.invcauchy(q))/q).evalf()),1,places=13)

    def test_catalog_dispatch_is_independent_of_angle_mode(self):
        for angle in ('DEG','RAD','GRAD'):
            for name,args,expected in [('cauchypdf',[0,0,1],1/math.pi),('cauchycdf',[-1,1,0,1],.5),('invcauchy',[.75,0,1],1)]:
                tree={'kind':'call','value':name,'args':[{'kind':'number','value':str(v)} for v in args]}
                result=json.loads(calc_engine.dispatch(json.dumps({'tree':tree,'angle':angle})))
                self.assertTrue(result['ok'],result)
                self.assertAlmostEqual(float(result['decimal']),expected,places=14)

    def test_invalid_parameters_bounds_and_arities(self):
        for name,args in [('cauchypdf',[]),('cauchypdf',[1,2]),('cauchycdf',[2,1]),('cauchycdf',[1,0,0]),('invcauchy',[-.1]),('invcauchy',[1.1]),
                          ('cauchypdf',[1,s.oo,1]),('cauchypdf',[1,0,s.oo]),('cauchycdf',[s.I]),('invcauchy',[s.nan]),('cauchypdf',[1,0,-1])]:
            with self.subTest(name=name,args=args):
                with self.assertRaises(calc_engine.MathError): getattr(calc,name)(*args)


if __name__ == '__main__': unittest.main()
