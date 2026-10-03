import json
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "app/src/main/python"))
import calc_engine
import sympy as s
from calc_shared import numeric_integral


def number(value): return {"kind": "number", "value": str(value)}
def symbol(name): return {"kind": "symbol", "value": name}
def binary(op, left, right): return {"kind": "binary", "value": op, "args": [left, right]}

x = symbol("x")
polynomial = binary("-", binary("-", binary("*", number(3), binary("^", x, number(2))),
                               binary("*", number(16), x)), number(20))


class NumericIntegralTests(unittest.TestCase):
    def dispatch(self, **request):
        result = json.loads(calc_engine.dispatch(json.dumps(request)))
        self.assertTrue(result["ok"], result)
        return result

    def test_graph_zero_signed_integral_keeps_shading(self):
        for tree in (polynomial, {"kind": "relation", "value": "=", "args": [symbol("y"), polynomial]}):
            for precision in (15, 50, 100):
                result = self.dispatch(action="graphAnalysis", trees=[tree], analysis="integral",
                                       a=-2, b=0, precision=precision)
                self.assertEqual(0, result["value"])
                points = [point for polygon in result["integralFill"] for point in polygon]
                self.assertTrue(any(py > 0 for _, py in points))
                self.assertTrue(any(py < 0 for _, py in points))

    def test_nintegrate_zero_nonzero_and_reversed_bounds(self):
        for lower, upper, expected in ((-2, 0, 0), (0, -2, 0), (-2, 1, -27), (1, -2, 27)):
            tree = {"kind": "call", "value": "nintegrate", "args": [polynomial, x, number(lower), number(upper)]}
            result = self.dispatch(tree=tree)
            self.assertEqual(expected, float(result["decimal"]))

    def test_small_nonzero_integrals_are_not_chopped(self):
        variable = s.Symbol("x")
        epsilon = s.Rational(1, 10**80)
        expression = 3*variable**2-16*variable-20+epsilon
        result = numeric_integral(expression, variable, -2.0, 0.0, 100)
        self.assertGreater(result, 0)
        self.assertEqual(s.N(2*epsilon, 100), result)
        graph = self.dispatch(action="graphAnalysis", trees=[binary("+", polynomial, number("1e-80"))],
                              analysis="integral", a=-2, b=0, precision=100)
        self.assertEqual(2e-80, graph["value"])

    def test_float_coefficients_preserve_stored_values(self):
        variable = s.Symbol("x")
        coefficient = s.Float("0.12345678901234567890123456789", 100)
        expression = coefficient*(variable+1)
        self.assertEqual(0, numeric_integral(expression, variable, -2.0, 0.0, 100))
        self.assertEqual(s.N(s.Rational(coefficient)/2, 100), numeric_integral(coefficient*variable, variable, 0, 1, 100))

    def test_parametric_integral_and_stationary_arclength(self):
        pair = {"kind": "list", "args": [x, polynomial]}
        result = self.dispatch(action="graphAnalysis", graphKind="parametric", variable="x",
                               trees=[pair], analysis="integral", a=-2, b=0)
        self.assertEqual(0, result["value"])
        stationary = {"kind": "list", "args": [number(1), number(2)]}
        result = self.dispatch(action="graphAnalysis", graphKind="parametric", variable="x",
                               trees=[stationary], analysis="arclength", a=-2, b=0)
        self.assertEqual(0, result["value"])

    def test_nonpolynomial_quadrature_and_infinite_bounds(self):
        variable = s.Symbol("x")
        self.assertAlmostEqual(2, float(numeric_integral(s.sin(variable), variable, 0, s.pi, 30)))
        self.assertAlmostEqual(1, float(numeric_integral(s.exp(-variable), variable, 0, s.oo, 30)))
        divergent = {"kind": "call", "value": "nintegrate", "args": [
            binary("/", number(1), binary("^", x, number(2))), x, number(-1), number(1)]}
        result = json.loads(calc_engine.dispatch(json.dumps({"tree": divergent})))
        self.assertFalse(result["ok"], result)


if __name__ == "__main__":
    unittest.main()
