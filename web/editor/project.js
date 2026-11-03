// Quest Studio project data model. Mirrors QuestPlus JSON v2, never the server's internal Gson projection.
export const EVENTS = [
  ['block_break','Casser des blocs'],['block_place','Placer des blocs'],['block_interact','Interagir avec un bloc'],
  ['item_obtained','Obtenir des objets'],['item_craft','Fabriquer'],['item_smelt','Cuire'],['item_use','Utiliser un objet'],
  ['item_consume','Consommer'],['fish_item','Pêcher un objet'],['trade','Échanger'],['entity_kill','Tuer des entités'],
  ['fish_entity','Pêcher une entité'],['tame_animal','Apprivoiser'],['breed_animal','Reproduire'],
  ['npc_interact','Parler à un PNJ'],['biome_visit','Visiter un biome'],['structure_visit','Visiter une structure'],
  ['dimension_enter','Entrer dans une dimension'],['inventory_count','Posséder des objets'],['travel_distance','Parcourir une distance'],
  ['location','Atteindre une position'],['player_death','Mourir'],['manual','Manuel']
];
export const EVENT_KEYS = EVENTS.map(([id])=>id);
export const PROGRESSIONS=['once','repeatable','infinite','tiered'];
export const MODES=['all','any','sequence'];
const ID=/^[a-z0-9_.-]+:[a-z0-9_./-]+$/;
const LOCAL=/^[a-z0-9_.-]+$/;
export const text=(value,lang='fr_fr')=>value?.[lang] || value?.en_us || '';
export const slug=(s)=>String(s||'').normalize('NFKD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/[^a-z0-9]+/g,'_').replace(/^_|_$/g,'') || 'new';
export const copy=(value)=>structuredClone(value);
export const emptyProject=()=>({categories:[],quests:[],config:{schemaVersion:1,autosaveSeconds:5}});
export function demoProject(){
  const p=emptyProject();
  p.categories.push(category('questplus:mining','Minage','Mining'));
  p.categories.push(category('questplus:exploration','Exploration','Exploration'));
  const a=quest(p.categories[0],'questplus:mining/stone','La pierre','The stone',{x:-120,y:0});
  a.objectives=[{id:'stone',event:'block_break',target:'minecraft:stone',count:32}];
  const b=quest(p.categories[0],'questplus:mining/iron','L’âge du fer','Iron age',{x:120,y:0});
  b.icon='minecraft:iron_ingot';
  b.objectives=[{id:'iron',event:'item_obtained',target:'minecraft:iron_ingot',count:16}];
  b.requires={quest:a.id};
  const c=quest(p.categories[0],'questplus:mining/diamond','Premiers diamants','First diamonds',{x:120,y:135});
  c.objectives=[{id:'diamond',event:'item_obtained',target:'minecraft:diamond',count:3}];
  c.icon='minecraft:diamond';c.requires={quest:b.id};
  p.quests.push(a,b,c);
  p.categories[0].annotations=[{id:'begin',x:-130,y:-90,text:{en_us:'BEGIN YOUR JOURNEY',fr_fr:'DÉBUT DE L’AVENTURE'},style:'heading',color:'#d7b987'}];
  return p;
}
export function category(id='questplus:new_category',fr='Nouvelle catégorie',en='New category'){
  return {id,icon:'minecraft:book',title:{en_us:en,fr_fr:fr},description:{en_us:'A quest category',fr_fr:'Une catégorie de quêtes'},annotations:[]};
}
export function quest(group,id='questplus:quest',fr='Nouvelle quête',en='New quest',layout={x:0,y:0}){
  return {schemaVersion:2,id,category:group.id,icon:'minecraft:book',title:{en_us:en,fr_fr:fr},
    objectives:[{id:'step',event:'block_break',target:'minecraft:stone',count:1}],rewards:[],progression:'once',layout};
}
export function annotation(id='note',x=0,y=0){
  return {id,x,y,text:{en_us:'Your note',fr_fr:'Votre annotation'},style:'note',color:'#c6a986'};
}
export function objectiveLabel(o,lang='fr_fr'){
  if(text(o.text,lang))return text(o.text,lang);
  const fr=lang.startsWith('fr');
  const names={
    block_break:['Casser','Break'],block_place:['Placer','Place'],block_interact:['Interagir avec','Interact with'],
    item_obtained:['Obtenir','Obtain'],item_craft:['Fabriquer','Craft'],item_smelt:['Cuire','Smelt'],
    item_use:['Utiliser','Use'],item_consume:['Consommer','Consume'],fish_item:['Pêcher','Fish'],
    fish_entity:['Pêcher','Fish'],trade:['Échanger','Trade'],entity_kill:['Vaincre','Defeat'],
    tame_animal:['Apprivoiser','Tame'],breed_animal:['Faire reproduire','Breed'],npc_interact:['Parler à','Talk to'],
    biome_visit:['Visiter','Visit'],structure_visit:['Visiter','Visit'],dimension_enter:['Entrer dans','Enter'],
    inventory_count:['Posséder','Hold'],travel_distance:['Parcourir','Travel'],location:['Atteindre','Reach'],
    player_death:['Mourir','Die'],manual:['Valider','Acknowledge']
  };
  const name=String(o.target||'').replace(/^#?/,'').split(':').pop().replace(/[_/]/g,' ');
  return (names[o.event]||['Accomplir','Complete'])[fr?0:1]+' '+(o.count||1)+' '+name;
}
export function visibleDescription(q,lang='fr_fr'){
  const description=text(q.description,lang).trim();
  if(!description)return '';
  if((q.objectives||[]).length===1 && normalized(description)===normalized(objectiveLabel(q.objectives[0],lang)))return '';
  return description;
}
const normalized=s=>String(s).normalize('NFKC').toLowerCase().replace(/[\p{P}\s]+/gu,' ').trim();
export function references(req,out=[]){
  if(!req||typeof req!=='object')return out;
  if(typeof req.quest==='string')out.push(req.quest);
  if(Array.isArray(req.and))req.and.forEach(n=>references(n,out));
  if(Array.isArray(req.or))req.or.forEach(n=>references(n,out));
  if(req.not)references(req.not,out);
  return out;
}
export function validate(p){
  const issues=[], seenC=new Set(),seenQ=new Set();
  const fail=(where,message)=>issues.push({where,message});
  if(!p || !Array.isArray(p.categories)||!Array.isArray(p.quests))return [{where:'Projet',message:'Format de projet invalide'}];
  if(p.quests.length>5000)fail('Projet','Plus de 5 000 quêtes');
  for(const c of p.categories){
    const label=c?.id||'Catégorie';
    if(!ID.test(label)||seenC.has(label))fail(label,'ID de catégorie invalide ou dupliqué');
    seenC.add(label);
    if(!c.title?.en_us?.trim()||!c.description?.en_us?.trim())fail(label,'Nom et description EN requis pour la catégorie');
    if(!ID.test(c.icon||'minecraft:book'))fail(label,'Icône invalide');
    if((c.annotations||[]).length>128)fail(label,'128 annotations maximum');
    const ids=new Set();
    for(const a of c.annotations||[]){
      if(!LOCAL.test(a.id)||ids.has(a.id))fail(label,'Annotation : ID invalide ou dupliqué');
      ids.add(a.id);
      if(!a.text?.en_us?.trim())fail(label,'Annotation : texte anglais requis');
      if(!Number.isFinite(a.x)||!Number.isFinite(a.y)||Math.abs(a.x)>100000||Math.abs(a.y)>100000)fail(label,'Position annotation invalide');
      if(!['note','heading','label'].includes(a.style||'note'))fail(label,'Style annotation inconnu');
      if(!/^#[0-9a-f]{6}$/i.test(a.color||'#c6a986'))fail(label,'Couleur annotation invalide');
    }
  }
  for(const q of p.quests){
    const label=q?.id||'Quête';
    if(!ID.test(label)||seenQ.has(label))fail(label,'ID de quête invalide ou dupliqué');
    seenQ.add(label);
    if(!seenC.has(q.category))fail(label,'Catégorie inexistante');
    if(!q.title?.en_us?.trim())fail(label,'Titre anglais obligatoire');
    if(q.description && !q.description.en_us?.trim())fail(label,'Description anglaise requise si renseignée');
    if(!ID.test(q.icon||''))fail(label,'Icône invalide');
    if(![1,2].includes(q.schemaVersion))fail(label,'schemaVersion doit être 1 ou 2');
    if(!PROGRESSIONS.includes(q.progression)||q.schemaVersion===1&&q.progression!=='once')fail(label,'Mode de progression incompatible');
    if(q.objectiveMode&&!MODES.includes(q.objectiveMode))fail(label,'Mode d’objectifs invalide');
    if(q.layout&&(!Number.isFinite(q.layout.x)||!Number.isFinite(q.layout.y)))fail(label,'Position invalide');
    const info=q.kind==='information';
    if(info && (q.schemaVersion!==2||q.progression!=='once'||q.objectives?.length))fail(label,'Quête informative incompatible');
    if(!info && (!Array.isArray(q.objectives)||!q.objectives.length))fail(label,'Au moins un objectif requis');
    if((q.objectives||[]).length>256)fail(label,'256 objectifs maximum');
    const objs=new Set();
    for(const o of q.objectives||[]){
      if(!LOCAL.test(o.id)||objs.has(o.id))fail(label,'Objectif : ID invalide ou dupliqué');
      objs.add(o.id);
      if(!EVENT_KEYS.includes(o.event)&&!ID.test(o.event))fail(label,'Événement non reconnu');
      if(!/^#?[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(o.target||''))fail(label,'Cible invalide');
      if(!Number.isInteger(o.count)||o.count<1||o.count>1000000000)fail(label,'Quantité invalide');
      if(o.text && !o.text.en_us?.trim())fail(label,'Texte objectif : en_us requis');
    }
    if((q.rewards||[]).length>64)fail(label,'64 récompenses maximum');
    for(const r of q.rewards||[]){
      if(r.type==='experience'&&(!Number.isInteger(r.amount)||r.amount<1||r.amount>1000000))fail(label,'XP invalide');
      else if(r.type==='item'&&(!ID.test(r.item)||!Number.isInteger(r.count)||r.count<1||r.count>2304))fail(label,'Récompense objet invalide');
      else if(!['experience','item'].includes(r.type)&&!ID.test(r.type||''))fail(label,'Type de récompense invalide');
    }
    if(q.progression==='tiered'&&!(q.tiers||[]).length)fail(label,'Paliers requis');
    if(q.schemaVersion===1&&['objectiveMode','tiers','afterLast','cooldownSeconds','timeLimitSeconds','noDeath'].some(k=>Object.hasOwn(q,k)))fail(label,'Fonctions v2 interdites dans le schéma v1');
  }
  const questMap=new Map(p.quests.map(q=>[q.id,q])),state=new Map();
  function visit(id,trail=[]){
    if(state.get(id)===2)return;
    if(state.get(id)===1){fail(id,'Cycle de dépendances : '+[...trail,id].join(' → '));return;}
    const q=questMap.get(id);if(!q)return;
    state.set(id,1);
    for(const ref of new Set(references(q.requires))){
      const target=questMap.get(ref);
      if(!target)fail(id,'Prérequis introuvable : '+ref);
      else if(target.kind==='information'&&target.acknowledgement==='read_only')fail(id,'Une quête en lecture seule ne peut être prérequise');
      else visit(ref,[...trail,id]);
    }
    state.set(id,2);
  }
  for(const q of p.quests)visit(q.id);
  return issues;
}
export function fileMap(p){
  const map=new Map(), used=new Set();
  function put(path,obj){if(used.has(path))throw new Error('Fichier dupliqué : '+path);used.add(path);map.set(path,JSON.stringify(obj,null,2)+'\n');}
  const fileName=id=>{const [ns,...rest]=id.split(':');const path=rest.join(':');if(!ID.test(id))throw new Error('ID invalide: '+id);return ns==='questplus'?path:ns+'/'+path;};
  put('config.json',p.config||{schemaVersion:1,autosaveSeconds:5});
  for(const c of p.categories)put('categories/'+fileName(c.id)+'.json',c);
  for(const q of p.quests)put('quests/'+fileName(q.id)+'.json',q);
  map.set('index.txt',[...map.keys()].join('\n')+'\n');
  return map;
}
export function importDocuments(documents){
  const p=emptyProject();let valid=0;
  for(const {name,content} of documents){
    const path=String(name).replace(/\\/g,'/').replace(/^.*?(?=(?:quests|categories)\/)/,'');
    if(!/\.json$/i.test(path))continue;
    let obj;try{obj=JSON.parse(String(content).replace(/^\uFEFF/,''));}catch(e){throw Error('JSON invalide dans '+name+' : '+e.message);}
    if(/(?:^|\/)categories\//.test(path)||(!obj.schemaVersion&&obj.id&&obj.title&&!obj.objectives))p.categories.push(obj);
    else if(/(?:^|\/)quests\//.test(path)||obj.objectives&&obj.id)p.quests.push(obj);
    else if(/(?:^|\/)config\.json$/i.test(path))p.config=obj;
    else continue;
    valid++;
  }
  if(!valid)throw Error('Aucune catégorie ou quête trouvée. Choisissez un dossier ou ZIP QuestPlus.');
  return p;
}
