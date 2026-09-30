// Display-only decimal rounding. Engine values and editable sources stay lossless.
export function roundNumber(value,digits=10){
  const text=String(value),match=/^([+-]?)(\d*)(?:\.(\d*))?([eE][+-]?\d+)?$/.exec(text);
  if(!match||!match[2]&&!match[3])return text;
  let [,sign,whole,fraction='',exponent='']=match;
  if(!text.includes('.'))return text;
  digits=Math.max(0,Math.min(200,Math.trunc(digits)));
  let raw=fraction.slice(0,digits).padEnd(digits,'0');whole=whole||'0';
  if(Number(fraction[digits]||'0')>=5){
    const rounded=(BigInt(whole+raw)+1n).toString().padStart(digits+1,'0');
    whole=digits?rounded.slice(0,-digits):rounded;raw=digits?rounded.slice(-digits):'';
  }
  raw=raw.replace(/0+$/,'');
  if(!/[1-9]/.test(whole+raw))sign='';
  return sign+whole+(raw?'.'+raw:'')+exponent;
}
export function displayNumber(value,digits=10){
  if(typeof value==='number'&&!Number.isFinite(value))return '—';
  return roundNumber(value,digits);
}
