import { Copy, TriangleAlert } from "lucide-react";
import React, { useEffect, useRef, useState } from "react";
import { AI_PROMPT_FIELDS, aiPrompt } from "../core/ai-prompt.js";
import { MINECRAFT_VERSIONS } from "../core/project.js";
import { tUI } from "../ui/ui-i18n.js";
import { Dialog } from "./Dialog";

const STORAGE_KEY = "questplus.pack-prompt";
type Details = Record<string, string>;
// span: "wide" takes the whole row, "third" a third of it, otherwise half.
const FIELDS: { key: string; label: string; placeholder: string; multiline?: boolean; span?: "wide" | "third" }[] = [
	{ key: "theme", label: "Modpack name and theme", placeholder: "Skyblock, tech, medieval magic..." },
	{ key: "mods", label: "Mods in the pack", placeholder: "One per line, with the version if you know it", multiline: true, span: "wide" },
	{ key: "goals", label: "What the quests should cover", placeholder: "Progression, goals, bosses, exploration...", multiline: true },
	{ key: "guided", label: "Complex mods that need a guided path", placeholder: "Mods that new players find hard to learn", multiline: true },
	{ key: "size", label: "Size", placeholder: "5 categories, about 80 quests", span: "third" },
	{ key: "languages", label: "Other quest languages", placeholder: "fr_fr, es_es", span: "third" },
	{ key: "rewards", label: "Rewards style", placeholder: "XP and useful items, generous or tight", span: "third" },
];

function storedDetails(): Details {
	try {
		const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || "{}");
		return Object.fromEntries(AI_PROMPT_FIELDS.filter((key) => typeof saved[key] === "string").map((key) => [key, saved[key]]));
	} catch {
		return {};
	}
}

export function AiPromptDialog({ version, onClose }: { version?: string; onClose: () => void }) {
	const [target, setTarget] = useState(version && MINECRAFT_VERSIONS.includes(version) ? version : MINECRAFT_VERSIONS[0]),
		[details, setDetails] = useState(storedDetails),
		[copied, setCopied] = useState(false),
		area = useRef<HTMLTextAreaElement>(null);
	const prompt = aiPrompt(target, details);
	useEffect(() => {
		try {
			localStorage.setItem(STORAGE_KEY, JSON.stringify(details));
		} catch {}
		setCopied(false);
	}, [details, target]);
	const copy = async () => {
		try {
			await navigator.clipboard.writeText(prompt);
			setCopied(true);
		} catch {
			// Clipboard access can be denied: open the preview and select it so Ctrl+C still works.
			area.current?.closest("details")?.setAttribute("open", "");
			area.current?.select();
		}
	};
	return (
		<Dialog title={tUI("Create your own quest pack")} onClose={onClose} className="ai-prompt-dialog">
			<p>{tUI("Describe your modpack, copy the prompt into an AI, then open the JSON file it returns with File > Open.")}</p>
			<div className="reward-notice ai-prompt-notice" role="note">
				<TriangleAlert size={16} aria-hidden="true" />
				<span>
					{tUI(
						"This only gives you a starting point. AI makes mistakes, more or less depending on the model. We do not recommend a quest pack made 100% by AI. Review and test every quest in game before you publish."
					)}
				</span>
			</div>
			<div className="ai-prompt-form">
				<label className="field">
					<span>{tUI("Target Minecraft version")}</span>
					<select value={target} onChange={(e) => setTarget(e.target.value)}>
						{MINECRAFT_VERSIONS.map((id) => (
							<option key={id} value={id}>
								{id}
							</option>
						))}
					</select>
				</label>
				{FIELDS.map(({ key, label, placeholder, multiline, span }) => {
					const props = {
						value: details[key] || "",
						placeholder: tUI(placeholder),
						maxLength: 4000,
						onChange: (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => setDetails((old) => ({ ...old, [key]: e.target.value })),
					};
					return (
						<label key={key} className={"field" + (span ? " " + span : "")}>
							<span>{tUI(label)}</span>
							{multiline ? <textarea rows={3} {...props} /> : <input type="text" {...props} />}
						</label>
					);
				})}
			</div>
			<details className="editor-details">
				<summary>{tUI("Preview the prompt")}</summary>
				<div className="details-body">
					<textarea ref={area} className="form-control ai-prompt-text" aria-label={tUI("AI prompt")} readOnly spellCheck={false} rows={12} value={prompt} />
				</div>
			</details>
			<div className="dialog-actions">
				<button className="button secondary" onClick={onClose}>
					{tUI("Close")}
				</button>
				<button className="button primary" onClick={copy}>
					<Copy size={16} aria-hidden="true" /> {tUI(copied ? "Copied" : "Copy prompt")}
				</button>
			</div>
		</Dialog>
	);
}
