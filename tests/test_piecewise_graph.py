"""Graph dispatch checks using trees exported by the actual Kotlin parser."""
import json
import math
from pathlib import Path
import sys
import unittest
import sympy as s

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'app/src/main/python'))
from calc_engine import dispatch
from calc_evaluator import Engine
from calc_graph import adaptive_samples

TREES={case['source']:case['tree'] for case in json.loads((ROOT/'build/math-cases.json').read_text(encoding='utf-8'))}

class PiecewiseGraphTests(unittest.TestCase):
    def graph(self,source,**options):
        tree=TREES[source]
        if tree.get('kind')=='relation' and tree['args'][0].get('kind')=='call': tree=tree['args'][1]
        result=json.loads(dispatch(json.dumps({'action':'graph','graphKind':'cartesian','trees':[tree],
                                             'min':-100,'max':4000,'yMin':-1,'yMax':2,**options})))
        self.assertTrue(result['ok'],result.get('error'))
        self.assertEqual([False],result['implicitCurves'])
        return result['curves'][0]

    def test_narrow_domain_is_sampled_and_chained_inequalities_work_in_both_directions(self):
        points=list(filter(None,self.graph('y=x{0.0001<=x<=0.0002}',min=0,max=100)))
        self.assertGreaterEqual(len(points),2)
        self.assertEqual(.0001,points[0][0]);self.assertEqual(.0002,points[-1][0])
        points=list(filter(None,self.graph('y=x{3000>=x>=0}')))
        self.assertEqual(0,points[0][0]);self.assertEqual(3000,points[-1][0])

    def test_same_sign_jump_never_has_a_connecting_segment(self):
        curve=self.graph('y={x<0:1,2}',min=-1,max=1)
        self.assertIn(None,curve)
        self.assertTrue(any(point and point[1]==1 for point in curve))
        self.assertTrue(any(point and point[1]==2 for point in curve))
        for first,second in zip(curve,curve[1:]):
            if first and second:self.assertEqual(first[1],second[1])

    def test_inactive_branch_is_lazy_and_domain_guards_respect_branch_priority(self):
        engine=Engine({});engine.bindings['x']=s.Integer(0)
        self.assertEqual(5,engine.build(TREES['{x<0:1/x,5}']))
        self.assertEqual([],engine.conditions)
        engine=Engine({});x=engine.symbol('x')
        value=engine.build(TREES['{x<1:5,x>-1:1/x,0}'])
        self.assertEqual(5,value.subs(x,0))
        self.assertTrue(all(s.simplify(guard.subs(x,0))==s.true for guard in engine.conditions))

    def test_pixel_refinement_resolves_curvature_with_different_axis_scales(self):
        f=lambda x:math.exp(-x/80)
        curve,_=adaptive_samples(f,0,4000,100,screen_bounds=(0,4000,-.1,1.1))
        for a,b in zip(curve,curve[1:]):
            if a and b:
                error=abs(f((a[0]+b[0])/2)-(a[1]+b[1])/2)*800/1.2
                self.assertLessEqual(error,.21)
