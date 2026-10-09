import test from 'node:test';
import assert from 'node:assert/strict';
import {graphInputTree,graphExpressionTarget,isImplicitSurface,surfaceFormula,graphShadings,graphExpressions,appendGraphSource,removeGraphInputLine,createGraphInputHistory,removeGraphSource,graphSelectedCurveIndex,graphAnalysisTarget,graphAnalysisCurves} from '../graph-workspace.js';
import {curvePointAtX} from '../graph-view.js';

test('3D transfers keep implicit equations and named scaled space curves intact',()=>{
  const implicit='x^2+y^2+z^2+sin(4*x)+sin(4*y)+sin(4*z)=a';
  assert.deepEqual(graphExpressionTarget(implicit),{kind:'surface',source:implicit});
  assert.equal(isImplicitSurface(implicit),true);assert.equal(surfaceFormula(implicit),implicit);
  assert.equal(isImplicitSurface('z=sin(x)*cos(y)'),false);
  assert.deepEqual(graphExpressionTarget('z=x+z'),{kind:'surface',source:'z=x+z'});
  assert.deepEqual(graphExpressions('z=x+z','surface'),['z=x+z']);
  const curve='C(t)=4*(sin(t),cos(t),0.6*sin(2*t))';
  assert.deepEqual(graphExpressionTarget(curve),{kind:'space',source:curve});
});

test('twenty curves coexist with four shading rows and the last curve can be removed',()=>{
  const shades=Array.from({length:4},(_,i)=>`[s] ${i}<x<${i+1},y<1`);
  let source=shades.join('\n');
  for(let i=1;i<=20;i++)source=appendGraphSource(source,`x+${i}`);
  assert.equal(graphExpressions(source,'cartesian').length,20);
  assert.equal(graphShadings(source,'cartesian').length,4);
  assert.throws(()=>appendGraphSource(source,'x+21'),/Graph limit/);
  assert.equal(graphExpressions(removeGraphSource(source,19),'cartesian').length,19);
  assert.equal(graphExpressions(source+'\nx+21','cartesian').length,20);
  for(const kind of ['parametric','polar','sequence','implicit'])assert.equal(graphExpressions(Array.from({length:21},(_,i)=>String(i)).join('\n'),kind).length,20);
});

test('line deletion uses actual input lines including blanks and duplicate expressions',()=>{
  const source='x\n\nx\n[s] y<x\n';
  assert.equal(removeGraphInputLine(source,1),'x\nx\n[s] y<x\n');
  assert.equal(removeGraphInputLine(source,2),'x\n\n[s] y<x\n');
  assert.equal(removeGraphInputLine(source,4),'x\n\nx\n[s] y<x');
  assert.equal(removeGraphInputLine('x',0),'');
  assert.equal(removeGraphInputLine(source,20),source);
  const history=createGraphInputHistory();history.remember('cartesian',{source});
  assert.equal(history.undo('cartesian').source,source);
});

test('named piecewise functions and unrestricted-parenthesis exponential inputs retain restriction scope',()=>{
  const named=graphInputTree('f(x)={x<0:x^2,x>=0:2*x}');assert.equal(named.kind,'piecewise');
  assert.deepEqual(named.args.map(branch=>branch.args[0].value),['^','*']);
  for(const source of ['y=(1-e^(-x/900)){0<=x<=3000}','y=1-e^(-x/900) {0<=x<=3000}']){
    const tree=graphInputTree(source),restriction=tree.args[1];assert.equal(restriction.kind,'piecewise');
    assert.equal(restriction.value,'restriction');assert.equal(restriction.args[0].args[1].args[0].value,'<=');
  }
  const tail=graphInputTree('y=(1-e^(-3000/900))*e^(-(x-3000)/80){x>3000}').args[1];
  assert.equal(tail.args[0].args[0].value,'*');
});

test('intersection choices include original and both derivatives with distinct analysis targets',()=>{
  const curves=graphAnalysisCurves(1,0,0);
  assert.deepEqual(curves,[{key:0,label:'f1'},{key:-1,label:'f1′'},{key:-2,label:'f1″'}]);
  assert.deepEqual(curves.map(curve=>graphAnalysisTarget(curve.key,0,0)),[{source:0,order:0},{source:0,order:1},{source:0,order:2}]);
  assert.deepEqual(graphAnalysisCurves(1,null,0),[{key:0,label:'f1'},{key:-2,label:'f1″'}]);
  assert.deepEqual(graphAnalysisCurves(1,2,null),[{key:0,label:'f1'}]);
  assert.equal(graphAnalysisTarget(-1,null,0),null);assert.equal(graphAnalysisTarget(-2,0,null),null);
});

test('selected derivatives trace their own samples and keep the correct curve when indices shift',()=>{
  const curves=[[[-1,-1],[1,1]],[[-1,2],[1,2]],[[-1,6],[1,6]]];
  for(const [order,expected] of [[0,.5],[1,2],[2,6]]){
    const index=graphSelectedCurveIndex(0,order,1,2);
    assert.deepEqual(curvePointAtX(curves[index],.5,0,{fallbackToNearest:false}),[.5,expected]);
  }
  const withoutFirst=[curves[0],curves[2]];
  assert.deepEqual(curvePointAtX(withoutFirst[graphSelectedCurveIndex(0,2,-1,1)],.5,0),[.5,6]);
  assert.equal(graphSelectedCurveIndex(0,1),-1);assert.equal(graphSelectedCurveIndex(0,2),-1);
});

test('graph undo restores deleted curves, shading, derivatives and parameter settings',()=>{
  const history=createGraphInputHistory();
  let current={source:'a*x\ncos(x)\n[s] x, 2, -1..1',derivative:0,secondDerivative:1,parameters:{a:2.5},parameterRanges:{a:[-5,5]}};
  const original=structuredClone(current);
  history.remember('cartesian',current);
  current.source=removeGraphSource(current.source,0,'cartesian',true);current.derivative=null;current.secondDerivative=null;
  history.remember('cartesian',current);
  current.source=removeGraphSource(current.source,0);
  history.remember('cartesian',current);
  current.source=removeGraphSource(current.source,0);current.parameters.a=99;
  assert.equal(current.source,'');
  assert.equal(history.canUndo('polar'),false);
  assert.equal(history.undo('cartesian').source,'cos(x)');
  assert.equal(history.undo('cartesian').source,'a*x\ncos(x)');
  assert.deepEqual(history.undo('cartesian'),original);
  assert.equal(history.canUndo('cartesian'),false);
  assert.equal(history.undo('cartesian'),undefined);
});

test('second derivative toggles can be undone independently of the first derivative',()=>{
  const history=createGraphInputHistory(),before={source:'x^3',derivative:0,secondDerivative:null};
  history.remember('cartesian',before);
  history.remember('cartesian',{...before,secondDerivative:0});
  assert.equal(history.undo('cartesian').secondDerivative,0);
  assert.deepEqual(history.undo('cartesian'),before);
});

test('shading bands combine x and y restrictions regardless of input order',()=>{
  for(const source of ['sin(x), cos(x), -1<x<2, y<0','y<0, -1<x<2, sin(x), cos(x)']){
    const [item]=graphShadings(`[s] ${source}`,'cartesian');
    assert.equal(item.mode,'band');assert.deepEqual(item.trees.map(tree=>tree.value),['sin','cos']);
    assert.deepEqual(item.xBounds.map(bound=>bound.side),['lower','upper']);
    assert.equal(item.yBounds.length,1);assert.equal(item.yBounds[0].side,'upper');assert.equal(item.yBounds[0].tree.value,'0');
  }
  const [item]=graphShadings('[shade] x, 2, -1<y<0','cartesian');
  assert.deepEqual(item.yBounds.map(bound=>bound.side),['lower','upper']);
});
