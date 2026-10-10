"""Independent numerical references and public-dispatch comparison suites."""
import json
from pathlib import Path
import unittest
import sympy as s
from test_advanced_statistics import run, evaluate, tree
from calc_engine import dispatch
from calc_shared import MathError
from calc_advanced_linear import least_squares, partial_f_test

ROOT=Path(__file__).resolve().parents[1]


class ComparisonTests(unittest.TestCase):
    def test_rank_and_repeated_posthoc_families_use_matched_samples_and_holm(self):
        ref=json.loads((ROOT/'tests/fixtures/statistics_group_reference.json').read_text())
        for method,title in [('friedman','Wilcoxon post-hoc (Holm)'),('repeatedanova','Paired t post-hoc (Holm)'),('kruskal','Mann–Whitney post-hoc (Holm)')]:
            source=method+'('+(str(ref['repeated']) if method!='kruskal' else ','.join(str(sample) for sample in ref['samples']))+')'
            report=evaluate(source)['statisticsReport']
            section=next(section for section in report['sections'] if section['title']==title)
            for i,row in enumerate(section['rows']):
                for key,reference in [('Raw p value','raw'),('Adjusted p value','holm')]:
                    self.assertAlmostEqual(float(row[section['columns'].index(key)]['decimal']),ref['posthoc'][method][reference][i],places=12)
            if method=='repeatedanova':
                qq=next(plot for plot in report['plots'] if plot['kind']=='qq')
                self.assertEqual(len(qq['series']),len(ref['repeated'][0])-1)
                self.assertEqual(qq['series'][0]['n'],len(ref['repeated']))

    def test_imputation_application_data_does_not_use_display_rounding(self):
        request={'tree':tree('impute([[1,NA],[2,4],[NA,6],[5,8]],mean)'),'precision':3}
        result=json.loads(dispatch(json.dumps(request)))
        self.assertTrue(result['ok'],result.get('error'))
        self.assertEqual(result['imputation']['data'][2][0],'2.6666666666666665')
        self.assertEqual(result['imputation']['imputedCells'],2)

    def test_student_welch_games_howell_and_tie_corrected_friedman(self):
        ref=json.loads((ROOT/'tests/fixtures/statistics_group_reference.json').read_text())
        groups=ref['samples']
        source=','.join(str(sample) for sample in groups)
        welch=evaluate('welchanova('+source+')')['statisticsReport']
        sections={section['title']:section for section in welch['sections']}
        values={row[0]:float(row[1]['decimal']) for row in sections['Summary']['rows'] if isinstance(row[1],dict)}
        for key,actual in [('F',values['F']),('df2',values['df denominator']),('p',values['p value'])]:
            self.assertAlmostEqual(actual,ref['welch'][key],places=12)
        self.assertIn('Games–Howell post-hoc',sections)
        pairs=sections['Games–Howell post-hoc'];cols=pairs['columns']
        for row,expected in zip(pairs['rows'],ref['pairs']):
            def value(column):return float(row[cols.index(column)]['decimal'])
            self.assertAlmostEqual(value('Adjusted p value'),expected['p'],delta=2e-6)
            self.assertAlmostEqual(value('Lower 95% CI'),expected['ci'][0],delta=2e-4)
            self.assertAlmostEqual(value('Upper 95% CI'),expected['ci'][1],delta=2e-4)
        classic=evaluate('anova('+source+')')['statisticsReport']
        self.assertTrue(any(section['title']=='Tukey–Kramer post-hoc' for section in classic['sections']))
        student=evaluate('ttest2(0,'+str(groups[0])+','+str(groups[1])+',student)')
        summary=student['statisticsReport']['sections'][0]
        numeric={row[0]:float(row[1]['decimal']) for row in summary['rows']}
        self.assertAlmostEqual(numeric['t'],ref['student']['t'],places=12)
        self.assertAlmostEqual(numeric['p value'],ref['student']['p'],places=12)
        self.assertEqual(student['statisticsReport']['title'],'Student t test')
        friedman=run('friedman',ref['repeated'])
        self.assertAlmostEqual(float(friedman['chi2']),ref['friedman']['chi2'],places=12)
        self.assertAlmostEqual(float(friedman['p']),ref['friedman']['p'],places=12)

    def test_three_factor_type_ii_iii_against_statsmodels(self):
        fixture=json.loads((ROOT/'tests/fixtures/statistics_factorial_reference.json').read_text())
        for case in fixture['cases']:
            model=run('linearmodel',fixture['rows'],[1,2,3],3,case['type'],s.Symbol('sum'))
            for term in model['ANOVA']:
                reference=case['expected'][term['Term']]
                self.assertAlmostEqual(float(term['SS']),reference['sum_sq'],delta=1e-9)
                self.assertEqual(int(term['df']),int(reference['df']))
                if term['Term']!='Residual':
                    self.assertAlmostEqual(float(term['F']),reference['F'],delta=1e-8)
                    self.assertAlmostEqual(float(term['p']),reference['PR(>F)'],delta=1e-12)

    def test_two_way_alias_reuses_linear_fit_and_preserves_source_labels(self):
        rows=[[1,1,2],[1,1,4],[1,2,5],[1,2,6],[2,1,4],[2,1,5],[2,2,8],[2,2,10]]
        factorial=run('twowayanova',rows,1)
        linear=run('linearmodel',rows,[1,2],2,3,s.Symbol('sum'))
        for one,two in zip(factorial['ANOVA'],linear['ANOVA']):
            self.assertAlmostEqual(float(one['SS']),float(two['SS']),places=12)
        request={'tree':tree('twowayanova('+str(rows)+',1)'),'precision':15,
                 'statisticsTermLabels':{'factor:A':'Treatment','factor:B':'Time','factor:A:1':'Control','factor:A:2':'Drug','factor:B:1':'Early','factor:B:2':'Late'}}
        result=json.loads(dispatch(json.dumps(request)))
        self.assertTrue(result['ok'],result.get('error'))
        report=result['statisticsReport']
        self.assertTrue(any(section['title']=='Assumption checks' for section in report['sections']))
        self.assertEqual(report['plots'][0]['lineLabels'],['Control','Drug'])
        self.assertEqual(report['plots'][0]['xLabels'],['Early','Late'])
        self.assertTrue(any(plot['kind']=='intervals' for plot in report['plots']))
        self.assertIn('Treatment × Time',result['exact'])

    def test_partial_f_blocks_and_unidentifiable_models(self):
        x=[[1.,i,float(i%2)] for i in range(10)]
        y=[2+3*row[1]+row[2]+.2*(row[1]%3) for row in x]
        fit=least_squares(x,y); test=partial_f_test(x,y,[1,2],fit)
        self.assertEqual(test['df'],2)
        self.assertAlmostEqual(test['SS'],sum((v-sum(y)/len(y))**2 for v in y)-fit[2],places=10)
        with self.assertRaises(MathError):run('twowayanova',[[1,1,2],[1,1,3],[1,2,4],[2,2,5],[2,2,6]],1)
        with self.assertRaises(MathError):run('linearmodel',[[1,1,2],[1,1,4],[1,2,5],[1,2,6],[2,1,4],[2,1,5],[2,2,8],[2,2,10]],[1,2],2,3,s.Symbol('treatment'))


if __name__=='__main__':unittest.main()
