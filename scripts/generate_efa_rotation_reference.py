"""Independent NumPy extraction and SciPy angle maximization for the supplied CSV.

python scripts/generate_efa_rotation_reference.py build/social-reference-deps
Application modules are never imported by this development-only generator.
"""
import csv
import json
import sys
from pathlib import Path
sys.path.insert(0,sys.argv[1] if len(sys.argv)>1 else 'build/social-reference-deps')
import numpy as np
from scipy.optimize import minimize_scalar

ROOT=Path(__file__).resolve().parents[1]
with (ROOT/'tests/fixtures/efa_study_habits_sample.csv').open(encoding='utf-8-sig',newline='') as stream:
    rows=list(csv.reader(stream)); labels=rows[0]; data=np.array(rows[1:],dtype=float)
corr=np.corrcoef(data,rowvar=False); p=len(labels); cases=[]
for extraction in ('pca','pa'):
    h=1-1/np.diag(np.linalg.inv(corr))
    for _ in range(2000):
        reduced=corr.copy()
        if extraction=='pa': np.fill_diagonal(reduced,h)
        eigen,vectors=np.linalg.eigh(reduced); selected=eigen[-2:][::-1]
        load=vectors[:,-2:][:,::-1]*np.sqrt(selected)
        updated=np.sum(load*load,axis=1)
        if extraction=='pca' or max(abs(updated-h))<1e-7: break
        h=updated
    normalized=load/np.linalg.norm(load,axis=1)[:,None]
    def rotate(angle):
        c,s=np.cos(angle),np.sin(angle)
        return normalized@np.array([[c,-s],[s,c]])
    def criterion(angle):
        squared=rotate(angle)**2
        return float(np.sum(squared*squared)-np.sum(squared.sum(axis=0)**2)/p)
    # Bracket the largest maximum on a full periodic interval independently
    # of the production closed-form angle solution.
    grid=np.linspace(-np.pi/4,np.pi/4,257); best=int(np.argmax([criterion(a) for a in grid])); step=grid[1]-grid[0]
    fit=minimize_scalar(lambda angle:-criterion(angle),bounds=(grid[best]-step,grid[best]+step),method='bounded',options={'xatol':1e-14})
    rotated=rotate(fit.x)*np.linalg.norm(load,axis=1)[:,None]
    for j in range(2):
        if rotated[np.argmax(abs(rotated[:,j])),j]<0: rotated[:,j]*=-1
    cases.append({'extraction':extraction,'loadings':rotated.tolist(),'percentages':(100*selected/p).tolist(),
                  'rotatedPercentages':(100*np.sum(rotated*rotated,axis=0)/p).tolist()})
(ROOT/'tests/fixtures/efa_rotation_reference.json').write_text(json.dumps({'sources':['NumPy correlation eigendecomposition and principal-axis iteration','SciPy bounded angle maximization of the Kaiser-normalized Varimax criterion'],'cases':cases},indent=2)+'\n',encoding='utf8')
print('Wrote independent two-factor EFA references')
