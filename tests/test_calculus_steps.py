import json
import pathlib
import sys
import unittest

sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]/"app/src/main/python"))
import sympy as s
from calc_engine import Engine,dispatch
from calc_display import result_ast
from calc_integrals import log_arctan_primitive
from calc_calculus_steps import calculus_steps

def call(name,*args): return {"kind":"call","value":name,"args":list(args)}
def symbol(name): return {"kind":"symbol","value":name}
def run(tree,**options): return json.loads(dispatch(json.dumps({"tree":tree,"angle":"RAD",**options})))

class CalculusExplanationTests(unittest.TestCase):
    def test_missing_integral_rule_verifies_the_actual_primitive_only(self):
        x=s.Symbol('x'); expression=x**x*(1+s.log(x))
        report=calculus_steps(Engine({}),'integrate',[expression,x],x**x)
        title='Verify the antiderivative by differentiation'
        self.assertIn(title,[step['title'] for step in report['steps']])
        self.assertIn('checked by differentiation',report['note'])
        wrong=calculus_steps(Engine({}),'integrate',[expression,x],2*x**x)
        self.assertNotIn(title,[step['title'] for step in wrong['steps']])
        definite=calculus_steps(Engine({}),'integrate',[expression,x,s.Integer(1),s.Integer(2)],s.Integer(3))
        self.assertNotIn(title,[step['title'] for step in definite['steps']])

    def report(self,method,expression,*args):
        tree=call(method,result_ast(expression),symbol("x"),*[result_ast(s.sympify(arg)) for arg in args])
        plain=run(tree)
        traced=run(tree,solutionSteps=True)
        self.assertTrue(traced["ok"],traced)
        self.assertEqual(plain,{key:value for key,value in traced.items() if key!="solutionSteps"})
        self.assertGreater(len(traced["solutionSteps"]["steps"]),2)
        return traced,traced["solutionSteps"]

    def test_derivative_rules_preserve_results_and_show_natural_language(self):
        x=s.Symbol("x")
        for expression,title in [(x**3+2*x,"Sum rule"),(s.sin(x*x),"Function and chain rules"),(x*s.exp(x),"Product rule"),(x/(x*x+1),"Quotient rule")]:
            result,report=self.report("diff",expression)
            self.assertIn(title,[step["title"] for step in report["steps"]])
            self.assertEqual(s.diff(expression,x),Engine({}).build(result["resultAst"]))
            self.assertTrue(all(step.get("explanation") for step in report["steps"]))

    def test_integral_rules_and_definite_bounds(self):
        x=s.Symbol("x")
        for expression,title in [(x*x+2*x,"Integrate term by term"),(2*x*s.sin(x*x),"Substitution rule"),(x*s.exp(x),"Integration by parts"),(1/(1+x*x),"Standard integral rule")]:
            result,report=self.report("integrate",expression)
            self.assertIn(title,[step["title"] for step in report["steps"]])
            primitive=Engine({}).build(result["resultAst"])
            self.assertEqual(0,s.simplify(s.diff(primitive,x)-expression))
            self.assertIn("Add the integration constant",[step["title"] for step in report["steps"]])
        result,report=self.report("integrate",x*x,0,2)
        self.assertEqual("8/3",result["exact"])
        self.assertIn("Evaluate at the bounds",[step["title"] for step in report["steps"]])
        _,divergent=self.report("integrate",1/x**2,-1,1)
        self.assertNotIn("Evaluate at the bounds",[step["title"] for step in divergent["steps"]])

    def test_indeterminate_powers_and_jumps_never_claim_direct_substitution(self):
        x=s.Symbol("x")
        for expression,side,answer in [(x**x,"right","1"),(s.sin(x)**x,"right","1"),
                                       (s.ceiling(x),"left","0"),(s.floor(x),"right","0"),
                                       (s.ceiling(x),"right","1"),(s.floor(x),"left","-1")]:
            with self.subTest(expression=expression,side=side):
                result=run(call("limit",result_ast(expression),symbol("x"),result_ast(s.Integer(0)),symbol(side)),solutionSteps=True)
                self.assertTrue(result['ok'],result)
                self.assertEqual(answer,result['exact'])
                titles=[step['title'] for step in result['solutionSteps']['steps']]
                self.assertNotIn('Direct substitution',titles)
                self.assertNotIn('Evaluate the continuous extension',titles)

    def test_dilogarithm_primitive_is_verified_and_preserves_branch_guards_in_ans(self):
        x=s.Symbol("x")
        result,report=self.report("integrate",s.log(x)/(1+x*x))
        self.assertNotIn("Integral",result["exact"])
        self.assertIn("polylog",result["exact"])
        self.assertEqual("special_function",result["guidance"]["status"])
        self.assertIn("x > 0",result["conditions"])
        primitive=Engine({}).build(result["resultAst"])
        positive=s.Symbol("p",positive=True)
        check=s.expand_func(s.diff(primitive,x))-s.log(x)/(1+x*x)
        self.assertEqual(0,s.simplify(check.subs(x,positive).rewrite(s.log)))
        for point in [s.Rational(1,10),1,3,10]:
            self.assertLess(abs(complex(s.N(check.subs(x,point),45))),1e-35)
        self.assertIn("Dilogarithm identity",[step["title"] for step in report["steps"]])
        for value,valid in [(1,True),(-1,False),(0,False)]:
            applied=run(call("Ans",result_ast(s.Integer(value))),variables={"Ans":result["resultAst"]})
            self.assertEqual(valid,applied["ok"],applied)
        self.assertIsNone(log_arctan_primitive(s.log(x)/(1+x),x))
        negative=s.Symbol("n",negative=True)
        self.assertIsNone(log_arctan_primitive(s.log(negative)/(1+negative**2),negative))

    def test_unresolved_equation_guides_to_verified_brackets_without_claiming_completeness(self):
        x=s.Symbol("x")
        tree=call("solve",result_ast(s.Eq(s.sin(x),x/2)),symbol("x"))
        result=run(tree,equationSteps=True)
        self.assertTrue(result["ok"],result)
        self.assertIn("ConditionSet",result["exact"])
        guidance=result["guidance"]
        self.assertEqual("unresolved_equation",guidance["status"])
        roots=s.sympify(guidance["knownRoots"]["exact"])
        self.assertEqual(3,len(roots))
        self.assertIn(0,roots)
        self.assertTrue(guidance['knownRoots']['approximate'])
        self.assertEqual([-10,10],guidance['searchRange'])
        for root in roots: self.assertLess(abs(s.N(s.sin(root)-root/2)),1e-25)
        commands=[item["command"] for item in guidance["suggestions"]]
        self.assertIn("nsolve(-x/2 + sin(x),x,1,2)",commands)
        self.assertIn("nsolve(-x/2 + sin(x),x,-2,-1)",commands)
        self.assertIn("partial",guidance["detail"])
        self.assertNotIn("ConditionSet",str(result["equationSteps"]["steps"][-1]["tree"]))

if __name__=="__main__": unittest.main()
