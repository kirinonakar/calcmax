"""Python access to the calculator catalog's functions and argument conventions."""
import sympy as sp
from calc_engine import Engine, Quantity

x, y, z, t = sp.symbols("x y z t")
pi = sp.pi
true, false = sp.true, sp.false
left, right, linear = "left", "right", "linear"
m, cm = "m", "cm"

_names = set("""
abs floor ceil round sign sqrt cbrt nthroot log ln exp sinc sinh cosh tanh asinh acosh atanh gamma
sin cos tan asin acos atan
factorial nCr nPr gcd lcm prime isprime factorint divisors simplify expand factor collect subs diff integrate
limit series sum product solve nsolve nintegrate nderivative minimum maximum piecewise re im conj arg polar
rectpolar det inverse transpose rank trace ref rref lu linsolve eigenvalues eigenvectors dot cross norm
normalize angle projection stats mean median variance stdev quartiles regression qty convert
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
