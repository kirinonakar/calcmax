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

    def test_linear_quadratic_degenerate_and_complex_roots(self):
        x = s.Symbol("x")
        for expression in [2*x+3, x*x-5*x+6, x*x+1, (x-2)**2, 0*x, s.Integer(3)]:
            result, report = self.report(s.Eq(expression, 0, evaluate=False), x)
            self.assertGreaterEqual(len(report["steps"]), 4)
            self.assertEqual(result["exact"], report["steps"][-1]["exact"])
            self.assertEqual("", report["note"])
        _, report = self.report(s.Eq(2*x*x+3*x+4, 0), x)
        discriminant = next(step for step in report["steps"] if step["title"] == "Compute the discriminant")
        self.assertEqual("Eq(D, -23)", discriminant["exact"])
        formula = next(step for step in report["steps"] if step["title"] == "Apply the quadratic formula")
        self.assertEqual(2, len(formula["tree"]["args"]))

    def test_cubic_factoring_and_cardano_substitution_preserve_equation(self):
        x = s.Symbol("x")
        _, factored = self.report(s.Eq(x**3-6*x*x+11*x-6, 0), x)
        self.assertIn("Factor the polynomial", [step["title"] for step in factored["steps"]])
        for var in (x, s.Symbol("t")):
            expression = 2*var**3+3*var**2+4*var+5
            _, report = self.report(s.Eq(expression, 0), var)
            substitution = next(step for step in report["steps"] if step["title"] == "Remove the quadratic term")
            depressed = next(step for step in report["steps"] if step["title"] == "Depressed cubic")
            change = s.sympify(substitution["exact"])
            reduced = s.sympify(depressed["exact"])
            self.assertEqual(0, s.expand(expression.subs(var, change.rhs)/2-reduced.lhs))
            self.assertNotIn(var, change.rhs.free_symbols)

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

    def test_higher_degree_factoring_and_power_substitution(self):
        x=s.Symbol("x")
        source=s.expand((x-1)*(x-2)*(x-3)*(x*x+1))
        _,report=self.report(s.Eq(source,0),x)
        self.assertEqual("",report["note"])
        factored=s.sympify(next(step["exact"] for step in report["steps"] if step["title"]=="Factor the polynomial"))
        self.assertEqual(0,s.expand(factored.lhs-source))
        for source in [x**4+x*x+1,x**6-5*x**3+6,x**8-2]:
            result,report=self.report(s.Eq(source,0),x)
            self.assertEqual("",report["note"])
            substitution=s.sympify(next(step["exact"] for step in report["steps"] if step["title"]=="Substitute a power of the variable"))
            reduced=s.sympify(next(step["exact"] for step in report["steps"] if step["title"]=="Solve the reduced polynomial"))
            self.assertEqual(0,s.expand(reduced.lhs.subs(substitution.lhs,substitution.rhs)-source))
            roots=Engine({}).build(result["resultAst"])
            for root in roots:
                self.assertEqual(0,s.simplify(source.subs(x,root)))
        # Generated symbols must not shadow an input variable or coefficient.
        t,k=s.symbols("t k")
        report=equation_steps(Engine({}),"solve",[t**4+k*t*t+1,t],s.S.EmptySet)
        change=s.sympify(next(step["exact"] for step in report["steps"] if step["title"]=="Substitute a power of the variable"))
        self.assertNotIn(change.lhs,{t,k})

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

    def test_beginner_substitution_example_preserves_the_visible_substitution(self):
        x, y = s.symbols("x y")
        result, report = self.report([s.Eq(x+y, 3*x), s.Eq(x-y, 1)], [x, y], solutionSteps=True)
        self.assertEqual("Substitution method", report["method"])
        rearrange = next(step for step in report["steps"] if step["title"] == "Rearrange the first equation")
        self.assertEqual(s.Eq(y, 2*x), s.sympify(rearrange["equations"][-1]["exact"]))
        self.assertEqual("Subtract x from both sides. Combine like terms. Use this expression in the other equation.", rearrange["explanation"])
        solved = next(step for step in report["steps"] if step["title"] == "Solve the equation with one unknown")
        self.assertEqual(["Eq(-x, 1)", "Eq(x, -1)"], [formula["exact"] for formula in solved["equations"]])
        self.assertEqual("Combine like terms. Multiply both sides by −1.", solved["explanation"])
        substitute = next(step for step in report["steps"] if step["title"] == "Substitute into the second equation")
        self.assertEqual("sum", substitute["equations"][0]["tree"]["args"][0]["kind"])
        remaining = next(step for step in report["steps"] if step["title"] == "Calculate the remaining variable")
        self.assertEqual(s.Eq(y, -2), s.sympify(remaining["equations"][-1]["exact"]))
        product = remaining["equations"][0]["tree"]["args"][1]
        self.assertEqual("×", product["displayOperator"])
        self.assertEqual("parentheses", product["args"][1]["kind"])
        self.assertEqual("-1", product["args"][1]["args"][0]["value"])
        solution_tree = report["steps"][-1]["tree"]
        self.assertEqual("tuple", solution_tree["kind"])
        self.assertEqual(["=", "="], [item["value"] for item in solution_tree["args"]])
        self.assertEqual(solution_tree, result["solutionSteps"]["steps"][-1]["tree"])
        self.assertEqual([{x: -1, y: -2}], Engine({}).build(result["resultAst"]))
        self.assertTrue(all(step.get("explanation") for step in report["steps"] if step["title"] != "Original equation"))
        matrices = [s.sympify(formula["exact"]) for step in report["advancedSteps"] for formula in step["equations"] if formula["tree"]["kind"] == "matrix"]
        self.assertIn(s.Matrix([[1, -s.Rational(1, 2), 0], [0, -s.Rational(1, 2), 1]]), matrices)
        self.assertEqual(s.Matrix([[1, 0, -1], [0, 1, -2]]), matrices[-1])
        self.assertEqual("Read the solution from the matrix", report["advancedSteps"][-1]["title"])
        self.assertEqual(["Eq(x, -1)", "Eq(y, -2)"], [formula["exact"] for formula in report["advancedSteps"][-1]["equations"]])

    def test_rearrangement_describes_only_operations_that_are_used(self):
        x, y = s.symbols("x y")
        for first, expected in [
            (s.Eq(2*x+3*y, 6), "Subtract 2*x from both sides. Combine like terms. Divide both sides by 3."),
            (s.Eq(x-y, 1), "Subtract x from both sides. Combine like terms. Multiply both sides by −1."),
            (s.Eq(y, 2*x), "Use this expression in the other equation."),
            (s.Eq(x+2*y, y+3), "Subtract y from both sides. Subtract x from both sides. Combine like terms."),
            (s.Eq(-x+y, 3), "Add x to both sides. Combine like terms."),
        ]:
            with self.subTest(first=first):
                _, report = self.report([first, s.Eq(x-y, 2)], [x, y])
                self.assertTrue(report["steps"][1]["explanation"].startswith(expected))

    def test_matrix_conclusion_distinguishes_free_variables_and_conflicts(self):
        x, y = s.symbols("x y")
        _, free = self.report([s.Eq(x+y, 3), s.Eq(2*x+2*y, 6)], [x, y])
        conclusion = free["advancedSteps"][-1]
        self.assertIn("free", conclusion["explanation"])
        self.assertEqual(["Eq(x, 3 - y)"], [formula["exact"] for formula in conclusion["equations"]])
        _, conflict = self.report([s.Eq(x+y, 3), s.Eq(2*x+2*y, 7)], [x, y])
        self.assertEqual("The equations conflict", conflict["advancedSteps"][-1]["title"])
        self.assertEqual("Eq(0, 1)", conflict["advancedSteps"][-1]["equations"][0]["exact"])

    def test_three_variable_system_uses_equation_elimination_and_back_substitution(self):
        x, y, z = s.symbols("x y z")
        equations = [s.Eq(x+y+z, 6), s.Eq(2*x-y+z, 3), s.Eq(x+2*y-z, 2)]
        _, report = self.report(equations, [x, y, z])
        self.assertEqual("Elimination method", report["method"])
        known = {}
        for step in report["steps"]:
            if step["title"] == "Back-substitute into an earlier equation":
                solved = s.sympify(step["equations"][-1]["exact"])
                known[solved.lhs] = solved.rhs
        self.assertEqual({x: 1, y: 2, z: 3}, known)
        self.assertTrue(all(s.simplify(eq.lhs.subs(known)-eq.rhs) == 0 for eq in equations))

    def test_domain_restrictions_reject_extraneous_roots(self):
        x = s.Symbol("x")
        source = {"kind": "call", "value": "solve", "args": [
            {"kind": "relation", "value": "=", "args": [
                {"kind": "binary", "value": "/", "args": [result_ast(x*x-1), result_ast(x-1)]}, result_ast(s.Integer(2))]},
            {"kind": "symbol", "value": "x"}]}
        result = json.loads(dispatch(json.dumps({"tree": source, "equationSteps": True})))
        self.assertTrue(result["ok"], result)
        self.assertEqual("EmptySet", result["exact"])
        titles = [step["title"] for step in result["equationSteps"]["steps"]]
        self.assertIn("Exclude zero denominators", titles)
        self.assertIn("Check the original domain restrictions", titles)

    def test_numerical_trace_uses_actual_result_and_residual(self):
        x = s.Symbol("x")
        result, report = self.report(s.Eq(x*x, 2), x, "nsolve", extra=(s.Integer(1), s.Integer(2)))
        self.assertIn("Initial bracket", [step["title"] for step in report["steps"]])
        residual = next(step for step in report["steps"] if step["title"].startswith("Substitute the root"))
        self.assertLess(abs(float(residual["exact"])), 1e-25)
        self.assertIn("does not enumerate all roots", report["note"])
        self.assertEqual(result["exact"], report["steps"][-1]["exact"])

    def test_periodic_and_unsupported_equations_are_honest(self):
        x = s.Symbol("x")
        _, trig = self.report(s.Eq(s.sin(x), s.Rational(1, 2)), x)
        branches = next(step for step in trig["steps"] if step["title"].startswith("Include periodic"))
        self.assertEqual(2, len(branches["tree"]["args"]))
        self.assertEqual("", trig["note"])
        _, unresolved = self.report(s.Eq(s.sin(x), x), x)
        self.assertIn("Detailed transformations are unavailable", unresolved["note"])
        self.assertEqual("Known real roots (partial)", unresolved["steps"][-1]["title"])
        y = s.Symbol("y")
        _, nonlinear = self.report([s.Eq(x*x+y*y, 1), s.Eq(x, y)], [x, y])
        self.assertIn("Detailed transformations are unavailable", nonlinear["note"])
        a = s.Symbol("a")
        _, conditional = self.report([s.Eq(x+y, 1), s.Eq(x+y, a)], [x, y])
        self.assertIn("Detailed transformations are unavailable", conditional["note"])

    def test_linear_ode_integrating_factor_satisfies_product_rule(self):
        t = s.Symbol("t")
        y = s.Function("y")(t)
        source = s.Eq(s.diff(y, t)+2*y, t)
        report = equation_steps(Engine({}), "dsolve", [source, y, t], s.Eq(y, t/2-s.Rational(1, 4)+s.Symbol("C1")*s.exp(-2*t)))
        factor = next(step for step in report["steps"] if step["title"] == "Integrating factor")
        mu = s.sympify(factor["exact"]).rhs
        self.assertEqual(0, s.simplify(s.diff(mu*y, t)-mu*(s.diff(y, t)+2*y)))
        self.assertEqual("", report["note"])

    def test_linear_ode_nonpolynomial_factor_and_second_order_solutions(self):
        t=s.Symbol("t")
        y=s.Function("y")(t)
        for p in [1/t,s.sin(t)]:
            source=s.Eq(s.diff(y,t)+p*y,t)
            report=equation_steps(Engine({}),"dsolve",[source,y,t],source)
            self.assertEqual("",report["note"])
            mu=s.sympify(next(step["exact"] for step in report["steps"] if step["title"]=="Integrating factor")).rhs
            self.assertEqual(0,s.simplify(s.diff(mu*y,t)-mu*(s.diff(y,t)+p*y)))
        for b,c in [(-3,2),(-2,1),(0,1)]:
            expression=s.diff(y,t,2)+b*s.diff(y,t)+c*y
            report=equation_steps(Engine({}),"dsolve",[s.Eq(expression,0),y,t],s.Eq(y,0))
            self.assertEqual("",report["note"])
            candidate=s.sympify(next(step["exact"] for step in report["steps"] if step["title"]=="Build the homogeneous solution")).rhs
            self.assertEqual(0,s.simplify(expression.subs(y,candidate).doit()))
        unsupported=s.Eq(s.diff(y,t,2)+t*y,0)
        report=equation_steps(Engine({}),"dsolve",[unsupported,y,t],unsupported)
        self.assertIn("Detailed transformations are unavailable",report["note"])

    def test_large_powers_do_not_expand_just_to_explain_a_solution(self):
        x = s.Symbol("x")
        report = equation_steps(Engine({}), "solve", [(x+1)**1000, x], s.FiniteSet(-1))
        self.assertIn("too large", report["note"])
        self.assertEqual(3, len(report["steps"]))


if __name__ == "__main__":
    unittest.main()
