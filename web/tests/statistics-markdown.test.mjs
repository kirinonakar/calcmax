import test from 'node:test';
import assert from 'node:assert/strict';
import {statisticsResultMarkdown,regressionEquationCopyText,statisticsFormattedCopyCell} from '../statistics-markdown.js';
import {setLanguage,t} from '../i18n.js';

const cell=value=>({exact:value,decimal:value,decimalTree:{kind:'number',value}});
test('result markdown retains table relationships, all rows, formatting and safe literal labels',()=>{
  const report={title:'Descriptive statistics',sections:[{title:'Summary',columns:['Metric','Value'],rows:[['mean',cell('1.234567')]],copyRows:[['mean',cell('1.234567')],['A|B\n<row>',cell('12345.6789')]]}]};
  setLanguage('en');
  report.assumptions=['Independent rows; ordered categories.'];
  const text=statisticsResultMarkdown({statisticsReport:report,note:'Result only',exact:'source should not be copied'},{digits:3,grouping:true});
  assert.ok(text.includes('| Metric | Value |\n| --- | --- |\n| mean | 1.235 |'));
  assert.ok(text.includes('| A\\|B<br>&lt;row&gt; | 12,345.679 |'));
  assert.ok(text.endsWith('Result only'));
  assert.ok(text.includes('### Assumptions\n\nIndependent rows; ordered categories.'));
  assert.ok(!text.includes('source should not be copied'));
  try{
    setLanguage('ko');const korean=statisticsResultMarkdown({statisticsReport:report});
    assert.ok(korean.startsWith('## '+t('Descriptive statistics')));
    assert.ok(korean.includes(`| ${t('Metric')} | ${t('Value')} |`));
    assert.ok(korean.includes(`| ${t('mean')} |`));
    assert.ok(korean.includes('A\\|B'));
  }finally{setLanguage('en');}
});

test('dedicated report copy includes regression tables and plain results keep their copy behavior',()=>{
  const statisticsCopyReport={title:'Regression',sections:[{title:'coefficients',columns:['Parameter','Estimate'],rows:[['b0',cell('0.3333333')]]}]};
  assert.ok(statisticsResultMarkdown({statisticsCopyReport},{digits:4}).includes('| b0 | 0.3333 |'));
  assert.equal(statisticsResultMarkdown(cell('0.3333333'),{digits:4}),'0.3333');
});

test('copied equations and numeric strings use current display digits and visible variables',()=>{
  const result={decimal:'0.123456*x+1.987654',regression:{fitScale:'y'},statisticsCopyReport:{title:'Regression',sections:[
    {title:'Summary',columns:['Metric','Value'],rows:[['Fitted expression',{exact:'unrounded source'}]]},
    {title:'coefficients',columns:['Parameter','Estimate'],rows:[['b1','0.123456'],['b0','1.987654']]}
  ]}};
  for(const [digits,equation,slope] of [[0,'y = 0*x+2','0'],[3,'y = 0.123*x+1.988','0.123'],[6,'y = 0.123456*x+1.987654','0.123456']]){
    const copied=statisticsResultMarkdown(result,{digits});
    assert.ok(copied.includes(`### Regression equation\n\n\x60\x60\x60text\n${equation}\n\x60\x60\x60`));
    assert.ok(copied.includes(`| b1 | ${slope} |`));assert.ok(!copied.includes('unrounded source'));
  }
  assert.equal(regressionEquationCopyText(result,{digits:3,regressionVariables:{x:'dose'},regressionPrefix:'response = '}),'response = 0.123*dose+1.988');
});

test('logistic expressions and composite cells round all coefficients without modifying their result',()=>{
  const result={decimal:'1/(1+exp(-0.123456*x+0.654321))',regression:{fitScale:'binomial'}};
  const original=JSON.stringify(result),equation=regressionEquationCopyText(result,{digits:3});
  assert.ok(equation.startsWith('P(y = 1) = '));assert.ok(equation.includes('0.123'));assert.ok(equation.includes('0.654'));
  assert.ok(!equation.includes('0.123456'));assert.equal(JSON.stringify(result),original);
  const value={decimal:'x + 1.23456789',decimalTree:{kind:'sum',args:[{kind:'symbol',value:'x'},{kind:'number',value:'1.23456789'}]}};
  assert.equal(statisticsFormattedCopyCell(value,{digits:3}),'x+1.235');
  const product={decimal:'1.23456789*x',decimalTree:{kind:'product',args:[{kind:'number',value:'1.23456789'},{kind:'symbol',value:'x'}]}};
  assert.equal(statisticsFormattedCopyCell(product,{digits:3}),'1.235*x');
  result.regression.model='randomforest';assert.equal(regressionEquationCopyText(result),null);
});
