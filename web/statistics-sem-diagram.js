import {t} from './i18n.js';

const ns='http://www.w3.org/2000/svg';
const number=value=>Number(value).toLocaleString(undefined,{maximumSignificantDigits:3});

export function semDiagramSize(plot,{fitToScreen=false,viewportWidth=plot.width,viewportHeight=plot.height}={}){
  const scale=fitToScreen?Math.min(1,Math.max(1,viewportWidth)/plot.width,Math.max(1,viewportHeight)/plot.height):1;
  return {width:plot.width*scale,height:plot.height*scale};
}

export function semDiagramSvg(plot,options){
  const svg=document.createElementNS(ns,'svg');
  svg.setAttribute('viewBox',`0 0 ${plot.width} ${plot.height}`);svg.setAttribute('role','img');
  svg.setAttribute('aria-label',t(plot.title));
  const size=semDiagramSize(plot,options);
  svg.style.cssText=`width:${size.width}px;height:${size.height}px;max-width:none;display:block;background:var(--number);border-radius:8px`;
  const shape=(tag,attrs,text='',parent=svg)=>{
    const node=document.createElementNS(ns,tag);for(const [key,value] of Object.entries(attrs))node.setAttribute(key,String(value));
    node.textContent=text;parent.append(node);return node;
  };
  const label=(x,y,text,color='var(--ink)',size=12)=>shape('text',{x,y,fill:color,'font-size':size,'text-anchor':'middle'},text);
  const arrow=(tip,control,color)=>{
    const angle=Math.atan2(tip[1]-control[1],tip[0]-control[0]);
    const a=[tip[0]-9*Math.cos(angle-.4),tip[1]-9*Math.sin(angle-.4)],b=[tip[0]-9*Math.cos(angle+.4),tip[1]-9*Math.sin(angle+.4)];
    shape('polygon',{points:[tip,a,b].map(point=>point.join(',')).join(' '),fill:color});
  };
  for(const edge of plot.edges){
    const {start,end,controls}=edge,color=edge.kind==='loading'?'var(--accent)':'var(--ink)';
    const path=shape('path',{d:`M ${start.join(' ')} C ${controls[0].join(' ')} ${controls[1].join(' ')} ${end.join(' ')}`,fill:'none',stroke:color,'stroke-width':1.8,...(edge.kind==='covariance'?{'stroke-dasharray':'5 3'}:{})});
    const title=`${edge.source} ${edge.kind==='covariance'?'↔':'→'} ${edge.target}: ${number(edge.estimate)}${edge.interval?` [${edge.interval.map(number).join(', ')}]`:''}`;
    shape('title',{},title,path);arrow(end,controls[1],color);if(edge.kind==='covariance')arrow(start,controls[0],color);
  }
  for(const edge of plot.edges){
    const {labelPosition}=edge,color=edge.kind==='loading'?'var(--accent)':'var(--ink)';
    shape('rect',{x:labelPosition[0]-59,y:labelPosition[1]-22,width:118,height:edge.interval?38:24,rx:4,fill:'var(--number)',opacity:.95});
    label(labelPosition[0],labelPosition[1]-6,number(edge.estimate),color);
    if(edge.interval)label(labelPosition[0],labelPosition[1]+10,`[${edge.interval.map(number).join(', ')}]`,'var(--muted)',11);
  }
  for(const node of plot.nodes){
    const attrs={fill:'var(--number)',stroke:'var(--ink)','stroke-width':1.6};
    const drawn=node.kind==='latent'?shape('ellipse',{cx:node.x,cy:node.y,rx:70,ry:37,...attrs}):shape('rect',{x:node.x-70,y:node.y-25,width:140,height:50,rx:3,...attrs});
    const name=node.kind==='latent'?node.label.replace('Factor',t('Factor')):node.label.replace(/^Feature /,`${t('Feature')} `);
    const hasR2=Number.isFinite(node.r2);
    shape('title',{},name,drawn);label(node.x,node.y+(node.kind==='latent'||hasR2?-6:4),name.length>18?name.slice(0,17)+'…':name);
    if(node.kind==='latent'||hasR2)label(node.x,node.y+13,hasR2?`R² = ${number(node.r2)}`:t('Exogenous'),'var(--muted)',11);
  }
  return svg;
}

