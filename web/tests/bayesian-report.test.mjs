import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {renderRegressionReport} from '../regression-report.js';
import {statisticsCommand} from '../workspace-commands.js';

test('NUTS form defaults produce the sampler command and preserve selected response',()=>{
  const html=readFileSync(new URL('../index.html',import.meta.url),'utf8');
  const settings={op:'regression',kind:'xy',regression:'bayeslinear',responseColumn:0,bayesianMethod:'nuts'};
  for(const [suffix,key] of [['samples','nutsSamples'],['warmup','nutsWarmup'],['max-depth','nutsMaxDepth'],['seed','nutsSeed'],['chains','nutsChains']]){
    const field=html.match(new RegExp(`<input id="regression-nuts-${suffix}"[^>]*>`))?.[0];
    assert.ok(field,suffix);settings[key]=field.match(/value="([^"]+)"/)[1];
  }
  assert.ok(html.includes('<option value="nuts">NUTS</option>'));
  assert.equal(statisticsCommand('0,-2\n1,2',settings),'regression([[-2,0],[2,1]],bayeslinear,[2.5,0.95,2,1,[nuts,500,500,8,0,2]])');
});

test('NUTS reports display aggregate and per-chain acceptance statistics',()=>{
  // Minimal DOM checks the report schema adapter; visual layout is not tested.
  class Node {
    children=[];style={};attributes={};textContent='';
    setAttribute(key,value){this.attributes[key]=value;}
    append(...nodes){this.children.push(...nodes);}
    replaceChildren(){this.children=[];}
    text(){return [this.textContent,...this.children.map(node=>node.text())].join('\n');}
  }
  const previous=globalThis.document;
  globalThis.document={createElement:()=>new Node()};
  try{
    const container=new Node();
    renderRegressionReport(container,{bayesian:true,model:'bayeslinear',method:'nuts',n:4,df:null,fitScale:'y',credibleLevel:.95,priorSD:2.5,
      nuts:{samples:100,warmup:50,maxTreeDepth:8,seed:0,chains:2,meanAcceptanceProbability:.91,divergences:0,maxTreeDepthHits:3,meanTreeDepth:2.5,meanLeapfrogSteps:5,
        chainDiagnostics:[{chain:1,meanAcceptanceProbability:.87,stepSize:.5,divergences:0}]},coefficients:[],residuals:[]});
    const text=container.text();
    assert.match(text,/NUTS/);assert.match(text,/Max tree depth=8/);assert.match(text,/Max tree depth hits=3/);
    assert.match(text,/Mean acceptance probability=0.91/);assert.match(text,/Chain 1 · Mean acceptance probability=0.87/);
    assert.match(text,/Split R-hat/);assert.match(text,/Autocorrelation ESS/);assert.match(text,/MCSE/);
  }finally{globalThis.document=previous;}
});
