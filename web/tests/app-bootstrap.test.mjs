import test from 'node:test';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';

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
