import React,{useEffect,useRef} from 'react';
import {X} from 'lucide-react';
import {tUI} from '../ui/ui-i18n.js';
export function Dialog({title,onClose,children,className=''}:{title:string;onClose:()=>void;children:React.ReactNode;className?:string}){
 const ref=useRef<HTMLDialogElement>(null),close=useRef(onClose);close.current=onClose;
 useEffect(()=>{const dialog=ref.current!,prior=document.activeElement as HTMLElement;dialog.showModal();const cancel=(e:Event)=>{e.preventDefault();close.current()};dialog.addEventListener('cancel',cancel);return()=>{dialog.removeEventListener('cancel',cancel);dialog.close();prior?.focus()}},[]);
 return <dialog ref={ref} className={'react-dialog '+className} aria-label={title} onClick={e=>{if(e.target===e.currentTarget){const r=e.currentTarget.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)onClose()}}}><div className="react-dialog-heading"><h2>{title}</h2><button className="icon-button" aria-label={tUI('Close')} onClick={onClose}><X size={18}/></button></div>{children}</dialog>;
}
