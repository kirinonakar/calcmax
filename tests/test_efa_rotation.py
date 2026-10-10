"""Regression for the supplied study-habits CSV and truthful loading reports."""
import csv
import json
import unittest
from test_advanced_statistics import ROOT, run, tree
from calc_evaluator import Engine
from calc_advanced_statistics import advanced
from calc_engine import dispatch


class EFARotationTests(unittest.TestCase):
    def test_default_rotation_is_oblimin(self):
        default=run('efa',self.rows,2);explicit=run('efa',self.rows,2,'oblimin')
        self.assertEqual(default['Rotation'],'oblimin');self.assertEqual(default['loadings'],explicit['loadings'])
        self.assertEqual(default['Factor correlations'],explicit['Factor correlations'])
    @classmethod
    def setUpClass(cls):
        with (ROOT/'tests/fixtures/efa_study_habits_sample.csv').open(encoding='utf-8-sig',newline='') as stream:
            rows=list(csv.reader(stream))
        cls.labels=rows[0]; cls.rows=[[float(v) for v in row] for row in rows[1:]]
        cls.references=json.loads((ROOT/'tests/fixtures/efa_rotation_reference.json').read_text())['cases']

    def test_supplied_data_matches_independent_varimax_and_variance_references(self):
        self.assertEqual(len(self.rows),180)
        for case in self.references:
            with self.subTest(extraction=case['extraction']):
                result=run('efa',self.rows,2,'varimax',case['extraction'])
                for actual,expected in zip(result['loadings'],case['loadings']):
                    for a,b in zip(actual,expected): self.assertAlmostEqual(float(a),b,places=6)
                for row,percentage,rotated in zip(result['Explained variance'],case['percentages'],case['rotatedPercentages']):
                    self.assertAlmostEqual(float(row['Explained variance (%)']),percentage,places=6)
                    self.assertAlmostEqual(float(row['Rotated explained variance (%)']),rotated,places=6)
                variance=result['Explained variance'][-1]
                self.assertAlmostEqual(float(variance['Cumulative explained variance (%)']),sum(case['percentages']),places=6)
                self.assertAlmostEqual(float(variance['Rotated cumulative explained variance (%)']),sum(case['percentages']),places=6)

    def test_public_dispatch_loadings_are_rotated_and_keep_csv_names(self):
        labels={'feature:'+str(i+1):name for i,name in enumerate(self.labels)}
        payload={'tree':tree('efa('+str(self.rows)+',2,varimax,pca)'), 'statisticsTermLabels':labels,'precision':20,'budget':30}
        result=json.loads(dispatch(json.dumps(payload)));self.assertTrue(result['ok'],result.get('error'))
        report=result['statisticsReport'];plot=next(p for p in report['plots'] if p['kind']=='loadings')
        self.assertEqual(plot['labels'],self.labels);self.assertEqual(plot['axisLabels'],['Component 1','Component 2'])
        self.assertEqual(plot['title'],'Principal component loadings');self.assertIn('reusable',result)
        table=next(s for s in report['sections'] if s['title']=='Explained variance')
        self.assertIn('Cumulative explained variance (%)',table['columns'])
        loadings=next(s for s in report['sections'] if s['title']=='loadings')
        for point,row in zip(plot['points'],loadings['rows']):
            for value,cell in zip(point,row[1:]): self.assertAlmostEqual(value,float(cell['decimal']),places=12)

    def test_single_component_and_oblique_axes_and_variance_are_honest(self):
        for count,rotation,extraction in [(1,'none','pca'),(2,'promax','pca'),(2,'oblimin','pa'),(3,'varimax','pca')]:
            with self.subTest(count=count,rotation=rotation,extraction=extraction):
                engine=Engine({});result=advanced(engine,'efa',[self.rows,count,rotation,extraction])
                plot=engine.statistics_plots[0]
                self.assertEqual(len(plot['points']),6);self.assertEqual(len(plot['axisLabels']),count)
                self.assertTrue(all(len(point)==count for point in plot['points']))
                if extraction=='pa': self.assertEqual(plot['axisLabels'][0],'Factor 1')
                if rotation in ('promax','oblimin'):
                    self.assertNotIn('Rotated cumulative explained variance (%)',result['Explained variance'][0])
                    self.assertIn('pattern',plot['caption'])


if __name__=='__main__': unittest.main()
