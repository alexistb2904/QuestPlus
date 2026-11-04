import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {detectUiLocale,storedUiLocale,setUiLocale,getUiLocale,tUI,UI_LANGUAGES} from '../ui-i18n.js';

test('browser language detection supports English, French, Spanish and Chinese',()=>{
 assert.deepEqual(UI_LANGUAGES,['en','fr','es','zh']);
 assert.equal(detectUiLocale(['fr-FR','en-US']),'fr');
 assert.equal(detectUiLocale(['es-MX']),'es');
 assert.equal(detectUiLocale(['zh-Hans-CN']),'zh');
 assert.equal(detectUiLocale(['ja-JP','en-GB']),'en');
 assert.equal(detectUiLocale(['de-DE']),'en');
});
test('explicit UI override is saved independently from quest content language',()=>{
 const map=new Map(),storage={getItem:key=>map.get(key),setItem:(key,value)=>map.set(key,value)};
 assert.equal(storedUiLocale(storage,['zh-CN']),'zh');
 assert.equal(setUiLocale('fr',{save:true,storage}),'fr');
 assert.equal(storedUiLocale(storage,['es-ES']),'fr');
 assert.equal(tUI('Library'),'Bibliothèque');
 assert.equal(tUI('Quest tree'),'Arbre de quêtes');
 assert.equal(tUI('Create quest'),'Créer une quête');
 setUiLocale('es');
 assert.equal(tUI('Library'),'Biblioteca');
 assert.equal(tUI('Prerequisites'),'Requisitos previos');
 setUiLocale('zh');
 assert.equal(tUI('Library'),'资料库');
 assert.equal(tUI('Quest tree'),'任务树');
 setUiLocale('en');
 assert.equal(tUI('Library'),'Library');
 assert.equal(tUI('Prérequis'),'Prerequisites');
 assert.equal(getUiLocale(),'en');
});
test('front-end has two distinct language controls, not an in-game preview',()=>{
 const html=fs.readFileSync(new URL('../index.html',import.meta.url),'utf8');
 const editor=fs.readFileSync(new URL('../editor.js',import.meta.url),'utf8');
 assert.match(html,/id="ui-language"/);
 assert.match(html,/id="preview-language"/);
 assert.match(editor,/setUiLocale\(storedUiLocale\(\)\)/);
 assert.match(editor,/translateUI\(document\)/);
 assert.match(editor,/\['en_us','fr_fr','es_es','zh_cn'/);
 assert.ok(!html.includes('minecraft-preview'));
 assert.ok(!editor.includes('previewMode'));
});
