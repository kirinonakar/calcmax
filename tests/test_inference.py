"""Frozen SciPy/statsmodels reference values plus inference edge cases.

The production engine and this suite do not need either reference package.
"""
import pathlib
import sys
import unittest
import math
import itertools
import random

sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]/'app/src/main/python'))
import sympy as s
from calc_evaluator import Engine
from calc_statistics import fit_regression
from calc_inference import rank_test, _binary_roc
import mpmath as mp
from calc_shared import MathError

def fit(rows, mode='linear', degree=None):
    engine=Engine({'precision':40})
    result=fit_regression(engine,[[s.sympify(v) for v in row] for row in rows],mode,s.Integer(degree) if degree else None)
    return result,engine.regression_report

class InferenceTests(unittest.TestCase):
    def test_ols_coefficient_inference_and_influence_match_statsmodels(self):
        with self.subTest(scenario='ols_coefficient_inference_and_influence_match_statsmodels'):
            _,report=fit(list(zip(range(1,7),[2,4,5,4,5,7])))
            self.assertAlmostEqual(float(report['rSquared']),.7714285714285716,places=13)
            self.assertAlmostEqual(float(report['adjustedRSquared']),.7142857142857144,places=13)
            for coefficient,reference in zip(report['coefficients'],[
                    (1.8,.8176621726430966,-.470194136940318,4.070194136940319,.09250837267236314),
                    (.7714285714285716,.20995626366712966,.18849653086435214,1.354360611992791,.02131164112875674)]):
                for key,expected in zip(['estimate','se','low','high','p'],reference):
                    self.assertAlmostEqual(float(coefficient[key]),expected,places=12)
            self.assertAlmostEqual(float(report['durbinWatson']),2.029100529100529,places=12)
            self.assertAlmostEqual(float(report['residuals'][0]['leverage']),.5238095238095242,places=12)
            self.assertAlmostEqual(float(report['residuals'][0]['cook']),.48888888888889137,places=12)
            self.assertEqual(len(report['residuals']),6)
        with self.subTest(scenario='insufficient_df_constant_response_and_perfect_fit'):
            for rows in ([(1,2),(2,3)],[(1,2),(2,2),(3,2)],[(1,3),(2,5),(3,7)]):
                _,report=fit(rows)
                if len(rows)==2:self.assertIsNone(report['coefficients'][0]['se']);self.assertTrue(report['warnings'])
                if rows[0][1]==rows[-1][1]:self.assertIsNone(report['rSquared'])
                self.assertIsNone(report['coefficients'][0]['p'])

    def test_multiple_regression_scale_and_singular_predictors(self):
        with self.subTest(scenario='multiple_regression_scale_and_singular_predictors'):
            rows=[(a,b,1+2*a-3*b+s.Rational((i%3)-1,10)) for i,(a,b) in enumerate([(0,0),(1,0),(0,1),(1,1),(2,0),(0,2),(2,2),(3,1)])]
            result,report=fit(rows,'multiple')
            self.assertEqual(result.free_symbols,{s.Symbol('x1'),s.Symbol('x2')})
            self.assertEqual(len(report['coefficients']),3)
            transformed=[(10**9+a*10**-6,b*10**6,y) for a,b,y in rows]
            # Preserve the exact tiny offsets rather than rounding them through binary64.
            transformed=[(s.Integer(10**9)+s.Rational(a,10**6),b*10**6,y) for a,b,y in rows]
            _,scaled=fit(transformed,'multiple')
            self.assertAlmostEqual(float(scaled['rSquared']),float(report['rSquared']),places=12)
            self.assertIsNotNone(scaled['coefficients'][0]['se'])
            with self.assertRaises(MathError):fit([(1,2,3),(2,4,5),(3,6,8),(4,8,10)],'multiple')
        with self.subTest(scenario='vif_uses_predictors_only_and_is_invariant_to_units_and_response'):
            predictors=[(-1,-2),(-1,0),(1,0),(1,2)]
            rows=[(a,b,1+2*a+3*b) for a,b in predictors]
            _,report=fit(rows,'multiple')
            self.assertIsNone(report['coefficients'][0]['vif'])
            for coefficient in report['coefficients'][1:]:
                self.assertAlmostEqual(float(coefficient['vif']),2,places=12)
            changed=[(10**9+s.Rational(a,10**6),b*10**6,y*y) for a,b,y in rows]
            _,scaled=fit(changed,'multiple')
            self.assertEqual([c['vif'] for c in report['coefficients']], [c['vif'] for c in scaled['coefficients']])
            _,logistic=fit([(a,b,response) for a,b in predictors for response in (0,1)],'logistic')
            for coefficient in logistic['coefficients'][1:]:
                self.assertAlmostEqual(float(coefficient['vif']),2,places=12)
            _,single=fit([(-3,0),(-2,0),(-1,1),(0,0),(0,1),(1,0),(2,1),(3,1)],'logistic')
            self.assertEqual(float(single['coefficients'][1]['vif']),1)

    def test_logistic_inference_matches_statsmodels_and_rejects_invalid_responses(self):
        rows=[(-3,0),(-2,0),(-1,1),(0,0),(0,1),(1,0),(2,1),(3,1)]
        result,report=fit(rows,'logistic')
        slope=report['coefficients'][1]
        for key,expected in [('estimate',.7324875300102196),('se',.552327460096075),('low',-.3500543994505714),('high',1.8150294594710106),('p',.18477894376948212)]:
            self.assertAlmostEqual(float(slope[key]),expected,places=12)
        self.assertAlmostEqual(float(report['pseudoRSquared']),.23126385277910777,places=12)
        self.assertAlmostEqual(float(report['likelihoodP']),.10926649982714333,places=12)
        self.assertAlmostEqual(float(report['auc']),.78125,places=12)
        self.assertEqual(report['roc'][0],['0.0','0.0'])
        self.assertEqual(report['roc'][-1],['1.0','1.0'])
        self.assertAlmostEqual(float(slope['oddsRatio']),math.exp(float(slope['estimate'])),places=12)
        # Independent weighted 2x2 information-matrix formula for the hat diagonal.
        beta=[float(c['estimate']) for c in report['coefficients']]
        probabilities=[1/(1+math.exp(-beta[0]-beta[1]*x)) for x,_ in rows]
        weights=[q*(1-q) for q in probabilities]
        a=sum(weights);b=sum(w*x for w,(x,_) in zip(weights,rows));c=sum(w*x*x for w,(x,_) in zip(weights,rows))
        for (x,y),q,w,residual in zip(rows,probabilities,weights,report['residuals']):
            expected_h=w*(c-2*b*x+a*x*x)/(a*c-b*b)
            expected_cook=(y-q)**2/(q*(1-q))*expected_h/(2*(1-expected_h)**2)
            self.assertAlmostEqual(float(residual['leverage']),expected_h,places=12)
            self.assertAlmostEqual(float(residual['cook']),expected_cook,places=12)
        for x,_ in rows:self.assertTrue(0<float(result.subs(s.Symbol('x'),x))<1)
        self.assertEqual(report['method'],'mle')
        for invalid in [[(1,0),(2,0),(3,0),(4,0)],[(1,0),(2,2),(3,1),(4,0)]]:
            with self.assertRaises(MathError):fit(invalid,'logistic')
        _,wide=fit([(-1000,0),(-5,0),*rows,(5,1),(1000,1)],'logistic')
        self.assertGreater(float(wide['coefficients'][1]['estimate']),.1)
        self.assertIsNotNone(wide['coefficients'][1]['se'])
        _,multivariate=fit([(a,b,response) for a,b in [(0,0),(1,0),(0,1),(1,1)] for response in (0,1)],'logistic')
        self.assertEqual(len(multivariate['coefficients']),3)
        self.assertEqual(multivariate['n'],8)

    def test_complete_separation_firth_matches_exact_two_group_solution_and_influence(self):
        with self.subTest(scenario='complete_separation_firth_matches_exact_two_group_solution_and_influence'):
            rows=[(0,0)]*4+[(1,1)]*6
            _,report=fit(rows,'logistic')
            self.assertEqual(report['method'],'firth')
            self.assertEqual(report['separation'],'complete')
            self.assertEqual(report['intervalMethod'],'profile')
            estimate=float(report['coefficients'][1]['estimate'])
            self.assertLess(float(report['coefficients'][1]['low']),estimate)
            self.assertGreater(float(report['coefficients'][1]['high']),estimate)
            # The saturated binary-predictor Firth solution adds 1/2 to each cell.
            self.assertAlmostEqual(float(report['coefficients'][0]['estimate']),math.log(1/9),places=12)
            self.assertAlmostEqual(float(report['coefficients'][1]['estimate']),math.log(117),places=12)
            self.assertAlmostEqual(float(report['coefficients'][1]['oddsRatio']),117,places=10)
            self.assertAlmostEqual(float(report['coefficients'][1]['se']),math.sqrt(1/(4*.1*.9)+1/(6*(13/14)*(1/14))),places=12)
            self.assertEqual(float(report['auc']),1)
            self.assertNotIn('aic',report);self.assertNotIn('likelihoodP',report)
            for i,row in enumerate(report['residuals']):
                q,n=(.1,4) if i<4 else (13/14,6)
                y=rows[i][1];h=1/n
                self.assertAlmostEqual(float(row['fitted']),q,places=12)
                self.assertAlmostEqual(float(row['leverage']),h,places=12)
                self.assertAlmostEqual(float(row['cook']),(y-q)**2/(q*(1-q))*h/(2*(1-h)**2),places=12)
        with self.subTest(scenario='firth_multivariate_score_stationarity_units_and_response_flip'):
            rows=[(-2,-1,0),(-1,2,0),(-1,-2,0),(1,1,1),(2,-2,1),(2,3,1)]
            _,report=fit(rows,'logistic')
            self.assertEqual(report['method'],'firth')
            with mp.workdps(60):
                beta=[mp.mpf(c['estimate']) for c in report['coefficients']]
                x=mp.matrix([[1,a,b] for a,b,_ in rows]);y=[mp.mpf(v) for _,_,v in rows]
                def objective(values):
                    z=x*mp.matrix(values)
                    q=[1/(1+mp.exp(-v)) for v in z]
                    info=x.T*mp.diag([v*(1-v) for v in q])*x
                    return mp.fsum(t*mp.log(v)+(1-t)*mp.log(1-v) for t,v in zip(y,q))+mp.log(mp.det(info))/2
                for j in range(3):
                    derivative=mp.diff(lambda value:objective(beta[:j]+[value]+beta[j+1:]),beta[j])
                    self.assertLess(abs(derivative),mp.mpf('1e-20'))
            transformed=[(s.Integer(10**9)+s.Rational(a,10**6),b*10**6,y) for a,b,y in rows]
            _,scaled=fit(transformed,'logistic')
            _,flipped=fit([(a,b,1-y) for a,b,y in rows],'logistic')
            for actual,changed,opposite in zip(report['residuals'],scaled['residuals'],flipped['residuals']):
                self.assertAlmostEqual(float(actual['fitted']),float(changed['fitted']),places=12)
                self.assertAlmostEqual(float(actual['leverage']),float(changed['leverage']),places=12)
                self.assertAlmostEqual(float(actual['cook']),float(changed['cook']),places=12)
                self.assertAlmostEqual(float(actual['fitted'])+float(opposite['fitted']),1,places=12)
                self.assertAlmostEqual(float(actual['cook']),float(opposite['cook']),places=12)
            self.assertAlmostEqual(sum(float(r['leverage']) for r in report['residuals']),3,places=12)
        with self.subTest(scenario='firth_certificate_does_not_mask_singular_designs_or_label_zero_margins_complete'):
            with self.assertRaises(MathError):fit([(-2,-4,0),(-1,-2,0),(1,2,1),(2,4,1)],'logistic')
            # Opposite responses at the same predictor cannot be strictly separated.
            from calc_firth import complete_separation
            rows=[[s.Integer(x),s.Integer(y)] for x,y in [(-1,0),(0,0),(0,1),(1,1)]]
            design=[[mp.mpf(1),mp.mpf(row[0])] for row in rows]
            self.assertFalse(complete_separation(rows,design,[mp.mpf(row[-1]) for row in rows],mp.matrix([0,1]),mp.eye(2)))

class RankTests(unittest.TestCase):
    def run_test(self,name,groups,tail='both'):
        engine=Engine({'precision':40})
        return rank_test(engine,name,[[s.sympify(v) for v in group] for group in groups],tail)

    def test_rank_tests_preserve_exact_probabilities_ties_and_pairing(self):
        with self.subTest(scenario='mann_whitney_exact_unequal_sizes_and_tie_correction'):
            for groups,expected in [([[1,2,3],[4,5,6]],.1),([[1,2],[3,4,5,6,7]],.09523809523809523),([[1,2,2,4],[2,3,3,5]],.2974830944159319)]:
                for tail,factor in [('both',1),('left',.5)]:
                    result=self.run_test('mannwhitney',groups,tail)
                    self.assertAlmostEqual(float(result['p value']),expected*factor,places=12)
            self.assertAlmostEqual(float(self.run_test('mannwhitney',[[1,2,2,4],[2,3,3,5]],'right')['p value']),.9097986668551219,places=12)
            self.assertEqual(float(self.run_test('mannwhitney',[[1,1],[1,1]])['p value']),1)
        with self.subTest(scenario='wilcoxon_exact_ties_zeros_and_direction'):
            self.assertEqual(float(self.run_test('wilcoxon',[[1,2,3,4,5]])['p value']),.0625)
            self.assertEqual(float(self.run_test('wilcoxon',[[1,-2,3,-4,5]])['p value']),.8125)
            differences=[0,1,1,-2,3]
            # Independently enumerate the tied midrank sign permutations.
            ranks=[1.5,1.5,3,4];observed=1.5+1.5+4
            sums=[sum(r*sign for r,sign in zip(ranks,signs)) for signs in itertools.product((0,1),repeat=4)]
            expected=2*min(sum(v<=observed for v in sums),sum(v>=observed for v in sums))/len(sums)
            result=self.run_test('wilcoxon',[differences])
            self.assertEqual(float(result['p value']),expected)
            self.assertEqual(result['zero differences'],1)
            self.assertEqual(float(self.run_test('wilcoxon',[[1,2,3,4,5]],'right')['p value']),.03125)
            with self.assertRaises(MathError):self.run_test('wilcoxon',[[0,0]])
            with self.assertRaises(MathError):self.run_test('wilcoxon',[[1],[1,2]])
        with self.subTest(scenario='kruskal_matches_scipy_tied_ranks'):
            result=self.run_test('kruskal',[[1,2,3,4,5],[4,5,6,7,8],[7,8,9,10,11]])
            self.assertAlmostEqual(float(result['H']),10.656115107913667,places=12)
            self.assertAlmostEqual(float(result['p value']),.004853488501369138,places=12)
            with self.assertRaises(MathError):self.run_test('kruskal',[[1,1],[1,1]])
            with self.assertRaises(MathError):self.run_test('kruskal',[[1],[2]],'left')

class RocTests(unittest.TestCase):
    def test_roc_handles_ties_concordance_and_invalid_inputs(self):
        with self.subTest(scenario='reference_curve_perfect_reversed_and_tied_scores'):
            area,points=_binary_roc([0,0,1,1],[.1,.4,.35,.8])
            self.assertEqual(float(area),.75)
            self.assertEqual([[float(x),float(y)] for x,y in points],[[0,0],[0,.5],[.5,.5],[.5,1],[1,1]])
            for labels,scores,expected in [([0,1],[.1,.9],1),([0,1],[.9,.1],0),([0,1],[1,1],.5),([1,0,1,0],[.8,.8,.6,.2],.625),([0,1],[1000,1001],1)]:
                self.assertEqual(float(_binary_roc(labels,scores)[0]),expected)
        with self.subTest(scenario='auc_matches_independent_pairwise_concordance_for_ties'):
            randomizer=random.Random(105)
            for _ in range(50):
                labels=[0]*randomizer.randint(1,8)+[1]*randomizer.randint(1,8)
                scores=[randomizer.randint(-3,3) for _ in labels]
                positive=[v for y,v in zip(labels,scores) if y==1];negative=[v for y,v in zip(labels,scores) if y==0]
                expected=sum(1 if a>b else .5 if a==b else 0 for a in positive for b in negative)/(len(positive)*len(negative))
                auc,points=_binary_roc(labels,scores)
                self.assertAlmostEqual(float(auc),expected,places=14)
                self.assertTrue(all(a[0]<=b[0] and a[1]<=b[1] for a,b in zip(points,points[1:])))
        with self.subTest(scenario='invalid_labels_and_scores_are_rejected'):
            for labels,scores in [([1,1],[0,1]),([0,0],[0,1]),([0,2],[0,1]),([0,1],[0]),([0,1],[0,mp.inf])]:
                with self.assertRaises(MathError):_binary_roc(labels,scores)

if __name__=='__main__':unittest.main()
