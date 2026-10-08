import {$} from './app-ui.js';
import {setText} from './i18n.js';

export function createEngineUI({engine,onChange,onReady,cancelPreview}) {
  let busy=false,stopTimer=null,stopVisible=false;
  setText($('stop'),'Cancel');
  const actions=new Map(['equation','statistics','statistics-advanced','regression'].map(context=>
    [context,document.querySelector(`[data-run="${context}"]`)]));
  const labels=new Map([...actions].map(([context])=>[context,context==='equation'?'Solve':'Analyze']));
  function updateStopButton(){
    const active=engine.pending?.context,visible=stopVisible&&!!engine.pending;
    for(const [context,button] of actions){
      const cancel=visible&&active===context;
      button.dataset.cancelCalculation=String(cancel);
      setText(button,cancel?'Cancel':labels.get(context));
      button.disabled=cancel?false:!engine.ready||busy;
    }
    const fallback=visible&&$('mode').value==='scientific';
    $('stop').disabled=!fallback;$('stop').hidden=false;$('stop').style.visibility=fallback?'':'hidden';
  }
  for(const button of actions.values())button.addEventListener('click',event=>{
    if(button.dataset.cancelCalculation!=='true')return;
    event.preventDefault();event.stopImmediatePropagation();cancelPreview();engine.cancel();
  },true);
  function updateButtons(){updateStopButton();onChange();}
  document.documentElement.dataset.busy='false';document.documentElement.dataset.engine='loading';
  engine.addEventListener('status',event=>{setText($('status'),event.detail);document.documentElement.dataset.engine=engine.ready?'ready':'loading';updateButtons();});
  engine.addEventListener('ready',()=>{document.documentElement.dataset.engine='ready';updateButtons();onReady();});
  // Do not compete with the first WASM/SymPy download by precaching the same
  // large runtime files. Offline installation starts only after the engine works.
  engine.addEventListener('ready',registerOfflineCache,{once:true});
  engine.addEventListener('busy',event=>{busy=event.detail;document.documentElement.dataset.busy=String(busy);updateButtons();if(!busy){onReady();}});
  engine.addEventListener('activity',event=>{
    clearTimeout(stopTimer);stopTimer=null;stopVisible=false;updateStopButton();
    if(event.detail)stopTimer=setTimeout(()=>{stopTimer=null;stopVisible=true;updateStopButton();},1000);
  });
  $('stop').onclick=()=>{cancelPreview();engine.cancel();};
  $('retry').onclick=()=>{cancelPreview();engine.cancel('계산 엔진을 재시작합니다.');};
  function registerOfflineCache() {
    if('serviceWorker' in navigator && location.protocol!=='file:'){
      navigator.serviceWorker.register('./sw.js',{updateViaCache:'none'}).then(registration=>{
        if(registration.active)setText($('offline-status'),'오프라인 사용 가능');
        registration.addEventListener('updatefound',()=>{const worker=registration.installing;worker?.addEventListener('statechange',()=>{if(worker.state==='activated')setText($('offline-status'),'오프라인 사용 가능');});});
      }).catch(()=>{setText($('offline-status'),'브라우저 캐시 사용');});
    }
  }
  return {get busy(){return busy;},updateStopButton,dispose:()=>clearTimeout(stopTimer)};
}
