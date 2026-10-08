import json
import pathlib
import sys
import unittest

sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]/"app/src/main/python"))
import sympy as s
from calc_engine import Engine,dispatch
from calc_display import result_ast,display_tree
from calc_integrals import log_arctan_primitive


def call(name,*args): return {"kind":"call","value":name,"args":list(args)}
def symbol(name): return {"kind":"symbol","value":name}
def run(tree,**options): return json.loads(dispatch(json.dumps({"tree":tree,"angle":"RAD",**options})))


class CalculusExplanationTests(unittest.TestCase):
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

    def test_limit_rules_and_direction(self):
        x=s.Symbol("x")
        for expression,point,title in [(x*x+1,2,"Direct substitution"),(s.sin(x)/x,0,"L'Hôpital's rule for 0/0"),((2*x*x+1)/(x*x+3),s.oo,"Compare highest powers")]:
            result,report=self.report("limit",expression,point)
            self.assertIn(title,[step["title"] for step in report["steps"]])
            self.assertEqual(s.limit(expression,x,point,dir="+-"),Engine({}).build(result["resultAst"]))
        tree=call("limit",result_ast(1/x),symbol("x"),result_ast(s.Integer(0)),symbol("left"))
        self.assertEqual("-oo",run(tree,solutionSteps=True)["exact"])
        _,removable=self.report("limit",(x*x-1)/(x-1),1)
        self.assertIn("Evaluate the continuous extension",[step["title"] for step in removable["steps"]])
        _,repeated=self.report("limit",3*x*x/s.sin(x)**2,0)
        self.assertEqual(2,sum(step["title"]=="L'Hôpital's rule for 0/0" for step in repeated["steps"]))

    def test_nested_calculus_calls_keep_individual_results_and_overall_result(self):
        x=s.Symbol("x")
        tree=call("diff",call("integrate",result_ast(x*x),symbol("x")),symbol("x"))
        result=run(tree,solutionSteps=True)
        self.assertTrue(result["ok"],result)
        titles=[step["title"] for step in result["solutionSteps"]["steps"]]
        self.assertIn("Integrate the expression",titles)
        self.assertIn("Differentiate the expression",titles)
        self.assertEqual("x**2",result["exact"])
        self.assertEqual(result["exact"],result["solutionSteps"]["steps"][-1]["exact"])
        equation=call("solve",{"kind":"relation","value":"=","args":[call("diff",result_ast(x*x),symbol("x")),result_ast(s.Integer(2))]},symbol("x"))
        combined=run(equation,solutionSteps=True,equationSteps=True)
        self.assertTrue(combined["ok"],combined)
        self.assertIn("Differentiate the expression",[step["title"] for step in combined["solutionSteps"]["steps"]])

    def test_substitution_does_not_shadow_the_original_variable(self):
        u=s.Symbol("u")
        result=run(call("integrate",result_ast(2*u*s.sin(u*u)),symbol("u")),solutionSteps=True)
        self.assertTrue(result["ok"],result)
        step=next(step for step in result["solutionSteps"]["steps"] if step["title"]=="Substitution rule")
        self.assertEqual("u1",step["equations"][0]["tree"]["args"][0]["value"])

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
        self.assertEqual("{0}",guidance["knownRoots"]["exact"])
        commands=[item["command"] for item in guidance["suggestions"]]
        self.assertIn("nsolve(-x/2 + sin(x),x,1,2)",commands)
        self.assertIn("nsolve(-x/2 + sin(x),x,-2,-1)",commands)
        self.assertIn("partial",guidance["detail"])
        self.assertNotIn("ConditionSet",str(result["equationSteps"]["steps"][-1]["tree"]))

    def test_calculus_display_trees_keep_integrals_and_derivatives_structured(self):
        x=s.Symbol("x")
        for expression,name in [(s.Integral(x,x),"integrate"),(s.Derivative(s.sin(x),x,evaluate=False),"diff"),(s.Limit(s.sin(x)/x,x,0),"limit")]:
            tree=display_tree(expression)
            self.assertEqual("call",tree["kind"])
            self.assertEqual(name,tree["value"])
            self.assertGreaterEqual(len(tree["args"]),2)


if __name__=="__main__": unittest.main()
