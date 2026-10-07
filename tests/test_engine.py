import json
import pathlib
import sys
import unittest
import random
import math
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
        categories=payload(dispatch(call("chi2independence",listing(num(0),num(1),num(1),num(0),num(0)),listing(num(1),num(0),num(0),num(0),num(1)),num(0))))
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


    if __name__=="__main__": unittest.main(verbosity=2)


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

    def test_graph_integrals_preserve_exact_zero_and_tiny_nonzero_values(self):
        with self.subTest(scenario='graph_zero_signed_integral_keeps_shading'):
            for tree in (polynomial, {"kind": "relation", "value": "=", "args": [symbol("y"), polynomial]}):
                for precision in (15, 50, 100):
                    result = self.dispatch(action="graphAnalysis", trees=[tree], analysis="integral",
                                           a=-2, b=0, precision=precision)
                    self.assertEqual(0, result["value"])
                    points = [point for polygon in result["integralFill"] for point in polygon]
                    self.assertTrue(any(py > 0 for _, py in points))
                    self.assertTrue(any(py < 0 for _, py in points))
        with self.subTest(scenario='small_nonzero_integrals_are_not_chopped'):
            variable = s.Symbol("x")
            epsilon = s.Rational(1, 10**80)
            expression = 3*variable**2-16*variable-20+epsilon
            result = numeric_integral(expression, variable, -2.0, 0.0, 100)
            self.assertGreater(result, 0)
            self.assertEqual(s.N(2*epsilon, 100), result)
            graph = self.dispatch(action="graphAnalysis", trees=[binary("+", polynomial, number("1e-80"))],
                                  analysis="integral", a=-2, b=0, precision=100)
            self.assertEqual(2e-80, graph["value"])


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
