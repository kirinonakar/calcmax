import test from 'node:test';
import assert from 'node:assert/strict';
import {emptyCallDeletion,emptyPowerDeletion,emptyFractionDeletion,infinityDeletion,powerInput,moveMathCursor,mathStructureExit} from '../input-navigation.js';
import {scientificRows,secondRows,topFunctions} from '../keypad.js';

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

test('power keys create an editable base when an operand is missing',()=>{
  for(const suffix of ['^2','^3','^(-1)','^()']){
    for(const [source,at] of [['',0],['1+',2],['sin()',4]])
      assert.deepEqual(powerInput(source,at,at,suffix),{text:`()${suffix}`,cursor:1});
    assert.deepEqual(powerInput('3',1,1,suffix),{text:suffix,cursor:suffix==='^()'?2:suffix.length});
  }
});

test('Right enters the exponent directly from a filled power base',()=>{
  for(const [source,baseEnd,exponentStart] of [
    ['(9)^()',2,5],['(9)^2',2,4],['9^()',1,3],['9^2',1,2],
    ['sin((9)^())',6,9],['(1+2)^(3+4)',4,7],
  ]){
    assert.equal(moveMathCursor(source,baseEnd,baseEnd,'RIGHT'),exponentStart,source);
    assert.equal(moveMathCursor(source,exponentStart,exponentStart,'LEFT'),baseEnd,source);
  }
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

test('DEL removes every unused keypad function template, including default calculus variables',()=>{
  const inputs=[...scientificRows.flat(),...secondRows.flat(),...topFunctions(),...topFunctions(true)]
    .flatMap(key=>[key.input,key.alternate]).filter(input=>/^[A-Za-z]+\(/.test(input));
  for(const source of [...new Set(inputs),'mean([])']){
    assert.deepEqual(emptyCallDeletion(source,source.indexOf('(')+1,source.indexOf('(')+1),{start:0,end:source.length,text:''},source);
  }
  assert.deepEqual(emptyCallDeletion('log(,)',5,5),{start:0,end:6,text:''});
  assert.deepEqual(emptyCallDeletion('mean([])',6,6),{start:0,end:8,text:''});
});

test('empty call deletion preserves surrounding terms, operand slots, selections, and entered arguments',()=>{
  for(const [source,position,start,end,text] of [
    ['1+sin()',6,2,7,''],['sin(cos())',8,4,9,''],['sin()^2',4,0,5,'()'],['2*sin()*3',6,2,7,'()'],
  ])assert.deepEqual(emptyCallDeletion(source,position,position),{start,end,text},source);
  for(const [source,start,end] of [['sin(2)',4,4],['log(,2)',4,4],['integrate(,y,,)',10,10],['sin()',0,0],['sin()',0,5],['12+*3',2,2]])
    assert.equal(emptyCallDeletion(source,start,end),null,source);
});
