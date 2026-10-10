"""Companion analyses preserve answers and use the same samples as the primary test."""
import math
import unittest
import sympy as s
from test_advanced_statistics import evaluate, tree
from calc_evaluator import Engine
from calc_statistics import _shapiro_wilk
from calc_statistics_diagnostics import normal_views


class StatisticsDiagnosticsTests(unittest.TestCase):
    def section(self,result,title):
        return next(section for section in result['statisticsReport']['sections'] if section['title']==title)

    def test_paired_normality_and_qq_use_differences_and_keep_primary_answer(self):
        source='ttestpaired(0,[10,20,30,40,50],[9,18,27,36,45])'
        result=evaluate(source)
        report=result['statisticsReport']
        qq=next(plot for plot in report['plots'] if plot['kind']=='qq')['series']
        self.assertEqual(len(qq),1)
        self.assertEqual([point[1] for point in qq[0]['points']],[1,2,3,4,5])
        self.assertEqual(qq[0]['label'],'Paired differences (A − B)')
        expected=Engine({'precision':20}).build(tree(source))
        self.assertAlmostEqual(float(self.section(result,'Summary')['rows'][0][1]['decimal']),float(expected['t']),places=12)
        p=self.section(result,'Assumption checks')['rows'][0][3]
        self.assertAlmostEqual(float(p['decimal']),float(_shapiro_wilk(list(map(s.Integer,[1,2,3,4,5])))[1]),places=12)
        self.assertFalse(result['reusable'])
        self.section(result,'Effect size')
        self.section(result,'Mean confidence interval (95%, two-sided)')

    def test_welch_variance_check_does_not_select_a_pooled_test(self):
        result=evaluate('ttest2(0,[1,2,3,4,5],[2,4,8,16,32])')
        rows=self.section(result,'Assumption checks')['rows']
        self.assertEqual([row[0] for row in rows],['Shapiro–Wilk','Shapiro–Wilk','Brown–Forsythe'])
        self.assertTrue(any('does not assume equal variances' in note for note in result['statisticsReport']['notes']))
        primary=Engine({'precision':20}).build(tree('ttest2(0,[1,2,3,4,5],[2,4,8,16,32])'))
        shown={row[0]:row[1] for row in self.section(result,'Summary')['rows']}
        self.assertAlmostEqual(float(shown['df']['decimal']),float(primary['df']),places=12)

    def test_short_and_summary_only_inputs_explain_unavailable_diagnostics(self):
        for source in ['ttest(0,[1,2])','ttest(0,2,1,10)']:
            result=evaluate(source)
            row=self.section(result,'Assumption checks')['rows'][0]
            self.assertEqual(row[2],'unavailable')
            self.assertTrue(row[-1])
        self.assertFalse(any(plot['kind']=='qq' for plot in evaluate('ttest(0,2,1,10)')['statisticsReport']['plots']))

    def test_anova_and_tukey_include_assumptions_effects_and_overall_test(self):
        for name in ('anova','tukey'):
            result=evaluate(name+'([1,2,4,5],[2,4,5,8],[4,5,7,9])')
            self.assertEqual(len(self.section(result,'Assumption checks')['rows']),4)
            self.section(result,'Effect size')
            self.assertEqual(len(next(plot for plot in result['statisticsReport']['plots'] if plot['kind']=='qq')['series']),3)
            if name=='tukey':self.section(result,'Overall ANOVA')

    def test_qq_quantiles_quartile_reference_and_plot_thinning(self):
        qq,hist=normal_views([1,2,3,4,5],'sample')
        self.assertAlmostEqual(qq['points'][0][0],-1.2815515655446004,places=12)
        self.assertEqual(qq['points'][2],[0.0,3.0])
        self.assertEqual(sum(hist['counts']),5)
        qq,hist=normal_views(range(1000),'large')
        self.assertEqual(len(qq['points']),200)
        self.assertEqual(qq['n'],1000)
        self.assertEqual(sum(hist['counts']),1000)
        self.assertTrue(all(math.isfinite(v) for point in qq['points'] for v in point))

    def test_ancova_uses_residuals_and_coefficient_intervals_are_flattened(self):
        result=evaluate('ancova([[1,1,3],[1,2,5],[1,3,4],[1,4,8],[2,2,6],[2,3,7],[2,4,9],[2,5,8],[3,1,5],[3,3,8],[3,4,10],[3,6,11]],0.95,1)')
        self.section(result,'Assumption checks')
        qq=next(plot for plot in result['statisticsReport']['plots'] if plot['kind']=='qq')
        self.assertTrue(all('residuals' in series['label'] for series in qq['series']))
        result=evaluate('poissonreg([[0,1],[0,0],[1,3],[1,1],[2,2],[2,5],[3,4],[3,8],[4,6],[4,10]])')
        columns=self.section(result,'coefficients')['columns']
        self.assertIn('Lower 95% CI',columns)
        self.assertIn('Upper 95% CI',columns)
        self.assertNotIn('CI95',columns)
        self.assertTrue(any(plot['kind']=='intervals' for plot in result['statisticsReport']['plots']))

    def test_clustering_and_crossvalidation_visuals_keep_original_semantics(self):
        result=evaluate('kmeans([[1,1],[1,2],[2,1],[8,8],[8,9],[9,8]],2,0)')
        plot=next(plot for plot in result['statisticsReport']['plots'] if plot['kind']=='clusters')
        self.assertEqual(plot['points'],[[1,1],[1,2],[2,1],[8,8],[8,9],[9,8]])
        self.assertEqual(len(plot['assignments']),6)
        self.assertEqual(len(plot['centroids']),2)
        self.section(result,'Cluster sizes')
        result=evaluate('crossvalidate([[0,1],[1,3],[2,4],[3,7],[4,8],[5,11],[6,12],[7,15],[8,16]],3,0)')
        plot=next(plot for plot in result['statisticsReport']['plots'] if plot['kind']=='bars')
        self.assertEqual(len(plot['values']),3)
        self.assertEqual(plot['ylabel'],'MSE')
