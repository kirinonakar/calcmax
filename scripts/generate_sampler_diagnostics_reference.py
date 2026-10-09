"""Independent ArviZ rank R-hat / bulk, tail and mean ESS fixtures.

Run with ArviZ 0.22+, NumPy and SciPy installed in a reference environment.
The application itself does not depend on these libraries.
"""
import json
import math
import random
from pathlib import Path
import arviz as az
import numpy as np

rng=random.Random(812)
cases=[]
for name in ('normal','shifted','scale mismatch','ties','autocorrelated','heavy tails'):
    chains=[]
    for chain_id in range(4):
        values=[]; state=0.0
        for _ in range(128):
            value=rng.gauss(0,1)
            if name=='shifted': value+=chain_id*.6
            if name=='scale mismatch': value*=1+chain_id*2
            if name=='ties': value=round(value)
            if name=='autocorrelated': state=.92*state+value; value=state
            if name=='heavy tails': value=math.tan(math.pi*(rng.random()-.5))
            values.append(value)
        chains.append(values)
    array=np.array(chains)
    cases.append({'name':name,'chains':chains,'expected':{'rHat':float(az.rhat(array)),
        'bulkEss':float(az.ess(array,method='bulk')),'tailEss':float(az.ess(array,method='tail')),
        'ess':float(az.ess(array,method='mean'))}})
destination=Path(__file__).resolve().parents[1]/'tests/fixtures/sampler_diagnostics_reference.json'
destination.write_text(json.dumps({'source':'ArviZ '+az.__version__,'cases':cases},indent=2)+'\n')
