"""Complete Lambert W families for affine exponential equations."""
import sympy as s


def product_exponential_form(expression, variable):
    """Normalize (a*x+d)*exp(b*x+c)+f=0 to u*exp(u)=z.

    Return the rate b, shift d/a, and z for u=b*(x+d/a).
    Coefficients must be finite real numbers with nonzero a and b.
    """
    expanded = s.expand(expression)
    exponentials = [term for term in expanded.atoms(s.exp) if term.has(variable)]
    if len(exponentials) != 1: return None
    exponential = exponentials[0]
    coefficient = expanded.coeff(exponential)
    a = s.diff(coefficient, variable)
    d = s.simplify(coefficient-a*variable)
    b = s.diff(exponential.args[0], variable)
    c = s.simplify(exponential.args[0]-b*variable)
    f = s.simplify(expanded-coefficient*exponential)
    if (any(value.has(variable) or value.is_number is not True or value.is_real is not True
            or value.is_finite is not True for value in (a, b, c, d, f))
            or any(value.is_zero is not False for value in (a, b))):
        return None
    shift = s.cancel(d/a)
    z = s.simplify(-b*f*s.exp(b*shift-c)/a)
    return b, shift, z


def lambert_branch_solutions(z, root, variable, domain):
    """Apply real-branch bounds or enumerate the entire complex family."""
    if z == 0:
        return s.FiniteSet(root(s.Integer(0))).intersect(domain), "The exponential factor is nonzero, so only the linear factor can vanish."
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
        roots=s.FiniteSet(*(root(s.LambertW(z,k)) for k in branches))
        return roots.intersect(domain),"All real Lambert W branches in the selected domain."
    if domain!=s.S.Complexes:
        return None
    k=s.Symbol("n" if str(variable)=="k" else "k",integer=True)
    family=s.ImageSet(s.Lambda(k,root(s.LambertW(z,k))),s.S.Integers)
    return family,"All complex solutions: Lambert W branch "+str(k)+" ranges over all integers."


def affine_exponential_solutions(expression, variable, domain):
    """Solve affine or affine-product exponential equations with numeric coefficients.

    Unlike a finite solve() fallback, the complex result contains every
    integer Lambert W branch. Real results use exactly branches 0 and -1
    where they exist.
    """
    product_form = product_exponential_form(expression, variable)
    if product_form is not None:
        rate, shift, z = product_form
        return lambert_branch_solutions(z, lambda w: w/rate-shift, variable, domain)
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
    return lambert_branch_solutions(z, lambda w: -w/b-f/d, variable, domain)
