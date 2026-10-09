"""Fixed-width integer operations for the programmer workspace."""
from calc_limits import within_limit
import sympy as s
from calc_shared import MathError, require
from calc_display import display_tree

def programmer(request):
    width = int(request.get("width",32)); require(width in (8,16,32,64),"Invalid word size")
    base = int(request.get("base",10)); require(base in (2,8,10,16),"Invalid base")
    mask=(1<<width)-1
    def parse(text):
        require(within_limit(len(str(text)),128),"Integer too long")
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
