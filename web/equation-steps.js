import {t} from './i18n.js';
import {resultMathDisplay} from './result-display.js';

// Keep expanded state through formatting/language changes; each solve starts closed.
export function createEquationSteps(details,body,state) {
  let report=null;
  let advancedOpen=false;
  function renderSteps(target,steps){
    for(const [index,step] of (steps||[]).entries()){
      const item=document.createElement('div');item.className='equation-step';
      const title=document.createElement('p');title.textContent=`${index+1}. ${t(step.title)}`;item.append(title);
      if(step.explanation){const text=document.createElement('p');text.className='step-explanation';text.textContent=t(step.explanation);item.append(text);}
      for(const tree of [step.variableOrder?.tree,step.operation,step.tree,...(step.equations||[]).map(formula=>formula.tree)].filter(Boolean))item.append(resultMathDisplay(tree,state.digits,false));
      target.append(item);
    }
  }
  function render(){
    details.hidden=!report;
    body.replaceChildren();
    if(!report)return;
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
    show(result){report=result.ok?result.equationSteps||null:null;details.open=false;advancedOpen=false;render();},
    clear(){report=null;details.open=false;advancedOpen=false;render();},
    render
  };
}
