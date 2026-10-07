import test from 'node:test';
import assert from 'node:assert/strict';
import {resultText} from '../result-display.js';

test('copied answers match displayed precision, notation and structured results',()=>{
  { // copied answers use

  const number=value=>({kind:'number',value});
  assert.equal(resultText({exact:'0.333333333333333333333333333333',decimal:'0.333333333333333333333333333333',decimalTree:number('0.333333333333333333333333333333')},{decimal:true,digits:10}),'0.3333333333');
  assert.equal(resultText({exact:'12345612345.6789',decimal:'12345612345.6789',decimalTree:number('12345612345.6789')},{decimal:true,digits:5,notation:'sci'}),'1.23456×10^10');
  assert.equal(resultText({exact:'1234567.891',decimal:'1234567.891',decimalTree:number('1234567.891')},{decimal:true,digits:10,grouping:true}),'1,234,567.891');

  }
  { // copied answers keep

  const number=value=>({kind:'number',value});
  const fraction={kind:'fraction',args:[{kind:'text',value:'7'},{kind:'text',value:'2'}]};
  assert.equal(resultText({exact:'7/2',decimal:'3.5',tree:fraction},{digits:10}),'7/2');
  assert.equal(resultText({exact:'7/2',decimal:'3.5',tree:fraction},{mixed:true,digits:10}),'3 1/2');
  assert.equal(resultText({exact:'12.5125',decimal:'12.5125',tree:{kind:'dms',args:[number('12'),number('30'),number('45')]}},{digits:10}),'12°30′45″');
  const rows={kind:'rows',args:[{kind:'row',value:'mean',args:[number('1.2345678901234567')]},{kind:'row',value:'stdev',args:[number('0.9876543210987654')]}]};
  assert.equal(resultText({exact:'mean: 1.2345678901234567\nstdev: 0.9876543210987654',decimal:'mean: 1.2345678901234567\nstdev: 0.9876543210987654',decimalTree:rows},{decimal:true,digits:10}),'mean: 1.2345678901\nstdev: 0.9876543211');

  }
});
