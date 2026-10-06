import {mathDisplay} from './math-display.js';

// Break at outer operators and collection separators. Structured operands remain
// intact, and punctuation stays attached to the neighboring mathematical term.
export function resultMathParts(tree) {
  const operator=value=>({kind:'input-operator',value,args:[]});
  function split(node) {
    const args=node.args||[],kind=node.kind;
    if(['set','list','tuple'].includes(kind)&&args.length) {
      const parts=args.flatMap((arg,i)=>{
        const pieces=split(arg);
        if(i<args.length-1)pieces.at(-1).push(operator(','));
        return pieces;
      });
      parts[0].unshift(operator({set:'{',list:'[',tuple:'('}[kind]));
      parts.at(-1).push(operator({set:'}',list:']',tuple:')'}[kind]));
      return parts;
    }
    if(kind==='sum'&&args.length) return args.flatMap((arg,i)=>{
      const pieces=split(arg);
      if(i&&arg.kind!=='unary')pieces[0].unshift(operator('+'));
      return pieces;
    });
    if(kind==='relation'&&args.length===2) {
      const right=split(args[1]);right[0].unshift(operator(node.value));
      return [...split(args[0]),...right];
    }
    return [[node]];
  }
  return split(tree);
}

export function resultMathDisplay(tree,digits=10,decimal=false,options={}) {
  const parts=resultMathParts(tree);
  const flow=document.createElement('span');flow.className='result-flow';
  for(const nodes of parts) {
    const part=document.createElement('span');part.className='result-part';
    // Every part scrolls horizontally, including a single wide result.
    part.append(parts.length===1?mathDisplay(tree,digits,decimal,options):mathDisplay({kind:'implicit-product',args:nodes},digits,decimal,options));
    flow.append(part);
  }
  return flow;
}
