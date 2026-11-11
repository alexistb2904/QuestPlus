import { ArrowDown, ArrowUp, Copy, Layers, PenLine, Plus, RefreshCw, Trash2, TriangleAlert } from "lucide-react";
import { useEffect, useState } from "react";
import { hubEditorDescriptors, hubFieldDefault } from "../core/hub-registry.js";
import { text } from "../core/project.js";
import { crateReward, crateTableName, POOL_TYPES, poolError, poolProbabilities, simulatePool } from "../core/reward-tables.js";
import type { LocaleMap, Project, Reward } from "../types";
import { REWARD_GROUPS, REWARD_HINTS } from "../ui/type-catalog.js";
import { tUI as t } from "../ui/ui-i18n.js";
import { ItemIcon, TargetPicker } from "./IconPicker";
import { Disclosure, Field, Select } from "./Inspector";
import { catalogOptions, InfoTip, TypePicker, type TypeOption } from "./TypePicker";

const types: [string, string][] = [
	["item", "Item"],
	["experience", "XP points"],
	["experience_levels", "XP Levels"],
	["questplus:advancement", "Advancement"],
	["questplus:message", "Message"],
	["questplus:stage", "Stage"],
	["questplus:toast", "Toast"],
];
const poolTypes: [string, string][] = [
	["questplus:choice", "Player Choice"],
	["questplus:random", "Weighted Random"],
	["questplus:loot", "Loot Chance"],
	["questplus:all", "All Table"],
];
const make = (type: string): Reward =>
	type === "item"
		? { type, item: "minecraft:diamond", count: 1 }
		: type.startsWith("experience")
			? { type, amount: 10 }
			: type === "questplus:advancement"
				? { type, data: { advancement: "minecraft:story/mine_stone" } }
				: type === "questplus:message"
					? { type, data: { message: "Congratulations!" } }
					: type === "questplus:stage"
						? { type, data: { stage: "questplus:chapter", remove: false } }
						: type === "questplus:toast"
							? { type, data: { title: { en_us: "Quest update" }, description: { en_us: "A new chapter awaits" }, icon: "minecraft:book" } }
							: POOL_TYPES.includes(type)
								? {
										type,
										data: {
											options: [
												{ ...make("item"), ...(type === "questplus:choice" ? {} : { weight: 1 }) },
												{ ...make("experience"), ...(type === "questplus:choice" ? {} : { weight: 1 }) },
											],
											...(type === "questplus:loot" ? { emptyWeight: 1 } : {}),
											...(["questplus:random", "questplus:loot"].includes(type) ? { rolls: 1 } : {}),
										},
									}
								: {
										type,
										data: Object.fromEntries(
											(hubEditorDescriptors("reward").find((d) => d.id === type)?.fields || []).map((f) => [
												f.key,
												hubFieldDefault(f),
											])
										),
									};
const typeNames: Record<string, string> = Object.fromEntries([
		...types,
		["questplus:table", "Reward table"],
		["questplus:crate", "Loot crate"],
		...poolTypes,
		["questplus:command", "Command"],
	]),
	optionNames: Record<string, string> = Object.fromEntries(types);
const move = (list: any[], index: number, delta: number) => {
	const copy = [...list];
	[copy[index], copy[index + delta]] = [copy[index + delta], copy[index]];
	return copy;
};
export function ItemComponentField({ value, onChange }: { value?: Record<string, unknown>; onChange: (value: Record<string, unknown> | undefined) => void }) {
	const encoded = JSON.stringify(value || {}, null, 2),
		[draft, setDraft] = useState(encoded),
		[error, setError] = useState("");
	useEffect(() => {
		setDraft(encoded);
		setError("");
	}, [encoded]);
	return (
		<label className="field">
			<span>{t("Item components (JSON)")}</span>
			<textarea
				aria-invalid={!!error}
				value={draft}
				onChange={(e) => {
					setDraft(e.target.value);
					try {
						const data = JSON.parse(e.target.value);
						if (!data || typeof data !== "object" || Array.isArray(data) || JSON.stringify(data).length > 16384) throw Error(t("Invalid item components"));
						onChange(Object.keys(data).length ? data : undefined);
						setError("");
					} catch {
						setError(t("Invalid item components"));
					}
				}}
			/>
			{error && <span role="alert">{error}</span>}
		</label>
	);
}
function Translation({ label, value, language, onChange }: { label: string; value?: LocaleMap; language: string; onChange: (v: LocaleMap | undefined) => void }) {
	const edit = (locale: string, s: string) => {
		const next = { ...value };
		if (s) next[locale] = s;
		else delete next[locale];
		onChange(Object.keys(next).length ? next : undefined);
	};
	return (
		<>
			<Field label={t(label) + " · en_us"} value={value?.en_us || ""} onChange={(v) => edit("en_us", v)} />
			{language !== "en_us" && <Field label={t(label) + " · " + language} value={value?.[language] || ""} onChange={(v) => edit(language, v)} />}
		</>
	);
}
type Table = NonNullable<Project["rewardTables"]>[number];
const policy = (r: Reward) => Object.fromEntries(["text", "teamReward", "autoClaim", "excludeFromClaimAll"].filter((key) => (r as any)[key] !== undefined).map((key) => [key, (r as any)[key]]));
/** A quest reward that draws a named table: choose the table, open it, or turn the link into an editable copy. */
function TableLink({
	reward: r,
	options,
	table,
	onChange,
	onOpenTables,
}: {
	reward: Reward;
	options: TypeOption[];
	table?: Table;
	onChange: (value: Reward) => void;
	onOpenTables?: (name?: string) => void;
}) {
	return (
		<div className="reward-table-link">
			<TypePicker label="Table" value={r.data?.name || ""} options={options} translate={false} placeholder="Choose a table…" onChange={(name) => onChange({ ...r, data: { name } })} />
			<p>{t("When the quest is claimed, the server draws this table. Edit the table to change what every linked quest gives.")}</p>
			<div className="toolbar-row">
				{onOpenTables && (
					<button type="button" className="button secondary" onClick={() => onOpenTables(r.data?.name)}>
						<PenLine size={14} />
						{t("Edit table")}
					</button>
				)}
				<button type="button" className="button secondary" disabled={!table} onClick={() => table && onChange({ ...structuredClone(table.reward), ...policy(r) })}>
					<Copy size={14} />
					{t("Make an editable copy")}
				</button>
				<InfoTip text={t("Replaces the link with a copy of the table that you edit here. Later changes to the table no longer apply.")} />
			</div>
		</div>
	);
}
/** A quest reward that gives the crate item of a named table. The player opens it later in Minecraft. */
function CrateReward({
	reward: r,
	options,
	tables,
	onChange,
	onOpenTables,
	onEnableCrate,
}: {
	reward: Reward;
	options: TypeOption[];
	tables: Table[];
	onChange: (value: Reward) => void;
	onOpenTables?: (name?: string) => void;
	onEnableCrate?: (name: string) => void;
}) {
	const name = crateTableName(r) || "",
		table = tables.find((row) => row.name === name),
		fresh = table && crateReward(table, r.count),
		stale = fresh && JSON.stringify(fresh.components) !== JSON.stringify(r.components);
	return (
		<div className="reward-crate">
			<div className="reward-crate-fields">
				<TypePicker
					label="Table"
					value={name}
					options={options}
					translate={false}
					onChange={(next) => {
						const target = tables.find((row) => row.name === next);
						if (!target) return;
						onChange({ ...r, ...crateReward(target, r.count) });
						if (!target.crate && target.reward.type !== "questplus:choice") onEnableCrate?.(target.name);
					}}
				/>
				<Field label="Count" type="number" value={r.count} onChange={(count) => onChange({ ...r, count: Math.max(1, Math.min(64, Math.floor(count) || 1)) })} />
			</div>
			<p>{t("The player receives this crate and opens it in Minecraft (right-click) to draw the table.")}</p>
			{table && !table.crate && (
				<div className="reward-notice" role="alert">
					<TriangleAlert size={15} aria-hidden="true" />
					<span>{t(table.reward.type === "questplus:choice" ? "Crates cannot use Player Choice. Choose another draw type." : "This table is not an openable crate yet.")}</span>
					{table.reward.type !== "questplus:choice" && onEnableCrate && (
						<button type="button" className="button secondary" onClick={() => onEnableCrate(table.name)}>
							{t("Make it a crate")}
						</button>
					)}
				</div>
			)}
			{stale && (
				<div className="reward-notice">
					<RefreshCw size={15} aria-hidden="true" />
					<span>{t("The crate name or color changed in the table.")}</span>
					<button type="button" className="button secondary" onClick={() => onChange({ ...r, components: fresh.components })}>
						{t("Update name and color")}
					</button>
				</div>
			)}
			{onOpenTables && (
				<div className="toolbar-row">
					<button type="button" className="button secondary" onClick={() => onOpenTables(name)}>
						<PenLine size={14} />
						{t("Edit table")}
					</button>
				</div>
			)}
		</div>
	);
}
export function RewardEditor({
	rewards,
	onChange,
	language,
	project,
	onAdvanced,
	onSaveTable,
	onOpenTables,
	onEnableCrate,
	singlePool = false,
}: {
	rewards: Reward[];
	onChange: (rewards: Reward[]) => void;
	language: string;
	project: Project;
	onAdvanced: () => void;
	onSaveTable: (index: number) => void;
	onOpenTables?: (name?: string) => void;
	onEnableCrate?: (name: string) => void;
	singlePool?: boolean;
}) {
	const tables = project.rewardTables || [];
	const tableOptions = (current?: string): TypeOption[] => {
		const list: TypeOption[] = tables.map((table) => ({
			id: table.name,
			name: table.name,
			icon: table.crate ? "minecraft:chest_minecart" : "minecraft:bundle",
			group: table.crate ? t("Crates") : t("Reward tables"),
			hint: [t(typeNames[table.reward.type] || table.reward.type), (table.reward.data?.options?.length ?? 0) + " " + t("options"), table.crate ? "" : t("Not a crate yet")]
				.filter(Boolean)
				.join(" · "),
		}));
		if (current && !tables.some((table) => table.name === current)) list.push({ id: current, name: current, hint: t("Unknown table") });
		return list;
	};
	const [newType, setNewType] = useState("item"),
		[result, setResult] = useState<{ index: number; values: any[] } | null>(null),
		[preview, setPreview] = useState<Record<number, number>>({});
	const [picker, setPicker] = useState<{ kind: any; pick: (id: string) => void } | null>(null);
	const update = (i: number, value: Reward) => {
		onChange(rewards.map((r, j) => (i === j ? value : r)));
		setResult(null);
	};
	const label = (r: Reward) =>
		[
			text(r.text, language),
			r.type === "item"
				? `${r.count} × ${String(r.item).replace(/^minecraft:/, "")}`
				: r.type === "experience"
					? `${r.amount} XP`
					: r.type === "experience_levels"
						? `${r.amount} ${t("XP Levels")}`
						: r.type === "questplus:advancement"
							? r.data?.advancement
							: r.type === "questplus:message"
								? r.data?.message
								: r.type,
		]
			.filter(Boolean)
			.join(" · ");
	const kindOf = (r: Reward) => (crateTableName(r) !== undefined ? "questplus:crate" : r.type);
	const icon = (r: Reward) =>
		crateTableName(r) !== undefined
			? "minecraft:chest_minecart"
			: r.type === "item"
				? r.item
				: r.type.startsWith("experience")
					? "minecraft:experience_bottle"
					: "minecraft:bundle";
	const needsTable = newType === "questplus:table" || newType === "questplus:crate";
	const addReward = () => {
		if (needsTable && !tables.length) return onOpenTables?.();
		if (newType === "questplus:table") return onChange([...rewards, { type: "questplus:table", data: { name: tables[0].name } }]);
		if (newType === "questplus:crate") {
			const table = tables.find((row) => row.crate) || tables[0];
			onChange([...rewards, crateReward(table)]);
			// Giving a crate is the intent: make the table openable unless its draw type forbids it.
			if (!table.crate && table.reward.type !== "questplus:choice") onEnableCrate?.(table.name);
			return;
		}
		onChange([...rewards, make(newType)]);
	};
	const reorder = (list: Reward[], i: number, onEdit: (v: Reward[]) => void) => (
		<span className="reward-order">
			<button type="button" className="tiny" aria-label={t("Move up")} disabled={i === 0} onClick={() => onEdit(move(list, i, -1))}>
				<ArrowUp size={14} />
			</button>
			<button type="button" className="tiny" aria-label={t("Move down")} disabled={i === list.length - 1} onClick={() => onEdit(move(list, i, 1))}>
				<ArrowDown size={14} />
			</button>
		</span>
	);
	const inputs = (r: Reward, edit: (value: Reward) => void) =>
		r.type === "item" ? (
			<>
				<div className="field-row reward-item-fields">
					<Field label="Item ID" value={r.item} onChange={(item) => edit({ ...r, item })} />
					<button className="button secondary" type="button" onClick={() => setPicker({ kind: "item", pick: (item) => edit({ ...r, item }) })}>
						{t("Browse")}
					</button>
					<Field label="Count" type="number" value={r.count} onChange={(count) => edit({ ...r, count })} />
				</div>
				<Disclosure title="Item components (JSON)">
					<ItemComponentField
						value={r.components}
						onChange={(components) => {
							const next = { ...r };
							if (components) next.components = components;
							else delete next.components;
							edit(next);
						}}
					/>
				</Disclosure>
				<div className="field-row">
					<Field label="Random bonus" type="number" value={r.randomBonus ?? 0} onChange={(randomBonus) => edit({ ...r, randomBonus })} />
					<label className="checkbox-row">
						<input type="checkbox" checked={!!r.onlyOne} onChange={(e) => edit({ ...r, onlyOne: e.target.checked })} />
						{t("Only if not already owned")}
					</label>
				</div>
			</>
		) : r.type.startsWith("experience") ? (
			<Field label={r.type === "experience" ? "XP points" : "XP Levels"} type="number" value={r.amount} onChange={(amount) => edit({ ...r, amount })} />
		) : r.type === "questplus:advancement" ? (
			<>
				<div className="field-row">
					<Field label="Advancement ID" value={r.data?.advancement} onChange={(advancement) => edit({ ...r, data: { ...r.data, advancement } })} />
					<button
						type="button"
						className="button secondary"
						onClick={() => setPicker({ kind: "advancement", pick: (advancement) => edit({ ...r, data: { ...r.data, advancement } }) })}>
						{t("Browse")}
					</button>
				</div>
				<Field
					label="Criterion (optional)"
					value={r.data?.criterion || ""}
					onChange={(criterion) => {
						const data = { ...r.data };
						if (criterion) data.criterion = criterion;
						else delete data.criterion;
						edit({ ...r, data });
					}}
				/>
			</>
		) : r.type === "questplus:message" ? (
			<Field label="Message to player" value={r.data?.message} onChange={(message) => edit({ ...r, data: { message } })} />
		) : r.type === "questplus:stage" ? (
			<>
				<Field label="Stage ID" value={r.data?.stage} onChange={(stage) => edit({ ...r, data: { ...r.data, stage } })} />
				<label className="checkbox-row">
					<input type="checkbox" checked={!!r.data?.remove} onChange={(e) => edit({ ...r, data: { ...r.data, remove: e.target.checked } })} />
					{t("Remove stage")}
				</label>
			</>
		) : r.type === "questplus:toast" ? (
			<>
				<Translation label="Title" value={r.data?.title} language={language} onChange={(title) => edit({ ...r, data: { ...r.data, title } })} />
				<Translation label="Description" value={r.data?.description} language={language} onChange={(description) => edit({ ...r, data: { ...r.data, description } })} />
				<Field label="Icon" value={r.data?.icon} onChange={(icon) => edit({ ...r, data: { ...r.data, icon } })} />
			</>
		) : r.type === "questplus:command" ? (
			<>
				<p>{t("Command rewards require administrator opt-in on the server.")}</p>
				<Field label="Server command" value={r.data?.command} onChange={(command) => edit({ ...r, data: { ...r.data, command } })} />
			</>
		) : (
			<>
				{hubEditorDescriptors("reward")
					.find((d) => d.id === r.type)
					?.fields.map((f) => (
						<div key={f.key}>
							{f.type === "boolean" ? (
                                <label className="checkbox-row">
                                    <input type="checkbox" checked={!!r.data?.[f.key]} onChange={(e) => edit({ ...r, data: { ...r.data, [f.key]: e.target.checked } })} />
                                    {f.label}
                                </label>
                            ) : f.type === "choice" ? (
                                <label className="field">
                                    <span>{f.label}</span>
                                    <select value={r.data?.[f.key] ?? hubFieldDefault(f)}
                                        onChange={(e) => edit({ ...r, data: { ...r.data, [f.key]: e.target.value } })}>
                                        {f.options.map((option: string) => <option key={option} value={option}>{option}</option>)}
                                    </select>
                                </label>
                            ) : (
								<div className="field-row">
									<Field
										label={f.label}
										type={["integer", "number"].includes(f.type) ? "number" : "text"}
										value={r.data?.[f.key]}
										onChange={(value) => edit({ ...r, data: { ...r.data, [f.key]: value } })}
									/>
									{!["string", "integer", "number"].includes(f.type) && (
										<button
											className="button secondary"
											type="button"
											onClick={() => setPicker({ kind: f.type, pick: (id) => edit({ ...r, data: { ...r.data, [f.key]: id } }) })}>
											{t("Browse")}
										</button>
									)}
								</div>
							)}
						</div>
					)) || (
					<button className="button secondary" type="button" onClick={onAdvanced}>
{t("Extension unavailable in Studio. Its parameters are preserved; edit in Advanced JSON.")}
					</button>
				)}
			</>
		);
	return (
		<div className="reward-editor">
			{!singlePool && (
				<div className="reward-add">
					<TypePicker
						label="Reward type"
						value={newType}
						options={catalogOptions(REWARD_GROUPS, typeNames, REWARD_HINTS, hubEditorDescriptors("reward"))}
						onChange={setNewType}
					/>
					<button type="button" className="button primary" disabled={rewards.length >= 64 || (needsTable && !tables.length && !onOpenTables)} onClick={addReward}>
						<Plus size={14} />
						{t(needsTable && !tables.length ? "Create reward table" : "Reward")}
					</button>
					{needsTable && !tables.length && <small className="reward-add-note">{t("Create a table first to use this reward.")}</small>}
					{onOpenTables && (
						<button type="button" className="button secondary" onClick={() => onOpenTables()}>
							<Layers size={14} />
							{t("Manage tables")}
						</button>
					)}
				</div>
			)}
			{!rewards.length && <p className="reward-empty">{t("No rewards")}</p>}
			{rewards.map((r, i) => {
				const pool = POOL_TYPES.includes(r.type),
					choice = r.type === "questplus:choice",
					all = r.type === "questplus:all",
					options: Reward[] = r.data?.options || [],
					probabilities = pool ? poolProbabilities(r) : [],
					error = pool ? poolError(r) : "";
				const edit = (value: Reward) => update(i, value),
					editData = (data: any) => edit({ ...r, data: { ...r.data, ...data } }),
					editOptions = (options: Reward[]) => editData({ options });
				return (
					<div className={singlePool ? "mini-card reward-pool" : "mini-card"} key={i}>
						<div className="mini-card-head">
							<ItemIcon id={icon(r)} size={24} />
							<strong>{t(typeNames[kindOf(r)] || hubEditorDescriptors("reward").find((d) => d.id === r.type)?.label || r.type)}</strong>
							<InfoTip text={REWARD_HINTS[kindOf(r)] && t(REWARD_HINTS[kindOf(r)])} />
							{!singlePool && (
								<>
									{reorder(rewards, i, onChange)}
									<button type="button" className="tiny danger" aria-label={t("Remove reward")} onClick={() => onChange(rewards.filter((_, j) => j !== i))}>
										<Trash2 size={14} />
									</button>
								</>
							)}
						</div>
						{r.type === "questplus:table" ? (
							<TableLink
								reward={r}
								options={tableOptions(r.data?.name)}
								table={tables.find((table) => table.name === r.data?.name)}
								onChange={edit}
								onOpenTables={onOpenTables}
							/>
						) : crateTableName(r) !== undefined ? (
							<CrateReward
								reward={r}
								options={tableOptions(crateTableName(r))}
								tables={tables}
								onChange={edit}
								onOpenTables={onOpenTables}
								onEnableCrate={onEnableCrate}
							/>
						) : pool ? (
							<div className="reward-pool-layout">
								<div className="reward-pool-main">
								<p>
									{t(
										choice
											? "The player confirms one option. The server saves the choice before claiming."
											: "Rolls are selected when the quest completes, and stored until claimed."
									)}
								</p>
								<div className="field-row reward-pool-settings">
									{!choice && !all && <Field label="Rolls" type="number" value={r.data?.rolls ?? 1} onChange={(rolls) => editData({ rolls })} />}
									{r.type === "questplus:loot" && (
										<Field label="Empty chance weight" type="number" value={r.data?.emptyWeight} onChange={(emptyWeight) => editData({ emptyWeight })} />
									)}
								</div>
								<div className="reward-options">
								{options.map((option, j) => (
									<div className="mini-card reward-option" key={j}>
										<div className="mini-card-head">
											<strong>
												{t("Option")} {j + 1}
											</strong>
											{probabilities[j] != null && <small>{(probabilities[j] * 100).toFixed(2)}%</small>}
											{reorder(options, j, editOptions)}
											<button
												type="button"
												className="tiny danger"
												disabled={options.length <= 1}
												aria-label={t("Remove option")}
												onClick={() => editOptions(options.filter((_, k) => k !== j))}>
												<Trash2 size={14} />
											</button>
										</div>
										<TypePicker
											label="Type"
											value={option.type}
											options={catalogOptions(
												REWARD_GROUPS,
												optionNames,
												REWARD_HINTS,
												hubEditorDescriptors("reward").filter((d) => d.pool)
											)}
											onChange={(type) =>
												editOptions(
													options.map((o, k) =>
														k === j
															? { ...make(type), ...(!choice ? { weight: option.weight ?? 1 } : {}), ...(option.text ? { text: option.text } : {}) }
															: o
													)
												)
											}
										/>
										{!choice && !all && (
											<Field
												label="Weight"
												type="number"
												value={option.weight}
												onChange={(weight) => editOptions(options.map((o, k) => (j === k ? { ...o, weight } : o)))}
											/>
										)}
										{inputs(option, (value) => editOptions(options.map((o, k) => (k === j ? value : o))))}
										<Disclosure title="Custom label">
											<Translation
												label="Title"
												value={option.text}
												language={language}
												onChange={(text) =>
													editOptions(
														options.map((o, k) => {
															if (k !== j) return o;
															const next = { ...o };
															if (text) next.text = text;
															else delete next.text;
															return next;
														})
													)
												}
											/>
										</Disclosure>
									</div>
								))}
								</div>
								<button
									className="button secondary reward-add-option"
									type="button"
									disabled={options.length >= 32}
									onClick={() => editOptions([...options, { ...make("item"), ...(!choice ? { weight: 1 } : {}) }])}>
									+ {t("Option")}
								</button>
								<Disclosure title="Description">
									<Translation
										label="Description"
										value={r.data?.description}
										language={language}
										onChange={(description) => {
											const data = { ...r.data };
											if (description) data.description = description;
											else delete data.description;
											edit({ ...r, data });
										}}
									/>
								</Disclosure>
								{error && (
									<p role="alert" className="reward-error">
										{t(error)}
									</p>
								)}
								</div>
								<div className="reward-pool-side">
								<strong className="reward-side-title">{t("Draw preview")}</strong>
								<div className="reward-preview" aria-label={t("Tablet preview")}>
									{options.map((option, j) => (
										<button
											type="button"
											className={"reward-preview-card" + (preview[i] === j ? " selected" : "")}
											key={j}
											aria-pressed={preview[i] === j}
											onClick={() => setPreview({ ...preview, [i]: j })}>
											<ItemIcon id={icon(option)} size={28} />
											<span>{label(option)}</span>
											{probabilities[j] != null && <small>{(probabilities[j] * 100).toFixed(2)}%</small>}
										</button>
									))}
									{r.type === "questplus:loot" && (
										<p>
											{t("Nothing")} · {((probabilities.at(-1) || 0) * 100).toFixed(2)}%
										</p>
									)}
								</div>
								<div className="toolbar-row">
									{!choice && (
										<button type="button" className="button secondary" disabled={!!error} onClick={() => setResult({ index: i, values: simulatePool(r) })}>
											{t("Simulate draw")}
										</button>
									)}
									{!singlePool && (
										<button type="button" className="button secondary" disabled={!!error} onClick={() => onSaveTable(i)}>
											{t("Save named table")}
										</button>
									)}
								</div>
								{result?.index === i && (
									<output className="reward-simulation" aria-live="polite">
										{t("Simulation only. No rewards are granted.")} {result.values.map((v) => (v ? label(v) : t("Nothing"))).join(" / ")}
									</output>
								)}
								</div>
							</div>
						) : (
							inputs(r, edit)
						)}
						<Disclosure title="Custom label">
							<Translation
								label="Title"
								value={r.text}
								language={language}
								onChange={(text) => {
									const value = { ...r };
									if (text) value.text = text;
									else delete value.text;
									edit(value);
								}}
							/>
						</Disclosure>
						{!singlePool && (
							<Disclosure title="Reward delivery">
								<label className="checkbox-row">
									<input type="checkbox" checked={!!r.teamReward} onChange={(e) => edit({ ...r, teamReward: e.target.checked })} />
									{t("One reward for the whole team")}
								</label>
								<Select
									label="Automatic claim"
									value={r.autoClaim || "disabled"}
									options={["disabled", "enabled", "no_toast", "invisible"].map((value) => [value, t(value)] as [string, string])}
									onChange={(autoClaim) => edit({ ...r, autoClaim })}
								/>
								<label className="checkbox-row">
									<input type="checkbox" checked={!!r.excludeFromClaimAll} onChange={(e) => edit({ ...r, excludeFromClaimAll: e.target.checked })} />
									{t("Exclude from claim all")}
								</label>
							</Disclosure>
						)}
					</div>
				);
			})}
			{picker && (
				<TargetPicker
					kind={picker.kind}
					onClose={() => setPicker(null)}
					onPick={(id) => {
						picker.pick(id);
						setPicker(null);
					}}
				/>
			)}
		</div>
	);
}
