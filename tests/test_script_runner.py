import json
import pathlib
import re
import sys
import unittest

sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1] / "app/src/main/python"))
import script_runner
import calcmax_catalog


class ScriptRunnerTests(unittest.TestCase):
    def test_every_catalog_name_has_a_python_callable(self):
        source=(pathlib.Path(__file__).resolve().parents[1] / "app/src/main/java/com/example/calcmax/ui/Catalog.kt").read_text(encoding="utf-8")
        names=set(re.findall(r'"([A-Za-z][A-Za-z0-9_]*)\(',source))
        self.assertGreater(len(names),70)
        for name in names:
            with self.subTest(name=name): self.assertTrue(callable(getattr(calcmax_catalog,name)))

    def test_import_and_output(self):
        result=json.loads(script_runner.run(json.dumps({"source":"import math\nprint(math.sqrt(9))","filename":"test.py"})))
        self.assertTrue(result["ok"],result)
        self.assertEqual(result["output"],"3.0\n")

    def test_input_prompt_and_float_conversion(self):
        class Bridge:
            def __init__(self): self.requests = []
            def request(self, prompt, output):
                self.requests.append((prompt, output))
                return "2.5"
        bridge = Bridge()
        result = json.loads(script_runner.run(json.dumps({"source":"a=float(input('a='))\nprint(a*2)"}), bridge))
        self.assertTrue(result["ok"], result)
        self.assertEqual(bridge.requests, [("a=", "")])
        self.assertEqual(result["output"], "a=2.5\n5.0\n")

    def test_traceback_contains_script_name(self):
        result=json.loads(script_runner.run(json.dumps({"source":"raise ValueError('bad')","filename":"example.py"})))
        self.assertFalse(result["ok"])
        self.assertIn("example.py",result["error"])
        self.assertIn("ValueError: bad",result["error"])

    def test_output_is_bounded(self):
        result=json.loads(script_runner.run(json.dumps({"source":"print('x' * 50000)"})))
        self.assertTrue(result["ok"])
        self.assertEqual(len(result["output"]),40000)

    def test_catalog_functions_have_python_definitions(self):
        source=("import calcmax_catalog as calc\n"
                "print(calc.mean([1, 2, 3]))\n"
                "print(calc.factorint(12))\n"
                "print(calc.diff(calc.x**3, calc.x))\n"
                "print(calc.convert(1, calc.m, calc.cm))")
        result=json.loads(script_runner.run(json.dumps({"source":source})))
        self.assertTrue(result["ok"],result)
        self.assertEqual(result["output"],"2\n[[2, 2], [3, 1]]\n3*x**2\n100\n")

    def test_custom_catalog_function_uses_saved_definition(self):
        definition={"f":{"parameters":["x"],"body":{"kind":"binary","value":"+","args":[{"kind":"symbol","value":"x"},{"kind":"number","value":"1"}]}}}
        result=json.loads(script_runner.run(json.dumps({"source":"import calcmax_catalog as calc\nprint(calc.f(3))","functions":definition})))
        self.assertTrue(result["ok"],result)
        self.assertEqual(result["output"],"4\n")


if __name__ == "__main__": unittest.main()
