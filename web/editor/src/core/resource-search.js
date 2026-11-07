import labels from "../data/minecraft-search-labels-26.3.json" with { type: "json" };
import { hubNames, hubRegistry } from "./hub-registry.js";

// A registry replacement drops its index with GC; labels can vary with the UI locale.
const indexes = new WeakMap();
function searchable(id, label) {
	const registry = hubRegistry();
	let cache = indexes.get(registry);
	if (!cache) {
		cache = new Map();
		indexes.set(registry, cache);
	}
	const key = label ? id + "\0" + label : id;
	let entry = cache.get(key);
	if (!entry) {
		const value = normalize([id, label, ...(labels[id.replace(/^#/, "")] || []), ...hubNames(id.replace(/^#/, ""))].join(" "));
		entry = { value, tokens: words(value) };
		if (cache.size >= 100000) cache.delete(cache.keys().next().value);
		cache.set(key, entry);
	}
	return entry;
}

// Vanilla names come from the installed Minecraft 26.3 language assets (fr/es/zh).
const normalize = (value) =>
	String(value)
		.normalize("NFD")
		.replace(/[\u0300-\u036f]/g, "")
		.toLowerCase();
const synonyms = [
	["wood", "bois", "madera", "木头"],
	["stone", "pierre", "piedra", "石头"],
	["iron", "fer", "hierro", "铁"],
	["gold", "or", "oro", "金"],
	["diamond", "diamant", "diamante", "钻石"],
	["water", "eau", "agua", "水"],
	["sword", "epee", "espada", "剑"],
	["egg", "oeuf", "huevo", "蛋"],
	["spawn", "apparition", "generateur", "generador", "刷怪"],
];
const words = (value) =>
	normalize(value)
		.split(/[^\p{L}\p{N}]+/u)
		.filter(Boolean);
function near(a, b) {
	if (a.length < 4 || Math.abs(a.length - b.length) > 1) return false;
	if (a.length === b.length) {
		const differences = [];
		for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) differences.push(i);
		if (differences.length === 2) {
			const [i, j] = differences;
			return j === i + 1 && a[i] === b[j] && a[j] === b[i];
		}
	}
	let i = 0,
		j = 0,
		errors = 0;
	while (i < a.length && j < b.length) {
		if (a[i] === b[j]) {
			i++;
			j++;
			continue;
		}
		if (++errors > 1) return false;
		if (a.length >= b.length) i++;
		if (b.length >= a.length) j++;
	}
	return errors + (i < a.length || j < b.length ? 1 : 0) <= 1;
}
export function resourceMatcher(query) {
	const terms = words(query);
	const expanded = terms.map((term) => {
		const group = synonyms.find((g) => g.some((word) => word === term || near(term, word)));
		if (group === synonyms[0])
			return [...group, "log", "plank", "stem", "hyphae", "oak", "spruce", "birch", "jungle", "acacia", "cherry", "mangrove", "bamboo", "crimson", "warped"];
		return group || [term];
	});
	return (id, label = "") => {
		if (!terms.length) return true;
		const { value, tokens } = searchable(id, label);
		return expanded.every((options) =>
			options.some((option) => (option.length < 3 ? tokens.includes(option) : value.includes(option)) || tokens.some((token) => near(option, token)))
		);
	};
}
