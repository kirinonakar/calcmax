"""Portable damped BFGS and observed information for likelihood models."""
import math
import mpmath as mp
from calc_shared import MathError, require
from calc_advanced_common import dot, inverse


def minimize(start, objective, tolerance=2e-7, maximum=500):
    """objective returns value and analytic gradient in scaled units."""
    x=list(start); value,gradient=objective(x); size=len(x); h=mp.eye(size)
    for iteration in range(maximum):
        if max(map(abs,gradient)) < tolerance: return x,value,iteration
        direction=list(map(float,-h*mp.matrix(gradient)))
        if dot(direction,gradient)>=0:
            h=mp.eye(size); direction=[-g for g in gradient]
        step=1.
        for _ in range(50):
            candidate=[v+step*d for v,d in zip(x,direction)]
            try: trial,g=objective(candidate)
            except (ValueError,OverflowError,ZeroDivisionError,MathError): trial=math.inf
            if math.isfinite(trial) and trial<=value+1e-4*step*dot(direction,gradient): break
            step*=.5
        else: raise MathError('Model optimization failed; check scaling and identifiability')
        delta=mp.matrix([a-b for a,b in zip(candidate,x)]); change=mp.matrix([a-b for a,b in zip(g,gradient)])
        product=float((delta.T*change)[0])
        if product>1e-12*float(mp.norm(delta))*float(mp.norm(change)):
            update=mp.eye(size)-delta*change.T/product
            h=update*h*update.T+delta*delta.T/product
        else: h=mp.eye(size)
        x,value,gradient=candidate,trial,g
    raise MathError('Model did not converge; simplify the model or check the data')


def information(parameters, objective, scale=1.):
    """Central differences of the analytic gradient, with an SPD guard."""
    size=len(parameters); matrix=mp.zeros(size)
    for j in range(size):
        step=1e-4*max(1.,abs(parameters[j])); left=parameters[:]; right=parameters[:]
        left[j]-=step; right[j]+=step
        gl=objective(left)[1]; gr=objective(right)[1]
        for i in range(size): matrix[i,j]=scale*(gr[i]-gl[i])/(2*step)
    matrix=(matrix+matrix.T)/2
    diagonal=[math.sqrt(float(matrix[i,i])) if matrix[i,i]>0 else 0 for i in range(size)]
    require(all(diagonal),'Model information is singular; estimates are not identifiable')
    normalized=mp.matrix([[matrix[i,j]/(diagonal[i]*diagonal[j]) for j in range(size)] for i in range(size)])
    require(min(mp.eigsy(normalized,eigvals_only=True))>1e-7,'Model information is singular; boundary or unidentified estimates')
    return inverse(matrix)
