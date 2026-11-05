import React,{useId,useState} from 'react';
import {ChevronDown,SlidersHorizontal,PenLine,Copy,Check,Gift,Target,Info} from 'lucide-react';
import {annotationText,annotationRich,text,selectedOwner} from '../core/project.js';
import {gameLayout} from '../core/game-layout.js';
import {childrenOf,setAt,addChild,wrap,OBJECTIVE_KINDS} from '../core/conditions.js';
import {GearEditor,armorPreset} from './GearEditor';
import {hubEditorDescriptors} from '../core/hub-registry.js';
import {versionWarning} from '../ui/minecraft-icons.js';
import {ItemIcon} from './IconPicker';
import {RichEditor,DescriptionEditor} from './RichEditor';
import {tUI} from '../ui/ui-i18n.js';
import type {Project,Selection} from '../types';

export type Props={project:Project;categoryId?:string|null;selection:Selection|null;language:string;onUpdate:(path:string,value:any)=>void;onAction:(kind:string,index?:number)=>void;onChooseIcon:(path:string)=>void;onCondition:(scope:string,value:any)=>void;onAddLocale?:(id:string)=>void;onOpenTranslations?:()=>void;translationRequest?:number;};
type FieldProps={label:string;value:any;onChange:(value:any)=>void;type?:string;placeholder?:string;rows?:number};
export function Field({label,value,onChange,type='text',placeholder='',rows}:FieldProps){
 return <label className="field"><span>{tUI(label)}</span>{rows?
  <textarea aria-label={tUI(label)} rows={rows} value={value??''} placeholder={tUI(placeholder)} onChange={e=>onChange(e.target.value)}/>:
  <input aria-label={tUI(label)} type={type} value={value??''} placeholder={tUI(placeholder)} onChange={e=>onChange(type==='number'?Number(e.target.value):e.target.value)}/>}
 </label>;
}
export function Select({label,value,options,onChange}:{label:string;value:any;options:[string,string][];onChange:(v:string)=>void}){
 return <label className="field"><span>{tUI(label)}</span><select aria-label={tUI(label)} value={value??''} onChange={e=>onChange(e.target.value)}>{options.map(([id,name])=><option value={id} key={id}>{tUI(name)}</option>)}</select></label>;
}
export function Section({title,children}:{title:string;children:React.ReactNode}){return <section className="section"><div className="section-title">{tUI(title)}</div>{children}</section>;}
export function Disclosure({title,children}:{title:string;children:React.ReactNode}){
 const [open,setOpen]=useState(false),id=useId(),Icon=title==='Conditions'?SlidersHorizontal:PenLine;
 return <div className={'disclosure'+(open?' open':'')}><button type="button" className="disclosure-trigger" aria-expanded={open} aria-controls={id} onClick={()=>setOpen(v=>!v)}><Icon size={14}/><span>{tUI(title)}</span><ChevronDown className="disclosure-icon" size={14}/></button><div className="disclosure-content" id={id} hidden={!open}>{children}</div></div>;
}
export function Translated({value,prefix,label,onUpdate}:{value:any;prefix:string;label:string;language?:string;onUpdate:(path:string,value:any)=>void}){
 return <Field label={tUI(label)+' · en_us'} value={value?.en_us||''} onChange={v=>onUpdate(prefix+'.en_us',v)}/>;
}
function IconField({id,onUpdate,onChooseIcon,project}:{id:string;onUpdate:(v:string)=>void;onChooseIcon:()=>void;project:Project}){
 return <div className="field"><span>{tUI('Icon')}</span><div className="react-icon-field">
  <ItemIcon id={id}/>
  <input aria-label={tUI('Icon')} spellCheck={false} value={id||''} onChange={e=>onUpdate(e.target.value)} placeholder="minecraft:book / mod:item"/>
  <button type="button" className="button secondary" onClick={onChooseIcon}>{tUI('Browse')}</button>
 </div>{versionWarning(project,id)&&<small className="version-alert">{versionWarning(project,id)}</small>}</div>;
}
// TODO(Codex P1): extend registry descriptors to objective parameters and support
// typed modded value pickers; this condition form only covers declared scalar fields.
export function ConditionEditor({value,scope,project,onChange,currentQuestId}:{value:any;scope:string;project:Project;onChange:(next:any)=>void;currentQuestId?:string}){
 const requires=scope==='requires';
 const available=project.quests.filter(q=>q.id&&q.id!==currentQuestId&&!(q.kind==='information'&&q.acknowledgement==='read_only')).map(q=>[q.id,text(q.title,'en_us')||q.id] as [string,string]);
 const mutate=(fn:(root:any)=>any)=>{try{onChange(fn(value))}catch(e){console.error(e)}};
 const newLeaf=()=>requires?{quest:available[0]?.[0]||''}:{dimension:'minecraft:overworld'};
 const add=(path:string,child:any)=>mutate(root=>addChild(root,path,child));
 const remove=(path:string)=>mutate(root=>setAt(root,path,null));
 const group=(path:string,kind:'and'|'or'|'not')=>mutate(root=>root?wrap(root,path,kind):{[kind]:kind==='not'?newLeaf():[newLeaf()]});
 function render(node:any,path:string,depth:number):React.ReactNode{
  const child=childrenOf(node);
  if(child){
   return <div key={path} className={'cond-group depth-'+Math.min(depth,4)}>
    <div className="cond-group-head"><select className="cond-op" aria-label={tUI("Logical operator")} value={child.type} onChange={e=>group(path,e.target.value as any)}>
     <option value="and">AND</option><option value="or">OR</option><option value="not">NOT</option>
    </select><button aria-label={tUI('Remove condition')} className="cond-remove" onClick={()=>remove(path)}>×</button></div>
    <div className="cond-children">{child.children.map((c:any,i:number)=>render(c,path?path+'.'+i:String(i),depth+1))}</div>
    {child.type!=='not'&&<div className="cond-tools"><button disabled={requires&&!available.length} onClick={()=>add(path,newLeaf())}>+ {tUI(requires?'Quest':'Condition')}</button></div>}
   </div>;
  }
  if(requires){
   return <div className="cond-leaf" key={path}><select aria-label={tUI('Prerequisite quest')} value={node?.quest||''} onChange={e=>mutate(root=>setAt(root,path,{quest:e.target.value}))}>
    <option value="">{tUI('Select quest…')}</option>{node?.quest&&!available.some(([id])=>id===node.quest)&&<option value={node.quest}>{node.quest}</option>}{available.map(([id,name])=><option key={id} value={id}>{name}</option>)}
   </select><button aria-label={tUI('Remove condition')} className="cond-remove" onClick={()=>remove(path)}>×</button></div>;
  }
 const kind=Object.keys(node||{})[0]||'dimension',val=node?.[kind];
 const extensions=hubEditorDescriptors('condition');
 const choices:[string,string][]=[...OBJECTIVE_KINDS,['equipment','Equipment'],['position','Position'],['context','Context'],...extensions.map(d=>[d.id,d.label] as [string,string])];
 const defaults:Record<string,any>={sneaking:true,equipment:{slot:'mainhand',item:'minecraft:stone'},'questplus:gear':{items:armorPreset('iron')},position:{xMin:0},context:{key:'',equals:true}};
 const setKind=(next:string)=>mutate(root=>{
  const descriptor=extensions.find(d=>d.id===next);
  const initial=descriptor?Object.fromEntries(descriptor.fields.map((field:any)=>[field.key,field.type==='boolean'?false:field.type==='integer'?0:'minecraft:stone'])):undefined;
  return setAt(root,path,{[next]:initial??defaults[next]??(next.startsWith('health')?2:'minecraft:overworld')});
 });
 const setMember=(member:string,next:any)=>mutate(root=>setAt(root,path,{[kind]:{...val,[member]:next}}));
  return <div className="cond-leaf" key={path}>
   <select aria-label={tUI('Condition type')} value={kind} onChange={e=>setKind(e.target.value)}>
    {choices.map(([key,name]:[string,string])=><option key={key} value={key}>{tUI(name)}</option>)}
    {!choices.some(([key])=>key===kind)&&<option value={kind}>{kind}</option>}
   </select>
   {kind==='sneaking'?<select aria-label={tUI('Condition value')} value={String(val)} onChange={e=>mutate(root=>setAt(root,path,{sneaking:e.target.value==='true'}))}><option value="true">{tUI('Yes')}</option><option value="false">{tUI('No')}</option></select>:
    kind==='questplus:gear'?<GearEditor conditions={node} onChange={next=>mutate(root=>setAt(root,path,next))}/>:
    kind==='equipment'?<><select aria-label={tUI("Equipment slot")} value={val?.slot||'mainhand'} onChange={e=>setMember('slot',e.target.value)}>{['mainhand','offhand','head','chest','legs','feet'].map(s=><option key={s} value={s}>{s}</option>)}</select><input aria-label={tUI("Equipment item")} value={val?.item||''} onChange={e=>setMember('item',e.target.value)}/></>:
    kind==='position'?<div className="field-row">{['xMin','xMax','yMin','yMax','zMin','zMax'].map(axis=><label className="field" key={axis}><span>{axis}</span><input type="number" value={val?.[axis]??''} onChange={e=>{const next={...val};if(e.target.value==='')delete next[axis];else next[axis]=Number(e.target.value);mutate(root=>setAt(root,path,{position:next}));}}/></label>)}</div>:
    kind==='context'?<><input aria-label={tUI("Context key")} value={val?.key||''} onChange={e=>setMember('key',e.target.value)}/><input aria-label={tUI("Context value (JSON)")} defaultValue={JSON.stringify(val?.equals??true)} key={path+':'+JSON.stringify(val?.equals)} onBlur={e=>{try{const next=JSON.parse(e.target.value);if(next===null||typeof next==='object')throw Error();setMember('equals',next)}catch{e.target.value=JSON.stringify(val?.equals??true)}}}/></>:
    extensions.some(d=>d.id===kind)&&val&&typeof val==='object'&&!Array.isArray(val)?<div className="field-row">
     {extensions.find(d=>d.id===kind)!.fields.map((field:any)=><label key={field.key} className="field">
       <span>{field.label}</span>{field.type==='boolean'?<input type="checkbox" checked={!!val[field.key]} onChange={e=>setMember(field.key,e.target.checked)}/>:
       <input aria-label={field.label} type={field.type==='integer'?'number':'text'} value={val[field.key]??''}
        onChange={e=>setMember(field.key,field.type==='integer'?Math.round(Number(e.target.value)||0):e.target.value)}/>}</label>)}
    </div>:
    typeof val==='object'?<small>{tUI('Edit custom conditions in Advanced JSON.')}</small>:
    <input aria-label={tUI('Condition value')} value={val??''} onChange={e=>mutate(root=>setAt(root,path,{[kind]:kind.startsWith('health')?Number(e.target.value):e.target.value}))}/>}
   <button aria-label={tUI('Remove condition')} className="cond-remove" onClick={()=>remove(path)}>×</button>
  </div>;
 }
 return <div className="condition-builder">{value?render(value,'',0):<div className="cond-empty">{tUI('No conditions')}</div>}
  <div className="cond-tools root-tools">
   <button disabled={requires&&!available.length} onClick={()=>add('',newLeaf())}>+ {tUI(requires?'Quest':'Condition')}</button>
   <button disabled={!value&&requires&&!available.length} onClick={()=>group('','and')}>AND</button><button disabled={!value&&requires&&!available.length} onClick={()=>group('','or')}>OR</button><button disabled={!value&&requires&&!available.length} onClick={()=>group('','not')}>NOT</button>
  </div>
 </div>;
}
function TranslationHelp({onOpen}:{onOpen?:()=>void}){
 const message=tUI('Add translations in the Languages tab.');
 return <button type="button" className="translation-help" title={message} aria-label={tUI('Open the Languages tab to translate this text')} onClick={onOpen}><Info size={15} aria-hidden="true"/></button>;
}
export function Inspector({project,categoryId,selection,language,onUpdate,onAction,onChooseIcon,onOpenTranslations}:Props){
 const [copyStatus,setCopyStatus]=useState<'idle'|'copied'|'error'>('idle');
 const category=selection?.type==='category'?project.categories.find(c=>c.id===selection.id):undefined;
 const quest=selection?.type==='quest'?project.quests.find(q=>q.id===selection.id):undefined;
 const note=selection?.type==='annotation'?selectedOwner(project,selection,categoryId):undefined;
 let position=quest?.layout;
 if(quest&&!position){try{position=gameLayout(project.quests.filter(q=>q.category===quest.category)).points.get(quest.id)}catch{/* Invalid prerequisites remain editable while validation reports the cycle. */}}
 const changeKind=(next:string)=>{if(next===(quest?.kind||'standard'))return;if(next==='information'&&!confirm(tUI('Changing to information removes objectives, tiers and their rewards, and gameplay constraints. Continue?')))return;onAction('kind:'+next)};
 const name=quest?text(quest.title,'en_us'):category?text(category.title,'en_us'):note?annotationText(note,language):'';
 return <aside className="inspector">
  <div className="inspector-head"><h2>{tUI('Properties')}</h2></div>
  {!selection?<div className="inspector-body"><p>{tUI('Select an element to edit.')}</p></div>:<>
   <div className="selection-overview"><div className="selection-summary">
    {(quest||category)&&<ItemIcon id={(quest||category)!.icon} size={36}/>}
    <div><strong>{name}</strong><small title={selection.id}>{selection.id}</small></div>
    <span>{tUI(quest?'Quest':note?'Annotation':'Category')}</span>
   </div>{quest&&<div className="selection-counts"><span><Target size={14}/>{tUI('Objectives')}: {quest.objectives?.length||0}</span><span><Gift size={14}/>{tUI('Rewards')}: {quest.rewards?.length||0}</span></div>}</div>
   <div className="inspector-body">
    {category&&<>
     <Section title="Category">
      <div className="translated-field"><Field label="Name (English)" value={category.title?.en_us||''} onChange={v=>onUpdate('title.en_us',v)}/><TranslationHelp onOpen={onOpenTranslations}/></div>
      <IconField id={category.icon} project={project} onUpdate={v=>onUpdate('icon',v)} onChooseIcon={()=>onChooseIcon('icon')}/>
      <Field label="Display order (lowest first)" type="number" value={category.order??''} placeholder="0" onChange={v=>onUpdate('order',v)}/>
      <div className="translated-field"><Field label="Description (English)" value={category.description?.en_us||''} onChange={v=>onUpdate('description.en_us',v)} rows={3}/><TranslationHelp onOpen={onOpenTranslations}/></div>
     </Section>
     <Section title="Add"><div className="toolbar-row">
      <button className="button secondary" onClick={()=>onAction('create-quest')}>+ {tUI('Quest')}</button>
      <button className="button secondary" onClick={()=>onAction('create-annotation')}>+ {tUI('Annotation')}</button>
      <button className="button secondary" onClick={()=>onAction('create-background')}>+ {tUI('Background')}</button>
     </div></Section>
     <Disclosure title="Category ID"><Field label="ID" value={category.id} onChange={v=>onUpdate('id',v)}/><button className="button danger" onClick={()=>onAction('delete-category')}>{tUI('Delete category')}</button></Disclosure>
    </>}
    {note&&<>
     <Section title="Annotation">
      {note.style!=='background'&&<RichEditor key={note.id+'-'+language} locale={language}
       content={annotationRich(note,language)||[{type:'text',text:annotationText(note,language),font:'default'}]}
       onChange={(parts,plain)=>onUpdate('rich-and-text',{locale:language,parts,plain})}/>}
      <Select label="Text alignment" value={note.align||'center'} options={[['left','Left'],['center','Center'],['right','Right']]} onChange={v=>onUpdate('align',v)}/>
      <Select label="Style" value={note.style||'note'} options={[[ 'note','Framed'],['heading','Heading'],['label','Label'],['background','Background']]} onChange={v=>onUpdate('style',v)}/>
      <Field label="Color" type="color" value={note.color||'#c6a986'} onChange={v=>onUpdate('color',v)}/>
      <div className="field-row"><Field label="Width" type="number" value={note.width??170} onChange={v=>onUpdate('width',Math.max(1,Math.round(v)||1))}/>
      <Field label="Height" type="number" value={note.height??100} onChange={v=>onUpdate('height',Math.max(1,Math.round(v)||1))}/></div>
      <div className="field-row"><Field label="X" type="number" value={note.x} onChange={v=>onUpdate('x',v)}/><Field label="Y" type="number" value={note.y} onChange={v=>onUpdate('y',v)}/></div>
     </Section>
    </>}
    {quest&&<>
     <Section title="Quest">
      <label className="field"><span>{tUI('Quest ID')}</span><span className="id-field"><input aria-label={tUI('Quest ID')} readOnly value={quest.id}/><button type="button" className="icon-button" aria-label={tUI('Copy quest ID')} title={copyStatus==='copied'?tUI('Copied'):copyStatus==='error'?tUI('Copy unavailable'):tUI('Copy quest ID')} onClick={async()=>{try{await navigator.clipboard.writeText(quest.id);setCopyStatus('copied')}catch{setCopyStatus('error')}}}>{copyStatus==='copied'?<Check size={16}/>:<Copy size={16}/>}</button></span>{copyStatus==='error'&&<small role="status">{tUI('Copy unavailable')}</small>}</label>
      <div className="translated-field"><Field label="Title (English)" value={quest.title?.en_us||''} onChange={v=>onUpdate('title.en_us',v)}/><TranslationHelp onOpen={onOpenTranslations}/></div>
      <Select label="Type" value={quest.kind||'standard'} options={[['standard','Quest'],['information','Information']]} onChange={changeKind}/>
      <Select label="Category" value={quest.category} options={project.categories.map(c=>[c.id,text(c.title,language)])} onChange={v=>onUpdate('category',v)}/>
      <IconField id={quest.icon} project={project} onUpdate={v=>onUpdate('icon',v)} onChooseIcon={()=>onChooseIcon('icon')}/>
      <Field label="Quest size" type="number" value={quest.size??32} onChange={v=>onUpdate('size',Math.max(1,Math.round(v)||1))}/>
      <div className="description-field"><div className="translation-label"><span>{tUI('Description (English)')}</span><TranslationHelp onOpen={onOpenTranslations}/></div><DescriptionEditor key={quest.id+'-description-en'} locale="en_us" value={quest.description?.en_us}
         rich={quest.descriptionRich?.en_us} onRichChange={parts=>onUpdate('descriptionRich.en_us',parts)}
         onChange={v=>onUpdate('description.en_us',v)}/></div>
     </Section>
     <Section title="Position"><div className="field-row">
      <Field label="X" type="number" value={position?.x??0} onChange={v=>onUpdate('layout.x',v)}/>
      <Field label="Y" type="number" value={position?.y??0} onChange={v=>onUpdate('layout.y',v)}/>
     </div></Section>
     <Section title="Visibility"><label className="checkbox-row"><input type="checkbox" checked={!!quest.hidden} onChange={e=>onUpdate('hidden',e.target.checked)}/> {tUI('Hidden until unlocked')}</label></Section>
    </>}
   </div>
   <div className="inspector-footer">
    {quest&&<><button className="button secondary" onClick={()=>onAction('duplicate-quest')}>{tUI('Duplicate')}</button><button className="button danger" onClick={()=>onAction('delete-quest')}>{tUI('Delete')}</button></>}
    {note&&<><button className="button secondary" onClick={()=>onAction('duplicate-annotation')}>{tUI('Duplicate')}</button><button className="button danger" onClick={()=>onAction('delete-annotation')}>{tUI('Delete')}</button></>}
    <button className="button secondary" onClick={()=>onAction('advanced-json')}>{tUI('Advanced JSON')}</button>
   </div>
  </>}
 </aside>;
}
