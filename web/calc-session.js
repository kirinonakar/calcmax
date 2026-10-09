import {computationLimitsRemoved} from './computation-limits.js';
// Android startCalc's source-AST traversal. Numeric stored values remain editable
// inputs; source formulas stored by STO expose their underlying input variables.
const constants=new Set('pi e i I oo true false Ans c0 hP hbar G qe NA kB0 me mp0 epsilon0 mu0 Z0 sigmaSB'.split(' '));
const binders=new Set('integrate diff nderivative limit sum product solve nsolve nintegrate series'.split(' '));
export function calcVariables(tree,variables={}) {
  const names=new Set();let visited=0;
  function collect(node,bound=new Set(),expanding=new Set(),depth=0){
    if(!node||!computationLimitsRemoved()&&(depth>96||++visited>12000))throw new Error('Expression complexity limit');
    if(node.kind==='symbol'||node.kind==='snapshot_symbol'){
      const name=node.value;
      if(constants.has(name)||bound.has(name))return false;
      const definition=Object.hasOwn(variables,name)?variables[name]:null;
      if(definition&&!expanding.has(name)&&collect(definition,bound,new Set([...expanding,name]),depth+1))return true;
      // A definition with no free symbols is a numeric value. Prompt for the
      // variable itself so stored numbers remain editable in CALC.
      names.add(name);return true;
    }
    const args=node.args||[];
    const binder=node.kind==='call'&&binders.has(node.value)?args[1]:null;
    const targets=binder?.kind==='symbol'?[binder]:['tuple','list'].includes(binder?.kind)?(binder.kind==='tuple'?binder.args.slice(0,1):binder.args).filter(n=>n.kind==='symbol'):[];
    const boundNames=targets.map(n=>n.value);let found=false;
    args.forEach((child,index)=>{
      if(index===1&&boundNames.length){if(binder.kind==='tuple')binder.args.slice(1).forEach(n=>{if(collect(n,bound,expanding,depth+1))found=true;});return;}
      if(collect(child,index===0?new Set([...bound,...boundNames]):bound,expanding,depth+1))found=true;
    });
    return found;
  }
  collect(tree);return [...names];
}
// Result ASTs freeze symbolic names for ordinary Ans/STO reuse. During CALC,
// those names are explicit inputs and must resolve to the accepted values.
export function calcBindings(variables) {
  let visited=0;
  function editable(node,depth=0){
    if(!node||!computationLimitsRemoved()&&(depth>96||++visited>12000))throw new Error('Expression complexity limit');
    return {...node,...(node.kind==='snapshot_symbol'?{kind:'symbol'}:{}),...(node.args?{args:node.args.map(child=>editable(child,depth+1))}:{})};
  }
  return Object.fromEntries(Object.entries(variables).map(([name,tree])=>[name,name==='Ans'?tree:editable(tree)]));
}
