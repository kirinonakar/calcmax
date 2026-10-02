// Interpolate the interval endpoints even when a display curve has only two points.
export function integralPolygons(points,interval){
  const low=Math.min(...interval),high=Math.max(...interval),polygons=[];let segment=[];
  const finite=p=>Array.isArray(p)&&p.length>=2&&p.every(Number.isFinite);
  const flush=()=>{if(segment.length>1)polygons.push([[segment[0][0],0],...segment,[segment.at(-1)[0],0]]);segment=[];};
  for(let i=1;i<points.length;i++){
    const a=points[i-1],b=points[i];
    if(!finite(a)||!finite(b)){flush();continue;}
    const dx=b[0]-a[0];
    if(dx===0){flush();continue;}
    const first=Math.max(0,Math.min((low-a[0])/dx,(high-a[0])/dx)),last=Math.min(1,Math.max((low-a[0])/dx,(high-a[0])/dx));
    if(last<=first){flush();continue;}
    const at=t=>[a[0]+t*dx,a[1]+t*(b[1]-a[1])],start=at(first),end=at(last),previous=segment.at(-1);
    if(previous&&(Math.abs(previous[0]-start[0])>1e-9||Math.abs(previous[1]-start[1])>1e-9))flush();
    if(!segment.length)segment.push(start);segment.push(end);
  }
  flush();return polygons;
}
