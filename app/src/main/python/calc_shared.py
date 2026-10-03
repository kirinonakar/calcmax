"""Validation, limits, units, constants, and shared math helpers."""
import sys
from calc_runtime import Budget
import sympy as s
from sympy.core.relational import Relational
from sympy.core.function import AppliedUndef

# Exact integers (e.g. factorial) are serialized to text; CPython 3.11+ caps
# int -> str conversion at 4300 digits, which is below the display limit used
# below. Raise it so the advertised range (factorial up to 10000) is usable.
if hasattr(sys, "set_int_max_str_digits"):
    sys.set_int_max_str_digits(100000)

class MathError(ValueError):
    pass

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

def numeric_integral(expression, variable, lower, upper, precision):
    """Use exact polynomial antiderivatives; otherwise use adaptive quadrature."""
    lower, upper = s.sympify(lower), s.sympify(upper)
    if lower.is_finite and upper.is_finite and expression.is_polynomial(variable):
        # Strict quadrature cannot establish relative accuracy for an integral
        # that cancels to zero. Keep the stored float values exactly as rationals
        # during subtraction, so small nonzero integrals are also preserved.
        exact = expression.xreplace({value: s.Rational(value) for value in expression.atoms(s.Float)})
        lower = lower.xreplace({value: s.Rational(value) for value in lower.atoms(s.Float)})
        upper = upper.xreplace({value: s.Rational(value) for value in upper.atoms(s.Float)})
        primitive = s.Poly(exact, variable).integrate()
        return (primitive.eval(upper)-primitive.eval(lower)).evalf(precision, strict=True)
    return s.Integral(expression, (variable, lower, upper)).evalf(precision, strict=True)


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


def z_transform(expression, sequence, transform):
    """Unilateral Z-transform sum f(n) z^-n over n = 0..oo.

    SymPy has no closed-form Z-transform, so the unilateral definition is
    evaluated directly as a summation and any convergence condition returned
    with a Piecewise result is reported as a note.
    """
    try:
        result = s.summation(expression/transform**sequence, (sequence, 0, s.oo))
    except Exception:
        raise MathError("No closed form was found for this Z-transform")
    condition = s.true
    if isinstance(result, s.Piecewise):
        branch = result.args[0]
        result, condition = branch[0], branch[1]
    if result.has(s.Sum):
        raise MathError("No closed form was found for this Z-transform")
    result = s.simplify(result)
    notes = []
    if condition is not s.true and condition is not True and condition is not None:
        notes.append("Convergence: " + str(condition))
    return result, " · ".join(notes)


def inverse_z_transform(expression, transform, sequence):
    """Inverse unilateral Z-transform from the residues of F(z) z^(n-1).

    This is the standard causal inverse for a rational Z-domain function: each
    pole contributes its residue, so repeated poles are handled by SymPy's own
    residue computation.
    """
    expression = s.cancel(expression)
    denominator = s.fraction(expression)[1]
    require(denominator.has(transform), "The inverse Z-transform needs a rational function of the transform variable")
    try:
        poles = s.roots(denominator, transform)
    except Exception:
        poles = {}
    require(poles, "Could not factor the denominator of the Z-domain expression")
    require(sum(poles.values()) <= 24, "Too many poles for the inverse Z-transform")
    total = s.Integer(0)
    for pole in poles:
        total += s.residue(expression*transform**(sequence-1), transform, pole)
    total = s.simplify(total)
    require(not total.has(s.Sum, s.residue), "Inverse Z-transform did not reduce to a closed form")
    return total


def mellin_transform(expression, variable, transform):
    """Mellin transform that reports its fundamental strip as a note."""
    result = s.mellin_transform(expression, variable, transform)
    value = result[0]
    strip = result[1] if len(result) > 1 else None
    condition = result[2] if len(result) > 2 else s.true
    details = []
    if strip is not None: details.append("Fundamental strip: " + str(strip))
    if condition is not s.true and condition is not True and condition is not None:
        details.append("Convergence: " + str(condition))
    return value, " · ".join(details)


def inverse_mellin_transform(expression, transform, variable, strip=None):
    """Inverse Mellin transform; a conventional strip is inferred when omitted."""
    if strip is not None:
        return s.inverse_mellin_transform(expression, transform, variable, strip), strip
    for candidate in ((0, s.oo), (0, 1), (-1, 0), (s.Rational(1, 2), s.oo), (-s.oo, 0)):
        try:
            return s.inverse_mellin_transform(expression, transform, variable, candidate), candidate
        except Exception:
            continue
    raise MathError("Inverse Mellin transform failed. Pass the convergence strip as two extra arguments.")

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
