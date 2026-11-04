import React,{useCallback,useEffect,useRef,useState} from 'react';
import {Bold,Italic,Underline,Strikethrough,Highlighter,ImagePlus,Trash2,Palette,ChevronDown} from 'lucide-react';
import {richFromDom,toEmbeddedImage,plainFallback} from '../ui/rich-dom.js';
import {richToHtml} from '../core/rich-format.js';
import {DESCRIPTION_COLORS,descriptionParts,descriptionText,descriptionHtml,descriptionFromDom} from '../core/description-format.js';
import {tUI} from '../ui/ui-i18n.js';
import type {RichPart} from '../types';

type Props={content:RichPart[];onChange:(parts:RichPart[],plain:string)=>void;locale:string;mode?:'annotation'|'description';label?:string};
export function RichEditor({content,onChange,locale,mode='annotation',label}:Props){
 const ref=useRef<HTMLDivElement>(null),savedRange=useRef<Range|null>(null),selectedImage=useRef<HTMLImageElement|null>(null),lastEmitted=useRef('');
 const [image,setImage]=useState<{width:number;height:number}|null>(null);
 const [colorOpen,setColorOpen]=useState(false);
 const colorNames=['Black','Dark blue','Dark green','Dark aqua','Dark red','Dark purple','Gold','Gray','Dark gray','Blue','Green','Aqua','Red','Light purple','Yellow','White'];
 const [active,setActive]=useState<Record<string,boolean>>({});
 const isDescription=mode==='description';
 const html=isDescription?descriptionHtml(content):richToHtml(content);
 useEffect(()=>{if(ref.current&&html!==lastEmitted.current){ref.current.innerHTML=html;lastEmitted.current=html;savedRange.current=null;selectedImage.current=null;setImage(null)}},[html,locale]);
 const read=useCallback(()=>{
  if(!ref.current)return;
  const clone=ref.current.cloneNode(true) as HTMLElement;
  clone.querySelectorAll('font[color]').forEach(el=>{const font=el as HTMLElement;font.style.color=font.getAttribute('color')||''});
  const parts=isDescription?descriptionFromDom(clone):richFromDom(clone);
  if(parts){lastEmitted.current=isDescription?descriptionHtml(parts):richToHtml(parts);onChange(parts,plainFallback(parts));}
 },[onChange,isDescription]);
 const captureSelection=()=>{
  const s=window.getSelection();
  if(s?.rangeCount&&ref.current?.contains(s.anchorNode)){
   savedRange.current=s.getRangeAt(0).cloneRange();
   setActive(Object.fromEntries(['bold','italic','underline','strikeThrough'].map(name=>[name,document.queryCommandState(name)])));
  }
 };
 const restore=()=>{
  const s=window.getSelection(),r=savedRange.current;
  if(!s||!r||!ref.current?.contains(r.commonAncestorContainer))return;
  try{s.removeAllRanges();s.addRange(r.cloneRange())}catch{}
 };
 const command=(name:string,value?:string)=>{
  ref.current?.focus();restore();if(name==='foreColor')document.execCommand('styleWithCSS',false,'true');
  document.execCommand(name,false,value||null);read();captureSelection();
 };
 const insert=async(file:File)=>{
  try{
   const data=await toEmbeddedImage(file);
   const tag=document.createElement('img');tag.src=data.data;tag.width=data.width;tag.height=data.height;tag.alt=tUI('Embedded image');
   ref.current?.focus();restore();
   const s=window.getSelection(),r=s?.rangeCount?s.getRangeAt(0):null;
   if(r&&ref.current?.contains(r.commonAncestorContainer)){r.deleteContents();r.insertNode(tag);r.setStartAfter(tag);r.collapse(true);s!.removeAllRanges();s!.addRange(r)}
   else ref.current?.append(tag);
   read();
  }catch(e){alert((e as Error).message||tUI('Unsupported image'))}
 };
 const chooseImage=(el:HTMLImageElement)=>{selectedImage.current=el;setImage({width:el.width,height:el.height});};
 const imageSize=(prop:'width'|'height',value:number)=>{
  const img=selectedImage.current;
  if(!img||!ref.current?.contains(img))return;
  const v=Math.max(8,Math.min(prop==='width'?400:300,value||8));
  img.setAttribute(prop,String(v));setImage({width:img.width,height:img.height});read();
 };
 const formatButtons=[['bold','Bold',Bold],['italic','Italic',Italic],['underline','Underline',Underline],['strikeThrough','Strikethrough',Strikethrough]] as const;
 return <div className={'rich-studio'+(isDescription?' description-editor':'')}>
  <div className="rich-toolbar" role="toolbar" aria-label={tUI('Text formatting')} onPointerDown={e=>{if((e.target as HTMLElement).closest('button'))e.preventDefault()}}>
   {formatButtons.map(([name,title,Icon])=><button type="button" key={name} onClick={()=>command(name)} title={tUI(title)} aria-label={tUI(title)} aria-pressed={!!active[name]}><Icon size={15}/></button>)}
   {isDescription?<div className="rich-color"><button type="button" className="rich-color-trigger" title={tUI('Text color')} aria-label={tUI('Text color')} aria-expanded={colorOpen} onClick={()=>setColorOpen(v=>!v)}><Palette size={15}/><ChevronDown size={12}/></button></div>:<>
    <button type="button" onClick={()=>command('hiliteColor','#ffe178')} title={tUI('Highlight')} aria-label={tUI('Highlight')}><Highlighter size={15}/></button>
    <select aria-label={tUI('Font')} defaultValue="default" onChange={e=>command('fontName',e.target.value)}><option value="default">Minecraft</option><option value="uniform">Uniform</option><option value="alt">Alt</option><option value="illageralt">Illager</option></select>
    <select aria-label={tUI('Text size')} defaultValue="3" onChange={e=>command('fontSize',e.target.value)}><option value="2">{tUI('Small')}</option><option value="3">{tUI('Normal')}</option><option value="4">{tUI('Large')}</option><option value="5">{tUI('XL')}</option></select>
    <label className="rich-upload"><ImagePlus size={15}/>{tUI('Image')}<input type="file" accept="image/png,image/jpeg,image/webp,image/gif" hidden onChange={e=>{const file=e.target.files?.[0];e.target.value='';if(file)void insert(file)}}/></label>
   </>}
  </div>
  {isDescription&&colorOpen&&<div className="rich-palette" role="group" aria-label={tUI('Description colors')} onPointerDown={e=>e.preventDefault()} onKeyDown={e=>{if(e.key==='Escape'){e.stopPropagation();setColorOpen(false)}}}>{Object.entries(DESCRIPTION_COLORS).map(([code,color],index)=><button key={code} type="button" style={{backgroundColor:color}} title={tUI(colorNames[index])} aria-label={tUI(colorNames[index])} onClick={()=>{command('foreColor',color);setColorOpen(false)}}/>)}</div>}
  {image&&!isDescription&&<div className="rich-image-tools"><strong>{tUI("Image")}</strong><label>W <input aria-label={tUI('Image width')} type="number" min="8" max="400" value={image.width} onChange={e=>imageSize('width',Number(e.target.value))}/></label><label>H <input aria-label={tUI('Image height')} type="number" min="8" max="300" value={image.height} onChange={e=>imageSize('height',Number(e.target.value))}/></label><button aria-label={tUI('Remove image')} onClick={()=>{selectedImage.current?.remove();selectedImage.current=null;setImage(null);read()}}><Trash2 size={15}/></button></div>}
  <div ref={ref} contentEditable role="textbox" aria-label={label||((isDescription?'Description ':'Annotation ')+locale)} aria-multiline="true" data-placeholder={isDescription?tUI('Write a description…'):undefined} className="rich-edit"
   suppressContentEditableWarning onInput={read} onKeyUp={captureSelection} onMouseUp={captureSelection}
   onClick={e=>{if(isDescription)return;const img=(e.target as HTMLElement).closest('img');if(img)chooseImage(img as HTMLImageElement);else if(image)setImage(null)}}
   onPaste={e=>{const images=Array.from(e.clipboardData.files).filter(f=>f.type.startsWith('image/'));if(images.length&&!isDescription){e.preventDefault();void insert(images[0])}else{e.preventDefault();document.execCommand('insertText',false,e.clipboardData.getData('text/plain'));read()}}}/>
 </div>;
}

export function DescriptionEditor({value,locale,onChange}:{value?:string;locale:string;onChange:(value:string)=>void}){
 return <RichEditor mode="description" locale={locale} label={tUI('Description')+' · '+locale} content={descriptionParts(value||'')} onChange={parts=>onChange(descriptionText(parts))}/>;
}
