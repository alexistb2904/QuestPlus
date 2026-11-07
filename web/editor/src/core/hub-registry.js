// Session-scoped, never persistent: the connected server is the authority for
// modded registry IDs and bounded previews. No auth tokens or player state are stored.
const keys = ["items", "blocks", "fluids", "entities", "effects", "enchantments", "biomes", "structures", "dimensions", "statistics", "advancements"];
let current = {};
const valid = (id) => /^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(id);
export function setHubRegistry(value) {
	const output = {};
	for (const key of keys) {
		const list = Array.isArray(value?.[key]) ? value[key] : [];
		output[key] = [...new Set(list.filter((id) => typeof id === "string" && valid(id)))].sort();
	}
	output.tags = {};
	for (const key of ["items", "blocks", "entities"]) {
		const list = Array.isArray(value?.tags?.[key]) ? value.tags[key] : [];
		output.tags[key] = [...new Set(list.filter((id) => typeof id === "string" && id.startsWith("#") && valid(id.slice(1))))].sort();
	}
	const known = new Set(keys.flatMap((key) => output[key]));
	output.names = {};
	output.textures = {};
	for (const [id, locales] of Object.entries(value?.assets?.names || {})) {
		if (!known.has(id) || !locales || typeof locales !== "object" || Array.isArray(locales)) continue;
		const names = {};
		for (const locale of ["en_us", "fr_fr", "es_es", "zh_cn"]) if (typeof locales[locale] === "string" && locales[locale].length <= 160) names[locale] = locales[locale];
		if (Object.keys(names).length) output.names[id] = names;
	}
	for (const [id, data] of Object.entries(value?.assets?.textures || {}))
		if (known.has(id) && typeof data === "string" && data.length <= 32022 && /^data:image\/png;base64,[A-Za-z0-9+/]+={0,2}$/.test(data)) output.textures[id] = data;
	output.assetsLimited = value?.assets?.limited === true;
	output.extensions = [];
	for (const e of Array.isArray(value?.extensions) ? value.extensions.slice(0, 128) : []) {
		if (
			!e ||
			!["event", "condition", "reward"].includes(e.kind) ||
			!valid(e.id) ||
			typeof e.label !== "string" ||
			e.label.length > 80 ||
			!Array.isArray(e.fields) ||
			e.fields.length > 20
		)
			continue;
		const fields = e.fields
			.filter(
				(f) =>
					f &&
					/^[a-z][a-zA-Z0-9_]{0,39}$/.test(f.key) &&
					[
						"string",
						"integer",
						"boolean",
						"item",
						"block",
						"entity",
						"biome",
						"dimension",
						"enchantment",
						"fluid",
						"structure",
						"statistic",
						"advancement",
						"effect",
					].includes(f.type) &&
					typeof f.label === "string" &&
					f.label.length <= 80
			)
			.map(({ key, type, label }) => ({ key, type, label }));
		if (fields.length === e.fields.length && (e.pool === undefined || typeof e.pool === "boolean"))
			output.extensions.push({ kind: e.kind, id: e.id, label: e.label, fields, ...(e.kind === "reward" && e.pool === true ? { pool: true } : {}) });
	}
	current = output;
	return output;
}
export function hubRegistry() {
	return current;
}
export function hubNames(id) {
	return Object.values(current.names?.[id] || {});
}
export function hubLabel(id, locale = "en") {
	const names = current.names?.[id] || {};
	return names[{ en: "en_us", fr: "fr_fr", es: "es_es", zh: "zh_cn" }[locale]] || names.en_us || id.split(":").pop().replace(/_/g, " ");
}
export function hubTexture(id) {
	return current.textures?.[id] || "";
}
export function hubTargets(kind) {
	const map = {
		item: "items",
		block: "blocks",
		fluid: "fluids",
		entity: "entities",
		biome: "biomes",
		structure: "structures",
		enchantment: "enchantments",
		effect: "effects",
		dimension: "dimensions",
		statistic: "statistics",
		advancement: "advancements",
	};
	return current[map[kind] || kind] || [];
}

/** Authoring-only metadata; gameplay events and validation remain server-owned. */
export function hubEditorDescriptors(kind) {
	return (current.extensions || []).filter((d) => d.kind === kind);
}
