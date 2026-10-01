import json
import math
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "app/src/main/python"))
import calc_engine


def number(value): return {"kind": "number", "value": str(value)}
def symbol(name): return {"kind": "symbol", "value": name}
def binary(op, left, right): return {"kind": "binary", "value": op, "args": [left, right]}
def equation(left, right): return {"kind": "relation", "value": "=", "args": [left, right]}

x, y = symbol("x"), symbol("y")
x2, y2 = binary("^", x, number(2)), binary("^", y, number(2))
circle = equation(binary("+", x2, y2), number(1))


class ImplicitGraphTests(unittest.TestCase):
    def graph(self, *trees, **options):
        return json.loads(calc_engine.dispatch(json.dumps({
            "action": "graph", "graphKind": "implicit", "angle": "RAD", "trees": trees,
            "min": -2, "max": 2, "yMin": -2, "yMax": 2, **options,
        })))

    def points(self, result, index=0):
        self.assertTrue(result["ok"], result.get("error"))
        return [point for point in result["curves"][index] if point is not None]

    def test_circle_has_all_branches_and_coordinates_are_not_parameters(self):
        result = self.graph(circle, variables={"x": number(8), "y": number(9)}, parameters={"x": 8, "y": 9})
        points = self.points(result)
        self.assertTrue(result["implicit"])
        self.assertEqual([], result["parameters"])
        self.assertGreater(len(points), 100)
        for xx, yy in points: self.assertAlmostEqual(1, xx*xx+yy*yy, delta=1e-6)
        for axis in (0, 1):
            self.assertGreater(max(point[axis] for point in points), .99)
            self.assertLess(min(point[axis] for point in points), -.99)

    def test_vertical_horizontal_and_repeated_factor_lines(self):
        for tree, axis, target in [(equation(x, number(.37)), 0, .37),
                                   (equation(y, number(.23)), 1, .23),
                                   (equation(y2, number(0)), 1, 0)]:
            points = self.points(self.graph(tree))
            self.assertGreater(len(points), 100)
            self.assertTrue(all(abs(point[axis]-target) < 1e-6 for point in points))
            self.assertLess(min(point[1-axis] for point in points), -1.99)
            self.assertGreater(max(point[1-axis] for point in points), 1.99)

    def test_ellipse_parameters_multiple_curves_and_bare_zero_expression(self):
        ellipse = equation(binary("+", binary("/", x2, symbol("a")), y2), number(1))
        result = self.graph(ellipse, equation(x, number(.5)), parameters={"a": 4})
        points = self.points(result)
        self.assertEqual(["a"], result["parameters"])
        self.assertEqual(2, len(result["curves"]))
        self.assertGreater(max(point[0] for point in points), 1.99)
        for xx, yy in points: self.assertAlmostEqual(1, xx*xx/4+yy*yy, delta=1e-6)
        default = self.graph(ellipse)
        self.assertLess(max(point[0] for point in self.points(default)), 1.01)
        residual = binary("-", binary("+", x2, y2), number(1))
        self.assertGreater(len(self.points(self.graph(residual))), 100)

    def test_pan_and_zoom_resample_inside_both_axis_ranges(self):
        points = self.points(self.graph(circle, min=0, max=1.2, yMin=0, yMax=1.2))
        self.assertTrue(all(0 <= xx <= 1.2 and 0 <= yy <= 1.2 for xx, yy in points))
        self.assertEqual([], self.points(self.graph(circle, min=2, max=3, yMin=2, yMax=3)))

    def test_disconnected_branches_have_breaks_and_poles_are_not_curves(self):
        result = self.graph(equation(binary("*", x, y), number(1)))
        points = self.points(result)
        self.assertTrue(any(xx < 0 for xx, _ in points))
        self.assertTrue(any(xx > 0 for xx, _ in points))
        self.assertIn(None, result["curves"][0])
        self.assertTrue(all(abs(xx*yy-1) < 1e-6 for xx, yy in points))
        pole = binary("/", number(1), binary("-", x, number(.013)))
        self.assertEqual([], self.points(self.graph(equation(pole, number(0)))))
        domain = {"kind": "call", "value": "sqrt", "args": [x]}
        points = self.points(self.graph(equation(domain, y)))
        self.assertTrue(all(xx >= 0 and yy >= 0 and abs(math.sqrt(xx)-yy) < 1e-5 for xx, yy in points))

    def test_invalid_equations_and_ranges(self):
        inequality = {"kind": "relation", "value": "<", "args": [x, y]}
        for result in [self.graph(inequality), self.graph(equation(x, x)),
                       self.graph(circle, yMin=2, yMax=1), self.graph(), self.graph(*([circle]*7))]:
            self.assertFalse(result["ok"])

    def test_six_curves_fit_the_default_execution_budget(self):
        result = self.graph(*[equation(binary("+", x2, y2), number(radius)) for radius in (1, 2, 3, 4, 5, 6)])
        self.assertEqual(6, len(result.get("curves", [])), result.get("error"))
        for index in range(6): self.assertGreater(len(self.points(result, index)), 100)


if __name__ == "__main__": unittest.main()
