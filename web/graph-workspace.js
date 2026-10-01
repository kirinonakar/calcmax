import {parse,latexInput} from './parser.js';
import {plot} from './plot.js';
import {mathDisplay} from './math-display.js';
import {renderFormulas} from './formula-preview.js';
import {displayNumber} from './display-format.js';
import {bindGraphGestures,transformBounds,nearestPoint} from './graph-view.js';
import {t,setText} from './i18n.js';

export function splitTopLevel(source){
  const values=[];let depth=0,start=0;
  for(let i=0;i<source.length;i++){if('([{'.includes(source[i]))depth++;if(')]}'.includes(source[i]))depth--;if(source[i]===','&&!depth){values.push(source.slice(start,i).trim());start=i+1;}}
  values.push(source.slice(start).trim());return values.filter(Boolean);
}
export function graphSources(source,kind){
  return source.split(/\r?\n/).map(s=>s.trim()).filter(Boolean).slice(0,['surface','differential'].includes(kind)?1:8);
}
export function graphExpressions(source,kind){
  return graphSources(source,kind).filter(s=>!s.startsWith('[shade]')).slice(0,['surface','differential'].includes(kind)?1:6).map(s=>s.replace(kind==='surface'?/^z\s*=\s*/:kind==='differential'?/^dy\/dt\s*=\s*/:kind==='cartesian'?/^y\s*=\s*/:kind==='sequence'?/^u\(n\)\s*=\s*/:kind==='polar'?/^r\s*=\s*/:/^\s*=/,''));
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
  const surface={rotation:35,elevation:32,zoom:1,...saved.surface};
  const sourceDrafts={cartesian:'sin(x)\ncos(x)',parametric:'[cos(t),sin(t)]',polar:'2*cos(3*t)',sequence:'n\nu(n-1)+u(n-2)',surface:'sin(sqrt(x^2+y^2))',differential:'y-t',...saved.sources};
  let sourceKind=kind();sourceDrafts[sourceKind]=value('graph-source');
  const rangeIds=['graph-min','graph-max','graph-ymin','graph-ymax','graph-xmin','graph-xmax','graph-analysis-a','graph-analysis-b','graph-t0','graph-zmin','graph-zmax'];
  const sliderIds=rangeIds.filter(id=>!['graph-t0','graph-zmin','graph-zmax'].includes(id));
  function numeric(id){const input=$(id);return input.value.trim()===''?NaN:Number(input.dataset.displayValue===input.value?input.dataset.fullValue:input.value);}
  function showNumber(id,number){const input=$(id);if(!Number.isFinite(number))return;input.dataset.fullValue=String(number);input.value=displayNumber(number,options().displayDigits);input.dataset.displayValue=input.value;const output=$(id+'-value');if(output)output.textContent=displayNumber(number,options().displayDigits);const slider=$(id+'-slider');if(slider){if(number<Number(slider.min))slider.min=String(number);if(number>Number(slider.max))slider.max=String(number);slider.value=String(number);}}
  function renderRangeNumbers(){for(const id of rangeIds){if(value(id)!=='')showNumber(id,numeric(id));}}
  const displayOptions=()=>({digits:options().displayDigits,notation:'off'});
  const selected=()=>Number(value('graph-selected'))||0;
  const expressions=()=>graphExpressions(value('graph-source'),kind());
  function currentBounds(){return {xmin:numeric(['cartesian','surface'].includes(kind())?'graph-min':'graph-xmin'),xmax:numeric(['cartesian','surface'].includes(kind())?'graph-max':'graph-xmax'),ymin:numeric('graph-ymin'),ymax:numeric('graph-ymax')};}
  function writeBounds(next){showNumber(kind()==='cartesian'||kind()==='surface'?'graph-min':'graph-xmin',next.xmin);showNumber(kind()==='cartesian'||kind()==='surface'?'graph-max':'graph-xmax',next.xmax);showNumber('graph-ymin',next.ymin);showNumber('graph-ymax',next.ymax);}
  function makeRequest(){
    const trees=expressions().map(s=>parse(latexInput(s))),shadings=graphShadings(value('graph-source'),kind()),min=numeric('graph-min'),max=numeric('graph-max'),view=currentBounds();
    if(!trees.length&&!shadings.length)throw new Error('Enter a function to graph');
    if(![min,max,...Object.values(view)].every(Number.isFinite)||max<=min||view.xmax<=view.xmin||view.ymax<=view.ymin)throw new Error('Enter finite values with minimum < maximum');
    const request={...options(),action:'graph',angle:'RAD',graphKind:kind(),trees,shadings,min,max,yMin:view.ymin,yMax:view.ymax,surfaceYMin:view.ymin,surfaceYMax:view.ymax,parameters,variable:['parametric','polar','differential'].includes(kind())?'t':kind()==='sequence'?'n':'x',samples:500};
    if(kind()==='sequence')request.initialTrees=splitTopLevel(value('graph-initial')).map(s=>parse(s));
    if(kind()==='differential'){request.initialValues=splitTopLevel(value('graph-initial')).map(Number);request.t0=numeric('graph-t0');}
    if(derivative!==null&&kind()==='cartesian'&&trees[derivative]){trees.push(parse(`diff((${expressions()[derivative]}),x)`));request.derivativeCurveIndex=trees.length-1;}
    return request;
  }
  function queue(){pending=true;clearTimeout(timer);if(!active)return;timer=setTimeout(()=>{timer=null;if(active&&!isBusy()&&isReady())run();},180);}
  function flush(){if(pending&&active&&!isBusy()&&isReady())queue();}
  function selections(){
    const count=expressions().length;
    for(const id of ['graph-selected','graph-other']){const before=value(id)===''?(id==='graph-other'?1:0):Number(value(id));$(id).replaceChildren(...Array.from({length:count},(_,i)=>{const option=document.createElement('option');option.value=String(i);option.textContent=`f${i+1}`;return option;}));$(id).value=String(Math.min(before,Math.max(0,count-1)));}
  }
  function formulas(){
    const variable=kind()==='sequence'?'n':['parametric','polar','differential'].includes(kind())?'t':'x',labels=expressions().map((s,i)=>kind()==='parametric'?`f${i+1}(t)=${s}`:kind()==='surface'?`z=${s}`:kind()==='differential'?`diff(y,t)=${s}`:kind()==='polar'?`r${i+1}(t)=${s}`:`f${i+1}(${variable})=${s}`);
    if(derivative!==null&&kind()==='cartesian'&&expressions()[derivative])labels.push(`diff((${expressions()[derivative]}),x)`);
    for(const line of graphSources(value('graph-source'),kind()).filter(s=>s.startsWith('[shade]')))labels.push(line.slice(7).split(';')[0].trim());
    renderFormulas($('graph-formulas'),labels,{digits:options().displayDigits});
    [...$('graph-formulas').children].slice(0,expressions().length).forEach((line,i)=>{
      line.classList.toggle('selected-curve',i===selected());line.tabIndex=0;line.setAttribute('role','button');line.setAttribute('aria-pressed',String(i===selected()));
      const pick=()=>{$('graph-selected').value=String(i);$('graph-selected').onchange();};
      line.onclick=pick;line.onkeydown=event=>{if(['Enter',' '].includes(event.key)){event.preventDefault();pick();}};
    });
  }
  function render(){
    formulas();renderRangeNumbers();for(const caption of $('graph-parameters').querySelectorAll('[data-parameter]'))caption.textContent=`${caption.dataset.parameter} = ${displayNumber(parameters[caption.dataset.parameter],options().displayDigits)}`;if(!result||!bounds)return;
    for(const [id,number,suffix] of [['graph-rotation',surface.rotation,'°'],['graph-elevation',surface.elevation,'°'],['graph-surface-zoom',surface.zoom*100,'%']]){let output=$(id+'-value');if(!output){output=document.createElement('output');output.id=id+'-value';$(id).insertAdjacentElement('afterend',output);}output.textContent=displayNumber(number,options().displayDigits)+suffix;}
    let shown=result;
    if(result.surface&&value('graph-zmin')!==''&&value('graph-zmax')!==''){const zMin=numeric('graph-zmin'),zMax=numeric('graph-zmax');if(Number.isFinite(zMin)&&Number.isFinite(zMax)&&zMax>zMin)shown={...result,zMin,zMax};}
    plot($('graph-plot'),shown,bounds,{digits:options().displayDigits,dots:kind()==='sequence',analysis,trace,integral,selected:selected(),radianAxis,surfaceView:surface});
    table();renderAnalysis();
    $('graph-trace').replaceChildren();if(trace)$('graph-trace').append(mathDisplay({kind:'relation',value:'≈',args:[{kind:'symbol',value:'Trace'},{kind:'tuple',args:trace.map(n=>({kind:'number',value:String(n)}))}]},options().displayDigits,true));
  }
  function table(){
    const table=document.createElement('table'),head=document.createElement('thead'),header=document.createElement('tr');
    for(const name of ['Curve','x / n','y',...(result.curveParameters?['t']:result.surface?['z']:[])]){const th=document.createElement('th');th.textContent=t(name);header.append(th);}head.append(header);table.append(head);const body=document.createElement('tbody');
    (result.curves||result.surface||[]).forEach((curve,i)=>curve.forEach((point,index)=>{if(!point||index%Math.max(1,Math.floor(curve.length/60))!==0)return;const row=document.createElement('tr');for(const number of [i+1,...point,...(result.curveParameters?[result.curveParameters[i]?.[index]]:[])]){const td=document.createElement('td');td.textContent=displayNumber(number,options().displayDigits);row.append(td);}if(!result.surface){row.tabIndex=0;const pick=()=>{trace=point;render();};row.onclick=pick;row.onkeydown=e=>{if(['Enter',' '].includes(e.key)){e.preventDefault();pick();}};}body.append(row);}));table.append(body);$('graph-table').replaceChildren(table);
  }
  function parameterControls(names=[],force=false){
    if(!force&&$('graph-parameters').dataset.names===JSON.stringify(names)){for(const caption of $('graph-parameters').querySelectorAll('[data-parameter]')){caption.textContent=`${caption.dataset.parameter} = ${displayNumber(parameters[caption.dataset.parameter],options().displayDigits)}`;caption.nextElementSibling.value=String(parameters[caption.dataset.parameter]);}return;}
    $('graph-parameters').dataset.names=JSON.stringify(names);
    $('graph-parameters').replaceChildren();$('graph-parameter-actions').hidden=!names.length;
    for(const name of names){if(!(name in parameters))parameters[name]=1;const limits=parameterRanges[name]||[-5,5],label=document.createElement('label'),caption=document.createElement('span'),input=document.createElement('input'),ranges=document.createElement('div'),low=document.createElement('input'),high=document.createElement('input');
      input.type='range';input.min=String(limits[0]);input.max=String(limits[1]);input.step='any';input.value=String(parameters[name]);caption.dataset.parameter=name;caption.textContent=`${name} = ${displayNumber(parameters[name],options().displayDigits)}`;
      input.oninput=()=>{parameters[name]=Number(input.value);caption.textContent=`${name} = ${displayNumber(parameters[name],options().displayDigits)}`;analysis=null;queue();};
      for(const [field,number,labelText] of [[low,limits[0],'Slider minimum'],[high,limits[1],'Slider maximum']]){field.type='number';field.step='any';field.value=String(number);field.setAttribute('aria-label',`${t(labelText)}: ${name}`);field.onchange=()=>{const a=Number(low.value),b=Number(high.value);if(!Number.isFinite(a)||!Number.isFinite(b)||a>=b){onError(t('Enter finite values with minimum < maximum'));return;}parameterRanges[name]=[a,b];parameters[name]=Math.max(a,Math.min(b,parameters[name]));parameterControls(names,true);queue();};}
      ranges.className='form-row';ranges.append(low,high);label.append(caption,input,ranges);$('graph-parameters').append(label);
    }
  }
  async function run(){
    clearTimeout(timer);timer=null;if(isBusy()||!isReady()){pending=true;return;}pending=false;
    try{
      const request=makeRequest(),token=++revision,source=value('graph-source'),graphKind=kind(),view=currentBounds();
      const nextSignature=JSON.stringify([source,graphKind,parameters,derivative]);if(nextSignature!==signature){analysis=null;trace=null;integral=null;analysisRevision++;signature=nextSignature;}
      const response=await execute(request);if(token!==revision||source!==value('graph-source')||graphKind!==kind())return;
      if(!response.ok){onError(response.error);return;}
      result=response;bounds=pending?currentBounds():view;$('graph-status').textContent='';selections();parameterControls(response.parameters);render();persist();
    }catch(error){onError(error.message);}
  }
  function renderAnalysis(){
    const container=$('graph-analysis-result');container.replaceChildren();if(!analysis)return;
    const title=document.createElement('div');title.textContent=t($('graph-analysis-action').selectedOptions[0]?.textContent||'Analyze');container.append(title);
    if(analysis.value!==undefined)container.append(mathDisplay({kind:'number',value:String(analysis.value)},options().displayDigits,true));
    if(analysis.vertical){const note=document.createElement('div');note.textContent=t('Vertical tangent');container.append(note);}
    for(const point of analysis.points||[]){const button=document.createElement('button');button.className='analysis-point';button.append(mathDisplay({kind:'tuple',args:point.map(n=>({kind:'number',value:String(n)}))},options().displayDigits,true));button.onclick=()=>{trace=point;render();};container.append(button);}
    if(!analysis.points?.length&&analysis.value===undefined&&!analysis.vertical){const note=document.createElement('div');note.textContent=t('No points found in this interval');container.append(note);}
  }
  async function analyze(action=value('graph-analysis-action'),point=null){
    if(isBusy()||!isReady())return;
    try{
      const trees=expressions().map(s=>parse(latexInput(s))),a=point??numeric('graph-analysis-a'),b=['derivative','tangent'].includes(action)?a:numeric('graph-analysis-b'),view=bounds||currentBounds(),token=++analysisRevision,source=value('graph-source'),graphKind=kind();
      if(!Number.isFinite(a)||!Number.isFinite(b)||!['derivative','tangent'].includes(action)&&a>=b)throw new Error('Enter finite values with a < b');
      const response=await execute({...options(),angle:'RAD',action:'graphAnalysis',graphKind,trees,analysis:action,a,b,selected:selected(),other:Number(value('graph-other')),variable:graphKind==='cartesian'?'x':'t',parameters,xMin:view.xmin,xMax:view.xmax,yMin:view.ymin,yMax:view.ymax});
      if(token!==analysisRevision||source!==value('graph-source')||graphKind!==kind())return;if(!response.ok){onError(response.error);return;}
      analysis=response;$('graph-status').textContent='';integral=action==='integral'&&graphKind==='cartesian'?[a,b]:null;trace=response.points?.[0]||null;$('graph-analysis-action').value=action;analysisControls();render();
    }catch(error){onError(error.message);}
  }
  function changeView(next,commit=true){if(!Object.values(next).every(Number.isFinite)||next.xmin>=next.xmax||next.ymin>=next.ymax){onError('Enter finite values with minimum < maximum');return;}bounds=next;writeBounds(next);render();if(commit){persist();if(['cartesian','surface','differential'].includes(kind()))queue();}}
  function surfaceChange(dx,dy,zoom){surface.rotation=(surface.rotation+dx*.7+360)%360;surface.elevation=Math.max(5,Math.min(85,surface.elevation+dy*.5));surface.zoom=Math.max(.4,Math.min(3,surface.zoom*zoom));$('graph-rotation').value=String(surface.rotation);$('graph-elevation').value=String(surface.elevation);$('graph-surface-zoom').value=String(surface.zoom);render();}
  const disposeGestures=bindGraphGestures($('graph-plot'),{getBounds:()=>bounds,onView:changeView,onTrace:position=>{const closest=nearestPoint(result,bounds,position,selected());if(closest){trace=closest.point;render();if(analysis?.analysis==='tangent'){ $('graph-analysis-a').value=String(closest.parameter);$('graph-tangent-slider').value=String(closest.parameter);analyze('tangent',closest.parameter);}}},isSurface:()=>kind()==='surface',onSurface:surfaceChange});
  const zoom=z=>{if(kind()==='surface'){surfaceChange(0,0,z);return;}const at={x:.5,y:.5};changeView(transformBounds(bounds||currentBounds(),at,at,z));};
  $('graph-zoom-in').onclick=()=>zoom(2);$('graph-zoom-out').onclick=()=>zoom(.5);
  for(const [id,dx,dy] of [['left',.15,0],['right',-.15,0],['up',0,.15],['down',0,-.15]])$('graph-'+id).onclick=()=>changeView(transformBounds(bounds||currentBounds(),{x:.5,y:.5},{x:.5+dx,y:.5+dy},1));
  $('graph-fit').onclick=()=>{const view=bounds||currentBounds(),curves=derivative!==null?(result?.curves||[]).slice(0,expressions().length):result?.curves||[],ys=curves.flat().filter(p=>p&&p.every(Number.isFinite)&&p[0]>=view.xmin&&p[0]<=view.xmax).map(p=>p[1]);if(ys.length){const low=Math.min(...ys),high=Math.max(...ys),padding=Math.max((high-low)*.12,high===low?1:1e-6);changeView({...view,ymin:low-padding,ymax:high+padding});}};
  $('graph-reset').onclick=()=>{analysis=null;trace=null;integral=null;surface.rotation=35;surface.elevation=32;surface.zoom=1;$('graph-rotation').value='35';$('graph-elevation').value='32';$('graph-surface-zoom').value='1';if(kind()==='sequence'){showNumber('graph-min',0);showNumber('graph-max',20);changeView({xmin:0,xmax:20,ymin:-2,ymax:20});queue();}else if(kind()==='differential'){showNumber('graph-min',-5);showNumber('graph-max',5);changeView({xmin:-5,xmax:5,ymin:-3,ymax:5});}else if(kind()==='surface'){$('graph-zmin').value='';$('graph-zmax').value='';changeView({xmin:-3,xmax:3,ymin:-3,ymax:3});}else changeView({xmin:-10,xmax:10,ymin:-5,ymax:5});};
  $('graph-axis').onclick=()=>{radianAxis=!radianAxis;setText($('graph-axis'),radianAxis?'x: π rad':'x: decimal');render();persist();};
  $('graph-selected').onchange=()=>{analysisRevision++;analysis=null;trace=null;integral=null;if($('graph-derivative').checked){derivative=selected();queue();}render();persist();};
  $('graph-derivative').onchange=()=>{derivative=$('graph-derivative').checked?selected():null;queue();formulas();};
  $('graph-analysis-run').onclick=()=>analyze();$('graph-analysis-clear').onclick=()=>{analysisRevision++;analysis=null;trace=null;integral=null;render();};
  function analysisControls(){const action=value('graph-analysis-action');$('graph-other').closest('label').hidden=action!=='intersection';$('graph-analysis-b').closest('label').hidden=['derivative','tangent'].includes(action);$('graph-tangent-position').hidden=action!=='tangent';$('graph-analysis-a-slider').hidden=action==='tangent';$('graph-analysis-a-value').hidden=action==='tangent';$('graph-tangent-slider').min=String(numeric('graph-min'));$('graph-tangent-slider').max=String(numeric('graph-max'));}
  $('graph-analysis-action').onchange=analysisControls;$('graph-tangent-slider').oninput=()=>{showNumber('graph-analysis-a',Number(value('graph-tangent-slider')));};$('graph-tangent-slider').onchange=()=>analyze('tangent');
  for(const id of ['graph-rotation','graph-elevation','graph-surface-zoom','graph-zmin','graph-zmax'])$(id).oninput=()=>{surface.rotation=Number(value('graph-rotation'));surface.elevation=Number(value('graph-elevation'));surface.zoom=Number(value('graph-surface-zoom'));render();};
  $('graph-reset-parameters').onclick=()=>{for(const name of Object.keys(parameters)){const [a,b]=parameterRanges[name]||[-5,5];parameters[name]=Math.max(a,Math.min(b,1));}parameterControls(Object.keys(parameters),true);queue();};
  $('graph-animate').onclick=()=>{if(animation){clearInterval(animation);animation=null;setText($('graph-animate'),'Animate');return;}let phase=0;setText($('graph-animate'),'Stop');animation=setInterval(()=>{phase+=.2;for(const name of Object.keys(parameters)){const [a,b]=parameterRanges[name]||[-5,5];parameters[name]=a+(b-a)*(Math.sin(phase)+1)/2;}queue();},250);};
  function typeControls(){$('graph-viewport-ranges').hidden=['cartesian','surface'].includes(kind());$('graph-surface-controls').hidden=kind()!=='surface';$('graph-analysis').hidden=!['cartesian','parametric','polar'].includes(kind());$('graph-derivative').closest('label').hidden=kind()!=='cartesian';$('graph-initial').closest('label').hidden=!['sequence','differential'].includes(kind());$('graph-t0').closest('label').hidden=kind()!=='differential';$('graph-analysis-action').querySelector('[value="intersection"]').disabled=kind()!=='cartesian';analysisControls();}
  $('graph-kind').onchange=()=>{
    sourceDrafts[sourceKind]=value('graph-source');sourceKind=kind();$('graph-source').value=sourceDrafts[sourceKind];
    if(animation){clearInterval(animation);animation=null;setText($('graph-animate'),'Animate');}
    const view=kind()==='sequence'?[0,20,-2,20]:kind()==='differential'?[-5,5,-3,5]:kind()==='cartesian'?[-10,10,-5,5]:[-3,3,-3,3];
    writeBounds({xmin:view[0],xmax:view[1],ymin:view[2],ymax:view[3]});
    if(['parametric','polar'].includes(kind())){showNumber('graph-min',0);showNumber('graph-max',2*Math.PI);}
    if(['sequence','differential'].includes(kind())){showNumber('graph-min',view[0]);showNumber('graph-max',view[1]);$('graph-initial').value=kind()==='sequence'?'0,1':'1';}
    parameters={};parameterRanges={};parameterControls([],true);derivative=null;$('graph-derivative').checked=false;result=null;bounds=null;analysis=null;trace=null;integral=null;revision++;analysisRevision++;$('graph-plot').replaceChildren();$('graph-table').replaceChildren();selections();formulas();typeControls();queue();persist();
  };
  $('graph-source').oninput=()=>{derivative=null;$('graph-derivative').checked=false;analysis=null;trace=null;integral=null;revision++;analysisRevision++;selections();formulas();queue();};
  for(const id of ['graph-min','graph-max','graph-ymin','graph-ymax','graph-xmin','graph-xmax','graph-initial','graph-t0'])$(id).onchange=()=>{analysisControls();queue();};
  for(const id of rangeIds){const field=$(id);for(const name of ['input','change'])field.addEventListener(name,()=>{delete field.dataset.displayValue;});}
  for(const id of sliderIds){const field=$(id),slider=document.createElement('input'),output=document.createElement('output'),current=numeric(id);slider.type='range';slider.id=id+'-slider';slider.min=String(Math.min(-30,current-20));slider.max=String(Math.max(30,current+20));slider.step='any';slider.value=String(current);slider.setAttribute('aria-label',field.closest('label').firstChild.textContent.trim());output.id=id+'-value';output.textContent=displayNumber(current,options().displayDigits);field.insertAdjacentElement('afterend',output);output.insertAdjacentElement('afterend',slider);
    slider.oninput=()=>{const number=Number(slider.value),partner=id.endsWith('min')?id.replace(/min$/,'max'):id.endsWith('max')?id.replace(/max$/,'min'):null;let bounded=number;if(partner){const limit=numeric(partner),gap=Math.max(2e-7,Math.abs(limit)*1e-10);bounded=id.endsWith('min')?Math.min(number,limit-gap):Math.max(number,limit+gap);}showNumber(id,bounded);if(!id.startsWith('graph-analysis')){bounds=currentBounds();render();}else renderRangeNumbers();};
    slider.onchange=()=>{persist();if(id.startsWith('graph-analysis'))analysisControls();else queue();};
  }
  for(const [id,number] of Object.entries(saved.ranges||{}))if(rangeIds.includes(id))showNumber(id,number);
  selections();formulas();typeControls();setText($('graph-axis'),radianAxis?'x: π rad':'x: decimal');for(const [id,name] of [['graph-rotation','rotation'],['graph-elevation','elevation'],['graph-surface-zoom','zoom']])$(id).value=String(surface[name]);
  renderRangeNumbers();
  return {run,render,flush,snapshot:()=>({sources:{...sourceDrafts,[kind()]:value('graph-source')},parameters,parameterRanges,radianAxis,surface:{...surface},ranges:Object.fromEntries(rangeIds.filter(id=>value(id)!==''&&Number.isFinite(numeric(id))).map(id=>[id,numeric(id)]))}),updateButtons(){ $('graph-analysis-run').disabled=isBusy()||!isReady();},activate(value){active=value;if(active&&(!result||pending))queue();if(!active){clearTimeout(timer);timer=null;if(animation){clearInterval(animation);animation=null;setText($('graph-animate'),'Animate');}}},dispose(){disposeGestures();clearTimeout(timer);clearInterval(animation);revision++;analysisRevision++;}};
}
