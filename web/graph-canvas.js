import {displayNumber} from './display-format.js';
import {clipSurfaceSegment,surfaceFaces,surfaceProjection,surfaceZRange} from './surface-geometry.js';

const colors=['#007b68','#a04c75','#3b70bd','#b17d00','#6b5ec2','#a34629'];
const w=800,pad=42;
const finite=point=>point&&point.every(Number.isFinite);

// One persistent bitmap per workspace, with a backing store sized for its
// actual CSS width and device pixel ratio. Geometry uses the gesture viewBox.
export function plotGraph(container,result,bounds,{digits=10,dots=false,selected=0,analysis=null,trace=null,integral=null,radianAxis=false,halfHeight=false,heightScale=halfHeight?.5:1,surfaceView={}}={}){
  const scale=[1,.5,2].includes(heightScale)?heightScale:1;
  const h=460*scale,iw=w-2*pad,ih=h-2*pad;
  const {xmin,xmax,ymin,ymax}=bounds;
  if(![xmin,xmax,ymin,ymax].every(Number.isFinite)||xmax<=xmin||ymax<=ymin)throw new Error('Enter finite values with minimum < maximum');
  let canvas=container.querySelector('canvas');
  if(!canvas){canvas=document.createElement('canvas');canvas.setAttribute('role','img');container.replaceChildren(canvas);}
  canvas.setAttribute('aria-label',result.surface?'3D surface graph':'Function graph');
  canvas.dataset.renderMode=result.surface?(surfaceView.renderMode||'wireframe'):'curves';
  canvas.dataset.halfHeight=String(scale===.5);canvas.dataset.heightScale=String(scale);canvas.dataset.plotHeight=String(h);canvas.style.aspectRatio=`${w}/${h}`;
  const window=container.ownerDocument.defaultView,ratio=window.devicePixelRatio||1;
  const cssWidth=canvas.getBoundingClientRect().width||container.getBoundingClientRect().width||w;
  const width=Math.max(1,Math.round(cssWidth*ratio)),height=Math.max(1,Math.round(cssWidth*h/w*ratio));
  if(canvas.width!==width||canvas.height!==height){canvas.width=width;canvas.height=height;}
  const ctx=canvas.getContext('2d');if(!ctx)return canvas;
  ctx.setTransform(canvas.width/w,0,0,canvas.height/h,0,0);ctx.clearRect(0,0,w,h);
  const style=window.getComputedStyle(container),muted=style.getPropertyValue('--muted').trim()||'#738a7c',ink=style.getPropertyValue('--ink').trim()||'#20392f',accent=style.getPropertyValue('--accent').trim()||colors[0];
  ctx.lineJoin='round';ctx.lineCap='round';ctx.font='12px system-ui';ctx.fillStyle=muted;
  const line=(a,b,color=muted,width=1)=>{ctx.beginPath();ctx.moveTo(...a);ctx.lineTo(...b);ctx.strokeStyle=color;ctx.lineWidth=width;ctx.stroke();};
  const circle=(point,radius,color)=>{ctx.beginPath();ctx.arc(...point,radius,0,2*Math.PI);ctx.fillStyle=color;ctx.fill();};
  const polygon=(points,color,alpha=1,stroke=null,width=1)=>{
    if(points.length<3||!points.every(finite))return;
    ctx.beginPath();ctx.moveTo(...points[0]);for(const point of points.slice(1))ctx.lineTo(...point);ctx.closePath();
    ctx.globalAlpha=alpha;ctx.fillStyle=color;ctx.fill();if(stroke){ctx.strokeStyle=stroke;ctx.lineWidth=width;ctx.stroke();}ctx.globalAlpha=1;
  };
  const clip=()=>{ctx.save();ctx.beginPath();ctx.rect(pad,pad,iw,ih);ctx.clip();};
  if(result.surface){
    const {rotation=35,elevation=32,zoom=1,renderMode='wireframe'}=surfaceView;
    const color=/^#[0-9a-f]{6}$/i.test(surfaceView.color||'')?surfaceView.color:colors[0],rgb=[1,3,5].map(i=>parseInt(color.slice(i,i+2),16));
    const [zmin,zmax]=surfaceZRange(result.zMin,result.zMax),box={...bounds,zmin,zmax},projection=surfaceProjection(box,rotation,elevation),scale=ih*.3*zoom;
    const project=p=>{const [x,y]=projection.project(p);return [w/2+x*scale,h/2+y*scale];};
    clip();
    if(renderMode!=='wireframe')for(const face of surfaceFaces(result.surface,box,projection)){
      const fill=`rgb(${rgb.map(v=>Math.round(v*(.65+.35*face.height)*face.light)).join(',')})`;
      polygon(face.points.map(project),fill,1,renderMode==='surface-wireframe'?muted:fill,renderMode==='surface-wireframe'?.65:.35);
    }
    else{
      ctx.beginPath();
      const wire=points=>{for(let i=1;i<points.length;i++){const segment=clipSurfaceSegment(points[i-1],points[i],box);if(segment){ctx.moveTo(...project(segment[0]));ctx.lineTo(...project(segment[1]));}}};
      for(const row of result.surface)wire(row);
      const columns=Math.max(0,...result.surface.map(row=>row.length));
      for(let col=0;col<columns;col++)wire(result.surface.map(row=>row[col]));
      ctx.strokeStyle=color;ctx.lineWidth=1.3;ctx.stroke();
    }
    ctx.restore();
    const origin=[xmin,ymin,zmin];
    for(const [index,name] of ['x','y','z'].entries()){
      const min=origin[index],max=[xmax,ymax,zmax][index],end=[...origin];end[index]=max;
      line(project(origin),project(end),muted,2);
      const raw=(max-min)/4,power=10**Math.floor(Math.log10(raw)),fraction=raw/power,step=power*(fraction>5?10:fraction>2?5:fraction>1?2:1),first=Math.ceil(min/step);
      ctx.textAlign='left';
      for(let i=0;i<12;i++){
        const number=(first+i)*step;if(number>max+(max-min)*1e-10)break;
        const point=[...origin];point[index]=number;const at=project(point);circle(at,1.5,muted);ctx.fillText(displayNumber(Math.abs(number)<step*1e-10?0:number,digits),at[0]+4,at[1]+(index===2?-5:15));
      }
      const at=project(end);ctx.fillStyle=ink;ctx.fillText(name,at[0]+8,at[1]+4);ctx.fillStyle=muted;
    }
    return canvas;
  }
  const x=v=>pad+(v-xmin)/(xmax-xmin)*iw,y=v=>h-pad-(v-ymin)/(ymax-ymin)*ih,project=p=>[x(p[0]),y(p[1])];
  let ticks=Array.from({length:11},(_,i)=>xmin+(xmax-xmin)*i/10);
  if(radianAxis){const step=Math.PI*2**Math.ceil(Math.log2((xmax-xmin)/Math.PI/8)),first=Math.ceil(xmin/step);ticks=Array.from({length:Math.min(9,Math.max(0,Math.floor(xmax/step)-first+1))},(_,i)=>(first+i)*step);}
  ctx.textAlign='center';
  for(const [i,n] of ticks.entries()){
    ctx.globalAlpha=.25;line([x(n),pad],[x(n),h-pad],'#a4b8ab');ctx.globalAlpha=1;
    if(radianAxis||i%2===0)ctx.fillText(radianAxis?piLabel(n,digits):displayNumber(n,digits),x(n),h-14);
  }
  ctx.textAlign='right';
  for(let i=0;i<=8;i++){const at=pad+ih*i/8;ctx.globalAlpha=.25;line([pad,at],[w-pad,at],'#a4b8ab');ctx.globalAlpha=1;if(i%2===0)ctx.fillText(displayNumber(ymax-(ymax-ymin)*i/8,digits),pad-8,at+4);}
  clip();
  if(xmin<=0&&xmax>=0)line([x(0),pad],[x(0),h-pad]);
  if(ymin<=0&&ymax>=0)line([pad,y(0)],[w-pad,y(0)]);
  for(const [i,shade] of (result.shadings||[]).entries())for(const points of shade.fill||[])polygon(points.map(project),colors[i%colors.length],.16);
  ctx.beginPath();for(const [at,value,slope] of result.fields||[]){const angle=Math.atan(slope*(xmax-xmin)/(ymax-ymin)*ih/iw),dx=7*Math.cos(angle),dy=-7*Math.sin(angle);ctx.moveTo(x(at)-dx,y(value)-dy);ctx.lineTo(x(at)+dx,y(value)+dy);}ctx.strokeStyle=muted;ctx.globalAlpha=.6;ctx.lineWidth=1;ctx.stroke();ctx.globalAlpha=1;
  const path=(points,i)=>{
    const color=colors[i%colors.length];ctx.beginPath();let pen=false;
    for(const point of points){if(!finite(point)){pen=false;continue;}const at=project(point);if(!at.every(Number.isFinite)){pen=false;continue;}if(pen)ctx.lineTo(...at);else ctx.moveTo(...at);pen=true;}
    ctx.strokeStyle=color;ctx.lineWidth=i===selected?4:2;ctx.stroke();
    if(dots)for(const point of points)if(finite(point))circle(project(point),3,color);
  };
  (result.curves||[]).forEach((curve,i)=>{if(i!==selected)path(curve,i);});if(result.curves?.[selected])path(result.curves[selected],selected);
  if(integral){let segment=[];const flush=()=>{if(segment.length>1)polygon([[segment[0][0],0],...segment,[segment.at(-1)[0],0]].map(project),colors[selected%colors.length],.18);segment=[];};for(const point of result.curves?.[selected]||[]){if(point&&point[0]>=integral[0]&&point[0]<=integral[1])segment.push(point);else flush();}flush();}
  if(analysis?.line?.length===2){ctx.setLineDash([6,4]);line(...analysis.line.map(project),accent,2);ctx.setLineDash([]);}
  for(const point of analysis?.points||[])if(finite(point))circle(project(point),5,accent);
  if(finite(trace)){ctx.setLineDash([3,3]);line([x(trace[0]),pad],[x(trace[0]),h-pad]);ctx.setLineDash([]);circle(project(trace),6,accent);}
  ctx.restore();return canvas;
}

function piLabel(value,digits){
  const ratio=value/Math.PI;if(!ratio)return '0';
  for(const denominator of [1,2,3,4,6,8,12,16,32,64,128,256,512,1024]){const numerator=Math.round(ratio*denominator);if(numerator&&Math.abs(ratio-numerator/denominator)<1e-9)return `${numerator===1?'':numerator===-1?'-':numerator}π${denominator===1?'':'/'+denominator}`;}
  return displayNumber(ratio,digits)+'π';
}
