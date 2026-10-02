import json
import math
import pathlib
import sys
import unittest
import time

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
    def test_parameterized_quadratic_circle_intersections_use_current_values_without_symbolic_quartics(self):
        from unittest.mock import patch
        quadratic=binary("+",binary("+",binary("*",symbol("a"),x2),binary("*",symbol("b"),x)),symbol("c"))
        radius=equation(binary("+",x2,y2),number(5))
        for parameters in ({"a":1,"b":1,"c":1},{"a":0,"b":0,"c":1},{"a":1,"b":0,"c":0},{"a":.125,"b":-.7,"c":.2},{"a":1,"b":0,"c":-3}):
            start=time.perf_counter()
            with patch("calc_graph.s.solve",side_effect=AssertionError("symbolic quartic solver used")):
                for trees in ((quadratic,radius),(radius,quadratic)):
                    result=self.analyze(*trees,analysis="intersection",a=-3,b=3,parameters=parameters,variables={"a":number(999),"b":number(999),"c":number(999)})
                    self.assertTrue(result["ok"],result);self.assertEqual(4 if parameters["c"]==-3 else 2,len(result["points"]))
                    for px,py in result["points"]:
                        self.assertAlmostEqual(parameters["a"]*px**2+parameters["b"]*px+parameters["c"],py,delta=1e-7)
                        self.assertAlmostEqual(px**2+py**2,5,delta=1e-7)
                    if parameters=={"a":0,"b":0,"c":1}:self.assertEqual([[-2.0,1.0],[2.0,1.0]],result["points"])
            self.assertLess(time.perf_counter()-start,1,"intersections must fit the interactive computation budget")

    def test_polynomial_intersections_keep_tangencies_and_no_real_intersections(self):
        radius=equation(binary("+",x2,y2),number(5))
        result=self.analyze(radius,number(3),analysis="intersection",a=-3,b=3)
        self.assertTrue(result["ok"],result);self.assertEqual([],result["points"])
        circle_one=equation(binary("+",x2,y2),number(1))
        result=self.analyze(circle_one,number(1),analysis="intersection",a=-2,b=2)
        self.assertTrue(result["ok"],result);self.assertEqual([[0.0,1.0]],result["points"])
    def analyze(self, *trees, **options):
        return json.loads(calc_engine.dispatch(json.dumps({
            "action":"graphAnalysis", "graphKind":"cartesian", "trees":trees,
            "analysis":"root", "a":-2, "b":2, "selected":0, "other":1, **options,
        })))

    def test_cartesian_mixes_functions_equations_and_contours_without_y_sliders(self):
        line = binary("+",x,number(1))
        result = self.graph(line,equation(y,line),circle,equation(x,number(.3)),graphKind="cartesian",
                            variables={"x":number(8),"y":number(9)},parameters={"x":8,"y":9})
        self.assertTrue(result["ok"],result)
        self.assertEqual([],result["parameters"])
        self.assertEqual([False,False,True,True],result["implicitCurves"])
        self.assertEqual(result["curves"][0],result["curves"][1])
        self.assertTrue(all(abs(xx*xx+yy*yy-1)<1e-6 for xx,yy in self.points(result,2)))
        self.assertTrue(all(abs(xx-.3)<1e-6 for xx,yy in self.points(result,3)))

    def test_cartesian_function_and_y_equation_support_the_same_analysis_and_derivative_graph(self):
        line = binary("+",x,number(1))
        for action in ("root","minimum","maximum","inflection","derivative","tangent","integral","arclength"):
            options={"analysis":action,"a":0,"b":0} if action in ("derivative","tangent") else {"analysis":action,"a":-2,"b":2}
            plain, explicit = self.analyze(line,**options),self.analyze(equation(y,line),**options)
            self.assertTrue(plain["ok"],plain);self.assertEqual(plain,explicit)
        result=self.graph(equation(y,line),graphKind="cartesian",derivativeSelected=0)
        self.assertTrue(result["ok"],result);self.assertEqual(1,result["derivativeCurveIndex"])
        self.assertTrue(all(abs(point[1]-1)<1e-9 for point in self.points(result,1)))

    def test_cartesian_circle_analysis_searches_both_branches_and_chooses_traced_tangents(self):
        roots=self.analyze(circle)
        self.assertTrue(roots["ok"],roots);self.assertEqual(2,len(roots["points"]))
        self.assertAlmostEqual(-1,roots["points"][0][0]);self.assertAlmostEqual(1,roots["points"][1][0])
        for action,expected in (("minimum",-1),("maximum",1)):
            result=self.analyze(circle,analysis=action)
            self.assertTrue(result["ok"],result);self.assertEqual(1,len(result["points"]))
            self.assertAlmostEqual(0,result["points"][0][0]);self.assertAlmostEqual(expected,result["points"][0][1])
        for tree in (equation(y,number(0)),equation(x,number(0))):
            result=self.analyze(circle,tree,analysis="intersection")
            self.assertTrue(result["ok"],result);self.assertEqual(2,len(result["points"]))
        missing=self.analyze(circle,analysis="tangent",a=0,b=0)
        self.assertFalse(missing["ok"]);self.assertIn("choose a branch",missing["error"])
        for py in (-math.sqrt(.75),math.sqrt(.75)):
            result=self.analyze(circle,analysis="tangent",a=.5,b=.5,tracePoint=[.5,py])
            self.assertTrue(result["ok"],result);self.assertAlmostEqual(-.5/py,result["value"])
            self.assertAlmostEqual(py,result["points"][0][1]);self.assertEqual(2,len(result["line"]))
        result=self.analyze(circle,analysis="tangent",a=1,b=1)
        self.assertTrue(result["ok"],result);self.assertTrue(result["vertical"])
        result=self.analyze(circle,analysis="integral",a=-1,b=1,tracePoint=[0,1])
        self.assertTrue(result["ok"],result);self.assertAlmostEqual(math.pi/2,result["value"])

    def test_cartesian_circle_derivative_preserves_both_branches(self):
        result=self.graph(circle,graphKind="cartesian",derivativeSelected=0)
        self.assertTrue(result["ok"],result)
        slopes=self.points(result,1)
        self.assertTrue(any(px>.3 and py>0 for px,py in slopes))
        self.assertTrue(any(px>.3 and py<0 for px,py in slopes))

    def test_integral_fill_covers_its_own_interval_for_simplified_lines_and_curves(self):
        line=binary("+",x,number(3))
        plotted=self.graph(line,graphKind="cartesian",min=-10,max=10)
        self.assertLess(len(self.points(plotted)),10,"display curve is intentionally simplified")
        self.assertFalse(any(.25<=px<=.75 for px,_ in self.points(plotted)),"viewport vertices alone cannot shade this interval")
        for tree in (line,equation(y,line),x2):
            result=self.analyze(tree,analysis="integral",a=.25,b=.75)
            self.assertTrue(result["ok"],result)
            polygon=result["integralFill"][0]
            self.assertGreater(len(polygon),100)
            self.assertEqual([.25,0.0],polygon[0]);self.assertEqual([.75,0.0],polygon[-1])
            self.assertAlmostEqual(.25,polygon[1][0]);self.assertAlmostEqual(.75,polygon[-2][0])
        result=self.analyze(circle,analysis="integral",a=-1,b=1,tracePoint=[0,-1])
        self.assertTrue(result["ok"],result)
        self.assertTrue(all(py<=0 for polygon in result["integralFill"] for _,py in polygon))

    def test_slider_frames_reuse_compiled_contour_and_keep_degenerate_repeated_factors(self):
        from calc_graph import _compiled_graph
        _compiled_graph.cache_clear()
        radius=equation(binary("+",x2,y2),symbol("a"))
        for value in (1,1.1,1.2):
            self.assertGreater(len(self.points(self.graph(radius,parameters={"a":value},samples=200))),100)
        self.assertEqual(1,_compiled_graph.cache_info().misses)
        points=self.points(self.graph(equation(y2,symbol("a")),parameters={"a":0}))
        self.assertGreater(len(points),100)
        self.assertTrue(all(abs(point[1])<1e-6 for point in points))
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
