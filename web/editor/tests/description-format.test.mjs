import test from 'node:test';
import assert from 'node:assert/strict';
import {DESCRIPTION_COLORS,descriptionParts,descriptionText,descriptionHtml,descriptionFromDom} from '../src/core/description-format.js';
const visual=value=>descriptionParts(value).map(({font,...part})=>part);

test('Minecraft description formatting round trips with reset, colors and unicode',()=>{
 const source='Plain §lbold §oitalic§r normal §cRed §nunderlined§r\nÉpée 🗡';
 assert.deepEqual(visual(descriptionText(descriptionParts(source))),visual(source));
 assert.equal(DESCRIPTION_COLORS.c,'#ff5555');
 assert.deepEqual(visual('§lA§cB§rC'),[
  {type:'text',text:'A',bold:true},{type:'text',text:'B',color:'#ff5555'},{type:'text',text:'C'}
 ]);
});
test('plain text and literal section signs survive edits without gaining formatting',()=>{
 const parts=[{type:'text',text:'§l is literal; §z is unknown; § ends.'}];
 const saved=descriptionText(parts);
 assert.equal(saved,'§§l is literal; §§z is unknown; §§ ends.');
 assert.equal(descriptionParts(saved).map(p=>p.text).join(''),parts[0].text);
 assert.equal(descriptionParts('unknown §z and trailing §').map(p=>p.text).join(''),'unknown §z and trailing §');
 assert.equal(descriptionText(descriptionParts('ordinary text')),'ordinary text');
});
test('changing formatting stops old styles and retains the Minecraft color',()=>{
 const parts=[{type:'text',text:'Bold',color:'#55ffff',bold:true},{type:'text',text:' plain',color:'#55ffff'}];
 assert.equal(descriptionText(parts),'§b§lBold§r§b plain');
 assert.deepEqual(visual(descriptionText(parts)),parts);
});


test('long descriptions render entirely and escape pasted HTML',()=>{
 const long='A'.repeat(3000)+' <script>alert(1)</script> 🗡';
 const html=descriptionHtml(descriptionParts(long));
 assert.equal((html.match(/A/g)||[]).length,3000);
 assert.ok(html.includes('&lt;script&gt;'));
 assert.ok(!html.includes('<script>'));
});
test('DOM formatting reads nested bold, colors and line breaks without annotation limits',()=>{
 const text=value=>({nodeType:3,textContent:value});
 const element=(tag,children=[],style={},attrs={})=>({nodeType:1,tagName:tag.toUpperCase(),childNodes:children,style:{fontWeight:'',fontStyle:'',textDecoration:'',color:'',...style},getAttribute:name=>attrs[name]||null});
 const root={childNodes:[element('b',[text('A'.repeat(2500)),element('font',[text('bold red')],{}, {color:'#ff5555'})]),element('br'),text('tail')]};
 const parts=descriptionFromDom(root);
 assert.equal(parts[0].text.length,2500);
 assert.equal(parts[0].bold,true);
 assert.equal(parts[1].color,'#ff5555');
 assert.equal(parts[1].bold,true);
 assert.equal(parts.at(-1).text,'\ntail');
 assert.equal(descriptionParts(descriptionText(parts)).map(p=>p.text).join(''),'A'.repeat(2500)+'bold red\ntail');
});
