"""Public-dispatch statistics checks, with independent SciPy/statsmodels fixtures."""
import ast
import json
import math
from pathlib import Path
import sys
import unittest

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'app/src/main/python'))
from calc_engine import dispatch
from calc_evaluator import Engine
from calc_advanced_statistics import FUNCTIONS, advanced
from calc_shared import MathError
import mpmath as mp
import sympy as s


def tree(source):
    def visit(node):
        if isinstance(node,ast.Call): return {'kind':'call','value':node.func.id,'args':[visit(a) for a in node.args]}
        if isinstance(node,ast.List): return {'kind':'list','args':[visit(a) for a in node.elts]}
        if isinstance(node,ast.Name): return {'kind':'symbol','value':node.id}
        if isinstance(node,ast.Constant): return {'kind':'number','value':str(node.value)}
        if isinstance(node,ast.UnaryOp): return {'kind':'unary','value':'-' if isinstance(node.op,ast.USub) else '+','args':[visit(node.operand)]}
        raise ValueError(ast.dump(node))
    return visit(ast.parse(source,mode='eval').body)


def evaluate(source):
    result=json.loads(dispatch(json.dumps({'tree':tree(source),'precision':20,'budget':30})))
    if not result['ok']: raise AssertionError(source+': '+result['error'])
    return result


def run(name,*args):
    return advanced(Engine({}),name,[s.sympify(v) for v in args])


class AdvancedStatisticsTests(unittest.TestCase):
    def test_categorical_report_headers_label_counts_without_changing_answers(self):
        labels={'table:row':'Treatment (z)','table:column':'Outcome (x4)',
                'table:row:1':'treated','table:row:2':'control','table:column:1':'yes','table:column:2':'no'}
        for source in ('fisherexact([1,1,2,2],[1,2,1,2])','chi2independence([1,1,2,2],[1,2,1,2],1)','mcnemar([[20,8],[2,15]],exact)'):
            with self.subTest(source=source):
                request={'tree':tree(source),'precision':20,'budget':30}
                original=json.loads(dispatch(json.dumps(request)))
                named=json.loads(dispatch(json.dumps({**request,'statisticsTermLabels':labels})))
                self.assertTrue(named['ok'],named.get('error'))
                for key in ('exact','decimal','resultAst','reusable'):
                    self.assertEqual(original.get(key),named.get(key))
                sections=named['statisticsReport']['sections']
                self.assertEqual(sections[0]['rows'],[['Treatment (z)','Outcome (x4)']])
                for section in sections:
                    if section['title'] in ('observed','expected'):
                        self.assertEqual(section['columns'],['Treatment (z)','Outcome (x4): yes','Outcome (x4): no'])
                        self.assertEqual([row[0] for row in section['rows']],['treated','control'])
                        plain=next(item for item in original['statisticsReport']['sections'] if item['title']==section['title'])
                        self.assertEqual([row[1:] for row in section['rows']],[row[1:] for row in plain['rows']])

    def test_statistics_reports_cover_analysis_examples_with_rectangular_tables(self):
        definitions=json.loads((ROOT/'app/src/main/assets/advanced_statistics.json').read_text(encoding='utf-8'))
        for definition in definitions:
            with self.subTest(analysis=definition['id']):
                result=evaluate(definition['example'])
                if definition['id']=='survivalanalysis':
                    self.assertNotIn('statisticsReport',result)
                    continue
                report=result['statisticsReport']
                self.assertEqual(report['analysis'],definition['id'])
                self.assertTrue(report['sections'])
                for section in report['sections']:
                    self.assertTrue(section['rows'])
                    self.assertGreaterEqual(section['totalRows'],len(section['rows']))
                    for row in section['rows']: self.assertEqual(len(row),len(section['columns']))

    def test_statistics_tables_preserve_exact_answers_and_parallel_row_relationships(self):
        result=evaluate('stats([1,2,4])')
        summary=result['statisticsReport']['sections'][0]
        mean=next(row[1] for row in summary['rows'] if row[0]=='mean')
        self.assertEqual(mean['exact'],'7/3')
        self.assertEqual(mean['tree']['kind'],'fraction')
        self.assertIn('mean: 7/3',result['exact'])
        interval=evaluate('tinterval(95,[1,2,4,5])')['statisticsReport']
        self.assertEqual(next(section for section in interval['sections'] if section['title']=='confidence interval')['columns'],['Lower','Upper'])
        adjusted=evaluate('padjust([0.01,0.03,0.2],holm)')['statisticsReport']
        rows=next(section for section in adjusted['sections'] if section['title']=='P-value adjustment')['rows']
        self.assertEqual(len(rows),3)
        self.assertAlmostEqual(float(rows[1][1]['exact']),.03)
        self.assertAlmostEqual(float(rows[1][2]['exact']),.06)
        comparisons=evaluate('tukey([1,2,3],[4,5,7],[3,6,8])')['statisticsReport']['sections'][0]
        self.assertEqual(comparisons['columns'],['Comparison','Mean difference','Adjusted p value'])
        self.assertEqual([row[0] for row in comparisons['rows']],['x-y','x-z','y-z'])

    def test_report_previews_bound_large_tables_without_truncating_full_results(self):
        rows=[[i,i+1] for i in range(105)]
        result=evaluate('impute('+str(rows)+',mean)')
        section=next(section for section in result['statisticsReport']['sections'] if section['title']=='data')
        self.assertEqual(section['totalRows'],105)
        self.assertEqual(len(section['rows']),100)
        self.assertEqual(len(section['copyRows']),105)
        self.assertEqual(section['rows'],section['copyRows'][:100])
        self.assertEqual(section['copyRows'][-1][0]['exact'],'105')
        self.assertIn('104',result['exact'])
        self.assertIn('105',result['exact'])
        self.assertIn('105',json.dumps(result['tree']))

    def test_dedicated_reports_expose_complete_copy_tables_without_changing_ui_routing(self):
        regression=evaluate('regression([[0,1],[1,3],[2,4],[3,7]],linear)')
        self.assertNotIn('statisticsReport',regression)
        copy=regression['statisticsCopyReport']
        self.assertEqual('Regression',copy['title'])
        coefficients=next(section for section in copy['sections'] if section['title']=='coefficients')
        self.assertEqual(len(regression['regression']['coefficients']),len(coefficients['rows']))
        residuals=next(section for section in copy['sections'] if section['title']=='residuals')
        self.assertEqual(4,len(residuals['rows']))
        survival=evaluate('survivalanalysis([[1,1,1],[2,0,1],[3,1,1]],0)')
        self.assertNotIn('statisticsReport',survival)
        tables=[section for section in survival['statisticsCopyReport']['sections'] if section['title']=='survival table']
        self.assertTrue(tables)
        self.assertEqual(len(survival['survival']['groups'][0]['curve']),tables[0]['totalRows'])

    def test_advanced_analyses_reject_invalid_arity_and_data(self):
        with self.subTest(scenario='registered_analyses_reject_missing_and_excess_arguments'):
            definitions=json.loads((ROOT/'app/src/main/assets/advanced_statistics.json').read_text(encoding='utf-8'))
            self.assertEqual(FUNCTIONS,{item['id'] for item in definitions})
            for name in FUNCTIONS:
                for args in ([],[s.Integer(0)]*21):
                    with self.subTest(function=name,count=len(args)):
                        with self.assertRaisesRegex(MathError,name+' argument count mismatch'):
                            advanced(Engine({}),name,args)
        with self.subTest(scenario='invalid_inputs_fail_without_plausible_results'):
            sources=['padjust([0.1,1.1])','cohend([1,1],[2,2])','mcnemar([[1,-2],[3,4]])','kaplanmeier([[1,2],[2,1]])','logrank([[1,0],[2,0]],[[1,0],[2,0]])',
                     'poissonreg([[1,-1],[2,2],[3,4]])','impute([[NA,1],[NA,2]])','pca([[1,2],[1,3]])','cohend([1,2],[3,4,5],paired)',
                     'crossvalidate([[0,1],[1,3],[2,5]],3)','gee([[1,0,1],[1,1,2],[2,0,3],[2,1,4]])','cox([[1,1,1],[2,1,1],[3,1,1],[4,1,1]])',
                     'multinomial([[-2,0],[-1,0],[1,1],[2,1]])','cox([[1,1,3],[2,1,2],[3,1,1],[4,0,0]])',
                     'poissonreg([[0,0],[0,0],[1,2],[1,3],[1,1]])','impute([[1,NA],[2,NA]],regression)',
                     'crossvalidate([[0,1],[1,3],[2,5]],3,0,random,ridge,0)']
            for source in sources:
                with self.subTest(source=source):
                    result=json.loads(dispatch(json.dumps({'tree':tree(source),'budget':30})))
                    self.assertFalse(result['ok'],source)


    def test_header_term_labels_affect_both_displays_and_preserve_reusable_answer(self):
        item=next(item for item in json.loads((ROOT/'app/src/main/assets/advanced_statistics.json').read_text(encoding='utf-8')) if item['id']=='gee')
        request={'tree':tree(item['example']),'precision':20,'budget':30}
        original=json.loads(dispatch(json.dumps(request)))
        labelled=json.loads(dispatch(json.dumps({**request,'statisticsTermLabels':{'x1':'treatment (z)'}})))
        self.assertTrue(labelled['ok'],labelled.get('error'))
        for field in ('exact','decimal','tree','decimalTree'):
            self.assertIn('treatment (z)',json.dumps(labelled[field]))
        self.assertEqual(labelled.get('resultAst'),original.get('resultAst'))
        self.assertEqual(labelled.get('reusable'),original.get('reusable'))
        coefficient_table=next(section for section in labelled['statisticsReport']['sections'] if section['title']=='coefficients')
        self.assertIn('treatment (z)',[row[0] for row in coefficient_table['rows']])
        for field in ('exact','decimal','tree','decimalTree'):
            self.assertEqual(json.dumps(labelled[field]).replace('treatment (z)','x1'),json.dumps(original[field]))
        self.assertIn('term: x1',original['exact'])


    def test_kaplan_meier_ties_and_nonreached_median(self):
        result=run('kaplanmeier',[[1,1],[1,0],[2,1],[3,0]])
        self.assertEqual(list(map(float,result['survival table'][0][:5])),[1,4,1,1,.75])
        self.assertAlmostEqual(float(result['survival table'][1][4]),.375)
        self.assertEqual(float(result['median survival']),2)
        self.assertEqual(run('kaplanmeier',[[1,0],[2,0]])['median survival'],'unavailable')
        self.assertEqual(float(run('kaplanmeier',[[1,1],[1,1]])['survival table'][0][4]),0)

    def test_survival_reports_preserve_ties_groups_and_censoring(self):
        with self.subTest(scenario='survival_suite_preserves_ties_ci_and_two_group_logrank'):
            rows=[[1,1,1],[1,0,1],[2,1,1],[3,0,1],[1,0,2],[2,1,2],[3,1,2],[4,0,2]]
            report=evaluate('survivalanalysis('+str(rows)+',0)')['survival']
            curve=report['groups'][0]['curve']
            self.assertEqual(curve[0][:5],[1,4,1,1,.75])
            reference=run('kaplanmeier',[r[:2] for r in rows if r[2]==1])['survival table']
            for actual,expected in zip(curve,reference):
                for x,y in zip(actual,expected):self.assertAlmostEqual(x,float(y),places=12)
            lr=run('logrank',[r[:2] for r in rows if r[2]==1],[r[:2] for r in rows if r[2]==2])
            for key in ['chi2','df','p']:self.assertAlmostEqual(report['logrank'][key],float(lr[key]),places=12)
        with self.subTest(scenario='survival_multigroup_is_invariant_to_label_and_row_order'):
            rows=[[1,1,1],[2,0,1],[2,1,2],[4,1,2],[3,1,3],[5,0,3]]
            first=evaluate('survivalanalysis('+str(rows)+')')['survival']
            second=evaluate('survivalanalysis('+str([[t,e,4-g] for t,e,g in reversed(rows)])+')')['survival']
            self.assertEqual(first['logrank']['df'],2)
            self.assertGreater(first['logrank']['chi2'],0)
            self.assertAlmostEqual(first['logrank']['chi2'],second['logrank']['chi2'],places=12)
            self.assertAlmostEqual(first['logrank']['p'],second['logrank']['p'],places=12)
        with self.subTest(scenario='survival_censor_only_and_single_subject_groups_keep_curves'):
            report=evaluate('survivalanalysis([[1,0,1],[2,0,2]],1)')['survival']
            self.assertIn('error',report['logrank'])
            self.assertIn('error',report['cox'])
            for group in report['groups']:
                self.assertIsNone(group['median'])
                self.assertEqual(group['curve'][0][1],1)
                self.assertEqual(group['curve'][0][4:],[1,1,1])
            report=evaluate('survivalanalysis([[0,1,1],[2,0,2]])')['survival']
            self.assertEqual(report['groups'][0]['curve'][0],[0,1,1,0,0,0,0])
            self.assertEqual(report['groups'][0]['median'],0)


    def test_survival_ph_check_reports_per_covariate_statistics(self):
        with self.subTest(scenario='survival_ph_check_reports_per_covariate_statistics'):
            rows=[[1,1,0,1],[2,1,1,0],[3,0,0,1],[4,1,1,1],[5,1,0,1],[6,0,1,0],[7,1,1,0],[8,1,0,1],[9,1,1,1],[10,0,0,0],[11,1,1,1],[12,1,0,0]]
            fit=run('cox',rows,'efron',-1,1)
            self.assertEqual(int(fit['PH test df']),2)
            terms=fit['PH test per covariate']
            self.assertEqual([term['term'] for term in terms],['x1','x2'])
            self.assertTrue(all(int(term['df'])==1 for term in terms))
            report=evaluate('survivalanalysis('+str([[r[0],r[1],1+r[2],r[3]] for r in rows])+',1)')['survival']
            self.assertIn('PH test p',report['cox'])
        with self.subTest(scenario='survival_ph_check_distinguishes_crossing_from_proportional_hazards'):
            crossing=[[1,1,1],[2,1,1],[3,1,1],[4,1,1],[5,1,1],[6,1,0],[7,1,0],[8,1,0],[9,1,0],[10,1,0],[11,0,1],[12,0,0]]
            holding=[[1,1,0],[2,1,1],[3,0,0],[4,1,1],[5,1,0],[6,0,1],[7,1,1],[8,1,0],[9,1,0],[10,1,1],[11,1,0],[12,0,1],[13,1,1],[14,1,0],[15,0,0],[16,1,1]]
            bad=run('cox',crossing,'efron',-1,1); good=run('cox',holding,'efron',-1,1)
            self.assertGreater(float(bad['PH test chi2']),float(good['PH test chi2']))
            self.assertLess(float(bad['PH test p']),.05)
            self.assertGreater(float(good['PH test p']),.2)
            self.assertLess(float(run('cox',crossing,'breslow',-1,1)['PH test p']),.05)


    def test_ks_exact_separated_samples_and_identical_ties(self):
        result=run('kstest',[1,2,3],[4,5,6])
        self.assertAlmostEqual(float(result['D']),1)
        self.assertAlmostEqual(float(result['p']),.1)
        self.assertEqual(float(run('kstest',[1,2,3],[1,2,3])['p']),1)
        self.assertGreater(float(run('kstest',[-1,0,1],'normal',0,1)['p']),0)
        self.assertAlmostEqual(float(run('kstest',list(range(60)),list(range(60,120)))['p'])/(2/math.comb(120,60)),1,places=12)

    def test_power_size_minimum_and_design(self):
        for design in ('independent','paired','onesample'):
            result=run('samplesize',.5,.8,.05,design); n=int(result['n per group / pairs'])
            self.assertGreaterEqual(float(run('testpower',.5,n,.05,design)['power']),.8)
            self.assertLess(float(run('testpower',.5,n-1,.05,design)['power']),.8)
        # Exact noncentral-t minimum: 64 independent, 34 paired or one-sample (R power.t.test).
        self.assertEqual(int(run('samplesize',.5,.8)['n per group / pairs']),64)
        self.assertAlmostEqual(float(run('testpower',.5,64,.05)['power']),.8014595579,places=6)
        two=float(run('testpower',.5,64,.05)['power']); one=float(run('testpower',.5,64,.05,'independent','greater')['power'])
        self.assertGreater(one,two)
        self.assertAlmostEqual(float(run('testpower',.5,64,.05,'independent','less')['power']),4.131985367258805e-06,places=9)
        self.assertLess(int(run('samplesize',.5,.8,.05,'independent','greater')['n per group / pairs']),64)

    def test_bootstrap_seed_and_constant_sample(self):
        a=run('bootstrapci',[1,2,3,4],'median',.95,500,7)
        self.assertEqual(a,run('bootstrapci',[1,2,3,4],'median',.95,500,7))
        constant=run('bootstrapci',[2,2,2],'mean',.95,100)
        self.assertEqual(float(constant['lower']),2)
        self.assertEqual(float(constant['upper']),2)

    def test_pca_reconstructs_data_and_restores_numeric_precision(self):
        with self.subTest(scenario='pca_reconstructs_and_explains_all_variance'):
            rows=[[1,2],[2,1],[3,4],[4,3],[5,7]]; result=run('pca',rows,2,1)
            self.assertAlmostEqual(sum(map(float,result['explained variance ratio'])),1)
            for r,score in zip(rows,result['scores']):
                for j in range(2):
                    reconstructed=sum(float(score[k]*result['loadings'][j][k]) for k in range(2))*float(result['scales'][j])+float(result['centers'][j])
                    self.assertAlmostEqual(reconstructed,r[j],places=10)
        with self.subTest(scenario='public_entry_preserves_precision_after_success_and_failure'):
            rows=[[1,2],[2,1],[3,4],[4,3],[5,7]]
            expected=run('pca',rows)
            for precision in (10,40):
                with mp.workdps(precision):
                    self.assertEqual(run('pca',rows),expected)
                    self.assertEqual(mp.mp.dps,precision)
                    with self.assertRaisesRegex(MathError,'Standardized PCA requires nonconstant columns'):
                        run('pca',[[1,2],[1,3]])
                    self.assertEqual(mp.mp.dps,precision)


    def test_crossvalidation_preserves_holdouts_models_and_metrics(self):
        with self.subTest(scenario='crossvalidation_predicts_without_holdout_leakage'):
            rows=[[i,1+2*i] for i in range(12)]; result=run('crossvalidate',rows,3,0)
            self.assertLess(float(result['MSE']),1e-20)
            changed=run('crossvalidate',[[i,100 if i==0 else y] for i,y in rows],3,0)
            self.assertAlmostEqual(float(changed['out-of-fold predictions'][0]),1)
        with self.subTest(scenario='crossvalidation_models_splits_and_metrics'):
            rows=[[i,1+2*i] for i in range(12)]
            exact=run('crossvalidate',rows,3,0)
            self.assertLess(float(exact['MSE']),1e-20)
            self.assertAlmostEqual(float(exact['R2']),1,places=9)
            for model,penalty in (('ridge',.5),('lasso',.05),('elasticnet',[.1,.5])):
                result=run('crossvalidate',rows,3,0,'random',model,penalty)
                self.assertEqual(result['model'],model)
                self.assertLess(float(result['MSE']),20.0)
                self.assertIn('MAE',result)
            self.assertEqual(run('crossvalidate',rows,3,0,'blocked','ridge',.5)['split'],'blocked')
            logistic=[[float(i%5),float(1 if i%5>=2 else 0)] for i in range(20)]
            result=run('crossvalidate',logistic,4,0,'stratified','logistic',.5)
            self.assertGreater(float(result['accuracy']),.6)
            self.assertGreater(float(result['AUC']),.8)
            self.assertIn('log loss',result)

    def test_imputation_methods_use_complete_cases(self):
        NA=s.Symbol('NA')
        rows=[[1,2],[2,4],[3,NA],[4,8],[5,10]]
        for method,figure in (('mean',6.0),('median',6.0),('mode',2.0),('regression',6.0),('knn',6.0)):
            result=run('impute',rows,method)
            self.assertAlmostEqual(float(result['data'][2][1]),figure,places=9,msg=method)
            self.assertEqual(int(result['imputed cells']),1)
            self.assertEqual(result['method'],method)
            for i,row in enumerate(result['data']):
                for j,value in enumerate(row):
                    if rows[i][j] is not NA: self.assertAlmostEqual(float(value),float(rows[i][j]),places=9)
        self.assertAlmostEqual(float(run('impute',rows,'knn',2)['data'][2][1]),6.0,places=9)


    def test_regression_coefficients_transform_back_from_scaled_units(self):
        with self.subTest(scenario='regression_coefficients_transform_back_from_scaled_units'):
            for name in ('poissonreg','nbreg','multinomial','ordinal','cox'):
                schema=json.loads((ROOT/'app/src/main/assets/advanced_statistics.json').read_text(encoding='utf-8'))
                example=next(item['example'] for item in schema if item['id']==name)
                rows=ast.literal_eval(ast.parse(example,mode='eval').body.args[0]); baseline=run(name,rows)
                column=2 if name=='cox' else 0
                shifted=[[v*1e-6+1 if j==column else v for j,v in enumerate(r)] for r in rows]
                actual=run(name,shifted)
                slope=0 if name in ('cox','ordinal') else 1
                self.assertAlmostEqual(float(actual['coefficients'][slope]['estimate'])*1e-6,float(baseline['coefficients'][slope]['estimate']),delta=2e-5)
            self.assertEqual(float(run('eta2',[1,1],[2,2])['eta2']),1)
        with self.subTest(scenario='count_exposure_is_log_offset_and_validates_row_alignment'):
            rows=[[0,1],[0,0],[1,3],[1,1],[2,2],[2,5],[3,4],[3,8],[4,6],[4,10]]
            exposure=[1+.3*(i%4) for i in range(len(rows))]
            first=run('poissonreg',rows,exposure,'exposure')
            second=run('poissonreg',rows,[math.log(v) for v in exposure])
            self.assertAlmostEqual(float(first['log likelihood']),float(second['log likelihood']),places=10)
            for invalid,mode in (([1],'offset'),([0]*len(rows),'exposure'),([-1]*len(rows),'exposure')):
                with self.assertRaises(MathError):run('poissonreg',rows,invalid,mode)
            clustered=[[g,t,(g+t)%2] for g in range(1,5) for t in range(3)]
            for args in ((clustered,'gamma'),(clustered,'binomial',2),(clustered,'binomial',15,[1]*len(clustered)),(clustered,'poisson',15,[1]),(clustered,'binomial',15,[0]*len(clustered),'unknown')):
                with self.assertRaises(MathError):run('glmm',*args)


    def test_independent_reference_fixtures(self):
        fixtures=ROOT/'tests/fixtures/advanced_statistics_reference.json'
        if not fixtures.exists(): self.fail('Reference fixtures must be checked in')
        for case in json.loads(fixtures.read_text(encoding='utf-8')):
            with self.subTest(case=case['name']):
                value=run(case['function'],*case['arguments'])
                for path,expected in case['expected']:
                    actual=value
                    for key in path: actual=actual[key]
                    self.assertAlmostEqual(float(actual),expected,delta=case.get('tolerance',1e-6)*max(1,abs(expected)))

    def test_gaussian_gee_correlation_is_invariant_to_response_units(self):
        with self.subTest(scenario='gaussian_gee_correlation_is_invariant_to_response_units'):
            rows=[[g,t,2+.4*t+(g%4-1.5)*.7+((g+2*t)%5-2)*.2] for g in range(12) for t in range(3+g%3)]
            for corr in ('exchangeable','ar1'):
                base=run('gee',rows,'gaussian',corr)
                scaled=run('gee',[[g,t,10*y+30] for g,t,y in rows],'gaussian',corr)
                self.assertAlmostEqual(float(base['alpha']),float(scaled['alpha']),places=8)
                for i in range(2):
                    self.assertAlmostEqual(float(scaled['coefficients'][i]['estimate']),10*float(base['coefficients'][i]['estimate'])+(30 if i==0 else 0),places=7)
                    self.assertAlmostEqual(float(scaled['coefficients'][i]['SE']),10*float(base['coefficients'][i]['SE']),places=7)
            self.assertIn('Few clusters',evaluate('gee('+str(rows)+',gaussian,exchangeable)')['note'])
        with self.subTest(scenario='gee_interaction_terms_expand_the_design_in_original_units'):
            base=[[0,0,1],[1,0,3],[0,1,4],[1,1,10]]
            rows=[[cluster]+row for cluster in (1,2,3) for row in base]
            result=run('gee',rows,'gaussian','independence',[[1,2]])
            self.assertEqual([term['term'] for term in result['coefficients']],['Intercept','x1','x2','x1:x2'])
            for actual,expected in zip(result['coefficients'],[1,2,3,4]):
                self.assertAlmostEqual(float(actual['estimate']),expected,places=8)
            from calc_shared import MathError
            for invalid in ([1,3],[[1,1],[1,1]]):
                with self.assertRaises(MathError):run('gee',rows,'gaussian','independence',invalid)

    def test_mixed_models_report_boundary_variances_and_random_effects(self):
        with self.subTest(scenario='mixed_slope_boundary_has_blups_and_explicit_icc_origin'):
            noise=[.1,-.2,.2,-.2,.1]
            rows=[[g,t,2+.4*t+(g%5-2)*.6+noise[t+2]] for g in range(1,16) for t in range(-2,3)]
            result=run('mixedmodel',rows,1)
            self.assertEqual(int(result['singular fit']),1)
            self.assertAlmostEqual(float(result['random slope variance']),0,places=6)
            self.assertIn('ICC at x=0',result)
            self.assertNotIn('ICC',result)
            self.assertEqual(len(result['subject random effects (BLUP)']),15)
            self.assertAlmostEqual(sum(float(row['x1']) for row in result['subject random effects (BLUP)']),0,places=6)
        with self.subTest(scenario='glmm_zero_variance_matches_independent_glm_and_nodes'):
            rows=[[g,t,(g+t)%2] for g in range(1,9) for t in range(3)]
            for points in (1,15,25):
                result=run('glmm',rows,'binomial',points)
                self.assertEqual(float(result['random intercept variance']),0)
                self.assertAlmostEqual(float(result['log likelihood']),-24*math.log(2),places=8)
                self.assertAlmostEqual(float(result['coefficients'][0]['SE']),math.sqrt(5/12),places=6)
                self.assertTrue(all(float(row['posterior mean'])==0 for row in result['subject random effects']))
            counts=[[g,t,v] for g in range(1,6) for t,v in enumerate([1,2,1,3,2])]
            glm=run('poissonreg',[[t,v] for _,t,v in counts])
            result=run('glmm',counts,'poisson')
            self.assertEqual(float(result['random intercept variance']),0)
            for actual,expected in zip(result['coefficients'],glm['coefficients']):
                self.assertAlmostEqual(float(actual['estimate']),float(expected['estimate']),places=6)
                self.assertAlmostEqual(float(actual['SE']),float(expected['SE']),places=6)

class BayesianStatisticsTests(unittest.TestCase):
    def test_bayesian_distributions_match_references_and_prior_moment_constraints(self):
        with self.subTest(scenario='independent_distribution_references'):
            fixtures = json.loads((ROOT/'tests/fixtures/bayesian_statistics_reference.json').read_text())
            for case in fixtures:
                with self.subTest(function=case['function'], arguments=case['arguments']):
                    result = run(case['function'], *case['arguments'])
                    for key, expected in case['expected'].items():
                        pairs = zip(result[key], expected) if isinstance(expected, list) else [(result[key], expected)]
                        for actual, reference in pairs:
                            self.assertTrue(math.isclose(float(actual), reference, rel_tol=case['tolerance'], abs_tol=1e-12), (key, actual, reference))
        with self.subTest(scenario='mean_prior_update_prediction_and_undefined_moments'):
            result = run('bayesmean', [1,3], 2, 2, 1, 1, .95, 2)
            for key, value in [('posterior mean',2), ('posterior kappa',4), ('posterior alpha',2), ('posterior beta',2), ('mean posterior t scale',.5), ('P(mean > threshold)',.5)]:
                self.assertEqual(float(result[key]), value)
            self.assertGreater(float(result['predictive interval'][1]-result['predictive interval'][0]), float(result['credible interval'][1]-result['credible interval'][0]))
            heavy = run('bayesmean', [1], 0, 1, .1, 1)
            self.assertEqual(heavy['posterior SD'], 'unavailable')
            self.assertEqual(heavy['posterior variance mean'], 'unavailable')
            constant = run('bayesmean', [3,3,3])
            self.assertLess(float(constant['credible interval'][0]), float(constant['posterior mean']))
            shifted = run('bayesmean', [101,103], 102, 2, 1, 1, .95, 102)
            self.assertAlmostEqual(float(shifted['credible interval'][0]-result['credible interval'][0]), 100)


    def test_invalid_inputs_and_selected_model_constraints(self):
        invalid = [('bayesproportion', [[2]]), ('bayesproportion', [[1,-1]]),
                   ('bayesproportion', [[[3,2]]]), ('bayesproportion', [[[0,0]]]),
                   ('bayesproportion', [[1],0]), ('bayesproportion', [[1],1,1,.95,1]),
                   ('bayesrate', [[.5]]), ('bayesrate', [[[1,0]]]),
                   ('bayesrate', [[[1,-1]]]), ('bayesrate', [[[1,2,3]]]),
                   ('bayesrate', [[0],1,-1]), ('bayesrate', [[0],1,1,1]),
                   ('bayesmean', [[]]), ('bayesmean', [[1],0,0]),
                   ('bayesmean', [[1],0,1,-1]), ('bayesmean', [[1],0,1,2,0])]
        for name, args in invalid:
            with self.subTest(name=name,args=args), self.assertRaises(MathError): run(name,*args)

if __name__ == "__main__": unittest.main()
