import {parse,latexInput} from './parser.js';
import {graphExpression} from './function-transfer.js';
import {plotGraph as plot,graphCurveColor} from './graph-canvas.js';
import {defaultGraphColors} from './graph-colors.js';
import {mathDisplay} from './math-display.js';
import {renderFormulas} from './formula-preview.js';
import {displayNumber} from './display-format.js';
import {bindGraphGestures,transformBounds,nearestPoint,curvePointAtX} from './graph-view.js';
import {surfaceZRange,surfaceSampleCount} from './surface-geometry.js';
import {graphSamplingInterval,graphPreviewRequest,graphSamplingIdentity} from './graph-sampling.js';
import {t,setText} from './i18n.js';
import {graphSvg,graphPng} from './graph-export.js';
import {downloadFile} from './storage.js';

export function graphInputTree(source,kind='cartesian'){
  const tree=parse(latexInput(source));
  return kind==='cartesian'?graphExpression(tree):tree;
}

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
  const hasVector=node=>['list','tuple'].includes(node.kind)&&node.args.length===3||(node.args||[]).some(hasVector);
  if(hasVector(tree))return {kind:'space',source:normalized};
  const hasZ=node=>node.kind==='symbol'&&node.value==='z'||(node.args||[]).some(hasZ);
  if(tree.kind==='relation'){
    if(!['=','=='].includes(tree.value))throw new Error('Graph an expression, y = f(x), or an equation F(x,y)=0');
    if(tree.args[0].kind==='symbol'&&tree.args[0].value==='z'&&!hasZ(tree.args[1]))return {kind:'surface',source:normalized.slice(tree.args[1].start,tree.args[1].end)};
  }
  return {kind:hasZ(tree)?'surface':'cartesian',source:normalized};
}
export function isImplicitSurface(source){
  try{
    const tree=parse(latexInput(source)),hasZ=node=>node.kind==='symbol'&&node.value==='z'||(node.args||[]).some(hasZ);
    return tree.kind==='relation'?!(tree.args[0].kind==='symbol'&&tree.args[0].value==='z'&&!hasZ(tree.args[1])):hasZ(tree);
  }catch{return false;}
}
export function surfaceFormula(source){
  try{if(parse(latexInput(source)).kind==='relation')return source;}catch{}
  return isImplicitSurface(source)?`${source}=0`:`z=${source}`;
}
function parametricFormula(source,index,space=false){
  try{if(parse(latexInput(source)).kind==='relation')return source;}catch{}
  return `${space?'C':'f'}${index+1}(t)=${source}`;
}
export function splitTopLevel(source){
  const values=[];let depth=0,start=0;
  for(let i=0;i<source.length;i++){if('([{'.includes(source[i]))depth++;if(')]}'.includes(source[i]))depth--;if(source[i]===','&&!depth){values.push(source.slice(start,i).trim());start=i+1;}}
  values.push(source.slice(start).trim());return values.filter(Boolean);
}
export function graphSources(source,kind){
  return source.split(/\r?\n/).map(s=>s.trim()).filter(Boolean).slice(0,['surface','differential'].includes(kind)?1:24);
}
export function isGraphShading(source){return /^\[(?:shade|s)\]/.test(source.trim());}
export function graphShadingBody(source){return source.trim().replace(/^\[(?:shade|s)\]/,'').trim();}
export function graphExpressions(source,kind){
  return graphSources(source,kind).filter(s=>!isGraphShading(s)).slice(0,['surface','differential'].includes(kind)?1:20).map(s=>s.replace(kind==='differential'?/^dy\/dt\s*=\s*/:kind==='sequence'?/^u\(n\)\s*=\s*/:kind==='polar'?/^r\s*=\s*/:/^\s*=/,''));
}
export function appendGraphSource(existing,source,kind='cartesian'){
  const lines=existing.split(/\r?\n/).filter(line=>line.trim());
  const limit=['surface','differential'].includes(kind)?1:20;
  if(lines.some(line=>line.trim()===source.trim()))return existing;
  if(lines.length>=24||lines.filter(line=>!isGraphShading(line)).length>=limit)throw new Error('Graph limit reached. Remove a function before adding another.');
  return existing.trimEnd()+(lines.length?'\n':'')+source;
}
export function removeGraphSource(existing,index,kind='cartesian',shading=false){
  const lines=existing.split(/\r?\n/),visible=lines.map((source,index)=>({source,index})).filter(line=>line.source.trim()).slice(0,['surface','differential'].includes(kind)?1:24);
  const target=visible.filter(line=>isGraphShading(line.source)===shading).slice(0,shading?4:['surface','differential'].includes(kind)?1:20)[index];
  return target?lines.filter((_,i)=>i!==target.index).join('\n'):existing;
}
export function removeGraphInputLine(source,index){
  const lines=source.split(/\r?\n/);
  return index>=0&&index<lines.length?lines.filter((_,i)=>i!==index).join('\n'):source;
}
export function createGraphInputHistory(){
  const histories=new Map();
  return {
    remember(kind,snapshot){
      const history=histories.get(kind)||[],last=history.at(-1);
      if(last?.source===snapshot.source&&last.derivative===snapshot.derivative&&last.secondDerivative===snapshot.secondDerivative)return;
      history.push(structuredClone(snapshot));if(history.length>100)history.shift();histories.set(kind,history);
    },
    undo:kind=>histories.get(kind)?.pop(),
    canUndo:kind=>!!histories.get(kind)?.length
  };
}
export function graphSelectedCurveIndex(sourceIndex,derivativeOrder=0,derivativeIndex=-1,secondDerivativeIndex=-1){
  return derivativeOrder===1?derivativeIndex:derivativeOrder===2?secondDerivativeIndex:sourceIndex;
}
export function graphAnalysisTarget(curve,derivative,secondDerivative){
  if(curve===-1)return derivative===null?null:{source:derivative,order:1};
  if(curve===-2)return secondDerivative===null?null:{source:secondDerivative,order:2};
  return curve>=0?{source:curve,order:0}:null;
}
export function graphAnalysisCurves(count,derivative,secondDerivative){
  const curves=Array.from({length:count},(_,i)=>({key:i,label:`f${i+1}`}));
  if(derivative!==null&&derivative>=0&&derivative<count)curves.push({key:-1,label:`f${derivative+1}′`});
  if(secondDerivative!==null&&secondDerivative>=0&&secondDerivative<count)curves.push({key:-2,label:`f${secondDerivative+1}″`});
  return curves;
}
export function graphShadings(source,kind){
  return graphSources(source,kind).filter(isGraphShading).slice(0,4).map(line=>{
    if(kind!=='cartesian')throw new Error('Shading requires a Cartesian graph');
    const [body,legacyRange]=graphShadingBody(line).split(';'),parts=splitTopLevel(body),expressions=[],intervals=[];
    for(const part of parts){if(part.includes('..'))intervals.push(part);else expressions.push(part);}
    if(legacyRange)intervals.push(legacyRange.trim());
    if(!expressions.length||intervals.length>1)throw new Error('Enter shading inequalities or one or two functions');
    const trees=expressions.map(s=>graphInputTree(s)),relation=trees[0];
    let item={mode:'band',trees};
    if(trees.length===1&&relation.kind==='relation'&&relation.args.every(node=>node.kind!=='relation')&&relation.args.some(node=>node.kind==='symbol'&&node.value==='y')){
      const [left,right]=relation.args,isLeft=left.kind==='symbol'&&left.value==='y',isRight=right.kind==='symbol'&&right.value==='y';
      if(!['<','<=','>','>='].includes(relation.value)||!isLeft&&!isRight)throw new Error('Enter y < f(x) or y > f(x)');
      const boundary=isLeft?right:left;
      if((function hasY(node){return node.kind==='symbol'&&node.value==='y'||(node.args||[]).some(hasY);})(boundary))throw new Error('Shading boundaries cannot depend on y');
      item={mode:'halfplane',side:(isLeft?relation.value.startsWith('<'):relation.value.startsWith('>'))?'below':'above',trees:[isLeft?right:left]};
    }else if(trees.some(tree=>tree.kind==='relation')){
      const boundaries=[],constraints=[];
      const has=(node,name)=>node.kind==='symbol'&&node.value===name||(node.args||[]).some(child=>has(child,name));
      function flatten(node){
        if(node.kind!=='relation')return [node];
        if(!['<','<=','>','>='].includes(node.value))throw new Error('Enter x or y inequalities for shading');
        const left=flatten(node.args[0]),right=flatten(node.args[1]),a=left.at(-1),b=right[0];
        const axis=node=>node.kind==='symbol'&&['x','y'].includes(node.value)?node.value:null;
        const onLeft=!!axis(a),name=axis(a)||axis(b),boundary=onLeft?b:a;
        if(!name||has(boundary,'y')||name==='x'&&has(boundary,'x'))throw new Error('Enter x bounds or y < f(x) inequalities');
        constraints.push({axis:name,side:(onLeft?node.value.startsWith('<'):node.value.startsWith('>'))?'upper':'lower'});
        boundaries.push(boundary);
        return [...left,...right];
      }
      for(const tree of trees.filter(tree=>tree.kind==='relation'))flatten(tree);
      const functions=trees.filter(tree=>tree.kind!=='relation');
      if(functions.length){
        if(functions.length>2)throw new Error('Use one or two shading functions with x and y bounds');
        const bounds=axis=>constraints.flatMap((constraint,index)=>constraint.axis===axis?[{side:constraint.side,tree:boundaries[index]}]:[]);
        item={mode:'band',trees:functions,xBounds:bounds('x'),yBounds:bounds('y')};
      }else item={mode:'region',trees:boundaries,constraints};
    }else if(trees.length>2){
      throw new Error('Enter one or two shading functions');
    }
    if(intervals.length){const [a,b]=intervals[0].split('..');if(!a||!b)throw new Error('Enter a..b for the shading interval');item.a=parse(a);item.b=parse(b);}
    return item;
  });
}
export function createGraphWorkspace({execute,options,onError:reportError,onClearError=()=>{},persist,isBusy,isReady=()=>true,saved={},getColors=()=>defaultGraphColors}){
  const $=id=>document.getElementById(id),value=id=>$(id).value,kind=()=>value('graph-kind');
  const onError=message=>{setText($('graph-status'),message);$('graph-status').classList.add('error');reportError(message);};
  let result=null,bounds=null,analysis=null,trace=null,integral=null,parameters={...(saved.parameters||{})},parameterRanges={...(saved.parameterRanges||{})},derivative=null,secondDerivative=null,radianAxis=!!saved.radianAxis,active=false,pending=false,timer=null,animation=null,revision=0,analysisRevision=0,signature='';
  let animationEnabled={...(saved.animationEnabled||{})},heightScale=[1,.5,2].includes(saved.heightScale)?saved.heightScale:saved.halfHeight?.5:1,running=false,frame=null,formulaSignature='',tableResult=null,tableDigits=null,tableLanguage=null;
  let parametersOpen=saved.parametersOpen!==false,pendingAnalysis=null,viewDragging=false;
  const viewSampler=graphSamplingInterval(()=>{if(pending&&active&&!running&&!isBusy()&&isReady())run();});
  const window=$('graph-plot').ownerDocument.defaultView;
  let exporting=false;
  function exportControls(){for(const format of ['svg','png'])$('graph-save-'+format).disabled=exporting||!result||!bounds;}
  for(const format of ['svg','png'])$('graph-save-'+format).onclick=async()=>{
    if(!result||!bounds||exporting)return;
    exporting=true;exportControls();
    try{
      let range=result.surface?zRange():null;
      const shown=range?{...result,zMin:range[0],zMax:range[1]}:result;
      const settings={colors:getColors(),digits:options().displayDigits,dots:kind()==='sequence',analysis,trace,integral,selected:selectedPlotIndex(),radianAxis,heightScale,surfaceView:surface};
      // Render synchronously to snapshot the current view before async encoding.
      if(format==='svg')downloadFile('symvacas-graph.svg',graphSvg($('graph-plot'),shown,bounds,settings),'image/svg+xml');
      else {draw();downloadFile('symvacas-graph.png',await graphPng($('graph-plot').querySelector('canvas')),'image/png');}
    }catch(error){onError(error.message||'Could not export the graph');}
    finally{exporting=false;exportControls();}
  };
  const requestFrame=callback=>window.requestAnimationFrame?window.requestAnimationFrame(callback):window.setTimeout(callback,16);
  const cancelFrame=id=>window.cancelAnimationFrame?window.cancelAnimationFrame(id):window.clearTimeout(id);
  function draw(){if(result&&bounds){let range;try{range=result.surface?zRange():null;}catch{return;}plot($('graph-plot'),range?{...result,zMin:range[0],zMax:range[1]}:result,bounds,{colors:getColors(),digits:options().displayDigits,dots:kind()==='sequence',analysis,trace,integral,selected:selectedPlotIndex(),radianAxis,heightScale,surfaceView:surface});}}
  function queueDraw(){if(frame!==null)return;frame=requestFrame(()=>{frame=null;draw();});}
  const resizeObserver=window.ResizeObserver?new window.ResizeObserver(queueDraw):null;
  resizeObserver?.observe($('graph-plot'));
  const surface={rotation:35,elevation:32,zoom:1,renderMode:'wireframe',color:'#007b68',samples:26,autoDensity:true,autoZ:!['graph-zmin','graph-zmax'].every(id=>Number.isFinite(saved.ranges?.[id])),...saved.surface};
  surface.elevation=Number.isFinite(surface.elevation)?Math.max(-90,Math.min(90,surface.elevation)):32;
  if(!['wireframe','surface','surface-wireframe'].includes(surface.renderMode))surface.renderMode='wireframe';
  if(!/^#[0-9a-f]{6}$/i.test(surface.color))surface.color='#007b68';
  surface.samples=Number.isFinite(surface.samples)?Math.max(12,Math.min(96,Math.round(surface.samples))):26;
  const sourceDrafts={implicit:'x^2+y^2=1',cartesian:'sin(x)\ncos(x)',parametric:'[cos(t),sin(t)]',space:'C(t)=4*(sin(t),cos(t),0.6*sin(2*t))',polar:'2*cos(3*t)',sequence:'n\nu(n-1)+u(n-2)',surface:'sin(sqrt(x^2+y^2))',differential:'y-t',...saved.sources};
  let sourceKind=kind();sourceDrafts[sourceKind]=value('graph-source');
  const inputHistory=createGraphInputHistory();
  function undoControls(){$('graph-undo').disabled=!inputHistory.canUndo(kind());}
  function rememberInput(){
    inputHistory.remember(kind(),{source:sourceDrafts[kind()],derivative,secondDerivative,parameters,parameterRanges,animationEnabled,selected:selected(),selectedDerivativeOrder,other:Number(value('graph-other'))});
    undoControls();
  }
  function syncInputLineScroll(){
    $('graph-line-numbers').style.transform=`translateY(${-$('graph-source').scrollTop}px)`;
  }
  function inputLineControls(){
    const gutter=$('graph-line-numbers');gutter.replaceChildren();
    value('graph-source').split(/\r?\n/).forEach((_,index)=>{
      const row=document.createElement('div'),number=document.createElement('span'),remove=document.createElement('button');
      row.className='graph-input-line';number.textContent=String(index+1);
      remove.type='button';remove.textContent='×';remove.title=`${t('Delete line')} ${index+1}`;remove.setAttribute('aria-label',remove.title);
      remove.onclick=()=>{
        const field=$('graph-source');field.value=removeGraphInputLine(field.value,index);field.oninput();persist();
      };
      row.append(number,remove);gutter.append(row);
    });
    syncInputLineScroll();
  }
  $('graph-source').addEventListener('scroll',syncInputLineScroll);
  const rangeIds=['graph-min','graph-max','graph-ymin','graph-ymax','graph-xmin','graph-xmax','graph-analysis-a','graph-analysis-b','graph-t0','graph-zmin','graph-zmax'];
  const sliderIds=rangeIds.filter(id=>id!=='graph-t0');
  const rangePairs=[['graph-min','graph-max'],['graph-ymin','graph-ymax'],['graph-xmin','graph-xmax'],['graph-zmin','graph-zmax'],['graph-analysis-a','graph-analysis-b']];
  const pairedSliders=new Map();
  function fieldNumber(input){return input.value.trim()===''?NaN:Number(input.dataset.displayValue===input.value?input.dataset.fullValue:input.value);}
  function numeric(id){return fieldNumber($(id));}
  function displayField(input,number){input.dataset.fullValue=String(number);input.value=displayNumber(number,options().displayDigits);input.dataset.displayValue=input.value;}
  function editField(input){input.onfocus=()=>{if(input.dataset.displayValue===input.value){input.value=input.dataset.fullValue;input.dataset.displayValue=input.value;}};input.onblur=()=>{const number=fieldNumber(input);if(Number.isFinite(number)&&!input.hasAttribute('aria-invalid'))displayField(input,number);};}
  function analysisRange(){return [numeric('graph-min'),numeric('graph-max')];}
  function syncRangePair(pair){
    const numbers=pair.ids.map(numeric);if(!numbers.every(Number.isFinite))return;
    const sliders=pair.ids.map(id=>$(id+'-slider')),linked=pair.ids[0]==='graph-analysis-a';
    const [low,high]=linked?analysisRange():[Math.min(...sliders.map(slider=>Number(slider.min)),...numbers),Math.max(...sliders.map(slider=>Number(slider.max)),...numbers)];
    if(!Number.isFinite(high-low)||high<=low)return;
    const positions=numbers.map(number=>Math.max(low,Math.min(high,number)));
    sliders.forEach((slider,index)=>{slider.min=String(low);slider.max=String(high);slider.value=String(positions[index]);slider.setAttribute('aria-valuetext',displayNumber(positions[index],options().displayDigits));});
    pair.track.style.setProperty('--range-start',`${100*(positions[0]-low)/(high-low)}%`);pair.track.style.setProperty('--range-end',`${100*((sliders[1].hidden?positions[0]:positions[1])-low)/(high-low)}%`);
  }
  function resetRangeDomain(pair){
    if(pair.ids[0]==='graph-analysis-a'){syncRangePair(pair);return;}
    const [min,max]=pair.ids.map(numeric),span=max-min;if(!Number.isFinite(span)||span<=0)return;
    const low=min-span/2,high=max+span/2;if(!Number.isFinite(high-low))return;
    for(const id of pair.ids){$(id+'-slider').min=String(low);$(id+'-slider').max=String(high);}
    syncRangePair(pair);
  }
  function showNumber(id,number){const input=$(id);if(!Number.isFinite(number))return;displayField(input,number);const output=$(id+'-value');if(output)output.textContent=displayNumber(number,options().displayDigits);const slider=$(id+'-slider');if(slider){if(number<Number(slider.min))slider.min=String(number);if(number>Number(slider.max))slider.max=String(number);slider.value=String(number);slider.setAttribute('aria-valuetext',displayNumber(number,options().displayDigits));}const pair=pairedSliders.get(id);if(pair)syncRangePair(pair);}
  function renderRangeNumbers(){for(const id of rangeIds){if(value(id)!==''&&document.activeElement!==$(id))showNumber(id,numeric(id));}}
  const displayOptions=()=>({digits:options().displayDigits,notation:'off'});
  const selected=()=>Number(value('graph-selected'))||0;
  let selectedDerivativeOrder=0;
  const analysisCurveKey=()=>selectedDerivativeOrder?-selectedDerivativeOrder:selected();
  const analysisTarget=()=>graphAnalysisTarget(analysisCurveKey(),derivative,secondDerivative);
  const otherTarget=()=>graphAnalysisTarget(Number(value('graph-other')),derivative,secondDerivative);
  const analysisCurveLabel=key=>graphAnalysisCurves(expressions().length,derivative,secondDerivative).find(curve=>curve.key===key)?.label||'';
  let otherChoicesSignature='';
  function otherChoices(){
    const choices=graphAnalysisCurves(expressions().length,derivative,secondDerivative).filter(curve=>curve.key!==analysisCurveKey()),signature=JSON.stringify(choices);
    if(signature===otherChoicesSignature)return;otherChoicesSignature=signature;
    const previous=value('graph-other');$('graph-other').replaceChildren(...choices.map(curve=>{const option=document.createElement('option');option.value=String(curve.key);option.textContent=curve.label;return option;}));
    $('graph-other').value=choices.some(curve=>String(curve.key)===previous)?previous:String(choices[0]?.key??'');
  }
  const selectedPlotIndex=()=>graphSelectedCurveIndex(selected(),selectedDerivativeOrder,
    derivative!==null&&result?.derivativeSelected===derivative?result.derivativeCurveIndex:-1,
    secondDerivative!==null&&result?.secondDerivativeSelected===secondDerivative?result.secondDerivativeCurveIndex:-1);
  function selectDerivative(order){
    selectedDerivativeOrder=order;pendingAnalysis=null;analysisRevision++;analysis=null;trace=null;integral=null;
    render();persist();
  }
  const expressions=()=>graphExpressions(value('graph-source'),kind());
  function currentBounds(){return {xmin:numeric(['cartesian','implicit','surface'].includes(kind())?'graph-min':'graph-xmin'),xmax:numeric(['cartesian','implicit','surface'].includes(kind())?'graph-max':'graph-xmax'),ymin:numeric('graph-ymin'),ymax:numeric('graph-ymax')};}
  function writeBounds(next){showNumber(['cartesian','implicit','surface'].includes(kind())?'graph-min':'graph-xmin',next.xmin);showNumber(['cartesian','implicit','surface'].includes(kind())?'graph-max':'graph-xmax',next.xmax);showNumber('graph-ymin',next.ymin);showNumber('graph-ymax',next.ymax);for(const pair of new Set(pairedSliders.values()))resetRangeDomain(pair);analysisControls();}
  function zControls(){for(const id of ['graph-zmin','graph-zmax']){$(id).disabled=surface.autoZ;const slider=$(id+'-slider');if(slider)slider.disabled=surface.autoZ;}}
  function zRange(){
    if(surface.autoZ)return surfaceZRange(result?.zMin??-1,result?.zMax??1);
    const min=numeric('graph-zmin'),max=numeric('graph-zmax');
    if(!Number.isFinite(max-min)||max<=min)throw new Error('Enter finite values with minimum < maximum');
    return [min,max];
  }
  function makeRequest(){
    const trees=expressions().map(s=>graphInputTree(s,kind())),shadings=graphShadings(value('graph-source'),kind()),min=numeric('graph-min'),max=numeric('graph-max'),view=currentBounds();
    if(!trees.length&&!shadings.length)throw new Error('Enter a function to graph');
    if(![min,max,...Object.values(view)].every(Number.isFinite)||max<=min||view.xmax<=view.xmin||view.ymax<=view.ymin)throw new Error('Enter finite values with minimum < maximum');
    if(kind()==='surface')zRange();
    const request={...options(),action:'graph',angle:'RAD',graphKind:kind(),trees,shadings,min,max,xMin:view.xmin,xMax:view.xmax,yMin:view.ymin,yMax:view.ymax,surfaceYMin:view.ymin,surfaceYMax:view.ymax,parameters:{...parameters},variable:['parametric','polar','space','differential'].includes(kind())?'t':kind()==='sequence'?'n':'x',samples:animation?200:500};
    if(kind()==='space'){[request.surfaceZMin,request.surfaceZMax]=surface.autoZ?[-5,5]:zRange();}if(kind()==='surface'){const density=surfaceSampleCount(view,surface.samples,surface.autoDensity,surface.zoom,isImplicitSurface(value('graph-source')));request.surfaceSamples=animation?Math.min(density,isImplicitSurface(value('graph-source'))?16:32):density;[request.surfaceZMin,request.surfaceZMax]=surface.autoZ?[view.ymin,view.ymax]:zRange();}
    if(kind()==='sequence')request.initialTrees=splitTopLevel(value('graph-initial')).map(s=>parse(s));
    if(kind()==='differential'){request.initialValues=splitTopLevel(value('graph-initial')).map(Number);request.t0=numeric('graph-t0');}
    if(derivative!==null&&kind()==='cartesian'&&trees[derivative])request.derivativeSelected=derivative;
    if(secondDerivative!==null&&kind()==='cartesian'&&trees[secondDerivative])request.secondDerivativeSelected=secondDerivative;
    return request;
  }
  function densityControls(){const implicit=isImplicitSurface(value('graph-source'));$('graph-surface-samples').max=implicit?'32':'96';const count=surfaceSampleCount(currentBounds(),surface.samples,surface.autoDensity,surface.zoom,isImplicitSurface(value('graph-source')));$('graph-auto-density').checked=surface.autoDensity;$('graph-surface-samples').disabled=surface.autoDensity;$('graph-surface-samples').value=String(count);$('graph-surface-samples-value').textContent=implicit?`${count} × ${count} × ${count}`:`${count} × ${count}`;}
  function queue(){pending=true;densityControls();clearTimeout(timer);timer=null;if(animation||!active)return;timer=setTimeout(()=>{timer=null;if(active&&!running&&!isBusy()&&isReady())run();},180);}
  function flush(){
    if(pendingAnalysis&&!running&&!isBusy()&&isReady()){const next=pendingAnalysis;pendingAnalysis=null;analyze(next.action,next.point);return;}
    if(!animation&&pending&&active&&!running&&!isBusy()&&isReady()&&timer===null){if(viewDragging)viewSampler.schedule();else queue();}
  }
  function selections(){
    if(selectedDerivativeOrder===1&&derivative===null||selectedDerivativeOrder===2&&secondDerivative===null)selectedDerivativeOrder=0;
    const count=expressions().length;
    const before=selected();$('graph-selected').replaceChildren(...Array.from({length:count},(_,i)=>{const option=document.createElement('option');option.value=String(i);option.textContent=`f${i+1}`;return option;}));$('graph-selected').value=String(Math.min(before,Math.max(0,count-1)));otherChoices();
  }
  function formulas(){
    const next=JSON.stringify([value('graph-source'),kind(),derivative,secondDerivative,result?.derivativeExpression,result?.secondDerivativeExpression,selected(),selectedDerivativeOrder,options().displayDigits,document.documentElement.lang,getColors()]);if(next===formulaSignature)return;formulaSignature=next;
    $('graph-derivative-label').textContent=`${t('Derivative curve')} f${(derivative??selected())+1}′`;
    $('graph-second-derivative-label').textContent=`${t('Derivative curve')} f${(secondDerivative??selected())+1}″`;
    const variable=kind()==='sequence'?'n':['parametric','polar','space','differential'].includes(kind())?'t':'x',labels=expressions().map((s,i)=>kind()==='cartesian'?cartesianFormula(s,i):['parametric','space'].includes(kind())?parametricFormula(s,i,kind()==='space'):kind()==='surface'?surfaceFormula(s):kind()==='differential'?`diff(y,t)=${s}`:kind()==='polar'?`r${i+1}(t)=${s}`:`f${i+1}(${variable})=${s}`);
    if(derivative!==null&&kind()==='cartesian'&&expressions()[derivative])labels.push(`diff(f${derivative+1}(x),x)`+(result?.derivativeSelected===derivative&&result?.derivativeExpression?`=${result.derivativeExpression}`:''));
    if(secondDerivative!==null&&kind()==='cartesian'&&expressions()[secondDerivative])labels.push(`diff(f${secondDerivative+1}(x),x,2)`+(result?.secondDerivativeSelected===secondDerivative&&result?.secondDerivativeExpression?`=${result.secondDerivativeExpression}`:''));
    for(const line of graphSources(value('graph-source'),kind()).filter(isGraphShading))labels.push(graphShadingBody(line).split(';')[0].trim());
    renderFormulas($('graph-formulas'),labels,{digits:options().displayDigits});
    const lines=[...$('graph-formulas').children],count=expressions().length;
    function removeButton(line,label,remove){
      const button=document.createElement('button');button.type='button';button.className='graph-formula-remove';button.textContent='×';button.setAttribute('aria-label',label);button.title=label;button.onclick=remove;line.append(button);
    }
    function selectButton(line,active,label,pick,color){
      line.classList.toggle('selected-curve',active);line.style.setProperty('--curve-color',color);
      const button=document.createElement('button');button.type='button';button.className='graph-formula-select';button.setAttribute('aria-label',label);button.setAttribute('aria-pressed',String(active));button.append(...line.childNodes);line.append(button);button.onclick=pick;
    }
    lines.slice(0,count).forEach((line,i)=>{
      const pick=()=>{$('graph-selected').value=String(i);$('graph-selected').onchange();};
      selectButton(line,selectedDerivativeOrder===0&&i===selected(),`f${i+1}`,pick,graphCurveColor(i,getColors()));
      removeButton(line,`${t('Delete graph')}: f${i+1}`,()=>removeSource(i));
    });
    const hasDerivative=derivative!==null&&kind()==='cartesian'&&expressions()[derivative];
    if(hasDerivative){
      selectButton(lines[count],selectedDerivativeOrder===1,`f${derivative+1}′`,()=>selectDerivative(1),graphCurveColor(count,getColors()));
      removeButton(lines[count],t('Delete derivative curve'),()=>{rememberInput();derivative=null;if(selectedDerivativeOrder===1)selectedDerivativeOrder=0;$('graph-derivative').checked=false;clearPlot();formulas();queue();persist();});
    }
    const hasSecondDerivative=secondDerivative!==null&&kind()==='cartesian'&&expressions()[secondDerivative];
    if(hasSecondDerivative){
      const index=count+(hasDerivative?1:0);
      selectButton(lines[index],selectedDerivativeOrder===2,`f${secondDerivative+1}″`,()=>selectDerivative(2),graphCurveColor(index,getColors()));
      removeButton(lines[index],`${t('Delete derivative curve')}: f${secondDerivative+1}″`,()=>{rememberInput();secondDerivative=null;if(selectedDerivativeOrder===2)selectedDerivativeOrder=0;$('graph-second-derivative').checked=false;clearPlot();formulas();queue();persist();});
    }
    lines.slice(count+(hasDerivative?1:0)+(hasSecondDerivative?1:0)).forEach((line,i)=>removeButton(line,`${t('Delete shading')}: ${i+1}`,()=>removeSource(i,true)));
  }
  function clearPlot(){
    viewSampler.cancel();viewDragging=false;
    result=null;analysis=null;trace=null;integral=null;pendingAnalysis=null;revision++;analysisRevision++;
    for(const id of ['graph-plot','graph-table','graph-trace','graph-analysis-result'])$(id).replaceChildren();
    $('graph-status').textContent='';$('graph-status').classList.remove('error');
    exportControls();
  }
  function removeSource(index,shading=false){
    const next=removeGraphSource(value('graph-source'),index,kind(),shading),before=selected(),other=Number(value('graph-other'));
    if(next===value('graph-source'))return;
    rememberInput();
    stopAnimation(false);clearPlot();$('graph-source').value=next;
    const count=expressions().length,adjust=i=>Math.max(0,Math.min(i>index?i-1:i,count-1));
    selections();
    if(!shading){$('graph-selected').value=String(adjust(before));$('graph-other').value=String(adjust(other));}
    if(count>1&&value('graph-other')===value('graph-selected'))$('graph-other').value=String((selected()+1)%count);
    $('graph-source').oninput();persist();
  }
  function render(){
    exportControls();heightControls();densityControls();parameterVisibility();formulas();renderRangeNumbers();analysisControls();syncParameterControls();if(!result||!bounds)return;
    for(const [id,number,suffix] of [['graph-rotation',surface.rotation,'°'],['graph-elevation',surface.elevation,'°'],['graph-surface-zoom',surface.zoom*100,'%']]){let output=$(id+'-value');if(!output){output=document.createElement('output');output.id=id+'-value';$(id).insertAdjacentElement('afterend',output);}output.textContent=displayNumber(number,options().displayDigits)+suffix;}
    let shown=result;
    if(result.surface){
      let range;try{range=zRange();}catch{return;}
      const [zMin,zMax]=range;shown={...result,zMin,zMax};
      if(surface.autoZ){showNumber('graph-zmin',zMin);showNumber('graph-zmax',zMax);resetRangeDomain(pairedSliders.get('graph-zmin'));}
    }
    plot($('graph-plot'),shown,bounds,{colors:getColors(),digits:options().displayDigits,dots:kind()==='sequence',analysis,trace,integral,selected:selectedPlotIndex(),radianAxis,heightScale,surfaceView:surface});
    table();renderAnalysis();
    $('graph-trace').replaceChildren();if(trace)$('graph-trace').append(mathDisplay({kind:'relation',value:'≈',args:[{kind:'symbol',value:'Trace'},{kind:'tuple',args:trace.map(n=>({kind:'number',value:String(n)}))}]},options().displayDigits,true));
  }
  function table(){
    if(tableResult===result&&tableDigits===options().displayDigits&&tableLanguage===document.documentElement.lang)return;tableResult=result;tableDigits=options().displayDigits;tableLanguage=document.documentElement.lang;
    const table=document.createElement('table'),head=document.createElement('thead'),header=document.createElement('tr');
    for(const name of ['Curve','x / n','y',...(result.spaceCurves?['z','t']:result.curveParameters?['t']:result.surface?['z']:[])]){const th=document.createElement('th');th.textContent=t(name);header.append(th);}head.append(header);table.append(head);const body=document.createElement('tbody');
    (result.spaceCurves||result.curves||result.surface||[]).forEach((curve,i)=>curve.forEach((point,index)=>{if(!point||index%Math.max(1,Math.floor(curve.length/60))!==0)return;const row=document.createElement('tr');for(const number of [i+1,...point,...(result.curveParameters?[result.curveParameters[i]?.[index]]:[])]){const td=document.createElement('td');td.textContent=displayNumber(number,options().displayDigits);row.append(td);}if(!result.surface){row.tabIndex=0;const pick=()=>{if(i===result.derivativeCurveIndex&&derivative!==null)selectDerivative(1);else if(i===result.secondDerivativeCurveIndex&&secondDerivative!==null)selectDerivative(2);else if(i<expressions().length){$('graph-selected').value=String(i);$('graph-selected').onchange();}selectTrace(point,result.curveParameters?.[i]?.[index]??point[0]);};row.onclick=pick;row.onkeydown=e=>{if(['Enter',' '].includes(e.key)){e.preventDefault();pick();}};}body.append(row);}));table.append(body);$('graph-table').replaceChildren(table);
  }
  function parameterVisibility(){
    const hasParameters=$('graph-parameters').children.length>0;
    $('graph-parameter-actions').hidden=!hasParameters;
    $('graph-parameters').hidden=!hasParameters||!parametersOpen;
    $('graph-parameters-toggle').setAttribute('aria-expanded',String(parametersOpen));
  }
  $('graph-parameters-toggle').onclick=()=>{parametersOpen=!parametersOpen;parameterVisibility();persist();};
  for(const [id,preference] of [['graph-ranges','rangesOpen'],['graph-help','helpOpen']]){
    $(id).open=id==='graph-help'?saved[preference]===true:saved[preference]!==false;
    $(id).addEventListener('toggle',persist);
  }
  function parameterChanged(){analysisRevision++;analysis=null;trace=null;integral=null;queueDraw();queue();}
  function syncParameterControls(resetRange=false){
    for(const caption of $('graph-parameters').querySelectorAll('[data-parameter]')){
      const name=caption.dataset.parameter,group=caption.closest('.graph-parameter'),number=parameters[name],limits=parameterRanges[name]||[-5,5];
      caption.textContent=`${name} = ${displayNumber(number,options().displayDigits)}`;
      const slider=group.querySelector('input[type="range"]'),field=group.querySelector('[data-parameter-value]');
      slider.min=String(limits[0]);slider.max=String(limits[1]);slider.value=String(number);slider.setAttribute('aria-valuetext',displayNumber(number,options().displayDigits));
      if(document.activeElement!==field){displayField(field,number);field.removeAttribute('aria-invalid');}
      for(const bound of group.querySelectorAll('[data-parameter-bound]'))if(document.activeElement!==bound)displayField(bound,limits[Number(bound.dataset.parameterBound)]);
      const rangeTrack=group.querySelector('[data-parameter-range]'),rangeSliders=[...rangeTrack.querySelectorAll('input[type="range"]')],span=limits[1]-limits[0];
      const low=resetRange?limits[0]-span/2:Math.min(Number(rangeSliders[0].min),limits[0]),high=resetRange?limits[1]+span/2:Math.max(Number(rangeSliders[0].max),limits[1]);
      rangeSliders.forEach((slider,index)=>{slider.min=String(low);slider.max=String(high);slider.value=String(limits[index]);slider.setAttribute('aria-valuetext',displayNumber(limits[index],options().displayDigits));});
      rangeTrack.style.setProperty('--range-start',`${100*(limits[0]-low)/(high-low)}%`);rangeTrack.style.setProperty('--range-end',`${100*(limits[1]-low)/(high-low)}%`);
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
        const half=(b-a)/2;
        parameterRanges[name]=[number-half,number+half];parameters[name]=number;
        if(animation)animation.phases[name]=parameterPhase(name)-animation.phase;
        valueInput.removeAttribute('aria-invalid');$('graph-status').textContent='';syncParameterControls(true);persist();parameterChanged();
      };
      valueInput.oninput=()=>valueInput.removeAttribute('aria-invalid');valueInput.onchange=applyValue;
      valueInput.onkeydown=event=>{if(event.key==='Enter'){event.preventDefault();applyValue();}else if(event.key==='Escape'){event.preventDefault();displayField(valueInput,parameters[name]);if(document.activeElement===valueInput){valueInput.value=valueInput.dataset.fullValue;valueInput.dataset.displayValue=valueInput.value;}valueInput.removeAttribute('aria-invalid');}};
      for(const [index,[field,number,labelText]] of [[low,limits[0],'Slider minimum'],[high,limits[1],'Slider maximum']].entries()){field.type='number';field.step='any';displayField(field,number);editField(field);field.dataset.parameterBound=String(index);field.setAttribute('aria-label',`${t(labelText)}: ${name}`);field.onchange=()=>{const a=fieldNumber(low),b=fieldNumber(high);if(!Number.isFinite(a)||!Number.isFinite(b)||a>=b){onError(t('Enter finite values with minimum < maximum'));return;}parameterRanges[name]=[a,b];parameters[name]=a+(b-a)/2;if(animation)animation.phases[name]=parameterPhase(name)-animation.phase;parameterControls(names,true);persist();parameterChanged();};}
      const rangeTrack=document.createElement('div');rangeTrack.className='graph-range-slider';rangeTrack.dataset.parameterRange=name;
      for(const index of [0,1]){
        const boundSlider=document.createElement('input'),span=limits[1]-limits[0];boundSlider.type='range';boundSlider.step='any';boundSlider.min=String(limits[0]-span/2);boundSlider.max=String(limits[1]+span/2);boundSlider.value=String(limits[index]);
        boundSlider.setAttribute('aria-label',`${t(index===0?'Slider minimum':'Slider maximum')}: ${name}`);
        boundSlider.oninput=()=>{
          const next=[...(parameterRanges[name]||[-5,5])],number=Number(boundSlider.value),other=next[1-index],gap=Math.max(2e-7,Math.abs(other)*1e-10);
          next[index]=index===0?Math.min(number,other-gap):Math.max(number,other+gap);
          parameterRanges[name]=next;parameters[name]=next[0]+(next[1]-next[0])/2;
          if(animation)animation.phases[name]=parameterPhase(name)-animation.phase;
          syncParameterControls();parameterChanged();
        };
        boundSlider.onchange=persist;rangeTrack.append(boundSlider);
      }
      bindRangeTrackClick(rangeTrack);
      label.className='graph-parameter-value';label.append(caption,valueInput);ranges.className='form-row';ranges.append(low,high);const group=document.createElement('div');group.className='graph-parameter';group.append(label,input,ranges,rangeTrack,toggleLabel);$('graph-parameters').append(group);
    }
    syncParameterControls();parameterVisibility();
  }
  async function run(){
    if(value('graph-source')!==sourceDrafts[kind()])$('graph-source').oninput();
    clearTimeout(timer);timer=null;if(running||isBusy()||!isReady()){pending=true;return;}pending=false;running=true;
    try{
      if(!value('graph-source').trim()){clearPlot();parameterControls([],true);selections();render();persist();return;}
      const preview=viewDragging&&['cartesian','implicit','differential'].includes(kind()),baseRequest=makeRequest(),request=preview?graphPreviewRequest(baseRequest):baseRequest,identity=graphSamplingIdentity(baseRequest),token=++revision,source=value('graph-source'),graphKind=kind();
      const nextSignature=JSON.stringify([source,graphKind,parameters,derivative,secondDerivative]);if(nextSignature!==signature){analysis=null;trace=null;integral=null;analysisRevision++;signature=nextSignature;}
      const response=await execute(request,{background:preview});if(token!==revision||source!==value('graph-source')||graphKind!==kind()||!animation&&identity!==graphSamplingIdentity(makeRequest()))return;
      if(!response.ok){onError(response.error);return;}
      result=response;bounds=currentBounds();$('graph-status').textContent='';parameterControls(response.parameters);
      if(animation||viewDragging)queueDraw();else{selections();render();persist();}
    }catch(error){onError(error.message);}finally{running=false;flush();}
  }
  function renderAnalysis(){
    const container=$('graph-analysis-result');container.replaceChildren();if(!analysis)return;
    const title=document.createElement('div');title.textContent=`${t($('graph-analysis-action').selectedOptions[0]?.textContent||'Analyze')} · ${analysisCurveLabel(analysisCurveKey())}`+(analysis.analysis==='intersection'?` × ${analysisCurveLabel(Number(value('graph-other')))}`:'');container.append(title);
    if(analysis.value!==undefined)container.append(mathDisplay({kind:'number',value:String(analysis.value)},options().displayDigits,true));
    if(analysis.vertical){const note=document.createElement('div');note.textContent=t('Vertical tangent');container.append(note);}
    for(const point of analysis.points||[]){const button=document.createElement('button');button.className='analysis-point';button.append(mathDisplay({kind:'tuple',args:point.map(n=>({kind:'number',value:String(n)}))},options().displayDigits,true));button.onclick=()=>{if(kind()==='cartesian')selectTrace(point);else{trace=point;render();}};container.append(button);}
    if(!analysis.points?.length&&analysis.value===undefined&&!analysis.vertical){const note=document.createElement('div');note.textContent=t('No points found in this interval');container.append(note);}
  }
  async function analyze(action=value('graph-analysis-action'),point=null){
    if(running||isBusy()||!isReady()){pendingAnalysis={action,point};return;}
    if(pending&&kind()==='cartesian'&&['derivative','tangent'].includes(action)){pendingAnalysis={action,point};return run();}
    pendingAnalysis=null;clearTimeout(timer);timer=null;
    const token=++analysisRevision;
    try{
      const fixedIntercept=action==='yintercept'&&kind()==='cartesian';
      const trees=expressions().map(s=>graphInputTree(s,kind())),a=fixedIntercept?0:point??numeric('graph-analysis-a'),b=fixedIntercept||['derivative','tangent'].includes(action)?a:numeric('graph-analysis-b'),view=bounds||currentBounds(),source=value('graph-source'),graphKind=kind();
      if(!Number.isFinite(a)||!Number.isFinite(b)||!fixedIntercept&&!['derivative','tangent'].includes(action)&&a>=b)throw new Error('Enter finite values with a < b');
      const currentParameters={...parameters};
      const target=analysisTarget(),other=otherTarget();
      if(!target||target.source>=trees.length||action==='intersection'&&(!other||other.source>=trees.length||JSON.stringify(target)===JSON.stringify(other)))throw new Error('Select two different functions');
      const tracePoint=trace||(graphKind==='cartesian'&&['derivative','tangent'].includes(action)&&(selectedDerivativeOrder!==0||result?.implicitCurves?.[selectedPlotIndex()])?curvePointAtX(result?.curves?.[selectedPlotIndex()]||[],a):null);
      const response=await execute({...options(),angle:'RAD',action:'graphAnalysis',graphKind,trees,analysis:action,a,b,selected:target.source,selectedDerivativeOrder:target.order,other:other?.source??0,otherDerivativeOrder:other?.order??0,variable:graphKind==='cartesian'?'x':'t',parameters:currentParameters,tracePoint,xMin:view.xmin,xMax:view.xmax,yMin:view.ymin,yMax:view.ymax});
      if(token!==analysisRevision||source!==value('graph-source')||graphKind!==kind()||JSON.stringify(currentParameters)!==JSON.stringify(parameters))return;if(!response.ok){onError(response.error);return;}
      analysis=response;$('graph-status').textContent='';integral=action==='integral'&&graphKind==='cartesian'?[a,b]:null;trace=response.points?.[0]||trace;$('graph-analysis-action').value=action;analysisControls();render();
    }catch(error){if(token===analysisRevision)onError(error.message);}finally{flush();}
  }
  function changeView(next,commit=true){
    if(!Object.values(next).every(Number.isFinite)||next.xmin>=next.xmax||next.ymin>=next.ymax){onError('Enter finite values with minimum < maximum');return;}
    viewDragging=!commit;bounds=next;writeBounds(next);queueDraw();
    if(commit){
      viewSampler.cancel();render();persist();
      if(['cartesian','implicit','surface','differential'].includes(kind())){clearTimeout(timer);timer=null;pending=true;run();}
    }else if(['cartesian','implicit','differential'].includes(kind())){pending=true;viewSampler.schedule();}
  }
  function surfaceChange(dx,dy,zoom){const previousCount=surfaceSampleCount(currentBounds(),surface.samples,surface.autoDensity,surface.zoom,isImplicitSurface(value('graph-source')));surface.rotation=((surface.rotation+dx*.7)%360+360)%360;surface.elevation=Math.max(-90,Math.min(90,surface.elevation+dy*.5));surface.zoom=Math.max(.4,Math.min(3,surface.zoom*zoom));$('graph-rotation').value=String(surface.rotation);$('graph-elevation').value=String(surface.elevation);$('graph-surface-zoom').value=String(surface.zoom);queueDraw();persist();if(surface.autoDensity&&previousCount!==surfaceSampleCount(currentBounds(),surface.samples,true,surface.zoom,isImplicitSurface(value('graph-source'))))queue();}
  function selectTrace(point,parameter=point[0]){
    trace=point;
    const action=value('graph-analysis-action');
    if(['derivative','tangent'].includes(action)){showNumber('graph-analysis-a',parameter);$('graph-tangent-slider').value=String(parameter);}
    render();
    if(action==='tangent')analyze('tangent',parameter);
  }
  const disposeGestures=bindGraphGestures($('graph-plot'),{getBounds:()=>bounds,onView:changeView,onTrace:position=>{
    if(['cartesian','implicit'].includes(kind())){
      const x=bounds.xmin+(bounds.xmax-bounds.xmin)*position.x,y=bounds.ymax-(bounds.ymax-bounds.ymin)*position.y;
      const point=curvePointAtX(result?.curves?.[selectedPlotIndex()]||[],x,y,{fallbackToNearest:false});
      if(point)selectTrace(point);else{trace=null;render();}
    }else{
      const closest=nearestPoint(result,bounds,position,selectedPlotIndex());if(closest)selectTrace(closest.point,closest.parameter);
    }
  },isSurface:()=>['surface','space'].includes(kind()),onSurface:surfaceChange});
  const zoom=z=>{if(['surface','space'].includes(kind())){surfaceChange(0,0,z);return;}const at={x:.5,y:.5};changeView(transformBounds(bounds||currentBounds(),at,at,z));};
  $('graph-zoom-in').onclick=()=>zoom(2);$('graph-zoom-out').onclick=()=>zoom(.5);
  function heightControls(){const button=$('graph-height-toggle'),label=heightScale===1?'Half height':heightScale===.5?'Double height':'Full height';button.dataset.heightScale=String(heightScale);button.setAttribute('aria-pressed',String(heightScale!==1));button.setAttribute('aria-label',t(label));button.title=t(label);button.textContent=heightScale===1?'½':heightScale===.5?'2×':'1×';}
  $('graph-height-toggle').onclick=()=>{heightScale=heightScale===1?.5:heightScale===.5?2:1;heightControls();render();persist();};heightControls();
  $('graph-reset-ranges').onclick=()=>{surface.autoZ=true;$('graph-auto-z').checked=true;zControls();changeView({xmin:-3,xmax:3,ymin:-3,ymax:3});for(const pair of new Set(pairedSliders.values()))resetRangeDomain(pair);};
  for(const [id,dx,dy] of [['left',.15,0],['right',-.15,0],['up',0,.15],['down',0,-.15]])$('graph-'+id).onclick=()=>changeView(transformBounds(bounds||currentBounds(),{x:.5,y:.5},{x:.5+dx,y:.5+dy},1));
  $('graph-fit').onclick=()=>{if(['surface','space'].includes(kind())){surface.autoZ=true;$('graph-auto-z').checked=true;zControls();render();if(isImplicitSurface(value('graph-source')))queue();persist();return;}const view=bounds||currentBounds(),curves=derivative!==null||secondDerivative!==null?(result?.curves||[]).slice(0,expressions().length):result?.curves||[],ys=curves.flat().filter(p=>p&&p.every(Number.isFinite)&&p[0]>=view.xmin&&p[0]<=view.xmax).map(p=>p[1]);if(ys.length){const low=Math.min(...ys),high=Math.max(...ys),padding=Math.max((high-low)*.12,high===low?1:1e-6);changeView({...view,ymin:low-padding,ymax:high+padding});}};
  $('graph-reset').onclick=()=>{analysis=null;trace=null;integral=null;surface.rotation=35;surface.elevation=32;surface.zoom=1;$('graph-rotation').value='35';$('graph-elevation').value='32';$('graph-surface-zoom').value='1';if(kind()==='sequence'){showNumber('graph-min',0);showNumber('graph-max',20);changeView({xmin:0,xmax:20,ymin:-2,ymax:20});queue();}else if(kind()==='differential'){showNumber('graph-min',-5);showNumber('graph-max',5);changeView({xmin:-5,xmax:5,ymin:-3,ymax:5});}else if(kind()==='implicit'){changeView({xmin:-3,xmax:3,ymin:-3,ymax:3});}else if(['surface','space'].includes(kind())){surface.autoZ=true;$('graph-auto-z').checked=true;zControls();const extent=kind()==='space'?5:3;changeView({xmin:-extent,xmax:extent,ymin:-extent,ymax:extent});}else changeView({xmin:-10,xmax:10,ymin:-5,ymax:5});};
  $('graph-axis').onclick=()=>{radianAxis=!radianAxis;setText($('graph-axis'),radianAxis?'x: π rad':'x: decimal');render();persist();};
  $('graph-selected').onchange=()=>{
    selectedDerivativeOrder=0;pendingAnalysis=null;analysisRevision++;analysis=null;trace=null;integral=null;
    if(derivative!==null&&derivative!==selected()||secondDerivative!==null&&secondDerivative!==selected())rememberInput();
    if($('graph-derivative').checked)derivative=selected();
    if($('graph-second-derivative').checked)secondDerivative=selected();
    if(derivative!==null||secondDerivative!==null)queue();render();persist();
  };
  $('graph-derivative').onchange=()=>{rememberInput();pendingAnalysis=null;analysisRevision++;analysis=null;trace=null;integral=null;if(selectedDerivativeOrder===1)selectedDerivativeOrder=0;derivative=$('graph-derivative').checked?selected():null;queue();render();persist();};
  $('graph-second-derivative').onchange=()=>{rememberInput();pendingAnalysis=null;analysisRevision++;analysis=null;trace=null;integral=null;if(selectedDerivativeOrder===2)selectedDerivativeOrder=0;secondDerivative=$('graph-second-derivative').checked?selected():null;queue();render();persist();};
  $('graph-undo').onclick=()=>{
    const previous=inputHistory.undo(kind());if(!previous)return;
    stopAnimation(false);clearPlot();
    $('graph-source').value=previous.source;sourceDrafts[kind()]=previous.source;
    parameters=previous.parameters;parameterRanges=previous.parameterRanges;animationEnabled=previous.animationEnabled;
    selectedDerivativeOrder=previous.selectedDerivativeOrder??0;derivative=previous.derivative;secondDerivative=previous.secondDerivative??null;$('graph-derivative').checked=derivative!==null;$('graph-second-derivative').checked=secondDerivative!==null;
    selections();$('graph-selected').value=String(previous.selected);$('graph-other').value=String(previous.other);
    parameterControls(Object.keys(parameters),true);undoControls();inputLineControls();formulas();render();queue();persist();
  };
  $('graph-analysis-run').onclick=()=>analyze();$('graph-analysis-clear').onclick=()=>{pendingAnalysis=null;analysisRevision++;analysis=null;trace=null;integral=null;$('graph-status').textContent='';$('graph-status').classList.remove('error');onClearError();render();};
  $('graph-analysis-visible-range').onclick=()=>{
    const [low,high]=analysisRange();
    showNumber('graph-analysis-a',low);showNumber('graph-analysis-b',high);analysisControls();
    $('graph-tangent-slider').value=String(low);persist();
  };
  function analysisControls(){
    otherChoices();
    const action=value('graph-analysis-action'),fixedIntercept=action==='yintercept'&&kind()==='cartesian',point=['derivative','tangent'].includes(action),tangent=action==='tangent';
    $('graph-other').closest('label').hidden=action!=='intersection';$('graph-analysis-b').closest('label').hidden=point||fixedIntercept;
    $('graph-analysis-a').closest('label').hidden=fixedIntercept;$('graph-analysis-visible-range').hidden=fixedIntercept;
    $('graph-tangent-position').hidden=!tangent;$('graph-analysis-a-slider').hidden=tangent||fixedIntercept;$('graph-analysis-b-slider').hidden=point||fixedIntercept;
    const pair=pairedSliders.get('graph-analysis-a');if(pair){pair.track.hidden=tangent||fixedIntercept;syncRangePair(pair);}
    const [low,high]=analysisRange(),position=numeric('graph-analysis-a');
    if(Number.isFinite(high-low)&&high>low){$('graph-tangent-slider').min=String(low);$('graph-tangent-slider').max=String(high);if(Number.isFinite(position))$('graph-tangent-slider').value=String(Math.max(low,Math.min(high,position)));}
  }
  $('graph-analysis-action').onchange=analysisControls;$('graph-tangent-slider').oninput=()=>{showNumber('graph-analysis-a',Number(value('graph-tangent-slider')));};$('graph-tangent-slider').onchange=()=>analyze('tangent');
  $('graph-other').onchange=()=>{pendingAnalysis=null;analysisRevision++;analysis=null;trace=null;integral=null;render();persist();};
  function bindRangeTrackClick(track){
    track.addEventListener('click',event=>{
      if(event.target!==track||event.button!==0)return;
      const sliders=[...track.querySelectorAll('input[type="range"]')].filter(slider=>!slider.hidden&&!slider.disabled);
      const rect=track.getBoundingClientRect(),width=rect.width-20;
      if(!sliders.length||width<=0)return;
      const position=Math.max(0,Math.min(1,(event.clientX-rect.left-10)/width));
      const distance=slider=>Math.abs((Number(slider.value)-Number(slider.min))/(Number(slider.max)-Number(slider.min))-position);
      const slider=sliders.reduce((nearest,current)=>distance(current)<distance(nearest)||distance(current)===distance(nearest)&&current===document.activeElement?current:nearest);
      slider.value=String(Number(slider.min)+(Number(slider.max)-Number(slider.min))*position);
      slider.focus({preventScroll:true});
      for(const type of ['input','change'])slider.dispatchEvent(new track.ownerDocument.defaultView.Event(type,{bubbles:true}));
    });
  }
  const tangentTrack=document.createElement('div');tangentTrack.className='graph-range-slider graph-axis-slider';
  $('graph-tangent-slider').before(tangentTrack);tangentTrack.append($('graph-tangent-slider'));
  bindRangeTrackClick(tangentTrack);
  const analysisSliders=document.createElement('div');analysisSliders.id='graph-analysis-sliders';
  $('graph-analysis').querySelector('summary').after(analysisSliders);analysisSliders.append($('graph-tangent-position'));
  for(const id of ['graph-rotation','graph-elevation','graph-surface-zoom'])$(id).oninput=()=>{const previousCount=surfaceSampleCount(currentBounds(),surface.samples,surface.autoDensity,surface.zoom,isImplicitSurface(value('graph-source')));surface.rotation=Number(value('graph-rotation'));surface.elevation=Number(value('graph-elevation'));surface.zoom=Number(value('graph-surface-zoom'));render();persist();if(surface.autoDensity&&previousCount!==surfaceSampleCount(currentBounds(),surface.samples,true,surface.zoom,isImplicitSurface(value('graph-source'))))queue();};
  $('graph-surface-render').onchange=()=>{surface.renderMode=value('graph-surface-render');render();persist();};
  $('graph-surface-color').oninput=()=>{surface.color=value('graph-surface-color');render();persist();};
  $('graph-surface-samples').oninput=()=>{surface.samples=Number(value('graph-surface-samples'));$('graph-surface-samples-value').textContent=`${surface.samples} × ${surface.samples}`;queue();};
  $('graph-surface-samples').onchange=()=>{persist();queue();};
  $('graph-auto-density').onchange=()=>{surface.autoDensity=$('graph-auto-density').checked;densityControls();persist();queue();};
  $('graph-auto-z').onchange=()=>{surface.autoZ=$('graph-auto-z').checked;zControls();if(!surface.autoZ&&(!Number.isFinite(numeric('graph-zmax')-numeric('graph-zmin'))||numeric('graph-zmax')<=numeric('graph-zmin'))){const [min,max]=surfaceZRange(result?.zMin??-1,result?.zMax??1);showNumber('graph-zmin',min);showNumber('graph-zmax',max);}render();if(isImplicitSurface(value('graph-source')))queue();persist();};
  for(const id of ['graph-zmin','graph-zmax'])$(id).onchange=()=>{try{zRange();render();if(isImplicitSurface(value('graph-source')))queue();$('graph-status').textContent='';persist();}catch(error){onError(error.message);}};
  $('graph-reset-parameters').onclick=()=>{for(const name of Object.keys(parameters)){parameterRanges[name]=[-5,5];parameters[name]=1;if(animation)animation.phases[name]=parameterPhase(name)-animation.phase;}parameterControls(Object.keys(parameters),true);persist();parameterChanged();};
  function parameterPhase(name){const [a,b]=parameterRanges[name]||[-5,5];return Math.asin(Math.max(-1,Math.min(1,2*(parameters[name]-a)/(b-a)-1)));}
  function updateButtons(){
    exportControls();
    $('graph-analysis-run').disabled=!!animation||isBusy()||!isReady();
    undoControls();
  }
  function stopAnimation(refine=true){if(!animation)return;if(animation.frame!==null)cancelFrame(animation.frame);animation=null;setText($('graph-animate'),'Animate');if(refine){render();persist();run();}updateButtons();}
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
  $('graph-animate').onclick=()=>{if(animation){stopAnimation();return;}clearTimeout(timer);timer=null;analysis=null;trace=null;integral=null;render();animation={frame:null,previous:null,updated:-Infinity,phase:0,phases:Object.fromEntries(Object.keys(parameters).map(name=>[name,parameterPhase(name)]))};setText($('graph-animate'),'Stop');updateButtons();animation.frame=requestFrame(animateFrame);};
  function typeControls(){$('graph-reset-ranges').hidden=kind()!=='surface';$('graph-viewport-ranges').hidden=['cartesian','implicit','surface'].includes(kind());$('graph-surface-controls').hidden=!['surface','space'].includes(kind());$('graph-surface-samples').closest('.graph-surface-density').hidden=kind()==='space';$('graph-surface-render').closest('label').hidden=kind()==='space';setText($('graph-fit'),['surface','space'].includes(kind())?'Fit Z':'Fit Y');setText($('graph-analysis-visible-range'),kind()==='cartesian'?'Use visible x range':'Use visible t range');$('graph-analysis').hidden=!['cartesian','parametric','polar'].includes(kind());for(const id of ['graph-derivative','graph-second-derivative'])$(id).closest('label').hidden=kind()!=='cartesian';$('graph-initial').closest('label').hidden=!['sequence','differential'].includes(kind());$('graph-t0').closest('label').hidden=kind()!=='differential';$('graph-analysis-action').querySelector('[value="intersection"]').disabled=kind()!=='cartesian';analysisControls();}
  $('graph-kind').onchange=()=>{
    pendingAnalysis=null;
    sourceDrafts[sourceKind]=value('graph-source');sourceKind=kind();$('graph-source').value=sourceDrafts[sourceKind];inputLineControls();
    stopAnimation(false);
    const view=kind()==='space'?[-5,5,-5,5]:kind()==='sequence'?[0,20,-2,20]:kind()==='differential'?[-5,5,-3,5]:kind()==='cartesian'?[-10,10,-5,5]:[-3,3,-3,3];
    writeBounds({xmin:view[0],xmax:view[1],ymin:view[2],ymax:view[3]});
    if(['parametric','polar','space'].includes(kind())){showNumber('graph-min',0);showNumber('graph-max',2*Math.PI);}
    if(['sequence','differential'].includes(kind())){showNumber('graph-min',view[0]);showNumber('graph-max',view[1]);$('graph-initial').value=kind()==='sequence'?'0,1':'1';}
    parameters={};parameterRanges={};parameterControls([],true);selectedDerivativeOrder=0;derivative=null;secondDerivative=null;$('graph-derivative').checked=false;$('graph-second-derivative').checked=false;result=null;bounds=null;analysis=null;trace=null;integral=null;revision++;analysisRevision++;$('graph-plot').replaceChildren();$('graph-table').replaceChildren();selections();formulas();typeControls();undoControls();queue();persist();
  };
  $('graph-source').oninput=()=>{if(value('graph-source')!==sourceDrafts[kind()])rememberInput();sourceDrafts[kind()]=value('graph-source');inputLineControls();pendingAnalysis=null;selectedDerivativeOrder=0;derivative=null;secondDerivative=null;$('graph-derivative').checked=false;$('graph-second-derivative').checked=false;analysis=null;trace=null;integral=null;revision++;analysisRevision++;selections();formulas();queue();};
  for(const id of ['graph-min','graph-max','graph-ymin','graph-ymax','graph-xmin','graph-xmax','graph-initial','graph-t0'])$(id).onchange=()=>{analysisControls();queue();};
  for(const id of rangeIds){const field=$(id);editField(field);for(const name of ['input','change'])field.addEventListener(name,()=>{if(field.dataset.displayValue!==field.value)delete field.dataset.displayValue;const pair=pairedSliders.get(id);if(pair)resetRangeDomain(pair);if(['graph-min','graph-max','graph-analysis-a'].includes(id))analysisControls();});}
  for(const id of sliderIds){const field=$(id),slider=document.createElement('input'),current=numeric(id);slider.type='range';slider.id=id+'-slider';slider.min=String(Math.min(-30,current-20));slider.max=String(Math.max(30,current+20));slider.step='any';slider.value=String(current);slider.setAttribute('aria-label',field.closest('label').firstChild.textContent.trim());field.insertAdjacentElement('afterend',slider);
    slider.oninput=()=>{
      const number=Number(slider.value),pair=pairedSliders.get(id),partner=pair&&!pair.ids.some(key=>$(key+'-slider').hidden)?pair.ids.find(key=>key!==id):null;
      let bounded=number;
      if(partner&&Number.isFinite(numeric(partner))){const limit=numeric(partner),gap=Math.max(2e-7,Math.abs(limit)*1e-10);bounded=id===pair.ids[0]?Math.min(number,limit-gap):Math.max(number,limit+gap);}
      if(id.startsWith('graph-analysis'))bounded=Math.max(Number(slider.min),Math.min(Number(slider.max),bounded));
      showNumber(id,bounded);if(!id.startsWith('graph-analysis')){bounds=currentBounds();render();}else renderRangeNumbers();
    };
    slider.onchange=()=>{persist();if(id.startsWith('graph-analysis'))analysisControls();else if(!id.startsWith('graph-z')||isImplicitSurface(value('graph-source')))queue();};
  }
  for(const ids of rangePairs){
    const group=document.createElement('div'),fields=document.createElement('div'),track=document.createElement('div'),labels=ids.map(id=>$(id).closest('label'));
    group.className='graph-range-pair';fields.className='graph-range-fields';track.className='graph-range-slider';group.setAttribute('role','group');group.setAttribute('aria-labelledby',ids.map(id=>id+'-label').join(' '));
    const analysisPair=ids[0]==='graph-analysis-a';if(analysisPair)track.classList.add('graph-axis-slider');
    group.append(fields,track);labels[0].before(group);
    labels.forEach((label,index)=>{label.id=ids[index]+'-label';fields.append(label);track.append($(ids[index]+'-slider'));});
    if(analysisPair)analysisSliders.prepend(group);
    const pair={ids,track};for(const id of ids)pairedSliders.set(id,pair);resetRangeDomain(pair);
    bindRangeTrackClick(track);
  }
  for(const [id,number] of Object.entries(saved.ranges||{}))if(rangeIds.includes(id))showNumber(id,number);
  for(const pair of new Set(pairedSliders.values()))resetRangeDomain(pair);
  selections();formulas();typeControls();setText($('graph-axis'),radianAxis?'x: π rad':'x: decimal');for(const [id,name] of [['graph-rotation','rotation'],['graph-elevation','elevation'],['graph-surface-zoom','zoom']])$(id).value=String(surface[name]);
  $('graph-surface-render').value=surface.renderMode;$('graph-auto-z').checked=surface.autoZ;zControls();
  $('graph-surface-color').value=surface.color;densityControls();
  renderRangeNumbers();parameterControls([],true);inputLineControls();
  function addExpression(source,graphKind){
    const next=appendGraphSource(kind()===graphKind?value('graph-source'):sourceDrafts[graphKind]||'',source,graphKind);
    if(kind()!==graphKind){$('graph-kind').value=graphKind;$('graph-kind').onchange();}
    $('graph-source').value=next;$('graph-source').oninput();persist();
  }
  return {addExpression,run,render,flush,snapshot:()=>({sources:{...sourceDrafts,[kind()]:value('graph-source')},parameters:{...parameters},parameterRanges,parametersOpen,rangesOpen:$('graph-ranges').open,helpOpen:$('graph-help').open,animationEnabled:{...animationEnabled},heightScale,halfHeight:heightScale===.5,radianAxis,surface:{...surface},ranges:Object.fromEntries(rangeIds.filter(id=>!(surface.autoZ&&id.startsWith('graph-z'))&&value(id)!==''&&Number.isFinite(numeric(id))).map(id=>[id,numeric(id)]))}),updateButtons,activate(value){active=value;if(active&&(!result||pending))queue();if(!active){viewSampler.cancel();viewDragging=false;pendingAnalysis=null;clearTimeout(timer);timer=null;stopAnimation(false);}},dispose(){viewSampler.cancel();viewDragging=false;pendingAnalysis=null;active=false;disposeGestures();resizeObserver?.disconnect();if(frame!==null)cancelFrame(frame);clearTimeout(timer);stopAnimation(false);revision++;analysisRevision++;}};
}
