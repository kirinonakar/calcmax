import {displayText} from './display-format.js';
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

// The tree the result view renders for the current toggles; the display and the
// Copy button share it so copied digits cannot drift from the visible digits.
export function resultDisplayTree(result,{decimal=false,mixed=false}={}) {
  let tree=decimal ? result.decimalTree||result.tree : result.tree;
  if(mixed&&!decimal&&tree?.kind==='fraction'){
    try{const numerator=BigInt(tree.args[0].value),denominator=BigInt(tree.args[1].value),whole=numerator/denominator,remainder=(numerator<0n?-numerator:numerator)%denominator;if(whole)tree={kind:'mixed',args:[{kind:'number',value:whole.toString()},{kind:'fraction',args:[{kind:'number',value:remainder.toString()},{kind:'number',value:denominator.toString()}]}]};}catch{}
  }
  return tree;
}

// Copy text for the answer view: the digits, grouping, and notation the result view shows.
export function resultText(result,{decimal=false,mixed=false,digits=10,notation='off',grouping=false,engineeringShift=0,showZeroExponent=false}={}) {
  if(!result)return '';
  const text=(decimal ? result.decimal : result.exact)||'';
  const options={notation,grouping,engineeringShift,showZeroExponent};
  const tree=resultDisplayTree(result,{decimal,mixed});
  if(tree&&text.length<=40000){
    const shown=treeText(tree,digits,options);
    if(shown!==null)return shown;
  }
  return displayText(text,digits,options);
}

function treeText(node,digits,options,allowNotation=true) {
  if(!node)return null;
  const kind=node.kind,value=node.value||'',args=node.args||[];
  const child=(index,childAllow)=>index<args.length?treeText(args[index],digits,options,childAllow):null;
  switch(kind){
    case 'number': case 'text': case 'fixed-number':
      return displayText(value,digits,options,allowNotation);
    case 'symbol':
      return value;
    case 'fraction': {
      const numerator=child(0,false),denominator=child(1,false);
      return numerator===null||denominator===null?null:`${numerator}/${denominator}`;
    }
    case 'unary': {
      if(value!=='-')return null;
      const argument=child(0,allowNotation);
      return argument===null?null:`-${argument}`;
    }
    case 'mixed': {
      const whole=child(0,false),fraction=child(1,false);
      return whole===null||fraction===null?null:`${whole} ${fraction}`;
    }
    case 'dms': {
      const markers=['°','′','″'];
      const parts=args.map((argument,index)=>{const shown=treeText(argument,digits,options,false);return shown===null?null:shown+(markers[index]||'');});
      return parts.includes(null)?null:parts.join('');
    }
    case 'product': {
      if(args.length!==2||args[1]?.kind!=='power'||args[1].args?.[0]?.value!=='10')return null;
      const mantissa=child(0,false),exponent=args[1].args?.[1]?.value;
      return mantissa===null||exponent===undefined?null:`${mantissa}×10^${exponent}`;
    }
    case 'relation': {
      const left=child(0,false),right=child(1,false);
      return left===null||right===null?null:`${left} ${value==='=='?'=':value} ${right}`;
    }
    case 'row': {
      const inner=child(0,false);
      return inner===null?null:`${value}: ${inner}`;
    }
    case 'rows': {
      const parts=args.map(argument=>treeText(argument,digits,options,false));
      return parts.includes(null)?null:parts.join('\n');
    }
    case 'quantity': {
      const base=child(0,false);
      return base===null?null:value?`${base} ${value}`:base;
    }
    case 'parentheses': {
      const inner=child(0,false);
      return inner===null?null:`(${inner})`;
    }
    case 'list': case 'matrix': {
      const parts=args.map(argument=>treeText(argument,digits,options,false));
      return parts.includes(null)?null:`[${parts.join(', ')}]`;
    }
    case 'set': {
      const parts=args.map(argument=>treeText(argument,digits,options,false));
      return parts.includes(null)?null:`{${parts.join(', ')}}`;
    }
    case 'tuple': {
      const parts=args.map(argument=>treeText(argument,digits,options,false));
      return parts.includes(null)?null:`(${parts.join(', ')})`;
    }
    default:
      return null;
  }
}
