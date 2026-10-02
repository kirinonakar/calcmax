import test from 'node:test';
import {regressionGraphSource} from '../statistics-workspace.js';
import assert from 'node:assert/strict';
import {csvRows,statisticsDataRows,statisticsDatasetSource,statisticsAnalysisData,numericStatisticsRows,statisticsCommand,distributionCommand,equationCommand,polynomialEquation} from '../workspace-commands.js';
import {closeInputBrackets,parse} from '../parser.js';
import {encodeFunctions,decodeFunctions,defineFunction} from '../function-transfer.js';
import {moveMathCursor,fractionExit} from '../input-navigation.js';
import {roundNumber} from '../display-format.js';
import {tipCommand} from '../money.js';

test('Excel paste keeps dates and padded thousands as two columns for analysis and regression',()=>{
  const dates=['2022-12-02','2023-01-15','2023-02-05','2023-03-05','2023-04-01','2023-05-01','2023-06-01','2023-07-01','2023-08-01','2023-09-01'];
  const values=['166,682','168,254','169,131','172,166','175,120','177,330','177,409','181,512','181,286','183,566'];
  const days=['1','45','66','94','121','151','182','212','243','274'];
  const source=dates.map((date,i)=>`${date}\t \u00a0\u00a0        ${values[i]}\u00a0 `).join('\r\n')+'\r\n';
  const rows=dates.map((date,i)=>[date,values[i]]),numeric=days.map((day,i)=>[day,values[i].replace(',','')]);
  assert.deepEqual(csvRows(source),rows);
  assert.deepEqual(numericStatisticsRows(statisticsDataRows(source,'xy')),numeric);
  const table='['+numeric.map(row=>'['+row.join(',')+']').join(',')+']';
  assert.equal(statisticsDatasetSource(source,'xy'),table);
  assert.equal(statisticsCommand(source,{kind:'xy',op:'regression'}),`regression(${table},linear)`);
  assert.equal(statisticsCommand(source,{kind:'xy',op:'mean',column:1}),`mean([${numeric.map(row=>row[1]).join(',')}])`);
  assert.equal(parse(statisticsCommand(source,{kind:'xy',op:'regression'})).args[0].args.length,10);
});

test('TSV preserves literal commas, quotes and blanks while CSV records keep their delimiter',()=>{
  assert.deepEqual(csvRows('x\ty\tz\r\n"A,B"\t"1,234"\t\r\n\t"C""D"\t5\r\n1,2,3'),[['A,B','1,234',''],['','C"D','5'],['1','2','3']]);
  assert.deepEqual(csvRows('"a\tb",2'),[['a\tb','2']]);
  assert.deepEqual(csvRows('"a\nb,c"\t1,234\r\n"d""e,f"\t5,678'),[['a\nb,c','1,234'],['d"e,f','5,678']]);
  assert.deepEqual(csvRows(',\n2022-12-02\t166,682\n,',{preserveEmptyRows:true}),[['',''],['2022-12-02','166,682'],['','']]);
  assert.deepEqual(csvRows('1,2,3'),[['1','2','3']]);
  assert.throws(()=>csvRows('2022-12-02\t"166,682'),/Unclosed CSV quote/);
});

test('CSV editing preserves empty rows while analysis omits them',()=>{
  const source=',\r\n1,2\r\n,\r\n3,4\r\n,';
  assert.deepEqual(csvRows(source,{preserveEmptyRows:true}),[['',''],['1','2'],['',''],['3','4'],['','']]);
  assert.deepEqual(csvRows(source),[['1','2'],['3','4']]);
  assert.deepEqual(csvRows('""\n5\n""',{preserveEmptyRows:true}),[[''],['5'],['']]);
  assert.deepEqual(csvRows('1,2\n',{preserveEmptyRows:true}),[['1','2']]);
  assert.deepEqual(csvRows('"a\nb",2\n,',{preserveEmptyRows:true}),[['a\nb','2'],['','']]);
  assert.throws(()=>csvRows(',\n,'),/Enter 1–5000 data rows/);
});

test('Home and End address the whole source even when selected, multiline, or invalid',()=>{
  for(const source of ['','1/2+sqrt(3)','12+*3','1+2\n+3']){
    assert.equal(moveMathCursor(source,0,source.length,'HOME'),0);
    assert.equal(moveMathCursor(source,0,source.length,'END'),source.length);
  }
});

test('Right leaves a completed denominator at the fraction boundary, including nested fractions',()=>{
  for(const source of ['(1)/(234)','(1)/(2+3)','(1)/(sqrt(2))','1/234']){
    const tree=parse(source),right=tree.args[1],at=right.kind==='group'?right.args[0].end:right.end;
    const exit=fractionExit(source,at,at,'RIGHT');assert.equal(exit.position,source.length,source);
    assert.equal(moveMathCursor(source,at,at,'RIGHT'),source.length);assert.equal(exit.denominatorEnd,at);
  }
  const nested='(1)/((2)/(3))+4',outer=parse(nested).args[0],inner=outer.args[1].args[0],at=inner.args[1].args[0].end;
  assert.equal(fractionExit(nested,at,at,'RIGHT').end,inner.end,'the inner fraction exits first');
  assert.equal(fractionExit(nested,inner.end,inner.end,'RIGHT').end,outer.end,'the next Right exits the outer fraction');
  assert.equal(fractionExit('(1)/()',5,5,'RIGHT'),null,'an empty denominator remains editable');
  assert.equal(fractionExit('(1)/(234)',6,6,'RIGHT'),null,'Right within a number advances normally');
  assert.equal(fractionExit('1÷2',3,3,'RIGHT'),null,'linear division has no fraction boundary');
});

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
test('regression transfer rounds numeric tokens and renames only the independent variable',()=>{
  const source=regressionGraphSource('0.333333333333333*x + 0.666666666666667',3);
  assert.ok(parse(source));assert.match(source,/0\.333/);assert.match(source,/0\.667/);assert.doesNotMatch(source,/0\.3333/);
  const custom=regressionGraphSource('-0.123456*exp(-1.987654*t)+0.333333',3,'t');
  assert.ok(parse(custom));assert.match(custom,/0\.123/);assert.match(custom,/1\.988/);assert.match(custom,/exp\(/);assert.doesNotMatch(custom,/\bt\b/);assert.match(custom,/\bx\b/);
  assert.match(regressionGraphSource('0.123456*x1+x',2),/x1/,'digits in symbol names stay intact');
});

test('List, xy, and xyz analyses use only the selected columns and pad missing cells',()=>{
  const source='x,y,z\n1,4,7\n2,5,8\n3,6,9';
  assert.deepEqual(statisticsDataRows(source,'list'),[['1'],['2'],['3']]);
  assert.deepEqual(statisticsDataRows(source,'xy'),[['1','4'],['2','5'],['3','6']]);
  assert.deepEqual(statisticsDataRows('1\n2,3','xyz'),[['1','',''],['2','3','']]);
  assert.equal(statisticsDatasetSource(source,'list'),'[1,2,3]');
  assert.equal(statisticsDatasetSource(source,'xy'),'[[1,4],[2,5],[3,6]]');
  assert.equal(statisticsDatasetSource('1,4,7\n,5,8\n3,6,','xyz'),'[[1,4,7]]','incomplete rows are omitted when storing a table');
  assert.equal(statisticsCommand('1,@,@\n2,@,@',{op:'mean',kind:'list',grouping:'groups'}),'mean([1,2])','hidden columns do not affect a list analysis');
  assert.equal(statisticsCommand(source,{op:'anova',kind:'xy'}),'anova([1,2,3],[4,5,6])');
  assert.equal(statisticsCommand(source,{op:'anova',kind:'xyz'}),'anova([1,2,3],[4,5,6],[7,8,9])');
  assert.throws(()=>statisticsCommand(source,{op:'correlation',kind:'list'}),/paired rows/);
  assert.throws(()=>statisticsCommand(source,{op:'regression',kind:'xyz'}),/x,y data/);
});

test('independent samples select x/y/z and paired operations ignore unused group and column settings',()=>{
  const source='1,4,7\n2,5,8\n3,6,9';
  assert.equal(statisticsCommand(source,{op:'ttest2',firstGroup:'y',secondGroup:'z'}),'ttest2(0,[4,5,6],[7,8,9])');
  assert.equal(statisticsCommand(source,{op:'ztest2',firstGroup:'z',secondGroup:'x',sigma:'2',sigmaY:'3'}),'ztest2(0,2,3,[7,8,9],[1,2,3])');
  assert.throws(()=>statisticsCommand(source,{op:'ttest2',firstGroup:'x',secondGroup:'x'}),/different/);
  assert.equal(statisticsCommand('1,2\n3,4',{op:'correlation',column:2,grouping:'groups'}),'correlation([1,3],[2,4])');
  assert.equal(statisticsCommand('A,1\nB,2\nA,3\nB,4',{op:'mean',grouping:'groups',firstGroup:'B'}),'mean([2,4])');
});

test('displayed analysis samples match paired, independent, and all-group command arguments',()=>{
  const source='1,2,@\n2,5,@\n,7,@\n4,,@\n6,8,@',pairedOptions={kind:'xyz',op:'ttestpaired',grouping:'groups',column:2,firstGroup:'x',secondGroup:'x'};
  const paired=statisticsAnalysisData(source,pairedOptions);
  assert.deepEqual(paired.samples,[{label:'x',values:['1','2','6']},{label:'y',values:['2','5','8']}]);
  assert.equal(statisticsCommand(source,pairedOptions),'ttestpaired(0,[1,2,6],[2,5,8])');
  const data='1,4,7\n2,5,8\n3,7,10',options={kind:'xyz',op:'anova',grouping:'groups',firstGroup:'x',secondGroup:'x'};
  assert.deepEqual(statisticsAnalysisData(data,options).samples.map(sample=>[sample.label,sample.values.length]),[['x',3],['y',3],['z',3]]);
  assert.equal(statisticsCommand(data,options),'anova([1,2,3],[4,5,7],[7,8,10])');
  const independent={kind:'xyz',op:'ttest2',firstGroup:'y',secondGroup:'z'};
  assert.deepEqual(statisticsAnalysisData(data,independent).samples.map(sample=>sample.label),['y','z']);
  assert.equal(statisticsCommand(data,independent),'ttest2(0,[4,5,7],[7,8,10])');
  const grouped='A,1\nB,2\nC,3\nA,4\nB,5\nC,6',groupOptions={kind:'xy',op:'anova',grouping:'groups'};
  assert.deepEqual(statisticsAnalysisData(grouped,groupOptions).samples.map(sample=>sample.label),['A','B','C']);
  assert.equal(statisticsCommand(grouped,groupOptions),'anova([1,4],[2,5],[3,6])');
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
