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

    def test_mcnemar_methods_show_discordant_count_and_matching_p_value(self):
        for method,title,p in [('asymptotic','McNemar',0.05777957112359715),
                               ('exact','Exact McNemar',0.109375),
                               ('corrected','McNemar (continuity correction)',0.11384629800665763)]:
            with self.subTest(method=method):
                result=evaluate(f'mcnemar([[20,8],[2,15]],{method})')
                report=result['statisticsReport']
                self.assertEqual(report['title'],title)
                self.assertEqual([item['label'] for item in report['highlights']],['discordant pairs','p value'])
                self.assertEqual(report['highlights'][0]['value']['exact'],'10')
                self.assertAlmostEqual(float(report['highlights'][1]['value']['decimal']),p,places=12)
                self.assertIn('discordant pairs: 10',result['exact'])
                empty=evaluate(f'mcnemar([[20,0],[0,15]],{method})')['statisticsReport']['highlights']
                self.assertEqual([item['value']['exact'] for item in empty],['0','1'])

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

    def test_advanced_analyses_reject_invalid_arity_and_data(self):
        with self.subTest(scenario='registered_analyses_reject_missing_and_excess_arguments'):
            definitions=json.loads((ROOT/'app/src/main/assets/advanced_statistics.json').read_text(encoding='utf-8'))
            self.assertEqual(FUNCTIONS,{item['id'] for item in definitions if item['id'] not in ('shapiro','tukey','gameshowell')})
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

    def test_lmm_defaults_to_reml_with_or_without_random_slopes(self):
        fixtures=json.loads((ROOT/'tests/fixtures/advanced_statistics_reference.json').read_text(encoding='utf-8'))
        for case in fixtures:
            if case['function']!='mixedmodel': continue
            args=case['arguments']
            if len(args)>2 and args[2]!='reml': continue
            with self.subTest(case=case['name']):
                rows=args[0]; slope=args[1] if len(args)>1 else 0
                result=run('mixedmodel',rows,slope)
                self.assertEqual(result['estimation'],'REML')
                for path,expected in case['expected']:
                    actual=result
                    for key in path: actual=actual[key]
                    self.assertAlmostEqual(float(actual),expected,delta=case.get('tolerance',1e-6)*max(1,abs(expected)))
        with self.assertRaises(MathError): run('mixedmodel',rows,0,'invalid')

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
