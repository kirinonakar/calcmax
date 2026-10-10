import test from 'node:test';
import assert from 'node:assert/strict';
import {statisticsImputationCSV} from '../statistics-imputation.js';
import {statisticsCommand,statisticsAnalysisData} from '../workspace-commands.js';
import {statisticsPlotPanels} from '../statistics-plot.js';
import {statisticsHeatMapData} from '../statistics-plot-data.js';

test('applying imputation preserves headers, observed precision and unselected cells',()=>{
  const source='"Height, cm",Weight,ID\n1,,s1\n2,4.000,s2\nNA,6,s3';
  assert.equal(statisticsImputationCSV(source,[['1','5'],['2','4'],['1.5','6']],2),'"Height, cm",Weight,ID\n1,5,s1\n2,4.000,s2\n1.5,6,s3');
  assert.throws(()=>statisticsImputationCSV(source,[['1','5']],2),/match/);
  assert.throws(()=>statisticsImputationCSV(source,[['1','Infinity'],['2','4'],['1.5','6']],2),/finite/);
});

test('four-column comparisons select arbitrary roles and match subject IDs before paired inference',()=>{
  const source='ID,Stage,Score,Unused\ns1,Pre,10,0\ns2,Pre,20,0\ns3,Pre,,0\ns2,Post,24,0\ns1,Post,13,0\ns3,Post,35,0';
  const settings={kind:'columns:4',grouping:'groups',groupColumn:1,valueColumn:2,firstGroup:'Pre',secondGroup:'Post',matching:'subject',subjectColumn:0};
  assert.equal(statisticsCommand(source,{...settings,op:'wilcoxon'}),'wilcoxon([10,20],[13,24])');
  assert.equal(statisticsCommand(source,{...settings,op:'ttestpaired'}),'ttestpaired(0,[10,20],[13,24])');
  const plan=statisticsAnalysisData(source,{...settings,op:'wilcoxon'});
  assert.equal(plan.omitted,1);assert.deepEqual(plan.samples.map(sample=>sample.label),['Pre','Post']);
  assert.equal(statisticsCommand('A,B,C,D\n1,2,3,4\n2,5,6,9',{kind:'columns:4',op:'ttest2',firstGroup:'z',secondGroup:'x4',independentMethod:'student'}),'ttest2(0,[3,6],[4,9],student)');
  assert.match(statisticsCommand('A,B\n1,2\n3,5',{kind:'xy',op:'anova'}),/^welchanova\(/);
  assert.match(statisticsCommand('A,B\n1,2\n3,5',{kind:'xy',op:'anova',anovaMethod:'classic'}),/^anova\(/);
  assert.equal(statisticsCommand('ID,A,B,C\ns1,1,2,3\ns2,4,5,6',{kind:'columns:4',op:'anova',groupColumns:'1,3'}),'welchanova([1,4],[3,6])');
});

test('plot grouping can use a middle column and retains header plus column IDs',()=>{
  const rows=[['1','Control','2'],['3','Drug','4'],['5','Control','6']],columnNames=['height (x)','treatment (y)','weight (z)'];
  const panels=statisticsPlotPanels(rows,{grouping:'column:1',columnCount:3,columnNames});
  assert.deepEqual(panels.map(panel=>panel.label),['height (x)','weight (z)']);
  assert.deepEqual(panels[0].series,[{label:'Control',values:[1,5]},{label:'Drug',values:[3]}]);
  const heatmap=statisticsHeatMapData(rows,{grouping:'column:1',columnCount:3,columnNames});
  assert.deepEqual(heatmap.columns,['height (x)','weight (z)']);
  assert.deepEqual(heatmap.rows.map(row=>row.label),['Control','Drug','Control']);
});
