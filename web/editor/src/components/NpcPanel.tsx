import { Pencil, Users, X } from "lucide-react";
import { formatPercent, placeName, VILLAGER_TYPES } from "../core/npc-spawns.js";
import { text } from "../core/project.js";
import type { Project, Quest } from "../types";
import { getUiLocale, tUI } from "../ui/ui-i18n.js";
import type { Npc } from "./QuestGiverLibrary";

type Props = {
	project: Project;
	quest: Quest;
	language: string;
	onConfig: (change: (config: any) => void, field?: string) => void;
	onManage: (npc: string) => void;
};

/** Every quest giver that offers this quest, one card each. The givers themselves are edited in their library. */
export function NpcPanel({ project, quest, language, onConfig, onManage }: Props) {
	const npcs: Npc[] = project.config?.npcs || [];
	const givers = npcs.filter((npc) => npc.quests.includes(quest.id));
	const others = npcs.filter((npc) => !npc.quests.includes(quest.id));
	const ui = (value: string) => tUI(value);
	const name = (npc: Npc) => text(npc.name, language) || npc.id;
	const offer = (id: string, offered: boolean) =>
		onConfig((config) => {
			const npc = config.npcs.find((row: Npc) => row.id === id);
			npc.quests = offered ? [...npc.quests, quest.id] : npc.quests.filter((other: string) => other !== quest.id);
		}, "config:npc:offer");
	const look = (npc: Npc) =>
		npc.model === "player"
			? ui(npc.skin ? "Your skin" : "QuestPlus skin")
			: ui("Villager") + " · " + ui(VILLAGER_TYPES.find((type) => type.id === (npc.villager ?? "plains"))?.name ?? "Plains");
	const places = (npc: Npc) =>
		(npc.spawns || []).map((rule) => ui(placeName(rule.target)) + " " + formatPercent(rule.chance, getUiLocale()) + " %").join(" · ") ||
		ui("Nowhere yet: place it with a command");
	return (
		<section className="giver-offer">
			<div className="detail-heading">
				<strong>{ui("Offered by")}</strong>
			</div>
			<p>
				{ui(
					givers.length
						? "A quest offered by an NPC starts only when the player accepts it from that NPC, and stays off the tablet until then."
						: "No quest giver offers this quest: it starts on its own, like any other quest."
				)}
			</p>
			{givers.length > 0 && (
				<ul className="giver-cards">
					{givers.map((npc) => (
						<li key={npc.id}>
							<header>
								<strong>{name(npc)}</strong>
								<button type="button" className="icon-button" aria-label={ui("Edit") + " " + name(npc)} title={ui("Edit")} onClick={() => onManage(npc.id)}>
									<Pencil size={14} />
								</button>
								<button
									type="button"
									className="icon-button"
									aria-label={ui("Remove") + " " + name(npc)}
									title={ui("Remove")}
									onClick={() => offer(npc.id, false)}>
									<X size={15} />
								</button>
							</header>
							<dl className="giver-summary">
								<dt>{ui("Looks like")}</dt>
								<dd>{look(npc)}</dd>
								<dt>{ui("Appears in")}</dt>
								<dd>{places(npc)}</dd>
							</dl>
						</li>
					))}
				</ul>
			)}
			<div className="giver-offer-actions">
				{others.length > 0 && (
					<label className="field">
						<span>{ui(givers.length ? "Also offered by" : "Offer it from")}</span>
						<select aria-label={ui("Add a quest giver")} value="" onChange={(e) => e.target.value && offer(e.target.value, true)}>
							<option value="" disabled>
								{ui("Choose a quest giver…")}
							</option>
							{others.map((npc) => (
								<option key={npc.id} value={npc.id}>
									{name(npc)}
								</option>
							))}
						</select>
					</label>
				)}
				<button type="button" className="button secondary" onClick={() => onManage(givers[0]?.id || "")}>
					<Users size={15} aria-hidden="true" />
					{ui(npcs.length ? "Manage quest givers" : "Create a quest giver")}
				</button>
			</div>
		</section>
	);
}
