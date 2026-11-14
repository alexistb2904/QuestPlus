import { Check, ChevronDown, Copy, Filter, Gift, Info, Layers, PenLine, Plus, SlidersHorizontal, Target, Trash2 } from "lucide-react";
import React, { useId, useMemo, useRef, useState } from "react";
import { addToGroup, childrenOf, groupView, setAt, setGroupMode } from "../core/conditions.js";
import { descriptionParts } from "../core/description-format.js";
import { gameLayout } from "../core/game-layout.js";
import { hubEditorDescriptors, hubFieldDefault } from "../core/hub-registry.js";
import { annotationRich, annotationText, orderedCategories, selectedOwner, text } from "../core/project.js";
import { resourceMatcher } from "../core/resource-search.js";
import type { Project, Selection } from "../types";
import { versionWarning } from "../ui/minecraft-icons.js";
import { CONDITION_GROUPS, CONDITION_HINTS } from "../ui/type-catalog.js";
import { tUI } from "../ui/ui-i18n.js";
import { GearEditor, armorPreset } from "./GearEditor";
import { ItemIcon, TargetPicker } from "./IconPicker";
import { DescriptionEditor, RichEditor } from "./RichEditor";
import { catalogOptions, ChoiceCards, InfoTip, TypePicker, type TypeOption } from "./TypePicker";

export type Props = {
	project: Project;
	categoryId?: string | null;
	selection: Selection | null;
	language: string;
	onUpdate: (path: string, value: any) => void;
	onAction: (kind: string, index?: number) => void;
	onChooseIcon: (path: string) => void;
	onCondition: (scope: string, value: any) => void;
	onAddLocale?: (id: string) => void;
	/** Edits config.json (NPCs, turn-in rule) as one undoable change. */
	onConfig?: (change: (config: any) => void, field?: string) => void;
	onOpenTranslations?: () => void;
	translationRequest?: number;
	issues?: { where: string; message: string }[];
};
type FieldProps = { label: string; value: any; onChange: (value: any) => void; type?: string; placeholder?: string; rows?: number };
export function Field({ label, value, onChange, type = "text", placeholder = "", rows }: FieldProps) {
	return (
		<label className="field">
			<span>{tUI(label)}</span>
			{rows ? (
				<textarea aria-label={tUI(label)} rows={rows} value={value ?? ""} placeholder={tUI(placeholder)} onChange={(e) => onChange(e.target.value)} />
			) : (
				<input
					aria-label={tUI(label)}
					type={type}
					value={value ?? ""}
					placeholder={tUI(placeholder)}
					onChange={(e) => onChange(type === "number" ? Number(e.target.value) : e.target.value)}
				/>
			)}
		</label>
	);
}
export function Select({ label, value, options, onChange }: { label: string; value: any; options: [string, string][]; onChange: (v: string) => void }) {
	if (options.length > 10) return <SearchableSelect label={label} value={value} options={options} onChange={onChange} />;
	return (
		<label className="field">
			<span>{tUI(label)}</span>
			<select aria-label={tUI(label)} value={value ?? ""} onChange={(e) => onChange(e.target.value)}>
				{options.map(([id, name]) => (
					<option value={id} key={id}>
						{tUI(name)}
					</option>
				))}
			</select>
		</label>
	);
}
function SearchableSelect({ label, value, options, onChange }: { label: string; value: any; options: [string, string][]; onChange: (v: string) => void }) {
	const [open, setOpen] = useState(false),
		[query, setQuery] = useState(""),
		root = useRef<HTMLDivElement>(null),
		trigger = useRef<HTMLButtonElement>(null),
		listId = useId();
	const match = resourceMatcher(query),
		visible = options.filter(([id, name]) => match(id, tUI(name)));
	const close = () => {
		setOpen(false);
		setQuery("");
	};
	return (
		<div
			className="field search-select"
			ref={root}
			onBlur={(e) => {
				if (!e.currentTarget.contains(e.relatedTarget as Node)) close();
			}}
			onKeyDown={(e) => {
				if (e.key === "Escape" && open) {
					e.stopPropagation();
					close();
					trigger.current?.focus();
				}
				if (e.key === "ArrowDown" && e.target instanceof HTMLInputElement) {
					e.preventDefault();
					root.current?.querySelector<HTMLButtonElement>("[role=option]")?.focus();
				}
				if ((e.key === "ArrowDown" || e.key === "ArrowUp") && e.target instanceof HTMLButtonElement && e.target.getAttribute("role") === "option") {
					e.preventDefault();
					const buttons = Array.from(root.current?.querySelectorAll<HTMLButtonElement>("[role=option]") || []),
						index = buttons.indexOf(e.target);
					buttons[Math.max(0, Math.min(buttons.length - 1, index + (e.key === "ArrowDown" ? 1 : -1)))]?.focus();
				}
			}}>
			<span>{tUI(label)}</span>
			<button
				ref={trigger}
				type="button"
				className="search-select-trigger"
				aria-label={tUI(label)}
				aria-haspopup="listbox"
				aria-expanded={open}
				aria-controls={listId}
				onClick={() => {
					setOpen(!open);
					setQuery("");
				}}>
				{tUI(options.find(([id]) => id === value)?.[1] || String(value || ""))}
				<ChevronDown size={14} />
			</button>
			{open && (
				<div className="search-select-menu">
					<input autoFocus value={query} onChange={(e) => setQuery(e.target.value)} placeholder={tUI("Search…")} aria-label={tUI("Search…")} />
					<div id={listId} role="listbox" aria-label={tUI(label)}>
						{visible.map(([id, name]) => (
							<button
								type="button"
								role="option"
								aria-selected={id === value}
								key={id}
								onClick={() => {
									onChange(id);
									close();
									trigger.current?.focus();
								}}>
								{tUI(name)}
							</button>
						))}
						{!visible.length && <small>{tUI("No results")}</small>}
					</div>
				</div>
			)}
		</div>
	);
}
export function Section({ title, children }: { title: string; children: React.ReactNode }) {
	return (
		<section className="section">
			<div className="section-title">{tUI(title)}</div>
			{children}
		</section>
	);
}
export function Disclosure({ title, children }: { title: string; children: React.ReactNode }) {
	const [open, setOpen] = useState(false),
		id = useId(),
		Icon = title === "Conditions" ? SlidersHorizontal : title === "Item matching" ? Filter : PenLine;
	return (
		<div className={"disclosure" + (open ? " open" : "")}>
			<button type="button" className="disclosure-trigger" aria-expanded={open} aria-controls={id} onClick={() => setOpen((v) => !v)}>
				<Icon size={14} />
				<span>{tUI(title)}</span>
				<ChevronDown className="disclosure-icon" size={14} />
			</button>
			<div className="disclosure-content" id={id} hidden={!open}>
				{children}
			</div>
		</div>
	);
}
export function Translated({ value, prefix, label, onUpdate }: { value: any; prefix: string; label: string; language?: string; onUpdate: (path: string, value: any) => void }) {
	return <Field label={tUI(label) + " · en_us"} value={value?.en_us || ""} onChange={(v) => onUpdate(prefix + ".en_us", v)} />;
}
function IconField({ id, onUpdate, onChooseIcon, project }: { id: string; onUpdate: (v: string) => void; onChooseIcon: () => void; project: Project }) {
	return (
		<div className="field">
			<span>{tUI("Icon")}</span>
			<div className="react-icon-field">
				<ItemIcon id={id} />
				<input
					aria-label={tUI("Icon")}
					spellCheck={false}
					value={id?.startsWith("data:") ? "" : id || ""}
					onChange={(e) => onUpdate(e.target.value)}
					placeholder={id?.startsWith("data:") ? tUI("Custom image") : "minecraft:book / mod:item"}
				/>
				<button type="button" className="button secondary" onClick={onChooseIcon}>
					{tUI("Browse")}
				</button>
			</div>
			{versionWarning(project, id) && <small className="version-alert">{versionWarning(project, id)}</small>}
		</div>
	);
}
const CONDITION_NAMES: Record<string, string> = {
	dimension: "Dimension",
	tool: "Tool",
	biome: "Biome",
	effect: "Effect",
	"questplus:stage": "Stage",
	healthAtMost: "Health ≤",
	healthAtLeast: "Health ≥",
	sneaking: "Sneaking",
	equipment: "Equipment",
	position: "Position",
	context: "Context",
};
const GROUP_MODES: [string, string][] = [
	["and", "all of these are met"],
	["or", "any one of these is met"],
	["at_least", "at least N of these are met"],
	["none", "none of these is met"],
];
const plain = (value: string) =>
	descriptionParts(value)
		.map((part: { text: string }) => part.text)
		.join("")
		.trim();
export function ConditionEditor({
	value,
	scope,
	project,
	onChange,
	currentQuestId,
}: {
	value: any;
	scope: string;
	project: Project;
	onChange: (next: any) => void;
	currentQuestId?: string;
}) {
	const [picker, setPicker] = useState<{ kind: any; pick: (id: string) => void } | null>(null);
	const requires = scope === "requires";
	const categoryOrder = orderedCategories(project.categories).map((c: { id: string }) => c.id);
	const questName = (id: string) => text(project.quests.find((q) => q.id === id)?.title, "en_us") || id;
	const questOptions = (current?: string): TypeOption[] => {
		const list: TypeOption[] = project.quests
			.filter((q) => q.id && q.id !== currentQuestId && !(q.kind === "information" && q.acknowledgement === "read_only"))
			.sort((a, b) => categoryOrder.indexOf(a.category) - categoryOrder.indexOf(b.category))
			.map((q) => ({
				id: q.id,
				name: text(q.title, "en_us") || q.id,
				hint: q.id,
				icon: q.icon,
				group: text(project.categories.find((c) => c.id === q.category)?.title, "en_us") || q.category,
				detail: plain(text(q.description, "en_us")).slice(0, 240) || tUI("No description"),
			}));
		if (current && !list.some((o) => o.id === current)) list.push({ id: current, name: current, hint: tUI("Unknown quest") });
		return list;
	};
	const extensions = hubEditorDescriptors("condition");
	const conditionOptions = (current?: string) => catalogOptions(CONDITION_GROUPS, CONDITION_NAMES, CONDITION_HINTS, extensions, current);
	const defaults: Record<string, any> = {
		sneaking: true,
		biome: "minecraft:plains",
		tool: "minecraft:iron_pickaxe",
		effect: "minecraft:speed",
		"questplus:stage": "questplus:chapter",
		equipment: { slot: "mainhand", item: "minecraft:stone" },
		"questplus:gear": { items: armorPreset("iron") },
		position: { xMin: 0 },
		context: { key: "", equals: true },
	};
	const initial = (kind: string) => {
		const descriptor = extensions.find((d) => d.id === kind);
		const fromDescriptor = descriptor ? Object.fromEntries(descriptor.fields.map((field: any) => [field.key, hubFieldDefault(field)])) : undefined;
		return { [kind]: fromDescriptor ?? defaults[kind] ?? (kind.startsWith("health") ? 2 : "minecraft:overworld") };
	};
	const mutate = (fn: (root: any) => any) => {
		try {
			onChange(fn(value));
		} catch (e) {
			console.error(e);
		}
	};
	const remove = (path: string) => mutate((root) => setAt(root, path, null));
	const describe = (node: any, top = true): string => {
		const child = childrenOf(node);
		if (child) {
			const parts = child.children.map((c: any) => describe(c, false));
			const sentence =
				child.type === "not"
					? tUI("not") + " " + parts[0]
					: child.type === "at_least"
						? `${tUI("at least")} ${node.at_least.count} ${tUI("of")} (${parts.join(", ")})`
						: parts.join(" " + tUI(child.type === "and" ? "and" : "or") + " ");
			return top || child.type === "not" ? sentence : "(" + sentence + ")";
		}
		if (requires) {
			const state = node?.started ? "started" : "quest";
			return "“" + questName(node?.[state] || "") + "” " + tUI(state === "started" ? "started" : "completed");
		}
		const kind = Object.keys(node || {})[0] || "";
		const val = node?.[kind];
		return (tUI(CONDITION_NAMES[kind] || kind) + (val === undefined || typeof val === "object" ? "" : " " + String(val))).trim();
	};
	const pick = (label: string, icon: React.ReactNode, onPick: (child: any) => void) => (
		<TypePicker
			label={label}
			triggerContent={
				<>
					{icon}
					{tUI(label)}
				</>
			}
			triggerClass="button secondary cond-add"
			value=""
			options={requires ? questOptions() : conditionOptions()}
			filterGroups={requires}
			translate={!requires}
			onChange={(id) => onPick(requires ? { quest: id } : initial(id))}
		/>
	);
	const tools = (path: string, depth: number) => (
		<div className="cond-tools">
			{pick(requires ? "Add quest" : "Add condition", <Plus size={14} aria-hidden="true" />, (child) => mutate((root) => addToGroup(root, path, child)))}
			{depth === 0 && value && (
				<>
					{pick("Add an “any one of” group", <Layers size={14} aria-hidden="true" />, (child) => mutate((root) => addToGroup(root, path, { or: [child] })))}
					<InfoTip text={tUI("Groups mix rules, for example: Quest A and (Quest B or Quest C). Pick the first item of the group, then add more inside it.")} />
				</>
			)}
		</div>
	);
	// A group reads as one sentence ("Available when all of these are met") followed by its items.
	function renderGroup(node: any, path: string, depth: number, removePath = path): React.ReactNode {
		const view = groupView(node, path),
			connector = view.mode === "and" ? "and" : view.mode === "or" ? "or" : "";
		return (
			<div key={path || "root"} className={"cond-group depth-" + Math.min(depth, 4)}>
				<div className="cond-sentence">
					<span>{tUI(depth ? "This group passes when" : requires ? "Available when" : "Counts only when")}</span>
					<select className="cond-mode" aria-label={tUI("Logical operator")} value={view.mode} onChange={(e) => mutate((root) => setGroupMode(root, path, e.target.value))}>
						{GROUP_MODES.map(([mode, label]) => (
							<option key={mode} value={mode}>
								{tUI(label)}
							</option>
						))}
					</select>
					{view.mode === "at_least" && (
						<label className="cond-count">
							<span>N =</span>
							<input
								type="number"
								min={1}
								max={view.items.length}
								aria-label={tUI("Required prerequisites")}
								value={view.count}
								onChange={(e) => mutate((root) => setGroupMode(root, path, "at_least", Number(e.target.value) || 1))}
							/>
							<span>/ {view.items.length}</span>
						</label>
					)}
					{depth > 0 && (
						<button type="button" className="cond-remove" aria-label={tUI("Remove group")} title={tUI("Remove group")} onClick={() => remove(removePath)}>
							<Trash2 size={14} />
						</button>
					)}
				</div>
				<ol className="cond-items">
					{view.items.map((item: { node: any; path: string; removePath: string }, i: number) => (
						<li key={item.path || "leaf"}>
							{i > 0 && connector && <span className="cond-connector">{tUI(connector)}</span>}
							{childrenOf(item.node) ? renderGroup(item.node, item.path, depth + 1, item.removePath) : renderLeaf(item.node, item.path, item.removePath)}
						</li>
					))}
				</ol>
				{tools(path, depth)}
			</div>
		);
	}
	function renderLeaf(node: any, path: string, removePath: string): React.ReactNode {
		const removeButton = (
			<button type="button" aria-label={tUI("Remove condition")} title={tUI("Remove condition")} className="cond-remove" onClick={() => remove(removePath)}>
				<Trash2 size={14} />
			</button>
		);
		if (requires) {
			const prerequisite = node?.started ? "started" : "quest",
				target = node?.[prerequisite] || "";
			return (
				<div className="cond-leaf cond-leaf-quest" key={path}>
					<TypePicker
						label="Prerequisite quest"
						value={target}
						options={questOptions(target)}
						filterGroups
						translate={false}
						placeholder="Choose a quest…"
						onChange={(id) => mutate((root) => setAt(root, path, { [prerequisite]: id }))}
					/>
					<div className="cond-state" role="radiogroup" aria-label={tUI("Prerequisite state")}>
						{(["quest", "started"] as const).map((state) => (
							<button
								type="button"
								role="radio"
								key={state}
								aria-checked={prerequisite === state}
								onClick={() => prerequisite !== state && mutate((root) => setAt(root, path, { [state]: target }))}>
								{tUI(state === "quest" ? "Completed" : "Started")}
							</button>
						))}
					</div>
					{removeButton}
				</div>
			);
		}
		const kind = Object.keys(node || {})[0] || "dimension",
			val = node?.[kind];
		const setMember = (member: string, next: any) => mutate((root) => setAt(root, path, { [kind]: { ...val, [member]: next } }));
		return (
			<div className="cond-leaf" key={path}>
				<TypePicker label="Condition type" value={kind} options={conditionOptions(kind)} onChange={(next) => next !== kind && mutate((root) => setAt(root, path, initial(next)))} />
				<div className="cond-value">
					{kind === "sneaking" ? (
						<select aria-label={tUI("Condition value")} value={String(val)} onChange={(e) => mutate((root) => setAt(root, path, { sneaking: e.target.value === "true" }))}>
							<option value="true">{tUI("Yes")}</option>
							<option value="false">{tUI("No")}</option>
						</select>
					) : kind === "questplus:gear" ? (
						<GearEditor conditions={node} onChange={(next) => mutate((root) => setAt(root, path, next))} />
					) : kind === "equipment" ? (
						<>
							<select aria-label={tUI("Equipment slot")} value={val?.slot || "mainhand"} onChange={(e) => setMember("slot", e.target.value)}>
								{["mainhand", "offhand", "head", "chest", "legs", "feet"].map((s) => (
									<option key={s} value={s}>
										{s}
									</option>
								))}
							</select>
							<input aria-label={tUI("Equipment item")} value={val?.item || ""} onChange={(e) => setMember("item", e.target.value)} />
							<button type="button" className="button secondary" onClick={() => setPicker({ kind: "item", pick: (id) => setMember("item", id) })}>
								{tUI("Browse")}
							</button>
						</>
					) : kind === "position" ? (
						<div className="cond-position">
							{["xMin", "xMax", "yMin", "yMax", "zMin", "zMax"].map((axis) => (
								<label className="field" key={axis}>
									<span>{axis}</span>
									<input
										type="number"
										value={val?.[axis] ?? ""}
										onChange={(e) => {
											const next = { ...val };
											if (e.target.value === "") delete next[axis];
											else next[axis] = Number(e.target.value);
											mutate((root) => setAt(root, path, { position: next }));
										}}
									/>
								</label>
							))}
						</div>
					) : kind === "context" ? (
						<>
							<input aria-label={tUI("Context key")} placeholder={tUI("Context key")} value={val?.key || ""} onChange={(e) => setMember("key", e.target.value)} />
							<input
								aria-label={tUI("Context value (JSON)")}
								defaultValue={JSON.stringify(val?.equals ?? true)}
								key={path + ":" + JSON.stringify(val?.equals)}
								onBlur={(e) => {
									try {
										const next = JSON.parse(e.target.value);
										if (next === null || typeof next === "object") throw Error();
										setMember("equals", next);
									} catch {
										e.target.value = JSON.stringify(val?.equals ?? true);
									}
								}}
							/>
						</>
					) : extensions.some((d) => d.id === kind) && val && typeof val === "object" && !Array.isArray(val) ? (
						<div className="field-row">
							{extensions
								.find((d) => d.id === kind)!
								.fields.map((field: any) => (
									<div key={field.key} className="field-row">
										{field.type === "boolean" ? (
											<label className="checkbox-row">
												<input type="checkbox" checked={!!val[field.key]} onChange={(e) => setMember(field.key, e.target.checked)} />
												{field.label}
											</label>
										) : field.type === "choice" ? (
											<label className="field">
												<span>{field.label}</span>
												<select value={val[field.key] ?? hubFieldDefault(field)} onChange={(e) => setMember(field.key, e.target.value)}>
													{field.options.map((option: string) => (
														<option key={option} value={option}>
															{option}
														</option>
													))}
												</select>
											</label>
										) : (
											<>
												<Field
													label={field.label}
													type={["integer", "number"].includes(field.type) ? "number" : "text"}
													value={val[field.key] ?? ""}
													onChange={(v) => setMember(field.key, v)}
												/>
												{!["string", "integer", "number"].includes(field.type) && (
													<button type="button" className="button secondary" onClick={() => setPicker({ kind: field.type, pick: (id) => setMember(field.key, id) })}>
														{tUI("Browse")}
													</button>
												)}
											</>
										)}
									</div>
								))}
						</div>
					) : typeof val === "object" ? (
						<small role="status">{tUI("Unknown or unavailable condition extension. Data is preserved; edit in Advanced JSON.")}</small>
					) : (
						<>
							<input
								aria-label={tUI("Condition value")}
								type={kind.startsWith("health") ? "number" : "text"}
								step={kind.startsWith("health") ? 0.5 : undefined}
								value={val ?? ""}
								onChange={(e) => mutate((root) => setAt(root, path, { [kind]: kind.startsWith("health") ? Number(e.target.value) : e.target.value }))}
							/>
							{["dimension", "biome", "tool", "effect"].includes(kind) && (
								<button
									type="button"
									className="button secondary"
									onClick={() =>
										setPicker({
											kind: kind === "tool" ? "item" : kind === "effect" ? "effect" : kind,
											pick: (id) => mutate((root) => setAt(root, path, { [kind]: id })),
										})
									}>
									{tUI("Browse")}
								</button>
							)}
						</>
					)}
				</div>
				{removeButton}
			</div>
		);
	}
	return (
		<div className="condition-builder">
			{value ? (
				<>
					{groupView(value).items.some((item: { node: any }) => childrenOf(item.node)) && (
						<p className="cond-summary">
							<strong>{tUI("In short:")}</strong> {describe(value)}
						</p>
					)}
					{renderGroup(value, "", 0)}
				</>
			) : (
				<>
					<p className="cond-summary empty">{tUI(requires ? "No prerequisites: the quest is available from the start." : "No conditions: the objective counts everywhere.")}</p>
					{tools("", 0)}
				</>
			)}
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
function TranslationHelp({ onOpen }: { onOpen?: () => void }) {
	const message = tUI("Add translations in the Languages tab.");
	return (
		<button type="button" className="translation-help" title={message} aria-label={tUI("Open the Languages tab to translate this text")} onClick={onOpen}>
			<Info size={15} aria-hidden="true" />
		</button>
	);
}
export function Inspector({ project, categoryId, selection, language, onUpdate, onAction, onChooseIcon, onOpenTranslations }: Props) {
	const [copyStatus, setCopyStatus] = useState<"idle" | "copied" | "error">("idle");
	const category = selection?.type === "category" ? project.categories.find((c) => c.id === selection.id) : undefined;
	const quest = selection?.type === "quest" ? project.quests.find((q) => q.id === selection.id) : undefined;
	const note = selection?.type === "annotation" ? selectedOwner(project, selection, categoryId) : undefined;
	// Laying out a whole category is not something to redo on every render (camera moves included).
	const position = useMemo(() => {
		if (!quest || quest.layout) return quest?.layout;
		try {
			return gameLayout(project.quests.filter((q) => q.category === quest.category)).points.get(quest.id);
		} catch {
			/* Invalid prerequisites remain editable while validation reports the cycle. */
		}
	}, [project.quests, quest]);
	const changeKind = (next: string) => {
		if (next === (quest?.kind || "standard")) return;
		if (next === "information" && !confirm(tUI("Changing to information removes objectives, tiers and their rewards, and gameplay constraints. Continue?"))) return;
		onAction("kind:" + next);
	};
	const name = quest ? text(quest.title, "en_us") : category ? text(category.title, "en_us") : note ? annotationText(note, language) : "";
	return (
		<aside className="inspector">
			<div className="inspector-head">
				<h2>{tUI("Properties")}</h2>
			</div>
			{!selection ? (
				<div className="inspector-body">
					<p>{tUI("Select an element to edit.")}</p>
				</div>
			) : (
				<>
					<div className="selection-overview">
						<div className="selection-summary">
							{(quest || category) && <ItemIcon id={(quest || category)!.icon} size={36} />}
							<div>
								<strong>{name}</strong>
								<small title={selection.id}>{selection.id}</small>
							</div>
							<span>{tUI(quest ? "Quest" : note ? "Annotation" : "Category")}</span>
						</div>
						{quest && (
							<div className="selection-counts">
								<span>
									<Target size={14} />
									{tUI("Objectives")}: {quest.objectives?.length || 0}
								</span>
								<span>
									<Gift size={14} />
									{tUI("Rewards")}: {quest.rewards?.length || 0}
								</span>
							</div>
						)}
					</div>
					<div className="inspector-body">
						{category && (
							<>
								<Section title="Category">
									<div className="translated-field">
										<Field label="Name (English)" value={category.title?.en_us || ""} onChange={(v) => onUpdate("title.en_us", v)} />
										<TranslationHelp onOpen={onOpenTranslations} />
									</div>
									<IconField id={category.icon} project={project} onUpdate={(v) => onUpdate("icon", v)} onChooseIcon={() => onChooseIcon("icon")} />
									<Field label="Chapter group" value={category.group || ""} onChange={(v) => onUpdate("group", v)} placeholder="Exploration" />
									<Field label="Display order (lowest first)" type="number" value={category.order ?? ""} placeholder="0" onChange={(v) => onUpdate("order", v)} />
									<div className="translated-field">
										<Field
											label="Description (English)"
											value={category.description?.en_us || ""}
											onChange={(v) => onUpdate("description.en_us", v)}
											rows={3}
										/>
										<TranslationHelp onOpen={onOpenTranslations} />
									</div>
								</Section>
								<Section title="Add">
									<div className="toolbar-row">
										<button className="button secondary" onClick={() => onAction("create-quest")}>
											+ {tUI("Quest")}
										</button>
										<button className="button secondary" onClick={() => onAction("create-annotation")}>
											+ {tUI("Annotation")}
										</button>
										<button className="button secondary" onClick={() => onAction("create-background")}>
											+ {tUI("Background")}
										</button>
									</div>
								</Section>
								<Disclosure title="Category ID">
									<Field label="ID" value={category.id} onChange={(v) => onUpdate("id", v)} />
									<button className="button danger" onClick={() => onAction("delete-category")}>
										{tUI("Delete category")}
									</button>
								</Disclosure>
							</>
						)}
						{note && (
							<>
								<Section title="Annotation">
									{note.style !== "background" && (
										<RichEditor
											key={note.id + "-" + language}
											locale={language}
											content={annotationRich(note, language) || [{ type: "text", text: annotationText(note, language), font: "default" }]}
											onChange={(parts, plain) => onUpdate("rich-and-text", { locale: language, parts, plain })}
										/>
									)}
									<Select
										label="Text alignment"
										value={note.align || "center"}
										options={[
											["left", "Left"],
											["center", "Center"],
											["right", "Right"],
										]}
										onChange={(v) => onUpdate("align", v)}
									/>
									<Select
										label="Style"
										value={note.style || "note"}
										options={[
											["note", "Framed"],
											["heading", "Heading"],
											["label", "Label"],
											["background", "Background"],
										]}
										onChange={(v) => onUpdate("style", v)}
									/>
									<Field label="Color" type="color" value={note.color || "#c6a986"} onChange={(v) => onUpdate("color", v)} />
									<div className="field-row">
										<Field label="Width" type="number" value={note.width ?? 170} onChange={(v) => onUpdate("width", Math.max(1, Math.round(v) || 1))} />
										<Field label="Height" type="number" value={note.height ?? 100} onChange={(v) => onUpdate("height", Math.max(1, Math.round(v) || 1))} />
									</div>
									<div className="field-row">
										<Field label="X" type="number" value={note.x} onChange={(v) => onUpdate("x", v)} />
										<Field label="Y" type="number" value={note.y} onChange={(v) => onUpdate("y", v)} />
									</div>
								</Section>
							</>
						)}
						{quest && (
							<>
								<Section title="Quest">
									<div className="translated-field">
										<Field label="Title (English)" value={quest.title?.en_us || ""} onChange={(v) => onUpdate("title.en_us", v)} />
										<TranslationHelp onOpen={onOpenTranslations} />
									</div>
									<label className="field">
										<span>{tUI("Quest ID")}</span>
										<span className="id-field">
											<input aria-label={tUI("Quest ID")} readOnly value={quest.id} />
											<button
												type="button"
												className="icon-button"
												aria-label={tUI("Copy quest ID")}
												title={copyStatus === "copied" ? tUI("Copied") : copyStatus === "error" ? tUI("Copy unavailable") : tUI("Copy quest ID")}
												onClick={async () => {
													try {
														await navigator.clipboard.writeText(quest.id);
														setCopyStatus("copied");
													} catch {
														setCopyStatus("error");
													}
												}}>
												{copyStatus === "copied" ? <Check size={16} /> : <Copy size={16} />}
											</button>
										</span>
										{copyStatus === "error" && <small role="status">{tUI("Copy unavailable")}</small>}
									</label>
									<ChoiceCards
										compact
										label="Type"
										value={quest.kind || "standard"}
										options={[
											["standard", "Quest"],
											["information", "Information"],
										]}
										onChange={changeKind}
									/>
									<Select
										label="Category"
										value={quest.category}
										options={project.categories.map((c) => [c.id, text(c.title, language)])}
										onChange={(v) => onUpdate("category", v)}
									/>
									<IconField id={quest.icon} project={project} onUpdate={(v) => onUpdate("icon", v)} onChooseIcon={() => onChooseIcon("icon")} />
									<div className="description-field">
										<div className="translation-label">
											<span>{tUI("Description (English)")}</span>
											<TranslationHelp onOpen={onOpenTranslations} />
										</div>
										<DescriptionEditor
											key={quest.id + "-description-en"}
											locale="en_us"
											value={quest.description?.en_us}
											rich={quest.descriptionRich?.en_us}
											onRichChange={(parts) => onUpdate("descriptionRich.en_us", parts)}
											onChange={(v) => onUpdate("description.en_us", v)}
										/>
									</div>
								</Section>
								<Section title="Layout">
									<div className="field-row field-row-3">
										<Field label="X" type="number" value={position?.x ?? 0} onChange={(v) => onUpdate("layout.x", v)} />
										<Field label="Y" type="number" value={position?.y ?? 0} onChange={(v) => onUpdate("layout.y", v)} />
										<Field label="Quest size" type="number" value={quest.size ?? 32} onChange={(v) => onUpdate("size", Math.max(1, Math.round(v) || 1))} />
									</div>
								</Section>
								<Section title="Visibility">
									<label className="checkbox-row">
										<input type="checkbox" checked={!!quest.hidden} onChange={(e) => onUpdate("hidden", e.target.checked)} /> {tUI("Hidden until unlocked")}
									</label>
								<Disclosure title="Presentation">
									<div className="presentation-group">
										<strong>{tUI("Appearance")}</strong>
										<Translated label="Subtitle" value={quest.presentation?.subtitle || {}} prefix="presentation.subtitle" onUpdate={onUpdate} />
										<Field
											label="Quest tags"
											value={(quest.presentation?.tags || []).join(", ")}
											onChange={(v) =>
												onUpdate(
													"presentation.tags",
													String(v)
														.split(",")
														.map((s) => s.trim())
														.filter(Boolean)
												)
											}
										/>
										<Select
											label="Node shape"
											value={quest.presentation?.shape || "square"}
											options={[
												"square",
												"circle",
												"rounded_square",
												"diamond",
												"pentagon",
												"hexagon",
												"octagon",
												"heart",
												"gear",
												...(quest.presentation?.shape?.includes(":") ? [quest.presentation.shape] : []),
											].map((s) => [s, s])}
											onChange={(v) => onUpdate("presentation.shape", v)}
										/>
										<Field
											label="Custom shape resource ID"
											value={quest.presentation?.shape?.includes(":") ? quest.presentation.shape : ""}
											placeholder="modid:custom"
											onChange={(v) => onUpdate("presentation.shape", v || "square")}
										/>
										<Field
											label="Icon scale"
											type="number"
											value={quest.presentation?.iconScale ?? 1}
											onChange={(v) => onUpdate("presentation.iconScale", v)}
										/>
									</div>
									<div className="presentation-group">
										<strong>{tUI("Behavior and visibility")}</strong>
										{(
											[
												"optional",
												"disableCompletionToast",
												"hideDetailsUntilUnlocked",
												"hideTextUntilCompleted",
												"invisibleUntilCompleted",
												"hideDependencyLines",
												"hideDependentLines",
												"hideUntilDependenciesVisible",
											] as const
										).map((key, i) => (
											<label className="checkbox-row" key={key}>
												<input type="checkbox" checked={!!quest.presentation?.[key]} onChange={(e) => onUpdate("presentation." + key, e.target.checked)} />
												{tUI(
													[
														"Optional quest",
														"Disable completion toast",
														"Hide details until unlocked",
														"Hide text until completed",
														"Invisible until completed",
														"Hide incoming links",
														"Hide outgoing links",
														"Hide until dependencies visible",
													][i]
												)}
											</label>
										))}
										<Field
											label="Completed objectives before visible"
											type="number"
											value={quest.presentation?.invisibleUntilTasks ?? 0}
											onChange={(v) => onUpdate("presentation.invisibleUntilTasks", v)}
										/>
									</div>
								</Disclosure>
								</Section>
							</>
						)}
					</div>
					<div className="inspector-footer">
						{quest && (
							<>
								<button className="button secondary" onClick={() => onAction("duplicate-quest")}>
									{tUI("Duplicate")}
								</button>
								<button className="button danger" onClick={() => onAction("delete-quest")}>
									{tUI("Delete")}
								</button>
							</>
						)}
						{note && (
							<>
								<button className="button secondary" onClick={() => onAction("duplicate-annotation")}>
									{tUI("Duplicate")}
								</button>
								<button className="button danger" onClick={() => onAction("delete-annotation")}>
									{tUI("Delete")}
								</button>
							</>
						)}
						<button className="button secondary" onClick={() => onAction("advanced-json")}>
							{tUI("Advanced JSON")}
						</button>
					</div>
				</>
			)}
		</aside>
	);
}
