import React, { useState } from "react";
import { descriptionHtml, descriptionParts } from "../core/description-format.js";
import { hubLabel } from "../core/hub-registry.js";
import { objectiveLabel, text } from "../core/project.js";
import { crateTableName } from "../core/reward-tables.js";
import { richToHtml } from "../core/rich-format.js";
import type { Objective, Project, Quest, Reward } from "../types";
import { supportedLocales } from "../types";
import { inVanillaRegistry } from "../ui/minecraft-icons.js";
import { TABLET_STRINGS } from "../ui/tablet-strings.js";
import { typeIcon } from "../ui/type-catalog.js";
import { tUI } from "../ui/ui-i18n.js";
import { Dialog } from "./Dialog";
import { ItemIcon } from "./IconPicker";

type Status = "locked" | "available" | "active" | "reward_ready" | "completed";
const STATES: [Status, string, string][] = [
	["locked", "Locked", "Requirements not met yet"],
	["available", "Available", "Unlocked, nothing done yet"],
	["active", "In progress", "About half of each objective"],
	["reward_ready", "Reward to claim", "Objectives done, reward waiting"],
	["completed", "Completed", "Reward claimed"],
];
const SUBMIT = new Set(["item_submit", "item_turn_in", "xp_submit_levels", "xp_submit_points", "fluid_submit", "energy_submit"]);
const REWARD_TYPES = new Set(["choice", "random", "loot", "all", "advancement", "message", "command", "stage", "toast"].map((t) => "questplus:" + t));

/** QuestSkin.onPaper: colours picked for a dark ground are darkened, hue kept, until they read on parchment. */
function onPaper(hex: string) {
	const n = parseInt(hex.slice(1), 16),
		[r, g, b] = [(n >> 16) & 255, (n >> 8) & 255, n & 255],
		luma = Math.floor((r * 299 + g * 587 + b * 114) / 1000);
	if (luma <= 84) return hex;
	return "#" + [r, g, b].map((c) => Math.floor((c * 84) / luma).toString(16).padStart(2, "0")).join("");
}
const paperHtml = (html: string) => html.replace(/(^|[^-\w])color:\s*(#[0-9a-f]{6})/gi, (_, lead, hex) => lead + "color:" + onPaper(hex));
const pretty = (id: string) =>
	id
		.replace(/^#?[^:]*:/, "")
		.split(/[_/]/)
		.map((w) => w.charAt(0).toUpperCase() + w.slice(1))
		.join(" ");
const questIds = (node: unknown, known: Set<string>, out: Set<string>) => {
	if (typeof node === "string") known.has(node) && out.add(node);
	else if (node && typeof node === "object") Object.values(node).forEach((v) => questIds(v, known, out));
	return out;
};

/** The tablet's quest details pane, drawn with the game's palette, typeface, wording and row order. */
export function QuestPreview({
	project,
	quest,
	language,
	locales,
	onLanguage,
	onClose,
}: {
	project: Project;
	quest: Quest;
	language: string;
	locales: string[];
	onLanguage: (locale: string) => void;
	onClose: () => void;
}) {
	const [status, setStatus] = useState<Status>("available");
	const strings = (TABLET_STRINGS as Record<string, Record<string, string>>)[
		language in TABLET_STRINGS ? language : (Object.keys(TABLET_STRINGS).find((l) => l.slice(0, 2) === language.slice(0, 2)) ?? "en_us")
	];
	const t = (key: string, arg?: string | number) => (strings[key] ?? (TABLET_STRINGS as any).en_us[key] ?? key).replace("%s", String(arg ?? ""));
	const done = status === "reward_ready" || status === "completed",
		factor = quest.progression === "tiered" && quest.tiers?.length ? quest.tiers[0].threshold || 1 : 1;
	const target = (o: Objective) => Math.max(1, (o.count || 1) * factor);
	const value = (o: Objective) => (done ? target(o) : status === "active" ? Math.floor(target(o) / 2) : 0);
	const information = quest.kind === "information";
	const manual = (information && quest.acknowledgement === "manual") || quest.objectives.some((o) => o.event === "manual" && o.target === quest.id);
	const action =
		status === "reward_ready"
			? quest.rewards.some((r) => r.type === "questplus:choice")
				? "choose"
				: "claim"
			: status === "available" || status === "active"
				? manual
					? "trigger"
					: quest.objectives.some((o) => SUBMIT.has(o.event))
						? "submit"
						: null
				: null;
	const objectiveIcon = (o: Objective) => (inVanillaRegistry(o.target) ? o.target : (typeIcon(o.event) ?? quest.icon));
	const rewardLabel = (r: Reward) => {
		const label =
			r.type === "item"
				? (r.count || 1) + " × " + (r.item?.startsWith("minecraft:") ? pretty(r.item) : hubLabel(r.item || "", language.slice(0, 2)))
				: r.type === "experience"
					? t("xp", r.amount)
					: r.type === "experience_levels"
						? t("xp_levels", r.amount)
						: REWARD_TYPES.has(r.type)
							? t("reward_type." + r.type)
							: (crateTableName(r) ?? r.table ?? r.type);
		const title = text(r.text, language);
		return title ? title + " · " + label : label;
	};
	const rewardIcon = (r: Reward) =>
		crateTableName(r) !== undefined ? "minecraft:chest_minecart" : r.type === "item" ? r.item : r.type.startsWith("experience") ? "minecraft:experience_bottle" : "minecraft:bundle";
	const rich = quest.descriptionRich?.[language]?.length ? quest.descriptionRich[language] : quest.descriptionRich?.en_us;
	const description = rich?.length ? richToHtml(rich) : descriptionHtml(descriptionParts(text(quest.description, language)));
	const required = [...questIds(quest.requires, new Set(project.quests.map((q) => q.id)), new Set<string>())];
	const mode = quest.objectiveMode || "all";

	const tile = (key: React.Key, icon: string | undefined, label: string, progress?: [number, number]) => (
		<div className="tp-tile" key={key}>
			<ItemIcon id={icon} size={28} />
			<span className="tp-tile-text">
				<span>{label}</span>
				{progress && (
					<span className="tp-progress">
						<span className="tp-bar">
							<span style={{ width: (100 * Math.min(1, progress[0] / progress[1])) + "%" }} className={progress[0] >= progress[1] ? "done" : ""} />
						</span>
						<span className="tp-count">{progress[0] + " / " + progress[1]}</span>
					</span>
				)}
			</span>
		</div>
	);
	return (
		<Dialog title={tUI("Quest preview")} onClose={onClose} className="quest-preview-dialog">
			<div className="tp-layout">
				<aside className="tp-controls">
					<fieldset className="tp-states">
						<legend>{tUI("Player state")}</legend>
						{STATES.map(([id, label, hint]) => (
							<label key={id} className={"tp-state" + (status === id ? " selected" : "")}>
								<input type="radio" name="tp-state" value={id} checked={status === id} onChange={() => setStatus(id)} />
								<span className={"tp-swatch " + id} aria-hidden="true" />
								<span>
									<strong>{tUI(label)}</strong>
									<small>{tUI(hint)}</small>
								</span>
							</label>
						))}
					</fieldset>
					{locales.length > 1 && (
						<label className="field tp-language">
							<span>{tUI("Text language")}</span>
							<select value={language} onChange={(e) => onLanguage(e.target.value)}>
								{locales.map((id) => (
									<option key={id} value={id}>
										{supportedLocales[id] || id}
									</option>
								))}
							</select>
						</label>
					)}
					<p className="tp-note">{tUI("Laid out like the tablet's details pane. Player progress is simulated here, the server decides in game.")}</p>
				</aside>
				<div className="tp-stage">
					<div className="tp-bezel">
						<section className="tp-screen" aria-label={tUI("Tablet details pane")}>
							<div className="tp-buttons" aria-hidden="true">
								<span className="tp-button wide">{t("close_details")}</span>
								<span className="tp-button">{t("pin")}</span>
								<span className="tp-button">{t("rewards")}</span>
								{action && <span className={"tp-button wide" + (action === "claim" || action === "choose" ? " accent" : "")}>{t(action)}</span>}
							</div>
							<h3 className="tp-title">{text(quest.title, language) || quest.id}</h3>
							<p className="tp-status">
								<span className={"tp-swatch " + status} aria-hidden="true" />
								{t("status." + status) + " · " + t("progression." + (quest.progression || "once"))}
							</p>
							<div className="tp-scroll">
								{text(quest.presentation?.subtitle, language) && <p className="tp-line">{text(quest.presentation.subtitle, language)}</p>}
								{quest.presentation?.optional && <p className="tp-line">{t("optional")}</p>}
								{description && <div className="tp-description" dangerouslySetInnerHTML={{ __html: paperHtml(description) }} />}
								{text(quest.narrative, language) && <p className="tp-line">{text(quest.narrative, language)}</p>}
								{!information ? (
									<>
										<h4 className="tp-heading">{t("objectives") + " · " + (t("mode." + mode) === "mode." + mode ? mode : t("mode." + mode)) + (mode === "at_least" ? " " + (quest.objectiveThreshold || 1) : "")}</h4>
										{quest.objectives.map((o) =>
											tile(
												o.id,
												objectiveIcon(o),
												objectiveLabel(o, language) + (o.optional ? " · " + t("optional") : ""),
												[value(o), target(o)]
											)
										)}
									</>
								) : (
									!done && <p className="tp-line">{t(quest.acknowledgement === "manual" ? "information_review" : "information_read")}</p>
								)}
								{required.length > 0 && (
									<>
										<h4 className="tp-heading">{t("requires")}</h4>
										{required.map((id) => {
											const q = project.quests.find((x) => x.id === id)!;
											return (
												<div className="tp-link" key={id}>
													<ItemIcon id={q.icon} size={20} />
													<span>{text(q.title, language) || id}</span>
													<span className={"tp-swatch " + (status === "locked" ? "available" : "completed")} aria-hidden="true" />
													<span aria-hidden="true">›</span>
												</div>
											);
										})}
									</>
								)}
								<h4 className="tp-heading">{t("rewards")}</h4>
								{quest.rewards.map((r, i) => tile(i, rewardIcon(r), rewardLabel(r)))}
								{(quest.tiers || []).map((tier: { id: string; threshold: number; rewards: Reward[] }) => (
									<React.Fragment key={tier.id}>
										<h4 className="tp-heading">{t("tier", tier.threshold)}</h4>
										{(tier.rewards || []).map((r, i) => tile(i, rewardIcon(r), rewardLabel(r)))}
									</React.Fragment>
								))}
								{quest.timeLimitSeconds > 0 && <p className="tp-line">{t("time_limit", quest.timeLimitSeconds)}</p>}
								{quest.noDeath && <p className="tp-line">{t("no_death")}</p>}
							</div>
						</section>
					</div>
				</div>
			</div>
		</Dialog>
	);
}
