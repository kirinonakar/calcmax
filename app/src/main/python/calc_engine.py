"""Offline math adapter. Only a validated AST is accepted, never executable source.

SymPy owns exact arithmetic and symbolic transformations. This module is independent
of Android and is exercised by the desktop test suite as well as on-device tests.
"""
import json
import math
import time
import sys
import statistics
import random
import sympy as s
import mpmath as mp
from sympy.core.relational import Relational
from sympy.core.function import AppliedUndef
from sympy.calculus.util import continuous_domain, function_range
from quantities import Quantity, quantity, convert_quantity

# Exact integers (e.g. factorial) are serialized to text; CPython 3.11+ caps
# int -> str conversion at 4300 digits, which is below the display limit used
# below. Raise it so the advertised range (factorial up to 10000) is usable.
if hasattr(sys, "set_int_max_str_digits"):
    sys.set_int_max_str_digits(100000)

class MathError(ValueError):
    pass

class Budget:
    def __init__(self, seconds=8, steps=3000000):
        self.deadline = time.monotonic() + seconds
        self.steps = steps
    def trace(self, frame, event, arg):
        self.steps -= 1
        if self.steps % 1024 == 0 and (self.steps <= 0 or time.monotonic() > self.deadline):
            raise MathError("Computation limit reached. Reduce expression complexity.")
        return self.trace

# Dimension order: length, mass, time, temperature, data, angle, current, amount.
UNITS = {}
def unit(names, dim, scale, offset=0):
    for name in names.split():
        UNITS[name] = (dim, s.Rational(str(scale)), s.Rational(str(offset)))
unit("m", "length", 1); unit("km", "length", 1000); unit("cm", "length", '.01'); unit("mm", "length", '.001')
unit("in inch", "length", '.0254'); unit("ft", "length", '.3048'); unit("yd", "length", '.9144'); unit("mi", "length", '1609.344')
unit("m2", "area", 1); unit("cm2", "area", '.0001'); unit("km2", "area", 1000000); unit("ha", "area", 10000); unit("acre", "area", '4046.8564224')
unit("m3", "volume", 1); unit("L", "volume", '.001'); unit("mL", "volume", '.000001'); unit("galUS", "volume", '.003785411784')
unit("kg", "mass", 1); unit("g", "mass", '.001'); unit("mg", "mass", '.000001'); unit("lb", "mass", '.45359237'); unit("oz", "mass", '.028349523125')
unit("K", "temperature", 1); unit("degC", "temperature", 1, '273.15'); unit("degF", "temperature", s.Rational(5,9), s.Rational(45967,180))
unit("s sec", "time", 1); unit("min", "time", 60); unit("h hr", "time", 3600); unit("day", "time", 86400); unit("ms", "time", '.001')
unit("mps", "speed", 1); unit("kph", "speed", s.Rational(5,18)); unit("mph", "speed", '.44704'); unit("knot", "speed", s.Rational(463,900))
unit("mps2", "acceleration", 1); unit("g0", "acceleration", '9.80665')
unit("Pa", "pressure", 1); unit("kPa", "pressure", 1000); unit("bar", "pressure", 100000); unit("atm", "pressure", 101325)
unit("N", "force", 1); unit("kN", "force", 1000); unit("lbf", "force", '4.4482216152605')
unit("J", "energy", 1); unit("kJ", "energy", 1000); unit("cal", "energy", '4.184'); unit("kWh", "energy", 3600000); unit("eV", "energy", '1.602176634e-19')
unit("W", "power", 1); unit("kW", "power", 1000)
unit("Hz", "frequency", 1); unit("kHz", "frequency", 1000); unit("MHz", "frequency", 1000000)
unit("A amp ampere", "current", 1); unit("mA", "current", '.001'); unit("uA", "current", '0.000001')
unit("C coulomb", "charge", 1); unit("mC", "charge", '.001'); unit("uC", "charge", '0.000001')
unit("V volt", "voltage", 1); unit("mV", "voltage", '.001'); unit("kV", "voltage", 1000)
unit("ohm Ω", "resistance", 1); unit("kohm kΩ", "resistance", 1000); unit("Mohm MΩ", "resistance", 1000000)
unit("S siemens", "conductance", 1); unit("mS", "conductance", '.001')
unit("F farad", "capacitance", 1); unit("uF", "capacitance", '0.000001'); unit("nF", "capacitance", '0.000000001'); unit("pF", "capacitance", '0.000000000001')
unit("H henry", "inductance", 1); unit("mH", "inductance", '.001'); unit("uH", "inductance", '0.000001')
unit("Wb weber Vs", "magnetic_flux", 1); unit("T tesla", "magnetic_flux_density", 1); unit("mT", "magnetic_flux_density", '.001'); unit("uT", "magnetic_flux_density", '0.000001')
unit("mol mole", "amount", 1); unit("mmol", "amount", '.001'); unit("umol", "amount", '0.000001')
unit("bit", "data", 1); unit("byte", "data", 8); unit("kB", "data", 8000); unit("KiB", "data", 8192); unit("MB", "data", 8000000); unit("MiB", "data", 8388608); unit("GB", "data", 8000000000)
unit("rad", "angle", 1); UNITS["deg"] = ("angle", s.pi/180, 0); UNITS["grad"] = ("angle", s.pi/200, 0)

CONSTANTS = {
    "c0": ("Speed of light", "299792458", "m/s", True),
    "hP": ("Planck constant", "6.62607015e-34", "J s", True),
    "hbar": ("Reduced Planck constant", None, "J s", True),
    "G": ("Newtonian gravitational constant", "6.67430e-11", "m³ kg⁻¹ s⁻²", False),
    "qe": ("Elementary charge", "1.602176634e-19", "C", True),
    "NA": ("Avogadro constant", "6.02214076e23", "mol⁻¹", True),
    "kB0": ("Boltzmann constant", "1.380649e-23", "J/K", True),
    "me": ("Electron mass", "9.1093837139e-31", "kg", False),
    "mp0": ("Proton mass", "1.67262192595e-27", "kg", False),
    "epsilon0": ("Vacuum electric permittivity", "8.8541878188e-12", "F/m", False),
    "mu0": ("Vacuum magnetic permeability", "1.25663706127e-6", "H/m", False),
    "Z0": ("Vacuum characteristic impedance", "376.730313412", "ohm", False),
    "sigmaSB": ("Stefan-Boltzmann constant", "5.670374419e-8", "W/(m^2 K^4)", False),
}

def require(condition, message):
    if not condition:
        raise MathError(message)

# arcsin/arccos/arctan(및 쌍곡선 변형) 별칭을 정식 asin 계열 이름으로 정규화한다.
CANONICAL_FUNCTION_ALIASES = {
    "arcsin": "asin",
    "arccos": "acos",
    "arctan": "atan",
    "arctan2": "atan2",
    "arcsinh": "asinh",
    "arsinh": "asinh",
    "arccosh": "acosh",
    "arcosh": "acosh",
    "arctanh": "atanh",
    "artanh": "atanh",
    "normalcdf": "normcdf",
    "normalpdf": "normpdf",
}

def canonical_function_name(name):
    """Return the canonical builtin name for a user-typed function alias."""
    return CANONICAL_FUNCTION_ALIASES.get(name, CANONICAL_FUNCTION_ALIASES.get(name.lower(), name))

def matrix(a):
    if isinstance(a, s.MatrixBase): return a
    require(isinstance(a, (list, tuple)), "Expected a vector or matrix")
    require(len(a) <= 32 and all(not isinstance(row,(list,tuple)) or len(row)<=32 for row in a), "Matrix size limit: 32 × 32")
    return s.Matrix(a)

def flatten(a):
    return list(a) if isinstance(a, (list, tuple, s.MatrixBase, s.Tuple)) else [a]

def dms_parts(value):
    """Return normalized [degrees, minutes, seconds] for a real numeric value."""
    require(getattr(value, "is_number", False) and not value.has(s.I), "DMS conversion requires a real numeric value")
    magnitude=s.Abs(value)
    whole=s.floor(magnitude)
    minutes=s.floor((magnitude-whole)*60)
    seconds=s.simplify((magnitude-whole-minutes/60)*3600)
    return [s.sign(value)*whole,minutes,seconds]

def coordinates(value):
    require(isinstance(value, (list, tuple)) and value and all(isinstance(item, s.Symbol) for item in value),
            "Provide a non-empty list of variables")
    require(len(set(value))==len(value), "Coordinate variables must be distinct")
    return tuple(value)

def numeric_derivative(expression, variable, point, precision, step=None):
    """A high-precision central difference with Richardson extrapolation.

    This deliberately does not use a symbolic derivative.  It makes nderivative
    useful for expressions that are numeric functions but have no convenient
    closed-form derivative, while retaining the calculator's exact-input model.
    """
    require(getattr(point, "is_number", False) and not point.has(s.I),
            "nderivative requires a real numeric point")
    if step is None:
        step=s.Rational(10)**(-max(8, min(80, (precision+5)//3)))
    else:
        require(getattr(step, "is_number", False) and step>0, "Derivative step must be positive")
    def central(h):
        return s.N((expression.subs(variable, point+h)-expression.subs(variable, point-h))/(2*h), precision+10)
    coarse=central(step)
    fine=central(step/2)
    result=(4*fine-coarse)/3
    finer=central(step/4)
    result=(16*((4*finer-fine)/3)-result)/15
    require(not result.has(s.nan, s.zoo) and result.is_finite is not False,
            "Numerical differentiation failed")
    return s.N(result, precision)

def discrete_fourier(values, inverse=False):
    values=flatten(values)
    require(1<=len(values)<=256, "FFT length must be between 1 and 256")
    count=len(values); sign=1 if inverse else -1
    divisor=count if inverse else 1
    return [s.simplify(sum((values[index]*s.exp(sign*2*s.pi*s.I*s.Rational(output*index,count)) for index in range(count)), s.Integer(0))/divisor)
            for output in range(count)]

def ode_equation(value):
    if isinstance(value, Relational):
        require(isinstance(value, s.Equality), "Differential equations must use equality")
        return value
    return s.Eq(value, 0)

def initial_conditions(value, dependent, independent):
    items=value if isinstance(value, (list, tuple)) else [value]
    result={}
    for item in items:
        require(isinstance(item, Relational) and isinstance(item, s.Equality),
                "Initial conditions must be equations")
        lhs=item.lhs
        if isinstance(lhs, AppliedUndef):
            require(lhs.args and lhs.args[0].is_number,
                    "Initial conditions need a numeric independent-variable value")
            point=lhs.args[0]; key=lhs
        elif isinstance(lhs, s.Derivative):
            require(lhs.variables==(independent,) and lhs.point and lhs.point[0].is_number,
                    "Derivative initial conditions need a numeric point")
            point=lhs.point[0]; key=s.Subs(lhs, independent, point)
        else:
            require(lhs==dependent, "Initial-condition left side is not the dependent function")
            point=s.Integer(0); key=dependent
        value=item.rhs.subs(independent, point) if independent in item.rhs.free_symbols else item.rhs
        result[key]=value
    return result

# --- Distributions, statistical tests and finance ---------------------------
# Compact closed forms stay symbolic (erf, binomial coefficients, exp) while the
# remaining cumulative probabilities use mpmath at the working precision.  The
# tests reuse the same tail probabilities, and the finance functions follow the
# TVM cash-flow convention: money received is positive, money paid is negative,
# and rates are per payment period.

def _real_value(value, message):
    require(getattr(value, "is_number", False) and not value.has(s.I), message)
    return value

def _real_or_infinite(value, message):
    if value in (s.oo, -s.oo): return value
    return _real_value(value, message)

def _positive(value, message):
    require(getattr(value, "is_number", False) and value > 0, message)
    return value

def _mpf(value, digits):
    if isinstance(value, s.Rational): return mp.mpf(int(value.p))/mp.mpf(int(value.q))
    return mp.mpf(str(s.N(value, digits + 10)))

def _mp_result(value, engine):
    if value == 0: value = mp.mpf(0)
    return s.Float(str(value), engine.precision)

def _normal_cdf(x):
    return (1 + mp.erf(x/mp.sqrt(2)))/2

def _normal_sf(x):
    return mp.erfc(x/mp.sqrt(2))/2

def _t_tail(t, df):
    """P(T > t) for t >= 0, the accurate half of the symmetric t tail."""
    return mp.betainc(df/2, mp.mpf(1)/2, 0, df/(df + t*t), regularized=True)/2

def _t_cdf(t, df):
    return _t_tail(-t, df) if t <= 0 else 1 - _t_tail(t, df)

def _t_sf(t, df):
    return _t_tail(t, df) if t >= 0 else 1 - _t_tail(-t, df)

def _chisq_cdf(x, df):
    if x <= 0: return mp.mpf(0)
    return mp.gammainc(df/2, 0, x/2, regularized=True)

def _chisq_sf(x, df):
    if x <= 0: return mp.mpf(1)
    return mp.gammainc(df/2, x/2, mp.inf, regularized=True)

def _f_cdf(x, d1, d2):
    if x <= 0: return mp.mpf(0)
    return mp.betainc(d1/2, d2/2, 0, d1*x/(d1*x + d2), regularized=True)

def _f_sf(x, d1, d2):
    if x <= 0: return mp.mpf(1)
    return mp.betainc(d2/2, d1/2, 0, d2/(d2 + d1*x), regularized=True)

def _bound_survival(sf, bound, digits):
    if bound == s.oo: return mp.mpf(0)
    if bound == -s.oo: return mp.mpf(1)
    return sf(_mpf(bound, digits))

def _quantile(cdf, probability, engine, lower, upper):
    """Invert a monotone cumulative distribution with bracket expansion and bisection."""
    target = _mpf(probability, engine.precision)
    low, high = mp.mpf(lower), mp.mpf(upper)
    width = high - low
    for _ in range(200):
        if cdf(low) < target <= cdf(high): break
        width *= 2
        if cdf(low) >= target: high = low; low = high - width
        else: low = high; high = low + width
    else:
        raise MathError("Numeric quantile did not converge")
    tolerance = mp.mpf(10)**(-(engine.precision + 4))*max(1, abs(low), abs(high))
    while high - low > tolerance:
        middle = (low + high)/2
        if cdf(middle) < target: low = middle
        else: high = middle
    return (low + high)/2

def _tail_probability(sf, statistic, tail):
    """Two-sided p value by default; sf(x) is P(X >= x) for a symmetric distribution."""
    if tail == "left": return min(mp.mpf(1), sf(-statistic))
    if tail == "right": return sf(statistic)
    return min(mp.mpf(1), 2*sf(abs(statistic)))

def _tail_argument(a, nodes):
    if nodes and isinstance(nodes[-1], dict) and nodes[-1].get("kind") == "symbol" and nodes[-1].get("value") in ("left", "right", "both"):
        return nodes[-1]["value"], a[:-1]
    return "both", a

def _mode_argument(a, nodes):
    if nodes and isinstance(nodes[-1], dict) and nodes[-1].get("kind") == "symbol" and nodes[-1].get("value") in ("begin", "end"):
        return nodes[-1]["value"], a[:-1]
    return "end", a

def _sample_statistics(data):
    values = flatten(data)
    require(len(values) >= 2, "Enter at least two data values")
    for value in values: _real_value(value, "Sample values must be real numbers")
    n = s.Integer(len(values))
    mean = s.Add(*values)/n
    sd = s.sqrt(s.Add(*[(value - mean)**2 for value in values])/(n - 1))
    require(sd > 0, "The sample needs some variation")
    return mean, sd, n

def _data_center(samples):
    values = flatten(samples)
    require(values, "Enter at least one data value")
    for value in values: _real_value(value, "Sample values must be real numbers")
    return s.Add(*values)/s.Integer(len(values)), s.Integer(len(values))

def _confidence_level(value):
    _real_value(value, "The confidence level must be a number")
    level = value/100 if value > 1 else value
    require(0 < level < 1, "The confidence level must be between 0 and 1, or between 1 and 100 percent")
    return level

def _binom_term(n, p, k):
    return s.binomial(n, k)*p**k*(1 - p)**(n - k)

def _tvm_value(n, i, pv, pmt, fv, begin):
    """Value of pv*(1+i)^n + pmt*((1+i)^n - 1)/i + fv for mpmath numbers."""
    if i == 0: return pv + pmt*n + fv
    growth = (1 + i)**n
    return pv*growth + pmt*(1 + i if begin else 1)*(growth - 1)/i + fv

def distribution_value(engine, name, a):
    digits = engine.precision
    if name == "normpdf":
        require(len(a) in (1, 3), "normpdf takes x, or x with μ and σ")
        x, mu, sigma = (a[0], s.Integer(0), s.Integer(1)) if len(a) == 1 else a
        _real_value(x, "normpdf requires numeric arguments")
        _real_value(mu, "normpdf requires numeric arguments")
        _positive(sigma, "Standard deviation must be positive")
        z = (x - mu)/sigma
        return s.exp(-z**2/2)/(sigma*s.sqrt(2*s.pi))
    if name == "normcdf":
        require(len(a) in (1, 2, 4), "normcdf takes one bound, two bounds, or two bounds with μ and σ")
        if len(a) == 1:
            x = _real_or_infinite(a[0], "normcdf requires a numeric bound")
            if x == s.oo: return s.Integer(1)
            if x == -s.oo: return s.Integer(0)
            return (s.erf(x/s.sqrt(2)) + 1)/2
        low = _real_or_infinite(a[0], "normcdf requires real bounds")
        high = _real_or_infinite(a[1], "normcdf requires real bounds")
        require(high >= low, "The lower bound must not be above the upper bound")
        if len(a) == 2: mu, sigma = s.Integer(0), s.Integer(1)
        else: mu, sigma = _real_value(a[2], "normcdf requires a numeric μ"), _positive(a[3], "Standard deviation must be positive")
        return (s.erf((high - mu)/(sigma*s.sqrt(2))) - s.erf((low - mu)/(sigma*s.sqrt(2))))/2
    if name == "invnorm":
        require(len(a) in (1, 3), "invnorm takes a probability, or a probability with μ and σ")
        p = a[0]
        mu, sigma = (s.Integer(0), s.Integer(1)) if len(a) == 1 else (a[1], a[2])
        require(getattr(p, "is_number", False) and not p.has(s.I) and 0 < p < 1, "invnorm requires a probability between 0 and 1")
        _real_value(mu, "invnorm requires a numeric μ")
        _positive(sigma, "Standard deviation must be positive")
        if p == s.Rational(1, 2): return mu
        with mp.workdps(digits + 10):
            return _mp_result(_mpf(mu, digits) + _mpf(sigma, digits)*_quantile(_normal_cdf, p, engine, -2, 2), engine)
    if name == "tpdf":
        require(len(a) == 2, "tpdf takes x and the degrees of freedom")
        x, df = a
        _real_value(x, "tpdf requires numeric arguments")
        _positive(df, "Degrees of freedom must be positive")
        return s.gamma((df + 1)/2)/(s.sqrt(df*s.pi)*s.gamma(df/2))*(1 + x**2/df)**(-(df + 1)/2)
    if name == "tcdf":
        require(len(a) in (2, 3), "tcdf takes a bound and df, or two bounds and df")
        if len(a) == 2:
            x = _real_or_infinite(a[0], "tcdf requires a real bound")
            df = _positive(a[1], "Degrees of freedom must be positive")
            if x == s.oo: return s.Integer(1)
            if x == -s.oo: return s.Integer(0)
            with mp.workdps(digits + 10): return _mp_result(_t_cdf(_mpf(x, digits), _mpf(df, digits)), engine)
        low = _real_or_infinite(a[0], "tcdf requires real bounds")
        high = _real_or_infinite(a[1], "tcdf requires real bounds")
        df = _positive(a[2], "Degrees of freedom must be positive")
        require(high >= low, "The lower bound must not be above the upper bound")
        with mp.workdps(digits + 10):
            df_mp = _mpf(df, digits)
            def survival(bound): return _bound_survival(lambda x: _t_sf(x, df_mp), bound, digits)
            return _mp_result(survival(low) - survival(high), engine)
    if name == "invt":
        require(len(a) == 2, "invt takes a probability and the degrees of freedom")
        p, df = a
        require(getattr(p, "is_number", False) and not p.has(s.I) and 0 < p < 1, "invt requires a probability between 0 and 1")
        _positive(df, "Degrees of freedom must be positive")
        if p == s.Rational(1, 2): return s.Integer(0)
        with mp.workdps(digits + 10):
            df_mp = _mpf(df, digits)
            return _mp_result(_quantile(lambda x: _t_cdf(x, df_mp), p, engine, -2, 2), engine)
    if name == "chi2pdf":
        require(len(a) == 2, "chi2pdf takes x and the degrees of freedom")
        x, df = a
        _real_value(x, "chi2pdf requires numeric arguments")
        _positive(df, "Degrees of freedom must be positive")
        require(x >= 0, "chi2pdf is defined for x ≥ 0")
        require(x > 0 or df >= 2, "chi2pdf is not finite at x = 0 below df = 2")
        return x**(df/2 - 1)*s.exp(-x/2)/(2**(df/2)*s.gamma(df/2))
    if name == "chi2cdf":
        require(len(a) in (2, 3), "chi2cdf takes a bound and df, or two bounds and df")
        if len(a) == 2:
            x = _real_or_infinite(a[0], "chi2cdf requires a real bound")
            df = _positive(a[1], "Degrees of freedom must be positive")
            if x == s.oo: return s.Integer(1)
            if x == -s.oo: return s.Integer(0)
            with mp.workdps(digits + 10): return _mp_result(_chisq_cdf(_mpf(x, digits), _mpf(df, digits)), engine)
        low = _real_or_infinite(a[0], "chi2cdf requires real bounds")
        high = _real_or_infinite(a[1], "chi2cdf requires real bounds")
        df = _positive(a[2], "Degrees of freedom must be positive")
        require(high >= low, "The lower bound must not be above the upper bound")
        with mp.workdps(digits + 10):
            df_mp = _mpf(df, digits)
            def survival(bound): return _bound_survival(lambda x: _chisq_sf(x, df_mp), bound, digits)
            return _mp_result(survival(low) - survival(high), engine)
    if name == "fpdf":
        require(len(a) == 3, "fpdf takes x and the two degrees of freedom")
        x, d1, d2 = a
        _real_value(x, "fpdf requires numeric arguments")
        _positive(d1, "Degrees of freedom must be positive")
        _positive(d2, "Degrees of freedom must be positive")
        require(x > 0, "fpdf is defined for x > 0")
        return s.sqrt((d1*x)**d1*d2**d2/(d1*x + d2)**(d1 + d2))/(x*s.beta(d1/2, d2/2))
    if name == "fcdf":
        require(len(a) in (3, 4), "fcdf takes a bound and two degrees of freedom, or two bounds")
        if len(a) == 3:
            x = _real_or_infinite(a[0], "fcdf requires a real bound")
            d1 = _positive(a[1], "Degrees of freedom must be positive")
            d2 = _positive(a[2], "Degrees of freedom must be positive")
            if x == s.oo: return s.Integer(1)
            if x == -s.oo: return s.Integer(0)
            with mp.workdps(digits + 10): return _mp_result(_f_cdf(_mpf(x, digits), _mpf(d1, digits), _mpf(d2, digits)), engine)
        low = _real_or_infinite(a[0], "fcdf requires real bounds")
        high = _real_or_infinite(a[1], "fcdf requires real bounds")
        d1 = _positive(a[2], "Degrees of freedom must be positive")
        d2 = _positive(a[3], "Degrees of freedom must be positive")
        require(high >= low, "The lower bound must not be above the upper bound")
        with mp.workdps(digits + 10):
            d1_mp, d2_mp = _mpf(d1, digits), _mpf(d2, digits)
            def survival(bound): return _bound_survival(lambda x: _f_sf(x, d1_mp, d2_mp), bound, digits)
            return _mp_result(survival(low) - survival(high), engine)
    if name in ("binompdf", "binomcdf"):
        require(len(a) in (2, 3), name + " takes n, p and optionally k")
        n, p = a[0], a[1]
        require(n.is_Integer and 0 < n <= 1000, "binom n must be an integer from 1 to 1000")
        require(getattr(p, "is_number", False) and 0 <= p <= 1, "binom p must be a probability")
        count = int(n)
        if len(a) == 3:
            k = a[2]
            require(k.is_Integer and 0 <= k <= n, "binom k must be an integer from 0 to n")
            if name == "binompdf": return _binom_term(count, p, int(k))
            return s.Add(*[_binom_term(count, p, index) for index in range(int(k) + 1)])
        require(count <= 100, "Use a k value for a single probability when n is above 100")
        probabilities = [_binom_term(count, p, index) for index in range(count + 1)]
        if name == "binompdf": return probabilities
        running, cumulative = s.Integer(0), []
        for term in probabilities:
            running = running + term
            cumulative.append(running)
        return cumulative
    if name in ("poissonpdf", "poissoncdf"):
        require(len(a) == 2, name + " takes the mean μ and k")
        mu, k = a
        _positive(mu, "The Poisson mean must be positive")
        require(k.is_Integer and 0 <= k <= 10000, "Poisson k must be an integer from 0 to 10000")
        count = int(k)
        if name == "poissonpdf": return s.exp(-mu)*mu**count/s.factorial(count)
        if count <= 200: return s.exp(-mu)*s.Add(*[mu**index/s.factorial(index) for index in range(count + 1)])
        with mp.workdps(digits + 10):
            return _mp_result(mp.gammainc(count + 1, _mpf(mu, digits), mp.inf, regularized=True), engine)
    if name in ("geometpdf", "geometcdf"):
        require(len(a) == 2, name + " takes p and k")
        p, k = a
        require(getattr(p, "is_number", False) and 0 < p <= 1, "geomet p must be a probability above 0")
        require(k.is_Integer and 1 <= k <= 10**6, "geomet k must be a positive integer")
        if name == "geometpdf": return (1 - p)**(int(k) - 1)*p
        return 1 - (1 - p)**int(k)
    raise MathError("Unknown distribution: " + name)

def statistical_test(engine, name, a, nodes):
    digits = engine.precision
    tail, args = _tail_argument(a, nodes)
    if tail != "both": engine.note = "One-tailed probability (" + tail + " tail)."
    if name == "ttest":
        require(len(args) in (2, 4), "ttest takes μ0 and data, or μ0, x̄, s and n")
        mu0 = _real_value(args[0], "ttest requires a numeric μ0")
        if len(args) == 2:
            require(isinstance(args[1], (list, tuple)), "ttest data must be a list")
            mean, sd, n = _sample_statistics(args[1])
        else:
            mean, sd, n = args[1], args[2], args[3]
            _real_value(mean, "ttest requires numeric summary values")
            _positive(sd, "The sample SD must be positive")
            require(n.is_Integer and n >= 2, "ttest n must be an integer of at least 2")
        with mp.workdps(digits + 10):
            statistic = (_mpf(mean, digits) - _mpf(mu0, digits))/(_mpf(sd, digits)/mp.sqrt(_mpf(n, digits)))
            probability = _tail_probability(lambda t: _t_sf(t, _mpf(n - 1, digits)), statistic, tail)
            return {"t": _mp_result(statistic, engine), "df": s.Integer(n - 1), "p value": _mp_result(probability, engine),
                    "sample mean": mean, "sample SD": sd, "n": s.Integer(n)}
    if name == "ztest":
        require(len(args) in (3, 4), "ztest takes μ0, σ and data, or μ0, σ, x̄ and n")
        mu0 = _real_value(args[0], "ztest requires a numeric μ0")
        sigma = _positive(args[1], "σ must be positive")
        if len(args) == 3:
            require(isinstance(args[2], (list, tuple)), "ztest data must be a list")
            mean, n = _data_center(args[2])
        else:
            mean, n = args[2], args[3]
            _real_value(mean, "ztest requires numeric summary values")
            require(n.is_Integer and n >= 1, "ztest n must be a positive integer")
        with mp.workdps(digits + 10):
            statistic = (_mpf(mean, digits) - _mpf(mu0, digits))/(_mpf(sigma, digits)/mp.sqrt(_mpf(n, digits)))
            probability = _tail_probability(_normal_sf, statistic, tail)
            return {"z": _mp_result(statistic, engine), "p value": _mp_result(probability, engine),
                    "sample mean": mean, "n": s.Integer(n)}
    if name == "chi2test":
        require(len(args) == 2, "chi2test takes observed and expected counts")
        observed, expected = flatten(args[0]), flatten(args[1])
        require(len(observed) == len(expected) and len(observed) >= 2, "chi2test needs two lists of equal length with at least two counts")
        for value in observed + expected: _real_value(value, "Counts must be real numbers")
        require(all(value > 0 for value in expected), "Expected counts must be positive")
        statistic = s.Add(*[((o - e)**2)/e for o, e in zip(observed, expected)])
        df = len(observed) - 1
        with mp.workdps(digits + 10):
            probability = _chisq_sf(_mpf(statistic, digits), _mpf(s.Integer(df), digits))
            return {"chi-square": statistic, "df": s.Integer(df), "p value": _mp_result(probability, engine)}
    if name == "anova":
        require(len(args) >= 2, "anova takes two or more data lists")
        groups = []
        for group in args:
            require(isinstance(group, (list, tuple)), "anova arguments must be data lists")
            values = flatten(group)
            require(len(values) >= 2, "Each anova group needs at least two values")
            for value in values: _real_value(value, "Sample values must be real numbers")
            groups.append(values)
        total = sum(len(group) for group in groups)
        means = [s.Add(*group)/s.Integer(len(group)) for group in groups]
        grand = s.Add(*[value for group in groups for value in group])/s.Integer(total)
        between = s.Add(*[s.Integer(len(group))*(mean - grand)**2 for group, mean in zip(groups, means)])
        within = s.Add(*[s.Add(*[(value - mean)**2 for value in group]) for group, mean in zip(groups, means)])
        require(within != 0, "anova needs variation inside the groups")
        count = len(groups)
        statistic = (between/(count - 1))/(within/(total - count))
        with mp.workdps(digits + 10):
            probability = _f_sf(_mpf(statistic, digits), _mpf(s.Integer(count - 1), digits), _mpf(s.Integer(total - count), digits))
            return {"F": statistic, "df numerator": s.Integer(count - 1), "df denominator": s.Integer(total - count), "p value": _mp_result(probability, engine)}
    if name in ("tinterval", "zinterval"):
        if name == "tinterval":
            require(len(args) in (2, 4), "tinterval takes a confidence level and data, or a level, x̄, s and n")
            level = _confidence_level(args[0])
            if len(args) == 2:
                require(isinstance(args[1], (list, tuple)), "tinterval data must be a list")
                mean, sd, n = _sample_statistics(args[1])
            else:
                mean, sd, n = args[1], args[2], args[3]
                _real_value(mean, "tinterval requires numeric summary values")
                _positive(sd, "The sample SD must be positive")
                require(n.is_Integer and n >= 2, "tinterval n must be an integer of at least 2")
            with mp.workdps(digits + 10):
                critical = _quantile(lambda x: _t_cdf(x, _mpf(n - 1, digits)), (1 + level)/2, engine, -2, 2)
                margin = critical*_mpf(sd, digits)/mp.sqrt(_mpf(n, digits))
                center = _mpf(mean, digits)
                return {"confidence interval": [_mp_result(center - margin, engine), _mp_result(center + margin, engine)],
                        "sample mean": mean, "sample SD": sd, "n": s.Integer(n), "df": s.Integer(n - 1)}
        require(len(args) in (3, 4), "zinterval takes a confidence level, σ and data, or a level, σ, x̄ and n")
        level = _confidence_level(args[0])
        sigma = _positive(args[1], "σ must be positive")
        if len(args) == 3:
            require(isinstance(args[2], (list, tuple)), "zinterval data must be a list")
            mean, n = _data_center(args[2])
        else:
            mean, n = args[2], args[3]
            _real_value(mean, "zinterval requires numeric summary values")
            require(n.is_Integer and n >= 1, "zinterval n must be a positive integer")
        with mp.workdps(digits + 10):
            critical = _quantile(_normal_cdf, (1 + level)/2, engine, -2, 2)
            margin = critical*_mpf(sigma, digits)/mp.sqrt(_mpf(n, digits))
            center = _mpf(mean, digits)
            return {"confidence interval": [_mp_result(center - margin, engine), _mp_result(center + margin, engine)],
                    "sample mean": mean, "n": s.Integer(n)}
    raise MathError("Unknown statistical test: " + name)

def finance_value(engine, name, a, nodes):
    digits = engine.precision
    mode, args = _mode_argument(a, nodes)
    begin = mode == "begin"
    if name == "npv":
        require(len(args) in (2, 3), "npv takes a rate and a cash-flow list, or a rate, initial flow and list")
        rate = _real_value(args[0], "npv requires a numeric rate")
        require(rate > -1, "The rate must be greater than -100%")
        if len(args) == 2:
            require(isinstance(args[1], (list, tuple)), "npv needs a cash-flow list")
            flows = list(args[1])
        else:
            require(isinstance(args[2], (list, tuple)), "npv needs a cash-flow list")
            flows = [args[1]] + list(args[2])
        require(2 <= len(flows) <= 500, "npv needs between 2 and 500 cash flows")
        for flow in flows: _real_value(flow, "Cash flows must be numbers")
        return s.Add(*[flow/(1 + rate)**index for index, flow in enumerate(flows)])
    if name == "irr":
        require(len(args) in (1, 2), "irr takes a cash-flow list, or an initial flow and list")
        if len(args) == 1:
            require(isinstance(args[0], (list, tuple)), "irr needs a cash-flow list")
            flows = list(args[0])
        else:
            require(isinstance(args[1], (list, tuple)), "irr needs a cash-flow list")
            flows = [args[0]] + list(args[1])
        require(2 <= len(flows) <= 100, "irr needs between 2 and 100 cash flows")
        for flow in flows: _real_value(flow, "Cash flows must be numbers")
        if s.Add(*flows) == 0: return s.Integer(0)
        with mp.workdps(digits + 15):
            coefficients = [_mpf(flow, digits + 5) for flow in flows]
            while coefficients and coefficients[0] == 0: coefficients.pop(0)
            require(len(coefficients) >= 2, "irr needs at least one change of sign")
            candidates = []
            for root in mp.polyroots(coefficients, maxsteps=200, extraprec=10):
                if abs(mp.im(root)) > mp.mpf(10)**(-max(digits - 6, 6))*(1 + abs(root)): continue
                rate = mp.re(root) - 1
                if rate > -1: candidates.append(rate)
            require(candidates, "No rate of return solves this cash-flow list")
            if len(candidates) > 1: engine.note = "Several rates solve this cash-flow list; the value closest to zero is shown."
            return _mp_result(min(candidates, key=abs), engine)
    if name in ("tvmfv", "tvmpv", "tvmpmt", "tvmn"):
        orders = {"tvmfv": ("n", "i", "pv", "pmt"), "tvmpv": ("n", "i", "pmt", "fv"),
                  "tvmpmt": ("n", "i", "pv", "fv"), "tvmn": ("i", "pv", "pmt", "fv")}
        require(len(args) == 4, name + " takes " + ", ".join(orders[name]) + ", and optionally begin or end")
        values = dict(zip(orders[name], args))
        for value in values.values(): _real_value(value, name + " requires numeric arguments")
        require(values["i"] > -1, "The interest rate must be greater than -100%")
        if "n" in values: require(values["n"] >= 0, "The number of periods must not be negative")
        with mp.workdps(digits + 10):
            n = _mpf(values["n"], digits) if "n" in values else None
            i = _mpf(values["i"], digits)
            pv = _mpf(values.get("pv", s.Integer(0)), digits)
            pmt = _mpf(values.get("pmt", s.Integer(0)), digits)
            fv = _mpf(values.get("fv", s.Integer(0)), digits)
            adjustment = 1 + i if begin else 1
            if name == "tvmfv":
                answer = -_tvm_value(n, i, pv, pmt, mp.mpf(0), begin)
            elif name == "tvmpv":
                if i == 0: answer = -(pmt*n + fv)
                else: answer = -_tvm_value(n, i, mp.mpf(0), pmt, fv, begin)/(1 + i)**n
            elif name == "tvmpmt":
                require(n > 0, "The number of periods must be positive")
                if i == 0:
                    answer = -(pv + fv)/n
                else:
                    answer = -(pv*(1 + i)**n + fv)/_tvm_value(n, i, mp.mpf(0), mp.mpf(1), mp.mpf(0), begin)
            else:
                if i == 0:
                    require(pmt != 0, "Zero interest needs a non-zero payment")
                    answer = -(pv + fv)/pmt
                else:
                    payment = pmt*adjustment
                    require(i*pv + payment != 0, "No number of periods solves this schedule")
                    ratio = (payment - i*fv)/(i*pv + payment)
                    require(ratio > 0, "No number of periods solves this schedule")
                    answer = mp.log(ratio)/mp.log(1 + i)
        return _mp_result(answer, engine)
    if name == "tvmrate":
        require(len(args) == 4, "tvmrate takes n, pv, pmt and fv, and optionally begin or end")
        n, pv, pmt, fv = args
        for value in args: _real_value(value, "tvmrate requires numeric arguments")
        require(n > 0, "The number of periods must be positive")
        if pv + pmt*n + fv == 0: return s.Integer(0)
        with mp.workdps(digits + 10):
            n_mp, pv_mp, pmt_mp, fv_mp = _mpf(n, digits), _mpf(pv, digits), _mpf(pmt, digits), _mpf(fv, digits)
            def equation(rate): return _tvm_value(n_mp, rate, pv_mp, pmt_mp, fv_mp, begin)
            rates = [mp.mpf(0)]
            for k in range(1, 31): rates += [-1 + mp.mpf(2)**(-k), -mp.mpf(2)**(-k)]
            for k in range(1, 16): rates += [mp.mpf(10)**(-k), mp.mpf(2)**k]
            rates = sorted(set(rates))
            roots, previous = [], None
            for rate in rates:
                current = equation(rate)
                if current == 0: roots.append(rate)
                elif previous is not None and current*previous[1] < 0:
                    low, high = previous[0], rate
                    low_value = previous[1]
                    tolerance = mp.mpf(10)**(-(digits + 4))*(1 + abs(low))
                    for _ in range(400):
                        middle = (low + high)/2
                        middle_value = equation(middle)
                        if low_value*middle_value <= 0: high = middle
                        else: low, low_value = middle, middle_value
                        if high - low <= tolerance: break
                    roots.append((low + high)/2)
                previous = (rate, current)
            require(roots, "No interest rate solves this payment schedule")
            unique = []
            for root in sorted(roots):
                if not unique or root - unique[-1] > mp.mpf(10)**(-(digits + 4))*(1 + abs(root)): unique.append(root)
            if len(unique) > 1: engine.note = "Several rates solve this schedule; the value closest to zero is shown."
            return _mp_result(min(unique, key=abs), engine)
    if name == "amort":
        require(len(args) in (3, 4), "amort takes i, pv and n, and optionally k")
        i, pv, n = args[0], args[1], args[2]
        for value in args[:3]: _real_value(value, "amort requires numeric arguments")
        require(i > -1, "The interest rate must be greater than -100%")
        require(n.is_Integer and n > 0, "The number of payments must be a positive integer")
        if len(args) == 4:
            k = args[3]
            require(k.is_Integer and 0 <= k <= n, "The payment index must be an integer from 0 to n")
        else: k = n
        with mp.workdps(digits + 10):
            i_mp, principal = _mpf(i, digits), _mpf(pv, digits)
            n_mp, k_mp = _mpf(n, digits), _mpf(k, digits)
            growth = (1 + i_mp)**n_mp
            if i_mp == 0: payment = -principal/n_mp
            else: payment = -principal*i_mp*growth/((1 + i_mp if begin else 1)*(growth - 1))
            balance = _tvm_value(k_mp, i_mp, principal, payment, mp.mpf(0), begin)
            if abs(balance) <= mp.mpf(10)**(-(max(digits, 12) - 3))*max(1, abs(principal)): balance = mp.mpf(0)
            interest = -payment*k_mp - (principal - balance)
            return {"payment": _mp_result(payment, engine), "payments": s.Integer(k),
                    "balance": _mp_result(balance, engine), "principal paid": _mp_result(principal - balance, engine),
                    "interest paid": _mp_result(interest, engine)}
    raise MathError("Unknown finance function: " + name)
class Engine:
    def __init__(self, request):
        self.request = request
        self.precision = max(3, min(200, int(request.get("precision", 30))))
        # Display digits limit only what the result view shows; the numeric work keeps self.precision.
        self.display_digits = max(1, min(self.precision, int(request.get("displayDigits", self.precision))))
        self.angle = request.get("angle", "RAD")
        self.variables = request.get("variables", {})
        self.functions = request.get("functions", {})
        self.resolving = set()
        self.symbols = {}
        self.visited = 0
        self.note = ""
        self.conditions = []
        self.bindings = {}
        self.allow_sequence_calls = False
        self.assumptions = request.get("assumptions", {})
    def symbol(self, name):
        if name not in self.symbols:
            options = {k: True for k in self.assumptions.get(name, []) if k in ("real", "positive", "negative", "integer", "nonzero")}
            self.symbols[name] = s.Symbol(name, **options)
        return self.symbols[name]
    def has_explicit_angle(self, node, seen=()):
        # 사용자가 쓴 각도 표시(π, °, ʳ, ᵍ)만 인정한다. 안쪽 DEG/GRAD 변환이 값을 만들며
        # 끼워 넣은 π를 명시적 라디안으로 오인하면 중첩 호출에서 모드 변환이 누락된다.
        if not isinstance(node, dict): return False
        if node.get("value") in ("degree", "pi", "rad", "gradian"): return True
        if node.get("kind") == "symbol":
            name = node.get("value")
            stored = self.variables.get(name) if isinstance(self.variables, dict) else None
            return (name not in self.bindings and name not in seen and name not in ("e", "i", "I", "oo", "true", "false")
                    and name not in CONSTANTS and isinstance(stored, dict) and self.has_explicit_angle(stored, seen + (name,)))
        return any(self.has_explicit_angle(child, seen) for child in node.get("args", []))
    def build(self, node, depth=0):
        self.visited += 1
        require(depth < 100 and self.visited <= 12000, "Expression complexity limit")
        kind, value = node["kind"], node.get("value", "")
        args = node.get("args", [])
        build = lambda a: self.build(a, depth + 1)
        if kind == "number":
            require(len(value) <= 1000, "Number too large")
            if "e" in value.lower(): require(abs(int(value.lower().split("e")[1])) <= 10000, "Decimal exponent limit: 10000")
            number = s.Rational(value)
            require(abs(number.p).bit_length() < 100000 and number.q.bit_length() < 100000, "Number size limit")
            return number
        if kind == "symbol":
            if value in self.bindings: return self.bindings[value]
            constants = {"pi": s.pi, "e": s.E, "i": s.I, "I": s.I, "oo": s.oo, "true": s.true, "false": s.false}
            if value in constants: return constants[value]
            if value in CONSTANTS:
                entry=CONSTANTS[value]
                if not entry[3]:
                    self.note="Uses a measured CODATA 2022 value with published precision."
                    return s.Float(entry[1],12)
                return s.Rational(entry[1]) if value != "hbar" else s.Rational(CONSTANTS["hP"][1])/(2*s.pi)
            if value in self.variables:
                require(value not in self.resolving, "Cyclic variable definition")
                self.resolving.add(value)
                result = build(self.variables[value])
                self.resolving.remove(value)
                return result
            if value=="Ans": raise MathError("Ans has no reusable result yet")
            return self.symbol(value)
        if kind == "constant":
            return {"pi":s.pi,"E":s.E,"I":s.I,"oo":s.oo,"-oo":-s.oo,"EmptySet":s.S.EmptySet,"True":s.true,"False":s.false}[value]
        if kind == "snapshot_symbol": return self.symbol(value)
        if kind == "float": return s.Float(value,self.precision)
        if kind == "restricted":
            result=build(args[0])
            for guard in args[1:]:
                condition=build(guard)
                require(condition!=s.false,"Domain ERROR: excluded value")
                if condition!=s.true: self.conditions.append(condition)
            return result
        if kind == "quantity": return Quantity(build(args[0]),tuple(node["dimensions"]),node.get("absolute",False))
        if kind == "frozen_call":
            old_angle=self.angle
            self.angle="RAD"
            try: return self.call(value,[build(a) for a in args],args)
            finally: self.angle=old_angle
        if kind == "set": return s.FiniteSet(*(build(a) for a in args))
        if kind == "mapping": return {build(pair["args"][0]):build(pair["args"][1]) for pair in args}
        if kind == "group": return build(args[0])
        if kind == "list": return [build(a) for a in args]
        if kind == "tuple": return tuple(build(a) for a in args)
        if kind == "unary": return (-1 if value == "-" else 1)*build(args[0])
        if kind == "sexagesimal":
            require(len(args) == 3, "DMS input requires degrees, minutes and seconds")
            values=[build(a) for a in args]
            require(all(getattr(item,"is_number",False) and not item.has(s.I) for item in values), "DMS fields must be real numbers")
            return values[0]+values[1]/60+values[2]/3600
        if kind in ("binary", "relation"):
            require(value != ":=", "Use STO for variables or the Variables editor for functions")
            a, b = map(build, args)
            if isinstance(a, list): a = matrix(a)
            if isinstance(b, list): b = matrix(b)
            if value == "+": return a+b
            if value == "-": return a-b
            if value == "*": return a*b
            if value == "∠": return self.call("polar",[a,b],args)
            if value == "/":
                require(b != 0, "Division by zero")
                if getattr(b, "free_symbols", None): self.conditions.append(s.Ne(b, 0, evaluate=False))
                return a/b
            if value == "^":
                if getattr(b, "is_number", False): require(abs(b) <= 10000, "Exponent limit: 10000")
                require(not (a == 0 and b == 0), "Undefined: 0^0")
                return a**b
            if value == "mod": require(b != 0, "Division by zero"); return s.Mod(a,b)
            relations = {"=": s.Eq, "==": s.Eq, "!=": s.Ne, "<": s.Lt, ">": s.Gt, "<=": s.Le, ">=": s.Ge, "->": s.Eq}
            if value in relations: return relations[value](a,b,evaluate=False)
            raise MathError("Unknown operator")
        require(kind == "call", "Unknown AST node")
        # Preserve bound variable identity even if the user stored x previously.
        scoped = value in ("diff", "integrate", "limit", "series", "taylor", "sum", "product", "solve", "nsolve", "nintegrate", "nderivative", "minimum", "maximum", "collect", "subs", "domain", "range", "coeff", "quo", "rem", "resultant", "discriminant", "charpoly", "gradient", "divergence", "curl", "hessian", "jacobian", "laplacian", "dsolve", "desolve", "laplace", "ilaplace", "fourier", "ifourier") and len(args) > 1
        old = self.bindings.copy()
        if value=="solve" and len(args)==1: self.bindings["x"]=self.symbol("x")
        if scoped:
            if value in ("dsolve", "desolve", "laplace", "fourier", "ilaplace", "ifourier"):
                candidates = list(args[1:3])
            else:
                varnode = args[1]
                candidates = varnode.get("args", []) if varnode["kind"] == "list" else [varnode]
                if varnode["kind"] == "tuple": candidates = varnode["args"][:1]
                if varnode["kind"] == "relation": candidates = [varnode["args"][0]]
            for n in candidates:
                if n["kind"] == "symbol": self.bindings[n["value"]] = self.symbol(n["value"])
        try:
            condition_start=len(self.conditions)
            values = [build(a) for a in args]
            if value=="subs" and len(values)==3:
                updated=[condition.subs(values[1],values[2]) for condition in self.conditions[condition_start:]]
                require(all(c!=s.false for c in updated),"Domain ERROR: substitution at an excluded value")
                self.conditions[condition_start:]=[c for c in updated if c!=s.true]
                return values[0].subs(values[1],values[2])
            return self.call(value, values, args)
        finally:
            self.bindings = old
    def call(self, name, a, nodes):
        # arcsin/arccos/arctan 계열 별칭은 사용자 정의 함수가 없을 때만 정식 이름으로 정규화한다.
        if name not in self.functions:
            name = canonical_function_name(name)
        if self.allow_sequence_calls and (name == "u" or name in ("u1", "u2", "u3", "u4", "u5", "u6")):
            require(len(a) == 1, "Sequence references take one integer index")
            return s.Function(name)(a[0])
        if name=="rnd":return s.N(a[0],self.display_digits)
        if name=="eng":return a[0]
        if name=="pol":
            z=a[0]+s.I*a[1];angle=s.arg(z)*{"DEG":180/s.pi,"GRAD":200/s.pi}.get(self.angle,1)
            return [s.Abs(z),angle]
        if name=="rec":
            z=self.call("polar",a,nodes)
            return [s.re(z),s.im(z)]
        if name=="randInt":
            require(len(a)==2 and all(x.is_Integer for x in a) and a[0]<=a[1],"Enter integer lower and upper bounds")
            return s.Integer(random.randint(int(a[0]),int(a[1])))
        if name=="sexagesimal":
            require(len(a) == 3, "DMS input requires degrees, minutes and seconds")
            return a[0]+a[1]/60+a[2]/3600
        if name=="dms":
            if len(a)==3:return self.call("sexagesimal",a,nodes)
            return dms_parts(a[0])
        if name=="qty": return quantity(a[0],nodes[1]["value"],UNITS)
        if name=="mixed":
            require(all(v.is_Integer for v in a) and a[2]>0 and 0<=a[1],"Mixed fractions require integer parts and a positive denominator")
            return a[0]+(-1 if a[0]<0 else 1)*a[1]/a[2]
        if name in ("arg","rectpolar"):
            angle=s.arg(a[0])
            if not angle.free_symbols: angle *= {"DEG":180/s.pi,"GRAD":200/s.pi}.get(self.angle,1)
            return angle if name=="arg" else [s.Abs(a[0]),angle]
        if name=="polar":
            theta=a[1]
            if not self.has_explicit_angle(nodes[1]) and not theta.free_symbols: theta *= {"DEG":s.pi/180,"GRAD":s.pi/200}.get(self.angle,1)
            return a[0]*(s.cos(theta)+s.I*s.sin(theta))
        if name in ("sin", "cos", "tan"):
            arg = a[0]
            if not self.has_explicit_angle(nodes[0]) and not getattr(arg, "free_symbols", set()):
                arg *= {"DEG": s.pi/180, "GRAD": s.pi/200}.get(self.angle, 1)
            return getattr(s,name)(arg)
        if name=="atan2":
            result=s.atan2(a[0],a[1])
            return result * ({"DEG":180/s.pi,"GRAD":200/s.pi}.get(self.angle,1) if not result.free_symbols else 1)
        if name in ("asin", "acos", "atan"):
            result = getattr(s,name)(*a)
            return result * ({"DEG": 180/s.pi, "GRAD":200/s.pi}.get(self.angle, 1) if not result.free_symbols else 1)
        basic = {"sqrt": s.sqrt, "cbrt": lambda x: s.real_root(x,3), "nthroot": s.root, "abs": s.Abs,
                 "floor": s.floor, "ceil": s.ceiling, "iPart": s.floor, "frac": s.frac,
                 "sign": s.sign, "gamma": s.gamma,
                 "erf":s.erf,"erfc":s.erfc,"Ei":s.Ei,"Si":s.Si,"Ci":s.Ci,"zeta":s.zeta,
                 "ln": s.log, "log": lambda x, b=10: s.log(x,b), "exp": s.exp,
                 "sinc": s.sinc, "sinh": s.sinh, "cosh": s.cosh, "tanh": s.tanh, "asinh": s.asinh, "acosh": s.acosh, "atanh": s.atanh,
                 "conj": s.conjugate, "re": s.re, "im": s.im, "arg": s.arg,
                 "simplify": s.simplify, "expand": s.expand, "factor": s.factor, "collect": s.collect,
                 "diff": s.diff, "gcd": s.gcd, "lcm": s.lcm, "nCr": s.binomial,
                 "percent": lambda x: x/100, "degree": lambda x: x*s.pi/180,
                 "rad":lambda x:x,"gradian":lambda x:x*s.pi/200,
                 "round": lambda x, n=0: x.round(int(n)), "quotient": lambda x,y: s.floor(x/y), "remainder": s.Mod,
                 "polar": lambda r,t: r*(s.cos(t)+s.I*s.sin(t)), "rectpolar": lambda z: [s.Abs(z),s.arg(z)]}
        if name in ("log","ln"): require(a[0]!=0,"Domain ERROR: logarithm of zero")
        if name=="log" and len(a)>1: require(a[1] not in (0,1),"Domain ERROR: invalid logarithm base")
        if name in basic: return basic[name](*a)
        if name in ("prime", "isprime"):
            require(len(a)==1, name+" expects one integer")
            require(a[0].is_Integer, name+" requires an integer")
            if name=="prime":
                require(1<=a[0]<=100000, "prime index must be between 1 and 100000")
                return s.Integer(s.prime(int(a[0])))
            require(abs(a[0])<=10**15, "isprime input outside supported range")
            return s.true if s.isprime(a[0]) else s.false
        if name in ("factorial", "nPr", "factorint", "divisors"):
            require(a[0].is_Integer and 0 <= a[0] <= (10000 if name in ("factorial", "nPr") else 10**15), "Number theory input outside supported range")
            if name == "factorial": return s.factorial(a[0])
            if name == "nPr":
                require(a[1].is_Integer and 0 <= a[1] <= a[0], "nPr requires 0 ≤ r ≤ n")
                return s.factorial(a[0])/s.factorial(a[0]-a[1])
            require(a[0]>0,"Factorization and divisors require a positive integer")
            if name == "factorint": return [[s.Integer(p), s.Integer(k)] for p,k in s.factorint(a[0]).items()]
            return s.divisors(a[0])
        if name == "subs": return a[0].subs(a[1],a[2])
        if name in ("apart","partfrac"):
            require(len(a)==2, name+" expects an expression and variable")
            return s.apart(a[0],a[1])
        if name in ("together","cancel","trigsimp","trigexpand","powsimp","powdenest","hyperexpand"):
            transforms={"together":s.together,"cancel":s.cancel,"trigsimp":s.trigsimp,
                        "trigexpand":s.trigexpand,"powsimp":s.powsimp,"powdenest":s.powdenest,
                        "hyperexpand":s.hyperexpand}
            require(len(a)==1, name+" expects one expression")
            return transforms[name](a[0])
        if name=="nsimplify":
            require(len(a)==1,"nsimplify expects one expression")
            return s.nsimplify(a[0])
        if name=="taylor":
            require(len(a)==4,"taylor expects expression, variable, point and order")
            return s.series(a[0],a[1],a[2],int(a[3]))
        if name in ("comDenom","numden"):
            numerator,denominator=s.fraction(s.together(a[0]))
            return denominator if name=="comDenom" else [numerator,denominator]
        if name=="coeff":
            require(len(a) in (2,3),"coeff expects an expression, variable and optional power")
            return a[0].coeff(a[1]) if len(a)==2 else a[0].coeff(a[1],int(a[2]))
        if name in ("quo","rem"):
            require(len(a)==3,name+" expects two polynomials and a variable")
            quotient,remainder=s.div(a[0],a[1],a[2])
            return quotient if name=="quo" else remainder
        if name=="resultant":
            require(len(a)==3,"resultant expects two expressions and a variable")
            return s.resultant(a[0],a[1],a[2])
        if name=="discriminant":
            require(len(a)==2,"discriminant expects a polynomial and variable")
            return s.discriminant(a[0],a[1])
        if name=="domain":
            require(len(a)==2,"domain expects an expression and variable")
            return continuous_domain(a[0],a[1],s.S.Reals)
        if name=="range":
            require(len(a)==2,"range expects an expression and variable")
            return function_range(a[0],a[1],s.S.Reals)
        if name == "integrate":
            require(len(a) in (2,4), "integrate expects a variable or integration bounds")
            spec = a[1] if len(a)==2 else (a[1],a[2],a[3])
            result = s.integrate(a[0],spec)
            if result.has(s.Integral): self.note = "Symbolic solution not found for the remaining integral."
            elif len(a)==2 and not isinstance(a[1], (list,tuple)): result += self.symbol("C")
            return result
        if name in ("dsolve","desolve"):
            require(len(a) in (3,4), "dsolve expects equation, dependent function and independent variable")
            require(isinstance(a[2], s.Symbol), "dsolve independent variable must be a symbol")
            equation=ode_equation(a[0]); dependent=a[1]
            function=dependent.func if isinstance(dependent, AppliedUndef) else dependent
            conditions=initial_conditions(a[3],dependent,a[2]) if len(a)==4 else None
            return s.dsolve(equation, fun=function, x=a[2], ics=conditions) if conditions else s.dsolve(equation, fun=function, x=a[2])
        if name in ("laplace","fourier"):
            require(len(a)==3, name+" expects an expression, time variable and transform variable")
            result=(s.laplace_transform if name=="laplace" else s.fourier_transform)(a[0],a[1],a[2])
            if isinstance(result, tuple):
                value, conditions = result[0], result[1:]
            else:
                value, conditions = result, ()
            meaningful=[str(condition) for condition in conditions if condition not in (s.true,0)]
            if meaningful: self.note="Transform conditions: "+", ".join(meaningful)
            return value
        if name in ("ilaplace","ifourier"):
            require(len(a)==3, name+" expects a transformed expression, transform variable and time variable")
            result=(s.inverse_laplace_transform if name=="ilaplace" else s.inverse_fourier_transform)(a[0],a[1],a[2])
            if isinstance(result, tuple):
                value, conditions = result[0], result[1:]
            else:
                value, conditions = result, ()
            meaningful=[str(condition) for condition in conditions if condition not in (s.true,0)]
            if meaningful: self.note="Transform conditions: "+", ".join(meaningful)
            return value
        if name in ("fft","ifft"):
            require(len(a)==1, name+" expects a list of samples")
            return discrete_fourier(a[0], inverse=name=="ifft")
        if name == "limit":
            if isinstance(a[1],Relational): var,point = a[1].lhs,a[1].rhs; direction = str(a[2]) if len(a)>2 else "+-"
            else: var,point = a[1],a[2]; direction = str(a[3]) if len(a)>3 else "+-"
            direction = {"left":"-","right":"+","both":"+-"}.get(direction,direction)
            return s.limit(a[0],var,point,dir=direction)
        if name == "series": return s.series(a[0],a[1],a[2] if len(a)>2 else 0,int(a[3]) if len(a)>3 else 6)
        if name in ("sum", "product"):
            spec = tuple(a[1]) if len(a)==2 else tuple(a[1:])
            return (s.summation if name=="sum" else s.product)(a[0],spec)
        if name == "piecewise": return s.Piecewise(*(tuple(x) for x in a))
        if name == "solve":
            symbols=set().union(*(e.free_symbols for e in a[0])) if isinstance(a[0],list) else a[0].free_symbols
            var = a[1] if len(a)>1 else sorted(symbols,key=str)
            if isinstance(var,list) and len(var)==1: var = var[0]
            if isinstance(a[0],list): return s.solve(a[0],var,dict=True)
            if isinstance(a[0],Relational) and not isinstance(a[0],s.Equality): return s.reduce_inequalities(a[0],var)
            expr = a[0].lhs-a[0].rhs if isinstance(a[0],s.Equality) else a[0]
            if isinstance(var,list): return s.solve(expr,var,dict=True)
            domain=s.S.Integers if var.is_integer else s.S.Reals if var.is_real else s.S.Complexes
            if var.is_positive: domain=domain.intersect(s.Interval.open(0,s.oo))
            elif var.is_negative: domain=domain.intersect(s.Interval.open(-s.oo,0))
            if var.is_nonzero: domain=domain-s.FiniteSet(0)
            result = s.solveset(expr,var,domain=domain)
            if isinstance(result,s.FiniteSet):
                result=s.FiniteSet(*(root for root in result if all(condition.subs(var,root)!=s.false for condition in self.conditions)))
            if isinstance(result,s.ConditionSet): self.note = "Symbolic solution not found. Try nsolve with a bracket."
            return result
        if name == "nsolve":
            expr = a[0].lhs-a[0].rhs if isinstance(a[0],s.Equality) else a[0]
            if len(a)==4:
                # Bracketing does not lose convergence merely due to a zero derivative.
                result=s.nsolve(expr,a[1],(a[2],a[3]),solver="bisect",prec=max(20,self.precision+10),maxsteps=max(100,4*(self.precision+10)))
            else: result=s.nsolve(expr,a[1],a[2],prec=max(20,self.precision+10),maxsteps=max(100,4*(self.precision+10)))
            require(all(c.subs(a[1],result)!=s.false for c in self.conditions),"No solution found in the expression domain")
            return s.N(result,self.precision)
        if name=="nderivative":
            require(len(a) in (3,4), "nderivative expects an expression, variable and point")
            return numeric_derivative(a[0],a[1],a[2],self.precision,a[3] if len(a)==4 else None)
        if name in ("nintegrate", "minimum", "maximum"):
            if name in ("minimum", "maximum"):
                return (s.minimum if name=="minimum" else s.maximum)(a[0],a[1],s.Interval(a[2],a[3]))
            # SymPy evalf uses adaptive quadrature and arbitrary precision.
            result = s.Integral(a[0],(a[1],a[2],a[3])).evalf(self.precision, strict=True)
            require(not result.has(s.Integral), "Numerical convergence failed")
            return result
        if name=="normalize":
            vector=matrix(a[0]);length=vector.norm()
            require(length!=0,"Domain ERROR: zero vector cannot be normalized")
            return vector/length
        if name=="gradient":
            coords=coordinates(a[1])
            return [s.diff(a[0],var) for var in coords]
        if name in ("divergence","curl"):
            coords=coordinates(a[1]); field=matrix(a[0])
            if field.rows==1 and field.cols==len(coords): field=field.T
            require(field.cols==1 and field.rows==len(coords), "Vector field dimension does not match coordinates")
            if name=="divergence":
                return s.Add(*(s.diff(field[index],coords[index]) for index in range(len(coords))))
            if field.rows==2:
                return s.diff(field[1],coords[0])-s.diff(field[0],coords[1])
            require(field.rows==3,"Curl supports two or three dimensional vector fields")
            return s.Matrix([s.diff(field[2],coords[1])-s.diff(field[1],coords[2]),
                             s.diff(field[0],coords[2])-s.diff(field[2],coords[0]),
                             s.diff(field[1],coords[0])-s.diff(field[0],coords[1])])
        if name=="hessian":
            coords=coordinates(a[1])
            return s.Matrix([[s.diff(a[0],coords[row],coords[column]) for column in range(len(coords))] for row in range(len(coords))])
        if name=="jacobian":
            coords=coordinates(a[1])
            return matrix(a[0]).jacobian(coords)
        if name=="laplacian":
            coords=coordinates(a[1])
            return s.Add(*(s.diff(a[0],var,var) for var in coords))
        if name=="charpoly":
            require(len(a) in (1,2), "charpoly expects a matrix and optional variable")
            m=matrix(a[0]); variable=a[1] if len(a)==2 else s.Symbol("lambda")
            return m.charpoly(variable).as_expr()
        if name=="identity":
            require(len(a)==1 and a[0].is_Integer and 0<a[0]<=32, "identity size must be an integer from 1 to 32")
            return s.eye(int(a[0]))
        if name=="diag":
            require(len(a)==1 and isinstance(a[0],(list,tuple)), "diag expects a list of diagonal entries")
            return s.diag(*a[0])
        matrix_ops = {"det": lambda m: m.det(), "inverse": lambda m: m.inv(), "transpose": lambda m: m.T,
                      "rank": lambda m: s.Integer(m.rank()), "trace": lambda m: m.trace(), "rref": lambda m: m.rref()[0],
                      "ref": lambda m: m.echelon_form(), "lu": lambda m: list(m.LUdecomposition()),
                      "eigenvalues": lambda m: [[k,s.Integer(v)] for k,v in m.eigenvals().items()],
                      "eigenvectors": lambda m: [[v,s.Integer(k),vec] for v,k,vec in m.eigenvects()],
                      "norm": lambda m: m.norm(), "qr": lambda m: list(m.QRdecomposition()),
                      "cholesky": lambda m: m.cholesky(hermitian=False), "nullspace": lambda m: list(m.nullspace()),
                      "cofactor": lambda m: m.cofactor_matrix(), "adjugate": lambda m: m.adjugate(),
                      "rowspace": lambda m: list(m.rowspace()), "singularvalues": lambda m: list(m.singular_values()),
                      "frob": lambda m: s.sqrt(sum(item*item for item in m)),
                      "jordan": lambda m: list(m.jordan_form()), "dim": lambda m: [m.rows,m.cols]}
        if name in matrix_ops: return matrix_ops[name](matrix(a[0]))
        if name in ("dot", "cross", "angle", "projection", "linsolve"):
            u,v = matrix(a[0]),matrix(a[1])
            if name == "dot": return u.dot(v)
            if name == "cross": return u.cross(v)
            if name == "angle":
                require(u.norm()!=0 and v.norm()!=0,"Domain ERROR: zero vector has no direction")
                result=s.acos(u.dot(v)/(u.norm()*v.norm()))
                return result if result.free_symbols else result*{"DEG":180/s.pi,"GRAD":200/s.pi}.get(self.angle,1)
            if name == "projection": return v*(u.dot(v)/v.dot(v))
            return u.inv()*v
        if name in ("mean", "median", "variance", "stdev", "sumdata", "quartiles", "stats"):
            data = flatten(a[0]); n = len(data)
            require(n>0,"Enter at least one data value")
            avg = sum(data)/n; var = sum((x-avg)**2 for x in data)/n
            ordered = sorted(data)
            med = s.Rational(1,2)*(ordered[(n-1)//2]+ordered[n//2])
            if name == "mean": return avg
            if name == "median": return med
            if name == "variance": return var
            if name == "stdev": return s.sqrt(var)
            if name == "sumdata": return sum(data)
            quartiles = [s.Rational(str(x)) for x in statistics.quantiles(ordered,method="inclusive")] if n>1 else [data[0]]*3
            if name == "quartiles": return quartiles
            return {"n": s.Integer(n), "sum": sum(data), "mean": avg, "median": med, "population variance": var,
                    "population SD": s.sqrt(var), "sample variance": var*n/(n-1) if n>1 else s.nan,
                    "sample SD": s.sqrt(var*n/(n-1)) if n>1 else s.nan, "quartiles (inclusive)": quartiles}
        if name in ("covariance","correlation"):
            require(len(a)==2, name+" expects two data lists")
            xs=flatten(a[0]); ys=flatten(a[1])
            require(len(xs)==len(ys),"Covariance and correlation require equal data lengths")
            n=len(xs)
            require(n>0,"Enter paired data")
            mx=sum(xs)/n; my=sum(ys)/n
            covariance=sum((x-mx)*(y-my) for x,y in zip(xs,ys))/n
            if name=="covariance": return covariance
            vx=sum((x-mx)**2 for x in xs)/n; vy=sum((y-my)**2 for y in ys)/n
            require(vx*vy!=0,"Correlation requires variation in both data sets")
            return s.simplify(covariance/s.sqrt(vx*vy))
        if name == "regression":
            rows = a[0]; mode = str(a[1]) if len(a)>1 else "linear"
            return fit_regression(self, rows, mode)
        if name == "convert":
            if len(a)==2 and isinstance(a[0],Quantity):
                self.note="Result in "+nodes[1]["value"]
                return convert_quantity(a[0],nodes[1]["value"],UNITS)
            src,dst = nodes[1]["value"],nodes[2]["value"]
            require(src in UNITS and dst in UNITS,"Unknown unit")
            d1,f1,o1 = UNITS[src]; d2,f2,o2 = UNITS[dst]
            require(d1==d2,"Unit dimension mismatch")
            base = a[0]*f1+o1
            if d1=="temperature": require(base>=0,"Temperature below absolute zero")
            self.note = "Result in " + dst
            return s.simplify((base-o2)/f2)
        if name in ("normpdf", "normcdf", "invnorm", "tpdf", "tcdf", "invt", "chi2pdf", "chi2cdf", "fpdf", "fcdf",
                    "binompdf", "binomcdf", "poissonpdf", "poissoncdf", "geometpdf", "geometcdf"):
            return distribution_value(self, name, a)
        if name in ("ttest", "ztest", "chi2test", "anova", "tinterval", "zinterval"):
            return statistical_test(self, name, a, nodes)
        if name in ("tvmfv", "tvmpv", "tvmpmt", "tvmn", "tvmrate", "npv", "irr", "amort"):
            return finance_value(self, name, a, nodes)
        if name in self.functions:
            function = self.functions[name]
            require(len(function["parameters"])==len(a),"Function argument count mismatch")
            require(name not in self.resolving,"Recursive function definition")
            old = self.bindings.copy(); self.bindings.update(zip(function["parameters"],a)); self.resolving.add(name)
            try: return self.build(function["body"])
            finally: self.bindings = old; self.resolving.remove(name)
        if name.isidentifier():
            return s.Function(name)(*a)
        raise MathError("Unknown function: " + name)

def is_dms_expression(node, variables=None):
    if not isinstance(node, dict): return False
    kind=node.get("kind")
    if kind == "sexagesimal": return True
    if kind == "frozen_call" and node.get("value") == "sexagesimal": return True
    if kind in ("group", "restricted", "unary"):
        return bool(node.get("args")) and is_dms_expression(node["args"][0], variables)
    if kind == "symbol" and node.get("value") == "Ans":
        return is_dms_expression((variables or {}).get("Ans", {}), variables)
    if kind == "binary" and node.get("value") in ("+", "-", "*", "/"):
        return any(is_dms_expression(arg, variables) for arg in node.get("args", []))
    return False

def display_tree(x):
    def t(kind,value="",args=()): return {"kind":kind,"value":value,"args":list(args)}
    if isinstance(x,Quantity): return t("quantity",x.unit_text(),[display_tree(x.base)])
    if isinstance(x,dict): return t("rows",args=[t("row",str(k),[display_tree(v)]) for k,v in x.items()])
    if isinstance(x,(list,tuple,s.Tuple)): return t("list",args=[display_tree(v) for v in x])
    if isinstance(x,s.MatrixBase): return t("matrix",args=[t("list",args=[display_tree(x[i,j]) for j in range(x.cols)]) for i in range(x.rows)])
    if isinstance(x,s.Rational) and x.q != 1: return t("fraction",args=[t("text",str(x.p)),t("text",str(x.q))])
    if isinstance(x,s.Pow):
        if x.exp == s.Rational(1,2): return t("root",args=[display_tree(x.base)])
        if x.exp.is_negative: return t("fraction",args=[t("text","1"),display_tree(x.base**(-x.exp))])
        return t("power",args=[display_tree(x.base),display_tree(x.exp)])
    if isinstance(x,s.Add): return t("sum",args=[t("unary","-",[display_tree(-a)]) if a.could_extract_minus_sign() else display_tree(a) for a in x.as_ordered_terms()])
    if isinstance(x,s.Mul):
        if x.could_extract_minus_sign(): return t("unary","-",[display_tree(-x)])
        num,den = s.fraction(x)
        if den != 1: return t("fraction",args=[display_tree(num),display_tree(den)])
        return t("product",args=[display_tree(a) for a in x.as_ordered_factors()])
    if isinstance(x,s.FiniteSet): return t("set",args=[display_tree(a) for a in sorted(x,key=s.default_sort_key)])
    if isinstance(x,Relational): return t("relation",x.rel_op,[display_tree(x.lhs),display_tree(x.rhs)])
    if isinstance(x,s.Function):
        name=x.func.__name__
        if name=="log": name="ln"
        return t("function",name,[display_tree(a) for a in x.args])
    if isinstance(x,s.Symbol): return t("symbol",readable(x))
    if isinstance(x,s.Number): return t("number",readable(x))
    return t("text",readable(x))

def dms_tree(value):
    return {"kind":"dms","args":[display_tree(part) for part in dms_parts(value)]}

def readable(x):
    if isinstance(x,Quantity): return readable(x.base)+" "+x.unit_text()
    if isinstance(x,dict): return "\n".join(str(k)+": "+readable(v) for k,v in x.items())
    if isinstance(x,(list,tuple)): return "["+", ".join(readable(v) for v in x)+"]"
    from sympy.printing.str import StrPrinter
    class CompactPrinter(StrPrinter):
        def _print_Float(self,expr):
            text=super()._print_Float(expr)
            parts=text.lower().split("e")
            mantissa=parts[0].rstrip("0").rstrip(".") if "." in parts[0] else parts[0]
            return mantissa+("e"+parts[1] if len(parts)>1 else "")
    return CompactPrinter().doprint(x)

def approximate(x, digits):
    if isinstance(x,Quantity): return Quantity(s.N(x.base,digits),x.dimensions,x.absolute_temperature)
    if isinstance(x,dict): return {k:approximate(v,digits) for k,v in x.items()}
    if isinstance(x,(list,tuple)): return [approximate(v,digits) for v in x]
    if isinstance(x,s.Set):
        return [s.N(v,digits) for v in sorted(x,key=s.default_sort_key)] if isinstance(x,s.FiniteSet) else x
    if x in (s.true,s.false): return x
    return s.N(x,digits) if hasattr(x,"evalf") else x

def display_rounded(x, digits):
    """Round floating-point values to the display digits, keeping exact forms exact."""
    if isinstance(x,Quantity): return Quantity(display_rounded(x.base,digits),x.dimensions,x.absolute_temperature)
    if isinstance(x,dict): return {k:display_rounded(v,digits) for k,v in x.items()}
    if isinstance(x,(list,tuple)): return [display_rounded(v,digits) for v in x]
    if isinstance(x,s.MatrixBase): return x.applyfunc(lambda v: display_rounded(v,digits))
    if isinstance(x,s.Set): return s.FiniteSet(*(display_rounded(v,digits) for v in x)) if isinstance(x,s.FiniteSet) else x
    if isinstance(x,s.Basic):
        floats={f:s.N(f,digits) for f in x.atoms(s.Float)}
        if floats: return x.xreplace(floats)
    return x

def result_ast(x):
    """Lossless result transfer for Ans and STO; never reparse printed mathematics."""
    def node(kind,value="",args=()): return {"kind":kind,"value":value,"args":list(args)}
    if isinstance(x,Quantity): return {**node("quantity",args=[result_ast(x.base)]),"dimensions":list(x.dimensions),"absolute":x.absolute_temperature}
    if isinstance(x,s.MatrixBase): return node("list",args=[node("list",args=[result_ast(x[i,j]) for j in range(x.cols)]) for i in range(x.rows)])
    if isinstance(x,(list,tuple,s.Tuple)): return node("list",args=[result_ast(v) for v in x])
    if isinstance(x,dict): return node("mapping",args=[node("pair",args=[result_ast(k),result_ast(v)]) for k,v in x.items()])
    if isinstance(x,s.FiniteSet): return node("set",args=[result_ast(v) for v in x])
    if str(x) in ("pi","E","I","oo","-oo","EmptySet","True","False"): return node("constant",str(x))
    if isinstance(x,s.Symbol): return node("snapshot_symbol",str(x))
    if isinstance(x,s.Float): return node("float",readable(x))
    if isinstance(x,s.Integer): return node("number",str(x))
    if isinstance(x,s.Rational): return node("binary","/",[node("number",str(x.p)),node("number",str(x.q))])
    if isinstance(x,(s.Add,s.Mul)):
        op="+" if isinstance(x,s.Add) else "*"
        result=result_ast(x.args[0])
        for v in x.args[1:]: result=node("binary",op,[result,result_ast(v)])
        return result
    if isinstance(x,s.Pow): return node("binary","^",[result_ast(x.base),result_ast(x.exp)])
    if isinstance(x,Relational): return node("relation",x.rel_op,[result_ast(x.lhs),result_ast(x.rhs)])
    if isinstance(x,s.Function):
        name=x.func.__name__
        reusable={"log":"ln","Abs":"abs","conjugate":"conj","Piecewise":"piecewise","exp":"exp",
                  "sin":"sin","cos":"cos","tan":"tan","asin":"asin","acos":"acos","atan":"atan",
                  "sinh":"sinh","cosh":"cosh","tanh":"tanh","asinh":"asinh","acosh":"acosh","atanh":"atanh",
                  "sinc":"sinc","gamma":"gamma","erf":"erf","erfc":"erfc","Ei":"Ei","Si":"Si","Ci":"Ci",
                  "zeta":"zeta","re":"re","im":"im","arg":"arg","sign":"sign","floor":"floor","ceiling":"ceil",
                  "atan2":"atan2"}
        require(name in reusable,"This result cannot be stored as a reusable expression")
        return node("frozen_call",reusable[name],[result_ast(v) for v in x.args])
    raise MathError("This result cannot be stored as a reusable expression")

def fit_regression(engine, rows, mode):
    require(len(rows)>=2 and all(len(row)==2 for row in rows),"Regression requires x,y pairs")
    xs,ys = zip(*rows)
    require(mode in ("linear","quadratic","logarithmic","exponential","power"),"Unknown regression type")
    if mode in ("logarithmic","power"): require(all(x>0 for x in xs),"Logarithmic x values must be positive"); xs = tuple(s.log(x) for x in xs)
    if mode in ("exponential","power"): require(all(y>0 for y in ys),"Logarithmic y values must be positive"); ys = tuple(s.log(y) for y in ys)
    degree = 2 if mode == "quadratic" else 1
    design = s.Matrix([[x**i for i in range(degree+1)] for x in xs]); target = s.Matrix(ys)
    coef = (design.T*design).inv()*design.T*target
    x = engine.symbol("x")
    result = sum(c*x**i for i,c in enumerate(coef))
    if mode == "logarithmic": result = result.subs(x,s.log(x))
    if mode == "exponential": result = s.exp(result)
    if mode == "power": result = s.exp(coef[0])*x**coef[1]
    return result

def regression_samples(engine, value, rows, request):
    xs = [point for point in (_finite_real(row[0]) for row in rows) if point is not None]
    require(len(xs)>=2,"Regression requires x,y pairs")
    low,high = min(xs),max(xs)
    if high<=low: low-=1.0; high+=1.0
    padding=(high-low)*0.05
    start,end=low-padding,high+padding
    count=min(600,max(120,int(request.get("samples",240))))
    x=next(iter(value.free_symbols),engine.symbol("x"))
    function=s.lambdify(x,value,modules="math",cse=True,docstring_limit=0)
    curve=[]
    for index in range(count+1):
        at=start+(end-start)*index/count
        try: y=float(function(at))
        except (TypeError,ValueError,ZeroDivisionError,OverflowError): continue
        if math.isfinite(y) and abs(y)<1e100: curve.append([at,y])
    return curve

def graph(engine, request):
    kind = request.get("graphKind","cartesian")
    trees = request.get("trees",[])
    start,end = float(request.get("min",-10)),float(request.get("max",10))
    require(math.isfinite(start) and math.isfinite(end) and end>start,"Invalid graph range")
    if kind == "sequence":
        return graph_sequence(engine, request, trees, start, end)
    if kind == "surface":
        return graph_surface(engine, request, trees, start, end)
    if kind == "differential":
        return graph_differential(engine, request, trees, start, end)
    var = engine.symbol(request.get("variable","x")); engine.bindings[str(var)] = var
    expressions = [engine.build(t) for t in trees]
    shade_items = (request.get("shadings") or []) if kind == "cartesian" else []
    shade_expressions = [[engine.build(t) for t in (item.get("trees") or [])] for item in shade_items]
    all_expressions = expressions+[expression for group in shade_expressions for expression in group]
    names = parameter_names(all_expressions, {str(var)})
    sliders = resolved_parameters(engine, request, all_expressions, {str(var)})
    count = min(1600,max(100,int(request.get("samples",500))))
    curves=[]
    for expression in expressions:
        function=s.lambdify(var,substitute_parameters(expression,sliders),modules="math",cse=True,docstring_limit=0)
        samples = adaptive_samples(function, start, end, count, kind)
        curves.append(samples)
    result = {"curves":curves,"parameters":sorted(names)}
    if kind == "cartesian" and shade_items:
        result["shadings"] = graph_shading(engine, request, shade_items, shade_expressions, sliders, start, end)
    return result

def _finite_real(value):
    try:
        value = float(value)
        return value if math.isfinite(value) and abs(value) < 1e100 else None
    except (TypeError, ValueError, ZeroDivisionError, OverflowError):
        return None

def parameter_values(engine, request):
    """Slider values for free graph parameters such as a, b and c."""
    sliders = {}
    for name, value in (request.get("parameters") or {}).items():
        number = _finite_real(value)
        if number is not None:
            sliders[engine.symbol(str(name))] = s.Float(number, engine.precision)
    return sliders

def resolved_parameters(engine, request, expressions, excluded):
    """Slider values plus unit defaults so a curve still plots before its sliders move."""
    sliders = parameter_values(engine, request)
    for name in parameter_names(expressions, excluded):
        sliders.setdefault(engine.symbol(name), s.Float(1.0, engine.precision))
    return sliders

def parameter_names(expressions, excluded):
    """Free symbols that the graphing panel can expose as sliders."""
    names = set()
    for expression in expressions:
        if isinstance(expression, (list, tuple)):
            names |= parameter_names(expression, excluded)
            continue
        for symbol in getattr(expression, "free_symbols", set()):
            if str(symbol) not in excluded: names.add(str(symbol))
    return names

def substitute_parameters(expression, sliders):
    if not sliders: return expression
    if isinstance(expression, (list, tuple)):
        return [substitute_parameters(item, sliders) for item in expression]
    return expression.subs(sliders) if getattr(expression, "subs", None) else expression

def graph_shading(engine, request, items, groups, sliders, xmin, xmax):
    """[shade] regions: half-plane inequalities and bands between one or two curves."""
    ymin,ymax = float(request.get("yMin",-5)),float(request.get("yMax",5))
    require(math.isfinite(ymin) and math.isfinite(ymax) and ymax>ymin,"Shading needs a valid y range")
    span = ymax-ymin; low_edge,high_edge = ymin-span,ymax+span
    def clamp(value): return min(max(value,low_edge),high_edge)
    x = engine.symbol("x"); engine.bindings["x"] = x
    count = min(1200,max(200,int(request.get("samples",500))))
    def endpoint(item, key, default):
        tree = item.get(key)
        if tree is None: return default
        number = _finite_real(s.N(substitute_parameters(engine.build(tree),sliders),engine.precision))
        require(number is not None,"Shading intervals must be finite numbers")
        return number
    def samples(expression, a, b):
        function = s.lambdify(x,substitute_parameters(expression,sliders),modules="math",cse=True,docstring_limit=0)
        points = []
        for index in range(count+1):
            at = a+(b-a)*index/count
            value = _finite_real(function(at))
            points.append([at,value] if value is not None else None)
        return points
    shadings = []
    for item,group in zip(items,groups):
        mode = item.get("mode")
        a,b = endpoint(item,"a",xmin),endpoint(item,"b",xmax)
        require(a < b,"Shading intervals must be increasing")
        if mode == "halfplane":
            require(len(group) == 1,"A shaded inequality needs one boundary curve")
            boundary = samples(group[0],a,b)
            edge = low_edge if item.get("side") == "below" else high_edge
            polygons=[]; run=[]
            for point in boundary+[None]:
                value = None if point is None else point[1]
                if value is None:
                    if len(run) >= 2: polygons.append(run+[[run[-1][0],edge],[run[0][0],edge]])
                    run=[]
                    continue
                run.append([point[0],clamp(value)])
            shadings.append({"mode":mode,"boundary":[boundary],"fill":polygons})
        elif mode == "band":
            require(1 <= len(group) <= 2,"[shade] takes one or two functions")
            first = samples(group[0],a,b)
            second = samples(group[1],a,b) if len(group) == 2 else [[point[0],0.0] if point else None for point in first]
            runs=[]; run=[]
            for index in range(len(first)):
                low = None if first[index] is None else first[index][1]
                high = None if second[index] is None else second[index][1]
                if low is None or high is None:
                    if len(run) >= 2: runs.append(run)
                    run=[]
                    continue
                run.append([first[index][0],clamp(low),clamp(high)])
            if len(run) >= 2: runs.append(run)
            polygons = []
            for segment in runs:
                top = [[at,max(low,high)] for at,low,high in segment]
                bottom = [[at,min(low,high)] for at,low,high in segment]
                polygons.append(top+list(reversed(bottom)))
            shadings.append({"mode":mode,"boundary":[first,second if len(group) == 2 else None],"fill":polygons})
        else:
            raise MathError("Unknown shading mode")
    return shadings

def adaptive_samples(function, start, end, base_count, kind="cartesian"):
    """Sample coarsely first, then add points where the curve bends or breaks."""
    def point(at):
        try:
            if kind == "parametric":
                x, y = map(float, function(at))
            else:
                y = float(function(at)); x = at
                if kind == "polar": x, y = y*math.cos(at), y*math.sin(at)
            return [x, y] if math.isfinite(x) and math.isfinite(y) and abs(x) < 1e100 and abs(y) < 1e100 else None
        except (TypeError, ValueError, ZeroDivisionError, OverflowError):
            return None
    intervals = min(512, max(100, base_count))
    values = {start + (end-start)*i/intervals: None for i in range(intervals+1)}
    for at in values:
        values[at] = point(at)
    max_points = min(1800, max(base_count+1, 1200))
    def refine(left, right, depth):
        if depth >= 4 or len(values) >= max_points:
            return
        middle = (left+right)/2
        actual = point(middle)
        a, b = values[left], values[right]
        split = a is None or b is None or actual is None
        if a is not None and b is not None and actual is not None:
            linear = ((a[0]+b[0])/2, (a[1]+b[1])/2)
            span = max(abs(b[0]-a[0]), abs(b[1]-a[1]), 1e-9)
            error = max(abs(actual[0]-linear[0]), abs(actual[1]-linear[1]))/span
            split = error > 0.012 or abs(b[1]-a[1]) > 0.22*max(abs(end-start),1e-9)
        if split:
            values[middle] = actual
            refine(left, middle, depth+1)
            refine(middle, right, depth+1)
    coarse = sorted(values)
    for left, right in zip(coarse, coarse[1:]):
        refine(left, right, 0)
    return [values[at] for at in sorted(values)]

def graph_sequence(engine, request, trees, start, end):
    require(start >= 0 and end <= 2000, "Sequence range must be between 0 and 2000")
    first, last = math.ceil(start), math.floor(end)
    require(last >= first and last-first <= 1200, "Sequence range is too large")
    n = engine.symbol("n"); engine.bindings["n"] = n
    engine.allow_sequence_calls = True
    expressions = [engine.build(tree) for tree in trees]
    names = parameter_names(expressions, {"n"})
    sliders = resolved_parameters(engine, request, expressions, {"n"})
    expressions = [substitute_parameters(expression,sliders) for expression in expressions]
    seed_trees = request.get("initialTrees", [])
    seeds = []
    for tree in seed_trees[:20]:
        value = engine.build(tree)
        numeric = _finite_real(s.N(value, engine.precision))
        require(numeric is not None, "Initial sequence values must be finite real numbers")
        seeds.append(numeric)
    require(expressions, "Enter a sequence rule")
    curves = []
    for curve_index, expression in enumerate(expressions):
        function_name = "u" if len(expressions) == 1 else "u%d" % (curve_index+1)
        sequence = {}
        recursive_calls = expression.atoms(AppliedUndef)
        for index, value in enumerate(seeds): sequence[index] = value
        for index in range(last+1):
            if recursive_calls and index in sequence:
                pass
            else:
                current = expression.subs(n, s.Integer(index))
                replacements = {}
                for call in current.atoms(AppliedUndef):
                    name = call.func.__name__
                    require(name in ("u", function_name), "A sequence rule may only refer to its own previous terms")
                    require(len(call.args) == 1 and call.args[0].is_Integer, "Sequence references need integer indices")
                    previous_index = int(call.args[0])
                    require(previous_index < index and previous_index in sequence,
                            "Provide enough initial values for every previous-term reference")
                    replacements[call] = s.Float(sequence[previous_index], engine.precision)
                value = _finite_real(s.N(current.xreplace(replacements), engine.precision))
                require(value is not None, "Sequence rule did not produce a finite real value")
                sequence[index] = value
        curves.append([[index, sequence[index]] for index in range(first, last+1) if index in sequence])
    return {"curves": curves, "discrete": True, "parameters": sorted(names)}

def graph_surface(engine, request, trees, xmin, xmax):
    require(len(trees) == 1, "Enter one surface expression z=f(x,y)")
    ymin, ymax = float(request.get("surfaceYMin", -3)), float(request.get("surfaceYMax", 3))
    require(math.isfinite(ymin) and math.isfinite(ymax) and ymax > ymin, "Invalid surface y range")
    x, y = engine.symbol("x"), engine.symbol("y")
    engine.bindings.update({"x":x, "y":y})
    expression = engine.build(trees[0])
    names = parameter_names([expression], {"x","y"})
    expression = substitute_parameters(expression, resolved_parameters(engine, request, [expression], {"x","y"}))
    fn = s.lambdify((x,y), expression, modules="math", cse=True, docstring_limit=0)
    count = min(40, max(12, int(request.get("surfaceSamples", 26))))
    mesh = []
    for row in range(count+1):
        yy = ymin+(ymax-ymin)*row/count
        points = []
        for col in range(count+1):
            xx = xmin+(xmax-xmin)*col/count
            try: z = _finite_real(fn(xx,yy))
            except (TypeError, ValueError, ZeroDivisionError, OverflowError): z = None
            points.append([xx,yy,z] if z is not None else None)
        mesh.append(points)
    values = [point[2] for row in mesh for point in row if point is not None]
    require(values, "Surface has no finite values in this range")
    return {"surface":mesh,"zMin":min(values),"zMax":max(values),"surfaceSamples":count,"parameters":sorted(names)}

def graph_differential(engine, request, trees, start, end):
    require(len(trees) == 1, "Enter one derivative rule dy/dt=f(t,y)")
    t, y = engine.symbol("t"), engine.symbol("y")
    engine.bindings.update({"t":t, "y":y})
    expression = engine.build(trees[0])
    names = parameter_names([expression], {"t","y"})
    expression = substitute_parameters(expression, resolved_parameters(engine, request, [expression], {"t","y"}))
    fn = s.lambdify((t,y), expression, modules="math", cse=True, docstring_limit=0)
    ymin, ymax = float(request.get("yMin", -5)), float(request.get("yMax", 5))
    t0 = float(request.get("t0", 0))
    require(math.isfinite(ymin) and math.isfinite(ymax) and ymax > ymin, "Invalid solution y range")
    require(math.isfinite(t0) and start <= t0 <= end, "Initial time must be inside the t range")
    initials = request.get("initialValues", [1])
    require(1 <= len(initials) <= 6, "Enter between one and six initial y values")
    def slope(at, value):
        try: return _finite_real(fn(at,value))
        except (TypeError, ValueError, ZeroDivisionError, OverflowError): return None
    curves=[]
    for initial in initials:
        y0 = _finite_real(initial)
        require(y0 is not None, "Initial y values must be finite real numbers")
        def integrate(bound):
            distance = bound-t0
            steps = max(40, min(500, int(240*abs(distance)/(end-start))+40))
            h = distance/steps
            points = [[t0,y0]]
            at, value = t0, y0
            for _ in range(steps):
                k1=slope(at,value)
                k2=slope(at+h/2,value+h*k1/2) if k1 is not None else None
                k3=slope(at+h/2,value+h*k2/2) if k2 is not None else None
                k4=slope(at+h,value+h*k3) if k3 is not None else None
                if None in (k1,k2,k3,k4): break
                value += h*(k1+2*k2+2*k3+k4)/6
                at += h
                if not math.isfinite(value) or abs(value)>1e100: break
                points.append([at,value])
            return points
        left=integrate(start); right=integrate(end)
        curves.append(list(reversed(left[1:]))+[[t0,y0]]+right[1:])
    fields=[]
    nx, ny = 17, 11
    for ix in range(nx):
        at=start+(end-start)*(ix+0.5)/nx
        for iy in range(ny):
            value=ymin+(ymax-ymin)*(iy+0.5)/ny
            dy=slope(at,value)
            if dy is not None: fields.append([at,value,dy])
    return {"curves":curves,"fields":fields,"differential":True,"parameters":sorted(names)}

def graph_analysis(engine, request):
    kind = request.get("graphKind","cartesian")
    trees = request.get("trees",[])
    require(kind in ("cartesian","parametric","polar"), "Analysis supports Cartesian, parametric and polar curves")
    selected = int(request.get("selected", 0)); other = int(request.get("other", 1))
    require(0 <= selected < len(trees), "Select a function")
    action = request.get("analysis", "root")
    require(action in ("root","intersection","minimum","maximum","inflection","derivative","tangent","integral","arclength"), "Unknown graph analysis")
    require(action != "intersection" or kind == "cartesian", "Intersections need two Cartesian functions")
    if action == "intersection": require(0 <= other < len(trees) and other != selected, "Select two different functions")
    a = float(request.get("a", -10)); b = float(request.get("b", 10))
    singled = action in ("derivative", "tangent")
    require(math.isfinite(a) and math.isfinite(b) and (singled or a < b) and abs(b-a) <= 1e9, "Invalid analysis range")
    def numeric(expr, variable):
        raw = s.lambdify(variable, expr, modules="math", cse=True, docstring_limit=0)
        def value(at):
            try:
                result = float(raw(at))
                return result if math.isfinite(result) else None
            except (TypeError, ValueError, ZeroDivisionError, OverflowError): return None
        return value
    def zeroes(fn):
        count = 1200
        xs = [a+(b-a)*i/count for i in range(count+1)]
        ys = [fn(at) for at in xs]
        roots = []
        def add(at):
            if not roots or all(abs(at-old)>max(1e-8,abs(b-a)*1e-6) for old in roots): roots.append(at)
        for i in range(count):
            left,right = xs[i],xs[i+1]; yl,yr = ys[i],ys[i+1]
            if yl is None or yr is None: continue
            if abs(yl) < 1e-9: add(left)
            if yl*yr < 0:
                lo,hi = left,right; low = yl
                for _ in range(55):
                    mid = (lo+hi)/2; middle = fn(mid)
                    if middle is None: break
                    if low*middle <= 0: hi=mid
                    else: lo=mid; low=middle
                root=(lo+hi)/2; residual=fn(root)
                if residual is not None and abs(residual) < 1e-6: add(root)
        if ys[-1] is not None and abs(ys[-1]) < 1e-9: add(b)
        return sorted(roots)
    def tangent_point(px, py, slope, direction=None):
        xmin=float(request.get("xMin",-10)); xmax=float(request.get("xMax",10))
        ymin=float(request.get("yMin",-10)); ymax=float(request.get("yMax",10))
        if not all(math.isfinite(value) for value in (xmin,xmax,ymin,ymax)) or xmax <= xmin or ymax <= ymin: xmin,xmax,ymin,ymax = -10,10,-10,10
        payload = {"analysis":action,"points":[[px,py]]}
        if slope is None: payload["vertical"]=True
        else: payload["value"]=slope
        if direction is not None:
            dx,dy = direction
            speed = math.hypot(dx,dy)
            if speed > 1e-12:
                length = 1.6*max(xmax-xmin,ymax-ymin)
                payload["line"] = [[px-dx/speed*length,py-dy/speed*length],[px+dx/speed*length,py+dy/speed*length]]
        elif slope is None or abs(slope) > 1e6:
            payload["line"] = [[px,ymin],[px,ymax]]
        else:
            payload["line"] = [[xmin,py+slope*(xmin-px)],[xmax,py+slope*(xmax-px)]]
        return payload
    if kind == "cartesian":
        x = engine.symbol("x"); engine.bindings["x"] = x
        raw = [engine.build(tree) for tree in trees]
        sliders = resolved_parameters(engine, request, raw, {"x"})
        expressions = [substitute_parameters(expression, sliders) for expression in raw]
        require(all(isinstance(expression, s.Expr) for expression in expressions), "Enter Cartesian functions")
        expression = expressions[selected]
        target = expression-expressions[other] if action == "intersection" else expression
        value = numeric(expression, x)
        if action == "derivative":
            derivative = numeric(s.diff(expression, x), x)(a)
            require(derivative is not None and value(a) is not None, "Derivative is undefined at this point")
            return {"analysis":action,"points":[[a,value(a)]],"value":derivative}
        if action == "tangent":
            require(value(a) is not None, "Tangent is undefined at this point")
            return tangent_point(a, value(a), numeric(s.diff(expression, x), x)(a))
        if action == "inflection":
            second = numeric(s.diff(expression, x, 2), x)
            step = max(1e-7,(b-a)*1e-4)
            positions = []
            for at in zeroes(second):
                left,right = second(at-step),second(at+step)
                if left is not None and right is not None and left*right < 0: positions.append(at)
            points = [[at,value(at)] for at in positions if value(at) is not None][:80]
            return {"analysis":action,"points":points,"count":len(points),"truncated":len(positions)>80}
        if action == "arclength":
            slope = s.diff(expression, x)
            result = s.Integral(s.sqrt(1+slope**2), (x, s.Float(a), s.Float(b))).evalf(engine.precision, strict=True)
            require(result.is_real and result.is_finite, "Numerical convergence failed")
            return {"analysis":action,"points":[],"value":float(result)}
        if action == "integral":
            result = s.Integral(expression, (x, s.Float(a), s.Float(b))).evalf(engine.precision, strict=True)
            require(result.is_real and result.is_finite, "Numerical convergence failed")
            return {"analysis":action,"points":[],"value":float(result)}
        tested = numeric(target, x)
        if action in ("root", "intersection"):
            positions = zeroes(tested)
            # A tangent intersection has no sign change. Its derivative identifies a zero minimum.
            try:
                for at in zeroes(numeric(s.diff(target, x), x)):
                    residual=tested(at)
                    if residual is not None and abs(residual) < 1e-7 and all(abs(at-old)>max(1e-8,abs(b-a)*1e-6) for old in positions): positions.append(at)
            except (TypeError,ValueError): pass
            positions.sort()
        else:
            positions = [a,b]
            try: positions += zeroes(numeric(s.diff(expression, x), x))
            except (TypeError,ValueError): pass
            entries = [(at,value(at)) for at in positions]
            entries = [(at,y) for at,y in entries if y is not None]
            require(entries, "No finite values in this range")
            limit = (min if action == "minimum" else max)(y for _,y in entries)
            positions = [at for at,y in entries if abs(y-limit) <= max(1e-8,abs(limit)*1e-8)]
        points = [[at,value(at)] for at in positions if value(at) is not None][:80]
        return {"analysis":action,"points":points,"count":len(points),"truncated":len(positions)>80}
    variable = engine.symbol(request.get("variable","t")); engine.bindings[str(variable)] = variable
    raw = engine.build(trees[selected])
    sliders = resolved_parameters(engine, request, [raw], {str(variable)})
    if kind == "polar":
        radius = substitute_parameters(raw, sliders)
        require(isinstance(radius, s.Expr), "Enter a polar radius r(t)")
        first, second = radius*s.cos(variable), radius*s.sin(variable)
    else:
        pair = substitute_parameters(raw, sliders)
        require(isinstance(pair,(list,tuple)) and len(pair)==2, "Parametric curves are [x(t), y(t)] pairs")
        first, second = pair
    dfirst, dsecond = s.diff(first, variable), s.diff(second, variable)
    xvalue, yvalue = numeric(first, variable), numeric(second, variable)
    dxvalue, dyvalue = numeric(dfirst, variable), numeric(dsecond, variable)
    def point(at):
        px,py = xvalue(at),yvalue(at)
        return [px,py] if px is not None and py is not None else None
    if action == "root":
        points = [point(at) for at in zeroes(yvalue)]
    elif action in ("minimum", "maximum"):
        target = radius if kind == "polar" else second
        target_value = numeric(target, variable)
        positions = [a,b]
        try: positions += zeroes(numeric(s.diff(target, variable), variable))
        except (TypeError,ValueError): pass
        entries = [(at,target_value(at)) for at in positions]
        entries = [(at,y) for at,y in entries if y is not None]
        require(entries, "No finite values in this range")
        limit = (min if action == "minimum" else max)(y for _,y in entries)
        points = [point(at) for at,y in entries if abs(y-limit) <= max(1e-8,abs(limit)*1e-8)]
    elif action == "inflection":
        curvature = dfirst*s.diff(dsecond, variable)-dsecond*s.diff(dfirst, variable)
        points = [point(at) for at in zeroes(numeric(curvature, variable))]
    elif action in ("derivative", "tangent"):
        current = point(a)
        require(current is not None, "Curve is undefined at this parameter")
        horizontal, vertical = dxvalue(a), dyvalue(a)
        slope = None if horizontal is None or vertical is None or abs(horizontal) < 1e-12 else vertical/horizontal
        if action == "derivative":
            payload = {"analysis":action,"points":[current]}
            if slope is None: payload["vertical"]=True
            else: payload["value"]=slope
            return payload
        direction = None if horizontal is None or vertical is None else (horizontal,vertical)
        return tangent_point(current[0], current[1], slope, direction)
    elif action == "integral":
        integrand = radius**2/2 if kind == "polar" else second*dfirst
        result = s.Integral(integrand, (variable, s.Float(a), s.Float(b))).evalf(engine.precision, strict=True)
        require(result.is_real and result.is_finite, "Numerical convergence failed")
        return {"analysis":action,"points":[],"value":float(result)}
    else:
        result = s.Integral(s.sqrt(dfirst**2+dsecond**2), (variable, s.Float(a), s.Float(b))).evalf(engine.precision, strict=True)
        require(result.is_real and result.is_finite, "Numerical convergence failed")
        return {"analysis":action,"points":[],"value":float(result)}
    found = [item for item in points if item is not None]
    points = found[:80]
    return {"analysis":action,"points":points,"count":len(points),"truncated":len(found)>80}

def programmer(request):
    width = int(request.get("width",32)); require(width in (8,16,32,64),"Invalid word size")
    base = int(request.get("base",10)); require(base in (2,8,10,16),"Invalid base")
    mask=(1<<width)-1
    def parse(text):
        require(len(str(text))<=128,"Integer too long")
        return int(str(text),base)&mask
    a=parse(request.get("a","0")); op=request.get("op","")
    b=parse(request.get("b","0")) if op else 0
    if op=="AND": a &= b
    elif op=="OR": a |= b
    elif op=="XOR": a ^= b
    elif op=="NOT": a = ~a
    elif op=="NAND": a = ~(a&b)
    elif op=="NOR": a = ~(a|b)
    elif op in ("<<",">>"):
        require(b<width,"Shift count must be less than word size")
        if op=="<<": a <<= b
        else:
            if request.get("signed",False) and a&(1<<(width-1)): a-=1<<width
            a >>= b
    elif op not in ("",): raise MathError("Unknown bit operation")
    a &= mask
    signed=a-(1<<width) if request.get("signed",False) and a&(1<<(width-1)) else a
    return {"exact":str(signed),"decimal":str(signed),"bases":{"BIN":format(a,f"0{width}b"),"OCT":format(a,"o"),"DEC":str(signed),"HEX":format(a,f"0{width//4}X")},"tree":display_tree(s.Integer(signed))}

# Symbolic calls whose cold first evaluation is heavy enough that the generic step allowance used
# to cut off legitimate work. Nested calls count too, so 1+fourier(exp(-t^2),t,w) is heavy as well.
HEAVY_CALLS=("integrate","dsolve","desolve","laplace","ilaplace","fourier","ifourier","domain","range","invt","tinterval","tvmrate","irr")
def contains_heavy_call(node):
    pending=[node]
    while pending:
        current=pending.pop()
        if not isinstance(current, dict): continue
        if current.get("kind")=="call" and current.get("value") in HEAVY_CALLS: return True
        pending.extend(current.get("args") or [])
    return False

def dispatch(payload):
    request=json.loads(payload)
    tree=request.get("tree",{})
    heavy=contains_heavy_call(tree)
    # The first evaluation of an expression also fills SymPy's caches, so a cold computation can
    # need several times the steps of a warm repeat. 1.5M and 6M steps both cut off legitimate
    # first evaluations: fourier(exp(-t^2),t,w) spends about 7.5M traced steps cold although the
    # real work takes a fraction of a second. Heavy calls keep a step ceiling above what the time
    # budget reaches on typical hardware, so the time limit stays the binding guard.
    seconds=float(request.get("budget",20 if heavy else 8))
    if heavy:
        steps=100000000
        # As-you-type previews pass two seconds; a committed heavy call (eight seconds and up) may
        # use the rest of the 20-second IPC window.
        if seconds>=8: seconds=max(seconds,16)
    else:
        steps=3000000
    budget=Budget(seconds,steps=steps)
    try:
        sys.settrace(budget.trace)
        engine=Engine(request)
        action=request.get("action","evaluate")
        if action=="constants":
            entries=[{"symbol":"pi","name":"Pi","value":"3.141592653589793…","unit":"","exact":True},{"symbol":"e","name":"Euler's number","value":"2.718281828459045…","unit":"","exact":True}]
            entries += [{"symbol":key,"name":v[0],"value":v[1] or "h / (2π)","unit":v[2],"exact":v[3]} for key,v in CONSTANTS.items()]
            result={"constants":entries,"source":"NIST CODATA 2022"}
        elif action=="graph": result=graph(engine,request)
        elif action=="graphAnalysis": result=graph_analysis(engine,request)
        elif action=="programmer": result=programmer(request)
        else:
            value=engine.build(request["tree"])
            if getattr(value,"is_number",False) and value.has(s.I): value=s.expand_complex(value)
            if isinstance(value,list) and value and all(isinstance(row,list) for row in value): value=matrix(value)
            if getattr(value,"has",lambda *_:False)(s.zoo,s.nan): raise MathError("Undefined or division by zero")
            # The result view rounds floating-point values to the display digits; the numeric work keeps engine.precision.
            display_value=display_rounded(value,engine.display_digits)
            exact=readable(display_value)
            require(len(exact)<=40000,"Result exceeds display size limit")
            decimal_value=approximate(value,engine.display_digits)
            dms_result=(is_dms_expression(request["tree"],request.get("variables",{}))
                        and getattr(value,"is_number",False) and not value.has(s.I))
            result={"exact":exact,"decimal":readable(decimal_value),"tree":display_tree(display_value),"note":engine.note,
                    "conditions":[readable(c.lhs)+" ≠ "+readable(c.rhs) if isinstance(c,s.Unequality) else str(c) for c in dict.fromkeys(engine.conditions)],"symbolic":bool(getattr(value,"free_symbols",False))}
            result["approximate"]=bool(getattr(value,"has",lambda *_:False)(s.Float))
            result["decimalTree"]=display_tree(decimal_value)
            if dms_result:
                result["tree"]=dms_tree(display_value)
                result["decimalTree"]=dms_tree(decimal_value)
                result["numericTree"]=display_tree(display_value)
                result["numericDecimalTree"]=display_tree(decimal_value)
                result["dms"]=True
            if request["tree"].get("value")=="eng" and getattr(value,"is_number",False):
                offset=engine.build(request["tree"]["args"][1]) if len(request["tree"]["args"])>1 else 0
                require(-300<=offset<=300,"Engineering exponent limit")
                exponent=(int(s.floor(s.log(s.Abs(value),10)/3))*3 if value!=0 else 0)+int(offset)
                mantissa=s.N(value/s.Integer(10)**exponent,engine.display_digits)
                power={"kind":"power","args":[{"kind":"text","value":"10"},{"kind":"text","value":str(exponent)}]}
                result["tree"]=result["decimalTree"]={"kind":"product","args":[display_tree(mantissa),power]}
            if request["tree"].get("value")=="dms" and isinstance(value,list):result["tree"]=result["decimalTree"]={"kind":"dms","args":[display_tree(x) for x in display_value]}
            if request["tree"].get("kind")=="call" and request["tree"].get("value")=="regression":
                try: result["curve"]=regression_samples(engine,value,engine.build(request["tree"]["args"][0]),request)
                except Exception: result["curve"]=[]
            try:
                ast=result_ast(value)
                if dms_result:
                    ast={"kind":"frozen_call","value":"sexagesimal","args":[result_ast(part) for part in dms_parts(value)]}
                symbols=getattr(value,"free_symbols",set())
                guards=[c for c in dict.fromkeys(engine.conditions) if c.free_symbols & symbols]
                result["resultAst"]={"kind":"restricted","args":[ast]+[result_ast(c) for c in guards]} if guards else ast
            except (MathError,TypeError,AttributeError): result["reusable"]=False
        return json.dumps({"ok":True,**result},ensure_ascii=False,allow_nan=False)
    except Exception as exc:
        message=str(exc) or type(exc).__name__
        if "NonInvertible" in type(exc).__name__: message="Singular matrix"
        elif "Shape" in type(exc).__name__: message="Matrix dimension mismatch"
        elif "Could not find root" in message: message="Numerical convergence failed. Try a different bracket or initial guess."
        return json.dumps({"ok":False,"error":message[:600]},ensure_ascii=False)
    finally:
        sys.settrace(None)
