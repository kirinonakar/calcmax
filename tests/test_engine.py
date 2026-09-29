import json
import pathlib
import sys
import unittest
import random
import subprocess
import math
from fractions import Fraction

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "app/src/main/python"))
import calc_engine as core

CASES = json.loads((ROOT / "build/math-cases.json").read_text(encoding="utf-8"))
TREES = {case["source"]: case["tree"] for case in CASES}

def run(source, **options):
    return json.loads(core.dispatch(json.dumps({"tree": TREES[source], "angle": "RAD", **options})))

class EngineTests(unittest.TestCase):
    def test_linear_regression_returns_pearson_correlation(self):
        def num(value): return {"kind":"number","value":str(value)}
        def regression(pairs,mode="linear"):
            table={"kind":"list","args":[{"kind":"list","args":[num(x),num(y)]} for x,y in pairs]}
            tree={"kind":"call","value":"regression","args":[table,{"kind":"symbol","value":mode}]}
            return json.loads(core.dispatch(json.dumps({"tree":tree,"angle":"RAD"})))
        rising=regression([(1,2),(2,4),(3,6)])
        falling=regression([(1,6),(2,4),(3,2)])
        uncorrelated=regression([(1,2),(2,1),(3,2)])
        flat=regression([(1,2),(2,2),(3,2)])
        quadratic=regression([(1,1),(2,4),(3,9)],"quadratic")
        self.assertTrue(all(result["ok"] for result in (rising,falling,uncorrelated,flat,quadratic)))
        self.assertEqual(1.0,rising["correlation"])
        self.assertEqual(-1.0,falling["correlation"])
        self.assertEqual(0.0,uncorrelated["correlation"])
        self.assertIsNone(flat["correlation"])
        self.assertNotIn("correlation",quadratic)

    def test_custom_nonlinear_regression_adc_ivim_and_decay(self):
        import sympy as s
        def symbol(name): return {"kind":"symbol","value":name}
        def number(value): return {"kind":"number","value":str(value)}
        def binary(op, left, right): return {"kind":"binary","value":op,"args":[left,right]}
        def exp(arg): return {"kind":"call","value":"exp","args":[arg]}
        def fit(pairs, formula, initials=None, independent="b"):
            table={"kind":"list","args":[{"kind":"list","args":[number(x),number(y)]} for x,y in pairs]}
            args=[table,symbol("custom"),formula,symbol(independent)]
            if initials is not None:
                args.append({"kind":"list","args":[{"kind":"list","args":[symbol(name),*[number(v) for v in values]]} for name,values in initials]})
            return json.loads(core.dispatch(json.dumps({"tree":{"kind":"call","value":"regression","args":args},"angle":"RAD"})))
        b=symbol("b")
        adc=exp(binary("*",number(-1),binary("*",b,symbol("ADC"))))
        adc_pairs=[(x,math.exp(-x*0.0012)) for x in range(0,1100,100)]
        adc_result=fit(adc_pairs,adc)
        self.assertTrue(adc_result["ok"],adc_result)
        fitted=s.sympify(adc_result["exact"])
        self.assertAlmostEqual(float(fitted.subs("b",500)),math.exp(-0.6),places=5)
        self.assertEqual(["ADC"],[name for name,_ in adc_result["parameters"]])
        self.assertAlmostEqual(0.0012,float(adc_result["parameters"][0][1]),places=8)
        self.assertGreater(len(adc_result["curve"]),100)

        slow=binary("*",binary("-",number(1),symbol("f")),exp(binary("*",number(-1),binary("*",b,symbol("D")))))
        fast=binary("*",symbol("f"),exp(binary("*",number(-1),binary("*",b,symbol("Dstar")))))
        ivim=binary("+",slow,fast)
        ivim_pairs=[(x,0.82*math.exp(-x*0.0008)+0.18*math.exp(-x*0.012)) for x in (0,10,20,40,80,120,200,400,600,800)]
        ivim_result=fit(ivim_pairs,ivim,[("f",[0.2,0,1]),("D",[0.001,0]),("Dstar",[0.01,0])])
        self.assertTrue(ivim_result["ok"],ivim_result)
        fitted=s.sympify(ivim_result["exact"])
        self.assertAlmostEqual(float(fitted.subs("b",80)),ivim_pairs[4][1],places=5)
        self.assertEqual({"D","Dstar","f"},{name for name,_ in ivim_result["parameters"]})
        values=dict(ivim_result["parameters"])
        self.assertAlmostEqual(0.18,float(values["f"]),places=4)
        self.assertAlmostEqual(0.0008,float(values["D"]),places=6)
        self.assertAlmostEqual(0.012,float(values["Dstar"]),places=4)
        self.assertNotIn("correlation",ivim_result)
        self.assertFalse(fit(ivim_pairs[:3],ivim)["ok"])

        x=symbol("x")
        decay=binary("+",binary("*",symbol("A"),exp(binary("*",number(-1),binary("*",symbol("k"),x)))),symbol("C"))
        decay_pairs=[(point,3*math.exp(-0.4*point)+0.6) for point in range(11)]
        decay_result=fit(decay_pairs,decay,independent="x")
        self.assertTrue(decay_result["ok"],decay_result)
        values=dict(decay_result["parameters"])
        self.assertAlmostEqual(3,float(values["A"]),places=5)
        self.assertAlmostEqual(0.4,float(values["k"]),places=5)
        self.assertAlmostEqual(0.6,float(values["C"]),places=5)

    def test_indefinite_integral_places_constant_after_expression(self):
        tree={"kind":"call","value":"integrate","args":[
            {"kind":"symbol","value":"x"},{"kind":"symbol","value":"x"}]}
        result=json.loads(core.dispatch(json.dumps({"tree":tree,"angle":"RAD"})))
        self.assertTrue(result["ok"],result)
        self.assertEqual(result["exact"],"x**2/2 + C")
        self.assertEqual(result["tree"]["args"][-1]["value"],"C")
        self.assertTrue(result["decimal"].endswith(" + C"))

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
    def test_factorint_displays_prime_powers_and_reuses_numeric_value(self):
        def factor(value):
            tree={"kind":"call","value":"factorint","args":[{"kind":"number","value":str(value)}]}
            return json.loads(core.dispatch(json.dumps({"tree":tree})))
        result=factor(360)
        self.assertTrue(result["ok"],result)
        self.assertEqual(result["exact"],"2**3*3**2*5")
        self.assertEqual(result["decimal"],"360")
        self.assertEqual(result["tree"]["kind"],"product")
        self.assertEqual([item["kind"] for item in result["tree"]["args"]],["power","power","number"])
        self.assertEqual(factor(1)["exact"],"1")
        reused=json.loads(core.dispatch(json.dumps({"tree":result["resultAst"]})))
        self.assertEqual(reused["exact"],"360")
    def test_round_decimal_places_and_rnd(self):
        def number(value): return {"kind":"number","value":str(value)}
        def call(name,*args):
            return json.loads(core.dispatch(json.dumps({"tree":{"kind":"call","value":name,"args":list(args)}})))
        self.assertEqual(call("round",number("3.1415"),number(2))["exact"],"3.14")
        self.assertEqual(call("round",number("1.235"),number(2))["exact"],"1.24")
        self.assertEqual(call("round",number("-1.225"),number(2))["exact"],"-1.22")
        self.assertEqual(call("round",number("1234"),number(-2))["exact"],"1200")
        self.assertEqual(call("round",number("2.5"))["exact"],"2")
        self.assertFalse(call("round",number(1),number("1.5"))["ok"])
        self.assertFalse(call("rnd",number(1))["ok"])
        samples=[call("rnd") for _ in range(5)]
        self.assertTrue(all(item["ok"] and 0<=float(item["exact"])<1 for item in samples),samples)
        self.assertGreater(len({item["exact"] for item in samples}),1)
    def test_roundh_half_up(self):
        def number(value): return {"kind":"number","value":str(value)}
        def call(name,*args):
            return json.loads(core.dispatch(json.dumps({"tree":{"kind":"call","value":name,"args":list(args)}})))
        for value,places,expected in [
            ("3.1415",2,"3.14"),("1.225",2,"1.23"),("-1.225",2,"-1.23"),
            ("2.5",0,"3"),("-2.5",0,"-3"),("125",-1,"130"),("-125",-1,"-130")
        ]:
            with self.subTest(value=value,places=places):
                result=call("roundh",number(value),number(places))
                self.assertTrue(result["ok"],result)
                self.assertEqual(result["exact"],expected)
        self.assertEqual(call("roundh",number("2.5"))["exact"],"3")
        self.assertEqual(call("round",number("2.5"))["exact"],"2")
        self.assertFalse(call("roundh",number(1),number("1.5"))["ok"])
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
    def test_internal_precision_and_display_digits_are_separate(self):
        third={"kind":"binary","value":"/","args":[{"kind":"number","value":"1"},{"kind":"number","value":"3"}]}
        wide=json.loads(core.dispatch(json.dumps({"tree":third,"precision":100,"displayDigits":10})))
        self.assertEqual(wide["exact"],"1/3")
        self.assertEqual(wide["decimal"],"0.3333333333")
        # Display digits never exceed internal precision.
        self.assertEqual(json.loads(core.dispatch(json.dumps({"tree":third,"precision":10,"displayDigits":40})))["decimal"],"0.3333333333")
        # The display cap must not shorten the value Ans and STO reuse.
        reused=json.loads(core.dispatch(json.dumps({"tree":{"kind":"binary","value":"*","args":[wide["resultAst"],{"kind":"number","value":"3"}]},"precision":100,"displayDigits":10})))
        self.assertEqual(reused["exact"],"1")
        # Floating-point results follow the same split.
        root=json.loads(core.dispatch(json.dumps({"tree":TREES["nsolve(cos(x)-x,x,0,1)"],"precision":50,"displayDigits":10})))
        self.assertEqual(root["exact"],"0.7390851332")
        self.assertEqual(root["tree"]["value"],"0.7390851332")
        self.assertGreaterEqual(len(json.loads(core.dispatch(json.dumps({"tree":TREES["nsolve(cos(x)-x,x,0,1)"],"precision":50})))["exact"]),30)
    def test_large_exact_integer_serialization(self):
        # Long exact results are abbreviated in the view but remain reusable at full precision.
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

    def test_large_exponents_use_result_size_and_preserve_symbols(self):
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

    def test_tiny_power_inside_numeric_sum_stays_compact(self):
        num=lambda value:{"kind":"number","value":str(value)}
        binary=lambda op,a,b:{"kind":"binary","value":op,"args":[a,b]}
        atan=lambda a:{"kind":"call","value":"atan","args":[a]}
        tiny=binary("^",num(10),{"kind":"unary","value":"-","args":[num(100000)]})
        angle=binary("-",binary("-",atan(binary("/",num(1),num(5))),atan(binary("/",num(1),num(239)))),binary("/",{"kind":"symbol","value":"pi"},num(4)))
        expression=binary("/",num(1),binary("+",angle,tiny))
        evaluate=lambda tree:json.loads(core.dispatch(json.dumps({"tree":tree,"angle":"RAD"})))
        result=evaluate(expression)
        self.assertTrue(result["ok"],result)
        self.assertIn("10**(-100000)",result["exact"])
        self.assertLess(len(json.dumps(result)),5000)
        self.assertAlmostEqual(float(result["decimal"]),-1.688656693123357,places=12)
        reused=evaluate(result["resultAst"])
        self.assertTrue(reused["ok"],reused)
        self.assertIn("10**(-100000)",reused["exact"])
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
    def test_cold_first_evaluation_stays_within_the_step_budget(self):
        tree={"kind":"number","value":"9"}
        for name in ["sin","cos","tan","atan","acos","asin"]: tree={"kind":"call","value":name,"args":[tree]}
        dependencies_path=str(pathlib.Path(core.s.__file__).resolve().parent.parent)
        script=("import sys,json\n"
                f"sys.path.insert(0,{dependencies_path!r})\n"
                f"sys.path.insert(0,{str(ROOT / 'app/src/main/python')!r})\n"
                "import calc_engine\n"
                f"print(calc_engine.dispatch({json.dumps(json.dumps({'tree':tree,'angle':'DEG'}))}))")
        result=subprocess.run([sys.executable,"-c",script],cwd=ROOT,capture_output=True,text=True,timeout=20)
        self.assertEqual(result.returncode,0,result.stderr)
        output=json.loads(result.stdout)
        self.assertTrue(output["ok"],output)
        self.assertAlmostEqual(float(output["decimal"]),9,places=9)
    def test_cold_fourier_transform_stays_within_the_step_budget(self):
        # Regression: fourier(exp(-t^2),t,w) spends about 7.5M traced steps on a cold first
        # evaluation; the former six-million step allowance cut it off after one second of work.
        tree={"kind":"call","value":"fourier","args":[
            {"kind":"call","value":"exp","args":[{"kind":"unary","value":"-","args":[
                {"kind":"binary","value":"^","args":[{"kind":"symbol","value":"t"},{"kind":"number","value":"2"}]}]}]},
            {"kind":"symbol","value":"t"},{"kind":"symbol","value":"w"}]}
        dependencies_path=str(pathlib.Path(core.s.__file__).resolve().parent.parent)
        script=("import sys,json\n"
                f"sys.path.insert(0,{dependencies_path!r})\n"
                f"sys.path.insert(0,{str(ROOT / 'app/src/main/python')!r})\n"
                "import calc_engine\n"
                f"print(calc_engine.dispatch({json.dumps(json.dumps({'tree':tree,'angle':'RAD','budget':8}))}))")
        result=subprocess.run([sys.executable,"-c",script],cwd=ROOT,capture_output=True,text=True,timeout=20)
        self.assertEqual(result.returncode,0,result.stderr)
        output=json.loads(result.stdout)
        self.assertTrue(output["ok"],output)
        self.assertEqual(output["exact"],"sqrt(pi)*exp(-pi**2*w**2)")
    def test_graph(self):
        result=run("sin(x)",action="graph",trees=[TREES["sin(x)"]],min=-3,max=3,samples=100)
        self.assertTrue(result["ok"],result)
        self.assertAlmostEqual(min(result["curves"][0],key=lambda point:abs(point[0]))[1],0)
        result=run("1/x",action="graph",trees=[TREES["1/x"]],min=-1,max=1,samples=100)
        self.assertTrue(result["ok"],result)
        # Adaptive sampling inserts points around the break, so look for the gap rather than a fixed index.
        curve=result["curves"][0]
        singular=curve.index(None)
        self.assertLess(curve[singular-1][0],0)
        self.assertGreater(curve[singular+1][0],0)
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
    def test_graph_parameters_shading_and_curve_analysis(self):
        def node(kind,value="",args=()): return {"kind":kind,"value":value,"args":list(args)}
        def num(value): return node("number",str(value))
        def sym(name): return node("symbol",name)
        def call(name,*args): return node("call",name,list(args))
        def binary(op,left,right): return node("binary",op,[left,right])
        def dispatch(**options): return json.loads(core.dispatch(json.dumps({"angle":"RAD",**options})))
        x,t=sym("x"),sym("t")
        # Free parameters become sliders and default to 1 before the first drag.
        scaled=binary("*",sym("a"),call("sin",x))
        curve=dispatch(action="graph",trees=[scaled],graphKind="cartesian",min=0,max=1,samples=100)
        self.assertTrue(curve["ok"],curve)
        self.assertEqual(curve["parameters"],["a"])
        self.assertAlmostEqual(max(point[1] for point in curve["curves"][0]),math.sin(1),4)
        doubled=dispatch(action="graph",trees=[scaled],graphKind="cartesian",min=0,max=1,samples=100,parameters={"a":2})
        self.assertTrue(doubled["ok"],doubled)
        self.assertAlmostEqual(max(point[1] for point in doubled["curves"][0]),2*math.sin(1),4)
        derived=dispatch(action="graph",trees=[scaled,call("diff",scaled,x)],graphKind="cartesian",
                         min=0,max=1,samples=100,parameters={"a":2},derivativeCurveIndex=1)
        self.assertTrue(derived["ok"],derived)
        self.assertEqual(derived["derivativeExpression"],"a*cos(x)")
        self.assertAlmostEqual(derived["curves"][1][0][1],2,6)
        # [shade] y < f(x) fills the half-plane down to the clamped viewport edge.
        below=dispatch(action="graph",trees=[x],graphKind="cartesian",min=-2,max=2,samples=200,yMin=-2,yMax=2,
                       shadings=[{"mode":"halfplane","side":"below","trees":[x]}])
        self.assertTrue(below["ok"],below)
        shading=below["shadings"][0]
        self.assertEqual(shading["mode"],"halfplane")
        self.assertAlmostEqual(shading["boundary"][0][0][0],-2,9)
        self.assertAlmostEqual(shading["boundary"][0][-1][1],2,9)
        self.assertAlmostEqual(shading["fill"][0][-1][1],-6,9)
        # [shade] f, g with an a..b interval samples only that interval.
        band=dispatch(action="graph",trees=[],graphKind="cartesian",min=-1,max=1,samples=200,yMin=-1,yMax=1,
                      shadings=[{"mode":"band","trees":[x,binary("^",x,num(2))],"a":num(0),"b":num(1)}])
        self.assertTrue(band["ok"],band)
        self.assertEqual(band["curves"],[])
        polygon=band["shadings"][0]["fill"][0]
        self.assertAlmostEqual(polygon[0][0],0,9)
        self.assertAlmostEqual(max(point[0] for point in polygon),1,9)
        def analyze(trees,action,a,b,**options):
            return dispatch(action="graphAnalysis",trees=trees,analysis=action,a=a,b=b,selected=0,other=1,**options)
        inflection=analyze([binary("^",x,num(3))],"inflection",-1,1)
        self.assertTrue(inflection["ok"],inflection)
        self.assertEqual(len(inflection["points"]),1)
        self.assertAlmostEqual(inflection["points"][0][0],0,6)
        arc=analyze([x],"arclength",0,2)
        self.assertTrue(arc["ok"],arc)
        self.assertAlmostEqual(arc["value"],2*math.sqrt(2),6)
        tangent=analyze([binary("^",x,num(2))],"tangent",1,1,xMin=-2,xMax=2,yMin=-2,yMax=2)
        self.assertTrue(tangent["ok"],tangent)
        self.assertAlmostEqual(tangent["value"],2,6)
        self.assertAlmostEqual(tangent["line"][0][1],-5,9)
        self.assertAlmostEqual(tangent["line"][1][1],3,9)
        circle=node("list","",[call("cos",t),call("sin",t)])
        plotted=dispatch(action="graph",trees=[circle],graphKind="parametric",variable="t",
                         min=0,max=math.pi/2,samples=100)
        self.assertTrue(plotted["ok"],plotted)
        self.assertEqual(len(plotted["curveParameters"][0]),len(plotted["curves"][0]))
        self.assertAlmostEqual(plotted["curveParameters"][0][-1],math.pi/2,9)
        self.assertAlmostEqual(plotted["curves"][0][-1][0],0,9)
        parametric=analyze([circle],"arclength",0,2*math.pi,graphKind="parametric",variable="t")
        self.assertTrue(parametric["ok"],parametric)
        self.assertAlmostEqual(parametric["value"],2*math.pi,6)
        slope=analyze([circle],"derivative",math.pi/2,math.pi/2,graphKind="parametric",variable="t")
        self.assertTrue(slope["ok"],slope)
        self.assertAlmostEqual(slope["value"],0,6)
        polar_plot=dispatch(action="graph",trees=[num(1)],graphKind="polar",variable="t",
                            min=0,max=math.pi/2,samples=100)
        self.assertTrue(polar_plot["ok"],polar_plot)
        self.assertEqual(len(polar_plot["curveParameters"][0]),len(polar_plot["curves"][0]))
        self.assertAlmostEqual(polar_plot["curveParameters"][0][-1],math.pi/2,9)
        polar=analyze([num(1)],"integral",0,2*math.pi,graphKind="polar",variable="t")
        self.assertTrue(polar["ok"],polar)
        self.assertAlmostEqual(polar["value"],math.pi,6)
        intersections=analyze([binary("^",x,num(2)),num(1)],"intersection",-2,3)
        self.assertEqual(len(intersections["points"]),2)
        self.assertAlmostEqual(intersections["points"][0][0],-1,6)
        self.assertAlmostEqual(intersections["points"][1][0],1,6)
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
        single=dispatch(call("dsolve",ode,fy,t,initial["args"][0]),budget=30)
        self.assertTrue(single["ok"],single)

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
        correlation=dispatch(call("correlation",node("list","",num(1),num(2),num(3)),node("list","",num(6),num(4),num(2))))
        self.assertTrue(correlation["ok"],correlation)
        self.assertEqual(correlation["exact"],"-1")


    def test_z_mellin_and_pde_tools(self):
        def node(kind,value="",*args): return {"kind":kind,"value":value,"args":list(args)}
        def sym(name): return node("symbol",name)
        def num(value): return node("number",str(value))
        def call(name,*args): return node("call",name,*args)
        def binary(op,left,right): return node("binary",op,left,right)
        def power(base,exponent): return binary("^",base,exponent)
        def neg(value): return node("unary","-",value)
        def dispatch(tree,**options): return json.loads(core.dispatch(json.dumps({"tree":tree,"angle":"RAD",**options})))
        n,z,svar,x,y=map(sym,("n","z","s","x","y"))

        geometric=dispatch(call("ztrans",power(sym("a"),n),n,z),budget=30)
        self.assertTrue(geometric["ok"],geometric)
        self.assertEqual(geometric["exact"],"z/(-a + z)")
        self.assertIn("Convergence",geometric["note"])
        ramp=dispatch(call("ztrans",n,n,z),budget=30)
        self.assertTrue(ramp["ok"],ramp)
        self.assertEqual(ramp["exact"],"z/(z - 1)**2")
        factorial=dispatch(call("ztrans",binary("/",num(1),call("factorial",n)),n,z),budget=30)
        self.assertTrue(factorial["ok"],factorial)
        self.assertEqual(factorial["exact"],"exp(1/z)")
        # A sequence with no closed form reports an error rather than an unevaluated sum.
        self.assertFalse(dispatch(call("ztrans",call("sin",binary("*",sym("b"),n)),n,z),budget=30)["ok"])

        inverse=dispatch(call("invztrans",binary("/",z,binary("-",z,num(2))),z,n),budget=30)
        self.assertTrue(inverse["ok"],inverse)
        self.assertEqual(inverse["exact"],"2**n")
        repeated=dispatch(call("invztrans",binary("/",z,power(binary("-",z,num(1)),num(2))),z,n),budget=30)
        self.assertTrue(repeated["ok"],repeated)
        self.assertEqual(repeated["exact"],"n")

        mellin=dispatch(call("mellin",call("exp",neg(x)),x,svar),budget=30)
        self.assertTrue(mellin["ok"],mellin)
        self.assertEqual(mellin["exact"],"gamma(s)")
        self.assertIn("(0, oo)",mellin["note"])
        inverse_mellin=dispatch(call("invmellin",call("gamma",svar),svar,x),budget=30)
        self.assertTrue(inverse_mellin["ok"],inverse_mellin)
        self.assertEqual(inverse_mellin["exact"],"exp(-x)")
        explicit=dispatch(call("invmellin",call("gamma",svar),svar,x,num(0),sym("oo")),budget=30)
        self.assertTrue(explicit["ok"],explicit)
        self.assertEqual(explicit["exact"],"exp(-x)")

        u=call("u",x,y)
        pde=node("relation","=",binary("+",call("diff",u,x),call("diff",u,y)),num(0))
        solved=dispatch(call("pdsolve",pde,u),budget=30)
        self.assertTrue(solved["ok"],solved)
        self.assertIn("u(x, y)",solved["exact"])
        higher=node("relation","=",binary("+",call("diff",u,x,x),call("diff",u,y,y)),num(0))
        self.assertFalse(dispatch(call("pdsolve",higher,u),budget=30)["ok"])
    def test_probability_distributions(self):
        def node(kind,value="",*args): return {"kind":kind,"value":value,"args":list(args)}
        def num(value): return node("number",str(value))
        def sym(name): return node("symbol",name)
        def call(name,*args): return node("call",name,*args)
        def neg(value): return node("unary","-",value)
        def dispatch(tree,**options): return json.loads(core.dispatch(json.dumps({"tree":tree,"angle":"RAD",**options})))
        self.assertEqual(dispatch(call("normcdf",num(0)))["exact"],"1/2")
        self.assertEqual(dispatch(call("normcdf",neg(sym("oo")),sym("oo")))["exact"],"1")
        self.assertEqual(dispatch(call("normpdf",num(0)))["exact"],"sqrt(2)/(2*sqrt(pi))")
        self.assertEqual(dispatch(call("normalcdf",num(0)))["exact"],"1/2")
        self.assertAlmostEqual(float(dispatch(call("normcdf",num("1.96")))["decimal"]),0.9750021048517795,12)
        self.assertAlmostEqual(float(dispatch(call("normcdf",neg(num("1.96")),num("1.96")))["decimal"]),0.950004209703559,12)
        self.assertAlmostEqual(float(dispatch(call("normcdf",neg(sym("oo")),num(1),num(0),num(1)))["decimal"]),0.8413447460685429,12)
        self.assertEqual(dispatch(call("invnorm",num("1/2")))["exact"],"0")
        self.assertAlmostEqual(float(dispatch(call("invnorm",num("0.975")))["decimal"]),1.959963984540054,9)
        self.assertEqual(dispatch(call("tpdf",num(0),num(10)))["exact"],"63*sqrt(10)/512")
        self.assertEqual(dispatch(call("tpdf",num(1),num(1)))["exact"],"1/(2*pi)")
        self.assertEqual(dispatch(call("tcdf",num(0),num(10)))["exact"],"0.5")
        self.assertAlmostEqual(float(dispatch(call("tcdf",num(1),num(1)))["decimal"]),0.75,12)
        self.assertAlmostEqual(float(dispatch(call("invt",num("0.975"),num(10)))["decimal"]),2.228138852,6)
        self.assertAlmostEqual(float(dispatch(call("invt",num("0.99"),num(1)))["decimal"]),math.tan(0.49*math.pi),6)
        self.assertEqual(dispatch(call("chi2pdf",num(2),num(2)))["exact"],"exp(-1)/2")
        self.assertEqual(dispatch(call("chi2cdf",num(0),num(5)))["exact"],"0")
        self.assertAlmostEqual(float(dispatch(call("chi2cdf",num(2),num(2)))["decimal"]),1-math.exp(-1),12)
        self.assertEqual(dispatch(call("fcdf",num(3),num(2),num(4)))["exact"],"0.84")
        self.assertEqual(dispatch(call("fpdf",num(1),num(2),num(4)))["exact"],"8/27")
        self.assertEqual(dispatch(call("binompdf",num(10),num("1/2"),num(5)))["exact"],"63/256")
        self.assertEqual(dispatch(call("binomcdf",num(10),num("1/2"),num(5)))["exact"],"319/512")
        self.assertEqual(dispatch(call("binomcdf",num(4),num("1/2")))["exact"],"[1/16, 5/16, 11/16, 15/16, 1]")
        self.assertEqual(dispatch(call("poissonpdf",num(2),num(3)))["exact"],"4*exp(-2)/3")
        self.assertEqual(dispatch(call("poissoncdf",num(2),num(3)))["exact"],"19*exp(-2)/3")
        self.assertEqual(dispatch(call("geometpdf",num("1/2"),num(3)))["exact"],"1/8")
        self.assertEqual(dispatch(call("geometcdf",num("1/2"),num(3)))["exact"],"7/8")

    def test_statistical_tests_and_intervals(self):
        def node(kind,value="",*args): return {"kind":kind,"value":value,"args":list(args)}
        def num(value): return node("number",str(value))
        def sym(name): return node("symbol",name)
        def call(name,*args): return node("call",name,*args)
        def listing(*values): return node("list","",*values)
        def dispatch(tree,**options): return json.loads(core.dispatch(json.dumps({"tree":tree,"angle":"RAD",**options})))
        def payload(result):
            self.assertTrue(result["ok"],result)
            return dict(line.split(": ",1) for line in result["exact"].splitlines())
        data=listing(num(1),num(2),num(3),num(4))
        sample=payload(dispatch(call("ttest",num(0),data)))
        self.assertAlmostEqual(float(sample["t"]),math.sqrt(15),9)
        self.assertEqual(sample["df"],"3")
        self.assertEqual(sample["sample mean"],"5/2")
        self.assertEqual(sample["sample SD"],"sqrt(15)/3")
        self.assertAlmostEqual(float(sample["p value"]),0.0304662916621710,9)
        right=payload(dispatch(call("ttest",num(0),data,sym("right"))))
        self.assertAlmostEqual(float(right["p value"]),0.0152331458310855,9)
        left=dispatch(call("ttest",num(0),data,sym("left")))
        self.assertIn("left",left["note"])
        self.assertAlmostEqual(float(payload(left)["p value"]),0.9847668541689145,9)
        summary=payload(dispatch(call("ttest",num(0),num("2.5"),num("1.291"),num(4))))
        self.assertAlmostEqual(float(summary["t"]),3.8729666924864446,9)
        z=payload(dispatch(call("ztest",num(0),num(2),num(1),num(4))))
        self.assertAlmostEqual(float(z["z"]),1,12)
        self.assertAlmostEqual(float(z["p value"]),math.erfc(1/math.sqrt(2)),12)
        chi=payload(dispatch(call("chi2test",listing(num(10),num(20),num(30)),listing(num(15),num(20),num(25)))))
        self.assertEqual(chi["chi-square"],"8/3")
        self.assertEqual(chi["df"],"2")
        self.assertAlmostEqual(float(chi["p value"]),math.exp(-4/3),12)
        independent=payload(dispatch(call("ttest2",num(0),listing(num(1),num(2),num(4)),listing(num(2),num(3),num(7)))))
        self.assertEqual(independent["mean difference"],"-5/3")
        self.assertEqual(independent["n x"],"3")
        self.assertTrue(0 < float(independent["p value"]) < 1)
        paired=payload(dispatch(call("ttestpaired",num(0),listing(num(1),num(3),num(5)),listing(num(0),num(1),num(2)))))
        self.assertEqual(paired["mean difference"],"2")
        self.assertEqual(paired["df"],"2")
        self.assertAlmostEqual(float(paired["t"]),2*math.sqrt(3),10)
        two_z=payload(dispatch(call("ztest2",num(0),num(2),num(3),listing(num(1),num(2)),listing(num(0),num(1)))))
        self.assertAlmostEqual(float(two_z["z"]),1/math.sqrt(13/2),10)
        categories=payload(dispatch(call("chi2independence",listing(num(0),num(1),num(1),num(0),num(0)),listing(num(1),num(0),num(0),num(0),num(1)))))
        self.assertEqual(categories["df"],"1")
        self.assertEqual(categories["chi-square"],"20/9")
        self.assertAlmostEqual(float(categories["p value"]),math.erfc(math.sqrt(10/9)),10)
        weights=listing(*(num(value) for value in [148,154,158,160,161,162,166,170,182,195,236]))
        normality=payload(dispatch(call("shapiro",weights)))
        self.assertEqual(normality["n"],"11")
        self.assertAlmostEqual(float(normality["W"]),0.7888146948353875,12)
        self.assertAlmostEqual(float(normality["p value"]),0.006703814056502984,12)
        three=payload(dispatch(call("shapiro",listing(num(1),num(2),num(3)))))
        self.assertAlmostEqual(float(three["W"]),1,12)
        self.assertAlmostEqual(float(three["p value"]),1,12)
        self.assertFalse(dispatch(call("shapiro",listing(num(1),num(1),num(1))))["ok"])
        self.assertFalse(dispatch(call("shapiro",listing(num(1),num(2))))["ok"])
        fisher_x=listing(*(num(value) for value in [0]*8 + [1]*5))
        fisher_y=listing(*(num(value) for value in [0]*6 + [1]*2 + [0] + [1]*4))
        fisher=payload(dispatch(call("fisherexact",fisher_x,fisher_y)))
        self.assertEqual(fisher["observed"],"[[6, 2], [1, 4]]")
        self.assertEqual(fisher["odds ratio"],"12")
        self.assertEqual(fisher["p value"],"4/39")
        fisher_right=payload(dispatch(call("fisherexact",fisher_x,fisher_y,sym("right"))))
        self.assertEqual(fisher_right["p value"],"37/429")
        fisher_left=payload(dispatch(call("fisherexact",fisher_x,fisher_y,sym("left"))))
        self.assertEqual(fisher_left["p value"],"427/429")
        self.assertFalse(dispatch(call("fisherexact",listing(num(0),num(1),num(2)),listing(num(0),num(1),num(0))))["ok"])
        analysis=payload(dispatch(call("anova",listing(num(1),num(2),num(3)),listing(num(4),num(5),num(6)))))
        self.assertEqual(analysis["F"],"27/2")
        self.assertEqual(analysis["df numerator"],"1")
        self.assertEqual(analysis["df denominator"],"4")
        self.assertAlmostEqual(float(analysis["p value"]),0.021311641128756725,9)
        interval=payload(dispatch(call("tinterval",num("0.95"),data)))
        low,high=[float(part) for part in interval["confidence interval"].strip("[]").split(",")]
        margin=3.182446305284263*math.sqrt(5/3)/2
        self.assertAlmostEqual(low,2.5-margin,9)
        self.assertAlmostEqual(high,2.5+margin,9)
        zinterval=payload(dispatch(call("zinterval",num(95),num(2),num("2.5"),num(4))))
        low,high=[float(part) for part in zinterval["confidence interval"].strip("[]").split(",")]
        self.assertAlmostEqual(low,0.540036015459946,9)
        self.assertAlmostEqual(high,4.459963984540054,9)

    def test_finance_functions(self):
        def node(kind,value="",*args): return {"kind":kind,"value":value,"args":list(args)}
        def num(value): return node("number",str(value))
        def sym(name): return node("symbol",name)
        def call(name,*args): return node("call",name,*args)
        def listing(*values): return node("list","",*values)
        def dispatch(tree,**options): return json.loads(core.dispatch(json.dumps({"tree":tree,"angle":"RAD",**options})))
        def payload(result):
            self.assertTrue(result["ok"],result)
            return dict(line.split(": ",1) for line in result["exact"].splitlines())
        rate=node("binary","/",num("0.05"),num(12))
        monthly=(1+0.05/12)**360
        payment=dispatch(call("tvmpmt",num(360),rate,num(250000),num(0)))
        self.assertAlmostEqual(float(payment["decimal"]),-250000*(0.05/12)*monthly/(monthly-1),6)
        self.assertEqual(dispatch(call("tvmpmt",num(10),num(0),num(1000),num(0)))["exact"],"-100")
        present=dispatch(call("tvmpv",num(10),num("0.05"),num(100),num(0)))
        self.assertAlmostEqual(float(present["decimal"]),-100*(1-1.05**-10)/0.05,9)
        periods=dispatch(call("tvmn",num("0.05"),num(0),num(100),num(-1000)))
        self.assertAlmostEqual(float(periods["decimal"]),math.log(1.5)/math.log(1.05),9)
        growth=(1+0.05/12)**12
        future=dispatch(call("tvmfv",num(12),rate,num(-1000),num(-100)))
        self.assertAlmostEqual(float(future["decimal"]),1000*growth+100*(growth-1)/(0.05/12),6)
        begin=dispatch(call("tvmfv",num(12),rate,num(-1000),num(-100),sym("begin")))
        self.assertAlmostEqual(float(begin["decimal"]),1000*growth+100*(1+0.05/12)*(growth-1)/(0.05/12),6)
        rate_value=dispatch(call("tvmrate",num(10),num(1000),num(-150),num(0)))
        self.assertAlmostEqual(float(rate_value["decimal"]),0.081441656464365663,12)
        flows=listing(num(-1000),num(300),num(400),num(500))
        net=dispatch(call("npv",num("0.1"),num(-1000),listing(num(300),num(400),num(500))))
        self.assertEqual(net["exact"],"-28000/1331")
        self.assertEqual(dispatch(call("npv",num(0),num(-1000),listing(num(300),num(400),num(500))))["exact"],"200")
        internal=dispatch(call("irr",flows))
        self.assertAlmostEqual(float(internal["decimal"]),0.08896339469334994,12)
        residual=dispatch(call("npv",internal["resultAst"],flows))
        self.assertAlmostEqual(float(residual["decimal"]),0,12)
        schedule=payload(dispatch(call("amort",rate,num(250000),num(360))))
        self.assertAlmostEqual(float(schedule["payment"]),-250000*(0.05/12)*monthly/(monthly-1),6)
        self.assertEqual(schedule["balance"],"0")
        self.assertEqual(schedule["principal paid"],"250000")
        self.assertAlmostEqual(float(schedule["interest paid"]),-float(schedule["payment"])*360-250000,6)
        start=payload(dispatch(call("amort",rate,num(250000),num(360),num(0))))
        self.assertEqual(start["balance"],"250000")
        self.assertEqual(start["interest paid"],"0")
        growth=dispatch(call("cagr",num(1000),num(1331),num(3)))
        self.assertEqual(growth["exact"],"1/10")
        annual=dispatch(call("cagr",num(1000),num(2000),num(5)))
        self.assertAlmostEqual(float(annual["decimal"]),2**0.2-1,9)

    def test_distribution_and_finance_errors(self):
        def node(kind,value="",*args): return {"kind":kind,"value":value,"args":list(args)}
        def num(value): return node("number",str(value))
        def call(name,*args): return node("call",name,*args)
        def listing(*values): return node("list","",*values)
        def dispatch(tree,**options): return json.loads(core.dispatch(json.dumps({"tree":tree,"angle":"RAD",**options})))
        for tree in [call("normcdf",num(1),num(2),num(3)),call("invnorm",num(0)),call("ttest",num(0),listing(num(1))),
                     call("irr",listing(num(1),num(2),num(3))),call("npv",num(-1),num(-1000),listing(num(300))),
                     call("tvmpmt",num(0),num("0.05"),num(100),num(0)),call("anova",listing(num(1),num(2))),
                     call("amort",num("0.005"),num(200000),num(0)),call("binompdf",num(1000),num("1/2")),
                     call("cagr",num(0),num(100),num(5)),call("cagr",num(1000),num(2000),num(0))]:
            with self.subTest(tree=tree): self.assertFalse(dispatch(tree)["ok"])
        self.assertEqual(dispatch(call("tvmpmt",num(0),num("0.05"),num(100),num(0)))["error"],"The number of periods must be positive")
        self.assertEqual(dispatch(call("cagr",num(0),num(100),num(5)))["error"],"The starting value must be positive")

    def test_extended_number_theory_special_functions_and_distributions(self):
        def node(kind,value="",*args): return {"kind":kind,"value":value,"args":list(args)}
        def num(value): return node("number",str(value))
        def sym(name): return node("symbol",name)
        def call(name,*args): return node("call",name,*args)
        def listing(*values): return node("list","",*values)
        def rows(pairs): return node("list","",*(node("list","",num(a),num(b)) for a,b in pairs))
        def dispatch(tree,**options): return json.loads(core.dispatch(json.dumps({"tree":tree,"angle":"RAD",**options})))
        def exact(name,*args):
            result=dispatch(call(name,*args))
            self.assertTrue(result["ok"],result)
            return result["exact"]
        # Number theory, combinatorics and special functions now evaluate instead of staying symbolic.
        self.assertEqual(exact("fibonacci",num(10)),"55")
        self.assertEqual(exact("lucas",num(10)),"123")
        self.assertEqual(exact("bernoulli",num(4)),"-1/30")
        self.assertEqual(exact("harmonic",num(5)),"137/60")
        self.assertEqual(exact("subfactorial",num(5)),"44")
        self.assertEqual(exact("totient",num(10)),"4")
        self.assertEqual(exact("divisor_sigma",num(12)),"28")
        self.assertEqual(exact("primepi",num(100)),"25")
        self.assertEqual(exact("nextprime",num(100)),"101")
        self.assertEqual(exact("prevprime",num(100)),"97")
        self.assertEqual(exact("beta",num(2),num(3)),"1/12")
        self.assertEqual(exact("digamma",num(1)),"-EulerGamma")
        self.assertEqual(exact("polygamma",num(1),num(1)),"pi**2/6")
        self.assertEqual(exact("lambertw",num(0)),"0")
        for name in ["besselj","bessely","besseli","besselk"]:
            with self.subTest(name=name): self.assertTrue(dispatch(call(name,num(0),num(1)))["ok"])
        # Polynomial roots and recurrence relations.
        square=node("binary","-",node("binary","^",sym("x"),num(2)),num(1))
        roots=dispatch(call("roots",square,sym("x")))
        self.assertTrue(roots["ok"],roots)
        self.assertIn("-1",roots["exact"])
        cube=node("binary","-",node("binary","^",sym("x"),num(3)),num(1))
        self.assertEqual(dispatch(call("real_roots",cube,sym("x")))["exact"],"[1]")
        sequence=node("call","y",sym("n"))
        shifted=node("call","y",node("binary","-",sym("n"),num(1)))
        recurrence=node("relation","=",sequence,node("binary","*",num(2),shifted))
        self.assertIn("2**n",dispatch(call("rsolve",recurrence,sequence))["exact"])
        condition=node("list","",node("relation","=",node("call","y",num(0)),num(0)))
        self.assertEqual(dispatch(call("rsolve",node("relation","=",sequence,node("binary","+",shifted,num(1))),sequence,condition))["exact"],"n")
        # Matrix additions.
        matrix=node("list","",node("list","",num(1),num(2)),node("list","",num(3),num(4)))
        pseudo=dispatch(call("pinv",matrix))
        self.assertTrue(pseudo["ok"],pseudo)
        self.assertIn("3/2",pseudo["exact"])
        self.assertTrue(dispatch(call("ctranspose",matrix))["ok"])
        diagonal=node("list","",node("list","",num(1),num(0)),node("list","",num(0),num(2)))
        self.assertTrue(dispatch(call("svd",diagonal))["ok"])
        # Distributions added alongside the existing normal/t/chi-square/F set.
        self.assertEqual(exact("exppdf",num(1)),"exp(-1)")
        self.assertEqual(exact("expcdf",num(1)),"1 - exp(-1)")
        self.assertEqual(exact("unifpdf",num(2)),"0")
        self.assertEqual(exact("unifcdf",num("0.5")),"1/2")
        self.assertEqual(exact("gammapdf",num(1),num(1)),"exp(-1)")
        self.assertAlmostEqual(float(dispatch(call("gammacdf",num(1),num(1)))["decimal"]),1-math.exp(-1),12)
        self.assertEqual(exact("betapdf",num("0.5"),num(2),num(3)),"3/2")
        self.assertEqual(exact("betacdf",num("0.5"),num(2),num(3)),"0.6875")
        self.assertEqual(exact("lognormcdf",num(1)),"1/2")
        # Angle and percent helpers are now reachable from the catalog.
        self.assertEqual(exact("percent",num(50)),"1/2")
        self.assertEqual(exact("degree",num(30)),"pi/6")
        self.assertEqual(exact("gradian",num(100)),"pi/2")
        # Two-sample tests and the previously undocumented normality/independence tests evaluate.
        self.assertTrue(dispatch(call("ttest2",num(0),listing(num(1),num(2),num(3)),listing(num(2),num(4),num(5))))["ok"])
        self.assertTrue(dispatch(call("ttestpaired",num(0),listing(num(1),num(2),num(3)),listing(num(2),num(3),num(5))))["ok"])
        self.assertTrue(dispatch(call("ztest2",num(0),num(1),num(1),listing(num(1),num(2),num(3)),listing(num(2),num(4),num(5))))["ok"])
        self.assertTrue(dispatch(call("chi2independence",listing(num(1),num(1),num(2),num(2)),listing(num(1),num(2),num(1),num(2))))["ok"])
        self.assertTrue(dispatch(call("fisherexact",listing(num(1),num(1),num(1),num(1),num(1),num(1),num(2),num(2)),listing(num(1),num(1),num(1),num(2),num(2),num(2),num(1),num(2))))["ok"])
        self.assertTrue(dispatch(call("shapiro",listing(num(1),num(2),num(3),num(4),num(5))))["ok"])
        self.assertEqual(exact("regression",rows([(1,1),(2,4),(3,9)]),sym("quadratic")),"x**2")

    if __name__=="__main__": unittest.main(verbosity=2)
