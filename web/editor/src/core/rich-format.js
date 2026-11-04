// A deliberately small, portable format shared by Quest Studio and the Minecraft client.
// Images are embedded data URIs (never remote URLs). Everything is serialized as JSON.
export const RICH_FONTS=['default','uniform','alt','illageralt'];
export const RICH_MAX_MEDIA=96*1024;
export const RICH_MAX_PARTS=96;
export const RICH_MAX_CATEGORY=900*1024;
const ESC={'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'};
const esc=v=>String(v??'').replace(/[&<>"']/g,x=>ESC[x]);
export const mediaPattern=/^data:image\/(png|jpeg|gif);base64,([a-zA-Z0-9+/]+={0,2})$/;
export const imageBytes=url=>{const m=String(url||'').match(mediaPattern);return m?Math.floor(m[2].length*3/4)-(m[2].endsWith('==')?2:m[2].endsWith('=')?1:0):Infinity;};
export function validRich(parts){
 if(!Array.isArray(parts)||parts.length>RICH_MAX_PARTS)return false;
 for(const v of parts){
  if(!v||typeof v!=='object')return false;
  if(v.type==='image'){
   if(!mediaPattern.test(v.data||'')||imageBytes(v.data)>RICH_MAX_MEDIA)return false;
   if(!Number.isInteger(v.width)||!Number.isInteger(v.height)||v.width<8||v.height<8||v.width>170||v.height>150)return false;
  }else if(v.type==='text'){
   if(typeof v.text!=='string'||v.text.length>2048)return false;
   if(v.font!=null&&!RICH_FONTS.includes(v.font))return false;
   if(v.size!=null&&(!Number.isFinite(v.size)||v.size<0.75||v.size>2))return false;
   if(v.color!=null&&!/^#[0-9a-f]{6}$/i.test(v.color))return false;
   if(v.highlight!=null&&!/^#[0-9a-f]{6}$/i.test(v.highlight))return false;
   for(const key of ['bold','italic','underline','strike'])if(v[key]!=null&&typeof v[key]!=='boolean')return false;
  }else return false;
 }
 return true;
}
export const richText=parts=>(parts||[]).filter(p=>p.type==='text').map(p=>p.text).join('').trim();
export function richToHtml(parts){
 if(!validRich(parts))return '';
 return parts.map(p=>{
  if(p.type==='image')return '<img draggable="false" alt="Embedded image" src="'+esc(p.data)+'" width="'+p.width+'" height="'+p.height+'">';
  const style=[];
  if(p.font)style.push('--mc-font:'+p.font);
  if(p.size)style.push('font-size:'+Math.round(p.size*100)+'%');
  if(p.color)style.push('color:'+p.color);
  if(p.highlight)style.push('background-color:'+p.highlight);
  if(p.bold)style.push('font-weight:700');
  if(p.italic)style.push('font-style:italic');
  if(p.underline||p.strike)style.push('text-decoration:'+(p.underline?'underline ':'')+(p.strike?'line-through':''));
  return '<span data-font="'+esc(p.font||'default')+'" style="'+style.join(';')+'">'+esc(p.text).replace(/\n/g,'<br>')+'</span>';
 }).join('');
}
