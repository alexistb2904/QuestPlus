import React,{useEffect,useMemo,useRef,useState} from 'react';
import {text,validate} from '../project.js';
import {storedUiLocale,setUiLocale,tUI} from '../ui-i18n.js';
import {localesOf,supportedLocales} from './types';
import {useStudio,owner} from './hooks/useStudio';
import {Sidebar} from './components/Sidebar';
import {TreeCanvas} from './components/TreeCanvas';
import {Inspector} from './components/Inspector';
import {IconPicker} from './components/IconPicker';
import logo from '../questplus-logo.png';

export default function App(){
 const studio=useStudio(),{state,setState}=studio;
 const {project,categoryId,selection,camera,language}=state;
 const [uiLocale,setInterfaceLocale]=useState(()=>{const l=storedUiLocale();setUiLocale(l);return l});
 const [picker,setPicker]=useState<string|null>(null),[jsonOpen,setJsonOpen]=useState(false),[jsonContent,setJsonContent]=useState(''),[jsonError,setJsonError]=useState(''),[issuesOpen,setIssuesOpen]=useState(false);
 const files=useRef<HTMLInputElement>(null),folder=useRef<HTMLInputElement>(null);
 const locales=useMemo(()=>localesOf(project),[project]),issues=useMemo(()=>validate(project),[project]);
 const cat=studio.currentCategory,quests=studio.quests;
 useEffect(()=>{
  const callback=(event:KeyboardEvent)=>{
   if(!(event.ctrlKey||event.metaKey)||event.key.toLowerCase()!=='z'||(event.target as HTMLElement)?.closest('input,textarea,[contenteditable]'))return;
   event.preventDefault();if(event.shiftKey)studio.redo();else studio.undo();
  };window.addEventListener('keydown',callback);return()=>window.removeEventListener('keydown',callback);
 });
 const changeCamera=(next:any)=>setState(s=>({...s,camera:next}));
 const editJson=()=>{setJsonContent(JSON.stringify(owner(project,selection),null,2));setJsonError('');setJsonOpen(true)};
 const doAction=(type:string,index?:number)=>{if(type==='advanced-json')return editJson();studio.action(type,index)};
 const onPick=(id:string)=>{studio.setField(picker||'icon',id);setPicker(null)};
 const goQuest=(id:string)=>{
  const q=project.quests.find(q=>q.id===id);
  setState(s=>({...s,selection:{type:'quest',id},camera:q?.layout?{...s.camera,x:q.layout.x,y:q.layout.y}:s.camera}));
 };
 const addLanguage=()=>studio.addLocale();
 return <div className="app">
  <header className="topbar">
   <div className="brand"><div className="brand-mark"><img src={logo} alt="QuestPlus" width="42" height="42"/></div><div><div className="brand-name">QUEST <span>STUDIO</span></div><div className="brand-caption">MINECRAFT QUEST EDITOR <span className="version">26.3</span></div></div></div>
   <div className="workspace-name"><span className="active-dot"/><span>{project.categories.length} categories · {project.quests.length} quests</span></div>
   <div className="top-actions">
    <label className="language-control">Interface <select id="ui-language" value={uiLocale} onChange={e=>{
     setUiLocale(e.target.value,{save:true});setInterfaceLocale(e.target.value);
    }}><option value="en">EN</option><option value="fr">FR</option><option value="es">ES</option><option value="zh">中文</option></select></label>
    <label className="language-control">Quest text <select id="preview-language" value={language} onChange={e=>setState(s=>({...s,language:e.target.value}))}>
     {locales.map(id=><option key={id} value={id}>{supportedLocales[id]||id} · {id}</option>)}
    </select></label>
    <button className="icon-button" aria-label="Add translation" title="Add translation" onClick={addLanguage}>🌐</button>
    <button className="icon-button" aria-label="Undo" onClick={studio.undo} title="Undo">↶</button>
    <button className="icon-button" aria-label="Redo" onClick={studio.redo} title="Redo">↷</button>
    <label className="version-selector">Minecraft <input aria-label="Target Minecraft version" value={project.targetMinecraftVersion||'26.3'}
     onChange={e=>studio.mutate(s=>{s.project.targetMinecraftVersion=e.target.value},'version')}/></label>
    <button className="button secondary" onClick={()=>files.current?.click()}>{tUI('Import')}</button>
    <button className="button secondary" onClick={()=>folder.current?.click()}>Folder</button>
    <button className="button secondary" onClick={()=>setIssuesOpen(v=>!v)}>{tUI('Validate')}</button>
    <button className="button primary" onClick={()=>{if(!studio.exportZip())setIssuesOpen(true)}}>{tUI('Export ZIP')}</button>
   </div>
  </header>
  <input ref={files} hidden type="file" multiple accept=".json,.zip" onChange={e=>{void studio.importFiles(e.target.files);e.target.value=''}}/>
  <input ref={el=>{folder.current=el;if(el)el.setAttribute('webkitdirectory','')}} hidden type="file" multiple onChange={e=>{void studio.importFiles(e.target.files);e.target.value=''}}/>
  <div className="workspace">
   <Sidebar project={project} categoryId={categoryId} quests={quests} selection={selection} language={language} onCategory={studio.selectCategory}
    onQuest={goQuest} onNewCategory={studio.createCategory} onReset={studio.reset}/>
   <main className="main">
    <div className="canvas-heading"><div><div className="breadcrumbs"><span>QUESTPLUS</span><span className="slash">/</span><span>{cat?.id||'Pack'}</span></div>
     <h1>{text(cat?.title,language)||tUI('Quest tree')}</h1><p>{quests.length} quests · {cat?.annotations?.length||0} annotations</p></div>
     <div className="canvas-actions"><button className="button secondary" disabled={!cat} onClick={studio.createAnnotation}>+ Annotation</button>
      <button className="button primary" disabled={!cat} onClick={studio.createQuest}>+ Quest</button></div>
    </div>
    <div className="canvas-shell">
     <div className="canvas-toolbar"><div className="seg">
      <button className="tool-button" onClick={()=>categoryId&&setState(s=>({...s,selection:{type:'category',id:categoryId}}))}>Category</button>
      <button className="tool-button" onClick={studio.autoLayout}>Auto-layout</button>
     </div><div className="canvas-zoom">
      <button title="Zoom out" onClick={()=>changeCamera({...camera,zoom:Math.max(.25,camera.zoom/1.2)})}>−</button><span>{Math.round(camera.zoom*100)}%</span>
      <button title="Zoom in" onClick={()=>changeCamera({...camera,zoom:Math.min(2.5,camera.zoom*1.2)})}>+</button>
      <button title="Fit" onClick={()=>changeCamera(studio.fit())}>⛶</button>
     </div></div>
     <TreeCanvas quests={quests} category={cat} language={language} selection={selection} camera={camera}
      onCamera={changeCamera} onSelect={s=>setState(old=>({...old,selection:s}))} onPosition={studio.setPosition}/>
     <div className="canvas-bottom"><span className="legend">◈ Quest &nbsp; ◇ Annotation &nbsp; ━ Prerequisite</span>
      <span>x: {Math.round(camera.x)} · y: {Math.round(camera.y)}</span><span>Drag · Scroll · Pinch</span></div>
    </div>
    <div className={'issues-bar'+(issues.length?' error':'')}><span className="issues-icon">{issues.length?'!':'✓'}</span>
     <span>{issues.length?issues.length+' errors':tUI('Ready to export')}</span><button className="subtle" onClick={()=>setIssuesOpen(v=>!v)}>Details →</button></div>
    {issuesOpen&&<div className="issues-list">{issues.length?issues.map((x:any,i:number)=><div key={i}><strong>{x.where}</strong> — {x.message}</div>):<div>No errors</div>}</div>}
   </main>
   <Inspector project={project} selection={selection} language={language} onUpdate={studio.setField} onAction={doAction}
    onChooseIcon={setPicker} onCondition={studio.changeCondition}/>
  </div>
  {picker&&<IconPicker onPick={onPick} onClose={()=>setPicker(null)}/>}
  {jsonOpen&&<div className="react-overlay" role="presentation"><section className="react-dialog" role="dialog" aria-modal="true" aria-label="Advanced JSON">
   <div className="react-dialog-heading"><h2>Advanced JSON</h2><button className="icon-button" onClick={()=>setJsonOpen(false)}>×</button></div>
   <textarea className="json-textarea" spellCheck={false} value={jsonContent} onChange={e=>setJsonContent(e.target.value)}/>
   {jsonError&&<p className="error-line">{jsonError}</p>}
   <div className="dialog-actions"><button className="button secondary" onClick={()=>setJsonOpen(false)}>Cancel</button>
    <button className="button primary" onClick={()=>{const error=studio.applyJson(jsonContent);if(error)setJsonError(error);else setJsonOpen(false)}}>Apply</button></div>
  </section></div>}
  {studio.notice&&<div className="toast show" role="status">{studio.notice}</div>}
 </div>;
}
