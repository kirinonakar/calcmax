import {parse,latexInput} from './parser.js';
import {plotGraph as plot} from './graph-canvas.js';
import {mathDisplay} from './math-display.js';
import {renderFormulas} from './formula-preview.js';
import {displayNumber} from './display-format.js';
import {bindGraphGestures,transformBounds,nearestPoint,curvePointAtX} from './graph-view.js';
import {surfaceZRange,surfaceSampleCount} from './surface-geometry.js';
import {t,setText} from './i18n.js';

export function implicitFormula(source){
  try{const tree=parse(latexInput(source));return tree.kind==='relation'?source:`${source}=0`;}catch{return source;}
}
export function cartesianFormula(source,index=0){
  try{
    const tree=parse(latexInput(source)),hasY=node=>node.kind==='symbol'&&node.value==='y'||(node.args||[]).some(hasY);
    if(tree.kind==='relation')return source;
    if(hasY(tree))return `${source}=0`;
  }catch{}
  return `f${index+1}(x)=${source}`;
}
export function graphExpressionTarget(source){
  const normalized=latexInput(source),tree=parse(normalized);
  if(tree.kind==='relation'){
    if(!['=','=='].includes(tree.value))throw new Error('Graph an expression, y = f(x), or an equation F(x,y)=0');
    if(tree.args[0].kind==='symbol'&&tree.args[0].value==='z')return {kind:'surface',source:normalized.slice(tree.args[1].start,tree.args[1].end)};
  }
  return {kind:'cartesian',source:normalized};
}
export function splitTopLevel(source){
  const values=[];let depth=0,start=0;
  for(let i=0;i<source.length;i++){if('([{'.includes(source[i]))depth++;if(')]}'.includes(source[i]))depth--;if(source[i]===','&&!depth){values.push(source.slice(start,i).trim());start=i+1;}}
  values.push(source.slice(start).trim());return values.filter(Boolean);
}
export function graphSources(source,kind){
  return source.split(/\r?\n/).map(s=>s.trim()).filter(Boolean).slice(0,['surface','differential'].includes(kind)?1:8);
}
export function graphExpressions(source,kind){
  return graphSources(source,kind).filter(s=>!s.startsWith('[shade]')).slice(0,['surface','differential'].includes(kind)?1:6).map(s=>s.replace(kind==='surface'?/^z\s*=\s*/:kind==='differential'?/^dy\/dt\s*=\s*/:kind==='sequence'?/^u\(n\)\s*=\s*/:kind==='polar'?/^r\s*=\s*/:/^\s*=/,''));
}
export function appendGraphSource(existing,source,kind='cartesian'){
  const lines=existing.split(/\r?\n/).filter(line=>line.trim());
  const limit=['surface','differential'].includes(kind)?1:6;
  if(lines.some(line=>line.trim()===source.trim()))return existing;
  if(lines.length>=8||lines.filter(line=>!line.trim().startsWith('[shade]')).length>=limit)throw new Error('Graph limit reached. Remove a function before adding another.');
  return existing.trimEnd()+(lines.length?'\n':'')+source;
}
export function graphShadings(source,kind){
  return graphSources(source,kind).filter(s=>s.startsWith('[shade]')).slice(0,4).map(line=>{
    if(kind!=='cartesian')throw new Error('Shading requires a Cartesian graph');
    const [body,legacyRange]=line.slice(7).trim().split(';'),parts=splitTopLevel(body),expressions=[],intervals=[];
    for(const part of parts){if(part.includes('..'))intervals.push(part);else expressions.push(part);}
    if(legacyRange)intervals.push(legacyRange.trim());
    if(!expressions.length||expressions.length>2||intervals.length>1)throw new Error('Enter one or two shading functions');
    const trees=expressions.map(s=>parse(latexInput(s))),relation=trees[0];
    let item={mode:'band',trees};
    if(trees.length===1&&relation.kind==='relation'){
      const [left,right]=relation.args,isLeft=left.kind==='symbol'&&left.value==='y',isRight=right.kind==='symbol'&&right.value==='y';
      if(!['<','<=','>','>='].includes(relation.value)||!isLeft&&!isRight)throw new Error('Enter y < f(x) or y > f(x)');
      item={mode:'halfplane',side:(isLeft?relation.value.startsWith('<'):relation.value.startsWith('>'))?'below':'above',trees:[isLeft?right:left]};
    }
    if(intervals.length){const [a,b]=intervals[0].split('..');if(!a||!b)throw new Error('Enter a..b for the shading interval');item.a=parse(a);item.b=parse(b);}
    return item;
  });
}
export function createGraphWorkspace({execute,options,onError:reportError,persist,isBusy,isReady=()=>true,saved={}}){
  const $=id=>document.getElementById(id),value=id=>$(id).value,kind=()=>value('graph-kind');
  const onError=message=>{setText($('graph-status'),message);$('graph-status').classList.add('error');reportError(message);};
  let result=null,bounds=null,analysis=null,trace=null,integral=null,parameters={...(saved.parameters||{})},parameterRanges={...(saved.parameterRanges||{})},derivative=null,radianAxis=!!saved.radianAxis,active=false,pending=false,timer=null,animation=null,revision=0,analysisRevision=0,signature='';
  let animationEnabled={...(saved.animationEnabled||{})},heightScale=[1,.5,2].includes(saved.heightScale)?saved.heightScale:saved.halfHeight?.5:1,running=false,frame=null,formulaSignature='',tableResult=null,tableDigits=null,tableLanguage=null;
  let parametersOpen=saved.parametersOpen!==false,pendingAnalysis=null;
  const window=$('graph-plot').ownerDocument.defaultView;
  const requestFrame=callback=>window.requestAnimationFrame?window.requestAnimationFrame(callback):window.setTimeout(callback,16);
  const cancelFrame=id=>window.cancelAnimationFrame?window.cancelAnimationFrame(id):window.clearTimeout(id);
  function draw(){if(result&&bounds){let range;try{range=result.surface?zRange():null;}catch{return;}plot($('graph-plot'),range?{...result,zMin:range[0],zMax:range[1]}:result,bounds,{digits:options().displayDigits,dots:kind()==='sequence',analysis,trace,integral,selected:selected(),radianAxis,heightScale,surfaceView:surface});}}
  function queueDraw(){if(frame!==null)return;frame=requestFrame(()=>{frame=null;draw();});}
  const resizeObserver=window.ResizeObserver?new window.ResizeObserver(queueDraw):null;
  resizeObserver?.observe($('graph-plot'));
  const surface={rotation:35,elevation:32,zoom:1,renderMode:'wireframe',color:'#007b68',samples:26,autoDensity:true,autoZ:!['graph-zmin','graph-zmax'].every(id=>Number.isFinite(saved.ranges?.[id])),...saved.surface};
  surface.elevation=Number.isFinite(surface.elevation)?Math.max(-90,Math.min(90,surface.elevation)):32;
  if(!['wireframe','surface','surface-wireframe'].includes(surface.renderMode))surface.renderMode='wireframe';
  if(!/^#[0-9a-f]{6}$/i.test(surface.color))surface.color='#007b68';
  surface.samples=Number.isFinite(surface.samples)?Math.max(12,Math.min(96,Math.round(surface.samples))):26;
  const sourceDrafts={implicit:'x^2+y^2=1',cartesian:'sin(x)\ncos(x)',parametric:'[cos(t),sin(t)]',polar:'2*cos(3*t)',sequence:'n\nu(n-1)+u(n-2)',surface:'sin(sqrt(x^2+y^2))',differential:'y-t',...saved.sources};
  let sourceKind=kind();sourceDrafts[sourceKind]=value('graph-source');
  const rangeIds=['graph-min','graph-max','graph-ymin','graph-ymax','graph-xmin','graph-xmax','graph-analysis-a','graph-analysis-b','graph-t0','graph-zmin','graph-zmax'];
  const sliderIds=rangeIds.filter(id=>id!=='graph-t0');
  const rangePairs=[['graph-min','graph-max'],['graph-ymin','graph-ymax'],['graph-xmin','graph-xmax'],['graph-zmin','graph-zmax']];
  const pairedSliders=new Map();
  function fieldNumber(input){return input.value.trim()===''?NaN:Number(input.dataset.displayValue===input.value?input.dataset.fullValue:input.value);}
  function numeric(id){return fieldNumber($(id));}
  function displayField(input,number){input.dataset.fullValue=String(number);input.value=displayNumber(number,options().displayDigits);input.dataset.displayValue=input.value;}
  function editField(input){input.onfocus=()=>{if(input.dataset.displayValue===input.value){input.value=input.dataset.fullValue;input.dataset.displayValue=input.value;}};input.onblur=()=>{const number=fieldNumber(input);if(Number.isFinite(number)&&!input.hasAttribute('aria-invalid'))displayField(input,number);};}
  function syncRangePair(pair){
    const numbers=pair.ids.map(numeric);if(!numbers.every(Number.isFinite))return;
    const sliders=pair.ids.map(id=>$(id+'-slider')),low=Math.min(...sliders.map(slider=>Number(slider.min)),...numbers),high=Math.max(...sliders.map(slider=>Number(slider.max)),...numbers);
    sliders.forEach((slider,index)=>{slider.min=String(low);slider.max=String(high);slider.value=String(numbers[index]);slider.setAttribute('aria-valuetext',displayNumber(numbers[index],options().displayDigits));});
    pair.track.style.setProperty('--range-start',`${100*(numbers[0]-low)/(high-low)}%`);pair.track.style.setProperty('--range-end',`${100*(numbers[1]-low)/(high-low)}%`);
  }
  function resetRangeDomain(pair){
    const [min,max]=pair.ids.map(numeric),span=max-min;if(!Number.isFinite(span)||span<=0)return;
    const low=min-2*span,high=max+2*span;if(!Number.isFinite(high-low))return;
    for(const id of pair.ids){$(id+'-slider').min=String(low);$(id+'-slider').max=String(high);}
    syncRangePair(pair);
  }
  function showNumber(id,number){const input=$(id);if(!Number.isFinite(number))return;displayField(input,number);const output=$(id+'-value');if(output)output.textContent=displayNumber(number,options().displayDigits);const slider=$(id+'-slider');if(slider){if(number<Number(slider.min))slider.min=String(number);if(number>Number(slider.max))slider.max=String(number);slider.value=String(number);slider.setAttribute('aria-valuetext',displayNumber(number,options().displayDigits));}const pair=pairedSliders.get(id);if(pair)syncRangePair(pair);}
  function renderRangeNumbers(){for(const id of rangeIds){if(value(id)!==''&&document.activeElement!==$(id))showNumber(id,numeric(id));}}
  const displayOptions=()=>({digits:options().displayDigits,notation:'off'});
  const selected=()=>Number(value('graph-selected'))||0;
  const expressions=()=>graphExpressions(value('graph-source'),kind());
  function currentBounds(){return {xmin:numeric(['cartesian','implicit','surface'].includes(kind())?'graph-min':'graph-xmin'),xmax:numeric(['cartesian','implicit','surface'].includes(kind())?'graph-max':'graph-xmax'),ymin:numeric('graph-ymin'),ymax:numeric('graph-ymax')};}
  function writeBounds(next){showNumber(['cartesian','implicit','surface'].includes(kind())?'graph-min':'graph-xmin',next.xmin);showNumber(['cartesian','implicit','surface'].includes(kind())?'graph-max':'graph-xmax',next.xmax);showNumber('graph-ymin',next.ymin);showNumber('graph-ymax',next.ymax);for(const pair of new Set(pairedSliders.values()))resetRangeDomain(pair);}
  function zControls(){for(const id of ['graph-zmin','graph-zmax']){$(id).disabled=surface.autoZ;const slider=$(id+'-slider');if(slider)slider.disabled=surface.autoZ;}}
  function zRange(){
    if(surface.autoZ)return surfaceZRange(result?.zMin??-1,result?.zMax??1);
    const min=numeric('graph-zmin'),max=numeric('graph-zmax');
    if(!Number.isFinite(max-min)||max<=min)throw new Error('Enter finite values with minimum < maximum');
    return [min,max];
  }
  function makeRequest(){
    const trees=expressions().map(s=>parse(latexInput(s))),shadings=graphShadings(value('graph-source'),kind()),min=numeric('graph-min'),max=numeric('graph-max'),view=currentBounds();
    if(!trees.length&&!shadings.length)throw new Error('Enter a function to graph');
    if(![min,max,...Object.values(view)].every(Number.isFinite)||max<=min||view.xmax<=view.xmin||view.ymax<=view.ymin)throw new Error('Enter finite values with minimum < maximum');
    if(kind()==='surface')zRange();
    const request={...options(),action:'graph',angle:'RAD',graphKind:kind(),trees,shadings,min,max,xMin:view.xmin,xMax:view.xmax,yMin:view.ymin,yMax:view.ymax,surfaceYMin:view.ymin,surfaceYMax:view.ymax,parameters:{...parameters},variable:['parametric','polar','differential'].includes(kind())?'t':kind()==='sequence'?'n':'x',samples:animation?200:500};
    if(kind()==='surface'){const density=surfaceSampleCount(view,surface.samples,surface.autoDensity,surface.zoom);request.surfaceSamples=animation?Math.min(density,32):density;}
    if(kind()==='sequence')request.initialTrees=splitTopLevel(value('graph-initial')).map(s=>parse(s));
    if(kind()==='differential'){request.initialValues=splitTopLevel(value('graph-initial')).map(Number);request.t0=numeric('graph-t0');}
    if(derivative!==null&&kind()==='cartesian'&&trees[derivative])request.derivativeSelected=derivative;
    return request;
  }
  function densityControls(){const count=surfaceSampleCount(currentBounds(),surface.samples,surface.autoDensity,surface.zoom);$('graph-auto-density').checked=surface.autoDensity;$('graph-surface-samples').disabled=surface.autoDensity;$('graph-surface-samples').value=String(count);$('graph-surface-samples-value').textContent=`${count} × ${count}`;}
  function queue(){pending=true;densityControls();clearTimeout(timer);timer=null;if(animation||!active)return;timer=setTimeout(()=>{timer=null;if(active&&!running&&!isBusy()&&isReady())run();},180);}
  function flush(){
    if(pendingAnalysis&&!running&&!isBusy()&&isReady()){const next=pendingAnalysis;pendingAnalysis=null;analyze(next.action,next.point);return;}
    if(!animation&&pending&&active&&!running&&!isBusy()&&isReady()&&timer===null)queue();
  }
  function selections(){
    const count=expressions().length;
    for(const id of ['graph-selected','graph-other']){const before=value(id)===''?(id==='graph-other'?1:0):Number(value(id));$(id).replaceChildren(...Array.from({length:count},(_,i)=>{const option=document.createElement('option');option.value=String(i);option.textContent=`f${i+1}`;return option;}));$(id).value=String(Math.min(before,Math.max(0,count-1)));}
  }
  function formulas(){
    const next=JSON.stringify([value('graph-source'),kind(),derivative,result?.derivativeExpression,selected(),options().displayDigits,document.documentElement.lang]);if(next===formulaSignature)return;formulaSignature=next;
    const variable=kind()==='sequence'?'n':['parametric','polar','differential'].includes(kind())?'t':'x',labels=expressions().map((s,i)=>kind()==='cartesian'?cartesianFormula(s,i):kind()==='parametric'?`f${i+1}(t)=${s}`:kind()==='surface'?`z=${s}`:kind()==='differential'?`diff(y,t)=${s}`:kind()==='polar'?`r${i+1}(t)=${s}`:`f${i+1}(${variable})=${s}`);
    if(derivative!==null&&kind()==='cartesian'&&expressions()[derivative])labels.push(`diff(f${derivative+1}(x),x)`+(result?.derivativeSelected===derivative&&result?.derivativeExpression?`=${result.derivativeExpression}`:''));
    for(const line of graphSources(value('graph-source'),kind()).filter(s=>s.startsWith('[shade]')))labels.push(line.slice(7).split(';')[0].trim());
    renderFormulas($('graph-formulas'),labels,{digits:options().displayDigits});
    [...$('graph-formulas').children].slice(0,expressions().length).forEach((line,i)=>{
      line.classList.toggle('selected-curve',i===selected());line.tabIndex=0;line.setAttribute('role','button');line.setAttribute('aria-pressed',String(i===selected()));
      const pick=()=>{$('graph-selected').value=String(i);$('graph-selected').onchange();};
      line.onclick=pick;line.onkeydown=event=>{if(['Enter',' '].includes(event.key)){event.preventDefault();pick();}};
    });
  }
  function render(){
    heightControls();densityControls();parameterVisibility();formulas();renderRangeNumbers();syncParameterControls();if(!result||!bounds)return;
    for(const [id,number,suffix] of [['graph-rotation',surface.rotation,'°'],['graph-elevation',surface.elevation,'°'],['graph-surface-zoom',surface.zoom*100,'%']]){let output=$(id+'-value');if(!output){output=document.createElement('output');output.id=id+'-value';$(id).insertAdjacentElement('afterend',output);}output.textContent=displayNumber(number,options().displayDigits)+suffix;}
    let shown=result;
    if(result.surface){
      let range;try{range=zRange();}catch{return;}
      const [zMin,zMax]=range;shown={...result,zMin,zMax};
      if(surface.autoZ){showNumber('graph-zmin',zMin);showNumber('graph-zmax',zMax);resetRangeDomain(pairedSliders.get('graph-zmin'));}
    }
    plot($('graph-plot'),shown,bounds,{digits:options().displayDigits,dots:kind()==='sequence',analysis,trace,integral,selected:selected(),radianAxis,heightScale,surfaceView:surface});
    table();renderAnalysis();
    $('graph-trace').replaceChildren();if(trace)$('graph-trace').append(mathDisplay({kind:'relation',value:'≈',args:[{kind:'symbol',value:'Trace'},{kind:'tuple',args:trace.map(n=>({kind:'number',value:String(n)}))}]},options().displayDigits,true));
  }
  function table(){
    if(tableResult===result&&tableDigits===options().displayDigits&&tableLanguage===document.documentElement.lang)return;tableResult=result;tableDigits=options().displayDigits;tableLanguage=document.documentElement.lang;
    const table=document.createElement('table'),head=document.createElement('thead'),header=document.createElement('tr');
    for(const name of ['Curve','x / n','y',...(result.curveParameters?['t']:result.surface?['z']:[])]){const th=document.createElement('th');th.textContent=t(name);header.append(th);}head.append(header);table.append(head);const body=document.createElement('tbody');
    (result.curves||result.surface||[]).forEach((curve,i)=>curve.forEach((point,index)=>{if(!point||index%Math.max(1,Math.floor(curve.length/60))!==0)return;const row=document.createElement('tr');for(const number of [i+1,...point,...(result.curveParameters?[result.curveParameters[i]?.[index]]:[])]){const td=document.createElement('td');td.textContent=displayNumber(number,options().displayDigits);row.append(td);}if(!result.surface){row.tabIndex=0;const pick=()=>{if(i<expressions().length&&i!==selected()){$('graph-selected').value=String(i);$('graph-selected').onchange();}selectTrace(point,result.curveParameters?.[i]?.[index]??point[0]);};row.onclick=pick;row.onkeydown=e=>{if(['Enter',' '].includes(e.key)){e.preventDefault();pick();}};}body.append(row);}));table.append(body);$('graph-table').replaceChildren(table);
  }
  function parameterVisibility(){
    const hasParameters=$('graph-parameters').children.length>0;
    $('graph-parameter-actions').hidden=!hasParameters;
    $('graph-parameters').hidden=!hasParameters||!parametersOpen;
    $('graph-parameters-toggle').setAttribute('aria-expanded',String(parametersOpen));
  }
  $('graph-parameters-toggle').onclick=()=>{parametersOpen=!parametersOpen;parameterVisibility();persist();};
  function parameterChanged(){analysisRevision++;analysis=null;trace=null;integral=null;queueDraw();queue();}
  function syncParameterControls(){
    for(const caption of $('graph-parameters').querySelectorAll('[data-parameter]')){
      const name=caption.dataset.parameter,group=caption.closest('.graph-parameter'),number=parameters[name],limits=parameterRanges[name]||[-5,5];
      caption.textContent=`${name} = ${displayNumber(number,options().displayDigits)}`;
      const slider=group.querySelector('input[type="range"]'),field=group.querySelector('[data-parameter-value]');
      slider.min=String(limits[0]);slider.max=String(limits[1]);slider.value=String(number);slider.setAttribute('aria-valuetext',displayNumber(number,options().displayDigits));
      if(document.activeElement!==field){displayField(field,number);field.removeAttribute('aria-invalid');}
      for(const bound of group.querySelectorAll('[data-parameter-bound]'))if(document.activeElement!==bound)displayField(bound,limits[Number(bound.dataset.parameterBound)]);
    }
  }
  function parameterControls(names=[],force=false){
    if(!force&&$('graph-parameters').dataset.names===JSON.stringify(names)){syncParameterControls();return;}
    $('graph-parameters').dataset.names=JSON.stringify(names);
    $('graph-parameters').replaceChildren();
    for(const name of names){if(!(name in parameters))parameters[name]=1;const limits=parameterRanges[name]||[-5,5],label=document.createElement('label'),caption=document.createElement('span'),input=document.createElement('input'),valueInput=document.createElement('input'),ranges=document.createElement('div'),low=document.createElement('input'),high=document.createElement('input');
      const toggleLabel=document.createElement('label'),toggle=document.createElement('input');toggleLabel.className='check';toggle.type='checkbox';toggle.checked=animationEnabled[name]!==false;toggle.dataset.animateParameter=name;toggle.setAttribute('aria-label',`${t('Animate')}: ${name}`);toggle.onchange=()=>{animationEnabled[name]=toggle.checked;if(animation&&toggle.checked)animation.phases[name]=parameterPhase(name)-animation.phase;persist();};toggleLabel.append(toggle,document.createTextNode(`${t('Animate')} ${name}`));
      input.type='range';input.min=String(limits[0]);input.max=String(limits[1]);input.step='any';input.value=String(parameters[name]);caption.dataset.parameter=name;caption.textContent=`${name} = ${displayNumber(parameters[name],options().displayDigits)}`;
      input.setAttribute('aria-label',`${t('Parameter value')}: ${name}`);
      input.oninput=()=>{parameters[name]=Number(input.value);if(animation)animation.phases[name]=parameterPhase(name)-animation.phase;syncParameterControls();parameterChanged();};
      valueInput.type='number';valueInput.step='any';displayField(valueInput,parameters[name]);editField(valueInput);valueInput.dataset.parameterValue=name;valueInput.setAttribute('aria-label',`${t('Parameter value')}: ${name}`);
      const applyValue=()=>{
        const number=fieldNumber(valueInput);
        if(!Number.isFinite(number)||Math.abs(number)>1e9){valueInput.setAttribute('aria-invalid','true');onError(t('Enter a finite value between -1e9 and 1e9'));return;}
        const [a,b]=parameterRanges[name]||[-5,5];
        parameterRanges[name]=[Math.min(a,number),Math.max(b,number)];parameters[name]=number;
        if(animation)animation.phases[name]=parameterPhase(name)-animation.phase;
        valueInput.removeAttribute('aria-invalid');$('graph-status').textContent='';syncParameterControls();persist();parameterChanged();
      };
      valueInput.oninput=()=>valueInput.removeAttribute('aria-invalid');valueInput.onchange=applyValue;
      valueInput.onkeydown=event=>{if(event.key==='Enter'){event.preventDefault();applyValue();}else if(event.key==='Escape'){event.preventDefault();displayField(valueInput,parameters[name]);if(document.activeElement===valueInput){valueInput.value=valueInput.dataset.fullValue;valueInput.dataset.displayValue=valueInput.value;}valueInput.removeAttribute('aria-invalid');}};
      for(const [index,[field,number,labelText]] of [[low,limits[0],'Slider minimum'],[high,limits[1],'Slider maximum']].entries()){field.type='number';field.step='any';displayField(field,number);editField(field);field.dataset.parameterBound=String(index);field.setAttribute('aria-label',`${t(labelText)}: ${name}`);field.onchange=()=>{const a=fieldNumber(low),b=fieldNumber(high);if(!Number.isFinite(a)||!Number.isFinite(b)||a>=b){onError(t('Enter finite values with minimum < maximum'));return;}parameterRanges[name]=[a,b];parameters[name]=Math.max(a,Math.min(b,parameters[name]));if(animation)animation.phases[name]=parameterPhase(name)-animation.phase;parameterControls(names,true);parameterChanged();};}
      label.className='graph-parameter-value';label.append(caption,valueInput);ranges.className='form-row';ranges.append(low,high);const group=document.createElement('div');group.className='graph-parameter';group.append(label,input,ranges,toggleLabel);$('graph-parameters').append(group);
    }
    syncParameterControls();parameterVisibility();
  }
  async function run(){
    clearTimeout(timer);timer=null;if(running||isBusy()||!isReady()){pending=true;return;}pending=false;running=true;
    try{
      const request=makeRequest(),token=++revision,source=value('graph-source'),graphKind=kind(),view=currentBounds();
      const nextSignature=JSON.stringify([source,graphKind,parameters,derivative]);if(nextSignature!==signature){analysis=null;trace=null;integral=null;analysisRevision++;signature=nextSignature;}
      const response=await execute(request);if(token!==revision||source!==value('graph-source')||graphKind!==kind())return;
      if(!response.ok){onError(response.error);return;}
      result=response;bounds=currentBounds();$('graph-status').textContent='';parameterControls(response.parameters);
      if(animation)queueDraw();else{selections();render();persist();}
    }catch(error){onError(error.message);}finally{running=false;flush();}
  }
  function renderAnalysis(){
    const container=$('graph-analysis-result');container.replaceChildren();if(!analysis)return;
    const title=document.createElement('div');title.textContent=t($('graph-analysis-action').selectedOptions[0]?.textContent||'Analyze');container.append(title);
    if(analysis.value!==undefined)container.append(mathDisplay({kind:'number',value:String(analysis.value)},options().displayDigits,true));
    if(analysis.vertical){const note=document.createElement('div');note.textContent=t('Vertical tangent');container.append(note);}
    for(const point of analysis.points||[]){const button=document.createElement('button');button.className='analysis-point';button.append(mathDisplay({kind:'tuple',args:point.map(n=>({kind:'number',value:String(n)}))},options().displayDigits,true));button.onclick=()=>{if(kind()==='cartesian')selectTrace(point);else{trace=point;render();}};container.append(button);}
    if(!analysis.points?.length&&analysis.value===undefined&&!analysis.vertical){const note=document.createElement('div');note.textContent=t('No points found in this interval');container.append(note);}
  }
  async function analyze(action=value('graph-analysis-action'),point=null){
    if(running||isBusy()||!isReady()){pendingAnalysis={action,point};return;}
    if(pending&&kind()==='cartesian'&&['derivative','tangent'].includes(action)){pendingAnalysis={action,point};return run();}
    pendingAnalysis=null;clearTimeout(timer);timer=null;
    try{
      const trees=expressions().map(s=>parse(latexInput(s))),a=point??numeric('graph-analysis-a'),b=['derivative','tangent'].includes(action)?a:numeric('graph-analysis-b'),view=bounds||currentBounds(),token=++analysisRevision,source=value('graph-source'),graphKind=kind();
      if(!Number.isFinite(a)||!Number.isFinite(b)||!['derivative','tangent'].includes(action)&&a>=b)throw new Error('Enter finite values with a < b');
      const currentParameters={...parameters};
      const tracePoint=trace||(graphKind==='cartesian'&&['derivative','tangent'].includes(action)&&result?.implicitCurves?.[selected()]?curvePointAtX(result.curves[selected()],a):null);
      const response=await execute({...options(),angle:'RAD',action:'graphAnalysis',graphKind,trees,analysis:action,a,b,selected:selected(),other:Number(value('graph-other')),variable:graphKind==='cartesian'?'x':'t',parameters:currentParameters,tracePoint,xMin:view.xmin,xMax:view.xmax,yMin:view.ymin,yMax:view.ymax});
      if(token!==analysisRevision||source!==value('graph-source')||graphKind!==kind()||JSON.stringify(currentParameters)!==JSON.stringify(parameters))return;if(!response.ok){onError(response.error);return;}
      analysis=response;$('graph-status').textContent='';integral=action==='integral'&&graphKind==='cartesian'?[a,b]:null;trace=response.points?.[0]||trace;$('graph-analysis-action').value=action;analysisControls();render();
    }catch(error){onError(error.message);}finally{flush();}
  }
  function changeView(next,commit=true){if(!Object.values(next).every(Number.isFinite)||next.xmin>=next.xmax||next.ymin>=next.ymax){onError('Enter finite values with minimum < maximum');return;}bounds=next;writeBounds(next);queueDraw();if(commit){render();persist();if(['cartesian','implicit','surface','differential'].includes(kind()))queue();}}
  function surfaceChange(dx,dy,zoom){const previousCount=surfaceSampleCount(currentBounds(),surface.samples,surface.autoDensity,surface.zoom);surface.rotation=((surface.rotation+dx*.7)%360+360)%360;surface.elevation=Math.max(-90,Math.min(90,surface.elevation+dy*.5));surface.zoom=Math.max(.4,Math.min(3,surface.zoom*zoom));$('graph-rotation').value=String(surface.rotation);$('graph-elevation').value=String(surface.elevation);$('graph-surface-zoom').value=String(surface.zoom);queueDraw();persist();if(surface.autoDensity&&previousCount!==surfaceSampleCount(currentBounds(),surface.samples,true,surface.zoom))queue();}
  function selectTrace(point,parameter=point[0]){
    trace=point;
    const action=value('graph-analysis-action');
    if(['derivative','tangent'].includes(action)){showNumber('graph-analysis-a',parameter);$('graph-tangent-slider').value=String(parameter);}
    render();
    if(action==='tangent')analyze('tangent',parameter);
  }
  const disposeGestures=bindGraphGestures($('graph-plot'),{getBounds:()=>bounds,onView:changeView,onTrace:position=>{const closest=nearestPoint(result,bounds,position,selected());if(closest)selectTrace(closest.point,closest.parameter);},isSurface:()=>kind()==='surface',onSurface:surfaceChange});
  const zoom=z=>{if(kind()==='surface'){surfaceChange(0,0,z);return;}const at={x:.5,y:.5};changeView(transformBounds(bounds||currentBounds(),at,at,z));};
  $('graph-zoom-in').onclick=()=>zoom(2);$('graph-zoom-out').onclick=()=>zoom(.5);
  function heightControls(){const button=$('graph-height-toggle'),label=heightScale===1?'Half height':heightScale===.5?'Double height':'Full height';button.dataset.heightScale=String(heightScale);button.setAttribute('aria-pressed',String(heightScale!==1));button.setAttribute('aria-label',t(label));button.title=t(label);button.textContent=heightScale===1?'½':heightScale===.5?'2×':'1×';}
  $('graph-height-toggle').onclick=()=>{heightScale=heightScale===1?.5:heightScale===.5?2:1;heightControls();render();persist();};heightControls();
  $('graph-reset-ranges').onclick=()=>{surface.autoZ=true;$('graph-auto-z').checked=true;zControls();changeView({xmin:-3,xmax:3,ymin:-3,ymax:3});for(const pair of new Set(pairedSliders.values()))resetRangeDomain(pair);};
  for(const [id,dx,dy] of [['left',.15,0],['right',-.15,0],['up',0,.15],['down',0,-.15]])$('graph-'+id).onclick=()=>changeView(transformBounds(bounds||currentBounds(),{x:.5,y:.5},{x:.5+dx,y:.5+dy},1));
  $('graph-fit').onclick=()=>{if(kind()==='surface'){surface.autoZ=true;$('graph-auto-z').checked=true;zControls();render();persist();return;}const view=bounds||currentBounds(),curves=derivative!==null?(result?.curves||[]).slice(0,expressions().length):result?.curves||[],ys=curves.flat().filter(p=>p&&p.every(Number.isFinite)&&p[0]>=view.xmin&&p[0]<=view.xmax).map(p=>p[1]);if(ys.length){const low=Math.min(...ys),high=Math.max(...ys),padding=Math.max((high-low)*.12,high===low?1:1e-6);changeView({...view,ymin:low-padding,ymax:high+padding});}};
  $('graph-reset').onclick=()=>{analysis=null;trace=null;integral=null;surface.rotation=35;surface.elevation=32;surface.zoom=1;$('graph-rotation').value='35';$('graph-elevation').value='32';$('graph-surface-zoom').value='1';if(kind()==='sequence'){showNumber('graph-min',0);showNumber('graph-max',20);changeView({xmin:0,xmax:20,ymin:-2,ymax:20});queue();}else if(kind()==='differential'){showNumber('graph-min',-5);showNumber('graph-max',5);changeView({xmin:-5,xmax:5,ymin:-3,ymax:5});}else if(kind()==='implicit'){changeView({xmin:-3,xmax:3,ymin:-3,ymax:3});}else if(kind()==='surface'){surface.autoZ=true;$('graph-auto-z').checked=true;zControls();changeView({xmin:-3,xmax:3,ymin:-3,ymax:3});}else changeView({xmin:-10,xmax:10,ymin:-5,ymax:5});};
  $('graph-axis').onclick=()=>{radianAxis=!radianAxis;setText($('graph-axis'),radianAxis?'x: π rad':'x: decimal');render();persist();};
  $('graph-selected').onchange=()=>{analysisRevision++;analysis=null;trace=null;integral=null;if($('graph-derivative').checked){derivative=selected();queue();}render();persist();};
  $('graph-derivative').onchange=()=>{derivative=$('graph-derivative').checked?selected():null;queue();formulas();};
  $('graph-analysis-run').onclick=()=>analyze();$('graph-analysis-clear').onclick=()=>{pendingAnalysis=null;analysisRevision++;analysis=null;trace=null;integral=null;render();};
  function analysisControls(){const action=value('graph-analysis-action');$('graph-other').closest('label').hidden=action!=='intersection';$('graph-analysis-b').closest('label').hidden=['derivative','tangent'].includes(action);$('graph-tangent-position').hidden=action!=='tangent';$('graph-analysis-a-slider').hidden=action==='tangent';$('graph-analysis-a-value').hidden=action==='tangent';$('graph-tangent-slider').min=String(numeric('graph-min'));$('graph-tangent-slider').max=String(numeric('graph-max'));}
  $('graph-analysis-action').onchange=analysisControls;$('graph-tangent-slider').oninput=()=>{showNumber('graph-analysis-a',Number(value('graph-tangent-slider')));};$('graph-tangent-slider').onchange=()=>analyze('tangent');
  for(const id of ['graph-rotation','graph-elevation','graph-surface-zoom'])$(id).oninput=()=>{const previousCount=surfaceSampleCount(currentBounds(),surface.samples,surface.autoDensity,surface.zoom);surface.rotation=Number(value('graph-rotation'));surface.elevation=Number(value('graph-elevation'));surface.zoom=Number(value('graph-surface-zoom'));render();persist();if(surface.autoDensity&&previousCount!==surfaceSampleCount(currentBounds(),surface.samples,true,surface.zoom))queue();};
  $('graph-surface-render').onchange=()=>{surface.renderMode=value('graph-surface-render');render();persist();};
  $('graph-surface-color').oninput=()=>{surface.color=value('graph-surface-color');render();persist();};
  $('graph-surface-samples').oninput=()=>{surface.samples=Number(value('graph-surface-samples'));$('graph-surface-samples-value').textContent=`${surface.samples} × ${surface.samples}`;queue();};
  $('graph-surface-samples').onchange=()=>{persist();queue();};
  $('graph-auto-density').onchange=()=>{surface.autoDensity=$('graph-auto-density').checked;densityControls();persist();queue();};
  $('graph-auto-z').onchange=()=>{surface.autoZ=$('graph-auto-z').checked;zControls();if(!surface.autoZ&&(!Number.isFinite(numeric('graph-zmax')-numeric('graph-zmin'))||numeric('graph-zmax')<=numeric('graph-zmin'))){const [min,max]=surfaceZRange(result?.zMin??-1,result?.zMax??1);showNumber('graph-zmin',min);showNumber('graph-zmax',max);}render();persist();};
  for(const id of ['graph-zmin','graph-zmax'])$(id).onchange=()=>{try{zRange();render();$('graph-status').textContent='';persist();}catch(error){onError(error.message);}};
  $('graph-reset-parameters').onclick=()=>{for(const name of Object.keys(parameters)){const [a,b]=parameterRanges[name]||[-5,5];parameters[name]=Math.max(a,Math.min(b,1));}parameterControls(Object.keys(parameters),true);parameterChanged();};
  function parameterPhase(name){const [a,b]=parameterRanges[name]||[-5,5];return Math.asin(Math.max(-1,Math.min(1,2*(parameters[name]-a)/(b-a)-1)));}
  function stopAnimation(refine=true){if(!animation)return;if(animation.frame!==null)cancelFrame(animation.frame);animation=null;setText($('graph-animate'),'Animate');if(refine){render();persist();run();}}
  function animateFrame(timestamp){
    const state=animation;if(!state)return;state.frame=null;
    const now=Number.isFinite(timestamp)?timestamp:window.performance.now();
    state.phase+=state.previous===null?0:Math.max(0,Math.min(.1,(now-state.previous)/1000));state.previous=now;
    if(now-state.updated>=1000/60-1e-6){
      state.updated=now;let changed=false;
      for(const name of Object.keys(parameters)){if(animationEnabled[name]===false)continue;const [a,b]=parameterRanges[name]||[-5,5];state.phases[name]??=parameterPhase(name)-state.phase;parameters[name]=a+(b-a)*(Math.sin(state.phase+state.phases[name])+1)/2;changed=true;}
      if(changed){pending=true;if(!running&&!isBusy()&&isReady())run();}
    }
    state.frame=requestFrame(animateFrame);
  }
  $('graph-animate').onclick=()=>{if(animation){stopAnimation();return;}clearTimeout(timer);timer=null;analysis=null;trace=null;integral=null;render();animation={frame:null,previous:null,updated:-Infinity,phase:0,phases:Object.fromEntries(Object.keys(parameters).map(name=>[name,parameterPhase(name)]))};setText($('graph-animate'),'Stop');animation.frame=requestFrame(animateFrame);};
  function typeControls(){$('graph-cartesian-help').hidden=kind()!=='cartesian';$('graph-reset-ranges').hidden=kind()!=='surface';$('graph-viewport-ranges').hidden=['cartesian','implicit','surface'].includes(kind());$('graph-surface-controls').hidden=kind()!=='surface';setText($('graph-fit'),kind()==='surface'?'Fit Z':'Fit Y');$('graph-analysis').hidden=!['cartesian','parametric','polar'].includes(kind());$('graph-derivative').closest('label').hidden=kind()!=='cartesian';$('graph-initial').closest('label').hidden=!['sequence','differential'].includes(kind());$('graph-t0').closest('label').hidden=kind()!=='differential';$('graph-analysis-action').querySelector('[value="intersection"]').disabled=kind()!=='cartesian';analysisControls();}
  $('graph-kind').onchange=()=>{
    pendingAnalysis=null;
    sourceDrafts[sourceKind]=value('graph-source');sourceKind=kind();$('graph-source').value=sourceDrafts[sourceKind];
    stopAnimation(false);
    const view=kind()==='sequence'?[0,20,-2,20]:kind()==='differential'?[-5,5,-3,5]:kind()==='cartesian'?[-10,10,-5,5]:[-3,3,-3,3];
    writeBounds({xmin:view[0],xmax:view[1],ymin:view[2],ymax:view[3]});
    if(['parametric','polar'].includes(kind())){showNumber('graph-min',0);showNumber('graph-max',2*Math.PI);}
    if(['sequence','differential'].includes(kind())){showNumber('graph-min',view[0]);showNumber('graph-max',view[1]);$('graph-initial').value=kind()==='sequence'?'0,1':'1';}
    parameters={};parameterRanges={};parameterControls([],true);derivative=null;$('graph-derivative').checked=false;result=null;bounds=null;analysis=null;trace=null;integral=null;revision++;analysisRevision++;$('graph-plot').replaceChildren();$('graph-table').replaceChildren();selections();formulas();typeControls();queue();persist();
  };
  $('graph-source').oninput=()=>{pendingAnalysis=null;derivative=null;$('graph-derivative').checked=false;analysis=null;trace=null;integral=null;revision++;analysisRevision++;selections();formulas();queue();};
  for(const id of ['graph-min','graph-max','graph-ymin','graph-ymax','graph-xmin','graph-xmax','graph-initial','graph-t0'])$(id).onchange=()=>{analysisControls();queue();};
  for(const id of rangeIds){const field=$(id);editField(field);for(const name of ['input','change'])field.addEventListener(name,()=>{if(field.dataset.displayValue!==field.value)delete field.dataset.displayValue;const pair=pairedSliders.get(id);if(pair)syncRangePair(pair);});}
  for(const id of sliderIds){const field=$(id),slider=document.createElement('input'),output=document.createElement('output'),current=numeric(id);slider.type='range';slider.id=id+'-slider';slider.min=String(Math.min(-30,current-20));slider.max=String(Math.max(30,current+20));slider.step='any';slider.value=String(current);slider.setAttribute('aria-label',field.closest('label').firstChild.textContent.trim());output.id=id+'-value';output.textContent=displayNumber(current,options().displayDigits);field.insertAdjacentElement('afterend',output);output.insertAdjacentElement('afterend',slider);
    slider.oninput=()=>{const number=Number(slider.value),partner=id.endsWith('min')?id.replace(/min$/,'max'):id.endsWith('max')?id.replace(/max$/,'min'):null;let bounded=number;if(partner){const limit=numeric(partner),gap=Math.max(2e-7,Math.abs(limit)*1e-10);bounded=id.endsWith('min')?Math.min(number,limit-gap):Math.max(number,limit+gap);}showNumber(id,bounded);if(!id.startsWith('graph-analysis')){bounds=currentBounds();render();}else renderRangeNumbers();};
    slider.onchange=()=>{persist();if(id.startsWith('graph-analysis'))analysisControls();else if(!id.startsWith('graph-z'))queue();};
  }
  for(const ids of rangePairs){
    const group=document.createElement('div'),fields=document.createElement('div'),track=document.createElement('div'),labels=ids.map(id=>$(id).closest('label'));
    group.className='graph-range-pair';fields.className='graph-range-fields';track.className='graph-range-slider';group.setAttribute('role','group');group.setAttribute('aria-labelledby',ids.map(id=>id+'-label').join(' '));
    group.append(fields,track);labels[0].before(group);
    labels.forEach((label,index)=>{label.id=ids[index]+'-label';fields.append(label);track.append($(ids[index]+'-slider'));});
    const pair={ids,track};for(const id of ids)pairedSliders.set(id,pair);resetRangeDomain(pair);
  }
  for(const [id,number] of Object.entries(saved.ranges||{}))if(rangeIds.includes(id))showNumber(id,number);
  for(const pair of new Set(pairedSliders.values()))resetRangeDomain(pair);
  selections();formulas();typeControls();setText($('graph-axis'),radianAxis?'x: π rad':'x: decimal');for(const [id,name] of [['graph-rotation','rotation'],['graph-elevation','elevation'],['graph-surface-zoom','zoom']])$(id).value=String(surface[name]);
  $('graph-surface-render').value=surface.renderMode;$('graph-auto-z').checked=surface.autoZ;zControls();
  $('graph-surface-color').value=surface.color;densityControls();
  renderRangeNumbers();parameterControls([],true);
  function addExpression(source,graphKind){
    const next=appendGraphSource(kind()===graphKind?value('graph-source'):sourceDrafts[graphKind]||'',source,graphKind);
    if(kind()!==graphKind){$('graph-kind').value=graphKind;$('graph-kind').onchange();}
    $('graph-source').value=next;$('graph-source').oninput();persist();
  }
  return {addExpression,run,render,flush,snapshot:()=>({sources:{...sourceDrafts,[kind()]:value('graph-source')},parameters:{...parameters},parameterRanges,parametersOpen,animationEnabled:{...animationEnabled},heightScale,halfHeight:heightScale===.5,radianAxis,surface:{...surface},ranges:Object.fromEntries(rangeIds.filter(id=>!(surface.autoZ&&id.startsWith('graph-z'))&&value(id)!==''&&Number.isFinite(numeric(id))).map(id=>[id,numeric(id)]))}),updateButtons(){ $('graph-analysis-run').disabled=isBusy()||!isReady();},activate(value){active=value;if(active&&(!result||pending))queue();if(!active){pendingAnalysis=null;clearTimeout(timer);timer=null;stopAnimation(false);}},dispose(){pendingAnalysis=null;active=false;disposeGestures();resizeObserver?.disconnect();if(frame!==null)cancelFrame(frame);clearTimeout(timer);stopAnimation(false);revision++;analysisRevision++;}};
}
