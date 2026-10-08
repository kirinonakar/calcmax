"""Public dispatch coverage for statistical conventions and CAS result guidance."""
import json
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "app/src/main/python"))
import calc_engine
import symvacas_catalog as catalog
import sympy as s


def node(kind, value="", *args):
    return {"kind": kind, "value": str(value), "args": list(args)}


def number(value):
    return node("number", value)


def data(*values):
    return node("list", "", *(number(v) for v in values))


def call(name, *args):
    return node("call", name, *args)


def evaluate(tree, **options):
    return json.loads(calc_engine.dispatch(json.dumps({"tree": tree, **options})))


class CalculatorConventionTests(unittest.TestCase):
    def test_sample_defaults_and_explicit_population_values(self):
        values = data(2, 4, 4, 4, 5, 5, 7, 9)
        for name, expected in [("variance", "4"), ("stdev", "2")]:
            self.assertEqual(expected, evaluate(call(name, values, number(0)))["exact"])
            self.assertEqual(evaluate(call(name, values, number(1)))["exact"], evaluate(call(name, values))["exact"])
        self.assertEqual("32/7", evaluate(call("variance", values))["exact"])
        self.assertEqual("32/7", evaluate(call("variance", values, number(1)))["exact"])
        self.assertEqual(s.sqrt(s.Rational(32, 7)), catalog.stdev([2,4,4,4,5,5,7,9], 1))
        self.assertEqual(1, catalog.variance([1,2,3]))
        self.assertEqual(s.Rational(2, 3), catalog.variance([1,2,3], 0))
        self.assertEqual(1, catalog.variance([1,2,3], 1))
        self.assertEqual(2, catalog.covariance([1,2,3], [2,4,6]))
        self.assertEqual(s.Rational(4, 3), catalog.covariance([1,2,3], [2,4,6], 0))
        self.assertEqual(2, catalog.covariance([1,2,3], [2,4,6], 1))
        report = catalog.stats([2,4,4,4,5,5,7,9])
        self.assertEqual(4, report["population variance"])
        self.assertEqual(s.Rational(32, 7), report["sample variance"])
        self.assertEqual("0", evaluate(call("stdev", data(7), number(0)))["exact"])

    def test_invalid_sample_inputs_are_rejected(self):
        for name, arguments in [("variance", [data(7)]), ("stdev", [data(7)]),
                                ("covariance", [data(7), data(9)])]:
            self.assertFalse(evaluate(call(name, *arguments))["ok"])
            for ddof in (1, -1, 2, "0.5"):
                result = evaluate(call(name, *arguments, number(ddof)))
                self.assertFalse(result["ok"], result)
        self.assertFalse(evaluate(call("covariance", data(1,2), data(1), number(1)))["ok"])

    def test_solve_domains_absolute_values_and_assumptions(self):
        self.assertEqual(s.FiniteSet(-2,4), catalog.solve(s.Eq(s.Abs(catalog.x-1),3),catalog.x,catalog.real))
        self.assertEqual(s.FiniteSet(-2,4), catalog.solve(s.Eq(s.Abs(catalog.x-1),3),catalog.x))
        x = node("symbol", "x")
        eq = node("relation", "=", call("abs", node("binary", "-", x, number(1))), number(3))
        result = evaluate(call("solve", eq, x))
        self.assertEqual("{-2, 4}", result.get("exact"), result)
        self.assertIn("real domain automatically", result["note"])
        explicit_complex = evaluate(call("solve", eq, x, node("symbol", "complex")))
        self.assertFalse(explicit_complex["ok"], explicit_complex)
        for domain in ("real", "integer"):
            result = evaluate(call("solve", eq, x, node("symbol", domain)), variables={"x":number(99)})
            self.assertEqual("{-2, 4}", result.get("exact"), result)
        self.assertEqual("{-2, 4}", evaluate(call("solve", eq, x), assumptions={"x":["real"]})["exact"])
        self.assertEqual("{4}", evaluate(call("solve", eq, x, node("symbol", "real")), assumptions={"x":["positive"]})["exact"])
        polynomial = node("binary", "+", node("binary", "^", x, number(2)), number(1))
        for args in [[], [node("symbol", "complex")]]:
            self.assertEqual("{-I, I}", evaluate(call("solve", polynomial, x, *args))["exact"])
        self.assertEqual("EmptySet", evaluate(call("solve", polynomial, x, node("symbol", "real")))["exact"])
        self.assertFalse(evaluate(call("solve", polynomial, x, node("symbol", "bogus")))["ok"])
        excluded = node("restricted", "", eq, node("relation", "!=", x, number(4)))
        self.assertEqual("{-2}", evaluate(call("solve", excluded, x, node("symbol", "real")))["exact"])
        self.assertEqual("{-2}", evaluate(call("solve", excluded, x))["exact"])

    def test_automatic_real_solving_preserves_domain_scope_and_complex_roots(self):
        x = node("symbol", "x")
        negative = node("relation", "=", call("abs", x), number(-1))
        empty = evaluate(call("solve", negative, x))
        self.assertEqual("EmptySet", empty.get("exact"), empty)
        self.assertIn("real domain automatically", empty["note"])
        interval = evaluate(call("solve", node("relation", "=", call("sign", x), number(1)), x))
        self.assertEqual("Interval.open(0, oo)", interval.get("exact"), interval)
        self.assertIn("real domain automatically", interval["note"])
        rational = node("relation", "=", call("abs", x), node("binary", "/", number(1), node("binary", "-", x, number(1))))
        roots = evaluate(call("solve", rational, x), variables={"x":number(99)})
        self.assertEqual("{1/2 + sqrt(5)/2}", roots.get("exact"), roots)
        engine = calc_engine.Engine({})
        engine.build(call("solve", node("relation", "=", call("abs", x), number(1)), x))
        self.assertIsNone(engine.symbol("x").is_real)
        polynomial = node("binary", "+", node("binary", "^", x, number(2)), number(1))
        self.assertEqual(s.FiniteSet(-s.I,s.I), engine.build(call("solve", polynomial, x)))

    def test_system_real_retry_preserves_independent_complex_unknowns(self):
        x,y=s.symbols("x y")
        engine=calc_engine.Engine({})
        result=engine.call("solve",[[s.Eq(s.Abs(x-1),3),s.Eq(y**2+1,0)],[x,y]],[])
        self.assertEqual({(-2,-s.I),(-2,s.I),(4,-s.I),(4,s.I)}, {(row[x],row[y]) for row in result})
        self.assertIn("for x",engine.note)
        self.assertIsNone(x.is_real)

    def test_unresolved_symbolic_results_explain_numeric_alternatives(self):
        x = node("symbol", "x")
        equation = node("relation", "=", call("sin", x), x)
        result = evaluate(call("solve", equation, x))
        self.assertTrue(result["ok"], result)
        self.assertIn("ConditionSet", result["exact"])
        self.assertIn("not proof", result["note"])
        integral = evaluate(call("integrate", call("sqrt", node("binary", "+", call("tan", x), x)), x))
        self.assertTrue(integral["ok"], integral)
        self.assertIn("Integral", integral["exact"])
        self.assertIn("nintegrate", integral["note"])

    def test_primality_range_and_factorization_limits(self):
        for value, expected in [(2**61-1, "True"), (2**64-59, "True"), (2**64-1, "False"), (-7,"False")]:
            self.assertEqual(expected, evaluate(call("isprime", number(value)))["exact"])
        self.assertIn("2^64", evaluate(call("isprime", number(2**64)))["error"])
        self.assertFalse(evaluate(call("isprime", number("1.5")))["ok"])
        self.assertEqual(360, s.prod(catalog.factorint(360).args))
        for name in ("factorint", "divisors"):
            for invalid in (0,-1,"1.5"):
                result = evaluate(call(name, number(invalid)))
                self.assertFalse(result["ok"], result)
                self.assertIn("positive integer", result["error"])

    def test_eigenvalues_are_labeled_and_numeric_pairs_remain_reusable(self):
        for rows, expected in [([data(2,1),data(1,2)], {(1,1),(3,1)}),
                               ([data(2,1),data(0,2)], {(2,2)})]:
            result = evaluate(call("eigenvalues", node("list", "", *rows)))
            self.assertTrue(result["ok"], result)
            self.assertEqual("rows", result["tree"]["kind"])
            self.assertIn("multiplicity", result["exact"])
            self.assertIn("multiplicity", result["decimal"])
            stored = result["resultAst"]
            pairs = calc_engine.Engine({}).build(stored)
            self.assertEqual(expected, {tuple(pair) for pair in pairs})
            self.assertTrue(evaluate(node("symbol", "Ans"), variables={"Ans":stored})["ok"])


if __name__ == "__main__":
    unittest.main()
