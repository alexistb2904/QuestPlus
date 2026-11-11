import { Check, Copy, Plus, Search, Trash2, TriangleAlert, Upload, X } from "lucide-react";
import { type ReactNode, useEffect, useId, useRef, useState } from "react";
import {
	ANY_VILLAGE,
	MAX_SKIN_BYTES,
	skinProblem,
	VILLAGER_TYPES,
	customRules,
	DEFAULT_CHANCE,
	DIMENSION_SPAWNS,
	formatPercent,
	fromPercent,
	npcId,
	ruleFor,
	setRule,
	STRUCTURE_GROUPS,
	VILLAGES,
} from "../core/npc-spawns.js";
import { STRUCTURES } from "../core/objective-targets.js";
import { orderedCategories, text, validateNpcs } from "../core/project.js";
import type { Project } from "../types";
import { getUiLocale, tUI as t } from "../ui/ui-i18n.js";
import { Dialog } from "./Dialog";
import { ItemIcon } from "./IconPicker";
import { ChoiceCards, InfoTip } from "./TypePicker";

type Rule = { type: "structure" | "random"; target: string; chance: number };
export type Npc = { id: string; name: Record<string, string>; quests: string[]; spawns?: Rule[]; model?: "villager" | "player"; villager?: string; skin?: string };
type Place = { target: string; name: string; icon: string };
type Edit = (change: (config: any) => void, field?: string) => void;

const ID = /^[a-z0-9_.-]{1,64}$/;
const TAGS = ["#minecraft:village", "#minecraft:abandoned_camp", "#minecraft:mineshaft", "#minecraft:ocean_ruin", "#minecraft:ruined_portal", "#minecraft:shipwreck"];

/** Typed as text so a half-typed "0," or "0." survives; a French decimal comma is accepted. */
function ChanceInput({ chance, label, onChange }: { chance: number; label: string; onChange: (chance: number) => void }) {
	const [draft, setDraft] = useState<string | null>(null);
	return (
		<label className="giver-chance">
			<input
				inputMode="decimal"
				aria-label={label}
				value={draft ?? formatPercent(chance, getUiLocale())}
				onChange={(e) => {
					const raw = e.target.value.replace(",", ".");
					setDraft(e.target.value);
					if (/^\d*\.?\d*$/.test(raw) && raw !== "" && raw !== ".") onChange(fromPercent(raw));
				}}
				onBlur={() => setDraft(null)}
			/>
			<span aria-hidden="true">%</span>
		</label>
	);
}

function PlaceTile({ place, rule, type, onChange }: { place: Place; rule?: Rule; type: Rule["type"]; onChange: (chance: number | null) => void }) {
	const name = t(place.name);
	return (
		<div className={"giver-place" + (rule ? " on" : "")}>
			<label className="giver-place-toggle" title={name}>
				<input type="checkbox" checked={!!rule} onChange={(e) => onChange(e.target.checked ? DEFAULT_CHANCE[type] : null)} />
				<ItemIcon id={place.icon} size={24} />
				<span>
					<strong>{name}</strong>
					{place.target.startsWith("#") && <small>{t("All variants")}</small>}
				</span>
			</label>
			{rule && <ChanceInput chance={rule.chance} label={t("Chance") + " · " + name} onChange={onChange} />}
		</div>
	);
}

const DEFAULT_SKIN = import.meta.env.BASE_URL + "textures/quest_giver.png";
/** Front faces of a 64×64 skin [source x, y, width, height, target x, y]: base layer, then its overlay. */
const SKIN_FRONT = [
	[8, 8, 8, 8, 4, 0],
	[20, 20, 8, 12, 4, 8],
	[44, 20, 4, 12, 0, 8],
	[36, 52, 4, 12, 12, 8],
	[4, 20, 4, 12, 4, 20],
	[20, 52, 4, 12, 8, 20],
	[40, 8, 8, 8, 4, 0],
	[20, 36, 8, 12, 4, 8],
	[44, 36, 4, 12, 0, 8],
	[52, 52, 4, 12, 12, 8],
	[4, 36, 4, 12, 4, 20],
	[4, 52, 4, 12, 8, 20],
];

/** The skin seen from the front, pixel-exact. Reports slim (3-pixel arm) skins, which the NPC's wide arms show with a gap. */
function SkinPreview({ src, onSlim }: { src: string; onSlim: (slim: boolean) => void }) {
	const canvas = useRef<HTMLCanvasElement>(null),
		report = useRef(onSlim);
	report.current = onSlim;
	useEffect(() => {
		const image = new Image();
		image.onload = () => {
			const g = canvas.current?.getContext("2d");
			if (!g) return;
			g.imageSmoothingEnabled = false;
			g.clearRect(0, 0, 64, 128);
			for (const [sx, sy, w, h, dx, dy] of SKIN_FRONT) g.drawImage(image, sx, sy, w, h, dx * 4, dy * 4, w * 4, h * 4);
			const probe = document.createElement("canvas").getContext("2d", { willReadFrequently: true })!;
			probe.canvas.width = probe.canvas.height = 64;
			probe.drawImage(image, 0, 0);
			report.current(probe.getImageData(54, 20, 2, 12).data.every((value, i) => i % 4 !== 3 || value === 0));
		};
		image.src = src;
	}, [src]);
	return <canvas ref={canvas} width={64} height={128} className="giver-skin-preview" role="img" aria-label={t("Skin preview")} />;
}

function Appearance({ npc, edit }: { npc: Npc; edit: (change: (npc: Npc) => void, field?: string) => void }) {
	const outfit = useId(),
		file = useRef<HTMLInputElement>(null),
		[error, setError] = useState(""),
		[slim, setSlim] = useState(false),
		model = npc.model ?? "villager",
		villager = npc.villager ?? "plains";
	useEffect(() => setError(""), [npc.id]);
	const upload = async (picked: File) => {
		if (picked.type !== "image/png" || picked.size > MAX_SKIN_BYTES) return setError(t("Choose a 64 × 64 PNG skin of at most 16 KB."));
		const data = await new Promise<string>((resolve, reject) => {
			const reader = new FileReader();
			reader.onload = () => resolve(String(reader.result));
			reader.onerror = reject;
			reader.readAsDataURL(picked);
		});
		const problem = skinProblem(data);
		if (problem) return setError(t(problem));
		setError("");
		edit((n) => {
			n.model = "player";
			n.skin = data;
		}, "skin");
	};
	return (
		<Section title="Appearance" count={0} hint="How players see this NPC in the world. Its name floats above its head either way.">
			<ChoiceCards
				label="Model"
				value={model}
				options={[
					["villager", "Villager", "A vanilla villager wearing the clothes of a biome."],
					["player", "Skin", "The player model, with the QuestPlus skin or your own."],
				]}
				onChange={(value) =>
					edit((n) => {
						n.model = value as Npc["model"];
						if (value === "villager") delete n.skin;
					}, "model")
				}
			/>
			{model === "villager" ? (
				<fieldset className="giver-outfits">
					<legend>{t("Outfit")}</legend>
					<div className="giver-grid">
						{VILLAGER_TYPES.map((type) => (
							<label key={type.id} className={"giver-place giver-place-toggle" + (type.id === villager ? " on" : "")}>
								<input type="radio" name={outfit} checked={type.id === villager} onChange={() => edit((n) => (n.villager = type.id), "villager")} />
								<ItemIcon id={type.icon} size={24} />
								<span>
									<strong>{t(type.name)}</strong>
								</span>
							</label>
						))}
					</div>
				</fieldset>
			) : (
				<div className="giver-skin">
					<SkinPreview src={npc.skin || DEFAULT_SKIN} onSlim={setSlim} />
					<div className="giver-skin-text">
						<strong>{t(npc.skin ? "Your skin" : "QuestPlus skin")}</strong>
						<p>{t("Upload a classic Minecraft skin: a 64 × 64 PNG with wide arms, up to 16 KB.")}</p>
						<div className="giver-skin-actions">
							<button type="button" className="button secondary" onClick={() => file.current?.click()}>
								<Upload size={15} aria-hidden="true" />
								{t(npc.skin ? "Replace skin" : "Upload a skin")}
							</button>
							{npc.skin && (
								<button type="button" className="button secondary" onClick={() => edit((n) => delete n.skin, "skin")}>
									{t("Use the QuestPlus skin")}
								</button>
							)}
						</div>
						<input
							ref={file}
							type="file"
							accept="image/png"
							hidden
							onChange={(e) => {
								const picked = e.target.files?.[0];
								e.target.value = "";
								if (picked) upload(picked).catch(() => setError(t("This file could not be read.")));
							}}
						/>
						{error && (
							<small className="reward-error" role="alert">
								{error}
							</small>
						)}
						{!error && slim && npc.skin && (
							<small className="giver-warning" role="status">
								{t("This looks like a slim-arm skin. The NPC has wide arms, so a thin gap will show along each arm.")}
							</small>
						)}
					</div>
				</div>
			)}
		</Section>
	);
}

const fold = (value: string) =>
	value
		.normalize("NFD")
		.replace(/[̀-ͯ]/g, "")
		.toLowerCase();

/**
 * Search the pack's quests and add one to this NPC. Results stay open while
 * adding, grouped by chapter, and name any other quest giver that already offers
 * the quest.
 */
function QuestAdder({ project, npcs, npc, language, onAdd }: { project: Project; npcs: Npc[]; npc: Npc; language: string; onAdd: (id: string) => void }) {
	const list = useId(),
		[query, setQuery] = useState(""),
		[open, setOpen] = useState(false),
		[active, setActive] = useState(0);
	const terms = fold(query).split(/\s+/).filter(Boolean);
	const groups = orderedCategories(project.categories)
		.map((category: Project["categories"][number]) => {
			const chapter = text(category.title, language) || category.id;
			const quests = project.quests.filter((q) => {
				if (q.category !== category.id || npc.quests.includes(q.id)) return false;
				const haystack = fold([text(q.title, language), q.title?.en_us, q.id, chapter].join(" "));
				return terms.every((term) => haystack.includes(term));
			});
			return { id: category.id, chapter, quests };
		})
		.filter((group: { quests: unknown[] }) => group.quests.length);
	const flat: Project["quests"] = groups.flatMap((group: { quests: Project["quests"] }) => group.quests);
	const current = Math.min(active, Math.max(0, flat.length - 1));
	const add = (id: string) => {
		onAdd(id);
		setActive(current);
	};
	const optionId = (id: string) => list + "-" + id.replace(/[^a-z0-9_-]/gi, "_");
	return (
		<div
			className="giver-adder"
			onBlur={(e) => {
				if (!e.currentTarget.contains(e.relatedTarget as Node)) setOpen(false);
			}}>
			<label className="giver-adder-field">
				<span>{t("Add a quest")}</span>
				<span className="giver-adder-input">
					<Search size={15} aria-hidden="true" />
					<input
						type="search"
						role="combobox"
						aria-expanded={open}
						aria-controls={list}
						aria-autocomplete="list"
						aria-activedescendant={open && flat[current] ? optionId(flat[current].id) : undefined}
						value={query}
						placeholder={t("Search by title or chapter")}
						onFocus={() => setOpen(true)}
						onChange={(e) => {
							setQuery(e.target.value);
							setActive(0);
							setOpen(true);
						}}
						onKeyDown={(e) => {
							if (e.key === "ArrowDown" || e.key === "ArrowUp") {
								e.preventDefault();
								setOpen(true);
								setActive(Math.max(0, Math.min(flat.length - 1, current + (e.key === "ArrowDown" ? 1 : -1))));
							} else if (e.key === "Enter" && open && flat[current]) {
								e.preventDefault();
								add(flat[current].id);
							} else if (e.key === "Escape" && open) {
								e.preventDefault();
								e.stopPropagation();
								setOpen(false);
							}
						}}
					/>
				</span>
			</label>
			{open && (
				<div className="giver-adder-list" id={list} role="listbox" aria-label={t("Quests to add")}>
					{groups.map((group: { id: string; chapter: string; quests: Project["quests"] }) => (
						<div role="group" aria-label={group.chapter} key={group.id}>
							<div className="giver-adder-chapter" aria-hidden="true">
								{group.chapter}
							</div>
							{group.quests.map((q) => {
								const elsewhere = npcs.filter((other) => other !== npc && other.quests.includes(q.id));
								return (
									<div
										key={q.id}
										id={optionId(q.id)}
										role="option"
										aria-selected={flat[current] === q}
										className="giver-adder-option"
										onMouseDown={(e) => e.preventDefault()}
										onMouseEnter={() => setActive(flat.indexOf(q))}
										onClick={() => add(q.id)}>
										<ItemIcon id={q.icon} size={20} />
										<span>{text(q.title, language) || q.id}</span>
										{elsewhere.length > 0 && (
											<small title={t("Also offered by") + " " + elsewhere.map((other) => text(other.name, language) || other.id).join(", ")}>
												{text(elsewhere[0].name, language) || elsewhere[0].id}
												{elsewhere.length > 1 ? " +" + (elsewhere.length - 1) : ""}
											</small>
										)}
										<Plus size={15} aria-hidden="true" />
									</div>
								);
							})}
						</div>
					))}
					{!flat.length && <p className="giver-adder-empty">{t(project.quests.length > npc.quests.length ? "No quest matches this search." : "This NPC already offers every quest.")}</p>}
				</div>
			)}
		</div>
	);
}

function Section({ title, hint, count, children }: { title: string; hint: string; count: number; children: ReactNode }) {
	return (
		<section className="giver-section">
			<header>
				<h3>{t(title)}</h3>
				{count > 0 && <span className="giver-count">{count + " " + t(count === 1 ? "place" : "places")}</span>}
			</header>
			<p>{t(hint)}</p>
			{children}
		</section>
	);
}

function IdField({ id, taken, onRename }: { id: string; taken: (id: string) => boolean; onRename: (id: string) => void }) {
	const [draft, setDraft] = useState(id);
	useEffect(() => setDraft(id), [id]);
	const clean = draft.trim(),
		error = !ID.test(clean) ? t("Lowercase letters, digits, _ . - only") : clean !== id && taken(clean) ? t("Another quest giver uses this identifier.") : "";
	return (
		<label className="field giver-id">
			<span>
				{t("Identifier")}
				<InfoTip text={t("Used by /questplus npc and stored in NPCs already placed in worlds. Renaming it disconnects those NPCs from their quests.")} />
			</span>
			<input
				value={draft}
				maxLength={64}
				spellCheck={false}
				aria-invalid={!!error}
				onChange={(e) => setDraft(e.target.value)}
				onKeyDown={(e) => e.key === "Enter" && e.currentTarget.blur()}
				onBlur={() => (error || clean === id ? setDraft(id) : onRename(clean))}
			/>
			{error && <small className="reward-error">{error}</small>}
		</label>
	);
}

export function QuestGiverLibrary({
	project,
	language,
	initial,
	quest,
	onConfig,
	onClose,
}: {
	project: Project;
	language: string;
	initial?: string;
	/** The quest the library was opened from: a new quest giver offers it. */
	quest?: string;
	onConfig: Edit;
	onClose: () => void;
}) {
	const npcs: Npc[] = project.config?.npcs || [],
		[index, setIndex] = useState(() => Math.max(0, npcs.findIndex((npc) => npc.id === initial))),
		[newName, setNewName] = useState(""),
		[custom, setCustom] = useState(""),
		[copied, setCopied] = useState(false),
		npc = npcs[index];
	const questTitle = (id: string) => {
		const q = project.quests.find((row) => row.id === id);
		return q ? text(q.title, language) || id : id;
	};
	const problems = new Map<string, string[]>();
	validateNpcs(npcs, new Set(project.quests.map((q) => q.id)), (where: string, message: string) =>
		problems.set(where, [...(problems.get(where) || []), message])
	);
	const edit = (change: (npc: Npc) => void, field = "npc") => onConfig((config) => change(config.npcs[index]), "config:npc:" + field + ":" + index);
	const place = (type: Rule["type"]) => (target: string) => (chance: number | null) => edit((n) => setRule(n, type, target, chance), target);
	const create = () => {
		const name = newName.trim();
		if (!name) return;
		const id = npcId(name, new Set(npcs.map((row) => row.id)));
		onConfig((config) => {
			config.npcs ||= [];
			config.npcs.push({ id, name: { en_us: name }, quests: quest ? [quest] : [], spawns: [{ type: "structure", target: ANY_VILLAGE.target, chance: DEFAULT_CHANCE.structure }] });
		}, "config:npc:create");
		setIndex(npcs.length);
		setNewName("");
	};
	const addCustom = () => {
		const target = custom.trim();
		if (!target || ruleFor(npc, "structure", target)) return;
		edit((n) => setRule(n, "structure", target, 0.1), "custom");
		setCustom("");
	};
	const command = npc ? "/questplus npc " + npc.id : "";
	const villages = npc ? [...VILLAGES, ANY_VILLAGE].filter((p) => ruleFor(npc, "structure", p.target)).length : 0;
	const structures = npc ? STRUCTURE_GROUPS.flatMap((g) => g.items).filter((p) => ruleFor(npc, "structure", p.target)).length : 0;
	const anywhere = npc ? (npc.spawns || []).filter((rule) => rule.type === "random").length : 0;
	const issues = npc ? problems.get("NPC " + npc.id) || [] : [];
	return (
		<Dialog title={t("Quest givers")} onClose={onClose} className="reward-library-dialog giver-dialog">
			<div className="reward-library">
				<nav className="reward-library-list" aria-label={t("Quest givers")}>
					<form
						className="reward-library-new"
						onSubmit={(e) => {
							e.preventDefault();
							create();
						}}>
						<label className="field">
							<span>{t("New quest giver")}</span>
							<input maxLength={64} value={newName} placeholder={t("Name, e.g. Marlo the Cartographer")} onChange={(e) => setNewName(e.target.value)} />
						</label>
						<button type="submit" className="button primary" disabled={!newName.trim() || npcs.length >= 256}>
							<Plus size={15} />
							{t("Create quest giver")}
						</button>
						{quest && <small className="giver-note">{t("It will offer this quest:") + " " + questTitle(quest)}</small>}
					</form>
					<ul>
						{npcs.map((row, i) => (
							<li key={row.id + i}>
								<button type="button" className="reward-library-item" aria-current={i === index} onClick={() => setIndex(i)}>
									<span>
										<strong>{text(row.name, language) || row.id}</strong>
										<small>
											{row.quests.length} {t(row.quests.length === 1 ? "quest" : "quests")} · {(row.spawns || []).length}{" "}
											{t((row.spawns || []).length === 1 ? "place" : "places")}
										</small>
									</span>
									{problems.has("NPC " + row.id) && <TriangleAlert size={15} className="reward-library-issue" aria-label={t("Problem")} />}
								</button>
							</li>
						))}
					</ul>
				</nav>
				{npc ? (
					<section className="reward-library-main" aria-label={text(npc.name, language) || npc.id}>
						<header className="reward-library-head">
							<label className="field giver-name">
								<span>{t("Name (English)")}</span>
								<input value={npc.name.en_us ?? ""} maxLength={64} onChange={(e) => edit((n) => (n.name.en_us = e.target.value), "name")} />
							</label>
							{language !== "en_us" && (
								<label className="field giver-name">
									<span>{t("Name") + " · " + language}</span>
									<input
										value={npc.name[language] ?? ""}
										maxLength={64}
										placeholder={npc.name.en_us}
										onChange={(e) => edit((n) => (e.target.value ? (n.name[language] = e.target.value) : delete n.name[language]), "name-" + language)}
									/>
								</label>
							)}
							<IdField
								id={npc.id}
								taken={(id) => npcs.some((row, i) => i !== index && row.id === id)}
								onRename={(id) => edit((n) => (n.id = id), "id")}
							/>
							<button
								type="button"
								className="button danger"
								onClick={() => {
									if (!confirm(t("Delete this quest giver? NPCs already in worlds stop offering quests."))) return;
									onConfig((config) => config.npcs.splice(index, 1), "config:npc:delete");
									setIndex(Math.max(0, index - 1));
								}}>
								<Trash2 size={15} />
								{t("Delete quest giver")}
							</button>
						</header>
						{issues.length > 0 && (
							<div className="reward-library-alert" role="alert">
								<TriangleAlert size={16} />
								<div>
									<strong>{t("Validation error")}</strong>
									{[...new Set(issues)].map((message) => (
										<p key={message}>{t(message)}</p>
									))}
								</div>
							</div>
						)}
						<div className="reward-library-columns">
							<div className="giver-places">
								<Appearance npc={npc} edit={edit} />
								<Section title="Villages" count={villages} hint="Each village rolls its chance once, when it is generated. Any village covers the ones without their own chance.">
									<div className="giver-grid">
										{[...VILLAGES, ANY_VILLAGE].map((p) => (
											<PlaceTile key={p.target} place={p} type="structure" rule={ruleFor(npc, "structure", p.target)} onChange={place("structure")(p.target)} />
										))}
									</div>
								</Section>
								<Section title="Structures" count={structures} hint="Rolled once per structure, when it is generated. The NPC stands at its centre.">
									{STRUCTURE_GROUPS.map((group) => (
										<div className="giver-group" key={group.name}>
											<h4>{t(group.name)}</h4>
											<div className="giver-grid">
												{group.items.map((p) => (
													<PlaceTile key={p.target} place={p} type="structure" rule={ruleFor(npc, "structure", p.target)} onChange={place("structure")(p.target)} />
												))}
											</div>
										</div>
									))}
								</Section>
								<Section title="Anywhere in a dimension" count={anywhere} hint="Rolled once per newly generated chunk, so keep it small: 0.2% is about one NPC every 500 chunks.">
									<div className="giver-grid">
										{DIMENSION_SPAWNS.map((p) => (
											<PlaceTile key={p.target} place={p} type="random" rule={ruleFor(npc, "random", p.target)} onChange={place("random")(p.target)} />
										))}
									</div>
								</Section>
								<Section
									title="Modded structures and tags"
									count={customRules(npc).length}
									hint="For structures from other mods or datapacks. Use an ID such as mymod:castle, or a tag such as #mymod:ruins.">
									{customRules(npc).length > 0 && (
										<ul className="giver-custom">
											{customRules(npc).map((rule) => (
												<li key={rule.target}>
													<code>{rule.target}</code>
													<ChanceInput chance={rule.chance} label={t("Chance") + " · " + rule.target} onChange={place("structure")(rule.target)} />
													<button type="button" className="icon-button" aria-label={t("Remove") + " " + rule.target} onClick={() => place("structure")(rule.target)(null)}>
														<X size={15} />
													</button>
												</li>
											))}
										</ul>
									)}
									<form
										className="giver-custom-add"
										onSubmit={(e) => {
											e.preventDefault();
											addCustom();
										}}>
										<label className="field">
											<span>{t("Structure ID or #tag")}</span>
											<input list="giver-structure-ids" value={custom} spellCheck={false} onChange={(e) => setCustom(e.target.value)} />
										</label>
										<button type="submit" className="button secondary" disabled={!custom.trim() || !!ruleFor(npc, "structure", custom.trim())}>
											<Plus size={15} />
											{t("Add")}
										</button>
									</form>
									<datalist id="giver-structure-ids">
										{[...TAGS, ...STRUCTURES].map((id) => (
											<option value={id} key={id} />
										))}
									</datalist>
								</Section>
							</div>
							<aside className="reward-library-aside">
								<section className="reward-library-card">
									<h3>{t("Quests offered")}</h3>
									{npc.quests.length ? (
										<ul className="giver-quests">
											{npc.quests.map((id) => {
												const q = project.quests.find((row) => row.id === id);
												return (
													<li key={id}>
														<ItemIcon id={q?.icon} size={20} />
														<span>{questTitle(id)}</span>
														<button
															type="button"
															className="icon-button"
															aria-label={t("Remove") + " " + questTitle(id)}
															onClick={() => edit((n) => (n.quests = n.quests.filter((other) => other !== id)), "quests")}>
															<X size={15} />
														</button>
													</li>
												);
											})}
										</ul>
									) : (
										<p>{t("No quests yet. Players who meet this NPC will hear that it has nothing for them.")}</p>
									)}
									<QuestAdder project={project} npcs={npcs} npc={npc} language={language} onAdd={(id) => edit((n) => n.quests.push(id), "quests")} />
								</section>
								<section className="reward-library-card">
									<h3>{t("Rewards")}</h3>
									<label className="checkbox-row reward-library-toggle">
										<input
											type="checkbox"
											checked={project.config?.npcTurnIn !== false}
											onChange={(e) => onConfig((config) => (config.npcTurnIn = e.target.checked), "config:npcTurnIn")}
										/>
										<span>{t("Return to the NPC to claim rewards")}</span>
									</label>
									<p>{t("Applies to every quest giver. When off, rewards are claimed from the tablet like any quest.")}</p>
								</section>
								<section className="reward-library-card">
									<h3>{t("Existing worlds")}</h3>
									<p>{t("Only chunks generated after this rule roll a spawn. In a world that already exists, an operator can place this NPC where they stand.")}</p>
									<div className="reward-library-command">
										<code>{command}</code>
										<button
											type="button"
											className="button secondary"
											onClick={() =>
												navigator.clipboard
													.writeText(command)
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
								</section>
							</aside>
						</div>
					</section>
				) : (
					<div className="library-empty reward-library-main">
						<strong>{t("No quest givers yet")}</strong>
						<span>{t("Create a quest giver, choose the villages and structures where it can appear, then pick the quests it offers.")}</span>
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
