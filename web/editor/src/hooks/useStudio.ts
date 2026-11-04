import { useEffect, useRef, useState } from "react";
import {
	demoProject,
	emptyProject,
	category as newCategory,
	quest as newQuest,
	annotation as newAnnotation,
	copy,
	validate,
	fileMap,
	importDocuments,
	text,
	references,
	PROGRESSIONS,
    orderedCategories,
} from "../core/project.js";
import { questId, categoryId } from "../core/quest-ids.js";
import { makeZip, readZip } from "../core/zip.js";
import { withPrerequisite } from "../core/quest-links.js";
import { fitEditor } from "../core/editor-geometry.js";
import { tUI, storedUiLocale } from "../ui/ui-i18n.js";
import { gameLayout } from "../core/game-layout.js";
import type { Dispatch, SetStateAction } from "react";
import type { Project, Selection, Camera, Quest, Category } from "../types";
export type Session = { project: Project; categoryId: string | null; selection: Selection | null; camera: Camera; language: string };
const KEY = "questplus.quest-studio.v1";
const initial = (): Session => {
	const p = demoProject() as Project;
	const fallback = {
		project: p,
		categoryId: orderedCategories(p.categories)[0]?.id || null,
		selection: { type: "quest", id: p.quests.find((q) => q.category === orderedCategories(p.categories)[0]?.id)?.id || "" } as Selection,
		camera: { x: 0, y: 30, zoom: 1 },
		language: "en_us",
	};
	try {
		const saved = JSON.parse(localStorage.getItem(KEY) || "null");
		if (saved?.project?.categories && saved?.project?.quests) return { ...fallback, ...saved };
	} catch {}
	return fallback;
};
export function owner(project: Project, selection: Selection | null): any {
	if (!selection) return null;
	if (selection.type === "quest") return project.quests.find((q) => q.id === selection.id);
	if (selection.type === "category") return project.categories.find((q) => q.id === selection.id);
	return project.categories.flatMap((c) => c.annotations || []).find((n) => n.id === selection.id);
}
export function useStudio() {
	const [state, commitState] = useState<Session>(initial),
		[notice, setNotice] = useState("");
	const latest = useRef(state);
	// Run state updaters once: history mutations must not be replayed by React StrictMode.
	const setState: Dispatch<SetStateAction<Session>> = (update) => {
		const next = typeof update === "function" ? update(latest.current) : update;
		latest.current = next;
		commitState(next);
	};
	const [saveStatus, setSaveStatus] = useState<"saving" | "saved" | "error">("saving");
	const history = useRef<Session[]>([]),
		future = useRef<Session[]>([]),
		last = useRef({ field: "", time: 0 }),
		warned = useRef(false);
	const toast = (msg: string) => {
		setNotice(msg);
		window.setTimeout(() => setNotice((v) => (v === msg ? "" : v)), 3600);
	};
	function save() {
		try {
			localStorage.setItem(KEY, JSON.stringify(latest.current));
			warned.current = false;
			setSaveStatus("saved");
			return true;
		} catch {
			setSaveStatus("error");
			if (!warned.current) {
				warned.current = true;
				toast(tUI("Storage is full. Export your project as ZIP."));
			}
			return false;
		}
	}
	useEffect(() => {
		setSaveStatus("saving");
		const timer = window.setTimeout(save, 500);
		return () => clearTimeout(timer);
	}, [state]);
	function mutate(change: (s: Session) => void, field = "") {
		setState((prev) => {
			const next = { ...prev, project: copy(prev.project) };
			change(next);
			if (JSON.stringify(prev) === JSON.stringify(next)) return prev;
			const now = Date.now();
			if (!field || last.current.field !== field || now - last.current.time > 600) {
				history.current.push(copy(prev));
				if (history.current.length > 65) history.current.shift();
			}
			last.current = { field, time: now };
			future.current = [];
			return next;
		});
	}
	function undo() {
		const old = history.current.pop();
		if (!old) return;
		future.current.push(copy(state));
		setState(old);
		last.current.field = "";
	}
	function redo() {
		const next = future.current.pop();
		if (!next) return;
		history.current.push(copy(state));
		setState(next);
		last.current.field = "";
	}
	function setField(path: string, value: any) {
		mutate(
			(s) => {
				const o = owner(s.project, s.selection);
				if (!o) return;
				if (path === "category" && s.selection?.type === "quest") s.categoryId = value;
				if (s.selection?.type === "quest" && o.schemaVersion === 1 && ["noDeath", "objectiveMode", "timeLimitSeconds", "cooldownSeconds"].includes(path))
					o.schemaVersion = 2;
				if (path.startsWith("layout.") && s.selection?.type === "quest" && !o.layout)
					o.layout = { ...gameLayout(s.project.quests.filter((q) => q.category === o.category)).points.get(o.id) };
				if (path === "rich-and-text") {
					o.rich ||= {};
					o.text ||= {};
					o.rich[value.locale] = value.parts;
					o.text[value.locale] = value.plain;
					return;
				}
				if (path === "id" && s.selection?.type === "category") {
					const prior = o.id;
					o.id = value;
					s.project.quests.forEach((q) => {
						if (q.category === prior) q.category = value;
					});
					s.categoryId = value;
					s.selection = { type: "category", id: value };
					return;
				}
				const parts = path.split(".");
				let v = o,
					container = o;
				for (let i = 0; i < parts.length - 1; i++) {
					if (v[parts[i]] == null) v[parts[i]] = {};
					container = v;
					v = v[parts[i]];
				}
				const leaf = parts.at(-1)!;
				if (value === undefined || (/^[a-z]{2,3}_[a-z]{2,4}$/.test(leaf) && typeof value === "string" && !value.trim())) {
					delete v[leaf];
					if (!Object.keys(v).length && ["description", "narrative", "text"].includes(parts.at(-2)!)) delete container[parts.at(-2)!];
				} else v[leaf] = value;
			},
			(state.selection?.id || "") + path
		);
	}
	const currentCategory = state.project.categories.find((c) => c.id === state.categoryId);
	const quests = state.project.quests.filter((q) => q.category === state.categoryId);
	function fit(group = currentCategory, rows = quests) {
		const el = document.getElementById("viewport");
		return fitEditor(
			[...gameLayout(rows).points.values(), ...(group?.annotations || []).map((n) => ({ x: n.x, y: n.y }))],
			el?.clientWidth || 720,
			el?.clientHeight || 450
		) as Camera;
	}
	function selectCategory(id: string) {
		const cat = state.project.categories.find((c) => c.id === id);
		setState((s) => ({
			...s,
			categoryId: id,
			selection: { type: "category", id },
			camera: fit(
				cat,
				s.project.quests.filter((q) => q.category === id)
			),
		}));
	}
	function createCategory() {
		const name = prompt(tUI("Category (English)"), tUI("New category"))?.trim();
		if (!name) return;
		mutate((s) => {
			const id = categoryId(name, new Set(s.project.categories.map((c) => c.id)));
			s.project.categories.forEach((c,i)=>{ if(c.order===undefined)c.order=i; });
            s.project.categories.push({...newCategory(id, name),
                order:Math.min(1_000_000,Math.max(-1,...s.project.categories.map(c=>c.order??-1))+1)});
			s.categoryId = id;
			s.selection = { type: "category", id };
			s.camera = { x: 0, y: 0, zoom: 1 };
		});
	}
	function createQuest() {
		if (!currentCategory) return toast(tUI("Create a category first"));
		const name = prompt(tUI("Quest (English)"), tUI("New quest"))?.trim();
		if (!name) return;
		mutate((s) => {
			const id = questId(currentCategory.id, name, new Set(s.project.quests.map((q) => q.id)));
			s.project.quests.push(newQuest(currentCategory, id, name, { x: Math.round(s.camera.x), y: Math.round(s.camera.y) }));
			s.selection = { type: "quest", id };
		});
	}
	function createAnnotation() {
		if (!currentCategory) return;
		mutate((s) => {
			const cat = s.project.categories.find((c) => c.id === s.categoryId)!;
			cat.annotations ||= [];
			let i = 1,
				id = "note";
			while (cat.annotations.some((n) => n.id === id)) id = "note" + ++i;
			cat.annotations.push(newAnnotation(id, s.camera.x, s.camera.y) as any);
			s.selection = { type: "annotation", id };
		});
	}
	function action(kind: string, index = 0) {
		if (kind === "create-quest") return createQuest();
		if (kind === "create-annotation") return createAnnotation();
		if (kind === "delete-category" && state.project.quests.some((q) => q.category === state.selection?.id)) return toast(tUI("Cannot delete a category with quests."));
		if (kind === "delete-quest" && state.selection?.type === "quest") {
			const dependents = state.project.quests.filter((q) => references(q.requires).includes(state.selection!.id));
			if (dependents.length)
				return toast(tUI("Remove this quest from its dependents first:") + " " + dependents.map((q) => text(q.title, state.language) || q.id).join(", "));
		}
		if (kind.startsWith("delete-") && !confirm(tUI("Delete selected item?"))) return;
		mutate((s) => {
			const selected = s.selection,
				o = owner(s.project, selected);
			if (!o) return;
			if (kind.startsWith("progression:")) {
				const value = kind.slice(12);
				if (!PROGRESSIONS.includes(value)) return;
				if (value !== "once" && o.schemaVersion === 1) o.schemaVersion = 2;
				if (value === "tiered" && !o.tiers?.length) o.tiers = [{ id: "tier1", threshold: 1, rewards: [] }];
				if (value !== "tiered") {
					delete o.tiers;
					delete o.afterLast;
				}
				o.progression = value;
				return;
			}
			if (kind.startsWith("kind:")) {
				const value = kind.slice(5);
				o.kind = value;
				if (value === "information") {
					o.schemaVersion = 2;
					o.progression = "once";
					o.objectives = [];
					o.narrative ||= { en_us: "Information" };
					delete o.tiers;
					delete o.objectiveMode;
					delete o.cooldownSeconds;
					delete o.timeLimitSeconds;
					delete o.noDeath;
					delete o.afterLast;
				} else {
					delete o.acknowledgement;
					if (!o.objectives?.length) o.objectives = [{ id: "step", event: "block_break", target: "minecraft:stone", count: 1 }];
				}
				return;
			}
			if (kind === "duplicate-quest") {
				const clone = copy(o),
					id = questId(o.category, text(o.title, "en_us") + " copy", new Set(s.project.quests.map((q) => q.id)));
				clone.id = id;
				clone.layout = { x: (o.layout?.x || 0) + 45, y: (o.layout?.y || 0) + 45 };
				s.project.quests.push(clone);
				s.selection = { type: "quest", id };
				return;
			}
			if (kind === "duplicate-annotation") {
				const group = s.project.categories.find((c) => c.annotations?.some((a) => a.id === o.id));
				if (!group) return;
				const clone = copy(o);
				let i = 2;
				clone.id = o.id + "_copy";
				while (group.annotations?.some((a) => a.id === clone.id)) clone.id = o.id + "_copy" + i++;
				clone.x += 40;
				clone.y += 40;
				group.annotations!.push(clone);
				s.selection = { type: "annotation", id: clone.id };
				return;
			}
			if (kind === "delete-quest") {
				s.project.quests = s.project.quests.filter((q) => q.id !== o.id);
				s.selection = { type: "category", id: s.categoryId || "" };
				return;
			}
			if (kind === "delete-annotation") {
				s.project.categories.forEach((c) => (c.annotations = c.annotations?.filter((n) => n.id !== o.id)));
				s.selection = { type: "category", id: s.categoryId || "" };
				return;
			}
			if (kind === "delete-category") {
				s.project.categories = s.project.categories.filter((c) => c.id !== o.id);
				s.project.quests = s.project.quests.filter((q) => q.category !== o.id);
				s.categoryId = orderedCategories(s.project.categories)[0]?.id || null;
				s.selection = s.categoryId ? { type: "category", id: s.categoryId } : null;
				return;
			}
			if (kind === "add-objective") {
				o.objectives ||= [];
				let i = o.objectives.length + 1,
					id = "step" + i;
				while (o.objectives.some((v: any) => v.id === id)) id = "step" + ++i;
				o.objectives.push({ id, event: "block_break", target: "minecraft:stone", count: 1 });
				return;
			}
			if (kind === "remove-objective") {
				o.objectives?.splice(index, 1);
				return;
			}
			if (kind === "add-xp") {
				o.rewards ||= [];
				o.rewards.push({ type: "experience", amount: 5 });
				return;
			}
			if (kind === "add-item") {
				o.rewards ||= [];
				o.rewards.push({ type: "item", item: "minecraft:diamond", count: 1 });
				return;
			}
			if (kind === "remove-reward") o.rewards?.splice(index, 1);
		});
	}
	function changeCondition(scope: string, value: any) {
		mutate((s) => {
			const q = owner(s.project, s.selection);
			if (!q) return;
			if (scope === "requires") {
				if (value) q.requires = value;
				else delete q.requires;
			} else {
				const n = Number(scope.split(":")[1]),
					o = q.objectives?.[n];
				if (o) {
					if (value) o.conditions = value;
					else delete o.conditions;
				}
			}
		});
	}
	function setPosition(type: "quest" | "annotation", id: string, pos: { x: number; y: number }) {
		mutate((s) => {
			const o = owner(s.project, { type, id });
			if (!o) return;
			if (type === "quest") o.layout = pos;
			else {
				o.x = pos.x;
				o.y = pos.y;
			}
		}, "drag:" + id);
	}
	function autoLayout() {
		if (!currentCategory || !confirm(tUI("Auto-arrange quests in this category?"))) return;
		mutate((s) => {
			const qs = s.project.quests.filter((q) => q.category === s.categoryId),
				computed = gameLayout(qs.map((q) => ({ ...q, layout: undefined })));
			qs.forEach((q) => {
				const p = computed.points.get(q.id);
				if (p) q.layout = { ...p };
			});
		});
	}
	function reset(defaults: boolean) {
		if (!confirm(tUI("Replace current project? Export first if needed."))) return;
		mutate((s) => {
			s.project = (defaults ? demoProject() : emptyProject()) as Project;
			s.categoryId = orderedCategories(s.project.categories)[0]?.id || null;
			s.selection = s.categoryId ? { type: "category", id: s.categoryId } : null;
			s.camera = { x: 0, y: 0, zoom: 1 };
			s.language = "en_us";
		});
	}
	function addLocale(id: string) {
		if (!/^[a-z]{2,3}_[a-z]{2,4}$/.test(id) || id === "en_us") {
			toast(tUI("Invalid language code"));
			return;
		}
		mutate((s) => {
			s.project.editorLocales = [...new Set([...(s.project.editorLocales || []), id])];
			s.language = id;
		});
	}
	function connectQuests(sourceId: string, targetId: string) {
		try {
			const requires = withPrerequisite(latest.current.project.quests, sourceId, targetId);
			mutate((s) => {
				s.project.quests.find((q) => q.id === targetId)!.requires = requires;
				s.selection = { type: "quest", id: targetId };
			});
			toast(tUI("Dependency added"));
			return true;
		} catch (error) {
			toast(tUI((error as Error).message));
			return false;
		}
	}
	async function importFiles(files: FileList | null) {
		if (!files?.length || !confirm(tUI("Replace the current quest pack?"))) return;
		try {
			const docs: { name: string; content: string }[] = [];
			for (const file of Array.from(files)) {
				if (file.name.endsWith(".zip")) docs.push(...(await readZip(file)));
				else if (file.name.endsWith(".json")) docs.push({ name: (file as any).webkitRelativePath || file.name, content: await file.text() });
			}
			const p = importDocuments(docs) as Project,
				errors = validate(p);
			if (errors.length) throw Error(errors[0].where + ": " + errors[0].message);
			mutate((s) => {
				s.project = p;
				s.categoryId = orderedCategories(p.categories)[0]?.id || null;
				s.selection = s.categoryId ? { type: "category", id: s.categoryId } : null;
				s.camera = { x: 0, y: 0, zoom: 1 };
				s.language = "en_us";
			});
			toast(tUI("Pack imported"));
		} catch (e) {
			toast(tUI("Import error:") + " " + (e as Error).message);
		}
	}
	function exportZip() {
		const errors = validate(latest.current.project);
		if (errors.length) {
			toast(tUI("Fix validation errors"));
			return false;
		}
		try {
			const blob = makeZip(fileMap(latest.current.project)),
				url = URL.createObjectURL(blob),
				link = document.createElement("a");
			link.href = url;
			link.download = "QuestPlus-quests.zip";
			document.body.appendChild(link);
			link.click();
			link.remove();
			setTimeout(() => URL.revokeObjectURL(url), 1500);
			toast(tUI("ZIP exported"));
			return true;
		} catch (e) {
			toast(tUI("Export error:") + " " + (e as Error).message);
			return false;
		}
	}
	// Manual Save creates an importable ZIP; automatic local autosave never downloads files.
	function saveAndExport() {
		save();
		return exportZip();
	}
	function applyJson(raw: string) {
		try {
			const obj = JSON.parse(raw),
				s = state.selection;
			if (!s || !obj || Array.isArray(obj) || typeof obj !== "object") throw Error("Invalid object");
			const p = copy(state.project) as Project;
			if (s.type === "quest") {
				const i = p.quests.findIndex((q) => q.id === s.id);
				p.quests[i] = obj;
			}
			if (s.type === "category") {
				const i = p.categories.findIndex((c) => c.id === s.id);
				p.categories[i] = obj;
			}
			if (s.type === "annotation")
				for (const c of p.categories) {
					const i = c.annotations?.findIndex((a) => a.id === s.id) ?? -1;
					if (i !== -1) {
						c.annotations![i] = obj;
						break;
					}
				}
			const issues = validate(p);
			if (issues.length) throw Error(issues[0].where + ": " + issues[0].message);
			mutate((next) => {
				next.project = p;
				if (obj.id !== s.id) {
					next.selection = { ...s, id: obj.id };
					if (s.type === "category") next.categoryId = obj.id;
				}
			});
			return "";
		} catch (e) {
			return (e as Error).message;
		}
	}
	return {
		state,
		setState,
		notice,
		toast,
		save,
		saveStatus,
		history,
		future,
		undo,
		redo,
		mutate,
		setField,
		currentCategory,
		quests,
		fit,
		selectCategory,
		createCategory,
		createQuest,
		createAnnotation,
		action,
		changeCondition,
		setPosition,
		autoLayout,
		reset,
		addLocale,
		connectQuests,
		importFiles,
		exportZip,
		saveAndExport,
		applyJson,
	};
}
