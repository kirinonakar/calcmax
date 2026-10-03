// Port of math/Expression.kt. User math is converted to the existing validated AST,
// never evaluated as JavaScript or passed to SymPy's parse_expr.
const letter = c => !!c && /\p{L}/u.test(c);
const digit = c => !!c && /[0-9]/.test(c);
const numeric = value => /^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(value);
const aliases = {"×":"*", "·":"*", "÷":"/", "−":"-", "π":"pi", "∞":"oo", "**":"^", "≤":"<=", "≥":">=", "→":"->"};
function scan(source) {
  if (source.length > 8192) throw new SyntaxError('Expression exceeds 8192 characters');
  const tokens = [];
  let i = 0;
  while (i < source.length) {
    if (/\s/.test(source[i])) { i++; continue; }
    const start = i++, c = source[start];
    if (digit(c) || c === '.') {
      while (digit(source[i]) || source[i] === '.') i++;
      if (/[eE]/.test(source[i] || '') && (digit(source[i+1]) || '+-'.includes(source[i+1] || '\0'))) {
        i++;
        if ('+-'.includes(source[i] || '\0')) i++;
        while (digit(source[i])) i++;
      }
    } else if (letter(c) && !'π∞√'.includes(c)) {
      while (letter(source[i]) || digit(source[i]) || source[i] === '_') i++;
    } else if (['<=','>=','!=','==',':=','**','->'].includes(source.slice(start,i+1))) i++;
    const text = source.slice(start,i);
    tokens.push({text: aliases[text] || text, start, end:i});
  }
  tokens.push({text:'', start:i, end:i});
  return tokens;
}
const node = (kind,value='',args=[],start=0,end=0,displayOperator='') => ({kind,value,args,start,end,...(displayOperator ? {displayOperator} : {})});
export {scan as scanInputTokens};
export function closeInputBrackets(source){
  const stack=[],pairs={'(' : ')','[':']','{':'}'};
  for(const character of source){if(pairs[character])stack.push(pairs[character]);else if(')]}'.includes(character)){if(stack.pop()!==character)return source;}}
  return source+stack.reverse().join('');
}
export function parse(source,{allowHoles=false}={}) {
  const tokens = scan(source);
  let index = 0, depth = 0, count = 0;
  const current = () => tokens[index] || tokens[tokens.length-1];
  const take = () => tokens[index++];
  const fail = message => { throw new SyntaxError(`Syntax ERROR at ${current().start+1}: ${message}`); };
  const expect = text => { if(allowHoles&&!current().text&&[')',']','}'].includes(text))return {...current(),text};if (current().text !== text) fail(`Expected '${text}'`); return take(); };
  function dmsComponent() {
    const saved = index, first = take();
    let raw = first.text, last = first;
    if (['+','-'].includes(raw)) { last = take(); raw += last.text; }
    if (!numeric(raw)) { index = saved; return null; }
    return node('number',raw,[],first.start,last.end);
  }
  function tryDms(left) {
    const saved = index;
    take();
    if (!(digit(current().text[0]) || current().text[0] === '.' || (['+','-'].includes(current().text) && (digit(tokens[index+1]?.text[0]) || tokens[index+1]?.text[0] === '.')))) { index = saved; return null; }
    const minute = dmsComponent();
    if (!minute) { index = saved; return null; }
    expect('′');
    const second = dmsComponent();
    if (!second) fail('Enter seconds');
    return node('sexagesimal','',[left,minute,second],left.start,expect('″').end);
  }
  function expression(min) {
    if(allowHoles&&['',')',']','}',','].includes(current().text))return node('hole','',[],current().start,current().start);
    if (++depth > 96 || ++count > 2048) fail('Expression complexity limit');
    const first = take();
    let left;
    if (['+','-','√'].includes(first.text)) {
      const arg = expression(25);
      left = node(first.text === '√' ? 'call' : 'unary',first.text === '√' ? 'sqrt' : first.text,[arg],first.start,arg.end);
    } else if (['(','[','{'].includes(first.text)) {
      const close = {'(':')','[':']','{':'}'}[first.text], args = [];
      let tuple = false;
      if (first.text === '(' || current().text !== close) {
        args.push(expression(0));
        while (current().text === ',') {
          take(); tuple = true;
          if (first.text === '(' && current().text === close) break;
          args.push(expression(0));
        }
      }
      left = node(first.text === '(' ? (tuple ? 'tuple' : 'group') : first.text === '[' ? 'list' : 'set','',args,first.start,expect(close).end);
    } else if (digit(first.text[0]) || first.text[0] === '.') {
      if (!numeric(first.text)) fail('Invalid number');
      left = node('number',first.text,[],first.start,first.end);
    } else if (letter(first.text[0])) {
      if (current().text === '(') {
        take(); const args = [];
        if (current().text === ')' && first.text !== 'rnd'&&!allowHoles) fail('Enter a function argument');
        if (current().text !== ')' || allowHoles&&first.text!=='rnd') {
          args.push(expression(0));
          while (current().text === ',') { take(); args.push(expression(0)); }
        }
        left = node('call',first.text,args,first.start,expect(')').end);
      } else left = node('symbol',first.text,[],first.start,first.end);
    } else fail('Expected an expression');
    while (true) {
      const op = current().text;
      if (op === '°' && min <= 40) { const dms = tryDms(left); if (dms) { left = dms; continue; } }
      if (['!','%','°','²','³'].includes(op) && min <= 40) {
        const end = take().end;
        left = ['²','³'].includes(op) ? node('binary','^',[left,node('number',op === '²' ? '2' : '3')],left.start,end) : node('call',{'!':'factorial','%':'percent','°':'degree'}[op],[left],left.start,end);
        continue;
      }
      const implicit = !!op && (op === '(' || letter(op[0]) && op !== 'mod' || op === '√' || op === '[' && left.kind === 'list');
      const actual = implicit ? '*' : op;
      const binding = {':=':1,'=':5,'==':5,'<':5,'>':5,'<=':5,'>=':5,'!=':5,'->':5,'+':10,'-':10,'*':20,'/':20,'mod':20,'∠':20,'^':30}[actual] ?? -1;
      if (binding < min) break;
      const operator = implicit ? null : take();
      const right = expression(['^',':='].includes(actual) ? binding : binding+1);
      left = node(binding === 5 ? 'relation' : 'binary',actual,[left,right],left.start,right.end,actual === '/' && operator && source[operator.start] === '÷' ? '÷' : implicit ? '∘' : '');
    }
    depth--;
    return left;
  }
  const tree = expression(0);
  if (current().text) fail(`Unexpected '${current().text}'`);
  return tree;
}

// Equivalent supported LaTeX subset to math/LatexInput.kt.
export function latexInput(input) {
  const commands = new Set(['int','frac','dfrac','tfrac','sqrt','sin','cos','tan','arcsin','arccos','arctan','ln','log','exp','pi','infty','times','cdot','left','right','quad','qquad']);
  let source = input.trim();
  if (!/^\\[[(]|^\$|\^\s*\{/.test(source) && ![...source.matchAll(/\\([A-Za-z]+)/g)].some(match=>commands.has(match[1]))) return input;
  for (const [open,close] of [['\\[','\\]'],['\\(','\\)'],['$$','$$'],['$','$']]) {
    if (source.length >= open.length+close.length && source.startsWith(open) && source.endsWith(close)) {
      source=source.slice(open.length,-close.length);break;
    }
  }
  function body(text) {
    let i = 0, result = '';
    function group(open='{',close='}') {
      while (/\s/.test(text[i] || '\0')) i++;
      if (text[i++] !== open) throw new SyntaxError('Expected LaTeX group');
      const start = i; let depth = 1, braces = 0;
      while (i < text.length && depth) {
        const character=text[i++];
        if (open==='[' && character==='{') braces++;
        else if (open==='[' && character==='}') {if (!braces) throw new SyntaxError('Unmatched LaTeX group');braces--;}
        else if (!braces) {if (character===open) depth++;if (character===close) depth--;}
      }
      if (depth) throw new SyntaxError('Unclosed LaTeX group');
      return text.slice(start,i-1);
    }
    while (i < text.length) {
      const c = text[i++];
      if (c !== '\\') { result += c === '{' ? '(' : c === '}' ? ')' : c; continue; }
      if (',;! '.includes(text[i] || '\0')) { i++; continue; }
      const command = /^[A-Za-z]+/.exec(text.slice(i))?.[0];
      if (!command) throw new SyntaxError('Incomplete LaTeX command');
      i += command.length;
      if (['frac','dfrac','tfrac'].includes(command)) { const top = group(), bottom = group(); result += `((${body(top)})/(${body(bottom)}))`; }
      else if (command === 'sqrt') {
        while (/\s/.test(text[i] || '\0')) i++;
        const degree=text[i]==='[' ? body(group('[',']')) : null;
        const argument=body(group());
        result += degree===null ? `sqrt(${argument})` : `nthroot(${argument},${degree})`;
      }
      else if (['left','right','quad','qquad'].includes(command)) continue;
      else if (['pi','infty','times','cdot','arcsin','arccos','arctan'].includes(command)) result += {pi:'pi',infty:'oo',times:'*',cdot:'*',arcsin:'asin',arccos:'acos',arctan:'atan'}[command];
      else if (['sin','cos','tan','ln','log','exp'].includes(command)) result += command;
      else throw new SyntaxError(`Unsupported LaTeX command: ${command}`);
    }
    return result.replace(/\s+/g,'');
  }
  source = source.replace(/\\int_\{([^{}]+)\}\^\{([^{}]+)\}([\s\S]*?)(?:\\[,;! ]\s*)?d([A-Za-z])(?=\s*(?:=|$))/g,(_,low,high,expr,v) => `integrate(${body(expr)},${v},${body(low)},${body(high)})`);
  const converted = body(source);
  parse(converted);
  return converted;
}
