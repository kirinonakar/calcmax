import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {parse} from '../parser.js';
import {requiresExplicitEvaluation} from '../evaluation-policy.js';

test('simple arithmetic and entry helpers preview while typing',()=>{
  for(const source of ['2+3*4','cos(2*x)','f(1)','log(100)','log(100,10)',
    'nthroot(8,3)','mixed(1,1,2)','mod(17,5)','divmod(17,5)','normcdf(0)','invnorm(0.975)'])
    assert.equal(requiresExplicitEvaluation(parse(source)),false,source);
});

test('expensive, random, and multi-argument calls require explicit evaluation',()=>{
  for(const source of ['rnd()','integrate(e)','roundh(1.225,2)',
    'integrate(exp(-x^2)*cos(2*x),(x,0,oo))','1+dot([1,2],[3,4])',
    'tcdf(2.228)','invt(0.9)','npv(0.1)','tvmpmt(360,0.05/12,250000)','cagr(1000)','f(1,2)'])
    assert.equal(requiresExplicitEvaluation(parse(source)),true,source);
  assert.equal(requiresExplicitEvaluation(parse('1+f(1)'),new Set(['f'])),true);
});

test('all Android policy entries and exceptions retain the same web behavior',()=>{
  const kotlin=readFileSync(new URL('../../math/src/main/kotlin/com/kirinonakar/calcmax/math/EvaluationPolicy.kt',import.meta.url),'utf8');
  const names=section=>[...kotlin.match(new RegExp(`${section} = setOf\\(([\\s\\S]*?)\\)`))[1].matchAll(/"([^"]+)"/g)].map(match=>match[1]);
  const helpers=new Set(names('previewFunctions'));
  for(const name of names('multiArgumentFunctions'))
    assert.equal(requiresExplicitEvaluation({kind:'call',value:name,args:[]}),!helpers.has(name),name);
  for(const name of helpers)
    assert.equal(requiresExplicitEvaluation({kind:'call',value:name,args:[parse('1'),parse('2')]}),false,name);
});
