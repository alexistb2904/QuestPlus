import {useEffect,useRef,useState} from 'react';
import {demoProject,emptyProject,category as newCategory,quest as newQuest,annotation as newAnnotation,copy,validate,fileMap,importDocuments,text} from '../../project.js';
import {questId,categoryId} from '../../quest-ids.js';
import {makeZip,readZip} from '../../zip.js';
import {gameLayout,gameFit} from '../../game-layout.js';
import type {Project,Selection,Camera,Quest,Category} from '../types';
export type Session={project:Project;categoryId:string|null;selection:Selection|null;camera:Camera;language:string};
const KEY='questplus.quest-studio.v1';
const initial=():Session=>{
 const p=demoProject() as Project;
 const fallback={project:p,categoryId:p.categories[0]?.id||null,selection:{type:'category',id:p.categories[0]?.id||''} as Selection,camera:{x:0,y:30,zoom:1},language:'en_us'};
 try{const saved=JSON.parse(localStorage.getItem(KEY)||'null');if(saved?.project?.categories&&saved?.project?.quests)return {...fallback,...saved}}catch{}
 return fallback;
};
export function owner(project:Project,selection:Selection|null):any{
 if(!selection)return null;
 if(selection.type==='quest')return project.quests.find(q=>q.id===selection.id);
 if(selection.type==='category')return project.categories.find(q=>q.id===selection.id);
 return project.categories.flatMap(c=>c.annotations||[]).find(n=>n.id===selection.id);
}
export function useStudio(){
 const [state,setState]=useState<Session>(initial),[notice,setNotice]=useState('');
 const history=useRef<Session[]>([]),future=useRef<Session[]>([]),last=useRef({field:'',time:0}),warned=useRef(false);
 const toast=(msg:string)=>{setNotice(msg);window.setTimeout(()=>setNotice(v=>v===msg?'':v),3600)};
 useEffect(()=>{const timer=window.setTimeout(()=>{
  try{localStorage.setItem(KEY,JSON.stringify(state));warned.current=false}
  catch{if(!warned.current){warned.current=true;toast('Storage is full. Export your project as ZIP.')}}},500);return()=>clearTimeout(timer)},[state]);
 function mutate(change:(s:Session)=>void,field=''){
  setState(prev=>{
   const next={...prev,project:copy(prev.project)};
   change(next);
   if(JSON.stringify(prev)===JSON.stringify(next))return prev;
   const now=Date.now();
   if(!field||last.current.field!==field||now-last.current.time>600){
    history.current.push(copy(prev));if(history.current.length>65)history.current.shift();
   }
   last.current={field,time:now};future.current=[];
   return next;
  });
 }
 function undo(){const old=history.current.pop();if(!old)return;future.current.push(copy(state));setState(old);last.current.field=''}
 function redo(){const next=future.current.pop();if(!next)return;history.current.push(copy(state));setState(next);last.current.field=''}
 function setField(path:string,value:any){
  mutate(s=>{
   const o=owner(s.project,s.selection);if(!o)return;
   if(path==='rich-and-text'){o.rich||={};o.text||={};o.rich[value.locale]=value.parts;o.text[value.locale]=value.plain;return;}
   if(path==='id'&&s.selection?.type==='category'){
    const prior=o.id;o.id=value;s.project.quests.forEach(q=>{if(q.category===prior)q.category=value});s.categoryId=value;s.selection={type:'category',id:value};return;
   }
   const parts=path.split('.');let v=o;
   for(let i=0;i<parts.length-1;i++){if(v[parts[i]]==null)v[parts[i]]={};v=v[parts[i]]}
   v[parts[parts.length-1]]=value;
  },(state.selection?.id||'')+path);
 }
 const currentCategory=state.project.categories.find(c=>c.id===state.categoryId);
 const quests=state.project.quests.filter(q=>q.category===state.categoryId);
 function fit(group=currentCategory,rows=quests){
  const el=document.getElementById('viewport');
  return gameFit([...gameLayout(rows).points.values(),...(group?.annotations||[]).map(n=>({x:n.x,y:n.y}))],el?.clientWidth||720,el?.clientHeight||450,30) as Camera;
 }
 function selectCategory(id:string){
  const cat=state.project.categories.find(c=>c.id===id);
  setState(s=>({...s,categoryId:id,selection:{type:'category',id},camera:fit(cat,s.project.quests.filter(q=>q.category===id))}));
 }
 function createCategory(){
  const name=prompt('Category (English)','New category')?.trim();if(!name)return;
  mutate(s=>{const id=categoryId(name,new Set(s.project.categories.map(c=>c.id)));s.project.categories.push(newCategory(id,name));s.categoryId=id;s.selection={type:'category',id};s.camera={x:0,y:0,zoom:1}});
 }
 function createQuest(){
  if(!currentCategory)return toast('Create a category first');
  const name=prompt('Quest (English)','New quest')?.trim();if(!name)return;
  mutate(s=>{const id=questId(currentCategory.id,name,new Set(s.project.quests.map(q=>q.id)));s.project.quests.push(newQuest(currentCategory,id,name,{x:Math.round(s.camera.x),y:Math.round(s.camera.y)}));s.selection={type:'quest',id}});
 }
 function createAnnotation(){
  if(!currentCategory)return;
  mutate(s=>{const cat=s.project.categories.find(c=>c.id===s.categoryId)!;cat.annotations||=[];let i=1,id='note';while(cat.annotations.some(n=>n.id===id))id='note'+(++i);cat.annotations.push(newAnnotation(id,s.camera.x,s.camera.y) as any);s.selection={type:'annotation',id}});
 }
 function action(kind:string,index=0){
  if(kind==='create-quest')return createQuest();
  if(kind==='create-annotation')return createAnnotation();
  if(kind.startsWith('delete-')&&!confirm('Delete selected '+kind.slice(7)+'?'))return;
  mutate(s=>{
   const selected=s.selection,o=owner(s.project,selected);if(!o)return;
   if(kind.startsWith('kind:')){
    const value=kind.slice(5);o.kind=value;
    if(value==='information'){o.schemaVersion=2;o.progression='once';o.objectives=[];o.narrative||={en_us:'Information'};delete o.tiers;delete o.objectiveMode;delete o.cooldownSeconds;delete o.timeLimitSeconds;delete o.noDeath;}
    else if(!o.objectives?.length)o.objectives=[{id:'step',event:'block_break',target:'minecraft:stone',count:1}];return;
   }
   if(kind==='duplicate-quest'){
    const clone=copy(o),id=questId(o.category,text(o.title,'en_us')+' copy',new Set(s.project.quests.map(q=>q.id)));
    clone.id=id;clone.layout={x:(o.layout?.x||0)+45,y:(o.layout?.y||0)+45};s.project.quests.push(clone);s.selection={type:'quest',id};return;
   }
   if(kind==='duplicate-annotation'){
    const group=s.project.categories.find(c=>c.annotations?.some(a=>a.id===o.id));if(!group)return;
    const clone=copy(o);let i=2;clone.id=o.id+'_copy';while(group.annotations?.some(a=>a.id===clone.id))clone.id=o.id+'_copy'+i++;
    clone.x+=40;clone.y+=40;group.annotations!.push(clone);s.selection={type:'annotation',id:clone.id};return;
   }
   if(kind==='delete-quest'){s.project.quests=s.project.quests.filter(q=>q.id!==o.id);
    for(const q of s.project.quests)if(q.requires&&JSON.stringify(q.requires).includes(o.id))delete q.requires;
    s.selection={type:'category',id:s.categoryId||''};return;}
   if(kind==='delete-annotation'){s.project.categories.forEach(c=>c.annotations=c.annotations?.filter(n=>n.id!==o.id));s.selection={type:'category',id:s.categoryId||''};return;}
   if(kind==='delete-category'){s.project.categories=s.project.categories.filter(c=>c.id!==o.id);s.project.quests=s.project.quests.filter(q=>q.category!==o.id);
    s.categoryId=s.project.categories[0]?.id||null;s.selection=s.categoryId?{type:'category',id:s.categoryId}:null;return;}
   if(kind==='add-objective'){o.objectives||=[];let i=o.objectives.length+1,id='step'+i;while(o.objectives.some((v:any)=>v.id===id))id='step'+ ++i;o.objectives.push({id,event:'block_break',target:'minecraft:stone',count:1});return;}
   if(kind==='remove-objective'){o.objectives?.splice(index,1);return;}
   if(kind==='add-xp'){o.rewards||=[];o.rewards.push({type:'experience',amount:5});return;}
   if(kind==='add-item'){o.rewards||=[];o.rewards.push({type:'item',item:'minecraft:diamond',count:1});return;}
   if(kind==='remove-reward')o.rewards?.splice(index,1);
  });
 }
 function changeCondition(scope:string,value:any){
  mutate(s=>{const q=owner(s.project,s.selection);if(!q)return;
   if(scope==='requires'){if(value)q.requires=value;else delete q.requires;}
   else{const n=Number(scope.split(':')[1]),o=q.objectives?.[n];if(o){if(value)o.conditions=value;else delete o.conditions;}}
  });
 }
 function setPosition(type:'quest'|'annotation',id:string,pos:{x:number;y:number}){
  mutate(s=>{const o=owner(s.project,{type,id});if(!o)return;if(type==='quest')o.layout=pos;else{o.x=pos.x;o.y=pos.y}},'drag:'+id);
 }
 function autoLayout(){
  if(!currentCategory||!confirm('Auto-arrange quests in this category?'))return;
  mutate(s=>{const qs=s.project.quests.filter(q=>q.category===s.categoryId),computed=gameLayout(qs.map(q=>({...q,layout:undefined})));
   qs.forEach(q=>{const p=computed.points.get(q.id);if(p)q.layout={...p}});
  });
 }
 function reset(defaults:boolean){
  if(!confirm('Replace current project? Export first if needed.'))return;
  mutate(s=>{s.project=(defaults?demoProject():emptyProject()) as Project;s.categoryId=s.project.categories[0]?.id||null;
   s.selection=s.categoryId?{type:'category',id:s.categoryId}:null;s.camera={x:0,y:0,zoom:1};s.language='en_us'});
 }
 function addLocale(){
  const id=prompt('Minecraft language code (e.g. de_de)','de_de')?.trim().toLowerCase();
  if(!id)return;if(!/^[a-z]{2,3}_[a-z]{2,4}$/.test(id)){toast('Invalid language code');return;}
  mutate(s=>{s.project.editorLocales=[...new Set([...(s.project.editorLocales||[]),id])];s.language=id});
 }
 async function importFiles(files:FileList|null){
  if(!files?.length||!confirm('Replace the current quest pack?'))return;
  try{
   const docs: {name:string;content:string}[]=[];
   for(const file of Array.from(files)){
    if(file.name.endsWith('.zip'))docs.push(...await readZip(file));
    else if(file.name.endsWith('.json'))docs.push({name:(file as any).webkitRelativePath||file.name,content:await file.text()});
   }
   const p=importDocuments(docs) as Project,errors=validate(p);if(errors.length)throw Error(errors[0].where+': '+errors[0].message);
   mutate(s=>{s.project=p;s.categoryId=p.categories[0]?.id||null;s.selection=s.categoryId?{type:'category',id:s.categoryId}:null;s.camera={x:0,y:0,zoom:1};s.language='en_us'});
   toast('Pack imported');
  }catch(e){toast('Import error: '+(e as Error).message)}
 }
 function exportZip(){
  const errors=validate(state.project);if(errors.length){toast('Fix validation errors');return false;}
  try{const blob=makeZip(fileMap(state.project)),url=URL.createObjectURL(blob),link=document.createElement('a');
   link.href=url;link.download='QuestPlus-quests.zip';document.body.appendChild(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),1500);
   toast('ZIP exported');return true;
  }catch(e){toast('Export error: '+(e as Error).message);return false}
 }
 function applyJson(raw:string){
  try{
   const obj=JSON.parse(raw),s=state.selection;if(!s||!obj||Array.isArray(obj)||typeof obj!=='object')throw Error('Invalid object');
   const p=copy(state.project) as Project;
   if(s.type==='quest'){const i=p.quests.findIndex(q=>q.id===s.id);p.quests[i]=obj}
   if(s.type==='category'){const i=p.categories.findIndex(c=>c.id===s.id);p.categories[i]=obj}
   if(s.type==='annotation')for(const c of p.categories){const i=c.annotations?.findIndex(a=>a.id===s.id)??-1;if(i!==-1){c.annotations![i]=obj;break}}
   const issues=validate(p);if(issues.length)throw Error(issues[0].where+': '+issues[0].message);
   mutate(next=>{next.project=p;if(obj.id!==s.id){next.selection={...s,id:obj.id};if(s.type==='category')next.categoryId=obj.id}});
   return '';
  }catch(e){return (e as Error).message}
 }
 return {state,setState,notice,toast,history,future,undo,redo,mutate,setField,currentCategory,quests,fit,selectCategory,createCategory,createQuest,createAnnotation,action,changeCondition,setPosition,autoLayout,reset,addLocale,importFiles,exportZip,applyJson};
}
