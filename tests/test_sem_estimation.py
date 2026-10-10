"""Reference-backed ML invariance and portable ordinal WLSMV contracts."""
import json
import random
import unittest
from test_advanced_statistics import ROOT, run, evaluate
from calc_shared import MathError
from calc_statistics_report import statistics_report
from calc_engine import statistics_display_terms


class SEMEstimationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.cases=json.loads((ROOT/'tests/fixtures/sem_estimation_reference.json').read_text())['cases']

    def test_independent_estimates_inference_and_fit_corrections(self):
        for case in self.cases:
            with self.subTest(function=case['function'],options=case['arguments'][1:]):
                result=run(case['function'],*case['arguments'])
                for path,expected in case['expected']:
                    actual=result
                    for key in path: actual=actual[key]
                    self.assertAlmostEqual(float(actual),expected,delta=case['tolerance']*max(1,abs(expected)),msg=str(path))

    def test_pca_default_and_explicit_principal_axis(self):
        case=json.loads((ROOT/'tests/fixtures/social_statistics_reference.json').read_text(encoding='utf8'))['cases']
        rows=next(c['arguments'][0] for c in case if c['function']=='efa')
        default=run('efa',rows,2,'none'); explicit=run('efa',rows,2,'none','pca')
        self.assertEqual(default['loadings'],explicit['loadings'])
        self.assertIn('Principal components',default['Extraction'])
        self.assertIn('Principal axis',run('efa',rows,2,'none','pa')['Extraction'])

    def test_scalar_and_strict_are_nested_and_report_raw_equalities(self):
        case=next(c for c in self.cases if c['arguments'][-1]=='scalar'); rows,factors,_,_,ids,_=case['arguments']
        fits=[run('cfa',rows,factors,[],'complete',ids,mode) for mode in ('configural','metric','scalar','strict')]
        for first,second in zip(fits,fits[1:]):
            self.assertGreaterEqual(float(second['χ²']),float(first['χ²'])-1e-5)
            self.assertGreater(second['df'],first['df'])
        scalar,strict=fits[-2:]; p=len(factors)
        self.assertEqual(strict['df']-scalar['df'],p)
        for fit in (scalar,strict):
            self.assertEqual(fit['Latent means'][0]['estimate'],0.)
            for i in range(p):
                self.assertEqual(fit['Indicator intercepts'][i]['Intercept'],fit['Indicator intercepts'][p+i]['Intercept'])
                self.assertEqual(fit['Loadings'][i]['estimate'],fit['Loadings'][p+i]['estimate'])
        for i in range(p): self.assertEqual(strict['Residual variances'][i]['Variance'],strict['Residual variances'][p+i]['Variance'])
        fiml=run('cfa',rows,factors,[],'fiml',ids,'scalar')
        self.assertAlmostEqual(float(fiml['χ²']),float(scalar['χ²']),places=5)
        masked=[[v if (i+j)%11 else 'NA' for j,v in enumerate(row)] for i,row in enumerate(rows)]
        self.assertIn('Indicator intercepts',run('cfa',masked,factors,[],'fiml',ids,'strict'))

    def test_ordinal_groups_cross_loadings_and_category_codes(self):
        case=self.cases[0]; rows=case['arguments'][0]; p=len(rows[0]); ids=[1]*len(rows)+[2]*len(rows)
        fits={mode:run('cfa',rows+rows,[1]*p,[],'complete',ids,mode,'wlsmv') for mode in ('configural','metric','scalar','strict')}
        for mode,fit in fits.items():
            self.assertEqual(fit['Invariance'],mode)
            if mode in ('scalar','strict'):
                for i in range(len(fit['Thresholds'])//2): self.assertEqual(fit['Thresholds'][i]['estimate'],fit['Thresholds'][i+len(fit['Thresholds'])//2]['estimate'])
                self.assertAlmostEqual(float(fit['Latent means'][1]['Mean']),0,delta=1e-5)
        for i in range(p): self.assertEqual(fits['strict']['Residual variances'][i]['Variance'],1.)
        self.assertEqual(fits['strict']['df']-fits['scalar']['df'],p)
        recoded=[[[-10,2,7,99][v] for v in row] for row in rows]
        same=run('cfa',recoded,*case['arguments'][1:]); original=run('cfa',*case['arguments'])
        self.assertAlmostEqual(float(same['χ²']),float(original['χ²']),places=8)
        semcase=self.cases[2]; args=semcase['arguments'][:]; args[3]=[[2,2]]
        cross=run('sem',*args)
        self.assertEqual(len(cross['Loadings']),7)

    def test_dispatch_report_preserves_threshold_labels_and_reusable_results(self):
        case=self.cases[0]; rows=case['arguments'][0][:80]
        expression='cfa('+str(rows)+',[1,1,1,1],[],complete,[],configural,wlsmv)'
        result=evaluate(expression)
        self.assertIn('WLSMV',result['exact']); self.assertIn('reusable',result)
        sections=result['statisticsReport']['sections']
        threshold=next(s for s in sections if s['title']=='Thresholds')
        self.assertEqual(len(threshold['rows']),12)
        self.assertIn('SE',threshold['columns'])
        loadings=next(s for s in sections if s['title']=='Loadings')
        self.assertIn('Standardized lower 95% CI',loadings['columns'])
        self.assertIn('Standardized upper 95% CI',loadings['columns'])
        forest=next(p for p in result['statisticsReport']['plots'] if p['title']=='Standardized factor loadings (95% CI)')
        self.assertEqual(len(forest['rows']),4)  # Fixed marker included.
        diagram=next(p for p in result['statisticsReport']['plots'] if p['kind']=='sem-diagram')
        self.assertEqual(len(diagram['nodes']),5)
        self.assertTrue(all(e['interval'][0]<e['estimate']<e['interval'][1] for e in diagram['edges']))
        r2=next(section for section in sections if section['title']=='Indicator R²')
        self.assertEqual(len(r2['rows']),4)
        self.assertFalse(any(section['title']=='Latent R²' for section in sections))
        self.assertTrue(all(0<node['r2']<1 for node in diagram['nodes'] if node['kind']=='observed'))

    def test_cfa_indicator_rsquare_includes_crossloadings_and_group_identity(self):
        case=next(c for c in self.cases if c['arguments'][-1]=='scalar')
        grouped=run('cfa',*case['arguments']);width=len(case['arguments'][1])
        for group in range(2):
            covariance=grouped['Implied covariance group '+str(group+1)]
            for i in range(width):
                at=group*width+i;expected=1-float(grouped['Residual variances'][at]['Variance'])/float(covariance[i][i])
                self.assertAlmostEqual(float(grouped['Indicator R²'][at]['R²']),expected,places=10)
                self.assertEqual(grouped['Indicator R²'][at]['Group'],grouped['Group summary'][group]['Group'])
        semcase=self.cases[2];args=semcase['arguments'][:];args[3]=[[2,2]]
        result=run('sem',*args)
        for i,row in enumerate(result['Indicator R²']):
            self.assertAlmostEqual(float(row['R²']),1-float(result['Residual variances'][i]['Variance'])/float(result['Implied covariance'][i][i]),places=10)

    def test_latent_rsquare_accounts_for_correlated_multiple_predictors(self):
        rng=random.Random(831); rows=[]
        for _ in range(180):
            first=rng.gauss(0,1); second=.5*first+rng.gauss(0,1)
            third=.6*first-.4*second+rng.gauss(0,.6)
            rows.append([loading*factor+rng.gauss(0,.45) for factor in (first,second,third) for loading in (1.,.8,1.2)])
        result=run('sem',rows,[1,1,1,2,2,2,3,3,3],[[1,3],[2,3]])
        covariance=result['Latent covariance']; paths=result['Structural paths']
        b1,b2=[float(row['estimate']) for row in paths]
        expected=(b1*b1*float(covariance[0][0])+b2*b2*float(covariance[1][1])+2*b1*b2*float(covariance[0][1]))/float(covariance[2][2])
        self.assertAlmostEqual(float(result['Latent R²'][2]['R²']),expected,places=10)
        self.assertEqual(result['Latent R²'][0]['Role'],'Exogenous (R² not applicable)')
        self.assertEqual(len(result['Exogenous correlations']),1)
        # Changing raw units leaves fully standardized inference unchanged.
        rescaled=run('sem',[[v*scale for v,scale in zip(row,[2,.5,3,4,.25,2,3,5,.5])] for row in rows],[1,1,1,2,2,2,3,3,3],[[1,3],[2,3]])
        for key in ('Loadings','Structural paths'):
            for left,right in zip(result[key],rescaled[key]):
                self.assertAlmostEqual(float(left['Standardized SE']),float(right['Standardized SE']),places=7)
                for a,b in zip(left['Standardized CI95'],right['Standardized CI95']): self.assertAlmostEqual(float(a),float(b),places=7)

    def test_cross_loading_diagram_and_multigroup_reports_preserve_identity(self):
        case=self.cases[2]; args=case['arguments'][:]; args[3]=[[2,2]]
        result=run('sem',*args)
        labels={'feature:1':'같은 이름','feature:2':'같은 이름','feature:4':'Outcome'}
        report=statistics_report('sem',statistics_display_terms(result,labels),15,labels)
        diagram=next(p for p in report['plots'] if p['kind']=='sem-diagram')
        self.assertEqual(len(diagram['nodes']),8);self.assertEqual(len(diagram['edges']),8)
        self.assertEqual(sum(n['label']=='같은 이름' for n in diagram['nodes']),2)
        self.assertEqual(sum(e['target']=='x2' for e in diagram['edges']),2)
        self.assertEqual(next(n['r2'] for n in diagram['nodes'] if n['id']=='x1'),float(result['Indicator R²'][0]['R²']))
        self.assertEqual(next(n['r2'] for n in diagram['nodes'] if n['id']=='x2'),float(result['Indicator R²'][1]['R²']))
        self.assertTrue(all(0<node['y']<diagram['height'] and 0<node['x']<diagram['width'] for node in diagram['nodes']))
        self.assertTrue(all(0<edge['labelPosition'][1]<diagram['height'] for edge in diagram['edges']))
        groupcase=next(c for c in self.cases if c['arguments'][-1]=='scalar')
        grouped=run('cfa',*groupcase['arguments']);report=statistics_report('cfa',grouped,15)
        diagram=next(p for p in report['plots'] if p['kind']=='sem-diagram')
        self.assertEqual(len(diagram['series']),2)
        self.assertEqual([s['label'] for s in diagram['series']],[row['Group'] for row in grouped['Group summary']])
        self.assertTrue(all(len(s['nodes'])==5 for s in diagram['series']))

    def test_invalid_ordinal_models_fail_with_actionable_errors(self):
        rows=self.cases[0]['arguments'][0]; binary=self.cases[1]['arguments'][0]; factors=[1]*4
        for data,missing,ids,mode,estimator in [(rows,'fiml',[],'configural','wlsmv'),
                 (rows,'complete',[],'configural','bad'),
                 (binary+binary,'complete',[1]*len(binary)+[2]*len(binary),'scalar','wlsmv'),
                 (rows+[[v+1 for v in r] for r in rows],'complete',[1]*len(rows)+[2]*len(rows),'strict','wlsmv')]:
            with self.subTest(missing=missing,invariance=mode,estimator=estimator):
                with self.assertRaises(MathError): run('cfa',data,factors,[],missing,ids,mode,estimator)


if __name__=='__main__': unittest.main()
