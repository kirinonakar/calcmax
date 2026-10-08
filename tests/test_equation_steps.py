import json
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]/"app/src/main/python"))
import sympy as s
from calc_engine import Engine, dispatch
from calc_display import result_ast, display_tree
from calc_equation_steps import equation_steps


def visible_formula(tree):
    """Read the operators and parentheses shown by both display renderers.

    Unary sums intentionally receive no implicit parentheses here: that was
    the bug, and a semantic AST decoder would silently hide it.
    """
    kind, args = tree["kind"], tree.get("args", [])
    shown = [visible_formula(arg) for arg in args]
    if kind in ("number", "text", "symbol"): return tree["value"]
    if kind == "parentheses": return "("+"".join(shown)+")"
    if kind == "unary": return tree["value"]+shown[0]
    if kind == "sum":
        return "".join(("+" if index and args[index]["kind"] != "unary" else "")+item
                       for index, item in enumerate(shown))
    if kind == "product":
        return "*".join("("+item+")" if arg["kind"] == "sum" else item
                        for arg, item in zip(args, shown))
    if kind == "fraction": return "("+shown[0]+")/("+shown[1]+")"
    if kind == "power": return "("+shown[0]+")**("+shown[1]+")"
    if kind == "relation": return "Eq("+",".join(shown)+",evaluate=False)"
    raise AssertionError(f"Unsupported display node: {tree}")


class EquationStepTests(unittest.TestCase):
    def test_root_equations_show_power_isolation_and_original_equation_checks(self):
        x = s.Symbol("x")
        for index in (2, 3, 4, 5, 7):
            with self.subTest(index=index):
                source = s.Eq(s.root(x, index), 16, evaluate=False)
                result, report = self.report(source, x)
                titles = [step["title"] for step in report["steps"]]
                self.assertEqual("", report["note"])
                self.assertNotIn("Move all terms to the left", titles)
                powered = next(step for step in report["steps"] if step["title"] == "Raise both sides to the root index")
                self.assertEqual(index, int(powered["tree"]["args"][1]["args"][1]["value"]))
                self.assertEqual("parentheses", powered["tree"]["args"][0]["args"][0]["kind"])
                simplified = next(step for step in report["steps"] if step["title"] == "Simplify the powered equation")
                self.assertEqual(s.Eq(x, 16**index, evaluate=False), s.sympify(simplified["exact"]))
                self.assertEqual("{"+str(16**index)+"}", result["exact"])
                self.assertIn("Check candidates in the original equation", titles)
        _, shifted = self.report(s.Eq(2*s.root(3*x+1, 3)+4, 12), x)
        self.assertEqual("", shifted["note"])
        self.assertIn("Isolate the root", [step["title"] for step in shifted["steps"]])
        self.assertIn("Eq(x, 21)", [step.get("exact") for step in shifted["steps"]])
        _, system = self.report([s.Eq(s.root(x, 3), 16)], [x])
        self.assertIn("Raise both sides to the root index", [step["title"] for step in system["steps"]])

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

    def test_linear_quadratic_degenerate_and_complex_roots(self):
        x = s.Symbol("x")
        for expression in [2*x+3, x*x-5*x+6, x*x+1, (x-2)**2, 0*x, s.Integer(3)]:
            result, report = self.report(s.Eq(expression, 0, evaluate=False), x)
            self.assertGreaterEqual(len(report["steps"]), 3)
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

    def test_substitution_under_minus_preserves_visible_grouping(self):
        x, y = s.symbols("x y")
        for first in [s.Eq(x+y, 1), s.Eq(y-2*x, 1), s.Eq(y, -1)]:
            with self.subTest(first=first):
                result, report = self.report([first, s.Eq(x-y, 2)], [x, y], solutionSteps=True)
                substitute = next(step for step in report["steps"] if step["title"] == "Substitute into the second equation")
                formula = substitute["equations"][0]
                shown = visible_formula(formula["tree"])
                self.assertIn("-(", shown)
                visible = s.sympify(shown)
                exact = s.sympify(formula["exact"], locals={"Eq": lambda left, right: s.Eq(left, right, evaluate=False)})
                self.assertEqual(0, s.expand(visible.lhs-exact.lhs))
                self.assertEqual(0, s.expand(visible.rhs-exact.rhs))
                self.assertEqual(report, result["solutionSteps"])
                if first == s.Eq(x+y, 1):
                    self.assertEqual([{x: s.Rational(3, 2), y: -s.Rational(1, 2)}],
                                     Engine({}).build(result["resultAst"]))

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

    def test_elimination_display_preserves_signs_before_combining_terms(self):
        x, y, z = s.symbols("x y z")
        examples = [
            [s.Eq(y+z, 3), s.Eq(x+2*y-z, 4), s.Eq(2*x-y+z, 1)],
            [s.Eq(x-y+z, 0), s.Eq(x+y-z, 2), s.Eq(-x+2*y+z, 4)],
            [s.Eq(2*x-y+z, 1), s.Eq(x+y-z, 2), s.Eq(-x+2*y+z, 4)],
        ]
        for equations in examples:
            with self.subTest(equations=equations):
                _, report = self.report(equations, [x, y, z])
                eliminations = [step for step in report["steps"] if step["title"] == "Eliminate one variable"]
                self.assertTrue(eliminations)
                for step in eliminations:
                    reduced = s.sympify(step["equations"][-1]["exact"])
                    for formula in step["equations"]:
                        visible = s.sympify(visible_formula(formula["tree"]))
                        exact = s.sympify(formula["exact"])
                        self.assertEqual(0, s.expand(visible.lhs-exact.lhs))
                        self.assertEqual(0, s.expand(visible.rhs-exact.rhs))
                        self.assertEqual(0, s.expand(visible.lhs-visible.rhs-reduced.lhs+reduced.rhs))
        result, report = self.report(examples[0], [x, y, z])
        self.assertEqual([{x: 1, y: 2, z: 1}], Engine({}).build(result["resultAst"]))
        eliminations = [step for step in report["steps"] if step["title"] == "Eliminate one variable"]
        self.assertEqual(["Eq(-5*y + 3*z, -7)", "Eq(8*z, 8)"],
                         [step["equations"][-1]["exact"] for step in eliminations])
        for step in eliminations:
            self.assertEqual(3, len(step["equations"]))
            expanded = step["equations"][1]["tree"]["args"][0]
            self.assertEqual("sum", expanded["kind"])
            self.assertTrue(all(arg["kind"] != "sum" for arg in expanded["args"]))

    def test_unevaluated_sum_and_negative_sum_keep_visible_grouping(self):
        x, y, z = s.symbols("x y z")
        expressions = [
            s.Add(-2*x-4*y+2*z, 2*x-y+z, evaluate=False),
            s.Add(-5*y+3*z, 5*y+5*z, evaluate=False),
            s.Mul(-1, x-y+z, evaluate=False),
        ]
        for expression in expressions:
            with self.subTest(expression=expression):
                shown = visible_formula(display_tree(expression))
                self.assertIn("(", shown)
                self.assertEqual(0, s.expand(s.sympify(shown)-expression))

    def test_already_normalized_equations_skip_unchanged_transformations(self):
        x = s.Symbol("x")
        _, report = self.report(s.Eq(x*x-5*x+6, 0), x)
        titles = [step["title"] for step in report["steps"]]
        self.assertNotIn("Move all terms to the left", titles)
        self.assertNotIn("Expand and collect like terms", titles)
        formula = next(step for step in report["steps"] if step["title"] == "Apply the quadratic formula")
        # Keep both radicals unevaluated, including the minus branch.
        self.assertEqual(2, formula["exact"].count("sqrt(1)"))
        def count_roots(tree):
            return int(tree["kind"] == "root")+sum(count_roots(arg) for arg in tree.get("args", []))
        self.assertEqual(2, count_roots(formula["tree"]))
        simplified = report["steps"][titles.index("Apply the quadratic formula")+1]
        self.assertEqual("Simplify the candidate roots", simplified["title"])
        self.assertEqual([s.Eq(x, 3), s.Eq(x, 2)], s.sympify(simplified["exact"]))
        # A transformation with different sides or an unexpanded product stays.
        _, moved = self.report(s.Eq(x*x, 5*x-6), x)
        self.assertIn("Move all terms to the left", [step["title"] for step in moved["steps"]])
        source = s.Eq(s.Mul(x-2, x-3, evaluate=False), 0, evaluate=False)
        expanded = equation_steps(Engine({}), "solve", [source, x], s.FiniteSet(2, 3))
        self.assertIn("Expand and collect like terms", [step["title"] for step in expanded["steps"]])

    def test_single_equation_system_uses_the_general_derivation(self):
        x, y = s.symbols("x y")
        examples = [s.Eq(x*x-5*x+6, 0), s.Eq(2*x+3, 0),
                    s.Eq(s.sin(x), s.Rational(1, 2)), s.Eq(s.sin(x), 2),
                    s.Eq(x**4+x*x+1, 0), s.Eq(0, 0, evaluate=False)]
        for equation in examples:
            _, general = self.report(equation, x)
            for source, variables in [([equation], [x]), ([equation], x), (equation, [x])]:
                with self.subTest(equation=equation, variables=variables):
                    result, system = self.report(source, variables, solutionSteps=True)
                    self.assertEqual(general["steps"][:-1], system["steps"][:-1])
                    self.assertEqual(general["note"], system["note"])
                    self.assertEqual(result["exact"], system["steps"][-1]["exact"])
                    self.assertEqual(system, result["solutionSteps"])
        # System's default variable list may include an unused variable.
        equation = s.Eq(x*x-5*x+6, 0)
        _, general = self.report(equation, x)
        for source in [equation, [equation]]:
            result, system = self.report(source, [x, y])
            self.assertEqual(general["steps"][:-1], system["steps"][:-1])
            self.assertEqual([{x: 2}, {x: 3}], Engine({}).build(result["resultAst"]))
        _, multivariable = self.report([s.Eq(x*x+y*y, 5)], [x, y])
        self.assertIn("Detailed transformations are unavailable", multivariable["note"])

    def test_trig_steps_skip_constant_domain_checks_and_unchanged_branch_isolation(self):
        x = s.Symbol("x")
        _, report = self.report(s.Eq(s.sin(x), s.Rational(1, 2)), x)
        titles = [step["title"] for step in report["steps"]]
        self.assertNotIn("Exclude zero denominators", titles)
        self.assertNotIn("Multiply by the nonzero denominator", titles)
        self.assertNotIn("Expand and collect like terms", titles)
        self.assertNotIn("Isolate the variable in each branch", titles)
        self.assertIn("Include periodic branches (n is an integer)", titles)
        _, shifted = self.report(s.Eq(s.sin(2*x+1), s.Rational(1, 2)), x)
        self.assertIn("Isolate the variable in each branch", [step["title"] for step in shifted["steps"]])

    def test_linear_and_quadratic_systems_show_substitution_and_matching_pairs(self):
        x, y = s.symbols("x y")
        examples = [
            [s.Eq(x+y, 3), s.Eq(x*x+y*y, 5)],
            [s.Eq(x*x+y*y, 5), s.Eq(x+y, 3)],
            [s.Eq(x-y, 1), s.Eq(x*x+y*y, 5)],
            [s.Eq(x+y, 2), s.Eq(x*x+y*y, 2)],
            [s.Eq(x+y, 0), s.Eq(x*x+y*y, -2)],
            [s.Eq(x+y, 3), s.Eq(x*x-y*y, 3)],
        ]
        for equations in examples:
            with self.subTest(equations=equations):
                result, report = self.report(equations, [x, y], solutionSteps=True)
                self.assertEqual("", report["note"])
                self.assertEqual("Substitution method", report["method"])
                back = next(step for step in report["steps"] if step["title"] == "Back-substitute each candidate root")
                candidates = []
                for formula in back["equations"][1:]:
                    pair = {item.lhs: item.rhs for item in s.sympify(formula["exact"])}
                    self.assertTrue(all(s.simplify(eq.lhs.subs(pair)-eq.rhs) == 0 for eq in equations))
                    candidates.append(pair)
                actual = Engine({}).build(result["resultAst"])
                self.assertEqual({tuple(pair[var] for var in (x, y)) for pair in actual},
                                 {tuple(pair[var] for var in (x, y)) for pair in candidates})
                self.assertEqual(report, result["solutionSteps"])
        _, example = self.report(examples[0], [x, y])
        expanded = next(step for step in example["steps"] if step["title"] == "Expand and collect like terms")
        self.assertEqual(s.Eq(2*x*x-6*x+4, 0), s.sympify(expanded["equations"][0]["exact"]))

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

    def test_squared_sum_and_product_systems_keep_every_sign_combination(self):
        x, y = s.symbols("x y")
        examples = [
            [s.Eq(x*x+y*y, 5), s.Eq(x*y, 2)],
            [s.Eq(2*x*y, 4), s.Eq(3*x*x+3*y*y, 15)],
            [s.Eq(x*x+y*y+x*y, 7), s.Eq(x*y, 2)],
            [s.Eq(x*x+y*y, 2), s.Eq(x*y, 1)],
            [s.Eq(x*x+y*y, 5), s.Eq(x*y, 0)],
            [s.Eq(x*x+y*y, 0), s.Eq(x*y, 0)],
            [s.Eq(x*x+y*y, 1), s.Eq(x*y, 1)],
        ]
        for equations in examples:
            with self.subTest(equations=equations):
                result, report = self.report(equations, [x, y], solutionSteps=True)
                self.assertEqual("", report["note"])
                self.assertEqual("Sum and difference method", report["method"])
                identities = next(step for step in report["steps"] if step["title"] == "Form the squared sum and difference")
                for formula in identities["equations"]:
                    identity = s.sympify(formula["exact"])
                    self.assertEqual(0, s.expand(identity.lhs-identity.rhs))
                pairs = next(step for step in report["steps"] if step["title"] == "Combine the candidate solution pairs")
                candidates = []
                for formula in pairs["equations"]:
                    pair = {item.lhs: item.rhs for item in s.sympify(formula["exact"])}
                    self.assertTrue(all(s.simplify(eq.lhs.subs(pair)-eq.rhs) == 0 for eq in equations))
                    candidates.append(tuple(pair[var] for var in (x, y)))
                actual = Engine({}).build(result["resultAst"])
                self.assertEqual(len(actual), len(candidates))
                self.assertTrue(all(any(all(s.simplify(s.expand(pair[var]-candidate[index])) == 0
                                            for index, var in enumerate((x, y))) for candidate in candidates)
                                    for pair in actual))
                self.assertEqual(len(candidates), len(set(candidates)))
        result, _ = self.report(examples[0], [x, y])
        self.assertEqual({(-2, -1), (-1, -2), (1, 2), (2, 1)},
                         {tuple(pair[var] for var in (x, y)) for pair in Engine({}).build(result["resultAst"])})
        result, report = self.report(examples[-1], [x, y], assumptions={"x": ["real"], "y": ["real"]})
        self.assertEqual("[]", result["exact"])
        self.assertEqual("", report["note"])
        # Auxiliary names must not shadow the input variable names.
        u, v = s.symbols("u v")
        _, report = self.report([s.Eq(u*u+v*v, 5), s.Eq(u*v, 2)], [u, v])
        change = next(step for step in report["steps"] if step["title"] == "Introduce the sum and difference")
        self.assertTrue(all(s.sympify(formula["exact"]).lhs not in {u, v} for formula in change["equations"]))

    def test_exponential_product_steps_use_lambert_w_and_selected_branches(self):
        x = s.Symbol("x")
        for source in [s.Eq(x*s.exp(x), 1), s.Eq((2*x+3)*s.exp(4*x+1), 5)]:
            for extra in [(), (s.Symbol("real"),)]:
                with self.subTest(source=source, extra=extra):
                    result, report = self.report(source, x, extra=extra, solutionSteps=True)
                    self.assertNotIn("Detailed transformations are unavailable", report["note"])
                    self.assertNotIn("Partial solutions", report["note"])
                    self.assertIn("Use the Lambert W inverse", [step["title"] for step in report["steps"]])
                    final = Engine({}).call("solve", [source, x, *extra], [])
                    roots = [final.lamda(k) for k in range(-2, 3)] if isinstance(final, s.ImageSet) else list(final)
                    for root in roots:
                        self.assertLess(abs(s.N((source.lhs-source.rhs).subs(x, root), 40)), s.Rational(1, 10)**30)
        result, report = self.report(s.Eq(x*s.exp(x), 1), x, extra=(s.Symbol("real"),))
        self.assertEqual("{LambertW(1)}", result["exact"])
        self.assertAlmostEqual(0.5671432904097838, float(s.LambertW(1)), places=14)
        result, report = self.report(s.Eq(x*s.exp(x), -1), x, extra=(s.Symbol("real"),))
        self.assertEqual("EmptySet", result["exact"])
        self.assertIn("Check the real Lambert W domain", [step["title"] for step in report["steps"]])

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
        self.assertEqual("Numerical real roots (partial)", unresolved["steps"][-1]["title"])
        y = s.Symbol("y")
        _, nonlinear = self.report([s.Eq(x*x+2*y*y, 1), s.Eq(x*y, 1)], [x, y])
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
        self.assertEqual(2, len(report["steps"]))


if __name__ == "__main__":
    unittest.main()
