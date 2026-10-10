// Geometry stays in data coordinates until clipping is complete.
export function surfaceSampleCount(bounds,requested=26,automatic=true,zoom=1,implicit=false){
  const density=automatic?Math.max(26,Math.max(bounds.xmax-bounds.xmin,bounds.ymax-bounds.ymin)*8)*Math.sqrt(zoom):requested;
  return Math.max(12,Math.min(implicit?32:96,Math.ceil(Number.isFinite(density)?density:96)));
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
  if(points.every(p=>limits(bounds).every(([lo,hi],i)=>p[i]>=lo&&p[i]<=hi)))return points;
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
  const ct=Math.cos(theta),st=Math.sin(theta),ce=Math.cos(tilt),se=Math.sin(tilt);
  const normalize=point=>point.map((v,i)=>{const [min,max]=limits(bounds)[i];return 2*(v-min)/(max-min)-1;});
  const project=point=>{
    const [x,y,z]=normalize(point),horizontal=x*ct-y*st,depth=x*st+y*ct;
    // The third component increases toward the camera; paint smaller depths first.
    return [horizontal,-depth*se-z*ce,z*se-depth*ce];
  };
  return {project,normalize,direction:[-st*ce,-ct*ce,se]};
}
// An affine lighting plane on each projected triangle is Gouraud shading.
// Shared vertex brightness gives neighboring triangles identical edge colors.
export function surfaceLightingGradient(points,levels){
  const [[x,y],[x1,y1],[x2,y2]]=points,dx1=x1-x,dy1=y1-y,dx2=x2-x,dy2=y2-y;
  const determinant=dx1*dy2-dx2*dy1;
  if(Math.abs(determinant)<1e-14)return null;
  const a=levels[1]-levels[0],b=levels[2]-levels[0],gx=(a*dy2-b*dy1)/determinant,gy=(dx1*b-dx2*a)/determinant;
  const length2=gx*gx+gy*gy,min=Math.min(...levels),max=Math.max(...levels);
  if(!Number.isFinite(length2)||length2<1e-16||max-min<1e-7)return null;
  const offset=(min-levels[0])/length2,span=(max-min)/length2;
  const start=[x+gx*offset,y+gy*offset],end=[start[0]+gx*span,start[1]+gy*span];
  return [...start,...end].every(Number.isFinite)?{start,end,min,max}:null;
}
export function prepareSurfaceFaces(mesh,bounds,triangles=null,triangleNormals=null,convex=false){
  const faces=[];
  const input=triangles?[...triangles]:[];
  for(let row=0;row<mesh.length-1;row++)for(let col=0;col<Math.min(mesh[row].length,mesh[row+1].length)-1;col++){
    const cell=[mesh[row][col],mesh[row][col+1],mesh[row+1][col+1],mesh[row+1][col]];
    if(!cell.every(finite))continue;
    input.push([cell[0],cell[1],cell[2]],[cell[0],cell[2],cell[3]]);
  }
  const spans=limits(bounds).map(([lo,hi])=>hi-lo),normalize=p=>p.map((v,i)=>2*(v-limits(bounds)[i][0])/spans[i]-1);
  const closed=convex&&input.every(triangle=>triangle.every(p=>finite(p)&&limits(bounds).every(([lo,hi],i)=>p[i]>=lo&&p[i]<=hi)));
  for(const [index,triangle] of input.entries()){
      if(triangle.length!==3||!triangle.every(finite))continue;
      const points=clipSurfacePolygon(triangle,bounds);if(!points.length)continue;
      const [a,b,c]=triangle.map(normalize),u=b.map((v,i)=>v-a[i]),v=c.map((n,i)=>n-a[i]);
      const normal=[u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]],length=Math.hypot(...normal);
      if(!length)continue;
      const light=.35+.65*Math.abs((normal[0]*-.4+normal[1]*-.5+normal[2]*.75)/(length*Math.hypot(.4,.5,.75)));
      const normals=triangleNormals?.[index];
      let levels=null;
      if(normals?.length===3&&normals.every(n=>finite(n)&&Math.hypot(...n)>0)){
        const average=normals.reduce((sum,n)=>sum.map((v,i)=>v+n[i]*spans[i]),[0,0,0]);
        if(normal.reduce((sum,v,i)=>sum+v*average[i],0)<0)for(let i=0;i<3;i++)normal[i]=-normal[i];
        levels=normals.map((n,i)=>{
          const scaled=n.map((v,k)=>v*spans[k]),size=Math.hypot(...scaled);
          const brightness=.35+.65*Math.abs((scaled[0]*-.4+scaled[1]*-.5+scaled[2]*.75)/(size*Math.hypot(.4,.5,.75)));
          return (.65+.35*Math.max(0,Math.min(1,(triangle[i][2]-bounds.zmin)/(bounds.zmax-bounds.zmin))))*brightness;
        });
      }
      faces.push({points,source:triangle,normal,levels,light,height:points.reduce((sum,p)=>sum+(p[2]-bounds.zmin)/(bounds.zmax-bounds.zmin),0)/points.length});
  }
  return {faces,closed,convex,limits:limits(bounds)};
}
export function projectSurfaceFaces(prepared,projection){
  const cache=new Map(),project=p=>{if(!cache.has(p))cache.set(p,projection.project(p));return cache.get(p);},faces=[];
  for(const face of prepared.faces){
    const front=face.normal.reduce((sum,v,i)=>sum+v*projection.direction[i],0)>1e-12*Math.hypot(...face.normal);
    if((face.closed??prepared.closed)&&!front)continue;
    const projected=face.points.map(project);
    const lighting=face.levels?surfaceLightingGradient(face.source.map(project),face.levels):null;
    faces.push({...face,projected,front,lighting,depth:projected.reduce((sum,p)=>sum+p[2],0)/projected.length});
  }
  // A convex shell's front and back each project without self-overlap. Draw
  // every back face before every front face, including when ranges cut it open.
  return faces.sort((a,b)=>prepared.convex&&a.front!==b.front?Number(a.front)-Number(b.front):a.depth-b.depth);
}
// Keep all surfaces in one scene so their depths interleave across groups.
export function combineSurfaceFaces(groups){
  if(groups.length===1)return groups[0];
  return {faces:groups.flatMap((group,index)=>group.faces.map(face=>({...face,group:index,closed:group.closed}))),
    closed:groups.every(group=>group.closed),convex:false,limits:groups[0]?.limits};
}
export function surfaceFaces(mesh,bounds,projection,triangles=null,triangleNormals=null,convex=false){
  return projectSurfaceFaces(prepareSurfaceFaces(mesh,bounds,triangles,triangleNormals,convex),projection);
}
