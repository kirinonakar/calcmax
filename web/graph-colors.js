export const defaultGraphColors=Object.freeze(['#007b68','#a04c75','#3b70bd','#b17d00','#6b5ec2','#a34629']);
export function normalizeGraphColors(saved){
  return defaultGraphColors.map((fallback,i)=>Array.isArray(saved)&&/^#[0-9a-f]{6}$/i.test(saved[i])?saved[i].toLowerCase():fallback);
}
export function hexToHsl(hex){
  const [r,g,b]=[1,3,5].map(i=>parseInt(hex.slice(i,i+2),16)/255),max=Math.max(r,g,b),min=Math.min(r,g,b),delta=max-min,l=(max+min)/2;
  if(!delta)return [0,0,l*100];
  const h=max===r?(g-b)/delta+(g<b?6:0):max===g?(b-r)/delta+2:(r-g)/delta+4;
  return [h*60,delta/(1-Math.abs(2*l-1))*100,l*100];
}
export function hslToHex([h,s,l]){
  s/=100;l/=100;h=((h%360)+360)%360;
  const a=s*Math.min(l,1-l),channel=n=>{const k=(n+h/30)%12;return Math.round(255*(l-a*Math.max(-1,Math.min(k-3,9-k,1)))).toString(16).padStart(2,'0');};
  return `#${channel(0)}${channel(8)}${channel(4)}`;
}
