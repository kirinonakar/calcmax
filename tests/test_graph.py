import json
import math
import pathlib
import sys
import unittest
import time

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "app/src/main/python"))
import calc_engine
from calc_evaluator import Engine
from calc_graph import simplify_samples, graph_expressions, _graph_programs
from unittest.mock import patch
import sympy as s


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


class GraphPerformanceTests(unittest.TestCase):
    def test_symbolic_programs_reuse_builds_and_invalidate_saved_context(self):
        _graph_programs.clear()
        tree={"kind":"symbol","value":"a"}
        def engine(value):return Engine({"variables":{"a":{"kind":"number","value":str(value)}}})
        self.assertEqual((s.Integer(2),),graph_expressions(engine(2),[tree],("x",)))
        reused=engine(2)
        with patch.object(reused,"build",side_effect=AssertionError("symbolic program rebuilt")):
            self.assertEqual((s.Integer(2),),graph_expressions(reused,[tree],("x",)))
        self.assertEqual((s.Integer(3),),graph_expressions(engine(3),[tree],("x",)))
        for value in range(50):graph_expressions(engine(value),[tree],("x",))
        self.assertLessEqual(len(_graph_programs),32)

    def test_program_cache_does_not_freeze_random_calls_in_stored_definitions(self):
        _graph_programs.clear()
        tree={"kind":"symbol","value":"a"}
        engine=Engine({"variables":{"a":{"kind":"call","value":"rnd","args":[]}}})
        with patch("calc_evaluator.random.random",side_effect=[.25,.75]):
            self.assertAlmostEqual(.25,float(graph_expressions(engine,[tree],("x",))[0]))
            self.assertAlmostEqual(.75,float(graph_expressions(engine,[tree],("x",))[0]))
        self.assertEqual(0,len(_graph_programs))


    def test_simplification_never_bridges_breaks_or_loses_a_closed_loop(self):
        points = [[-1,-1], [0,0], None, [1,1], [2,2]]
        result, parameters = simplify_samples(points, list(range(5)), {}, -2, 2)
        self.assertEqual(points, result)
        self.assertEqual(list(range(5)), parameters)
        circle = [[math.cos(i*2*math.pi/500), math.sin(i*2*math.pi/500)] for i in range(501)]
        result, parameters = simplify_samples(circle, list(range(501)), {"graphKind": "parametric", "xMin": -2, "xMax": 2}, 0, 2*math.pi)
        self.assertGreater(len(result), 30)
        self.assertEqual(circle[0], result[0]); self.assertEqual(circle[-1], result[-1])
        self.assertEqual(result, [circle[i] for i in parameters])


if __name__ == "__main__":
    unittest.main()
