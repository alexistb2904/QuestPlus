import React, { useEffect, useRef, useState } from "react";
import { visibleRows } from "../core/virtual-grid.js";

export function ResourceGrid({
	items,
	onPick,
	renderIcon,
	selected,
}: {
	items: { id: string; label: string }[];
	onPick: (id: string) => void;
	renderIcon?: (id: string) => React.ReactNode;
	selected?: string;
}) {
	const ref = useRef<HTMLDivElement>(null),
		pending = useRef<number | null>(null),
		[width, setWidth] = useState(0),
		[top, setTop] = useState(0),
		[height, setHeight] = useState(0);
	useEffect(() => {
		const el = ref.current!;
		const observer = new ResizeObserver(() => {
			setWidth(el.clientWidth);
			setHeight(el.clientHeight);
		});
		observer.observe(el);
		return () => observer.disconnect();
	}, []);
	const { columns, rowHeight, rows, first, last } = visibleRows(items.length, width, top, height);
	useEffect(() => {
		if (pending.current !== null) {
			const button = ref.current?.querySelector<HTMLButtonElement>(`[data-index="${pending.current}"]`);
			if (button) {
				button.focus();
				pending.current = null;
			}
		}
	}, [first, last, columns, items]);
	return (
		<div
			ref={ref}
			className="registry-virtual"
			onScroll={(e) => setTop(e.currentTarget.scrollTop)}
			onKeyDown={(e) => {
				const current = Number((e.target as HTMLElement).dataset.index);
				if (!Number.isInteger(current)) return;
				const step = e.key === "ArrowRight" ? 1 : e.key === "ArrowLeft" ? -1 : e.key === "ArrowDown" ? columns : e.key === "ArrowUp" ? -columns : 0;
				if (!step) return;
				e.preventDefault();
				const next = Math.max(0, Math.min(items.length - 1, current + step));
				pending.current = next;
				const el = ref.current!,
					button = el.querySelector<HTMLButtonElement>(`[data-index="${next}"]`);
				if (button) {
					button.focus();
					pending.current = null;
					button.scrollIntoView({ block: "nearest" });
				} else {
					el.scrollTop = Math.floor(next / columns) * rowHeight;
					setTop(el.scrollTop);
				}
			}}>
			<div style={{ height: rows * rowHeight, position: "relative" }}>
				<div className="registry-virtual-rows" style={{ top: first * rowHeight, gridTemplateColumns: `repeat(${columns},minmax(0,1fr))` }}>
					{items.slice(first * columns, last * columns).map(({ id, label }, offset) => (
						<button key={id} data-index={first * columns + offset} type="button" className="picker-item" title={id} aria-pressed={selected === undefined ? undefined : selected === id} onClick={() => onPick(id)}>
							{renderIcon?.(id)}
							<span>{label}</span>
						</button>
					))}
				</div>
			</div>
		</div>
	);
}
