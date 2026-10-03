"""Shared Android/Pyodide symbolic answer application."""
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


class AnswerFunctionTests(unittest.TestCase):
    def test_indefinite_integral_answers_bind_x_and_preserve_the_integration_constant(self):
        x = node("symbol", "x")
        for integrand, expected in [(x, "2 + C"), (node("number", 0), "C")]:
            result = evaluate(node("call", "integrate", integrand, x), variables={"x": node("number", 99)})
            self.assertTrue(result["ok"], result)
            self.assertEqual(["x"], result["resultAst"]["parameters"])
            applied = evaluate(node("call", "Ans", node("number", 2)), variables={"Ans": result["resultAst"], "C": node("number", 7)})
            self.assertEqual(expected, applied["exact"], applied)

    def test_definite_integral_numeric_answers_are_not_implicitly_functions(self):
        x = node("symbol", "x")
        result = evaluate(node("call", "integrate", x, x, node("number", 0), node("number", 2)))
        self.assertEqual("2", result["exact"])
        self.assertNotIn("parameters", result["resultAst"])

    def test_derivative_answer_calls_ignore_stored_x_and_keep_exact_values(self):
        x = node("symbol", "x")
        body = node("binary", "^", x, node("number", 3))
        derivative = node("call", "diff", node("call", "f", x), x)
        result = evaluate(derivative, functions={"f": {"parameters": ["x"], "body": body}},
                          variables={"x": node("number", 99)})
        self.assertTrue(result["ok"], result)
        self.assertEqual(["x"], result["resultAst"]["parameters"])
        for argument, expected in [(1, "3"), (2, "12")]:
            applied = evaluate(node("call", "Ans", node("number", argument)),
                               variables={"Ans": result["resultAst"], "x": node("number", 99)})
            self.assertEqual(expected, applied["exact"], applied)
        symbolic = evaluate(node("call", "Ans", node("symbol", "t")), variables={"Ans": result["resultAst"]})
        self.assertEqual("3*t**2", symbolic["exact"])

    def test_constant_derivative_and_copied_answer_remain_callable(self):
        x = node("symbol", "x")
        derivative = evaluate(node("call", "diff", node("binary", "*", node("number", 2), x), x))
        copied = evaluate(node("symbol", "Ans"), variables={"Ans": derivative["resultAst"]})
        applied = evaluate(node("call", "Ans", node("number", 123)), variables={"Ans": copied["resultAst"]})
        self.assertEqual("2", applied["exact"])
        frozen_copy = evaluate(copied["resultAst"])
        self.assertEqual(["x"], frozen_copy["resultAst"]["parameters"])

    def test_constant_derivatives_keep_original_excluded_values(self):
        x = node("symbol", "x")
        result = evaluate(node("call", "diff", node("binary", "/", x, x), x))
        self.assertEqual("0", result["exact"])
        self.assertEqual("0", evaluate(node("call", "Ans", node("number", 2)), variables={"Ans": result["resultAst"]})["exact"])
        self.assertFalse(evaluate(node("call", "Ans", node("number", 0)), variables={"Ans": result["resultAst"]})["ok"])

    def test_univariate_answers_apply_without_metadata_and_check_domains(self):
        x = node("symbol", "x")
        answer = evaluate(node("binary", "/", node("number", 1), x))["resultAst"]
        self.assertEqual("1/2", evaluate(node("call", "Ans", node("number", 2)), variables={"Ans": answer})["exact"])
        self.assertFalse(evaluate(node("call", "Ans", node("number", 0)), variables={"Ans": answer})["ok"])

    def test_saved_answer_calls_and_radian_snapshots(self):
        answer = evaluate(node("call", "diff", node("call", "sin", node("symbol", "x")), node("symbol", "x")))["resultAst"]
        applied = evaluate(node("answer_call", "", answer, node("symbol", "pi")),
                           variables={"Ans": node("number", 42)}, angle="DEG")
        self.assertEqual("-1", applied["exact"], applied)

    def test_missing_numeric_multivariate_and_wrong_arity_answers_are_rejected(self):
        for variables in [{}, {"Ans": node("number", 42)},
                          {"Ans": node("binary", "+", node("snapshot_symbol", "x"), node("snapshot_symbol", "y"))}]:
            self.assertFalse(evaluate(node("call", "Ans", node("number", 1)), variables=variables)["ok"])
        answer = evaluate(node("binary", "^", node("symbol", "x"), node("number", 2)))["resultAst"]
        for arguments in [[], [node("number", 1), node("number", 2)]]:
            self.assertFalse(evaluate(node("call", "Ans", *arguments), variables={"Ans": answer})["ok"])
