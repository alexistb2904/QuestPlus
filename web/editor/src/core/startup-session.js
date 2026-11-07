/** Browser draft detection is shared by startup and the React state hook. */
export const STUDIO_STORAGE_KEY = "questplus.quest-studio.v1";

export function readStoredStudioSession(storage = globalThis.localStorage) {
	try {
		const saved = JSON.parse(storage?.getItem(STUDIO_STORAGE_KEY) || "null");
		if (saved && Array.isArray(saved.project?.categories) && Array.isArray(saved.project?.quests)) return saved;
	} catch {}
	return null;
}
