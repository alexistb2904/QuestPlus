import {EVENTS,MODES,PROGRESSIONS,objectiveLabel,text,visibleDescription,references} from './project.js';
const esc=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const attr=esc;
const control=(label,field,value='',placeholder='',type='text',help='')=>`<div class="field"><label>${esc(label)}</label><input data-field="${attr(field)}" type="${type}" value="${attr(value)}" placeholder="${attr(placeholder)}">${help?`<div class="field-help">${esc(help)}</div>`:''}</div>`;
const textarea=(label,field,value='',placeholder='',help='')=>`<div class="field"><label>${esc(label)}</label><textarea data-field="${attr(field)}" placeholder="${attr(placeholder)}">${esc(value)}</textarea>${help?`<div class="field-help">${esc(help)}</div>`:''}</div>`;
const select=(label,field,current,options)=>`<div class="field"><label>${esc(label)}</label><select data-field="${attr(field)}">${options.map(([id,label])=>`<option value="${attr(id)}" ${id===current?'selected':''}>${esc(label)}</option>`).join('')}</select></div>`;
const check=(label,field,value)=>`<label class="checkbox-row"><input type="checkbox" data-field="${attr(field)}" ${value?'checked':''}><span>${esc(label)}</span></label>`;
const sect=(name,content)=>`<section class="section"><div class="section-title">${esc(name)}</div>${content}</section>`;
const button=(act,label,more='')=>`<button class="button secondary" data-action="${act}" ${more}>${esc(label)}</button>`;
const mini=(act,index)=>`<button class="tiny danger" data-action="${act}" data-index="${index}" title="Supprimer">✕</button>`;
const i18n=(o,field,title='Texte')=>`<div class="field-row">${control(title+' (FR)',field+'.fr_fr',o?.fr_fr||'','Facultatif')}${control(title+' (EN)',field+'.en_us',o?.en_us||'','Required')}</div>`;
const idhelp='⚠ Modifier un ID après publication peut interrompre la progression des joueurs.';

export function inspectorData(project,selection,categoryId,lang){
  const category=project.categories.find(c=>c.id===categoryId);
  if(!selection||!category)return {title:'Propriétés',subtitle:'Choisissez une catégorie',html:'<div class="tip">Crée une catégorie pour commencer.</div>'};
  if(selection.type==='category'){
    return {title:'Catégorie',subtitle:category.id,html:
      sect('Identité',control('Identifiant', 'id',category.id,'questplus:mining','text',idhelp)+
        control('Icône Minecraft','icon',category.icon,'minecraft:book')+
        i18n(category.title,'title','Nom')+
        i18n(category.description,'description','Description'))+
      sect('Contenu',`<div class="preview"><strong>${esc(project.quests.filter(q=>q.category===category.id).length)}</strong> quêtes · <strong>${esc((category.annotations||[]).length)}</strong> annotations</div>
      <div class="tip">Les annotations sont positionnées dans l'arbre, comme les quêtes. Elles sont visibles en jeu et suivent le zoom et le déplacement.</div>`)+
      sect('Actions',`<div class="toolbar-row">${button('create-quest','＋ Quête')}${button('create-annotation','✎ Annotation')}</div>
        <div class="danger-zone">${button('delete-category','Supprimer la catégorie','style="color:#e5a2ad"')}</div>`)};
  }
  if(selection.type==='annotation'){
    const note=(category.annotations||[]).find(a=>a.id===selection.id);
    if(!note)return {title:'Annotation',subtitle:'Introuvable',html:''};
    return {title:'Annotation',subtitle:note.id,html:
      sect('Contenu',control('Identifiant','id',note.id,'start_here','text','Unique dans la catégorie')+
        i18n(note.text,'text','Annotation')+
        select('Style','style',note.style||'note',[['note','Encadré'],['heading','Grand titre'],['label','Texte libre']])+
        control('Couleur','color',note.color||'#c6a986','','color'))+
      sect('Position sur l’arbre',`<div class="field-row">${control('X','x',note.x,'0','number')}${control('Y','y',note.y,'0','number')}</div><div class="tip">Vous pouvez aussi glisser directement l'annotation sur le canvas.</div>`)+
      sect('Actions',button('duplicate-annotation','Dupliquer')+' '+button('delete-annotation','Supprimer','style="color:#e5a2ad"'))};
  }
  const q=project.quests.find(o=>o.id===selection.id);
  if(!q)return {title:'Quête',subtitle:'Introuvable',html:''};
  const other=project.quests.filter(o=>o.id!==q.id),refs=new Set(references(q.requires));
  const prereqs=other.map(item=>`<label class="checkbox-row"><input type="checkbox" data-dependency="${attr(item.id)}" ${refs.has(item.id)?'checked':''}><span title="${attr(item.id)}">${esc(text(item.title,lang))}</span></label>`).join('')||'<div class="field-help">Crée une autre quête pour ajouter un prérequis.</div>';
  const objectives=(q.objectives||[]).map((o,i)=>`
    <div class="mini-card"><div class="mini-card-head"><span class="mini-card-title">Étape ${i+1}</span><div class="mini-card-actions">${mini('remove-objective',i)}</div></div>
      ${control('ID de l’objectif',`objectives.${i}.id`,o.id,'step_1')}
      ${select('Événement',`objectives.${i}.event`,o.event,
          [...EVENTS,...(!EVENTS.some(a=>a[0]===o.event)?[[o.event,o.event]]:[])])}
      ${control('Cible',`objectives.${i}.target`,o.target,'minecraft:cobblestone')}
      ${control('Quantité',`objectives.${i}.count`,o.count,1,'number')}
      ${i18n(o.text,`objectives.${i}.text`,'Libellé personnalisé')}
      <div class="field-help">Aucun texte ? Le jeu génère automatiquement : <strong>${esc(objectiveLabel(o,lang))}</strong></div>
      ${o.conditions?'<div class="tip">Conditions supplémentaires : conservées dans le JSON avancé.</div>':''}
    </div>`).join('');
  const rewards=(q.rewards||[]).map((r,i)=>`<div class="mini-card"><div class="mini-card-head"><span class="mini-card-title">${esc(r.type==='experience'?'Expérience':r.type==='item'?'Objet':'Extension')}</span>${mini('remove-reward',i)}</div>
   ${r.type==='experience'?control('Points d’expérience',`rewards.${i}.amount`,r.amount,'10','number'):
    r.type==='item'?control('Identifiant objet',`rewards.${i}.item`,r.item,'minecraft:diamond')+control('Quantité',`rewards.${i}.count`,r.count,1,'number'):
    '<div class="tip">Récompense personnalisée : modifier dans le JSON avancé.</div>'}</div>`).join('');
  const info=q.kind==='information';
  return {title:'Édition de quête',subtitle:q.id,html:
    sect('Général',control('Identifiant', 'id',q.id,'questplus:mining/stone','text',idhelp)+
      control('Icône Minecraft','icon',q.icon,'minecraft:stone')+
      select('Catégorie','category',q.category,project.categories.map(c=>[c.id,text(c.title,lang)]))+
      i18n(q.title,'title','Titre')+
      i18n(q.description,'description','Description facultative')+
      `<div class="tip">Si la description est vide, QuestPlus affiche seulement l'objectif. Une description identique au seul objectif n'est jamais répétée.</div>`+
      (visibleDescription(q,lang)?`<div class="preview">${esc(visibleDescription(q,lang))}</div>`:
        `<div class="preview">${esc((q.objectives||[]).map(o=>objectiveLabel(o,lang)).join(' · ')||'Quête informative')}</div>`))+
    sect('Objectifs',select('Type de quête','kind',q.kind||'standard',[['standard','Objectifs de jeu'],['information','Page informative']])+
       (!info?objectives+`<button class="add-line" data-action="add-objective">＋ Ajouter un objectif</button>`:
        select('Confirmation','acknowledgement',q.acknowledgement||'manual',[['manual','Lecture à valider'],['read_only','Lecture seule']])+
        i18n(q.narrative,'narrative','Contenu de la page'))+
       (!info?select('Mode d’objectifs','objectiveMode',q.objectiveMode||'all',MODES.map(a=>[a,({all:'Tous',any:'Un au choix',sequence:'Dans l’ordre'})[a]])):''
       ))+
    sect('Prérequis',`<p class="field-help">Les quêtes sans prérequis sont immédiatement disponibles. En cochant plusieurs quêtes, toutes deviennent nécessaires (ET). Pour un OU / NON, utilise le JSON avancé.</p>${prereqs}`)+
    sect('Récompenses',rewards+`<div class="toolbar-row">${button('add-xp','＋ XP')}${button('add-item','＋ Objet')}</div>`)+
    sect('Progression',select('Type de progression','progression',q.progression,PROGRESSIONS.map(p=>[p,({once:'Unique',repeatable:'Rejouable',infinite:'Infinie',tiered:'À paliers'})[p]]))+
       check('Quête cachée avant déblocage','hidden',!!q.hidden)+
       (q.progression==='tiered'?'<div class="tip">Les paliers et leurs récompenses sont éditables dans le JSON avancé.</div>':'')+
       `<div class="field-row">${control('Cooldown (s)','cooldownSeconds',q.cooldownSeconds||0,'0','number')}${control('Temps limite (s)','timeLimitSeconds',q.timeLimitSeconds||0,'0','number')}</div>`+
       check('Sans mourir','noDeath',!!q.noDeath))+
    sect('Position',`<div class="field-row">${control('X','layout.x',q.layout?.x??0,'0','number')}${control('Y','layout.y',q.layout?.y??0,'0','number')}</div><p class="field-help">Les coordonnées sont identiques au champ layout utilisé par le mod.</p>`)+
    sect('Actions',`<div class="toolbar-row">${button('duplicate-quest','Dupliquer')}${button('delete-quest','Supprimer','style="color:#e5a2ad"')}</div>`)};
}
