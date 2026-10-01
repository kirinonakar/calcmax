"""Transformed regression accuracy, scale, and cancellation checks."""
import json
import pathlib
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "app/src/main/python"))
import mpmath as mp
import sympy as s
import calc_engine
import calc_statistics
from calc_evaluator import Engine


def request(rows, mode, **options):
    table = {"kind": "list", "args": [
        {"kind": "list", "args": [{"kind": "number", "value": str(value)} for value in row]}
        for row in rows]}
    return json.dumps({"tree": {"kind": "call", "value": "regression", "args": [
        table, {"kind": "symbol", "value": mode}]}, **options})


class RegressionTests(unittest.TestCase):
    def test_transformed_fits_match_log_linear_least_squares(self):
        rows = [(s.Integer(x), s.Integer(y)) for x, y in [(1, 8), (2, 11), (4, 17), (7, 25), (11, 41)]]
        engine = Engine({"precision": 70})
        x = engine.symbol("x")
        for mode in ("logarithmic", "exponential", "power"):
            with self.subTest(mode=mode):
                tx = [s.N(s.log(xx) if mode != "exponential" else xx, 90) for xx, _ in rows]
                ty = [s.N(s.log(yy) if mode != "logarithmic" else yy, 90) for _, yy in rows]
                design = s.Matrix([[1, xx] for xx in tx])
                a, b = (design.T*design).inv()*design.T*s.Matrix(ty)
                expected = (a+b*s.log(x) if mode == "logarithmic" else
                            s.exp(a+b*x) if mode == "exponential" else s.exp(a)*x**b)
                fitted = calc_statistics.fit_regression(engine, rows, mode)
                for at in (s.Rational(3, 2), s.Integer(5), s.Integer(10)):
                    relative = abs(s.N((fitted-expected).subs(x, at)/expected.subs(x, at), 70))
                    self.assertLess(relative, s.Float("1e-65"))

    def test_precision_and_large_offsets_preserve_small_x_variation(self):
        engine = Engine({"precision": 80})
        x = engine.symbol("x")
        # A binary float fit would collapse these distinct x values to one value.
        origin = s.Integer(10)**40
        rows = [(origin+i, s.exp(s.Rational(i, 10))) for i in range(6)]
        fitted = calc_statistics.fit_regression(engine, rows, "exponential")
        for i in (0, 3, 5):
            error = abs(s.N(fitted.subs(x, origin+i)/rows[i][1]-1, 80))
            self.assertLess(error, s.Float("1e-35"))
        rows = [(s.Integer(10)**40+i, (s.Integer(10)**40+i)**s.Rational(3, 2)) for i in range(6)]
        fitted = calc_statistics.fit_regression(engine, rows, "power")
        self.assertLess(abs(s.N(fitted.subs(x, rows[3][0])/rows[3][1]-1, 80)), s.Float("1e-65"))

    def test_hundreds_of_points_finish_with_a_curve_and_reusable_result(self):
        for mode in ("exponential", "power", "logarithmic"):
            with self.subTest(mode=mode):
                result = json.loads(calc_engine.dispatch(request([(i, i*i+3*i+7) for i in range(1, 301)], mode)))
                self.assertTrue(result["ok"], result)
                self.assertTrue(result["approximate"])
                self.assertGreater(len(result["curve"]), 100)
                self.assertIn("resultAst", result)
                self.assertLess(len(result["exact"]), 300)

    def test_input_domains_and_singular_data_are_rejected(self):
        for mode, rows in [("power", [(0, 1), (2, 3)]), ("power", [(1, -1), (2, 3)]),
                           ("exponential", [(1, 0), (2, 3)]), ("logarithmic", [(-1, 2), (2, 3)]),
                           ("exponential", [(1, 2), (1, 3)]), ("power", [(1, 2), (1, 3)])]:
            with self.subTest(mode=mode, rows=rows):
                self.assertFalse(json.loads(calc_engine.dispatch(request(rows, mode)))["ok"])
        for mode in ("power", "exponential", "logarithmic"):
            result = json.loads(calc_engine.dispatch(request([(1, 2), (2, 2), (3, 2)], mode)))
            self.assertTrue(result["ok"], result)

    def test_linear_and_quadratic_keep_exact_coefficients(self):
        linear = json.loads(calc_engine.dispatch(request([(1, 2), (2, 4), (3, 6)], "linear")))
        quadratic = json.loads(calc_engine.dispatch(request([(1, 1), (2, 4), (3, 9)], "quadratic")))
        self.assertEqual(linear["exact"], "2*x")
        self.assertEqual(quadratic["exact"], "x**2")
        self.assertFalse(linear["approximate"])
        self.assertFalse(quadratic["approximate"])

    def test_cancellation_during_numeric_fitting_and_next_request(self):
        class Control:
            cancelled = False
            def isCancelled(self): return self.cancelled
        for mode in ("exponential", "power"):
            control = Control()
            original = calc_statistics._mpf
            calls = 0
            def convert(value, digits):
                nonlocal calls
                calls += 1
                if calls == 8: control.cancelled = True
                return original(value, digits)
            before = mp.mp.dps
            with patch.object(calc_statistics, "_mpf", convert):
                result = json.loads(calc_engine.dispatch(request([(i, i*i+1) for i in range(1, 301)], mode), control))
            self.assertGreaterEqual(calls, 8)
            self.assertEqual(result["error"], "Calculation cancelled")
            self.assertEqual(mp.mp.dps, before)
            self.assertTrue(json.loads(calc_engine.dispatch(request([(1, 2), (2, 4)], mode)))["ok"])


if __name__ == "__main__": unittest.main()
