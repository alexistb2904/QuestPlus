import { ArrowRight, BookOpen, Download, ExternalLink, FileArchive, Package, Square, TriangleAlert } from "lucide-react";
import React, { useState } from "react";
import { orderedCategories, text } from "../core/project.js";
import type { Project } from "../types";
import { tUI } from "../ui/ui-i18n.js";
import { Dialog } from "./Dialog";
import { ItemIcon } from "./IconPicker";

type Fresh = "empty" | "examples";

/** First screen when a browser draft exists: resume it (safe) or replace it (guarded). */
export function WelcomeDialog({
	project,
	language,
	docsUrl,
	onResume,
	onStartFresh,
	onImport,
	onBackup,
}: {
	project: Project;
	language: string;
	docsUrl: string;
	onResume: () => void;
	onStartFresh: (withExamples: boolean) => void;
	onImport: () => void;
	onBackup: () => void;
}) {
	const [pending, setPending] = useState<Fresh | null>(null);
	const quests = project.quests.length,
		categories = orderedCategories(project.categories),
		shown = categories.slice(0, 8),
		empty = quests === 0 && categories.length === 0;
	const fresh = (choice: Fresh) => (empty ? onStartFresh(choice === "examples") : setPending((p) => (p === choice ? null : choice)));
	const count = (n: number, one: string, many: string) => n + " " + tUI(n === 1 ? one : many);
	return (
		<Dialog className="startup-dialog grass-trim" title={tUI("Welcome to Quest Studio")} dismissible={false} onClose={() => {}}>
			<div className="startup-body">
				<button className="startup-resume" onClick={onResume}>
					<span className="startup-resume-text">
						<strong>{tUI("Resume current project")}</strong>
						<small>
							{empty ? tUI("Empty draft") : count(quests, "quest", "quests") + " · " + count(categories.length, "category", "categories")} · {tUI("saved in this browser")}
						</small>
						{shown.length > 0 && (
							<span className="startup-resume-icons" aria-hidden="true">
								{shown.map((c) => (
									<span key={c.id} title={text(c.title, language)}>
										<ItemIcon id={c.icon} version={project.targetMinecraftVersion} size={24} />
									</span>
								))}
								{categories.length > shown.length && <em>+{categories.length - shown.length}</em>}
							</span>
						)}
					</span>
					<span className="startup-resume-go">
						{tUI("Open")}
						<ArrowRight size={17} aria-hidden="true" />
					</span>
				</button>

				<section className="startup-fresh" aria-labelledby="startup-fresh-title">
					<header>
						<h3 id="startup-fresh-title">{tUI("Start something else")}</h3>
						{!empty && (
							<span className="startup-warning-chip">
								<TriangleAlert size={13} aria-hidden="true" />
								{tUI("Replaces the current draft")}
							</span>
						)}
					</header>
					<div className="startup-choices">
						<button className="startup-choice" data-tone="stone" aria-pressed={pending === "empty"} onClick={() => fresh("empty")}>
							<Square size={20} aria-hidden="true" />
							<strong>{tUI("Empty project")}</strong>
							<small>{tUI("No categories or quests. Build your own pack.")}</small>
						</button>
						<button className="startup-choice" data-tone="gold" aria-pressed={pending === "examples"} onClick={() => fresh("examples")}>
							<Package size={20} aria-hidden="true" />
							<strong>{tUI("Project with examples")}</strong>
							<small>{tUI("Use the built-in Quest Plus catalogue.")}</small>
						</button>
						<button className="startup-choice" data-tone="amethyst" onClick={onImport}>
							<FileArchive size={20} aria-hidden="true" />
							<strong>{tUI("Import a pack")}</strong>
							<small>{tUI("Quest Plus or FTB Quests ZIP. Asks before replacing.")}</small>
						</button>
					</div>
					{pending && (
						<div className="startup-confirm" role="alert">
							<p>
								<TriangleAlert size={17} aria-hidden="true" />
								<span>
									<strong>{tUI(pending === "empty" ? "Empty project" : "Project with examples")}</strong>{" "}
									{tUI("replaces your draft") + " (" + count(quests, "quest", "quests") + "). " + tUI("This cannot be undone.")}
								</span>
							</p>
							<div className="startup-confirm-actions">
								<button className="button" onClick={onBackup}>
									<Download size={15} aria-hidden="true" />
									{tUI("Download a backup")}
								</button>
								<button className="button" onClick={() => setPending(null)}>
									{tUI("Cancel")}
								</button>
								<button className="button danger" onClick={() => onStartFresh(pending === "examples")}>
									{tUI("Replace draft")}
								</button>
							</div>
						</div>
					)}
				</section>

				<a className="startup-docs-link" href={docsUrl} target="_blank" rel="noopener noreferrer">
					<BookOpen size={16} aria-hidden="true" />
					<span>
						<strong>{tUI("Documentation")}</strong> {tUI("Learn how to create quests and integrate your mod.")}
					</span>
					<ExternalLink size={14} aria-hidden="true" />
				</a>
			</div>
		</Dialog>
	);
}
