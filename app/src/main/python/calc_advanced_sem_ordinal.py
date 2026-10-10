"""Ordinal probit SEM: two-stage polychorics, DWLS and scaled-shifted WLSMV.

Only Python/mpmath are used on Android and in WASM. Gamma is the full
casewise influence covariance of marginal thresholds and two-step polychorics.
The test uses T3 = T / c + shift, preserving the model degrees of freedom.
References: https://lavaan.ugent.be/tutorial/cat.html and
https://github.com/yrosseel/lavaan/blob/master/R/lav_test_satorra_bentler.R
"""
import math
from collections import Counter
import mpmath as mp
from calc_shared import require
from calc_advanced_common import table, vector, integer, option, inference
from calc_advanced_optimize import minimize
from calc_statistics import _chisq_sf


def normal(x):
    return .5*math.erfc(-x/math.sqrt(2))


def density(x):
    return math.exp(-x*x/2)/math.sqrt(2*math.pi)


def quadrature():
    nodes=[]
    for i in range(1,33):
        z=math.cos(math.pi*(i-.25)/32.5)
        for _ in range(20):
            p0,p1=1.,z
            for j in range(2,33): p0,p1=p1,((2*j-1)*z*p1-(j-1)*p0)/j
            derivative=32*(z*p1-p0)/(z*z-1); updated=z-p1/derivative
            if abs(updated-z)<1e-15: break
            z=updated
        nodes.append((z,2/((1-z*z)*derivative**2)))
    return nodes


QUADRATURE=quadrature()


def bivariate(a,b,rho):
    if a==-math.inf or b==-math.inf: return 0.
    if a==math.inf: return normal(b)
    if b==math.inf: return normal(a)
    angle=math.asin(rho)
    integral=math.fsum(weight*math.exp(-(a*a-2*a*b*math.sin(t)+b*b)/(2*math.cos(t)**2))
                       for node,weight in QUADRATURE for t in [angle*(node+1)/2])
    return normal(a)*normal(b)+angle*integral/(4*math.pi)


def bivariate_density(a,b,rho):
    if not math.isfinite(a) or not math.isfinite(b): return 0.
    return math.exp(-(a*a-2*rho*a*b+b*b)/(2*(1-rho*rho)))/(2*math.pi*math.sqrt(1-rho*rho))


def cells(left,right,rho):
    """Cell probability, rho derivative and all threshold derivatives."""
    a=[-math.inf]+left+[math.inf]; b=[-math.inf]+right+[math.inf]
    corners=[[bivariate(x,y,rho) for y in b] for x in a]
    derivatives=[[bivariate_density(x,y,rho) for y in b] for x in a]
    out={}; root=math.sqrt(1-rho*rho)
    for i in range(len(a)-1):
        for j in range(len(b)-1):
            probability=corners[i+1][j+1]-corners[i][j+1]-corners[i+1][j]+corners[i][j]
            dr=derivatives[i+1][j+1]-derivatives[i][j+1]-derivatives[i+1][j]+derivatives[i][j]
            dt=[0.]*(len(left)+len(right))
            for index,sign in ((i-1,-1),(i,1)):
                if 0<=index<len(left):
                    x=left[index]; dt[index]=sign*density(x)*(normal((b[j+1]-rho*x)/root)-normal((b[j]-rho*x)/root))
            for index,sign in ((j-1,-1),(j,1)):
                if 0<=index<len(right):
                    y=right[index]; dt[len(left)+index]=sign*density(y)*(normal((a[i+1]-rho*y)/root)-normal((a[i]-rho*y)/root))
            out[i,j]=(max(probability,1e-15),dr,dt)
    return out


def statistics(rows):
    n=len(rows); p=len(rows[0]); categories=[sorted(set(c)) for c in zip(*rows)]
    require(all(len(c)>=2 for c in categories),'Each ordinal indicator needs at least two observed categories')
    codes=[{v:i for i,v in enumerate(c)} for c in categories]
    encoded=[[codes[i][v] for i,v in enumerate(row)] for row in rows]
    thresholds=[]; positions=[]; values=[]; influences=[]
    for i,category in enumerate(categories):
        marginal=Counter(row[i] for row in encoded); at=[]; cuts=[]; cumulative=0
        for j in range(len(category)-1):
            cumulative+=marginal[j]; probability=cumulative/n
            cut=float(mp.sqrt(2)*mp.erfinv(2*probability-1)); at.append(len(values)); cuts.append(cut); values.append(cut)
            influences.append([(probability-(row[i]<=j))/density(cut) for row in encoded])
        thresholds.append(cuts); positions.append(at)
    pairs=[]; corr=mp.eye(p)
    for i in range(p):
        for j in range(i):
            counts=Counter((row[i],row[j]) for row in encoded)
            def score(rho):
                probabilities=cells(thresholds[i],thresholds[j],rho)
                return math.fsum(count*probabilities[cell][1]/probabilities[cell][0] for cell,count in counts.items())
            low,high=-.999,.999
            require(score(low)>0 and score(high)<0,'Boundary polychoric correlation; combine sparse categories or revise indicators')
            for _ in range(45):
                rho=(low+high)/2
                if score(rho)>0: low=rho
                else: high=rho
            rho=(low+high)/2; probabilities=cells(thresholds[i],thresholds[j],rho)
            information=math.fsum(dr*dr/prob for prob,dr,_ in probabilities.values())
            cross=[math.fsum(dr*dt[t]/prob for prob,dr,dt in probabilities.values()) for t in range(len(thresholds[i])+len(thresholds[j]))]
            indices=positions[i]+positions[j]
            influence=[(probabilities[row[i],row[j]][1]/probabilities[row[i],row[j]][0]
                        -math.fsum(v*influences[at][r] for v,at in zip(cross,indices)))/information for r,row in enumerate(encoded)]
            values.append(rho); influences.append(influence); pairs.append((i,j)); corr[i,j]=corr[j,i]=rho
    size=len(values); centers=[math.fsum(v)/n for v in influences]
    gamma=mp.matrix([[math.fsum((x-centers[i])*(y-centers[j]) for x,y in zip(influences[i],influences[j]))/n
                      for j in range(size)] for i in range(size)])
    require(all(gamma[i,i]>1e-12 for i in range(size)),'Ordinal sampling information is singular; check category frequencies')
    return dict(n=n,categories=categories,thresholds=thresholds,positions=positions,values=values,pairs=pairs,gamma=gamma,corr=corr)


def adjusted(statistic,u,gamma,df):
    if not df: return 0.,None,None
    ug=u*gamma; trace=float(sum(ug[i,i] for i in range(ug.rows)))
    trace2=float(sum(ug[i,j]*ug[j,i] for i in range(ug.rows) for j in range(ug.rows)))
    require(trace>0 and trace2>0,'WLSMV correction is singular; increase the sample or simplify the model')
    scale=math.sqrt(trace2/df); shift=df-trace/scale
    return max(0.,statistic/scale+shift),scale,shift


def calculate(engine,name,a):
    offset=3 if name=='sem' else 2
    require(option(a,offset+1,'complete')=='complete','WLSMV requires complete ordinal rows; FIML is continuous ML only')
    rows=table(a[0],5,3); p=len(rows[0])
    assignment=[integer(v,1,p) for v in vector(a[1],p)] if len(a)>1 else [1]*p
    require(len(assignment)==p,'Specify one primary factor per indicator')
    k=max(assignment); require(set(assignment)==set(range(1,k+1)),'Factor IDs must be consecutive from 1')
    groups=[[i for i,f in enumerate(assignment) if f==j+1] for j in range(k)]
    require(all(len(g)>=3 for g in groups),'Each factor needs at least three primary indicators')
    markers=[g[0] for g in groups]; cross=[]; paths=[]
    if len(a)>offset:
        require(isinstance(a[offset],(list,tuple)),'Use [indicator,factor] cross-loadings')
        for pair in a[offset]:
            require(isinstance(pair,(list,tuple)) and len(pair)==2,'Use [indicator,factor] cross-loadings')
            i,j=integer(pair[0],1,p)-1,integer(pair[1],1,k)-1
            require(j!=assignment[i]-1,'Cross-loadings must be additional factors'); cross.append((i,j))
    from calc_advanced_sem_summary import measurement_markers
    markers=measurement_markers(groups,cross)
    if name=='sem' and len(a)>2:
        require(isinstance(a[2],(list,tuple)),'Use [source,target] latent paths')
        for pair in a[2]:
            require(isinstance(pair,(list,tuple)) and len(pair)==2,'Use [source,target] latent paths')
            source,target=[integer(v,1,k)-1 for v in pair]; require(source!=target,'Self paths are not supported'); paths.append((source,target))
    require(len(set(cross))==len(cross) and len(set(paths))==len(paths),'Paths and cross-loadings must be distinct')
    remaining=set(range(k))
    while remaining:
        ready={i for i in remaining if all(source not in remaining for source,target in paths if target==i)}
        require(ready,'SEM requires acyclic directed paths'); remaining-=ready
    ids=vector(a[offset+2],len(rows)) if len(a)>offset+2 and a[offset+2] else [1.]*len(rows)
    require(len(ids)==len(rows),'Group IDs must align with every data row')
    labels=sorted(set(ids)); invariance=option(a,offset+3,'configural')
    require(invariance in ('configural','metric','scalar','strict'),'Choose configural, metric, scalar or strict invariance')
    scalar=invariance in ('scalar','strict'); prepared=[]; parameters=[]; sharing={}
    exogenous=set(range(k))-{target for _,target in paths}
    for group,label in enumerate(labels):
        g=statistics([r for r,ident in zip(rows,ids) if ident==label]); g['local']=[]
        require(g['n']>p,'Each group needs more rows than indicators')
        if scalar and group:
            require(g['categories']==prepared[0]['categories'],'Scalar/strict WLSMV requires the same observed categories in every group')
            require(all(len(c)>=3 for c in g['categories']),'Scalar/strict multi-group WLSMV needs at least three categories per indicator to identify response scales')
        def add(kind,i,j,value,shared=False):
            key=(kind,i,j) if shared else (group,kind,i,j)
            if key not in sharing: sharing[key]=len(parameters); parameters.append(value)
            g['local'].append((kind,i,j,sharing[key]))
        for i,f in enumerate(assignment):
            if i!=markers[f-1]: add('loading',i,f-1,1. if g['corr'][i,markers[f-1]]>=0 else -1.,invariance!='configural')
        for i,j in cross: add('loading',i,j,.1,invariance!='configural')
        for i in range(k): add('diagonal',i,i,0.)
        for i in range(k):
            for j in range(i):
                if i in exogenous and j in exogenous: add('covariance',i,j,0.)
        for source,target in paths: add('path',target,source,.1)
        # Theta identification: response residual variances=1 in reference group.
        if invariance=='scalar' and group:
            for i in range(p): add('error',i,i,0.)
        if scalar and group:
            for i in range(k): add('latentmean',i,i,0.)
        for i,cuts in enumerate(g['thresholds']):
            for j,value in enumerate(cuts): add('threshold',i,j,value*math.sqrt(2),scalar)
        prepared.append(g)
    n=sum(g['n'] for g in prepared); size=sum(len(g['values']) for g in prepared); df=size-len(parameters)
    require(df>=0,'Model has negative degrees of freedom; remove free parameters')
    w=mp.zeros(size); gamma=mp.zeros(size); sample=[]; at=0
    for g in prepared:
        g['at']=at; fraction=g['n']/n; m=len(g['values']); sample+=g['values']
        for i in range(m):
            w[at+i,at+i]=fraction/g['gamma'][i,i]
            for j in range(m): gamma[at+i,at+j]=g['gamma'][i,j]/fraction
        at+=m
    sample=mp.matrix(sample)
    def model(x,g,derivatives=False):
        load=mp.zeros(p,k); factor=mp.zeros(k); path=mp.zeros(k); errors=[1.]*p; means=mp.zeros(k,1)
        thresholds=[cuts[:] for cuts in g['thresholds']]
        for j,i in enumerate(markers): load[i,j]=1.
        for kind,i,j,index in g['local']:
            value=x[index]
            if kind=='loading': load[i,j]=value
            elif kind=='diagonal': factor[i,j]=math.exp(value)
            elif kind=='covariance': factor[i,j]=value
            elif kind=='path': path[i,j]=value
            elif kind=='error': errors[i]=math.exp(value)
            elif kind=='latentmean': means[i]=value
            else: thresholds[i][j]=value
        propagation=(mp.eye(k)-path)**-1; psi=factor*factor.T; latent=propagation*psi*propagation.T
        sigma=load*latent*load.T+mp.diag(errors); mu=load*means; scales=[math.sqrt(float(sigma[i,i])) for i in range(p)]
        require(all(all(right>left for left,right in zip(cuts,cuts[1:])) for cuts in thresholds),'Thresholds must remain ordered')
        implied=mp.matrix([(cut-float(mu[i]))/scales[i] for i,cuts in enumerate(thresholds) for cut in cuts]
                          +[float(sigma[i,j])/(scales[i]*scales[j]) for i,j in g['pairs']])
        if not derivatives: return implied,load,latent,path,errors,means,thresholds,sigma
        jac=mp.zeros(len(implied),len(x)); t=load*propagation; threshold_count=sum(map(len,thresholds))
        for kind,i,j,index in g['local']:
            ds=mp.zeros(p); dm=mp.zeros(p,1)
            if kind=='loading':
                dl=mp.zeros(p,k); dl[i,j]=1.; part=dl*latent*load.T; ds=part+part.T; dm[i]=means[j]
            elif kind=='error': ds[i,i]=errors[i]
            elif kind in ('diagonal','covariance'):
                dc=mp.zeros(k); dc[i,j]=factor[i,j] if kind=='diagonal' else 1.; ds=t*(dc*factor.T+factor*dc.T)*t.T
            elif kind=='path':
                db=mp.zeros(k); db[i,j]=1.; dt=t*db*propagation; part=dt*psi*t.T; ds=part+part.T
            elif kind=='latentmean': dm=load[:,i]
            for feature,positions in enumerate(g['positions']):
                for cut,position in enumerate(positions):
                    jac[position,index]+=((1. if kind=='threshold' and i==feature and j==cut else 0.)-dm[feature])/scales[feature]-implied[position]*ds[feature,feature]/(2*sigma[feature,feature])
            for pair,(left,right) in enumerate(g['pairs']):
                position=threshold_count+pair
                jac[position,index]+=ds[left,right]/(scales[left]*scales[right])-implied[position]*(ds[left,left]/sigma[left,left]+ds[right,right]/sigma[right,right])/2
        return implied,jac
    def moments(x):
        implied=mp.zeros(size,1); jac=mp.zeros(size,len(x))
        for g in prepared:
            values,deriv=model(x,g,True)
            for i in range(len(values)):
                implied[g['at']+i]=values[i]
                for j in range(len(x)): jac[g['at']+i,j]=deriv[i,j]
        return implied,jac
    def objective(x):
        implied,jac=moments(x); residual=implied-sample
        return float((residual.T*w*residual)[0])/2,list(map(float,jac.T*w*residual))
    estimates,loss,iterations=minimize(parameters,objective,tolerance=1e-7,maximum=1500)
    implied,jac=moments(estimates); bread=jac.T*w*jac
    scales=[math.sqrt(float(bread[i,i])) if bread[i,i]>0 else 0. for i in range(bread.rows)]
    require(all(scales),'Ordinal model is not identifiable')
    normalized=mp.matrix([[bread[i,j]/(scales[i]*scales[j]) for j in range(bread.cols)] for i in range(bread.rows)])
    require(min(mp.eigsy(normalized,eigvals_only=True))>1e-7,'Ordinal model is not identifiable; revise factors or category coverage')
    inv=bread**-1; cov=inv*jac.T*w*gamma*w*jac*inv/n
    u=w-w*jac*inv*jac.T*w; raw=2*n*loss; statistic,scale,shift=adjusted(raw,u,gamma,df)
    # Independence reference: thresholds free and all indicator correlations zero.
    baseu=mp.zeros(size); baseresidual=mp.zeros(size,1); basedf=0
    for g in prepared:
        count=sum(map(len,g['thresholds']))
        for j in range(count,len(g['values'])):
            index=g['at']+j; baseu[index,index]=w[index,index]; baseresidual[index]=sample[index]; basedf+=1
    baseraw=n*float((baseresidual.T*w*baseresidual)[0]); base,_,_=adjusted(baseraw,baseu,gamma,basedf)
    result={'n':n,'Estimator':'WLSMV (ordinal probit, DWLS; scaled-shifted T3)','df':df,'χ²':statistic,
            'p':float(_chisq_sf(statistic,df)) if df else None,'Unadjusted DWLS χ²':raw,'Scaling factor':scale,'Shift parameter':shift,
            'CFI':1-max(statistic-df,0)/max(statistic-df,base-basedf,1e-15),
            'TLI':(base/basedf-statistic/df)/(base/basedf-1) if df and abs(base/basedf-1)>1e-12 else None,
            'RMSEA':math.sqrt(max(statistic-df,0)*len(labels)/(df*(n-len(labels)))) if df else None,'Iterations':iterations,
            'Fit index convention':'Scaled-shifted model and independence tests; N−G RMSEA denominator with multiplier G.',
            'Assumptions':'Complete ordinal numeric category codes ordered numerically; underlying bivariate-normal responses. Two-stage marginal thresholds/polychoric ML; full casewise influence covariance for sandwich Wald SEs and mean/variance-adjusted T3. Theta parameterization: residual variances fixed at 1 in each configural/metric group and the reference scalar group. Scalar shares loadings/response thresholds and frees other-group latent means and response residual variances; strict fixes residual variances at 1 in all groups. Multi-group scalar/strict requires at least three identical observed categories per indicator. The first pure indicator per factor is the marker with loading fixed at 1; acyclic latent paths and independent response errors. Adjusted χ² values cannot be subtracted for a nested-model difference test.'}
    locals=[]; discrepancy=0.
    for g in prepared:
        values,load,latent,path,errors,means,thresholds,sigma=model(estimates,g)
        require(all(errors[i]/float(sigma[i,i])>1e-6 for i in range(p)),'Heywood / boundary response residual variance; revise the ordinal factor model')
        loading_rows=[]; path_rows=[]
        for kind,i,j,index in g['local']:
            if kind in ('loading','path'):
                row=inference([estimates[index]],[[cov[index,index]]],['feature:'+str(i+1) if kind=='loading' else str(j+1)+' → '+str(i+1)])[0]
                if kind=='loading': row.update({'Factor':j+1,'Standardized loading':float(load[i,j]*mp.sqrt(latent[j,j]/sigma[i,i]))}); loading_rows.append(row)
                else: row['Standardized path']=float(path[i,j]*mp.sqrt(latent[j,j]/latent[i,i])); path_rows.append(row)
        for j,i in enumerate(markers): loading_rows.append({'term':'feature:'+str(i+1),'Factor':j+1,'estimate':1.,'Fixed':1,'Standardized loading':float(mp.sqrt(latent[j,j]/sigma[i,i]))})
        loading_rows.sort(key=lambda r:(int(r['term'].split(':')[1]),r['Factor']))
        local={'n':g['n'],'Loadings':loading_rows,'Structural paths':path_rows,
               'Thresholds':[dict(inference([estimates[index]],[[cov[index,index]]],['feature:'+str(i+1)])[0],Threshold=j+1,**{'Category below':g['categories'][i][j]}) for kind,i,j,index in g['local'] if kind=='threshold'],
               'Residual variances':[{'term':'feature:'+str(i+1),'Variance':errors[i]} for i in range(p)],
               'Latent means':[{'Factor':i+1,'Mean':float(means[i])} for i in range(k)],
               'Polychoric correlations':[[float(v) for v in g['corr'][i,:]] for i in range(p)],
               'Implied covariance':[[float(v) for v in sigma[i,:]] for i in range(p)],
               'Latent covariance':[[float(v) for v in latent[i,:]] for i in range(k)]}
        count=sum(map(len,g['thresholds']))
        discrepancy+=g['n']/n*sum(float(values[count+j]-g['values'][count+j])**2 for j in range(len(g['pairs'])))
        from calc_advanced_sem_summary import augment
        def summary_model(x):
            values,load,latent,path,errors,means,thresholds,sigma=model(x,g)
            return sigma,load,latent,path
        augment(local,estimates,cov,summary_model,g['local'],paths)
        locals.append(local)
    result['SRMR']=math.sqrt(discrepancy/(p*(p+1)/2))
    if len(labels)==1: result.update(locals[0])
    else:
        result.update({'Groups':len(labels),'Invariance':invariance})
        for key in ('Loadings','Structural paths','Thresholds','Residual variances','Latent means','Latent R²','Exogenous correlations','Indicator R²'):
            result[key]=[dict(row,Group='group:'+format(label,'.15g')) for label,local in zip(labels,locals) for row in local[key]]
        result['Group summary']=[{'Group':'group:'+format(label,'.15g'),'n':local['n']} for label,local in zip(labels,locals)]
        for index,local in enumerate(locals):
            for key in ('Polychoric correlations','Implied covariance','Latent covariance'): result[key+' group '+str(index+1)]=local[key]
    return result
