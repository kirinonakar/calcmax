"""Regularization optimality, forest validation, and public engine contract."""
import json
import math
import pathlib
import random
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "app/src/main/python"))
import sympy as s
from calc_evaluator import Engine
from calc_statistics import fit_regression
from calc_shared import MathError
import calc_engine
import symvacas_catalog
from calc_machine_learning import classification_metrics


def fit(rows, mode, options=None):
    engine = Engine({"precision": 30})
    expression = fit_regression(engine, [[s.sympify(v) for v in row] for row in rows], mode,
                                symvacas_catalog._sympify(options) if options is not None else None)
    return engine, expression


class MachineLearningTests(unittest.TestCase):

    def test_forests_keep_training_and_oob_predictions_separate_and_reproducible(self):
        # No feature can split: every tree predicts its bootstrap positive fraction.
        # Independently reconstruct those bags to verify held-out metrics.
        with self.subTest(scenario='forest_classifier_oob_excludes_each_observations_training_trees'):
            labels = [0, 0, 0, 1, 1, 1]
            count, seed = 40, 17
            engine, _ = fit([[7, y] for y in labels], "randomforestclassifier", [count, 4, seed])
            report = engine.regression_report
            rng = random.Random(seed)
            train, oob = [], [[] for _ in labels]
            for _ in range(count):
                bag = [rng.randrange(len(labels)) for _ in labels]
                probability = sum(labels[i] for i in bag)/len(bag)
                if 0 < probability < 1:
                    rng.sample(range(1), 1)  # one unsplittable feature considered
                train.append(probability)
                for i in range(len(labels)):
                    if i not in bag:
                        oob[i].append(probability)
            train_expected = classification_metrics(labels, [sum(train)/count]*len(labels))
            covered = [i for i, predictions in enumerate(oob) if predictions]
            oob_expected = classification_metrics([labels[i] for i in covered], [sum(oob[i])/len(oob[i]) for i in covered])
            for key in ("auc", "cStatistic", "confusionMatrix", "sensitivity", "specificity", "accuracy"):
                self.assertEqual(report[key], train_expected[key])
                if key in ("auc", "cStatistic"):
                    self.assertAlmostEqual(float(report["oobClassification"][key]), float(oob_expected[key]))
                else:
                    self.assertEqual(report["oobClassification"][key], oob_expected[key])
            self.assertEqual(report["permutationMetric"], "auc")
            self.assertAlmostEqual(report["permutationImportance"][0]["estimate"], 0)
            self.assertNotIn("rSquared", report)
            self.assertNotIn("oobRSquared", report)
        with self.subTest(scenario='forest_auto_classification_and_explicit_regression_and_sparse_oob'):
            rows = [[x, int(x>0)] for x in (-3, -2, -1, 1, 2, 3)]
            classifier, _ = fit(rows, "randomforest", [60, 8, 42])
            report = classifier.regression_report
            self.assertEqual(report["task"], "classification")
            self.assertGreater(float(report["auc"]), .95)
            self.assertEqual(report["confusionMatrix"], [[3, 0], [0, 3]])
            self.assertEqual(report["sensitivity"], 1)
            self.assertEqual(report["specificity"], 1)
            self.assertEqual(report["accuracy"], 1)
            self.assertTrue(all(0 <= float(r["fitted"]) <= 1 for r in report["residuals"]))
            regressor, _ = fit(rows, "randomforestregressor", [60, 8, 42])
            self.assertEqual(regressor.regression_report["task"], "regression")
            self.assertNotIn("roc", regressor.regression_report)
            sparse, _ = fit([[0, 0], [1, 1]], "randomforestclassifier", [1, 2, 0])
            self.assertIsNone(sparse.regression_report["oobAuc"])
            self.assertEqual(sparse.regression_report["oobClassification"]["roc"], [])
            self.assertTrue(any("both classes" in message for message in sparse.regression_report["warnings"]))
            with self.assertRaises(MathError):
                fit([[0, 5], [1, 6]], "randomforestclassifier")
            with self.assertRaises(MathError):
                fit([[0, 1], [1, 1]], "randomforestclassifier")
        with self.subTest(scenario='forest_reproducibility_importance_and_oob_predictions'):
            rows = [[x, 7, x*x] for x in range(-12, 13)]
            engine, _ = fit(rows, "randomforest", [60, 8, 42])
            repeated, _ = fit(rows, "randomforest", [60, 8, 42])
            self.assertEqual(engine.regression_report, repeated.regression_report)
            report = engine.regression_report
            self.assertGreater(float(report["rSquared"]), .9)
            self.assertGreater(float(report["oobRSquared"]), .7)
            self.assertEqual(report["oobN"], len(rows))
            self.assertAlmostEqual(report["featureImportance"][0]["estimate"], 1)
            self.assertEqual(report["featureImportance"][1]["estimate"], 0)
            self.assertGreater(report["permutationImportance"][0]["estimate"], .5)
            self.assertAlmostEqual(report["permutationImportance"][1]["estimate"], 0)
            self.assertTrue(math.isfinite(engine.regression_predict([100, 7])))


    def test_orthogonal_solution_matches_closed_form_for_all_linear_penalties(self):
        # Unit-SD, orthogonal predictors; soft threshold then divide by 1+L2.
        with self.subTest(scenario='orthogonal_solution_matches_closed_form_for_all_linear_penalties'):
            rows = [[x, z, 4+2*x+.05*z] for x in (-1, 1) for z in (-1, 1)]
            for mode, options, ratio in [("ridge", .1, 0), ("lasso", .1, 1), ("elasticnet", [.1, .5], .5)]:
                engine, expression = fit(rows, mode, options)
                beta = [float(v) for _, v in engine.regression_parameters]
                expected = [4, (2-.1*ratio)/(1+.1*(1-ratio)), max(.05-.1*ratio, 0)/(1+.1*(1-ratio))]
                for actual, target in zip(beta, expected):
                    self.assertAlmostEqual(actual, target, places=7)
                self.assertIsNone(engine.regression_report["df"])
                self.assertNotIn("se", engine.regression_report["coefficients"][0])
                self.assertEqual(len(engine.regression_report["residuals"]), 4)
        with self.subTest(scenario='elastic_net_endpoints_match_ridge_and_lasso'):
            rows = [[x, x*x, 2*x+math.sin(x)] for x in range(-5, 6)]
            for mode, ratio in [("ridge", 0), ("lasso", 1)]:
                a, _ = fit(rows, mode, .2)
                b, _ = fit(rows, "elasticnet", [.2, ratio])
                self.assertEqual(a.regression_parameters, b.regression_parameters)
        with self.subTest(scenario='scaling_constant_columns_collinearity_and_more_predictors_than_rows'):
            rows = [[x, x, 7, 1+2*x] for x in (-1, 1)]
            engine, _ = fit(rows, "lasso", .1)
            self.assertAlmostEqual(float(engine.regression_parameters[3][1]), 0)
            scaled, _ = fit([[100*x, 100*z, c, y] for x, z, c, y in rows], "lasso", .1)
            for a, b in zip(engine.regression_report["residuals"], scaled.regression_report["residuals"]):
                self.assertAlmostEqual(float(a["fitted"]), float(b["fitted"]))


    def test_penalized_logistic_kkt_and_separated_inputs(self):
        with self.subTest(scenario='penalized_logistic_kkt_and_separated_inputs'):
            rows = [[x, x*x, int(x>0)] for x in (-3, -2, -1, 1, 2, 3)]
            for mode, ratio in [("logisticridge", 0), ("logisticlasso", 1), ("logisticelasticnet", .5)]:
                options = [.1, ratio] if mode.endswith("elasticnet") else .1
                engine, expression = fit(rows, mode, options)
                report = engine.regression_report
                self.assertAlmostEqual(float(report["auc"]), 1)
                self.assertEqual(report["accuracy"], 1)
                self.assertTrue(math.isfinite(report["logLoss"]))
                self.assertEqual(report['influenceMethod'],'glm-penalized-approximate')
                self.assertTrue(all(0 <= float(r['leverage']) <= 1 and float(r['cook']) >= 0 for r in report['residuals']))
                coefficients = [float(v) for _, v in engine.regression_parameters]
                predictions = [float(r["fitted"]) for r in report["residuals"]]
                self.assertAlmostEqual(sum(row[-1]-q for row, q in zip(rows, predictions)), 0, places=6)
                for j in range(2):
                    mean = sum(row[j] for row in rows)/len(rows)
                    scale = math.sqrt(sum((row[j]-mean)**2 for row in rows)/len(rows))
                    beta = coefficients[j+1]*scale
                    gradient = sum((row[j]-mean)/scale*(row[-1]-q) for row, q in zip(rows, predictions))/len(rows)-.1*(1-ratio)*beta
                    self.assertLess(abs(gradient-math.copysign(.1*ratio, beta)) if beta else max(abs(gradient)-.1*ratio, 0), 1e-7)
        with self.subTest(scenario='regularized_logistic_or_uses_original_units_and_zero_coefficients_give_one'):
            rows = [[x, 7, int(x>0)] for x in (-3, -2, -1, 1, 2, 3)]
            for mode in ("logisticridge", "logisticlasso", "logisticelasticnet"):
                options = [.1, .5] if mode.endswith("elasticnet") else .1
                engine, _ = fit(rows, mode, options)
                scaled, _ = fit([[100*x, z, y] for x, z, y in rows], mode, options)
                coefficients = engine.regression_report["coefficients"]
                self.assertNotIn("oddsRatio", coefficients[0])
                for coefficient in coefficients[1:]:
                    self.assertAlmostEqual(float(coefficient["oddsRatio"]), math.exp(float(coefficient["estimate"])))
                    self.assertNotIn("oddsLow", coefficient)
                    self.assertNotIn("oddsHigh", coefficient)
                self.assertEqual(float(coefficients[2]["oddsRatio"]), 1)
                scaled_coefficient = scaled.regression_report["coefficients"][1]
                self.assertAlmostEqual(math.log(float(scaled_coefficient["oddsRatio"]))*100,
                                       math.log(float(coefficients[1]["oddsRatio"])))
            # Strong LASSO shrinks an otherwise informative predictor to zero.
            shrunk, _ = fit(rows, "logisticlasso", 1)
            self.assertEqual(float(shrunk.regression_report["coefficients"][1]["oddsRatio"]), 1)


    def test_invalid_options_and_data(self):
        for mode, options in [("lasso", 0), ("ridge", -1), ("elasticnet", [.1, 2]),
                              ("randomforest", [0, 10, 0]), ("randomforest", [10, 21, 0]),
                              ("randomforest", [10, 10, .5])]:
            with self.subTest(mode=mode, options=options), self.assertRaises(MathError):
                fit([[1, 2], [2, 3]], mode, options)
        with self.assertRaises(MathError):
            fit([[1, 2], [2, 2]], "logisticridge", .1)

    def test_public_dispatch_curve_non_reuse_and_cancellation(self):
        def node(value):
            return {"kind": "list", "args": [node(v) for v in value]} if isinstance(value, list) else {"kind": "number", "value": str(value)}
        rows = [[x, x*x] for x in range(20)]
        request = {"tree": {"kind": "call", "value": "regression", "args": [node(rows),
                   {"kind": "symbol", "value": "randomforest"}, node([20, 5, 0])]}}
        result = json.loads(calc_engine.dispatch(json.dumps(request)))
        self.assertTrue(result["ok"], result.get("error"))
        self.assertGreater(len(result["curve"]), 100)
        self.assertFalse(result["reusable"])
        self.assertNotIn("resultAst", result)
        class Control:
            calls = 0
            def isCancelled(self):
                self.calls += 1
                return self.calls > 5
        stopped = json.loads(calc_engine.dispatch(json.dumps(request), Control()))
        self.assertFalse(stopped["ok"])
        self.assertIn("cancel", stopped["error"].lower())


if __name__ == "__main__":
    unittest.main()
