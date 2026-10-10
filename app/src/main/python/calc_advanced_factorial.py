"""Independent two-factor ANOVA, Type III tests with sum contrasts."""
import math
import mpmath as mp
import sympy as s
from calc_shared import require
from calc_advanced_common import table, integer
from calc_advanced_linear import least_squares, factorial_design, term_f_tests, partial_f_test


def calculate(engine, name, a):
    if name == 'linearmodel':
        return linear_model(engine,a)
    rows = table(a[0], minimum=5, columns=3)
    require(all(len(row) == 3 for row in rows), 'Use factor A, factor B, response columns')
    interaction = integer(a[1], 0, 1) if len(a) > 1 else 1
    levels = [sorted(set(row[j] for row in rows)) for j in (0, 1)]
    require(all(len(level) >= 2 for level in levels), 'Each factor needs at least two levels')
    def contrasts(value, level):
        return [float(value == item) - float(value == level[-1]) for item in level[:-1]]
    design, blocks, coefficient_names, _ = factorial_design([row[:2] for row in rows], [0,1], 2 if interaction else 1)
    y = [row[2] for row in rows]
    beta, covariance, sse, fitted = least_squares(design, y)
    df_error = len(rows) - len(beta)
    require(sse > 0, 'Residual variance must be positive')
    mse = sse / df_error
    labels = engine.request.get('statisticsTermLabels') or {}
    table_rows = []
    for (variables, _), test in zip(blocks,term_f_tests(design,y,blocks,3,(beta,covariance,sse,fitted))):
        label=' × '.join(labels.get('factor:'+('A' if j==0 else 'B'), 'Factor '+('A' if j==0 else 'B')) for j in variables)
        ss=test['SS']
        table_rows.append(dict(Term=label, **test, MS=ss/test['df'], **{'Partial η²':ss/(ss+sse)}))
    table_rows.append({'Term':'Residual','SS':sse,'df':df_error,'MS':mse})
    cells = []
    residuals, residual_labels = [], []
    from calc_statistics import _t_sf
    lo, hi = mp.mpf(0), mp.mpf(1)
    while _t_sf(hi, df_error) > mp.mpf('.025'): hi *= 2
    for _ in range(65):
        mid = (lo+hi)/2
        if _t_sf(mid, df_error) > mp.mpf('.025'): lo = mid
        else: hi = mid
    critical = float((lo+hi)/2)
    for ia, va in enumerate(levels[0]):
        for ib, vb in enumerate(levels[1]):
            indices = [i for i,row in enumerate(rows) if row[:2] == [va,vb]]
            ca, cb = contrasts(va,levels[0]), contrasts(vb,levels[1])
            vector = [1.] + ca + cb + ([x*y for x in ca for y in cb] if interaction else [])
            estimate = math.fsum(x*b for x,b in zip(vector,beta))
            se = math.sqrt(max(0.,float((mp.matrix([vector])*covariance*mp.matrix(vector))[0])*mse))
            label_a, label_b = labels.get('factor:A:'+str(ia+1),str(va)), labels.get('factor:B:'+str(ib+1),str(vb))
            cells.append({'Group':labels.get('cell:'+str(ia+1)+':'+str(ib+1),label_a+' × '+label_b),'n':len(indices),'Mean':estimate,'CI95':[estimate-critical*se,estimate+critical*se]})
            if indices:
                residuals.append([s.Float(y[i]-fitted[i]) for i in indices]); residual_labels.append(label_a+' × '+label_b)
    b_labels=[labels.get('factor:B:'+str(i+1),str(value)) for i,value in enumerate(levels[1])]
    a_labels=[labels.get('factor:A:'+str(i+1),str(value)) for i,value in enumerate(levels[0])]
    lines=[[[ib+1,cells[ia*len(levels[1])+ib]['Mean']] for ib in range(len(levels[1]))] for ia in range(len(levels[0]))]
    engine.statistics_plots=[{'kind':'interaction','title':'Factor interaction plot','points':[point for line in lines for point in line], 'assignments':[ia+1 for ia,line in enumerate(lines) for _ in line], 'lineGroups':lines,'lineLabels':a_labels,'xLabels':b_labels,'features':[labels.get('factor:B','Factor B'),labels.get('response','Mean response')]}]
    engine.statistics_residuals = {'groups':residuals,'labels':residual_labels}
    engine.note = 'Type III tests with sum-to-zero contrasts. Independent observations, normal errors and common residual variance are assumed. Main effects average equally over factor levels; interpret interaction first. Empty cells can make the interaction model unidentifiable.'
    return {'n':len(rows),'Residual df':df_error,'ANOVA':table_rows,'Cell means':cells,'Interaction included':interaction,'Method':'Type III / sum contrasts'}


def linear_model(engine,a):
    """General OLS: numeric covariates, categorical factors and interactions."""
    rows=table(a[0],minimum=4,columns=2)
    require(len(a)<2 or isinstance(a[1],(list,tuple)), 'Categorical positions must be a list')
    categorical=[integer(v,1,len(rows[0])-1)-1 for v in a[1]] if len(a)>1 else []
    order=integer(a[2],1,len(rows[0])-1) if len(a)>2 else 1
    ss_type=integer(a[3],2,3) if len(a)>3 else 3
    coding=str(a[4]) if len(a)>4 else 'sum'
    require(not(categorical and order>1 and ss_type==3 and coding!='sum'), 'Use sum contrasts for Type III factorial effects')
    design,terms,names,levels=factorial_design([row[:-1] for row in rows],categorical,order,coding)
    fit=least_squares(design,[row[-1] for row in rows]); beta,covariance,sse,fitted=fit
    df_error=len(rows)-len(beta); require(sse>0,'Residual variance must be positive'); mse=sse/df_error
    labels=engine.request.get('statisticsTermLabels') or {}
    def term_name(variables): return ' × '.join(labels.get('x'+str(j+1),'x'+str(j+1)) for j in variables)
    tests=[]
    for (variables,_),test in zip(terms,term_f_tests(design,[row[-1] for row in rows],terms,ss_type,fit)):
        ss=test['SS'];tests.append(dict(Term=term_name(variables),**test,MS=ss/test['df'],**{'Partial η²':ss/(ss+sse)}))
    tests.append(dict(Term='Residual',SS=sse,df=df_error,MS=mse))
    from calc_statistics import _t_sf, _quantile, _t_cdf
    proxy=type('Precision',(),{'precision':engine.precision})()
    with mp.workdps(engine.precision+10):
        critical=float(_quantile(lambda t:_t_cdf(t,mp.mpf(df_error)),s.Rational(975,1000),proxy,0,4))
    coefficients=[]
    for j,(coefficient,value) in enumerate(zip(names,beta)):
        import re
        def label_token(match):
            at=int(match[1])-1; title=labels.get('x'+str(at+1),'x'+str(at+1))
            if match[2] is not None:
                index=levels[at].index(float(match[2]))+1
                title+='['+labels.get('level:'+str(at+1)+':'+str(index),match[2])+']'
            return title
        label=re.sub(r'x(\d+)(?:\[([^]]+)\])?',label_token,coefficient)
        se=math.sqrt(max(0.,float(covariance[j,j])*mse)); t=value/se if se else 0.
        coefficients.append({'Term':label,'Estimate':value,'SE':se,'t':t,'p':float(2*_t_sf(abs(t),df_error)),'CI95':[value-critical*se,value+critical*se]})
    groups={}
    for i,row in enumerate(rows):
        key=tuple(row[j] for j in categorical)
        groups.setdefault(key,[]).append(s.Float(row[-1]-fitted[i]))
    engine.statistics_residuals={'groups':list(groups.values()),'labels':[' × '.join(labels.get('level:'+str(j+1)+':'+str(levels[j].index(value)+1),str(value)) for j,value in zip(categorical,key)) or 'Residuals' for key in groups]}
    mean=math.fsum(row[-1] for row in rows)/len(rows); total=math.fsum((row[-1]-mean)**2 for row in rows)
    result={'n':len(rows),'Residual df':df_error,'R²':1-sse/total if total else 0.,'Adjusted R²':1-(sse/df_error)/(total/(len(rows)-1)) if total else 0.,'ANOVA':tests,'Coefficients':coefficients,'Method':'Type '+('II' if ss_type==2 else 'III')+' / '+coding+' contrasts','Interaction order':order}
    if len(beta)>1: result['Overall model']=partial_f_test(design,[row[-1] for row in rows],list(range(1,len(beta))),fit)
    engine.note='OLS with categorical contrasts and hierarchical interaction terms. ANOVA terms jointly test coefficient blocks. Type II respects marginality; Type III tests each term after all others with sum contrasts for factorial interactions. Normal independent errors and common variance are assumed. Numeric interaction main effects are evaluated at zero; center covariates for a meaningful reference. Treat an important interaction before interpreting main effects.'
    return result
