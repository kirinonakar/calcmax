"""Logarithm solve regressions for the shared Android/Web engine."""
import json
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "app/src/main/python"))
import calc_engine


def node(kind, value="", *args):
    return {"kind": kind, "value": str(value), "args": list(args)}


def evaluate(tree, **options):
    return json.loads(calc_engine.dispatch(json.dumps({"tree": tree, **options})))


class LogSolveTests(unittest.TestCase):
    def equation(self):
        x = node("symbol", "x")
        left = node("call", "log", node("binary", "-", x, node("number", 3)), node("number", 2))
        right = node("call", "log", node("binary", "-", node("binary", "*", node("number", 3), x), node("number", 5)), node("number", 4))
        return node("relation", "=", left, right)

    def test_shifted_log_equation_returns_only_the_verified_root(self):
        x = node("symbol", "x")
        for assumptions in ({}, {"x": ["real"]}, {"x": ["positive"]}, {"x": ["integer"]}):
            for explicit in (False, True):
                with self.subTest(assumptions=assumptions, explicit=explicit):
                    args = [self.equation()] + ([x] if explicit else [])
                    result = evaluate(node("call", "solve", *args), assumptions=assumptions, variables={"x": node("number", 99)})
                    self.assertTrue(result["ok"], result)
                    # Squaring gives 2 and 7, but 2 fails the original logarithms.
                    self.assertEqual("{7}", result["exact"])
                    self.assertEqual("", result["note"])
                    self.assertEqual("set", result["tree"]["kind"])
                    reused = evaluate(node("symbol", "Ans"), variables={"Ans": result["resultAst"]})
                    self.assertEqual("{7}", reused["exact"])

    def test_log_roots_respect_assumptions_and_preserved_domain_guards(self):
        x = node("symbol", "x")
        result = evaluate(node("call", "solve", self.equation(), x), assumptions={"x": ["negative"]})
        self.assertEqual("EmptySet", result.get("exact"), result)
        excluded = node("restricted", "", self.equation(), node("relation", "!=", x, node("number", 7)))
        result = evaluate(node("call", "solve", excluded, x))
        self.assertEqual("EmptySet", result.get("exact"), result)

    def test_general_transcendental_log_equations_stay_unresolved(self):
        x = node("symbol", "x")
        equation = node("relation", "=", node("call", "ln", x), x)
        result = evaluate(node("call", "solve", equation, x))
        self.assertTrue(result["ok"], result)
        self.assertIn("ConditionSet", result["exact"])
        self.assertIn("Symbolic solution not found", result["note"])


if __name__ == "__main__":
    unittest.main()
