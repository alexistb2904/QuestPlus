import { useEffect, useRef, useState } from "react";
import { exportTemplateBundle, importTemplateBundle, savedQuestTemplates } from "../core/studio-template-library.js";
import { tUI } from "../ui/ui-i18n.js";
import { Dialog } from "./Dialog";

/** Personal templates stay local and portable; quest packs do not silently include them. */
export function TemplateLibrary({
	onClose,
	projectTemplates,
	onSaveProject,
	onSaveSelected,
}: {
	onClose: () => void;
	projectTemplates?: any[];
	onSaveProject?: (templates: any[]) => void;
	onSaveSelected?: () => void;
}) {
	const [templates, setTemplates] = useState(() => savedQuestTemplates());
	const [message, setMessage] = useState(""),
		ref = useRef<HTMLInputElement>(null);
	const refresh = () => setTemplates(savedQuestTemplates());
	const exportFile = () => {
		const blob = new Blob([exportTemplateBundle()], { type: "application/json" });
		const url = URL.createObjectURL(blob),
			link = document.createElement("a");
		link.href = url;
		link.download = "QuestPlus-Studio-Templates.json";
		link.click();
		setTimeout(() => URL.revokeObjectURL(url), 1500);
		setMessage("Personal templates exported (no player data).");
	};
	const importFile = async (file: File) => {
		try {
			if (file.size > 750_000) throw Error("Template archive exceeds 750 kB");
			const count = importTemplateBundle(await file.text());
			refresh();
			setMessage(count + " personal templates imported. Right-click the quest tree to use them.");
		} catch (error) {
			setMessage("Import failed: " + (error as Error).message);
		}
	};
	useEffect(() => {
		const listener = () => refresh();
		window.addEventListener("storage", listener);
		return () => window.removeEventListener("storage", listener);
	}, []);
	return (
		<Dialog title="Quest Template Library" onClose={onClose}>
			<p>
				{tUI(
					"Templates reuse objectives and rewards, but not quest IDs, prerequisites or player progress. Save a selected quest, then right-click the graph to place a copy."
				)}
			</p>
			<div className="toolbar-row library-actions">
				<button
					className="button primary"
					disabled={!onSaveSelected}
					onClick={() => {
						onSaveSelected?.();
						refresh();
					}}>
					{tUI("Save selected quest as template")}
				</button>
				<button className="button secondary" onClick={exportFile} disabled={!templates.length}>
					Export personal templates
				</button>
				<button className="button primary" onClick={() => ref.current?.click()}>
					Import templates
				</button>
				<input
					type="file"
					hidden
					accept=".json"
					ref={ref}
					onChange={(e) => {
						const f = e.target.files?.[0];
						if (f) void importFile(f);
						e.target.value = "";
					}}
				/>
			</div>
			{onSaveProject && (
				<div className="toolbar-row">
					<button
						className="button secondary"
						onClick={() => {
							onSaveProject(structuredClone([...(projectTemplates || []).filter((old) => !templates.some((t) => t.id === old.id)), ...templates]));
							setMessage("Personal templates copied into this project.");
						}}>
						Copy personal templates to project
					</button>
					<button
						className="button secondary"
						disabled={!projectTemplates?.length}
						onClick={() => {
							try {
								const count = importTemplateBundle(JSON.stringify({ format: "questplus-studio-templates", version: 1, templates: projectTemplates }));
								refresh();
								setMessage(count + " project templates copied to this browser.");
							} catch (error) {
								setMessage("Import failed: " + (error as Error).message);
							}
						}}>
						Copy project templates to personal library
					</button>
				</div>
			)}
			<p role="status" className="picker-help">
				{message || templates.length + " personal templates saved in this browser."}
			</p>
			{templates.length > 0 && (
				<div className="bulk-quest-list">
					{templates.map((template) => (
						<div className="mini-card" key={template.id}>
							<strong>{template.label}</strong>
							<small style={{ display: "block" }}>
								{template.id} · {template.quest.objectives.length} objectives
							</small>
						</div>
					))}
				</div>
			)}
			{!templates.length && (
				<div className="library-empty">
					<strong>{tUI("No saved templates yet")}</strong>
					<span>{tUI("Save a selected quest to reuse its objectives and rewards. Templates stay in this browser until you export or copy them to the project.")}</span>
				</div>
			)}
		</Dialog>
	);
}
