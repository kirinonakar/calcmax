// Android KeySpec layout from ui/CalculatorKeypad.kt. Keep the two pages and
// shifted/alpha legends together so touch, mouse, and keyboard use the same action.
const key=(title,input=title,secondary='',alternate='',alpha='',type='scientific')=>({title,input,secondary,alternate,alpha,type});
export const scientificRows=[
  [key('a/b','()/()','mixed','mixed(,,)'),key('√','sqrt()','³√','cbrt()'),key('x²','^2','x³','^3'),key('x□','^()','ⁿ√','nthroot(,)'),key('log','log()','10ˣ','10^()','n'),key('ln','ln()','eˣ','e^()','t')],
  [key('(−)','NEG','∠','∠','A'),key('°′″','DMS_INPUT','←','DMS','B'),key('hyp','HYP','Abs','abs()','C'),key('sin','sin()','sin⁻¹','asin()','D'),key('cos','cos()','cos⁻¹','acos()','r'),key('tan','tan()','tan⁻¹','atan()','F')],
  [key('RCL','RCL','STO','STO'),key('ENG','ENG','←','ENG−','i'),key('(','(','%','%','z'),key(')',')',',',',','x'),key('S⇔D','S⇔D','a b/c ⇔ d/c','MIXED','y'),key('M+','M+','M−','M−','M')]
];
export const secondRows=[
  [key('simp','simplify()'),key('factor','factor()','factorint','factorint()'),key('expand','expand()'),key('x','x','^','^()'),key('y','y','=','RELATION'),key('z')],
  [key('⌊x⌋','floor()','mod','mod(,)'),key('⌈x⌉','ceil()','divmod','divmod(,)'),key('∞','oo','sign','sign()'),key(','),key('{','{','[','['),key('}','}',']',']')],
  [key('MATRIX','MATRIX_INPUT','n×m','','','action'),key('det','det()','Pol','pol(,)'),key('inv','inverse()','Rec','rec(,)'),key('T','transpose()'),key('‖v‖','norm()'),key('GRAPH','TO_GRAPH','MODE','Graph','','action')]
];
export const numericRows=[
  [key('7','7','CONST','Constants'),key('8','8','CONV','Units'),key('9','9','CLR','Clear'),key('DEL','DEL','INS','INS','','danger'),key('AC','AC','CLR ALL','CLR ALL','','danger')],
  [key('4','4','MATRIX','Matrix'),key('5','5','VECTOR','Vector'),key('6','6','EQN','Equations'),key('×','*','nPr','nPr(,)'),key('÷','÷','nCr','nCr(,)')],
  [key('1','1','STAT','Statistics'),key('2','2','PY','Python'),key('3','3','BASE','Programmer'),key('+','+','π','pi'),key('−','-','𝑒','e')],
  [key('0','0','Ran#','RANDOM'),key('.','.','','','randInt(,)'),key('×10ˣ','*10^()'),key('Ans'),key('=','=','GRAPH','Graph')]
].map(row=>row.map(k=>({...k,type:k.type==='danger'?'danger':'numeric'})));
export function topKeys(second=false){return [key('SHIFT','SHIFT','','','','utility'),key('ALPHA','ALPHA','','','','utility'),key('MODE','MODE','','Scientific/CAS','','utility'),key(second?'1st':'2nd','SECOND','','','','utility')];}
export function topFunctions(second=false){return second?
  [key('d/dx','diff(,x)','∫','integrate(,x)'),key('lim','limit(,x,)'),key('sinc','sinc()'),key('Π','product(,x,,)')]:
  [key('CALC','CALC','SOLVE','SOLVE','='),key('∫','integrate(,x,,)','d/dx','nderivative(,x,)',':'),key('x⁻¹','^(-1)','x!','!'),key('logₐ□','log(,)','Σ','sum(,x,,)')];
}

export function bindKeyPress(button,press,longPress,{delay=500,schedule=setTimeout,cancel=clearTimeout}={}) {
  let timer=null,start=null,longFired=false;
  const clear=()=>{if(timer!==null)cancel(timer);timer=null;};
  button.addEventListener('pointerdown',event=>{
    if(button.disabled || event.button>0 || event.isPrimary===false)return;
    clear();longFired=false;start={x:event.clientX,y:event.clientY};
    button.setPointerCapture?.(event.pointerId);
    timer=schedule(()=>{timer=null;longFired=true;longPress();},delay);
  });
  button.addEventListener('pointermove',event=>{if(start&&Math.hypot(event.clientX-start.x,event.clientY-start.y)>12){clear();start=null;}});
  button.addEventListener('pointerup',()=>{clear();start=null;});
  button.addEventListener('pointercancel',()=>{clear();start=null;longFired=true;});
  button.addEventListener('lostpointercapture',clear);
  button.addEventListener('contextmenu',event=>event.preventDefault());
  button.addEventListener('click',event=>{clear();if(longFired){event.preventDefault();longFired=false;return;}press();});
  return clear;
}

export function renderKeypad(container,{second=false,shift=false,alpha=false,hyperbolic=false,press,longPress}) {
  const create=k=>{
    const button=document.createElement('button');button.type='button';button.className=`key ${k.type}`;button.dataset.input=k.input;
    button.keySpec=k;
    if(k.input==='='||k.input==='CALC')button.dataset.evaluate='true';
    button.setAttribute('aria-label',shift&&k.alternate?k.alternate:k.title);
    if(['SHIFT','ALPHA','SECOND','HYP'].includes(k.input)){const active=k.input==='SHIFT'?shift:k.input==='ALPHA'?alpha:k.input==='SECOND'?second:hyperbolic;button.classList.toggle('active',active);button.setAttribute('aria-pressed',String(active));}
    const legends=document.createElement('small');legends.className='key-hints';legends.append(document.createTextNode(k.secondary));
    if(k.alpha){const letter=document.createElement('span');letter.className='alpha-legend';letter.textContent=k.alpha;legends.append(document.createTextNode('  '),letter);}
    const face=document.createElement('span');face.className='key-face';
    if(k.input==='()/()'){
      const fraction=document.createElement('span');fraction.className='key-fraction';fraction.setAttribute('aria-hidden','true');
      for(const [text,className] of [['□',''],['','key-fraction-bar'],['□','']]){const part=document.createElement('span');part.textContent=text;part.className=className;fraction.append(part);}
      face.append(fraction);
    }else if(k.input==='DEL'){
      const ns='http://www.w3.org/2000/svg',icon=document.createElementNS(ns,'svg'),shape=document.createElementNS(ns,'path'),cross=document.createElementNS(ns,'path');
      icon.setAttribute('viewBox','0 0 24 18');icon.setAttribute('aria-hidden','true');icon.setAttribute('focusable','false');icon.classList.add('key-backspace');
      icon.setAttribute('fill','none');icon.setAttribute('stroke','currentColor');icon.setAttribute('stroke-width','1.8');icon.setAttribute('stroke-linecap','round');icon.setAttribute('stroke-linejoin','round');
      shape.setAttribute('d','M6.1 1.4H20.2C21.2 1.4 22 2.2 22 3.2V14.8C22 15.8 21.2 16.6 20.2 16.6H6.1C5.5 16.6 5.1 16.4 4.8 15.9L1.8 9.8C1.5 9.3 1.5 8.7 1.8 8.2L4.8 2.1C5.1 1.6 5.5 1.4 6.1 1.4Z');cross.setAttribute('d','M10.9 5.8L17.3 12.2M17.3 5.8L10.9 12.2');cross.setAttribute('stroke-width','2.2');icon.append(shape,cross);face.append(icon);
    }else face.textContent=k.title;
    button.append(legends,face);bindKeyPress(button,()=>press(k),()=>longPress(k));return button;
  };
  const top=document.createElement('div');top.className='keypad-top';
  topKeys(second).forEach((k,i)=>{const button=create(k);button.style.gridColumn=String(i<2?i+1:i+3);button.style.gridRow='1';top.append(button);});
  topFunctions(second).forEach((k,i)=>{const button=create(k);button.style.gridColumn=String(i<2?i+1:i+3);button.style.gridRow='2';top.append(button);});
  const directions=document.createElement('div');directions.className='direction-pad';directions.setAttribute('aria-label','Cursor controls');
  for(const [title,input,position] of [['▲','UP','up'],['◀','LEFT','left'],['▶','RIGHT','right'],['▼','DOWN','down']]){const button=create(key(title,input));button.className=`direction-key ${position}`;button.setAttribute('aria-label',`Cursor ${position}`);directions.append(button);}
  top.append(directions);
  const rows=(second?secondRows:scientificRows).concat(numericRows).map((specs,i)=>{const row=document.createElement('div');row.className=`keypad-row ${i<3?'scientific-row':'numeric-row'}`;row.append(...specs.map(create));return row;});
  container.replaceChildren(top,...rows);container.dataset.page=second?'2':'1';
}
export function updateKeypadState(container,{second=false,shift=false,alpha=false,hyperbolic=false}){
  for(const button of container.querySelectorAll('.key')){const k=button.keySpec;if(!k)continue;
    const label=alpha&&k.alpha?k.alpha:shift&&k.alternate?k.alternate:k.title;button.setAttribute('aria-label',label);
    if(['SHIFT','ALPHA','SECOND','HYP'].includes(k.input)){const active=k.input==='SHIFT'?shift:k.input==='ALPHA'?alpha:k.input==='SECOND'?second:hyperbolic;button.classList.toggle('active',active);button.setAttribute('aria-pressed',String(active));}
  }
}
