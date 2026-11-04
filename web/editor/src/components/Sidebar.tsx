import React,{useState} from 'react';
import {text} from '../../project.js';
import {tUI} from '../../ui-i18n.js';
import {ItemIcon} from './IconPicker';
import type {Project,Category,Quest,Selection} from '../types';
export function Sidebar({project,categoryId,quests,selection,language,onCategory,onQuest,onNewCategory,onReset}:{project:Project;categoryId:string|null;quests:Quest[];selection:Selection|null;language:string;onCategory:(id:string)=>void;onQuest:(id:string)=>void;onNewCategory:()=>void;onReset:(original:boolean)=>void}){
 const [search,setSearch]=useState('');
 return <aside className="sidebar">
  <div className="sidebar-header"><div className="overline">QUEST PACK</div><h2>{tUI('Library')}</h2></div>
  <div className="sidebar-actions"><button className="button primary block" onClick={onNewCategory}>+ {tUI('Category')}</button></div>
  <div className="sidebar-section-head"><span>{tUI('Categories').toUpperCase()}</span><span className="count">{project.categories.length}</span></div>
  <div className="category-list">
   {project.categories.map(c=><button className={'category-item'+(c.id===categoryId?' active':'')} key={c.id} onClick={()=>onCategory(c.id)}>
    <span className="cat-icon"><ItemIcon id={c.icon}/></span><span><strong className="cat-name">{text(c.title,language)}</strong><small className="cat-sub">{c.id}</small></span>
   </button>)}
  </div>
  <div className="sidebar-section-head quests-head"><span>{tUI('Quests').toUpperCase()}</span><span className="count">{quests.length}</span></div>
  <div className="search-box"><span aria-hidden>⌕</span><input type="search" value={search} onChange={e=>setSearch(e.target.value)} placeholder={tUI('Search quests…')}/></div>
  <div className="quest-list">{quests.filter(q=>(text(q.title,language)+' '+q.id).toLowerCase().includes(search.toLowerCase())).map(q=>
   <button className={'quest-item'+(selection?.type==='quest'&&selection.id===q.id?' active':'')} onClick={()=>onQuest(q.id)} key={q.id}>
    <span className="quest-glyph"><ItemIcon id={q.icon}/></span><span className="quest-label"><strong>{text(q.title,language)}</strong><small>{q.id.split('/').pop()}</small></span>
   </button>)}</div>
  <div className="sidebar-footer"><span className="save-dot"/><div><strong>Local draft</strong><small>Autosaved</small></div><button className="subtle" onClick={()=>onReset(true)}>Defaults</button><button className="subtle" onClick={()=>onReset(false)}>New</button></div>
 </aside>;
}
