import json
import pathlib
import sys
import unittest
import random
import subprocess
from fractions import Fraction

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "app/src/main/python"))
import calc_engine as core

CASES = json.loads((ROOT / "build/math-cases.json").read_text(encoding="utf-8"))
TREES = {case["source"]: case["tree"] for case in CASES}

def run(source, **options):
    return json.loads(core.dispatch(json.dumps({"tree": TREES[source], "angle": "RAD", **options})))

class EngineTests(unittest.TestCase):
    def test_exact_rational_properties(self):
        rng=random.Random(991)
        def rational(a,b): return {"kind":"binary","value":"/","args":[{"kind":"number","value":str(a)},{"kind":"number","value":str(b)}]}
        for _ in range(100):
            a,b,c,d=[rng.randint(1,999999) for _ in range(4)]
            tree={"kind":"binary","value":"+","args":[rational(a,b),rational(c,d)]}
            result=json.loads(core.dispatch(json.dumps({"tree":tree})))
            self.assertTrue(result["ok"],result)
            self.assertEqual(Fraction(result["exact"]),Fraction(a,b)+Fraction(c,d))
    def test_result_ast_round_trip(self):
        for source in ["1/3+1/6","sqrt(8)","diff(sin(x^2),x)","solve(x^2-5x+6=0,x)","(1+i)^2","qty(2,m)+qty(30,cm)"]:
            result=run(source)
            with self.subTest(source=source):
                self.assertIn("resultAst",result)
                again=json.loads(core.dispatch(json.dumps({"tree":result["resultAst"],"angle":"DEG"})))
                self.assertEqual(again["exact"],result["exact"])
    def test_precision_and_budget(self):
        result=run("1/3+1/6",precision=100)
        self.assertEqual(result["decimal"],"0.5")
        third={"kind":"binary","value":"/","args":[{"kind":"number","value":"1"},{"kind":"number","value":"3"}]}
        self.assertGreaterEqual(len(json.loads(core.dispatch(json.dumps({"tree":third,"precision":100})))["decimal"]),100)
        self.assertEqual(json.loads(core.dispatch(json.dumps({"tree":third,"precision":3})))["decimal"],"0.333")
        self.assertEqual(json.loads(core.dispatch(json.dumps({"tree":third,"precision":10})))["decimal"],"0.3333333333")
        huge={"kind":"number","value":"1e100000000"}
        self.assertFalse(json.loads(core.dispatch(json.dumps({"tree":huge})))["ok"])
        self.assertFalse(run("integrate(x^2*sin(x),x)",budget=-1)["ok"])
    def test_exact_examples(self):
        for case in CASES:
            if "expected" in case:
                with self.subTest(source=case["source"]):
                    result=run(case["source"])
                    self.assertTrue(result["ok"], result)
                    self.assertEqual(result["exact"],case["expected"])
    def test_errors(self):
        for source in ["1/0","0^0","inverse([[1,2],[2,4]])","dot([1,2],[1,2,3])","convert(1,m,kg)","convert(-1,K,degC)","factorial(-1)"]:
            with self.subTest(source=source): self.assertFalse(run(source)["ok"])
    def test_principal_root_and_domains(self):
        self.assertEqual(run("sqrt(x^2)")["exact"],"sqrt(x**2)")
        self.assertTrue(run("simplify((x^2-1)/(x-1))")["conditions"])
        self.assertEqual(run("solve((x^2-1)/(x-1)=2,x)")["exact"],"EmptySet")
        restricted=run("simplify((x^2-1)/(x-1))")["resultAst"]
        substitution={"kind":"call","value":"subs","args":[restricted,{"kind":"symbol","value":"x"},{"kind":"number","value":"1"}]}
        invalid=json.loads(core.dispatch(json.dumps({"tree":substitution})))
        self.assertFalse(invalid["ok"])
        substitution["args"][2]["value"]="2"
        self.assertEqual(json.loads(core.dispatch(json.dumps({"tree":substitution})))["exact"],"3")
    def test_numeric(self):
        self.assertAlmostEqual(float(run("nsolve(cos(x)-x,x,0,1)")["exact"]),.7390851332151607,12)
        self.assertAlmostEqual(float(run("nintegrate(sin(x),x,0,pi)")["exact"]),2,12)
        self.assertEqual(run("minimum(x^2,x,-2,3)")["exact"],"0")
        self.assertEqual(run("maximum(x^2,x,-2,3)")["exact"],"9")
    def test_sympy_integration_bounds(self):
        tuple_result=run("integrate(exp(-x^2)*cos(2x),(x,0,oo))")
        flat_result=run("integrate(exp(-x^2)*cos(2*x),x,0,oo)")
        self.assertTrue(tuple_result["ok"],tuple_result)
        self.assertEqual(tuple_result["exact"],flat_result["exact"])
        self.assertEqual(tuple_result["exact"],"sqrt(pi)*exp(-1)/2")
        self.assertEqual(run("integrate(exp(-x^2)*cos(2x),(x,0,oo))",variables={"x":{"kind":"number","value":"7"}})["exact"],tuple_result["exact"])
    def test_symbolic_coefficient_display_tree(self):
        result=run("cos(2*x)")
        self.assertTrue(result["ok"],result)
        self.assertEqual([part["kind"] for part in result["tree"]["args"][0]["args"]],["number","symbol"])
    def test_cold_sympy_integration_bounds(self):
        tree=TREES["integrate(exp(-x^2)*cos(2x),(x,0,oo))"]
        dependencies_path=str(pathlib.Path(core.s.__file__).resolve().parent.parent)
        script=("import sys,json\n"
                f"sys.path.insert(0,{dependencies_path!r})\n"
                f"sys.path.insert(0,{str(ROOT / 'app/src/main/python')!r})\n"
                "import calc_engine\n"
                f"print(calc_engine.dispatch({json.dumps(json.dumps({'tree':tree,'angle':'RAD'}))}))")
        result=subprocess.run([sys.executable,"-c",script],cwd=ROOT,capture_output=True,text=True,timeout=20)
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertTrue(json.loads(result.stdout)["ok"],result.stdout)
    def test_graph(self):
        result=run("sin(x)",action="graph",trees=[TREES["sin(x)"]],min=-3,max=3,samples=100)
        self.assertTrue(result["ok"],result)
        self.assertAlmostEqual(result["curves"][0][50][1],0)
        result=run("1/x",action="graph",trees=[TREES["1/x"]],min=-1,max=1,samples=100)
        self.assertIsNone(result["curves"][0][50])
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
    def test_result_shapes(self):
        for source in ["stats([1,2,3])","series(exp(x),x,0,4)","solve([x+y=3,x-y=1],[x,y])","integrate(exp(-x^2),x)","eigenvalues([[1,0],[0,2]])","lu([[1,2],[3,4]])"]:
            with self.subTest(source=source): self.assertTrue(run(source)["ok"],run(source))

if __name__=="__main__": unittest.main(verbosity=2)
