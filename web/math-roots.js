// Native MathML lays out roots and keeps their accessible semantics. Its glyph
// and overbar can be snapped to different pixels, so paint a connected contour
// in an HTML overlay, outside the MathML layout/baseline coordinate system.
// Chromium's MathMLPainter::PaintBar pixel-snaps bars independently of DrawText.
const SVG='http://www.w3.org/2000/svg';
const tracked=new Map();
let resizeObserver,mutationObserver,scheduled=false;

export function rootPath({left,top,right,bottom},base,fontSize){
  const width=Math.min(.75*fontSize,base.left-left),x=base.left-width;
  const roof=Math.max(top+.022*fontSize,base.top-.12*fontSize),foot=Math.min(bottom-.022*fontSize,base.bottom-.08*fontSize);
  const points=[[x,foot-.25*fontSize],[x+.2*width,foot-.34*fontSize],[x+.44*width,foot],[base.left-.08*fontSize,roof],[right,roof]];
  // Intersect adjacent offset edges to join different weights cleanly. One
  // filled outline avoids the protruding caps of overlapping stroked paths.
  const segments=points.slice(1).map((end,i)=>{
    const dx=end[0]-points[i][0],dy=end[1]-points[i][1],length=Math.hypot(dx,dy);
    const half=.022*fontSize*(i===1?2:1);
    return {direction:[dx/length,dy/length],normal:[-dy/length*half,dx/length*half]};
  });
  const offset=(point,normal,side)=>point.map((value,i)=>value+side*normal[i]);
  const cross=(a,b)=>a[0]*b[1]-a[1]*b[0];
  const edge=side=>points.map((point,i)=>{
    if(i===0)return offset(point,segments[0].normal,side);
    if(i===points.length-1)return offset(point,segments.at(-1).normal,side);
    const previous=segments[i-1],next=segments[i];
    const a=offset(point,previous.normal,side),b=offset(point,next.normal,side);
    const turn=cross(previous.direction,next.direction);
    if(Math.abs(turn)<1e-8)return a.map((value,j)=>(value+b[j])/2);
    const distance=cross(b.map((value,j)=>value-a[j]),next.direction)/turn;
    return a.map((value,j)=>value+distance*previous.direction[j]);
  });
  const outline=[...edge(1),...edge(-1).reverse()];
  return outline.map(([x,y],i)=>`${i?'L':'M'} ${x} ${y}`).join(' ')+' Z';
}

export function paintMathRoots(math){
  const document=math.ownerDocument;
  const roots=[...math.querySelectorAll('msqrt,mroot')];
  if(!math.isConnected||!roots.length)return null;
  const rect=math.getBoundingClientRect();
  if(!rect.width||!rect.height)return null; // Hidden panels keep native paint.
  let frame=math.parentElement;
  if(!frame?.classList.contains('math-frame')&&!frame?.classList.contains('input-math')){
    frame=document.createElement('span');frame.className='math-frame';
    math.replaceWith(frame);frame.append(math);
  }
  frame.classList.add('math-frame');
  const view=math.ownerDocument.defaultView,origin=frame.getBoundingClientRect();
  frame.style.setProperty('--math-ink',view.getComputedStyle(math).color);
  let overlay=[...frame.children].find(node=>node.classList.contains('math-root-overlay'));
  if(!overlay){
    overlay=document.createElementNS(SVG,'svg');overlay.classList.add('math-root-overlay');
    overlay.setAttribute('aria-hidden','true');overlay.setAttribute('focusable','false');frame.append(overlay);
  }
  // Explicit dimensions prevent an intrinsic SVG size from enlarging formulas.
  overlay.setAttribute('width',String(origin.width));overlay.setAttribute('height',String(origin.height));
  overlay.setAttribute('viewBox',`0 0 ${origin.width} ${origin.height}`);
  const relative=box=>({left:box.left-origin.left,top:box.top-origin.top,right:box.right-origin.left,bottom:box.bottom-origin.top});
  const paths=[];
  for(const root of roots){
    const base=root.firstElementChild,box=base.getBoundingClientRect(),fontSize=parseFloat(view.getComputedStyle(root).fontSize);
    if(!box.width||!box.height||!Number.isFinite(fontSize)){root.classList.remove('math-root-painted');continue;}
    const path=document.createElementNS(SVG,'path');
    path.setAttribute('d',rootPath(relative(root.getBoundingClientRect()),relative(box),fontSize));
    path.setAttribute('fill','currentColor');path.setAttribute('stroke','none');
    paths.push(path);
    root.classList.add('math-root-painted');
  }
  overlay.replaceChildren(...paths);
  return overlay;
}

function refresh(){
  scheduled=false;
  for(const [math,roots] of tracked){
    if(!math.isConnected){resizeObserver?.unobserve(math);for(const root of roots)resizeObserver?.unobserve(root);tracked.delete(math);}
    else paintMathRoots(math);
  }
  if(!tracked.size){mutationObserver?.disconnect();mutationObserver=null;}
}
function schedule(){
  if(scheduled)return;scheduled=true;
  (globalThis.requestAnimationFrame||((callback)=>setTimeout(callback,0)))(refresh);
}
export function trackMathRoots(math){
  if(!math.querySelector('msqrt,mroot'))return;
  queueMicrotask(()=>{
    if(!math.isConnected)return;
    paintMathRoots(math);
    const view=math.ownerDocument.defaultView;
    // jsdom has no layout; keep the native fallback there and in older clients.
    if(!view.ResizeObserver)return;
    if(!resizeObserver)resizeObserver=new view.ResizeObserver(schedule);
    const roots=[...math.querySelectorAll('msqrt,mroot')];tracked.set(math,roots);
    resizeObserver.observe(math);for(const root of roots)resizeObserver.observe(root);
    if(!mutationObserver){
      mutationObserver=new view.MutationObserver(records=>{
        if(records.some(record=>[...record.removedNodes].some(node=>node.nodeType===1&&(node.localName==='math'||node.querySelector('math')))))schedule();
      });
      mutationObserver.observe(math.ownerDocument.body,{childList:true,subtree:true});
    }
  });
}
