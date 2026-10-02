import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {JSDOM} from 'jsdom';
import {loadPyodide} from '../vendor/pyodide.mjs';
import {installEngine} from '../engine-bootstrap.js';
import {createMatrixWorkspace} from '../matrix-workspace.js';
import {parse} from '../parser.js';
import {astSource} from '../ast-source.js';
import {setLanguage,translateDOM} from '../i18n.js';
import {renderFormulas} from '../formula-preview.js';

test('matrix/vector controls, saved values and calculations use the shared WASM engine',async t=>{
  const dom=new JSDOM(readFileSync(new URL('../index.html',import.meta.url),'utf8'));
  globalThis.document=dom.window.document;globalThis.NodeFilter=dom.window.NodeFilter;
  t.after(()=>{setLanguage('en');dom.window.close();});
  const $=id=>document.getElementById(id),state={fields:{'matrix-op':'add'},matrixCells:{},variables:{},digits:10};
  const errors=[],changes=[];let saves=0;
  const py=await loadPyodide({indexURL:fileURLToPath(new URL('../vendor/',import.meta.url))});
  await installEngine(py,{runtimeURL:new URL('../vendor/',import.meta.url),engineURL:new URL('../engine.zip',import.meta.url),fetcher:async url=>new Response(readFileSync(url))});
  function evaluate(source) {
    py.globals.set('payload',JSON.stringify({tree:parse(source),angle:'RAD',variables:state.variables}));
    return JSON.parse(py.runPython('calc_engine.dispatch(payload)'));
  }
  const workspace=createMatrixWorkspace({state,persist:()=>saves++,restoreSelect:()=>{},refreshWorkspaceMath:()=>renderFormulas($('vector-other-math'),[workspace.operandExpression()]),error:message=>errors.push(message),
    changeMode:mode=>{$('mode').value=mode;changes.push(mode);},replaceInput:()=>{},
    storeExpression:async(name,source)=>{const result=evaluate(source);assert.equal(result.ok,true,result.error);state.variables[name]=result.resultAst;}});
  function grid(mode,rows,columns,cells) {
    $('mode').value=mode;$('matrix-rows').value=String(rows);$('matrix-cols').value=String(columns);workspace.render();
    [...$('matrix-grid').querySelectorAll('input')].forEach((input,index)=>{input.value=String(cells[index]);input.dispatchEvent(new dom.window.Event('input'));});
  }
  function operation(op,other='') {$('matrix-op').value=op;$('vector-other').value=other;$('matrix-op').dispatchEvent(new dom.window.Event('change'));}
  function result(expected) {const source=workspace.command(),actual=evaluate(source),reference=evaluate(expected);assert.equal(actual.ok,true,`${source}: ${actual.error}`);assert.equal(reference.ok,true,reference.error);assert.deepEqual(actual.resultAst,reference.resultAst);return actual;}

  await t.test('matrix arithmetic preserves names and saved operation; supports rectangular multiplication and scalars',()=>{
    grid('matrix',2,2,[1,2,3,4]);assert.equal($('matrix-op').value,'add');
    for(const name of ['add','subtract','multiply'])assert.equal([...$('matrix-op').options].find(option=>option.value===name).textContent,name);
    operation('add','[[5,6],[7,8]]');result('[[6,8],[10,12]]');
    operation('subtract','[[5,6],[7,8]]');result('[[-4,-4],[-4,-4]]');
    operation('multiply','[[5,6],[7,8]]');result('[[19,22],[43,50]]');
    operation('multiply','2');result('[[2,4],[6,8]]');
    operation('divide','2');result('[[1/2,1],[3/2,2]]');
    grid('matrix',2,3,[1,2,3,4,5,6]);operation('multiply','[[1,2],[3,4],[5,6]]');result('[[22,28],[49,64]]');
    operation('add','[[1,2],[3,4]]');assert.equal(evaluate(workspace.command()).ok,false);
    operation('divide','0');assert.equal(evaluate(workspace.command()).ok,false);
  });

  await t.test('vector arithmetic and Android vector operations calculate with literals and saved operands',()=>{
    grid('vector',3,1,[1,2,3]);
    operation('add','[4,5,6]');result('[[5],[7],[9]]');
    operation('subtract','[4,5,6]');result('[[-3],[-3],[-3]]');
    operation('multiply','2');result('[[2],[4],[6]]');assert.equal($('matrix-other-title').textContent,'Scalar (k)');
    operation('divide','2');result('[[1/2],[1],[3/2]]');
    state.variables.B=parse('[4,5,6]');operation('add','B');result('[[5],[7],[9]]');
    state.variables.k=parse('3');operation('multiply','k');result('[[3],[6],[9]]');
    operation('dot','B');result('32');operation('cross','B');result('[[-3],[6],[-3]]');
    grid('vector',3,1,[1,0,0]);operation('angle','[0,1,0]');result('pi/2');
    operation('projection','[1,1,0]');result('[[1/2],[1/2],[0]]');
    operation('norm');result('1');assert.equal($('vector-other-label').hidden,true);
    operation('normalize');result('[[1],[0],[0]]');
    operation('add','[1,2]');assert.equal(evaluate(workspace.command()).ok,false);
  });

  await t.test('second operand selection previews the saved value and calculates without replacing the grid',()=>{
    state.variables={M:parse('[[5,6],[7,8]]'),B:parse('[4,5,6]'),k:parse('2')};
    grid('matrix',2,2,[1,2,3,4]);operation('add');
    const choose=name=>{$('matrix-other-name').value=name;$('matrix-other-name').dispatchEvent(new dom.window.Event('change'));};
    assert.deepEqual([...$('matrix-other-name').options].map(option=>option.value),['','B','M','k']);
    choose('M');assert.equal(workspace.expression(),'[[1,2],[3,4]]');assert.equal($('vector-other').value,'M');assert.equal($('vector-other').hidden,true);
    assert.equal(workspace.operandExpression(),'M=[[5,6],[7,8]]');assert.ok($('vector-other-math').querySelector('mtable'));assert.match($('vector-other-math').textContent,/M.*5.*6.*7.*8/);
    result('[[6,8],[10,12]]');
    operation('multiply','M');choose('k');result('[[2,4],[6,8]]');assert.match($('vector-other-math').textContent,/k.*2/);
    grid('vector',3,1,[1,2,3]);operation('dot');choose('B');result('32');assert.equal(workspace.expression(),'[1,2,3]');
    assert.match($('vector-other-math').textContent,/B.*4.*5.*6/);
    state.variables.B=parse('[7,8,9]');$('dialog').dispatchEvent(new dom.window.Event('close'));
    assert.equal($('matrix-other-name').value,'B');assert.match($('vector-other-math').textContent,/B.*7.*8.*9/);result('50');
    choose('');assert.equal($('vector-other').hidden,false);
    $('vector-other').value='[3,2,1]';$('vector-other').dispatchEvent(new dom.window.Event('input'));
    assert.equal($('matrix-other-name').value,'');assert.equal(workspace.operandExpression(),'[3,2,1]');result('10');
    setLanguage('ko');workspace.render();assert.equal($('matrix-other-name').options[0].textContent,'직접 입력');setLanguage('en');
  });

  await t.test('saved list previews matrices/vectors, loads cells and switches to the required workspace',async()=>{
    state.variables={scalar:parse('5'),M:parse('[[1,2],[3,4]]'),V:parse('[1/2,x,3]'),Column:parse('[[5],[7],[9]]'),Row:parse('[[2,4,6]]'),Invalid:parse('[[1],[2,3]]'),Empty:parse('[]'),Large:parse('['+Array.from({length:10},(_,i)=>i+1).join(',')+']')};
    workspace.render();
    assert.equal($('matrix-name').tagName,'SELECT');
    assert.deepEqual([...$('matrix-name').options].map(option=>option.value),['A','B','C','Column','Large','M','Row','V','']);
    assert.equal([...$('matrix-name').options].find(option=>option.value==='Large').disabled,true);
    assert.deepEqual([...$('matrix-saved-list').querySelectorAll('button')].map(button=>button.dataset.variable),['Column','Large','M','Row','V']);
    assert.ok($('matrix-saved-list').querySelector('math'));assert.match($('matrix-saved-list').textContent,/M · Matrix · 2 × 2/);
    const load=name=>$('matrix-saved-list').querySelector(`[data-variable="${name}"]`).click();
    const select=name=>{$('matrix-name').value=name;$('matrix-name').dispatchEvent(new dom.window.Event('change'));};
    assert.equal($('matrix-saved-list').querySelector('[data-variable="Large"]').disabled,true);
    select('M');assert.equal($('mode').value,'matrix');assert.equal(workspace.expression(),'[[1,2],[3,4]]');
    select('V');assert.equal($('mode').value,'vector');assert.equal(workspace.expression(),'[(1/2),x,3]');assert.equal($('matrix-name').value,'V');
    load('Column');assert.equal(workspace.expression(),'[5,7,9]');
    load('Row');assert.equal(workspace.expression(),'[2,4,6]');
    operation('add','[1,1,1]');result('[[3],[5],[7]]');
    select('');assert.equal($('matrix-new-name-label').hidden,false);assert.equal(workspace.expression(),'[2,4,6]');
    $('matrix-new-name').value='NewVector';await $('matrix-store').onclick();
    assert.ok($('matrix-saved-list').querySelector('[data-variable="NewVector"]'));
    assert.equal($('matrix-name').value,'NewVector');assert.equal($('matrix-new-name-label').hidden,true);
    const original=workspace.expression();select('A');assert.equal(workspace.expression(),original);
    await $('matrix-store').onclick();select('NewVector');assert.equal(workspace.expression(),original);
    delete state.variables.NewVector;$('dialog').dispatchEvent(new dom.window.Event('close'));
    assert.equal($('matrix-saved-list').querySelector('[data-variable="NewVector"]'),null);
    state.variables={};$('dialog').dispatchEvent(new dom.window.Event('close'));
    assert.equal($('matrix-saved-list').querySelector('button'),null);assert.match($('matrix-saved-list').textContent,/No saved matrices or vectors/);
    assert.deepEqual(changes,['matrix','vector']);
  });

  await t.test('operand controls translate, retain English operation names and report empty operands',()=>{
    setLanguage('ko');operation('multiply','2');translateDOM();
    assert.equal($('matrix-other-title').textContent,'스칼라 (k)');assert.equal($('vector-other').placeholder,'2 또는 저장한 스칼라 변수');
    for(const name of ['add','subtract','multiply'])assert.equal([...$('matrix-op').options].find(option=>option.value===name).textContent,name);
    operation('add','  ');assert.throws(()=>workspace.command(),/두 번째 피연산자/);
    setLanguage('en');translateDOM();assert.equal($('matrix-other-title').textContent,'Second vector (B)');
    operation('norm');assert.equal($('vector-other-label').hidden,true);assert.ok($('vector-other-label').contains($('vector-other-math')));
    assert.ok(saves>0);assert.deepEqual(errors,[]);
  });
});
