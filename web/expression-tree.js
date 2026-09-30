import {parse} from './parser.js';

export function expressionTree(source) {
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
      if(n.value==='/')return n.displayOperator==='÷'?mapped('relation','÷'):mapped('fraction','',n.args.map(node=>node.kind==='group'?convert(node.args[0]):convert(node)));
      if(n.value==='^')return mapped('power','',[args[0],n.args[1].kind==='group'?convert(n.args[1].args[0]):args[1]]);
      if(n.value==='*')return mapped(n.displayOperator==='∘'?'implicit-product':'explicit-product');
      if(n.value==='+')return mapped('sum');
      return mapped('relation',n.value);
    }
    if(n.kind==='call') {
      if(n.value==='sqrt')return mapped('root');
      if(n.value==='cbrt')return mapped('indexed-root','',[args[0],{kind:'number',value:'3'}]);
      if(n.value==='nthroot')return mapped('indexed-root');
      if(n.value==='exp')return mapped('power','',[{kind:'symbol',value:'e'},...args]);
      if(['integrate','Integral'].includes(n.value)&&args.length>=2){const bounds=n.args[1].kind==='tuple'?n.args[1].args.map(convert):args.slice(1);return mapped('integral','',[args[0],...bounds]);}
      if(['sum','Sum','product','Product'].includes(n.value)&&args.length>=2){const bounds=n.args[1].kind==='tuple'?n.args[1].args.map(convert):args.slice(1);return mapped('large-operator',/product/i.test(n.value)?'Π':'Σ',[args[0],...bounds]);}
      if(['limit','Limit'].includes(n.value))return mapped('limit');
      if(n.value==='nderivative')return mapped('point-derivative');
      if(['diff','Derivative'].includes(n.value))return mapped('derivative');
      if(n.value==='factorial')return mapped('postfix','!');
      if(n.value==='degree')return mapped('postfix','°');
      if(n.value==='percent')return mapped('postfix','%');
      if(n.value==='log'&&args.length===2)return mapped('logarithm');
      return mapped('function',n.value==='log'?'log':n.value);
    }
    return mapped('text',n.value);
  }
  return convert(parse(source,{allowHoles:true}));
}
