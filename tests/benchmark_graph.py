"""Run with `python tests/benchmark_graph.py`; times are host-dependent."""
import json
import math
import pathlib
import statistics
import sys
import time

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "app/src/main/python"))
import calc_engine
from calc_graph import implicit_samples


def benchmark():
    for name, formula in [("circle", lambda x, y: x*x+y*y-1),
                          ("hyperbola", lambda x, y: x*y-1),
                          ("oscillating", lambda x, y: math.sin(3*x)+math.cos(3*y))]:
        durations = []
        for _ in range(5):
            calls = [0]
            def function(x, y):
                calls[0] += 1
                return formula(x, y)
            start = time.perf_counter()
            curve = implicit_samples(function, -3, 3, -3, 3, 176)
            durations.append(1000*(time.perf_counter()-start))
        print(f"{name}: {statistics.median(durations):.1f} ms, {calls[0]} evaluations, {len(curve)} entries")

    request = {"action": "graph", "angle": "RAD", "min": -10, "max": 10,
               "yMin": -5, "yMax": 5, "samples": 500,
               "trees": [{"kind": "binary", "value": "*", "args": [
                   {"kind": "symbol", "value": "a"},
                   {"kind": "call", "value": "sin", "args": [{"kind": "symbol", "value": "x"}]}]}]}
    calc_engine.dispatch(json.dumps({**request, "parameters": {"a": 1}}))
    start = time.perf_counter()
    for index in range(50):
        response = json.loads(calc_engine.dispatch(json.dumps({**request, "parameters": {"a": 1+index/50}})))
        assert response["ok"], response
    print(f"50 slider updates: {1000*(time.perf_counter()-start):.1f} ms, {len(response['curves'][0])} points in last curve")


if __name__ == "__main__":
    benchmark()
