import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {parse} from '../parser.js';
import {statisticsModelWorkflowPlan,statisticsDetectedCrossLoadings,appendStatisticsModelWorkflow} from '../statistics-model-workflow.js';

const cases=JSON.parse(readFileSync(new URL('../../tests/fixtures/statistics_model_workflow.json',import.meta.url),'utf8'));
test('model transfers preserve memberships, analyzed order, options, groups and labels',()=>{
  for(const item of cases){
    if(item.error){assert.throws(()=>statisticsModelWorkflowPlan(item.workflow,item.settings),{message:item.error});continue;}
    if(item.detected)assert.deepEqual(statisticsDetectedCrossLoadings(item.workflow,item.settings),item.detected,item.name);
    const plan=statisticsModelWorkflowPlan(item.workflow,item.settings);
    assert.deepEqual(parse(plan.expression),parse(item.expected),item.name);
    assert.deepEqual(plan.termLabels,item.workflow.termLabels);
  }
});

class Node {
  constructor(tag){this.tag=tag;this.children=[];this.dataset={};this.attrs={};this.handlers={};this.value='';this.disabled=false;}
  setAttribute(key,value){this.attrs[key]=value;}
  addEventListener(name,handler){this.handlers[name]=handler;}
  append(...nodes){this.children.push(...nodes);}
  replaceChildren(...nodes){this.children=[...nodes];}
}
test('result buttons execute reviewed assignments and require explicit SEM paths',t=>{
  const previous=globalThis.document;t.after(()=>globalThis.document=previous);
  globalThis.document={createElement:tag=>new Node(tag)};
  let sent=null,busy=false;const panel=new Node('div');
  const workflow=cases[0].workflow;
  appendStatisticsModelWorkflow(panel,workflow,plan=>sent=plan,{isBusy:()=>busy});
  const block=panel.children[0],field=block.children.find(node=>node.tag==='label').children[0],button=block.children.at(-1);
  field.value='1,1,1,1,2,2';field.oninput();assert.equal(button.disabled,true);
  field.value='2,1,2,1,2,1';field.oninput();assert.equal(button.disabled,false);
  busy=true;button.handlers.click();assert.equal(sent,null);
  busy=false;button.handlers.click();assert.deepEqual(parse(sent.expression).args[1].args.map(a=>Number(a.value)),[2,1,2,1,2,1]);
  const semPanel=new Node('div');sent=null;
  appendStatisticsModelWorkflow(semPanel,cases.find(item=>item.workflow.target==='sem').workflow,plan=>sent=plan);
  const semBlock=semPanel.children[0],paths=semBlock.children.filter(node=>node.tag==='label')[1].children[0],semButton=semBlock.children.at(-1);
  assert.equal(semButton.disabled,true);paths.value='1,2;2,1';paths.oninput();assert.equal(semButton.disabled,true);
  paths.value='2,1';paths.oninput();assert.equal(semButton.disabled,false);semButton.handlers.click();assert.equal(sent.target,'sem');
});

test('EFA candidates react to cutoff, individual exclusions and automatic inclusion',t=>{
  const previous=globalThis.document;t.after(()=>globalThis.document=previous);globalThis.document={createElement:tag=>new Node(tag)};
  const panel=new Node('div');let sent;
  const workflow=cases.find(item=>item.detected).workflow;
  appendStatisticsModelWorkflow(panel,workflow,plan=>sent=plan);
  const block=panel.children[0],labels=block.children.filter(node=>node.tag==='label');
  const threshold=labels[1].children[0],automatic=labels[2].children[0],candidates=block.children.filter(node=>node.tag==='div')[1],button=block.children.at(-1);
  assert.equal(candidates.children.length,3);assert.equal(button.disabled,false);
  button.handlers.click();assert.equal(parse(sent.expression).args[2].args.length,3);
  const first=candidates.children[0].children[0];first.checked=false;first.onchange();button.handlers.click();assert.equal(parse(sent.expression).args[2].args.length,2);
  threshold.value='.4';threshold.oninput();button.handlers.click();assert.equal(parse(sent.expression).args[2].args.length,0);
  threshold.value='0';threshold.oninput();assert.equal(button.disabled,true);
  automatic.checked=false;automatic.onchange();assert.equal(button.disabled,false);assert.equal(threshold.disabled,true);
  button.handlers.click();assert.equal(parse(sent.expression).args[2].args.length,0);
});
