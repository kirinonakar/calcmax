"""Python access to the calculator catalog's functions and argument conventions."""
import sympy as sp
from calc_engine import Engine, Quantity

x, y, z, t, u, v, w, s = sp.symbols("x y z t u v w s")
pi = sp.pi
true, false = sp.true, sp.false
left, right, both, linear = "left", "right", "both", "linear"
begin, end = "begin", "end"
m, cm = "m", "cm"
_names = set("""
abs floor ceil round roundh sign sqrt cbrt nthroot atan2 arctan2 frac iPart log ln exp sinc sinh cosh tanh asin acos atan arcsin arccos arctan sin cos tan
asinh acosh atanh arcsinh arsinh arccosh arcosh arctanh artanh gamma erf erfc Ei Si Ci zeta factorial nCr nPr gcd lcm prime isprime factorint divisors
rnd eng pol rec randInt sexagesimal dms mixed quotient remainder mod divmod sumdata
simplify expand factor collect subs diff integrate limit series taylor sum product solve nsolve nintegrate
nderivative minimum maximum piecewise apart partfrac together cancel trigsimp trigexpand powsimp powdenest hyperexpand
nsimplify comDenom numden coeff quo rem resultant discriminant domain range
re im conj arg polar rectpolar
det inverse transpose rank trace ref rref lu linsolve eigenvalues eigenvectors dot cross norm normalize angle projection
charpoly identity diag qr cholesky nullspace cofactor adjugate rowspace singularvalues frob jordan dim
gradient divergence curl hessian jacobian laplacian
dsolve desolve laplace ilaplace fourier ifourier fft ifft
stats mean median variance stdev quartiles regression covariance correlation qty convert
normpdf normalcdf normcdf normalpdf invnorm tpdf tcdf invt chi2pdf chi2cdf fpdf fcdf binompdf binomcdf poissonpdf poissoncdf geometpdf geometcdf
ttest ttest2 ttestpaired ztest ztest2 chi2test chi2independence fisherexact anova shapiro tinterval zinterval
tvmfv tvmpv tvmpmt tvmn tvmrate npv irr amort cagr
""".split())
_functions = {}
_variables = {}
_assumptions = {}


def set_context(functions=None, variables=None, assumptions=None):
    global _functions, _variables, _assumptions
    _functions = functions or {}
    _variables = variables or {}
    _assumptions = assumptions or {}


def _sympify(value):
    if isinstance(value, Quantity): return value
    if isinstance(value, list): return [_sympify(item) for item in value]
    if isinstance(value, tuple): return tuple(_sympify(item) for item in value)
    return sp.sympify(value)


def __getattr__(name):
    if name not in _names and name not in _functions:
        raise AttributeError("Unknown catalog function: " + name)

    def calculate(*args):
        engine = Engine({"angle":"RAD", "functions":_functions, "variables":_variables, "assumptions":_assumptions})
        values = [_sympify(value) for value in args]
        nodes = [{"kind":"symbol", "value":str(value)} for value in args]
        return engine.call(name, values, nodes)

    calculate.__name__ = name
    return calculate
