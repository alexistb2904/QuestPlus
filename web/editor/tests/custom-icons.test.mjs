import test from 'node:test';
import assert from 'node:assert/strict';
import {emptyProject,category,quest,validate,fileMap,importDocuments} from '../src/core/project.js';
import {makeZip,readZip} from '../src/core/zip.js';

test('custom icons survive a QuestPlus ZIP round trip',async()=>{
 const icon='data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/aZsAAAAASUVORK5CYII=';
 const project=emptyProject(),chapter=category('questplus:custom','Custom');chapter.icon=icon;
 const task=quest(chapter,'questplus:custom_icon','Icon');task.icon=icon;
 project.categories=[chapter];project.quests=[task];assert.deepEqual(validate(project),[]);
 const loaded=importDocuments(await readZip(makeZip(fileMap(project))));
 assert.equal(loaded.categories[0].icon,icon);assert.equal(loaded.quests[0].icon,icon);
 task.icon='data:image/svg+xml;base64,AAAA';assert.ok(validate(project).some(x=>x.message.includes('Icône')));
});
