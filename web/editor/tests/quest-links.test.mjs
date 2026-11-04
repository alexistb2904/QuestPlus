import test from 'node:test';
import assert from 'node:assert/strict';
import {withPrerequisite,dependencyError} from '../src/core/quest-links.js';
import {references} from '../src/core/project.js';
const q=(id,requires)=>({id:'questplus:'+id,requires});
test('cables point from prerequisite to dependent and preserve complex prerequisite expressions',()=>{
 const existing={or:[{quest:'questplus:a'},{not:{quest:'questplus:b'}}]},quests=[q('a'),q('b'),q('c'),q('d',existing)];
 assert.deepEqual(withPrerequisite(quests,'questplus:a','questplus:c'),{quest:'questplus:a'});
 assert.deepEqual(withPrerequisite(quests,'questplus:c','questplus:d'),{and:[existing,{quest:'questplus:c'}]});
 assert.deepEqual(quests[3].requires,existing);
 assert.equal(references(withPrerequisite(quests,'questplus:c','questplus:d')).length,3);
});
test('cables reject self-links, duplicates, cycles, missing quests and read-only prerequisites',()=>{
 const quests=[q('a'),q('ab',{quest:'questplus:a'}),q('c',{and:[{quest:'questplus:ab'}]})];
 assert.match(dependencyError(quests,'questplus:a','questplus:a'),/another quest/);
 assert.match(dependencyError(quests,'questplus:a','questplus:ab'),/already/);
 assert.match(dependencyError(quests,'questplus:c','questplus:a'),/cycle/);
 assert.match(dependencyError(quests,'questplus:missing','questplus:a'),/not found/);
 assert.equal(dependencyError(quests,'questplus:a','questplus:c'),'');
 quests[0].kind='information';quests[0].acknowledgement='read_only';
 assert.throws(()=>withPrerequisite(quests,'questplus:a','questplus:c'),/read-only/);
});

test('unfinished language choices survive ZIP metadata without entering server quest JSON',async()=>{
 const {demoProject,fileMap,importDocuments}=await import('../src/core/project.js');
 const {makeZip,readZip}=await import('../src/core/zip.js');
 const project=demoProject();project.editorLocales=['de_de','ja_jp'];
 project.quests[0].description={en_us:'§lA bold description§r',fr_fr:'§aDescription verte'};
 const files=fileMap(project),roundTrip=importDocuments(await readZip(makeZip(files)));
 assert.deepEqual(roundTrip.editorLocales,['de_de','ja_jp']);
 assert.deepEqual(roundTrip.quests[0].description,project.quests[0].description);
 for(const [name,json] of files)if(name.startsWith('quests/'))assert.equal(JSON.parse(json).editorLocales,undefined);
 const bad=[{name:'queststudio-compatibility.json',content:JSON.stringify({editorLocales:['not a language']})}];
 assert.throws(()=>importDocuments(bad),/language/);
});
