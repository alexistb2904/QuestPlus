import React,{useCallback,useEffect,useRef,useState} from 'react';
import {richFromDom,toEmbeddedImage,plainFallback} from '../ui/rich-dom.js';
import {richToHtml} from '../core/rich-format.js';
import type {RichPart} from '../types';

type Props={content:RichPart[];onChange:(parts:RichPart[],plain:string)=>void;locale:string};
export function RichEditor({content,onChange,locale}:Props){
 const ref=useRef<HTMLDivElement>(null),savedRange=useRef<Range|null>(null),selectedImage=useRef<HTMLImageElement|null>(null);
 const [image,setImage]=useState<{width:number;height:number}|null>(null);
 useEffect(()=>{if(ref.current)ref.current.innerHTML=richToHtml(content);savedRange.current=null;selectedImage.current=null;setImage(null)},[locale]);
 const read=useCallback(()=>{
  if(!ref.current)return;
  const parts=richFromDom(ref.current);
  if(parts)onChange(parts,plainFallback(parts));
 },[onChange]);
 const captureSelection=()=>{
  const s=window.getSelection();
  if(s?.rangeCount&&ref.current?.contains(s.anchorNode))savedRange.current=s.getRangeAt(0).cloneRange();
 };
 const restore=()=>{
  const s=window.getSelection(),r=savedRange.current;
  if(!s||!r||!ref.current?.contains(r.commonAncestorContainer))return;
  try{s.removeAllRanges();s.addRange(r.cloneRange())}catch{}
 };
 const command=(name:string,value?:string)=>{
  ref.current?.focus();restore();document.execCommand(name,false,value||null);read();captureSelection();
 };
 const insert=async(file:File)=>{
  try{
   const data=await toEmbeddedImage(file);
   const tag=document.createElement('img');tag.src=data.data;tag.width=data.width;tag.height=data.height;tag.alt='Embedded image';
   ref.current?.focus();restore();
   const s=window.getSelection(),r=s?.rangeCount?s.getRangeAt(0):null;
   if(r&&ref.current?.contains(r.commonAncestorContainer)){r.deleteContents();r.insertNode(tag);r.setStartAfter(tag);r.collapse(true);s!.removeAllRanges();s!.addRange(r)}
   else ref.current?.append(tag);
   read();
  }catch(e){alert((e as Error).message||'Unsupported image')}
 };
 const chooseImage=(el:HTMLImageElement)=>{
  selectedImage.current=el;setImage({width:el.width,height:el.height});
 };
 const imageSize=(prop:'width'|'height',value:number)=>{
  const img=selectedImage.current;
  if(!img||!ref.current?.contains(img))return;
  const v=Math.max(8,Math.min(prop==='width'?170:150,value||8));
  img.setAttribute(prop,String(v));setImage({width:img.width,height:img.height});read();
 };
 return <div className="rich-studio">
  <div className="rich-toolbar" onPointerDown={e=>{if((e.target as HTMLElement).closest('button'))e.preventDefault()}}>
   <button type="button" onClick={()=>command('bold')} title="Bold"><b>B</b></button>
   <button type="button" onClick={()=>command('italic')} title="Italic"><i>I</i></button>
   <button type="button" onClick={()=>command('underline')} title="Underline"><u>U</u></button>
   <button type="button" onClick={()=>command('strikeThrough')} title="Strikethrough"><s>S</s></button>
   <button type="button" onClick={()=>command('hiliteColor','#ffe178')} title="Highlight">▤</button>
   <select aria-label="Font" defaultValue="default" onChange={e=>command('fontName',e.target.value)}>
    <option value="default">Minecraft</option><option value="uniform">Uniform</option><option value="alt">Alt</option><option value="illageralt">Illager</option>
   </select>
   <select aria-label="Text size" defaultValue="3" onChange={e=>command('fontSize',e.target.value)}>
    <option value="2">Small</option><option value="3">Normal</option><option value="4">Large</option><option value="5">XL</option>
   </select>
   <label className="rich-upload">+ Image<input type="file" accept="image/png,image/jpeg,image/webp,image/gif" hidden
     onChange={e=>{const file=e.target.files?.[0];e.target.value='';if(file)void insert(file)}}/></label>
  </div>
  {image&&<div className="rich-image-tools">
   <strong>Image</strong>
   <label>W <input aria-label="Image width" type="number" min="8" max="170" value={image.width} onChange={e=>imageSize('width',Number(e.target.value))}/></label>
   <label>H <input aria-label="Image height" type="number" min="8" max="150" value={image.height} onChange={e=>imageSize('height',Number(e.target.value))}/></label>
   <button aria-label="Remove image" onClick={()=>{selectedImage.current?.remove();selectedImage.current=null;setImage(null);read()}}>×</button>
  </div>}
  <div ref={ref} contentEditable role="textbox" aria-label={'Annotation '+locale} aria-multiline="true" className="rich-edit"
   suppressContentEditableWarning onInput={read} onKeyUp={captureSelection} onMouseUp={captureSelection}
   onClick={e=>{const img=(e.target as HTMLElement).closest('img');if(img)chooseImage(img as HTMLImageElement);else if(image)setImage(null)}}
   onPaste={e=>{const images=Array.from(e.clipboardData.files).filter(f=>f.type.startsWith('image/'));if(images.length){e.preventDefault();void insert(images[0])}else{e.preventDefault();document.execCommand('insertText',false,e.clipboardData.getData('text/plain'));read()}}}/>
 </div>;
}
