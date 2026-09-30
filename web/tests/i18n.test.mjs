import test from 'node:test';
import assert from 'node:assert/strict';
import {initialLanguage} from '../i18n.js';

test('browser language determines the default unless Setup has an explicit choice',()=>{
  assert.equal(initialLanguage(),'en');
  assert.equal(initialLanguage({},'ko-KR'),'ko');
  assert.equal(initialLanguage({},'ko'),'ko');
  assert.equal(initialLanguage({},'KO_kr'),'ko');
  assert.equal(initialLanguage({},'en-US'),'en');
  assert.equal(initialLanguage({},'ja-JP'),'en');
  assert.equal(initialLanguage({language:'ko'},'en-US'),'en');
  assert.equal(initialLanguage({language:'en',languageChosen:false},'ko-KR'),'ko');
  assert.equal(initialLanguage({language:'fr',languageChosen:true},'ko-KR'),'ko');
});
test('explicit Setup language choices survive a saved-state round trip',()=>{
  for(const language of ['ko','en'])for(const browserLanguage of ['ko-KR','en-US'])assert.equal(initialLanguage(JSON.parse(JSON.stringify({language,languageChosen:true})),browserLanguage),language);
});
