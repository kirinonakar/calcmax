import {roundNumber} from './display-format.js';

// Copy the displayed intermediate expression, including unevaluated grouping.
export function equationFormulaText(node,digits=10){
  const {kind,value='',args=[]}=node;
  const parts=args.map(arg=>equationFormulaText(arg,digits));
  const part=index=>parts[index]||'';
  const operand=index=>['sum','product','unary','relation','fraction','power'].includes(args[index]?.kind)?`(${part(index)})`:part(index);
  switch(kind){
    case 'number':case 'text':case 'float':return roundNumber(value,digits);
    case 'parentheses':case 'group':return `(${part(0)})`;
    case 'unary':return value+operand(0);
    case 'sum':return parts.map((text,index)=>!index||args[index].kind==='unary'?text:'+'+text).join('');
    case 'product':return args.map((_,index)=>operand(index)).join('*');
    case 'fraction':return operand(0)+'/'+operand(1);
    case 'power':return operand(0)+'^'+operand(1);
    case 'root':return `sqrt(${part(0)})`;
    case 'relation':return parts.join(` ${value==='=='?'=':value} `);
    case 'binary':return operand(0)+value+operand(1);
    case 'call':case 'function':case 'frozen_call':return `${value}(${parts.join(',')})`;
    case 'matrix':case 'list':return `[${parts.join(',')}]`;
    case 'tuple':return `(${parts.join(',')})`;
    case 'set':return `{${parts.join(',')}}`;
    case 'row-operation':return parts.join(' → ');
    case 'row':return `${value}: ${part(0)}`;
    case 'rows':return parts.join('\n');
    case 'quantity':return `${part(0)} ${value}`;
    case 'restricted':return part(0);
    default:return value;
  }
}
