import {downloadFile} from './storage.js';
import {t} from './i18n.js';
import {graphPng} from './graph-export.js';

const NS='http://www.w3.org/2000/svg';

// Resolve the live theme into a standalone vector file, including rotated text
// and local clipping references. No page CSS is required to open the result.
export function standalonePlotSvg(svg,{captions=[],scale=null}={}){
  const document=svg.ownerDocument,window=document.defaultView;
  const copy=svg.cloneNode(true),source=[svg,...svg.querySelectorAll('*')],targets=[copy,...copy.querySelectorAll('*')];
  const style=window.getComputedStyle(svg);
  const properties=['fill','stroke','color','font-family','font-size','font-weight','font-style','text-anchor','stroke-width','stroke-dasharray','stroke-linecap','stroke-linejoin'];
  source.forEach((node,index)=>{
    const computed=window.getComputedStyle(node);
    targets[index].removeAttribute('class');targets[index].removeAttribute('style');
    for(const property of properties){const value=computed.getPropertyValue(property);if(value)targets[index].setAttribute(property,value);}
  });
  const view=(svg.getAttribute('viewBox')||'').trim().split(/[\s,]+/).map(Number);
  if(view.length!==4||!view.every(Number.isFinite)||view[2]<=0||view[3]<=0)throw new Error('Could not export the graph');
  const [x,y,width,chartHeight]=view,height=chartHeight+captions.length*22+(scale?50:0);
  copy.setAttribute('xmlns',NS);copy.setAttribute('width',width);copy.setAttribute('height',height);copy.setAttribute('viewBox',`${x} ${y} ${width} ${height}`);
  const node=(tag,attrs,text='')=>{const element=document.createElementNS(NS,tag);for(const [key,value] of Object.entries(attrs))element.setAttribute(key,String(value));element.textContent=text;return element;};
  const background=style.getPropertyValue('--number').trim()||'#fff',muted=style.getPropertyValue('--muted').trim()||'#738a7c';
  copy.prepend(node('rect',{x,y,width,height,fill:background}));
  captions.forEach((caption,index)=>copy.append(node('text',{x:x+8,y:y+chartHeight+18+index*22,fill:caption.color||muted,'font-family':'sans-serif','font-size':14},caption.text)));
  if(scale){
    const top=y+chartHeight+captions.length*22+6;
    const defs=node('defs',{}),gradient=node('linearGradient',{id:'export-color-scale'});
    for(const [offset,color] of [[0,'rgb(59,112,189)'],[.5,'rgb(241,241,235)'],[1,'rgb(180,55,72)']])gradient.append(node('stop',{offset,'stop-color':scale.color||color}));
    defs.append(gradient);copy.append(defs,node('rect',{x:x+8,y:top,width:Math.max(1,width-16),height:12,fill:'url(#export-color-scale)'}));
    copy.append(node('text',{x:x+8,y:top+33,fill:muted,'font-family':'sans-serif','font-size':14},scale.low),node('text',{x:x+width-8,y:top+33,fill:muted,'text-anchor':'end','font-family':'sans-serif','font-size':14},scale.high));
  }
  return {text:new window.XMLSerializer().serializeToString(copy),width,height};
}

export function pngPlotSize(width,height){
  // Bound the bitmap allocation while keeping the complete figure in view.
  const ratio=Math.min(2,16384/width,16384/height,Math.sqrt(16_000_000/(width*height)));
  return [Math.max(1,Math.round(width*ratio)),Math.max(1,Math.round(height*ratio))];
}

export async function plotSvgPng(snapshot,document){
  const window=document.defaultView,url=window.URL.createObjectURL(new Blob([snapshot.text],{type:'image/svg+xml'}));
  try{
    const image=new window.Image();
    await new Promise((resolve,reject)=>{image.onload=resolve;image.onerror=()=>reject(new Error('Could not export the graph'));image.src=url;});
    const canvas=document.createElement('canvas');[canvas.width,canvas.height]=pngPlotSize(snapshot.width,snapshot.height);
    const context=canvas.getContext('2d');if(!context)throw new Error('Could not export the graph');
    context.drawImage(image,0,0,canvas.width,canvas.height);
    return await graphPng(canvas);
  }finally{window.URL.revokeObjectURL(url);}
}

export function appendPlotExportButtons(svg,name='symvacas-statistics-plot',extras={}){
  const document=svg.ownerDocument,actions=document.createElement('div');actions.className='plot-export-actions';actions.style.textAlign='right';
  const status=document.createElement('span');status.setAttribute('role','status');
  const buttons=['svg','png'].map(format=>{
    const button=document.createElement('button');button.type='button';button.textContent=t(format==='svg'?'Save SVG':'Save PNG');
    button.onclick=async()=>{
      buttons.forEach(button=>{button.disabled=true;});status.textContent='';
      try{
        const snapshot=standalonePlotSvg(svg,typeof extras==='function'?extras():extras);
        const content=format==='svg'?snapshot.text:await plotSvgPng(snapshot,document);
        downloadFile(`${name}.${format}`,content,format==='svg'?'image/svg+xml':'image/png');
      }catch(error){status.textContent=t(error.message||'Could not export the graph');}
      finally{buttons.forEach(button=>{button.disabled=false;});}
    };
    actions.append(button);return button;
  });
  actions.append(status);svg.after(actions);
  return actions;
}
