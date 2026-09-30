import {parse} from './parser.js';
import {astSource} from './ast-source.js';
const reserved=new Set(["sinc", "sin", "cos", "tan", "asin", "acos", "atan", "arcsin", "arccos", "arctan", "sinh", "cosh", "tanh", "asinh", "acosh", "atanh", "arcsinh", "arsinh", "arccosh", "arcosh", "arctanh", "artanh", "atan2", "arctan2", "sqrt", "cbrt", "nthroot", "abs", "floor", "ceil", "round", "roundh", "sign", "factorial", "gamma", "ln", "log", "exp", "erf", "erfc", "Ei", "Si", "Ci", "zeta", "re", "im", "arg", "conj", "polar", "rectpolar", "simplify", "expand", "factor", "collect", "diff", "integrate", "limit", "series", "solve", "nsolve", "sum", "product", "piecewise", "subs", "gcd", "lcm", "nCr", "nPr", "prime", "isprime", "factorint", "divisors", "percent", "degree", "quotient", "remainder", "mod", "divmod", "det", "inverse", "transpose", "rank", "trace", "rref", "ref", "lu", "eigenvalues", "eigenvectors", "norm", "normalize", "dot", "cross", "angle", "projection", "linsolve", "mean", "median", "variance", "stdev", "sumdata", "quartiles", "stats", "regression", "convert", "qty", "nintegrate", "nderivative", "minimum", "maximum", "rad", "gradian", "pinv", "ctranspose", "svd", "roots", "real_roots", "rsolve", "lambertw", "beta", "digamma", "polygamma", "fibonacci", "lucas", "bernoulli", "harmonic", "subfactorial", "totient", "divisor_sigma", "primepi", "nextprime", "prevprime", "besselj", "bessely", "besseli", "besselk", "normpdf", "normcdf", "invnorm", "tpdf", "tcdf", "invt", "chi2pdf", "chi2cdf", "fpdf", "fcdf", "binompdf", "binomcdf", "poissonpdf", "poissoncdf", "geometpdf", "geometcdf", "exppdf", "expcdf", "unifpdf", "unifcdf", "gammapdf", "gammacdf", "betapdf", "betacdf", "lognormpdf", "lognormcdf", "ttest", "ttest2", "ttestpaired", "ztest", "ztest2", "chi2test", "chi2independence", "fisherexact", "anova", "shapiro", "tinterval", "zinterval", "tvmfv", "tvmpv", "tvmpmt", "tvmn", "tvmrate", "npv", "irr", "amort", "cagr", "normalpdf", "normalcdf"]);

export function defineFunction(name,parameters,source){
  if(!/^[A-Za-z][A-Za-z0-9_]*$/.test(name)||reserved.has(name))throw new Error('This function name is invalid or reserved');
  if(typeof parameters==='string')parameters=parameters.split(',').map(s=>s.trim());
  if(!Array.isArray(parameters)||!parameters.length||parameters.some(p=>!/^[A-Za-z][A-Za-z0-9_]*$/.test(p))||new Set(parameters).size!==parameters.length)throw new Error('Enter distinct valid parameter names');
  return {parameters,source,body:parse(source)};
}
export function encodeFunctions(functions){return JSON.stringify({format:'calcmax.functions',version:1,functions:Object.fromEntries(Object.entries(functions).map(([name,f])=>[name,{parameters:f.parameters,source:f.source||astSource(f.body)}]))},null,2);}
export function decodeFunctions(text){
  const root=JSON.parse(text.replace(/^\uFEFF/,''));if(!root||typeof root!=='object'||Array.isArray(root))throw new Error('Invalid function file');
  if(root.format&&root.format!=='calcmax.functions')throw new Error('Unsupported function file');
  const entries=root.format?root.functions:root,functions={};let skipped=0;
  for(const [name,f] of Object.entries(entries||{}))try{functions[name]=defineFunction(name,f.parameters,f.source||astSource(f.body));}catch{skipped++;}
  if(!Object.keys(functions).length)throw new Error('No valid functions in this file');
  return {functions,skipped};
}
