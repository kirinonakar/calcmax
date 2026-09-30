import test from 'node:test';
import assert from 'node:assert/strict';
import {fractionInput} from '../fraction-input.js';
import {parse} from '../parser.js';

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
