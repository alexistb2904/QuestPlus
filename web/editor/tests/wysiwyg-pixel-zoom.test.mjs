import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {validRich,richToHtml} from '../src/core/rich-format.js';
import {emptyProject,category,quest,fileMap,importDocuments,seedProjectLocale,validate} from '../src/core/project.js';

const png='data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aet0AAAAASUVORK5CYII=';
const read=p=>fs.readFileSync(new URL(p,import.meta.url),'utf8');

test('pixel font sizes are validated and serialized for annotations and descriptions',()=>{
 const parts=[{type:'text',text:'Large quest text',sizePx:46,font:'default',bold:true},{type:'image',data:png,width:640,height:512}];
 assert.ok(validRich(parts));
 assert.match(richToHtml(parts),/font-size:46px/);
 assert.match(richToHtml(parts),/width="640" height="512"/);
 assert.equal(validRich([{type:'text',text:'x',sizePx:10000}]),true);
 assert.equal(validRich([{type:'text',text:'x',sizePx:0}]),false);
 assert.equal(validRich([{type:'image',data:png,width:2049,height:512}]),true);
 assert.equal(validRich([{type:'image',data:png,width:100000,height:100000}]),true);
 const project=emptyProject();project.categories=[category('questplus:pix','Pixel')];
 const questData=quest(project.categories[0],'questplus:pix/intro','Intro');
 questData.description={en_us:'Image'};
 questData.descriptionRich={en_us:parts};
 project.quests=[questData];
 assert.deepEqual(validate(project),[]);
 const doc=fileMap(project);
 const imported=importDocuments([...doc].filter(([name])=>name.endsWith('.json')).map(([name,content])=>({name,content})));
 assert.deepEqual(imported.quests[0].descriptionRich.en_us,parts);
 seedProjectLocale(imported,'fr_fr');
 assert.deepEqual(imported.quests[0].descriptionRich.fr_fr,parts);
 imported.quests[0].descriptionRich.fr_fr[0].sizePx=24;
 assert.equal(imported.quests[0].descriptionRich.en_us[0].sizePx,46);
});

test('zoom controls scale every node and annotation without changing saved world dimensions',()=>{
 const canvas=read('../src/components/TreeCanvas.tsx');
 assert.match(canvas,/width:nodeSize,height:nodeSize/);
 assert.match(canvas,/width:nodeWidth,height:nodeHeight/);
 assert.match(canvas,/transform:'translate\(-50%,-50%\) scale\('\+clampZoom\(camera\.zoom\)\+'\)'/);
 assert.match(canvas,/style=\{\{left:p\.x,top:p\.y,width,height,transform:'translate\(-50%,-50%\) scale\('\+z\+'\)'/);
 assert.match(canvas,/showQuestTitles=false/);
 assert.match(canvas,/node-title-below/);
 const app=read('../src/App.tsx');
 assert.match(app,/useState\(\(\)=>localStorage\.getItem\("questplus\.quest-titles"\)==="true"\)/);
 assert.match(app,/aria-pressed=\{showQuestTitles\}/);
});

test('WYSIWYG allows direct pixel sizing and drag-resizing in both editing modes',()=>{
 const editor=read('../src/components/RichEditor.tsx');
 for(const token of ['sizePx','applyPixelSize','image-grip','onPointerMove','onPointerUp','mode="description"','onRichChange'])
  assert.ok(editor.includes(token),token);
 assert.match(editor,/image&&<div className="rich-image-tools">/);
 const renderer=read('../../../client/src/main/java/dev/questplus/fabric/client/RichAnnotationRenderer.java');
 assert.match(renderer,/get\("sizePx"\)/);
 const registry=read('../../../server/src/main/java/dev/questplus/core/QuestRegistry.java');
 assert.match(registry,/descriptionRich\(/);
 assert.match(registry,/sizePx/);
});
