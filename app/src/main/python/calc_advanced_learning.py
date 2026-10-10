"""Cross-validation, PCA, clustering and missing-data imputation."""
from calc_limits import within_limit
import math
import random
import statistics
import mpmath as mp
from calc_shared import MathError, require
from calc_advanced_common import (
    dot, integer, inverse, mean, number, option, regression_data, table, variance,
)


def least_squares(design,target):
    """Normal-equation least squares with ridge escalation for degenerate imputation designs."""
    size=len(design[0])
    gram=[[math.fsum(row[i]*row[j] for row in design) for j in range(size)] for i in range(size)]
    right=[math.fsum(row[i]*v for row,v in zip(design,target)) for i in range(size)]
    for ridge in (0.0,1e-10,1e-7):
        matrix=mp.matrix([[gram[i][j]+(ridge*gram[j][j] if i==j else 0.0) for j in range(size)] for i in range(size)])
        try: return [float(v) for v in inverse(matrix)*mp.matrix(right)]
        except MathError: continue
    raise MathError('Imputation predictors are collinear; remove duplicate columns')


def regression_imputation(values):
    """Iterated conditional-mean (single) regression imputation."""
    n=len(values); columns=len(values[0])
    filled=[list(row) for row in values]
    for j in range(columns):
        fill=mean([row[j] for row in values if row[j] is not None])
        for i in range(n):
            if filled[i][j] is None: filled[i][j]=fill
    incomplete=[j for j in range(columns) if any(row[j] is None for row in values)]
    sweeps=0
    for sweep in range(40):
        largest=0.0
        for j in incomplete:
            predictors=[c for c in range(columns) if c!=j]
            observed=[i for i in range(n) if values[i][j] is not None]
            require(len(observed)>len(predictors),'Regression imputation needs more observed rows than predictors')
            centers=[]; scales=[]
            for c in predictors:
                sample=[filled[i][c] for i in observed]
                centers.append(mean(sample)); scales.append(math.sqrt(variance(sample)) if len(sample)>1 else 0.0)
            def standardize(i): return [1.0]+[(filled[i][c]-center)/scale if scale else 0.0 for c,center,scale in zip(predictors,centers,scales)]
            beta=least_squares([standardize(i) for i in observed],[values[i][j] for i in observed])
            for i in range(n):
                if values[i][j] is not None: continue
                prediction=dot(standardize(i),beta)
                require(math.isfinite(prediction),'Regression imputation prediction is outside the numeric range')
                largest=max(largest,abs(prediction-filled[i][j])); filled[i][j]=prediction
        sweeps=sweep+1
        if largest<1e-12: break
    return filled,sweeps


def neighbor_imputation(values,neighbors):
    """k-nearest-neighbour (single) imputation on standardized observed coordinates."""
    n=len(values); columns=len(values[0])
    require(within_limit(sum(1 for row in values for v in row if v is None)*n*columns,8000000),'k-NN imputation is too large; use regression or mean for this table')
    centers=[]; scales=[]
    for j in range(columns):
        sample=[row[j] for row in values if row[j] is not None]
        centers.append(mean(sample)); scales.append(math.sqrt(variance(sample)) if len(sample)>1 else 0.0)
    standardized=[[None if v is None else (v-centers[j])/scales[j] if scales[j] else 0.0 for j,v in enumerate(row)] for row in values]
    filled=[list(row) for row in values]
    for i in range(n):
        for j in range(columns):
            if values[i][j] is not None: continue
            candidates=[]
            for h in range(n):
                if values[h][j] is None: continue
                distance=0.0; shared=False
                for c in range(columns):
                    if c==j: continue
                    a=standardized[i][c]; b=standardized[h][c]
                    if a is None or b is None: continue
                    distance+=(a-b)**2; shared=True
                candidates.append((0 if shared else 1,distance,h))
            require(candidates,'k-NN imputation needs observed values in every column')
            candidates.sort()
            filled[i][j]=mean([values[h][j] for _,_,h in candidates[:neighbors]])
    return filled,neighbors


def machine_fit(train,model,alpha,ratio):
    """Penalized linear or logistic fit on standardized columns; returns a predictor for new rows."""
    from calc_machine_learning import _linear_core, _logistic_core
    xs=[[float(v) for v in row[:-1]] for row in train]; ys=[float(row[-1]) for row in train]
    size=len(xs); width=len(xs[0])
    require(size>=2 and width>=1,'Cross-validation needs complete predictor rows')
    origins=xs[0]
    offsets=[[row[j]-origins[j] for j in range(width)] for row in xs]
    centers=[math.fsum(row[j]/size for row in offsets) for j in range(width)]
    centered=[[row[j]-centers[j] for row in offsets] for j in range(width)]
    scales=[]
    for column in centered:
        extreme=max(map(abs,column))
        scales.append(extreme*math.sqrt(math.fsum((v/extreme)**2/size for v in column)) if extreme else 0.0)
    require(all(math.isfinite(v) for v in centers+scales),'Regression data range is too large')
    standardized=[[v/scale for v in column] if scale else [0.0]*size for column,scale in zip(centered,scales)]
    active=[scale>0 for scale in scales]
    def design(row): return [(row[j]-origins[j]-centers[j])/scales[j] if scales[j] else 0.0 for j in range(width)]
    if model=='logistic':
        require(set(ys)<={0.0,1.0} and 0<sum(ys)<size,'Each logistic fold needs both 0 and 1 responses')
        intercept,beta,_,_,converged=_logistic_core(standardized,active,ys,0.0,alpha)
        require(converged,'Logistic fit did not converge; increase the penalty')
        def predict(row):
            linear=intercept+math.fsum(b*v for b,v in zip(beta,design(row)))
            return 1.0/(1.0+math.exp(-max(-30.0,min(30.0,linear))))
        return predict
    yorigin=ys[0]; shifted=[v-yorigin for v in ys]; ymean=math.fsum(v/size for v in shifted)
    target=[v-ymean for v in shifted]; yscale=max(map(abs,target)) or 1.0
    residual=[v/yscale for v in target]
    beta,_,_,converged=_linear_core(standardized,active,residual,alpha*ratio/yscale,alpha*(1-ratio))
    require(converged,'Regularized regression did not converge; increase the penalty')
    def predict(row): return yorigin+ymean+yscale*math.fsum(b*v for b,v in zip(beta,design(row)))
    return predict


def binary_auc(labels,scores):
    """Rank-based AUC with average ranks for ties; None when a class is missing."""
    positive=sum(1 for v in labels if v); count=len(labels)-positive
    if not positive or not count: return None
    order=sorted(range(len(labels)),key=scores.__getitem__)
    ranks=[0.0]*len(order); position=0
    while position<len(order):
        end=position
        while end+1<len(order) and scores[order[end+1]]==scores[order[position]]: end+=1
        average=(position+end)/2+1
        for index in range(position,end+1): ranks[order[index]]=average
        position=end+1
    return (sum(ranks[i] for i in range(len(labels)) if labels[i])-positive*(positive+1)/2)/(positive*count)


def calculate(engine,name,a):
    if name=='impute':
        require(isinstance(a[0],list) and len(a[0])>=2 and all(isinstance(r,list) for r in a[0]),'Enter a rectangular table; use NA for missing cells')
        rows=a[0]; require(all(len(r)==len(rows[0]) for r in rows) and len(rows[0])>0,'Enter a nonempty rectangular table')
        method=option(a,1,'mean'); require(method in ('mean','median','mode','regression','knn'),'Use mean, median, mode, regression, or knn')
        neighbors=integer(a[2],1,100,capacity=True) if len(a)>2 else 5
        missing=lambda v: str(v) in ('NA','nan')
        values=[[None if missing(v) else number(v) for v in row] for row in rows]
        for column in zip(*values): require(any(v is not None for v in column),'Cannot impute a completely missing column')
        count=sum(v is None for row in values for v in row)
        def publish(result):
            engine.imputation_result={'data':[[format(value,'.17g') for value in row] for row in result['data']],'imputedCells':count}
            return result
        if method in ('mean','median','mode'):
            fills=[]
            for column in zip(*values):
                observed=[v for v in column if v is not None]
                fills.append(mean(observed) if method=='mean' else statistics.median(observed) if method=='median' else statistics.multimode(observed)[0])
            engine.note += ' Single columnwise imputation; does not account for imputation uncertainty. NA denotes missing. Mode ties use first appearance.'
            return publish({'data':[[fills[i] if v is None else v for i,v in enumerate(row)] for row in values],'fill values':fills,'imputed cells':count,'method':method})
        if method=='regression':
            filled,sweeps=regression_imputation(values)
            engine.note += ' Single regression imputation with iterated conditional means (chained equations); does not account for imputation uncertainty.'
            return publish({'data':filled,'imputed cells':count,'sweeps':sweeps,'method':method})
        filled,neighbors=neighbor_imputation(values,neighbors)
        engine.note += ' Single k-nearest-neighbour imputation on standardized observed coordinates; does not account for imputation uncertainty.'
        return publish({'data':filled,'imputed cells':count,'neighbors':neighbors,'method':method})
    rows=table(a[0]); n=len(rows); p=len(rows[0])
    if name=='crossvalidate':
        require(p>=2,'Rows: predictors then response')
        k=integer(a[1],2,n) if len(a)>1 else min(5,n); seed=integer(a[2],0,2**32-1) if len(a)>2 else 0
        split=option(a,3,'random'); require(split in ('random','blocked','stratified'),'Split: random, blocked, or stratified')
        model=option(a,4,'linear'); require(model in ('linear','ridge','lasso','elasticnet','logistic'),'Model: linear, ridge, lasso, elasticnet, or logistic')
        alpha,ratio=0.0,0.0
        if model in ('ridge','lasso','logistic'):
            alpha=number(a[5]) if len(a)>5 else .1; require(alpha>0,'Penalty alpha must be positive')
        elif model=='elasticnet':
            if len(a)>5:
                require(isinstance(a[5],(list,tuple)) and len(a[5])==2,'Elastic net options are [alpha,l1 ratio]')
                alpha=number(a[5][0]); ratio=number(a[5][1])
            else: alpha,ratio=.1,.5
            require(alpha>0 and 0<=ratio<=1,'Elastic net needs positive alpha and an L1 ratio from 0 to 1')
        responses=[row[-1] for row in rows]
        if split=='stratified': require(set(responses)<={0.0,1.0},'Stratified folds require a 0/1 response')
        generator=random.Random(seed)
        if split=='blocked':
            bounds=[f*n//k for f in range(k+1)]; folds=[list(range(bounds[f],bounds[f+1])) for f in range(k)]
        elif split=='stratified':
            assignment=[0]*n
            for label in (0.0,1.0):
                group=[i for i in range(n) if responses[i]==label]; generator.shuffle(group)
                for position,i in enumerate(group): assignment[i]=position%k
            folds=[[i for i in range(n) if assignment[i]==f] for f in range(k)]
        else:
            order=list(range(n)); generator.shuffle(order); folds=[order[f::k] for f in range(k)]
        predictions=[0.0]*n; errors=[]
        for fold in range(k):
            test=folds[fold]; member=set(test); train=[i for i in range(n) if i not in member]
            require(len(train)>p,'Each training fold needs more rows than predictors')
            if model=='linear':
                x,y=regression_data([rows[i] for i in train]); X=mp.matrix(x); b=inverse(X.T*X)*X.T*mp.matrix(y)
                for i in test: predictions[i]=float(dot([1]+rows[i][:-1],b))
                errors.append(mean([(rows[i][-1]-predictions[i])**2 for i in test]))
                continue
            fit=machine_fit([rows[i] for i in train],model,alpha,ratio)
            for i in test: predictions[i]=fit(rows[i])
            errors.append(mean([-(math.log(max(1e-12,predictions[i])) if rows[i][-1] else math.log(max(1e-12,1-predictions[i]))) for i in test]) if model=='logistic' else mean([(rows[i][-1]-predictions[i])**2 for i in test]))
        if model=='logistic':
            clipped=[min(1-1e-12,max(1e-12,v)) for v in predictions]
            result={'out-of-fold probabilities':predictions,'fold log loss':errors,'log loss':mean([-(math.log(clipped[i]) if responses[i] else math.log(1-clipped[i])) for i in range(n)]),'accuracy':mean([(v>=.5)==bool(responses[i]) for i,v in enumerate(predictions)]),'Brier score':mean([(responses[i]-predictions[i])**2 for i in range(n)]),'model':model,'split':split,'folds':k,'seed':seed}
            score=binary_auc(responses,predictions)
            if score is not None: result['AUC']=score
            engine.note += ' Out-of-fold validation of an L2-penalized logistic regression; folds use training rows only. Reports accuracy, AUC, log loss and the Brier score.'
            return result
        residuals=[(rows[i][-1]-predictions[i])**2 for i in range(n)]
        center=mean(responses); total=sum((value-center)**2 for value in responses)
        result={'out-of-fold predictions':predictions,'fold MSE':errors,'MSE':mean(residuals),'RMSE':math.sqrt(mean(residuals)),'MAE':mean([abs(rows[i][-1]-predictions[i]) for i in range(n)]),'model':model,'split':split,'folds':k,'seed':seed}
        if total>0: result['R2']=1-sum(residuals)/total
        engine.note += ' k-fold out-of-fold validation with training-only fits; blocked splits keep the row order. Grouped data still needs cluster-aware splits.'
        return result
    centers=[mean(c) for c in zip(*rows)]
    if name=='pca':
        count=integer(a[1],1,p) if len(a)>1 else p; standard=integer(a[2],0,1) if len(a)>2 else 1; scales=[math.sqrt(variance(c)) if standard else 1 for c in zip(*rows)]
        require(min(scales)>0,'Standardized PCA requires nonconstant columns')
        X=mp.matrix([[(v-centers[j])/scales[j] for j,v in enumerate(r)] for r in rows]); vals,vecs=mp.eigsy(X.T*X/(n-1)); order=list(range(p-1,-1,-1)); total=sum(vals); require(total>0,'PCA requires variation')
        V=mp.matrix([[vecs[i,j] for j in order[:count]] for i in range(p)])
        for j in range(count):
            dominant=max(range(p),key=lambda i:abs(V[i,j]))
            if V[dominant,j]<0:
                for i in range(p): V[i,j]=-V[i,j]
        engine.note += ' PCA: covariance eigendecomposition, sample-SD standardization by default. Loadings rows=features, columns=components.'
        eigenvalues=[max(0,float(vals[j])) for j in order]
        ratios=[max(0,float(vals[j]/total)) for j in order]
        scores=[[float(v) for v in row] for row in (X*V).tolist()]
        loadings=[[float(v) for v in row] for row in V.tolist()]
        labels=engine.request.get('statisticsTermLabels',{})
        features=[labels.get('feature:'+str(i+1),'Feature '+str(i+1)) for i in range(p)]
        engine.statistics_plots=[{'kind':'scree','title':'Explained variance','ratios':ratios,'eigenvalues':eigenvalues},
                                 {'kind':'scores','title':'PCA scores','points':scores,'ratios':ratios[:count]},
                                 {'kind':'loadings','title':'PCA loadings','points':loadings,'labels':features,'ratios':ratios[:count]}]
        return {'eigenvalues':eigenvalues[:count],'explained variance ratio':ratios[:count],
                'cumulative explained variance':[math.fsum(ratios[:i+1]) for i in range(count)],
                'loadings':loadings,'scores':scores,'centers':centers,'scales':scales}
    k=integer(a[1],1,n); seed=integer(a[2],0,2**32-1) if len(a)>2 else 0; rng=random.Random(seed); distinct=list(dict.fromkeys(tuple(r) for r in rows)); require(len(distinct)>=k,'k exceeds number of distinct observations')
    require(within_limit(n*k*p,1000000),'Clustering size limit exceeded')
    def distance(x,y): return sum((u-v)**2 for u,v in zip(x,y))
    best=None
    for restart in range(10):
        centroids=[list(rng.choice(distinct))]
        while len(centroids)<k:
            weights=[min(distance(r,c) for c in centroids) for r in rows]; target=rng.random()*sum(weights); cumulative=0
            for r,w in zip(rows,weights):
                cumulative+=w
                if cumulative>target: centroids.append(r[:]); break
        converged=False
        for iteration in range(200):
            labels=[min(range(k),key=lambda j:distance(r,centroids[j])) for r in rows]; new=[]
            for j in range(k):
                members=[r for r,l in zip(rows,labels) if l==j]
                new.append([mean(c) for c in zip(*members)] if members else rows[max(range(n),key=lambda i:distance(rows[i],centroids[labels[i]]))][:])
            if sum(distance(u,v) for u,v in zip(new,centroids))<1e-12: converged=True; centroids=new; break
            centroids=new
        if not converged: continue
        labels=[min(range(k),key=lambda j:distance(r,centroids[j])) for r in rows]; inertia=sum(distance(r,centroids[l]) for r,l in zip(rows,labels))
        if len(set(labels))<k: continue
        if best is None or inertia<best['inertia']: best={'labels (1-based)':[l+1 for l in labels],'centroids':centroids,'inertia':inertia,'iterations':iteration+1,'seed':seed}
    require(best is not None,'K-means did not converge')
    labels=engine.request.get('statisticsTermLabels',{})
    features=[labels.get('feature:'+str(i+1),'Feature '+str(i+1)) for i in range(p)]
    engine.statistics_plots=[{'kind':'clusters','title':'Cluster assignments','points':rows,'assignments':best['labels (1-based)'],'centroids':best['centroids'],'features':features}]
    engine.note += ' K-means++ initialization, 10 restarts, Euclidean distance on raw columns; scale features before clustering if needed.'
    return best
