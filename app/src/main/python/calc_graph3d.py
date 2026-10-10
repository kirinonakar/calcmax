"""Indexed isosurfaces from a bounded volume grid, shared by Android and WASM."""
import math
from functools import lru_cache


@lru_cache(maxsize=3)
def unit_sphere_mesh(level=3):
    """Uniform, consistently outward triangles; reused for every ellipsoid frame."""
    phi=(1+math.sqrt(5))/2
    vertices=[[-1,phi,0],[1,phi,0],[-1,-phi,0],[1,-phi,0],[0,-1,phi],[0,1,phi],
              [0,-1,-phi],[0,1,-phi],[phi,0,-1],[phi,0,1],[-phi,0,-1],[-phi,0,1]]
    vertices=[[v/math.hypot(*p) for v in p] for p in vertices]
    faces=[(0,11,5),(0,5,1),(0,1,7),(0,7,10),(0,10,11),(1,5,9),(5,11,4),(11,10,2),
           (10,7,6),(7,1,8),(3,9,4),(3,4,2),(3,2,6),(3,6,8),(3,8,9),(4,9,5),(2,4,11),
           (6,2,10),(8,6,7),(9,8,1)]
    for _ in range(level):
        edges={};next_faces=[]
        def midpoint(a,b):
            key=(min(a,b),max(a,b))
            if key not in edges:
                p=[(u+v)/2 for u,v in zip(vertices[a],vertices[b])];length=math.hypot(*p)
                edges[key]=len(vertices);vertices.append([v/length for v in p])
            return edges[key]
        for a,b,c in faces:
            ab,bc,ca=midpoint(a,b),midpoint(b,c),midpoint(c,a)
            next_faces.extend(((a,ab,ca),(b,bc,ab),(c,ca,bc),(ab,bc,ca)))
        faces=next_faces
    return tuple(tuple(p) for p in vertices),tuple(faces)


def ellipsoid_samples(center,radii,count):
    directions,faces=unit_sphere_mesh(3 if count>=16 else 2)
    vertices=[[center[i]+radii[i]*p[i] for i in range(3)] for p in directions]
    normals=[]
    for p in directions:
        n=[p[i]/radii[i] for i in range(3)];length=math.hypot(*n)
        normals.append([v/length for v in n])
    return vertices,faces,normals

_TETRAHEDRA = ((0,5,1,6),(0,1,2,6),(0,2,3,6),(0,3,7,6),(0,7,4,6),(0,4,5,6))


def _case(mask):
    inside=[i for i in range(4) if mask & (1 << i)]
    outside=[i for i in range(4) if not mask & (1 << i)]
    if len(inside)==1:
        return [tuple((inside[0],j) for j in outside)]
    if len(inside)==3:
        return [tuple((outside[0],j) for j in reversed(inside))]
    if len(inside)==2:
        a,b=inside;c,d=outside
        return [((a,c),(b,c),(b,d)),((a,c),(b,d),(a,d))]
    return []


_CASES=tuple(_case(mask) for mask in range(16))


def space_coordinate_trees(tree):
    """Expand scalar/vector operations without treating a tuple as repetition."""
    kind=tree.get('kind');args=tree.get('args',[])
    if kind=='group':return space_coordinate_trees(args[0])
    if kind=='relation' and tree.get('value') in ('=','==') and args[0].get('kind')=='call':
        return space_coordinate_trees(args[1])
    if kind in ('list','tuple') and len(args)==3:return args
    if kind=='unary':
        vector=space_coordinate_trees(args[0])
        return [{**tree,'args':[coordinate]} for coordinate in vector] if vector else None
    if kind=='binary':
        a,b=args;left,right=space_coordinate_trees(a),space_coordinate_trees(b);op=tree.get('value')
        if left and right and op in ('+','-'):
            return [{**tree,'args':[u,v]} for u,v in zip(left,right)]
        if left and not right and op in ('*','/'):
            return [{**tree,'args':[coordinate,b]} for coordinate in left]
        if right and not left and op=='*':
            return [{**tree,'args':[a,coordinate]} for coordinate in right]
    return None


def space_curve_samples(function,start,end,count,bounds):
    count=max(100,min(512,count));values={}
    def point(t):
        try:
            p=list(map(float,function(t)))
            return p if len(p)==3 and all(math.isfinite(v) and abs(v)<1e100 for v in p) else None
        except (ValueError,TypeError,ZeroDivisionError,OverflowError):return None
    for i in range(count+1):
        at=start+(end-start)*i/count;values[at]=point(at)
    def refine(left,right,depth):
        if depth>=8 or len(values)>=1800:return
        middle=(left+right)/2
        if middle in (left,right):return
        a,b=values[left],values[right];actual=point(middle)
        if a is None and b is None and actual is None:return
        split=a is None or b is None or actual is None
        if not split:
            error=max(abs(actual[i]-(a[i]+b[i])/2)*800/(high-low) for i,(low,high) in enumerate(bounds))
            split=error>.25
        if split:
            values[middle]=actual;refine(left,middle,depth+1);refine(middle,right,depth+1)
    coarse=sorted(values)
    for left,right in zip(coarse,coarse[1:]):refine(left,right,0)
    positions=sorted(values)
    return [values[t] for t in positions],positions


def implicit_surface_samples(function, bounds, count):
    """March tetrahedra consistently across cells; undefined cells remain holes."""
    count=max(12,min(32,count))
    axes=[[low+(high-low)*i/count for i in range(count+1)] for low,high in bounds]
    n=count+1;plane=n*n
    points=[];values=[]
    for z in axes[2]:
        for y in axes[1]:
            for x in axes[0]:
                points.append([x,y,z])
                try:
                    value=float(function(x,y,z))
                    values.append(value if math.isfinite(value) else None)
                except (ValueError,TypeError,ZeroDivisionError,OverflowError):
                    values.append(None)
    vertices=[];triangles=[];edges={}
    def evaluate(point):
        try:
            value=float(function(*point))
            return value if math.isfinite(value) else None
        except (ValueError,TypeError,ZeroDivisionError,OverflowError):return None
    def crossing(first,last):
        a,b=values[first],values[last]
        key=(first,first) if a==0 else (last,last) if b==0 else (min(first,last),max(first,last))
        if key in edges:return edges[key]
        # Scale to avoid overflow when opposite residuals are very large.
        scale=max(abs(a),abs(b));aa,bb=a/scale,b/scale
        t=aa/(aa-bb)
        point=[u+(v-u)*t for u,v in zip(points[first],points[last])]
        # Keep shared edge topology, but solve the actual function along the
        # edge instead of leaving the vertex on a linear field approximation.
        if a!=0 and b!=0:
            low,high=0.0,1.0;lo,hi=a,b
            initial=evaluate(point)
            best=abs(initial) if initial is not None else math.inf;best_point=point
            for _ in range(10):
                value=evaluate(point)
                if value is None:break
                if abs(value)<=1e-10*max(1.0,min(abs(a),abs(b))):break
                if abs(value)<best:best,best_point=abs(value),point
                if (value<0)==(lo<0):low,lo=t,value;hi*=.5
                else:high,hi=t,value;lo*=.5
                scale=max(abs(lo),abs(hi));aa,bb=lo/scale,hi/scale
                t=low+(high-low)*aa/(aa-bb)
                point=[u+(v-u)*t for u,v in zip(points[first],points[last])]
            value=evaluate(point)
            if value is None or abs(value)>best:point=best_point
        index=len(vertices);vertices.append(point);edges[key]=index
        return index
    offsets=(0,1,n+1,n,plane,plane+1,plane+n+1,plane+n)
    for z in range(count):
        for y in range(count):
            for x in range(count):
                base=z*plane+y*n+x
                cell=tuple(base+offset for offset in offsets)
                samples=tuple(values[i] for i in cell)
                if any(v is None for v in samples):continue
                if min(samples)>=0 or max(samples)<0:continue
                for tetrahedron in _TETRAHEDRA:
                    ids=tuple(cell[i] for i in tetrahedron)
                    mask=sum(1 << i for i,index in enumerate(ids) if values[index]<0)
                    for triangle in _CASES[mask]:
                        face=[crossing(ids[a],ids[b]) for a,b in triangle]
                        if len(set(face))==3:triangles.append(face)
    # The gradient belongs to the mathematical surface, not to one triangle.
    # Sharing these normals makes lighting continuous across tessellation edges.
    normals=[]
    for point in vertices:
        gradient=[]
        center=evaluate(point)
        for axis,(low,high) in enumerate(bounds):
            step=max((high-low)/count*1e-4,math.ulp(point[axis])*16,1e-12)
            before=point.copy();after=point.copy();before[axis]-=step;after[axis]+=step
            a,b=evaluate(before),evaluate(after)
            gradient.append((b-a)/(2*step) if a is not None and b is not None else
                            (b-center)/step if b is not None and center is not None else
                            (center-a)/step if a is not None and center is not None else 0.0)
        length=math.hypot(*gradient)
        normals.append([v/length for v in gradient] if math.isfinite(length) and length>0 else [0.0,0.0,0.0])
    return vertices,triangles,count,normals
