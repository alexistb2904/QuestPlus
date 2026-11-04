import test from 'node:test';
import assert from 'node:assert/strict';
import {category,quest,emptyProject,annotation,seedProjectLocale,annotationRich,annotationText,compactCategoryImages,expandCategoryImages,fileMap,importDocuments,validate} from '../src/core/project.js';
import {validRich,RICH_MAX_MEDIA,imageBytes} from '../src/core/rich-format.js';
const tiny='data:image/png;base64,YWJj';
const make=()=>{const p=emptyProject();p.categories=[category('questplus:test','Test')];p.quests=[quest(p.categories[0],'questplus:test/one','One')];return p;};
const img=()=>({type:'image',data:tiny,width:90,height:60});

test('five references to same image become one indexed pool entry, with ZIP roundtrip',()=>{
 const p=make(),c=p.categories[0];
 c.annotations=[
  {...annotation('one',0,0),rich:{en_us:[img(),img()],fr_fr:[img()]}},
  {...annotation('two',30,20),rich:{en_us:[img(),img()]}}
 ];
 const packed=compactCategoryImages(c);
 assert.deepEqual(packed.images,[tiny]);
 assert.deepEqual(packed.annotations[0].rich.en_us.map(x=>x.ref),[0,0]);
 assert.equal(JSON.stringify(packed).includes('data:image/png'),true);
 assert.equal(JSON.stringify(packed.annotations).includes('data:image/png'),false);
 assert.equal(c.annotations[0].rich.en_us[0].data,tiny); // editor never mutates
 const files=fileMap(p),raw=JSON.parse(files.get('categories/test.json'));
 assert.equal(raw.images.length,1);
 assert.equal(raw.annotations[1].rich.en_us[1].ref,0);
 const reimport=importDocuments([...files].filter(([name])=>name.endsWith('.json')).map(([name,content])=>({name,content})));
 assert.deepEqual(reimport.categories[0].annotations,c.annotations);
 assert.equal(reimport.categories[0].images,undefined);
});
test('invalid references fail loudly instead of silently losing imagery',()=>{
 const p=make(),c=p.categories[0];c.images=[tiny];c.annotations=[{...annotation('broken'),rich:{fr_fr:[{type:'image',ref:19,width:24,height:24}]}}];
 assert.throws(()=>expandCategoryImages(c),/Invalid image reference/);
});
test('locale seed clones rich images and quest texts without overwriting existing translations',()=>{
 const p=make(),c=p.categories[0],q=p.quests[0];
 q.title={en_us:'First',fr_fr:'Premier'};
 q.description={en_us:'Golden <b>text</b>'};
 q.objectives[0].text={en_us:'Break wood'};
 q.rewards=[{type:'experience',amount:5,text:{en_us:'Bonus'}}];
 c.annotations=[{...annotation('one'),text:{en_us:'Hello'},rich:{en_us:[{type:'text',text:'Hello'},img()]}}];
 seedProjectLocale(p,'fr_fr');
 assert.equal(q.title.fr_fr,'Premier');
 assert.equal(q.description.fr_fr,'Golden <b>text</b>');
 assert.equal(q.objectives[0].text.fr_fr,'Break wood');
 assert.equal(q.rewards[0].text.fr_fr,'Bonus');
 assert.equal(c.annotations[0].rich.fr_fr[1].data,tiny);
 c.annotations[0].rich.fr_fr[0].text='Bonjour';
 assert.equal(c.annotations[0].rich.en_us[0].text,'Hello');
 seedProjectLocale(p,'es_es');
 assert.equal(c.annotations[0].rich.es_es[0].text,'Hello');
 assert.equal(annotationRich(c.annotations[0],'es_es')[1].data,tiny);
 assert.deepEqual(validate(p),[]);
});
test('preview annotation falls back to actual rich language when en_us is the seed Note',()=>{
 const note={...annotation('n'),text:{en_us:'Note',fr_fr:'Bonjour'},rich:{fr_fr:[{type:'text',text:'Bonjour'},img()]}};
 assert.equal(annotationText(note,'en_us'),'Bonjour');
 assert.equal(annotationRich(note,'en_us')[1].data,tiny);
 const p=make();p.categories[0].annotations=[note];seedProjectLocale(p,'es_es');
 assert.equal(note.text.es_es,'Bonjour');
 assert.equal(note.rich.es_es[1].data,tiny);
});
test('custom quest and annotation dimensions survive export/import and invalid sizes are rejected',()=>{
 const p=make();
 p.quests[0].size=96;
 p.categories[0].annotations=[{...annotation('banner'),width:400,height:300,rich:{en_us:[img()]}}];
 assert.deepEqual(validate(p),[]);
 const files=fileMap(p);
 const read=importDocuments([...files].filter(([name])=>name.endsWith('.json')).map(([name,content])=>({name,content})));
 assert.equal(read.quests[0].size,96);
 assert.deepEqual([read.categories[0].annotations[0].width,read.categories[0].annotations[0].height],[400,300]);
 read.quests[0].size=100;
 assert.ok(validate(read).some(x=>x.message.includes('Taille quête')));
 read.quests[0].size=32;read.categories[0].annotations[0].width=500;
 assert.ok(validate(read).some(x=>x.message.includes('Largeur annotation')));
});
test('larger image bounds are accepted and excessive data rejected',()=>{
 assert.ok(RICH_MAX_MEDIA>=384*1024);
 assert.ok(validRich([{type:'image',data:tiny,width:400,height:300}]));
 assert.equal(imageBytes(tiny),3);
 assert.equal(validRich([{type:'image',data:tiny,width:401,height:300}]),false);
});
