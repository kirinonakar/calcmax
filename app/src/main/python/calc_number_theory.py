"""Number theory helpers bounded by runtime and presentation size."""
import sympy as s
from calc_shared import require

MAX_DIVISORS=2000
MAX_DIVISOR_TEXT=40000


def bounded_divisors(number):
    factors=s.factorint(number)
    count=1
    for exponent in factors.values():
        count*=int(exponent)+1
        require(count<=MAX_DIVISORS,"Too many divisors: limit is "+str(MAX_DIVISORS)+" results")
    require(len(str(number))+3<=MAX_DIVISOR_TEXT,"Divisor output exceeds 40000 characters")
    values=[s.Integer(1)]
    for prime,exponent in factors.items():
        powers=[s.Integer(prime)**i for i in range(int(exponent)+1)]
        values=[value*power for value in values for power in powers]
    size=2+2*(len(values)-1)
    for value in values:
        size+=len(str(value))
        require(size<=MAX_DIVISOR_TEXT,"Divisor output exceeds 40000 characters")
    return sorted(values)
