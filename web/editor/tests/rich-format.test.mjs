import test from 'node:test';
import assert from 'node:assert/strict';
import {validRich,richToHtml,richText,imageBytes,RICH_MAX_CATEGORY} from '../src/core/rich-format.js';
import {demoProject,validate,fileMap,importDocuments} from '../src/core/project.js';
const png='data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aet0AAAAASUVORK5CYII=';
const sample=[
 {type:'text',text:'QuestPlus ',bold:true,font:'default',size:1.25,color:'#f6ce91'},
 {type:'text',text:'Welcome',underline:true,italic:true,strike:true,highlight:'#ffe178',font:'uniform'},
 {type:'image',data:png,width:32,height:24}
];
test('rich annotations store text styles and embedded image without HTML',()=>{
 assert.equal(validRich(sample),true);
 assert.equal(richText(sample),'QuestPlus Welcome');
 const html=richToHtml(sample);
 assert.match(html,/font-weight:700/);
 assert.match(html,/font-size:125%/);
 assert.match(html,/line-through/);
 assert.match(html,/src="data:image\/png;base64/);
 assert.ok(imageBytes(png)>0);
});
test('XSS, remote media, videos and too-large contents are rejected',()=>{
 assert.match(richToHtml([{type:'text',text:'<img src=x onerror=alert(1)>'}]),/&lt;img/);
 assert.equal(validRich([{type:'image',data:'https://example.com/evil.png',width:32,height:24}]),false);
 assert.equal(validRich([{type:'image',data:'data:video/mp4;base64,AA==',width:32,height:24}]),false);
 assert.equal(validRich([{type:'image',data:png,width:2049,height:24}]),false);
 assert.equal(validRich([{type:'text',text:'X'.repeat(2049)}]),false);
 assert.equal(validRich(Array.from({length:97},()=>({type:'text',text:'a'}))),false);
});
test('localized rich annotations survive QuestPlus export/import in original coordinates',()=>{
 const p=demoProject(),note={id:'banner',x:96,y:44,style:'heading',color:'#c6a986',
 text:{en_us:'QuestPlus Welcome',fr_fr:'Bienvenue'},
 rich:{en_us:sample,fr_fr:[{type:'text',text:'Bienvenue',font:'alt'}]}};
 p.categories[0].annotations=[note];
 assert.deepEqual(validate(p),[]);
 const json=fileMap(p).get('categories/combat.json');
 const exported=JSON.parse(json);
 assert.deepEqual(exported.images,[png]);
 assert.equal(exported.annotations[0].rich.en_us[2].ref,0);
 assert.ok(!Object.hasOwn(exported.annotations[0].rich.en_us[2],'data'));
 const docs=[{name:'categories/combat.json',content:json},
  {name:'quests/combat/iron_and_zombie.json',content:fileMap(p).get('quests/combat/iron_and_zombie.json')}];
 const loaded=importDocuments(docs);
 assert.deepEqual(loaded.categories[0].annotations[0],note);
 assert.equal(loaded.categories[0].annotations[0].x,96);
});
test('oversize category JSON is rejected at the 12 MiB pooled limit',()=>{
 const p=demoProject();
 p.categories[0].annotations=[{id:'big',x:0,y:0,text:{en_us:'Image'},rich:{en_us:[{type:'image',data:'data:image/png;base64,'+'A'.repeat(RICH_MAX_CATEGORY),width:120,height:120}]}}];
 assert.ok(validate(p).some(x=>/exceeds|media/.test(x.message)));
});

test('category bytes are measured in UTF-8 as Minecraft counts them',()=>{
 const p=demoProject();
 p.categories[0].annotations=Array.from({length:128},(_,i)=>({
  id:'note'+i,x:0,y:0,text:{en_us:'Note'},
  rich:{en_us:Array.from({length:17},()=>({type:'text',text:'文'.repeat(2048)}))}
 }));
 const json=JSON.stringify(p.categories[0]);
 assert.ok(json.length<RICH_MAX_CATEGORY,'UTF-16 JS length alone would silently pass');
 assert.ok(new TextEncoder().encode(json).byteLength>RICH_MAX_CATEGORY);
 assert.ok(validate(p).some(x=>/exceeds 12 MiB/.test(x.message)));
});
