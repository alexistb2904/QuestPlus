import {richToHtml,validRich,richText,mediaPattern,RICH_MAX_MEDIA,RICH_FONTS} from './rich-format.js';
const DEFAULT={font:'default',size:1};
function hex(v){
 if(/^#[0-9a-f]{6}$/i.test(v||''))return v.toLowerCase();
 const x=String(v||'').match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/i);
 return x?'#'+x.slice(1,4).map(k=>Number(k).toString(16).padStart(2,'0')).join(''):null;
}
function normalizedSize(v){
 if(!v)return null;
 const pt=parseFloat(v);
 if(!Number.isFinite(pt))return null;
 if(String(v).endsWith('%'))return Math.max(.75,Math.min(2,pt/100));
 if(String(v).endsWith('px'))return Math.max(.75,Math.min(2,pt/14));
 return null;
}
export function richFromDom(root){
 const parts=[];
 function pushText(txt,style){
  if(!txt)return;
  const data={type:'text',text:txt,...style};
  const last=parts.at(-1);
  if(last&&last.type==='text'&&JSON.stringify({...last,text:''})===JSON.stringify({...data,text:''})){
   last.text+=txt;return;
  }
  parts.push(data);
 }
 function walk(node,style){
  if(node.nodeType===Node.TEXT_NODE){pushText(node.textContent,style);return;}
  if(node.nodeType!==Node.ELEMENT_NODE)return;
  const el=node,tag=el.tagName.toLowerCase();
  if(tag==='img'){
   const src=el.getAttribute('src')||'';
   if(mediaPattern.test(src))parts.push({type:'image',data:src,width:Math.min(170,Math.max(8,Number(el.getAttribute('width'))||120)),height:Math.min(150,Math.max(8,Number(el.getAttribute('height'))||64))});
   return;
  }
  if(tag==='br'){pushText('\n',style);return;}
  const s={...style};
  if(['b','strong'].includes(tag)||el.style.fontWeight==='bold')s.bold=true;
  if(['i','em'].includes(tag)||el.style.fontStyle==='italic')s.italic=true;
  if(tag==='u'||el.style.textDecoration.includes('underline'))s.underline=true;
  if(['s','strike','del'].includes(tag)||el.style.textDecoration.includes('line-through'))s.strike=true;
  if(tag==='mark')s.highlight='#ffe178';
  if(el.style.backgroundColor)s.highlight=hex(el.style.backgroundColor)||s.highlight;
  if(el.style.color)s.color=hex(el.style.color)||s.color;
  const face=el.getAttribute('face')||el.style.fontFamily||el.dataset.font;
  if(face){
   const v=face.toLowerCase();
   if(v.includes('uniform')||v.includes('mono'))s.font='uniform';
   else if(v.includes('illager'))s.font='illageralt';
   else if(v.includes('alt'))s.font='alt';
   else if(v.includes('default'))s.font='default';
  }
  const size=normalizedSize(el.style.fontSize)||(tag==='font'?({1:.75,2:.85,3:1,4:1.3,5:1.6,6:1.85,7:2})[Number(el.getAttribute('size'))]:null);
  if(size)s.size=size;
  const before=parts.length;
  for(const child of el.childNodes)walk(child,s);
  if((tag==='div'||tag==='p')&&parts.length>before)pushText('\n',s);
 }
 for(const child of root.childNodes)walk(child,DEFAULT);
 const trimmed=parts.filter(p=>p.type!=='text'||p.text.length);
 return trimmed.length<=96&&validRich(trimmed)?trimmed:null;
}
export function setRichEditor(editor,parts){editor.innerHTML=richToHtml(parts);}
export async function toEmbeddedImage(file){
 if(!file||!['image/png','image/jpeg','image/webp','image/gif'].includes(file.type))throw Error('Only PNG, JPEG, WebP or GIF images are supported.');
 if(file.size>RICH_MAX_MEDIA)throw Error('The image exceeds 96 KiB. Resize or optimize it first.');
 const data=await new Promise((resolve,reject)=>{const reader=new FileReader();reader.onerror=()=>reject(Error('Cannot read image'));reader.onload=()=>resolve(reader.result);reader.readAsDataURL(file);});
 if(!mediaPattern.test(data))throw Error('Invalid image data.');
 const img=new Image();await new Promise((resolve,reject)=>{img.onload=resolve;img.onerror=reject;img.src=data;});
 if(img.naturalWidth>1024||img.naturalHeight>1024)throw Error('Images must be at most 1024 × 1024.');
 const scale=Math.min(170/img.naturalWidth,150/img.naturalHeight,1);
 return {data,width:Math.max(8,Math.round(img.naturalWidth*scale)),height:Math.max(8,Math.round(img.naturalHeight*scale))};
}
export function plainFallback(parts){return richText(parts)||((parts||[]).some(p=>p.type==='image')?'Image':'Annotation');}
