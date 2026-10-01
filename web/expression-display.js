import {expressionTree} from './expression-tree.js';
import {mathDisplay} from './math-display.js';

// Source previews preserve the expression; result captions can round display only.
export function expressionDisplay(source,{digits=10,roundNumbers=false}={}) {
  return mathDisplay(expressionTree(source),digits,false,{roundNumbers});
}

// An invalid expression must still have selectable source ranges for editing.
export function expressionInputDisplay(source,{wordWrap=false}={}) {
    // Both modes render the very same parts. Only the containing row wraps.
    const frame=document.createElement('span');frame.className='input-math input-flow'+(wordWrap?' input-wrapped':'');
    const append=node=>{const part=document.createElement('span');part.className='input-part';part.append(mathDisplay(node,10,false,{roundNumbers:false}));frame.append(part);};
    const operator=(value,start,end)=>append({kind:'input-operator',value,args:[],start,end});
    function flow(node){
      // Inline pending operands use the caret; fraction/power slots stay visible.
      if(node.kind==='hole')return;
      if(['sum','explicit-product','implicit-product','relation'].includes(node.kind)){
        node.args.forEach((child,i)=>{
          if(i){const previous=node.args[i-1],value=node.kind==='sum'?(child.kind==='unary'?'':'+'):node.kind==='explicit-product'?'×':node.kind==='relation'?node.value:'';if(value)operator(value,previous.end,child.start);}
          flow(child);
        });
      }else if(node.kind==='number'&&node.value.length>18&&!/[eE]/.test(node.value)&&source.slice(node.start,node.end)===node.value){
        // Long exact numbers may wrap between digits while keeping source offsets.
        for(let i=0;i<node.value.length;i++)append({...node,value:node.value[i],start:node.start+i,end:node.start+i+1});
      }else append(node);
    }
    try{flow(expressionTree(source));}catch{
      for(const match of source.matchAll(/[\p{L}\p{N}_.]+|\s+|[^\p{L}\p{N}_.\s]/gu)){
        append({kind:'input-text',value:match[0]==='*'?'×':match[0],args:[],start:match.index,end:match.index+match[0].length});
      }
    }
    return frame;
}
