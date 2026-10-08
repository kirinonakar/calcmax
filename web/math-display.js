import {roundNumber} from './display-format.js';
import {expressionTree} from './expression-tree.js';
import {trackMathRoots} from './math-roots.js';
import {latexSymbolLabels} from './parser.js';
const NS = 'http://www.w3.org/1998/Math/MathML';
function el(tag,children=[],text='') {
  const result = document.createElementNS(NS,tag);
  if (text) result.textContent = text;
  result.append(...children);
  return result;
}
const operator = value => el('mo',[],value);
const fractionMinus = () => {const sign=operator('−');sign.setAttribute('rspace','0.18em');return sign;};
const row = children => el('mrow',children);
const join = (nodes,separator) => nodes.flatMap((n,i) => i ? [operator(separator),n] : [n]);
function fenced(children,open='(',close=')') { return row([operator(open),...children,operator(close)]); }
function superscript(base,exponent) {
  // Native msup raises scripts against the whole radical box, including its
  // roof. Offset the ink without changing the two-operand MathML structure.
  const script=el('mpadded',[exponent]);
  script.classList.add('math-exponent');
  const radical=base.localName==='msqrt'||base.localName==='mroot'||base.querySelector('msqrt,mroot');
  script.setAttribute('voffset',radical?'-0.3em':'-0.12em');
  return el('msup',[base,script]);
}
export function mathDisplay(tree,digits=10,decimal=false,{notation='off',grouping=false,roundNumbers=true,engineeringShift=0,showZeroExponent=false}={}) {
  function draw(t,allowNotation) {
    if (!t) return el('mtext');
    const args = (t.args || []).map(child=>render(child,allowNotation&&t.kind==='unary')), value = t.value || '';
    switch(t.kind) {
      case 'call': return draw({...t,kind:({integrate:'integral',diff:'derivative',limit:'limit'})[value]||'function'},allowNotation);
      case 'fraction': {
        // Nested fractions retain normal operand sizes instead of adding
        // another compact MathML script level at every fraction bar.
        const numerator=t.args[0],negative=numerator?.kind==='unary'&&numerator.value==='-';
        const fraction=el('mfrac',negative?[render(numerator.args[0],false),args[1]]:args);
        fraction.setAttribute('displaystyle','true');
        if(!negative)return fraction;
        // A leading minus belongs on the fraction's axis, outside its numerator.
        // Keep the complete fraction range for the editor's after-fraction caret.
        if(t.start!==undefined){fraction.setAttribute('data-source-start',String(t.start));fraction.setAttribute('data-source-end',String(t.end));}
        const sign=fractionMinus();
        if(numerator.start!==undefined){sign.setAttribute('data-source-start',String(numerator.start));sign.setAttribute('data-source-end',String(numerator.args[0].start));}
        return row([sign,fraction]);
      }
      case 'root': case 'indexed-root': {
        const contents=args.map(arg=>{const content=row([arg]);content.classList.add('math-root-content');return content;});
        return el(t.kind==='root'?'msqrt':'mroot',contents);
      }
      case 'power': {
        let base=t.args[0];
        while(base?.kind==='parentheses')base=base.args[0];
        if(base?.kind==='function' && ['sin','cos','tan','sinh','cosh','tanh','sinc'].includes(base.value) && base.args.length===1 && t.args[1]?.kind==='number' && /^\d+$/.test(t.args[1].value) && Number(t.args[1].value)>=2) {
          const name=el('mi',[],base.value);name.setAttribute('mathvariant','normal');
          if(base.start!==undefined){name.setAttribute('data-source-start',String(base.start));name.setAttribute('data-source-end',String(base.start+base.value.length));}
          const powered=row([superscript(name,args[1]),fenced([render(base.args[0],false)])]);
          powered.setAttribute('data-function-power','true');
          return powered;
        }
        return superscript(['sum','product','explicit-product','implicit-product','unary','relation'].includes(t.args[0]?.kind)?fenced([args[0]]):args[0],args[1]);
      }
      case 'sum': return row(args.flatMap((a,i) => i && t.args[i].kind !== 'unary' ? [operator('+'),a] : [a]));
      case 'product': return row(args.flatMap((a,i)=>{const item=t.args[i].kind==='sum'?fenced([a]):a;return i&&!(t.args[i-1].kind==='number'&&t.args[i].kind==='symbol')?[operator('·'),item]:[item];}));
      case 'explicit-product': return row(join(args.map((a,i)=>t.args[i].kind==='sum'?fenced([a]):a),'×'));
      case 'implicit-product': return row(args);
      case 'parentheses': return fenced(args);
      case 'postfix': return row([...args,operator(value)]);
      case 'hole': {const slot=el('mi',[],'□');slot.classList.add('input-slot');return slot;}
      case 'mixed': return row(args);
      case 'integral': return row([args.length>=4?el('msubsup',[operator('∫'),args[2],args[3]]):operator('∫'),args[0],el('mi',[],'d'),args[1]]);
      case 'large-operator': return row([args.length>=4?el('munderover',[operator(value),row([args[1],operator('='),args[2]]),args[3]]):operator(value),args[0]]);
      case 'limit': return row([el('munder',[el('mi',[],'lim'),row([args[1],operator('→'),args[2]])]),args[0]]);
      case 'derivative': return row([el('mfrac',[args[2]?superscript(el('mi',[],'d'),args[2].cloneNode(true)):el('mi',[],'d'),row([el('mi',[],'d'),args[2]?superscript(args[1],args[2]):args[1]])]),args[0]]);
      case 'point-derivative': return row([el('mfrac',[el('mi',[],'d'),row([el('mi',[],'d'),args[1]])]),fenced([args[0]]),el('msub',[operator('|'),row([args[1],operator('='),args[2]])])]);
      case 'logarithm': return row([el('msub',[el('mi',[],'log'),args[1]]),fenced([args[0]])]);
      case 'unary': {
        let argument=t.args[0];
        while(argument?.kind==='parentheses')argument=argument.args[0];
        // The mathematical minus uses the font's MATH axis, matching the fraction rule.
        return row([value==='-'&&argument?.kind==='fraction'?fractionMinus():operator(value),...args]);
      }
      case 'relation': return row([args[0],operator(value==='=='?'=':value),args[1]]);
      case 'function': {
        if(value==='exp')return superscript(el('mi',[],'e'),args[0]);
        if(['abs','Abs'].includes(value))return fenced(args,'|','|');
        const name=el('mi',[],value);if(t.start!==undefined){name.setAttribute('data-source-start',String(t.start));name.setAttribute('data-source-end',String(t.start+value.length));}
        return row([name,fenced(join(args,','))]);
      }
      case 'matrix': {
        const table=el('mtable',(t.args || []).map(r=>el('mtr',(r.args || []).map(c=>el('mtd',[render(c,false)])))));
        if(Number.isInteger(t.augmentedColumn))table.setAttribute('columnlines',Array.from({length:Math.max(0,(t.args?.[0]?.args?.length||0)-1)},(_,index)=>index===t.augmentedColumn-1?'solid':'none').join(' '));
        return fenced([table],'[',']');
      }
      case 'rows': return el('mtable',args.map(a => el('mtr',[el('mtd',[a])])));
      case 'row': return row([el('mtext',[],`${value}: `),...args]);
      case 'list': case 'set': case 'tuple': return fenced(join(args,','),t.kind === 'set' ? '{' : t.kind==='tuple'?'(':'[',t.kind === 'set' ? '}' : t.kind==='tuple'?')':']');
      case 'quantity': return row([...args,el('mtext',[],` ${value}`)]);
      case 'dms': return row(args.flatMap((a,i) => [a,operator(['°','′','″'][i])]));
      case 'symbol': return el('mi',[],latexSymbolLabels[value]||{oo:'∞',E:'e',I:'i'}[value]||value);
      case 'fixed-number': return el('mn',[],value);
      case 'input-operator': {const result=operator(value==='=='?'=':value);result.setAttribute('form','infix');return result;}
      case 'input-text': return el('mtext',[],value);
      case 'number': case 'text': {
        let shown = value;
        if(allowNotation&&notation!=='off'&&/^-?\d+(?:\.\d+)?(?:e[+-]?\d+)?$/i.test(value)&&/[1-9]/.test(value.split(/e/i)[0])) {
          const [mantissa,exponent='0']=value.replace(/^-/,'').split(/e/i),[whole,fraction='']=mantissa.split('.'),combined=whole+fraction,first=combined.search(/[1-9]/),power=whole.length-first-1+Number(exponent),engPower=(notation==='eng'?Math.floor(power/3)*3:power)+engineeringShift,places=power-engPower+1;
          if(Math.abs(engPower)>40000||Math.abs(places)>40000)return el('mn',[],roundNumber(value,digits));
          const normalized=combined.slice(first).padEnd(Math.max(0,places),'0');
          const mantissaText=(value.startsWith('-')?'-':'')+(places<=0?'0.'+'0'.repeat(-places)+normalized:normalized.slice(0,places)+(normalized.length>places?'.'+normalized.slice(places):''));
          const rendered=mathDisplay({kind:'number',value:mantissaText},digits,true,{grouping}).firstChild;
          return engPower||showZeroExponent?row([rendered,operator('×'),superscript(el('mn',[],'10'),el('mn',[],String(engPower)))]):rendered;
        }
        if(roundNumbers)shown=roundNumber(value,digits);
        const number=/^-?[\d.]+(?:e[+-]?\d+)?$/i.test(shown);
        if(number&&/e/i.test(shown)){
          const [mantissa,exponent]=shown.split(/e/i);
          return row([el('mn',[],mantissa),operator('×'),superscript(el('mn',[],'10'),el('mn',[],String(Number(exponent))))]);
        }
        if(!number&&t.kind==='text'&&value.length<8192&&!value.includes('\n'))try{return draw(expressionTree(value),false);}catch{}
        if(grouping&&number){const [mantissa,exp]=shown.split(/e/i),[whole,fraction]=mantissa.split('.');shown=whole.replace(/\B(?=(\d{3})+(?!\d))/g,',')+(fraction?'.'+fraction:'')+(exp?'e'+exp:'');}
        return el(number ? 'mn' : 'mtext',[],shown);
      }
      default: return el('mtext',[],value);
    }
  }
  function render(t,allowNotation=true){const result=draw(t,allowNotation);if(t?.start!==undefined){result.setAttribute('data-source-start',String(t.start));result.setAttribute('data-source-end',String(t.end));}return result;}
  const math = el('math',[render(tree)]);
  math.setAttribute('display','block');
  math.setAttribute('displaystyle','true');
  trackMathRoots(math);
  return math;
}
