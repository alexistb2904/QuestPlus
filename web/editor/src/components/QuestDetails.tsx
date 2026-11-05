import React,{useEffect,useState} from 'react';
import {Target,Gift,GitBranch,Repeat2,MessageCircle,Globe,Trash2,Plus,Search,Check,X} from 'lucide-react';
import {EVENTS,objectiveLabel,text} from '../core/project.js';
import {tUI} from '../ui/ui-i18n.js';
import {ItemIcon,TargetPicker} from './IconPicker';
import {DIMENSIONS,EVENT_LABELS,objectiveUi,changeObjectiveEvent,positionCoordinate,positionRadius,setPositionCoordinate,setPositionRadius} from '../core/objective-targets.js';
import {DescriptionEditor} from './RichEditor';
import {descriptionParts} from '../core/description-format.js';
import {localesOf,supportedLocales} from '../types';
import {Field,Select,Translated,Disclosure,ConditionEditor} from './Inspector';
import {GearEditor,updateGear,armorPreset} from './GearEditor';
import {hubEditorDescriptors} from '../core/hub-registry.js';
import type {Props} from './Inspector';
import type {Reward,Project,Quest,Category,Objective} from '../types';

type Tier={id:string;threshold:number;rewards:Reward[]};
type Tab='objectives'|'rewards'|'conditions'|'progression'|'npc'|'localization';
const tabIcons={objectives:Target,rewards:Gift,conditions:GitBranch,progression:Repeat2,npc:MessageCircle,localization:Globe};
const tabs:[Tab,string][]=[['objectives','Objectives'],['rewards','Rewards'],['conditions','Prerequisites'],['progression','Progression'],['npc','NPC'],['localization','Localization']];

type LocaleField={label:string;path:string;value?:Record<string,string>;rich?:boolean;multiline?:boolean;requiresSource?:boolean};
function LocalizationPanel({project,quest,category,onUpdate,onAddLocale}:{project:Project;quest?:Quest;category?:Category;onUpdate:Props['onUpdate'];onAddLocale?:Props['onAddLocale']}){
 const [selected,setSelected]=useState(''),[adding,setAdding]=useState(false),[search,setSearch]=useState('');
 const locales=localesOf(project).filter(code=>code!=='en_us');
 const locale=locales.includes(selected)?selected:locales[0]||'';
 const query=search.trim().toLowerCase();
 const choices=Object.entries(supportedLocales).filter(([id,name])=>id!=='en_us'&&!locales.includes(id)&&(id+' '+name).toLowerCase().includes(query));
 const custom=/^[a-z]{2,3}_[a-z]{2,4}$/.test(query)&&query!=='en_us'&&!locales.includes(query)&&!supportedLocales[query]?query:'';
 const add=(id:string)=>{onAddLocale?.(id);setSelected(id);setAdding(false);setSearch('')};
 const target=quest||category;
 if(!target)return null;
 const fields:LocaleField[]=[{label:'Title',path:'title',value:target.title},{label:'Description',path:'description',value:target.description,rich:!!quest,multiline:!quest}];
 if(quest){
  if(quest.kind==='information'||quest.narrative)fields.push({label:'Content',path:'narrative',value:quest.narrative,multiline:true});
  quest.objectives?.forEach((o,i)=>fields.push({label:tUI('Objective')+' '+(i+1),path:'objectives.'+i+'.text',value:o.text,requiresSource:true}));
  quest.rewards?.forEach((r,i)=>fields.push({label:tUI('Reward')+' '+(i+1),path:'rewards.'+i+'.text',value:r.text as Record<string,string>|undefined,requiresSource:true}));
  (quest.tiers||[]).forEach((tier:Tier,i:number)=>tier.rewards?.forEach((r,j)=>fields.push({label:tUI('Tier')+' '+(i+1)+' · '+tUI('Reward')+' '+(j+1),path:'tiers.'+i+'.rewards.'+j+'.text',value:r.text as Record<string,string>|undefined,requiresSource:true})));
 }
 return <div className="localization-panel">
  <div className="detail-heading"><div><strong>{tUI('Localization')}</strong><p>{tUI('English is the source. Add a language to translate this content.')}</p></div><button type="button" className="button primary" aria-expanded={adding} onClick={()=>setAdding(v=>!v)}><Plus size={15}/>{tUI('Add a language')}</button></div>
  {adding&&<div className="locale-picker"><div className="locale-search"><Search size={16}/><input autoFocus aria-label={tUI('Search languages')} placeholder={tUI('Search languages')} value={search} onChange={e=>setSearch(e.target.value)}/><button type="button" className="icon-button" aria-label={tUI('Close')} onClick={()=>setAdding(false)}><X size={15}/></button></div><div className="locale-picker-list">{choices.map(([id,name])=><button type="button" className="locale-picker-option" key={id} onClick={()=>add(id)}><span><strong>{name}</strong><small>{id}</small></span><Plus size={15}/></button>)}{custom&&<button type="button" className="locale-picker-option" onClick={()=>add(custom)}><span><strong>{custom}</strong><small>{tUI('Custom language code')}</small></span><Plus size={15}/></button>}{!choices.length&&!custom&&<p className="localization-empty">{tUI('No languages found')}</p>}</div></div>}
  {locales.length?<><div className="localization-languages" aria-label={tUI('Translation language')}>{locales.map(code=><button type="button" className={'locale-chip'+(locale===code?' active':'')} aria-pressed={locale===code} onClick={()=>setSelected(code)} key={code}>{locale===code&&<Check size={13}/>}<span>{supportedLocales[code]||code}</span><small>{code}</small></button>)}</div>
   <div className="localization-columns"><span>{tUI('Source')}</span><span>{supportedLocales[locale]||locale} · {locale}</span></div>
   {fields.map(field=>{
    const source=text(field.value,'en_us'),value=field.value?.[locale]||'',disabled=!!field.requiresSource&&!source.trim();
    const sourceText=field.rich?descriptionParts(source).map((part:any)=>part.text).join(''):source;
    return <div className="localization-field" key={field.path}><div className="localization-source"><span>{tUI(field.label)}</span><div>{sourceText||tUI('No source text')}</div></div><div className="localization-target"><span>{field.label}</span>{disabled?<><input aria-label={field.label+' · '+locale} disabled value=""/><small>{tUI('Add an English custom label first.')}</small></>:field.rich?<DescriptionEditor key={target.id+'-'+locale+'-'+field.path} locale={locale} value={value}
       rich={quest?.descriptionRich?.[locale]}
       onRichChange={parts=>onUpdate('descriptionRich.'+locale,parts)}
       onChange={v=>onUpdate(field.path+'.'+locale,v)}/>:<Field label={field.label+' · '+locale} value={value} placeholder={tUI("Optional")} rows={field.multiline?3:undefined} onChange={v=>onUpdate(field.path+'.'+locale,v)}/>}</div></div>;
   })}
  </>:<div className="localization-empty"><Globe size={22}/><strong>{tUI('No translations yet')}</strong><p>{tUI('Choose a language to start translating.')}</p></div>}
 </div>;
}


type PickerKind='block'|'item'|'entity'|'biome'|'structure'|'statistic'|'advancement';
function ObjectiveInputs({objective:o,index:i,questId,onUpdate,onCondition,onBrowse}:{
 objective:Objective;index:number;questId:string;onUpdate:Props['onUpdate'];onCondition:Props['onCondition'];
 onBrowse:(index:number,kind:PickerKind)=>void;
}){
 const kind=objectiveUi(o.event).kind as string;
 const label=objectiveUi(o.event).amount;
 const path='objectives.'+i;
 const editTarget=(value:string)=>onUpdate(path+'.target',value);
 const editCount=(value:number)=>onUpdate(path+'.count',Math.max(1,Math.floor(value)||1));
 const pickerKind=o.event==='custom_stat'?'statistic':o.event==='advancement_complete'?'advancement':kind;
 const browse=['block','item','entity','biome','structure','statistic','advancement'].includes(pickerKind);
 const modifier=(next:any)=>onCondition('objective:'+i,next);
 const field=()=>{
  if(kind==='position')return <div className="coordinate-fields objective-coordinates">
    {(['x','y','z'] as const).map(axis=><label className="field" key={axis}>
      <span>{axis.toUpperCase()}</span>
      <input type="number" step="any" aria-label={axis.toUpperCase()} value={positionCoordinate(o.conditions,axis)}
        onChange={e=>modifier(setPositionCoordinate(o.conditions,axis,e.target.value))}/>
    </label>)}
    <label className="field"><span>{tUI('Radius')}</span><input aria-label={tUI('Radius')} type="number" min={0} max={128} step="any" value={positionRadius(o.conditions)}
      onChange={e=>modifier(setPositionRadius(o.conditions,e.target.value))}/></label>
   </div>;
  if(kind==='distance')return <Field label="Distance (m)" type="number" value={Number((o.count/100).toFixed(2))} onChange={v=>onUpdate(path+'.count',Math.max(1,Math.min(1000000000,Math.round(v*100)||1)))}/>;
  if(o.event==='equipment_check')return <GearEditor conditions={o.conditions} onChange={modifier}/>;
  if(kind==='death')return <span className="fixed-target">{tUI('Player death')}</span>;
  if(kind==='manual')return <div className="target-field">
    <Field label="Manual event ID" value={o.target} onChange={editTarget} placeholder={questId}/>
    <button type="button" className="button secondary" onClick={()=>editTarget(questId)}>{tUI('Quest ID')}</button>
   </div>;
  if(kind==='dimension')return <div className="dimension-target">
    <select aria-label={tUI('Dimension')} value={DIMENSIONS.some(([id])=>id===o.target)?o.target:'custom'}
      onChange={e=>editTarget(e.target.value==='custom'?'modid:dimension':e.target.value)}>
      {DIMENSIONS.map(([id,name])=><option key={id} value={id}>{tUI(name)}</option>)}
      <option value="custom">{tUI('Custom dimension')}</option>
    </select>
    {!DIMENSIONS.some(([id])=>id===o.target)&&<Field label="Target ID" value={o.target} onChange={editTarget} placeholder="modid:dimension"/>}
   </div>;
  return <div className="target-field">
     <Field label={kind==='custom'?'Custom target ID':'Target ID'} value={o.target} onChange={editTarget} placeholder={kind==='custom'?'modid:target':'minecraft:stone'}/>
     {browse&&<button type="button" className="button secondary" onClick={()=>onBrowse(i,pickerKind as PickerKind)}>{tUI('Browse')}</button>}
   </div>;
 };
 return <div className={'field-row objective-inputs objective-inputs-'+kind}>
  <Select label="Action" value={o.event}
    options={[...EVENTS.map(([id])=>[id,EVENT_LABELS[id]||id] as [string,string]),...hubEditorDescriptors('event').filter(d=>!EVENTS.some(([id])=>id===d.id)).map(d=>[d.id,d.label] as [string,string]),...(!EVENTS.some(([id])=>id===o.event)&&!hubEditorDescriptors('event').some(d=>d.id===o.event)?[[o.event,o.event] as [string,string]]:[])]}
    onChange={event=>{
      const next=changeObjectiveEvent(o,event,questId);
      if(event==='equipment_check' && !next.conditions?.['questplus:gear'])
        next.conditions=updateGear(next.conditions,armorPreset('iron'));
      onUpdate(path,next);
    }}/>
  {field()}
  {label&&kind!=='distance'&&<Field label={label} type="number" value={o.count} onChange={editCount}/>}
 </div>;
}

export function QuestDetails({project,selection,language,onUpdate,onAction,onCondition,onAddLocale,translationRequest=0}:Props){
 const [tab,setTab]=useState<Tab>('objectives');
  const [targetBrowser,setTargetBrowser]=useState<{index:number;kind:PickerKind}|null>(null);
  const [rewardAdvBrowser,setRewardAdvBrowser]=useState<number|null>(null);
 useEffect(()=>{
  if(!translationRequest)return;
  setTab('localization');
  requestAnimationFrame(()=>{
   const button=document.getElementById('detail-tab-localization');
   button?.focus();button?.scrollIntoView({block:'nearest'});
  });
 },[translationRequest]);
 const quest=selection?.type==='quest'?project.quests.find(q=>q.id===selection.id):undefined;
 const category=selection?.type==='category'?project.categories.find(c=>c.id===selection.id):undefined;
 if(category)return <section className="quest-details" aria-label={tUI('Localization')}><div className="detail-tabs" role="tablist"><button type="button" role="tab" id="detail-tab-localization" aria-selected="true" className="active"><Globe size={16}/>{tUI('Localization')}</button></div><div className="detail-content" role="tabpanel"><LocalizationPanel key={category.id} project={project} category={category} onUpdate={onUpdate} onAddLocale={onAddLocale}/></div></section>;
 if(!quest)return <section className="quest-details"><div className="detail-heading"><strong>{tUI('Quest details')}</strong></div><div className="detail-content"><p>{tUI('Select a quest to edit its objectives and rewards.')}</p></div></section>;
 const info=quest.kind==='information';
 const changeProgression=(next:string)=>{
  if(quest.progression==='tiered'&&next!=='tiered'&&!window.confirm(tUI('Changing progression removes the tiers and their rewards. Continue?')))return;
  onAction('progression:'+next);
 };
 const changeTier=(index:number,tier:Tier)=>onUpdate('tiers',(quest.tiers as Tier[]).map((entry,i)=>i===index?tier:entry));
 const addTier=()=>{
  const tiers=(quest.tiers||[]) as Tier[];
  let n=tiers.length+1,id='tier'+n;
  while(tiers.some(t=>t.id===id))id='tier'+ ++n;
  onUpdate('tiers',[...tiers,{id,threshold:(tiers.at(-1)?.threshold||0)+1,rewards:[]}]);
 };
 const addNpcObjective=()=>{
  const objectives=quest.objectives||[];let n=objectives.length+1,id='step'+n;
  while(objectives.some(o=>o.id===id))id='step'+ ++n;
  onUpdate('objectives',[...objectives,{id,event:'npc_interact',target:'minecraft:villager',count:1}]);
  setTab('objectives');
 };
 const ui=(value:string)=>tUI(value);
 return <section className="quest-details" aria-label={ui('Quest details')}>
  <div className="detail-tabs" role="tablist" aria-label={ui('Quest details')}>
   {tabs.map(([id,label],index)=>{const Icon=tabIcons[id];return <button type="button" role="tab" id={'detail-tab-'+id} aria-controls="detail-panel" aria-selected={tab===id} tabIndex={tab===id?0:-1} key={id} className={tab===id?'active':''} onClick={()=>setTab(id)} onKeyDown={e=>{
    if(!['ArrowLeft','ArrowRight','Home','End'].includes(e.key))return;
    e.preventDefault();const next=e.key==='Home'?0:e.key==='End'?tabs.length-1:(index+(e.key==='ArrowRight'?1:-1)+tabs.length)%tabs.length;
    setTab(tabs[next][0]);document.getElementById('detail-tab-'+tabs[next][0])?.focus();
   }}><Icon size={16}/>{ui(label)}{id==='objectives'?' ('+(quest.objectives?.length||0)+')':id==='rewards'?' ('+(quest.rewards?.length||0)+')':''}</button>})}
  </div>
  <button type="button" className="detail-json" onClick={()=>onAction('advanced-json')}>{ui('Advanced JSON')}</button>
  <div className="detail-content" role="tabpanel" id="detail-panel" aria-labelledby={'detail-tab-'+tab}>
   {tab==='objectives'&&<>
    <div className="detail-heading"><strong>{ui('Quest objectives')} · {text(quest.title,language)}</strong>
     <button type="button" className="button secondary" onClick={()=>onAction('save-as-template')}>Save as template</button>
     {!info&&<button className="button primary" onClick={()=>onAction('add-objective')}><Plus size={14}/>{ui('Objective')}</button>}</div>
    {info?<div className="objective-row"><Translated label="Content" prefix="narrative" value={quest.narrative} language={language} onUpdate={onUpdate}/>
      <Select label="Completion" value={quest.acknowledgement||'manual'} options={[[ 'manual','Acknowledge'],['read_only','Read only']]} onChange={v=>onUpdate('acknowledgement',v)}/></div>:
     <div className="objectives-list">{(quest.objectives||[]).map((o,i)=><div className="mini-card objective-row" key={o.id}>
      <div className="mini-card-head"><ItemIcon id={o.target?.replace(/^#/,'')} size={25}/><strong>{i+1}. {objectiveLabel(o,language)}</strong><small title={o.id}>{o.id}</small><button className="tiny danger" aria-label={ui('Remove objective')} title={ui('Remove objective')} onClick={()=>onAction('remove-objective',i)}><Trash2 size={14}/></button></div>
      <ObjectiveInputs objective={o} index={i} questId={quest.id} onUpdate={onUpdate} onCondition={onCondition}
       onBrowse={(index,kind)=>setTargetBrowser({index,kind})}/>
      <label className="checkbox-row"><input type="checkbox" checked={!!o.optional} onChange={e=>onUpdate('objectives.'+i+'.optional',e.target.checked)}/>{ui('Optional objective')}</label>
      <Disclosure title="Custom label"><Translated label="Text" prefix={'objectives.'+i+'.text'} value={o.text} language={language} onUpdate={onUpdate}/></Disclosure>
      <Disclosure title="Conditions"><ConditionEditor project={project} scope={'objective:'+i} value={o.conditions} onChange={v=>onCondition('objective:'+i,v)}/></Disclosure>
     </div>)}</div>}
   </>}
   {targetBrowser&&<TargetPicker kind={targetBrowser.kind} onClose={()=>setTargetBrowser(null)} onPick={id=>{
     onUpdate('objectives.'+targetBrowser.index+'.target',id);
     setTargetBrowser(null);
   }}/>}
   {tab==='rewards'&&<>
    <div className="detail-heading"><strong>{ui('Rewards')}</strong><div className="toolbar-row"><button className="button secondary" onClick={()=>onAction('add-xp')}>+ XP</button><button className="button secondary" onClick={()=>onAction('add-xp-levels')}>+ XP Levels</button><button className="button primary" onClick={()=>onAction('add-item')}>+ {ui('Item')}</button><button className="button secondary" type="button" onClick={()=>onUpdate('rewards',[...(quest.rewards||[]),{type:'questplus:advancement',data:{advancement:'minecraft:story/mine_stone'}}])}>+ Advancement</button><button className="button secondary" onClick={()=>onAction('add-random')}>+ Weighted Random</button><button className="button secondary" onClick={()=>onAction('add-loot')}>+ Loot Chance</button><button className="button secondary" onClick={()=>onAction('add-all')}>+ All Table</button><button className="button secondary" onClick={()=>onAction('add-command')}>+ Command</button><button className="button secondary" onClick={()=>onAction('add-message')}>+ Message</button></div></div>
    {!!hubEditorDescriptors('reward').length&&<div className="toolbar-row" aria-label="Mod rewards">
      {hubEditorDescriptors('reward').map(d=><button className="button secondary" type="button" key={d.id} onClick={()=>{
        const data=Object.fromEntries(d.fields.map(f=>[f.key,f.type==='boolean'?false:f.type==='integer'?1:'']));
        onUpdate('rewards',[...(quest.rewards||[]),{type:d.id,data}]);
      }}>+ {d.label}</button>)}
    </div>}
    {!quest.rewards?.length&&<p>{ui('No rewards')}</p>}
    {(quest.rewards||[]).map((r,i)=><div className="mini-card" key={i}>
     <div className="mini-card-head"><strong>{r.type==='experience'?ui('Experience'):r.type==='experience_levels'?'XP Levels':r.type==='item'?ui('Item'):r.type}</strong><button className="tiny danger" aria-label={ui('Remove reward')} onClick={()=>onAction('remove-reward',i)}><Trash2 size={14}/></button></div>
     {(r.type==='experience'||r.type==='experience_levels')?<Field label={r.type==='experience'?'XP':'XP Levels'} type="number" value={r.amount} onChange={v=>onUpdate('rewards.'+i+'.amount',v)}/>:
      r.type==='item'?<div className="field-row"><Field label="Item ID" value={r.item} onChange={v=>onUpdate('rewards.'+i+'.item',v)}/><Field label="Count" type="number" value={r.count} onChange={v=>onUpdate('rewards.'+i+'.count',v)}/></div>:
      r.type==='questplus:advancement'?<div className="field-row">
       <Field label="Advancement ID" value={r.data?.advancement||''} onChange={v=>onUpdate('rewards.'+i+'.data.advancement',v)} placeholder="minecraft:story/mine_stone"/>
       <button className="button secondary" type="button" onClick={()=>setRewardAdvBrowser(i)}>{ui('Browse')}</button>
      </div>:
      r.type==='questplus:message'?<Field label="Message to player" value={r.data?.message||''}
        onChange={v=>onUpdate('rewards.'+i+'.data.message',v)}/>:
      r.type==='questplus:command'?<div className="mini-card">
        <p>Only trusted server pack authors may enable command rewards. The command runs on the server as the claiming player (GAMEMASTER permission maximum).</p>
        <Field label="Server command" value={r.data?.command||''} onChange={v=>onUpdate('rewards.'+i+'.data.command',v)} placeholder="say Quest completed"/>
      </div>:
      ['questplus:random','questplus:loot','questplus:all'].includes(r.type)?<div className="random-reward-builder">
        <p>{ui('Rolls are selected when the quest completes, and stored until claimed.')}</p>
        {r.type!=='questplus:all'&&<Field label="Rolls" type="number" value={r.data?.rolls||1}
          onChange={v=>onUpdate('rewards.'+i+'.data.rolls',Math.max(1,Math.min(16,Math.floor(v))))}/>}
        {r.type==='questplus:loot'&&<Field label="Empty chance weight" type="number" value={r.data?.emptyWeight??0}
          onChange={v=>onUpdate('rewards.'+i+'.data.emptyWeight',Math.max(0,Math.min(1000000,Math.floor(v))))}/>}
        {(r.data?.options||[]).map((option:any,j:number)=><div className="mini-card" key={j}>
          <div className="field-row">
            <Select label="Type" value={option.type} options={[[ 'item','Item'],['experience','XP points'],['experience_levels','XP Levels']]}
              onChange={type=>onUpdate('rewards.'+i+'.data.options.'+j,{weight:option.weight||1,type,...(type==='item'?{item:'minecraft:diamond',count:1}:{amount:5})})}/>
            <Field label="Weight" type="number" value={option.weight} onChange={v=>onUpdate('rewards.'+i+'.data.options.'+j+'.weight',Math.max(1,Math.min(1000000,Math.floor(v))))}/>
            {option.type==='item'?<>
              <Field label="Item ID" value={option.item} onChange={v=>onUpdate('rewards.'+i+'.data.options.'+j+'.item',v)}/>
              <Field label="Count" type="number" value={option.count} onChange={v=>onUpdate('rewards.'+i+'.data.options.'+j+'.count',Math.max(1,Math.floor(v)))}/>
            </>:<Field label="XP amount" type="number" value={option.amount} onChange={v=>onUpdate('rewards.'+i+'.data.options.'+j+'.amount',Math.max(1,Math.floor(v)))}/>}
            <button className="tiny danger" title="Remove option" aria-label="Remove option" disabled={r.data?.options?.length<=1}
              onClick={()=>onUpdate('rewards.'+i+'.data.options',(r.data?.options||[]).filter((_:any,k:number)=>k!==j))}><Trash2 size={14}/></button>
          </div>
        </div>)}
        <button className="button secondary" disabled={(r.data?.options?.length||0)>=32}
          onClick={()=>onUpdate('rewards.'+i+'.data.options',[...(r.data?.options||[]),{weight:1,type:'item',item:'minecraft:diamond',count:1}])}>
          + {ui('Add random option')}
        </button>
      </div>:
      (hubEditorDescriptors('reward').some(d=>d.id===r.type)?<div className="field-row">
        {hubEditorDescriptors('reward').find(d=>d.id===r.type)!.fields.map(field=><div key={field.key}>
          {field.type==='boolean'?<label className="checkbox-row"><input type="checkbox" checked={!!r.data?.[field.key]} onChange={e=>onUpdate('rewards.'+i+'.data.'+field.key,e.target.checked)}/>{field.label}</label>:
          <Field label={field.label} type={field.type==='integer'?'number':'text'} value={r.data?.[field.key]??''}
            onChange={v=>onUpdate('rewards.'+i+'.data.'+field.key,field.type==='integer'?Math.max(0,Math.floor(Number(v)||0)):v)}/>}
        </div>)}
      </div>:<button className="button secondary" onClick={()=>onAction('advanced-json')}>{ui('Edit custom rewards in Advanced JSON.')}</button>)}
     <Disclosure title="Custom label"><Translated label="Text" prefix={'rewards.'+i+'.text'} value={r.text} language={language} onUpdate={onUpdate}/></Disclosure>
    </div>)}
   </>}
   {rewardAdvBrowser!==null&&<TargetPicker kind="advancement" onClose={()=>setRewardAdvBrowser(null)}
     onPick={id=>{onUpdate('rewards.'+rewardAdvBrowser+'.data.advancement',id);setRewardAdvBrowser(null)}}/>}
   {tab==='conditions'&&<>
    <div className="detail-heading"><strong>{ui('Prerequisites')}</strong></div>
    <p>{ui('A quest becomes available when its prerequisites are complete.')}</p>
    <ConditionEditor value={quest.requires} scope="requires" project={project} currentQuestId={quest.id} onChange={v=>onCondition('requires',v)}/>
   </>}
   {tab==='progression'&&<>
    <div className="detail-heading"><strong>{ui('Progression')}</strong></div>
    {info?<p>{ui('Information quests are completed once.')}</p>:<>
     <div className="field-row">
      <Select label="Mode" value={quest.progression} options={[[ 'once','Once'],['repeatable','Repeatable'],['infinite','Infinite'],['tiered','Tiered']]} onChange={changeProgression}/>
      <Select label="Objectives" value={quest.objectiveMode||'all'} options={[[ 'all','All'],['any','Any'],['sequence','Sequence'],['at_least','At least N']]} onChange={v=>{onUpdate('objectiveMode',v);if(v==='at_least'&&!quest.objectiveThreshold)onUpdate('objectiveThreshold',1);else if(v!=='at_least')onUpdate('objectiveThreshold',undefined)}}/>
      {quest.objectiveMode==='at_least'&&<Field label="Required objectives" type="number" value={quest.objectiveThreshold||1} onChange={v=>onUpdate('objectiveThreshold',Math.max(1,Math.floor(v)))}/>}
      <Field label="Cooldown (s)" type="number" value={quest.cooldownSeconds??0} onChange={v=>{onUpdate('cooldownSeconds',Math.max(0,v))}}/>
      <Field label="Time limit (s)" type="number" value={quest.timeLimitSeconds??''} placeholder="—" onChange={v=>{onUpdate('timeLimitSeconds',v>0?v:undefined)}}/>
     </div>
     <label className="checkbox-row"><input type="checkbox" checked={!!quest.noDeath} onChange={e=>{onUpdate('noDeath',e.target.checked)}}/> {ui('Without dying')}</label>
     <label className="checkbox-row"><input type="checkbox" checked={!!quest.progressBeforeUnlock} onChange={e=>onUpdate('progressBeforeUnlock',e.target.checked)}/>{ui('Track objectives before prerequisites are unlocked')}</label>
     {quest.progression==='tiered'&&<>
      <Select label="After last tier" value={quest.afterLast||'stop'} options={[[ 'stop','Stop'],['repeat','Repeat last tier']]} onChange={v=>onUpdate('afterLast',v)}/>
      {(quest.tiers||[]).map((tier:Tier,i:number)=><div className="mini-card" key={tier.id}>
       <div className="mini-card-head"><strong>{ui('Tier')} {i+1} · {tier.id}</strong><button className="tiny danger" aria-label={ui('Remove tier')} onClick={()=>{if(window.confirm(ui('Remove this tier and its rewards?')))onUpdate('tiers',(quest.tiers as Tier[]).filter((_,n)=>n!==i))}}><Trash2 size={14}/></button></div>
       <Field label="Threshold" type="number" value={tier.threshold} onChange={v=>changeTier(i,{...tier,threshold:Math.max(1,v)})}/>
       {tier.rewards.map((r,j)=><div className="field-row" key={j}>
        {(r.type==='experience'||r.type==='experience_levels')?<Field label={r.type==='experience'?'XP':'XP Levels'} type="number" value={r.amount} onChange={v=>changeTier(i,{...tier,rewards:tier.rewards.map((x,k)=>k===j?{...x,amount:v}:x)})}/>:
         r.type==='item'?<><Field label="Item ID" value={r.item} onChange={v=>changeTier(i,{...tier,rewards:tier.rewards.map((x,k)=>k===j?{...x,item:v}:x)})}/><Field label="Count" type="number" value={r.count} onChange={v=>changeTier(i,{...tier,rewards:tier.rewards.map((x,k)=>k===j?{...x,count:v}:x)})}/></>:
         <button className="button secondary" onClick={()=>onAction('advanced-json')}>{r.type} · {ui('Advanced JSON')}</button>}
        <button className="tiny danger" aria-label={ui('Remove reward')} onClick={()=>changeTier(i,{...tier,rewards:tier.rewards.filter((_,k)=>k!==j)})}><Trash2 size={14}/></button>
        <Disclosure title="Custom label"><Translated label="Text" prefix={'tiers.'+i+'.rewards.'+j+'.text'} value={r.text} language={language} onUpdate={onUpdate}/></Disclosure>
       </div>)}
       <div className="toolbar-row"><button className="button secondary" onClick={()=>changeTier(i,{...tier,rewards:[...tier.rewards,{type:'experience',amount:5}]})}>+ XP</button><button className="button secondary" onClick={()=>changeTier(i,{...tier,rewards:[...tier.rewards,{type:'item',item:'minecraft:diamond',count:1}]})}>+ {ui('Item')}</button></div>
      </div>)}
      <button className="button secondary" onClick={addTier}>+ {ui('Tier')}</button>
     </>}
    </>}
   </>}
   {tab==='npc'&&<>
    <div className="detail-heading"><strong>{ui('NPC interaction')}</strong></div>
    <p>{ui('An NPC interaction is an objective. Its target is an entity type, not a unique NPC.')}</p>
    <p>{ui('To unlock another quest after an interaction, create an NPC interaction quest and select it as a prerequisite. Dialogues and direct NPC unlocks are not available in the current engine.')}</p>
    {!info&&<button className="button primary" onClick={addNpcObjective}>+ {ui('NPC interaction objective')}</button>}
   </>}
   {tab==='localization'&&<LocalizationPanel key={quest.id} project={project} quest={quest} onUpdate={onUpdate} onAddLocale={onAddLocale}/>}
  </div>
 </section>;
}
