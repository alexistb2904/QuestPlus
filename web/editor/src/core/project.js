import {BUILTIN_PROJECT} from '../data/default-data.js';
import JSON5 from 'json5';
import {validRich,mediaPattern} from './rich-format.js';
import {BIOMES,STRUCTURES,DIMENSIONS} from './objective-targets.js';
import {isCategoryLink} from './diagram-geometry.js';
// Quest Studio project data model. Mirrors QuestPlus JSON v2, never the server's internal Gson projection.
export const EVENTS = [
  ['block_break','Casser des blocs'],['block_place','Placer des blocs'],['block_interact','Interagir avec un bloc'],
  ['item_obtained','Obtenir des objets'],['item_craft','Fabriquer'],['item_smelt','Cuire'],['item_use','Utiliser un objet'],
  ['item_consume','Consommer'],['fish_item','Pêcher un objet'],['trade','Échanger'],['entity_kill','Tuer des entités'],
  ['fish_entity','Pêcher une entité'],['questplus:fish_any','Pêcher des poissons (toutes espèces)'],['tame_animal','Apprivoiser'],['breed_animal','Reproduire'],
  ['npc_interact','Parler à un PNJ'],['biome_visit','Visiter un biome'],['structure_visit','Visiter une structure'],
  ['dimension_enter','Entrer dans une dimension'],['inventory_count','Posséder des objets'],['travel_distance','Parcourir une distance'],
  ['location','Atteindre une position'],['xp_levels','Posséder des niveaux XP'],['equipment_check','Vérifier un équipement'],['player_death','Mourir'],['manual','Manuel']
];
export const EVENT_KEYS = EVENTS.map(([id])=>id);
export const PROGRESSIONS=['once','repeatable','infinite','tiered'];
export const MODES=['all','any','sequence'];
const ID=/^[a-z0-9_.-]+:[a-z0-9_./-]+$/;
const LOCAL=/^[a-z0-9_.-]+$/;
export const text=(value,lang='en_us')=>{
  if(!value||typeof value!=='object')return '';
  const valid=s=>typeof s==='string'&&s.trim().length>0;
  if(valid(value[lang]))return value[lang];
  if(valid(value.en_us))return value.en_us;
  const key=Object.keys(value).sort().find(k=>valid(value[k]));
  return key?value[key]:'';
};
// Annotation IDs are unique within a category, not across the pack.
export function selectedOwner(project,selection,categoryId){
  if(!selection)return undefined;
  if(selection.type==='quest')return project.quests.find(q=>q.id===selection.id);
  if(selection.type==='category')return project.categories.find(c=>c.id===selection.id);
  return project.categories.find(c=>c.id===categoryId)?.annotations?.find(a=>a.id===selection.id);
}
/** Locale fallback for authored annotations; ignore the studio's untouched "Note" seed. */
export const annotationText=(annotation,lang='en_us')=>{
  const map=annotation?.text||{};
  const authored=Object.entries(map).filter(([,value])=>typeof value==='string'&&value.trim()&&value.trim()!=='Note');
  if(authored.length) {
    const locale=authored.find(([key])=>key===lang)||authored.find(([key])=>key==='en_us')||authored.sort(([a],[b])=>a.localeCompare(b))[0];
    return locale[1];
  }
  return text(map,lang);
};
export const annotationRich=(annotation,lang='en_us')=>{
  const rich=annotation?.rich||{};
  const usable=parts=>Array.isArray(parts)&&parts.some(p=>p?.type==='image'&&typeof p.data==='string'&&p.data.length>0 || p?.type==='text'&&typeof p.text==='string'&&p.text.trim().length>0);
  if(usable(rich[lang]))return rich[lang];
  if(usable(rich.en_us))return rich.en_us;
  const key=Object.keys(rich).sort().find(k=>usable(rich[k]));
  return key?rich[key]:null;
};
/** Seed a new preview/translation language from an authored source once.
 * Copies rich objects so edits never mutate the original translation. */
export function seedProjectLocale(project,locale){
  if(!/^[a-z]{2,3}_[a-z]{2,4}$/.test(locale)||locale==='en_us')return project;
  const seed=map=>{
    if(!map||typeof map!=='object'||Array.isArray(map))return;
    if(typeof map[locale]==='string'&&map[locale].trim())return;
    const source=text(map,'en_us');
    if(source)map[locale]=source;
  };
  for(const category of project.categories||[]){
    seed(category.title);seed(category.description);
    for(const note of category.annotations||[]){
      note.text ||= {};
      const src=annotationText(note,'en_us');
      if((!note.text[locale]||note.text[locale]==='Note')&&src)note.text[locale]=src;
      const parts=annotationRich(note,'en_us');
      if(parts?.length&&!annotationRich({...note,rich:{[locale]:note.rich?.[locale]}},locale)){
        note.rich ||= {};
        note.rich[locale]=structuredClone(parts);
      }
    }
  }
  for(const quest of project.quests||[]){
    seed(quest.title);seed(quest.description);seed(quest.narrative);
    if(quest.descriptionRich){
      const rich=quest.descriptionRich;
      const usable=parts=>Array.isArray(parts)&&parts.some(p=>p.type==='image'||p.type==='text'&&p.text?.trim());
      if(!usable(rich[locale])){
        const source=usable(rich.en_us)?rich.en_us:Object.values(rich).find(usable);
        if(source)rich[locale]=structuredClone(source);
      }
    }
    for(const o of quest.objectives||[])seed(o.text);
    for(const reward of quest.rewards||[])seed(reward.text);
    for(const tier of quest.tiers||[])for(const reward of tier.rewards||[])seed(reward.text);
  }
  project.editorLocales=[...new Set([...(project.editorLocales||[]),locale])];
  return project;
}
export const slug=(s)=>String(s||'').normalize('NFKD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/[^a-z0-9]+/g,'_').replace(/^_|_$/g,'') || 'new';
export const copy=(value)=>structuredClone(value);
export const emptyProject=()=>({categories:[],quests:[],config:{schemaVersion:1,autosaveSeconds:5},targetMinecraftVersion:'26.3',iconVersions:{}});
/** The editor starts from the exact packaged challenge catalogue plus historical examples. */
export function demoProject(){return copy(BUILTIN_PROJECT);}
/** Missing order sorts after explicitly ordered categories; ties are deterministic. */
export function orderedCategories(categories){
  return [...categories].sort((a,b)=>(a.order??Number.MAX_SAFE_INTEGER)-(b.order??Number.MAX_SAFE_INTEGER)||a.id.localeCompare(b.id));
}
export function category(id='questplus:new_category',en='New category'){
  return {id,icon:'minecraft:book',title:{en_us:en},annotations:[]};
}
export function quest(group,id='questplus:quest',en='New quest',layout={x:0,y:0}){
  return {schemaVersion:2,id,category:group.id,icon:'minecraft:book',size:32,title:{en_us:en},
    objectives:[{id:'step',event:'block_break',target:'minecraft:stone',count:1}],rewards:[],progression:'once',layout};
}
export function annotation(id='note',x=0,y=0){
  return {id,x,y,text:{en_us:'Note'},style:'note',color:'#c6a986',width:170,height:100};
}
export function gearRequirementText(conditions,lang='en_us'){
  const root=conditions?.['questplus:gear'] ? conditions : conditions?.and?.find(x=>x?.['questplus:gear']);
  const items=root?.['questplus:gear']?.items;
  if(!Array.isArray(items)||!items.length)return '';
  const locale=lang.startsWith('fr')?'fr':lang.startsWith('es')?'es':lang.startsWith('zh')?'zh':'en';
  const names=items.map(x=>String(x.item||'').split(':').pop().replace(/_/g,' '));
  const suffixes=['helmet','chestplate','leggings','boots'];
  const slots=['head','chest','legs','feet'];
  const isSet=items.length===4 && slots.every((slot,i)=>items.some(x=>x.slot===slot &&
    String(x.item||'').endsWith('_'+suffixes[i])));
  const base=isSet?items.find(x=>x.slot==='head').item.replace(/_helmet$/,''):null;
  const sameMaterial=isSet&&slots.every((slot,i)=>items.find(x=>x.slot===slot).item.replace(new RegExp('_'+suffixes[i]+'$'),'')===base);
  const pretty=id=>String(id).split(':').pop().replace(/_/g,' ');
  const levels=items.map(x=>Object.entries(x.enchantments||{}).map(([id,n])=>pretty(id)+' '+n));
  const common=levels[0]||[];
  const uniform=levels.every(x=>JSON.stringify(x)===JSON.stringify(common));
  if(sameMaterial){
    const prefix={en:'Wear full',fr:'Porter une armure complète en',es:'Llevar armadura completa de',zh:'穿戴整套'}[locale];
    const suffix=locale==='en'?' armor':locale==='zh'?'护甲':'';
    const extras=uniform?common.join(', '):({en:'Enchanted set',fr:'Armure enchantée',es:'Armadura encantada',zh:'附魔套装'}[locale]);
    const material=String(base).replace(/^minecraft:/,'');
    const materialNames={
      leather:{en:'leather',fr:'cuir',es:'cuero',zh:'皮革'},
      copper:{en:'copper',fr:'cuivre',es:'cobre',zh:'铜'},
      chainmail:{en:'chainmail',fr:'mailles',es:'cota de malla',zh:'锁链'},
      iron:{en:'iron',fr:'fer',es:'hierro',zh:'铁'},
      golden:{en:'gold',fr:'or',es:'oro',zh:'金'},
      diamond:{en:'diamond',fr:'diamant',es:'diamante',zh:'钻石'},
      netherite:{en:'netherite',fr:'Netherite',es:'netherita',zh:'下界合金'}
    };
    const localizedMaterial=materialNames[material]?.[locale]||pretty(base);
    return prefix+' '+localizedMaterial+suffix+(extras?' · '+extras:'');
  }
  const verbs={en:'Equip',fr:'Équiper',es:'Equipar',zh:'装备'};
  const equipment=items.map((x,i)=>names[i]+(levels[i].length?' ('+levels[i].join(', ')+')':''));
  return verbs[locale]+' '+equipment.join(' · ');
}

export function objectiveLabel(o,lang='fr_fr'){
  const gear=gearRequirementText(o.conditions,lang);
  if(gear && o.event==='equipment_check')return gear;
  if(text(o.text,lang))return text(o.text,lang)+(gear?' · '+gear:'');
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
  if(o.event==='travel_distance')return (fr?'Parcourir ':'Travel ')+Number(((o.count||0)/100).toFixed(2))+' m';
  if(o.event==='location')return fr?'Atteindre la position':'Reach location';
  const name=String(o.target||'').replace(/^#?/,'').split(':').pop().replace(/[_/]/g,' ');
  return (names[o.event]||['Accomplir','Complete'])[fr?0:1]+' '+(o.count||1)+' '+name;
}
export function visibleDescription(q,lang='fr_fr'){
  const description=text(q.description,lang).trim();
  if(!description)return '';
  if((q.objectives||[]).some(o=>normalized(description)===normalized(objectiveLabel(o,lang))))return '';
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
  const translations=value=>value&&typeof value==='object'&&!Array.isArray(value)&&typeof value.en_us==='string'&&value.en_us.trim()&&Object.values(value).every(s=>typeof s==='string'&&s.trim());
  const validateRewards=(list,label)=>{
    if(!Array.isArray(list)){fail(label,'Liste de récompenses invalide');return;}
    if(list.length>64)fail(label,'64 récompenses maximum');
    for(const r of list){
      if(!r||typeof r!=='object'||Array.isArray(r)){fail(label,'Récompense invalide');continue;}
      if(['experience','experience_levels'].includes(r.type)&&(!Number.isInteger(r.amount)||r.amount<1||r.amount>1000000))fail(label,'XP invalide');
      else if(r.type==='item'&&(!ID.test(r.item)||r.item==='minecraft:air'||!Number.isInteger(r.count)||r.count<1||r.count>2304))fail(label,'Récompense objet invalide');
      else if(!['experience','experience_levels','item'].includes(r.type)&&(!ID.test(r.type||'')||!r.data||typeof r.data!=='object'||Array.isArray(r.data)))fail(label,'Récompense personnalisée invalide');
      if(Object.hasOwn(r,'text')&&!translations(r.text))fail(label,'Texte récompense : traduction anglaise et textes non vides requis');
    }
  };
  const validateCondition=(root,label,prerequisite=false)=>{
    let nodes=0;
    const visit=(node,depth)=>{
      if(++nodes>256||depth>16||!node||typeof node!=='object'||Array.isArray(node)||Object.keys(node).length!==1)return false;
      const [key,value]=Object.entries(node)[0];
      if(key==='and'||key==='or')return Array.isArray(value)&&value.length>0&&value.every(child=>visit(child,depth+1));
      if(key==='not')return visit(value,depth+1);
      if(prerequisite)return key==='quest'&&typeof value==='string'&&ID.test(value);
      if(['dimension','tool','biome','effect'].includes(key))return typeof value==='string'&&ID.test(value);
      if(key==='sneaking')return typeof value==='boolean';
      if(['healthAtMost','healthAtLeast'].includes(key))return Number.isFinite(value)&&value>=0;
      if(key==='questplus:gear'){
        if(!value||typeof value!=='object'||Array.isArray(value)||Object.keys(value).join()!=='items'
           ||!Array.isArray(value.items)||value.items.length<1||value.items.length>40)return false;
        return value.items.every(item=>{
          if(!item||typeof item!=='object'||Array.isArray(item)||
            !['inventory','head','chest','legs','feet','mainhand','offhand'].includes(item.slot)||
            typeof item.item!=='string'||!ID.test(item.item)||
            Object.keys(item).some(k=>!['slot','item','enchantments'].includes(k)))return false;
          if(item.enchantments===undefined)return true;
          return item.enchantments&&typeof item.enchantments==='object'&&!Array.isArray(item.enchantments)&&
            Object.entries(item.enchantments).every(([id,level])=>ID.test(id)&&Number.isInteger(level)&&level>=1&&level<=10);
        });
      }
      if(key==='equipment')return value&&typeof value==='object'&&!Array.isArray(value)&&Object.keys(value).length===2&&['head','chest','legs','feet','mainhand','offhand'].includes(value.slot)&&typeof value.item==='string'&&ID.test(value.item);
      if(key==='position'){
        if(!value||typeof value!=='object'||Array.isArray(value)||!Object.keys(value).length)return false;
        return Object.entries(value).every(([k,v])=>/^[xyz](Min|Max)$/.test(k)&&Number.isFinite(v))&&['x','y','z'].every(axis=>value[axis+'Min']===undefined||value[axis+'Max']===undefined||value[axis+'Min']<=value[axis+'Max']);
      }
      if(key==='context')return value&&typeof value==='object'&&!Array.isArray(value)&&Object.keys(value).length===2&&typeof value.key==='string'&&value.key.trim()&&['string','number','boolean'].includes(typeof value.equals)&&(typeof value.equals!=='number'||Number.isFinite(value.equals));
      return ID.test(key);
    };
    if(!visit(root,0))fail(label,prerequisite?'Prérequis invalide':'Condition d’objectif invalide');
  };
  if(!p || !Array.isArray(p.categories)||!Array.isArray(p.quests))return [{where:'Projet',message:'Format de projet invalide'}];
  if(p.quests.length>5000)fail('Projet','Plus de 5 000 quêtes');
  for(const c of p.categories){
    const label=c?.id||'Catégorie';
    if(!ID.test(label)||seenC.has(label))fail(label,'ID de catégorie invalide ou dupliqué');
    seenC.add(label);
    if(Object.hasOwn(c,'order')&&(!Number.isSafeInteger(c.order)||c.order<0||c.order>1000000))fail(label,'L’ordre doit être un entier de 0 à 1 000 000');
    if(!c.title?.en_us?.trim())fail(label,'Nom anglais requis');
    if(c.description && !c.description.en_us?.trim())fail(label,'Description anglaise requise si renseignée');
    if(!ID.test(c.icon||'minecraft:book'))fail(label,'Icône invalide');
    if((c.annotations||[]).length>128)fail(label,'128 annotations maximum');
    const ids=new Set();
for(const a of c.annotations||[]){
      if(!LOCAL.test(a.id)||ids.has(a.id))fail(label,'Annotation : ID invalide ou dupliqué');
      ids.add(a.id);
      const hasTranslatedText=Object.values(a.text||{}).some(v=>typeof v==='string'&&v.trim());
      const hasRichContent=Object.values(a.rich||{}).some(parts=>Array.isArray(parts)&&parts.some(part=>part?.type==='image'&&part.data||part?.type==='text'&&part.text?.trim()));
      if(a.style!=='background'&&!hasTranslatedText&&!hasRichContent)fail(label,'Annotation : contenu requis dans au moins une langue');
      if(a.style==='background'&&(a.rich||!Number.isSafeInteger(a.width)||!Number.isSafeInteger(a.height)||a.width>100000||a.height>100000))fail(label,'Background requires dimensions up to 100000 and no rich content');
      if(a.rich){
       if(typeof a.rich!=='object'||Array.isArray(a.rich)||!Object.keys(a.rich).length)fail(label,'Invalid rich annotation');
       else for(const [locale,parts] of Object.entries(a.rich)){
        if(!/^[a-z]{2,3}_[a-z]{2,4}$/.test(locale)||!validRich(parts))fail(label,'Invalid rich annotation/media: '+locale);
       }
      }
      if(!Number.isFinite(a.x)||!Number.isFinite(a.y)||Math.abs(a.x)>100000||Math.abs(a.y)>100000)fail(label,'Position annotation invalide');
      if(a.width!==undefined&&(!Number.isSafeInteger(a.width)||a.width<=0))fail(label,'La largeur doit être positive');
      if(a.height!==undefined&&(!Number.isSafeInteger(a.height)||a.height<=0))fail(label,'La hauteur doit être positive');
      if(!['note','heading','label','background'].includes(a.style||'note'))fail(label,'Style annotation inconnu');
      if(a.align!==undefined&&!['left','center','right'].includes(a.align))fail(label,'Invalid annotation alignment');
      if(!/^#[0-9a-f]{6}$/i.test(a.color||'#c6a986'))fail(label,'Couleur annotation invalide');
    }
    if(c.linkRoutes!==undefined){
     if(!Array.isArray(c.linkRoutes)||c.linkRoutes.length>128)fail(label,'128 link routes maximum');
     else {
      const pairs=new Set();
      for(const route of c.linkRoutes){
       if(!route||typeof route!=='object'||Array.isArray(route)||Object.keys(route).some(k=>!['from','to','points'].includes(k))||!ID.test(route.from||'')||!ID.test(route.to||'')||!isCategoryLink(c,p.quests,route)){
        fail(label,'Link route must reference a prerequisite in the same category');continue;
       }
       const pair=route.from+'>'+route.to;
       if(pairs.has(pair))fail(label,'Duplicate link route');
       pairs.add(pair);
       if(!Array.isArray(route.points)||route.points.length>64)fail(label,'64 waypoints maximum per link');
       else for(const point of route.points){
        if(!point||typeof point!=='object'||Object.keys(point).some(k=>!['x','y'].includes(k))||!Number.isFinite(point.x)||!Number.isFinite(point.y)||Math.abs(point.x)>100000||Math.abs(point.y)>100000)fail(label,'Invalid link waypoint');
       }
      }
     }
    }
  }
  for(const q of p.quests){
    const label=q?.id||'Quête';
    if(!ID.test(label)||seenQ.has(label))fail(label,'ID de quête invalide ou dupliqué');
    seenQ.add(label);
    if(!seenC.has(q.category))fail(label,'Catégorie inexistante');
    if(!q.title?.en_us?.trim())fail(label,'Titre anglais obligatoire');
    if(q.description && !q.description.en_us?.trim())fail(label,'Description anglaise requise si renseignée');
    if(q.descriptionRich){
      for(const [locale,parts] of Object.entries(q.descriptionRich))
        if(!/^[a-z]{2,3}_[a-z]{2,4}$/.test(locale)||!validRich(parts))
          fail(label,'Invalid rich description: '+locale);
    }
    if(!ID.test(q.icon||''))fail(label,'Icône invalide');
    if(q.size!==undefined&&(!Number.isSafeInteger(q.size)||q.size<=0))fail(label,'La taille doit être positive');
    if(![1,2].includes(q.schemaVersion))fail(label,'schemaVersion doit être 1 ou 2');
    if(!PROGRESSIONS.includes(q.progression)||q.schemaVersion===1&&q.progression!=='once')fail(label,'Mode de progression incompatible');
    if(Object.hasOwn(q,'objectiveMode')&&!MODES.includes(q.objectiveMode))fail(label,'Mode d’objectifs invalide');
    if(q.layout&&(!Number.isFinite(q.layout.x)||!Number.isFinite(q.layout.y)))fail(label,'Position invalide');
    const info=q.kind==='information';
    if(Object.hasOwn(q,'kind')&&!['standard','information'].includes(q.kind))fail(label,'Type de quête invalide');
    if(Object.hasOwn(q,'acknowledgement')&&(!info||!['manual','read_only'].includes(q.acknowledgement)))fail(label,'Mode de lecture invalide');
    if(info && (q.schemaVersion!==2||q.progression!=='once'||q.objectives?.length||Object.hasOwn(q,'tiers')||Object.hasOwn(q,'objectiveMode')||q.cooldownSeconds||q.timeLimitSeconds||q.noDeath))fail(label,'Quête informative incompatible');
    if(info&&q.acknowledgement==='read_only'&&q.rewards?.length)fail(label,'Lecture seule : aucune récompense autorisée');
    if(Object.hasOwn(q,'narrative')&&!translations(q.narrative))fail(label,'Texte narratif : traduction anglaise et textes non vides requis');
    if(!Array.isArray(q.objectives))fail(label,'Liste d’objectifs invalide');
    if(!info && (!Array.isArray(q.objectives)||!q.objectives.length))fail(label,'Au moins un objectif requis');
    if((q.objectives||[]).length>256)fail(label,'256 objectifs maximum');
    const objs=new Set();
    for(const o of Array.isArray(q.objectives)?q.objectives:[]){
      if(!o||typeof o!=='object'||Array.isArray(o)){fail(label,'Objectif invalide');continue;}
      if(!LOCAL.test(o.id)||objs.has(o.id))fail(label,'Objectif : ID invalide ou dupliqué');
      objs.add(o.id);
      if(!EVENT_KEYS.includes(o.event)&&!ID.test(o.event))fail(label,'Événement non reconnu');
      if(!/^#?[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(o.target||''))fail(label,'Cible invalide');
      if(!Number.isInteger(o.count)||o.count<1||o.count>1000000000)fail(label,'Quantité invalide');
      if(Object.hasOwn(o,'text')&&!translations(o.text))fail(label,'Texte objectif : traduction anglaise et textes non vides requis');
      if(o.event==='location'){
        const bounds=o.conditions?.position || (Array.isArray(o.conditions?.and)?o.conditions.and.find(node=>node?.position)?.position:null);
        if(!bounds || !['x','y','z'].every(axis=>Number.isFinite(bounds[axis+'Min'])&&Number.isFinite(bounds[axis+'Max'])))
          fail(label,'Position : renseigner X, Y et Z');
      }
      if(o.event==='biome_visit'&&o.target?.startsWith('minecraft:')&&!BIOMES.includes(o.target))fail(label,'Biome vanilla inconnu : '+o.target);
      if(o.event==='structure_visit'&&o.target?.startsWith('minecraft:')&&!STRUCTURES.includes(o.target))fail(label,'Structure vanilla inconnue : '+o.target);
      if(o.event==='dimension_enter'&&o.target?.startsWith('minecraft:')&&!DIMENSIONS.some(([id])=>id===o.target))fail(label,'Dimension vanilla inconnue : '+o.target);
      if(Object.hasOwn(o,'conditions'))validateCondition(o.conditions,label);
    }
    if(Object.hasOwn(q,'requires'))validateCondition(q.requires,label,true);
    validateRewards(q.rewards,label);
    if(Object.hasOwn(q,'cooldownSeconds')&&(!Number.isInteger(q.cooldownSeconds)||q.cooldownSeconds<0||q.cooldownSeconds>31536000))fail(label,'Délai invalide');
    if(Object.hasOwn(q,'timeLimitSeconds')&&(!Number.isInteger(q.timeLimitSeconds)||q.timeLimitSeconds<1||q.timeLimitSeconds>31536000))fail(label,'Limite de temps invalide');
    if(Object.hasOwn(q,'noDeath')&&typeof q.noDeath!=='boolean')fail(label,'Contrainte sans mort invalide');
    if(Object.hasOwn(q,'hidden')&&typeof q.hidden!=='boolean')fail(label,'Visibilité invalide');
    if(Object.hasOwn(q,'afterLast')&&(q.progression!=='tiered'||!['stop','repeat'].includes(q.afterLast)))fail(label,'Action après le dernier palier invalide');
    if(q.progression==='tiered'&&(!Array.isArray(q.tiers)||!q.tiers.length))fail(label,'Paliers requis');
    if(Object.hasOwn(q,'tiers')){
      if(q.progression!=='tiered'||!Array.isArray(q.tiers)||q.tiers.length>256)fail(label,'Liste de paliers invalide');
      if(Array.isArray(q.tiers)){
        const ids=new Set();let previous=0;
        for(const tier of q.tiers){
          if(!tier||typeof tier!=='object'||Array.isArray(tier)){fail(label,'Palier invalide');continue;}
          if(!LOCAL.test(tier.id||'')||ids.has(tier.id))fail(label,'ID de palier invalide ou dupliqué');
          ids.add(tier.id);
          if(!Number.isInteger(tier.threshold)||tier.threshold<1||tier.threshold>1000000000||tier.threshold<=previous)fail(label,'Seuils de paliers : entiers strictement croissants requis');
          if((Array.isArray(q.objectives)?q.objectives:[]).some(o=>Number.isInteger(o?.count)&&Number.isInteger(tier.threshold)&&o.count*tier.threshold>1000000000))fail(label,'Quantité d’objectif trop grande pour ce palier');
          previous=tier.threshold;
          validateRewards(tier.rewards,label);
        }
      }
    }
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
/** Export-only per-category deduplication. Studio never edits pooled references. */
export function compactCategoryImages(category){
  const result=structuredClone(category),pool=[],indexes=new Map();
  for(const note of result.annotations||[]){
    for(const parts of Object.values(note.rich||{})){
      if(!Array.isArray(parts))continue;
      for(const part of parts){
        if(part?.type!=='image'||typeof part.data!=='string')continue;
        let ref=indexes.get(part.data);
        if(ref===undefined){ref=pool.length;pool.push(part.data);indexes.set(part.data,ref);}
        delete part.data;
        part.ref=ref;
      }
    }
  }
  if(pool.length)result.images=pool;
  else delete result.images;
  return result;
}
/** Accept both legacy inline data URIs and compact exports; reject broken references. */
export function expandCategoryImages(category){
  if(!category.images)return category;
  const result=structuredClone(category),pool=result.images;
  if(!Array.isArray(pool))throw Error('Invalid category image pool');
  for(const note of result.annotations||[])for(const parts of Object.values(note.rich||{})){
    if(!Array.isArray(parts))continue;
    for(const part of parts){
      if(part?.type!=='image'||part.ref===undefined)continue;
      if(!Number.isSafeInteger(part.ref)||part.ref<0||part.ref>=pool.length||!mediaPattern.test(pool[part.ref]))
        throw Error('Invalid image reference '+part.ref+' in '+category.id);
      if(part.data!==undefined)throw Error('Ambiguous image data/ref in '+category.id);
      part.data=pool[part.ref];
      delete part.ref;
    }
  }
  delete result.images;
  return result;
}
export function fileMap(p){
  const map=new Map(), used=new Set();
  function put(path,obj){if(used.has(path))throw new Error('Fichier dupliqué : '+path);used.add(path);map.set(path,(path.endsWith('.json5')?JSON5.stringify(obj,null,2):JSON.stringify(obj,null,2))+'\n');}
  const fileName=id=>{const [ns,...rest]=id.split(':');const path=rest.join(':');if(!ID.test(id))throw new Error('ID invalide: '+id);return ns==='questplus'?path:ns+'/'+path;};
  put('config.json',p.config||{schemaVersion:1,autosaveSeconds:5});
  // Studio-only metadata is separate: QuestRegistry must never see unknown fields
  // on quest/category/config JSON objects.
  put('queststudio-compatibility.json',{
    format:1,targetMinecraftVersion:p.targetMinecraftVersion||'26.3',
    iconVersions:p.iconVersions||{},editorLocales:p.editorLocales||[]
  });
  for(const c of p.categories)put('categories/'+fileName(c.id)+'.json',compactCategoryImages(c));
  for(const q of p.quests)put('quests/'+fileName(q.id)+'.json5',q);
  map.set('index.txt',[...map.keys()].join('\n')+'\n');
  return map;
}
export function importDocuments(documents){
  const p=emptyProject();let valid=0;
  for(const {name,content} of documents){
    const path=String(name).replace(/\\/g,'/').replace(/^.*?(?=(?:quests|categories)\/)/,'');
    if(!/\.json5?$/i.test(path))continue;
    let obj;try{obj=path.toLowerCase().endsWith('.json5')?JSON5.parse(String(content).replace(/^\uFEFF/,'')):JSON.parse(String(content).replace(/^\uFEFF/,''));}catch(e){throw Error('JSON/JSON5 invalide dans '+name+' : '+e.message);}
    if(/(?:^|\/)queststudio-compatibility\.json$/i.test(path)){
      p.targetMinecraftVersion=obj.targetMinecraftVersion||'26.3';
      p.iconVersions=obj.iconVersions||{};
      if(obj.editorLocales!==undefined&&(!Array.isArray(obj.editorLocales)||obj.editorLocales.length>128||obj.editorLocales.some(id=>typeof id!=='string'||!/^[a-z]{2,3}_[a-z]{2,4}$/.test(id))))throw Error('Invalid editor language list');
      p.editorLocales=obj.editorLocales||[];continue;
    }
    if(/(?:^|\/)categories\//.test(path)||(!obj.schemaVersion&&obj.id&&obj.title&&!obj.objectives))p.categories.push(expandCategoryImages(obj));
    else if(/(?:^|\/)quests\//.test(path)||obj.objectives&&obj.id)p.quests.push(obj);
    else if(/(?:^|\/)config\.json$/i.test(path))p.config=obj;
    else continue;
    valid++;
  }
  if(!valid)throw Error('Aucune catégorie ou quête trouvée. Choisissez un dossier ou ZIP QuestPlus.');
  return p;
}
