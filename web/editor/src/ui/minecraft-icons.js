import { BLOCK_IDS, ITEM_IDS, VERSION } from "../data/vanilla-registry-26.3.js";
import texturePaths from "../data/vanilla-textures-26.3.json" with { type: "json" };
export const MC_VERSION = VERSION;
export const COMMON_ITEMS = ITEM_IDS;
const itemSet = new Set(ITEM_IDS),
	blockSet = new Set(BLOCK_IDS);
const names = [...new Set([...ITEM_IDS, ...BLOCK_IDS])].sort((a, b) => a.localeCompare(b));
const catalog = names.map((name) => ({
	id: "minecraft:" + name,
	label: name.replace(/[_/]/g, " "),
	kind: itemSet.has(name) && blockSet.has(name) ? "both" : blockSet.has(name) ? "block" : "item",
	version: VERSION,
}));
export const isVanilla = (id) => /^minecraft:[a-z0-9_./-]+$/.test(String(id));
export const vanillaCatalog = () => catalog;
export async function loadVanillaIcons() {
	return catalog;
}
export const itemExists = (id) => isVanilla(id) && itemSet.has(id.slice("minecraft:".length));
export const blockExists = (id) => isVanilla(id) && blockSet.has(id.slice("minecraft:".length));
export const inVanillaRegistry = (id) => itemExists(id) || blockExists(id);
export const knownVersion = (id) => (inVanillaRegistry(id) ? VERSION : null);
export function iconUrl(id, version = VERSION) {
	if (!isVanilla(id)) return "";
	const path = texturePaths[id.slice("minecraft:".length)];
	return path ? "https://assets.mcasset.cloud/" + encodeURIComponent(version) + "/assets/minecraft/textures/" + path + ".png" : "";
}
export function blockIconUrl(id, version = VERSION) {
	return iconUrl(id, version);
}
export function imageCandidates(id, version = VERSION) {
	if (!isVanilla(id)) return [];
	const url = iconUrl(id, version);
	return url ? [url] : [];
}
export const versionCompare = (a, b) => {
	const parse = (s) =>
		String(s || VERSION)
			.split(".")
			.map((n) => Number.parseInt(n, 10) || 0);
	const A = parse(a),
		B = parse(b);
	for (let i = 0; i < Math.max(A.length, B.length); i++) {
		if ((A[i] || 0) !== (B[i] || 0)) return (A[i] || 0) - (B[i] || 0);
	}
	return 0;
};
export const requiredMinecraftVersion = (project, id) => project?.iconVersions?.[id] || knownVersion(id) || null;
export const versionWarning = (project, id) => {
	const required = requiredMinecraftVersion(project, id),
		target = project?.targetMinecraftVersion || VERSION;
	if (required && versionCompare(required, target) > 0) return `⚠ ${id}: Minecraft ${required} required (pack: ${target})`;
	if (isVanilla(id) && blockExists(id) && !itemExists(id)) return `⚠ ${id}: block without an item form. Fallback icon in-game`;
	if (isVanilla(id) && !inVanillaRegistry(id) && !project?.iconVersions?.[id]) return `⚠ ${id}: not in Minecraft ${VERSION} registry. Icon may be missing`;
	return null;
};
