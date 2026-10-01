import {parse} from './parser.js';
// Remove an unused function template as a unit, including its hidden delimiters.
export function emptyCallDeletion(source,start,end){
  if(start!==end)return null;
  const nodes=[];
  try{const visit=node=>{nodes.push(node);node.args.forEach(visit);};visit(parse(source,{allowHoles:true}));}catch{return null;}
  const empty=node=>node.kind==='hole'||['group','list','set','tuple','matrix'].includes(node.kind)&&node.args.every(empty);
  const variableTemplates=['diff','integrate','nderivative','limit','sum','product'];
  const call=nodes.filter(node=>node.kind==='call'&&
    node.args.some(arg=>empty(arg)&&start>=arg.start&&start<=arg.end)&&
    node.args.every((arg,index)=>empty(arg)||index===1&&variableTemplates.includes(node.value)&&arg.kind==='symbol'&&arg.value==='x'))
    .sort((a,b)=>(a.end-a.start)-(b.end-b.start))[0];
  if(!call)return null;
  const structuralOperand=nodes.some(node=>node.kind==='binary'&&
    (node.value==='^'?node.args[0].start===call.start&&node.args[0].end===call.end:
      (node.value==='*'||node.value==='/'&&node.displayOperator!=='÷')&&node.args.some(arg=>arg.start===call.start&&arg.end===call.end)));
  return {start:call.start,end:call.end,text:structuralOperand?'()':''};
}
export function fractionExit(source,start,end,direction,outside=null){
  if(direction!=='RIGHT'||start!==end)return null;
  const fractions=[];
  try{
    const visit=node=>{if(node.kind==='binary'&&node.value==='/'&&node.displayOperator!=='÷')fractions.push(node);node.args.forEach(visit);};
    visit(parse(source,{allowHoles:true}));
  }catch{return null;}
  fractions.sort((a,b)=>(a.end-a.start)-(b.end-b.start));
  for(const node of fractions){
    if(outside&&node.start===outside.start&&node.end===outside.end)continue;
    const denominator=node.args[1],content=denominator.kind==='group'?denominator.args[0]:denominator;
    const incomplete=node=>node.kind==='hole'||node.args.some(incomplete);
    if(!content||incomplete(content))continue;
    if(start===content.end||denominator.kind==='group'&&start===denominator.end-1)
      return {position:node.end,start:node.start,end:node.end,denominatorEnd:content.end};
  }
  return null;
}
export function moveMathCursor(source,start,end,direction){
  if(direction==='HOME')return 0;
  if(direction==='END')return source.length;
  if(start!==end)return direction==='LEFT'?start:direction==='RIGHT'?end:null;
  const exit=fractionExit(source,start,end,direction);if(exit)return exit.position;
  const nodes=[];try{const visit=node=>{nodes.push(node);node.args.forEach(visit);};visit(parse(source,{allowHoles:true}));}catch{return ['LEFT','RIGHT'].includes(direction)?Math.max(0,Math.min(source.length,start+(direction==='LEFT'?-1:1))):null;}
  const roots=nodes.filter(node=>node.kind==='call'&&['sqrt','cbrt','nthroot'].includes(node.value)).sort((a,b)=>(a.end-a.start)-(b.end-b.start));
  for(const root of roots){const argument=root.args[0];if(!argument)continue;if(direction==='LEFT'&&start===argument.start)return root.start;if(direction==='RIGHT'&&start===argument.end)return root.end;if(direction==='RIGHT'&&start===root.start)return argument.start;if(direction==='LEFT'&&start===root.end)return argument.end;if(direction==='UP'&&start>=argument.start&&start<=argument.end)return root.end;}
  const matrix=nodes.filter(node=>node.kind==='list'&&node.args.length&&node.args.every(row=>row.kind==='list')&&start>=node.start&&start<=node.end).sort((a,b)=>(a.end-a.start)-(b.end-b.start))[0];
  if(matrix){const rowIndex=matrix.args.findIndex(row=>start<=row.end),row=matrix.args[rowIndex],column=row?.args.findIndex(cell=>start<=cell.end),cell=row?.args[column];if(cell){if(direction==='UP'||direction==='DOWN'){const target=matrix.args[rowIndex+(direction==='UP'?-1:1)]?.args[column];if(target)return Math.min(target.end,target.start+Math.max(0,start-cell.start));}if(direction==='RIGHT'&&start===cell.end){const target=row.args[column+1]||matrix.args[rowIndex+1]?.args[0];if(target)return target.start;}if(direction==='LEFT'&&start===cell.start){const target=row.args[column-1]||matrix.args[rowIndex-1]?.args.at(-1);if(target)return target.end;}}}
  for(const node of nodes){if(node.kind!=='binary')continue;const [left,right]=node.args;if(node.value==='/'&&node.displayOperator!=='÷'){const a=left.end-(left.kind==='group'?1:0),b=right.start+(right.kind==='group'?1:0);if(direction==='RIGHT'&&start===a)return b;if(direction==='LEFT'&&start===b)return a;if(direction==='DOWN'&&start>=left.start&&start<=left.end)return b;if(direction==='UP'&&start>=right.start&&start<=right.end)return left.start+(left.kind==='group'?1:0);}if(node.value==='^'&&right.kind==='group'){if(direction==='LEFT'&&start===right.start+1)return left.end;if(direction==='RIGHT'&&start===left.end)return right.start+1;}}
  return ['LEFT','RIGHT'].includes(direction)?Math.max(0,Math.min(source.length,start+(direction==='LEFT'?-1:1))):null;
}
