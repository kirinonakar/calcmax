import {parse} from './parser.js';
import {mathDisplay} from './math-display.js';

// Render the source AST without evaluating it; empty template slots stay editable.
export function expressionDisplay(source) {
  function convert(n) {
    const args=n.args.map(convert);
    const mapped=(kind,value='',children=args)=>({kind,value,args:children,start:n.start,end:n.end});
    if(n.kind==='number'||n.kind==='hole')return mapped(n.kind,n.value);
    if(n.kind==='symbol')return mapped('symbol',{pi:'π',oo:'∞',Ans:'Ans'}[n.value]||n.value);
    if(n.kind==='group')return mapped('parentheses');
    if(n.kind==='unary'||n.kind==='relation')return mapped(n.kind,n.value);
    if(['list','set','tuple'].includes(n.kind))return mapped(n.kind);
    if(n.kind==='sexagesimal')return mapped('dms');
    if(n.kind==='binary') {
      if(n.value==='/')return mapped('fraction');
      if(n.value==='^')return mapped('power');
      if(n.value==='*')return mapped(n.displayOperator==='∘'?'implicit-product':'product');
      if(n.value==='+')return mapped('sum');
      return mapped('relation',n.value);
    }
    if(n.kind==='call') {
      if(n.value==='sqrt')return mapped('root');
      if(n.value==='exp')return mapped('power','',[{kind:'symbol',value:'e'},...args]);
      if(n.value==='integrate'&&args.length>=2){const bounds=n.args[1].kind==='tuple'?n.args[1].args.map(convert):args.slice(1);return mapped('integral','',[args[0],...bounds]);}
      if(['diff','nderivative'].includes(n.value))return mapped('derivative');
      if(n.value==='factorial')return mapped('postfix','!');
      if(n.value==='degree')return mapped('postfix','°');
      if(n.value==='percent')return mapped('postfix','%');
      if(n.value==='log'&&args.length===2)return mapped('logarithm');
      return mapped('function',n.value==='log'?'log':n.value);
    }
    return mapped('text',n.value);
  }
  return mathDisplay(convert(parse(source,{allowHoles:true})));
}
