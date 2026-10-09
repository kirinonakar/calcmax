"""Kaplan-Meier, log-rank, Cox models and grouped survival reports."""
from calc_limits import within_limit
import math
import statistics
import mpmath as mp
from calc_shared import MathError, require
from calc_statistics import _chisq_sf
from calc_advanced_common import (
    dot, inference, integer, inverse, newton, number, option,
    standardized_design, table,
)


def survival(rows, entry=-1):
    require(all(r[0] >= 0 and r[1] in (0,1) for r in rows), 'Survival rows: nonnegative time, event 0/1, then predictors')
    if entry >= 0:
        require(entry < len(rows[0]), 'Entry column is out of range')
        require(all(0 <= r[entry] < r[0] for r in rows), 'Entry time must be nonnegative and earlier than the exit time')


def calculate(engine, name, a):
    if name=='survivalanalysis': return survival_analysis(engine,a)
    if name=='kaplanmeier':
        rows=table(a[0],1,2); level=number(a[1]) if len(a)>1 else .95; require(0<level<1,'Confidence level must lie in (0,1)')
        entry=integer(a[2],-1,19,capacity=True) if len(a)>2 else -1
        require(entry>=0 or len(rows[0])==2,'Rows are [time,event]')
        survival(rows,entry); starts=[r[entry] for r in rows] if entry>=0 else None
        z=statistics.NormalDist().inv_cdf((1+level)/2); prob=1; greenwood=0; curve=[]; median=None
        for time in sorted(set(r[0] for r in rows)):
            risk=sum(r[0]>=time and (starts is None or starts[i]<time) for i,r in enumerate(rows)); events=sum(r[0]==time and r[1]==1 for r in rows); cens=sum(r[0]==time and r[1]==0 for r in rows)
            prob *= 1-events/risk
            if events and risk>events: greenwood+=events/(risk*(risk-events))
            lo=hi=prob
            if 0<prob<1:
                center=math.log(-math.log(prob)); se=math.sqrt(greenwood)/abs(math.log(prob)); lo=math.exp(-math.exp(center+z*se)); hi=math.exp(-math.exp(center-z*se))
            curve.append([time,risk,events,cens,prob,lo,hi])
            if prob<=.5 and median is None: median=time
        engine.note += ' Kaplan–Meier: events precede censoring at tied times; Greenwood log-log CI. Columns: time, at risk, events, censored, survival, lower, upper.'
        return {'survival table':curve,'median survival':median,'confidence level':level}
    if name=='logrank':
        x,y=table(a[0],2,2),table(a[1],2,2); survival(x); survival(y)
        require(len(x[0])==len(y[0])==2,'Rows are [time,event]')
        observed=expected=var=0.0
        for time in sorted(set(r[0] for r in x+y if r[1])):
            n1=sum(r[0]>=time for r in x); n2=sum(r[0]>=time for r in y); n=n1+n2
            d1=sum(r[0]==time and r[1] for r in x); d2=sum(r[0]==time and r[1] for r in y); d=d1+d2
            observed+=d1; expected+=d*n1/n
            if n>1: var+=n1*n2*d*(n-d)/(n*n*(n-1))
        require(var>0,'Log-rank needs informative events in both risk sets')
        chi=(observed-expected)**2/var
        return {'chi2':chi,'df':1,'p':float(_chisq_sf(chi,1)),'observed group 1':observed,'expected group 1':expected}
    if name=='cox':
        rows=table(a[0],3,3)
        ties=option(a,1,'efron'); require(ties in ('breslow','efron'),'Cox ties: breslow or efron')
        entry=integer(a[2],-1,19,capacity=True) if len(a)>2 else -1
        check=integer(a[3],0,1) if len(a)>3 else 1
        survival(rows,entry)
        columns=[j for j in range(len(rows[0])) if j not in (0,1) and j!=entry]
        require(columns,'Choose at least one predictor')
        x=[[r[j] for j in columns] for r in rows]; p=len(x[0]); require(sum(r[1] for r in rows)>p,'More events than predictors are required')
        ends=[r[0] for r in rows]; observed=[r[1] for r in rows]; starts=[r[entry] for r in rows] if entry>=0 else None
        design,_,_,scales=standardized_design([[1]+r for r in x]); x=[r[1:] for r in design]
        times=sorted(set(t for t,e in zip(ends,observed) if e))
        def risk_at(time): return [i for i in range(len(rows)) if ends[i]>=time and (starts is None or starts[i]<time)]
        def accumulate(beta,transform=None):
            """Partial log-likelihood derivatives at beta.

            Returns the negative partial log likelihood, the score and the
            observed information. A time transform adds the Grambsch–Therneau
            blocks of a time-varying coefficient: the weighted score residual
            vector and the g- and g^2-weighted information terms.
            """
            z=[dot(row,beta) for row in x]
            value=0.0; score=[0.0]*p; information=[[0.0]*p for _ in range(p)]
            if transform is not None: residual=[0.0]*p; cross=[[0.0]*p for _ in range(p)]; square=[[0.0]*p for _ in range(p)]
            for time in times:
                risk=risk_at(time); events=[i for i in risk if ends[i]==time and observed[i]]; top=max(z[i] for i in risk)
                members=[(math.exp(z[i]-top),i) for i in risk]; selected=[(math.exp(z[i]-top),i) for i in events]
                count=len(selected); factor=count if ties=='breslow' else 1
                s0=math.fsum(w for w,_ in members); s1=[math.fsum(w*x[i][j] for w,i in members) for j in range(p)]; s2=[[math.fsum(w*x[i][j]*x[i][k] for w,i in members) for k in range(p)] for j in range(p)]
                e0=math.fsum(w for w,_ in selected); e1=[math.fsum(w*x[i][j] for w,i in selected) for j in range(p)]; e2=[[math.fsum(w*x[i][j]*x[i][k] for w,i in selected) for k in range(p)] for j in range(p)]
                value+=count*top-math.fsum(z[i] for i in events)
                at=[math.fsum(x[i][j] for i in events) for j in range(p)]; bt=[[0.0]*p for _ in range(p)]
                for draw in range(1 if ties=='breslow' else count):
                    share=0.0 if ties=='breslow' else draw/count
                    denominator=s0-share*e0; numerator=[s1[j]-share*e1[j] for j in range(p)]
                    value+=factor*math.log(denominator)
                    for j in range(p):
                        at[j]-=factor*numerator[j]/denominator
                        for k in range(p): bt[j][k]+=factor*((s2[j][k]-share*e2[j][k])/denominator-numerator[j]*numerator[k]/denominator**2)
                for j in range(p):
                    score[j]-=at[j]
                    for k in range(p): information[j][k]+=bt[j][k]
                if transform is not None:
                    g=transform[time]
                    for j in range(p):
                        residual[j]+=g*at[j]
                        for k in range(p): cross[j][k]+=g*bt[j][k]; square[j][k]+=g*g*bt[j][k]
            if transform is not None: return value,score,information,residual,cross,square
            return value,score,information
        b,cov,ll,it=newton([0.0]*p,lambda beta:accumulate(beta))
        engine.note += ' Cox proportional hazards, '+('Efron' if ties=='efron' else 'Breslow')+' ties; no intercept'+(', left truncation at the entry column' if entry>=0 else '')+'. Analytic score and observed information (Newton-Raphson); exp(coef) is hazard ratio.'
        ph={}
        if check:
            try:
                ranks={time:rank+1 for rank,time in enumerate(times)}
                counts={}
                for t,e in zip(ends,observed):
                    if e: counts[t]=counts.get(t,0)+1
                centre=sum(ranks[t]*counts[t] for t in times)/sum(counts.values())
                _,_,_,residual,cross,square=accumulate(b,{time:ranks[time]-centre for time in times})
                schur=mp.matrix(square)-mp.matrix(cross)*cov*mp.matrix(cross).T
                require(min(float(v) for v in mp.eigsy(schur,eigvals_only=True))>1e-12,'Proportional-hazards check is not estimable for this data')
                statistic=mp.matrix(residual); chi=max(0.0,float((statistic.T*inverse(schur)*statistic)[0]))
                ph={'PH test chi2':chi,'PH test df':p,'PH test p':float(_chisq_sf(chi,p)),
                    'PH test per covariate':[{'term':'x'+str(j+1),'chi2':max(0.0,float(residual[j])**2/float(schur[j,j])),'df':1,'p':float(_chisq_sf(max(0.0,float(residual[j])**2/float(schur[j,j])),1))} for j in range(p)]}
                engine.note += ' Proportional-hazards check: Grambsch–Therneau scaled-Schoenfeld score test on event-time ranks.'
            except (MathError,OverflowError,ValueError) as exc: ph={'PH test':'unavailable: '+str(exc)}
        b=[v/scale for v,scale in zip(b,scales)]; cov=mp.matrix([[cov[i,j]/(scales[i]*scales[j]) for j in range(p)] for i in range(p)])
        result={'coefficients':inference(b,cov,['x'+str(i+1) for i in range(p)],True),'partial log likelihood':-ll,'iterations':it}
        result.update(ph)
        return result
    raise MathError('Unknown advanced analysis')


def survival_analysis(engine,a):
    """Rows: time, event, optional entry time, numeric group ID, covariates.

    The UI encodes labels in first-occurrence order. Cox includes treatment
    dummies (first group as reference), plus the selected covariates. Left
    truncation, tied-event handling and the proportional-hazards check follow
    the Cox options. Subtest failures preserve valid KM curves.
    """
    rows=table(a[0],2,3); fit=integer(a[1],0,1) if len(a)>1 else 0
    ties=option(a,2,'efron'); require(ties in ('breslow','efron'),'Cox ties: breslow or efron')
    entry=integer(a[3],-1,19,capacity=True) if len(a)>3 else -1; require(entry in (-1,2),'Entry time follows the event column')
    check=integer(a[4],0,1) if len(a)>4 else 1
    survival(rows,entry)
    base=3 if entry>=0 else 2
    ids=list(dict.fromkeys(r[base] for r in rows))
    require(within_limit(len(ids),20),'Survival analysis limit: 20 groups')
    def truncated(r,time): return entry<0 or r[entry]<time
    groups=[]
    for label in ids:
        sample=[r[:2]+([r[entry]] if entry>=0 else []) for r in rows if r[base]==label]
        km=calculate(engine,'kaplanmeier',[sample,.95]+([entry] if entry>=0 else []))
        groups.append({'id':label,'n':len(sample),'events':int(sum(r[1] for r in sample)),
                       'median':km['median survival'],'curve':km['survival table']})
    report={'groups':groups,'level':.95,'logrank':None,'cox':None,'ties':ties,'truncation':entry>=0,'ph':bool(check and fit)}
    if len(ids)>1:
        try:
            k=len(ids); score=[0.0]*k; covariance=[[0.0]*k for _ in ids]
            for time in sorted(set(r[0] for r in rows if r[1])):
                risk=[sum(r[0]>=time and truncated(r,time) and r[base]==label for r in rows) for label in ids]
                events=[sum(r[0]==time and r[1] and r[base]==label for r in rows) for label in ids]
                n=sum(risk); d=sum(events)
                for i in range(k):
                    score[i]+=events[i]-d*risk[i]/n
                    for j in range(k):
                        if n>1: covariance[i][j]+=d*(n-d)/(n-1)*((risk[i]/n if i==j else 0)-risk[i]*risk[j]/n**2)
            reduced=mp.matrix([r[:-1] for r in covariance[:-1]]); v=mp.matrix(score[:-1])
            require(min(float(x) for x in mp.eigsy(reduced,eigvals_only=True))>1e-12,'Log-rank needs informative events in all risk sets')
            chi=max(0,float((v.T*inverse(reduced)*v)[0]))
            report['logrank']={'chi2':chi,'df':k-1,'p':float(_chisq_sf(chi,k-1))}
        except MathError as exc: report['logrank']={'error':str(exc)}
    if fit:
        try:
            coxrows=[r[:2]+([r[entry]] if entry>=0 else [])+[float(r[base]==label) for label in ids[1:]]+list(r[base+1:]) for r in rows]
            require(len(coxrows[0])>2,'Choose Cox predictors or at least two groups')
            report['cox']=calculate(engine,'cox',[coxrows,ties,2 if entry>=0 else -1,check])
            for index,term in enumerate(report['cox']['coefficients']):
                term['term']='group:'+str(index+1) if index<len(ids)-1 else 'predictor:'+str(index-len(ids)+1)
                term['HR']=term['exp(coef)']
                term['HR CI95']=[math.exp(v) if v<709 else math.inf for v in term['CI95']] if term['CI95'] else None
        except MathError as exc: report['cox']={'error':str(exc)}
    # Raw JSON metadata is separate from the compact reusable calculator result.
    def json_numbers(value):
        if isinstance(value,dict): return {k:json_numbers(v) for k,v in value.items()}
        if isinstance(value,list): return [json_numbers(v) for v in value]
        if isinstance(value,float) and not math.isfinite(value): return None
        return value
    engine.survival_report=json_numbers(report)
    engine.note += ' Survival analysis: pointwise Greenwood log-log 95% CI; log-rank'+(' with left-truncated risk sets' if entry>=0 else '')+'; Cox '+('Efron' if ties=='efron' else 'Breslow')+' ties, first group as reference'+(', proportional-hazards check by scaled-Schoenfeld score test' if check and fit else '')+'.'
    return {'groups':len(ids),'observations':len(rows),'events':int(sum(r[1] for r in rows)),
            'log-rank':report['logrank'] or 'one group','Cox':report['cox'] or 'off'}
