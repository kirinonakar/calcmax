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

    def test_new_continuous_values_and_moments(self):
        cases = [
            ("gamma",dict(shape=2,scale=3),3,1-2/math.e,1/(3*math.e),6,3*math.sqrt(2)),
            ("beta",dict(alpha=2,beta=3),0.5,0.6875,1.5,0.4,0.2),
            ("lognormal",dict(mu=0,sigma=1),1,0.5,1/math.sqrt(2*math.pi),math.exp(0.5),math.sqrt((math.e-1)*math.e)),
            ("weibull",dict(shape=2,scale=3),3,1-1/math.e,2/(3*math.e),3*math.sqrt(math.pi)/2,3*math.sqrt(1-math.pi/4)),
        ]
        for kind, params, x, cumulative, density, mean, sd in cases:
            with self.subTest(distribution=kind):
                for op in ("le","lt"):
                    self.assertAlmostEqual(self.value(distribution=kind,operation=op,x=x,**params),cumulative,places=14)
                for op in ("ge","gt"):
                    self.assertAlmostEqual(self.value(distribution=kind,operation=op,x=x,**params),1-cumulative,places=14)
                result=self.run_probability(distribution=kind,operation="density",x=x,**params)
                self.assertTrue(result["ok"],result)
                self.assertFalse(result["isProbability"])
                self.assertNotIn("percent",result)
                self.assertAlmostEqual(float(result["value"]),density,places=14)
                details={d["label"]:float(d["value"]) for d in result["details"]}
                self.assertAlmostEqual(details["E[X]"],mean,places=14)
                self.assertAlmostEqual(details["SD[X]"],sd,places=14)
                self.assertAlmostEqual(self.value(distribution=kind,operation="between",lower=0,upper=x,**params),cumulative,places=14)

    def test_new_support_and_endpoint_densities(self):
        cases=[("gamma",dict(shape=0.5,scale=2),0,"∞"),
               ("gamma",dict(shape=1,scale=2),0,"0.5"),
               ("gamma",dict(shape=2,scale=2),0,"0.0"),
               ("beta",dict(alpha=0.5,beta=2),0,"∞"),
               ("beta",dict(alpha=2,beta=0.5),1,"∞"),
               ("beta",dict(alpha=1,beta=3),0,"3.0"),
               ("beta",dict(alpha=3,beta=1),1,"3.0"),
               ("beta",dict(alpha=2,beta=2),0,"0.0"),
               ("lognormal",dict(mu=0,sigma=1),0,"0.0"),
               ("weibull",dict(shape=0.5,scale=2),0,"∞"),
               ("weibull",dict(shape=1,scale=2),0,"0.5")]
        for kind,params,x,expected in cases:
            with self.subTest(distribution=kind,params=params):
                result=self.run_probability(distribution=kind,operation="density",x=x,**params)
                self.assertTrue(result["ok"],result)
                self.assertEqual(result["value"],expected)
                for point in [-1,"-inf"]:
                    self.assertEqual(self.value(distribution=kind,x=point,**params),0)
                    self.assertEqual(self.value(distribution=kind,operation="ge",x=point,**params),1)
                self.assertEqual(self.value(distribution=kind,x="inf",**params),1)
                self.assertEqual(self.value(distribution=kind,operation="gt",x="inf",**params),0)

    def test_new_quantiles_and_small_tails(self):
        cases=[("gamma",dict(shape=2,scale=3)),("beta",dict(alpha=0.5,beta=3)),
               ("lognormal",dict(mu=1,sigma=0.7)),("weibull",dict(shape=0.7,scale=3))]
        for kind,params in cases:
            for q in ["0.001","0.5","0.999"]:
                with self.subTest(distribution=kind,q=q):
                    x=self.run_probability(distribution=kind,operation="quantile",q=q,**params)
                    self.assertTrue(x["ok"],x)
                    self.assertAlmostEqual(self.value(distribution=kind,x=x["value"],**params),float(q),places=14)
            self.assertEqual(self.value(distribution=kind,operation="quantile",q=0,**params),0)
            self.assertEqual(self.run_probability(distribution=kind,operation="quantile",q=1,**params)["value"],"1" if kind=="beta" else "∞")
        for kind,params,x,expected in [
            ("gamma",dict(shape=1,scale=2),200,math.exp(-100)),
            ("beta",dict(alpha=2,beta=3),"0.9999999999",4e-30-3e-40),
            ("lognormal",dict(mu=0,sigma=1),str(math.exp(10)),7.619853024160526e-24),
            ("weibull",dict(shape=2,scale=3),30,math.exp(-100))]:
            self.assertAlmostEqual(self.value(distribution=kind,operation="gt",x=x,**params)/expected,1,places=13)

    def test_negative_binomial_failures_and_geometric_equivalence(self):
        params=dict(r=3,p="1/2")
        masses=[math.comb(k+2,k)/2**(k+3) for k in range(12)]
        for x in [-1,0,1,2,2.3,7]:
            for op,predicate in [("eq",lambda k:k==x),("le",lambda k:k<=x),("lt",lambda k:k<x)]:
                self.assertAlmostEqual(self.value(distribution="negativeBinomial",operation=op,x=x,**params),sum(v for k,v in enumerate(masses) if predicate(k)),places=14)
            self.assertAlmostEqual(self.value(distribution="negativeBinomial",operation="gt",x=x,**params)+self.value(distribution="negativeBinomial",operation="le",x=x,**params),1,places=14)
            for op in ["eq","le","lt","ge","gt"]:
                self.assertAlmostEqual(self.value(distribution="negativeBinomial",operation=op,r=1,p="1/4",x=x),self.value(distribution="geometric",operation=op,p="1/4",x=x+1),places=14)
        self.assertAlmostEqual(self.value(distribution="negativeBinomial",operation="between",lower="1.2",upper="3.8",**params),masses[2]+masses[3],places=14)
        for q in ["0.01","0.5","0.99"]:
            x=self.value(distribution="negativeBinomial",operation="quantile",q=q,**params)
            self.assertGreaterEqual(self.value(distribution="negativeBinomial",x=x,**params),float(q))
            self.assertLess(self.value(distribution="negativeBinomial",x=x-1,**params),float(q))
        for op in ["eq","le","ge","quantile"]:
            self.assertEqual(self.value(distribution="negativeBinomial",operation=op,r=3,p=1,x=0,q="0.5"),0 if op=="quantile" else 1)
        result=self.run_probability(distribution="negativeBinomial",x=2,**params)
        self.assertEqual({d["label"]:float(d["value"]) for d in result["details"]},{"E[X]":3,"Var[X]":6,"SD[X]":math.sqrt(6)})
        self.assertIn("failures",result["note"])

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

    def test_new_parameters_are_validated(self):
        requests=[dict(distribution="gamma",shape=0,scale=1),dict(distribution="gamma",shape=2,scale=-1),
                  dict(distribution="beta",alpha=0,beta=2),dict(distribution="beta",alpha=2,beta=-1),
                  dict(distribution="lognormal",mu=0,sigma=0),dict(distribution="weibull",shape=-1,scale=2),dict(distribution="weibull",shape=1,scale=0),
                  dict(distribution="negativeBinomial",r=0,p="0.5"),dict(distribution="negativeBinomial",r="1.5",p="0.5"),dict(distribution="negativeBinomial",r=2,p=0),
                  dict(category="draw",operation="exactly",population=100,marked=10,draws=20,k="1.5"),dict(category="draw",operation="atLeast",population=100,marked=10,draws=20,k=-1)]
        for request in requests:
            self.assertFalse(self.run_probability(x=1,**request)["ok"],request)


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

    def test_repeat_ranges_match_enumeration_and_keep_exact_fractions(self):
        from fractions import Fraction
        for n in (0,1,5,10):
            for p in ('0','1/5','1'):
                fp=Fraction(p)
                masses=[Fraction(math.comb(n,k))*fp**k*(1-fp)**(n-k) for k in range(n+1)]
                for k in (0,1,3,n+1):
                    for operation,expected in [('atLeast',sum(masses[k:])),('atMost',sum(masses[:k+1])),('between',sum(masses[k:k+3]))]:
                        result=self.run_probability(category='repeat',operation=operation,n=n,p=p,k=k,lower=k,upper=k+2)
                        self.assertTrue(result['ok'],result)
                        self.assertEqual(float(result['value']),float(expected))
                        self.assertEqual(Fraction(result['fraction']),expected)
        self.assertAlmostEqual(self.value(category='repeat',operation='atLeast',n=10,p='20%',k=3),0.3222004736,places=14)
        self.assertFalse(self.run_probability(category='repeat',operation='between',n=10,p='.2',lower=4,upper=3)['ok'])
        self.assertFalse(self.run_probability(category='repeat',operation='atLeast',n=10,p='.2',k='1.5')['ok'])
        tail=self.value(category='repeat',operation='atLeast',n=100000,p='1e-30',k=1)
        self.assertAlmostEqual(tail/1e-25,1,places=13)

    def test_bayes_specificity_matches_false_positive_rate(self):
        for op in ('posterior','negative'):
            expected=self.value(category='bayes',operation=op,prior='1%',likelihood='99%',falsePositive='5%')
            self.assertEqual(self.value(category='bayes',operation=op+'Specificity',prior='1%',likelihood='99%',specificity='95%'),expected)
        self.assertFalse(self.run_probability(category='bayes',operation='posteriorSpecificity',prior='.1',likelihood='.9',specificity='101%')['ok'])

    def test_plot_quantiles_and_nonexistent_moments(self):
        for distribution,params in [('lognormal',dict(mu=0,sigma=3)),('gamma',dict(shape='.2',scale=5)),('weibull',dict(shape='.3',scale=2)),('binomial',dict(n=100000,p='.5')),('poisson',dict(rate=100000)),('t',dict(df=1)),('f',dict(df1=1,df2=1))]:
            result=self.run_probability(distribution=distribution,x=1,**params)
            self.assertTrue(result['ok'],result)
            points=result['plot']['points']
            self.assertTrue(1<len(points)<=162)
            self.assertTrue(all(all(math.isfinite(v) for v in p[:2]) for p in points))
            for index,q in [(0,'.001'),(-1,'.999')]:
                bound=self.value(distribution=distribution,operation='quantile',q=q,**params)
                self.assertAlmostEqual(points[index][0]/bound,1,places=12)
        for kind,params,mean,variance in [('t',dict(df=1),'undefined','undefined'),('t',dict(df=2),'0.0','∞'),('t',dict(df=3),'0.0','3.0'),('f',dict(df1=5,df2=2),'∞','undefined'),('f',dict(df1=5,df2=4),'2.0','∞')]:
            result=self.run_probability(distribution=kind,x=1,**params)
            details={d['label']:d['value'] for d in result['details']}
            self.assertEqual(details['E[X]'],mean)
            self.assertEqual(details['Var[X]'],variance)
            if variance in ('undefined','∞'): self.assertEqual(details['SD[X]'],variance)
            else: self.assertAlmostEqual(float(details['SD[X]']),math.sqrt(float(variance)),places=14)
        discrete=self.run_probability(distribution='binomial',operation='quantile',n=10,p='.2',q='.95')
        self.assertIn('≥',discrete['formula']);self.assertNotIn(' = ',discrete['formula'])

    def test_catalog_distributions_match_probability_mode(self):
        def evaluate(name,*args):
            tree={'kind':'call','value':name,'args':[{'kind':'number','value':str(arg)} for arg in args]}
            return json.loads(calc_engine.dispatch(json.dumps({'tree':tree})))
        for name,args,params in [('hgeom',(50,10,5),dict(distribution='hypergeometric',population=50,successes=10,draws=5)),('nbinom',(3,.25),dict(distribution='negativeBinomial',r=3,p='.25'))]:
            for k in (-1,0,1,2,2.5,6):
                for suffix,operation in [('pdf','eq'),('cdf','le')]:
                    result=evaluate(name+suffix,*args,k)
                    self.assertTrue(result['ok'],result)
                    self.assertAlmostEqual(float(result['decimal']),self.value(operation=operation,x=k,**params),places=14)
        for x in (-1,0,.5,2):
            for suffix,operation in [('pdf','density'),('cdf','le')]:
                result=evaluate('weibull'+suffix,x,2,3)
                self.assertTrue(result['ok'],result)
                self.assertAlmostEqual(float(result['decimal']),self.value(distribution='weibull',operation=operation,x=x,shape=2,scale=3),places=14)
        self.assertEqual(evaluate('hgeompdf',10,2,2,2)['exact'],'1/45')
        self.assertEqual(evaluate('nbinompdf',3,.5,2)['exact'],'3/16')
        for name,args in [('hgeomcdf',(10,11,2,1)),('hgeompdf',(10,2,2.5,1)),('nbinompdf',(0,.5,1)),('nbinomcdf',(3,0,1)),('weibullpdf',(1,0,1)),('weibullcdf',(1,2,0))]:
            self.assertFalse(evaluate(name,*args)['ok'])
        result=evaluate('nbinomcdf',3,.25,300)
        self.assertTrue(result['ok'],result)
        self.assertAlmostEqual(float(result['decimal']),self.value(distribution='negativeBinomial',r=3,p='.25',x=300),places=14)
        result=evaluate('nbinompdf',1,.25,300)
        self.assertTrue(result['ok'],result)
        self.assertAlmostEqual(float(result['decimal'])/(.25*.75**300),1,places=13)
        self.assertEqual(evaluate('nbinompdf',3,1,0)['exact'],'1')
        self.assertEqual(evaluate('weibullpdf',0,1,3)['exact'],'1/3')

    def test_preview_numerical_limits_do_not_discard_a_valid_answer(self):
        result=self.run_probability(distribution='f',df1=1,df2='.001',x=1)
        self.assertTrue(result['ok'],result)
        self.assertTrue(0<float(result['value'])<1)
        self.assertNotIn('plot',result)


if __name__ == "__main__":unittest.main()
