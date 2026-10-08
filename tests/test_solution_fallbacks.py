"""Complete versus partial Lambert W solutions and runtime-bounded factorization."""
import pathlib
import subprocess
import sys
import unittest

sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]/"app/src/main/python"))
import sympy as s
from calc_engine import Engine,contains_heavy_call
from calc_solutions import affine_exponential_solutions
from calc_number_theory import bounded_divisors
from calc_shared import MathError


class SolutionFallbackTests(unittest.TestCase):
    def test_complete_lambert_families_satisfy_equations_across_branches(self):
        x=s.Symbol("x")
        for expression in [s.exp(x)-x,s.exp(2*x+1)+3*x-1,s.exp(x)-4*x,
                           x*s.exp(x)-1,(2*x+3)*s.exp(4*x+1)-5]:
            result,note=affine_exponential_solutions(expression,x,s.S.Complexes)
            self.assertIsInstance(result,s.ImageSet)
            self.assertEqual(s.S.Integers,result.base_set)
            self.assertIn("All complex solutions",note)
            for k in range(-3,4):
                root=result.lamda(k)
                residual=s.N(expression.subs(x,root),50)
                self.assertLess(abs(residual),s.Rational(1,10)**40)

    def test_real_branches_and_domain_restrictions(self):
        x=s.Symbol("x")
        self.assertEqual(s.S.EmptySet,affine_exponential_solutions(s.exp(x)-x,x,s.S.Reals)[0])
        roots,_=affine_exponential_solutions(s.exp(x)-4*x,x,s.S.Reals)
        self.assertEqual(s.FiniteSet(-s.LambertW(-s.Rational(1,4)),-s.LambertW(-s.Rational(1,4),-1)),roots)
        self.assertEqual(s.FiniteSet(1),affine_exponential_solutions(s.exp(x)-s.E*x,x,s.S.Reals)[0])
        self.assertEqual(s.S.EmptySet,affine_exponential_solutions(s.exp(x)-4*x,x,s.Interval.open(-s.oo,0))[0])
        self.assertIsNone(affine_exponential_solutions(s.exp(x**2)-x,x,s.S.Complexes))
        self.assertEqual(s.FiniteSet(s.LambertW(1)),affine_exponential_solutions(x*s.exp(x)-1,x,s.S.Reals)[0])
        self.assertEqual(s.FiniteSet(s.LambertW(-s.Rational(1,4)),s.LambertW(-s.Rational(1,4),-1)),
                         affine_exponential_solutions(x*s.exp(x)+s.Rational(1,4),x,s.S.Reals)[0])
        self.assertEqual(s.S.EmptySet,affine_exponential_solutions(x*s.exp(x)+1,x,s.S.Reals)[0])
        self.assertEqual(s.FiniteSet(0),affine_exponential_solutions(x*s.exp(x),x,s.S.Complexes)[0])
        self.assertEqual(s.S.EmptySet,affine_exponential_solutions(x*s.exp(x)-1,x,s.Interval.open(-s.oo,0))[0])

    def test_auxiliary_roots_are_verified_and_marked_partial(self):
        x=s.Symbol("x")
        engine=Engine({})
        roots=engine.call("solve",[s.log(x)-x,x],[])
        self.assertTrue(any(root.has(s.LambertW) for root in roots))
        self.assertIn("Partial solutions",engine.note)
        self.assertIn("not the complete",engine.note)


class NumberTheoryLimitTests(unittest.TestCase):
    def test_large_easy_inputs_factor_and_enumerate_divisors_exactly(self):
        number=s.Integer(10)**20
        factorization=Engine({}).call("factorint",[number],[])
        self.assertEqual(number,s.prod(factorization.args))
        divisors=bounded_divisors(number)
        self.assertEqual(441,len(divisors))
        self.assertEqual(1,divisors[0])
        self.assertEqual(number,divisors[-1])
        self.assertEqual(len(divisors),len(set(divisors)))
        self.assertTrue(all(number%divisor==0 for divisor in divisors))
        self.assertEqual([1],bounded_divisors(s.Integer(1)))

    def test_divisor_count_and_output_size_are_bounded(self):
        with self.assertRaisesRegex(MathError,"2000 results"):
            bounded_divisors(s.Integer(2)**2000)
        with self.assertRaisesRegex(MathError,"40000 characters"):
            bounded_divisors(s.Integer(2)**1999)
        for name in ("factorint","divisors"):
            self.assertTrue(contains_heavy_call({"kind":"call","value":name}))

    def test_hard_factorization_stops_at_runtime_deadline(self):
        python_source=str(pathlib.Path(__file__).resolve().parents[1]/"app/src/main/python")
        program=f"""
import sys,json,time
sys.path.insert(0,{python_source!r})
import calc_engine
number=(2**127-1)*(2**61-1)
tree={{'kind':'call','value':'factorint','args':[{{'kind':'number','value':str(number)}}]}}
result=json.loads(calc_engine.dispatch(json.dumps({{'tree':tree,'budget':.01}})))
assert not result['ok'] and 'Computation limit reached' in result['error'],result
"""
        completed=subprocess.run([sys.executable,"-c",program],capture_output=True,text=True,timeout=5)
        self.assertEqual(0,completed.returncode,completed.stdout+completed.stderr)


if __name__=="__main__":
    unittest.main()
