import test from 'node:test';
import assert from 'node:assert/strict';
import {emptyCallDeletion} from '../input-navigation.js';
import {scientificRows,secondRows,topFunctions} from '../keypad.js';

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
