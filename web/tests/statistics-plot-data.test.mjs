import test from 'node:test';
import assert from 'node:assert/strict';
import {statisticsHeatMapData,statisticsCorrelationHeatMap,beeswarmLayout,heatMapColor} from '../statistics-plot-data.js';

test('heat map retains row order, zero, missing cells and categorical labels without compacting columns',()=>{
  const rows=[['A','0','1,234'],['B','','-2'],['A','NaN','Infinity']];
  assert.deepEqual(statisticsHeatMapData(rows,{columnCount:3,grouping:'first'}),{columns:['y','z'],rows:[
    {label:'A',values:[0,1234]},{label:'B',values:[null,-2]},{label:'A',values:[null,null]}
  ]});
  assert.deepEqual(statisticsHeatMapData([['1','','A'],['2','3','B']],{columnCount:3,grouping:'last'}).rows.map(row=>row.values),[[1,null],[2,3]]);
  assert.equal(heatMapColor(0,0,0),'rgb(241,241,235)');assert.equal(heatMapColor(null,-1,1),null);
  assert.equal(heatMapColor(-1e308,-1e308,1e308),'rgb(59,112,189)');assert.equal(heatMapColor(1e308,-1e308,1e308),'rgb(180,55,72)');
});

test('Pearson matrix uses complete pairs, omits categorical columns and marks constant/insufficient pairs undefined',()=>{
  const data=statisticsCorrelationHeatMap([
    ['1','2','-1','.1','A'],['2','4','-2','.1','B'],['3','8','','.1','C'],['','10','-4','.1','D'],['5','','-5','.1','E'],['','','','.1','F']
  ],{columnCount:5});
  assert.deepEqual(data.columns,['x','y','z','x4']);assert.deepEqual(data.range,[-1,1]);
  assert.ok(Math.abs(data.rows[0].values[1]-.9819805060619657)<1e-12);
  assert.ok(Math.abs(data.rows[0].values[2]+1)<1e-12);assert.equal(data.rows[0].counts[1],3);assert.equal(data.rows[0].counts[2],3);
  assert.deepEqual(data.rows[3].values,[null,null,null,null]);
  data.rows.forEach((row,i)=>row.values.forEach((value,j)=>assert.equal(value,data.rows[j].values[i])));
  assert.equal(statisticsCorrelationHeatMap([['1',''],['','2']],{columnCount:2}).rows[0].values[1],null);
  const large=statisticsCorrelationHeatMap([['-1e308','1e308'],['0','0'],['1e308','-1e308']],{columnCount:2});
  assert.ok(Math.abs(large.rows[0].values[1]+1)<1e-12);
});
