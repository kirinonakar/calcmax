import {parse,scanInputTokens,latexSymbolLabels} from './parser.js';
const equationCalls=new Set(['solve','nsolve','linsolve','dsolve','desolve','pdsolve','rsolve','piecewise']);
// An equation following a formula belongs outside its calls. Solvers and
// piecewise conditions keep their own equation input scope.
export function functionRelationExit(source,start,end){
  if(start!==end||/[=!<>:]/.test(source[start-1]||''))return null;
  const calls=[];
  try{
    const visit=node=>{if(node.kind==='call'&&node.args.length&&start>=node.args[0].start&&start<node.end&&source[node.end-1]===')')calls.push(node);node.args.forEach(visit);};
    visit(parse(source,{allowHoles:true}));
  }catch{return null;}
  calls.sort((a,b)=>(a.end-a.start)-(b.end-b.start));
  let position=start;
  for(const call of calls){if(equationCalls.has(call.value))break;position=call.end;}
  return position===start?null:position;
}
function emptyNodeDeletion(nodes,target){
  const structuralOperand=nodes.some(node=>node.kind==='binary'&&
    (node.value==='^'?node.args[0].start===target.start&&node.args[0].end===target.end:
      (node.value==='*'||node.value==='/'&&node.displayOperator!=='÷')&&node.args.some(arg=>arg.start===target.start&&arg.end===target.end)));
  return {start:target.start,end:target.end,text:structuralOperand?'()':''};
}
function tailDeletion(source,node){
  const head=node.args[0],inner=head.args[0];
  const keep=head.kind==='group'&&['number','symbol','call'].includes(inner?.kind)?inner:head;
  return {start:node.start,end:node.end,text:source.slice(keep.start,keep.end),cursor:keep.end-keep.start};
}
// Use the renderer's symbol map so a displayed Greek glyph has one deletion unit.
export function symbolDeletion(source,start,end,backward=true){
  if(start!==end)return null;
  let token;
  try{
    const tokens=scanInputTokens(source);
    token=tokens.find((token,index)=>(Object.hasOwn(latexSymbolLabels,token.text)||token.text==='oo')&&tokens[index+1]?.text!=='('&&(backward?
      start>token.start&&start<=token.end:start>=token.start&&start<token.end));
  }catch{return null;}
  if(!token)return null;
  const nodes=[];
  try{const visit=node=>{nodes.push(node);node.args.forEach(visit);};visit(parse(source,{allowHoles:true}));}catch{}
  return emptyNodeDeletion(nodes,token);
}
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
  return emptyNodeDeletion(nodes,call);
}
export function powerInput(source,start,end,suffix){
  const before=source[start-1];
  const emptyBase=!source.trim()||start===end&&(!before||'+-−×*÷/([,='.includes(before));
  return emptyBase?{text:`()${suffix}`,cursor:1}:{text:suffix,cursor:suffix==='^()'?2:suffix.length};
}
// Hidden denominator delimiters keep subsequent operators inside the fraction.
// A slash also extends a bare exponent until Right explicitly leaves its scope.
export function divisionInput(source,start,end,text,outside=null){
  if(text!=='/')return null;
  const division={start,end,text:'/()',cursor:2};
  if(start!==end)return division;
  const powers=[];
  try{const visit=node=>{if(node.kind==='binary'&&node.value==='^'&&node.args[1].kind!=='group'&&start>=node.args[1].start&&start<=node.args[1].end&&
    !(outside?.start===node.start&&outside?.end===node.end))powers.push(node);node.args.forEach(visit);};visit(parse(source,{allowHoles:true}));}catch{return division;}
  const exponent=powers.sort((a,b)=>(a.end-a.start)-(b.end-b.start))[0]?.args[1];
  if(!exponent)return division;
  return {start:exponent.start,end:exponent.end,text:`(${source.slice(exponent.start,start)}/()${source.slice(start,exponent.end)})`,cursor:start-exponent.start+3};
}
export function emptyPowerDeletion(source,start,end){
  if(start!==end)return null;
  const nodes=[];
  try{const visit=node=>{nodes.push(node);node.args.forEach(visit);};visit(parse(source,{allowHoles:true}));}catch{return null;}
  const content=node=>node.kind==='group'?node.args[0]:node;
  const power=nodes.filter(node=>node.kind==='binary'&&node.value==='^'&&
    node.args.some(arg=>content(arg)?.kind==='hole'&&content(arg).start===start))
    .sort((a,b)=>(a.end-a.start)-(b.end-b.start))[0];
  if(!power)return null;
  return content(power.args[0])?.kind==='hole'?emptyNodeDeletion(nodes,power):tailDeletion(source,power);
}
export function emptyFractionDeletion(source,start,end){
  if(start!==end)return null;
  const nodes=[];
  try{const visit=node=>{nodes.push(node);node.args.forEach(visit);};visit(parse(source,{allowHoles:true}));}catch{return null;}
  const content=node=>node.kind==='group'?node.args[0]:node;
  const fraction=nodes.filter(node=>node.kind==='binary'&&node.value==='/'&&node.displayOperator!=='÷'&&
    content(node.args[1])?.kind==='hole'&&(content(node.args[1]).start===start||
      content(node.args[0])?.kind==='hole'&&content(node.args[0]).start===start))
    .sort((a,b)=>(a.end-a.start)-(b.end-b.start))[0];
  if(!fraction)return null;
  return content(fraction.args[0])?.kind==='hole'?emptyNodeDeletion(nodes,fraction):tailDeletion(source,fraction);
}
export function fractionExit(source,start,end,direction,outside=null){
  return mathStructureExit(source,start,end,direction,outside,['/']);
}
// Source offsets alone cannot distinguish an operand end from its parent edge.
export function mathStructureExit(source,start,end,direction,outside=null,operators=['/','^']){
  if(direction!=='RIGHT'||start!==end)return null;
  const structures=[];
  try{
    const visit=node=>{if(node.kind==='binary'&&operators.includes(node.value)&&node.displayOperator!=='÷')structures.push(node);node.args.forEach(visit);};
    visit(parse(source,{allowHoles:true}));
  }catch{return null;}
  structures.sort((a,b)=>(a.end-a.start)-(b.end-b.start));
  for(const node of structures){
    if(outside&&node.start===outside.start&&node.end===outside.end)continue;
    const operand=node.args[1],content=operand.kind==='group'?operand.args[0]:operand;
    const incomplete=node=>node.kind==='hole'||node.args.some(incomplete);
    if(!content||incomplete(content))continue;
    if(start===content.end||operand.kind==='group'&&(start===operand.end-1||node.value==='^'&&start===operand.end))
      return {position:node.end,start:node.start,end:node.end,...(node.value==='^'?{exponentEnd:content.end}:{denominatorEnd:content.end})};
  }
  return null;
}
// Function heads include their opening delimiter; other nonnumeric tokens are
// single cursor steps even when their source spelling has several characters.
function moveTokenCursor(source,start,direction){
  if(!['LEFT','RIGHT'].includes(direction))return null;
  try{
    const tokens=scanInputTokens(source);
    for(let index=0;index<tokens.length;index++){
      const token=tokens[index];
      if(!token.text||/[0-9.]/.test(source[token.start]))continue;
      const next=tokens[index+1],end=/\p{L}/u.test(source[token.start])&&token.text!=='mod'&&next?.text==='('?next.end:token.end;
      if(direction==='LEFT'&&start>token.start&&start<=end)return token.start;
      if(direction==='RIGHT'&&start>=token.start&&start<end)return end;
    }
  }catch{}
  return Math.max(0,Math.min(source.length,start+(direction==='LEFT'?-1:1)));
}
function isMatrix(node){return ['list','matrix'].includes(node.kind)&&node.args.length&&node.args.every(row=>row.kind==='list');}
// Keep new factors outside both closing brackets, including a caret left between them.
export function matrixFactorInput(source,start,end,text){
  if(start!==end||!/^[\p{L}\p{N}_.([{√∞]/u.test(text))return null;
  if(source[start-1]!==']'&&source[start]!==']'&&!/\s/.test(source[start-1]||''))return null;
  const matrices=[];
  try{
    const visit=node=>{
      if(isMatrix(node)&&node.end>node.args.at(-1).end&&source[node.end-1]===']'&&
        (start>=node.args.at(-1).end&&start<=node.end||node.end<=start&&!source.slice(node.end,start).trim()))matrices.push(node);
      node.args.forEach(visit);
    };
    visit(parse(source,{allowHoles:true}));
  }catch{return null;}
  const matrix=matrices.sort((a,b)=>(a.end-a.start)-(b.end-b.start))[0];
  return matrix?{position:Math.max(start,matrix.end),prefix:'*'}:null;
}
export function moveMathCursor(source,start,end,direction,outside=null){
  if(direction==='HOME')return 0;
  if(direction==='END')return source.length;
  if(start!==end)return direction==='LEFT'?start:direction==='RIGHT'?end:null;
  const exit=mathStructureExit(source,start,end,direction,outside);if(exit)return exit.position;
  const nodes=[];try{const visit=node=>{nodes.push(node);node.args.forEach(visit);};visit(parse(source,{allowHoles:true}));}catch{return moveTokenCursor(source,start,direction);}
  const roots=nodes.filter(node=>node.kind==='call'&&['sqrt','cbrt','nthroot'].includes(node.value)).sort((a,b)=>(a.end-a.start)-(b.end-b.start));
  for(const root of roots){const argument=root.args[0];if(!argument)continue;if(direction==='LEFT'&&start===argument.start)return root.start;if(direction==='RIGHT'&&start===argument.end)return root.end;if(direction==='RIGHT'&&start===root.start)return argument.start;if(direction==='LEFT'&&start===root.end)return argument.end;if(direction==='UP'&&start>=argument.start&&start<=argument.end)return root.end;}
  const matrix=nodes.filter(node=>isMatrix(node)&&start>=node.start&&start<=node.end).sort((a,b)=>(a.end-a.start)-(b.end-b.start))[0];
  if(matrix){
    if(direction==='LEFT'&&start>=matrix.args.at(-1).end){const cell=matrix.args.at(-1).args.at(-1);if(cell)return cell.end;}
    const rowIndex=matrix.args.findIndex(row=>start<=row.end),row=matrix.args[rowIndex],column=row?.args.findIndex(cell=>start<=cell.end),cell=row?.args[column];
    if(cell){
      if(direction==='UP'||direction==='DOWN'){const target=matrix.args[rowIndex+(direction==='UP'?-1:1)]?.args[column];if(target)return Math.min(target.end,target.start+Math.max(0,start-cell.start));}
      if(direction==='RIGHT'&&start===cell.end){const target=row.args[column+1]||matrix.args[rowIndex+1]?.args[0];return target?.start??matrix.end;}
      if(direction==='LEFT'&&start===cell.start){const target=row.args[column-1]||matrix.args[rowIndex-1]?.args.at(-1);if(target)return target.end;}
    }
  }
  for(const node of nodes){if(node.kind!=='binary')continue;const [left,right]=node.args;if(node.value==='/'&&node.displayOperator!=='÷'){const a=left.end-(left.kind==='group'?1:0),b=right.start+(right.kind==='group'?1:0);if(direction==='RIGHT'&&start===a)return b;if(direction==='LEFT'&&start===b)return a;if(direction==='DOWN'&&start>=left.start&&start<=left.end)return b;if(direction==='UP'&&start>=right.start&&start<=right.end)return left.start+(left.kind==='group'?1:0);}if(node.value==='^'){const a=left.end-(left.kind==='group'?1:0),b=right.start+(right.kind==='group'?1:0);if(direction==='LEFT'&&start===b)return a;if(direction==='RIGHT'&&(start===a||start===left.end))return b;}}
  return moveTokenCursor(source,start,direction);
}
