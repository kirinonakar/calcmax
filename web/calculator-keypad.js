import {renderKeypad as buildKeypad,updateKeypadState} from './keypad.js';
import {translateDOM} from './i18n.js';
import {$} from './app-ui.js';

export function createCalculatorKeypad({state,persist,isCalcActive,isBusy,handleKey,updateButtons}) {
  let shift=false,alpha=false,hyperbolic=false,keypadSignature='',keyAudioContext=null;
  async function performKey(input) {
    if(isCalcActive()&&isBusy()&&input!=='AC')return;
    if(state.haptics)navigator.vibrate?.(12);
    if(state.sound)try{const context=keyAudioContext||(keyAudioContext=new (window.AudioContext||window.webkitAudioContext)()),oscillator=context.createOscillator(),gain=context.createGain();oscillator.frequency.value=1200;gain.gain.value=.025;oscillator.connect(gain);gain.connect(context.destination);oscillator.start();oscillator.stop(context.currentTime+.025);oscillator.onended=()=>{oscillator.disconnect();gain.disconnect();};}catch{}
    const jumps={'Scientific/CAS':'scientific',Graph:'graph',Python:'python',Matrix:'matrix',Vector:'vector',Statistics:'statistics',Programmer:'programmer',Units:'units',Constants:'constants',Equations:'equation'};
    if(input==='SHIFT'){shift=!shift;alpha=false;renderKeypad();return;}
    if(input==='ALPHA'){alpha=!alpha;shift=false;renderKeypad();return;}
    if(input==='SECOND'){state.secondKeys=!state.secondKeys;shift=false;alpha=false;renderKeypad();persist();return;}
    if(isCalcActive()){
      if(['AC','=','CALC'].includes(input))return handleKey(input);
      if(['MODE','STO','RCL','Clear','CLR ALL','SOLVE','RELATION','ENG','ENG−','S⇔D','MIXED','M+','M−','INS','MATRIX_INPUT','MATRIX_SIZE','TO_GRAPH','TO_SYSTEM',...Object.keys(jumps)].includes(input)){shift=false;alpha=false;renderKeypad();return;}
    }
    if(input==='HYP'){hyperbolic=!hyperbolic;shift=false;alpha=false;renderKeypad();return;}
    if(hyperbolic&&/^(?:a?sin|a?cos|a?tan)\(\)$/.test(input))input=input.replace('()','h()');
    const task=handleKey(input);
    if(task?.then)await task;
    shift=false;alpha=false;hyperbolic=false;renderKeypad();
  }
  function renderKeypad() {
    const signature=`${state.secondKeys}:${shift}:${alpha}:${hyperbolic}`;if(keypadSignature===signature)return;keypadSignature=signature;
    const press=k=>performKey(k.input==='CALC'&&alpha?'RELATION':alpha&&k.alpha?k.alpha:shift&&k.alternate?k.alternate:k.input);
    if(!$('keypad').hasChildNodes())buildKeypad($('keypad'),{second:state.secondKeys,shift,alpha,hyperbolic,press,longPress:k=>{if(['SHIFT','ALPHA','SECOND'].includes(k.input)||!k.alternate)press(k);else{shift=false;alpha=false;performKey(k.alternate);}}});
    else updateKeypadState($('keypad'),{second:state.secondKeys,shift,alpha,hyperbolic});
    $('key-modifier').textContent=shift?'SHIFT':alpha?'ALPHA':hyperbolic?'HYP':state.secondKeys?'2ND':'';
    $('key-modifier').classList.toggle('alpha',alpha);translateDOM($('keypad'));updateButtons();
  }
  return {press:performKey,render:renderKeypad};
}
