import {t} from './i18n.js';
import {mathDisplay} from './math-display.js';
import {equationFormulaText} from './equation-formula-text.js';

export function equationStepTrees(step) {
  const equations=(step.equations||[]).map(formula=>formula.tree).filter(Boolean);
  const formulas=step.operation&&equations.length
    ? [{kind:'row-operation',args:[step.operation,equations[0]]},...equations.slice(1)]
    : [step.operation,...equations];
  return [step.variableOrder?.tree,step.tree,...formulas].filter(Boolean);
}

export function equationStepExplanation(step) {
  return step.explanationParts?.map(part=>t(part.text).replace(/\{(\w+)\}/g,(match,key)=>part.values?.[key]??match)).join(' ')||t(step.explanation||'');
}

export function solutionStepsCopyText(report,digits=10){
  const formula=value=>value.tree?equationFormulaText(value.tree,digits):value.exact||'';
  const blocks=[t('Step-by-step solution')];
  if(report.note)blocks.push(...report.note.split('\n').filter(Boolean).map(t));
  if(report.method)blocks.push(t(report.method));
  const steps=list=>{for(const [index,step] of (list||[]).entries()){
    const lines=[`${index+1}. ${t(step.title)}`,equationStepExplanation(step)];
    if(step.variableOrder)lines.push(formula(step.variableOrder));
    if(step.operation)lines.push(equationFormulaText(step.operation,digits));
    if(step.tree||step.exact)lines.push(formula(step));
    lines.push(...(step.equations||[]).map(formula));
    blocks.push(lines.filter(Boolean).join('\n'));
  }};
  steps(report.steps);
  if(report.advancedSteps?.length){blocks.push(t('Advanced solution · Gaussian elimination'));steps(report.advancedSteps);}
  return blocks.join('\n\n');
}

// Keep expanded state through formatting/language changes; each solve starts closed.
export function createEquationSteps(details,body,state,copy) {
  let report=null;
  let advancedOpen=false;
  function renderSteps(target,steps){
    for(const [index,step] of (steps||[]).entries()){
      const item=document.createElement('div');item.className='equation-step';
      const title=document.createElement('p');title.textContent=`${index+1}. ${t(step.title)}`;item.append(title);
      if(step.explanation){const text=document.createElement('p');text.className='step-explanation';text.textContent=equationStepExplanation(step);item.append(text);}
      for(const tree of equationStepTrees(step)){
        const formula=document.createElement('div');formula.className='step-formula';
        formula.append(mathDisplay(tree,state.digits));item.append(formula);
      }
      target.append(item);
    }
  }
  function render(){
    details.hidden=!report;
    body.replaceChildren();
    if(!report)return;
    const actions=document.createElement('div');actions.style.textAlign='right';
    const button=document.createElement('button');button.type='button';button.textContent=t('Copy full solution');
    button.onclick=()=>copy(solutionStepsCopyText(report,state.digits));actions.append(button);body.append(actions);
    if(report.note){const note=document.createElement('p');note.className='hint';note.textContent=report.note.split('\n').map(t).join('\n');body.append(note);}
    if(report.method){const method=document.createElement('p');method.className='step-method';method.textContent=t(report.method);body.append(method);}
    renderSteps(body,report.steps);
    if(report.advancedSteps?.length){
      const advanced=document.createElement('details');advanced.className='equation-advanced';advanced.open=advancedOpen;
      const summary=document.createElement('summary');summary.textContent=t('Advanced solution · Gaussian elimination');advanced.append(summary);
      const content=document.createElement('div');advanced.append(content);renderSteps(content,report.advancedSteps);
      advanced.ontoggle=()=>{advancedOpen=advanced.open;};body.append(advanced);
    }
  }
  return {
    show(result){report=result.ok?result.solutionSteps||result.equationSteps||null:null;details.open=false;advancedOpen=false;render();},
    clear(){report=null;details.open=false;advancedOpen=false;render();},
    render
  };
}
