"""Social-science numeric references, invalid models and report integration."""
import json
import math
import unittest
from test_advanced_statistics import ROOT,run,evaluate,tree
from calc_engine import dispatch
from calc_shared import MathError

class SocialStatisticsTests(unittest.TestCase):
    def test_independent_references(self):
        fixture=json.loads((ROOT/'tests/fixtures/social_statistics_reference.json').read_text(encoding='utf8'))
        for case in fixture['cases']:
            with self.subTest(function=case['function'],options=case['arguments'][1:]):
                result=run(case['function'],*case['arguments'])
                for path,expected in case['expected']:
                    actual=result
                    for key in path: actual=actual[key]
                    self.assertAlmostEqual(float(actual),float(expected),delta=case['tolerance']*max(1,abs(expected)),msg=str(path))

    def test_all_new_examples_dispatch_to_structured_reports(self):
        definitions=json.loads((ROOT/'app/src/main/assets/advanced_statistics.json').read_text(encoding='utf8'))
        ids={'cronbach','efa','cfa','sem','manova','mediation','moderation','cramerv','phi','cohenkappa','dunn','discriminantanalysis','quantreg','zeroinflated','tobit','hcluster'}
        for d in definitions:
            if d['id'] not in ids: continue
            with self.subTest(function=d['id']):
                result=evaluate(d['example'])
                self.assertEqual(result['statisticsReport']['analysis'],d['id'])
                self.assertTrue(result['statisticsReport']['sections'])
                self.assertIn('tree',result)
                if d['id'] not in ('dunn',):
                    report=result['statisticsReport']
                    self.assertTrue(report['assumptions'])
                    self.assertFalse(any(row[0]=='Assumptions' for section in report['sections'] for row in section['rows']))
        # Retain the polynomial function whose name predates the new analysis.
        self.assertEqual(evaluate('discriminant(x,x)')['exact'],'1')

    def test_weighted_kappa_order_missing_margins_and_degenerate_cases(self):
        counts=[[10,5,0],[4,10,4],[0,6,12]]
        unweighted=float(run('cohenkappa',counts,'unweighted')['Cohen κ'])
        linear=float(run('cohenkappa',counts,'linear')['Cohen κ'])
        quadratic=float(run('cohenkappa',counts,'quadratic')['Cohen κ'])
        self.assertLess(unweighted,linear); self.assertLess(linear,quadratic)
        for mode in ('unweighted','linear','quadratic'):
            first=run('cohenkappa',counts,mode)
            reverse=run('cohenkappa',[list(reversed(row)) for row in reversed(counts)],mode)
            self.assertAlmostEqual(float(first['Cohen κ']),float(reverse['Cohen κ']),places=12)
            self.assertAlmostEqual(float(first['SE']),float(reverse['SE']),places=12)
            self.assertAlmostEqual(float(first['Cohen κ']),float((first['Observed agreement']-first['Expected agreement'])/(1-first['Expected agreement'])),places=12)
        self.assertTrue(math.isfinite(float(run('cohenkappa',[[0,0],[2,3]])['Cohen κ'])))
        with self.assertRaises(MathError): run('cohenkappa',[[4,0],[0,0]])
        with self.assertRaises(MathError): run('cohenkappa',[[1,2,3],[3,2,1]])

    def test_invalid_models_do_not_return_finite_inference(self):
        cases=[('cronbach',[[[1,1],[1,1],[1,1]]]),
               ('efa',[[[1,2],[2,4],[3,6]],1]),
               ('cfa',[[[1,2,3],[2,3,4],[3,4,5],[4,5,6],[5,6,7]],[1,1,2]]),
               ('sem',[[[1,2,3,4,5,6]]*8,[1,1,1,2,2,2],[[1,2],[2,1]]]),
               ('manova',[[[1,1,2],[1,2,4],[2,3,6],[2,4,8]]]),
               ('mediation',[[[1,2,3]]*8,100]),
               ('moderation',[[[1,2,3]]*8]),
               ('phi',[[[1,2,3],[3,4,5]]]),
               ('dunn',[[[1,1],[1,1]]]),
               ('discriminantanalysis',[[[1,1],[2,1],[3,2],[4,2]],'qda','unknown']),
               ('quantreg',[[[1,2],[2,3],[3,4],[4,6]],1]),
               ('tobit',[[[1,0],[2,0],[3,0],[4,0]]]),
               ('tobit',[[[1,1],[2,2],[3,3],[4,4]],2,1]),
               ('zeroinflated',[[[1,1],[2,2],[3,3],[4,4]]]),
               ('hcluster',[[[1,2],[2,3],[3,4]],4])]
        for name,args in cases:
            with self.subTest(function=name):
                with self.assertRaises(MathError): run(name,*args)

    def test_scale_invariance_and_mediation_identity(self):
        fixture=json.loads((ROOT/'tests/fixtures/social_statistics_reference.json').read_text(encoding='utf8'))['cases']
        case=next(c for c in fixture if c['function']=='mediation'); rows=case['arguments'][0]
        result=run('mediation',rows,100,7); again=run('mediation',rows,100,7)
        self.assertEqual(result['Bootstrap percentile CI95'],again['Bootstrap percentile CI95'])
        self.assertAlmostEqual(float(result['Total effect c']-result['Direct effect c′']),float(result['Indirect effect a×b']),places=10)
        case=next(c for c in fixture if c['function']=='cfa'); rows,assignment=case['arguments']
        first=run('cfa',rows,assignment); second=run('cfa',[[1000+(j+1)*v for j,v in enumerate(row)] for row in rows],assignment)
        self.assertAlmostEqual(float(first['χ²']),float(second['χ²']),places=6)
        self.assertAlmostEqual(float(first['CFI']),float(second['CFI']),places=7)
        for i,row in enumerate(first['Loadings']): self.assertAlmostEqual(float(row['Standardized loading']),float(second['Loadings'][i]['Standardized loading']),places=7)

    def test_dunn_companion_and_variable_labels_preserve_reusable_results(self):
        result=evaluate('kruskal([1,2,3],[2,3,5],[4,6,7])')
        self.assertTrue(any(section['title']=='Dunn post-hoc (Holm)' for section in result['statisticsReport']['sections']))
        self.assertTrue(any(section['title']=='Comparisons' and 'z' in section['columns'] for section in result['statisticsReport']['sections']))
        source='cronbach([[1,2,1],[2,3,2],[3,3,4],[4,5,4]])'
        plain=evaluate(source)
        labeled=json.loads(dispatch(json.dumps({'tree':tree(source),'precision':20,'statisticsTermLabels':{'feature:1':'Item A','feature:2':'Item B','feature:3':'Item C'}})))
        self.assertTrue(labeled['ok']);self.assertEqual(plain['reusable'],labeled['reusable'])
        self.assertEqual(plain['statisticsReport']['highlights'][0]['value']['exact'],labeled['statisticsReport']['highlights'][0]['value']['exact'])
        section=next(s for s in labeled['statisticsReport']['sections'] if s['title']=='Item diagnostics')
        self.assertEqual(section['rows'][0][0],'Item A')

if __name__=='__main__': unittest.main()
