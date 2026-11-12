import { ChevronDown, Info, Search } from "lucide-react";
import React, { useEffect, useId, useRef, useState } from "react";
import { resourceMatcher } from "../core/resource-search.js";
import { EXTENSION_GROUP, EXTENSION_HINT, typeIcon } from "../ui/type-catalog.js";
import { tUI } from "../ui/ui-i18n.js";
import { ItemIcon } from "./IconPicker";

export type TypeOption = { id: string; name: string; hint?: string; group?: string; icon?: string; detail?: string };

/** Built-in types in catalogue group order, then server extensions, then an unknown current value so it stays visible. */
export function catalogOptions(
	groups: [string, string[]][],
	names: Record<string, string>,
	hints: Record<string, string>,
	extensions: { id: string; label: string; description?: string }[] = [],
	current?: string
): TypeOption[] {
	const list: TypeOption[] = groups.flatMap(([group, ids]) => ids.filter((id) => id in names).map((id) => ({ id, name: names[id], hint: hints[id], group, icon: typeIcon(id) })));
	for (const id of Object.keys(names)) if (!list.some((o) => o.id === id)) list.push({ id, name: names[id], hint: hints[id], icon: typeIcon(id) });
	for (const d of extensions) if (!list.some((o) => o.id === d.id)) list.push({ id: d.id, name: d.label, hint: d.description || EXTENSION_HINT, group: EXTENSION_GROUP });
	if (current && !list.some((o) => o.id === current)) list.push({ id: current, name: current, group: EXTENSION_GROUP });
	return list;
}

export const anchorVar = (id: string) => ({ "--anchor": "--qp" + id.replace(/[^a-zA-Z0-9_-]/g, "") }) as React.CSSProperties;
// CSS anchor positioning attaches popovers to their trigger. Engines without it get fixed coordinates once on open.
export function place(anchor: HTMLElement | null, popover: HTMLElement | null, above = false) {
	if (!anchor || !popover || CSS.supports("anchor-name: --a")) return;
	const r = anchor.getBoundingClientRect();
	Object.assign(popover.style, {
		position: "fixed",
		margin: "0",
		inset: "auto",
		left: Math.max(8, Math.min(r.left, innerWidth - popover.offsetWidth - 8)) + "px",
		top: Math.max(8, above ? r.top - popover.offsetHeight - 6 : r.bottom + 4) + "px",
	});
}

/** Small "i" button: hover (after a short delay) or focus shows what the current choice does. */
export function InfoTip({ text, label = "What does this do?" }: { text?: string; label?: string }) {
	const id = useId(),
		button = useRef<HTMLButtonElement>(null),
		tip = useRef<HTMLDivElement>(null),
		timer = useRef(0);
	useEffect(() => () => clearTimeout(timer.current), []);
	if (!text) return null;
	const show = (delay: number) => {
		clearTimeout(timer.current);
		timer.current = window.setTimeout(() => {
			if (!tip.current || tip.current.matches(":popover-open")) return;
			tip.current.showPopover?.();
			place(button.current, tip.current, true);
		}, delay);
	};
	const hide = () => {
		clearTimeout(timer.current);
		if (tip.current?.matches(":popover-open")) tip.current.hidePopover();
	};
	return (
		<>
			<button
				ref={button}
				type="button"
				className="info-tip"
				aria-label={tUI(label)}
				aria-describedby={id}
				style={anchorVar(id)}
				onPointerEnter={() => show(250)}
				onPointerLeave={hide}
				onFocus={() => show(0)}
				onBlur={hide}
				onClick={(e) => {
					e.preventDefault();
					show(0);
				}}>
				<Info size={13} aria-hidden="true" />
			</button>
			<div ref={tip} id={id} role="tooltip" popover="manual" className="info-tip-bubble" style={anchorVar(id)}>
				{text}
			</div>
		</>
	);
}

/** Searchable, grouped type list with a description pane, rendered in the top layer so dialogs and scroll areas never clip it. */
export function TypePicker({
	label,
	value,
	options,
	onChange,
	placeholder = "",
	filterGroups = false,
	translate = true,
	triggerContent,
	triggerClass = "type-picker-trigger",
}: {
	label: string;
	value: string;
	options: TypeOption[];
	onChange: (id: string) => void;
	placeholder?: string;
	/** Show one chip per group so long lists (quests by category) can be narrowed in one click. */
	filterGroups?: boolean;
	/** Option names are user content (quest titles) when false, so they are not passed through the UI dictionary. */
	translate?: boolean;
	/** Replaces the labelled field with a plain button, for "add" actions that open the list directly. */
	triggerContent?: React.ReactNode;
	triggerClass?: string;
}) {
	const tr = (text: string) => (translate ? tUI(text) : text);
	const id = useId(),
		menuId = id + "menu",
		trigger = useRef<HTMLButtonElement>(null),
		menu = useRef<HTMLDivElement>(null),
		search = useRef<HTMLInputElement>(null),
		[open, setOpen] = useState(false),
		[query, setQuery] = useState(""),
		[active, setActive] = useState(value),
		[group, setGroup] = useState("");
	const current = options.find((o) => o.id === value),
		needle = query.trim().toLowerCase(),
		match = resourceMatcher(query),
		visible = options.filter(
			(o) => (!group || o.group === group) && (match(o.id, tr(o.name)) || (needle.length > 2 && tUI(o.hint || "").toLowerCase().includes(needle)))
		),
		allGroups = [...new Set(options.map((o) => o.group || ""))].filter(Boolean),
		groups: [string, TypeOption[]][] = [];
	for (const o of visible) {
		const group = groups.find(([name]) => name === (o.group || ""));
		if (group) group[1].push(o);
		else groups.push([o.group || "", [o]]);
	}
	const detail = options.find((o) => o.id === active) || current || visible[0];
	useEffect(() => {
		const el = menu.current;
		if (!el) return;
		const toggle = (e: Event) => {
			const isOpen = (e as ToggleEvent).newState === "open";
			setOpen(isOpen);
			if (!isOpen) return;
			setQuery("");
			setGroup("");
			setActive(value);
			place(trigger.current, el);
			search.current?.focus();
		};
		el.addEventListener("toggle", toggle);
		return () => el.removeEventListener("toggle", toggle);
	}, [value]);
	const pick = (next: string) => {
		onChange(next);
		menu.current?.hidePopover();
		trigger.current?.focus();
	};
	const optionsIn = () => Array.from(menu.current?.querySelectorAll<HTMLButtonElement>("[role=option]") || []);
	return (
		<div className={"field type-picker" + (triggerContent ? " bare" : "")}>
			{!triggerContent && (
				<span className="type-picker-label">
					{tUI(label)}
					<InfoTip text={current?.hint && tUI(current.hint)} />
				</span>
			)}
			<button
				ref={trigger}
				type="button"
				className={triggerClass + " type-picker-anchor"}
				popoverTarget={menuId}
				aria-haspopup="listbox"
				aria-expanded={open}
				aria-controls={menuId}
				aria-label={triggerContent ? tUI(label) : tUI(label) + ": " + (current ? tr(current.name) : value || tUI(placeholder))}
				title={current?.hint && tUI(current.hint)}
				style={anchorVar(id)}>
				{triggerContent || (
					<>
						{current?.icon && <ItemIcon id={current.icon} size={18} />}
						<span className={"type-picker-value" + (current || value ? "" : " empty")}>{current ? tr(current.name) : value || tUI(placeholder)}</span>
						<ChevronDown size={14} aria-hidden="true" />
					</>
				)}
			</button>
			<div
				ref={menu}
				id={menuId}
				popover="auto"
				className="type-picker-menu"
				style={anchorVar(id)}
				onKeyDown={(e) => {
					if (e.key !== "ArrowDown" && e.key !== "ArrowUp" && e.key !== "Enter") return;
					const buttons = optionsIn(),
						index = buttons.indexOf(e.target as HTMLButtonElement);
					if (e.key === "Enter") {
						if (e.target === search.current && buttons[0]) {
							e.preventDefault();
							pick(buttons[0].dataset.value!);
						}
						return;
					}
					e.preventDefault();
					if (index < 0) return buttons[e.key === "ArrowDown" ? 0 : buttons.length - 1]?.focus();
					const next = index + (e.key === "ArrowDown" ? 1 : -1);
					if (next < 0) search.current?.focus();
					else buttons[Math.min(buttons.length - 1, next)]?.focus();
				}}>
				<label className="type-picker-search">
					<Search size={15} aria-hidden="true" />
					<input ref={search} value={query} onChange={(e) => setQuery(e.target.value)} placeholder={tUI("Type to filter")} aria-label={tUI("Search…")} aria-controls={id + "list"} />
				</label>
				{filterGroups && allGroups.length > 1 && (
					<div className="type-picker-chips" role="group" aria-label={tUI("Filter by category")}>
						<button type="button" aria-pressed={!group} onClick={() => setGroup("")}>
							{tUI("All")}
						</button>
						{allGroups.map((name) => (
							<button type="button" key={name} aria-pressed={group === name} onClick={() => setGroup(group === name ? "" : name)}>
								{tr(name)}
							</button>
						))}
					</div>
				)}
				<div className="type-picker-body">
					<div role="listbox" id={id + "list"} aria-label={tUI(label)} className="type-picker-list">
						{groups.map(([group, items]) => (
							<div role="group" aria-label={group ? tr(group) : undefined} key={group}>
								{group && <div className="type-picker-group">{tr(group)}</div>}
								{items.map((o) => (
									<button
										type="button"
										role="option"
										aria-selected={o.id === value}
										data-value={o.id}
										key={o.id}
										className={o.id === active ? "active" : ""}
										onPointerEnter={() => setActive(o.id)}
										onFocus={() => setActive(o.id)}
										onClick={() => pick(o.id)}>
										<ItemIcon id={o.icon} size={20} />
										<span>
											<strong>{tr(o.name)}</strong>
											{o.hint && <small>{tUI(o.hint)}</small>}
										</span>
									</button>
								))}
							</div>
						))}
						{!visible.length && <p className="type-picker-empty">{tUI("No results")}</p>}
					</div>
					{detail && (
						<aside className="type-picker-detail" aria-live="polite">
							<ItemIcon id={detail.icon} size={32} />
							<strong>{tr(detail.name)}</strong>
							<code>{detail.id}</code>
							<p>{detail.detail ?? (detail.hint ? tUI(detail.hint) : tUI("No description"))}</p>
						</aside>
					)}
				</div>
			</div>
		</div>
	);
}

/** Radio choices drawn as cards (with a one-line explanation) or as a compact segmented control. */
export function ChoiceCards({
	label,
	value,
	options,
	onChange,
	compact = false,
}: {
	label: string;
	value: string;
	options: [string, string, string?][];
	onChange: (id: string) => void;
	compact?: boolean;
}) {
	const name = useId();
	return (
		<fieldset className={"choice-cards" + (compact ? " compact" : "")}>
			<legend>{tUI(label)}</legend>
			<div>
				{options.map(([id, title, hint]) => (
					<label key={id} className={"choice-card" + (id === value ? " selected" : "")} title={compact && hint ? tUI(hint) : undefined}>
						<input type="radio" name={name} value={id} checked={id === value} onChange={() => onChange(id)} />
						<strong>{tUI(title)}</strong>
						{!compact && hint && <small>{tUI(hint)}</small>}
					</label>
				))}
			</div>
		</fieldset>
	);
}
