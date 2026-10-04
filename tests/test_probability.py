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

    def test_all_eight_user_examples(self):
        for example, expected, fraction in zip(SCHEMA["examples"], [0.5,0.5,1/6,1/13,0.3125,0.875,1/3,1/45], ["1/2","1/2","1/6","1/13","5/16","7/8","1/3","1/45"]):
            with self.subTest(example=example["id"]):
                result=json.loads(calc_engine.dispatch(json.dumps({"action":"probability",**example})))
                self.assertTrue(result["ok"],result)
                self.assertAlmostEqual(float(result["value"]),expected,places=14)
                self.assertEqual(result["fraction"],fraction)
                self.assertAlmostEqual(float(result["percent"][:-1]),expected*100,places=12)

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

    def test_known_continuous_and_discrete_values(self):
        self.assertAlmostEqual(self.value(mu=0,sigma=1,x=0),0.5)
        self.assertAlmostEqual(self.value(operation="between",mu=0,sigma=1,lower="-1.96",upper="1.96"),0.950004209703559,places=13)
        self.assertAlmostEqual(self.value(distribution="uniform",a=0,b=10,x=4),0.4)
        self.assertAlmostEqual(self.value(distribution="exponential",rate=2,x=1),1-math.exp(-2))
        self.assertAlmostEqual(self.value(distribution="t",df=1,x=1),0.75)
        self.assertAlmostEqual(self.value(distribution="chi2",df=2,x=2),1-math.exp(-1))
        self.assertAlmostEqual(self.value(distribution="f",df1=1,df2=1,x=1),0.5)
        self.assertAlmostEqual(self.value(distribution="poisson",operation="eq",rate=3,x=2),math.exp(-3)*9/2)
        self.assertAlmostEqual(self.value(distribution="geometric",operation="ge",p="1/4",x=3),0.75**2)
        self.assertAlmostEqual(self.value(distribution="hypergeometric",operation="eq",population=10,successes=2,draws=2,x=2),1/45)

    def test_quantiles(self):
        self.assertAlmostEqual(self.value(operation="quantile",mu=0,sigma=1,q="97.5%"),1.959963984540054,places=13)
        self.assertAlmostEqual(self.value(distribution="t",operation="quantile",df=10,q="97.5%"),2.228138851986275,places=12)
        for distribution,params in [("binomial",dict(n=10,p="0.5")),("poisson",dict(rate=3)),("geometric",dict(p="0.25")),("hypergeometric",dict(population=50,successes=10,draws=5))]:
            for q in [0.01,0.5,0.99]:
                result=self.value(distribution=distribution,operation="quantile",q=q,**params)
                self.assertGreaterEqual(self.value(distribution=distribution,x=result,**params)+1e-14,q)
                self.assertLess(self.value(distribution=distribution,x=result-1,**params),q)
        self.assertEqual(self.run_probability(operation="quantile",mu=0,sigma=1,q=0)["value"],"−∞")
        self.assertEqual(self.run_probability(operation="quantile",mu=0,sigma=1,q=1)["value"],"∞")

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

    def test_bayes_events_and_counting(self):
        self.assertAlmostEqual(self.value(category="bayes",operation="posterior",prior="1%",likelihood="99%",falsePositive="5%"),1/6)
        self.assertAlmostEqual(self.value(category="events",operation="conditional",pa="4/52",pb="12/52",intersection="4/52"),1/3)
        self.assertEqual(self.value(category="events",operation="union",pa="0.4",pb="0.5",intersection="0.2"),0.7)
        self.assertEqual(self.value(category="counting",operation="combination",n=45,r=6),8145060)
        self.assertEqual(self.value(category="counting",operation="permutation",n=10,r=3),720)
        self.assertEqual(self.value(category="counting",operation="replacement",n=3,r=4),81)
        self.assertEqual(self.value(category="counting",operation="multicombination",n=3,r=4),15)

    def test_invalid_inputs_are_explicit_errors(self):
        requests=[dict(mu=0,sigma=0,x=1),dict(mu=0,sigma=-1,x=1),dict(mu="nan",sigma=1,x=1),dict(mu="1/0",sigma=1,x=1),dict(mu="1e1000000000",sigma=1,x=1),dict(mu=0,sigma=1,operation="between",lower=2,upper=1),
                  dict(distribution="binomial",n="5.5",p="0.5",x=3),dict(distribution="binomial",n=5,p="110%",x=3),dict(distribution="geometric",p=0,x=1),
                  dict(distribution="hypergeometric",population=10,successes=20,draws=2,x=1),dict(category="events",operation="conditional",pa="0.2",pb=0,intersection=0),
                  dict(category="events",operation="union",pa="0.2",pb="0.3",intersection="0.5"),dict(category="bayes",operation="posterior",prior=0,likelihood=1,falsePositive=0),
                  dict(category="basic",operation="ratio",favorable=3,total=2),dict(category="draw",operation="allMarked",population=10,draws=11,marked=2)]
        for request in requests:
            with self.subTest(request=request):self.assertFalse(self.run_probability(**request)["ok"])

    def test_density_is_not_reported_as_percentage(self):
        result=self.run_probability(operation="density",mu=0,sigma="0.1",x=0)
        self.assertTrue(result["ok"],result)
        self.assertGreater(float(result["value"]),1)
        self.assertFalse(result["isProbability"])
        self.assertNotIn("percent",result)

    def test_large_binomial_and_exact_event_consistency(self):
        probability=self.value(distribution="binomial",n=100000,p="0.5",x=50000)
        # Symmetry: CDF at the midpoint is 1/2 plus half the midpoint mass.
        midpoint=math.exp(math.lgamma(100001)-2*math.lgamma(50001)-100000*math.log(2))
        self.assertAlmostEqual(probability,0.5+midpoint/2,places=12)
        self.assertEqual(self.value(category="events",operation="union",pa="0.7",pb="0.6",intersection="0.3"),1)


if __name__ == "__main__":unittest.main()
