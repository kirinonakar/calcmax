const NS = 'http://www.w3.org/1998/Math/MathML';
function el(tag,children=[],text='') {
  const result = document.createElementNS(NS,tag);
  if (text) result.textContent = text;
  result.append(...children);
  return result;
}
const operator = value => el('mo',[],value);
const row = children => el('mrow',children);
const join = (nodes,separator) => nodes.flatMap((n,i) => i ? [operator(separator),n] : [n]);
function fenced(children,open='(',close=')') { return row([operator(open),...children,operator(close)]); }
export function mathDisplay(tree,digits=10,decimal=false,{engineering=false,grouping=false}={}) {
  function draw(t) {
    if (!t) return el('mtext');
    const args = (t.args || []).map(render), value = t.value || '';
    switch(t.kind) {
      case 'fraction': return el('mfrac',args);
      case 'root': return el('msqrt',args);
      case 'power': return el('msup',[t.args[0]?.kind === 'sum' ? fenced([args[0]]) : args[0],args[1]]);
      case 'sum': return row(args.flatMap((a,i) => i && t.args[i].kind !== 'unary' ? [operator('+'),a] : [a]));
      case 'product': return row(join(args.map((a,i) => t.args[i].kind === 'sum' ? fenced([a]) : a),'·'));
      case 'implicit-product': return row(args);
      case 'parentheses': return fenced(args);
      case 'postfix': return row([...args,operator(value)]);
      case 'hole': {const slot=el('mi',[],'□');slot.classList.add('input-slot');return slot;}
      case 'mixed': return row(args);
      case 'integral': return row([args.length>=4?el('msubsup',[operator('∫'),args[2],args[3]]):operator('∫'),args[0],el('mi',[],'d'),args[1]]);
      case 'derivative': return row([el('mfrac',[args[2]?el('msup',[el('mi',[],'d'),args[2]]):el('mi',[],'d'),row([el('mi',[],'d'),args[2]?el('msup',[args[1],args[2]]):args[1]])]),args[0]]);
      case 'logarithm': return row([el('msub',[el('mi',[],'log'),args[1]]),fenced([args[0]])]);
      case 'unary': return row([operator(value),...args]);
      case 'relation': return row([args[0],operator(value),args[1]]);
      case 'function': return row([el('mi',[],value),fenced(join(args,','))]);
      case 'matrix': return fenced([el('mtable',(t.args || []).map(r => el('mtr',(r.args || []).map(c => el('mtd',[render(c)])))))],'[',']');
      case 'rows': return el('mtable',args.map(a => el('mtr',[el('mtd',[a])])));
      case 'row': return row([el('mtext',[],`${value}: `),...args]);
      case 'list': case 'set': case 'tuple': return fenced(join(args,','),t.kind === 'set' ? '{' : t.kind==='tuple'?'(':'[',t.kind === 'set' ? '}' : t.kind==='tuple'?')':']');
      case 'quantity': return row([...args,el('mtext',[],` ${value}`)]);
      case 'dms': return row(args.flatMap((a,i) => [a,operator(['°','′','″'][i])]));
      case 'symbol': return el('mi',[],value);
      case 'number': case 'text': {
        let shown = value;
        if(engineering && /^-?\d+(?:\.\d+)?(?:e[+-]?\d+)?$/i.test(value)&&/[1-9]/.test(value.split(/e/i)[0])) {
          const [mantissa,exponent='0']=value.replace(/^-/,'').split(/e/i),[whole,fraction='']=mantissa.split('.'),combined=whole+fraction,first=combined.search(/[1-9]/),power=whole.length-first-1+Number(exponent),engPower=Math.floor(power/3)*3,places=power-engPower+1,normalized=combined.slice(first).padEnd(places,'0');
          const mantissaText=(value.startsWith('-')?'-':'')+normalized.slice(0,places)+(normalized.length>places?'.'+normalized.slice(places):'');
          const rendered=mathDisplay({kind:'number',value:mantissaText},digits,true).firstChild;
          return engPower?row([rendered,operator('×'),el('msup',[el('mn',[],'10'),el('mn',[],String(engPower))])]):rendered;
        }
        if (decimal && /^-?\d+\.\d+(?:e[+-]?\d+)?$/i.test(value)) {
          const [mantissa,exponent] = value.split(/e/i), [whole,fraction] = mantissa.split('.');
          // Round text without converting high precision values to IEEE doubles.
          let raw = fraction.slice(0,digits).padEnd(digits,'0');
          let integer = whole;
          if (Number(fraction[digits] || '0') >= 5) {
            const negative = integer.startsWith('-');
            let rounded = (BigInt(integer.replace('-','') + raw) + 1n).toString().padStart(digits+1,'0');
            integer = (negative ? '-' : '') + rounded.slice(0,-digits);
            raw = rounded.slice(-digits);
          }
          raw = raw.replace(/0+$/,'');
          shown = integer + (raw ? '.'+raw : '') + (exponent ? 'e'+exponent : '');
        }
        const number=/^-?[\d.]+(?:e[+-]?\d+)?$/i.test(shown);
        if(grouping&&number){const [mantissa,exp]=shown.split(/e/i),[whole,fraction]=mantissa.split('.');shown=whole.replace(/\B(?=(\d{3})+(?!\d))/g,',')+(fraction?'.'+fraction:'')+(exp?'e'+exp:'');}
        return el(number ? 'mn' : 'mtext',[],shown);
      }
      default: return el('mtext',[],value);
    }
  }
  function render(t){const result=draw(t);if(t?.start!==undefined){result.setAttribute('data-source-start',String(t.start));result.setAttribute('data-source-end',String(t.end));}return result;}
  const math = el('math',[render(tree)]);
  math.setAttribute('display','block');
  return math;
}
