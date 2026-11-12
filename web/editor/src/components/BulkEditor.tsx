import { ChevronRight, ChevronsDownUp, ChevronsUpDown, Eye, EyeOff, FolderInput, Gift, Image, Search, Sparkles, Star } from "lucide-react";
import React, { useMemo, useState } from "react";
import { BULK_ACTIONS, bulkEdit } from "../core/bulk-operations.js";
import { orderedCategories, text, validate } from "../core/project.js";
import type { Project } from "../types";
import { tUI } from "../ui/ui-i18n.js";
import { Dialog } from "./Dialog";
import { IconPicker, ItemIcon } from "./IconPicker";

const ID = /^[a-z0-9_.-]+:[a-z0-9_./-]+$/;
const OPERATIONS: Record<string, { icon: React.ReactNode; hint: string; summary: string }> = {
	hide: { icon: <EyeOff size={18} />, hint: "Players no longer see them on the tablet", summary: "Hide" },
	show: { icon: <Eye size={18} />, hint: "Make hidden quests visible again", summary: "Show" },
	category: { icon: <FolderInput size={18} />, hint: "IDs, links and progress are kept", summary: "Move" },
	icon: { icon: <Image size={18} />, hint: "One icon for every selected quest", summary: "Change the icon of" },
	reward_item: { icon: <Gift size={18} />, hint: "Added after the existing rewards", summary: "Add an item reward to" },
	reward_xp: { icon: <Sparkles size={18} />, hint: "Experience points", summary: "Add experience points to" },
	reward_levels: { icon: <Star size={18} />, hint: "Whole experience levels", summary: "Add experience levels to" },
};

/** Pick quests, pick one change, apply it as a single undoable edit. IDs, objectives and prerequisites never change. */
export function BulkEditor({ project, language, onApply, onClose }: { project: Project; language: string; onApply: (result: Project, count: number) => void; onClose: () => void }) {
	const [query, setQuery] = useState(""),
		[expanded, setExpanded] = useState<Set<string>>(() => new Set()),
		[selected, setSelected] = useState<string[]>([]),
		[type, setType] = useState("hide"),
		[destination, setDestination] = useState(""),
		[icon, setIcon] = useState(""),
		[item, setItem] = useState("minecraft:diamond"),
		[amounts, setAmounts] = useState<Record<string, number>>({ reward_item: 1, reward_xp: 100, reward_levels: 5 }),
		[picking, setPicking] = useState<"icon" | "item" | null>(null),
		[error, setError] = useState("");
	const categories = useMemo(() => orderedCategories(project.categories), [project.categories]),
		categoryName = (id: string) => text(project.categories.find((c) => c.id === id)?.title, language) || id;
	const searching = query.trim() !== "";
	const matches = useMemo(() => {
		const needle = query.trim().toLowerCase();
		return project.quests.filter((q) => (q.id + " " + text(q.title, language)).toLowerCase().includes(needle));
	}, [project.quests, query, language]);
	// Category order as in the library, then any quest whose category no longer exists, so nothing is unreachable.
	const groups = useMemo(() => {
		const known = categories.map((c) => ({ id: c.id, icon: c.icon, title: text(c.title, language) || c.id }));
		for (const q of matches) if (!known.some((g) => g.id === q.category)) known.push({ id: q.category, icon: "", title: q.category });
		return known.map((g) => ({ ...g, quests: matches.filter((q) => q.category === g.id) })).filter((g) => g.quests.length);
	}, [categories, matches, language]);
	const ids = useMemo(() => new Set(selected), [selected]),
		allShown = matches.length > 0 && matches.every((q) => ids.has(q.id)),
		allOpen = groups.every((g) => expanded.has(g.id));
	const toggle = (id: string) => setSelected((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));
	const setMany = (list: { id: string }[], on: boolean) =>
		setSelected((prev) => {
			const set = new Set(prev);
			list.forEach((q) => (on ? set.add(q.id) : set.delete(q.id)));
			return [...set];
		});
	const flip = (id: string) =>
		setExpanded((prev) => {
			const next = new Set(prev);
			if (!next.delete(id)) next.add(id);
			return next;
		});
	const amount = amounts[type] ?? 1;
	const ready =
		type === "category" ? !!destination : type === "icon" ? ID.test(icon) : type === "reward_item" ? ID.test(item) && amount >= 1 && amount <= 2304 : type.startsWith("reward_") ? amount >= 1 : true;
	const n = ids.size,
		quests = n + " " + tUI(n === 1 ? "quest" : "quests"),
		summary =
			tUI(OPERATIONS[type].summary) +
			" " +
			quests +
			(type === "category" && destination ? " → " + categoryName(destination) : "") +
			(type === "reward_item" ? " · " + amount + " × " + item.replace(/^minecraft:/, "") : "") +
			(type === "reward_xp" ? " · " + amount + " XP" : "") +
			(type === "reward_levels" ? " · " + amount + " " + tUI("levels") : "");
	const run = () => {
		try {
			const op: { type: string; value?: unknown } = { type };
			if (type === "category") op.value = destination;
			else if (type === "icon") op.value = icon;
			else if (type === "reward_item") op.value = { item, count: amount };
			else if (type.startsWith("reward_")) op.value = amount;
			const draft = bulkEdit(project, selected, op);
			const issues = validate(draft);
			if (issues.length) throw Error(issues[0].where + ": " + tUI(issues[0].message));
			onApply(draft, n);
			onClose();
		} catch (e) {
			setError(tUI((e as Error).message));
		}
	};
	const idField = (label: string, value: string, set: (v: string) => void, pick: "icon" | "item") => (
		<div className="bulk-id-field">
			<ItemIcon id={value} size={32} />
			<label className="field">
				<span>{tUI(label)}</span>
				<input value={value} spellCheck={false} placeholder="minecraft:…" aria-invalid={!!value && !ID.test(value)} onChange={(e) => set(e.target.value.trim())} />
			</label>
			<button type="button" className="button secondary" onClick={() => setPicking(pick)}>
				{tUI("Browse")}
			</button>
		</div>
	);
	return (
		<Dialog title={tUI("Bulk edit")} onClose={onClose} className="reward-library-dialog bulk-workbench">
			<div className="bulk-layout">
				<section className="bulk-pick" aria-label={tUI("Quests")}>
					<header className="bulk-section-head">
						<h3>{tUI("Quests")}</h3>
						<span className="bulk-count">
							{n} {tUI("selected")}
							{n > 0 && (
								<button type="button" className="subtle" onClick={() => setSelected([])}>
									{tUI("Clear")}
								</button>
							)}
						</span>
					</header>
					<div className="bulk-filters">
						<label className="bulk-search">
							<Search size={15} aria-hidden="true" />
							<input aria-label={tUI("Search quests…")} placeholder={tUI("Search quests…")} value={query} onChange={(e) => setQuery(e.target.value)} />
						</label>
						<button type="button" className="button secondary" disabled={searching || !groups.length} onClick={() => setExpanded(allOpen ? new Set() : new Set(groups.map((g) => g.id)))}>
							{allOpen ? <ChevronsDownUp size={15} aria-hidden="true" /> : <ChevronsUpDown size={15} aria-hidden="true" />}
							{tUI(allOpen ? "Collapse all" : "Expand all")}
						</button>
					</div>
					<label className="bulk-all">
						<input type="checkbox" checked={allShown} disabled={!matches.length} onChange={() => setMany(matches, !allShown)} />
						{tUI(searching ? "Select the quests shown" : "Select every quest")} <span>({matches.length})</span>
					</label>
					<div className="bulk-list" role="group" aria-label={tUI("Quests by category")}>
						{groups.map((g) => {
							const picked = g.quests.filter((q) => ids.has(q.id)).length,
								open = searching || expanded.has(g.id);
							return (
								<div className="bulk-group" key={g.id}>
									<div className={"bulk-group-head" + (picked ? " some" : "")}>
										<button type="button" className="bulk-group-toggle" aria-label={g.title} aria-expanded={open} disabled={searching} onClick={() => flip(g.id)}>
											<ChevronRight size={15} aria-hidden="true" />
										</button>
										<label className="bulk-group-label">
											<input
												type="checkbox"
												checked={picked === g.quests.length}
												ref={(el) => {
													if (el) el.indeterminate = picked > 0 && picked < g.quests.length;
												}}
												onChange={() => setMany(g.quests, picked < g.quests.length)}
											/>
											{g.icon && <ItemIcon id={g.icon} size={22} />}
											<strong>{g.title}</strong>
										</label>
										<span className="bulk-group-count">
											{picked ? picked + " / " : ""}
											{g.quests.length}
										</span>
									</div>
									{open && (
										<div className="bulk-group-rows" role="group" aria-label={g.title}>
											{g.quests.slice(0, 5000).map((q) => (
												<label className={"bulk-row" + (ids.has(q.id) ? " checked" : "")} key={q.id}>
													<input type="checkbox" checked={ids.has(q.id)} onChange={() => toggle(q.id)} />
													<ItemIcon id={q.icon} size={24} />
													<span className="bulk-row-text">
														<strong>{text(q.title, language) || q.id}</strong>
														<small>{q.id}</small>
													</span>
													{q.hidden && (
														<span className="bulk-tag hidden" title={tUI("Hidden")}>
															<EyeOff size={12} aria-hidden="true" />
															{tUI("Hidden")}
														</span>
													)}
												</label>
											))}
										</div>
									)}
								</div>
							);
						})}
						{!matches.length && <p className="bulk-empty">{tUI("No quest matches this filter.")}</p>}
					</div>
				</section>
				<section className="bulk-change" aria-label={tUI("Change to apply")}>
					<header className="bulk-section-head">
						<h3>{tUI("Change to apply")}</h3>
					</header>
					<div className="bulk-operations" role="radiogroup" aria-label={tUI("Change to apply")}>
						{BULK_ACTIONS.map((op) => (
							<label key={op.id} className={"bulk-operation" + (type === op.id ? " selected" : "")}>
								<input
									type="radio"
									name="bulk-operation"
									checked={type === op.id}
									onChange={() => {
										setType(op.id);
										setError("");
									}}
								/>
								<span className="bulk-operation-icon">{OPERATIONS[op.id].icon}</span>
								<span>
									<strong>{tUI(op.label)}</strong>
									<small>{tUI(OPERATIONS[op.id].hint)}</small>
								</span>
							</label>
						))}
					</div>
					{type === "category" && (
						<label className="field">
							<span>{tUI("Destination category")}</span>
							<select value={destination} onChange={(e) => setDestination(e.target.value)}>
								<option value="">{tUI("Choose a category…")}</option>
								{categories.map((c) => (
									<option key={c.id} value={c.id}>
										{text(c.title, language) || c.id}
									</option>
								))}
							</select>
						</label>
					)}
					{type === "icon" && idField("Icon ID", icon, setIcon, "icon")}
					{type === "reward_item" && idField("Item ID", item, setItem, "item")}
					{type.startsWith("reward_") && (
						<label className="field bulk-amount">
							<span>{tUI(type === "reward_item" ? "Quantity" : type === "reward_xp" ? "Experience points" : "Levels")}</span>
							<input
								type="number"
								min="1"
								max={type === "reward_item" ? 2304 : 1000000}
								value={amount}
								onChange={(e) => setAmounts((a) => ({ ...a, [type]: Math.floor(Number(e.target.value)) }))}
							/>
						</label>
					)}
					<p className="bulk-safe">{tUI("IDs, objectives and prerequisites are never changed. Ctrl+Z undoes the whole edit.")}</p>
				</section>
			</div>
			<footer className="reward-library-footer">
				{error ? (
					<span className="template-error" role="alert">
						{error}
					</span>
				) : (
					<span role="status">{n ? summary : tUI("Select at least one quest.")}</span>
				)}
				<div className="bulk-actions">
					<button type="button" className="button secondary" onClick={onClose}>
						{tUI("Cancel")}
					</button>
					<button type="button" className="button primary" disabled={!n || !ready} onClick={run}>
						{tUI("Apply to")} {quests}
					</button>
				</div>
			</footer>
			{picking && (
				<IconPicker
					onPick={(id) => {
						(picking === "icon" ? setIcon : setItem)(id);
						setPicking(null);
					}}
					onClose={() => setPicking(null)}
				/>
			)}
		</Dialog>
	);
}
