"""Presentation-only tables for Statistics; reusable answers stay untouched."""
import sympy as s
from calc_display import approximate, display_rounded, display_tree, readable

BASIC = set('mean median variance stdev sumdata quartiles stats covariance correlation ttest ttest2 ttestpaired ztest ztest2 chi2test chi2independence fisherexact anova tukey shapiro wilcoxon mannwhitney kruskal tinterval zinterval'.split())
TITLES = dict(zip('stats mean median variance stdev sumdata quartiles covariance correlation ttest ttest2 ttestpaired ztest ztest2 chi2test chi2independence fisherexact anova tukey shapiro wilcoxon mannwhitney kruskal tinterval zinterval padjust effectsize levene bartlett mcnemar kaplanmeier logrank cox repeatedanova poissonreg nbreg mixedmodel gee glmm multinomial ordinal bootstrap power samplesize impute crossvalidate pca kmeans'.split(),
    ['Descriptive statistics','Mean','Median','Variance','Standard deviation','Sum','Quartiles','Covariance','Correlation','One-sample t test','Welch t test','Paired t test','One-sample z test','Two-sample z test','χ² test','χ² independence test','Fisher exact test','ANOVA','Tukey HSD','Shapiro–Wilk','Wilcoxon','Mann–Whitney','Kruskal–Wallis','t interval','z interval','P-value adjustment','Effect size','Levene test','Bartlett test','McNemar test','Kaplan–Meier','Log-rank test','Cox regression','Repeated-measures ANOVA','Poisson regression','Negative binomial regression','Mixed model','GEE','GLMM','Multinomial regression','Ordinal regression','Bootstrap','Power','Sample size','Imputation','Cross-validation','PCA','K-means']))


TITLES.update({'ancova':'ANCOVA', 'glm':'Generalized linear model (GLM)',
               'cohend':'Effect size', 'eta2':'Effect size', 'bootstrapci':'Bootstrap confidence interval',
               'testpower':'Power', 'kstest':'Kolmogorov–Smirnov test',
               'bayesproportion':'Bayesian proportion', 'bayesmean':'Bayesian mean', 'bayesrate':'Bayesian rate',
               'bayescompare':'Bayesian Two-Sample Comparison'})


def statistics_report(name, value, precision, labels=None):
    """Each section has named columns and cells using the normal result formatter.

    Preview large tables at 100 rows; copyRows retains every formatted row for Copy.
    Never infer that unrelated vectors of equal length describe the same observations.
    """
    sections = []
    table_labels = labels if isinstance(labels, dict) else {}
    categorical = name in ('chi2independence', 'fisherexact', 'mcnemar') and all(table_labels.get(key) for key in ('table:row', 'table:column'))

    def cell(v):
        if isinstance(v, str): return v
        if isinstance(v, bool): return str(v)
        if v is None: return 'unavailable'
        if v is s.nan: return 'undefined'
        shown = display_rounded(v, precision)
        dec = approximate(v, precision)
        return {'exact': readable(shown), 'decimal': readable(dec),
                'tree': display_tree(shown), 'decimalTree': display_tree(dec),
                'approximate': bool(getattr(shown, 'has', lambda *_: False)(s.Float))}

    def add(title, columns, rows):
        if rows:
            formatted = [[cell(v) for v in row] for row in rows]
            section = {'title': title, 'columns': columns, 'rows': formatted[:100], 'totalRows': len(rows)}
            if len(rows) > 100: section['copyRows'] = formatted
            sections.append(section)

    def vector(v): return isinstance(v, (list, tuple)) and all(not isinstance(x, (list, tuple, dict)) for x in v)

    def visit(title, v):
        if isinstance(v, s.MatrixBase): v = v.tolist()
        if isinstance(v, dict):
            remaining = dict(v)
            if isinstance(remaining.get('diagnostics'),dict):
                visit('Model diagnostics',remaining.pop('diagnostics'))
            if name == 'tukey':
                pairs = [key[:-16] for key in remaining if key.endswith(' mean difference')]
                add('Pairwise comparisons', ['Comparison','Mean difference','Adjusted p value'],
                    [[pair, remaining.pop(pair+' mean difference'), remaining.pop(pair+' adjusted p value')] for pair in pairs])
            # Preserve row relationships for known parallel arrays.
            bundles = [('P-value adjustment', 'Observation', ['raw p','adjusted p','reject (1=yes)']),
                       ('Components', 'Component', ['eigenvalues','explained variance ratio']),
                       ('Feature scaling', 'Feature', ['centers','scales']),
                       ('Fold scores', 'Fold', ['fold MSE','fold log loss'])]
            summary = [[key, item] for key, item in remaining.items() if not isinstance(item, (dict, list, tuple, s.MatrixBase))]
            add(title, ['Metric','Value'], summary)
            for key, _ in summary: remaining.pop(key)
            for heading, index, keys in bundles:
                present = [key for key in keys if key in remaining and vector(remaining[key])]
                if not present: continue
                lengths = {len(remaining[key]) for key in present}
                if len(lengths) != 1: continue
                add(heading, [index]+present, [[i+1]+[remaining[key][i] for key in present] for i in range(next(iter(lengths)))])
                for key in present: remaining.pop(key)
            for key, item in remaining.items(): visit(key, item)
        elif isinstance(v, (list, tuple)):
            if not v: return
            if all(isinstance(row, dict) for row in v):
                keys = list(dict.fromkeys(key for row in v for key in row))
                add(title, keys, [[row.get(key, 'unavailable') for key in keys] for row in v])
            elif all(vector(row) for row in v) and len({len(row) for row in v}) == 1:
                width = len(v[0])
                headers = {'survival table':['Time','At risk','Events','Censored','Survival','Lower 95% CI','Upper 95% CI'],
                           'observed':['Category 1','Category 2'], 'expected':['Category 1','Category 2']}.get(title)
                if headers is None or len(headers) != width:
                    prefix = 'PC' if title in ('loadings','scores') else 'Column '
                    headers = [prefix+str(i+1) for i in range(width)]
                index = 'Feature' if title == 'loadings' else 'Cluster' if title == 'centroids' else 'Observation'
                if categorical and title in ('observed', 'expected'):
                    headers = [str(table_labels['table:column'])+': '+str(table_labels.get('table:column:'+str(i+1), 'Category '+str(i+1))) for i in range(width)]
                    add(title, [table_labels['table:row']]+headers,
                        [[table_labels.get('table:row:'+str(i+1), str(i+1))]+list(row) for i,row in enumerate(v)])
                    return
                add(title, [index]+headers, [[i+1]+list(row) for i,row in enumerate(v)])
            elif vector(v):
                if title in ('confidence interval','credible interval','difference credible interval','effect credible interval','quartiles (inclusive)','Quartiles'):
                    labels = ['Lower','Upper'] if len(v)==2 else ['Q1','Median','Q3']
                    if len(labels)==len(v): add(title, labels, [list(v)]); return
                add(title, ['Observation','Value'], [[i+1,item] for i,item in enumerate(v)])
            else:
                for i,item in enumerate(v): visit(title+' '+str(i+1), item)
        else: add(title, ['Metric','Value'], [[title,v]])

    if categorical:
        add('Compared columns', ['First column', 'Second column'] if name == 'mcnemar' else ['Row variable', 'Column variable'], [[table_labels['table:row'], table_labels['table:column']]])
    visit('Summary' if isinstance(value, dict) else TITLES.get(name, name), value)
    return {'analysis': name, 'title': TITLES.get(name, name), 'sections': sections}


def statistics_copy_report(name, value, details, precision):
    """Copy tables for dedicated reports without changing visible report routing."""
    if name == 'regression':
        data = {'Fitted expression': value, **details}
        report = statistics_report(name, data, precision)
        report['title'] = 'Regression'
    else:
        data = {key: item for key, item in details.items() if key != 'groups'}
        for index, group in enumerate(details.get('groups', [])):
            data['Group '+str(index+1)] = {key: item for key, item in group.items() if key != 'curve'}
            data['Group '+str(index+1)]['survival table'] = group.get('curve', [])
        report = statistics_report(name, data, precision)
        report['title'] = 'Survival analysis'
    return report
