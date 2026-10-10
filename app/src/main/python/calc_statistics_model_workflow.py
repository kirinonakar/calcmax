"""Snapshot the fitted indicators and options for EFA → CFA → SEM actions."""
import math


def model_workflow(name,value,inputs,labels):
    if name not in ('efa','cfa') or not inputs or inputs[0]!=name: return None
    args=inputs[1]; rows=args[0]
    def token(v):
        if str(v) in ('NA','nan','None'): return 'NA'
        number=float(v)
        if not math.isfinite(number): return 'NA'
        return repr(number)
    data=[[token(v) for v in row] for row in rows]
    p=len(data[0]); feature_labels={f'feature:{i+1}':labels.get(f'feature:{i+1}',f'Feature {i+1}') for i in range(p)}
    feature_labels.update({key:label for key,label in labels.items() if key.startswith('group:')})
    if name=='efa':
        k=int(value['Factors'])
        loadings=[[float(v) for v in row] for row in value['loadings']]
        factors=[max(range(k),key=lambda j:abs(row[j]))+1 for row in loadings]
        cross=[[i+1,j+1] for i,row in enumerate(loadings) for j,v in enumerate(row) if j+1!=factors[i] and abs(v)>=.3]
        missing='complete'; groups=[]; invariance='configural'; estimator='ml'
    else:
        factors=[int(v) for v in args[1]] if len(args)>1 else [1]*p
        k=max(factors)
        cross=[[int(v) for v in pair] for pair in args[2]] if len(args)>2 else []
        missing=str(args[3]) if len(args)>3 else 'complete'
        groups=[token(v) for v in args[4]] if len(args)>4 else []
        invariance=str(args[5]) if len(args)>5 else 'configural'
        estimator=str(args[6]) if len(args)>6 else 'ml'
    workflow={'target':'cfa' if name=='efa' else 'sem','data':data,'factors':factors,'factorCount':k,
            'cross':cross,'missing':missing,'groups':groups,'invariance':invariance,'estimator':estimator,
            'termLabels':feature_labels}
    if name=='efa': workflow.update({'efaLoadings':loadings,'crossThreshold':.3})
    return workflow
