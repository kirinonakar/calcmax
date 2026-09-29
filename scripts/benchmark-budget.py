"""Compare the former line trace with the current guard on identical warm CAS work.

Run with the project's Python environment: python scripts/benchmark-budget.py
"""
import json
import pathlib
import statistics
import sys
import time

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "app/src/main/python"))
import calc_engine
from calc_runtime import Budget


class LegacyBudget:
    def __init__(self, seconds=30, steps=100000000, control=None):
        self.steps = steps
        self.deadline = time.monotonic() + seconds

    def trace(self, frame, event, arg):
        self.steps -= 1
        if self.steps % 1024 == 0 and (self.steps <= 0 or time.monotonic() > self.deadline):
            raise ValueError("Computation limit reached")
        return self.trace

    def __enter__(self): sys.settrace(self.trace)
    def check(self): pass
    def stop(self): sys.settrace(None)


def node(kind, value, *args): return {"kind": kind, "value": value, "args": list(args)}
x = node("symbol", "x")
cases = {
    "integrate(sin(x^2),x)": node("call", "integrate", node("call", "sin", node("binary", "^", x, node("number", "2"))), x),
    "factor(x^20-1)": node("call", "factor", node("binary", "-", node("binary", "^", x, node("number", "20")), node("number", "1"))),
}
try:
    for name, tree in cases.items():
        payload = json.dumps({"tree": tree, "angle": "RAD", "budget": 30})
        calc_engine.dispatch(payload)
        samples = {LegacyBudget: [], Budget: []}
        expected = None
        # Alternate the guards to reduce order/CPU frequency bias.
        for _ in range(9):
            for guard in (LegacyBudget, Budget):
                calc_engine.Budget = guard
                started = time.perf_counter()
                result = json.loads(calc_engine.dispatch(payload))
                samples[guard].append(time.perf_counter() - started)
                assert result["ok"], result
                if expected is None: expected = result
                assert result == expected, "Guard changed the calculation result"
        old, new = (statistics.median(samples[guard])*1000 for guard in (LegacyBudget, Budget))
        print(f"{name}: trace {old:.2f} ms, monitoring {new:.2f} ms ({(1-new/old)*100:.1f}% less time)")
finally:
    calc_engine.Budget = Budget
