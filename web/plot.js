// SVG only: no network chart library, and discontinuities retain null breaks.
const NS = 'http://www.w3.org/2000/svg';
const colors = ['#007b68','#a04c75','#3b70bd','#b17d00','#6b5ec2','#a34629'];
function svgElement(tag,attributes={},text='') {
  const el = document.createElementNS(NS,tag);
  for (const [key,value] of Object.entries(attributes)) el.setAttribute(key,String(value));
  if (text) el.textContent = text;
  return el;
}
export function plot(container,result,bounds,{dots=false}={}) {
  const {xmin,xmax,ymin,ymax} = bounds;
  if (![xmin,xmax,ymin,ymax].every(Number.isFinite) || xmax<=xmin || ymax<=ymin) throw new Error('그래프 범위를 확인해 주세요.');
  const w=800,h=460,pad=42,innerW=w-2*pad,innerH=h-2*pad;
  const svg = svgElement('svg',{viewBox:`0 0 ${w} ${h}`,role:'img','aria-label':result.surface ? '3D surface graph' : 'Function graph'});
  const defs = svgElement('defs'), clip = svgElement('clipPath',{id:`clip-${container.id}`});
  clip.append(svgElement('rect',{x:pad,y:pad,width:innerW,height:innerH})); defs.append(clip); svg.append(defs);
  const x = v => pad+(v-xmin)/(xmax-xmin)*innerW, y = v => h-pad-(v-ymin)/(ymax-ymin)*innerH;
  const group = svgElement('g',{'clip-path':`url(#clip-${container.id})`});
  for(let i=0;i<=10;i++) {
    const at=pad+innerW*i/10;
    svg.append(svgElement('line',{x1:at,y1:pad,x2:at,y2:h-pad,stroke:'#a4b8ab',opacity:.25}));
    if (i%2===0) svg.append(svgElement('text',{x:at,y:h-14,fill:'#738a7c','font-size':12,'text-anchor':'middle'},Number(xmin+(xmax-xmin)*i/10).toPrecision(3)));
  }
  for(let i=0;i<=8;i++) {
    const at=pad+innerH*i/8;
    svg.append(svgElement('line',{x1:pad,y1:at,x2:w-pad,y2:at,stroke:'#a4b8ab',opacity:.25}));
    if(i%2===0) svg.append(svgElement('text',{x:pad-8,y:at+4,fill:'#738a7c','font-size':12,'text-anchor':'end'},Number(ymax-(ymax-ymin)*i/8).toPrecision(3)));
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
      if(dots) group.append(svgElement('circle',{cx:xx,cy:yy,r:3,fill:color}));
    }
    group.append(svgElement('path',{d,fill:'none',stroke:color,'stroke-width':2,'data-curve':index}));
  }
  for(const shade of result.shadings || []) for(const points of shade.fill || []) {
    group.append(svgElement('polygon',{points:points.map(p=>`${x(p[0])},${y(p[1])}`).join(' '),fill:colors[0],opacity:.16}));
  }
  for(const [at,value,slope] of result.fields || []) {
    const angle=Math.atan(slope*(xmax-xmin)/(ymax-ymin)*innerH/innerW),dx=7*Math.cos(angle),dy=-7*Math.sin(angle);
    group.append(svgElement('line',{x1:x(at)-dx,y1:y(value)-dy,x2:x(at)+dx,y2:y(value)+dy,stroke:'#738a7c',opacity:.6}));
  }
  if(result.surface) {
    const zspan = result.zMax-result.zMin || 1;
    const project = ([xx,yy,zz]) => {
      const nx=(xx-xmin)/(xmax-xmin)-.5,ny=(yy-ymin)/(ymax-ymin)-.5,nz=(zz-result.zMin)/zspan-.5;
      return [w/2+(nx-ny)*innerW*.42,h/2+(nx+ny)*innerH*.2-nz*innerH*.65];
    };
    for(const row of result.surface) path(row,colors[0],0,project);
    for(let i=0;i<result.surface[0].length;i++) path(result.surface.map(row=>row[i]),colors[2],1,project);
  } else (result.curves || []).forEach((curve,i)=>path(curve,colors[i%colors.length],i));
  svg.append(group); container.replaceChildren(svg);
}
export function dataBounds(points) {
  const good=points.filter(p=>p && p.every(Number.isFinite));
  const xs=good.map(p=>p[0]),ys=good.map(p=>p[1]);
  function span(values) { let low=Math.min(...values),high=Math.max(...values); if(low===high){low--;high++;} const pad=(high-low)*.1; return [low-pad,high+pad]; }
  const [xmin,xmax]=span(xs),[ymin,ymax]=span(ys); return {xmin,xmax,ymin,ymax};
}
