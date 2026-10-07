import json
import pathlib
import subprocess
import sys
import time
import unittest
from unittest.mock import patch

PYTHON = pathlib.Path(__file__).resolve().parents[1] / "app/src/main/python"
sys.path.insert(0, str(PYTHON))
import calc_engine
import script_runner
from calc_runtime import Budget, ExecutionStopped


class Control:
    def __init__(self): self.cancelled = False
    def isCancelled(self): return self.cancelled


class RuntimeTests(unittest.TestCase):
    def test_heavy_and_statistics_deadlines_allow_25_seconds_but_stop_after_60(self):
        number = {"kind": "number", "value": "2"}
        integral = {"kind": "call", "value": "integrate", "args": [number, {"kind": "symbol", "value": "x"}]}
        stats = {"kind": "call", "value": "stats", "args": [{"kind": "list", "args": [number, number]}]}
        nested = {"kind": "binary", "value": "+", "args": [number, integral]}
        for tree, explicit, elapsed, succeeds in (
                (integral, None, 25, True), (integral, 8, 25, True),
                (stats, None, 25, True), (nested, None, 25, True),
                (integral, None, 61, False), (stats, None, 61, False),
                (integral, 2, 3, False), (number, None, 9, False)):
            with self.subTest(tree=tree, budget=explicit, elapsed=elapsed):
                clock = [0]
                original = calc_engine.Engine.build
                def delayed_build(engine, node, *args, **kwargs):
                    clock[0] = elapsed
                    return original(engine, node, *args, **kwargs)
                request = {"tree": tree}
                if explicit is not None: request["budget"] = explicit
                with patch("calc_runtime.time.monotonic", side_effect=lambda: clock[0]), \
                        patch.object(calc_engine.Engine, "build", delayed_build):
                    result = json.loads(calc_engine.dispatch(json.dumps(request)))
                self.assertEqual(succeeds, result["ok"], result)
                if not succeeds: self.assertIn("limit reached", result["error"])

    def test_expired_and_cancelled_short_requests_are_rejected(self):
        payload = json.dumps({"tree": {"kind": "number", "value": "1"}, "budget": -1})
        self.assertIn("limit reached", json.loads(calc_engine.dispatch(payload))["error"])
        control = Control()
        control.cancelled = True
        payload = json.dumps({"tree": {"kind": "number", "value": "1"}})
        self.assertEqual("Calculation cancelled", json.loads(calc_engine.dispatch(payload, control))["error"])
        self.assertTrue(json.loads(calc_engine.dispatch(payload))["ok"])


    def test_input_cancellation_does_not_echo_a_fake_answer(self):
        control = Control()
        class Bridge:
            def request(self, prompt, output):
                control.cancelled = True
                return ""
        result = json.loads(script_runner.run(json.dumps({"source": "print('before'); input('value=')"}), Bridge(), control))
        self.assertFalse(result["ok"])
        self.assertEqual("Calculation cancelled", result["error"])
        self.assertEqual("before\n", result["output"])

    def test_monitoring_tool_is_released_on_success_and_failure(self):
        monitoring = getattr(sys, "monitoring", None)
        if monitoring is None: self.skipTest("Requires Python 3.12+")
        before = [monitoring.get_tool(i) for i in range(6)]
        def work(steps):
            with Budget(1, steps):
                for _ in range(2000): pass
        for steps in (100000, 10):
            try:
                work(steps)
            except ExecutionStopped: pass
            self.assertEqual(before, [monitoring.get_tool(i) for i in range(6)])

    def test_trace_fallback_restores_existing_hook(self):
        def previous(frame, event, arg): return previous
        sys.settrace(previous)
        try:
            with patch.object(sys, "monitoring", None):
                with Budget(1): sum(range(10))
                self.assertIs(previous, sys.gettrace())
        finally: sys.settrace(None)

    def test_loop_guards_and_cooperative_cancellation_in_bounded_subprocesses(self):
        # Each subprocess has a timeout so a broken single-line loop hook cannot hang CI.
        for fallback in (False, True):
            with self.subTest(fallback=fallback):
                program = f"""
import sys, json, threading
sys.path.insert(0, {str(PYTHON)!r})
import calc_runtime, script_runner
if {fallback!r}: sys.monitoring = None
for source in ('while True: pass', 'n=1\\nwhile n: n+=1', 'while True:\\n try: raise ValueError()\\n except Exception: pass'):
    try:
        with calc_runtime.Budget(.02, steps=1000000): exec(source)
    except calc_runtime.ExecutionStopped: pass
    else: raise AssertionError('loop escaped deadline')
class Control:
    cancelled = False
    def isCancelled(self): return self.cancelled
control = Control()
timer = threading.Timer(.02, lambda: setattr(control, 'cancelled', True))
timer.start()
result = json.loads(script_runner.run(json.dumps({{'source': 'while True: pass'}}), control=control))
timer.join()
assert result['error'] == 'Calculation cancelled', result
assert json.loads(script_runner.run(json.dumps({{'source': "print('again')"}})))['output'] == 'again\\n'
"""
                completed = subprocess.run([sys.executable, "-c", program], capture_output=True, text=True, timeout=10)
                self.assertEqual(0, completed.returncode, completed.stderr)


if __name__ == "__main__": unittest.main()
