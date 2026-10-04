import json
import pathlib
import subprocess
import sys
import unittest

PYTHON=pathlib.Path(__file__).resolve().parents[1]/"app/src/main/python"


class PolynomialSolveTests(unittest.TestCase):
    def test_fresh_process_solves_all_roots_at_requested_precision(self):
        # A separate process per degree reproduces the reported cache dependence.
        for degree in (4,5,6):
            with self.subTest(degree=degree):
                program=f"""
import sys,json
sys.path.insert(0,{str(PYTHON)!r})
import calc_engine as c
x={{'kind':'symbol','value':'x'}}
def n(v):return {{'kind':'number','value':str(v)}}
def b(op,*args):return {{'kind':'binary','value':op,'args':list(args)}}
tree={{'kind':'call','value':'solve','args':[{{'kind':'relation','value':'=','args':[b('+',b('-',b('^',x,n({degree})),x),n(1)),n(0)]}},x]}}
for precision in (30,60):
    for attempt in range(2):
        result=json.loads(c.dispatch(json.dumps({{'tree':tree,'precision':precision,'budget':8}})))
        assert result['ok'],result
        assert result['tree']['kind']=='set' and len(result['tree']['args'])=={degree},result
        if {degree}>4:
            assert 'CRootOf' not in result['exact'] and 'CRootOf' not in json.dumps(result['tree']),result
            assert result['approximate'],result
            shown=c.s.sympify(result['exact'])
            assert len(shown)=={degree},result
            assert all(abs(c.s.N(root**{degree}-root+1,precision))<c.s.Rational(1,10)**(precision-5) for root in shown),result
        roots=c.s.sympify(result['decimal'])
        assert len(roots)=={degree} and len(set(roots))=={degree},result
        assert all(abs(c.s.N(root**{degree}-root+1,precision))<c.s.Rational(1,10)**(precision-5) for root in roots),result
        assert result['note']=='',result
"""
                completed=subprocess.run([sys.executable,"-c",program],capture_output=True,text=True,timeout=45)
                self.assertEqual(0,completed.returncode,completed.stdout+completed.stderr)

    def test_numeric_root_display_preserves_explicit_exact_roots(self):
        sys.path.insert(0,str(PYTHON))
        import calc_engine as c
        from calc_display import display_rounded
        x=c.s.Symbol('x')
        roots=c.s.FiniteSet(2,c.s.sqrt(2),c.s.CRootOf(x**5-x+1,0))
        shown=display_rounded(roots,40)
        self.assertIn(c.s.Integer(2),shown)
        self.assertIn(c.s.sqrt(2),shown)
        self.assertFalse(shown.has(c.s.CRootOf))
        self.assertTrue(roots.has(c.s.CRootOf))
        self.assertEqual(c.s.FiniteSet(-c.s.sqrt(2),c.s.sqrt(2)),display_rounded(c.s.FiniteSet(-c.s.sqrt(2),c.s.sqrt(2)),40))

    def test_domain_assumptions_and_excluded_roots_are_preserved(self):
        sys.path.insert(0,str(PYTHON))
        import calc_engine as c
        # Use the public AST dispatch; stored x must not replace the solve variable.
        def node(kind,value,args=()):return {'kind':kind,'value':value,'args':list(args)}
        variable=node('symbol','x')
        expr=node('binary','+',[
            node('binary','-',[node('binary','^',[variable,node('number','5')]),variable]),node('number','1')])
        tree=node('call','solve',[expr,variable])
        for assumptions,count in [(['real'],1),(['positive'],0),(['integer'],0)]:
            result=json.loads(c.dispatch(json.dumps({'tree':tree,'assumptions':{'x':assumptions},'variables':{'x':node('number','99')}})))
            self.assertTrue(result['ok'],result)
            self.assertEqual(count,len(result['tree'].get('args',[])))
        rational=node('binary','/',[node('binary','-',[node('binary','^',[variable,node('number','2')]),node('number','1')]),node('binary','-',[variable,node('number','1')])])
        result=json.loads(c.dispatch(json.dumps({'tree':node('call','solve',[node('relation','=',[rational,node('number','2')]),variable])})))
        self.assertTrue(result['ok'],result)
        self.assertEqual('EmptySet',result['exact'])


if __name__=='__main__':unittest.main()
