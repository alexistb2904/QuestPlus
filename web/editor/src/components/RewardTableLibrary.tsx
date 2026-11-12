import { Check, Copy, Package, Plus, Trash2, TriangleAlert } from "lucide-react";
import { useEffect, useState } from "react";
import { validate } from "../core/project.js";
import { convertPool, crateGiveCommand, POOL_TYPES, poolError, resolveNamedTable } from "../core/reward-tables.js";
import type { Project } from "../types";
import { REWARD_HINTS } from "../ui/type-catalog.js";
import { tUI as t } from "../ui/ui-i18n.js";
import { Dialog } from "./Dialog";
import { RewardEditor } from "./RewardEditor";
import { catalogOptions, InfoTip, TypePicker } from "./TypePicker";

type Table = NonNullable<Project["rewardTables"]>[number];
const poolNames: Record<string, string> = {
	"questplus:choice": "Player Choice",
	"questplus:random": "Weighted Random",
	"questplus:loot": "Loot Chance",
	"questplus:all": "All Table",
};
const drawOptions = catalogOptions([["Reward pools", POOL_TYPES]], poolNames, REWARD_HINTS);
function tableProblem(table: Table, tables: Table[]) {
	try {
		const resolved = resolveNamedTable(table.reward, tables),
			error = poolError(resolved);
		if (error) return error;
		if (table.crate && resolved.type === "questplus:choice") return "Crates cannot contain a player choice";
	} catch (error) {
		return (error as Error).message;
	}
	return "";
}
function NameField({ name, taken, onRename }: { name: string; taken: (name: string) => boolean; onRename: (name: string) => void }) {
	const [draft, setDraft] = useState(name);
	useEffect(() => setDraft(name), [name]);
	const clean = draft.trim(),
		error = !clean ? t("Table name") : clean.length > 80 ? t("Invalid name") : clean !== name && taken(clean) ? t("A table with this name already exists.") : "";
	return (
		<label className="field reward-library-name">
			<span>
				{t("Table name")}
				<InfoTip
					text={t(
						"The table name is its ID: quests and crates refer to it. Renaming updates linked quests, but crates already given to players keep the old name and will no longer open."
					)}
				/>
			</span>
			<input
				value={draft}
				maxLength={80}
				aria-invalid={!!error}
				onChange={(e) => setDraft(e.target.value)}
				onKeyDown={(e) => e.key === "Enter" && e.currentTarget.blur()}
				onBlur={() => (error || clean === name ? setDraft(name) : onRename(clean))}
			/>
			{error && <small className="reward-error">{error}</small>}
		</label>
	);
}
export function RewardTableLibrary({
	project,
	language,
	initial,
	onChange,
	onRename,
	onClose,
}: {
	project: Project;
	language: string;
	initial?: string;
	onChange: (tables: NonNullable<Project["rewardTables"]>) => void;
	onRename: (from: string, to: string) => void;
	onClose: () => void;
}) {
	const tables = project.rewardTables || [],
		[index, setIndex] = useState(() => Math.max(0, tables.findIndex((row) => row.name === initial))),
		[newName, setNewName] = useState(""),
		[copied, setCopied] = useState(false),
		table = tables[index];
	const clean = newName.trim(),
		taken = (name: string) => tables.some((row) => row.name === name),
		canCreate = !!clean && clean.length <= 80 && !taken(clean) && tables.length < 128;
	const create = () => {
		if (!canCreate) return;
		onChange([
			...tables,
			{ name: clean, reward: { type: "questplus:loot", data: { options: [{ type: "item", item: "minecraft:diamond", count: 1, weight: 1 }], emptyWeight: 1, rolls: 1 } } },
		]);
		setIndex(tables.length);
		setNewName("");
	};
	const update = (patch: Partial<Table>) => onChange(tables.map((row, i) => (i === index ? { ...row, ...patch } : row)));
	const errors = validate(project).filter((e: { where: string }) => e.where === "Reward tables");
	const problem = table ? tableProblem(table, tables) : "",
		choice = table?.reward.type === "questplus:choice";
	return (
		<Dialog title={t("Reward tables")} onClose={onClose} className="reward-library-dialog">
			<div className="reward-library">
				<nav className="reward-library-list" aria-label={t("Saved reward tables")}>
					<form
						className="reward-library-new"
						onSubmit={(e) => {
							e.preventDefault();
							create();
						}}>
						<label className="field">
							<span>{t("New table")}</span>
							<input maxLength={80} value={newName} placeholder={t("Table name")} onChange={(e) => setNewName(e.target.value)} />
						</label>
						<button type="submit" className="button primary" disabled={!canCreate}>
							<Plus size={15} />
							{t("Create reward table")}
						</button>
						{clean && taken(clean) && <small className="reward-error">{t("A table with this name already exists.")}</small>}
					</form>
					<ul>
						{tables.map((row, i) => {
							const issue = tableProblem(row, tables),
								count = row.reward.data?.options?.length ?? 0;
							return (
								<li key={row.name}>
									<button type="button" className="reward-library-item" aria-current={i === index} onClick={() => setIndex(i)}>
										<span>
											<strong>{row.name}</strong>
											<small>
												{t(poolNames[row.reward.type] || row.reward.type)} · {count} {t("options")}
											</small>
										</span>
										{row.crate && <Package size={15} aria-label={t("Loot crate")} />}
										{issue && <TriangleAlert size={15} className="reward-library-issue" aria-label={t("Problem")} />}
									</button>
								</li>
							);
						})}
					</ul>
				</nav>
				{table ? (
					<section className="reward-library-main" aria-label={table.name}>
						<header className="reward-library-head">
							<NameField name={table.name} taken={taken} onRename={(name) => onRename(table.name, name)} />
							{POOL_TYPES.includes(table.reward.type) && (
								<TypePicker
									label="Draw type"
									value={table.reward.type}
									options={drawOptions}
									onChange={(type) => update({ reward: convertPool(table.reward, type), ...(type === "questplus:choice" ? { crate: false } : {}) })}
								/>
							)}
							<span className={"reward-library-status" + (problem ? " error" : "")} role="status">
								{problem ? <TriangleAlert size={15} /> : <Check size={15} />}
								{problem ? t("Problem") : t("Valid")}
							</span>
							<button
								type="button"
								className="button danger"
								onClick={() => {
									if (!confirm(t("Delete named table? Linked quests must be updated before export."))) return;
									onChange(tables.filter((_, i) => i !== index));
									setIndex(Math.max(0, index - 1));
								}}>
								<Trash2 size={15} />
								{t("Delete table")}
							</button>
						</header>
						{(problem || errors.length > 0) && (
							<div className="reward-library-alert" role="alert">
								<TriangleAlert size={16} />
								<div>
									<strong>{t("Validation error")}</strong>
									{[...new Set([...(problem ? [problem] : []), ...errors.map((e: { message: string }) => e.message)])].map((message) => (
										<p key={message}>{t(message)}</p>
									))}
								</div>
							</div>
						)}
						<div className="reward-library-columns">
							<div className="reward-library-pool">
								<RewardEditor
									key={table.name}
									singlePool
									rewards={[table.reward]}
									language={language}
									project={project}
									onAdvanced={() => {}}
									onSaveTable={() => {}}
									onChange={(rewards) => update({ reward: rewards[0] })}
								/>
							</div>
							<aside className="reward-library-aside">
								<section className="reward-library-card">
									<h3>{t("How to use this table")}</h3>
									<p>{t("In a quest, open the Rewards tab, pick this table under “From a reward table”, then Link it.")}</p>
								</section>
								<section className="reward-library-card">
									<label className="checkbox-row reward-library-toggle">
										<input type="checkbox" disabled={choice} checked={!!table.crate} onChange={(e) => update({ crate: e.target.checked })} />
										<span>
											<Package size={15} aria-hidden="true" />
											{t("Openable loot crate")}
										</span>
									</label>
									<p>{t(choice ? "Crates cannot use Player Choice. Choose another draw type." : "Turn the table into an item players can open in Minecraft. Opening the crate draws this table once.")}</p>
									{table.crate && (
										<>
											<label className="field">
												<span>
													{t("Crate name")}
													<InfoTip text={t("Name shown on the crate item. Leave empty to use the table name.")} />
												</span>
												<input
													value={table.crateName ?? ""}
													placeholder={table.name}
													maxLength={256}
													onChange={(e) => update({ crateName: e.target.value.trim() ? e.target.value : undefined })}
												/>
											</label>
											<div className="reward-library-look">
												<label className="field">
													<span>{t("Color")}</span>
													<input type="color" value={table.crateColor || "#ffffff"} onChange={(e) => update({ crateColor: e.target.value })} />
												</label>
												<label className="checkbox-row">
													<input type="checkbox" checked={!!table.crateGlow} onChange={(e) => update({ crateGlow: e.target.checked })} />
													{t("Enchantment glint")}
												</label>
											</div>
											<fieldset className="reward-library-drops">
												<legend>
													{t("Mob drops")}
													<InfoTip
														text={t(
															"Relative chance that a killed mob drops this crate. 0 never drops. Drops must also be enabled in Server & sharing > Settings (Enable loot crate drops)."
														)}
													/>
												</legend>
												{(["passive", "monster", "boss"] as const).map((kind) => (
													<label className="field" key={kind}>
														<span>{t(kind === "passive" ? "Passive" : kind === "monster" ? "Monster" : "Boss")}</span>
														<input
															type="number"
															min={0}
															value={table.crateDrops?.[kind] ?? 0}
															onChange={(e) => update({ crateDrops: { passive: 0, monster: 0, boss: 0, ...table.crateDrops, [kind]: Number(e.target.value) } })}
														/>
													</label>
												))}
											</fieldset>
											<div className="reward-library-command">
												<span>
													{t("Give command")}
													<InfoTip text={t("Run this command in Minecraft as an operator to get the crate. It updates live with the name, color and glint.")} />
												</span>
												<code>{crateGiveCommand(table)}</code>
												<button
													type="button"
													className="button secondary"
													onClick={() =>
														navigator.clipboard
															.writeText(crateGiveCommand(table))
															.then(() => {
																setCopied(true);
																setTimeout(() => setCopied(false), 1600);
															})
															.catch(() => {})
													}>
													{copied ? <Check size={14} /> : <Copy size={14} />}
													{t(copied ? "Copied" : "Copy command")}
												</button>
											</div>
										</>
									)}
								</section>
							</aside>
						</div>
					</section>
				) : (
					<div className="library-empty reward-library-main">
						<strong>{t("No reward tables yet")}</strong>
						<span>{t("Create a table to reuse the same reward pool across quests. You can edit its items, chances and draw count here.")}</span>
					</div>
				)}
			</div>
			<footer className="reward-library-footer">
				<span>
					<Check size={15} aria-hidden="true" />
					{t("All changes are saved automatically in the project.")}
				</span>
				<button type="button" className="button primary" onClick={onClose}>
					{t("Done")}
				</button>
			</footer>
		</Dialog>
	);
}
