import React,{useCallback,useMemo} from 'react';
import {EVENTS,MODES,PROGRESSIONS,objectiveLabel,text} from '../../project.js';
import {childrenOf,atPath,setAt,addChild,wrap,OBJECTIVE_KINDS} from '../../conditions.js';
import {versionWarning} from '../../minecraft-icons.js';
import {ItemIcon} from './IconPicker';
import {RichEditor} from './RichEditor';
import type {Project,Selection,Category,Quest,Annotation,RichPart} from '../types';

type Props={project:Project;selection:Selection|null;language:string;onUpdate:(path:string,value:any)=>void;onAction:(kind:string,index?:number)=>void;onChooseIcon:(path:string)=>void;onCondition:(scope:string,value:any)=>void;};
type FieldProps={label:string;value:any;onChange:(value:any)=>void;type?:string;placeholder?:string;rows?:number};
function Field({label,value,onChange,type='text',placeholder='',rows}:FieldProps){
 return <label className="field"><span>{label}</span>{rows?
  <textarea rows={rows} value={value??''} placeholder={placeholder} onChange={e=>onChange(e.target.value)}/>:
  <input type={type} value={value??''} placeholder={placeholder} onChange={e=>onChange(type==='number'?Number(e.target.value):e.target.value)}/>}
 </label>;
}
function Select({label,value,options,onChange}:{label:string;value:any;options:[string,string][];onChange:(v:string)=>void}){
 return <label className="field"><span>{label}</span><select value={value??''} onChange={e=>onChange(e.target.value)}>{options.map(([id,name])=><option value={id} key={id}>{name}</option>)}</select></label>;
}
function Section({title,children}:{title:string;children:React.ReactNode}){return <section className="section"><div className="section-title">{title}</div>{children}</section>;}
function Disclosure({title,children}:{title:string;children:React.ReactNode}){return <details className="editor-details"><summary>{title}</summary><div className="details-body">{children}</div></details>;}
function Translated({value,prefix,label,language,onUpdate}:{value:any;prefix:string;label:string;language:string;onUpdate:(path:string,value:any)=>void}){
 return <div className="translation-stack">
  <Field label={label+' · en_us'} value={value?.en_us||''} onChange={v=>onUpdate(prefix+'.en_us',v)}/>
  {language!=='en_us'&&<Field label={label+' · '+language} value={value?.[language]||''} placeholder="Optional" onChange={v=>onUpdate(prefix+'.'+language,v)}/>}
 </div>;
}
function IconField({id,onUpdate,onChooseIcon,project}:{id:string;onUpdate:(v:string)=>void;onChooseIcon:()=>void;project:Project}){
 return <div className="field"><span>Icon</span><div className="react-icon-field">
  <ItemIcon id={id}/>
  <input spellCheck={false} value={id||''} onChange={e=>onUpdate(e.target.value)} placeholder="minecraft:book / mod:item"/>
  <button type="button" className="button secondary" onClick={onChooseIcon}>Browse</button>
 </div>{versionWarning(project,id)&&<small className="version-alert">{versionWarning(project,id)}</small>}</div>;
}
function ConditionEditor({value,scope,project,onChange}:{value:any;scope:string;project:Project;onChange:(next:any)=>void}){
 const requires=scope==='requires';
 const available=project.quests.filter(q=>q.id).map(q=>[q.id,text(q.title,'en_us')||q.id] as [string,string]);
 const mutate=(fn:(root:any)=>any)=>{try{onChange(fn(value))}catch(e){console.error(e)}};
 const newLeaf=()=>requires?{quest:available[0]?.[0]||''}:{dimension:'minecraft:overworld'};
 const add=(path:string,child:any)=>mutate(root=>addChild(root,path,child));
 const remove=(path:string)=>mutate(root=>setAt(root,path,null));
 const group=(path:string,kind:'and'|'or'|'not')=>mutate(root=>root?wrap(root,path,kind):{[kind]:kind==='not'?newLeaf():[newLeaf()]});
 function render(node:any,path:string,depth:number):React.ReactNode{
  const child=childrenOf(node);
  if(child){
   return <div key={path} className={'cond-group depth-'+Math.min(depth,4)}>
    <div className="cond-group-head"><select className="cond-op" aria-label="Logical operator" value={child.type} onChange={e=>group(path,e.target.value as any)}>
     <option value="and">AND</option><option value="or">OR</option><option value="not">NOT</option>
    </select><button className="cond-remove" onClick={()=>remove(path)}>×</button></div>
    <div className="cond-children">{child.children.map((c:any,i:number)=>render(c,path?path+'.'+i:String(i),depth+1))}</div>
    {child.type!=='not'&&<div className="cond-tools"><button onClick={()=>add(path,newLeaf())}>+ {requires?'Quest':'Condition'}</button></div>}
   </div>;
  }
  if(requires){
   return <div className="cond-leaf" key={path}><select value={node?.quest||''} onChange={e=>mutate(root=>setAt(root,path,{quest:e.target.value}))}>
    <option value="">Select quest…</option>{available.map(([id,name])=><option key={id} value={id}>{name}</option>)}
   </select><button className="cond-remove" onClick={()=>remove(path)}>×</button></div>;
  }
  const kind=Object.keys(node||{})[0]||'dimension',val=node?.[kind];
  return <div className="cond-leaf" key={path}>
   <select aria-label="Condition type" value={kind} onChange={e=>mutate(root=>setAt(root,path,e.target.value==='sneaking'?{sneaking:true}:{[e.target.value]:e.target.value.startsWith('health')?2:'minecraft:overworld'}))}>
    {OBJECTIVE_KINDS.map(([key,name]:[string,string])=><option key={key} value={key}>{name}</option>)}
   </select>
   {kind==='sneaking'?<select value={String(val)} onChange={e=>mutate(root=>setAt(root,path,{sneaking:e.target.value==='true'}))}><option value="true">True</option><option value="false">False</option></select>:
    <input aria-label="Condition value" value={val??''} onChange={e=>mutate(root=>setAt(root,path,{[kind]:kind.startsWith('health')?Number(e.target.value):e.target.value}))}/>}
   <button className="cond-remove" onClick={()=>remove(path)}>×</button>
  </div>;
 }
 return <div className="condition-builder">{value?render(value,'',0):<div className="cond-empty">No conditions</div>}
  <div className="cond-tools root-tools">
   <button onClick={()=>add('',newLeaf())}>+ {requires?'Quest':'Condition'}</button>
   <button onClick={()=>group('','and')}>AND</button><button onClick={()=>group('','or')}>OR</button><button onClick={()=>group('','not')}>NOT</button>
  </div>
 </div>;
}
export function Inspector({project,selection,language,onUpdate,onAction,onChooseIcon,onCondition}:Props){
 const group=project.categories.find(c=>c.id===(selection?.type==='category'?selection?.id:selection?.type==='quest'?project.quests.find(q=>q.id===selection.id)?.category:project.categories.find(c=>c.annotations?.some(a=>a.id===selection.id))?.id));
 if(!selection)return <aside className="inspector"><div className="inspector-head"><h2>Properties</h2></div></aside>;
 const category=selection.type==='category'?project.categories.find(c=>c.id===selection.id):group;
 const quest=selection.type==='quest'?project.quests.find(q=>q.id===selection.id):undefined;
 const note=selection.type==='annotation'?project.categories.flatMap(c=>c.annotations||[]).find(a=>a.id===selection.id):undefined;
 const update=onUpdate;
 return <aside className="inspector">
  <div className="inspector-head"><div className="overline">EDITOR</div><h2>{quest?'Quest':note?'Annotation':'Category'}</h2><p title={selection.id}>{selection.id}</p></div>
  <div className="inspector-body">
   {category&&selection.type==='category'&&<>
    <Section title="Category">
     <Translated label="Name" value={category.title} prefix="title" language={language} onUpdate={update}/>
     <IconField id={category.icon} project={project} onUpdate={v=>update('icon',v)} onChooseIcon={()=>onChooseIcon('icon')}/>
     <Disclosure title="Description"><Translated label="Description" value={category.description} prefix="description" language={language} onUpdate={update}/></Disclosure>
    </Section>
    <Section title="Add"><div className="toolbar-row">
     <button className="button secondary" onClick={()=>onAction('create-quest')}>+ Quest</button>
     <button className="button secondary" onClick={()=>onAction('create-annotation')}>+ Annotation</button>
    </div></Section>
    <Disclosure title="Category ID"><Field label="ID" value={category.id} onChange={v=>update('id',v)}/><button className="button danger" onClick={()=>onAction('delete-category')}>Delete category</button></Disclosure>
   </>}
   {note&&<>
    <Section title="Annotation">
     <RichEditor key={note.id+'-'+language} locale={language}
       content={note.rich?.[language]||[{type:'text',text:note.text?.[language]||note.text?.en_us||'',font:'default'}]}
       onChange={(parts,plain)=>onUpdate('rich-and-text',{locale:language,parts,plain})}/>
     <Select label="Style" value={note.style||'note'} options={[[ 'note','Framed'],['heading','Heading'],['label','Label']]} onChange={v=>update('style',v)}/>
     <Field label="Color" type="color" value={note.color||'#c6a986'} onChange={v=>update('color',v)}/>
     <div className="field-row"><Field label="X" type="number" value={note.x} onChange={v=>update('x',v)}/><Field label="Y" type="number" value={note.y} onChange={v=>update('y',v)}/></div>
    </Section>
    <Disclosure title="Actions"><button className="button secondary" onClick={()=>onAction('duplicate-annotation')}>Duplicate</button> <button className="button danger" onClick={()=>onAction('delete-annotation')}>Delete</button></Disclosure>
   </>}
   {quest&&<>
    <Section title="Quest">
     <Translated label="Name" prefix="title" value={quest.title} language={language} onUpdate={update}/>
     <IconField id={quest.icon} project={project} onUpdate={v=>update('icon',v)} onChooseIcon={()=>onChooseIcon('icon')}/>
     <Select label="Category" value={quest.category} options={project.categories.map(c=>[c.id,text(c.title,language)])} onChange={v=>update('category',v)}/>
     <Disclosure title="Description"><Translated label="Description" prefix="description" value={quest.description} language={language} onUpdate={update}/></Disclosure>
    </Section>
    <Section title="Objectives">
     <Select label="Type" value={quest.kind||'standard'} options={[['standard','Quest'],['information','Information']]} onChange={v=>onAction('kind:'+v)}/>
     {(quest.kind||'standard')==='information'?<>
      <Translated label="Content" prefix="narrative" value={quest.narrative} language={language} onUpdate={update}/>
      <Select label="Completion" value={quest.acknowledgement||'manual'} options={[[ 'manual','Acknowledge'],['read_only','Read only']]} onChange={v=>update('acknowledgement',v)}/>
     </>:<>
      {(quest.objectives||[]).map((o,i)=><div className="mini-card" key={o.id+'-'+i}>
       <div className="mini-card-head"><strong>{i+1}. {objectiveLabel(o,language)}</strong><button className="tiny danger" title="Remove objective" onClick={()=>onAction('remove-objective',i)}>×</button></div>
       <Select label="Action" value={o.event} options={[...EVENTS,...(!EVENTS.some((x:[string,string])=>x[0]===o.event)?[[o.event,o.event]]:[])]} onChange={v=>update('objectives.'+i+'.event',v)}/>
       <Field label="Target ID" value={o.target} onChange={v=>update('objectives.'+i+'.target',v)} placeholder="minecraft:cobblestone"/>
       <Field label="Count" type="number" value={o.count} onChange={v=>update('objectives.'+i+'.count',Math.max(1,v))}/>
       <Disclosure title="Custom label"><Translated label="Text" prefix={'objectives.'+i+'.text'} value={o.text} language={language} onUpdate={update}/></Disclosure>
       <Disclosure title="Conditions"><ConditionEditor project={project} scope={'objective:'+i} value={o.conditions} onChange={v=>onCondition('objective:'+i,v)}/></Disclosure>
      </div>)}
      <button className="add-line" onClick={()=>onAction('add-objective')}>+ Objective</button>
     </>}
    </Section>
    <Section title="Position"><div className="field-row">
     <Field label="X" type="number" value={quest.layout?.x??0} onChange={v=>update('layout.x',v)}/>
     <Field label="Y" type="number" value={quest.layout?.y??0} onChange={v=>update('layout.y',v)}/>
    </div></Section>
    <Disclosure title="Prerequisites"><ConditionEditor value={quest.requires} scope="requires" project={project} onChange={v=>onCondition('requires',v)}/></Disclosure>
    <Disclosure title="Rewards">
     {(quest.rewards||[]).map((r,i)=><div className="mini-card" key={i}>
      <div className="mini-card-head"><strong>{r.type==='experience'?'Experience':'Item'}</strong><button className="tiny danger" onClick={()=>onAction('remove-reward',i)}>×</button></div>
      {r.type==='experience'?<Field label="XP" type="number" value={r.amount} onChange={v=>update('rewards.'+i+'.amount',v)}/>:
       r.type==='item'?<><Field label="Item ID" value={r.item} onChange={v=>update('rewards.'+i+'.item',v)}/><Field label="Count" type="number" value={r.count} onChange={v=>update('rewards.'+i+'.count',v)}/></>:<small>Edit custom rewards in Advanced JSON.</small>}
     </div>)}
     <div className="toolbar-row"><button className="button secondary" onClick={()=>onAction('add-xp')}>+ XP</button><button className="button secondary" onClick={()=>onAction('add-item')}>+ Item</button></div>
    </Disclosure>
    <Disclosure title="Progression">
     <Select label="Mode" value={quest.progression} options={PROGRESSIONS.map((x:string)=>[x,x])} onChange={v=>update('progression',v)}/>
     <Select label="Objectives" value={quest.objectiveMode||'all'} options={MODES.map((x:string)=>[x,x])} onChange={v=>update('objectiveMode',v)}/>
     <label className="checkbox-row"><input type="checkbox" checked={!!quest.hidden} onChange={e=>update('hidden',e.target.checked)}/> Hidden until unlocked</label>
     <Field label="Cooldown (s)" type="number" value={quest.cooldownSeconds||0} onChange={v=>update('cooldownSeconds',v)}/>
     <Field label="Time limit (s)" type="number" value={quest.timeLimitSeconds||0} onChange={v=>update('timeLimitSeconds',v)}/>
    </Disclosure>
    <Disclosure title="Quest ID & actions"><p className="generated-id">{quest.id}</p>
     <button className="button secondary" onClick={()=>onAction('duplicate-quest')}>Duplicate</button> <button className="button danger" onClick={()=>onAction('delete-quest')}>Delete</button>
    </Disclosure>
   </>}
  </div>
  <div className="inspector-footer"><button className="button secondary block" onClick={()=>onAction('advanced-json')}>Advanced JSON</button></div>
 </aside>;
}
