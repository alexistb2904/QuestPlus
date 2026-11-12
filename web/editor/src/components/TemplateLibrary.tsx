import { Copy, Download, FolderInput, HardDrive, Package, Plus, Save, Trash2, Upload } from "lucide-react";
import React, { useEffect, useMemo, useRef, useState } from "react";
import { objectiveLabel, text } from "../core/project.js";
import { buildQuestFromTemplate, QUEST_TEMPLATES } from "../core/quest-templates.js";
import { exportTemplateBundle, importTemplateBundle, removeQuestTemplate, savedQuestTemplates, saveQuestTemplate } from "../core/studio-template-library.js";
import type { Quest, Reward } from "../types";
import { inVanillaRegistry } from "../ui/minecraft-icons.js";
import { typeIcon } from "../ui/type-catalog.js";
import { tUI } from "../ui/ui-i18n.js";
import { Dialog } from "./Dialog";
import { ItemIcon } from "./IconPicker";

type Saved = { id: string; label: string; quest: Partial<Quest> };
type Source = "builtin" | "browser" | "project";
type Entry = { source: Source; id: string; label: string; description?: string; quest: Partial<Quest> };

const rewardText = (r: Reward) =>
	r.type === "item"
		? (r.count || 1) + " × " + String(r.item).replace(/^minecraft:/, "").replace(/_/g, " ")
		: r.type === "experience"
			? r.amount + " XP"
			: r.type === "experience_levels"
				? r.amount + " " + tUI("XP Levels")
				: r.type.replace(/^questplus:/, "");
const rewardIcon = (r: Reward) => (r.type === "item" ? r.item : r.type.startsWith("experience") ? "minecraft:experience_bottle" : "minecraft:bundle");

/** Built-in, browser and project templates in one place. Personal templates stay local until copied or exported. */
export function TemplateLibrary({
	onClose,
	projectTemplates = [],
	onSaveProject,
	selectedQuest,
	language,
	canCreate,
	onCreate,
}: {
	onClose: () => void;
	projectTemplates?: Saved[];
	onSaveProject?: (templates: Saved[]) => void;
	selectedQuest?: Quest;
	language: string;
	canCreate: boolean;
	onCreate: (templateId: string) => void;
}) {
	const [personal, setPersonal] = useState<Saved[]>(() => savedQuestTemplates()),
		[current, setCurrent] = useState<string>(() => "builtin:basic"),
		[name, setName] = useState(() => (selectedQuest ? text(selectedQuest.title, language) : "")),
		[message, setMessage] = useState<{ tone: "ok" | "error"; text: string } | null>(null),
		file = useRef<HTMLInputElement>(null);
	const refresh = () => setPersonal(savedQuestTemplates());
	useEffect(() => {
		window.addEventListener("storage", refresh);
		return () => window.removeEventListener("storage", refresh);
	}, []);
	const entries = useMemo<Entry[]>(() => {
		const builtin = QUEST_TEMPLATES.map((t) => ({
			source: "builtin" as const,
			id: t.id,
			label: t.label,
			description: t.description,
			quest: buildQuestFromTemplate({ id: "preview" }, "preview:quest", t.label, { x: 0, y: 0 }, t.id),
		}));
		return [
			...builtin,
			...personal.map((t) => ({ source: "browser" as const, ...t })),
			...projectTemplates.map((t) => ({ source: "project" as const, ...t })),
		];
	}, [personal, projectTemplates]);
	const key = (e: Entry) => e.source + ":" + e.id,
		entry = entries.find((e) => key(e) === current) ?? entries[0];
	const report = (tone: "ok" | "error", value: string) => setMessage({ tone, text: value });
	const fail = (error: unknown) => report("error", tUI("Could not complete the action") + ": " + tUI((error as Error).message));

	const saveSelected = (e: React.FormEvent) => {
		e.preventDefault();
		if (!selectedQuest) return;
		try {
			const item = saveQuestTemplate(selectedQuest, name);
			refresh();
			setCurrent("browser:" + item.id);
			report("ok", tUI("Template saved in this browser."));
		} catch (error) {
			fail(error);
		}
	};
	const exportFile = () => {
		const url = URL.createObjectURL(new Blob([exportTemplateBundle()], { type: "application/json" })),
			link = document.createElement("a");
		link.href = url;
		link.download = "QuestPlus-Studio-Templates.json";
		link.click();
		setTimeout(() => URL.revokeObjectURL(url), 1500);
		report("ok", tUI("Personal templates exported (no player data)."));
	};
	const importFile = async (f: File) => {
		try {
			if (f.size > 750_000) throw Error("Template file exceeds 750 kB");
			const count = importTemplateBundle(await f.text());
			refresh();
			report("ok", count + " " + tUI("templates imported into this browser."));
		} catch (error) {
			fail(error);
		}
	};
	const copyToProject = (item: Saved) => {
		onSaveProject?.(structuredClone([...projectTemplates.filter((t) => t.id !== item.id), { id: item.id, label: item.label, quest: item.quest }]));
		setCurrent("project:" + item.id);
		report("ok", tUI("Copied into the project. It ships with the exported pack."));
	};
	const copyToBrowser = (item: Saved) => {
		try {
			importTemplateBundle(JSON.stringify({ format: "questplus-studio-templates", version: 1, templates: [item] }));
			refresh();
			setCurrent("browser:" + item.id);
			report("ok", tUI("Copied into this browser."));
		} catch (error) {
			fail(error);
		}
	};
	const group = (source: Source, title: string, icon: React.ReactNode) => {
		const rows = entries.filter((e) => e.source === source);
		return (
			<li className="template-group" key={source}>
				<h3>
					{icon}
					{tUI(title)}
					<span>{rows.length}</span>
				</h3>
				{rows.length ? (
					<ul>
						{rows.map((e) => (
							<li key={key(e)}>
								<button type="button" className="reward-library-item" aria-current={key(e) === key(entry)} onClick={() => setCurrent(key(e))}>
									<ItemIcon id={e.quest.icon} size={24} />
									<span>
										<strong>{source === "builtin" ? tUI(e.label) : e.label}</strong>
										<small>
											{(e.quest.objectives?.length || 0) + " " + tUI(e.quest.objectives?.length === 1 ? "objective" : "objectives")}
											{" · "}
											{(e.quest.rewards?.length || 0) + " " + tUI(e.quest.rewards?.length === 1 ? "reward" : "rewards")}
										</small>
									</span>
								</button>
							</li>
						))}
					</ul>
				) : (
					<p className="template-group-empty">{tUI(source === "browser" ? "Save a quest above to reuse it." : "Copy a browser template here to ship it with the pack.")}</p>
				)}
			</li>
		);
	};
	const quest = entry.quest,
		savedEntry: Saved = { id: entry.id, label: entry.label, quest };
	return (
		<Dialog title={tUI("Quest templates")} onClose={onClose} className="reward-library-dialog template-library">
			<div className="reward-library">
				<nav className="reward-library-list" aria-label={tUI("Quest templates")}>
					<form className="reward-library-new" onSubmit={saveSelected}>
						<label className="field">
							<span>{tUI("Save the selected quest")}</span>
							<input value={name} maxLength={64} disabled={!selectedQuest} placeholder={tUI(selectedQuest ? "Template name" : "Select a quest first")} onChange={(e) => setName(e.target.value)} />
						</label>
						<button type="submit" className="button secondary" disabled={!selectedQuest || !name.trim()}>
							<Save size={15} aria-hidden="true" />
							{tUI("Save as template")}
						</button>
					</form>
					<ul className="template-groups">
						{group("builtin", "Built-in", <Package size={14} aria-hidden="true" />)}
						{group("browser", "This browser", <HardDrive size={14} aria-hidden="true" />)}
						{onSaveProject && group("project", "This project", <FolderInput size={14} aria-hidden="true" />)}
					</ul>
					<div className="template-transfer">
						<button type="button" className="button secondary" onClick={() => file.current?.click()}>
							<Upload size={15} aria-hidden="true" />
							{tUI("Import")}
						</button>
						<button type="button" className="button secondary" onClick={exportFile} disabled={!personal.length}>
							<Download size={15} aria-hidden="true" />
							{tUI("Export")}
						</button>
						<input
							ref={file}
							type="file"
							hidden
							accept=".json"
							onChange={(e) => {
								const f = e.target.files?.[0];
								e.target.value = "";
								if (f) void importFile(f);
							}}
						/>
					</div>
				</nav>
				<section className="reward-library-main template-detail" aria-label={entry.label}>
					<header className="template-head">
						<ItemIcon id={quest.icon} size={44} />
						<div>
							<h3>{entry.source === "builtin" ? tUI(entry.label) : entry.label}</h3>
							<p>
								<span className={"template-source " + entry.source}>{tUI(entry.source === "builtin" ? "Built-in" : entry.source === "browser" ? "This browser" : "This project")}</span>
								{entry.source !== "builtin" && <code>{entry.id}</code>}
							</p>
						</div>
						<div className="template-head-actions">
							{entry.source === "browser" && onSaveProject && (
								<button type="button" className="button secondary" onClick={() => copyToProject(savedEntry)}>
									<Copy size={15} aria-hidden="true" />
									{tUI("Copy to project")}
								</button>
							)}
							{entry.source === "project" && (
								<button type="button" className="button secondary" onClick={() => copyToBrowser(savedEntry)}>
									<Copy size={15} aria-hidden="true" />
									{tUI("Copy to this browser")}
								</button>
							)}
							{entry.source !== "builtin" && (
								<button
									type="button"
									className="icon-button template-delete"
									aria-label={tUI("Delete template")}
									title={tUI("Delete template")}
									onClick={() => {
										if (!window.confirm(tUI("Delete this template? Quests already created from it are not changed."))) return;
										if (entry.source === "browser") {
											removeQuestTemplate(entry.id);
											refresh();
										} else onSaveProject?.(projectTemplates.filter((t) => t.id !== entry.id));
										setCurrent("builtin:basic");
										report("ok", tUI("Template deleted."));
									}}>
									<Trash2 size={16} />
								</button>
							)}
						</div>
					</header>
					{entry.description && <p className="template-summary">{tUI(entry.description)}</p>}
					<div className="template-columns">
						<section>
							<h4>
								{tUI("Objectives")}
								<span>{quest.objectives?.length || 0}</span>
							</h4>
							{quest.objectives?.length ? (
								<ul className="template-rows">
									{quest.objectives.map((o) => (
										<li key={o.id}>
											<ItemIcon id={inVanillaRegistry(o.target) ? o.target : (typeIcon(o.event) ?? quest.icon)} size={22} />
											<span>{objectiveLabel(o, language)}</span>
										</li>
									))}
								</ul>
							) : (
								<p className="template-group-empty">{tUI("No objectives")}</p>
							)}
						</section>
						<section>
							<h4>
								{tUI("Rewards")}
								<span>{quest.rewards?.length || 0}</span>
							</h4>
							{quest.rewards?.length ? (
								<ul className="template-rows">
									{quest.rewards.map((r, i) => (
										<li key={i}>
											<ItemIcon id={rewardIcon(r)} size={22} />
											<span>{rewardText(r)}</span>
										</li>
									))}
								</ul>
							) : (
								<p className="template-group-empty">{tUI("No rewards")}</p>
							)}
						</section>
					</div>
					<p className="template-kept">{tUI("A new quest copies the objectives, rewards and settings. Its ID, prerequisites and player progress are never copied.")}</p>
				</section>
			</div>
			<footer className="reward-library-footer">
				<span role="status" className={message?.tone === "error" ? "template-error" : undefined}>
					{message?.text ?? tUI("Tip: right-click the quest diagram to place a template where you click.")}
				</span>
				<button type="button" className="button primary" disabled={!canCreate} title={canCreate ? undefined : tUI("Create a category first")} onClick={() => onCreate(entry.id)}>
					<Plus size={16} aria-hidden="true" />
					{tUI("Create a quest from this template")}
				</button>
			</footer>
		</Dialog>
	);
}
