import json
import pathlib
import sys
import unittest

sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1] / "app/src/main/python"))
import script_runner


class ScriptRunnerTests(unittest.TestCase):
    def test_import_and_output(self):
        result=json.loads(script_runner.run(json.dumps({"source":"import math\nprint(math.sqrt(9))","filename":"test.py"})))
        self.assertTrue(result["ok"],result)
        self.assertEqual(result["output"],"3.0\n")

    def test_traceback_contains_script_name(self):
        result=json.loads(script_runner.run(json.dumps({"source":"raise ValueError('bad')","filename":"example.py"})))
        self.assertFalse(result["ok"])
        self.assertIn("example.py",result["error"])
        self.assertIn("ValueError: bad",result["error"])

    def test_output_is_bounded(self):
        result=json.loads(script_runner.run(json.dumps({"source":"print('x' * 50000)"})))
        self.assertTrue(result["ok"])
        self.assertEqual(len(result["output"]),40000)


if __name__ == "__main__": unittest.main()
