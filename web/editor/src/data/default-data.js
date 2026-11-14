import CHALLENGES from './challenge-catalogue.json' with { type: 'json' };
import EQUIPMENT from './equipment-catalogue.json' with { type: 'json' };
import EXPLORERS from './explorers-catalogue.json' with { type: 'json' };

export const BUILTIN_PROJECT = {
	categories: [...CHALLENGES.categories, ...EQUIPMENT.categories, ...EXPLORERS.categories],
	quests: [...CHALLENGES.quests, ...EQUIPMENT.quests, ...EXPLORERS.quests],
	config: { schemaVersion: 1, autosaveSeconds: 5, npcs: EXPLORERS.npcs },
	targetMinecraftVersion: "26.3",
	editorLocales: ["en_us", "fr_fr", "es_es", "zh_cn"],
};
