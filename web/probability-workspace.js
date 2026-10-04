import {$,element,control,value} from './app-ui.js';
import {getLanguage,t} from './i18n.js';
import {displayNumber} from './display-format.js';
import {probabilitySchema as schema} from './probability-schema.js';

const label=item=>getLanguage()==='ko'?(item.ko||item.label):item.label;
const fieldLabel=field=>field[getLanguage()==='ko'?2:1];
export function probabilityNumber(value,digits){
  const text=String(value),match=/^(-?)0\.(0*)([1-9]\d*)$/.exec(text);
  if(match&&match[2].length>=digits){
    const significant=match[3];
    return displayNumber(`${match[1]}${significant[0]}.${significant.slice(1)}e-${match[2].length+1}`,Math.max(3,digits-1));
  }
  return displayNumber(text,digits);
}

export function createProbabilityWorkspace({state,engine,persist,requestOptions}) {
  let revision=0,lastResult=null,activeExample='';
  const category=()=>value('probability-category');
  const distribution=()=>schema.distributions.find(d=>d.id===value('probability-distribution'))||schema.distributions[0];
  const tool=()=>schema.tools.find(d=>d.id===category());
  const operations=()=>category()==='distribution'?schema.operations.filter(op=>(!op.discrete||distribution().discrete)&&(!op.continuous||!distribution().discrete)):tool().operations;
  const selectedOperation=()=>operations().find(op=>op.id===value('probability-operation'));
  const fields=()=>category()==='distribution'?[...distribution().fields,...selectedOperation().fields]:(selectedOperation().fields||tool().fields).filter(f=>f[0]!=='k'||['exactly','atLeast','atMost'].includes(value('probability-operation')));
  const fieldId=key=>`probability-${category()==='distribution'?distribution().id:category()}-${key}`;
  function invalidate(){revision++;lastResult=null;$('probability-result').hidden=true;$('probability-plot').hidden=true;$('probability-error').hidden=true;}
  function fillSelect(id,items,fallback){
    const select=$(id),selected=select.value||state.fields[id]||fallback;
    select.replaceChildren(...items.map(item=>{const option=element('option',label(item));option.value=item.id;return option;}));
    select.value=items.some(item=>item.id===selected)?selected:fallback||items[0].id;
  }
  function renderForm(){
    fillSelect('probability-category',schema.categories,'basic');
    fillSelect('probability-distribution',schema.distributions,'binomial');
    $('probability-distribution-label').hidden=category()!=='distribution';
    const ops=operations();fillSelect('probability-operation',ops,ops[0].id);
    $('probability-operations').replaceChildren(...ops.map(op=>{
      const button=control(label(op),()=>{persist();$('probability-operation').value=op.id;activeExample='';invalidate();renderForm();persist();},'probability-operation');
      button.setAttribute('aria-pressed',String(op.id===value('probability-operation')));return button;
    }));
    const inputs=fields().map(field=>{
      const id=fieldId(field[0]),wrapper=element('label'),name=element('span',fieldLabel(field)),input=element('input');
      input.id=id;input.type='text';input.inputMode='decimal';input.autocomplete='off';input.value=state.fields[id]??field[3];
      input.addEventListener('input',()=>{state.fields[id]=input.value;activeExample='';invalidate();persist();renderExamples();});
      input.addEventListener('keydown',event=>{if(event.key==='Enter'){event.preventDefault();if(engine.ready&&!engine.pending)void run();}});
      input.disabled=category()==='events'&&field[0]==='intersection'&&$('probability-independent').checked;
      wrapper.append(name,input);return wrapper;
    });
    $('probability-fields').replaceChildren(...inputs);
    $('probability-independent-label').hidden=category()!=='events'||value('probability-operation')==='conditionalCounts';
    $('probability-number-hint').hidden=['basic','dice','draw','counting'].includes(category())||value('probability-operation')==='conditionalCounts';
    const source=selectedOperation().hint?selectedOperation():category()==='distribution'?distribution():tool();
    $('probability-hint').textContent=getLanguage()==='ko'?source.hintKo:source.hint;
    renderExamples();
  }
  function renderExamples(){
    $('probability-examples').replaceChildren(...schema.examples.map(example=>{
      const button=control(label(example),()=>applyExample(example));
      button.setAttribute('aria-pressed',String(activeExample===example.id));return button;
    }));
  }
  function applyExample(example){
    persist();invalidate();
    $('probability-category').value=example.category;
    if(example.distribution)$('probability-distribution').value=example.distribution;
    // Populate fields before rebuilding, and select the operation after its options exist.
    const prefix=example.distribution||example.category;
    for(const [key,v] of Object.entries(example.values))state.fields[`probability-${prefix}-${key}`]=v;
    $('probability-independent').checked=false;
    renderForm();$('probability-operation').value=example.operation;
    activeExample=example.id;renderForm();persist();
  }
  function request(){
    const values=Object.fromEntries(fields().map(field=>[field[0],value(fieldId(field[0]))]));
    return {...requestOptions(),action:'probability',category:category(),distribution:distribution().id,operation:value('probability-operation'),independent:$('probability-independent').checked,values};
  }
  function renderResult(result){
    const target=$('probability-result');target.replaceChildren();target.hidden=false;
    target.append(element('div',result.formula,'probability-formula'));
    if(result.fraction?.includes('/'))target.append(element('div',result.fraction,'probability-fraction'));
    target.append(element('div',probabilityNumber(result.value,state.digits),'probability-value'));
    if(result.isProbability)target.append(element('div',probabilityNumber(result.percent.replace(/%$/,''),state.digits)+'%','probability-percent'));
    if(result.details?.length){
      const row=element('div','','probability-details');
      for(const detail of result.details)row.append(element('span',`${t(detail.label)}  ${displayNumber(detail.value,state.digits)}`));
      target.append(row);
    }
    if(result.note)target.append(element('p',t(result.note),'hint'));
    renderPlot(result.plot);
  }
  function renderPlot(plot){
    const target=$('probability-plot');target.replaceChildren();target.hidden=!plot;
    if(!plot)return;
    const points=plot.points,first=points[0][0],last=points.at(-1)[0],max=Math.max(...points.map(p=>p[1]));
    if(last<=first||max<=0){target.hidden=true;return;}
    const svg=document.createElementNS('http://www.w3.org/2000/svg','svg');svg.setAttribute('viewBox','0 0 440 150');svg.setAttribute('role','img');svg.setAttribute('aria-label',t('Probability distribution preview'));
    const x=v=>25+(v-first)/(last-first)*390,y=v=>120-v/max*100;
    const add=(tag,attributes)=>{const n=document.createElementNS(svg.namespaceURI,tag);for(const [k,v] of Object.entries(attributes))n.setAttribute(k,String(v));svg.append(n);return n;};
    add('line',{x1:25,x2:415,y1:120,y2:120,stroke:'var(--line)'});
    if(plot.discrete){
      const width=Math.max(2,Math.min(24,300/points.length));
      for(const [px,py,selected] of points)add('rect',{x:x(px)-width/2,y:y(py),width,height:120-y(py),fill:selected?'var(--accent)':'var(--line)'});
    }else{
      for(let i=1;i<points.length;i++)if(points[i-1][2]&&points[i][2])add('path',{d:`M ${x(points[i-1][0])} 120 L ${x(points[i-1][0])} ${y(points[i-1][1])} L ${x(points[i][0])} ${y(points[i][1])} L ${x(points[i][0])} 120 Z`,fill:'var(--accent)',opacity:.3});
      add('path',{d:points.map(([px,py],i)=>`${i?'L':'M'} ${x(px)} ${y(py)}`).join(' '),stroke:'var(--accent)',fill:'none','stroke-width':2});
    }
    for(const [px,anchor] of [[first,'start'],[last,'end']]){const n=add('text',{x:x(px),y:142,fill:'var(--muted)','font-size':11,'text-anchor':anchor});n.textContent=displayNumber(px,3);}
    target.append(svg,element('p',t(plot.event===false?'Mass / density preview':'Preview · shaded region is the selected event'),'hint'));
  }
  async function run(){
    persist();const draft=request();invalidate();const version=revision;
    try{
      const result=await engine.execute(draft);
      if(version!==revision)return;
      if(result.ok){lastResult=result;renderResult(result);$('probability-result').scrollIntoView?.({behavior:'smooth',block:'nearest'});}
      else{$('probability-error').textContent=translateProbabilityError(result.error);$('probability-error').hidden=false;}
    }catch(error){if(version===revision){$('probability-error').textContent=translateProbabilityError(error.message);$('probability-error').hidden=false;}}
  }
  function translateProbabilityError(message){
    const match=/^(.*?)( \(([^)]+)\))?$/.exec(message||'');
    const field=fields().find(f=>f[0]===match?.[3]);
    return t(match?.[1]||message)+(field?` (${fieldLabel(field)})`:match?.[2]||'');
  }
  for(const id of ['probability-category','probability-distribution'])$(id).addEventListener('change',()=>{persist();activeExample='';invalidate();$('probability-operation').replaceChildren();state.fields['probability-operation']='';renderForm();persist();});
  $('probability-independent').addEventListener('change',()=>{invalidate();persist();renderForm();});
  $('probability-reset').onclick=()=>{invalidate();activeExample='';for(const field of fields())delete state.fields[fieldId(field[0])];renderForm();persist();};
  renderForm();
  return {run,request,applyExample,render:()=>{persist();renderForm();if(lastResult)renderResult(lastResult);}};
}
