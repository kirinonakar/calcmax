import test from 'node:test';
import {functionRelationExit,emptyPowerDeletion,emptyFractionDeletion,infinityDeletion,moveMathCursor,mathStructureExit} from '../input-navigation.js';
import assert from 'node:assert/strict';
import {bindKeyPress} from '../keypad.js';
import {fractionInput} from '../fraction-input.js';
import {parse} from '../parser.js';
import {JSDOM} from 'jsdom';
import {requiresExplicitEvaluation} from '../evaluation-policy.js';

test('equality moves out of formula calls while keeping solver equation scopes',()=>{
  for(const source of ['diff(x,x)','integrate(x,x)','sin(x)','sin(diff(x,x))','f(x)']){
    const at=source.indexOf('x')+1;
    assert.equal(functionRelationExit(source,at,at),source.length,source);
  }
  assert.equal(functionRelationExit('diff(x,x)',7,7),9,'the right arrow may move past the comma');
  assert.equal(functionRelationExit('solve(x,x)',7,7),null);
  assert.equal(functionRelationExit('solve(sin(x),x)',11,11),12);
  const nested='dsolve(diff(y(t),t),y(t),t)',at=nested.indexOf('y(t)')+3;
  assert.equal(functionRelationExit(nested,at,at),nested.indexOf(',y(t)'));
  assert.equal(functionRelationExit('piecewise((x,x>0),(0,true))',12,12),null);
  for(const [source,start,end] of [['diff(x,x)',5,6],['diff(x,x)',0,0],['sin(x)',6,6],['sin(x>',6,6]])assert.equal(functionRelationExit(source,start,end),null,source);
});

test('infinity deletes as a whole symbol without treating names containing oo as infinity',()=>{
  for(const at of [1,2])assert.deepEqual(infinityDeletion('oo',at,at),{start:0,end:2,text:''});
  for(const at of [0,1])assert.deepEqual(infinityDeletion('oo',at,at,false),{start:0,end:2,text:''});
  assert.deepEqual(infinityDeletion('limit(1/x,x,oo)',14,14),{start:12,end:14,text:''});
  assert.deepEqual(infinityDeletion('oo^2',2,2),{start:0,end:2,text:'()'});
  assert.deepEqual(infinityDeletion('2*oo',4,4),{start:2,end:4,text:'()'});
  assert.deepEqual(infinityDeletion('1+*oo',5,5),{start:3,end:5,text:''},'invalid expressions still delete the token atomically');
  for(const source of ['foo','oo_value','oo2','food','root'])assert.equal(infinityDeletion(source,source.length,source.length),null,source);
  assert.equal(infinityDeletion('oo',0,0),null);
  assert.equal(infinityDeletion('oo',2,2,false),null);
  assert.equal(infinityDeletion('oo',0,1),null,'selections retain their own deletion behavior');
});

test('Right exits completed exponents and visits nested power/fraction parents in order',()=>{
  for(const source of ['5^2','5^3','5^(-1)','(9)^(9)','5^(23)+1']){
    const treeEnd=source.endsWith('+1')?source.length-2:source.length;
    const at=source[treeEnd-1]===')'?treeEnd-1:treeEnd;
    assert.deepEqual(mathStructureExit(source,at,at,'RIGHT'),{position:treeEnd,start:0,end:treeEnd,exponentEnd:at},source);
    assert.equal(moveMathCursor(source,at,at,'RIGHT'),treeEnd,source);
  }
  for(const source of ['2^(3^4)','1/(2^3)','2^(1/3)','2^3^4']){
    const at=source.endsWith(')')?source.length-1:source.length;
    const inner=mathStructureExit(source,at,at,'RIGHT');assert.ok(inner.start>0,source);
    const outer=mathStructureExit(source,inner.position,inner.position,'RIGHT',inner);
    assert.equal(outer.start,0,source);assert.equal(outer.position,source.length,source);
  }
  for(const [source,at] of [['5^()',3],['5^(2+)',5],['5^(234)',5]])
    assert.equal(mathStructureExit(source,at,at,'RIGHT'),null,source);
  assert.equal(mathStructureExit('5^2',2,3,'RIGHT'),null,'selection collapse is not an exponent exit');
  assert.equal(mathStructureExit('5^2',3,3,'LEFT'),null);
});

test('empty powers and fractions delete as a unit while filled heads survive empty-tail deletion',()=>{
  for(const [source,position,remove] of [
    ['()^2',1,emptyPowerDeletion],['()^3',1,emptyPowerDeletion],['()^(-1)',1,emptyPowerDeletion],
    ['()^()',1,emptyPowerDeletion],['()^()',4,emptyPowerDeletion],
    ['()/()',1,emptyFractionDeletion],['()/()',4,emptyFractionDeletion],
  ]){
    assert.deepEqual(remove(source,position,position),{start:0,end:source.length,text:''},source);
    assert.deepEqual(remove(`sin(${source})`,position+4,position+4),{start:4,end:source.length+4,text:''},source);
  }
  assert.deepEqual(emptyPowerDeletion('2*()^2*3',3,3),{start:2,end:6,text:'()'});
  for(const [source,at,remove,expected] of [
    ['3^()',3,emptyPowerDeletion,'3'],['(3)^()',5,emptyPowerDeletion,'3'],
    ['(3)/()',5,emptyFractionDeletion,'3'],['(3+4)/()',7,emptyFractionDeletion,'(3+4)'],
  ])assert.deepEqual(remove(source,at,at),{start:0,end:source.length,text:expected,cursor:expected.length});
  for(const source of ['(2)^2','(2)^(-1)','(1)/(2)']){
    assert.equal(emptyPowerDeletion(source,source.length,source.length),null);
    assert.equal(emptyFractionDeletion(source,source.length,source.length),null);
  }
  assert.equal(emptyFractionDeletion('1÷',2,2),null,'plain division keeps its text deletion behavior');
  assert.equal(emptyPowerDeletion('()^2',0,4),null,'ordinary selections keep their behavior');
});

test('fraction templates reuse the preceding operand or selected expression',()=>{
  for(const [source,start,end,expected,position] of [
    ['8',1,1,'(8)/()',5],
    ['12.5',4,4,'(12.5)/()',8],
    ['2+8',3,3,'2+(8)/()',7],
    ['sqrt(8)',6,6,'sqrt((8)/())',10],
    ['sqrt(8)',7,7,'(sqrt(8))/()',11],
    ['2+3',0,3,'(2+3)/()',7],
    ['2+8*4',2,3,'2+(8)/()*4',7],
    ['Ans',3,3,'(Ans)/()',7],
    ['',0,0,'()/()',1],
    ['8+',2,2,'8+()/()',3],
    ['sqrt()',5,5,'sqrt(()/())',6],
  ]){
    const change=fractionInput(source,start,end);
    const result=source.slice(0,change.start)+change.text+source.slice(change.end);
    assert.equal(result,expected,source);assert.equal(change.start+change.cursor,position,source);
    assert.doesNotThrow(()=>parse(result,{allowHoles:true}),source);
  }
});

test('long press invokes only the shifted action; release does not also type the base key',()=>{
  const dom=new JSDOM('<button></button>'),button=dom.window.document.querySelector('button');
  let pending,short=0,long=0;
  const schedule=callback=>{pending=callback;return 1;},cancel=()=>{pending=null;};
  bindKeyPress(button,()=>short++,()=>long++,{schedule,cancel});
  const pointer=(type,x=0)=>button.dispatchEvent(new dom.window.MouseEvent(type,{clientX:x,clientY:0,button:0,bubbles:true}));
  pointer('pointerdown');pointer('pointerup');button.click();assert.equal(short,1);assert.equal(long,0);
  pointer('pointerdown');pending();pointer('pointerup');button.click();assert.equal(short,1);assert.equal(long,1);
  // A normal keyboard/mouse click still works after the long click was consumed.
  button.click();assert.equal(short,2);
  pointer('pointerdown');pointer('pointercancel');assert.equal(pending,null);button.click();assert.equal(short,2);
  pointer('pointerdown');pointer('pointermove',20);assert.equal(pending,null);
  button.disabled=true;pointer('pointerdown');assert.equal(pending,null);
  dom.window.close();
});

test('preview policy evaluates arithmetic but waits for expensive or stateful calls',()=>{
  for(const source of ['2+3*4','cos(2*x)','f(1)','log(100,10)','nthroot(8,3)'])
    assert.equal(requiresExplicitEvaluation(parse(source)),false,source);
  for(const source of ['rnd()','integrate(exp(-x^2),(x,0,oo))','1+dot([1,2],[3,4])','f(1,2)'])
    assert.equal(requiresExplicitEvaluation(parse(source)),true,source);
  assert.equal(requiresExplicitEvaluation(parse('1+f(1)'),new Set(['f'])),true);
});
