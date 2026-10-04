// Port of math/Expression.kt. User math is converted to the existing validated AST,
// never evaluated as JavaScript or passed to SymPy's parse_expr.
const letter = c => !!c && /\p{L}/u.test(c);
const digit = c => !!c && /[0-9]/.test(c);
const numeric = value => /^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(value);
const aliases = {"×":"*", "·":"*", "÷":"/", "−":"-", "π":"pi", "θ":"theta", "∞":"oo", "**":"^", "≤":"<=", "≥":">=", "→":"->"};
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
  const commands = new Set(['int','lim','frac','dfrac','tfrac','sqrt','sin','cos','tan','arcsin','arccos','arctan','ln','log','exp','pi','theta','infty','times','cdot','left','right','quad','qquad']);
  let source = input.trim();
  if (!/^\\[[(]|^\$|\^\s*\{/.test(source) && ![...source.matchAll(/\\([A-Za-z]+)/g)].some(match=>commands.has(match[1]))) return input;
  for (const [open,close] of [['\\[','\\]'],['\\(','\\)'],['$$','$$'],['$','$']]) {
    if (source.length >= open.length+close.length && source.startsWith(open) && source.endsWith(close)) {
      source=source.slice(open.length,-close.length);break;
    }
  }
  function compactExpression(source) {
    const tree=parse(source),groups=[];
    const shape=n=>n.kind==='group'?shape(n.args[0]):[n.kind,n.value,n.args.map(shape)];
    const expected=JSON.stringify(shape(tree));
    function collect(n){if(n.kind==='group')groups.push(n);n.args.forEach(collect);}
    collect(tree);
    const removed=new Set();
    let result=source;
    // Remove only parentheses whose absence preserves the complete parsed expression.
    for(const group of groups.sort((a,b)=>b.start-a.start)) {
      removed.add(group.start);removed.add(group.end-1);
      const candidate=source.split('').filter((_,index)=>!removed.has(index)).join('');
      try{if(JSON.stringify(shape(parse(candidate)))===expected){result=candidate;continue;}}catch{}
      removed.delete(group.start);removed.delete(group.end-1);
    }
    return result;
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
    function skipSpacing() {
      while (i<text.length) {
        if (/\s/.test(text[i])) i++;
        else if (text[i]==='\\' && ',;! '.includes(text[i+1] || '\0')) i+=2;
        else break;
      }
    }
    // Read one logarithm base/argument before whitespace is removed.
    function argument(singleToken=false) {
      skipSpacing();
      const start=i,c=text[i];
      if (c==='{' || c==='(') group(c,c==='{'?'}':')');
      else if (c==='\\') {
        const command=/^[A-Za-z]+/.exec(text.slice(i+1))?.[0];
        if (!command) throw new SyntaxError('Expected LaTeX argument');
        i+=command.length+1;
        if (['frac','dfrac','tfrac'].includes(command)) {group();group();}
        else if (command==='sqrt') {skipSpacing();if(text[i]==='[')group('[',']');group();}
        else if (command==='left') {skipSpacing();group('(',')');}
        else if (command==='log') {skipSpacing();if(text[i]==='_'){i++;argument(true);}argument();}
        else if (['sin','cos','tan','arcsin','arccos','arctan','ln','exp'].includes(command)) {skipSpacing();if(text[i]==='^'){i++;argument(true);}argument();}
      } else {
        if (!letter(c) && !digit(c) && c!=='.') throw new SyntaxError('Expected LaTeX argument');
        i++;
        if (!singleToken) {
          if (digit(c) || c==='.') while(digit(text[i]) || text[i]==='.')i++;
          else while(letter(text[i]) || digit(text[i]))i++;
        }
      }
      if (!singleToken) {
        const end=i;
        skipSpacing();
        if (text[i]==='^') {i++;argument(true);} else i=end;
      }
      return text.slice(start,i);
    }
    function logOperand(raw) {
      let converted=body(raw);
      // The call's comma/closing parenthesis already delimits each operand.
      while (parse(converted).kind==='group') converted=converted.slice(1,-1);
      return converted;
    }
    // A limit applies to the remaining expression within its enclosing group.
    function limitExpression() {
      const start=i;
      let depth=0;
      while(i<text.length) {
        const c=text[i];
        if(!depth && (')]}=<>,'.includes(c) || text.startsWith('\\right',i) && !letter(text[i+6])))break;
        if('([{'.includes(c))depth++;
        else if(')]}'.includes(c))depth--;
        i++;
      }
      return compactExpression(body(text.slice(start,i)));
    }
    while (i < text.length) {
      const c = text[i++];
      if (c !== '\\') { result += c === '{' ? '(' : c === '}' ? ')' : c; continue; }
      if (',;! '.includes(text[i] || '\0')) { i++; continue; }
      const command = /^[A-Za-z]+/.exec(text.slice(i))?.[0];
      if (!command) throw new SyntaxError('Incomplete LaTeX command');
      i += command.length;
      if (command==='lim') {
        skipSpacing();
        if(text[i++]!=='_')throw new SyntaxError('Expected LaTeX limit approach');
        const approach=group().split(/\\(?:to|rightarrow)\b|->|→/);
        if(approach.length!==2)throw new SyntaxError('Expected LaTeX limit arrow');
        const variable=body(approach[0]);
        if(parse(variable).kind!=='symbol')throw new SyntaxError('Expected limit variable');
        let point=approach[1].trim(),direction='';
        const side=/\^\s*(?:\{\s*([+-])\s*\}|([+-]))\s*$/.exec(point);
        if(side){direction=`,${(side[1]||side[2])==='+'?'right':'left'}`;point=point.slice(0,side.index);}
        point=body(point);parse(point);
        result+=`limit(${limitExpression()},${variable},${point}${direction})`;
      }
      else if (['frac','dfrac','tfrac'].includes(command)) { const top = group(), bottom = group(); result += `((${body(top)})/(${body(bottom)}))`; }
      else if (command === 'sqrt') {
        while (/\s/.test(text[i] || '\0')) i++;
        const degree=text[i]==='[' ? body(group('[',']')) : null;
        const argument=body(group());
        result += degree===null ? `sqrt(${argument})` : `nthroot(${argument},${degree})`;
      }
      else if (command === 'log') {
        skipSpacing();
        let base=null;
        if (text[i]==='_') {i++;base=logOperand(argument(true));skipSpacing();}
        // Keep the calculator's existing explicit log(value,base) syntax.
        if (base===null && (text[i]==='(' || text.startsWith('\\left',i))) result+='log';
        else result+=`log(${logOperand(argument())}${base===null?'':`,${base}`})`;
      }
      else if (['sin','cos','tan','arcsin','arccos','arctan','ln','exp'].includes(command)) {
        const name={arcsin:'asin',arccos:'acos',arctan:'atan'}[command]||command;
        skipSpacing();
        let exponent=null;
        if(text[i]==='^'){i++;exponent=body(argument(true));skipSpacing();}
        if(exponent===null && (text[i]==='(' || text.startsWith('\\left',i)))result+=name;
        else result+=`${name}(${body(argument())})${exponent===null?'':`^(${exponent})`}`;
      }
      else if (['left','right','quad','qquad'].includes(command)) continue;
      else if (['pi','infty','times','cdot','arcsin','arccos','arctan'].includes(command)) result += {pi:'pi',infty:'oo',times:'*',cdot:'*',arcsin:'asin',arccos:'acos',arctan:'atan'}[command];
      else if (['sin','cos','tan','ln','log','exp','theta'].includes(command)) result += command;
      else throw new SyntaxError(`Unsupported LaTeX command: ${command}`);
    }
    return result.replace(/\s+/g,'');
  }
  source = source.replace(/\\int\s*_\s*\{([^{}]+)\}\s*\^\s*\{([^{}]+)\}([\s\S]*?)(?:\\[,;! ]\s*)?d\s*([A-Za-z])(?=\s*(?:=|$))/g,(_,low,high,expr,v) => `integrate(${body(expr)},${v},${body(low)},${body(high)})`);
  const converted = body(source);
  parse(converted);
  return converted;
}
