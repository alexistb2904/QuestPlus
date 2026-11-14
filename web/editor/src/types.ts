export type LocaleMap = Record<string, string>;
export type Position = { x: number; y: number };
export type RichPart =
	| {
			type: "text";
			text: string;
			bold?: boolean;
			italic?: boolean;
			underline?: boolean;
			strike?: boolean;
			font?: string;
			size?: number;
			sizePx?: number;
			color?: string;
			highlight?: string;
	  }
	| { type: "image"; data: string; width: number; height: number };
export type Annotation = {
	align?: "left" | "center" | "right";
	id: string;
	x: number;
	y: number;
	text: LocaleMap;
	style?: "note" | "heading" | "label" | "background";
	color?: string;
	width?: number;
	height?: number;
	rich?: Record<string, RichPart[]>;
};
export type LinkRoute = { from: string; to: string; points: Position[] };
export type Category = {
	id: string;
	group?: string;
	order?: number;
	icon: string;
	title: LocaleMap;
	description?: LocaleMap;
	annotations?: Annotation[];
	linkRoutes?: LinkRoute[];
};
export type Objective = { id: string; event: string; target: string; count: number; text?: LocaleMap; conditions?: any; optional?: boolean; data?: Record<string, unknown> };
export type Reward = { type: string; item?: string; count?: number; amount?: number; [key: string]: any };
export type Quest = {
	id: string;
	schemaVersion: number;
	category: string;
	icon: string;
	size?: number;
	title: LocaleMap;
	description?: LocaleMap;
	descriptionRich?: Record<string, RichPart[]>;
	objectives: Objective[];
	requires?: any;
	rewards: Reward[];
	progression: string;
	layout?: Position;
	kind?: string;
	acknowledgement?: string;
	narrative?: LocaleMap;
	hidden?: boolean;
	objectiveMode?: string;
	[key: string]: any;
};
export type Project = {
	ftbMappings?: { sourceQuest: string; targetQuest: string; tasks: { sourceId: string; objectiveId: string }[]; rewards: { sourceId: string | null; index: number }[] }[];
	questTemplates?: { id: string; label: string; quest: Partial<Quest> }[];
	rewardTables?: {
		name: string;
		reward: Reward;
		crate?: boolean;
		crateName?: string;
		crateColor?: string;
		crateGlow?: boolean;
		crateDrops?: { passive: number; monster: number; boss: number };
	}[];
	categories: Category[];
	quests: Quest[];
	config: any;
	targetMinecraftVersion?: string;
	targetModVersion?: string;
	iconVersions?: Record<string, string>;
	editorLocales?: string[];
};
export type Selection = { type: "category" | "quest" | "annotation"; id: string };
export type Camera = { x: number; y: number; zoom: number };
export const supportedLocales: Record<string, string> = {
	en_us: "English (US)",
	fr_fr: "Français",
	en_gb: "English (UK)",
	es_es: "Español (España)",
	es_mx: "Español (México)",
	de_de: "Deutsch",
	ja_jp: "日本語",
	zh_cn: "简体中文",
	zh_tw: "繁體中文",
	pt_br: "Português (Brasil)",
	pt_pt: "Português (Portugal)",
	it_it: "Italiano",
	ko_kr: "한국어",
	ru_ru: "Русский",
	uk_ua: "Українська",
	pl_pl: "Polski",
	nl_nl: "Nederlands",
	tr_tr: "Türkçe",
	ar_sa: "العربية",
	cs_cz: "Čeština",
	da_dk: "Dansk",
	el_gr: "Ελληνικά",
	fi_fi: "Suomi",
	hu_hu: "Magyar",
	id_id: "Bahasa Indonesia",
	nb_no: "Norsk bokmål",
	ro_ro: "Română",
	sk_sk: "Slovenčina",
	sv_se: "Svenska",
	th_th: "ไทย",
	vi_vn: "Tiếng Việt",
	he_il: "עברית",
	hi_in: "हिन्दी",
	ca_es: "Català",
	es_ar: "Español (Argentina)",
	es_cl: "Español (Chile)",
	es_uy: "Español (Uruguay)",
	es_ve: "Español (Venezuela)",
	en_au: "English (Australia)",
	en_ca: "English (Canada)",
	fr_ca: "Français (Canada)",
	zh_hk: "繁體中文 (香港)",
	bg_bg: "Български",
	hr_hr: "Hrvatski",
	lt_lt: "Lietuvių",
	lv_lv: "Latviešu",
	sl_si: "Slovenščina",
	sr_sp: "Српски",
	et_ee: "Eesti",
};
export function localesOf(project: Project) {
	const codes = new Set(["en_us", ...(project.editorLocales || [])]);
	const visit = (o: any) => {
		if (!o || typeof o !== "object") return;
		for (const [k, v] of Object.entries(o)) {
			if (/^[a-z]{2,3}_[a-z]{2,4}$/.test(k) && (typeof v === "string" || (Array.isArray(v) && v.length > 0))) codes.add(k);
			else if (typeof v === "object") visit(v);
		}
	};
	project.categories.forEach(visit);
	project.quests.forEach(visit);
	return [...codes].sort((a, b) => (a === "en_us" ? -1 : b === "en_us" ? 1 : a.localeCompare(b)));
}
