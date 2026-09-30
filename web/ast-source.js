export function astSource(node){
  if(!node)return '';
  const args=(node.args||[]).map(astSource);
  if(['symbol','snapshot_symbol','number','float','constant'].includes(node.kind))return ({E:'e',I:'i'}[node.value]||node.value);
  if(node.kind==='restricted')return args[0];
  if(node.kind==='binary'||node.kind==='relation')return `(${args[0]}${node.value}${args[1]})`;
  if(node.kind==='unary')return node.value+`(${args[0]})`;
  if(['list','tuple','set','group'].includes(node.kind)){const [a,b]=node.kind==='set'?['{','}']:['tuple','group'].includes(node.kind)?['(',')']:['[',']'];return a+args.join(',')+b;}
  if(['call','frozen_call'].includes(node.kind))return `${node.value}(${args.join(',')})`;
  return node.value||'';
}
