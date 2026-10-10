"""Complete-data CFA/SEM and routing for extended model options."""
import math
import mpmath as mp
from calc_shared import require
from calc_advanced_common import table, vector, integer, inference, option
from calc_advanced_survey import correlation
from calc_advanced_multivariate import positive
from calc_advanced_optimize import minimize, information
from calc_statistics import _chisq_sf


def calculate(engine,name,a):
    offset=3 if name=='sem' else 2
    estimator=option(a,offset+4,'ml')
    require(estimator in ('ml','wlsmv'),'Choose ml or wlsmv estimation')
    if estimator=='wlsmv':
        from calc_advanced_sem_ordinal import calculate as ordinal
        return ordinal(engine,name,a)
    if len(a)>offset:
        from calc_advanced_sem_extended import calculate as extended
        return extended(engine,name,a)
    rows=table(a[0],5,3); n=len(rows); p=len(rows[0])
    assignments=[integer(v,1,p) for v in vector(a[1],p)] if len(a)>1 else [1]*p
    require(len(assignments)==p,'Specify one factor ID per selected indicator')
    k=max(assignments); require(set(assignments)==set(range(1,k+1)),'Factor IDs must be consecutive from 1')
    require(all(assignments.count(i)>=3 for i in range(1,k+1)),'Each factor needs at least three indicators')
    groups=[[j for j,f in enumerate(assignments) if f==i+1] for i in range(k)]; markers=[group[0] for group in groups]
    paths=[]
    if name=='sem' and len(a)>2:
        require(isinstance(a[2],(list,tuple)),'Enter [source factor,target factor] paths')
        for row in a[2]:
            pair=vector(row,2); require(len(pair)==2,'Each path requires source and target factor IDs')
            source,target=[integer(v,1,k)-1 for v in pair]; require(source!=target,'Self paths are not supported'); paths.append((source,target))
    require(len(set(paths))==len(paths),'Paths must be distinct')
    ordered=[]; remaining=set(range(k))
    while remaining:
        ready=sorted(i for i in remaining if all(source not in remaining for source,target in paths if target==i))
        require(ready,'SEM requires acyclic directed paths')
        ordered+=ready; remaining-=set(ready)
    exogenous=set(range(k))-{target for _,target in paths}
    specs=[]; start=[]
    for i,factor in enumerate(assignments):
        if i!=markers[factor-1]: specs.append(('loading',i,factor-1)); start.append(.8)
    for i in range(p): specs.append(('error',i,i)); start.append(math.log(.5))
    for i in range(k): specs.append(('diagonal',i,i)); start.append(math.log(math.sqrt(.5)))
    for i in range(k):
        for j in range(i):
            if i in exogenous and j in exogenous: specs.append(('covariance',i,j)); start.append(0.)
    for source,target in paths: specs.append(('path',target,source)); start.append(.1)
    df=p*(p+1)//2-len(specs)
    require(df>=0 and n>p,'Model needs nonnegative degrees of freedom and more observations than indicators')
    corr,centers,scales=correlation(rows); sample=corr*((n-1)/n); positive(sample); logdet_sample=float(mp.log(mp.det(sample)))
    for index,(kind,i,j) in enumerate(specs):
        if kind=='loading': start[index]=.8 if corr[i,markers[j]]>=0 else -.8
    def model(parameters,derivatives=False):
        load=mp.zeros(p,k); factor=mp.zeros(k); structural=mp.zeros(k); theta=[0.]*p
        for i,f in enumerate(assignments):
            if i==markers[f-1]: load[i,f-1]=1.
        for value,(kind,i,j) in zip(parameters,specs):
            if kind=='loading': load[i,j]=value
            elif kind=='error': theta[i]=math.exp(value)
            elif kind=='diagonal': factor[i,j]=math.exp(value)
            elif kind=='covariance': factor[i,j]=value
            else: structural[i,j]=value
        propagation=(mp.eye(k)-structural)**-1; psi=factor*factor.T; total=propagation*psi*propagation.T
        sigma=load*total*load.T+mp.diag(theta)
        if not derivatives: return sigma,load,total,psi,structural,theta
        d=[]; t=load*propagation
        for kind,i,j in specs:
            if kind=='loading':
                dl=mp.zeros(p,k); dl[i,j]=1.; part=dl*total*load.T; d.append(part+part.T)
            elif kind=='error':
                part=mp.zeros(p); part[i,i]=theta[i]; d.append(part)
            elif kind in ('diagonal','covariance'):
                dc=mp.zeros(k); dc[i,j]=factor[i,j] if kind=='diagonal' else 1.
                d.append(t*(dc*factor.T+factor*dc.T)*t.T)
            else:
                db=mp.zeros(k); db[i,j]=1.; dt=t*db*propagation; part=dt*psi*t.T; d.append(part+part.T)
        return sigma,d
    def objective(parameters):
        sigma,derivatives=model(parameters,True); inv=sigma**-1
        determinant=mp.det(sigma); require(determinant>0,'Implied covariance is not positive definite')
        loss=(float(mp.log(determinant))+float(sum((inv*sample)[i,i] for i in range(p)))-logdet_sample-p)/2
        score=inv-inv*sample*inv
        gradient=[float(sum(score[i,j]*d[j,i] for i in range(p) for j in range(p)))/2 for d in derivatives]
        return loss,gradient
    parameters,loss,iterations=minimize(start,objective,tolerance=2e-7,maximum=800)
    cov=information(parameters,objective,n)
    sigma,load,total,psi,structural,theta=model(parameters)
    require(all(theta[i]/float(sigma[i,i])>1e-6 for i in range(p)),'Heywood / boundary residual variance; revise the factor model')
    loading_rows=[]; path_rows=[]
    for index,(kind,i,j) in enumerate(specs):
        if kind=='loading':
            scale=scales[i]/scales[markers[j]]
            row=inference([parameters[index]*scale],[[cov[index,index]*scale**2]],['feature:'+str(i+1)])[0]
            row.update({'Factor':j+1,'Standardized loading':float(load[i,j]*mp.sqrt(total[j,j]/sigma[i,i]))}); loading_rows.append(row)
        elif kind=='path':
            scale=scales[markers[i]]/scales[markers[j]]
            row=inference([parameters[index]*scale],[[cov[index,index]*scale**2]],[str(j+1)+' → '+str(i+1)])[0]
            row['Standardized path']=float(structural[i,j]*mp.sqrt(total[j,j]/total[i,i])); path_rows.append(row)
    for j,i in enumerate(markers): loading_rows.append({'term':'feature:'+str(i+1),'Factor':j+1,'estimate':1.,'SE':None,'p':None,'CI95':None,'Standardized loading':float(mp.sqrt(total[j,j]/sigma[i,i])),'Fixed':1})
    loading_rows.sort(key=lambda row:int(row['term'].split(':')[1]))
    statistic=max(0.,2*n*loss); base=max(0.,n*(sum(math.log(float(sample[i,i])) for i in range(p))-logdet_sample)); basedf=p*(p-1)/2
    discrepancy=sum(float((sample[i,j]-sigma[i,j])/mp.sqrt(sample[i,i]*sample[j,j]))**2 for i in range(p) for j in range(i,p))
    latent_scales=[scales[i] for i in markers]
    result={'n':n,'Estimator':'Normal-theory covariance ML (N divisor)','df':df,'χ²':statistic,'p':float(_chisq_sf(statistic,df)) if df else None,
            'CFI':1-max(statistic-df,0)/max(statistic-df,base-basedf,1e-15),
            'TLI':(base/basedf-statistic/df)/(base/basedf-1) if df and abs(base/basedf-1)>1e-12 else None,
            'RMSEA':math.sqrt(max(statistic-df,0)/(df*(n-1))) if df else None,'SRMR':math.sqrt(discrepancy/(p*(p+1)/2)),
            'Loadings':loading_rows,'Structural paths':path_rows,'Residual variances':[{'term':'feature:'+str(i+1),'Variance':theta[i]*scales[i]**2} for i in range(p)],
            'Latent covariance':[[float(total[i,j])*latent_scales[i]*latent_scales[j] for j in range(k)] for i in range(k)],
            'Implied covariance':[[float(sigma[i,j])*scales[i]*scales[j] for j in range(p)] for i in range(p)],'Iterations':iterations,
            'Assumptions':'Continuous multivariate-normal indicators and complete rows. One factor per indicator, at least three indicators per factor, first loading fixed at 1. Acyclic latent paths, correlated exogenous factors, independent endogenous disturbances and indicator errors. Observed-information Wald inference.'}
    return result
