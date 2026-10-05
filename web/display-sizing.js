function mathHeight(viewport){
  // MathML scripts can extend beyond their layout box in Chromium. Include
  // that overflow so horizontal scrollers don't acquire a vertical range.
  const boxes=[...viewport.querySelectorAll('math')].map(math=>{
    const box=math.getBoundingClientRect();
    return {top:box.top,bottom:box.top+Math.max(box.height,math.scrollHeight),height:box.height};
  }).filter(box=>box.height>0);
  return boxes.length?Math.max(...boxes.map(box=>box.bottom))-Math.min(...boxes.map(box=>box.top)):0;
}

export function fitCalculationDisplay(input,answer){
  for(const part of answer.querySelectorAll('.result-part')){
    const style=answer.ownerDocument.defaultView.getComputedStyle(part);
    part.style.minHeight=`${Math.ceil(mathHeight(part)+(parseFloat(style.paddingTop)||0)+(parseFloat(style.paddingBottom)||0))}px`;
  }
  const inputMath=mathHeight(input),answerMath=mathHeight(answer);
  const inputHeight=Math.max(60,Math.ceil(inputMath+16)),answerHeight=Math.max(56,Math.ceil(answerMath+16));
  input.style.minHeight=`${inputHeight}px`;answer.style.minHeight=`${answerHeight}px`;
  // Grow only the scrollable math content, leaving the keypad track fixed.
}

export function createDisplaySizing(display,input,answer){
  const observedMath=new Set();
  const refresh=()=>{
    // Recalculate after font loading, font-size changes, and root layout too,
    // even when the display's existing minimum height absorbs the change.
    const currentMath=new Set(answer.querySelectorAll('math'));
    for(const math of observedMath)if(!currentMath.has(math)){observer?.unobserve(math);observedMath.delete(math);}
    for(const math of currentMath)if(!observedMath.has(math)){observer?.observe(math);observedMath.add(math);}
    if(display.ownerDocument.documentElement.dataset.workspace!=='scientific'){
      input.style.removeProperty('min-height');answer.style.removeProperty('min-height');
      for(const part of answer.querySelectorAll('.result-part'))part.style.removeProperty('min-height');
      return;
    }
    fitCalculationDisplay(input,answer);
  };
  const Observer=display.ownerDocument.defaultView.ResizeObserver;
  const observer=Observer?new Observer(refresh):null;
  observer?.observe(input);observer?.observe(answer);
  return {refresh,dispose:()=>observer?.disconnect()};
}
