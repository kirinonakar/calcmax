"""Verified substitutions for real branches missed by the symbolic integrator."""
import sympy as s


def rational_trig_primitive(expression, variable):
    """Rationalize fractional tan/cot powers on a positive real branch.

    t = trig(a*x+b)**(1/q), for q <= 4. Both the forward substitution
    and the rational primitive are checked before a result is returned.
    Definite integrals and complex branch continuation are left to SymPy.
    """
    if not isinstance(variable,s.Symbol) or variable.is_real is False:
        return None
    if expression.free_symbols - {variable}:
        return None
    trigs=[term for term in expression.atoms(s.tan,s.cot) if term.has(variable)]
    if len(trigs)!=1:
        return None
    trig=trigs[0]
    argument=trig.args[0]
    slope=s.diff(argument,variable)
    offset=s.simplify(argument-slope*variable)
    if (slope.has(variable) or offset.has(variable) or slope.is_number is not True
            or slope.is_real is not True or slope.is_zero is not False
            or offset.is_real is not True):
        return None
    fractional_powers=[power for power in expression.atoms(s.Pow)
                       if power.base in (trig,-trig) and power.exp.is_Rational and power.exp.q>1]
    root_signs={1 if power.base==trig else -1 for power in fractional_powers}
    if len(root_signs)!=1:
        return None
    root_sign=root_signs.pop()
    root_base=root_sign*trig
    denominators=[power.exp.q for power in fractional_powers]
    if any(denominator>4 for denominator in denominators):
        return None
    q=int(s.ilcm(1,*denominators)) if denominators else 1
    if not 2<=q<=4:
        return None
    t=s.Dummy("trig_root",positive=True)
    transformed=expression.subs(trig,root_sign*t**q)
    if transformed.has(variable) or transformed.is_rational_function(t) is not True:
        return None
    sign=1 if trig.func==s.tan else -1
    integrand=s.cancel(root_sign*sign*q*t**(q-1)*transformed/(slope*(1+t**(2*q))))
    primitive=s.integrate(integrand,t)
    if primitive.has(s.Integral) or s.cancel(s.diff(primitive,t)-integrand)!=0:
        return None
    result=primitive.subs(t,root_base**s.Rational(1,q))
    conditions=[s.Gt(root_base,0)]
    if variable.is_real is not True:
        conditions.insert(0,s.Eq(s.im(variable),0))
    note="Used t = "+str(root_base**s.Rational(1,q))+" to integrate a rational function. Valid on each continuous real interval with "+str(root_base)+" > 0; complex branches are not extended by this rule."
    return result,conditions,note
