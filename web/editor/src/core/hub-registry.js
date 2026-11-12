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
	    const fields = e.fields.map((field) => {
        if (!field || !/^[a-z][a-zA-Z0-9_]{0,39}$/.test(field.key)
                || !["string", "integer", "number", "boolean", "choice", "item", "block", "entity",
                    "biome", "dimension", "enchantment", "fluid", "structure", "statistic",
                    "advancement", "effect"].includes(field.type)
                || typeof field.label !== "string" || !field.label || field.label.length > 80)
            return null;
        const allowed = new Set(["key", "type", "label", "default", "min", "max", "options"]);
        if (Object.keys(field).some((key) => !allowed.has(key)))
            return null;
        const numeric = field.type === "integer" || field.type === "number";
        if ((field.min !== undefined || field.max !== undefined) && !numeric)
            return null;
        for (const bound of ["min", "max"])
            if (field[bound] !== undefined && (!Number.isFinite(field[bound])
                || (field.type === "integer" && !Number.isInteger(field[bound]))))
                return null;
        if (field.min !== undefined && field.max !== undefined && field.min > field.max)
            return null;
        const choice = field.type === "choice";
        if (choice !== (field.options !== undefined)) return null;
        if (choice && (!Array.isArray(field.options) || field.options.length < 1 || field.options.length > 30
                || field.options.some((v) => typeof v !== "string" || !v || v.length > 120)
                || new Set(field.options).size !== field.options.length)) return null;
        const value = field.default;
        if (value !== undefined) {
            const validDefault = field.type === "boolean" ? typeof value === "boolean"
                : numeric ? Number.isFinite(value) && (field.type !== "integer" || Number.isInteger(value))
                    && (field.min === undefined || value >= field.min)
                    && (field.max === undefined || value <= field.max)
                : choice ? field.options.includes(value)
                : field.type === "string" ? typeof value === "string" && value.length <= 256
                : typeof value === "string" && valid(value);
            if (!validDefault) return null;
        }
        return { ...field };
    });
    if (fields.every(Boolean) && new Set(fields.map((f) => f.key)).size === fields.length
            && (e.pool === undefined || typeof e.pool === "boolean"))
        output.extensions.push({ kind: e.kind, id: e.id, label: e.label, fields,
            ...(e.kind === "reward" && e.pool === true ? { pool: true } : {}) });
	}
	output.questplusVersion = typeof value?.questplusVersion === "string" && /^\d+\.\d+\.\d+/.test(value.questplusVersion) ? value.questplusVersion : "";
	current = output;
	return output;
}
export const CONFIG_KEY_SINCE = {
	schemaVersion: "1.0.0",
	autosaveSeconds: "1.0.0",
	teamRewardPolicy: "1.0.0",
	enableCommandRewards: "1.0.0",
	dropLootCrates: "1.0.0",
	crateNoDrop: "1.0.0",
	allowDuplicateTeamNames: "1.0.0",
	npcTurnIn: "1.1.3",
	npcs: "1.1.3",
	crateBosses: "1.1.5",
	waitForInventorySpace: "1.1.7",
};
export function serverVersion(loadedConfig) {
	if (current.questplusVersion) return current.questplusVersion;
	let floor = "1.0.0";
	for (const key of Object.keys(loadedConfig || {})) if (CONFIG_KEY_SINCE[key] && !hubAtLeast(CONFIG_KEY_SINCE[key], floor)) floor = CONFIG_KEY_SINCE[key];
	return floor;
}
export const unsupportedConfigKeys = (config, version) => Object.keys(config || {}).filter((key) => CONFIG_KEY_SINCE[key] && !hubAtLeast(CONFIG_KEY_SINCE[key], version));
export function hubAtLeast(min, version = current.questplusVersion) {
	if (!version) return false;
	const have = version.split(/[^\d.]/)[0].split(".").map(Number),
		need = min.split(".").map(Number);
	for (let i = 0; i < need.length; i++) if ((have[i] || 0) !== need[i]) return (have[i] || 0) > need[i];
	return true;
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

export function hubEditorDescriptors(kind) {
	return (current.extensions || []).filter((d) => d.kind === kind);
}

export function hubFieldDefault(field) {
    if (field.default !== undefined) return field.default;
    if (field.type === "boolean") return false;
    if (field.type === "integer" || field.type === "number") return field.min ?? 0;
    if (field.type === "choice") return field.options[0];
    if (field.type === "string") return "";
    return field.type === "dimension" ? "minecraft:overworld" : "minecraft:stone";
}
