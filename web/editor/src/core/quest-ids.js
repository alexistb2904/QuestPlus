/** Stable Minecraft resource IDs generated from the English title. */
export function titleSlug(title) {
	const normalized = String(title ?? "")
		.normalize("NFKD")
		.replace(/[\u0300-\u036f]/g, "")
		.toLowerCase()
		.replace(/[^a-z0-9]+/g, "-")
		.replace(/^-+|-+$/g, "");
	return normalized;
}
export function uniqueId(base, ids, { random = () => globalThis.crypto?.randomUUID?.() || Math.random().toString(36).slice(2) } = {}) {
	const existing = ids instanceof Set ? ids : new Set(ids);
	const seed = base.replace(/\/+$/, "");
	const legal = /^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(seed) ? seed : null;
	if (legal) {
		if (!existing.has(legal)) return legal;
		for (let n = 2; n < 100000; n++) if (!existing.has(legal + "-" + n)) return legal + "-" + n;
	}
	for (let n = 0; n < 1000; n++) {
		const candidate =
			"questplus:quest-" +
			String(random())
				.replace(/[^a-z0-9]/g, "")
				.slice(0, 16);
		if (!existing.has(candidate) && /^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(candidate)) return candidate;
	}
	throw Error("Cannot create a unique quest identifier");
}
export function questId(categoryId, title, ids, opts = {}) {
	const [ns, group = "quests"] = String(categoryId || "questplus:quests").split(":");
	const base = titleSlug(title);
	return uniqueId(base ? `${ns}:${group.replace(/\/$/, "")}/${base}` : "", ids, opts);
}
export function categoryId(title, ids, opts = {}) {
	const base = titleSlug(title);
	return uniqueId(base ? "questplus:" + base : "", ids, opts);
}
