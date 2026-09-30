import {parse} from './parser.js';

// Match the structured editor: a selection, or the nearest complete operand
// ending at the cursor, becomes the numerator. Empty slots start in the top.
export function fractionInput(source,start,end=start){
  if(start===end){
    const boundary=source.slice(0,start).trimEnd().length,candidates=[];
    try{
      const visit=node=>{const holes=node.args.map(visit).some(Boolean)||node.kind==='hole';if(!holes&&node.end===boundary&&node.start<node.end)candidates.push(node);return holes;};
      visit(parse(source,{allowHoles:true}));
    }catch{}
    candidates.sort((a,b)=>(a.end-a.start)-(b.end-b.start));
    if(candidates.length)start=candidates[0].start;
  }
  const numerator=source.slice(start,end),text=`(${numerator})/()`;
  return {start,end,text,cursor:numerator.trim()?text.length-1:1};
}
