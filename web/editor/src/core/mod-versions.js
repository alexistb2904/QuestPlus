import { CONFIG_KEY_SINCE, hubAtLeast } from "./hub-registry.js";

/**
 * QuestPlus releases a pack can target, newest first (the footer select). A new release goes first,
 * and gets an entry below when it changes what a pack may contain. studio-ux.test.mjs checks gradle mod_version.
 */
export const MOD_VERSIONS = ["1.1.8", "1.1.7", "1.1.6", "1.1.5", "1.1.4", "1.1.3"];
/** Pack features that need a minimum QuestPlus version. Config keys are in CONFIG_KEY_SINCE. */
export const MOD_FEATURES = { descriptionLinks: "1.1.8" };
/**
 * Objective events retired by a release. Import converts them for every target, because the
 * replacement also works on older versions. The conversion itself is migrateObjective in project.js.
 */
export const RETIRED_EVENTS = { "questplus:fish_any": "1.1.8" };

export const modSupports = (feature, version) => hubAtLeast(MOD_FEATURES[feature], version);
/** The newest listed release not newer than the given one. No version means the latest. */
export function closestModVersion(version) {
	if (!/^\d+\.\d+\.\d+/.test(version || "")) return MOD_VERSIONS[0];
	return MOD_VERSIONS.find((release) => hubAtLeast(release, version)) || MOD_VERSIONS[MOD_VERSIONS.length - 1];
}
export const targetModVersion = (project) => closestModVersion(project?.targetModVersion);
/** Config keys the target version does not accept: an older server would reject the whole pack. */
export const configKeysTooNew = (project) =>
	Object.keys(project?.config || {}).filter((key) => CONFIG_KEY_SINCE[key] && !hubAtLeast(CONFIG_KEY_SINCE[key], targetModVersion(project)));
/** What the pack loses on its target version. Shown next to the select, never blocking. */
export function modVersionNotes(project) {
	const notes = [];
	if (!modSupports("descriptionLinks", targetModVersion(project)) && (project?.quests || []).some((q) => /https:\/\//.test(JSON.stringify(q.description || {}))))
		notes.push("Links in quest descriptions stay plain text on this QuestPlus version.");
	return notes;
}
