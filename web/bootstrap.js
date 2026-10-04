// Keep module-linking failures visible even when app.js cannot execute.
try {
  await import('./app.js');
} catch (error) {
  console.error('SymvaCAS initialization failed:',error);
  document.documentElement.dataset.engine='error';
  document.getElementById('status').textContent=`SymvaCAS initialization failed: ${error.message || error}`;
  const retry=document.getElementById('retry');
  retry.hidden=false;
  retry.textContent='Reload page';
  retry.onclick=()=>location.reload();
}
