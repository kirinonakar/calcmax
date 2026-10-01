import math
import pathlib
import sys
import unittest
import sympy as s

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "app/src/main/python"))
from calc_graph import _compiled_graph, graph_function, implicit_samples, simplify_samples, adaptive_samples


class GraphPerformanceTests(unittest.TestCase):
    def test_compilation_reused_across_slider_values_and_bounded(self):
        x, a = s.symbols("x a")
        _compiled_graph.cache_clear()
        for value in range(40):
            function = graph_function(a*s.sin(x), (x,), {a: value})
            self.assertAlmostEqual(value, function(math.pi/2))
        self.assertEqual(1, _compiled_graph.cache_info().misses)
        self.assertEqual(39, _compiled_graph.cache_info().hits)
        for value in range(100): graph_function(x+value, (x,))
        self.assertLessEqual(_compiled_graph.cache_info().currsize, 64)

    def test_quadtree_avoids_empty_plane_without_losing_closed_curve(self):
        calls = [0]
        def circle(x, y):
            calls[0] += 1
            return (x-.17)**2+(y+.23)**2-.4**2
        curve = implicit_samples(circle, -3, 3, -3, 3, 176)
        points = [p for p in curve if p is not None]
        self.assertGreater(len(points), 100)
        self.assertLess(calls[0], 176**2//2)
        for x, y in points: self.assertAlmostEqual(.4**2, (x-.17)**2+(y+.23)**2, delta=1e-6)
        self.assertLess(min(p[0] for p in points), -.22)
        self.assertGreater(max(p[0] for p in points), .56)

    def test_simplification_respects_screen_error_and_parameters(self):
        points = [[-3+i*6/500, math.sin(-3+i*6/500)] for i in range(501)]
        parameters = list(range(501))
        result, retained = simplify_samples(points, parameters, {"yMin": -2, "yMax": 2}, -3, 3)
        self.assertLess(len(result), 150)
        self.assertEqual(points[0], result[0]); self.assertEqual(points[-1], result[-1])
        self.assertEqual(result, [points[i] for i in retained])
        for left, right in zip(retained, retained[1:]):
            for index in range(left+1, right):
                fraction = (points[index][0]-points[left][0])/(points[right][0]-points[left][0])
                linear = points[left][1]+fraction*(points[right][1]-points[left][1])
                self.assertLessEqual(abs(points[index][1]-linear)*460/4, .300001)

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

    def test_axis_aligned_runs_collapse_without_repeated_axis_hit_anchors(self):
        points = [[i/500, 0] for i in range(501)]
        result, parameters = simplify_samples(points, list(range(501)), {}, 0, 1)
        self.assertEqual([points[0], points[-1]], result)
        self.assertEqual([0,500], parameters)

    def test_long_linear_segments_are_continuous_but_shifted_poles_have_explicit_breaks(self):
        points, parameters = adaptive_samples(lambda x: x, -10, 10, 500)
        result, _ = simplify_samples(points, parameters, {}, -10, 10)
        self.assertNotIn(None, result)
        self.assertLessEqual(len(result), 3)
        points, parameters = adaptive_samples(lambda x: 1/(x-.013), -1, 1, 500)
        self.assertIn(None, points)
        gap = points.index(None)
        self.assertLess(points[gap-1][0], .013)
        self.assertGreater(points[gap+1][0], .013)


if __name__ == "__main__": unittest.main()
