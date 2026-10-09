"""Independent Bayesian-bootstrap moments and PCA visualization contracts."""
import json
import math
from pathlib import Path
import sys
import unittest
import sympy as s

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'app/src/main/python'))
from calc_advanced_statistics import advanced
from calc_evaluator import Engine
from calc_engine import dispatch
from calc_shared import MathError


class BayesianBootstrapTests(unittest.TestCase):
    def compare(self,first,second,mode='independent',statistic='mean',draws=20000):
        engine=Engine({})
        return advanced(engine,'bayesbootstrap',[first,second,s.Symbol(statistic),.95,draws,7,s.Symbol(mode)])

    def test_independent_column_difference_matches_analytic_moments_and_probability(self):
        result=self.compare([0,1],[0,2])
        self.assertEqual(.5,float(result['estimate']))
        self.assertAlmostEqual(.5,float(result['posterior mean']),delta=.02)
        self.assertAlmostEqual(math.sqrt(5/12),float(result['posterior SD']),delta=.01)
        self.assertAlmostEqual(.75,float(result['P(difference > 0)']),delta=.015)
        self.assertAlmostEqual(.25,float(result['P(difference < 0)']),delta=.015)
        self.assertEqual(0,float(result['P(difference = 0)']))
        self.assertAlmostEqual(1,sum(float(result[key]) for key in ('P(difference > 0)','P(difference < 0)','P(difference = 0)')))
        unequal=self.compare([0,1],[1,2,3],draws=100)
        self.assertEqual(2,int(unequal['n A']));self.assertEqual(3,int(unequal['n B']))

    def test_paired_columns_keep_joint_row_weights_and_constant_differences(self):
        result=self.compare([0,1],[1,0],mode='paired')
        self.assertAlmostEqual(0,float(result['posterior mean']),delta=.02)
        self.assertAlmostEqual(1/math.sqrt(3),float(result['posterior SD']),delta=.01)
        self.assertAlmostEqual(.5,float(result['P(difference > 0)']),delta=.015)
        shift=self.compare([2,10,-1],[7,15,4],mode='paired',draws=100)
        self.assertEqual([5,5],list(map(float,shift['credible interval'])))
        self.assertEqual(0,float(shift['posterior SD']))
        for statistic in ('mean','median','variance','stdev'):
            same=self.compare([2,10,-1],[2,10,-1],mode='paired',statistic=statistic,draws=100)
            self.assertEqual([0,0],list(map(float,same['credible interval'])))
            self.assertEqual(1,float(same['P(difference = 0)']))
            self.assertEqual(0,float(same['P(difference < 0)']))
            self.assertEqual(0,float(same['P(difference > 0)']))

    def test_default_draws_histogram_mass_and_lower_weighted_median_definition(self):
        from calc_advanced_resampling import bayesian_statistic
        self.assertEqual(1,bayesian_statistic([2,1],'median')([1,1]))
        for args in ([[1,2]],[[1,2],[4,5]]):
            engine=Engine({});result=advanced(engine,'bayesbootstrap',args)
            self.assertEqual(10000,int(result['draws']))
            self.assertEqual(10000,sum(engine.statistics_plots[0]['counts']))
        engine=Engine({});result=advanced(engine,'bayesbootstrap',[[1,2],s.Symbol('median'),.95,100,0])
        self.assertEqual('Sample median (midpoint for even n)',result['estimate median definition'])
        self.assertEqual('Lower weighted quantile',result['posterior median definition'])
        self.assertEqual(1.5,float(result['estimate']))
        self.assertIn('Lower weighted quantile',engine.note)

    def test_comparison_validates_pair_lengths_mode_and_single_sample_arity(self):
        for first,second,mode in (([1,2],[1,2,3],'paired'),([1],[1,2],'independent'),([1,2],[1,2],'single')):
            with self.assertRaises(MathError):self.compare(first,second,mode=mode,draws=100)
        with self.assertRaises(MathError):advanced(Engine({}),'bayesbootstrap',[[1,2],s.Symbol('mean'),.95,100,0,3])

    def test_group_names_are_presentation_only_and_reach_copy_tables_and_plot(self):
        def number(value):return {'kind':'number','value':str(value)}
        tree={'kind':'call','value':'bayesbootstrap','args':[{'kind':'list','args':[number(v) for v in data]} for data in ([1,2],[4,5,6])]}
        plain=json.loads(dispatch(json.dumps({'tree':tree})))
        named=json.loads(dispatch(json.dumps({'tree':tree,'statisticsTermLabels':{'sample:A':'control','sample:B':'treatment'}})))
        self.assertTrue(named['ok'],named)
        self.assertEqual(plain['exact'],named['exact']);self.assertEqual(plain['decimal'],named['decimal'])
        self.assertEqual([['control','treatment']],named['statisticsReport']['sections'][0]['rows'])
        self.assertEqual(['control','treatment'],named['statisticsReport']['plots'][0]['groupLabels'])

    def run_bootstrap(self,data,statistic='mean',level=.95,draws=20000,seed=7):
        engine=Engine({})
        result=advanced(engine,'bayesbootstrap',[data,s.Symbol(statistic),level,draws,seed])
        return result,engine.statistics_plots[0]

    def test_uniform_two_point_mean_matches_exact_posterior_and_histogram_mass(self):
        result,plot=self.run_bootstrap([0,1])
        self.assertEqual(.5,float(result['estimate']))
        self.assertAlmostEqual(.5,float(result['posterior mean']),delta=.01)
        self.assertAlmostEqual(1/math.sqrt(12),float(result['posterior SD']),delta=.01)
        for actual,expected in zip(result['credible interval'],(.025,.975)):self.assertAlmostEqual(expected,float(actual),delta=.01)
        self.assertEqual(20000,sum(plot['counts']))
        self.assertEqual(len(plot['counts'])+1,len(plot['edges']))
        self.assertAlmostEqual(float(result['posterior SD'])/math.sqrt(20000),float(result['posterior mean MCSE']))
        again,_=self.run_bootstrap([0,1]);self.assertEqual(result,again)
        changed,_=self.run_bootstrap([0,1],seed=8);self.assertNotEqual(result,changed)

    def test_dirichlet_mean_variance_matches_analytic_moments(self):
        result,_=self.run_bootstrap([1,2,6,11])
        center=5;variance=sum((x-center)**2 for x in (1,2,6,11))/(4*5)
        self.assertAlmostEqual(center,float(result['posterior mean']),delta=.06)
        self.assertAlmostEqual(math.sqrt(variance),float(result['posterior SD']),delta=.035)

    def test_population_statistics_and_weighted_quantile_degenerate_limits(self):
        for statistic,expected in (('mean',3),('median',3),('variance',0),('stdev',0)):
            result,plot=self.run_bootstrap([3,3,3],statistic,draws=100)
            self.assertEqual(expected,float(result['estimate']))
            self.assertEqual([expected,expected],list(map(float,result['credible interval'])))
            self.assertGreater(plot['edges'][-1],plot['edges'][0])
        result,_=self.run_bootstrap([0,1],'variance',draws=100)
        self.assertEqual(.25,float(result['estimate']))
        self.assertTrue(all(0<=float(v)<=.25 for v in result['credible interval']))
        result,_=self.run_bootstrap([0,1],'median',draws=100)
        self.assertEqual(.5,float(result['estimate']))
        self.assertEqual([0,1],list(map(float,result['credible interval'])))

    def test_sample_median_changes_only_estimates_and_histogram_reference(self):
        cases=json.loads((Path(__file__).parent/'fixtures/bayesian_bootstrap_median_reference.json').read_text(encoding='utf-8'))
        for case in cases:
            with self.subTest(mode=case['mode']):
                engine=Engine({});compare=case['second'] is not None
                args=[case['first']]+([case['second']] if compare else [])+[s.Symbol('median'),.95,case['draws'],case['seed']]+([s.Symbol(case['mode'])] if compare else [])
                result=advanced(engine,'bayesbootstrap',args)
                for key,expected in case['posterior'].items():
                    actual=[float(v) for v in result[key]] if isinstance(result[key],list) else float(result[key])
                    self.assertEqual(expected,actual,key)
                plot=engine.statistics_plots[0]
                self.assertEqual(case['histogram'],{key:plot[key] for key in ('edges','counts','interval')})
                if compare:
                    self.assertEqual(3,float(result['estimate A']))
                    self.assertEqual(4.5,float(result['estimate B']))
                    self.assertEqual(1.5,float(result['estimate']))
                    self.assertEqual(1.5,plot['estimate'])
                else:
                    self.assertEqual(5,float(result['estimate']))
                    self.assertEqual(5,plot['estimate'])

    def test_invalid_parameters_are_rejected(self):
        for data,statistic,level,draws,seed in (([1],'mean',.95,100,0),([1,2],'bad',.95,100,0),([1,2],'mean',1,100,0),([1,2],'mean',.95,99,0),([1,2],'mean',.95,100,-1)):
            with self.assertRaises(MathError):self.run_bootstrap(data,statistic,level,draws,seed)

    def test_public_dispatch_retains_numeric_tables_and_plots(self):
        tree={'kind':'call','value':'bayesbootstrap','args':[{'kind':'list','args':[{'kind':'number','value':str(v)} for v in (1,2,3)]}]}
        result=json.loads(dispatch(json.dumps({'tree':tree})))
        self.assertTrue(result['ok'],result)
        estimate=next(row[1] for row in result['statisticsReport']['sections'][0]['rows'] if row[0]=='estimate')
        self.assertEqual('2',estimate['decimal'])
        self.assertEqual('bayesbootstrap',result['statisticsReport']['analysis'])
        self.assertEqual('histogram',result['statisticsReport']['plots'][0]['kind'])

    def test_pca_plots_keep_all_components_and_selected_feature_names(self):
        engine=Engine({'statisticsTermLabels':{'feature:1':'height','feature:2':'weight','feature:3':'age'}})
        rows=[[1,3,2],[2,1,4],[3,5,1],[4,2,6],[5,7,3]]
        result=advanced(engine,'pca',[rows,1,1])
        self.assertEqual(1,len(result['eigenvalues']))
        scree,scores,loadings=engine.statistics_plots
        self.assertEqual(3,len(scree['ratios']));self.assertAlmostEqual(1,sum(scree['ratios']))
        self.assertEqual(5,len(scores['points']));self.assertEqual(1,len(scores['points'][0]))
        self.assertEqual(['height','weight','age'],loadings['labels'])
        self.assertAlmostEqual(1,sum(row[0]**2 for row in loadings['points']))
        self.assertAlmostEqual(float(result['eigenvalues'][0]),sum(row[0]**2 for row in scores['points'])/4)
