import JSON5 from 'json5';
import test from 'node:test';
import assert from 'node:assert/strict';
import {annotation,category,emptyProject,selectedOwner,fileMap,importDocuments,validate} from '../src/core/project.js';

test('same annotation ID in different categories resolves and edits only the active category',()=>{
 const p=emptyProject();
 p.categories=[category('questplus:first','First'),category('questplus:second','Second')];
 p.categories.forEach((c,i)=>c.annotations=[{...annotation('heading',i*100,0),text:{en_us:c.title.en_us}}]);
 const selection={type:'annotation',id:'heading'};
 const note=selectedOwner(p,selection,'questplus:second');
 assert.equal(note.text.en_us,'Second');
 note.text.en_us='Updated second';note.x=250;note.align='right';
 assert.equal(p.categories[0].annotations[0].text.en_us,'First');
 assert.equal(p.categories[0].annotations[0].x,0);
 assert.equal(p.categories[1].annotations[0].x,250);
 assert.equal(selectedOwner(p,selection,'questplus:missing'),undefined);
 assert.equal(selectedOwner(p,selection),undefined);
 assert.equal(selectedOwner(p,{type:'category',id:'questplus:second'},'questplus:first'),p.categories[1]);
 const docs=[...fileMap(p)].filter(([name])=>/\.json5?$/.test(name)).map(([name,content])=>({name,content}));
 assert.equal(importDocuments(docs).categories[1].annotations[0].align,'right');
});

test('alignment accepts only the three supported values and preserves legacy annotations',()=>{
 const p=emptyProject();p.categories=[category('questplus:test','Test')];
 const note=annotation('note',0,0);p.categories[0].annotations=[note];
 assert.deepEqual(validate(p),[]);
 for(const align of ['left','center','right']){note.align=align;assert.deepEqual(validate(p),[])}
 for(const align of ['justify','',null,1]){note.align=align;assert.ok(validate(p).some(e=>e.message==='Invalid annotation alignment'))}
});
