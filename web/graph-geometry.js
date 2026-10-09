// Clip in data coordinates before projecting: Canvas must never receive the
// enormous pixel coordinates produced by exponential curves outside the view.
export function clipGraphSegment(first,last,{xmin,xmax,ymin,ymax}){
  if(!first||!last||![...first,...last].every(Number.isFinite))return null;
  const code=p=>(p[0]<xmin?1:p[0]>xmax?2:0)|(p[1]<ymin?4:p[1]>ymax?8:0);
  let a=first,b=last;
  for(let i=0;i<8;i++){
    const ca=code(a),cb=code(b);
    if(!(ca|cb))return [a,b];
    if(ca&cb)return null;
    const outside=ca||cb,axis=outside&12?1:0,edge=outside&8?ymax:outside&4?ymin:outside&2?xmax:xmin;
    // Interpolate from the nearer endpoint to retain precision when the other
    // endpoint is many orders of magnitude beyond the viewport.
    const near=Math.abs(edge-a[axis])<=Math.abs(edge-b[axis])?a:b,far=near===a?b:a;
    const fraction=(edge-near[axis])/(far[axis]-near[axis]),point=[...near];
    point[axis]=edge;point[1-axis]=near[1-axis]+fraction*(far[1-axis]-near[1-axis]);
    if(!point.every(Number.isFinite))return null;
    if(ca)a=point;else b=point;
  }
  return null;
}
