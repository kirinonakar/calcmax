import json
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]/"app/src/main/python"))
import sympy as s
from calc_engine import Engine, dispatch
from calc_display import result_ast
from calc_equation_steps import equation_steps

class EquationStepTests(unittest.TestCase):
    def test_rational_inequality_sign_charts_match_solver_and_exclude_poles(self):
        x=s.Symbol('x')
        cases=[(s.Lt(x*x,4),s.Interval.open(-2,2)),
               (s.Le(x*x,4),s.Interval(-2,2)),
               (s.Le((x-1)/(x+2),0),s.Interval(-2,1,left_open=True)),
               (s.Ge((x-1)**2/(x+2),0),s.Interval.open(-2,s.oo)),
               (s.Lt(x*x+1,0),s.EmptySet), (s.Le((x-1)**2,0),s.FiniteSet(1))]
        for source,expected in cases:
            with self.subTest(source=source):
                result,report=self.report(source,x)
                self.assertEqual('',report['note'])
                self.assertEqual('Sign chart method',report['method'])
                selected=next(step for step in report['steps'] if step['title']=='Select intervals and check endpoints')
                self.assertEqual(expected,s.sympify(selected['equations'][0]['exact']))
                solver=s.sympify(result['exact'],locals={'x':x})
                if solver == s.false: solver=s.EmptySet
                self.assertEqual(expected,solver.as_set() if hasattr(solver,'as_set') else solver)
                self.assertEqual(result['tree'],report['steps'][-1]['tree'])

    def test_polynomial_elimination_checks_all_original_equations(self):
        x,y=s.symbols('x y')
        source=[s.Eq(x*x+2*y*y,9),s.Eq(x*y,2)]
        result,report=self.report(source,[x,y])
        self.assertEqual('',report['note'])
        self.assertEqual('Polynomial elimination method',report['method'])
        self.assertEqual(4,sum(step['title']=='Back-substitute each candidate root' for step in report['steps']))
        for check in (step for step in report['steps'] if step['title']=='Check candidates in the original equation'):
            self.assertEqual(2,len(check['equations']))
            for formula in check['equations']:
                relation=s.sympify(formula['exact'],evaluate=False)
                self.assertTrue(relation == s.true or s.simplify(relation.lhs-relation.rhs) == 0)
        self.assertEqual(4,len(result['resultAst']['args']))

    def test_separable_odes_retain_equilibria_and_general_odes_verify_solutions(self):
        t=s.Symbol('t'); y=s.Function('y')(t); c=s.Symbol('C1')
        source=s.Eq(s.diff(y,t),y*(1-y))
        answer=s.Eq(y,1/(1+c*s.exp(-t)))
        report=equation_steps(Engine({}),'dsolve',[source,y,t],answer)
        self.assertEqual('',report['note'])
        self.assertEqual('Separation of variables',report['method'])
        constants=next(step for step in report['steps'] if step['title']=='Check constant solutions before division')
        self.assertEqual({s.Eq(y,0),s.Eq(y,1)},set(s.sympify(constants['exact'])))
        title='Substitute the solution into the differential equation'
        self.assertIn(title,[step['title'] for step in report['steps']])
        source=s.Eq(s.diff(y,t,2)+t*y,0)
        report=equation_steps(Engine({}),'dsolve',[source,y,t],s.Eq(y,s.airyai(-t)))
        self.assertIn('Substitution verifies',report['note'])
        self.assertIn(title,[step['title'] for step in report['steps']])
        invalid=equation_steps(Engine({}),'dsolve',[source,y,t],s.Eq(y,t))
        self.assertNotIn(title,[step['title'] for step in invalid['steps']])

    def test_powering_a_root_does_not_accept_extraneous_candidates(self):
        x = s.Symbol("x")
        for index in (2, 3):
            result, report = self.report(s.Eq(s.root(x, index), -2, evaluate=False), x)
            self.assertEqual("EmptySet", result["exact"])
            check = next(step for step in report["steps"] if step["title"] == "Check candidates in the original equation")
            self.assertEqual("!=", check["tree"]["args"][0]["args"][1]["value"])
            powered = next(step for step in report["steps"] if step["title"] == "Raise both sides to the root index")
            self.assertEqual("parentheses", powered["tree"]["args"][1]["args"][0]["kind"])
        result, report = self.report(s.Eq(s.sqrt(x**2), 2, evaluate=False), x)
        self.assertEqual("{-2, 2}", result["exact"])
        check = next(step for step in report["steps"] if step["title"] == "Check candidates in the original equation")
        self.assertEqual(2, len(check["tree"]["args"]))

    def report(self, source, variables, method="solve", extra=(), **options):
        args = [result_ast(source), result_ast(variables), *[result_ast(item) for item in extra]]
        # Solver variables are ordinary parser symbols, not frozen answer snapshots.
        args[1] = ({"kind": "list", "args": [{"kind": "symbol", "value": str(var)} for var in variables]}
                   if isinstance(variables, list) else {"kind": "symbol", "value": str(variables)})
        tree = {"kind": "call", "value": method, "args": args}
        plain = json.loads(dispatch(json.dumps({"tree": tree, **options})))
        traced = json.loads(dispatch(json.dumps({"tree": tree, "equationSteps": True, **options})))
        self.assertTrue(traced["ok"], traced)
        self.assertNotIn("equationSteps", plain)
        self.assertEqual(plain, {key: value for key, value in traced.items() if key != "equationSteps"})
        return traced, traced["equationSteps"]

    def test_linear_system_elimination_matches_independent_rref(self):
        x, y = s.symbols("x y")
        for expressions in [[y-2, 2*x+3*y-8], [x+y-3, 2*x+2*y-6], [x+y-3, 2*x+2*y-7]]:
            _, report = self.report([s.Eq(item, 0) for item in expressions], [x, y])
            matrices = [formula for step in report["advancedSteps"] for formula in step["equations"] if formula["tree"]["kind"] == "matrix"]
            a, b = s.linear_eq_to_matrix(expressions, [x, y])
            self.assertEqual(a.row_join(b).rref()[0], s.sympify(matrices[-1]["exact"]))
            self.assertTrue(all(len(formula["tree"]["args"]) == 2 for formula in matrices))
            self.assertFalse(any(step.get("tree", {}).get("kind") == "matrix" for step in report["steps"]))
            self.assertEqual("", report["note"])

    def test_parameter_systems_require_certain_pivots_and_consistency(self):
        x,y,a,b=s.symbols("x y a b")
        for equations in [[s.Eq(x+y,a),s.Eq(x-y,b)],
                          [s.Eq(x+a*y,1),s.Eq(y,b)]]:
            result,report=self.report(equations,[x,y])
            self.assertEqual("",report["note"])
            matrix,rhs=s.linear_eq_to_matrix(equations,[x,y])
            final_matrix=[s.sympify(formula["exact"]) for step in report["advancedSteps"] for formula in step["equations"] if formula["tree"]["kind"]=="matrix"][-1]
            self.assertEqual(matrix.row_join(rhs).rref()[0],final_matrix)
            for solution in Engine({}).build(result["resultAst"]):
                self.assertTrue(all(s.simplify(equation.lhs.subs(solution,simultaneous=True)-equation.rhs.subs(solution,simultaneous=True))==0 for equation in equations))
        _,conditional=self.report([s.Eq(a*x+y,1),s.Eq(x+a*y,2)],[x,y])
        self.assertIn("Detailed transformations are unavailable",conditional["note"])

    def test_nonlinear_substitution_keeps_domains_and_handles_degenerate_reductions(self):
        x, y = s.symbols("x y")
        equations = [s.Eq(x+y, 0), s.Eq(x*x+y*y, -2)]
        result, report = self.report(equations, [x, y], assumptions={"x": ["real"], "y": ["real"]})
        self.assertEqual("[]", result["exact"])
        self.assertEqual("", report["note"])
        for constant, title in [(9, "The equations describe the same relation"), (8, "The equations conflict")]:
            _, report = self.report([s.Eq(x+y, 3), s.Eq((x+y)**2, constant)], [x, y])
            self.assertEqual("", report["note"])
            self.assertIn(title, [step["title"] for step in report["steps"]])
        a = s.Symbol("a")
        _, conditional = self.report([s.Eq(a*x+a*y, 3), s.Eq(x*x+y*y, 5)], [x, y])
        self.assertIn("Detailed transformations are unavailable", conditional["note"])

    def test_periodic_and_unsupported_equations_are_honest(self):
        x = s.Symbol("x")
        _, trig = self.report(s.Eq(s.sin(x), s.Rational(1, 2)), x)
        branches = next(step for step in trig["steps"] if step["title"].startswith("Include periodic"))
        self.assertEqual(2, len(branches["tree"]["args"]))
        self.assertEqual("", trig["note"])
        _, unresolved = self.report(s.Eq(s.sin(x), x), x)
        self.assertIn("Detailed transformations are unavailable", unresolved["note"])
        self.assertEqual("Numerical real roots (partial)", unresolved["steps"][-1]["title"])
        y = s.Symbol("y")
        _, nonlinear = self.report([s.Eq(x*x+2*y*y, 1), s.Eq(x*y, 1)], [x, y])
        self.assertIn("Detailed transformations are unavailable", nonlinear["note"])
        a = s.Symbol("a")
        _, conditional = self.report([s.Eq(x+y, 1), s.Eq(x+y, a)], [x, y])
        self.assertIn("Detailed transformations are unavailable", conditional["note"])

if __name__ == "__main__":
    unittest.main()
