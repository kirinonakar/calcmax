"""Development-only NumPy/SciPy references; no production-engine imports."""
import json
from pathlib import Path
import numpy as np
from scipy import optimize, special, stats
import scipy

ROOT = Path(__file__).resolve().parents[1]
rows = np.array([[-2,0],[-1,0],[1,1],[2,1]], dtype=float)
x = rows[:,:-1]; y = rows[:,-1]
center = x.mean(axis=0); scale = x.std(axis=0)
design = np.column_stack([np.ones(len(y)),(x-center)/scale])
transform = np.array([[1,-center[0]/scale[0]],[0,1/scale[0]]])
prior_sd = 2.5; precision = 1/prior_sd**2
inverse = np.linalg.inv(design.T@design+precision*np.eye(2))
mean = inverse@design.T@y
a = 2+len(y)/2
b = 1+(np.sum((y-design@mean)**2)+precision*np.sum(mean**2))/2
cov = transform@inverse@transform.T
linear = []
for value,v in zip(transform@mean,np.diag(cov)):
    distribution = stats.t(2*a,loc=value,scale=np.sqrt(b/a*v))
    linear.append(dict(estimate=float(value),posteriorSD=float(distribution.std()),
                       low=float(distribution.ppf(.025)),high=float(distribution.ppf(.975)),
                       probabilityPositive=float(distribution.sf(0))))

def objective(beta):
    logits = design@beta
    return np.logaddexp(0,logits).sum()-y@logits+precision*(beta@beta)/2

def gradient(beta):
    return design.T@(special.expit(design@beta)-y)+precision*beta

solution = optimize.minimize(objective,[0.,0.],jac=gradient,method='BFGS',options={'gtol':1e-11})
assert np.max(np.abs(gradient(solution.x))) < 1e-8
probs = special.expit(design@solution.x)
cov = transform@np.linalg.inv(design.T@((probs*(1-probs))[:,None]*design)+precision*np.eye(2))@transform.T
laplace = []
for value,v in zip(transform@solution.x,np.diag(cov)):
    distribution = stats.norm(loc=value,scale=np.sqrt(v))
    laplace.append(dict(estimate=float(value),posteriorSD=float(distribution.std()),
                        low=float(distribution.ppf(.025)),high=float(distribution.ppf(.975)),
                        probabilityPositive=float(distribution.sf(0))))

# Independent 2D Gauss-Legendre quadrature for the non-Gaussian posterior.
# Split the slope at zero, avoiding a discontinuity when calculating P(beta>0).
nodes,weights = special.roots_legendre(400)
intercepts = nodes*25; iw = weights*25
moments = []; positive = 0
for lo,hi in [(-25,0),(0,25)]:
    slopes = (nodes+1)*(hi-lo)/2+lo; sw = weights*(hi-lo)/2
    intercept,slope = np.meshgrid(intercepts,slopes,indexing='ij')
    log_density = -precision*(intercept**2+slope**2)/2
    for z,response in zip(design[:,1],y):
        eta = intercept+z*slope
        log_density += response*eta-np.logaddexp(0,eta)
    mass = np.exp(log_density)*iw[:,None]*sw[None,:]
    moments.append((mass.sum(),(mass*slope).sum(),(mass*slope**2).sum(),(mass*intercept**2).sum()))
    if lo == 0: positive = mass.sum()
mass,first,second,intercept_second = np.sum(moments,axis=0)
quadrature = [dict(estimate=0.,posteriorSD=float(np.sqrt(intercept_second/mass)),probabilityPositive=.5),
              dict(estimate=float(first/mass/scale[0]),posteriorSD=float(np.sqrt(second/mass-(first/mass)**2)/scale[0]),probabilityPositive=float(positive/mass))]
fixture = dict(source=f'NumPy/SciPy {scipy.__version__}; independent conjugate solution, MAP/Hessian, and 2D quadrature',
               rows=rows.tolist(),options=[2.5,.95],linear=linear,laplace=laplace,logisticQuadrature=quadrature)
(ROOT/'tests/fixtures/bayesian_regression_reference.json').write_text(json.dumps(fixture,indent=2)+'\n')
