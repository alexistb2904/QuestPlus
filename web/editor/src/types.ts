export type LocaleMap=Record<string,string>;
export type Position={x:number;y:number};
export type RichPart={type:'text';text:string;bold?:boolean;italic?:boolean;underline?:boolean;strike?:boolean;font?:string;size?:number;sizePx?:number;color?:string;highlight?:string}|{type:'image';data:string;width:number;height:number};
export type Annotation={align?:'left'|'center'|'right';id:string;x:number;y:number;text:LocaleMap;style?:'note'|'heading'|'label'|'background';color?:string;width?:number;height?:number;rich?:Record<string,RichPart[]>};
export type LinkRoute={from:string;to:string;points:Position[]};
export type Category={id:string;order?:number;icon:string;title:LocaleMap;description?:LocaleMap;annotations?:Annotation[];linkRoutes?:LinkRoute[]};
export type Objective={id:string;event:string;target:string;count:number;text?:LocaleMap;conditions?:any;optional?:boolean};
export type Reward={type:string;item?:string;count?:number;amount?:number;[key:string]:any};
export type Quest={id:string;schemaVersion:number;category:string;icon:string;size?:number;title:LocaleMap;description?:LocaleMap;descriptionRich?:Record<string,RichPart[]>;objectives:Objective[];requires?:any;rewards:Reward[];progression:string;layout?:Position;kind?:string;acknowledgement?:string;narrative?:LocaleMap;hidden?:boolean;objectiveMode?:string;[key:string]:any};
export type Project={categories:Category[];quests:Quest[];config:any;targetMinecraftVersion?:string;iconVersions?:Record<string,string>;editorLocales?:string[]};
export type Selection={type:'category'|'quest'|'annotation';id:string};
export type Camera={x:number;y:number;zoom:number};
export const supportedLocales:Record<string,string>={en_us:'English (US)',fr_fr:'Français',en_gb:'English (UK)',es_es:'Español',es_mx:'Español (MX)',de_de:'Deutsch',ja_jp:'日本語',zh_cn:'简体中文',zh_tw:'繁體中文',pt_br:'Português',it_it:'Italiano',ko_kr:'한국어'};
export function localesOf(project:Project){
 const codes=new Set(['en_us',...(project.editorLocales||[])]);
 const visit=(o:any)=>{if(!o||typeof o!=='object')return;for(const [k,v] of Object.entries(o)){if(/^[a-z]{2,3}_[a-z]{2,4}$/.test(k)&&(typeof v==='string'||Array.isArray(v)&&v.length>0))codes.add(k);else if(typeof v==='object')visit(v);}};
 project.categories.forEach(visit);project.quests.forEach(visit);
 return [...codes].sort((a,b)=>a==='en_us'?-1:b==='en_us'?1:a.localeCompare(b));
}
