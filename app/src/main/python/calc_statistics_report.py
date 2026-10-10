"""Presentation-only tables for Statistics; reusable answers stay untouched."""
import sympy as s
import math
from calc_display import approximate, display_rounded, display_tree, readable

BASIC = set('mean median variance stdev sumdata quartiles stats covariance correlation ttest ttest2 ttestpaired ztest ztest2 chi2test chi2independence fisherexact anova tukey shapiro wilcoxon mannwhitney kruskal tinterval zinterval'.split())
BASIC.update(('welchanova','gameshowell'))
TITLES = dict(zip('stats mean median variance stdev sumdata quartiles covariance correlation ttest ttest2 ttestpaired ztest ztest2 chi2test chi2independence fisherexact anova tukey shapiro wilcoxon mannwhitney kruskal tinterval zinterval padjust effectsize levene bartlett mcnemar kaplanmeier logrank cox repeatedanova poissonreg nbreg mixedmodel gee glmm multinomial ordinal bootstrap power samplesize impute crossvalidate pca kmeans'.split(),
    ['Descriptive statistics','Mean','Median','Variance','Standard deviation','Sum','Quartiles','Covariance','Correlation','One-sample t test','Welch t test','Paired t test','One-sample z test','Two-sample z test','χ² test','χ² independence test','Fisher exact test','ANOVA','Tukey HSD','Shapiro–Wilk','Wilcoxon','Mann–Whitney','Kruskal–Wallis','t interval','z interval','P-value adjustment','Effect size','Levene test','Bartlett test','McNemar test','Kaplan–Meier','Log-rank test','Cox regression','Repeated-measures ANOVA','Poisson regression','Negative binomial regression','Mixed model','GEE','GLMM','Multinomial regression','Ordinal regression','Bootstrap','Power','Sample size','Imputation','Cross-validation','PCA','K-means']))


TITLES.update({'ancova':'ANCOVA', 'glm':'Generalized linear model (GLM)',
               'welchanova':'Welch ANOVA','gameshowell':'Games–Howell','linearmodel':'Factorial linear model / ANOVA', 'twowayanova':'Two-way ANOVA', 'friedman':'Friedman test',
               'cohend':'Effect size', 'eta2':'Effect size', 'bootstrapci':'Bootstrap confidence interval',
               'testpower':'Power', 'kstest':'Kolmogorov–Smirnov test',
               'bayesproportion':'Bayesian proportion', 'bayesmean':'Bayesian mean', 'bayesrate':'Bayesian rate',
               'bayescompare':'Bayesian Two-Sample Comparison','bayesbootstrap':'Bayesian Bootstrap'})


def statistics_report(name, value, precision, labels=None):
    """Each section has named columns and cells using the normal result formatter.

    Preview large tables at 100 rows; copyRows retains every formatted row for Copy.
    Never infer that unrelated vectors of equal length describe the same observations.
    """
    sections = []
    plots = []
    table_labels = labels if isinstance(labels, dict) else {}
    categorical = name in ('chi2independence', 'fisherexact', 'mcnemar') and all(table_labels.get(key) for key in ('table:row', 'table:column'))

    def cell(v):
        if isinstance(v, str): return v
        if isinstance(v, bool): return str(v)
        if v is None: return 'unavailable'
        if v is s.nan: return 'undefined'
        if isinstance(v,(int,float)): v=s.sympify(v)
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

    def finite(v):
        try: return float(v) if math.isfinite(float(v)) else None
        except (TypeError,ValueError,OverflowError): return None

    def interval_plot(title, rows, reference=None):
        points=[]
        for label,estimate,lower,upper in rows:
            values=list(map(finite,(estimate,lower,upper)))
            if all(v is not None for v in values) and values[1]<=values[0]<=values[2]: points.append([str(label),*values])
        if points: plots.append({'kind':'intervals','title':title,'rows':points,'reference':reference})

    def visit(title, v):
        if isinstance(v, s.MatrixBase): v = v.tolist()
        if isinstance(v, dict):
            remaining = dict(v)
            if isinstance(remaining.get('diagnostics'),dict):
                visit('Model diagnostics',remaining.pop('diagnostics'))
            estimate=v.get('estimate',v.get('posterior mean',v.get('sample mean')))
            interval=v.get('credible interval',v.get('confidence interval'))
            if vector(interval) and len(interval)==2:
                interval_plot('Credible interval' if 'credible interval' in v else 'Confidence interval',[(TITLES.get(name,name) if title=='Summary' else title,estimate,*interval)])
            elif 'lower' in v and 'upper' in v:
                interval_plot('Confidence interval',[(TITLES.get(name,name) if title=='Summary' else title,estimate,v['lower'],v['upper'])])
            if name=='bayescompare' and 'difference credible interval' in v:
                interval_plot('Difference credible interval', [('Difference (B − A)',v['Posterior Mean Difference (B - A)'],*v['difference credible interval'])],0)
                interval_plot('Effect credible interval', [('Posterior Effect Size',v['Posterior Effect Size'],*v['effect credible interval'])],0)
            if name == 'tukey':
                pairs = [key[:-16] for key in remaining if key.endswith(' mean difference')]
                add('Pairwise comparisons', ['Comparison','Mean difference','Adjusted p value'],
                    [[pair, remaining.pop(pair+' mean difference'), remaining.pop(pair+' adjusted p value')] for pair in pairs])
            # Preserve row relationships for known parallel arrays.
            bundles = [('P-value adjustment', 'Observation', ['raw p','adjusted p','reject (1=yes)']),
                       ('Components', 'Component', ['eigenvalues','explained variance ratio','cumulative explained variance']),
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
                add(heading, [index]+present, [[table_labels.get('feature:'+str(i+1),i+1) if name=='pca' and index=='Feature' else i+1]+[remaining[key][i] for key in present] for i in range(next(iter(lengths)))])
                for key in present: remaining.pop(key)
            for key, item in remaining.items(): visit(key, item)
        elif isinstance(v, (list, tuple)):
            if not v: return
            if all(isinstance(row, dict) for row in v):
                intervals=[]; expanded=[]
                for raw in v:
                    row=dict(raw)
                    bounds=row.pop('CI95',None)
                    if isinstance(bounds,(list,tuple)) and len(bounds)==2: row['Lower 95% CI'],row['Upper 95% CI']=bounds
                    elif 'CI95' in raw: row['Lower 95% CI']=row['Upper 95% CI']=None
                    label=row.get('term',row.get('Term',row.get('group',row.get('Group',row.get('Comparison','')))))
                    estimate=row.get('estimate',row.get('Estimate',row.get('Mean',row.get('adjusted mean',row.get('Mean difference')))))
                    low=row.get('Lower 95% CI',row.get('Lower CI'))
                    high=row.get('Upper 95% CI',row.get('Upper CI'))
                    if label: intervals.append((label,estimate,low,high))
                    expanded.append(row)
                if intervals: interval_plot(title+' intervals',intervals,0 if ('coefficients' in title.lower() or 'post-hoc' in title.lower()) else None)
                v=expanded
                keys = list(dict.fromkeys(key for row in v for key in row))
                add(title, keys, [[row.get(key, 'unavailable') for key in keys] for row in v])
            elif all(vector(row) for row in v) and len({len(row) for row in v}) == 1:
                width = len(v[0])
                headers = {'survival table':['Time','At risk','Events','Censored','Survival','Lower 95% CI','Upper 95% CI'],
                           'observed':['Category 1','Category 2'], 'expected':['Category 1','Category 2']}.get(title)
                if headers is None or len(headers) != width:
                    prefix = 'PC' if title in ('loadings','scores') else 'Feature ' if title=='centroids' else 'Column '
                    headers = [prefix+str(i+1) for i in range(width)]
                    if title=='centroids': headers=[table_labels.get('feature:'+str(i+1),header) for i,header in enumerate(headers)]
                index = 'Feature' if title == 'loadings' else 'Cluster' if title == 'centroids' else 'Observation'
                if categorical and title in ('observed', 'expected'):
                    headers = [str(table_labels['table:column'])+': '+str(table_labels.get('table:column:'+str(i+1), 'Category '+str(i+1))) for i in range(width)]
                    add(title, [table_labels['table:row']]+headers,
                        [[table_labels.get('table:row:'+str(i+1), str(i+1))]+list(row) for i,row in enumerate(v)])
                    return
                add(title, [index]+headers, [[table_labels.get('feature:'+str(i+1),i+1) if name=='pca' and index=='Feature' else i+1]+list(row) for i,row in enumerate(v)])
            elif vector(v):
                if title in ('confidence interval','credible interval','difference credible interval','effect credible interval','quartiles (inclusive)','Quartiles'):
                    labels = ['Lower','Upper'] if len(v)==2 else ['Q1','Median','Q3']
                    if len(labels)==len(v): add(title, labels, [list(v)]); return
                add(title, ['Observation','Value'], [[i+1,item] for i,item in enumerate(v)])
            else:
                for i,item in enumerate(v): visit(title+' '+str(i+1), item)
        else: add(title, ['Metric','Value'], [[title,v]])

    if name in ('stats','mean','median','stdev','variance','sumdata','quartiles','correlation','covariance') and table_labels.get('sample:1'):
        add('Analyzed variables',['Sample'],[[table_labels[key]] for key in sorted(table_labels) if key.startswith('sample:')])
    if categorical:
        add('Compared columns', ['First column', 'Second column'] if name == 'mcnemar' else ['Row variable', 'Column variable'], [[table_labels['table:row'], table_labels['table:column']]])
    if name=='bayesbootstrap' and isinstance(value,dict) and value.get('comparison'):
        add('Compared groups',['Group A','Group B'],[[table_labels.get('sample:A','A'),table_labels.get('sample:B','B')]])
    if name=='kmeans' and isinstance(value,dict):
        assignments=value.get('labels (1-based)',[])
        add('Cluster sizes',['Cluster','n'],[[i+1,sum(label==i+1 for label in assignments)] for i in range(len(value.get('centroids',[])))])
    if name=='crossvalidate' and isinstance(value,dict):
        metric='fold log loss' if 'fold log loss' in value else 'fold MSE'
        scores=value.get(metric,[])
        if scores: plots.append({'kind':'bars','title':'Validation score by fold','values':list(map(float,scores)),'labels':['Fold '+str(i+1) for i in range(len(scores))],'ylabel':'Log loss' if metric=='fold log loss' else 'MSE'})
    if name=='friedman' and isinstance(value,dict):
        ranks=value['mean ranks'];plots.append({'kind':'bars','title':'Mean ranks by condition','values':list(map(float,ranks)),'labels':[table_labels.get('feature:'+str(i+1),'Condition '+str(i+1)) for i in range(len(ranks))],'ylabel':'Mean rank'})
    if name=='padjust' and isinstance(value,dict):
        plots.append({'kind':'bars','title':'Raw and adjusted p values','values':list(map(float,value['adjusted p'])),'secondary':list(map(float,value['raw p'])),'labels':[str(i+1) for i in range(len(value['raw p']))],'ylabel':'p value','reference':float(value['alpha']),'maximum':1})
    visit('Summary' if isinstance(value, dict) else TITLES.get(name, name), value)
    priority=['p value','p','mean difference','posterior mean','Posterior Mean Difference (B - A)','Posterior Effect Size','BF10','P(μB > μA)','estimate','power','achieved power','n per group / pairs','R²','Adjusted R²','Kendall W','eta2','Cohen d','Cohen dz','RMSE','R2','inertia']
    highlights=[{'label':key,'value':cell(value[key])} for key in priority if isinstance(value,dict) and key in value and finite(value[key]) is not None][:4]
    return {'analysis': name, 'title': TITLES.get(name, name), 'sections': sections,'highlights':highlights,'plots':plots}


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
