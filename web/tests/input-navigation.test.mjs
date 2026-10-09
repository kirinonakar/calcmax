import test from 'node:test';
import {inputEnd,functionRelationExit,emptyPowerDeletion,emptyFractionDeletion,moveMathCursor,mathStructureExit,matrixFactorInput,divisionInput} from '../input-navigation.js';
import {fractionInput} from '../fraction-input.js';
import {expressionTree} from '../expression-tree.js';
import assert from 'node:assert/strict';
import {bindKeyPress} from '../keypad.js';

import {parse} from '../parser.js';
import {requiresExplicitEvaluation} from '../evaluation-policy.js';

test('trailing placement exits all fractions, powers and function arguments',()=>{
  for(const source of ['25^1','2^3^4','1/2^3','25/(3)','2^(1/(3^4))',
    'integrate(x^2,x)','diff(sin(x^2),x)','sin(integrate(x/(2^3),x))']){
    const outside=inputEnd(source),at=outside.position;
    assert.equal(outside.source,source);assert.equal(at,source.length);assert.equal(outside.edge,'end');
    assert.equal(mathStructureExit(source,at,at,'RIGHT',outside),null,source);
    assert.deepEqual(divisionInput(source,at,at,'/',outside),{start:at,end:at,text:'/()',cursor:2},source);
    const fraction=fractionInput(source,at,at,outside);
    assert.equal(source.slice(0,fraction.start)+fraction.text+source.slice(fraction.end),`(${source})/()`,source);
    assert.deepEqual(fractionInput(source,at,at,{position:at,edge:'end'}),fraction,'End uses the same outer scope');
    assert.equal(parse(source+'+1').value,'+',source);
  }
  for(const source of ['sin(9','diff(sin(x),x','integrate(diff(x^2,x),x','sin(integrate(x,x']){
    const outside=inputEnd(source);
    assert.equal(outside.position,outside.source.length);
    assert.equal(parse(outside.source+'+1').value,'+',source);
    assert.equal(parse(outside.source+'/2').value,'/',source);
  }
});

test('slash and fraction stay in a bare exponent until Right leaves it',()=>{
  const apply=(source,edit)=>source.slice(0,edit.start)+edit.text+source.slice(edit.end);
  const edit=divisionInput('25^1',4,4,'/');
  assert.equal(apply('25^1',edit),'25^(1/())');assert.equal(edit.cursor+edit.start,7);
  const outside=mathStructureExit('25^1',4,4,'RIGHT');
  assert.deepEqual(divisionInput('25^1',4,4,'/',outside),{start:4,end:4,text:'/()',cursor:2});
  assert.deepEqual(divisionInput('25^(1)',5,5,'/'),{start:5,end:5,text:'/()',cursor:2});
  const fraction=fractionInput('25^1',4,4);
  const source=apply('25^1',fraction);assert.equal(source,'25^((1)/())');
  assert.equal(source[fraction.start+fraction.cursor],')','caret is in denominator');
  const full=fractionInput('25^1',4,4,outside);
  assert.equal(apply('25^1',full),'(25^1)/()');
  assert.deepEqual(divisionInput('25^1',3,4,'/'),{start:3,end:4,text:'/()',cursor:2});
});

test('division groups the denominator invisibly and Right exits before subtraction',()=>{
  assert.deepEqual(divisionInput('25',2,2,'/'),{start:2,end:2,text:'/()',cursor:2});
  assert.equal(parse('25/(3-1)').args[1].args[0].value,'-');
  const displayed=expressionTree('25/(3-1)');
  assert.equal(displayed.kind,'fraction');assert.equal(displayed.args[1].kind,'relation');
  assert.equal(displayed.args[1].value,'-','hidden denominator group renders only its contents');
  const exit=mathStructureExit('25/(3)',5,5,'RIGHT');
  assert.equal(exit.position,6);
  assert.equal(parse('25/(3)-1').value,'-');
});

test('structured navigation preserves equation scope and empty-template editing',()=>{
  { // equality moves

  for(const source of ['diff(x,x)','integrate(x,x)','sin(x)','sin(diff(x,x))','f(x)']){
    const at=source.indexOf('x')+1;
    assert.equal(functionRelationExit(source,at,at),source.length,source);
  }
  assert.equal(functionRelationExit('diff(x,x)',7,7),9,'the right arrow may move past the comma');
  assert.equal(functionRelationExit('solve(x,x)',7,7),null);
  assert.equal(functionRelationExit('solve(sin(x),x)',11,11),12);
  const nested='dsolve(diff(y(t),t),y(t),t)',at=nested.indexOf('y(t)')+3;
  assert.equal(functionRelationExit(nested,at,at),nested.indexOf(',y(t)'));
  assert.equal(functionRelationExit('piecewise((x,x>0),(0,true))',12,12),null);
  for(const [source,start,end] of [['diff(x,x)',5,6],['diff(x,x)',0,0],['sin(x)',6,6],['sin(x>',6,6]])assert.equal(functionRelationExit(source,start,end),null,source);

  }
  { // Right exits completed

  for(const source of ['5^2','5^3','5^(-1)','(9)^(9)','5^(23)+1']){
    const treeEnd=source.endsWith('+1')?source.length-2:source.length;
    const at=source[treeEnd-1]===')'?treeEnd-1:treeEnd;
    assert.deepEqual(mathStructureExit(source,at,at,'RIGHT'),{position:treeEnd,start:0,end:treeEnd,exponentEnd:at},source);
    assert.equal(moveMathCursor(source,at,at,'RIGHT'),treeEnd,source);
  }
  for(const source of ['2^(3^4)','1/(2^3)','2^(1/3)','2^3^4']){
    const at=source.endsWith(')')?source.length-1:source.length;
    const inner=mathStructureExit(source,at,at,'RIGHT');assert.ok(inner.start>0,source);
    const outer=mathStructureExit(source,inner.position,inner.position,'RIGHT',inner);
    assert.equal(outer.start,0,source);assert.equal(outer.position,source.length,source);
  }
  for(const [source,at] of [['5^()',3],['5^(2+)',5],['5^(234)',5]])
    assert.equal(mathStructureExit(source,at,at,'RIGHT'),null,source);
  assert.equal(mathStructureExit('5^2',2,3,'RIGHT'),null,'selection collapse is not an exponent exit');
  assert.equal(mathStructureExit('5^2',3,3,'LEFT'),null);

  }
  { // empty powers and fractions

  for(const [source,position,remove] of [
    ['()^2',1,emptyPowerDeletion],['()^3',1,emptyPowerDeletion],['()^(-1)',1,emptyPowerDeletion],
    ['()^()',1,emptyPowerDeletion],['()^()',4,emptyPowerDeletion],
    ['()/()',1,emptyFractionDeletion],['()/()',4,emptyFractionDeletion],
  ]){
    assert.deepEqual(remove(source,position,position),{start:0,end:source.length,text:''},source);
    assert.deepEqual(remove(`sin(${source})`,position+4,position+4),{start:4,end:source.length+4,text:''},source);
  }
  assert.deepEqual(emptyPowerDeletion('2*()^2*3',3,3),{start:2,end:6,text:'()'});
  for(const [source,at,remove,expected] of [
    ['3^()',3,emptyPowerDeletion,'3'],['(3)^()',5,emptyPowerDeletion,'3'],
    ['(3)/()',5,emptyFractionDeletion,'3'],['(3+4)/()',7,emptyFractionDeletion,'(3+4)'],
  ])assert.deepEqual(remove(source,at,at),{start:0,end:source.length,text:expected,cursor:expected.length});
  for(const source of ['(2)^2','(2)^(-1)','(1)/(2)']){
    assert.equal(emptyPowerDeletion(source,source.length,source.length),null);
    assert.equal(emptyFractionDeletion(source,source.length,source.length),null);
  }
  assert.equal(emptyFractionDeletion('1÷',2,2),null,'plain division keeps its text deletion behavior');
  assert.equal(emptyPowerDeletion('()^2',0,4),null,'ordinary selections keep their behavior');

  }
});


test('preview policy evaluates arithmetic but waits for expensive or stateful calls',()=>{
  for(const source of ['2+3*4','cos(2*x)','f(1)','log(100,10)','nthroot(8,3)'])
    assert.equal(requiresExplicitEvaluation(parse(source)),false,source);
  for(const source of ['rnd()','integrate(exp(-x^2),(x,0,oo))','1+dot([1,2],[3,4])','f(1,2)'])
    assert.equal(requiresExplicitEvaluation(parse(source)),true,source);
  assert.equal(requiresExplicitEvaluation(parse('1+f(1)'),new Set(['f'])),true);
});

test('matrix edge arrows exit the entire matrix and reenter its last element',()=>{
  for(const matrix of ['[[3,5],[3,6]]','[[33,55],[33,66]]','[[,],[,]]','[[1]]','[[1,2,3]]','[[1],[2],[3]]']){
    for(const source of [matrix,`1+${matrix}+2`,`det(${matrix})`]){
      const start=source.indexOf(matrix),end=start+matrix.length;
      const node=parse(matrix,{allowHoles:true}),last=node.args.at(-1).args.at(-1),at=start+last.end;
      assert.equal(moveMathCursor(source,at,at,'RIGHT'),end,source);
      assert.equal(moveMathCursor(source,end,end,'LEFT'),at,source);
      assert.equal(moveMathCursor(source,end-1,end-1,'LEFT'),at,source);
      assert.equal(moveMathCursor(source,at,at,'RIGHT'),end,source);
    }
  }
  const source='[[33,55],[33,66]]';
  assert.equal(moveMathCursor(source,3,3,'RIGHT'),4,'multi-digit cell editing stays in the cell');
  assert.equal(moveMathCursor(source,4,4,'RIGHT'),5,'the next cell is reached across the comma');
  assert.equal(moveMathCursor(source,7,7,'RIGHT'),10,'the next row is reached across both row delimiters');
  assert.equal(moveMathCursor(source,10,10,'LEFT'),7);
  assert.equal(moveMathCursor(source,10,10,'UP'),2);
  assert.equal(moveMathCursor(source,2,2,'DOWN'),10);
  const power='[[1,2],[3,4^2]]',at=power.indexOf(']]');
  const outside=mathStructureExit(power,at,at,'RIGHT');
  assert.equal(moveMathCursor(power,at,at,'RIGHT',outside),power.length,'exiting a power then its matrix skips both closing brackets');
});

test('matrix factors get explicit multiplication without changing cells or operators',()=>{
  const matrix='[[3,5],[3,6]]';
  for(const source of [matrix,`1+${matrix}+2`,`det(${matrix})`]){
    const end=source.indexOf(matrix)+matrix.length;
    for(const position of [end-1,end]){
      for(const text of ['3','.5','x','theta','sin()','(3+4)','[[1,0],[0,1]]','√4','∞']){
        const edit=matrixFactorInput(source,position,position,text);
        assert.deepEqual(edit,{position:end,prefix:'*'},source+text);
        const result=source.slice(0,edit.position)+edit.prefix+text+source.slice(edit.position);
        assert.equal(result,source.slice(0,end)+'*'+text+source.slice(end));
        const tree=parse(result,{allowHoles:true});
        const nodes=[];const visit=node=>{nodes.push(node);node.args.forEach(visit);};visit(tree);
        const retained=nodes.find(node=>node.start===source.indexOf(matrix)&&node.end===end&&node.kind==='list');
        assert.deepEqual(retained.args.map(row=>row.args.map(cell=>cell.value)),[['3','5'],['3','6']]);
      }
    }
    for(const text of ['+','-','*','/','^','=',',',')',']',''])assert.equal(matrixFactorInput(source,end,end,text),null,text);
    assert.equal(matrixFactorInput(source,end-2,end-2,'7'),null,'continuing the final cell must not insert a product');
  }
  assert.equal(matrixFactorInput(matrix,0,matrix.length,'3'),null,'replacing a selection must not insert a product');
  assert.deepEqual(matrixFactorInput(matrix+' ',matrix.length+1,matrix.length+1,'3'),{position:matrix.length+1,prefix:'*'});
  assert.equal(matrixFactorInput('[[3,5],[3,6',10,10,'7'),null,'an unfinished matrix remains editable');
});
