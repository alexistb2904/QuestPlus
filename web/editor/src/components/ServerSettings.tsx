import type { ReactNode } from "react";
import { CONFIG_KEY_SINCE, hubAtLeast } from "../core/hub-registry.js";
import { targetModVersion } from "../core/mod-versions.js";
import { parseCrateBosses } from "../core/project.js";
import type { Project } from "../types";
import { tUI } from "../ui/ui-i18n.js";

type OnConfig = (change: (config: any) => void, field?: string) => void;
const NO_DROP: Record<string, number> = { passive: 4000, monster: 600, boss: 0 };
const integer = (text: string, min: number, max: number, fallback: number) => {
	const n = Number(text);
	return text.trim() && Number.isInteger(n) ? Math.min(max, Math.max(min, n)) : fallback;
};

/** Offline, these settings write the exported ZIP's config.json for the pack's target QuestPlus version. Connected, the server's version gates them. */
export function ServerSettings({ project, onConfig, version, offline = false }: { project: Project; onConfig: OnConfig; version: string; offline?: boolean }) {
	const config = project.config || {};
	const noDrop = { ...NO_DROP, ...config.crateNoDrop };
	const gate = (key: keyof typeof CONFIG_KEY_SINCE, control: ReactNode) => {
		const since = CONFIG_KEY_SINCE[key],
			gateVersion = offline ? targetModVersion(project) : version,
			locked = !gateVersion || !hubAtLeast(since, gateVersion);
		return (
			<fieldset className="hub-setting" disabled={locked}>
				{control}
				{gateVersion && locked && <small className="hub-setting-lock">{tUI("Requires QuestPlus") + " " + since}</small>}
			</fieldset>
		);
	};
	const flag = (key: keyof typeof CONFIG_KEY_SINCE, label: string, fallback = false) =>
		gate(
			key,
			<label className="checkbox-row">
				<input type="checkbox" checked={config[key] ?? fallback} onChange={(e) => onConfig((c) => (c[key] = e.target.checked), "config:" + key)} />
				{tUI(label)}
			</label>
		);
	return (
		<>
			<header className="hub-section-head">
				<h3>{tUI(offline ? "Pack settings" : "Server settings")}</h3>
				<p>
					{tUI(
						offline
							? "Edits the config.json of the exported ZIP. Connect a server to edit its own settings."
							: version
								? "Edits config.json. Changes apply when you save to the server."
								: "Load the server quests to edit its settings."
					)}
				</p>
			</header>
			<section className="hub-group">
				<h4>{tUI("Saving and teams")}</h4>
				<div className="hub-group-fields">
					{gate(
						"autosaveSeconds",
						<label className="field">
							<span>{tUI("Autosave interval (seconds)")}</span>
							<input
								type="number"
								min={1}
								max={300}
								key={"autosave" + config.autosaveSeconds}
								defaultValue={config.autosaveSeconds ?? 5}
								onBlur={(e) => onConfig((c) => (c.autosaveSeconds = integer(e.target.value, 1, 300, c.autosaveSeconds ?? 5)), "config:autosaveSeconds")}
							/>
						</label>
					)}
					{gate(
						"teamRewardPolicy",
						<label className="field">
							<span>{tUI("Team rewards")}</span>
							<select value={config.teamRewardPolicy || "all_members"} onChange={(e) => onConfig((c) => (c.teamRewardPolicy = e.target.value), "config:teamRewardPolicy")}>
								<option value="all_members">{tUI("Every member")}</option>
								<option value="contributor">{tUI("Contributor only")}</option>
							</select>
						</label>
					)}
				</div>
				{flag("allowDuplicateTeamNames", "Allow duplicate team names", true)}
			</section>
			<section className="hub-group">
				<h4>{tUI("Rewards")}</h4>
				{flag("npcTurnIn", "Claim NPC quest rewards at the NPC", true)}
				{flag("waitForInventorySpace", "Wait for inventory space instead of dropping rewards")}
				<p className="picker-help">{tUI("Quest givers are edited from a quest's NPC tab.")}</p>
				{offline
					? flag("enableCommandRewards", "Enable server command rewards (trusted packs)")
					: <p className="picker-help">{tUI("Command rewards can only be enabled in the server's own config.json, never from the browser.")}</p>}
			</section>
			<section className="hub-group">
				<h4>{tUI("Loot crates")}</h4>
				{flag("dropLootCrates", "Enable loot crate drops")}
				{gate(
					"crateNoDrop",
					<div className="field">
						<span>{tUI("Crate no-drop weights")}</span>
						<div className="hub-group-fields three">
							{Object.keys(NO_DROP).map((group) => (
								<label key={group} className="field">
									<span>{tUI(group)}</span>
									<input
										type="number"
										min={0}
										max={1000000}
										key={group + noDrop[group]}
										defaultValue={noDrop[group]}
										onBlur={(e) => {
											const next = integer(e.target.value, 0, 1000000, noDrop[group]);
											if (next !== noDrop[group]) onConfig((c) => (c.crateNoDrop = { ...NO_DROP, ...c.crateNoDrop, [group]: next }), "config:crateNoDrop");
										}}
									/>
								</label>
							))}
						</div>
					</div>
				)}
				{gate(
					"crateBosses",
					<label className="field" title={tUI("Entity IDs or #tags treated as bosses for crates; prefix ! to exclude")}>
						<span>{tUI("Crate bosses")}</span>
						<input
							key={(config.crateBosses || []).join(", ")}
							defaultValue={(config.crateBosses || []).join(", ")}
							placeholder="mymod:boss, !minecraft:wither"
							onBlur={(e) =>
								onConfig((c) => {
									const list = parseCrateBosses(e.target.value);
									if (list.length) c.crateBosses = list;
									else delete c.crateBosses;
								}, "config:crateBosses")
							}
						/>
					</label>
				)}
			</section>
		</>
	);
}
