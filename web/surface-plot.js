import {displayNumber} from './display-format.js';
import {clipSurfaceSegment,surfaceFaces,surfaceProjection,surfaceZRange} from './surface-geometry.js';
const NS='http://www.w3.org/2000/svg';
function element(tag,attributes={},text=''){
  const node=document.createElementNS(NS,tag);
  for(const [name,value] of Object.entries(attributes))node.setAttribute(name,String(value));
  node.textContent=text;return node;
}
export function plotSurface(container,result,view,{digits=10,rotation=35,elevation=32,zoom=1,renderMode='surface',color='#007b68'}={}){
  if(!/^#[0-9a-f]{6}$/i.test(color))color='#007b68';
  const rgb=[1,3,5].map(index=>parseInt(color.slice(index,index+2),16));
  const [zmin,zmax]=surfaceZRange(result.zMin,result.zMax),bounds={...view,zmin,zmax},w=800,h=460,pad=42,scale=(h-2*pad)*.3*zoom;
  const projection=surfaceProjection(bounds,rotation,elevation),project=point=>{const [x,y]=projection.project(point);return [w/2+x*scale,h/2+y*scale];};
  const svg=element('svg',{viewBox:`0 0 ${w} ${h}`,role:'img','aria-label':'3D surface graph','data-render-mode':renderMode});
  const defs=element('defs'),clip=element('clipPath',{id:`clip-${container.id}`});clip.append(element('rect',{x:pad,y:pad,width:w-2*pad,height:h-2*pad}));defs.append(clip);svg.append(defs);
  const group=element('g',{'clip-path':`url(#clip-${container.id})`});svg.append(group);
  if(renderMode!=='wireframe')for(const face of surfaceFaces(result.surface,bounds,projection)){
    const shaded=rgb.map(v=>Math.round(v*(.65+.35*face.height)*face.light)),fill=`rgb(${shaded.join(',')})`;
    const polygon=element('polygon',{points:face.points.map(project).map(p=>p.join(',')).join(' '),fill,'data-surface-face':'true','data-depth':face.depth});
    // Outlines are painted with each face so rear grid lines cannot show through.
    polygon.setAttribute('stroke',renderMode==='surface-wireframe'?'var(--muted)':fill);
    polygon.setAttribute('stroke-width',renderMode==='surface-wireframe'?.65:.35);polygon.setAttribute('stroke-linejoin','round');group.append(polygon);
  }
  if(renderMode==='wireframe'){
    const path=(points,color,index)=>{
      let d='';
      for(let i=1;i<points.length;i++){
        const segment=clipSurfaceSegment(points[i-1],points[i],bounds);if(!segment)continue;
        const [a,b]=segment.map(project);d+=`M${a[0].toFixed(2)},${a[1].toFixed(2)}L${b[0].toFixed(2)},${b[1].toFixed(2)}`;
      }
      group.append(element('path',{d,fill:'none',stroke:color,'stroke-width':1.3,'data-curve':index}));
    };
    for(const row of result.surface)path(row,color,0);
    const columns=Math.max(0,...result.surface.map(row=>row.length));
    for(let col=0;col<columns;col++)path(result.surface.map(row=>row[col]),color,1);
  }
  const origin=[bounds.xmin,bounds.ymin,zmin],axes=element('g',{'data-surface-axes':'true'});svg.append(axes);
  for(const [index,name] of ['x','y','z'].entries()){
    const min=origin[index],max=[bounds.xmax,bounds.ymax,zmax][index],end=[...origin];end[index]=max;
    const [a,b]=[origin,end].map(project);
    axes.append(element('line',{x1:a[0],y1:a[1],x2:b[0],y2:b[1],stroke:'var(--muted)','stroke-width':2,'data-surface-axis':name}));
    const span=max-min,raw=span/4,power=10**Math.floor(Math.log10(raw)),fraction=raw/power,step=power*(fraction>5?10:fraction>2?5:fraction>1?2:1),first=Math.ceil(min/step);
    for(let i=0;i<12;i++){
      const number=(first+i)*step;if(number>max+span*1e-10)break;
      const point=[...origin];point[index]=number;const [x,y]=project(point);
      axes.append(element('circle',{cx:x,cy:y,r:1.5,fill:'var(--muted)'}));
      axes.append(element('text',{x:x+4,y:y+(index===2?-5:15),fill:'var(--muted)','font-size':12,'data-axis':name},displayNumber(Math.abs(number)<step*1e-10?0:number,digits)));
    }
    axes.append(element('text',{x:b[0]+8,y:b[1]+4,fill:'var(--ink)','font-size':14,'font-weight':'bold','data-surface-axis-label':name},name));
  }
  container.replaceChildren(svg);return svg;
}
