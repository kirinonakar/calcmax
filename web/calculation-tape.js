import {expressionDisplay} from './expression-display.js';
import {mathDisplay} from './math-display.js';
import {t} from './i18n.js';

export function previousCalculations(history,active=null,clearedAt=0){
  return history.filter(entry=>entry!==active&&(!clearedAt||Number(entry.time)>clearedAt)).slice(0,10).reverse();
}
function compact(tree){
  const pending=[tree];let count=0;
  while(pending.length){const node=pending.pop();if(!node)continue;if(++count>120)return true;pending.push(...(node.args||[]));}
  return false;
}
export function renderPreviousCalculations(container,entries,{decimal=false,digits=10,engineering=false,grouping=false,reuseDisabled=false,reuse}){
  const rows=entries.map(entry=>{
    const row=document.createElement('article');row.className='tape-entry';
    const input=document.createElement('button');input.type='button';input.className='tape-expression';input.disabled=reuseDisabled;
    input.setAttribute('aria-label',`${t('Reuse calculation')}: ${entry.source.slice(0,120)}`);
    input.dataset.source=entry.source;
    try{if(entry.source.length>800)throw new Error('compact');input.append(expressionDisplay(entry.source));}catch{input.textContent=entry.source.slice(0,800);}
    input.addEventListener('click',()=>reuse(entry));
    const output=document.createElement('div');output.className='tape-result';
    const text=(decimal?entry.decimal:entry.exact)||'',display=entry.display||{},tree=decimal||engineering?display.decimalTree||display.tree:display.tree;
    if(tree&&!compact(tree)&&text.length<10000&&!text.includes('\n'))output.append(mathDisplay(tree,digits,decimal||display.approximate||engineering,{engineering,grouping}));
    else if(text.length<=1200&&!text.includes('\n'))try{output.append(expressionDisplay(text));}catch{output.textContent=text;}
    else output.textContent=text.slice(0,1200);
    row.append(input,output);
    if(entry.calcValues){const note=document.createElement('div');note.className='tape-note';note.textContent=Object.entries(entry.calcValues).map(([name,value])=>`${name} = ${value}`).join(', ');row.append(note);}
    return row;
  });
  container.replaceChildren(...rows);
}

// New input/results follow the newest line; wheel/touch gestures can browse older
// rows without focus or status updates repeatedly snapping the tape back down.
export function followTape(scroll,{schedule=setTimeout,cancel=clearTimeout,now=Date.now}={}){
  let dragging=false,quietUntil=0,pending=false,timer=null;
  function clear(){if(timer!==null)cancel(timer);timer=null;}
  function settle(){clear();if(pending&&!dragging)timer=schedule(()=>{timer=null;latest();},Math.max(0,quietUntil-now()));}
  function latest(){
    if(dragging||quietUntil>now()){pending=true;settle();return;}
    pending=false;clear();scroll.scrollTop=scroll.scrollHeight;
  }
  scroll.addEventListener('pointerdown',()=>{dragging=true;clear();});
  for(const name of ['pointerup','pointercancel'])scroll.addEventListener(name,()=>{dragging=false;quietUntil=now()+160;settle();});
  scroll.addEventListener('wheel',()=>{quietUntil=now()+160;settle();},{passive:true});
  scroll.addEventListener('scroll',()=>{if(pending){quietUntil=now()+160;settle();}},{passive:true});
  return {latest,dispose:clear};
}
