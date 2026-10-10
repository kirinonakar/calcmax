"""Games–Howell and Tukey–Kramer report rows using shared range probabilities."""
import math
import sympy as s
from calc_shared import require
from calc_statistics import _sample_mean_variance, _mpf, _studentized_range_sf, _studentized_range_critical


def dunn_comparisons(groups, engine, adjustment='holm'):
    """Two-sided pooled midrank comparisons with tie-corrected variance."""
    from calc_advanced_common import vector, normal_p
    from calc_advanced_inference import calculate
    require(adjustment in ('holm','bonferroni','fdr','none'),'Choose Holm, Bonferroni, FDR or none')
    groups=[vector(group) for group in groups]; require(len(groups)>=2,'Dunn requires at least two groups')
    ordered=sorted((v,i) for i,group in enumerate(groups) for v in group)
    n=len(ordered); sums=[0.]*len(groups); ties=0; at=0
    while at<n:
        end=at+1
        while end<n and ordered[end][0]==ordered[at][0]: end+=1
        rank=(at+1+end)/2
        for _,i in ordered[at:end]: sums[i]+=rank
        count=end-at; ties+=count**3-count; at=end
    variance=n*(n+1)/12-ties/(12*(n-1))
    require(variance>0,'Dunn is undefined when all observations are tied')
    labels=engine.request.get('statisticsTermLabels',{})
    rows=[]
    for i in range(len(groups)):
        for j in range(i+1,len(groups)):
            difference=sums[i]/len(groups[i])-sums[j]/len(groups[j])
            se=math.sqrt(variance*(1/len(groups[i])+1/len(groups[j]))); z=difference/se
            rows.append({'Comparison':labels.get('sample:'+str(i+1),'Sample '+str(i+1))+' − '+labels.get('sample:'+str(j+1),'Sample '+str(j+1)),
                         'Mean rank difference':difference,'SE':se,'z':z,'Raw p value':normal_p(z)})
    probabilities=[row['Raw p value'] for row in rows]
    adjusted=probabilities if adjustment=='none' else calculate(engine,'padjust',[probabilities,adjustment])['adjusted p']
    for row,p in zip(rows,adjusted): row['Adjusted p value']=p
    return {'Method':'Dunn (pooled midranks, tie corrected)','Adjustment':adjustment,'Comparisons':rows}


def posthoc_comparisons(groups, method, engine):
    count=len(groups)
    moments=[_sample_mean_variance(group) for group in groups]
    total=sum(int(n) for _,_,n in moments)
    pooled=sum((n-1)*variance for _,variance,n in moments)/(total-count)
    rows=[]
    for i in range(count):
        for j in range(i+1,count):
            mi,vi,ni=moments[i];mj,vj,nj=moments[j]
            if method=='gameshowell':
                vleft,vright=vi/ni,vj/nj;var=vleft+vright
                require(var>0,'Games–Howell requires variation in every compared pair')
                df=var**2/(vleft**2/(ni-1)+vright**2/(nj-1))
            else:
                var=pooled*(1/ni+1/nj);df=s.Integer(total-count)
                require(var>0,'Tukey comparisons require within-group variation')
            difference=mi-mj
            se=float(s.N(s.sqrt(var),min(engine.precision,30)))
            degrees=float(_mpf(df,engine.precision));q=abs(float(difference))/(se/math.sqrt(2))
            critical=_studentized_range_critical(count,degrees)
            margin=critical*se/math.sqrt(2)
            labels=engine.request.get('statisticsTermLabels',{}) if hasattr(engine,'request') else {}
            first=labels.get('sample:'+str(i+1),'Sample '+str(i+1));second=labels.get('sample:'+str(j+1),'Sample '+str(j+1))
            rows.append({'Comparison':first+' − '+second,'Mean difference':difference,'SE':se,'df':df,'q':q,
                         'Adjusted p value':_studentized_range_sf(q,count,degrees),'Lower 95% CI':float(difference)-margin,'Upper 95% CI':float(difference)+margin})
    return rows


def holm_comparisons(groups, method, engine):
    """Paired t / Wilcoxon or independent Mann–Whitney, one Holm family."""
    from calc_shared import MathError
    from calc_statistics import statistical_test
    labels=engine.request.get('statisticsTermLabels',{})
    rows=[]; probabilities=[]
    for i in range(len(groups)):
        for j in range(i+1,len(groups)):
            first=labels.get('sample:'+str(i+1),'Sample '+str(i+1))
            second=labels.get('sample:'+str(j+1),'Sample '+str(j+1))
            row={'Comparison':first+' − '+second}
            try:
                args=[s.Integer(0),groups[i],groups[j]] if method=='ttestpaired' else [groups[i],groups[j]]
                result=statistical_test(engine,method,args,[])
                statistic=next(result[key] for key in ('t','W','U') if key in result)
                row.update({'Statistic':statistic,'Raw p value':result['p value'],'Method':engine.note})
                probabilities.append(float(result['p value']))
            except (MathError,ValueError,ZeroDivisionError,OverflowError) as error:
                row.update({'status':'unavailable','reason':str(error)})
                # Retain the complete comparison family in the adjustment.
                probabilities.append(1.)
            rows.append(row)
    running=0.; count=len(rows)
    for rank,index in enumerate(sorted(range(count),key=probabilities.__getitem__)):
        running=max(running,min(1.,(count-rank)*probabilities[index]))
        if 'Raw p value' in rows[index]:rows[index]['Adjusted p value']=running
    return rows
