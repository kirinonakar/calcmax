import test from 'node:test';
import assert from 'node:assert/strict';
import {csvRows,numericStatisticsRows,statisticsCommand,distributionCommand,equationCommand,polynomialEquation} from '../workspace-commands.js';
import {closeInputBrackets,parse} from '../parser.js';
import {encodeFunctions,decodeFunctions,defineFunction} from '../function-transfer.js';
import {moveMathCursor} from '../input-navigation.js';
import {roundNumber} from '../display-format.js';
import {tipCommand} from '../money.js';

test('display decimals round nested numeric strings, preserve precision, and normalize negative zero',()=>{
  assert.equal(roundNumber('12345678901234567890.123456',3),'12345678901234567890.123');
  assert.equal(roundNumber('-0.00004999',3),'0');assert.equal(roundNumber('9.99995',3),'10');
  assert.equal(roundNumber('1.234567e-200',3),'1.235e-200');assert.equal(roundNumber('1234567890',2),'1234567890');
});
test('CSV blanks retain paired alignment; independent samples omit blanks separately and grouped tests use all groups',()=>{
  assert.deepEqual(csvRows('x,y\n1,2\n,4\n3,\n4,8'),[['1','2'],['','4'],['3',''],['4','8']]);
  assert.equal(statisticsCommand('1,2\n,4\n3,\n4,8',{op:'correlation'}),'correlation([1,4],[2,8])');
  assert.equal(statisticsCommand('1,2\n,4\n3,\n4,8',{op:'ttest2',extra:'0',tail:'right'}),'ttest2(0,[1,3,4],[2,4,8],right)');
  assert.equal(statisticsCommand('A,1\nB,2\nA,3\nB,4',{op:'anova',grouping:'groups'}),'anova([1,3],[2,4])');
  assert.equal(statisticsCommand('yes,a\nno,b\nyes,b',{op:'chi2independence'}),'chi2independence([1,2,1],[1,2,2])');
  assert.deepEqual(numericStatisticsRows(csvRows('date,value\n2026-09-30,2\n2026-10-02,4')),[['1','2'],['3','4']]);
  assert.deepEqual(csvRows('"a,b",2\n"x""y",4'),[['a,b','2'],['x"y','4']]);
});
test('distribution and equation commands match Android engine arity, coefficient forms, and initial conditions',()=>{
  assert.equal(distributionCommand({family:'normal',query:'cdf',x:'1',mean:'2',sigma:'3'}),'normcdf(-oo,1,2,3)');
  assert.equal(distributionCommand({family:'binomial',query:'list-pdf',trials:'4',success:'1/2'}),'binompdf(4,1/2)');
  assert.equal(distributionCommand({family:'t',query:'quantile',p:'.95',df:'10'}),'invt(.95,10)');
  assert.equal(equationCommand({kind:'dsolve',source:'diff(y(t),t)=y(t)',variable:'y(t)',extra:'t',initial:'y(0)=1'}),'dsolve(diff(y(t),t)=y(t),y(t),t,y(0)=1)');
  assert.equal(equationCommand({source:'x+y=3\nx-y=1',variable:'x,y'}),'solve([x+y=3,x-y=1],[x,y])');
  assert.ok(parse(polynomialEquation(['1','-5','6'])));
});
test('missing trailing brackets close in order; malformed delimiters still fail and natural root arrows skip hidden names',()=>{
  assert.equal(closeInputBrackets('4+sqrt(5'),'4+sqrt(5)');assert.equal(closeInputBrackets('sqrt([1,2'),'sqrt([1,2])');
  assert.throws(()=>parse(closeInputBrackets('sqrt(5]')),SyntaxError);assert.throws(()=>parse(closeInputBrackets('4+')),SyntaxError);
  assert.equal(moveMathCursor('4+sqrt(5)',7,7,'LEFT'),2);
  assert.equal(moveMathCursor('4+sqrt(5)',8,8,'RIGHT'),9);
  assert.equal(moveMathCursor('4+sqrt(5)',2,2,'RIGHT'),7);
  assert.equal(moveMathCursor('4+sqrt(5)',9,9,'LEFT'),8);
  assert.equal(moveMathCursor('sqrt(123)',6,6,'UP'),9);
  assert.equal(moveMathCursor('(1)/(2)',2,2,'RIGHT'),5);
  assert.equal(moveMathCursor('[[1,2],[3,4]]',3,3,'RIGHT'),4);
});
test('function files round-trip with Android format and reject reserved names without trusting imported ASTs',()=>{
  const functions={f:defineFunction('f',['x'],'x^2+1')},text=encodeFunctions(functions),imported=decodeFunctions(text);
  assert.equal(JSON.parse(text).format,'calcmax.functions');assert.deepEqual(imported.functions,functions);
  assert.throws(()=>defineFunction('sin',['x'],'x'),/reserved/);
  assert.throws(()=>decodeFunctions('{"format":"another.format"}'),/Unsupported/);
  assert.deepEqual(decodeFunctions(JSON.stringify(functions)).functions,functions);
});
test('tip command allocates rounded cents using quotient and remainder, as Android does',()=>{
  assert.ok(parse(tipCommand({bill:'100',people:'3'})));
  assert.throws(()=>tipCommand({bill:'-1'}));assert.throws(()=>tipCommand({bill:'100',people:'1.5'}));
});
