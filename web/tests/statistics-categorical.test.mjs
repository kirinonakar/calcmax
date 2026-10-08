import test from 'node:test';
import assert from 'node:assert/strict';
import {statisticsAnalysisData,statisticsCommand,statisticsColumnLabels,statisticsCategoryLabels} from '../workspace-commands.js';
import {advancedStatisticsTermLabels,guidedStatisticsCommand} from '../advanced-statistics.js';
import {advancedStatisticsSchema} from '../advanced-statistics-schema.js';

const source='ID,Unused,Treatment,Outcome\n10,junk,treated,yes\n11,,control,no\n12,invalid,treated,no\n13,junk,control,yes\n14,junk,,yes\n15,junk,control,';
test('Fisher and chi-square select distinct columns and omit only incomplete selected pairs',()=>{
  const options={kind:'columns:4',firstGroup:'z',secondGroup:'x4',tail:'right',yatesCorrection:false};
  for(const op of ['fisherexact','chi2independence']){
    const plan=statisticsAnalysisData(source,{...options,op});
    assert.deepEqual(plan.pairs,[['treated','yes'],['control','no'],['treated','no'],['control','yes']]);
    assert.deepEqual(plan.samples.map(sample=>sample.label),['z','x4']);
    assert.equal(statisticsCommand(source,{...options,op}),op==='fisherexact'?'fisherexact([1,2,1,2],[1,2,2,1],right)':'chi2independence([1,2,1,2],[1,2,2,1],0)');
    assert.throws(()=>statisticsCommand(source,{...options,op,secondGroup:'z'}),/different columns/);
    assert.throws(()=>statisticsCommand(source,{...options,op,firstGroup:'missing'}),/different columns/);
  }
  assert.throws(()=>statisticsCommand(source,{op:'fisherexact',kind:'columns:4',firstGroup:'x',secondGroup:'x4'}),/exactly two categories/);
  assert.throws(()=>statisticsCommand('1,0,1\n2,,0',{op:'fisherexact',kind:'xyz',firstGroup:'y',secondGroup:'z'}),/at least two complete/);
  assert.equal(statisticsCommand('0,0\n0,1\n1,0\n1,1',{op:'fisherexact'}),'fisherexact([1,1,2,2],[1,2,1,2])');
});

test('categorical report labels keep headers and the category encoding order',()=>{
  const plan=statisticsAnalysisData(source,{op:'fisherexact',kind:'columns:4',firstGroup:'z',secondGroup:'x4'}),labels=statisticsColumnLabels(source,'columns:4');
  assert.deepEqual(statisticsCategoryLabels(plan.pairs,labels[2],labels[3]),{'table:row':'Treatment (z)','table:column':'Outcome (x4)','table:row:1':'treated','table:row:2':'control','table:column:1':'yes','table:column:2':'no'});
  const mcnemar=advancedStatisticsSchema.find(item=>item.id==='mcnemar'),rows=[['1','yes','no'],['2','no','yes'],['3','yes','yes'],['4','','yes'],['5','yes','']],settings={layout:'pairs',first:'1',second:'2'};
  assert.equal(guidedStatisticsCommand(mcnemar,rows,settings),'mcnemar([[1,1],[1,0]],exact)');
  const named=advancedStatisticsTermLabels(mcnemar,rows,settings,['ID (x)','Before (y)','After (z)']);
  assert.equal(named['table:row'],'Before (y)');assert.equal(named['table:column'],'After (z)');
  assert.equal(named['table:row:1'],'yes');assert.equal(named['table:column:1'],'yes');
});
