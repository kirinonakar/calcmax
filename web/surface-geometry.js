// Geometry stays in data coordinates until clipping is complete.
export function surfaceSampleCount(bounds,requested=26,automatic=true,zoom=1){
  const density=automatic?Math.max(26,Math.max(bounds.xmax-bounds.xmin,bounds.ymax-bounds.ymin)*8)*Math.sqrt(zoom):requested;
  return Math.max(12,Math.min(96,Math.ceil(Number.isFinite(density)?density:96)));
}
export function surfaceZRange(min,max){
  if(!Number.isFinite(min)||!Number.isFinite(max))return [-1,1];
  if(max>min)return [min,max];
  const padding=Math.max(1,Math.abs(min)*.1);return [min-padding,min+padding];
}
const limits=bounds=>[[bounds.xmin,bounds.xmax],[bounds.ymin,bounds.ymax],[bounds.zmin,bounds.zmax]];
const finite=point=>point?.length===3&&point.every(Number.isFinite);
const interpolate=(a,b,t)=>a.map((v,i)=>v+(b[i]-v)*t);
export function clipSurfaceSegment(a,b,bounds){
  if(!finite(a)||!finite(b))return null;
  let start=0,end=1;
  for(const [axis,[min,max]] of limits(bounds).entries()){
    const delta=b[axis]-a[axis];
    if(!delta){if(a[axis]<min||a[axis]>max)return null;continue;}
    const t1=(min-a[axis])/delta,t2=(max-a[axis])/delta;
    start=Math.max(start,Math.min(t1,t2));end=Math.min(end,Math.max(t1,t2));
    if(start>=end)return null;
  }
  return [interpolate(a,b,start),interpolate(a,b,end)];
}
export function clipSurfacePolygon(points,bounds){
  if(!points.every(finite))return [];
  let polygon=points;
  for(const [axis,[min,max]] of limits(bounds).entries())for(const [edge,above] of [[min,true],[max,false]]){
    const input=polygon;polygon=[];
    if(!input.length)return [];
    let a=input.at(-1),insideA=above?a[axis]>=edge:a[axis]<=edge;
    for(const b of input){
      const insideB=above?b[axis]>=edge:b[axis]<=edge;
      if(insideA!==insideB)polygon.push(interpolate(a,b,(edge-a[axis])/(b[axis]-a[axis])));
      if(insideB)polygon.push(b);
      a=b;insideA=insideB;
    }
  }
  return polygon.length>=3?polygon:[];
}
export function surfaceProjection(bounds,rotation,elevation){
  const theta=rotation*Math.PI/180,tilt=elevation*Math.PI/180;
  const normalize=point=>point.map((v,i)=>{const [min,max]=limits(bounds)[i];return 2*(v-min)/(max-min)-1;});
  const project=point=>{
    const [x,y,z]=normalize(point),horizontal=x*Math.cos(theta)-y*Math.sin(theta),depth=x*Math.sin(theta)+y*Math.cos(theta);
    // The third component increases toward the camera; paint smaller depths first.
    return [horizontal,-depth*Math.sin(tilt)-z*Math.cos(tilt),z*Math.sin(tilt)-depth*Math.cos(tilt)];
  };
  return {project,normalize};
}
export function surfaceFaces(mesh,bounds,projection){
  const faces=[];
  for(let row=0;row<mesh.length-1;row++)for(let col=0;col<Math.min(mesh[row].length,mesh[row+1].length)-1;col++){
    const cell=[mesh[row][col],mesh[row][col+1],mesh[row+1][col+1],mesh[row+1][col]];
    if(!cell.every(finite))continue;
    for(const triangle of [[cell[0],cell[1],cell[2]],[cell[0],cell[2],cell[3]]]){
      const points=clipSurfacePolygon(triangle,bounds);if(!points.length)continue;
      const [a,b,c]=triangle.map(projection.normalize),u=b.map((v,i)=>v-a[i]),v=c.map((n,i)=>n-a[i]);
      const normal=[u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]],length=Math.hypot(...normal);
      if(!length)continue;
      const light=.35+.65*Math.abs((normal[0]*-.4+normal[1]*-.5+normal[2]*.75)/(length*Math.hypot(.4,.5,.75)));
      faces.push({points,depth:points.reduce((sum,p)=>sum+projection.project(p)[2],0)/points.length,light,height:points.reduce((sum,p)=>sum+(p[2]-bounds.zmin)/(bounds.zmax-bounds.zmin),0)/points.length});
    }
  }
  return faces.sort((a,b)=>a.depth-b.depth);
}
