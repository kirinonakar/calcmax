import test from 'node:test';
import assert from 'node:assert/strict';
import {installEngine} from '../engine-bootstrap.js';
import {fetchEngineAsset} from '../engine-fetch.js';
import {spawnSync} from 'node:child_process';

function fixture(){
  const requests=[],unpacked=[],python=[];
  const lock={packages:Object.fromEntries(['mpmath','sympy'].map(name=>[name,{file_name:name+'.whl',sha256:'00'.repeat(32)}]))};
  const pyodide={runPython:source=>{python.push(source);return '/lib/python3.14/site-packages';},unpackArchive:(body,format,options)=>unpacked.push({body:new TextDecoder().decode(body),format,...options}),loadPackage:()=>{throw new Error('Package manager must not run during startup');}};
  const fetcher=async(url,options)=>{requests.push({url:String(url),options});return new Response(String(url).endsWith('pyodide-lock.json')?JSON.stringify(lock):String(url).split('/').at(-1));};
  const options={runtimeURL:new URL('https://example.test/symvacas/vendor/'),engineURL:new URL('https://example.test/symvacas/engine.zip'),fetcher};
  return {requests,unpacked,python,pyodide,options};
}

test('cold startup installs bundled wheels in dependency order with integrity and nested paths',async()=>{
  const f=fixture();await installEngine(f.pyodide,f.options);
  assert.deepEqual(f.unpacked.map(item=>item.body),['mpmath.whl','sympy.whl','engine.zip']);
  assert.deepEqual(f.unpacked.map(item=>item.extractDir),['/lib/python3.14/site-packages','/lib/python3.14/site-packages','/symvacas']);
  for(const request of f.requests.filter(item=>item.url.endsWith('.whl'))){assert.match(request.url,/\/symvacas\/vendor\//);assert.match(request.options.integrity,/^sha256-/);}
  assert.match(f.python.at(-1),/import calc_engine, script_runner/);
});

test('missing wheels stop startup before Python imports or partial installation',async()=>{
  const f=fixture(),fetcher=f.options.fetcher;
  f.options.fetcher=(url,options)=>fetchEngineAsset((input,init)=>String(input).endsWith('sympy.whl')?Promise.resolve(new Response('missing',{status:404})):fetcher(input,init),url,options);
  await assert.rejects(installEngine(f.pyodide,f.options),/404/);
  assert.equal(f.unpacked.length,0);assert.equal(f.python.length,0);
});

test('startup retries a stalled response body and bypasses the HTTP cache',async()=>{
  const requests=[];
  const response=await fetchEngineAsset(async(_,options)=>{
    requests.push(options);
    return requests.length===1?{ok:true,status:200,arrayBuffer:()=>new Promise(()=>{})}:new Response('wheel');
  },'sympy.whl',{},15);
  assert.equal(await response.text(),'wheel');
  assert.equal(requests.length,2);assert.equal(requests[0].signal.aborted,true);assert.equal(requests[1].cache,'reload');
});
test('a download that never resolves fails after two bounded attempts',async()=>{
  let requests=0;
  await assert.rejects(fetchEngineAsset(()=>{requests++;return new Promise(()=>{});},'engine.zip',{},10),/timed out/);
  assert.equal(requests,2);
});

test('application module exports link and bootstrap reports failed imports with a working page reload',()=>{
  const result=spawnSync(process.execPath,['--experimental-vm-modules','--input-type=module','--eval',`
    import vm from 'node:vm';
    import assert from 'node:assert/strict';
    import {readFile} from 'node:fs/promises';
    const root=new URL('../',${JSON.stringify(import.meta.url)}),modules=new Map();
    async function get(url){
      if(modules.has(url.href))return modules.get(url.href);
      const module=new vm.SourceTextModule(await readFile(url,'utf8'),{identifier:url.href});
      modules.set(url.href,module);return module;
    }
    const app=await get(new URL('app.js',root));
    await app.link((name,parent)=>get(new URL(name,parent.identifier)));
    const source=await readFile(new URL('bootstrap.js',root),'utf8');
    for(const failure of [false,true]){
      const status={textContent:'Loading WebAssembly runtime…'},retry={hidden:true},dataset={};
      let reloads=0,errors=0,imports=0;
      const context=vm.createContext({
        document:{documentElement:{dataset},getElementById:id=>id==='status'?status:retry},
        location:{reload:()=>reloads++},console:{error:()=>errors++}
      });
      const module=new vm.SourceTextModule(source,{context,importModuleDynamically:async name=>{
        assert.equal(name,'./app.js');imports++;
        if(failure)throw new SyntaxError("Module does not provide an export named functionRelationExit");
        const loaded=new vm.SyntheticModule([],()=>{},{context});
        await loaded.link(()=>{});await loaded.evaluate();return loaded;
      }});
      await module.link(()=>{});await module.evaluate();assert.equal(imports,1);
      if(failure){
        assert.equal(dataset.engine,'error');assert.match(status.textContent,/functionRelationExit/);
        assert.equal(retry.hidden,false);retry.onclick();assert.equal(reloads,1);assert.equal(errors,1);
      }else{assert.equal(retry.hidden,true);assert.equal(errors,0);assert.equal(dataset.engine,undefined);}
    }
  `],{encoding:'utf8'});
  assert.equal(result.status,0,result.stderr || result.stdout);
});
