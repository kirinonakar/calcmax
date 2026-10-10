"""Small portable least-squares fits with reorthogonalized QR (no NumPy)."""
from calc_limits import within_limit
import math
import mpmath as mp
from calc_shared import require
from calc_advanced_common import dot


def least_squares(x, y, weights=None):
    n, p = len(x), len(x[0])
    require(n > p and within_limit(p,30), 'More observations than coefficients are required (limit: 30 coefficients)')
    roots = [1.0]*n if weights is None else [math.sqrt(w) for w in weights]
    require(all(math.isfinite(w) and w > 0 for w in roots), 'Model weights are invalid')
    columns = [[row[j]*w for row, w in zip(x, roots)] for j in range(p)]
    target = [v*w for v, w in zip(y, roots)]
    q, r = [], mp.zeros(p)
    for j, column in enumerate(columns):
        norm = math.sqrt(math.fsum(v*v for v in column))
        require(math.isfinite(norm) and norm > 0, 'Predictors are collinear')
        v = column[:]
        for _ in range(2):
            for i, basis in enumerate(q):
                projection = math.fsum(a*b for a, b in zip(basis, v))
                r[i, j] += projection
                v = [a-projection*b for a, b in zip(v, basis)]
        residual_norm = math.sqrt(math.fsum(a*a for a in v))
        require(residual_norm > 1e-10*norm, 'Predictors are collinear or numerically unidentifiable')
        r[j, j] = residual_norm
        q.append([a/residual_norm for a in v])
    inverse_r = r**-1
    beta = list(map(float, inverse_r*mp.matrix([math.fsum(a*b for a, b in zip(basis, target)) for basis in q])))
    fitted = [dot(row, beta) for row in x]
    sse = math.fsum((v-fit)**2*w*w for v, fit, w in zip(y, fitted, roots))
    return beta, inverse_r*inverse_r.T, sse, fitted


def partial_f_test(design, target, columns, fit=None):
    """Joint test of a coefficient block against the same full OLS model."""
    fit = fit or least_squares(design, target)
    indices = sorted(set(columns))
    require(indices and all(0 <= i < len(design[0]) for i in indices), 'Choose coefficient columns to test')
    reduced = [[value for j,value in enumerate(row) if j not in indices] for row in design]
    require(reduced[0], 'Keep at least one coefficient in the reduced model')
    reduced_sse = least_squares(reduced, target)[2]
    df_error = len(target)-len(design[0])
    require(fit[2] > 0, 'Residual variance must be positive')
    ss = max(0., reduced_sse-fit[2])
    f = ss/len(indices)/(fit[2]/df_error)
    from calc_statistics import _f_sf
    return dict(SS=ss,df=len(indices),F=f,p=float(_f_sf(f,len(indices),df_error)))


def factorial_design(rows, categorical, order=1, coding='sum'):
    """Expand categorical contrasts and all hierarchical interaction terms."""
    from itertools import combinations, product
    width = len(rows[0])
    require(coding in ('sum','treatment'), 'Choose sum or treatment contrasts')
    require(1 <= order <= width, 'Interaction order must be within the predictor count')
    require(len(set(categorical)) == len(categorical) and all(i in range(width) for i in categorical), 'Choose categorical predictor positions')
    bases, levels = [], {}
    for j in range(width):
        if j in categorical:
            level = sorted(set(row[j] for row in rows)); levels[j]=level
            require(len(level)>=2, 'Categorical predictors need at least two levels')
            bases.append([[float(row[j]==value)-(float(row[j]==level[-1]) if coding=='sum' else 0.) for value in level[:-1]] for row in rows])
        else:
            require(len(set(row[j] for row in rows))>=2, 'Predictors require variation')
            bases.append([[row[j]] for row in rows])
    design = [[1.] for _ in rows]; terms=[]; names=['Intercept']
    for size in range(1,order+1):
        for variables in combinations(range(width),size):
            start=len(design[0])
            require(within_limit(start+math.prod(len(bases[j][0]) for j in variables),30),'More observations than coefficients are required (limit: 30 coefficients)')
            for indices in product(*(range(len(bases[j][0])) for j in variables)):
                for r in range(len(rows)):
                    design[r].append(math.prod(bases[j][r][index] for j,index in zip(variables,indices)))
                names.append(':'.join('x'+str(j+1)+(f'[{levels[j][index]}]' if j in levels else '') for j,index in zip(variables,indices)))
            terms.append((variables,list(range(start,len(design[0])))))
    return design,terms,names,levels


def term_f_tests(design, target, terms, ss_type=3, fit=None):
    """Type III block tests or Type II covariance-orthogonal marginal tests."""
    require(ss_type in (2,3), 'Choose Type II or III sums of squares')
    fit=fit or least_squares(design,target)
    beta,covariance,sse,_=fit; df_error=len(target)-len(beta)
    require(sse>0,'Residual variance must be positive')
    from calc_statistics import _f_sf
    result=[]
    for variables,indices in terms:
        higher=[at for other,cols in terms if set(variables)<set(other) for at in cols] if ss_type==2 else []
        if not higher:
            result.append(partial_f_test(design,target,indices,fit)); continue
        own=indices+higher
        # Complement of Cov(L1 b, L2 b) eliminates the higher-order hypotheses.
        vectors=[]
        def orthogonal(vector):
            v=list(vector)
            for _ in range(2):
                for basis in vectors:
                    projection=math.fsum(x*y for x,y in zip(v,basis))
                    v=[x-projection*y for x,y in zip(v,basis)]
            norm=math.sqrt(math.fsum(x*x for x in v))
            return [x/norm for x in v] if norm>1e-10 else None
        for at in higher:
            basis=orthogonal([float(covariance[i,at]) for i in own])
            require(basis is not None,'Type II hypotheses are unidentifiable'); vectors.append(basis)
        complement=[]
        for j in range(len(own)):
            basis=orthogonal([float(i==j) for i in range(len(own))])
            if basis is not None: vectors.append(basis); complement.append(basis)
        require(len(complement)==len(indices),'Type II hypotheses are unidentifiable')
        h=mp.zeros(len(indices),len(beta))
        for r,vector in enumerate(complement):
            for at,value in zip(own,vector): h[r,at]=value
        estimate=h*mp.matrix(beta)
        ss=max(0.,float((estimate.T*(h*covariance*h.T)**-1*estimate)[0]))
        f=ss/len(indices)/(sse/df_error)
        result.append(dict(SS=ss,df=len(indices),F=f,p=float(_f_sf(f,len(indices),df_error))))
    return result
