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
    def test_registered_analyses_reject_missing_and_excess_arguments(self):
        definitions=json.loads((ROOT/'app/src/main/assets/advanced_statistics.json').read_text(encoding='utf-8'))
        self.assertEqual(FUNCTIONS,{item['id'] for item in definitions})
        for name in FUNCTIONS:
            for args in ([],[s.Integer(0)]*21):
                with self.subTest(function=name,count=len(args)):
                    with self.assertRaisesRegex(MathError,name+' argument count mismatch'):
                        advanced(Engine({}),name,args)

    def test_public_entry_preserves_precision_after_success_and_failure(self):
        rows=[[1,2],[2,1],[3,4],[4,3],[5,7]]
        expected=run('pca',rows)
        for precision in (10,40):
            with mp.workdps(precision):
                self.assertEqual(run('pca',rows),expected)
                self.assertEqual(mp.mp.dps,precision)
                with self.assertRaisesRegex(MathError,'Standardized PCA requires nonconstant columns'):
                    run('pca',[[1,2],[1,3]])
                self.assertEqual(mp.mp.dps,precision)

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
        for field in ('exact','decimal','tree','decimalTree'):
            self.assertEqual(json.dumps(labelled[field]).replace('treatment (z)','x1'),json.dumps(original[field]))
        self.assertIn('term: x1',original['exact'])

    def test_all_visible_examples_evaluate_through_public_dispatch(self):
        for item in json.loads((ROOT/'app/src/main/assets/advanced_statistics.json').read_text(encoding='utf-8')):
            with self.subTest(function=item['id']):
                result=evaluate(item['example'])
                self.assertEqual(result['tree']['kind'],'rows')
                self.assertIn('binary64',result['note'])

    def test_adjustment_preserves_order_ties_and_bounds(self):
        values=[.04,.01,.03,.2]
        for method,expected in [('bonferroni',[.16,.04,.12,.8]),('holm',[.09,.04,.09,.2]),('fdr',[.0533333333333333,.04,.0533333333333333,.2])]:
            actual=run('padjust',values,method)['adjusted p']
            for x,y in zip(actual,expected): self.assertAlmostEqual(float(x),y,places=12)
        self.assertEqual(list(map(float,run('padjust',[0,1,0], 'fdr')['adjusted p'])),[0,1,0])

    def test_mcnemar_exact_and_no_discordance(self):
        self.assertAlmostEqual(float(run('mcnemar',[[20,8],[2,15]])['p']),.109375)
        self.assertEqual(float(run('mcnemar',[[3,0],[0,5]])['p']),1)
        self.assertAlmostEqual(float(run('mcnemar',[[20,8],[2,15]],'corrected')['chi2']),2.5)

    def test_kaplan_meier_ties_and_nonreached_median(self):
        result=run('kaplanmeier',[[1,1],[1,0],[2,1],[3,0]])
        self.assertEqual(list(map(float,result['survival table'][0][:5])),[1,4,1,1,.75])
        self.assertAlmostEqual(float(result['survival table'][1][4]),.375)
        self.assertEqual(float(result['median survival']),2)
        self.assertEqual(run('kaplanmeier',[[1,0],[2,0]])['median survival'],'unavailable')
        self.assertEqual(float(run('kaplanmeier',[[1,1],[1,1]])['survival table'][0][4]),0)

    def test_survival_suite_preserves_ties_ci_and_two_group_logrank(self):
        rows=[[1,1,1],[1,0,1],[2,1,1],[3,0,1],[1,0,2],[2,1,2],[3,1,2],[4,0,2]]
        report=evaluate('survivalanalysis('+str(rows)+',0)')['survival']
        curve=report['groups'][0]['curve']
        self.assertEqual(curve[0][:5],[1,4,1,1,.75])
        reference=run('kaplanmeier',[r[:2] for r in rows if r[2]==1])['survival table']
        for actual,expected in zip(curve,reference):
            for x,y in zip(actual,expected):self.assertAlmostEqual(x,float(y),places=12)
        lr=run('logrank',[r[:2] for r in rows if r[2]==1],[r[:2] for r in rows if r[2]==2])
        for key in ['chi2','df','p']:self.assertAlmostEqual(report['logrank'][key],float(lr[key]),places=12)

    def test_survival_multigroup_is_invariant_to_label_and_row_order(self):
        rows=[[1,1,1],[2,0,1],[2,1,2],[4,1,2],[3,1,3],[5,0,3]]
        first=evaluate('survivalanalysis('+str(rows)+')')['survival']
        second=evaluate('survivalanalysis('+str([[t,e,4-g] for t,e,g in reversed(rows)])+')')['survival']
        self.assertEqual(first['logrank']['df'],2)
        self.assertGreater(first['logrank']['chi2'],0)
        self.assertAlmostEqual(first['logrank']['chi2'],second['logrank']['chi2'],places=12)
        self.assertAlmostEqual(first['logrank']['p'],second['logrank']['p'],places=12)

    def test_survival_censor_only_and_single_subject_groups_keep_curves(self):
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

    def test_survival_cox_group_dummy_matches_reference_and_exponentiates_ci(self):
        fixture=next(x for x in json.loads((ROOT/'tests/fixtures/advanced_statistics_reference.json').read_text()) if x['name']=='cox')
        original=fixture['arguments'][0]
        rows=[r[:2]+[1+r[2]] for r in original]
        report=evaluate('survivalanalysis('+str(rows)+',1)')['survival']
        actual=report['cox']['coefficients'][0]
        expected=run('cox',original)['coefficients'][0]
        self.assertAlmostEqual(actual['HR'],float(expected['exp(coef)']),places=7)
        self.assertEqual(actual['term'],'group:1')
        for x,y in zip(actual['HR CI95'],expected['CI95']):self.assertAlmostEqual(x,math.exp(float(y)),places=7)
        with self.assertRaises(AssertionError):evaluate('survivalanalysis([[1,2,1],[2,0,2]])')


    def test_survival_ph_check_is_scaled_schoenfeld_score(self):
        rows=[[1,1,0],[2,1,1],[3,0,0],[4,1,1],[5,1,0],[6,0,1],[7,1,1],[8,1,0]]
        fit=run('cox',rows,'efron',-1,1)
        self.assertEqual(int(fit['PH test df']),1)
        self.assertEqual([term['term'] for term in fit['PH test per covariate']],['x1'])
        self.assertAlmostEqual(float(fit['PH test chi2']),float(fit['PH test per covariate'][0]['chi2']),places=12)
        # The rank time transform keeps the statistic invariant to rescaled times.
        moved=run('cox',[[5*t+1000,e,x] for t,e,x in rows],'efron',-1,1)
        self.assertAlmostEqual(float(fit['PH test chi2']),float(moved['PH test chi2']),places=10)

    def test_survival_ph_check_reports_per_covariate_statistics(self):
        rows=[[1,1,0,1],[2,1,1,0],[3,0,0,1],[4,1,1,1],[5,1,0,1],[6,0,1,0],[7,1,1,0],[8,1,0,1],[9,1,1,1],[10,0,0,0],[11,1,1,1],[12,1,0,0]]
        fit=run('cox',rows,'efron',-1,1)
        self.assertEqual(int(fit['PH test df']),2)
        terms=fit['PH test per covariate']
        self.assertEqual([term['term'] for term in terms],['x1','x2'])
        self.assertTrue(all(int(term['df'])==1 for term in terms))
        report=evaluate('survivalanalysis('+str([[r[0],r[1],1+r[2],r[3]] for r in rows])+',1)')['survival']
        self.assertIn('PH test p',report['cox'])

    def test_survival_ph_check_distinguishes_crossing_from_proportional_hazards(self):
        crossing=[[1,1,1],[2,1,1],[3,1,1],[4,1,1],[5,1,1],[6,1,0],[7,1,0],[8,1,0],[9,1,0],[10,1,0],[11,0,1],[12,0,0]]
        holding=[[1,1,0],[2,1,1],[3,0,0],[4,1,1],[5,1,0],[6,0,1],[7,1,1],[8,1,0],[9,1,0],[10,1,1],[11,1,0],[12,0,1],[13,1,1],[14,1,0],[15,0,0],[16,1,1]]
        bad=run('cox',crossing,'efron',-1,1); good=run('cox',holding,'efron',-1,1)
        self.assertGreater(float(bad['PH test chi2']),float(good['PH test chi2']))
        self.assertLess(float(bad['PH test p']),.05)
        self.assertGreater(float(good['PH test p']),.2)
        self.assertLess(float(run('cox',crossing,'breslow',-1,1)['PH test p']),.05)

    def test_poisson_standard_errors_use_analytic_information(self):
        rows=[[0,1],[0,0],[1,3],[1,1],[2,2],[2,5],[3,4],[3,8],[4,6],[4,10]]
        result=run('poissonreg',rows)
        beta=[float(c['estimate']) for c in result['coefficients']]
        means=[math.exp(beta[0]+beta[1]*r[0]) for r in rows]
        information=[[sum(m*(1.0 if i==0 else r[0])*(1.0 if j==0 else r[0]) for r,m in zip(rows,means)) for j in range(2)] for i in range(2)]
        determinant=information[0][0]*information[1][1]-information[0][1]*information[1][0]
        for index,coefficient in enumerate(result['coefficients']):
            self.assertAlmostEqual(float(coefficient['SE']),math.sqrt(information[1-index][1-index]/determinant),places=12)

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

    def test_pca_reconstructs_and_explains_all_variance(self):
        rows=[[1,2],[2,1],[3,4],[4,3],[5,7]]; result=run('pca',rows,2,1)
        self.assertAlmostEqual(sum(map(float,result['explained variance ratio'])),1)
        for r,score in zip(rows,result['scores']):
            for j in range(2):
                reconstructed=sum(float(score[k]*result['loadings'][j][k]) for k in range(2))*float(result['scales'][j])+float(result['centers'][j])
                self.assertAlmostEqual(reconstructed,r[j],places=10)

    def test_clustering_and_imputation(self):
        result=run('kmeans',[[1,1],[1,2],[2,1],[8,8],[8,9],[9,8]],2,0)
        labels=result['labels (1-based)']; self.assertEqual(labels[:3],[labels[0]]*3); self.assertEqual(labels[3:],[labels[3]]*3); self.assertNotEqual(labels[0],labels[3])
        self.assertAlmostEqual(float(result['inertia']),8/3)
        imputed=run('impute',[[1,s.Symbol('NA')],[2,4],[s.Symbol('NA'),6],[4,8]])
        self.assertAlmostEqual(float(imputed['data'][2][0]),7/3)
        self.assertEqual(float(imputed['data'][0][1]),6)

    def test_crossvalidation_predicts_without_holdout_leakage(self):
        rows=[[i,1+2*i] for i in range(12)]; result=run('crossvalidate',rows,3,0)
        self.assertLess(float(result['MSE']),1e-20)
        changed=run('crossvalidate',[[i,100 if i==0 else y] for i,y in rows],3,0)
        self.assertAlmostEqual(float(changed['out-of-fold predictions'][0]),1)

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

    def test_crossvalidation_models_splits_and_metrics(self):
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

    def test_mixed_model_rows_slopes_and_reml(self):
        rows=[]
        for subject in range(1,41):
            intercept=(subject%5-2)*0.6
            effect=(subject%7-3)*0.2
            for point in range(8):
                rows.append([subject,point,2.0+0.5*point+intercept+effect*point+((subject*3+point)%5-2)*0.05])
        self.assertEqual(len(rows),320)
        ml=run('mixedmodel',rows,0)
        self.assertEqual(int(ml['subjects']),40)
        self.assertAlmostEqual(float(ml['coefficients'][0]['estimate']),2.0,places=3)
        self.assertAlmostEqual(float(ml['coefficients'][1]['estimate']),0.5,places=3)
        self.assertEqual(ml['estimation'],'ML')
        reml=run('mixedmodel',rows,0,'reml')
        self.assertEqual(reml['estimation'],'REML')
        self.assertGreater(float(reml['random intercept variance']),0)
        slopes=run('mixedmodel',rows,[1])
        self.assertGreater(float(slopes['random slope variance']),.02)
        self.assertLess(float(slopes['random slope variance']),.6)

    def test_regression_coefficients_transform_back_from_scaled_units(self):
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

    def test_gee_interaction_terms_expand_the_design_in_original_units(self):
        base=[[0,0,1],[1,0,3],[0,1,4],[1,1,10]]
        rows=[[cluster]+row for cluster in (1,2,3) for row in base]
        result=run('gee',rows,'gaussian','independence',[[1,2]])
        self.assertEqual([term['term'] for term in result['coefficients']],['Intercept','x1','x2','x1:x2'])
        for actual,expected in zip(result['coefficients'],[1,2,3,4]):
            self.assertAlmostEqual(float(actual['estimate']),expected,places=8)
        from calc_shared import MathError
        for invalid in ([1,3],[[1,1],[1,1]]):
            with self.assertRaises(MathError):run('gee',rows,'gaussian','independence',invalid)

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

    def test_invalid_inputs_fail_without_plausible_results(self):
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


if __name__=='__main__': unittest.main()
