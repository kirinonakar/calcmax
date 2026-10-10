"""One-factor MANOVA, LDA/QDA and independent agglomerative clustering."""
import math
import heapq
import mpmath as mp
from calc_limits import within_limit
from calc_shared import require
from calc_advanced_common import table, integer, option, mean, inverse
from calc_advanced_survey import covariance, correlation
from calc_statistics import _f_sf


def positive(matrix):
    diagonal=[math.sqrt(float(matrix[i,i])) if matrix[i,i]>0 else 0 for i in range(matrix.rows)]
    require(all(diagonal),'Covariance is singular; remove redundant variables or add observations')
    normalized=mp.matrix([[matrix[i,j]/(diagonal[i]*diagonal[j]) for j in range(matrix.rows)] for i in range(matrix.rows)])
    require(min(mp.eigsy(normalized,eigvals_only=True))>1e-9,'Covariance is singular; remove redundant variables or add observations')
    return inverse(matrix)


def calculate(engine,name,a):
    rows=table(a[0],2,1 if name=='hcluster' else 2); n=len(rows)
    if name=='hcluster': return cluster(rows,a)
    at=0 if name=='manova' else -1
    labels=sorted(set(r[at] for r in rows)); g=len(labels)
    require(g>=2,'Select at least two groups')
    features=[r[1:] if name=='manova' else r[:-1] for r in rows]; p=len(features[0])
    groups=[[x for x,r in zip(features,rows) if r[at]==label] for label in labels]
    require(all(len(group)>1 for group in groups),'At least two observations per group are required')
    centers=[[mean(c) for c in zip(*group)] for group in groups]
    covariances=[covariance(group)[0] for group in groups]
    e=sum((cov*(len(group)-1) for cov,group in zip(covariances,groups)),mp.zeros(p)); v=n-g
    if name=='manova':
        require(p>=2 and v>=p,'MANOVA requires multiple responses and enough residual degrees of freedom')
        positive(e); overall=[mean(c) for c in zip(*features)]
        h=mp.zeros(p)
        for group,center in zip(groups,centers):
            delta=mp.matrix([x-y for x,y in zip(center,overall)]); h+=len(group)*delta*delta.T
        # Whiten E+H to compute bounded real generalized roots.
        vals,vec=mp.eigsy(e+h); whitener=vec*mp.diag([1/mp.sqrt(t) for t in vals])*vec.T
        roots=[max(0.,min(1.-1e-14,float(t))) for t in mp.eigsy(whitener*h*whitener,eigvals_only=True)]
        q=g-1; s=min(p,q); m=(abs(p-q)-1)/2; nn=(v-p-1)/2
        pillai=sum(roots); wilks=math.prod(1-t for t in roots); hotelling=sum(t/(1-t) for t in roots); roy=max(t/(1-t) for t in roots)
        df1=s*(2*m+s+1); df2=s*(2*nn+s+1); f=(df2/df1)*pillai/(s-pillai)
        tests=[{'Test':'Pillai trace','Statistic':pillai,'F':f,'df1':df1,'df2':df2,'p':float(_f_sf(f,df1,df2))}]
        t=math.sqrt((p*p*q*q-4)/(p*p+q*q-5)) if p*p+q*q>5 else 1.
        df1=p*q; df2=(v-(p-q+1)/2)*t-(p*q-2)/2
        require(df2>0,'More observations are required for MANOVA inference')
        powered=wilks**(1/t); f=(1-powered)/powered*df2/df1
        tests.append({'Test':'Wilks lambda (Rao F)','Statistic':wilks,'F':f,'df1':df1,'df2':df2,'p':float(_f_sf(f,df1,df2))})
        return {'n':n,'groups':g,'responses':p,'df residual':v,'Multivariate tests':tests,'Hotelling–Lawley trace':hotelling,'Roy largest root':roy,
                'Group means':[{'group':'group:'+str(int(label)),'n':len(group),**{'response:'+str(j+1):value for j,value in enumerate(center)}} for label,group,center in zip(labels,groups,centers)],
                'Error SSCP':e.tolist(),'Hypothesis SSCP':h.tolist(),'Assumptions':'One independent categorical factor; multivariate normal errors and equal within-group covariance. Pillai F and Wilks Rao F approximations; no repeated or factorial MANOVA.'}
    method=option(a,1,'lda'); require(method in ('lda','qda'),'Choose LDA or QDA')
    prior=option(a,2,'empirical'); require(prior in ('empirical','equal'),'Choose empirical or equal priors')
    pooled=e/v; matrices=[pooled]*g if method=='lda' else covariances
    inverses=[positive(matrix) for matrix in matrices]
    priors=[len(group)/n if prior=='empirical' else 1/g for group in groups]
    def prediction(x):
        logs=[]
        for center,inv,cov,prob in zip(centers,inverses,matrices,priors):
            d=mp.matrix([v-m for v,m in zip(x,center)])
            logs.append(math.log(prob)-float((d.T*inv*d)[0])/2-float(mp.log(mp.det(cov)))/2)
        maximum=max(logs); probs=[math.exp(v-maximum) for v in logs]; total=sum(probs)
        return labels[max(range(g),key=logs.__getitem__)],[v/total for v in probs]
    predictions=[prediction(x)[0] for x in features]
    confusion=[[sum(r[at]==left and fit==right for r,fit in zip(rows,predictions)) for right in labels] for left in labels]
    new=table(a[3],1,p) if len(a)>3 else []
    require(all(len(row)==p for row in new),'Prediction rows must match the feature count')
    return {'n':n,'Method':method.upper(),'Class order':labels,'Priors':priors,'Class means':centers,'Training predictions':predictions,
            'Training confusion matrix':confusion,'Training accuracy':sum(r[at]==fit for r,fit in zip(rows,predictions))/n,
            'Predictions':[{'Observation':i+1,'Predicted class':prediction(x)[0],**{'P(class '+str(label)+')':prob for label,prob in zip(labels,prediction(x)[1])}} for i,x in enumerate(new)],
            'Assumptions':'Multivariate normal classes; LDA uses pooled unbiased within-class covariance, QDA uses separate unbiased covariances. Training accuracy is resubstitution, not validation. No automatic regularization.'}


def cluster(rows,a):
    n=len(rows); p=len(rows[0]); k=integer(a[1],1,n) if len(a)>1 else 2
    require(within_limit(n,1000),'Hierarchical clustering uses quadratic memory (default limit: 1000 observations)')
    method=option(a,2,'ward'); standardize=integer(a[3],0,1) if len(a)>3 else 1
    require(method in ('single','complete','average','ward'),'Choose single, complete, average or Ward linkage')
    if standardize:
        _,centers,scales=correlation(rows); rows=[[(v-centers[j])/scales[j] for j,v in enumerate(row)] for row in rows]
    # Distances and Lance-Williams updates are independent of heatmap ordering.
    active=set(range(n)); sizes={i:1 for i in active}; members={i:[i] for i in active}
    distances={(i,j):math.sqrt(sum((x-y)**2 for x,y in zip(rows[i],rows[j]))) for i in range(n) for j in range(i+1,n)}
    queue=[(value,i,j) for (i,j),value in distances.items()]; heapq.heapify(queue)
    def distance(i,j): return distances[min(i,j),max(i,j)]
    merges=[]; labels=None
    if k==n: labels=list(range(1,n+1))
    for step in range(n-1):
        while True:
            height,i,j=heapq.heappop(queue)
            if i in active and j in active: break
        new=n+step
        if len(active)==k and labels is None:
            labels=[0]*n
            for group,old in enumerate(sorted(active),1):
                for item in members[old]: labels[item]=group
        for old in active-{i,j}:
            left,right=distance(i,old),distance(j,old)
            if method=='single': value=min(left,right)
            elif method=='complete': value=max(left,right)
            elif method=='average': value=(sizes[i]*left+sizes[j]*right)/(sizes[i]+sizes[j])
            else: value=math.sqrt(max(0.,((sizes[old]+sizes[i])*left**2+(sizes[old]+sizes[j])*right**2-sizes[old]*height**2)/(sizes[old]+sizes[i]+sizes[j])))
            distances[old,new]=value
            heapq.heappush(queue,(value,old,new))
            distances.pop((min(i,old),max(i,old))); distances.pop((min(j,old),max(j,old)))
        distances.pop((i,j))
        sizes[new]=sizes[i]+sizes[j]; members[new]=members.pop(i)+members.pop(j)
        active-= {i,j}; active.add(new)
        merges.append({'Left node':i+1,'Right node':j+1,'Distance':height,'Size':sizes[new]})
    if labels is None: labels=[1]*n
    return {'n':n,'Linkage':method,'Standardized':standardize,'clusters':k,'labels (1-based)':labels,'Merge tree':merges,
            'Cluster sizes':[{'Cluster':i,'n':labels.count(i)} for i in range(1,k+1)],'Assumptions':'Euclidean distance; Ward uses the variance-minimizing Euclidean update. Leaves are observations 1…n, merge nodes n+1…2n−1. Ties resolve by node order.'}
