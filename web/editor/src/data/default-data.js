// Snapshot of QuestPlus src/main/resources/defaults/ (Minecraft 26.3).
// Keep in sync with shipped definitions. Source: main/defaults/*.json.
import CHALLENGES from './challenge-catalogue.json' with { type: 'json' };
import EQUIPMENT from './equipment-catalogue.json' with { type: 'json' };

// Same files are embedded into the mod JAR by processResources.
export const BUILTIN_PROJECT = {
  categories: [...CHALLENGES.categories, ...EQUIPMENT.categories],
  quests: [...CHALLENGES.quests, ...EQUIPMENT.quests],
  config: {schemaVersion:1,autosaveSeconds:5},
  targetMinecraftVersion: '26.3',
  editorLocales: ['en_us', 'fr_fr']
};
