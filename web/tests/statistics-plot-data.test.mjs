import test from 'node:test';
import assert from 'node:assert/strict';
import {statisticsHeatMapData,statisticsCorrelationHeatMap,violinDensity,beeswarmLayout,heatMapColor} from '../statistics-plot-data.js';

function assertSwarm(positions,halfWidth){
  const swarm=beeswarmLayout(positions,3,halfWidth);
  assert.equal(swarm.offsets.length,positions.length);assert.ok(swarm.radius>0&&swarm.radius<=3);
  swarm.offsets.forEach(offset=>assert.ok(Number.isFinite(offset)&&Math.abs(offset)+swarm.radius<=halfWidth+1e-8));
  for(let i=0;i<positions.length;i++)for(let j=0;j<i;j++)assert.ok(Math.hypot(positions[i]-positions[j],swarm.offsets[i]-swarm.offsets[j])>=2*swarm.radius-1e-8,`points ${i} and ${j} overlap`);
  assert.deepEqual(swarm,beeswarmLayout(positions,3,halfWidth),'redraws preserve the swarm');
  return swarm;
}

test('beeswarm preserves every observation without overlaps at sparse and dense scales',()=>{
  { // beeswarm centers

  assert.deepEqual(beeswarmLayout([],3,28),{radius:3,offsets:[]});
  assert.deepEqual(assertSwarm([0,20,40],28).offsets,[0,0,0]);
  const swarm=assertSwarm([20,0,0,0,1,2,21,40],28);
  assert.equal(swarm.radius,3);assert.equal(swarm.offsets[1],0);assert.ok(swarm.offsets.some(offset=>offset>0)&&swarm.offsets.some(offset=>offset<0));
  const pair=beeswarmLayout([0,1],3,28);assert.ok(Math.abs(Math.abs(pair.offsets[1])-Math.sqrt(6.45**2-1))<1e-12);

  }
  { // dense and constant

  for(const positions of [Array(100).fill(0),Array.from({length:100},(_,i)=>i*.01),[...Array(80).fill(0),...Array(80).fill(1),20]])assert.ok(assertSwarm(positions,23).radius<3);
  const large=beeswarmLayout(Array(5000).fill(50),3,28);
  assert.equal(large.offsets.length,5000);assert.ok(large.offsets.every(offset=>Math.abs(offset)+large.radius<=28+1e-8));

  }
});

test('violin KDE is symmetric, normalized and finite at extreme scales; degenerate samples use only raw points',()=>{
  const density=violinDensity([0,1,2]);
  assert.equal(density.length,97);assert.equal(density[48][0],1);assert.equal(density[48][1],1);
  density.forEach((point,i)=>assert.ok(Math.abs(point[1]-density[96-i][1])<1e-12));
  for(const values of [[1e308,0,-1e308],[1e-300,2e-300,3e-300]])assert.ok(violinDensity(values).flat().every(Number.isFinite));
  assert.deepEqual(violinDensity([3]),[]);assert.deepEqual(violinDensity([3,3,3]),[]);assert.deepEqual(violinDensity([]),[]);
});

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
