import test from 'node:test';
import assert from 'node:assert/strict';
import {parse} from '../parser.js';
import {statisticsRequest} from '../statistics-request.js';

test('large tables become small expressions with exact decimal dataset references',()=>{
  const source=`mixedmodel([${Array.from({length:5000},(_,i)=>`[${i%20},-1.25e-3,${i}]`).join(',')}],0)`;
  assert.throws(()=>parse(source),/8192/);
  const request=statisticsRequest(source),name=request.tree.args[0].value;
  assert.equal(request.tree.args[0].kind,'symbol');
  assert.equal(request.statisticsDatasets[name].length,5000);
  assert.deepEqual(request.statisticsDatasets[name][0],['0','-1.25e-3','0']);
  assert.ok(JSON.stringify(request.tree).length<500);
});

test('lists with formulas use normal parsing and references avoid existing names',()=>{
  const request=statisticsRequest('regression([[1,2],[3,4]],custom,SymvaStatisticsData0*x,x,[1])');
  assert.equal(request.tree.args[0].value,'SymvaStatisticsData0Data');
  assert.deepEqual(statisticsRequest('mean([1/2,2+3])').tree,parse('mean([1/2,2+3])'));
  assert.throws(()=>statisticsRequest('mean([1,,2])'));
  assert.throws(()=>statisticsRequest('mean([1,2]'));
});
