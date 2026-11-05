import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {tUI,UI_LANGUAGES,setUiLocale} from '../src/ui/ui-i18n.js';
import {UI_COMPLETIONS} from '../src/ui/ui-i18n-completions.js';
import {UI_ADDITIONAL} from '../src/ui/ui-i18n-additional.js';
import {demoProject,validate,fileMap,importDocuments} from '../src/core/project.js';
import {makeZip,readZip} from '../src/core/zip.js';

const read = path=>fs.readFileSync(new URL(path,import.meta.url),'utf8');
test('all primary languages translate the editor, hints, and feedback',()=>{
 assert.deepEqual(UI_LANGUAGES,['en','fr','es','zh']);
 const samples=[
  ['Title (English)','Titre (anglais)','Título (inglés)','标题（英文）'],
  ['Description (English)','Description (anglais)','Descripción (inglés)','描述（英文）'],
  ['Save','Enregistrer','Guardar','保存'],
  ['Add a language','Ajouter une langue','Añadir un idioma','添加语言'],
  ['Quest objectives','Objectifs de la quête','Objetivos de la misión','任务目标'],
  ['ZIP exported','ZIP exporté','ZIP exportado','已导出 ZIP'],
 ];
 for(const [key,enFr,enEs,enZh] of samples){
  assert.equal(tUI(key,'en'),key);
  assert.equal(tUI(key,'fr'),enFr);
  assert.equal(tUI(key,'es'),enEs);
  assert.equal(tUI(key,'zh'),enZh);
 }
 assert.equal(tUI('Cycle de dépendances : q:a → q:b','es'),'Ciclo de dependencias: q:a → q:b');
 assert.equal(tUI('Prérequis introuvable : questplus:missing','zh'),'未找到前置条件：questplus:missing');
 setUiLocale('en');
});
test('every added locale key has 4 nonempty translations',()=>{
 for(const row of UI_ADDITIONAL){
  assert.equal(row.length,4);
  for(const text of row)assert.equal(typeof text,'string');
  for(const text of row)assert.ok(text.trim());
 }
 for(const [key,es,zh] of UI_COMPLETIONS){
  assert.ok(key && es && zh,key);
  assert.equal(tUI(key,'es'),es,key);
  assert.equal(tUI(key,'zh'),zh,key);
 }
});
test('English source properties open translations and Save uses the connected server',()=>{
 const inspector=read('../src/components/Inspector.tsx');
 const app=read('../src/App.tsx');
 const details=read('../src/components/QuestDetails.tsx');
 const studio=read('../src/hooks/useStudio.ts');
 for(const label of ['Title (English)','Description (English)','Name (English)'])assert.ok(inspector.includes(label),label);
 assert.match(inspector,/TranslationHelp/);
 assert.match(inspector,/onOpenTranslations/);
 assert.match(details,/translationRequest/);
 assert.match(details,/setTab\('localization'\)/);
 const hub=read('../src/components/HubPanel.tsx');
 assert.doesNotMatch(app+studio,/saveAndExport/);
 assert.match(hub,/if\(token&&loaded\)\{void publish\(\)/);
 assert.match(hub,/onSaveLocal\(\)/);
 assert.match(hub,/baseRevision:revision/);
 assert.match(hub,/update.status==='applied'/);
 assert.match(studio,/link\.download = "QuestPlus-quests\.zip"/);
 assert.match(studio,/window\.setTimeout\(save, 500\)/);
});
test('the explicit ZIP export uses the same importable Minecraft pack format',async()=>{
 const project=demoProject();
 assert.deepEqual(validate(project),[]);
 const zip=makeZip(fileMap(project)),decoded=importDocuments(await readZip(zip));
 assert.deepEqual(decoded.categories,project.categories);
 assert.deepEqual(decoded.quests,project.quests);
});
