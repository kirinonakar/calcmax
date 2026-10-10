import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {advancedStatisticsSchema as schema} from '../advanced-statistics-schema.js';
import {statisticsReportTarget} from '../statistics-report.js';
import {statisticsPlotModel} from '../statistics-visualization.js';
import {parseCatalogHelp} from '../catalog-help.js';

test('all shared analyses have one menu, grouped order, guided forms and local report routing',()=>{
  const android=JSON.parse(readFileSync(new URL('../../app/src/main/assets/advanced_statistics.json',import.meta.url),'utf8'));
  assert.deepEqual(schema,android);
  assert.equal(new Set(schema.map(item=>item.id)).size,schema.length);
  for(const item of schema){
    assert.ok(item.controls?.length,item.id);assert.ok(item.group&&item.groupKo);
    assert.equal(statisticsReportTarget({statisticsReport:{analysis:item.id}}),`statistics-${item.section}-result`);
    assert.equal(statisticsReportTarget({statisticsReport:{analysis:item.id}},'',`statistics-${item.section}-result`),`statistics-${item.section}-result`);
  }
  const ids=section=>schema.filter(item=>item.section===section).map(item=>item.id);
  assert.deepEqual(ids('preparation'),['impute']);
  assert.deepEqual(ids('general'),['propztest','propztest2','mcnemar']);
  assert.deepEqual(ids('tests'),['shapiro','kstest','levene','bartlett','tukey','gameshowell','twowayanova','ancova','repeatedanova','friedman','cohend','eta2','padjust','tinterval','zinterval']);
  assert.deepEqual(ids('models'),['linearmodel','glm','poissonreg','nbreg','multinomial','ordinal','mixedmodel','glmm','gee','crossvalidate']);
  assert.deepEqual(ids('advanced'),['bayesmean','bayescompare','bayesproportion','bayesrate','bootstrapci','bayesbootstrap','pca','kmeans','survivalanalysis','kaplanmeier','logrank','cox','testpower','samplesize']);
  const catalog=readFileSync(new URL('../../app/src/main/java/com/kirinonakar/symvacas/ui/Catalog.kt',import.meta.url),'utf8');
  const advanced=catalog.match(/"Advanced statistics" to listOf\(([^\n]+)\)/)[1];
  assert.ok(!advanced.includes('impute(')&&!advanced.includes('levene(')&&!advanced.includes('glm('));
  const html=readFileSync(new URL('../index.html',import.meta.url),'utf8');
  const generalMenu=html.match(/id="statistics-op">([\s\S]*?)<\/select>/)[1];
  assert.ok(generalMenu.includes('value="propztest"')&&generalMenu.includes('value="propztest2"'));
  assert.ok(!generalMenu.includes('value="tinterval"')&&!generalMenu.includes('value="zinterval"'));
  const all=[...html.matchAll(/\bid="([^"]+)"/g)].map(match=>match[1]);
  assert.equal(new Set(all).size,all.length,'DOM IDs must be unique');
  for(const section of ['advanced','general','tests','preparation','models'])for(const suffix of ['kind','source','controls','result','preview','example-preview'])assert.ok(all.includes(`statistics-${section}-${suffix}`));
});

test('Q-Q, original-feature cluster axes and interval plots retain numerical meaning',()=>{
  const qq=statisticsPlotModel({kind:'qq',points:[[-1,2],[0,3],[1,5]],referenceLine:[[-1,2],[1,4]]});
  assert.deepEqual(qq.points.map(point=>[point.x,point.y]),[[-1,2],[0,3],[1,5]]);
  assert.ok(!qq.xlabel.includes('PC'));assert.deepEqual(qq.referenceLine,[[-1,2],[1,4]]);
  const cluster=statisticsPlotModel({kind:'clusters',features:['age','weight','height'],points:[[20,60,170],[40,80,180]],assignments:[1,2],centroids:[[20,60,170],[40,80,180]]},2,0);
  assert.equal(cluster.xlabel,'height');assert.equal(cluster.ylabel,'age');assert.equal(cluster.points[1].group,2);
  assert.deepEqual(cluster.centroids.map(point=>[point.x,point.y]),[[170,20],[180,40]]);
  const forest=statisticsPlotModel({kind:'intervals',rows:[['a',2,1,3],['b',-1,-2,.5]],reference:0});
  assert.ok(forest.xmin<-2&&forest.xmax>3);assert.equal(forest.intervals[0].estimate,2);assert.equal(forest.reference,0);
});

test('both help documents preserve the selection flowchart and searchable use cases',()=>{
  for(const language of ['', '_ko']){
    const text=readFileSync(new URL(`../catalog_help${language}.md`,import.meta.url),'utf8');
    const blocks=parseCatalogHelp(text),diagram=blocks.find(block=>block.kind==='diagram');
    assert.ok(diagram.text.includes('Welch'));assert.ok(diagram.text.includes('McNemar'));
    assert.ok(diagram.text.includes('ANCOVA'));assert.ok(!blocks.some(block=>block.text==='```text'));
    assert.ok(text.includes('parametric')&&text.includes('nonparametric'));
    assert.ok(text.includes('GEE')&&text.includes('Lilliefors'));
    const table=blocks.find(block=>block.kind==='table');assert.equal(table.columns.length,4);assert.equal(table.rows.length,4);
    assert.ok(table.rows[3].join(' ').includes('Friedman'));assert.ok(parseCatalogHelp(text,'Friedman').some(block=>block.kind==='table'));
    assert.ok(parseCatalogHelp(text,'Welch').some(block=>block.kind==='diagram'));
    for(const id of ['ttest2','ttestpaired','wilcoxon','mcnemar','ancova','gee'])assert.ok(blocks.some(block=>block.kind==='entry'&&block.signature.split('(')[0]===id&&block.text.length>35),id);
  }
});
