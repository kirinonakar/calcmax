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
    def test_derivative_analysis_uses_the_selected_order_for_every_action(self):
        cubic=binary("-",binary("^",x,number(3)),binary("*",number(3),x))
        def analyze(action,order=1,**options):
            result=self.analyze(cubic,analysis=action,selectedDerivativeOrder=order,**options)
            self.assertTrue(result["ok"],result)
            self.assertEqual(order,result["selectedDerivativeOrder"])
            return result
        roots=analyze("root")["points"]
        self.assertEqual(2,len(roots))
        for point,expected in zip(roots,(-1,1)):self.assertAlmostEqual(expected,point[0],delta=1e-7)
        self.assertEqual([[0.0,-3.0]],analyze("minimum")["points"])
        self.assertEqual([[-2.0,9.0],[2.0,9.0]],analyze("maximum")["points"])
        self.assertEqual([],analyze("inflection")["points"])
        self.assertEqual([[0.0,-3.0]],analyze("yintercept",a=100,b=101)["points"])
        self.assertAlmostEqual(3,analyze("derivative",a=.5)["value"])
        tangent=analyze("tangent",a=.5)
        self.assertEqual([[.5,-2.25]],tangent["points"]);self.assertAlmostEqual(3,tangent["value"])
        integral=analyze("integral",a=-1,b=1)
        self.assertAlmostEqual(-4,integral["value"]);self.assertTrue(integral["integralFill"])
        self.assertAlmostEqual(.5*math.sqrt(37)+math.asinh(6)/12,analyze("arclength",a=0,b=1)["value"])
        self.assertEqual([[0.0,0.0]],analyze("root",2)["points"])
        self.assertAlmostEqual(6,analyze("derivative",2,a=.5)["value"])
        self.assertAlmostEqual(0,analyze("integral",2,a=-1,b=1)["value"])
        self.assertAlmostEqual(2*math.sqrt(37),analyze("arclength",2,a=-1,b=1)["value"])
        tangent=analyze("tangent",2,a=1)
        self.assertEqual([[1.0,6.0]],tangent["points"]);self.assertAlmostEqual(6,tangent["value"])

    def test_intersections_accept_original_first_and_second_derivative_targets(self):
        for source,orders in ((x2,(0,1)),(binary("^",x,number(3)),(1,2))):
            for first,second in (orders,tuple(reversed(orders))):
                result=self.analyze(source,analysis="intersection",selected=0,other=0,selectedDerivativeOrder=first,otherDerivativeOrder=second,a=-1,b=3)
                self.assertTrue(result["ok"],result);self.assertEqual(2,len(result["points"]))
                for point,expected in zip(result["points"],(0,2)):self.assertAlmostEqual(expected,point[0],delta=1e-7)
        parameterized=binary("*",symbol("a"),binary("^",x,number(3)))
        other=binary("*",symbol("b"),x)
        result=self.analyze(parameterized,other,analysis="intersection",selectedDerivativeOrder=1,parameters={"a":2,"b":6},variables={"a":number(999)},a=-1,b=2)
        self.assertTrue(result["ok"],result)
        self.assertEqual(2,len(result["points"]));self.assertAlmostEqual(1,result["points"][1][0],delta=1e-7);self.assertAlmostEqual(6,result["points"][1][1],delta=1e-7)
        coincident=self.analyze({"kind":"call","value":"exp","args":[x]},analysis="intersection",selected=0,other=0,selectedDerivativeOrder=1)
        self.assertFalse(coincident["ok"]);self.assertIn("not isolated",coincident["error"])
        zero=self.analyze(x,analysis="root",selectedDerivativeOrder=2)
        self.assertFalse(zero["ok"]);self.assertIn("not isolated",zero["error"])

    def test_implicit_derivative_analysis_preserves_traced_branches_and_intersections(self):
        hint=[.25,-.25/math.sqrt(1-.25**2)]
        tangent=self.analyze(circle,analysis="tangent",a=0,selectedDerivativeOrder=1,tracePoint=hint)
        self.assertTrue(tangent["ok"],tangent);self.assertAlmostEqual(-1,tangent["value"])
        integral=self.analyze(circle,analysis="integral",a=-.5,b=.5,selectedDerivativeOrder=1,tracePoint=hint)
        self.assertTrue(integral["ok"],integral);self.assertAlmostEqual(0,integral["value"])
        ambiguous=self.analyze(circle,analysis="tangent",a=0,selectedDerivativeOrder=1,tracePoint=[0,0])
        self.assertFalse(ambiguous["ok"]);self.assertIn("choose a branch",ambiguous["error"])
        roots=self.analyze(circle,analysis="root",a=-.8,b=.8,selectedDerivativeOrder=1)
        self.assertTrue(roots["ok"],roots);self.assertEqual([[0.0,0.0]],roots["points"])
        intersections=self.analyze(circle,number(2),analysis="intersection",a=-.8,b=.8,selectedDerivativeOrder=2)
        self.assertTrue(intersections["ok"],intersections);self.assertEqual(2,len(intersections["points"]))
        for point in intersections["points"]:
            self.assertAlmostEqual(math.sqrt(1-2**(-2/3)),abs(point[0]),delta=1e-7);self.assertAlmostEqual(2,point[1],delta=1e-7)

    def test_derivative_integrals_respect_poles_and_allow_integrable_endpoints(self):
        pole=self.analyze(binary("/",number(1),x),analysis="integral",a=-1,b=1,selectedDerivativeOrder=1)
        self.assertFalse(pole["ok"]);self.assertIn("continuous interval",pole["error"])
        endpoint=self.analyze({"kind":"call","value":"sqrt","args":[x]},analysis="integral",a=0,b=1,selectedDerivativeOrder=1)
        self.assertTrue(endpoint["ok"],endpoint);self.assertAlmostEqual(1,endpoint["value"])

    def test_first_and_second_derivative_curves_are_independent(self):
        cubic=binary("^",x,number(3))
        result=self.graph(cubic,x2,graphKind="cartesian",derivativeSelected=0,secondDerivativeSelected=1)
        self.assertTrue(result["ok"],result)
        self.assertEqual(4,len(result["curves"]))
        self.assertEqual(2,result["derivativeCurveIndex"])
        self.assertEqual(3,result["secondDerivativeCurveIndex"])
        self.assertEqual(0,result["derivativeSelected"])
        self.assertEqual(1,result["secondDerivativeSelected"])
        for at,value in filter(None,result["curves"][2]):self.assertAlmostEqual(3*at**2,value,delta=1e-8)
        for at,value in filter(None,result["curves"][3]):self.assertAlmostEqual(2,value,delta=1e-8)
        for source,expected in ((cubic,lambda at:6*at),(x,lambda at:0)):
            result=self.graph(source,graphKind="cartesian",secondDerivativeSelected=0)
            self.assertTrue(result["ok"],result)
            self.assertNotIn("derivativeCurveIndex",result)
            self.assertEqual(1,result["secondDerivativeCurveIndex"])
            self.assertTrue(result["secondDerivativeExpression"])
            points=list(filter(None,result["curves"][1]));self.assertTrue(points)
            for at,value in points:self.assertAlmostEqual(expected(at),value,delta=1e-8)

    def test_second_derivative_of_implicit_circle_samples_both_branches(self):
        result=self.graph(circle,graphKind="cartesian",min=-.8,max=.8,secondDerivativeSelected=0)
        self.assertTrue(result["ok"],result)
        points=result["curves"][result["secondDerivativeCurveIndex"]]
        self.assertIn(None,points)
        values=[value for at,value in filter(None,points)]
        self.assertTrue(any(value<0 for value in values));self.assertTrue(any(value>0 for value in values))
        for at,value in filter(None,points):self.assertAlmostEqual((1-at**2)**-1.5,abs(value),delta=1e-8)

    def test_band_y_constraints_keep_disconnected_negative_regions(self):
        with self.subTest(scenario='band_y_constraints_keep_disconnected_negative_regions'):
            with self.subTest(scenario='band_y_constraints_keep_disconnected_negative_regions'):
                item={"mode":"band","trees":[{"kind":"call","value":name,"args":[x]} for name in ("sin","cos")],
                      "xBounds":[{"side":"lower","tree":number(-1)},{"side":"upper","tree":number(2)}],
                      "yBounds":[{"side":"upper","tree":number(0)}]}
                result=self.graph(graphKind="cartesian",min=-5,max=5,shadings=[item])
                self.assertTrue(result["ok"],result)
                polygons=result["shadings"][0]["fill"]
                self.assertEqual(2,len(polygons))
                self.assertAlmostEqual(-1,min(px for px,py in polygons[0]));self.assertAlmostEqual(0,max(px for px,py in polygons[0]),delta=1e-7)
                self.assertAlmostEqual(math.pi/2,min(px for px,py in polygons[1]),delta=1e-7);self.assertAlmostEqual(2,max(px for px,py in polygons[1]))
                self.assertTrue(all(py<=0 and min(math.sin(px),math.cos(px))-1e-7<=py<=max(math.sin(px),math.cos(px))+1e-7 for polygon in polygons for px,py in polygon))
                self.assertTrue(all(py<=0 for line in result["shadings"][0]["boundary"] for point in line if point is not None for px,py in [point]))
            with self.subTest(scenario='band_y_constraints_parameters_and_empty_intersections'):
                item={"mode":"band","trees":[x,number(2)],"yBounds":[{"side":"upper","tree":symbol("c")}]}
                result=self.graph(graphKind="cartesian",min=-1,max=2,shadings=[item],parameters={"c":-.25})
                self.assertTrue(result["ok"],result);self.assertEqual(["c"],result["parameters"])
                polygon=result["shadings"][0]["fill"][0]
                self.assertAlmostEqual(-.25,max(px for px,py in polygon));self.assertAlmostEqual(-.25,max(py for px,py in polygon))
                item["yBounds"]=[{"side":"lower","tree":number(-.5)},{"side":"upper","tree":number(0)}]
                result=self.graph(graphKind="cartesian",min=-1,max=2,shadings=[item])
                self.assertTrue(result["ok"],result);self.assertTrue(all(-.5<=py<=0 for polygon in result["shadings"][0]["fill"] for px,py in polygon))
                for bounds in ([{"side":"upper","tree":number(-2)}],[{"side":"lower","tree":number(1)},{"side":"upper","tree":number(0)}]):
                    item["yBounds"]=bounds
                    result=self.graph(graphKind="cartesian",min=-1,max=2,shadings=[item])
                    self.assertTrue(result["ok"],result);self.assertEqual([],result["shadings"][0]["fill"]);self.assertEqual([[],[]],result["shadings"][0]["boundary"])
            with self.subTest(scenario='function_band_inequality_bounds_clip_and_use_slider_parameters'):
                item={"mode":"band","trees":[{"kind":"call","value":name,"args":[x]} for name in ("sin","cos")],
                      "xBounds":[{"side":"lower","tree":{"kind":"unary","value":"-","args":[symbol("pi")]}},{"side":"upper","tree":symbol("pi")}]}
                result=self.graph(graphKind="cartesian",min=-10,max=10,shadings=[item])
                self.assertTrue(result["ok"],result)
                polygon=result["shadings"][0]["fill"][0]
                self.assertAlmostEqual(-math.pi,min(p[0] for p in polygon));self.assertAlmostEqual(math.pi,max(p[0] for p in polygon))
                self.assertTrue(all(min(math.sin(px),math.cos(px))-1e-10 <= py <= max(math.sin(px),math.cos(px))+1e-10 for px,py in polygon))
                result=self.graph(graphKind="cartesian",min=0,max=1,shadings=[item])
                self.assertTrue(result["ok"],result);self.assertEqual({0.0,1.0},{result["shadings"][0]["fill"][0][0][0],result["shadings"][0]["fill"][0][500][0]})
                item["xBounds"][1]["tree"]=symbol("a")
                for parameters,expected in (({"a":2},2.0),({},1.0)):
                    result=self.graph(graphKind="cartesian",min=-10,max=10,shadings=[item],parameters=parameters)
                    self.assertTrue(result["ok"],result);self.assertEqual(["a"],result["parameters"])
                    self.assertEqual(expected,max(p[0] for p in result["shadings"][0]["fill"][0]))
                result=self.graph(graphKind="cartesian",min=5,max=10,shadings=[item])
                self.assertTrue(result["ok"],result);self.assertEqual([],result["shadings"][0]["fill"])
        with self.subTest(scenario='shaded_regions_skip_undefined_and_conflicting_y_bounds'):
            constraints=[{"axis":"y","side":side} for side in ("lower","upper")]
            for boundary in (binary("/",number(1),x),{"kind":"call","value":"sqrt","args":[x]}):
                item={"mode":"region","trees":[number(0),boundary],"constraints":constraints}
                result=self.graph(graphKind="cartesian",min=-2,max=2,shadings=[item])
                self.assertTrue(result["ok"],result)
                self.assertTrue(result["shadings"][0]["fill"])
                self.assertTrue(all(px>=0 for polygon in result["shadings"][0]["fill"] for px,py in polygon))
            item={"mode":"region","trees":[number(3),number(1)],"constraints":constraints}
            result=self.graph(graphKind="cartesian",shadings=[item])
            self.assertTrue(result["ok"],result);self.assertEqual([],result["shadings"][0]["fill"]);self.assertEqual([],result["shadings"][0]["boundary"])


    def test_y_intercepts_ignore_x_search_range_and_handle_all_branches(self):
        with self.subTest(scenario='y_intercepts_ignore_x_search_range_and_handle_all_branches'):
            for tree,expected in ((binary("+",x,number(3)),[[0.0,3.0]]),(circle,[[0.0,-1.0],[0.0,1.0]]),
                                  (equation(y2,number(0)),[[0.0,0.0]]),(binary("/",number(1),x),[]),
                                  (equation(x,number(1)),[])):
                result=self.analyze(tree,analysis="yintercept",a=8,b=8,yMin=-2,yMax=2)
                self.assertTrue(result["ok"],result);self.assertEqual(expected,result["points"])
            result=self.analyze(equation(x,number(0)),analysis="yintercept")
            self.assertFalse(result["ok"]);self.assertIn("not isolated",result["error"])
            result=self.analyze(binary("+",x,symbol("a")),analysis="yintercept",parameters={"a":7})
            self.assertTrue(result["ok"],result);self.assertEqual([[0.0,7.0]],result["points"])
        with self.subTest(scenario='parametric_y_intercepts_find_tangencies_and_deduplicate'):
            pair={"kind":"list","args":[x2,binary("+",x,number(1))]}
            result=self.analyze(pair,graphKind="parametric",variable="x",analysis="yintercept")
            self.assertTrue(result["ok"],result);self.assertEqual([[0.0,1.0]],result["points"])
            result=self.analyze(number(1),graphKind="polar",analysis="yintercept",a=0,b=2*math.pi)
            self.assertTrue(result["ok"],result);self.assertEqual(2,len(result["points"]))
            self.assertAlmostEqual(1,result["points"][0][1]);self.assertAlmostEqual(-1,result["points"][1][1])


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

    def analyze(self, *trees, **options):
        return json.loads(calc_engine.dispatch(json.dumps({
            "action":"graphAnalysis", "graphKind":"cartesian", "trees":trees,
            "analysis":"root", "a":-2, "b":2, "selected":0, "other":1, **options,
        })))

    def test_cartesian_mixes_functions_equations_and_contours_without_y_sliders(self):
        with self.subTest(scenario='cartesian_mixes_functions_equations_and_contours_without_y_sliders'):
            line = binary("+",x,number(1))
            result = self.graph(line,equation(y,line),circle,equation(x,number(.3)),graphKind="cartesian",
                                variables={"x":number(8),"y":number(9)},parameters={"x":8,"y":9})
            self.assertTrue(result["ok"],result)
            self.assertEqual([],result["parameters"])
            self.assertEqual([False,False,True,True],result["implicitCurves"])
            self.assertEqual(result["curves"][0],result["curves"][1])
            self.assertTrue(all(abs(xx*xx+yy*yy-1)<1e-6 for xx,yy in self.points(result,2)))
            self.assertTrue(all(abs(xx-.3)<1e-6 for xx,yy in self.points(result,3)))
        with self.subTest(scenario='cartesian_circle_analysis_searches_both_branches_and_chooses_traced_tangents'):
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


    def graph(self, *trees, **options):
        return json.loads(calc_engine.dispatch(json.dumps({
            "action": "graph", "graphKind": "implicit", "angle": "RAD", "trees": trees,
            "min": -2, "max": 2, "yMin": -2, "yMax": 2, **options,
        })))

    def points(self, result, index=0):
        self.assertTrue(result["ok"], result.get("error"))
        return [point for point in result["curves"][index] if point is not None]


    def test_disconnected_branches_have_breaks_and_poles_are_not_curves(self):
        with self.subTest(scenario='disconnected_branches_have_breaks_and_poles_are_not_curves'):
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
        with self.subTest(scenario='slider_frames_reuse_compiled_contour_and_keep_degenerate_repeated_factors'):
            from calc_graph import _compiled_graph
            _compiled_graph.cache_clear()
            radius=equation(binary("+",x2,y2),symbol("a"))
            for value in (1,1.1,1.2):
                self.assertGreater(len(self.points(self.graph(radius,parameters={"a":value},samples=200))),100)
            self.assertEqual(1,_compiled_graph.cache_info().misses)
            points=self.points(self.graph(equation(y2,symbol("a")),parameters={"a":0}))
            self.assertGreater(len(points),100)
            self.assertTrue(all(abs(point[1])<1e-6 for point in points))

    def test_invalid_equations_and_ranges(self):
        inequality = {"kind": "relation", "value": "<", "args": [x, y]}
        for result in [self.graph(inequality), self.graph(equation(x, x)),
                       self.graph(circle, yMin=2, yMax=1), self.graph(), self.graph(*([circle]*7))]:
            self.assertFalse(result["ok"])


class GraphPerformanceTests(unittest.TestCase):
    def test_symbolic_programs_reuse_builds_and_invalidate_saved_context(self):
        with self.subTest(scenario='symbolic_programs_reuse_builds_and_invalidate_saved_context'):
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
        with self.subTest(scenario='program_cache_does_not_freeze_random_calls_in_stored_definitions'):
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
