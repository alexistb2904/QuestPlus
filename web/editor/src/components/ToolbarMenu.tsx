import { ChevronDown } from "lucide-react";
import React, { useEffect, useId, useRef, useState } from "react";
import { anchorVar, place } from "./TypePicker";

const ITEMS = "[role^=menuitem]:not(:disabled)";

/** Toolbar button that opens a top-layer menu. MenuItem actions close it, toggles and the zoom stepper keep it open. */
export function ToolbarMenu({ label, icon, summary, children }: { label: string; icon: React.ReactNode; summary?: string; children: React.ReactNode }) {
	const id = useId(),
		menuId = id + "menu",
		trigger = useRef<HTMLButtonElement>(null),
		menu = useRef<HTMLDivElement>(null),
		[open, setOpen] = useState(false);
	useEffect(() => {
		const el = menu.current;
		if (!el) return;
		const toggle = (e: Event) => {
			const isOpen = (e as ToggleEvent).newState === "open";
			setOpen(isOpen);
			if (!isOpen) return;
			place(trigger.current, el);
			el.querySelector<HTMLElement>(ITEMS)?.focus();
		};
		el.addEventListener("toggle", toggle);
		return () => el.removeEventListener("toggle", toggle);
	}, []);
	return (
		<>
			<button
				ref={trigger}
				type="button"
				className="button secondary toolbar-menu-trigger"
				popoverTarget={menuId}
				aria-haspopup="menu"
				aria-expanded={open}
				aria-controls={menuId}
				aria-label={summary ? label + " " + summary : label}
				title={label}
				style={anchorVar(id)}>
				{icon}
				<span className="toolbar-menu-label">{label}</span>
				{summary && <span className="toolbar-menu-summary">{summary}</span>}
				<ChevronDown size={14} aria-hidden="true" className="toolbar-menu-chevron" />
			</button>
			<div
				ref={menu}
				id={menuId}
				popover="auto"
				role="menu"
				aria-label={label}
				className="toolbar-menu"
				style={anchorVar(id)}
				onClick={(e) => {
					if ((e.target as HTMLElement).closest(".toolbar-menu-item[role=menuitem]")) menu.current?.hidePopover();
				}}
				onKeyDown={(e) => {
					if (!["ArrowDown", "ArrowUp", "Home", "End"].includes(e.key)) return;
					e.preventDefault();
					const items = Array.from(menu.current?.querySelectorAll<HTMLElement>(ITEMS) || []),
						index = items.indexOf(document.activeElement as HTMLElement),
						next = e.key === "Home" ? 0 : e.key === "End" ? items.length - 1 : (index + (e.key === "ArrowDown" ? 1 : -1) + items.length) % items.length;
					items[next]?.focus();
				}}>
				{children}
			</div>
		</>
	);
}

/** One menu row: icon, name and a short line saying what it opens. */
export function MenuItem({
	icon,
	label,
	hint,
	onSelect,
	disabled,
	checked,
	role = "menuitem",
}: {
	icon: React.ReactNode;
	label: string;
	hint?: string;
	onSelect: () => void;
	disabled?: boolean;
	checked?: boolean;
	role?: "menuitem" | "menuitemcheckbox";
}) {
	return (
		<button type="button" role={role} aria-checked={role === "menuitem" ? undefined : !!checked} className="toolbar-menu-item" disabled={disabled} onClick={onSelect}>
			<span className="toolbar-menu-icon">{icon}</span>
			<span className="toolbar-menu-text">
				<strong>{label}</strong>
				{hint && <small>{hint}</small>}
			</span>
			{role === "menuitemcheckbox" && <span className="toolbar-menu-switch" aria-hidden="true" />}
		</button>
	);
}
