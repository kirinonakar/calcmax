import {roundNumber} from './display-format.js';

export function tipCommand({bill,percent='15',fixed='15',tax='0',people='2',method='percent',whole=true}){
  const clean=text=>{text=String(text).replace(/,/g,'');if(text.length>128||!/^\+?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(text))throw new Error('Enter non-negative amounts and percentages');return text;};
  bill=clean(bill);percent=method==='amount'?'0':clean(percent);fixed=method==='amount'?clean(fixed):'0';tax=clean(tax);
  if(!Number.isInteger(Number(people))||Number(people)<1||Number(people)>999)throw new Error('People must be between 1 and 999');
  const cents=value=>`floor((${value})*100+1/2)/100`,originalTip=cents(method==='amount'?fixed:`(${bill})*(${percent})/100`),taxAmount=cents(`(${bill})*(${tax})/100`),originalTotal=`(${cents(bill)}+${originalTip}+${taxAmount})`,total=whole&&Number(people)>1?`(ceil(${originalTotal}/${people})*${people})`:originalTotal,tip=whole&&Number(people)>1?`(${originalTip}+${total}-${originalTotal})`:originalTip,share=`floor(${total}*100/${people})/100`,extras=`mod(${total}*100,${people})`;
  const tipPercent=method==='amount'||whole&&Number(people)>1?(Number(bill)===0?'0':`(${tip})/(${bill})*100`):percent;
  return `[${tip},${taxAmount},${total},${share},${share}+1/100,${extras},${tipPercent}]`;
}
export function moneyResult(result,people=2){
  const labels=['Tip','Tax','Total','Per person'],tree=result.tree||result.decimalTree;
  if(tree?.kind!=='list')return result;
  function moneyNumber(node){if(node.kind!=='fraction')return node;const [numerator,denominator]=node.args,cents=BigInt(numerator.value)*100n/BigInt(denominator.value),raw=cents.toString().padStart(3,'0');return {kind:'number',value:raw.slice(0,-2)+'.'+raw.slice(-2)};}
  function percentNumber(node){
    let shown;
    if(node.kind==='fraction'){
      const numerator=BigInt(node.args[0].value),denominator=BigInt(node.args[1].value);
      const hundredths=(numerator*200n+denominator)/(denominator*2n),raw=hundredths.toString().padStart(3,'0');
      shown=raw.slice(0,-2)+'.'+raw.slice(-2);
    }else{
      const [whole,fraction='']=roundNumber(node.value,2).split('.');shown=whole+'.'+fraction.padEnd(2,'0');
    }
    return {kind:'fixed-number',value:shown};
  }
  const [tip,tax,total,share,extraShare,extraPeople]=tree.args.slice(0,6).map(moneyNumber),count=Number(extraPeople?.value)||0,allocation=Array.from({length:people},(_,i)=>i<count?extraShare:share);
  const rows={kind:'rows',args:[tip,tax,total,!count?share:{kind:'list',args:allocation}].map((value,i)=>({kind:'row',value:labels[i],args:[value]}))};
  if(tree.args[6])rows.args.push({kind:'row',value:'Tip %',args:[{kind:'postfix',value:'%',args:[percentNumber(tree.args[6])]}]});
  return {...result,tree:rows,decimalTree:rows,approximate:true};
}
