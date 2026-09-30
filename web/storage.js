const KEY='calcmax-web-v1';
export function readState() {
  try { const value=JSON.parse(localStorage.getItem(KEY)); return value && typeof value==='object' && !Array.isArray(value) ? value : {}; }
  catch { return {}; }
}
export function writeState(state) {
  try { localStorage.setItem(KEY,JSON.stringify(state)); return true; }
  catch { return false; }
}
export function downloadFile(name,content,type='text/plain') {
  const url=URL.createObjectURL(new Blob([content],{type}));
  const a=document.createElement('a'); a.href=url; a.download=name; a.click();
  setTimeout(()=>URL.revokeObjectURL(url),1000);
}
