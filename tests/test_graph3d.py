import collections
import json
import math
import pathlib
import sys
import unittest

sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]/'app/src/main/python'))
from calc_engine import dispatch


def num(value):return {'kind':'number','value':str(value)}
def sym(value):return {'kind':'symbol','value':value}
def binary(op,a,b):return {'kind':'binary','value':op,'args':[a,b]}
def call(name,*args):return {'kind':'call','value':name,'args':list(args)}
def equation(a,b):return {'kind':'relation','value':'=','args':[a,b]}
def add(*terms):
    result=terms[0]
    for term in terms[1:]:result=binary('+',result,term)
    return result


class Graph3dTests(unittest.TestCase):
    def run_graph(self,tree,kind='surface',**options):
        result=json.loads(dispatch(json.dumps({'action':'graph','graphKind':kind,'trees':[tree],
            'min':-2,'max':2,'surfaceYMin':-2,'surfaceYMax':2,'surfaceZMin':-2,'surfaceZMax':2,'surfaceSamples':20,**options})))
        self.assertTrue(result['ok'],result.get('error'))
        return result

    def test_sphere_has_both_z_branches_and_a_closed_shared_vertex_mesh(self):
        squared=add(*(binary('^',sym(axis),num(2)) for axis in 'xyz'))
        tree=equation(squared,sym('a'))
        for a in (1,2):
            result=self.run_graph(tree,parameters={'a':a})
            vertices=result['surfaceVertices'];faces=result['surfaceTriangles']
            self.assertEqual(['a'],result['parameters']);self.assertTrue(faces)
            self.assertLess(min(p[2] for p in vertices),-.9*math.sqrt(a));self.assertGreater(max(p[2] for p in vertices),.9*math.sqrt(a))
            for point in vertices:self.assertAlmostEqual(a,sum(v*v for v in point),delta=.04)
            edges=collections.Counter(tuple(sorted((face[i],face[(i+1)%3]))) for face in faces for i in range(3))
            self.assertTrue(all(count==2 for count in edges.values()))
            self.assertLess(len(vertices),len(faces))

    def test_user_periodic_surface_parameters_and_explicit_surface_compatibility(self):
        terms=[binary('^',sym(axis),num(2)) for axis in 'xyz']+[call('sin',binary('*',num(4),sym(axis))) for axis in 'xyz']
        tree=equation(add(*terms),sym('a'))
        result=self.run_graph(tree,parameters={'a':1},surfaceSamples=24)
        self.assertTrue(result['implicitSurface']);self.assertTrue(result['surfaceTriangles'])
        self.assertEqual(['a'],result['parameters'])
        for point in result['surfaceVertices']:
            residual=sum(v*v+math.sin(4*v) for v in point)-1
            self.assertLess(abs(residual),.35)
        explicit=self.run_graph(equation(sym('z'),binary('+',sym('x'),sym('y'))))
        self.assertNotIn('implicitSurface',explicit)
        for row in explicit['surface']:
            for x,y,z in row:self.assertAlmostEqual(x+y,z)
        plane=self.run_graph(equation(sym('z'),binary('+',sym('x'),sym('z'))))
        self.assertTrue(plane['implicitSurface'])
        self.assertTrue(all(abs(p[0])<1e-12 for p in plane['surfaceVertices']))

    def test_no_surface_and_undefined_cells_do_not_create_fake_faces(self):
        empty=self.run_graph(equation(sym('z'),binary('+',sym('z'),num(1))))
        self.assertEqual([],empty['surfaceTriangles'])
        tree=equation(add(binary('/',num(1),sym('x')),sym('y'),sym('z')),num(0))
        result=self.run_graph(tree)
        for face in result['surfaceTriangles']:
            xs=[result['surfaceVertices'][i][0] for i in face]
            self.assertFalse(min(xs)<0<max(xs))

    def test_named_scaled_space_curve_preserves_coordinates_period_and_sliders(self):
        t=sym('t');vector={'kind':'tuple','args':[call('sin',t),call('cos',t),binary('*',num(.6),call('sin',binary('*',num(2),t)))]}
        tree=equation(call('C',t),binary('*',sym('r'),vector))
        result=self.run_graph(tree,kind='space',min=0,max=2*math.pi,parameters={'r':4})
        self.assertEqual(['r'],result['parameters'])
        curve=result['spaceCurves'][0];positions=result['curveParameters'][0]
        for point,at in zip(curve,positions):
            for value,expected in zip(point,(4*math.sin(at),4*math.cos(at),2.4*math.sin(2*at))):self.assertAlmostEqual(expected,value,delta=1e-12)
        for a,b in zip(curve[0],curve[-1]):self.assertAlmostEqual(a,b,delta=1e-12)


if __name__=='__main__':unittest.main()
