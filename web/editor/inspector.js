import {tUI} from './ui-i18n.js';
import {EVENTS,MODES,PROGRESSIONS,objectiveLabel,text,references} from './project.js';
import {iconUrl,knownVersion,versionWarning} from './minecraft-icons.js';
import {renderConditionTree} from './conditions.js';
const esc=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const attr=esc;
const field=(label,name,value='',placeholder='',type='text')=>`<div class="field"><label>${esc(label)}</label><input data-field="${attr(name)}" type="${type}" value="${attr(value)}" placeholder="${attr(placeholder)}"></div>`;
const area=(label,name,value='',placeholder='')=>`<div class="field"><label>${esc(label)}</label><textarea data-field="${attr(name)}" placeholder="${attr(placeholder)}">${esc(value)}</textarea></div>`;
const select=(label,name,selected,options)=>`<div class="field"><label>${esc(label)}</label><select data-field="${attr(name)}">${options.map(([key,title])=>`<option value="${attr(key)}" ${key===selected?'selected':''}>${esc(title)}</option>`).join('')}</select></div>`;
const checkbox=(label,name,checked)=>`<label class="checkbox-row"><input type="checkbox" data-field="${attr(name)}" ${checked?'checked':''}>${esc(label)}</label>`;
const section=(label,body)=>`<section class="section"><div class="section-title">${esc(label)}</div>${body}</section>`;
const disclosure=(label,body,opened=false)=>`<details class="editor-details" data-panel="${attr(label)}" ${opened?'open':''}><summary>${esc(label)}</summary><div class="details-body">${body}</div></details>`;
const action=(id,label)=>`<button class="button secondary" data-action="${attr(id)}">${esc(label)}</button>`;
const mini=(action,index)=>`<button class="tiny danger" data-action="${attr(action)}" data-index="${index}" title="Supprimer">×</button>`;
const loc=(value,name,label,language,optional=false)=>`<div class="translation-stack">
  ${field(tUI(label)+' · en_us',name+'.en_us',value?.en_us||'',optional?tUI('Optional'):tUI('English source'))}
  ${language!=='en_us'?field(tUI(label)+' · '+language,name+'.'+language,value?.[language]||'',tUI('Optional translation')):''}
</div>`;
function iconField(label,fieldName,id,project){
  const version=project.iconVersions?.[id]||knownVersion(id)||'26.3',image=iconUrl(id,version);
  const warning=versionWarning(project,id);
  return `<div class="field"><label>${esc(label)}</label>
    <div class="icon-field"><span class="item-preview">${image?`<img src="${attr(image)}" alt="" loading="lazy">`:'◇'}</span>
      <input data-field="${attr(fieldName)}" value="${attr(id||'')}" placeholder="minecraft:book / mod:item" spellcheck="false">
      <button type="button" data-icon-open="${attr(fieldName)}" class="button secondary" title="Choisir dans les objets Minecraft">Parcourir</button>
    </div>
    <div class="icon-version-row"><label>Version min.</label>
      <input type="text" data-icon-version="${attr(id)}" value="${attr(version)}" placeholder="26.3" aria-label="Version Minecraft minimum pour cet objet">
      ${warning?`<span class="version-alert" role="status">${esc(warning)}</span>`:''}
    </div>
  </div>`;
}
export function inspectorData(project,selection,categoryId,language='en_us'){
  const category=project.categories.find(c=>c.id===categoryId);
  if(!selection||!category)return {title:'Éditeur',subtitle:'',html:'<div class="empty-inspector">Sélectionne un élément.</div>'};
  if(selection.type==='category'){
    return {title:'Catégorie',subtitle:category.id,html:
      section('Identité',loc(category.title,'title','Nom',language)+iconField('Icône','icon',category.icon,project)+disclosure('Description',loc(category.description,'description','Description',language,true)))+
      section('Actions',`<div class="toolbar-row">${action('create-quest','+ Quête')}${action('create-annotation','+ Annotation')}</div>`)+
      disclosure('Options',field('ID','id',category.id)+action('delete-category','Supprimer'))};
  }
  if(selection.type==='annotation'){
    const note=(category.annotations||[]).find(a=>a.id===selection.id);
    if(!note)return {title:'Annotation',subtitle:'',html:''};
    return {title:'Annotation',subtitle:note.id,html:
      section('Texte',loc(note.text,'text','Annotation',language)+select('Style','style',note.style||'note',[['note','Encadré'],['heading','Titre'],['label','Étiquette']]))+
      section('Aspect',field('Couleur','color',note.color||'#c6a986','','color')+
        `<div class="field-row">${field('X','x',note.x,'0','number')}${field('Y','y',note.y,'0','number')}</div>`)+
      disclosure('Options',field('ID','id',note.id)+action('duplicate-annotation','Dupliquer')+' '+action('delete-annotation','Supprimer'))};
  }
  const q=project.quests.find(v=>v.id===selection.id);
  if(!q)return {title:'Quête',subtitle:'',html:''};
  const info=q.kind==='information';
  const objectiveCards=(q.objectives||[]).map((o,i)=>`
    <div class="mini-card">
      <div class="mini-card-head"><span class="mini-card-title">${i+1}. ${esc(objectiveLabel(o,language))}</span>${mini('remove-objective',i)}</div>
      ${select('Action',`objectives.${i}.event`,o.event,[...EVENTS,...(!EVENTS.some(e=>e[0]===o.event)?[[o.event,o.event]]:[])])}
      ${field('Bloc / objet / entité',`objectives.${i}.target`,o.target,'minecraft:stone')}
      ${field('Quantité',`objectives.${i}.count`,o.count,'1','number')}
      ${disclosure('Libellé personnalisé',loc(o.text,`objectives.${i}.text`,'Texte',language,true))}
      ${disclosure('Conditions',renderConditionTree(o.conditions,`objective:${i}`,{language,quests:project.quests}))}
      ${disclosure('ID',field('Identifiant',`objectives.${i}.id`,o.id))}
    </div>`).join('');
  const rewards=(q.rewards||[]).map((r,i)=>`<div class="mini-card"><div class="mini-card-head"><strong>${r.type==='experience'?'XP':r.type==='item'?'Objet':'Extension'}</strong>${mini('remove-reward',i)}</div>
    ${r.type==='experience'?field('Points',`rewards.${i}.amount`,r.amount,'5','number'):
      r.type==='item'?field('ID de l’item',`rewards.${i}.item`,r.item,'minecraft:diamond')+field('Quantité',`rewards.${i}.count`,r.count,'1','number'):
      '<small>Éditable dans le JSON avancé</small>'}</div>`).join('');
  const prereqs=renderConditionTree(q.requires,'requires',{language,quests:project.quests.filter(v=>v.id!==q.id)});
  return {title:'Quête',subtitle:q.id,html:
    section('Général',loc(q.title,'title','Nom',language)+iconField('Icône','icon',q.icon,project)+
      select('Catégorie','category',q.category,project.categories.map(c=>[c.id,text(c.title,language)]))+
      disclosure('Description',loc(q.description,'description','Description',language,true)))+
    section('Objectifs',select('Type','kind',q.kind||'standard',[['standard','Quête'],['information','Information']])+
      (info?loc(q.narrative,'narrative','Contenu',language)+
        select('Validation','acknowledgement',q.acknowledgement||'manual',[['manual','À valider'],['read_only','Lecture seule']]):
        objectiveCards+`<button class="add-line" data-action="add-objective">+ Objectif</button>`))+
    section('Position',`<div class="field-row">${field('X','layout.x',q.layout?.x??0,'0','number')}${field('Y','layout.y',q.layout?.y??0,'0','number')}</div>`)+
      section('Prérequis',prereqs)+
      disclosure('Récompenses',rewards+`<div class="toolbar-row">${action('add-xp','+ XP')}${action('add-item','+ Objet')}</div>`)+
      disclosure('Progression',select('Mode','progression',q.progression||'once',PROGRESSIONS.map(v=>[v,({once:'Unique',repeatable:'Répétable',infinite:'Infinie',tiered:'Paliers'})[v]]))+
        select('Objectifs','objectiveMode',q.objectiveMode||'all',MODES.map(v=>[v,({all:'Tous',any:'Au choix',sequence:'Dans l’ordre'})[v]]))+
        checkbox('Masquer avant déblocage','hidden',!!q.hidden)+
        `<div class="field-row">${field('Délai (s)','cooldownSeconds',q.cooldownSeconds||0,'0','number')}${field('Temps (s)','timeLimitSeconds',q.timeLimitSeconds||0,'0','number')}</div>`+
        checkbox('Sans mourir','noDeath',!!q.noDeath))+
      disclosure('ID & actions',`<div class="generated-id">${esc(q.id)}</div>`+
        `<div class="toolbar-row">${action('duplicate-quest','Dupliquer')}${action('delete-quest','Supprimer')}</div>`)};
}
