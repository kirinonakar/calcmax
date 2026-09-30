// Cover the response body as well as headers: a stalled wheel download must
// reject so startup can recover without asking the user to reload the page.
export async function fetchEngineAsset(fetcher,input,options={},timeout=30000) {
  let lastError;
  for(let attempt=0;attempt<2;attempt++) {
    const controller=new AbortController();
    let timer;
    try {
      return await Promise.race([
        (async()=>{
          const response=await fetcher(input,{...options,signal:controller.signal,...(attempt?{cache:'reload'}:{})});
          if(!response.ok)throw new Error(`Engine download failed (${response.status}): ${input}`);
          const body=await response.arrayBuffer();
          return new Response(body,{status:response.status,headers:response.headers});
        })(),
        new Promise((_,reject)=>{timer=setTimeout(()=>{controller.abort();reject(new Error(`Engine download timed out: ${input}`));},timeout);})
      ]);
    } catch(error) {lastError=error;}
    finally {clearTimeout(timer);}
  }
  throw lastError;
}
