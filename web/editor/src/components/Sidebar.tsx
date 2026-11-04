import React,{useState} from 'react';
import {Search,ChevronDown,ChevronRight,FolderOpen,FolderPlus,PanelLeftClose,Upload,FilePlus2,RotateCcw} from 'lucide-react';
import {text} from '../core/project.js';
import {tUI} from '../ui/ui-i18n.js';
import {ItemIcon} from './IconPicker';
import type {Project,Quest,Selection} from '../types';
type Props={project:Project;categoryId:string|null;quests:Quest[];selection:Selection|null;language:string;onCategory:(id:string)=>void;onQuest:(id:string)=>void;onNewCategory:()=>void;onReset:(original:boolean)=>void;search:string;onSearch:(s:string)=>void;onCollapse:()=>void;onImport:()=>void;onImportFolder:()=>void};
export function Sidebar({project,categoryId,selection,language,onCategory,onQuest,onNewCategory,onReset,search,onSearch,onCollapse,onImport,onImportFolder}:Props){
 const [collapsed,setCollapsed]=useState<Set<string>>(new Set());
 const query=search.trim().toLocaleLowerCase();
 return <aside className="sidebar wood" aria-label={tUI('Library')}>
  <div className="sidebar-header grass-trim"><FolderOpen size={22}/><h2>{tUI('Quest Library')}</h2><button className="icon-button" aria-label={tUI('Collapse library')} onClick={onCollapse}><PanelLeftClose size={18}/></button></div>
  <div className="search-box"><Search size={16}/><input type="search" value={search} aria-label={tUI('Search categories / quests…')} onChange={e=>onSearch(e.target.value)} placeholder={tUI('Search categories / quests…')}/></div>
  <nav className="library-tree">{project.categories.map(c=>{
   const all=project.quests.filter(q=>q.category===c.id),categoryMatches=(text(c.title,language)+' '+c.id).toLocaleLowerCase().includes(query);
   const rows=query&&!categoryMatches?all.filter(q=>(text(q.title,language)+' '+q.id).toLocaleLowerCase().includes(query)):all;
   if(query&&!categoryMatches&&!rows.length)return null;
   const open=query||!collapsed.has(c.id);
   return <div className={'library-group'+(categoryId===c.id?' current':'')} key={c.id}>
    <div className="category-row"><button className="category-expander" aria-label={tUI(open?'Collapse category':'Expand category')+' '+text(c.title,language)} aria-expanded={!!open} onClick={()=>setCollapsed(old=>{const next=new Set(old);if(next.has(c.id))next.delete(c.id);else next.add(c.id);return next})}>{open?<ChevronDown size={15}/>:<ChevronRight size={15}/>}</button><button className={'category-item'+(selection?.type==='category'&&selection.id===c.id?' active':'')} onClick={()=>onCategory(c.id)}><ItemIcon id={c.icon} size={26}/><strong className="cat-name">{text(c.title,language)}</strong><span className="category-count">({all.length})</span></button></div>
    {open&&<div className="quest-list">{rows.map(q=><button className={'quest-item'+(selection?.type==='quest'&&selection.id===q.id?' active':'')} aria-label={text(q.title,language)} onClick={()=>onQuest(q.id)} key={q.id}><ItemIcon id={q.icon} size={25}/><span className="quest-label"><strong>{text(q.title,language)}</strong></span></button>)}{!all.length&&<p className="library-empty">{tUI('No quests yet')}</p>}</div>}
   </div>;
  })}{query&&!project.categories.some(c=>(text(c.title,language)+' '+c.id).toLocaleLowerCase().includes(query))&&!project.quests.some(q=>(text(q.title,language)+' '+q.id).toLocaleLowerCase().includes(query))&&<p className="library-empty">{tUI('No results')}</p>}</nav>
  <div className="library-import"><button className="button secondary" onClick={onImport}><Upload size={15}/>{tUI('Import')}</button><button className="button secondary" onClick={onImportFolder}><FolderOpen size={15}/>{tUI('Folder')}</button></div>
  <details className="project-options"><summary>{tUI('Project options')}</summary><button className="subtle" onClick={()=>onReset(true)}><RotateCcw size={13}/>{tUI('Load defaults')}</button><button className="subtle" onClick={()=>onReset(false)}><FilePlus2 size={13}/>{tUI('New project')}</button></details>
  <div className="sidebar-footer"><button className="button secondary block" onClick={onNewCategory}><FolderPlus size={18}/>{tUI('New Category')}</button></div>
 </aside>;
}
