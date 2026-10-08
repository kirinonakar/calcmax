"""Derivative and domain checks for the rational trig substitution fallback."""
import json
import pathlib
import sys
import unittest

sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]/"app/src/main/python"))
import sympy as s
import calc_engine
from calc_integrals import rational_trig_primitive


def node(kind,value="",*args):
    return {"kind":kind,"value":str(value),"args":list(args)}


def evaluate(tree,**options):
    return json.loads(calc_engine.dispatch(json.dumps({"tree":tree,**options})))


class RationalTrigIntegralTests(unittest.TestCase):
    def test_primitives_differentiate_to_original_integrands(self):
        x=s.Symbol("x")
        expressions=[s.sqrt(s.tan(x)),s.sqrt(s.tan(2*x+1)),s.sqrt(s.tan(-2*x+1)),
                     s.sqrt(s.cot(x)),1/s.sqrt(s.tan(x)),s.tan(x)**s.Rational(1,3)]
        for expression in expressions:
            with self.subTest(expression=expression):
                primitive,conditions,note=rational_trig_primitive(expression,x)
                self.assertFalse(primitive.has(s.Integral))
                self.assertEqual(0,s.trigsimp(s.simplify(s.diff(primitive,x)-expression)))
                self.assertIn(s.Eq(s.im(x),0),conditions)
                self.assertIn("continuous real interval",note)
        t=s.Symbol("t",positive=True)
        formula=(s.atan(s.sqrt(2)*t-1)+s.atan(s.sqrt(2)*t+1))/s.sqrt(2)+s.log((t**2-s.sqrt(2)*t+1)/(t**2+s.sqrt(2)*t+1))/(2*s.sqrt(2))
        self.assertEqual(0,s.cancel(s.diff(formula,t)-2*t**2/(1+t**4)))

    def test_dispatch_preserves_constant_parameters_and_branch_guards_in_ans(self):
        x=node("symbol","x")
        integrand=node("call","sqrt",node("call","tan",x))
        result=evaluate(node("call","integrate",integrand,x),variables={"x":node("number",99)})
        self.assertTrue(result["ok"],result)
        self.assertNotIn("Integral",result["exact"])
        self.assertIn("atan",result["exact"])
        self.assertIn(" + C",result["exact"])
        self.assertIn("tan(x) > 0",result["conditions"])
        self.assertEqual(["x"],result["resultAst"]["parameters"])
        engine=calc_engine.Engine({"angle":"DEG"})
        primitive=engine.build(result["resultAst"])
        self.assertEqual(0,s.simplify(s.diff(primitive,engine.symbol("x"))-s.sqrt(s.tan(engine.symbol("x")))))
        for argument in ("0.5", "3.5"):
            applied=evaluate(node("call","Ans",node("number",argument)),variables={"Ans":result["resultAst"]},angle="DEG")
            self.assertTrue(applied["ok"],applied)
            self.assertIn("C",applied["exact"])
        for argument in ("-0.5", "0", "2"):
            self.assertFalse(evaluate(node("call","Ans",node("number",argument)),variables={"Ans":result["resultAst"]})["ok"])
        imaginary=evaluate(node("call","Ans",node("symbol","i")),variables={"Ans":result["resultAst"]})
        self.assertFalse(imaginary["ok"],imaginary)

    def test_rule_skips_unsupported_branches_and_nonlinear_substitutions(self):
        x=s.Symbol("x")
        for expression in [s.sqrt(s.tan(x)+x),s.sqrt(s.tan(x**2)),s.sqrt(s.tan(x+s.I)),
                           s.tan(x)**s.Rational(1,5),s.sqrt(s.tan(x)*s.tan(2*x))]:
            self.assertIsNone(rational_trig_primitive(expression,x))
        nonreal=s.Symbol("z",real=False)
        self.assertIsNone(rational_trig_primitive(s.sqrt(s.tan(nonreal)),nonreal))


if __name__=="__main__":
    unittest.main()
