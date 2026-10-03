import {element,control} from './app-ui.js';
import {t} from './i18n.js';
import {defaultGraphColors,hexToHsl,hslToHex} from './graph-colors.js';

export function graphColorSettings({state,persist,refreshDisplays}){
  const section=element('section','','graph-color-settings'),heading=element('h3','Graph colors'),choices=element('div','','graph-color-choices'),preview=element('div','','graph-color-preview'),swatch=element('span','','graph-color-swatch'),code=element('code'),sliders=element('div'),buttons=[];
  let selected=0,hsl=hexToHsl(state.graphColors[0]);
  const inputs=[],outputs=[];
  function render(){
    buttons.forEach((button,i)=>{button.style.setProperty('--swatch',state.graphColors[i]);button.setAttribute('aria-pressed',String(i===selected));});
    swatch.style.backgroundColor=state.graphColors[selected];code.textContent=state.graphColors[selected].toUpperCase();
    inputs.forEach((input,i)=>{input.value=hsl[i];outputs[i].textContent=`${Math.round(hsl[i])}${i===0?'°':'%'}`;});
    const [h,s,l]=hsl;
    inputs[0].style.background='linear-gradient(to right, #ff0000, #ffff00, #00ff00, #00ffff, #0000ff, #ff00ff, #ff0000)';
    inputs[1].style.background=`linear-gradient(to right, hsl(${h} 0% ${l}%), hsl(${h} 100% ${l}%))`;
    inputs[2].style.background=`linear-gradient(to right, #000000, hsl(${h} ${s}% 50%), #ffffff)`;
  }
  function save(){render();refreshDisplays();persist();}
  for(let i=0;i<6;i++){
    const button=control(`f${i+1}`,()=>{selected=i;hsl=hexToHsl(state.graphColors[i]);render();});
    button.dataset.graphColor=String(i);button.setAttribute('aria-label',`${t('Graph color')} ${i+1}`);buttons.push(button);choices.append(button);
  }
  ['Hue (H)','Saturation (S)','Lightness (L)'].forEach((name,i)=>{
    const label=element('label'),row=element('span','','graph-color-slider-label'),input=element('input'),output=element('output');
    input.type='range';input.min=0;input.max=i===0?360:100;input.step=i===0?'1':'0.1';input.dataset.hsl=String(i);input.className='hsl-slider';input.setAttribute('aria-label',t(name));
    input.oninput=()=>{hsl[i]=Number(input.value);state.graphColors[selected]=hslToHex(hsl);save();};
    inputs.push(input);outputs.push(output);row.append(element('span',name),output);label.append(row,input);sliders.append(label);
  });
  preview.append(swatch,code);
  const resets=element('div','','graph-color-resets');
  resets.append(control('Reset color',()=>{state.graphColors[selected]=defaultGraphColors[selected];hsl=hexToHsl(state.graphColors[selected]);save();}),control('Reset all colors',()=>{state.graphColors=[...defaultGraphColors];hsl=hexToHsl(state.graphColors[selected]);save();}));
  section.append(heading,choices,preview,sliders,resets);render();return section;
}
