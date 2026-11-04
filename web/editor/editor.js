import {gameLayout,gameFit,NODE_SIZE} from './game-layout.js';
import {iconUrl,blockIconUrl,vanillaCatalog,loadVanillaIcons} from './minecraft-icons.js';
import {demoProject,emptyProject,category as newCategory,quest as newQuest,annotation as newAnnotation,
 copy,text,slug,validate,fileMap,importDocuments,references} from './project.js';
import {makeZip,readZip} from './zip.js';
import {inspectorData} from './inspector.js';

const $=s=>document.querySelector(s);
const escape=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const STORAGE='questplus.quest-studio.v1',LIMIT=65;
let project=demoProject(),categoryId=project.categories[0].id,selection={type:'category',id:categoryId};
let camera={x:0,y:30,zoom:1},language='en_us',previewMode=false,history=[],future=[],savedTimer,drag,toastTimer,focusRecorded=false;
try {
  const data=JSON.parse(localStorage.getItem(STORAGE)||'null');
  if(data?.project?.categories && data?.project?.quests){
    project=data.project;
    categoryId=project.categories.some(c=>c.id===data.categoryId)?data.categoryId:project.categories[0]?.id||null;
    selection=data.selection||{type:'category',id:categoryId};
    camera=data.camera||camera;language=data.language||'en_us';
  }
} catch(e){console.warn('Quest Studio: local save could not be restored',e);}
const currentCategory=()=>project.categories.find(c=>c.id===categoryId);
const getSelected=()=>{
  if(!selection)return null;
  if(selection.type==='category')return currentCategory();
  if(selection.type==='quest')return project.quests.find(q=>q.id===selection.id);
  return currentCategory()?.annotations?.find(n=>n.id===selection.id);
};
function notify(message){const el=$('#toast');el.textContent=message;el.classList.add('show');clearTimeout(toastTimer);toastTimer=setTimeout(()=>el.classList.remove('show'),3100);}
function snapshot(){return {project:copy(project),categoryId,selection:copy(selection),camera:copy(camera)};}
function persist(){
  clearTimeout(savedTimer);
  savedTimer=setTimeout(()=>{
    try{localStorage.setItem(STORAGE,JSON.stringify({...snapshot(),language}));$('#autosave-status').textContent='Enregistré dans ce navigateur';}
    catch(e){$('#autosave-status').textContent='Stockage local indisponible';notify('Sauvegarde locale impossible : exportez un ZIP.');}
  },500);
}
function remember(before){history.push(before);if(history.length>LIMIT)history.shift();future=[];}
function mutate(callback){
  const before=snapshot(),serialized=JSON.stringify(project);
  const result=callback();
  if(result===false)return false;
  if(JSON.stringify(project)!==serialized){remember(before);persist();}
  refresh();
  return true;
}
function restore(s){project=copy(s.project);categoryId=s.categoryId;selection=copy(s.selection);camera=copy(s.camera);refresh();persist();}
function undo(){if(!history.length)return;future.push(snapshot());restore(history.pop());}
function redo(){if(!future.length)return;history.push(snapshot());restore(future.pop());}
function ensureCategory(){if(!currentCategory()&&project.categories.length){categoryId=project.categories[0].id;selection={type:'category',id:categoryId};}}
const categoryQuests=()=>project.quests.filter(q=>q.category===categoryId);
const iconFor=id=>{
  const src=iconUrl(id),backup=blockIconUrl(id);
  if(!src)return '<span class="mc-icon" title="'+escape(id||'')+'">◇</span>';
  // Retry with the matching block face when a vanilla item has no item texture.
  return '<span class="mc-icon" title="'+escape(id)+'"><img loading="lazy" alt="" src="'+escape(src)+
    '" onerror="this.onerror=null;'+(backup?"this.src='"+escape(backup)+"';":"this.style.display='none';")+'"></span>';
};
const LOCALE_LABELS={
  en_us:'English',en_gb:'English (UK)',fr_fr:'Français',fr_ca:'Français (CA)',de_de:'Deutsch',
  es_es:'Español',es_mx:'Español (MX)',pt_br:'Português (BR)',pt_pt:'Português',
  it_it:'Italiano',ja_jp:'日本語',ko_kr:'한국어',zh_cn:'简体中文',zh_tw:'繁體中文',
  ru_ru:'Русский',nl_nl:'Nederlands',pl_pl:'Polski',tr_tr:'Türkçe',uk_ua:'Українська',
  cs_cz:'Čeština',da_dk:'Dansk',fi_fi:'Suomi',sv_se:'Svenska',nb_no:'Norsk',
  hu_hu:'Magyar',ro_ro:'Română',el_gr:'Ελληνικά',id_id:'Bahasa Indonesia',
  vi_vn:'Tiếng Việt',th_th:'ไทย',ar_sa:'العربية',hi_in:'हिन्दी',bg_bg:'Български',
  sk_sk:'Slovenčina',sl_si:'Slovenščina',hr_hr:'Hrvatski',sr_sp:'Srpski',he_il:'עברית',
  lt_lt:'Lietuvių',lv_lv:'Latviešu',et_ee:'Eesti',ca_es:'Català',eu_es:'Euskara',
  gl_es:'Galego',af_za:'Afrikaans'
};
const userLocales=()=>{
  const codes=new Set(['en_us',...(project.editorLocales||[]),language]);
  const collect=o=>{
    if(!o||typeof o!=='object')return;
    for(const [key,v] of Object.entries(o)){
      if(/^[a-z]{2,3}_[a-z]{2,4}$/.test(key)&&typeof v==='string')codes.add(key);
      else if(v&&typeof v==='object')collect(v);
    }
  };
  for(const c of project.categories)collect(c);
  for(const q of project.quests)collect(q);
  return [...codes].sort((a,b)=>a==='en_us'?-1:b==='en_us'?1:a.localeCompare(b));
};
function renderLanguages(){
  const select=$('#preview-language');
  select.innerHTML=userLocales().map(code=>'<option value="'+escape(code)+'">'+
    escape((LOCALE_LABELS[code]||code)+' · '+code)+'</option>').join('');
  select.value=language;
}
const categoryLabel=()=>text(currentCategory()?.title,language)||'Aucune catégorie';
function refresh(){
  ensureCategory();
  renderLanguages();
  document.title='Quest Studio — '+categoryLabel();
  $('#project-name').textContent='QuestPlus · '+project.categories.length+' catégories';
  $('#crumb-category').textContent=categoryLabel();
  $('#canvas-title').textContent=categoryLabel();
  $('#canvas-subtitle').textContent=(categoryQuests().length)+' quêtes · '+(currentCategory()?.annotations?.length||0)+' annotations';
  $('#category-count').textContent=project.categories.length;
  $('#quest-count').textContent=categoryQuests().length;
  $('#undo').disabled=!history.length;$('#redo').disabled=!future.length;
  $('#new-quest').disabled=!currentCategory();$('#new-note').disabled=!currentCategory();
  $('#categories').innerHTML=project.categories.map(c=>`<button class="category-item ${c.id===categoryId?'active':''}" data-cat="${escape(c.id)}"><span class="cat-icon">${iconFor(c.icon)}</span><span><strong class="cat-name">${escape(text(c.title,language))}</strong><small class="cat-sub">${escape(c.id)}</small></span></button>`).join('')||'<div class="tip">Aucune catégorie</div>';
  const needle=$('#quest-search').value.trim().toLowerCase();
  $('#quest-list').innerHTML=categoryQuests().filter(q=>!needle||text(q.title,language).toLowerCase().includes(needle)||q.id.includes(needle)).map(q=>
    `<button class="quest-item ${selection?.type==='quest'&&selection.id===q.id?'active':''}" data-quest="${escape(q.id)}">
      <span class="quest-glyph">${iconFor(q.icon)}</span><span class="quest-label"><strong>${escape(text(q.title,language))}</strong><small>${escape(q.id.split('/').pop())}</small></span></button>`).join('')||
    '<div class="tip">Aucune quête.</div>';
  renderInspector();renderScene();renderIssues();
}
function renderInspector(){
  const i=inspectorData(project,selection,categoryId,language);
  $('#inspector-title').textContent=i.title;$('#inspector-subtitle').textContent=i.subtitle;$('#inspector-body').innerHTML=i.html;
}
function renderIssues(){
  const issues=validate(project),bar=$('#issues-bar');
  bar.classList.toggle('error',issues.length>0);
  bar.querySelector('.issues-icon').textContent=issues.length?'!':'✓';
  $('#issues-summary').textContent=issues.length?issues.length+' erreur'+(issues.length>1?'s':''):'Prêt à exporter';
  $('#issues-list').innerHTML=issues.map(i=>`<div><strong>${escape(i.where)}</strong> — ${escape(i.message)}</div>`).join('')||'<div>Aucune erreur.</div>';
}
function worldToPixel(x,y){const box=$('#viewport');return {x:(x-camera.x)*camera.zoom+box.clientWidth/2,y:(y-camera.y)*camera.zoom+box.clientHeight/2};}
function pixelToWorld(x,y){const box=$('#viewport');return {x:(x-box.clientWidth/2)/camera.zoom+camera.x,y:(y-box.clientHeight/2)/camera.zoom+camera.y};}
function renderScene(){
  const el=$('#viewport'),width=el.clientWidth,height=el.clientHeight;if(!width||!height)return;
  const rows=categoryQuests(),layout=gameLayout(rows),positions=layout.points;
  $('#zoom-value').textContent=Math.round(camera.zoom*100)+'%';
  el.style.backgroundSize=`${32*camera.zoom}px ${32*camera.zoom}px`;
  el.style.backgroundPosition=`${-camera.x*camera.zoom+width/2}px ${-camera.y*camera.zoom+height/2}px`;
  $('#empty-tree').classList.toggle('hidden',previewMode||rows.length>0||(currentCategory()?.annotations||[]).length>0);
  $('#minecraft-preview').classList.toggle('hidden',!previewMode);
  $('#preview-toggle').setAttribute('aria-pressed',String(previewMode));
  $('#preview-toggle').textContent=previewMode?'✎ Édition':'▣ Aperçu jeu';
  $('#canvas-hint').textContent=previewMode?'Aperçu du rendu Minecraft':'Molette : zoom · Glisser : déplacer';
  if(previewMode){$('#nodes').innerHTML='';$('#edges').innerHTML='';renderGamePreview(layout);return;}
  const shapes=[];
  for(const q of rows){
    const pos=positions.get(q.id),pt=worldToPixel(pos.x,pos.y);
    if(pt.x< -100||pt.x>width+100||pt.y< -80||pt.y>height+80)continue;
    const selected=selection?.type==='quest'&&selection.id===q.id;
    shapes.push(`<div class="graph-node ${selected?'selected':''}" data-node-type="quest" data-node-id="${escape(q.id)}"
      style="left:${pt.x}px;top:${pt.y}px;transform:translate(-50%,-50%) scale(${camera.zoom})">
      <div class="node-icon">${iconFor(q.icon)}</div><div class="node-title" title="${escape(q.id)}">${escape(text(q.title,language))}</div></div>`);
  }
  for(const a of currentCategory()?.annotations||[]){
    const pt=worldToPixel(a.x,a.y);
    if(pt.x< -240||pt.x>width+240||pt.y< -60||pt.y>height+60)continue;
    const selected=selection?.type==='annotation'&&selection.id===a.id;
    shapes.push(`<div class="annotation-node ${escape(a.style||'note')} ${selected?'selected':''}"
      style="left:${pt.x}px;top:${pt.y}px;border-color:${escape(a.color||'#c6a986')};color:${escape(a.color||'#c6a986')};transform:translate(-50%,-50%) scale(${camera.zoom})"
      data-node-type="annotation" data-node-id="${escape(a.id)}">${escape(text(a.text,language))}</div>`);
  }
  $('#nodes').innerHTML=shapes.join('');
  const lines=[];
  for(const {from,to} of layout.edges){
    const a=positions.get(from),b=positions.get(to);
    const p=worldToPixel(a.x,a.y+16),q=worldToPixel(b.x,b.y-16);
    const middle=(p.y+q.y)/2;
    lines.push(`<path class="${selection?.type==='quest'&&[from,to].includes(selection.id)?'selected-edge':''}"
      d="M ${p.x} ${p.y} V ${middle} H ${q.x} V ${q.y}"/>`);
  }
  $('#edges').setAttribute('viewBox',`0 0 ${width} ${height}`);
  $('#edges').innerHTML=lines.join('');
}
function renderGamePreview(layout){
  const target=$('#game-tree'),width=target.clientWidth,height=target.clientHeight;
  if(!width||!height){requestAnimationFrame(()=>{if(previewMode)renderGamePreview(layout);});return;}
  const previewCam=gameFit(layout.points.values(),width,height,24);
  const point=(x,y)=>({x:(x-previewCam.x)*previewCam.zoom+width/2,y:(y-previewCam.y)*previewCam.zoom+height/2});
  $('#game-categories').innerHTML=project.categories.map(c=>
    `<button class="game-category ${c.id===categoryId?'selected':''}" data-preview-category="${escape(c.id)}" title="${escape(text(c.title,language))}">${iconFor(c.icon)}</button>`).join('');
  const rows=categoryQuests();
  $('#game-nodes').innerHTML=rows.map(q=>{
    const v=layout.points.get(q.id);if(!v)return '';
    const p=point(v.x,v.y),selected=selection?.id===q.id;
    return `<button class="game-quest ${selected?'selected':''}" data-preview-quest="${escape(q.id)}"
      title="${escape(text(q.title,language))}" style="left:${p.x}px;top:${p.y}px;transform:translate(-50%,-50%) scale(${previewCam.zoom})">${iconFor(q.icon)}</button>`;
  }).join('')+(currentCategory()?.annotations||[]).map(a=>{
    const p=point(a.x,a.y);
    return `<div class="game-note ${escape(a.style||'note')}" style="left:${p.x}px;top:${p.y}px;transform:translate(-50%,-50%) scale(${previewCam.zoom});color:${escape(a.color||'#c6a986')}">${escape(text(a.text,language))}</div>`;
  }).join('');
  const edges=layout.edges.map(({from,to})=>{
    const a=layout.points.get(from),b=layout.points.get(to);
    const p=point(a.x,a.y+16),q=point(b.x,b.y-16);
    return `<path d="M ${p.x} ${p.y} V ${(p.y+q.y)/2} H ${q.x} V ${q.y}"/>`;
  });
  $('#game-edges').setAttribute('viewBox',`0 0 ${width} ${height}`);
  $('#game-edges').innerHTML=edges.join('');
}
function selectCategory(id){categoryId=id;selection={type:'category',id};fit();refresh();}
function selectObject(type,id){selection={type,id};refresh();}
function fit(){
  const graph=gameLayout(categoryQuests());
  const points=[...graph.points.values(),...(currentCategory()?.annotations||[]).map(a=>({x:a.x,y:a.y}))];
  const el=$('#viewport');
  camera=gameFit(points,Math.max(1,el.clientWidth),Math.max(1,el.clientHeight),48);
}
function zoomAt(factor,x,y){
  const el=$('#viewport'),before=pixelToWorld(x,y);
  camera.zoom=Math.max(.25,Math.min(3,camera.zoom*factor));
  camera.x=before.x-(x-el.clientWidth/2)/camera.zoom;
  camera.y=before.y-(y-el.clientHeight/2)/camera.zoom;
  renderScene();
}
function placeAuto(){
  const list=categoryQuests();
  // Reset old explicit positions before applying the same algorithm as QuestLayout.java.
  const positions=gameLayout(list.map(q=>({...q,layout:null}))).points;
  for(const q of list)q.layout={...positions.get(q.id)};
  fit();
}
function unique(base,ids){let id=base,index=2;while(ids.has(id))id=base+'_'+index++;return id;}
function createCategory(){
  const name=prompt('Category name (English):','New category');
  if(name===null)return;const title=name.trim()||'New category';
  mutate(()=>{
    const id=unique('questplus:'+slug(title),new Set(project.categories.map(c=>c.id)));
    project.categories.push(newCategory(id,title));categoryId=id;selection={type:'category',id};
    camera={x:0,y:0,zoom:1};
  });
  notify('Catégorie créée');
}
function createQuest(){
  const cat=currentCategory();if(!cat)return notify('Crée d’abord une catégorie.');
  const name=prompt('Quest name (English):','New quest');
  if(name===null)return;
  mutate(()=>{
    const title=name.trim()||'New quest',base=cat.id.split(':')[1].replace(/\/$/,'');
    const id=unique(cat.id.split(':')[0]+':'+base+'/'+slug(title),new Set(project.quests.map(q=>q.id)));
    const pos={x:Math.round(camera.x/10)*10,y:Math.round(camera.y/10)*10};
    project.quests.push(newQuest(cat,id,title,pos));selection={type:'quest',id};
  });
  notify('Quête créée. Déplace-la sur l’arbre.');
}
function createAnnotation(){
  const cat=currentCategory();if(!cat)return;
  mutate(()=>{
    cat.annotations ||= [];
    const id=unique('note',new Set(cat.annotations.map(a=>a.id)));
    cat.annotations.push(newAnnotation(id,Math.round(camera.x/10)*10,Math.round(camera.y/10)*10));
    selection={type:'annotation',id};
  });
  notify('Annotation créée');
}
function rewriteRefs(node,oldId,newId){
  if(!node||typeof node!=='object')return;
  if(node.quest===oldId)node.quest=newId;
  if(Array.isArray(node.and))node.and.forEach(n=>rewriteRefs(n,oldId,newId));
  if(Array.isArray(node.or))node.or.forEach(n=>rewriteRefs(n,oldId,newId));
  if(node.not)rewriteRefs(node.not,oldId,newId);
}
function setField(field,value){
  const o=getSelected();if(!o)return;
  const chain=field.split('.');let parent=o;
  for(let i=0;i<chain.length-1;i++){const key=chain[i];parent[key]??={};parent=parent[key];}
  const key=chain.at(-1),prev=parent[key];
  if(field==='id'){
    if(selection.type==='category'){
      for(const q of project.quests)if(q.category===o.id)q.category=value;
      categoryId=value;selection.id=value;
    } else if(selection.type==='quest'){
      for(const q of project.quests)rewriteRefs(q.requires,o.id,value);
      selection.id=value;
    } else selection.id=value;
  }
  if(['x','y','count','amount','cooldownSeconds','timeLimitSeconds'].includes(key))value=Number(value);
  if(field==='kind'){
    if(value==='information'){
      o.schemaVersion=2;o.objectives=[];o.progression='once';o.acknowledgement='manual';
      delete o.objectiveMode;delete o.tiers;delete o.cooldownSeconds;delete o.timeLimitSeconds;delete o.noDeath;
    } else if(prev==='information'){o.objectives=[{id:'step',event:'block_break',target:'minecraft:stone',count:1}];delete o.acknowledgement;}
  }
  if(field==='progression'){
    o.schemaVersion=2;
    if(value==='tiered')o.tiers ||= [{id:'first',threshold:1,rewards:[]}];
    else {delete o.tiers;delete o.afterLast;}
  }
  if(['objectiveMode','cooldownSeconds','timeLimitSeconds','noDeath'].includes(field)&&value)o.schemaVersion=2;
  parent[key]=value;
  if(field.startsWith('description.')&&o.description&&!Object.values(o.description).some(s=>s.trim()))delete o.description;
  if(field.startsWith('objectives.')&&field.includes('.text.')){
    for(const step of o.objectives||[])if(step.text&&!Object.values(step.text).some(s=>s.trim()))delete step.text;
  }
  if(field.startsWith('narrative.')&&o.narrative&&!Object.values(o.narrative).some(s=>s.trim()))delete o.narrative;
  if(['cooldownSeconds','timeLimitSeconds'].includes(field)&&!value)delete o[field];
  if(field==='noDeath'&&!value)delete o.noDeath;
}
function deleteSelected(){
  const selected=getSelected();if(!selected)return;
  if(selection.type==='category'){
    if(categoryQuests().length)return notify('Déplace ou supprime les quêtes avant cette catégorie.');
    if(!confirm('Supprimer la catégorie '+selected.id+' ?'))return;
    mutate(()=>{project.categories=project.categories.filter(c=>c!==selected);categoryId=project.categories[0]?.id||null;selection=categoryId?{type:'category',id:categoryId}:null;});
  }else if(selection.type==='quest'){
    const used=project.quests.filter(q=>q.id!==selected.id&&references(q.requires).includes(selected.id));
    if(used.length)return notify('Cette quête est un prérequis de '+used.length+' autre(s) quête(s).');
    if(!confirm('Supprimer définitivement cette quête ?'))return;
    mutate(()=>{project.quests=project.quests.filter(q=>q!==selected);selection={type:'category',id:categoryId};});
  }else{
    mutate(()=>{currentCategory().annotations=currentCategory().annotations.filter(a=>a!==selected);selection={type:'category',id:categoryId};});
  }
}
function inspectorAction(action,index){
  const o=getSelected();
  switch(action){
    case 'create-quest':return createQuest();
    case 'create-annotation':return createAnnotation();
    case 'delete-category':case 'delete-quest':case 'delete-annotation':return deleteSelected();
    case 'duplicate-quest':return mutate(()=>{
      const clone=copy(o);clone.id=unique(o.id+'_copy',new Set(project.quests.map(q=>q.id)));
      clone.layout={x:(o.layout?.x||0)+70,y:(o.layout?.y||0)+70};project.quests.push(clone);selection={type:'quest',id:clone.id};
    });
    case 'duplicate-annotation':return mutate(()=>{
      const clone=copy(o),cat=currentCategory();clone.id=unique(o.id+'_copy',new Set(cat.annotations.map(a=>a.id)));
      clone.x+=35;clone.y+=35;cat.annotations.push(clone);selection={type:'annotation',id:clone.id};
    });
    case 'add-objective':return mutate(()=>{
      const id=unique('step',new Set((o.objectives||[]).map(x=>x.id)));
      o.objectives||=[];o.objectives.push({id,event:'block_break',target:'minecraft:stone',count:1});
    });
    case 'remove-objective':return mutate(()=>{
      if(o.objectives.length===1)return notify('Une quête standard doit avoir au moins un objectif.')&&false;
      o.objectives.splice(index,1);
    });
    case 'add-xp':return mutate(()=>{o.rewards||=[];o.rewards.push({type:'experience',amount:5});});
    case 'add-item':return mutate(()=>{o.rewards||=[];o.rewards.push({type:'item',item:'minecraft:diamond',count:1});});
    case 'remove-reward':return mutate(()=>{o.rewards.splice(index,1);});
  }
}
const inspector=$('#inspector-body');
let iconTarget='icon';
function renderIconPicker(){
  const query=$('#icon-search').value.trim().toLowerCase();
  const items=vanillaCatalog().filter(v=>(v.id+' '+v.label).toLowerCase().includes(query)).slice(0,240);
  $('#icon-grid').innerHTML=items.map(item=>`<button class="icon-choice" data-pick-icon="${escape(item.id)}" title="${escape(item.id)}">
    ${iconFor(item.id)}<span>${escape(item.label)}</span></button>`).join('')||'<div class="tip">Aucun résultat</div>';
}
inspector.addEventListener('click',e=>{
  const button=e.target.closest('[data-icon-open]');if(!button)return;
  iconTarget=button.dataset.iconOpen;$('#icon-search').value='';
  showModal('#icon-picker');renderIconPicker();$('#icon-search').focus();
  loadVanillaIcons().then(()=>{if(!$('#icon-picker').classList.contains('hidden'))renderIconPicker();});
});
$('#icon-search').addEventListener('input',renderIconPicker);
$('#icon-close').onclick=()=>hideModal('#icon-picker');
$('#icon-grid').addEventListener('click',e=>{
  const button=e.target.closest('[data-pick-icon]');if(!button)return;
  const value=button.dataset.pickIcon;
  mutate(()=>setField(iconTarget,value));
  hideModal('#icon-picker');
});

inspector.addEventListener('focusin',e=>{
  if(e.target.matches('[data-field]')&&!focusRecorded){history.push(snapshot());if(history.length>LIMIT)history.shift();future=[];focusRecorded=true;}
});
inspector.addEventListener('focusout',e=>{if(e.target.matches('[data-field]')){focusRecorded=false;refresh();persist();}});
inspector.addEventListener('input',e=>{
  const el=e.target;if(!el.matches('[data-field]'))return;
  setField(el.dataset.field,el.type==='checkbox'?el.checked:el.value);
  renderScene();renderIssues();persist();
});
inspector.addEventListener('change',e=>{
  const el=e.target;
  if(el.matches('[data-dependency]')){
    mutate(()=>{
      const q=getSelected(),all=new Set(references(q.requires));
      if(el.checked)all.add(el.dataset.dependency);else all.delete(el.dataset.dependency);
      const refs=[...all].sort();q.requires=refs.length===0?undefined:refs.length===1?{quest:refs[0]}:{and:refs.map(quest=>({quest}))};
      if(!refs.length)delete q.requires;
    });
  }else if(el.matches('[data-field]')){if(!focusRecorded){remember(snapshot());setField(el.dataset.field,el.type==='checkbox'?el.checked:el.value);}refresh();persist();}
});
inspector.addEventListener('click',e=>{const btn=e.target.closest('[data-action]');if(btn)inspectorAction(btn.dataset.action,Number(btn.dataset.index));});
$('#categories').addEventListener('click',e=>{const node=e.target.closest('[data-cat]');if(node)selectCategory(node.dataset.cat);});
$('#quest-list').addEventListener('click',e=>{const node=e.target.closest('[data-quest]');if(node){selectObject('quest',node.dataset.quest);const q=getSelected();if(q?.layout){camera.x=q.layout.x;camera.y=q.layout.y;renderScene();}}});
$('#quest-search').addEventListener('input',refresh);
$('#preview-language').addEventListener('change',e=>{language=e.target.value;refresh();persist();});
$('#add-language').onclick=()=>{
  const value=prompt('Minecraft locale code (e.g. de_de, es_es, ja_jp):','de_de');
  if(value===null)return;
  const code=value.trim().toLowerCase();
  if(!/^[a-z]{2,3}_[a-z]{2,4}$/.test(code)){notify('Code de langue invalide (ex : de_de).');return;}
  const before=snapshot();
  project.editorLocales=[...new Set([...(project.editorLocales||[]),code])];
  remember(before);language=code;refresh();persist();notify('Langue : '+code);
};
$('#new-category').onclick=createCategory;$('#new-quest').onclick=createQuest;
$('#preview-toggle').onclick=()=>{previewMode=!previewMode;renderScene();};
$('#game-categories').addEventListener('click',e=>{const b=e.target.closest('[data-preview-category]');if(b)selectCategory(b.dataset.previewCategory);});
$('#game-nodes').addEventListener('click',e=>{const b=e.target.closest('[data-preview-quest]');if(b)selectObject('quest',b.dataset.previewQuest);});
$('#load-default').onclick=()=>{
  if(!confirm('Restaurer les quêtes livrées avec QuestPlus ? Le brouillon actuel sera remplacé.'))return;
  mutate(()=>{project=demoProject();categoryId=project.categories[0]?.id||null;selection={type:'category',id:categoryId};camera={x:0,y:0,zoom:1};fit();});
  notify('Quêtes du mod restaurées');
};
$('#empty-add').onclick=createQuest;$('#new-note').onclick=createAnnotation;
$('#select-category').onclick=()=>selectObject('category',categoryId);
$('#auto-layout').onclick=()=>{if(!confirm('Repositionner automatiquement toutes les quêtes de cette catégorie ?'))return;mutate(placeAuto);notify('Disposition recalculée');};
$('#zoom-fit').onclick=()=>{fit();renderScene();};$('#zoom-in').onclick=()=>{const el=$('#viewport');zoomAt(1.18,el.clientWidth/2,el.clientHeight/2);};
$('#zoom-out').onclick=()=>{const el=$('#viewport');zoomAt(1/1.18,el.clientWidth/2,el.clientHeight/2);};
$('#undo').onclick=undo;$('#redo').onclick=redo;
$('#reset-project').onclick=()=>{if(!confirm('Créer un projet vierge ? Le projet actuel reste exportable seulement s’il a déjà été téléchargé.'))return;mutate(()=>{project=emptyProject();categoryId=null;selection=null;camera={x:0,y:0,zoom:1};});};
$('#run-validate').onclick=()=>{renderIssues();$('#issues-list').classList.remove('hidden');notify(validate(project).length?'Corrige les alertes de validation':'Structure du projet vérifiée ✓');};
$('#show-issues').onclick=()=>$('#issues-list').classList.toggle('hidden');
$('#viewport').addEventListener('pointerdown',e=>{
  if(previewMode||e.button!==0||e.target.closest('button'))return;
  const el=$('#viewport'),rect=el.getBoundingClientRect(),node=e.target.closest('[data-node-type]');
  const type=node?.dataset.nodeType,id=node?.dataset.nodeId;
  if(node){
    selection={type,id};renderInspector();renderScene();
    const obj=getSelected(),computed=type==='quest'?gameLayout(categoryQuests()).points.get(id):null;
    drag={mode:'node',type,id,startX:e.clientX,startY:e.clientY,from:{x:type==='quest'?computed.x:obj.x,y:type==='quest'?computed.y:obj.y},before:snapshot(),moved:false};
  }else{
    drag={mode:'pan',startX:e.clientX,startY:e.clientY,from:{x:camera.x,y:camera.y},moved:false};
  }
  el.setPointerCapture(e.pointerId);
});
$('#viewport').addEventListener('pointermove',e=>{
  const rect=$('#viewport').getBoundingClientRect(),pos=pixelToWorld(e.clientX-rect.left,e.clientY-rect.top);
  $('#coordinates').textContent='x: '+Math.round(pos.x)+' · y: '+Math.round(pos.y);
  if(!drag)return;
  const dx=e.clientX-drag.startX,dy=e.clientY-drag.startY;if(Math.abs(dx)+Math.abs(dy)>3)drag.moved=true;
  if(drag.mode==='pan'){camera.x=drag.from.x-dx/camera.zoom;camera.y=drag.from.y-dy/camera.zoom;}
  else{
    const obj=drag.type==='quest'?project.quests.find(q=>q.id===drag.id):currentCategory()?.annotations.find(a=>a.id===drag.id);
    if(!obj)return;
    const x=Math.round((drag.from.x+dx/camera.zoom)/8)*8,y=Math.round((drag.from.y+dy/camera.zoom)/8)*8;
    if(drag.type==='quest')obj.layout={x,y};else {obj.x=x;obj.y=y;}
  }
  renderScene();
});
function finishPointer(){if(!drag)return;if(drag.mode==='node'&&drag.moved){remember(drag.before);persist();renderInspector();}drag=null;}
$('#viewport').addEventListener('pointerup',finishPointer);$('#viewport').addEventListener('pointercancel',finishPointer);
$('#viewport').addEventListener('wheel',e=>{if(previewMode)return;e.preventDefault();const rect=$('#viewport').getBoundingClientRect();zoomAt(Math.exp(-e.deltaY*.0014),e.clientX-rect.left,e.clientY-rect.top);},{passive:false});
function showModal(m){$(m).classList.remove('hidden');}
function hideModal(m){$(m).classList.add('hidden');}
$('#open-json').onclick=()=>{
  const obj=getSelected();if(!obj)return notify('Choisis un élément à éditer.');
  $('#json-input').value=JSON.stringify(obj,null,2);$('#json-error').textContent='';
  $('#modal-kicker').textContent=selection.type.toUpperCase();$('#modal-title').textContent='Modifier '+(obj.id||'la sélection');
  showModal('#modal');$('#json-input').focus();
};
$('#modal-close').onclick=$('#modal-cancel').onclick=()=>hideModal('#modal');
$('#modal-apply').onclick=()=>{
  let candidate;
  try{candidate=JSON.parse($('#json-input').value);if(!candidate||typeof candidate!=='object'||Array.isArray(candidate)||!candidate.id)throw Error('Un objet avec un ID est attendu.');}
  catch(e){$('#json-error').textContent=e.message;return;}
  const kind=selection.type,original=getSelected();
  if(!original)return;
  const next=copy(project);
  if(kind==='quest'){const i=next.quests.findIndex(q=>q.id===original.id);next.quests[i]=candidate;}
  else if(kind==='category'){const i=next.categories.findIndex(c=>c.id===original.id);next.categories[i]=candidate;next.quests.filter(q=>q.category===original.id).forEach(q=>q.category=candidate.id);}
  else{const cat=next.categories.find(c=>c.id===categoryId);const i=cat.annotations.findIndex(a=>a.id===original.id);cat.annotations[i]=candidate;}
  const issues=validate(next);
  if(issues.length){$('#json-error').textContent=issues.slice(0,3).map(e=>e.where+': '+e.message).join(' | ');return;}
  mutate(()=>{project=next;selection.id=candidate.id;if(kind==='category')categoryId=candidate.id;});
  hideModal('#modal');notify('JSON appliqué');
};
$('#open-import').onclick=()=>showModal('#import-modal');
$('#import-close').onclick=()=>hideModal('#import-modal');
$('#choose-zip').onclick=()=>$('#file-zip').click();
$('#choose-folder').onclick=()=>$('#file-folder').click();
$('#choose-files').onclick=()=>$('#file-json').click();
async function importSelection(el,zip=false){
  const files=Array.from(el.files||[]);if(!files.length)return;
  try{
    const docs=zip?await readZip(files[0]):await Promise.all(files.filter(f=>f.name.endsWith('.json')).map(async f=>({name:f.webkitRelativePath||f.name,content:await f.text()})));
    const imported=importDocuments(docs);
    if(!confirm('Remplacer le projet en cours par '+imported.quests.length+' quêtes et '+imported.categories.length+' catégories ?'))return;
    mutate(()=>{project=imported;categoryId=project.categories[0]?.id||null;selection=categoryId?{type:'category',id:categoryId}:null;camera={x:0,y:0,zoom:1};fit();});
    hideModal('#import-modal');notify('Projet importé : '+project.quests.length+' quêtes');
  }catch(e){console.error(e);notify('Import impossible : '+e.message);}
  finally{el.value='';}
}
$('#file-zip').onchange=e=>importSelection(e.target,true);
$('#file-folder').onchange=e=>importSelection(e.target);
$('#file-json').onchange=e=>importSelection(e.target);
async function exportZip(){
  const issues=validate(project);
  if(issues.length){renderIssues();$('#issues-list').classList.remove('hidden');return notify('Export bloqué : '+issues.length+' erreur(s) à corriger.');}
  try{
    const blob=makeZip(fileMap(project)),url=URL.createObjectURL(blob),a=document.createElement('a');
    a.href=url;a.download='questplus-pack.zip';document.body.append(a);a.click();a.remove();
    setTimeout(()=>URL.revokeObjectURL(url),2000);notify('Pack QuestPlus téléchargé');
  }catch(e){notify('Export impossible : '+e.message);}
}
$('#export-zip').onclick=exportZip;
document.addEventListener('keydown',e=>{
  if(e.key==='Escape'){hideModal('#modal');hideModal('#import-modal');hideModal('#icon-picker');return;}
  const typed=/^(INPUT|TEXTAREA|SELECT)$/.test(document.activeElement?.tagName||'')||!$('#modal').classList.contains('hidden');
  if(typed)return;
  if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='z'){e.preventDefault();e.shiftKey?redo():undo();}
  else if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='s'){e.preventDefault();exportZip();}
  else if(e.key==='Delete'&&selection?.type!=='category'){e.preventDefault();deleteSelected();}
});
window.addEventListener('resize',renderScene);
refresh();requestAnimationFrame(()=>{fit();renderScene();});
