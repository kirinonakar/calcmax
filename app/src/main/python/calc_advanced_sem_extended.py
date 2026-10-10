"""Continuous ML: cross-loadings, FIML and measurement invariance.

Configural/metric FIML estimates free indicator means. Metric shares raw
loadings; scalar also shares intercepts and frees non-reference latent means.
Strict also shares residual variances. Pooled units preserve raw equalities.
"""
import math
import mpmath as mp
from calc_shared import require, MathError
from calc_limits import within_limit
from calc_advanced_common import number, integer, vector, inference, option
from calc_advanced_multivariate import positive
from calc_advanced_optimize import minimize, information
from calc_statistics import _chisq_sf


def summaries(rows):
    grouped={}
    for row in rows:
        at=tuple(i for i,v in enumerate(row) if v is not None)
        if at: grouped.setdefault(at,[]).append([row[i] for i in at])
    return [(at,len(values),mp.matrix([math.fsum(c)/len(values) for c in zip(*values)]),
             mp.matrix([[math.fsum(r[i]*r[j] for r in values)/len(values) for j in range(len(at))] for i in range(len(at))])) for at,values in grouped.items()]


def observed_loss(mean,cov,patterns):
    value=0.; p=len(mean); gm=mp.zeros(p,1); gc=mp.zeros(p)
    for at,count,center,second in patterns:
        sub=mp.matrix([[cov[i,j] for j in at] for i in at]); inv=sub**-1
        mu=mp.matrix([mean[i] for i in at]); residual=second-center*mu.T-mu*center.T+mu*mu.T
        value+=count*(float(mp.log(mp.det(sub)))+float(sum((inv*residual)[i,i] for i in range(len(at)))))/2
        score=(inv-inv*residual*inv)*count/2; dmean=inv*(mu-center)*count
        for i,left in enumerate(at):
            gm[left]+=dmean[i]
            for j,right in enumerate(at): gc[left,right]+=score[i,j]
    return value,gm,gc


def saturated(rows,patterns):
    """Observed-data saturated normal ML via conditional-moment EM."""
    p=len(rows[0]); n=len(rows); mean=mp.matrix([0.]*p); cov=mp.eye(p)
    previous=math.inf
    for _ in range(2000):
        first=mp.zeros(p,1); second=mp.zeros(p)
        for at,count,center,moment in patterns:
            missing=[i for i in range(p) if i not in at]
            obs=mp.matrix([[cov[i,j] for j in at] for i in at]); inv=obs**-1
            # E[x | observed] = intercept + transform * observed.
            transform=mp.zeros(p,len(at)); intercept=mean.copy(); conditional=mp.zeros(p)
            for j,i in enumerate(at): transform[i,j]=1.; intercept[i]=0.
            if missing:
                cross=mp.matrix([[cov[i,j] for j in at] for i in missing]); b=cross*inv
                block=mp.matrix([[cov[i,j] for j in missing] for i in missing])-b*cross.T
                obsmean=mp.matrix([mean[i] for i in at])
                for r,i in enumerate(missing):
                    intercept[i]=mean[i]-(b*obsmean)[r]
                    for c,j in enumerate(at): transform[i,c]=b[r,c]
                    for c,j in enumerate(missing): conditional[i,j]=block[r,c]
            projected=transform*center
            first+=count*(intercept+projected)
            second+=count*(conditional+intercept*intercept.T+intercept*projected.T+projected*intercept.T+transform*moment*transform.T)
        updated=first/n; newcov=second/n-updated*updated.T; newcov=(newcov+newcov.T)/2
        positive(newcov); value=observed_loss(updated,newcov,patterns)[0]/n
        change=max(float(mp.norm(newcov-cov)),float(mp.norm(updated-mean)))
        mean,cov=updated,newcov
        if abs(previous-value)<1e-10 and change<1e-6: return value
        previous=value
    raise MathError('Saturated FIML reference did not converge; check missing-data coverage')


def calculate(engine,name,a):
    offset=3 if name=='sem' else 2
    missing=option(a,offset+1,'complete'); require(missing in ('complete','fiml'),'Choose complete or fiml')
    raw=a[0]; require(isinstance(raw,(list,tuple)) and len(raw)>=5,'Enter at least five data rows')
    rows=[]
    for row in raw:
        require(isinstance(row,(list,tuple)),'Enter a rectangular indicator table')
        rows.append([None if str(v) in ('NA','nan','None') else number(v) for v in row])
    p=len(rows[0]); require(p>=3 and all(len(r)==p for r in rows),'Use at least three indicators and equal row widths')
    require(within_limit(len(rows),5000) and within_limit(p,20),'Limit: 5000 rows and 20 indicators')
    require(missing=='fiml' or all(v is not None for r in rows for v in r),'Missing cells require FIML')
    assignments=[integer(v,1,p) for v in vector(a[1],p)] if len(a)>1 else [1]*p
    require(len(assignments)==p,'Specify one primary factor per indicator')
    k=max(assignments); require(set(assignments)==set(range(1,k+1)),'Factor IDs must be consecutive from 1')
    groups=[[i for i,f in enumerate(assignments) if f==j+1] for j in range(k)]
    require(all(len(g)>=3 for g in groups),'Each factor needs at least three primary indicators')
    markers=[g[0] for g in groups]
    cross=[]
    if len(a)>offset:
        require(isinstance(a[offset],(list,tuple)),'Cross-loadings must be [indicator,factor] pairs')
        for pair in a[offset]:
            require(isinstance(pair,(list,tuple)) and len(pair)==2,'Use [indicator,factor] cross-loadings')
            i=integer(pair[0],1,p)-1; j=integer(pair[1],1,k)-1
            require(j!=assignments[i]-1,'Cross-loadings must be additional factors')
            cross.append((i,j))
    require(len(set(cross))==len(cross),'Cross-loadings must be distinct')
    from calc_advanced_sem_summary import measurement_markers
    markers=measurement_markers(groups,cross)
    paths=[]
    if name=='sem' and len(a)>2:
        for pair in a[2]:
            require(isinstance(pair,(list,tuple)) and len(pair)==2,'Use [source,target] latent paths')
            source,target=[integer(v,1,k)-1 for v in pair]
            require(source!=target,'Self paths are not supported'); paths.append((source,target))
    require(len(set(paths))==len(paths),'Paths must be distinct')
    remaining=set(range(k))
    while remaining:
        ready={i for i in remaining if all(source not in remaining for source,target in paths if target==i)}
        require(ready,'SEM requires acyclic directed paths'); remaining-=ready
    group_ids=vector(a[offset+2],len(rows)) if len(a)>offset+2 and a[offset+2] else [1.]*len(rows)
    require(len(group_ids)==len(rows),'Group IDs must align with every data row')
    invariance=option(a,offset+3,'configural')
    require(invariance in ('configural','metric','scalar','strict'),'Choose configural, metric, scalar or strict invariance')
    scalar=invariance in ('scalar','strict')
    use_means=missing=='fiml' or scalar
    # Shared scaling preserves equality of raw loadings across groups.
    labels=sorted(set(group_ids)); scales=[]
    for i in range(p):
        bygroup=[[r[i] for r,g in zip(rows,group_ids) if g==label and r[i] is not None] for label in labels]
        require(all(len(values)>1 for values in bygroup),'Each group and indicator needs observed variation')
        sums=[math.fsum((v-math.fsum(values)/len(values))**2 for v in values) for values in bygroup]
        require(all(value>0 for value in sums),'Each indicator must vary within each group')
        scales.append(math.sqrt(math.fsum(sums)/(sum(map(len,bygroup))-len(labels))))
    prepared=[]; start=[]; sharing={}
    pooled_centers=[math.fsum(r[i] for r in rows if r[i] is not None)/sum(r[i] is not None for r in rows) for i in range(p)]
    exogenous=set(range(k))-{target for _,target in paths}
    for group,label in enumerate(labels):
        selected=[r for r,g in zip(rows,group_ids) if g==label and any(v is not None for v in r)]
        n=len(selected); require(n>p,'Each group needs more observed rows than indicators')
        require(all(sum(r[i] is not None and r[j] is not None for r in selected)>=2 for i in range(p) for j in range(i,p)),
                'Every indicator pair needs at least two joint observations in each group')
        centers=pooled_centers if scalar else [math.fsum(r[i] for r in selected if r[i] is not None)/sum(r[i] is not None for r in selected) for i in range(p)]
        standardized=[[None if v is None else (v-centers[j])/scales[j] for j,v in enumerate(r)] for r in selected]
        patterns=summaries(standardized)
        local=[]
        def add(kind,i,j,value):
            shared=(kind=='loading' and invariance!='configural') or (kind=='mean' and scalar) or (kind=='error' and invariance=='strict')
            key=(kind,i,j) if shared else (group,kind,i,j)
            if key not in sharing:
                sharing[key]=len(start); start.append(value)
            local.append((kind,i,j,sharing[key]))
        for i,factor in enumerate(assignments):
            if i!=markers[factor-1]: add('loading',i,factor-1,.8)
        for i,j in cross: add('loading',i,j,.1)
        for i in range(p): add('error',i,i,math.log(.5))
        for i in range(k): add('diagonal',i,i,math.log(math.sqrt(.5)))
        for i in range(k):
            for j in range(i):
                if i in exogenous and j in exogenous: add('covariance',i,j,0.)
        for source,target in paths: add('path',target,source,.1)
        observed_means=[math.fsum(r[i] for r in standardized if r[i] is not None)/sum(r[i] is not None for r in standardized) for i in range(p)]
        if use_means:
            for i in range(p): add('mean',i,i,observed_means[i])
            if scalar and group:
                for i in range(k): add('latentmean',i,i,0.)
            if missing=='fiml': saturated_loss=saturated(standardized,patterns)
            else:
                from calc_advanced_survey import covariance
                complete_sample=covariance(standardized,divisor=n)[0]; positive(complete_sample)
                saturated_loss=(float(mp.log(mp.det(complete_sample)))+p)/2
            diagonal=mp.diag([math.fsum((r[i]-observed_means[i])**2 for r in standardized if r[i] is not None)/sum(r[i] is not None for r in standardized) for i in range(p)])
            base=2*(observed_loss(mp.matrix(observed_means),diagonal,patterns)[0]-n*saturated_loss)
            sample=None if missing=='fiml' else complete_sample
        else:
            from calc_advanced_survey import covariance
            sample=covariance(standardized,divisor=n)[0]; positive(sample)
            saturated_loss=(float(mp.log(mp.det(sample)))+p)/2
            base=n*(sum(math.log(float(sample[i,i])) for i in range(p))-float(mp.log(mp.det(sample))))
        for kind,i,j,index in local:
            if kind=='loading' and (invariance=='configural' or group==0):
                observed=[(r[i],r[markers[j]]) for r in standardized if r[i] is not None and r[markers[j]] is not None]
                if math.fsum(left*right for left,right in observed)<0: start[index]=-abs(start[index])
        prepared.append(dict(n=n,local=local,patterns=patterns,sample=sample,saturated=saturated_loss,baseline=base,means=observed_means,centers=centers))
    totaln=sum(g['n'] for g in prepared); moments=len(labels)*(p*(p+1)//2+(p if use_means else 0)); df=moments-len(start)
    require(df>=0,'Model has negative degrees of freedom; remove free parameters')
    def model(parameters,g,derivatives=False):
        load=mp.zeros(p,k); factor=mp.zeros(k); structural=mp.zeros(k); theta=[0.]*p; mu=mp.matrix(g['means']); latentmean=mp.zeros(k,1)
        for j,i in enumerate(markers): load[i,j]=1.
        for kind,i,j,index in g['local']:
            value=parameters[index]
            if kind=='loading': load[i,j]=value
            elif kind=='error': theta[i]=math.exp(value)
            elif kind=='diagonal': factor[i,j]=math.exp(value)
            elif kind=='covariance': factor[i,j]=value
            elif kind=='mean': mu[i]=value
            elif kind=='latentmean': latentmean[i]=value
            else: structural[i,j]=value
        propagation=(mp.eye(k)-structural)**-1; psi=factor*factor.T; total=propagation*psi*propagation.T
        sigma=load*total*load.T+mp.diag(theta)
        mu+=load*latentmean
        if not derivatives: return sigma,load,total,structural,theta,mu
        deriv=[]; t=load*propagation
        for kind,i,j,index in g['local']:
            dm=mp.zeros(p,1)
            if kind=='loading':
                dl=mp.zeros(p,k); dl[i,j]=1; part=dl*total*load.T; d=part+part.T
                dm[i]=latentmean[j]
            elif kind=='error': d=mp.zeros(p); d[i,i]=theta[i]
            elif kind in ('diagonal','covariance'):
                dc=mp.zeros(k); dc[i,j]=factor[i,j] if kind=='diagonal' else 1.
                d=t*(dc*factor.T+factor*dc.T)*t.T
            elif kind=='path':
                db=mp.zeros(k); db[i,j]=1.; dt=t*db*propagation; part=dt*psi*t.T; d=part+part.T
            elif kind=='mean': d=None; dm[i]=1.
            else: d=None; dm=load[:,i]
            deriv.append((index,d,dm))
        return sigma,mu,deriv
    def objective(parameters):
        value=0.; gradient=[0.]*len(start)
        for g in prepared:
            sigma,mu,deriv=model(parameters,g,True)
            if use_means: raw,gm,score=observed_loss(mu,sigma,g['patterns'])
            else:
                inv=sigma**-1; sample=g['sample']; det=mp.det(sigma)
                require(det>0,'Implied covariance is not positive definite')
                raw=g['n']*(float(mp.log(det))+float(sum((inv*sample)[i,i] for i in range(p))))/2
                score=(inv-inv*sample*inv)*g['n']/2; gm=mp.zeros(p,1)
            value+=(raw-g['n']*g['saturated'])/totaln
            for index,d,dm in deriv:
                gradient[index]+=float((gm.T*dm)[0]+(sum(score[i,j]*d[j,i] for i in range(p) for j in range(p)) if d is not None else 0))/totaln
        return value,gradient
    parameters,loss,iterations=minimize(start,objective,tolerance=2e-7,maximum=1000)
    cov=information(parameters,objective,totaln)
    results=[]
    for label,g in zip(labels,prepared):
        sigma,load,total,structural,theta,mu=model(parameters,g)
        require(all(theta[i]/float(sigma[i,i])>1e-6 for i in range(p)),'Heywood / boundary residual variance; revise the factor model')
        loading_rows=[]; path_rows=[]
        for kind,i,j,index in g['local']:
            if kind=='loading':
                scale=scales[i]/scales[markers[j]]
                row=inference([parameters[index]*scale],[[cov[index,index]*scale**2]],['feature:'+str(i+1)])[0]
                row.update({'Factor':j+1,'Standardized loading':float(load[i,j]*mp.sqrt(total[j,j]/sigma[i,i]))}); loading_rows.append(row)
            elif kind=='path':
                scale=scales[markers[i]]/scales[markers[j]]
                row=inference([parameters[index]*scale],[[cov[index,index]*scale**2]],[str(j+1)+' → '+str(i+1)])[0]
                row['Standardized path']=float(structural[i,j]*mp.sqrt(total[j,j]/total[i,i])); path_rows.append(row)
        for j,i in enumerate(markers): loading_rows.append({'term':'feature:'+str(i+1),'Factor':j+1,'estimate':1.,'SE':None,'p':None,'CI95':None,'Standardized loading':float(mp.sqrt(total[j,j]/sigma[i,i])),'Fixed':1})
        loading_rows.sort(key=lambda r:(int(r['term'].split(':')[1]),r['Factor']))
        local={'n':g['n'],'Loadings':loading_rows,'Structural paths':path_rows,
               'Residual variances':[{'term':'feature:'+str(i+1),'Variance':theta[i]*scales[i]**2} for i in range(p)],
               'Latent covariance':[[float(total[i,j])*scales[markers[i]]*scales[markers[j]] for j in range(k)] for i in range(k)],
               'Implied covariance':[[float(sigma[i,j])*scales[i]*scales[j] for j in range(p)] for i in range(p)]}
        if use_means: local['Indicator means']=[{'term':'feature:'+str(i+1),'Mean':float(mu[i])*scales[i]+g['centers'][i]} for i in range(p)]
        if scalar:
            local['Indicator intercepts']=[{'term':'feature:'+str(i+1),'Intercept':parameters[index]*scales[i]+g['centers'][i]} for kind,i,j,index in g['local'] if kind=='mean']
            free_means={i:index for kind,i,j,index in g['local'] if kind=='latentmean'}
            local['Latent means']=[dict(Factor=i+1,**inference([parameters[free_means[i]]*scales[markers[i]]],[[cov[free_means[i],free_means[i]]*scales[markers[i]]**2]],[str(i+1)])[0]) if i in free_means else {'Factor':i+1,'estimate':0.,'Fixed':1} for i in range(k)]
        from calc_advanced_sem_summary import augment
        augment(local,parameters,cov,lambda x:model(x,g)[:4],g['local'],paths)
        results.append(local)
    statistic=max(0.,2*totaln*loss); base=max(0.,sum(g['baseline'] for g in prepared)); basedf=len(labels)*p*(p-1)/2
    result={'n':totaln,'Estimator':'Normal-theory observed-data FIML' if missing=='fiml' else 'Normal-theory mean/covariance ML (N divisor)' if scalar else 'Normal-theory covariance ML (N divisor)',
            'df':df,'χ²':statistic,'p':float(_chisq_sf(statistic,df)) if df else None,
            'CFI':1-max(statistic-df,0)/max(statistic-df,base-basedf,1e-15),
            'TLI':(base/basedf-statistic/df)/(base/basedf-1) if df and abs(base/basedf-1)>1e-12 else None,
            'RMSEA':math.sqrt(max(statistic-df,0)*len(labels)/(df*(totaln-len(labels)))) if df else None,'Iterations':iterations,
            'RMSEA convention':'N−G denominator, group multiplier G; uncorrected normal-theory index',
            'Missing patterns':sum(len(g['patterns']) for g in prepared),'Dropped empty rows':len(rows)-totaln,
            'Assumptions':'Continuous multivariate-normal indicators; at least three primary indicators per factor and first pure primary loading fixed at 1. Cross-loadings use selected indicator positions on non-marker indicators. Acyclic latent paths, independent indicator errors and endogenous disturbances. FIML estimates the observed-data likelihood and indicator means under MCAR/MAR. Configural groups estimate separate parameters; metric groups share raw loadings and estimate other parameters separately. Scalar groups also share raw indicator intercepts, with reference latent means fixed at zero and other-group latent means free; strict also shares raw residual variances. Observed-information Wald inference.'}
    if len(labels)==1:
        result.update(results[0])
        sample=prepared[0]['sample']
        if sample is not None:
            sigma=model(parameters,prepared[0])[0]
            discrepancy=sum(float((sample[i,j]-sigma[i,j])/mp.sqrt(sample[i,i]*sample[j,j]))**2 for i in range(p) for j in range(i,p))
            result['SRMR']=math.sqrt(discrepancy/(p*(p+1)/2))
    else:
        result['Groups']=len(labels); result['Invariance']=invariance
        # Flat report tables retain group identity and avoid opaque nested objects.
        for key in ('Loadings','Structural paths','Residual variances','Indicator means','Indicator intercepts','Latent means','Latent R²','Exogenous correlations','Indicator R²'):
            result[key]=[dict(Group='group:'+format(label,'.15g'),**row) for label,local in zip(labels,results) for row in local.get(key,[])]
        result['Group summary']=[{'Group':'group:'+format(label,'.15g'),'n':local['n']} for label,local in zip(labels,results)]
        for index,local in enumerate(results):
            result['Implied covariance group '+str(index+1)]=local['Implied covariance']
            result['Latent covariance group '+str(index+1)]=local['Latent covariance']
    return result
