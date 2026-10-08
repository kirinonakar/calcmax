"""Complete Lambert W families for affine exponential equations."""
import sympy as s


def affine_exponential_solutions(expression, variable, domain):
    """Solve a*exp(b*x+c)+d*x+f=0, with finite real numeric coefficients.

    Unlike a finite solve() fallback, the complex result contains every
    integer Lambert W branch. Real results use exactly branches 0 and -1
    where they exist.
    """
    expanded=s.expand(expression)
    exponentials=[term for term in expanded.atoms(s.exp) if term.has(variable)]
    if len(exponentials)!=1:
        return None
    exponential=exponentials[0]
    a=expanded.coeff(exponential)
    remainder=s.expand(expanded-a*exponential)
    b=s.diff(exponential.args[0],variable)
    c=s.simplify(exponential.args[0]-b*variable)
    d=s.diff(remainder,variable)
    f=s.simplify(remainder-d*variable)
    coefficients=(a,b,c,d,f)
    if (any(value.has(variable) or value.is_number is not True or value.is_real is not True
            or value.is_finite is not True for value in coefficients)
            or any(value.is_zero is not False for value in (a,b,d))):
        return None
    z=s.simplify(b*a*s.exp(c-b*f/d)/d)
    if domain.is_subset(s.S.Reals):
        real_exists=s.Ge(z,-1/s.E)
        if real_exists==s.false:
            return s.S.EmptySet,"No real solutions (Lambert W real-branch domain)."
        if real_exists!=s.true:
            return None
        negative=s.Lt(z,0)
        if negative not in (s.true,s.false):
            return None
        branches=(0,-1) if negative==s.true else (0,)
        roots=s.FiniteSet(*(-s.LambertW(z,k)/b-f/d for k in branches))
        return roots.intersect(domain),"All real Lambert W branches in the selected domain."
    if domain!=s.S.Complexes:
        return None
    k=s.Symbol("n" if str(variable)=="k" else "k",integer=True)
    family=s.ImageSet(s.Lambda(k,-s.LambertW(z,k)/b-f/d),s.S.Integers)
    return family,"All complex solutions: Lambert W branch "+str(k)+" ranges over all integers."
