import {element,control} from './app-ui.js';
import {t} from './i18n.js';

export function statisticsDetectedCrossLoadings(workflow,{factors=workflow.factors.join(','),threshold=workflow.crossThreshold??.3}={}){
  const cutoff=Number(threshold),ids=String(factors).split(',').map(value=>Number(value.trim()));
  if(!Number.isFinite(cutoff)||cutoff<=0)throw new Error('Cross-loading cutoff must be a positive finite number');
  if(!workflow.efaLoadings)return [];
  if(ids.length!==workflow.efaLoadings.length||ids.some(id=>!Number.isInteger(id)||id<1||id>workflow.factorCount))throw new Error('Factor ID count must match selected indicators');
  return workflow.efaLoadings.flatMap((row,i)=>row.flatMap((loading,j)=>j+1!==ids[i]&&Math.abs(loading)>=cutoff?[{indicator:i+1,factor:j+1,loading}]:[]));
}

export function statisticsModelWorkflowPlan(workflow,{factors=workflow.factors.join(','),paths='',cross}={}){
  const data=workflow.data;
  if(!['cfa','sem'].includes(workflow.target)||!Array.isArray(data)||!data.length)throw new Error('Invalid measurement model transfer');
  const assignment=String(factors).trim().split(',').map(value=>{
    if(!/^\d+$/.test(value.trim()))throw new Error('Specify one positive factor ID per selected indicator');
    return Number(value.trim());
  });
  if(assignment.length!==data[0].length)throw new Error('Factor ID count must match selected indicators');
  const k=workflow.factorCount;
  if(!Number.isInteger(k)||k<1||k>assignment.length)throw new Error('Invalid measurement model transfer');
  if(assignment.some(id=>id<1||id>k)||Array.from({length:k},(_,i)=>i+1).some(id=>!assignment.includes(id)))throw new Error('Keep all analyzed factor IDs consecutive from 1');
  if(Array.from({length:k},(_,i)=>i+1).some(id=>assignment.filter(value=>value===id).length<3))throw new Error('Each factor needs at least three indicators');
  const crossPairs=workflow.target==='sem'?workflow.cross:cross??(workflow.efaLoadings?statisticsDetectedCrossLoadings(workflow,{factors}).map(row=>[row.indicator,row.factor]):workflow.cross);
  if(!Array.isArray(crossPairs)||crossPairs.some(pair=>!Array.isArray(pair)||pair.length!==2||!pair.every(Number.isInteger)||pair[0]<1||pair[0]>assignment.length||pair[1]<1||pair[1]>k||pair[1]===assignment[pair[0]-1])||new Set(crossPairs.map(pair=>pair.join(','))).size!==crossPairs.length)throw new Error('Cross-loadings must be distinct additional indicator-factor pairs');
  if(Array.from({length:k},(_,i)=>i+1).some(id=>!assignment.some((factor,index)=>factor===id&&!crossPairs.some(pair=>pair[0]===index+1))))throw new Error('Keep at least one indicator without cross-loadings per factor');
  const pairs=String(paths).trim()?String(paths).split(';').map(pair=>pair.split(',').map(value=>{
    if(!/^\d+$/.test(value.trim()))throw new Error('Use latent paths like 1,2;2,3');
    return Number(value.trim());
  })):[];
  if(workflow.target==='sem'){
    if(k<2)throw new Error('SEM structural paths require at least two latent factors');
    if(!pairs.length)throw new Error('Enter at least one latent structural path');
    if(pairs.some(pair=>pair.length!==2||pair.some(id=>id<1||id>k)||pair[0]===pair[1])||new Set(pairs.map(pair=>pair.join(','))).size!==pairs.length)throw new Error('Use distinct paths between different analyzed factors');
    const remaining=new Set(assignment);
    while(remaining.size){
      const ready=[...remaining].filter(id=>pairs.every(([source,target])=>target!==id||!remaining.has(source)));
      if(!ready.length)throw new Error('SEM requires acyclic directed paths');
      ready.forEach(id=>remaining.delete(id));
    }
  }
  const table=rows=>'['+rows.map(row=>'['+row.join(',')+']').join(',')+']';
  const token=value=>{const text=String(value);if(text!=='NA'&&!/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:e[+-]?\d+)?$/i.test(text))throw new Error('Invalid measurement model transfer');return text;};
  if(data.some(row=>row.length!==assignment.length))throw new Error('Invalid measurement model transfer');
  if(!['complete','fiml'].includes(workflow.missing)||!['configural','metric','scalar','strict'].includes(workflow.invariance)||!['ml','wlsmv'].includes(workflow.estimator))throw new Error('Invalid measurement model transfer');
  const expression=`${workflow.target}(${table(data.map(row=>row.map(token)))},[${assignment}]${workflow.target==='sem'?','+table(pairs):''},${table(crossPairs)},${workflow.missing},[${workflow.groups.map(token)}],${workflow.invariance},${workflow.estimator})`;
  return {target:workflow.target,expression,termLabels:{...workflow.termLabels}};
}

export function appendStatisticsModelWorkflow(panel,workflow,onRun,{isBusy=()=>false}={}){
  if(!workflow||!onRun)return;
  const block=element('section','','statistics-model-workflow');
  block.append(element('h4',t(workflow.target==='cfa'?'CFA from EFA':'SEM from CFA')));
  const factorInput=element('input');factorInput.value=workflow.factors.join(',');
  const label=element('label',t('Factor IDs in analyzed column order'));label.append(factorInput);block.append(label);
  factorInput.disabled=workflow.target==='sem';
  if(workflow.target==='cfa')block.append(element('p',t('Indicators are assigned to the factor with the largest absolute rotated loading. Review or edit the assignments before CFA.'),'hint'));
  const membership=element('div'),message=element('p','','hint');block.append(membership);
  const excluded=new Set(),candidates=element('div'),cutoff=element('input'),automatic=element('input');
  cutoff.value=String(workflow.crossThreshold??.3);automatic.type='checkbox';automatic.checked=true;
  if(workflow.target==='cfa'&&workflow.efaLoadings){
    const thresholdLabel=element('label',t('Minimum absolute secondary loading'));thresholdLabel.append(cutoff);block.append(thresholdLabel);
    const autoLabel=element('label',t('Automatically include detected cross-loadings'),'check');autoLabel.append(automatic);block.append(autoLabel,candidates);
    block.append(element('p',t('Detection uses absolute rotated pattern loadings, not statistical significance. Uncheck candidates to exclude them. Each factor needs an indicator without cross-loadings.'),'hint'));
  }else if(workflow.cross.length)block.append(element('p',`${t('Cross-loadings')}: ${workflow.cross.map(([i,f])=>`${workflow.termLabels[`feature:${i}`]} → ${t('Factor')} ${f}`).join('; ')}`,'hint'));
  const paths=element('input');paths.value='';
  if(workflow.target==='sem'){
    const label=element('label',t('Latent paths: source,target;…'));label.append(paths);block.append(label);
    block.append(element('p',t('CFA transfers the measurement model, data and estimation options. Specify structural paths before running SEM.'),'hint'));
  }
  let plan=null;
  const button=control(workflow.target==='cfa'?'Run CFA':'Run SEM',()=>{if(plan&&!isBusy())onRun(plan);});
  button.dataset.modelWorkflowRun=workflow.target;
  const update=()=>{
    membership.replaceChildren();
    const ids=factorInput.value.split(',').map(value=>Number(value.trim()));
    for(let factor=1;factor<=workflow.factorCount;factor++)membership.append(element('p',`${t('Factor')} ${factor}: ${ids.flatMap((id,i)=>id===factor?[workflow.termLabels[`feature:${i+1}`]||`${t('Feature')} ${i+1}`]:[]).join(', ')}`,'hint'));
    try{
      let detected=null;
      cutoff.disabled=!automatic.checked;
      if(workflow.target==='cfa'&&workflow.efaLoadings){
        try{detected=statisticsDetectedCrossLoadings(workflow,{factors:factorInput.value,threshold:cutoff.value});}
        catch(error){if(automatic.checked)throw error;detected=[];}
      }
      candidates.replaceChildren();
      if(detected){
        if(!detected.length)candidates.append(element('p',t('No cross-loadings meet the current cutoff.'),'hint'));
        for(const row of detected){
          const key=`${row.indicator},${row.factor}`,check=element('input');check.type='checkbox';check.checked=automatic.checked&&!excluded.has(key);check.disabled=!automatic.checked;
          const label=element('label',`${workflow.termLabels[`feature:${row.indicator}`]} → ${t('Factor')} ${row.factor} · ${Number(row.loading).toPrecision(4)}`,'check');label.append(check);candidates.append(label);
          check.onchange=()=>{if(check.checked)excluded.delete(key);else excluded.add(key);update();};
        }
      }
      const cross=detected?(automatic.checked?detected.filter(row=>!excluded.has(`${row.indicator},${row.factor}`)).map(row=>[row.indicator,row.factor]):[]):undefined;
      plan=statisticsModelWorkflowPlan(workflow,{factors:factorInput.value,paths:paths.value,cross});message.textContent='';
    }
    catch(error){plan=null;message.textContent=t(error.message);}
    button.dataset.invalidAnalysis=String(!plan);button.disabled=!plan||isBusy();
  };
  factorInput.oninput=update;paths.oninput=update;cutoff.oninput=update;automatic.onchange=update;update();block.append(message,button);panel.append(block);
}
