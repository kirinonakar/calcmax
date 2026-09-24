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
    def test_prime_index_and_primality_are_distinct(self):
        def call(name, value):
            tree={"kind":"call","value":name,"args":[{"kind":"number","value":str(value)}]}
            return json.loads(core.dispatch(json.dumps({"tree":tree})))
        self.assertEqual(call("prime",1)["exact"],"2")
        self.assertEqual(call("prime",1000)["exact"],"7919")
        self.assertEqual(call("isprime",123457)["exact"],"True")
        self.assertEqual(call("isprime",123456)["exact"],"False")
        self.assertEqual(call("isprime",-7)["exact"],"False")
        self.assertFalse(call("prime",0)["ok"])
        self.assertFalse(call("prime",100001)["ok"])
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
    def test_large_exact_integer_serialization(self):
        # Results beyond CPython's default 4300-digit int->str cap must still serialize.
        for n in (2000,10000):
            tree={"kind":"call","value":"factorial","args":[{"kind":"number","value":str(n)}]}
            with self.subTest(n=n):
                result=json.loads(core.dispatch(json.dumps({"tree":tree})))
                self.assertTrue(result["ok"],result)
                self.assertEqual(result["exact"],str(core.s.factorial(n)))
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
    def test_graph_analysis_finds_all_intersections_and_tangencies(self):
        def node(kind, value="", args=()): return {"kind":kind,"value":value,"args":list(args)}
        x=node("symbol","x")
        polynomial=node("binary","*",[x,node("binary","-",[x,node("number","2")])])
        zero=node("number","0")
        def analyze(trees, action, a, b):
            return json.loads(core.dispatch(json.dumps({"action":"graphAnalysis","trees":trees,"analysis":action,"selected":0,"other":1,"a":a,"b":b})))
        crossings=analyze([polynomial,zero],"intersection",-1,3)
        self.assertTrue(crossings["ok"],crossings)
        self.assertEqual(len(crossings["points"]),2)
        self.assertAlmostEqual(crossings["points"][0][0],0,places=6)
        self.assertAlmostEqual(crossings["points"][1][0],2,places=6)
        tangent=node("binary","^",[node("binary","-",[x,node("binary","/",[node("number","1"),node("number","3")])]),node("number","2")])
        touch=analyze([tangent,zero],"intersection",0,1)
        self.assertTrue(touch["ok"],touch)
        self.assertEqual(len(touch["points"]),1)
        self.assertAlmostEqual(touch["points"][0][0],1/3,places=6)
        minimum=analyze([polynomial],"minimum",-1,3)
        self.assertTrue(minimum["ok"],minimum)
        self.assertAlmostEqual(minimum["points"][0][0],1,places=6)
        roots=analyze([polynomial],"root",-1,3)
        self.assertEqual(len(roots["points"]),2)
        derivative=analyze([polynomial],"derivative",1,1)
        self.assertTrue(derivative["ok"],derivative)
        self.assertAlmostEqual(derivative["value"],0,places=6)
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

    def test_engineering_cas_extensions(self):
        def node(kind,value="",*args): return {"kind":kind,"value":value,"args":list(args)}
        def sym(name): return node("symbol",name)
        def num(value): return node("number",str(value))
        def call(name,*args): return node("call",name,*args)
        def binary(op,left,right): return node("binary",op,left,right)
        def power(base,exponent): return binary("^",base,exponent)
        def dispatch(tree,**options): return json.loads(core.dispatch(json.dumps({"tree":tree,"angle":"RAD",**options})))
        x,y,t,w=map(sym,("x","y","t","w"))

        rational=binary("/",num(1),binary("*",x,binary("+",x,num(1))))
        apart=dispatch(call("apart",rational,x))
        self.assertTrue(apart["ok"],apart)
        self.assertEqual(apart["exact"],"-1/(x + 1) + 1/x")

        scalar=binary("+",power(x,num(2)),power(y,num(2)))
        coords=node("list","",x,y)
        gradient=dispatch(call("gradient",scalar,coords))
        self.assertTrue(gradient["ok"],gradient)
        self.assertEqual(gradient["exact"],"[2*x, 2*y]")
        field=node("list","",binary("*",x,y),power(y,num(2)))
        curl=dispatch(call("curl",field,coords))
        self.assertTrue(curl["ok"],curl)
        self.assertEqual(curl["exact"],"-x")

        fy=call("y",t)
        ode=node("relation","=",call("diff",fy,t),fy)
        dsolve=dispatch(call("dsolve",ode,fy,t),budget=30)
        self.assertTrue(dsolve["ok"],dsolve)
        self.assertIn("exp(t)",dsolve["exact"])
        initial=node("list","",node("relation","=",call("y",num(0)),num(1)))
        solved=dispatch(call("dsolve",ode,fy,t,initial),budget=30)
        self.assertTrue(solved["ok"],solved)
        self.assertIn("exp(t)",solved["exact"])

        laplace=dispatch(call("laplace",call("sin",t),t,w),budget=30)
        self.assertTrue(laplace["ok"],laplace)
        self.assertEqual(laplace["exact"],"1/(w**2 + 1)")
        inverse=dispatch(call("ilaplace",binary("/",num(1),binary("+",power(w,num(2)),num(1))),w,t),budget=30)
        self.assertTrue(inverse["ok"],inverse)
        self.assertIn("sin(t)",inverse["exact"])

        mat=node("list","",node("list","",num(1),num(2)),node("list","",num(3),num(4)))
        characteristic=dispatch(call("charpoly",mat,x))
        self.assertTrue(characteristic["ok"],characteristic)
        self.assertEqual(characteristic["exact"],"x**2 - 5*x - 2")
        self.assertTrue(dispatch(call("qr",node("list","",node("list","",num(4),num(2)),node("list","",num(2),num(3)))))["ok"])
        self.assertTrue(dispatch(call("nullspace",node("list","",node("list","",num(1),num(2)),node("list","",num(2),num(4)))))["ok"])

        voltage=call("qty",num(1),sym("V")); resistance=call("qty",num(1),sym("ohm"))
        converted=dispatch(call("convert",binary("/",voltage,resistance),sym("A")))
        self.assertTrue(converted["ok"],converted)
        self.assertEqual(converted["exact"],"1")
        derivative=dispatch(call("nderivative",call("sin",x),x,num(0)))
        self.assertTrue(derivative["ok"],derivative)
        self.assertEqual(derivative["exact"],"1")
        covariance=dispatch(call("covariance",node("list","",num(1),num(2),num(3)),node("list","",num(2),num(4),num(6))))
        self.assertTrue(covariance["ok"],covariance)
        self.assertEqual(covariance["exact"],"4/3")

    if __name__=="__main__": unittest.main(verbosity=2)
