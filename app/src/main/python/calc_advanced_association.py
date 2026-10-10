"""Uncorrected association and agreement from contingency counts."""
import math
from calc_shared import require
from calc_advanced_common import table, option
from calc_statistics import _chisq_sf


def calculate(engine,name,a):
    rows=table(a[0],2,2); nr=len(rows); nc=len(rows[0])
    require(all(v>=0 and v.is_integer() for row in rows for v in row),'Enter nonnegative integer counts')
    n=sum(map(sum,rows)); rowtotal=list(map(sum,rows)); coltotal=list(map(sum,zip(*rows)))
    require(n>0,'The count table must contain observations')
    if name=='cohenkappa':
        require(nr==nc,'Kappa requires the same categories in the same order for both raters')
        mode=option(a,1,'unweighted'); require(mode in ('unweighted','linear','quadratic'),'Choose unweighted, linear or quadratic kappa')
        weights=[[float(i!=j) if mode=='unweighted' else (abs(i-j)/(nr-1))**(2 if mode=='quadratic' else 1) for j in range(nr)] for i in range(nr)]
        observed=sum(weights[i][j]*rows[i][j] for i in range(nr) for j in range(nr))/n
        expected=sum(weights[i][j]*rowtotal[i]*coltotal[j] for i in range(nr) for j in range(nr))/n**2
        require(expected>0,'Kappa is undefined with zero expected disagreement')
        # Multinomial delta method, including the changing marginal probabilities.
        r=[v/n for v in rowtotal]; c=[v/n for v in coltotal]
        gradient=[[ -weights[i][j]/expected+observed/expected**2*(sum(weights[i][k]*c[k] for k in range(nr))+sum(weights[k][j]*r[k] for k in range(nr))) for j in range(nr)] for i in range(nr)]
        center=sum(rows[i][j]/n*gradient[i][j] for i in range(nr) for j in range(nr))
        se=math.sqrt(sum(rows[i][j]/n*(gradient[i][j]-center)**2 for i in range(nr) for j in range(nr))/n)
        value=1-observed/expected
        return {'n':int(n),'Cohen κ':value,'Weights':mode,'Observed agreement':1-observed,'Expected agreement':1-expected,'Exact agreement':sum(rows[i][i] for i in range(nr))/n,
                'SE':se,'confidence interval':[value-1.95996398454*se,value+1.95996398454*se],
                'Assumptions':'Two raters, independent paired subjects, common category order. Weighted kappa requires ordered categories. Multinomial delta-method Wald CI is asymptotic and may exceed [-1,1].'}
    require(all(rowtotal) and all(coltotal),'Every category must have a positive marginal count')
    expected=[[rowtotal[i]*coltotal[j]/n for j in range(nc)] for i in range(nr)]
    chi=sum((rows[i][j]-expected[i][j])**2/expected[i][j] for i in range(nr) for j in range(nc))
    df=(nr-1)*(nc-1)
    result={'n':int(n),'χ² (uncorrected)':chi,'df':df,'p':float(_chisq_sf(chi,df)),"Cramér’s V":math.sqrt(chi/(n*min(nr-1,nc-1))),'expected':expected,
            'Assumptions':'Independent observations; uncorrected Pearson association, no Yates correction. Sparse expected counts can invalidate the chi-square approximation.'}
    if nr==nc==2:
        result['phi']=(rows[0][0]*rows[1][1]-rows[0][1]*rows[1][0])/math.sqrt(math.prod(rowtotal)*math.prod(coltotal))
    if name=='phi': require(nr==nc==2,'Signed phi requires a 2×2 count table')
    return result
