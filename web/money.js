export function tipCommand({bill,percent='15',fixed='15',tax='0',people='2',method='percent',whole=true}){
  const clean=text=>{text=String(text).replace(/,/g,'');if(text.length>128||!/^\+?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(text))throw new Error('Enter non-negative amounts and percentages');return text;};
  bill=clean(bill);percent=clean(percent);fixed=clean(fixed);tax=clean(tax);
  if(!Number.isInteger(Number(people))||Number(people)<1||Number(people)>999)throw new Error('People must be between 1 and 999');
  const cents=value=>`floor((${value})*100+1/2)/100`,originalTip=cents(method==='amount'?fixed:`(${bill})*(${percent})/100`),taxAmount=cents(`(${bill})*(${tax})/100`),originalTotal=`(${cents(bill)}+${originalTip}+${taxAmount})`,total=whole&&Number(people)>1?`(ceil(${originalTotal}/${people})*${people})`:originalTotal,tip=whole&&Number(people)>1?`(${originalTip}+${total}-${originalTotal})`:originalTip,share=`floor(${total}*100/${people})/100`,extras=`mod(${total}*100,${people})`;
  return `[${tip},${taxAmount},${total},${share},${share}+1/100,${extras}]`;
}
export function moneyResult(result,people=2){
  const labels=['Tip','Tax','Total','Per person'],tree=result.tree||result.decimalTree;
  if(tree?.kind!=='list')return result;
  function moneyNumber(node){if(node.kind!=='fraction')return node;const [numerator,denominator]=node.args,cents=BigInt(numerator.value)*100n/BigInt(denominator.value),raw=cents.toString().padStart(3,'0');return {kind:'number',value:raw.slice(0,-2)+'.'+raw.slice(-2)};}
  const [tip,tax,total,share,extraShare,extraPeople]=tree.args.map(moneyNumber),count=Number(extraPeople?.value)||0,allocation=Array.from({length:people},(_,i)=>i<count?extraShare:share);
  const rows={kind:'rows',args:[tip,tax,total,!count?share:{kind:'list',args:allocation}].map((value,i)=>({kind:'row',value:labels[i],args:[value]}))};
  return {...result,tree:rows,decimalTree:rows,approximate:true};
}
