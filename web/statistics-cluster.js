// Single-linkage hierarchy from a minimum spanning forest; O(n) working memory.
// Missing cells use the shared dimensions, rescaled to the full vector width.
export function statisticsHierarchy(vectors){
  const n=vectors.length,dimensions=vectors[0]?.length||0;
  if(!n)return {order:[],links:[]};
  let scale=0;for(const row of vectors)for(const value of row)if(Number.isFinite(value))scale=Math.max(scale,Math.abs(value));scale||=1;
  const data=vectors.map(row=>Float64Array.from({length:dimensions},(_,i)=>Number.isFinite(row[i])?row[i]/scale:NaN));
  const distance=(a,b)=>{let sum=0,count=0;for(let i=0;i<dimensions;i++){const x=data[a][i],y=data[b][i];if(Number.isFinite(x)&&Number.isFinite(y)){sum+=(x-y)**2;count++;}}return count?Math.sqrt(sum*dimensions/count):Infinity;};
  const visited=new Uint8Array(n),nearest=new Float64Array(n).fill(Infinity),from=new Int32Array(n).fill(-1),edges=[];
  for(let step=0;step<n;step++){
    let next=-1;for(let i=0;i<n;i++)if(!visited[i]&&(next<0||nearest[i]<nearest[next]))next=i;
    visited[next]=1;if(from[next]>=0)edges.push({a:from[next],b:next,height:nearest[next]});
    for(let i=0;i<n;i++)if(!visited[i]){const d=distance(next,i);if(d<nearest[i]){nearest[i]=d;from[i]=next;}}
  }
  edges.sort((a,b)=>a.height-b.height||a.a-b.a||a.b-b.b);
  const parent=Int32Array.from({length:n},(_,i)=>i),roots=Array.from({length:n},(_,i)=>i),nodes=Array.from({length:n},(_,i)=>({minimum:i,height:0}));
  const find=i=>{while(parent[i]!==i){parent[i]=parent[parent[i]];i=parent[i];}return i;};
  for(const edge of edges){
    let a=find(edge.a),b=find(edge.b);if(a===b)continue;
    if(nodes[roots[a]].minimum>nodes[roots[b]].minimum)[a,b]=[b,a];
    nodes.push({left:roots[a],right:roots[b],minimum:nodes[roots[a]].minimum,height:edge.height});
    roots[a]=nodes.length-1;parent[b]=a;
  }
  const forest=Array.from({length:n},(_,i)=>i).filter(i=>find(i)===i).map(i=>roots[i]).sort((a,b)=>nodes[a].minimum-nodes[b].minimum);
  const order=[],stack=[...forest].reverse();
  while(stack.length){const id=stack.pop(),node=nodes[id];if(id<n)order.push(id);else stack.push(node.right,node.left);}
  const centers=Array(nodes.length).fill(0);order.forEach((id,index)=>centers[id]=index);
  const links=[];
  for(let id=n;id<nodes.length;id++){
    const node=nodes[id];centers[id]=(centers[node.left]+centers[node.right])/2;
    links.push({left:centers[node.left],right:centers[node.right],leftHeight:nodes[node.left].height,rightHeight:nodes[node.right].height,height:node.height});
  }
  return {order,links};
}

export function clusteredHeatMap(data){
  const rows=statisticsHierarchy(data.rows.map(row=>row.values));
  const columns=statisticsHierarchy(data.columns.map((_,i)=>data.rows.map(row=>row.values[i])));
  return {...data,clustered:true,rowLinks:rows.links,columnLinks:columns.links,
    columns:columns.order.map(i=>data.columns[i]),rows:rows.order.map(i=>({...data.rows[i],values:columns.order.map(j=>data.rows[i].values[j]),...(data.rows[i].counts?{counts:columns.order.map(j=>data.rows[i].counts[j])}:{})}))};
}
