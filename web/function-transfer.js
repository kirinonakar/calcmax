import {parse} from './parser.js';
import {astSource} from './ast-source.js';
const reserved=new Set(["sinc", "sin", "cos", "tan", "asin", "acos", "atan", "arcsin", "arccos", "arctan", "sinh", "cosh", "tanh", "asinh", "acosh", "atanh", "arcsinh", "arsinh", "arccosh", "arcosh", "arctanh", "artanh", "atan2", "arctan2", "sqrt", "cbrt", "nthroot", "abs", "floor", "ceil", "round", "roundh", "sign", "factorial", "gamma", "polylog", "ln", "log", "exp", "erf", "erfc", "Ei", "Si", "Ci", "zeta", "re", "im", "arg", "conj", "polar", "rectpolar", "simplify", "expand", "factor", "collect", "diff", "integrate", "limit", "series", "solve", "nsolve", "sum", "product", "piecewise", "subs", "gcd", "lcm", "nCr", "nPr", "prime", "isprime", "factorint", "divisors", "percent", "degree", "quotient", "remainder", "mod", "divmod", "det", "inverse", "transpose", "rank", "trace", "rref", "ref", "lu", "eigenvalues", "eigenvectors", "norm", "normalize", "dot", "cross", "angle", "projection", "linsolve", "mean", "median", "variance", "stdev", "sumdata", "quartiles", "stats", "survivalanalysis", "regression", "convert", "qty", "nintegrate", "nderivative", "minimum", "maximum", "rad", "gradian", "pinv", "ctranspose", "svd", "roots", "real_roots", "rsolve", "lambertw", "beta", "digamma", "polygamma", "fibonacci", "lucas", "bernoulli", "harmonic", "subfactorial", "totient", "divisor_sigma", "primepi", "nextprime", "prevprime", "besselj", "bessely", "besseli", "besselk", "normpdf", "normcdf", "invnorm", "tpdf", "tcdf", "invt", "chi2pdf", "chi2cdf", "fpdf", "fcdf", "binompdf", "binomcdf", "poissonpdf", "poissoncdf", "geometpdf", "geometcdf", "exppdf", "expcdf", "unifpdf", "unifcdf", "gammapdf", "gammacdf", "betapdf", "betacdf", "lognormpdf", "lognormcdf", "hgeompdf", "hgeomcdf", "nbinompdf", "nbinomcdf", "weibullpdf", "weibullcdf", "cauchypdf", "cauchycdf", "invcauchy", "ttest", "ttest2", "ttestpaired", "ztest", "ztest2", "propztest", "propztest2", "chi2test", "chi2independence", "fisherexact", "anova", "shapiro", "wilcoxon", "mannwhitney", "kruskal", "tinterval", "zinterval", "tvmfv", "tvmpv", "tvmpmt", "tvmn", "tvmrate", "npv", "irr", "amort", "cagr", "normalpdf", "normalcdf"]);

export function defineFunction(name,parameters,source){
  if(!/^[A-Za-z][A-Za-z0-9_]*$/.test(name)||reserved.has(name))throw new Error('This function name is invalid or reserved');
  if(typeof parameters==='string')parameters=parameters.split(',').map(s=>s.trim());
  if(!Array.isArray(parameters)||!parameters.length||parameters.some(p=>!/^[A-Za-z][A-Za-z0-9_]*$/.test(p))||new Set(parameters).size!==parameters.length)throw new Error('Enter distinct valid parameter names');
  return {parameters,source,body:parse(source)};
}
// Prefer an existing leading definition; built-in calls can be saved with a trailing name.
export function inputAssignment(tree){
  if(!['=',':='].includes(tree.value)||tree.args?.length!==2)return null;
  const [left,right]=tree.args;
  const functionHead=node=>node.kind==='call'&&node.args.every(arg=>arg.kind==='symbol');
  const constants=new Set(['pi','e','i','I','oo','Ans','c0','hP','hbar','G','qe','NA','kB0','me','mp0']);
  const forward=left.kind==='symbol'||functionHead(left)&&(!reserved.has(left.value)||right.kind==='symbol'&&left.args.some(arg=>arg.value===right.value));
  const target=forward?left:right.kind==='symbol'&&!constants.has(right.value)||functionHead(right)&&!reserved.has(right.value)?right:left;
  const expression=target===right?left:right;
  if(target.kind==='symbol')return {expression,name:target.value,parameters:null};
  if(functionHead(target))return {expression,name:target.value,parameters:target.args.map(arg=>arg.value)};
  return null;
}
export function graphExpression(tree){
  const head=tree.args?.[0];
  return tree.kind==='relation'&&['=','=='].includes(tree.value)&&tree.args.length===2&&
    head?.kind==='call'&&!reserved.has(head.value)&&head.args.length===1&&head.args[0].kind==='symbol'&&head.args[0].value==='x'?tree.args[1]:tree;
}
// Result-to-function input is deliberately limited so ordinary equations keep their meaning.
export function resultTarget(tree){
  if(!['=',':='].includes(tree.value)||tree.args?.length!==2)return null;
  const [left,right]=tree.args;
  if(left.kind==='call'&&left.value==='Ans'){
    if(!left.args.every(arg=>arg.kind==='symbol'))throw new Error('Enter distinct valid parameter names');
    const definition=defineFunction('Ans',left.args.map(arg=>arg.value),'0');
    return {expression:right,parameters:definition.parameters};
  }
  const reverse=right.kind==='call'&&['diff','integrate'].includes(right.value)&&(left.kind==='call'||left.kind==='symbol'&&left.value==='Ans');
  const [expression,target]=reverse?[right,left]:[left,right];
  const calculus=expression.kind==='call'&&['diff','integrate'].includes(expression.value);
  if(!(calculus||expression.kind==='symbol'&&expression.value==='Ans'))return null;
  if(target.kind==='symbol'&&target.value==='Ans'&&calculus)return {expression};
  if(target.kind!=='call')return null;
  if(!target.args.every(arg=>arg.kind==='symbol'))throw new Error('Enter distinct valid parameter names');
  const definition=defineFunction(target.value,target.args.map(arg=>arg.value),'0');
  return {expression,name:target.value,parameters:definition.parameters};
}
export function resultFunction(name,parameters,body){
  function editable(node){return {...node,...(node.kind==='snapshot_symbol'&&parameters.includes(node.value)?{kind:'symbol'}:{}),...(node.args?{args:node.args.map(editable)}:{})};}
  const stored=editable(body),source=astSource(stored);
  defineFunction(name,parameters,source);
  return {parameters,source,body:stored};
}
function answerKey(value){
  if(Array.isArray(value))return '['+value.map(answerKey).join(',')+']';
  if(value&&typeof value==='object')return '{'+Object.keys(value).sort().map(key=>JSON.stringify(key)+':'+answerKey(value[key])).join(',')+'}';
  return JSON.stringify(value);
}
export function removeExpiredAnswerFunctions(functions,answer){
  const current=answerKey(answer);let changed=false;
  for(const [name,definition] of Object.entries(functions)){
    if(definition&&Object.hasOwn(definition,'answerSource')&&answerKey(definition.answerSource)!==current){delete functions[name];changed=true;}
  }
  return changed;
}
export function encodeFunctions(functions){return JSON.stringify({format:'symvacas.functions',version:1,functions:Object.fromEntries(Object.entries(functions).map(([name,f])=>[name,{parameters:f.parameters,source:f.source||astSource(f.body),...(Object.hasOwn(f,'answerSource')?{answerSource:f.answerSource}:{})}]))},null,2);}
export function decodeFunctions(text){
  const root=JSON.parse(text.replace(/^\uFEFF/,''));if(!root||typeof root!=='object'||Array.isArray(root))throw new Error('Invalid function file');
  if(root.format&&root.format!=='symvacas.functions')throw new Error('Unsupported function file');
  const entries=root.format?root.functions:root,functions={};let skipped=0;
  for(const [name,f] of Object.entries(entries||{}))try{functions[name]={...defineFunction(name,f.parameters,f.source||astSource(f.body)),...(Object.hasOwn(f,'answerSource')?{answerSource:f.answerSource}:{})};}catch{skipped++;}
  if(!Object.keys(functions).length)throw new Error('No valid functions in this file');
  return {functions,skipped};
}
