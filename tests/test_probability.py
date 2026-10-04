import itertools
import json
import math
import pathlib
import sys
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT / "app/src/main/python"))
import calc_engine

SCHEMA = json.loads((ROOT / "app/src/main/assets/probability.json").read_text(encoding="utf-8"))


class ProbabilityTests(unittest.TestCase):
    def run_probability(self, category="distribution", distribution="normal", operation="le", **values):
        return json.loads(calc_engine.dispatch(json.dumps(dict(action="probability",category=category,distribution=distribution,operation=operation,values=values))))

    def value(self, **request):
        response = self.run_probability(**request)
        self.assertTrue(response["ok"],response)
        return float(response["value"])


    def test_every_distribution_and_operation_has_valid_defaults(self):
        for distribution in SCHEMA["distributions"]:
            for op in SCHEMA["operations"]:
                if op.get("discrete") and not distribution.get("discrete"):continue
                if op.get("continuous") and distribution.get("discrete"):continue
                values={f[0]:f[3] for f in distribution["fields"]+op["fields"]}
                result=self.run_probability(distribution=distribution["id"],operation=op["id"],**values)
                with self.subTest(distribution=distribution["id"],operation=op["id"]):
                    self.assertTrue(result["ok"],result)
                    if result["isProbability"]:self.assertTrue(0<=float(result["value"])<=1)

    def test_binomial_integer_and_noninteger_boundaries(self):
        for x in [0,1,2,2.3,3,5,6,-1]:
            probabilities=[math.comb(5,k)/32 for k in range(6)]
            for operation,predicate in [("le",lambda k:k<=x),("lt",lambda k:k<x),("ge",lambda k:k>=x),("gt",lambda k:k>x),("eq",lambda k:k==x)]:
                self.assertAlmostEqual(self.value(distribution="binomial",operation=operation,n=5,p="50%",x=x),sum(v for k,v in enumerate(probabilities) if predicate(k)),places=14)
        self.assertAlmostEqual(self.value(distribution="binomial",operation="between",n=5,p="1/2",lower="1.2",upper="3.8"),0.625)

    def test_degenerate_distributions(self):
        for distribution,values,point in [("binomial",dict(n=5,p=0),0),("binomial",dict(n=5,p=1),5),("binomial",dict(n=0,p="0.5"),0),("poisson",dict(rate=0),0),("geometric",dict(p=1),1),("hypergeometric",dict(population=1,successes=1,draws=1),1)]:
            for op,expected in [("eq",1),("le",1),("lt",0),("ge",1),("gt",0)]:
                self.assertEqual(self.value(distribution=distribution,operation=op,x=point,**values),expected)
            self.assertEqual(self.value(distribution=distribution,operation="quantile",q="0.5",**values),point)


    def test_extreme_tails_keep_small_probabilities(self):
        tail=self.value(operation="gt",mu=0,sigma=1,x=10)
        self.assertAlmostEqual(tail/7.619853024160526e-24,1,places=13)
        interval=self.value(operation="between",mu=0,sigma=1,lower=9,upper=10)
        self.assertGreater(interval,1e-19)
        self.assertLess(interval,2e-19)
        self.assertAlmostEqual(self.value(category="repeat",operation="atLeastOne",n=10,p="1e-30")/1e-29,1,places=14)
        self.assertEqual(self.value(operation="between",mu=0,sigma=1,lower="-inf",upper="inf"),1)

    def test_dice_sum_matches_enumerated_outcomes(self):
        rolls=list(itertools.product(range(1,5),repeat=3))
        for x in range(1,14):
            for operation,predicate in [("eq",lambda s:s==x),("le",lambda s:s<=x),("ge",lambda s:s>=x)]:
                expected=sum(predicate(sum(roll)) for roll in rolls)/len(rolls)
                self.assertEqual(self.value(category="dice",operation=operation,dice=3,sides=4,x=x),expected)


    def test_invalid_inputs_are_explicit_errors(self):
        requests=[dict(mu=0,sigma=0,x=1),dict(mu=0,sigma=-1,x=1),dict(mu="nan",sigma=1,x=1),dict(mu="1/0",sigma=1,x=1),dict(mu="1e1000000000",sigma=1,x=1),dict(mu=0,sigma=1,operation="between",lower=2,upper=1),
                  dict(distribution="binomial",n="5.5",p="0.5",x=3),dict(distribution="binomial",n=5,p="110%",x=3),dict(distribution="geometric",p=0,x=1),
                  dict(distribution="hypergeometric",population=10,successes=20,draws=2,x=1),dict(category="events",operation="conditional",pa="0.2",pb=0,intersection=0),
                  dict(category="events",operation="union",pa="0.2",pb="0.3",intersection="0.5"),dict(category="bayes",operation="posterior",prior=0,likelihood=1,falsePositive=0),
                  dict(category="basic",operation="ratio",favorable=3,total=2),dict(category="draw",operation="allMarked",population=10,draws=11,marked=2)]
        for request in requests:
            with self.subTest(request=request):self.assertFalse(self.run_probability(**request)["ok"])


    def test_new_quantiles_preserve_accuracy_across_scales(self):
        for kind,params in [("gamma",dict(shape=2,scale="1e-60")),("gamma",dict(shape="0.1",scale="1e60")),
                            ("weibull",dict(shape=2,scale="1e-60")),("beta",dict(alpha="0.01",beta=3)),
                            ("lognormal",dict(mu=-200,sigma=1)),("lognormal",dict(mu=0,sigma=20))]:
            for q in ["0.001","0.5","0.999"]:
                with self.subTest(distribution=kind,params=params,q=q):
                    result=self.run_probability(distribution=kind,operation="quantile",q=q,**params)
                    self.assertTrue(result["ok"],result)
                    self.assertAlmostEqual(self.value(distribution=kind,x=result["value"],**params),float(q),places=14)

    def test_draw_operations_match_enumeration_and_hypergeometric(self):
        for population in range(1,7):
            for marked in range(population+1):
                for draws in range(population+1):
                    counts=[sum(item<marked for item in choice) for choice in itertools.combinations(range(population),draws)]
                    for op,distribution_op in [("exactly","eq"),("atLeast","ge"),("atMost","le"),("atLeastOne","ge"),("allMarked","eq")]:
                        for k in ([1] if op=="atLeastOne" else [marked] if op=="allMarked" else range(marked+2)):
                            expected=sum(x==k if distribution_op=="eq" else x>=k if distribution_op=="ge" else x<=k for x in counts)/len(counts)
                            result=self.run_probability(category="draw",operation=op,population=population,marked=marked,draws=draws,k=k)
                            self.assertTrue(result["ok"],result)
                            self.assertEqual(float(result["value"]),expected)
                            self.assertEqual(float(result["value"]),self.value(distribution="hypergeometric",operation=distribution_op,population=population,successes=marked,draws=draws,x=k))
        for op,distribution_op,k in [("exactly","eq",3),("atLeast","ge",3),("atMost","le",3),("atLeastOne","ge",1)]:
            self.assertEqual(self.value(category="draw",operation=op,population=100,marked=10,draws=20,k=k),self.value(distribution="hypergeometric",operation=distribution_op,population=100,successes=10,draws=20,x=k))


    def test_normal_parameter_solver_round_trips_both_tails(self):
        from fractions import Fraction
        for operation in ("muLe","muGe","sigmaLe","sigmaGe"):
            for q in ("0.95","5%","1/2","1e-60"):
                if operation.startswith("sigma") and q == "1/2": continue
                known = dict(sigma=10) if operation.startswith("mu") else dict(mu=60)
                # Choose the side of μ that yields a positive σ.
                expected=float(Fraction(q.rstrip('%')))/(100 if q.endswith('%') else 1)
                right = (expected > .5) == operation.endswith("Le")
                x = 80 if operation.startswith("mu") or right else 40
                result=self.run_probability(category="normalSolver",operation=operation,x=x,q=q,**known)
                self.assertTrue(result["ok"],result)
                self.assertFalse(result["isProbability"])
                params={d['label']:d['value'] for d in result['details']}
                actual=self.value(operation='le' if operation.endswith('Le') else 'ge',mu=params['μ'],sigma=params['σ'],x=x)
                self.assertAlmostEqual(actual/expected,1,places=13)
        result=self.run_probability(category="normalSolver",operation="muLe",x=80,q="0.95",sigma=10)
        self.assertAlmostEqual(float(result['value']),63.5514637304853,places=12)
        for params in [dict(mu=60,x=60,q='0.5'),dict(mu=60,x=80,q='0.5'),dict(mu=60,x=40,q='0.95'),dict(mu=60,x=80,q=0),dict(mu=60,x=80,q=1)]:
            self.assertFalse(self.run_probability(category='normalSolver',operation='sigmaLe',**params)['ok'])
        self.assertFalse(self.run_probability(category='normalSolver',operation='muLe',x=80,q='.95',sigma=0)['ok'])


    def test_preview_numerical_limits_do_not_discard_a_valid_answer(self):
        result=self.run_probability(distribution='f',df1=1,df2='.001',x=1)
        self.assertTrue(result['ok'],result)
        self.assertTrue(0<float(result['value'])<1)
        self.assertNotIn('plot',result)


if __name__ == "__main__":unittest.main()
