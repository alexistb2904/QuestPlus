import { useMemo, useState } from "react";
import { BULK_ACTIONS, bulkEdit } from "../core/bulk-operations.js";
import { text, validate } from "../core/project.js";
import type { Project } from "../types";
import { Dialog } from "./Dialog";

type Operation = { type: string; value?: any };
export function BulkEditor({ project, onApply, onClose }: { project: Project; onApply: (result: Project, count: number) => void; onClose: () => void }) {
	const [category, setCategory] = useState("all"),
		[query, setQuery] = useState(""),
		[selected, setSelected] = useState<string[]>([]);
	const [type, setType] = useState("hide"),
		[value, setValue] = useState("minecraft:diamond"),
		[count, setCount] = useState(1),
		[error, setError] = useState("");
	const matches = useMemo(
		() => project.quests.filter((q) => (category === "all" || q.category === category) && (q.id + " " + text(q.title, "en_us")).toLowerCase().includes(query.toLowerCase())),
		[project, category, query]
	);
	const ids = useMemo(() => new Set(selected), [selected]);
	const allVisible = matches.length > 0 && matches.every((q) => ids.has(q.id));
	const toggle = (id: string) => setSelected((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));
	const toggleAll = () =>
		setSelected((prev) => {
			const set = new Set(prev);
			if (matches.every((q) => set.has(q.id))) matches.forEach((q) => set.delete(q.id));
			else matches.forEach((q) => set.add(q.id));
			return [...set];
		});
	const run = () => {
		try {
			const op: Operation = { type };
			if (type === "category" || type === "icon") op.value = value;
			else if (type === "reward_item") op.value = { item: value, count };
			else if (type === "reward_xp" || type === "reward_levels") op.value = count;
			const draft = bulkEdit(project, selected, op);
			const issues = validate(draft);
			if (issues.length) throw Error(issues[0].where + ": " + issues[0].message);
			onApply(draft, selected.length);
			onClose();
		} catch (e) {
			setError((e as Error).message);
		}
	};
	return (
		<Dialog title="Bulk Quest Editor" onClose={onClose} className="bulk-dialog">
			<p>Modify multiple quests at once. IDs, objectives and prerequisites are preserved, and changes can be undone with Ctrl+Z.</p>
			<div className="picker-filters">
				<select aria-label="Filter category" value={category} onChange={(e) => setCategory(e.target.value)}>
					<option value="all">All categories</option>
					{project.categories.map((c) => (
						<option key={c.id} value={c.id}>
							{text(c.title, "en_us")}
						</option>
					))}
				</select>
				<input aria-label="Filter quests" placeholder="Search quest ID or title…" value={query} onChange={(e) => setQuery(e.target.value)} />
			</div>
			<label className="checkbox-row">
				<input type="checkbox" checked={allVisible} onChange={toggleAll} /> Select all {matches.length} filtered quests ({ids.size} selected)
			</label>
			<div className="bulk-quest-list" role="group" aria-label="Select quests">
				{matches.slice(0, 5000).map((q) => (
					<label className="checkbox-row" key={q.id}>
						<input type="checkbox" checked={ids.has(q.id)} onChange={() => toggle(q.id)} />
						<span>
							{text(q.title, "en_us") || q.id}
							<small style={{ display: "block", opacity: 0.75 }}>{q.id}</small>
						</span>
					</label>
				))}
			</div>
			<div className="picker-filters">
				<label className="field">
					<span>Operation</span>
					<select
						value={type}
						onChange={(e) => {
							setType(e.target.value);
							setError("");
						}}>
						{BULK_ACTIONS.map((op) => (
							<option key={op.id} value={op.id}>
								{op.label}
							</option>
						))}
					</select>
				</label>
				{type === "category" ? (
					<label className="field">
						<span>Destination</span>
						<select value={project.categories.some((c) => c.id === value) ? value : ""} onChange={(e) => setValue(e.target.value)}>
							<option value="">Select a category…</option>
							{project.categories.map((c) => (
								<option key={c.id} value={c.id}>
									{text(c.title, "en_us")}
								</option>
							))}
						</select>
					</label>
				) : ["icon", "reward_item"].includes(type) ? (
					<label className="field">
						<span>{type === "icon" ? "Icon ID" : "Item ID"}</span>
						<input value={value} onChange={(e) => setValue(e.target.value)} />
					</label>
				) : null}
				{type.startsWith("reward_") && (
					<label className="field">
						<span>{type === "reward_item" ? "Quantity" : "Amount"}</span>
						<input type="number" min="1" value={count} onChange={(e) => setCount(Number(e.target.value))} />
					</label>
				)}
			</div>
			{error && (
				<p className="error-line" role="alert">
					{error}
				</p>
			)}
			<div className="dialog-actions">
				<button type="button" className="button secondary" onClick={onClose}>
					Cancel
				</button>
				<button type="button" className="button primary" disabled={!ids.size} onClick={run}>
					Apply to {ids.size} quest{ids.size === 1 ? "" : "s"}
				</button>
			</div>
		</Dialog>
	);
}
