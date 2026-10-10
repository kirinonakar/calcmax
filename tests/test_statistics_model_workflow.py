"""Fitted-data snapshots and factor memberships for EFA → CFA → SEM."""
import csv
import json
import unittest
from test_advanced_statistics import ROOT, tree
from calc_engine import dispatch
from calc_statistics_model_workflow import model_workflow


class StatisticsModelWorkflowTests(unittest.TestCase):
    def test_efa_uses_absolute_rotated_loadings_and_keeps_empty_factors_for_review(self):
        value={'Factors':3,'loadings':[[.4,-.8,.1],[.6,-.6,.1],[-.7,.2,.1]]}
        inputs=('efa',[[[1,2,3],[4,5,6]],'parallel'],[])
        labels={'feature:1':'same','feature:2':'same','feature:3':'third'}
        plan=model_workflow('efa',value,inputs,labels)
        self.assertEqual(plan['factors'],[2,1,1]);self.assertEqual(plan['factorCount'],3)
        self.assertEqual(plan['data'],[['1.0','2.0','3.0'],['4.0','5.0','6.0']]);self.assertEqual(plan['termLabels'],labels)

    def test_cfa_preserves_crossloadings_missingness_and_all_options(self):
        plan=model_workflow('cfa',{},('cfa',[[[1,'NA',3,4,5,6],[2,3,4,5,6,7]],[1,1,1,2,2,2],[[2,2]],'fiml',[2,5],'scalar','ml'],[]),{'group:2':'Control','group:5':'Treatment'})
        self.assertEqual(plan['target'],'sem');self.assertEqual(plan['data'][0][1],'NA')
        self.assertEqual(plan['factors'],[1,1,1,2,2,2]);self.assertEqual(plan['cross'],[[2,2]])
        self.assertEqual((plan['missing'],plan['groups'],plan['invariance'],plan['estimator']),('fiml',['2.0','5.0'],'scalar','ml'))
        self.assertEqual(plan['termLabels']['group:5'],'Treatment')
        self.assertNotIn('paths',plan)

    def test_public_efa_and_cfa_preserve_reordered_columns_and_fitted_data(self):
        with (ROOT/'tests/fixtures/efa_study_habits_sample.csv').open(encoding='utf-8-sig',newline='') as file: csv_rows=list(csv.reader(file))
        order=[5,4,3,2,1,0]; rows=[[float(row[i]) for i in order] for row in csv_rows[1:]]
        labels={'feature:'+str(i+1):csv_rows[0][index] for i,index in enumerate(order)}
        def calculate(expression):
            result=json.loads(dispatch(json.dumps({'tree':tree(expression),'statisticsTermLabels':labels,'budget':60})))
            self.assertTrue(result['ok'],result.get('error'));return result['statisticsReport']
        report=calculate('efa('+str(rows)+',2,varimax,pca)');plan=report['modelWorkflow']
        points=next(plot['points'] for plot in report['plots'] if plot['kind']=='loadings')
        self.assertEqual(plan['factors'],[max(range(2),key=lambda j:abs(row[j]))+1 for row in points])
        self.assertEqual(plan['termLabels'],labels)
        cfa=calculate('cfa('+str(plan['data']).replace("'",'')+','+str(plan['factors'])+',[],complete,[],configural,ml)')
        next_plan=cfa['modelWorkflow']
        self.assertEqual(next_plan['data'],plan['data']);self.assertEqual(next_plan['factors'],plan['factors'])
        self.assertEqual(next_plan['termLabels'],labels)
        self.assertEqual(next_plan['target'],'sem')


if __name__=='__main__': unittest.main()
