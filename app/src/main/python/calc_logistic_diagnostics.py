"""One-step GLM influence diagnostics for binary logistic fits."""
import mpmath as mp
from calc_shared import MathError


def logistic_diagnostics(report, design, target, logits, precision, inverse=None, l2=0):
    """Populate Pearson/deviance residuals, hat diagonals and Cook's distance.

    Penalized callers supply an active design and the L2 Hessian contribution.
    Their local diagnostics hold predictor selection fixed. Firth callers supply
    unpenalized Fisher information at the bias-reduced estimate.
    """
    from calc_inference import _covariance
    with mp.workdps(max(40, precision+15)):
        design = [[mp.mpf(v) for v in row] for row in design]
        target, logits = list(map(mp.mpf, target)), list(map(mp.mpf, logits))
        weights = [mp.exp(-abs(z))/(1+mp.exp(-abs(z)))**2 for z in logits]
        out = lambda value: None if value is None else mp.nstr(value, precision)
        p = len(design[0])
        for row, y, z in zip(report["residuals"], target, logits):
            dev = mp.sqrt(2*(max(-z, 0)+mp.log1p(mp.exp(-abs(z))))) if y == 1 else -mp.sqrt(2*(max(z, 0)+mp.log1p(mp.exp(-abs(z)))))
            pearson = mp.exp(-z/2) if y == 1 else -mp.exp(z/2)
            row.update(standardized=out(pearson), deviance=out(dev), leverage=None, cook=None)
        if inverse is None:
            weighted = [[mp.sqrt(w)*x for x in row] for row, w in zip(design, weights)]
            if l2:
                # Intercept is unpenalized. The standardized likelihood is a mean
                # loss, so its regularization Hessian scales by n here.
                penalty = mp.sqrt(mp.mpf(l2)*len(design))
                weighted += [[penalty if i == j else 0 for i in range(p)] for j in range(1, p)]
            try:
                inverse = _covariance(weighted)
            except (MathError, ValueError, ZeroDivisionError):
                report["warnings"].append("Influence diagnostics unavailable for singular active predictors.")
                return
        for result, row, w in zip(report["residuals"], design, weights):
            h = min(mp.mpf(1), max(mp.mpf(0), w*(mp.matrix([row])*inverse*mp.matrix(row))[0]))
            pearson = mp.mpf(result["standardized"])
            cook = pearson**2*h/(p*(1-h)**2) if h < 1 else None
            result.update(leverage=out(h), cook=out(cook))
        report["influenceParameters"] = p
