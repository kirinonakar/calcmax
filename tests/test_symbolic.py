import json
import pathlib
import subprocess
import sys
import unittest

PYTHON=pathlib.Path(__file__).resolve().parents[1]/"app/src/main/python"
sys.path.insert(0, str(PYTHON))
import calc_engine

class PolynomialSolveTests(unittest.TestCase):
    def test_fresh_process_solves_all_roots_at_requested_precision(self):
        # A separate process per degree reproduces the reported cache dependence.
        for degree in (4,5,6):
            with self.subTest(degree=degree):
                program=f"""
import sys,json
sys.path.insert(0,{str(PYTHON)!r})
import calc_engine as c
x={{'kind':'symbol','value':'x'}}
def n(v):return {{'kind':'number','value':str(v)}}
def b(op,*args):return {{'kind':'binary','value':op,'args':list(args)}}
tree={{'kind':'call','value':'solve','args':[{{'kind':'relation','value':'=','args':[b('+',b('-',b('^',x,n({degree})),x),n(1)),n(0)]}},x]}}
for precision in (30,60):
    for attempt in range(2):
        result=json.loads(c.dispatch(json.dumps({{'tree':tree,'precision':precision,'budget':8}})))
        assert result['ok'],result
        assert result['tree']['kind']=='set' and len(result['tree']['args'])=={degree},result
        if {degree}>4:
            assert 'CRootOf' not in result['exact'] and 'CRootOf' not in json.dumps(result['tree']),result
            assert result['approximate'],result
            shown=c.s.sympify(result['exact'])
            assert len(shown)=={degree},result
            assert all(abs(c.s.N(root**{degree}-root+1,precision))<c.s.Rational(1,10)**(precision-5) for root in shown),result
        roots=c.s.sympify(result['decimal'])
        assert len(roots)=={degree} and len(set(roots))=={degree},result
        assert all(abs(c.s.N(root**{degree}-root+1,precision))<c.s.Rational(1,10)**(precision-5) for root in roots),result
        assert result['note']=='',result
"""
                completed=subprocess.run([sys.executable,"-c",program],capture_output=True,text=True,timeout=45)
                self.assertEqual(0,completed.returncode,completed.stdout+completed.stderr)


    def test_domain_assumptions_and_excluded_roots_are_preserved(self):
        sys.path.insert(0,str(PYTHON))
        import calc_engine as c
        # Use the public AST dispatch; stored x must not replace the solve variable.
        def node(kind,value,args=()):return {'kind':kind,'value':value,'args':list(args)}
        variable=node('symbol','x')
        expr=node('binary','+',[
            node('binary','-',[node('binary','^',[variable,node('number','5')]),variable]),node('number','1')])
        tree=node('call','solve',[expr,variable])
        for assumptions,count in [(['real'],1),(['positive'],0),(['integer'],0)]:
            result=json.loads(c.dispatch(json.dumps({'tree':tree,'assumptions':{'x':assumptions},'variables':{'x':node('number','99')}})))
            self.assertTrue(result['ok'],result)
            self.assertEqual(count,len(result['tree'].get('args',[])))
        rational=node('binary','/',[node('binary','-',[node('binary','^',[variable,node('number','2')]),node('number','1')]),node('binary','-',[variable,node('number','1')])])
        result=json.loads(c.dispatch(json.dumps({'tree':node('call','solve',[node('relation','=',[rational,node('number','2')]),variable])})))
        self.assertTrue(result['ok'],result)
        self.assertEqual('EmptySet',result['exact'])


if __name__=='__main__':unittest.main()


def node(kind, value="", *args):
    return {"kind": kind, "value": str(value), "args": list(args)}


def evaluate(tree, **options):
    return json.loads(calc_engine.dispatch(json.dumps({"tree": tree, **options})))


class AnswerFunctionTests(unittest.TestCase):
    def test_indefinite_integral_answers_bind_x_and_preserve_the_integration_constant(self):
        x = node("symbol", "x")
        for integrand, expected in [(x, "2 + C"), (node("number", 0), "C")]:
            result = evaluate(node("call", "integrate", integrand, x), variables={"x": node("number", 99)})
            self.assertTrue(result["ok"], result)
            self.assertEqual(["x"], result["resultAst"]["parameters"])
            applied = evaluate(node("call", "Ans", node("number", 2)), variables={"Ans": result["resultAst"], "C": node("number", 7)})
            self.assertEqual(expected, applied["exact"], applied)


    def test_derivative_answer_calls_ignore_stored_x_and_keep_exact_values(self):
        x = node("symbol", "x")
        body = node("binary", "^", x, node("number", 3))
        derivative = node("call", "diff", node("call", "f", x), x)
        result = evaluate(derivative, functions={"f": {"parameters": ["x"], "body": body}},
                          variables={"x": node("number", 99)})
        self.assertTrue(result["ok"], result)
        self.assertEqual(["x"], result["resultAst"]["parameters"])
        for argument, expected in [(1, "3"), (2, "12")]:
            applied = evaluate(node("call", "Ans", node("number", argument)),
                               variables={"Ans": result["resultAst"], "x": node("number", 99)})
            self.assertEqual(expected, applied["exact"], applied)
        symbolic = evaluate(node("call", "Ans", node("symbol", "t")), variables={"Ans": result["resultAst"]})
        self.assertEqual("3*t**2", symbolic["exact"])


    def test_constant_derivatives_keep_original_excluded_values(self):
        x = node("symbol", "x")
        result = evaluate(node("call", "diff", node("binary", "/", x, x), x))
        self.assertEqual("0", result["exact"])
        self.assertEqual("0", evaluate(node("call", "Ans", node("number", 2)), variables={"Ans": result["resultAst"]})["exact"])
        self.assertFalse(evaluate(node("call", "Ans", node("number", 0)), variables={"Ans": result["resultAst"]})["ok"])


    def test_missing_numeric_multivariate_and_wrong_arity_answers_are_rejected(self):
        for variables in [{}, {"Ans": node("number", 42)},
                          {"Ans": node("binary", "+", node("snapshot_symbol", "x"), node("snapshot_symbol", "y"))}]:
            self.assertFalse(evaluate(node("call", "Ans", node("number", 1)), variables=variables)["ok"])
        answer = evaluate(node("binary", "^", node("symbol", "x"), node("number", 2)))["resultAst"]
        for arguments in [[], [node("number", 1), node("number", 2)]]:
            self.assertFalse(evaluate(node("call", "Ans", *arguments), variables={"Ans": answer})["ok"])


class LogSolveTests(unittest.TestCase):
    def equation(self):
        x = node("symbol", "x")
        left = node("call", "log", node("binary", "-", x, node("number", 3)), node("number", 2))
        right = node("call", "log", node("binary", "-", node("binary", "*", node("number", 3), x), node("number", 5)), node("number", 4))
        return node("relation", "=", left, right)


    def test_log_roots_respect_assumptions_and_preserved_domain_guards(self):
        x = node("symbol", "x")
        result = evaluate(node("call", "solve", self.equation(), x), assumptions={"x": ["negative"]})
        self.assertEqual("EmptySet", result.get("exact"), result)
        excluded = node("restricted", "", self.equation(), node("relation", "!=", x, node("number", 7)))
        result = evaluate(node("call", "solve", excluded, x))
        self.assertEqual("EmptySet", result.get("exact"), result)

    def test_general_transcendental_log_equations_stay_unresolved(self):
        x = node("symbol", "x")
        equation = node("relation", "=", node("call", "ln", x), x)
        result = evaluate(node("call", "solve", equation, x))
        self.assertTrue(result["ok"], result)
        self.assertIn("ConditionSet", result["exact"])
        self.assertIn("Symbolic solution not found", result["note"])


if __name__ == "__main__":
    unittest.main()
