"""Repeated-measures ANOVA, Gaussian mixed effects and clustered GEE models."""
from calc_limits import within_limit
import math
import mpmath as mp
from calc_shared import MathError, require
from calc_statistics import _f_sf
from calc_advanced_common import (
    dot, inference, integer, inverse, logistic, mean, newton, option, softplus,
    standardized_design, table,
)


def repeated_anova(engine, a):
    """Balanced one- or two-factor within-subjects ANOVA with GG corrections."""
    rows=table(a[0],2,2); n=len(rows); k=len(rows[0])
    factor2=integer(a[1],1,20,capacity=True) if len(a)>1 else 1
    if factor2==1:
        overall=mean(sum(rows,[])); cols=list(zip(*rows))
        total=sum((v-overall)**2 for r in rows for v in r); sscondition=n*sum((mean(c)-overall)**2 for c in cols); sssubject=k*sum((mean(r)-overall)**2 for r in rows); error=total-sscondition-sssubject
        require(error>1e-12, 'Repeated-measures residual variation is required')
        df1=k-1; df2=(n-1)*(k-1); f=(sscondition/df1)/(error/df2)
        # Greenhouse–Geisser epsilon from double-centered covariance.
        cov=[[sum((rows[t][i]-mean(cols[i]))*(rows[t][j]-mean(cols[j])) for t in range(n))/(n-1) for j in range(k)] for i in range(k)]
        cm=[mean(r) for r in cov]; gm=mean(cm); centered=[[cov[i][j]-cm[i]-cm[j]+gm for j in range(k)] for i in range(k)]
        epsilon=min(1,max(1/df1,sum(centered[i][i] for i in range(k))**2/(df1*sum(v*v for r in centered for v in r))))
        engine.note += ' Balanced one-factor repeated measures: rows=subjects, columns=conditions. Includes Greenhouse–Geisser correction.'
        return {'F':f,'df1':df1,'df2':df2,'p':float(_f_sf(f,df1,df2)),'partial eta2':sscondition/(sscondition+error),'GG epsilon':epsilon,'GG p':float(_f_sf(f,df1*epsilon,df2*epsilon))}
    require(k%factor2==0,'Condition count must be divisible by the second-factor levels')
    second=k//factor2; require(second>=2 and factor2>=2,'Two-way repeated measures need two or more levels per factor')
    overall=mean(sum(rows,[]))
    cells=[[mean([rows[s][i*factor2+j] for s in range(n)]) for j in range(factor2)] for i in range(second)]
    first_means=[mean(cells[i]) for i in range(second)]; second_means=[mean([cells[i][j] for i in range(second)]) for j in range(factor2)]
    subject_means=[mean(r) for r in rows]; subject_first=[[mean(rows[s][i*factor2:(i+1)*factor2]) for i in range(second)] for s in range(n)]
    subject_second=[[mean(rows[s][j::factor2]) for j in range(factor2)] for s in range(n)]
    total=sum((v-overall)**2 for r in rows for v in r); subject_ss=k*sum((v-overall)**2 for v in subject_means)
    first_ss=n*factor2*sum((v-overall)**2 for v in first_means); second_ss=n*second*sum((v-overall)**2 for v in second_means)
    interaction_ss=n*sum((cells[i][j]-first_means[i]-second_means[j]+overall)**2 for i in range(second) for j in range(factor2))
    first_error=factor2*sum((subject_first[s][i]-first_means[i]-subject_means[s]+overall)**2 for s in range(n) for i in range(second))
    second_error=second*sum((subject_second[s][j]-second_means[j]-subject_means[s]+overall)**2 for s in range(n) for j in range(factor2))
    within=total-subject_ss; interaction_error=within-first_ss-second_ss-interaction_ss-first_error-second_error
    require(min(first_error,second_error,interaction_error)>1e-12,'Repeated-measures residual variation is required')
    def epsilon(variables,dimension):
        size=len(variables[0]); columns=list(zip(*variables))
        centered=[[sum((variables[t][i]-mean(columns[i]))*(variables[t][j]-mean(columns[j])) for t in range(len(variables)))/(len(variables)-1) for j in range(size)] for i in range(size)]
        rows_mean=[mean(r) for r in centered]; grand=mean(rows_mean)
        starred=[[centered[i][j]-rows_mean[i]-rows_mean[j]+grand for j in range(size)] for i in range(size)]
        require(sum(v*v for r in starred for v in r)>0,'Greenhouse–Geisser correction needs contrast variation')
        return min(1,max(1/dimension,sum(starred[i][i] for i in range(size))**2/(dimension*sum(v*v for r in starred for v in r))))
    def helmert(levels): return [[1/math.sqrt(u*(u+1)) if j<u else -u/math.sqrt(u*(u+1)) if j==u else 0.0 for j in range(levels)] for u in range(1,levels)]
    contrasts_first,contrasts_second=helmert(second),helmert(factor2)
    residual_rows=[[rows[s][i*factor2+j]-subject_first[s][i]-subject_second[s][j]+subject_means[s] for i in range(second) for j in range(factor2)] for s in range(n)]
    interaction_variables=[[sum(contrasts_first[u][i]*contrasts_second[v][j]*row[i*factor2+j] for i in range(second) for j in range(factor2)) for v in range(factor2-1) for u in range(second-1)] for row in residual_rows]
    effects={'A':(first_ss,second-1,first_error,(n-1)*(second-1)),'B':(second_ss,factor2-1,second_error,(n-1)*(factor2-1)),'AB':(interaction_ss,(second-1)*(factor2-1),interaction_error,(n-1)*(second-1)*(factor2-1))}
    adjustments={'A':epsilon(subject_first,second-1),'B':epsilon(subject_second,factor2-1),'AB':epsilon(interaction_variables,(second-1)*(factor2-1))}
    result={'first factor levels':second,'second factor levels':factor2,'subject df':n-1}
    for key,(effect,degree1,error,degree2) in effects.items():
        value=(effect/degree1)/(error/degree2)
        result[key+' F']=value; result[key+' df1']=degree1; result[key+' df2']=degree2; result[key+' p']=float(_f_sf(value,degree1,degree2))
        result[key+' GG epsilon']=adjustments[key]; result[key+' GG p']=float(_f_sf(value,degree1*adjustments[key],degree2*adjustments[key]))
    engine.note += ' Balanced two-way within-subjects ANOVA: rows=subjects, columns list the first factor (slowest) crossed with the second factor. Greenhouse–Geisser corrections per effect.'
    return result


def interaction_pairs(value, width):
    """Normalize [i,j] predictor-pair interactions into distinct ascending pairs."""
    if value is None:
        return []
    require(isinstance(value, (list, tuple)) and len(value) >= 1, 'Interactions must be a list of [i,j] predictor pairs')
    pairs = []
    for item in value:
        require(isinstance(item, (list, tuple)) and len(item) == 2, 'Interactions must be a list of [i,j] predictor pairs')
        first, second = integer(item[0], 1, width), integer(item[1], 1, width)
        pairs.append((min(first, second), max(first, second)))
    require(len(set(pairs)) == len(pairs), 'Interaction pairs must be distinct')
    return pairs


def nelder_mead(objective,start,step,iterations=160,diagnostics=False):
    """Deterministic direct search for small profile objectives with boundaries."""
    size=len(start); simplex=[list(start)]
    for i in range(size):
        point=list(start); point[i]+=step[i]; simplex.append(point)
    values=[objective(point) for point in simplex]
    converged=False
    for iteration in range(iterations):
        order=sorted(range(size+1),key=lambda i:values[i]); simplex=[simplex[i] for i in order]; values=[values[i] for i in order]
        if max(abs(simplex[i][j]-simplex[0][j]) for i in range(1,size+1) for j in range(size))<1e-7:
            converged=True; break
        centroid=[sum(simplex[i][j] for i in range(size))/size for j in range(size)]
        reflected=[2*centroid[j]-simplex[size][j] for j in range(size)]; value=objective(reflected)
        if value<values[0]:
            expanded=[centroid[j]+2*(reflected[j]-centroid[j]) for j in range(size)]; trial=objective(expanded)
            if trial<value: simplex[size],values[size]=expanded,trial
            else: simplex[size],values[size]=reflected,value
        elif value<values[size-1]: simplex[size],values[size]=reflected,value
        else:
            contracted=[centroid[j]+.5*(simplex[size][j]-centroid[j]) for j in range(size)]; trial=objective(contracted)
            if trial<values[size]: simplex[size],values[size]=contracted,trial
            else:
                for i in range(1,size+1):
                    simplex[i]=[(simplex[i][j]+simplex[0][j])/2 for j in range(size)]; values[i]=objective(simplex[i])
    best=min(range(size+1),key=lambda i:values[i])
    result=(simplex[best],values[best])
    return result+(iteration+1,converged) if diagnostics else result


def numeric_rank(values,tolerance=1e-10):
    """Column rank of a numeric design by scaled Gaussian elimination (no symbolic cost)."""
    matrix=[[float(v) for v in row] for row in values]; height=len(matrix); width=len(matrix[0])
    scales=[max(abs(row[j]) for row in matrix) or 1.0 for j in range(width)]
    matrix=[[row[j]/scales[j] for j in range(width)] for row in matrix]
    rank=0; position=0
    for column in range(width):
        if position>=height: break
        pivot=max(range(position,height),key=lambda i:abs(matrix[i][column]))
        if abs(matrix[pivot][column])<=tolerance: continue
        matrix[position],matrix[pivot]=matrix[pivot],matrix[position]
        leading=matrix[position]
        for i in range(position+1,height):
            row=matrix[i]; factor=row[column]/leading[column]
            if factor:
                for j in range(column,width): row[j]-=factor*leading[j]
        rank+=1; position+=1
    return rank


def clustered_design(rows):
    """Intercept design and response for clustered models with a numeric collinearity check."""
    design=[[1.0]+[float(v) for v in row[1:-1]] for row in rows]; response=[float(row[-1]) for row in rows]
    require(len(design)>len(design[0]),'More observations than coefficients are required')
    require(numeric_rank(design)==len(design[0]),'Predictors are collinear')
    return design,response


def small_logdet(values):
    """log|A| of a small positive-definite matrix by elimination with partial pivoting."""
    size=len(values); work=[[float(values[i][j]) for j in range(size)] for i in range(size)]; total=0.0
    for column in range(size):
        pivot=max(range(column,size),key=lambda i:abs(work[i][column]))
        if abs(work[pivot][column])<=1e-300: return None
        if pivot!=column: work[column],work[pivot]=work[pivot],work[column]
        total+=math.log(abs(work[column][column]))
        for i in range(column+1,size):
            factor=work[i][column]/work[column][column]
            for j in range(column+1,size): work[i][j]-=factor*work[column][j]
    return total


def small_inverse(values):
    """Inverse of a small matrix by Gauss-Jordan elimination with partial pivoting."""
    size=len(values)
    work=[[float(values[i][j]) for j in range(size)]+[1.0 if i==j else 0.0 for j in range(size)] for i in range(size)]
    for column in range(size):
        pivot=max(range(column,size),key=lambda i:abs(work[i][column]))
        if abs(work[pivot][column])<=1e-300: return None
        work[column],work[pivot]=work[pivot],work[column]
        divisor=work[column][column]
        work[column]=[v/divisor for v in work[column]]
        for i in range(size):
            if i==column: continue
            factor=work[i][column]
            if factor: work[i]=[v-factor*w for v,w in zip(work[i],work[column])]
    return [[work[i][size+j] for j in range(size)] for i in range(size)]


def gls_solution(information,vector,total,fixed=None):
    """Solve GLS, optionally fixing one coefficient for a likelihood profile."""
    vector=mp.matrix(vector); p=information.rows
    if fixed is None:
        beta=inverse(information)*vector
    else:
        index,value=fixed; free=[j for j in range(p) if j!=index]; beta=mp.zeros(p,1); beta[index]=value
        if free:
            matrix=mp.matrix([[information[i,j] for j in free] for i in free])
            right=mp.matrix([vector[i]-information[i,index]*value for i in free])
            fitted=inverse(matrix)*right
            for i,b in zip(free,fitted): beta[i]=b
    rss=total-float((2*vector.T*beta-beta.T*information*beta)[0])
    return beta,rss


def intercept_fit(x,y,clusters,method,fixed=None):
    """Gaussian random-intercept ML/REML fit with per-cluster algebra (no n x n matrices)."""
    n=len(y); p=len(x[0])
    gram=[[math.fsum(r[i]*r[j] for r in x) for j in range(p)] for i in range(p)]
    right=[math.fsum(r[i]*v for r,v in zip(x,y)) for i in range(p)]
    energy=math.fsum(v*v for v in y)
    pieces=[(len(cluster),[math.fsum(x[i][j] for i in cluster) for j in range(p)],math.fsum(y[i] for i in cluster)) for cluster in clusters]
    def fit(ratio):
        matrix=[row[:] for row in gram]; vector=list(right); total=energy; logdet=0.0
        for size,moment,outcome in pieces:
            factor=ratio/(1+size*ratio); logdet+=math.log1p(size*ratio)
            total-=factor*outcome*outcome
            for i in range(p):
                vector[i]-=factor*moment[i]*outcome
                for j in range(i,p):
                    matrix[i][j]-=factor*moment[i]*moment[j]
                    if i!=j: matrix[j][i]=matrix[i][j]
        information=mp.matrix(matrix); beta,rss=gls_solution(information,vector,total,fixed)
        require(rss>1e-12,'Mixed model requires residual variation')
        if method=='reml':
            extra=small_logdet(matrix); require(extra is not None,'Mixed model information is singular')
            objective=(n-p)*math.log(rss/(n-p))+logdet+extra
        else: objective=n*math.log(rss/n)+logdet
        return objective,beta,information,rss,ratio
    def objective(z): return fit(math.exp(z))[0]
    low,high=-16.0,16.0; golden=(math.sqrt(5)-1)/2
    u=high-golden*(high-low); v=low+golden*(high-low); fu,fv=objective(u),objective(v)
    for _ in range(60):
        if fu<fv: high,v,fv=v,u,fu; u=high-golden*(high-low); fu=objective(u)
        else: low,u,fu=u,v,fv; v=low+golden*(high-low); fv=objective(v)
    chosen=fit(math.exp((low+high)/2)); boundary=fit(0)
    return boundary if boundary[0]<=chosen[0] else chosen


def mixed_intercept(engine,x,y,clusters,ids,method,ci_options=None):
    objective,beta,information,rss,ratio=intercept_fit(x,y,clusters,method)
    sigma=rss/(len(y)-len(x[0])) if method=='reml' else rss/len(y)
    engine.note += ' Gaussian random-intercept mixed model, '+('restricted maximum likelihood (REML)' if method=='reml' else 'maximum likelihood (ML)')+', Wald inference. Rows: subject ID, predictors, response.'
    result={'coefficients':inference(list(map(float,beta)),inverse(information)*sigma,['Intercept']+['x'+str(i) for i in range(1,len(x[0]))]),'residual variance':sigma,'random intercept variance':ratio*sigma,'ICC':ratio/(1+ratio),'subjects':len(ids),'estimation':method.upper(),'singular fit':int(ratio==0)}
    result['subject random effects (BLUP)']=[{'subject':id_,'Intercept':ratio*math.fsum(y[i]-dot(x[i],beta) for i in c)/(1+len(c)*ratio)} for id_,c in zip(ids,clusters)]
    if ratio==0: engine.note += ' Singular fit: random-intercept variance is zero.'
    mixed_diagnostics(engine,result,objective,len(y),len(x[0]),1,True)
    if ci_options:
        from calc_mixed_intervals import intervals
        intervals(engine,result,x,y,clusters,[],method,ci_options)
    return result


def random_effects_fit(x,y,clusters,slopes,method,fixed=None):
    """Gaussian random intercept plus up to three random slopes (ML or REML)."""
    n=len(y); p=len(x[0]); count=1+len(slopes); pairs=[(i,j) for i in range(count) for j in range(i+1)]
    blocks=[]
    for cluster in clusters:
        members=list(cluster); design=[[1.0]+[x[i][s] for s in slopes] for i in members]
        products=[[math.fsum(design[t][a]*design[t][b] for t in range(len(members))) for b in range(count)] for a in range(count)]
        cross=[[math.fsum(design[t][a]*x[i][j] for t,i in enumerate(members)) for j in range(p)] for a in range(count)]
        outcome=[math.fsum(design[t][a]*y[i] for t,i in enumerate(members)) for a in range(count)]
        gram=[[math.fsum(x[i][a]*x[i][b] for i in members) for b in range(p)] for a in range(p)]
        moments=[math.fsum(x[i][a]*y[i] for i in members) for a in range(p)]
        blocks.append((products,cross,outcome,gram,moments,math.fsum(y[i]*y[i] for i in members)))
    def evaluate(parameters):
        if max(abs(v) for v in parameters)>1e4: return None
        lower=mp.zeros(count); position=0
        for i in range(count):
            for j in range(i+1):
                lower[i,j]=parameters[position]; position+=1
        theta=lower*lower.T
        # I + L'Z'ZL and L(I + L'Z'ZL)^-1 L' remain defined at
        # exactly zero variance and rank-deficient random-effect covariance.
        logdet=0.0
        information=[[0.0]*p for _ in range(p)]; vector=[0.0]*p; total=0.0
        for products,cross,outcome,gram,moments,energy in blocks:
            core=mp.eye(count)+lower.T*mp.matrix(products)*lower
            raw=small_inverse(core.tolist())
            if raw is None: return None
            values=(lower*mp.matrix(raw)*lower.T).tolist()
            if any(not math.isfinite(v) for row in values for v in row): return None
            entry=small_logdet(core.tolist())
            if entry is None: return None
            logdet+=entry
            for a in range(p):
                for b in range(a,p):
                    subtotal=gram[a][b]
                    for u in range(count):
                        for v in range(count): subtotal-=cross[u][a]*values[u][v]*cross[v][b]
                    information[a][b]+=subtotal
                    if a!=b: information[b][a]+=subtotal
                adjustment=moments[a]
                for u in range(count):
                    for v in range(count): adjustment-=cross[u][a]*values[u][v]*outcome[v]
                vector[a]+=adjustment
            quadratic=0.0
            for u in range(count):
                for v in range(count): quadratic+=outcome[u]*values[u][v]*outcome[v]
            total+=energy-quadratic
        matrix=mp.matrix(information); beta,rss=gls_solution(matrix,vector,total,fixed)
        if not math.isfinite(rss) or rss<=1e-12: return None
        if method=='reml':
            extra=small_logdet(information)
            if extra is None: return None
            objective=(n-p)*math.log(rss/(n-p))+logdet+extra
        else: objective=n*math.log(rss/n)+logdet
        return objective,beta,matrix,rss,theta
    def objective(parameters):
        result=evaluate(parameters)
        return math.inf if result is None else result[0]
    seed=intercept_fit(x,y,clusters,method,fixed)[4]
    start=math.sqrt(max(0,seed))
    best=None
    for scale in (max(.1,start),.25,0.0):
        parameters=[start if i==0 and j==0 else scale if i==j else 0.0 for i,j in pairs]
        candidate,value,iterations,converged=nelder_mead(objective,parameters,[.2]*len(parameters),700+150*max(0,len(parameters)-3),True)
        if math.isfinite(value) and (best is None or value<best[1]): best=(candidate,value,iterations,converged)
    boundary=[start if i==0 and j==0 else 0.0 for i,j in pairs]
    boundary_value=objective(boundary)
    if best is None or boundary_value<=best[1]+1e-8: best=(boundary,boundary_value,0,True)
    require(best is not None,'Random-slope model did not converge')
    fields=evaluate(best[0]); require(fields is not None,'Random-slope model did not converge')
    return fields,best


def mixed_diagnostics(engine,result,objective,n,p,random_parameters,converged):
    reml=result['estimation']=='REML'; degrees=n-p if reml else n
    likelihood=-.5*(objective+degrees*(1+math.log(2*math.pi)))
    parameters=p+random_parameters+1
    result.update({'restricted log likelihood' if reml else 'log likelihood':likelihood,
                   'AIC':-2*likelihood+2*parameters,'BIC':-2*likelihood+math.log(n)*parameters,
                   'likelihood parameters':parameters,'optimizer converged':int(converged)})
    warnings=[]
    if not converged: warnings.append('Optimizer iteration limit reached; Wald SE, p-values and CI are unavailable.')
    if result['singular fit']: warnings.append('Singular random-effect covariance; variance-component uncertainty is nonstandard.')
    if result['subjects']<20: warnings.append('Few subjects; asymptotic Wald inference may be unreliable.')
    if reml:
        warnings.append('REML AIC/BIC comparisons require identical fixed-effect design and response data; use ML to compare fixed effects.')
    result['diagnostics']={'convergence':'converged' if converged else 'iteration limit',
        'random effects':'singular' if result['singular fit'] else 'interior',
        'inference':'unavailable' if not converged else 'caution' if result['singular fit'] or result['subjects']<20 else 'asymptotic Wald',
        'warnings':warnings}
    result['CI method']='Wald (fixed effects)' if converged else 'unavailable'
    for warning in warnings: engine.note+=' '+warning


def gee_covariance(fisher,scores,cluster_information,correction,clusters,p):
    """Mancl-DeRouen sandwich via p-dimensional leverage correction.

    D' V^-1 (I-H)^-1 e = (I - F_cluster B)^-1 score; avoids
    constructing a cluster-sized leverage matrix. Small mode uses t(G-p).
    """
    bread=inverse(fisher); meat=mp.zeros(p)
    if correction=='small': require(clusters>p,'Small-sample GEE requires more clusters than coefficients')
    for score,information in zip(scores,cluster_information):
        if correction=='small':
            leverage=mp.eye(p)-information*bread
            require(abs(float(mp.det(leverage)))>1e-12,'GEE cluster leverage is singular; small-sample correction unavailable')
            score=inverse(leverage)*score
        meat+=score*score.T
    return bread*meat*bread


def gee_diagnostics(result,clusters,p,correction):
    warnings=[]
    if clusters<20: warnings.append('Few clusters; inference can remain unreliable even after a small-sample correction.')
    if any(row['SE']==0 for row in result['coefficients']): warnings.append('Zero robust SE; corresponding p-values and CI are unavailable.')
    result['covariance correction']='Mancl-DeRouen' if correction=='small' else 'none (asymptotic sandwich)'
    if correction=='small': result['inference df']=clusters-p
    result['diagnostics']={'convergence':'converged','inference':'caution' if warnings else 't inference' if correction=='small' else 'asymptotic Wald','warnings':warnings}
    return result


def mixed_random_effects(engine,x,y,clusters,ids,slopes,method,ci_options=None):
    fields,best=random_effects_fit(x,y,clusters,slopes,method)
    n=len(y); p=len(x[0]); count=1+len(slopes)
    _,beta,information,rss,theta=fields
    sigma=rss/(n-p) if method=='reml' else rss/n
    result={'coefficients':inference(list(map(float,beta)),inverse(information)*sigma,['Intercept']+['x'+str(i) for i in range(1,p)],reliable=best[3]),'residual variance':sigma,'random intercept variance':sigma*float(theta[0,0])}
    for index,slope in enumerate(slopes):
        result['random slope variance x'+str(slope)]=sigma*float(theta[index+1,index+1])
        denominator=math.sqrt(float(theta[0,0])*float(theta[index+1,index+1]))
        result['intercept-slope correlation x'+str(slope)]=float(theta[0,index+1])/denominator if denominator else None
    for i in range(1,count):
        for j in range(i+1,count):
            label='x'+str(slopes[i-1])+' / x'+str(slopes[j-1])
            result['slope-slope covariance '+label]=sigma*float(theta[i,j])
            denominator=math.sqrt(float(theta[i,i])*float(theta[j,j]))
            result['slope-slope correlation '+label]=float(theta[i,j])/denominator if denominator else None
    if count==2:
        result['random slope variance']=result['random slope variance x'+str(slopes[0])]
        result['intercept-slope correlation']=result['intercept-slope correlation x'+str(slopes[0])]
    result['ICC at x=0']=float(theta[0,0])/(1+float(theta[0,0]))
    eigenvalues=[float(v) for v in mp.eigsy(theta,eigvals_only=True)]
    result['singular fit']=int(min(eigenvalues)<=1e-6*max(1,max(eigenvalues)))
    result['optimizer iterations']=best[2]; result['optimizer converged']=int(best[3])
    result['subject random effects (BLUP)']=[]
    for id_,c in zip(ids,clusters):
        z=mp.matrix([[1.0]+[x[i][s] for s in slopes] for i in c])
        effects=theta*inverse(mp.eye(count)+z.T*z*theta)*z.T*mp.matrix([y[i]-dot(x[i],beta) for i in c])
        result['subject random effects (BLUP)'].append({'subject':id_,**{label:float(v) for label,v in zip(['Intercept']+['x'+str(s) for s in slopes],effects)}})
    result['subjects']=len(ids); result['estimation']=method.upper()
    engine.note += ' Gaussian random intercept with random slopes on '+', '.join('x'+str(s) for s in slopes)+', '+('restricted maximum likelihood (REML)' if method=='reml' else 'maximum likelihood (ML)')+', Wald inference. Rows: subject ID, predictors, response.'
    engine.note += ' ICC at x=0; within-subject correlation varies with the predictors.'
    if result['singular fit']: engine.note += ' Singular fit: random-effect covariance is on or near a boundary.'
    if not best[3]: engine.note += ' Optimizer iteration limit reached; estimates may be unreliable.'
    mixed_diagnostics(engine,result,fields[0],n,p,count*(count+1)//2,best[3])
    if ci_options:
        from calc_mixed_intervals import intervals
        intervals(engine,result,x,y,clusters,slopes,method,ci_options)
    return result


def clustered(engine,name,a):
    rows=table(a[0],4,3)
    grouped={}
    for index,row in enumerate(rows): grouped.setdefault(row[0],[]).append(index)
    ids=sorted(grouped); clusters=[grouped[id_] for id_ in ids]
    require(len(clusters)>=3,'At least three subject/cluster IDs are required')
    if name=='gee' and len(clusters)<20:
        engine.note += ' Few clusters: sandwich SE and asymptotic Wald p-values may be unreliable.'
    pairs=[]; names=[]; terms=''
    if name=='gee':
        family=option(a,1,'gaussian'); require(family in ('gaussian','binomial','poisson'),'GEE family: gaussian, binomial, or poisson')
        corr=option(a,2,'independence'); require(corr in ('independence','exchangeable','ar1'),'GEE working correlation: independence, exchangeable, or ar1')
        width=len(rows[0])-2; require(width>=1,'Choose at least one predictor')
        pairs=interaction_pairs(a[3] if len(a)>3 and not (len(a)>4 and a[3]==[]) else None,width)
        correction=option(a,4,'robust'); require(correction in ('robust','small'),'GEE covariance: robust or small (Mancl-DeRouen, t with clusters minus coefficients df)')
        names=['Intercept']+['x'+str(i) for i in range(1,width+1)]+[('x'+str(i)+'^2') if i==j else ('x'+str(i)+':x'+str(j)) for i,j in pairs]
        terms=', '.join(('x'+str(i)+'^2') if i==j else ('x'+str(i)+':x'+str(j)) for i,j in pairs)
        if pairs: rows=[row[:1]+row[1:-1]+[row[i]*row[j] for i,j in pairs]+row[-1:] for row in rows]
    x,y=clustered_design(rows); n=len(y); p=len(x[0]); Y=mp.matrix(y)
    if name=='mixedmodel':
        argument=a[1] if len(a)>1 else 0
        if isinstance(argument,(list,tuple)):
            slopes=[integer(v,1,19,capacity=True) for v in argument]
            require(slopes and len(slopes)<=3 and len(set(slopes))==len(slopes),'Use 0, a predictor position, or up to three distinct positions such as [1,2]')
        else:
            selected=integer(argument,0,19,capacity=True); slopes=[] if selected==0 else [selected]
        method=option(a,2,'reml'); require(method in ('ml','reml'),'Estimation: ml or reml')
        from calc_mixed_intervals import options
        ci_options=options(a[3] if len(a)>3 else 'wald')
        require(any(len(c)>1 for c in clusters),'Random intercept requires repeated subjects')
        for slope in slopes: require(1<=slope<p,'Random-slope predictor position is out of range')
        if not slopes: return mixed_intercept(engine,x,y,clusters,ids,method,ci_options)
        require(within_limit(len(clusters)*(p*(len(slopes)+1))**2,20000),'Random-slope model is too large; reduce predictors, random effects, or subjects')
        return mixed_random_effects(engine,x,y,clusters,ids,slopes,method,ci_options)
    x,transform,_,_=standardized_design(x); X=mp.matrix(x)
    if pairs: engine.note += ' GEE interactions: '+terms+'.'
    if family=='binomial': require(all(v in (0,1) for v in y),'Binomial GEE response must be 0/1')
    if family=='poisson': require(all(v>=0 and v.is_integer() for v in y),'Poisson GEE response must be integer counts')
    if family=='gaussian': b=list(map(float,inverse(X.T*X)*X.T*Y)); mu=[dot(r,b) for r in x]; weights=[1.0]*n
    else:
        def exact(b):
            # Canonical-link GLM start: the score and the Fisher information
            # are analytic, so the working-correlation iteration avoids
            # finite differences.
            value = 0.0; score = [0.0]*p; information = [[0.0]*p for _ in range(p)]
            for row,v in zip(x,y):
                z = dot(row,b)
                if family=='binomial':
                    fitted = logistic(z); value += softplus(z)-v*z; weight = fitted*(1-fitted)
                else:
                    fitted = math.exp(z); value += fitted-v*z; weight = fitted
                for j in range(p):
                    score[j] += row[j]*(fitted-v)
                    for k in range(j,p): information[j][k] += row[j]*row[k]*weight
            for j in range(1,p):
                for k in range(j): information[j][k] = information[k][j]
            return value,score,information
        b,_,_,_=newton([0.0]*p,exact); mu=[logistic(dot(r,b)) if family=='binomial' else math.exp(dot(r,b)) for r in x]; weights=[v*(1-v) if family=='binomial' else v for v in mu]
        if family=='binomial':
            margins=[(2*v-1)*dot(r,b) for r,v in zip(x,y)]
            require(not (min(margins)>=-1e-8 and max(margins)>1e-8),'Complete or quasi separation: binomial GEE estimates are not finite')
    if corr=='independence':
        fisher=mp.matrix([[sum(weights[t]*x[t][i]*x[t][j] for t in range(n)) for j in range(p)] for i in range(p)]); scores=[]; cluster_information=[]
        for c in clusters:
            scores.append(mp.matrix([sum(x[t][i]*(y[t]-mu[t]) for t in c) for i in range(p)]))
            cluster_information.append(mp.matrix([[sum(weights[t]*x[t][i]*x[t][j] for t in c) for j in range(p)] for i in range(p)]))
        cov=gee_covariance(fisher,scores,cluster_information,correction,len(ids),p)
        b=list(map(float,transform*mp.matrix(b))); cov=transform*cov*transform.T
        engine.note += ' GEE: independent working correlation, cluster sandwich covariance, '+('t inference' if correction=='small' else 'asymptotic Wald inference')+'. Rows: cluster ID, predictors, response. Zero robust SE leaves p/CI unavailable.'
        if correction=='small': engine.note+=' Mancl-DeRouen leverage-adjusted covariance; t inference with clusters minus coefficients degrees of freedom.'
        return gee_diagnostics({'coefficients':inference(b,cov,names,family!='gaussian',df=len(ids)-p if correction=='small' else None),'clusters':len(ids),'family':family},len(ids),p,correction)
    largest=max(len(c) for c in clusters)
    def moments(alpha,beta):
        mean_values=[]; variances=[]; derivatives=[]
        for row in x:
            linear=dot(row,beta)
            if family=='gaussian': mean_values.append(linear); variances.append(1.0); derivatives.append(1.0)
            else:
                value=logistic(linear) if family=='binomial' else math.exp(linear)
                mean_values.append(value); variances.append(value*(1-value) if family=='binomial' else value); derivatives.append(value*(1-value) if family=='binomial' else value)
        require(all(math.isfinite(v) for v in mean_values),'GEE mean function exceeded the numeric range')
        correlation=alpha
        if corr=='exchangeable' and largest>1: correlation=max(correlation,-1/(largest-1)+1e-6)
        fisher=mp.zeros(p); scores=[]; cluster_information=[]; standardized=[0.0]*n
        for c in clusters:
            size=len(c); scaled=[]; residual=[]
            for i in c:
                divided=derivatives[i]/math.sqrt(variances[i])
                scaled.append(mp.matrix([x[i][j]*divided for j in range(p)]))
                residual.append((y[i]-mean_values[i])/math.sqrt(variances[i])); standardized[i]=residual[-1]
            if corr=='exchangeable':
                first=1/(1-correlation); second=correlation/((1-correlation)*(1+(size-1)*correlation))
                columns_sum=mp.matrix([mp.fsum(scaled[k][j,0] for k in range(size)) for j in range(p)])
                gram=mp.zeros(p)
                for k in range(size): gram+=scaled[k]*scaled[k].T
                score=first*mp.matrix([mp.fsum(scaled[k][j,0]*residual[k] for k in range(size)) for j in range(p)])-second*math.fsum(residual)*columns_sum
                information=first*gram-second*columns_sum*columns_sum.T
                fisher+=information
            else:
                if size==1: coefficient=1.0; diagonals=[1.0]
                else:
                    coefficient=1/(1-correlation*correlation); diagonals=[1.0]*size
                    for k in range(1,size-1): diagonals[k]=1+correlation*correlation
                vector=mp.matrix([mp.fsum(diagonals[k]*scaled[k][j,0]*residual[k] for k in range(size)) for j in range(p)])
                gram=mp.zeros(p)
                for k in range(size): gram+=diagonals[k]*(scaled[k]*scaled[k].T)
                if size>1:
                    for k in range(size-1):
                        vector-=correlation*mp.matrix([scaled[k][j,0]*residual[k+1]+scaled[k+1][j,0]*residual[k] for j in range(p)])
                        gram-=correlation*(scaled[k]*scaled[k+1].T+scaled[k+1]*scaled[k].T)
                score=coefficient*vector; information=coefficient*gram; fisher+=information
            scores.append(score)
            cluster_information.append(information)
        return fisher,scores,standardized,cluster_information
    def update(standardized):
        numerator=0.0; denominator=0.0
        # Estimate Pearson dispersion before estimating a dimensionless
        # correlation. This also makes Gaussian alpha invariant to units.
        dispersion=math.fsum(v*v for v in standardized)/(n-p)
        if dispersion<=1e-30: return 0.0
        if corr=='exchangeable':
            for c in clusters:
                values=[standardized[i] for i in c]; numerator+=math.fsum(values[i]*values[j] for i in range(len(values)) for j in range(i+1,len(values))); denominator+=len(values)*(len(values)-1)/2
            # Pearson scale and pair degrees of freedom follow statsmodels.
            denominator=dispersion*max(1,denominator-p)
        else:
            for c in clusters:
                values=[standardized[i] for i in c]
                if len(values)>1:
                    numerator+=math.fsum(values[k]*values[k+1] for k in range(len(values)-1))/(len(values)-1)
                    denominator+=math.fsum(v*v for v in values)/len(values)
        if denominator<=0: return 0.0
        lower=-1/(largest-1)+1e-6 if corr=='exchangeable' and largest>1 else -0.9999
        return max(lower,min(0.9999,numerator/denominator))
    alpha=0.0
    for iteration in range(200):
        fisher,scores,standardized,_=moments(alpha,b)
        previous_alpha=alpha; alpha=update(standardized)
        total=mp.zeros(p,1)
        for vector in scores: total+=vector
        step=inverse(fisher)*total
        if max(abs(float(step[j,0])) for j in range(p))<1e-9 and abs(alpha-previous_alpha)<1e-9: break
        b=[b[j]+float(step[j,0]) for j in range(p)]
        require(all(math.isfinite(v) for v in b) and (family=='gaussian' or max(abs(v) for v in b)<40),'GEE estimates diverged; simplify the working correlation')
    else: raise MathError('GEE did not converge; simplify the working correlation')
    fisher,scores,standardized,cluster_information=moments(alpha,b)
    cov=gee_covariance(fisher,scores,cluster_information,correction,len(ids),p)
    b=list(map(float,transform*mp.matrix(b))); cov=transform*cov*transform.T
    engine.note += ' GEE: '+corr+' working correlation'+(' (moment estimate alpha='+format(alpha,'.4g')+')' if corr!='independence' else '')+', cluster sandwich covariance, '+('t inference' if correction=='small' else 'asymptotic Wald inference')+'. Rows: cluster ID, predictors, response'+('; AR(1) uses the within-cluster row order as the time order' if corr=='ar1' else '')+'. Zero robust SE leaves p/CI unavailable.'
    if correction=='small': engine.note+=' Mancl-DeRouen leverage-adjusted covariance; t inference with clusters minus coefficients degrees of freedom.'
    return gee_diagnostics({'coefficients':inference(b,cov,names,family!='gaussian',df=len(ids)-p if correction=='small' else None),'clusters':len(ids),'family':family,'working correlation':corr,'alpha':alpha,'Pearson dispersion':math.fsum(v*v for v in standardized)/(n-p)},len(ids),p,correction)


def calculate(engine, name, a):
    if name == 'repeatedanova':
        return repeated_anova(engine, a)
    return clustered(engine, name, a)
