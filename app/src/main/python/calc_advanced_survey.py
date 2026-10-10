"""Reliability, principal-axis/PCA extraction and rotated loadings, without NumPy."""
import math
import mpmath as mp
from calc_shared import require, MathError
from calc_advanced_common import table, integer, option, mean, inverse, number
from calc_advanced_factor_rotation import oblique, parallel_analysis
from calc_statistics import _chisq_sf


def covariance(rows, divisor=None):
    n=len(rows); p=len(rows[0]); centers=[mean(c) for c in zip(*rows)]
    matrix=mp.matrix([[math.fsum((r[i]-centers[i])*(r[j]-centers[j]) for r in rows)/(divisor or n-1) for j in range(p)] for i in range(p)])
    return matrix,centers


def correlation(rows):
    cov,centers=covariance(rows); scales=[math.sqrt(float(cov[i,i])) for i in range(cov.rows)]
    require(all(v>0 for v in scales),'Selected items must vary')
    return mp.matrix([[cov[i,j]/(scales[i]*scales[j]) for j in range(cov.rows)] for i in range(cov.rows)]),centers,scales


def alpha(cov):
    p=cov.rows; total=float(sum(cov))
    require(p>=2 and total>0,'At least two items and positive total-score variance are required')
    return p/(p-1)*(1-float(sum(cov[i,i] for i in range(p)))/total)


def varimax(loadings):
    p,k=loadings.rows,loadings.cols
    if k==1: return loadings
    norms=[math.sqrt(float(sum(loadings[i,j]**2 for j in range(k)))) for i in range(p)]
    scaled=mp.matrix([[loadings[i,j]/(norms[i] or 1.) for j in range(k)] for i in range(p)])
    rotation=mp.eye(k); previous=0.
    for _ in range(200):
        current=scaled*rotation
        sums=[sum(current[i,j]**2 for i in range(p)) for j in range(k)]
        target=mp.matrix([[current[i,j]**3-current[i,j]*sums[j]/p for j in range(k)] for i in range(p)])
        u,d,v=mp.svd(scaled.T*target); rotation=u*v; value=float(sum(d))
        if previous and abs(value-previous)<1e-12*previous: break
        previous=value
    else: raise MathError('Varimax rotation did not converge')
    result=scaled*rotation
    return mp.matrix([[result[i,j]*norms[i] for j in range(k)] for i in range(p)])


def calculate(engine,name,a):
    rows=table(a[0],3,2); p=len(rows[0]); n=len(rows)
    if name=='cronbach':
        mode=option(a,1,'raw'); require(mode in ('raw','standardized'),'Choose raw or standardized alpha')
        cov,_=covariance(rows); corr,_,_=correlation(rows); used=corr if mode=='standardized' else cov
        items=[]
        for j in range(p):
            rest=[i for i in range(p) if i!=j]; restvar=float(sum(used[i,k] for i in rest for k in rest))
            cross=float(sum(used[j,i] for i in rest))
            items.append({'term':'feature:'+str(j+1),'Corrected item-total correlation':cross/math.sqrt(float(used[j,j])*restvar) if restvar>0 else None,
                          'Alpha if deleted':alpha(mp.matrix([[used[i,k] for k in rest] for i in rest])) if p>2 and restvar>0 else None})
        return {'n':n,'items':p,'Cronbach α':alpha(used),'Raw α':alpha(cov),'Standardized α':alpha(corr),'Item diagnostics':items,
                'Assumptions':'Complete rows; reverse-code items before analysis. Alpha is internal consistency, not evidence of unidimensionality or validity.'}
    rotation=option(a,2,'varimax'); extraction=option(a,3,'pa')
    require(rotation in ('varimax','none','promax','oblimin'),'Choose varimax, promax, oblimin or none')
    require(extraction in ('pa','pca'),'Choose pa (principal axis) or pca (principal components)')
    corr,centers,scales=correlation(rows); inv=inverse(corr)
    require(min(mp.eigsy(corr,eigvals_only=True))>1e-9,'EFA requires a positive-definite correlation matrix')
    automatic=len(a)>1 and str(a[1])=='parallel'
    samples=integer(a[4],0,2000,capacity=True) if len(a)>4 else (100 if automatic else 0)
    require(not automatic or samples>=20,'Automatic parallel selection requires at least 20 simulations')
    require(samples==0 or samples>=20,'Use at least 20 parallel simulations')
    seed=integer(a[5],0,2147483647) if len(a)>5 else 0
    percentile=number(a[6]) if len(a)>6 else .95
    require(0<percentile<1,'Parallel percentile must be between 0 and 1')
    parallel=parallel_analysis(rows,corr,extraction,samples,seed,percentile) if samples else {}
    k=parallel['Suggested factors'] if automatic else integer(a[1],1,p if extraction=='pca' else p-1) if len(a)>1 else 1
    require(k>0,'Parallel analysis suggests no factors; inspect eigenvalues with a fixed factor count')
    require(k<=p if extraction=='pca' else k<p,'Too many factors for principal-axis extraction')
    h=[1-1/float(inv[i,i]) for i in range(p)]
    for iteration in range(1000):
        reduced=corr.copy()
        if extraction=='pa':
            for i in range(p): reduced[i,i]=h[i]
        eigen,vectors=mp.eigsy(reduced); selected=list(range(p-1,p-k-1,-1))
        require(all(eigen[j]>0 for j in selected),'Too many factors for the positive common eigenvalues')
        loadings=mp.matrix([[vectors[i,j]*mp.sqrt(eigen[j]) for j in selected] for i in range(p)])
        updated=[float(sum(loadings[i,j]**2 for j in range(k))) for i in range(p)]
        require(max(updated)<1+1e-7,'Heywood case: communality exceeds one; reduce factors or revise items')
        if extraction=='pca' or max(abs(x-y) for x,y in zip(updated,h))<1e-7: break
        h=updated
    else: raise MathError('Principal-axis factoring did not converge')
    phi=mp.eye(k)
    if rotation=='varimax': loadings=varimax(loadings)
    elif rotation in ('promax','oblimin'): loadings,phi=oblique(loadings,rotation,varimax)
    for j in range(k):
        pivot=max(range(p),key=lambda i:abs(loadings[i,j]))
        if loadings[pivot,j]<0:
            for i in range(p): loadings[i,j]*=-1
            for i in range(k):
                if i!=j: phi[i,j]*=-1; phi[j,i]*=-1
    partial=mp.matrix([[0 if i==j else -inv[i,j]/mp.sqrt(inv[i,i]*inv[j,j]) for j in range(p)] for i in range(p)])
    r2=float(sum(corr[i,j]**2 for i in range(p) for j in range(p) if i!=j)); q2=float(sum(v*v for v in partial))
    bartlett=-(n-1-(2*p+5)/6)*float(mp.log(mp.det(corr))); df=p*(p-1)//2
    require(n-1>(2*p+5)/6,'More observations are required for Bartlett sphericity inference')
    structure=loadings*phi
    scores=mp.matrix([[(v-centers[j])/scales[j] for j,v in enumerate(row)] for row in rows])*inv*structure
    return {'n':n,'Extraction':'Principal axis factoring (SMC start)' if extraction=='pa' else 'Principal components (correlation PCA)','Rotation':rotation,'Factors':k,'KMO':r2/(r2+q2),**parallel,
            'Bartlett χ²':bartlett,'Bartlett df':df,'Bartlett p':float(_chisq_sf(bartlett,df)),
            'Item diagnostics':[{'term':'feature:'+str(i+1),'Communality':updated[i],'Uniqueness':1-updated[i],'KMO':float(sum(corr[i,j]**2 for j in range(p) if j!=i)/sum(corr[i,j]**2+partial[i,j]**2 for j in range(p) if j!=i))} for i in range(p)],
            'loadings':loadings.tolist(),'Structure loadings':structure.tolist(),'Factor correlations':phi.tolist(),'scores':scores.tolist(),'Correlation eigenvalues':list(reversed(list(mp.eigsy(corr,eigvals_only=True)))),
            ('Pattern squared-loading sums (not additive variance)' if rotation in ('promax','oblimin') else 'Factor variance'):[float(sum(loadings[i,j]**2 for i in range(p))) for j in range(k)],
            'Assumptions':'Pearson correlations of complete numeric rows. Principal axis estimates common factors; PCA extracts components and yields different values. Promax (power 4, Kaiser normalization) and oblimin (delta 0) allow correlated factors: loadings are pattern coefficients, structure loadings are correlations. Oblique squared pattern loadings are not additive explained variance. Scores are regression estimates. Parallel analysis uses seeded normal simulations with the selected extraction eigenvalues. No ordinal/polychoric estimator.'}
