import json
import pathlib
import sys
import unittest
import random
from fractions import Fraction

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "app/src/main/python"))
import calc_engine as core
import sympy as s
from calc_shared import numeric_integral

CASES = json.loads((ROOT / "build/math-cases.json").read_text(encoding="utf-8"))
TREES = {case["source"]: case["tree"] for case in CASES}

def run(source, **options):
    return json.loads(core.dispatch(json.dumps({"tree": TREES[source], "angle": "RAD", **options})))

class EngineTests(unittest.TestCase):
    def test_reusable_answers_round_trip_without_losing_domain_guards(self):
        with self.subTest(scenario='result_ast_round_trip'):
            for source in ["1/3+1/6","sqrt(8)","diff(sin(x^2),x)","solve(x^2-5x+6=0,x)","(1+i)^2","qty(2,m)+qty(30,cm)"]:
                result=run(source)
                with self.subTest(source=source):
                    self.assertIn("resultAst",result)
                    again=json.loads(core.dispatch(json.dumps({"tree":result["resultAst"],"angle":"DEG"})))
                    self.assertEqual(again["exact"],result["exact"])
        with self.subTest(scenario='principal_root_and_domains'):
            self.assertEqual(run("sqrt(x^2)")["exact"],"sqrt(x**2)")
            self.assertTrue(run("simplify((x^2-1)/(x-1))")["conditions"])
            self.assertEqual(run("solve((x^2-1)/(x-1)=2,x)")["exact"],"EmptySet")
            restricted=run("simplify((x^2-1)/(x-1))")["resultAst"]
            substitution={"kind":"call","value":"subs","args":[restricted,{"kind":"symbol","value":"x"},{"kind":"number","value":"1"}]}
            invalid=json.loads(core.dispatch(json.dumps({"tree":substitution})))
            self.assertFalse(invalid["ok"])
            substitution["args"][2]["value"]="2"
            self.assertEqual(json.loads(core.dispatch(json.dumps({"tree":substitution})))["exact"],"3")
    def test_angle_mode_applies_inside_nested_calls(self):
        def call(name,argument): return {"kind":"call","value":name,"args":[argument]}
        def number(value): return {"kind":"number","value":str(value)}
        def evaluate(tree,angle="DEG",variables=None): return json.loads(core.dispatch(json.dumps({"tree":tree,"angle":angle,"variables":variables or {}})))
        chain=number(9)
        for name in ["sin","cos","tan","atan","acos","asin"]: chain=call(name,chain)
        for angle in ("DEG","GRAD"):
            with self.subTest(angle=angle):
                result=evaluate(chain,angle)
                self.assertTrue(result["ok"],result)
                self.assertAlmostEqual(float(result["decimal"]),9,places=9)
        tangent=evaluate(call("tan",call("cos",call("sin",number(9)))))
        self.assertTrue(tangent["ok"],tangent)
        self.assertLess(abs(float(tangent["decimal"])),0.1)  # Reading the inner value as radians would give ≈1.56
        sixth={"kind":"binary","value":"/","args":[{"kind":"symbol","value":"pi"},number(6)]}
        self.assertEqual(evaluate(call("sin",sixth))["exact"],"1/2")
        self.assertEqual(evaluate(call("sin",call("degree",number(30))))["exact"],"1/2")
        self.assertEqual(evaluate(call("sin",{"kind":"symbol","value":"x"}),variables={"x":sixth})["exact"],"1/2")
        self.assertEqual(evaluate(call("sin",{"kind":"symbol","value":"x"}),variables={"x":number(2)})["exact"],"sin(pi/90)")
    def test_large_results_stay_bounded_and_preserve_exact_reusable_values(self):
        # Long exact results are abbreviated in the view but remain reusable at full precision.
        with self.subTest(scenario='large_exact_integer_serialization'):
            for n in (2000,10000):
                tree={"kind":"call","value":"factorial","args":[{"kind":"number","value":str(n)}]}
                with self.subTest(n=n):
                    result=json.loads(core.dispatch(json.dumps({"tree":tree})))
                    self.assertTrue(result["ok"],result)
                    expected=str(core.s.factorial(n))
                    if len(expected)<=10000: self.assertEqual(result["exact"],expected)
                    else:
                        self.assertIn(f"{len(expected)} digits",result["exact"])
                        self.assertLess(len(result["exact"]),200)
                        self.assertEqual(result["resultAst"]["value"],expected)
        with self.subTest(scenario='large_exponents_use_result_size_and_preserve_symbols'):
            num=lambda value:{"kind":"number","value":str(value)}
            power=lambda base,exponent:{"kind":"binary","value":"^","args":[base,num(exponent)]}
            evaluate=lambda tree:json.loads(core.dispatch(json.dumps({"tree":tree})))
            exact=evaluate(power(num(2),100000))
            self.assertTrue(exact["ok"],exact)
            self.assertIn("30103 digits",exact["exact"])
            self.assertEqual(exact["resultAst"]["value"],str(2**100000))
            self.assertEqual(evaluate(exact["resultAst"])["resultAst"]["value"],str(2**100000))
            large=evaluate(power(num(999999),100000))
            self.assertTrue(large["ok"],large)
            self.assertEqual(large["exact"],"999999**100000")
            self.assertLess(len(json.dumps(large)),1000)
            enormous=evaluate(power(num(10),10000000))
            self.assertTrue(enormous["ok"],enormous)
            self.assertEqual(enormous["exact"],"10**10000000")
            self.assertLess(len(json.dumps(enormous)),1000)
            self.assertEqual(evaluate(power(num(2),1000000000))["exact"],"2**1000000000")
            symbolic=evaluate(power({"kind":"symbol","value":"x"},1000000000))
            self.assertEqual(symbolic["exact"],"x**1000000000")
            relaxed=evaluate(power(num(2),200000))
            self.assertTrue(relaxed["ok"],relaxed)
            self.assertIn("60206 digits",relaxed["exact"])
            small=evaluate(power(num(2),1000))
            self.assertEqual(small["exact"],str(2**1000))
            reciprocal=evaluate(power(num(2),-100000))
            self.assertTrue(reciprocal["ok"],reciprocal)
            self.assertIn("30103 digits",reciprocal["exact"])
            self.assertLess(len(reciprocal["exact"]),200)
            scientific=evaluate(num("1.2e100000"))
            self.assertTrue(scientific["ok"],scientific)
            self.assertEqual(scientific["exact"],"(6/5)*10**100000")
            self.assertLess(len(json.dumps(scientific["tree"])),1000)
            self.assertEqual(evaluate(scientific["resultAst"])["exact"],scientific["exact"])

    def test_exact_results_match_golden_fixtures_and_rational_arithmetic(self):
        with self.subTest(scenario='exact_examples'):
            for case in CASES:
                if "expected" in case:
                    with self.subTest(source=case["source"]):
                        result=run(case["source"])
                        self.assertTrue(result["ok"], result)
                        self.assertEqual(result["exact"],case["expected"])
        with self.subTest(scenario='exact_rational_properties'):
            rng=random.Random(991)
            def rational(a,b): return {"kind":"binary","value":"/","args":[{"kind":"number","value":str(a)},{"kind":"number","value":str(b)}]}
            for _ in range(100):
                a,b,c,d=[rng.randint(1,999999) for _ in range(4)]
                tree={"kind":"binary","value":"+","args":[rational(a,b),rational(c,d)]}
                result=json.loads(core.dispatch(json.dumps({"tree":tree})))
                self.assertTrue(result["ok"],result)
                self.assertEqual(Fraction(result["exact"]),Fraction(a,b)+Fraction(c,d))
    def test_invalid_requests_and_precision_budget_boundaries(self):
        with self.subTest(scenario='errors'):
            for source in ["1/0","0^0","inverse([[1,2],[2,4]])","dot([1,2],[1,2,3])","convert(1,m,kg)","convert(-1,K,degC)","factorial(-1)"]:
                with self.subTest(source=source): self.assertFalse(run(source)["ok"])
        with self.subTest(scenario='precision_and_budget'):
            with self.subTest(scenario='precision_and_budget'):
                result=run("1/3+1/6",precision=100)
                self.assertEqual(result["decimal"],"0.5")
                third={"kind":"binary","value":"/","args":[{"kind":"number","value":"1"},{"kind":"number","value":"3"}]}
                self.assertGreaterEqual(len(json.loads(core.dispatch(json.dumps({"tree":third,"precision":100})))["decimal"]),100)
                self.assertEqual(json.loads(core.dispatch(json.dumps({"tree":third,"precision":3})))["decimal"],"0.333")
                self.assertEqual(json.loads(core.dispatch(json.dumps({"tree":third,"precision":10})))["decimal"],"0.3333333333")
                huge={"kind":"number","value":"1e100000000"}
                self.assertFalse(json.loads(core.dispatch(json.dumps({"tree":huge})))["ok"])
                self.assertFalse(run("integrate(x^2*sin(x),x)",budget=-1)["ok"])
            with self.subTest(scenario='display_trees_keep_internal_precision_for_decimal_place_formatting'):
                third={"kind":"binary","value":"/","args":[{"kind":"number","value":"1"},{"kind":"number","value":"3"}]}
                wide=json.loads(core.dispatch(json.dumps({"tree":third,"precision":100,"displayDigits":10})))
                self.assertEqual(wide["exact"],"1/3")
                self.assertGreaterEqual(len(wide["decimal"]),100)
                self.assertEqual(wide["decimalTree"]["value"],wide["decimal"])
                large=json.loads(core.dispatch(json.dumps({"tree":{"kind":"number","value":"12342456656.123456789"},"precision":30,"displayDigits":5})))
                self.assertTrue(large["decimalTree"]["value"].startswith("12342456656.123456789"))
                # The engine only supplies digits supported by internal precision.
                self.assertEqual(json.loads(core.dispatch(json.dumps({"tree":third,"precision":10,"displayDigits":40})))["decimal"],"0.3333333333")
                # The display cap must not shorten the value Ans and STO reuse.
                reused=json.loads(core.dispatch(json.dumps({"tree":{"kind":"binary","value":"*","args":[wide["resultAst"],{"kind":"number","value":"3"}]},"precision":100,"displayDigits":10})))
                self.assertEqual(reused["exact"],"1")
                # Floating-point results follow the same split.
                root=json.loads(core.dispatch(json.dumps({"tree":TREES["nsolve(cos(x)-x,x,0,1)"],"precision":50,"displayDigits":10})))
                self.assertGreaterEqual(len(root["exact"]),50)
                self.assertEqual(root["tree"]["value"],root["exact"])
                self.assertGreaterEqual(len(json.loads(core.dispatch(json.dumps({"tree":TREES["nsolve(cos(x)-x,x,0,1)"],"precision":50})))["exact"]),30)
    def test_programmer(self):
        def bit(**kw): return json.loads(core.dispatch(json.dumps({"action":"programmer",**kw})))
        self.assertEqual(bit(a="FF",base=16,width=8,signed=True)["exact"],"-1")
        self.assertEqual(bit(a="170",b="15",op="AND")["exact"],"10")
        self.assertEqual(bit(a="0",width=8,op="NOT")["bases"]["HEX"],"FF")
        self.assertFalse(bit(a="1",b="64",op="<<",width=64)["ok"])
    def test_variables_functions_and_assumptions(self):
        number={"kind":"number","value":"7"}
        self.assertEqual(run("x+1",variables={"x":number})["exact"],"8")
        self.assertEqual(run("diff(x^3,x)",variables={"x":number})["exact"],"3*x**2")
        self.assertEqual(run("sqrt(x^2)",assumptions={"x":["real"]})["exact"],"Abs(x)")
        self.assertEqual(run("f(3)",functions={"f":{"parameters":["x"],"body":TREES["x+1"]}})["exact"],"4")
        self.assertEqual(run("solve(x^2+1=0,x)",assumptions={"x":["real"]})["exact"],"EmptySet")

def number(value): return {"kind": "number", "value": str(value)}
def symbol(name): return {"kind": "symbol", "value": name}
def binary(op, left, right): return {"kind": "binary", "value": op, "args": [left, right]}

x = symbol("x")
polynomial = binary("-", binary("-", binary("*", number(3), binary("^", x, number(2))),
                               binary("*", number(16), x)), number(20))

class NumericIntegralTests(unittest.TestCase):
    def dispatch(self, **request):
        result = json.loads(core.dispatch(json.dumps(request)))
        self.assertTrue(result["ok"], result)
        return result

    def test_nonpolynomial_quadrature_and_infinite_bounds(self):
        variable = s.Symbol("x")
        self.assertAlmostEqual(2, float(numeric_integral(s.sin(variable), variable, 0, s.pi, 30)))
        self.assertAlmostEqual(1, float(numeric_integral(s.exp(-variable), variable, 0, s.oo, 30)))
        divergent = {"kind": "call", "value": "nintegrate", "args": [
            binary("/", number(1), binary("^", x, number(2))), x, number(-1), number(1)]}
        result = json.loads(core.dispatch(json.dumps({"tree": divergent})))
        self.assertFalse(result["ok"], result)

if __name__ == "__main__":
    unittest.main()
