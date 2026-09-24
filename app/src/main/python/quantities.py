"""Small dimension algebra. Values are expressed in coherent SI base units."""
from dataclasses import dataclass
import sympy as s

# length, mass, time, temperature, data, angle, current, amount
# The final two entries are appended so quantities persisted by older versions
# (which used a six-tuple) remain readable.
DIMENSION_NAMES=("length","mass","time","temperature","data","angle","current","amount")
BASE_NAMES=("m","kg","s","K","bit","rad","A","mol")
DIMENSIONS={
    "length":(1,0,0,0,0,0,0,0),"area":(2,0,0,0,0,0,0,0),"volume":(3,0,0,0,0,0,0,0),
    "mass":(0,1,0,0,0,0,0,0),"time":(0,0,1,0,0,0,0,0),"temperature":(0,0,0,1,0,0,0,0),
    "speed":(1,0,-1,0,0,0,0,0),"acceleration":(1,0,-2,0,0,0,0,0),"pressure":(-1,1,-2,0,0,0,0,0),
    "force":(1,1,-2,0,0,0,0,0),"energy":(2,1,-2,0,0,0,0,0),"power":(2,1,-3,0,0,0,0,0),
    "frequency":(0,0,-1,0,0,0,0,0),"data":(0,0,0,0,1,0,0,0),"angle":(0,0,0,0,0,1,0,0),
    "current":(0,0,0,0,0,0,1,0),"amount":(0,0,0,0,0,0,0,1),
    "charge":(0,0,1,0,0,0,1,0),"voltage":(2,1,-3,0,0,0,-1,0),
    "resistance":(2,1,-3,0,0,0,-2,0),"conductance":(-2,-1,3,0,0,0,2,0),
    "capacitance":(-2,-1,4,0,0,0,2,0),
    # Inductance: V*s/A.  The electrical dimensions are [M,L,T,I].
    "inductance":(2,1,-2,0,0,0,-2,0),"magnetic_flux":(2,1,-2,0,0,0,-1,0),
    "magnetic_flux_density":(0,1,-2,0,0,0,-1,0),
}
ZERO=(0,)*len(DIMENSION_NAMES)

@dataclass(frozen=True)
class Quantity:
    base: object
    dimensions: tuple
    absolute_temperature: bool=False
    def __post_init__(self):
        dimensions=tuple(int(item) for item in self.dimensions)
        if len(dimensions)==6: dimensions += (0,0)  # legacy persisted quantity
        if len(dimensions)!=len(DIMENSION_NAMES):
            raise ValueError("Unsupported physical dimension tuple")
        object.__setattr__(self,"dimensions",dimensions)
    def _algebra(self,other):
        if self.absolute_temperature or (isinstance(other,Quantity) and other.absolute_temperature):
            raise ValueError("Absolute temperatures support conversion only; use scalar temperature differences for algebra")
    @property
    def free_symbols(self): return self.base.free_symbols
    def __add__(self,other):
        self._algebra(other)
        if not isinstance(other,Quantity) or self.dimensions!=other.dimensions: raise ValueError("Unit dimension mismatch")
        return Quantity(self.base+other.base,self.dimensions)
    def __neg__(self): return Quantity(-self.base,self.dimensions,self.absolute_temperature)
    def __sub__(self,other): return self+-other
    def __mul__(self,other):
        self._algebra(other)
        if isinstance(other,Quantity): return Quantity(self.base*other.base,tuple(a+b for a,b in zip(self.dimensions,other.dimensions)))
        return Quantity(self.base*other,self.dimensions)
    __rmul__=__mul__
    def __truediv__(self,other):
        self._algebra(other)
        if isinstance(other,Quantity):
            if other.base==0: raise ValueError("Division by zero")
            dim=tuple(a-b for a,b in zip(self.dimensions,other.dimensions));value=self.base/other.base
            return value if dim==ZERO else Quantity(value,dim)
        if other==0: raise ValueError("Division by zero")
        return Quantity(self.base/other,self.dimensions)
    def __rtruediv__(self,other):
        self._algebra(other)
        if self.base==0: raise ValueError("Division by zero")
        return Quantity(other/self.base,tuple(-a for a in self.dimensions))
    def __pow__(self,power):
        self._algebra(power)
        if not power.is_Integer: raise ValueError("Quantity powers must be integers")
        return Quantity(self.base**power,tuple(int(power)*a for a in self.dimensions))
    def unit_text(self):
        return " ".join(name+("^"+str(exponent) if exponent!=1 else "") for name,exponent in zip(BASE_NAMES,self.dimensions) if exponent) or "1"

def quantity(value,name,units):
    if name not in units: raise ValueError("Unknown unit: "+name)
    dim,scale,offset=units[name]
    base=value*scale+offset
    if dim=="temperature" and base<0: raise ValueError("Temperature below absolute zero")
    return Quantity(base,DIMENSIONS[dim],dim=="temperature")

def convert_quantity(q,name,units):
    if name not in units: raise ValueError("Unknown unit: "+name)
    dim,scale,offset=units[name]
    if q.dimensions!=DIMENSIONS[dim]: raise ValueError("Unit dimension mismatch")
    return s.simplify((q.base-offset)/scale)
