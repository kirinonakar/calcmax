"""Reference-backed ML invariance and portable ordinal WLSMV contracts."""
import json
import unittest
from test_advanced_statistics import ROOT, run, evaluate
from calc_shared import MathError


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

    def test_invalid_ordinal_models_fail_with_actionable_errors(self):
        rows=self.cases[0]['arguments'][0]; binary=self.cases[1]['arguments'][0]; factors=[1]*4
        for data,missing,ids,mode,estimator in [(rows,'fiml',[],'configural','wlsmv'),
                 (rows,'complete',[],'configural','bad'),
                 (binary+binary,'complete',[1]*len(binary)+[2]*len(binary),'scalar','wlsmv'),
                 (rows+[[v+1 for v in r] for r in rows],'complete',[1]*len(rows)+[2]*len(rows),'strict','wlsmv')]:
            with self.subTest(missing=missing,invariance=mode,estimator=estimator):
                with self.assertRaises(MathError): run('cfa',data,factors,[],missing,ids,mode,estimator)


if __name__=='__main__': unittest.main()
