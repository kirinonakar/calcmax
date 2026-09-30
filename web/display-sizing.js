function mathHeight(viewport){
  const boxes=[...viewport.querySelectorAll('math')].map(math=>math.getBoundingClientRect()).filter(box=>box.height>0);
  return boxes.length?Math.max(...boxes.map(box=>box.bottom))-Math.min(...boxes.map(box=>box.top)):0;
}

export function fitCalculationDisplay(display,input,answer){
  const inputMath=mathHeight(input),answerMath=mathHeight(answer);
  const inputHeight=Math.max(60,Math.ceil(inputMath+16)),answerHeight=Math.max(56,Math.ceil(answerMath+16));
  input.style.minHeight=`${inputHeight}px`;answer.style.minHeight=`${answerHeight}px`;
  // Reserve room for the editing row, toolbar, and result note. The grid
  // gives tall expressions more room while retaining a usable keypad.
  display.style.setProperty('--calculation-content-height',inputMath||answerMath?`${inputHeight+answerHeight+74}px`:'0px');
}

export function createDisplaySizing(display,input,answer){
  const refresh=()=>{
    if(display.ownerDocument.documentElement.dataset.workspace!=='scientific'){
      input.style.removeProperty('min-height');answer.style.removeProperty('min-height');display.style.removeProperty('--calculation-content-height');return;
    }
    fitCalculationDisplay(display,input,answer);
  };
  const Observer=display.ownerDocument.defaultView.ResizeObserver;
  const observer=Observer?new Observer(refresh):null;
  observer?.observe(input);observer?.observe(answer);
  return {refresh,dispose:()=>observer?.disconnect()};
}
