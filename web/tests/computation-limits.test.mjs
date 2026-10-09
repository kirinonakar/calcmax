import test from 'node:test';
import assert from 'node:assert/strict';
import {setComputationLimitsRemoved} from '../computation-limits.js';
import {createAppState} from '../app-state.js';
import {parse} from '../parser.js';
import {csvRows} from '../workspace-commands.js';
import {advancedStatisticsRows} from '../advanced-statistics.js';
import {roundNumber} from '../display-format.js';

test('saved limit removal is opt-in and preserves precision and display ceilings',()=>{
  assert.equal(createAppState().removeComputationLimit,false);
  assert.equal(createAppState({removeComputationLimit:'true'}).removeComputationLimit,false);
  const state=createAppState({removeComputationLimit:true,precision:1000,digits:1000});
  assert.equal(state.removeComputationLimit,true);
  assert.equal(state.precision,200);assert.equal(state.digits,200);
});

test('parser and statistics capacity checks follow the setting and restore defaults',t=>{
  t.after(()=>setComputationLimitsRemoved(false));
  const literal='1'+'0'.repeat(8192),expression=`[${Array(2100).fill('1').join(',')}]`;
  const data=Array(5001).fill('1,2').join('\n');
  const wide=Array(21).fill('1').join(',')+'\n'+Array(21).fill('2').join(',');
  for(const check of [()=>parse(literal),()=>parse(expression),()=>csvRows(data),()=>advancedStatisticsRows(wide)])assert.throws(check);
  setComputationLimitsRemoved(true);
  assert.equal(parse(literal).value,literal);
  assert.equal(parse(expression).args.length,2100);
  assert.equal(csvRows(data).length,5001);
  assert.equal(advancedStatisticsRows(wide)[0].length,21);
  assert.throws(()=>parse('1+*2'));
  assert.throws(()=>csvRows(''));
  const decimal='1.'+'1'.repeat(250);
  assert.equal(roundNumber(decimal,1000).length,202,'display precision remains bounded');
  setComputationLimitsRemoved(false);
  assert.throws(()=>parse(literal));assert.throws(()=>csvRows(data));
});
