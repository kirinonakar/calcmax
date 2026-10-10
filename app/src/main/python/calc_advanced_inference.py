"""P-value adjustments, effect sizes and tests of grouped or paired data."""
import math
import statistics
import mpmath as mp
from calc_shared import MathError, require
from calc_statistics import _chisq_sf, _f_sf
from calc_advanced_common import (
    mean, number, option, table, variance, vector,
)


def groups(a): return [vector(v,2) for v in a]


def oneway(g):
    require(len(g) >= 2, 'Enter at least two groups')
    n = sum(map(len,g)); overall = mean(sum(g,[]))
    between = sum(len(x)*(mean(x)-overall)**2 for x in g)
    within = sum(sum((v-mean(x))**2 for v in x) for x in g)
    require(within > 0, 'Within-group variation is required')
    f = (between/(len(g)-1))/(within/(n-len(g)))
    return {'F':f,'df1':len(g)-1,'df2':n-len(g),'p':float(_f_sf(f,len(g)-1,n-len(g))), 'eta2':between/(between+within)}


def calculate(engine, name, a):
    if name=='friedman':
        from calc_inference import _ranks
        rows=table(a[0],2,3);n=len(rows);k=len(rows[0]);sums=[0]*k;ties=0
        for row in rows:
            ranks,counts=_ranks(row)
            sums=[total+rank for total,rank in zip(sums,ranks)]
            ties+=sum(count**3-count for count in counts)
        correction=1-mp.mpf(ties)/(n*k*(k*k-1))
        require(correction>0,'Friedman requires within-subject variation')
        statistic=(12*sum(rank**2 for rank in sums)/(n*k*(k+1))-3*n*(k+1))/correction
        engine.note+=' Friedman ranks within each subject, with tie correction; chi-square approximation, independent subjects and complete repeated conditions. Small samples or few conditions can give inaccurate p values; no exact/permutation inference.'
        return {'chi2':float(statistic),'df':k-1,'p':float(_chisq_sf(statistic,k-1)),'subjects':n,'conditions':k,
                'Kendall W':float(statistic/(n*(k-1))),'mean ranks':[float(rank/n) for rank in sums],'tie correction':float(correction),'method':'Tie-corrected chi-square approximation'}
    if name == 'padjust':
        vals = vector(a[0]); method = option(a,1,'holm'); alpha = number(a[2]) if len(a)>2 else .05
        require(all(0 <= p <= 1 for p in vals) and 0<alpha<1, 'p values must lie in [0,1]; alpha in (0,1)')
        require(method in ('bonferroni','holm','fdr','bh','by'), 'Use bonferroni, holm, fdr (BH), or by')
        n = len(vals); order = sorted(range(n),key=vals.__getitem__); adj = [0.0]*n
        if method=='bonferroni': adj = [min(1,n*p) for p in vals]
        elif method=='holm':
            running = 0
            for rank,i in enumerate(order): running=max(running,min(1,(n-rank)*vals[i])); adj[i]=running
        else:
            running = 1; factor = sum(1/i for i in range(1,n+1)) if method=='by' else 1
            for rank in range(n-1,-1,-1):
                i=order[rank]; running=min(running,vals[i]*n*factor/(rank+1)); adj[i]=running
        engine.note += ' FDR uses Benjamini–Hochberg (BH); BY supports arbitrary dependence.'
        return {'raw p':vals,'adjusted p':adj,'reject (1=yes)': [int(p<=alpha) for p in adj], 'alpha':alpha,'method':method}
    if name=='cohend':
        x,y = vector(a[0],2),vector(a[1],2); paired = option(a,2,'independent')=='paired'
        require(option(a,2,'independent') in ('independent','paired'), 'Use independent or paired')
        if paired:
            require(len(x)==len(y), 'Paired samples require equal lengths')
            dif = [u-v for u,v in zip(x,y)]; sd = math.sqrt(variance(dif)); dmean = mean(dif); df=len(x)-1
        else:
            df=len(x)+len(y)-2; sd=math.sqrt(((len(x)-1)*variance(x)+(len(y)-1)*variance(y))/df); dmean=mean(x)-mean(y)
        require(sd>0, 'Effect size requires variation')
        d=dmean/sd
        return {'Cohen dz' if paired else 'Cohen d':d,'Hedges g (approximate)':d*(1-3/(4*df-1)), 'mean difference':dmean}
    if name=='eta2':
        g=groups(a); all_values=sum(g,[]); grand=mean(all_values)
        between=sum(len(x)*(mean(x)-grand)**2 for x in g); total=sum((v-grand)**2 for v in all_values)
        require(total>0,'Eta squared requires variation')
        return {'eta2':between/total}
    if name=='levene':
        g=groups(a); engine.note += ' Levene with median centers (Brown–Forsythe).'
        return oneway([[abs(v-statistics.median(x)) for v in x] for x in g])
    if name=='bartlett':
        g=groups(a); ns=[len(x) for x in g]; vs=[variance(x) for x in g]
        require(min(vs)>0, 'Each group needs positive variance')
        df=sum(ns)-len(g); pooled=sum((n-1)*v for n,v in zip(ns,vs))/df
        chi=(df*math.log(pooled)-sum((n-1)*math.log(v) for n,v in zip(ns,vs)))/(1+(sum(1/(n-1) for n in ns)-1/df)/(3*(len(g)-1)))
        return {'chi2':max(0,chi),'df':len(g)-1,'p':float(_chisq_sf(max(0,chi),len(g)-1))}
    if name=='mcnemar':
        rows=table(a[0],2,2); require(len(rows)==2 and len(rows[0])==2 and all(v>=0 and v.is_integer() for r in rows for v in r), 'Enter a 2×2 table of paired nonnegative integer counts')
        b,c=rows[0][1],rows[1][0]; n=int(b+c); method=option(a,1,'exact')
        require(method in ('exact','corrected','asymptotic'),'Use exact, corrected, or asymptotic')
        chi=(max(0,abs(b-c)-(1 if method=='corrected' else 0)))**2/n if n else 0
        p=min(1,float(2*sum(mp.binomial(n,j) for j in range(int(min(b,c))+1))/mp.mpf(2)**n)) if n and method=='exact' else float(_chisq_sf(chi,1))
        engine.note += ' McNemar tests paired counts; exact two-sided binomial is the default.'
        return {'discordant pairs':n,'chi2':chi,'p':p,'method':method}
    raise MathError('Unknown advanced analysis')
