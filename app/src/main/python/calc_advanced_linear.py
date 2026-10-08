"""Small portable least-squares fits with reorthogonalized QR (no NumPy)."""
import math
import mpmath as mp
from calc_shared import require
from calc_advanced_common import dot


def least_squares(x, y, weights=None):
    n, p = len(x), len(x[0])
    require(n > p and p <= 30, 'More observations than coefficients are required (limit: 30 coefficients)')
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
