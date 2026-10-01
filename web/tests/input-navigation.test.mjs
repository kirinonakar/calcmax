import test from 'node:test';
import assert from 'node:assert/strict';
import {emptyCallDeletion,emptyPowerDeletion,emptyFractionDeletion,powerInput} from '../input-navigation.js';
import {scientificRows,secondRows,topFunctions} from '../keypad.js';

test('power keys create an editable base when an operand is missing',()=>{
  for(const suffix of ['^2','^3','^(-1)','^()']){
    for(const [source,at] of [['',0],['1+',2],['sin()',4]])
      assert.deepEqual(powerInput(source,at,at,suffix),{text:`()${suffix}`,cursor:1});
    assert.deepEqual(powerInput('3',1,1,suffix),{text:suffix,cursor:suffix==='^()'?2:suffix.length});
  }
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
