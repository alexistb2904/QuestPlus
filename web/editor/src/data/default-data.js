// Shared source of the default QuestPlus catalogue (Minecraft 26.3).
import CHALLENGES from './challenge-catalogue.json' with { type: 'json' };
import EQUIPMENT from './equipment-catalogue.json' with { type: 'json' };
import EXPLORERS from './explorers-catalogue.json' with { type: 'json' };

// Same files are embedded into the mod JAR by processResources.
export const BUILTIN_PROJECT = {
	categories: [...CHALLENGES.categories, ...EQUIPMENT.categories, ...EXPLORERS.categories],
	quests: [...CHALLENGES.quests, ...EQUIPMENT.quests, ...EXPLORERS.quests],
	// The Explorers' quest givers; the server merges the same list into a new world's config.json.
	config: { schemaVersion: 1, autosaveSeconds: 5, npcs: EXPLORERS.npcs },
	targetMinecraftVersion: "26.3",
	editorLocales: ["en_us", "fr_fr"],
};
