// SVG only: no network chart library, and discontinuities retain null breaks.
import {displayNumber} from './display-format.js';
import {integralPolygons} from './graph-integral.js';
import {plotSurface} from './surface-plot.js';
const NS = 'http://www.w3.org/2000/svg';
const colors = ['#007b68','#a04c75','#3b70bd','#b17d00','#6b5ec2','#a34629'];
function svgElement(tag,attributes={},text='') {
  const el = document.createElementNS(NS,tag);
  for (const [key,value] of Object.entries(attributes)) el.setAttribute(key,String(value));
  if (text) el.textContent = text;
  return el;
}
export function plot(container,result,bounds,{dots=false,scatterCurves=[],digits=10,analysis=null,trace=null,selected=0,integral=null,radianAxis=false,surfaceView={rotation:35,elevation:32,zoom:1}}={}) {
  const {xmin,xmax,ymin,ymax} = bounds;
  if (![xmin,xmax,ymin,ymax].every(Number.isFinite) || xmax<=xmin || ymax<=ymin) throw new Error('그래프 범위를 확인해 주세요.');
  if(result.surface)return plotSurface(container,result,bounds,{digits,...surfaceView});
  const w=800,h=460,pad=42,innerW=w-2*pad,innerH=h-2*pad;
  const svg = svgElement('svg',{viewBox:`0 0 ${w} ${h}`,role:'img','aria-label':result.surface ? '3D surface graph' : 'Function graph'});
  const defs = svgElement('defs'), clip = svgElement('clipPath',{id:`clip-${container.id}`});
  clip.append(svgElement('rect',{x:pad,y:pad,width:innerW,height:innerH})); defs.append(clip); svg.append(defs);
  const x = v => pad+(v-xmin)/(xmax-xmin)*innerW, y = v => h-pad-(v-ymin)/(ymax-ymin)*innerH;
  const group = svgElement('g',{'clip-path':`url(#clip-${container.id})`});
  const xTicks=radianAxis?piTicks(xmin,xmax):Array.from({length:11},(_,i)=>xmin+(xmax-xmin)*i/10);
  for(const [i,number] of xTicks.entries()) {
    const at=x(number);
    svg.append(svgElement('line',{x1:at,y1:pad,x2:at,y2:h-pad,stroke:'#a4b8ab',opacity:.25}));
    if (radianAxis||i%2===0) svg.append(svgElement('text',{x:at,y:h-14,fill:'var(--muted)','font-size':12,'text-anchor':'middle','data-axis':'x'},radianAxis?piLabel(number,digits):displayNumber(number,digits)));
  }
  for(let i=0;i<=8;i++) {
    const at=pad+innerH*i/8;
    svg.append(svgElement('line',{x1:pad,y1:at,x2:w-pad,y2:at,stroke:'#a4b8ab',opacity:.25}));
    if(i%2===0) svg.append(svgElement('text',{x:pad-8,y:at+4,fill:'var(--muted)','font-size':12,'text-anchor':'end'},displayNumber(ymax-(ymax-ymin)*i/8,digits)));
  }
  if (xmin<=0 && xmax>=0) group.append(svgElement('line',{x1:x(0),y1:pad,x2:x(0),y2:h-pad,stroke:'#738a7c'}));
  if (ymin<=0 && ymax>=0) group.append(svgElement('line',{x1:pad,y1:y(0),x2:w-pad,y2:y(0),stroke:'#738a7c'}));
  function path(points,color,index=0,projection=null) {
    let d='',pen=false;
    for(const point of points) {
      if(!point || !point.every(Number.isFinite)) { pen=false; continue; }
      const [xx,yy] = projection ? projection(point) : [x(point[0]),y(point[1])];
      if(!Number.isFinite(xx) || !Number.isFinite(yy)) { pen=false; continue; }
      d+=(pen?'L':'M')+xx.toFixed(2)+','+yy.toFixed(2); pen=true;
      if(dots || scatterCurves.includes(index)) group.append(svgElement('circle',{cx:xx,cy:yy,r:3,fill:color}));
    }
    if(!scatterCurves.includes(index)) group.append(svgElement('path',{d,fill:'none',stroke:color,'stroke-width':!result.surface&&index===selected?4:2,'data-curve':index,'data-selected':!result.surface&&index===selected,'stroke-linejoin':'round','stroke-linecap':'round'}));
  }
  for(const shade of result.shadings || []) for(const points of shade.fill || []) {
    group.append(svgElement('polygon',{points:points.map(p=>`${x(p[0])},${y(p[1])}`).join(' '),fill:colors[0],opacity:.16}));
  }
  for(const [at,value,slope] of result.fields || []) {
    const angle=Math.atan(slope*(xmax-xmin)/(ymax-ymin)*innerH/innerW),dx=7*Math.cos(angle),dy=-7*Math.sin(angle);
    group.append(svgElement('line',{x1:x(at)-dx,y1:y(value)-dy,x2:x(at)+dx,y2:y(value)+dy,stroke:'#738a7c',opacity:.6}));
  }
  (result.curves || []).forEach((curve,i)=>{if(i!==selected)path(curve,colors[i%colors.length],i);});
  if(result.curves?.[selected])path(result.curves[selected],colors[selected%colors.length],selected);
  if(!result.surface){
    if(integral)for(const points of analysis?.integralFill||integralPolygons(result.curves?.[selected]||[],integral))group.append(svgElement('polygon',{points:points.map(p=>`${x(p[0])},${y(p[1])}`).join(' '),fill:colors[selected%colors.length],opacity:.18,'data-integral':'true'}));
    if(analysis?.line?.length===2){const line=analysis.line;group.append(svgElement('line',{x1:x(line[0][0]),y1:y(line[0][1]),x2:x(line[1][0]),y2:y(line[1][1]),stroke:'var(--accent)','stroke-width':2,'stroke-dasharray':'6 4','data-tangent':'true'}));}
    for(const point of analysis?.points||[])group.append(svgElement('circle',{cx:x(point[0]),cy:y(point[1]),r:5,fill:'var(--accent)','data-analysis-point':'true'}));
    if(trace){group.append(svgElement('line',{x1:x(trace[0]),x2:x(trace[0]),y1:pad,y2:h-pad,stroke:'var(--muted)','stroke-dasharray':'3 3'}),svgElement('circle',{cx:x(trace[0]),cy:y(trace[1]),r:6,fill:colors[selected%colors.length],'data-trace':'true'}));}
  }
  svg.append(group); container.replaceChildren(svg);return svg;
}
function piTicks(min,max){
  const step=Math.PI*2**Math.ceil(Math.log2((max-min)/Math.PI/8)),first=Math.ceil(min/step),last=Math.floor(max/step);
  return Array.from({length:Math.min(9,Math.max(0,last-first+1))},(_,i)=>(first+i)*step);
}
function piLabel(value,digits){const ratio=value/Math.PI;if(!ratio)return '0';for(const denominator of [1,2,3,4,6,8,12,16,32,64,128,256,512,1024]){const numerator=Math.round(ratio*denominator);if(numerator&&Math.abs(ratio-numerator/denominator)<1e-9){return `${numerator===1?'':numerator===-1?'-':numerator}π${denominator===1?'':'/'+denominator}`;}}return displayNumber(ratio,digits)+'π';}
export function dataBounds(points) {
  const good=points.filter(p=>p && p.every(Number.isFinite));
  const xs=good.map(p=>p[0]),ys=good.map(p=>p[1]);
  function span(values) { let low=Math.min(...values),high=Math.max(...values); if(low===high){low--;high++;} const pad=(high-low)*.1; return [low-pad,high+pad]; }
  const [xmin,xmax]=span(xs),[ymin,ymax]=span(ys); return {xmin,xmax,ymin,ymax};
}
