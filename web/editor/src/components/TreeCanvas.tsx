import { GitBranch, MessageCircle, Plus, SquarePlus, StickyNote, X } from "lucide-react";
import React, { useEffect, useMemo, useRef, useState } from "react";
import { backgroundRect, edgePoints, insertWaypoint, nearestSegment } from "../core/diagram-geometry.js";
import { clampZoom, screenPoint, snapEditor, wheelZoomFactor, worldPoint, zoomEditorAt } from "../core/editor-geometry.js";
import { gameLayout } from "../core/game-layout.js";
import { annotationRich, annotationText, text } from "../core/project.js";
import { dependencyError } from "../core/quest-links.js";
import { QUEST_TEMPLATES } from "../core/quest-templates.js";
import { richToHtml } from "../core/rich-format.js";
import { savedQuestTemplates } from "../core/studio-template-library.js";
import type { Camera, Category, Position, Quest, Selection } from "../types";
import { tUI } from "../ui/ui-i18n.js";
import { ItemIcon } from "./IconPicker";

type Props = {
	quests: Quest[];
	allQuests: Quest[];
	onLink: (sourceId: string, targetId: string) => void;
	category?: Category;
	language: string;
	selection: Selection | null;
	camera: Camera;
	onCamera: (v: Camera) => void;
	onSelect: (s: Selection) => void;
	showGrid: boolean;
	gridSize: number;
	snap: boolean;
	tool: "select" | "pan" | "background";
	showQuestTitles?: boolean;
	onPosition: (type: "quest" | "annotation", id: string, at: Position) => void;
	onCreateAt?: (kind: "quest" | "annotation", at: Position) => void;
	onCreateTemplate?: (templateId: string, at: Position) => void;
	onBackgroundCreate?: (rect: { x: number; y: number; width: number; height: number }) => void;
	onRoute?: (from: string, to: string, points: Position[]) => void;
	onResize?: (kind: "quest" | "annotation", id: string, size: { width?: number; height?: number; size?: number }) => void;
};
export function TreeCanvas({
	quests,
	allQuests,
	onLink,
	category,
	language,
	selection,
	camera,
	onCamera,
	onSelect,
	onPosition,
	onCreateAt,
	onCreateTemplate,
	onResize,
	onBackgroundCreate,
	onRoute,
	showGrid,
	gridSize,
	snap,
	tool,
	showQuestTitles = false,
}: Props) {
	const ref = useRef<HTMLDivElement>(null);
	const [contextMenu, setContextMenu] = useState<{ x: number; y: number; at: Position } | null>(null);
	const [customTemplates, setCustomTemplates] = useState<{ id: string; label: string }[]>([]);
	const resize = useRef<{ type: "quest" | "annotation"; id: string; startX: number; startY: number; width: number; height: number; size: number; pointer: number } | null>(null);
	const [resizeGhost, setResizeGhost] = useState<{ id: string; width?: number; height?: number; size?: number } | null>(null);
	const resizePending = useRef<{ id: string; width?: number; height?: number; size?: number } | null>(null);
	const startResize = (e: React.PointerEvent, type: "quest" | "annotation", id: string, width: number, height: number, nodeSize: number) => {
		e.preventDefault();
		e.stopPropagation();
		onSelect({ type, id });
		resize.current = { type, id, startX: e.clientX, startY: e.clientY, width, height, size: nodeSize, pointer: e.pointerId };
		e.currentTarget.setPointerCapture(e.pointerId);
	};
	const moveResize = (e: React.PointerEvent) => {
		const v = resize.current;
		if (!v || v.pointer !== e.pointerId) return;
		e.stopPropagation();
		const scale = clampZoom(camera.zoom);
		const dx = (e.clientX - v.startX) / scale,
			dy = (e.clientY - v.startY) / scale;
		let next: { id: string; width?: number; height?: number; size?: number };
		if (v.type === "quest") {
			const sz = Math.max(1, Math.round(v.size + dx / 5));
			next = { id: v.id, size: sz };
		} else next = { id: v.id, width: Math.max(1, Math.round(v.width + dx)), height: Math.max(1, Math.round(v.height + dy)) };
		resizePending.current = next;
		setResizeGhost(next);
	};
	const endResize = (e: React.PointerEvent) => {
		const v = resize.current;
		if (!v || v.pointer !== e.pointerId) return;
		e.stopPropagation();
		if (e.type !== "pointercancel" && resizePending.current?.id === v.id) onResize?.(v.type, v.id, resizePending.current);
		resize.current = null;
		resizePending.current = null;
		setResizeGhost(null);
		try {
			e.currentTarget.releasePointerCapture(e.pointerId);
		} catch {}
	};
	const handle = (type: "quest" | "annotation", id: string, width: number, height: number, sz: number) => (
		<button
			type="button"
			className="node-resize-handle"
			aria-label={tUI("Resize")}
			title={tUI("Drag to resize")}
			onPointerDown={(e) => startResize(e, type, id, width, height, sz)}
			onPointerMove={moveResize}
			onPointerUp={endResize}
			onPointerCancel={endResize}
		/>
	);

	const pointers = useRef(new Map<number, { x: number; y: number }>());
	const gesture = useRef<any>(null);
	const dragging = useRef<any>(null);
	const drawing = useRef<{ start: Position; end: Position } | null>(null);
	const [draft, setDraft] = useState<{ x: number; y: number; width: number; height: number } | null>(null);
	const routeDrag = useRef<{ from: string; to: string; points: Position[]; index: number; pointer: number } | null>(null);
	const [routeGhost, setRouteGhost] = useState<{ from: string; to: string; points: Position[] } | null>(null);
	type Cable = { from: string; point: { x: number; y: number }; to: string | null; error: string; moved: boolean; pointerId?: number };
	const cable = useRef<Cable | null>(null),
		[connection, setConnection] = useState<Cable | null>(null);
	const showCable = (next: Cable | null) => {
		cable.current = next;
		setConnection(next);
	};
	const targetAt = (x: number, y: number) => document.elementFromPoint(x, y)?.closest<HTMLElement>('[data-node-type="quest"]')?.dataset.nodeId || null;
	const selectNode = (id: string) => {
		if (cable.current) {
			onLink(cable.current.from, id);
			showCable(null);
		} else onSelect({ type: "quest", id });
	};
	const [size, setSize] = useState({ width: 720, height: 520 });
	useEffect(() => {
		const el = ref.current;
		if (!el) return;
		const resize = () => setSize({ width: el.clientWidth, height: el.clientHeight });
		resize();
		const observer = new ResizeObserver(resize);
		observer.observe(el);
		return () => observer.disconnect();
	}, []);
	const graph = useMemo(() => {
		try {
			return gameLayout(quests);
		} catch (e) {
			return { points: new Map(), edges: [] };
		}
	}, [quests]);
	const pixel = (p: Position) => screenPoint(p, camera, size);
	const world = (x: number, y: number) => worldPoint({ x, y }, camera, size);
	const local = (e: React.PointerEvent) => {
		const rect = ref.current!.getBoundingClientRect();
		return { x: e.clientX - rect.left, y: e.clientY - rect.top };
	};
	// Pointer-centered zoom uniformly scales all diagram content in both clients.
	// Shift+wheel or a horizontal trackpad scroll pans the viewport without zooming.
	useEffect(() => {
		const el = ref.current;
		if (!el) return;
		const onWheel = (e: WheelEvent) => {
			e.preventDefault();
			if (e.shiftKey || Math.abs(e.deltaX) > Math.abs(e.deltaY)) {
				const factor = e.deltaMode === 1 ? 22 : e.deltaMode === 2 ? size.height * 0.85 : 1;
				const dx = (e.shiftKey && !e.deltaX ? e.deltaY : e.deltaX) * factor;
				const dy = e.shiftKey ? 0 : e.deltaY * factor;
				onCamera({ x: camera.x + dx / clampZoom(camera.zoom), y: camera.y + dy / clampZoom(camera.zoom), zoom: clampZoom(camera.zoom) });
				return;
			}
			const box = el.getBoundingClientRect();
			const factor = wheelZoomFactor(e.deltaY, e.deltaMode);
			onCamera(zoomEditorAt(camera, clampZoom(camera.zoom) * factor, { x: e.clientX - box.left, y: e.clientY - box.top }, size));
		};
		el.addEventListener("wheel", onWheel, { passive: false });
		return () => el.removeEventListener("wheel", onWheel);
	}, [camera, size, onCamera]);
	const onDown = (e: React.PointerEvent) => {
		if (contextMenu) setContextMenu(null);
		if (e.button !== 0 && e.pointerType !== "touch") return;
		const target = tool === "pan" ? null : (e.target as Element).closest<HTMLElement>("[data-node-id]");
		const p = local(e);
		pointers.current.set(e.pointerId, p);
		ref.current?.setPointerCapture(e.pointerId);
		if (tool === "background") {
			e.preventDefault();
			const start = world(p.x, p.y);
			drawing.current = { start, end: start };
			setDraft(backgroundRect(start, start));
			return;
		}
		if (pointers.current.size === 2) {
			const pair = [...pointers.current.values()];
			gesture.current = {
				d: Math.hypot(pair[1].x - pair[0].x, pair[1].y - pair[0].y) || 1,
				zoom: clampZoom(camera.zoom),
				center: { x: (pair[0].x + pair[1].x) / 2, y: (pair[0].y + pair[1].y) / 2 },
				base: camera,
			};
			dragging.current = null;
			showCable(null);
			return;
		}
		const port = (e.target as Element).closest<HTMLElement>("[data-link-port]");
		if (port && target?.dataset.nodeId) {
			if (port.getAttribute("aria-disabled") === "true") return;
			showCable({ from: target.dataset.nodeId, point: p, to: null, error: "", moved: false, pointerId: e.pointerId });
			dragging.current = null;
			onSelect({ type: "quest", id: target.dataset.nodeId });
			return;
		}
		if (cable.current) {
			showCable({ ...cable.current, point: p, pointerId: e.pointerId });
			dragging.current = null;
			return;
		}
		const type = target?.dataset.nodeType as "quest" | "annotation" | undefined,
			id = target?.dataset.nodeId;
		if (type && id) {
			const source = type === "quest" ? graph.points.get(id) : category?.annotations?.find((a) => a.id === id);
			if (source) {
				dragging.current = { type, id, origin: p, initial: { x: source.x, y: source.y }, moved: false };
				onSelect({ type, id });
			}
		} else dragging.current = { type: "pan", origin: p, initial: { x: camera.x, y: camera.y }, moved: false };
	};
	const onMove = (e: React.PointerEvent) => {
		const p = local(e);
		if (!pointers.current.has(e.pointerId)) return;
		pointers.current.set(e.pointerId, p);
		if (drawing.current) {
			drawing.current.end = world(p.x, p.y);
			setDraft(backgroundRect(drawing.current.start, drawing.current.end));
			return;
		}
		if (pointers.current.size === 2 && gesture.current) {
			const pair = [...pointers.current.values()],
				g = gesture.current;
			const midpoint = { x: (pair[0].x + pair[1].x) / 2, y: (pair[0].y + pair[1].y) / 2 };
			const next = clampZoom((g.zoom * Math.hypot(pair[1].x - pair[0].x, pair[1].y - pair[0].y)) / g.d);
			const anchor = worldPoint(g.center, g.base, size);
			onCamera({ zoom: next, x: anchor.x - (midpoint.x - size.width / 2) / next, y: anchor.y - (midpoint.y - size.height / 2) / next });
			return;
		}
		if (cable.current?.pointerId === e.pointerId) {
			const current = cable.current,
				to = targetAt(e.clientX, e.clientY);
			showCable({ ...current, point: p, to, error: to === current.to ? current.error : to ? dependencyError(allQuests, current.from, to) : "", moved: true });
			return;
		}
		const drag = dragging.current;
		if (!drag) return;
		const dx = p.x - drag.origin.x,
			dy = p.y - drag.origin.y;
		if (Math.hypot(dx, dy) > 3) drag.moved = true;
		if (!drag.moved) return;
		if (drag.type === "pan") onCamera({ x: drag.initial.x - dx / clampZoom(camera.zoom), y: drag.initial.y - dy / clampZoom(camera.zoom), zoom: clampZoom(camera.zoom) });
		else {
			drag.pending = { x: Math.round(drag.initial.x + dx / clampZoom(camera.zoom)), y: Math.round(drag.initial.y + dy / clampZoom(camera.zoom)) };
			if (snap) drag.pending = snapEditor(drag.pending, gridSize);
			setGhost({ id: drag.id, ...drag.pending });
		}
	};
	const [ghost, setGhost] = useState<any>(null);
	const onUp = (e: React.PointerEvent) => {
		pointers.current.delete(e.pointerId);
		try {
			ref.current?.releasePointerCapture(e.pointerId);
		} catch {}
		if (pointers.current.size < 2) gesture.current = null;
		if (drawing.current) {
			const rect = backgroundRect(drawing.current.start, drawing.current.end);
			if (e.type !== "pointercancel" && rect.width >= 16 && rect.height >= 16) onBackgroundCreate?.(rect);
			drawing.current = null;
			setDraft(null);
			return;
		}
		const active = cable.current;
		if (active?.pointerId === e.pointerId) {
			const to = targetAt(e.clientX, e.clientY);
			if (e.type !== "pointercancel" && to && to !== active.from) {
				onLink(active.from, to);
				showCable(null);
			} else if (e.type === "pointercancel" || active.moved) showCable(null);
			else showCable({ ...active, pointerId: undefined });
			return;
		}
		const drag = dragging.current;
		if (e.type !== "pointercancel" && drag?.pending) onPosition(drag.type, drag.id, drag.pending);
		dragging.current = null;
		setGhost(null);
	};
	const routePosition = (e: React.PointerEvent) => {
		const p = local(e);
		let at = world(p.x, p.y);
		if (snap) at = snapEditor(at, gridSize);
		return { x: Math.round(Math.max(-100000, Math.min(100000, at.x))), y: Math.round(Math.max(-100000, Math.min(100000, at.y))) };
	};
	const startRoute = (e: React.PointerEvent, from: string, to: string, points: Position[], index: number) => {
		if (tool !== "select" || e.button !== 0) return;
		e.preventDefault();
		e.stopPropagation();
		onSelect({ type: "quest", id: to });
		routeDrag.current = { from, to, points, index, pointer: e.pointerId };
		setRouteGhost({ from, to, points });
		e.currentTarget.setPointerCapture(e.pointerId);
	};
	const moveRoute = (e: React.PointerEvent) => {
		const drag = routeDrag.current;
		if (!drag || drag.pointer !== e.pointerId) return;
		e.stopPropagation();
		drag.points = drag.points.map((p, i) => (i === drag.index ? routePosition(e) : p));
		setRouteGhost({ from: drag.from, to: drag.to, points: drag.points });
	};
	const endRoute = (e: React.PointerEvent) => {
		const drag = routeDrag.current;
		if (!drag || drag.pointer !== e.pointerId) return;
		e.stopPropagation();
		if (e.type !== "pointercancel") onRoute?.(drag.from, drag.to, drag.points);
		routeDrag.current = null;
		setRouteGhost(null);
		try {
			e.currentTarget.releasePointerCapture(e.pointerId);
		} catch {}
	};
	return (
		<div
			className="viewport"
			id="viewport"
			ref={ref}
			aria-label={tUI("Interactive quest tree")}
			role="region"
			tabIndex={0}
			onPointerDown={onDown}
			onPointerMove={onMove}
			onPointerUp={onUp}
			onPointerCancel={onUp}
			onContextMenu={(e) => {
				e.preventDefault();
				const rect = ref.current!.getBoundingClientRect();
				const x = e.clientX - rect.left,
					y = e.clientY - rect.top;
				setCustomTemplates(savedQuestTemplates().map(({ id, label }) => ({ id, label })));
				setContextMenu({ x: Math.min(x, Math.max(0, rect.width - 185)), y: Math.min(y, Math.max(0, rect.height - 115)), at: world(x, y) });
			}}
			onKeyDown={(e) => {
				if (e.key === "Escape") {
					showCable(null);
					setContextMenu(null);
					dragging.current = null;
					drawing.current = null;
					setDraft(null);
					routeDrag.current = null;
					setRouteGhost(null);
					return;
				}
				if ((e.target as HTMLElement).closest("[data-node-id],[data-waypoint]")) return;
				const delta = 24 / clampZoom(camera.zoom);
				if (["ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown"].includes(e.key)) {
					e.preventDefault();
					onCamera({
						...camera,
						x: camera.x + (e.key === "ArrowLeft" ? -delta : e.key === "ArrowRight" ? delta : 0),
						y: camera.y + (e.key === "ArrowUp" ? -delta : e.key === "ArrowDown" ? delta : 0),
					});
				}
			}}
			style={
				{
					backgroundImage: showGrid ? undefined : "none",
					backgroundSize: gridSize * clampZoom(camera.zoom) + "px " + gridSize * clampZoom(camera.zoom) + "px",
					backgroundPosition: -camera.x * clampZoom(camera.zoom) + size.width / 2 + "px " + (-camera.y * clampZoom(camera.zoom) + size.height / 2) + "px",
					"--diagram-zoom": clampZoom(camera.zoom),
				} as React.CSSProperties
			}>
			{contextMenu && (
				<div className="graph-context-menu" role="menu" style={{ left: contextMenu.x, top: contextMenu.y }} onPointerDown={(e) => e.stopPropagation()}>
					<button
						role="menuitem"
						type="button"
						onClick={() => {
							onCreateAt?.("quest", contextMenu.at);
							setContextMenu(null);
						}}>
						<SquarePlus size={15} />
						{tUI("Create quest")}
					</button>
					<details className="template-submenu">
						<summary>{tUI("Choose quest template")}</summary>
						<div className="template-options">
							{QUEST_TEMPLATES.map((template) => (
								<button
									key={template.id}
									role="menuitem"
									title={template.description}
									type="button"
									onClick={() => {
										onCreateTemplate?.(template.id, contextMenu.at);
										setContextMenu(null);
									}}>
									{template.label}
								</button>
							))}
							{customTemplates.map((template) => (
								<button
									key={template.id}
									role="menuitem"
									title="Saved custom template"
									type="button"
									onClick={() => {
										onCreateTemplate?.(template.id, contextMenu.at);
										setContextMenu(null);
									}}>
									★ {template.label}
								</button>
							))}
						</div>
					</details>
					<button
						role="menuitem"
						type="button"
						onClick={() => {
							onCreateAt?.("annotation", contextMenu.at);
							setContextMenu(null);
						}}>
						<StickyNote size={15} />
						{tUI("Create annotation")}
					</button>
					<button
						role="menuitem"
						type="button"
						onClick={() => {
							onBackgroundCreate?.({ ...contextMenu.at, width: 280, height: 180 });
							setContextMenu(null);
						}}>
						<SquarePlus size={15} />
						{tUI("Add background")}
					</button>
				</div>
			)}
			<div className="graph-backgrounds">
				{(category?.annotations || [])
					.filter((a) => a.style === "background")
					.map((a) => {
						const at = ghost?.id === a.id ? ghost : a,
							p = pixel(at),
							z = clampZoom(camera.zoom);
						const width = resizeGhost?.id === a.id ? (resizeGhost.width ?? a.width ?? 280) : (a.width ?? 280);
						const height = resizeGhost?.id === a.id ? (resizeGhost.height ?? a.height ?? 180) : (a.height ?? 180);
						return (
							<div
								key={a.id}
								role="button"
								tabIndex={0}
								aria-label={tUI("Background") + " " + annotationText(a, language)}
								data-node-id={a.id}
								data-node-type="annotation"
								className={"graph-background" + (selection?.type === "annotation" && selection.id === a.id ? " selected" : "")}
								onKeyDown={(e) => {
									if (e.key === "Enter" || e.key === " ") {
										e.preventDefault();
										onSelect({ type: "annotation", id: a.id });
									}
								}}
								style={{
									left: p.x,
									top: p.y,
									width,
									height,
									transform: "translate(-50%,-50%) scale(" + z + ")",
									backgroundColor: (a.color || "#6b727e") + "24",
									borderColor: (a.color || "#6b727e") + "70",
								}}>
								{selection?.type === "annotation" && selection.id === a.id && handle("annotation", a.id, width, height, 32)}
							</div>
						);
					})}
			</div>
			{draft &&
				(() => {
					const p = pixel(draft),
						z = clampZoom(camera.zoom);
					return (
						<div
							className="background-draft"
							style={{ left: p.x, top: p.y, width: draft.width, height: draft.height, transform: "translate(-50%,-50%) scale(" + z + ")" }}
						/>
					);
				})()}
			<svg className="edges" viewBox={"0 0 " + size.width + " " + size.height} role="group" aria-label={tUI("Drag a link to add a waypoint")}>
				<defs>
					<marker id="quest-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
						<path d="M0,0 L8,4 L0,8 Z" className="arrow-head" />
					</marker>
				</defs>
				{graph.edges.map((edge: any) => {
					const a = graph.points.get(edge.from),
						b = graph.points.get(edge.to);
					if (!a || !b) return null;
					const p = pixel(ghost?.id === edge.from ? ghost : a),
						q = pixel(ghost?.id === edge.to ? ghost : b);
					const source = allQuests.find((z) => z.id === edge.from),
						target = allQuests.find((z) => z.id === edge.to);
					if (source?.presentation?.hideDependentLines || target?.presentation?.hideDependencyLines) return null;
					p.y += ((source?.size || 32) * clampZoom(camera.zoom)) / 2;
					q.y -= ((target?.size || 32) * clampZoom(camera.zoom)) / 2;
					const authored =
						routeGhost?.from === edge.from && routeGhost.to === edge.to
							? routeGhost.points
							: category?.linkRoutes?.find((r) => r.from === edge.from && r.to === edge.to)?.points;
					const points = edgePoints(p, q, authored?.map(pixel)),
						path = points.map((pt, i) => (i ? "L" : "M") + pt.x + " " + pt.y).join(" ");
					const selected = selection?.type === "quest" && [edge.from, edge.to].includes(selection.id);
					return (
						<g key={edge.from + ">" + edge.to} data-edge-from={edge.from} data-edge-to={edge.to}>
							<path
								markerEnd="url(#quest-arrow)"
								className={selected ? "selected-edge" : ""}
								style={{ strokeWidth: Math.max(0.2, 1.5 * clampZoom(camera.zoom)) }}
								d={path}
							/>
							<path
								className="edge-hit"
								d={path}
								aria-label={tUI("Drag a link to add a waypoint")}
								role="button"
								tabIndex={0}
								onPointerDown={(e) => {
									if (!onRoute || tool !== "select") return;
									// Persist only the clicked anchor; generated orthogonal elbows are visual.
									const localPoint = local(e),
										index = nearestSegment(points, localPoint);
									const next = insertWaypoint(authored || [], index, routePosition(e));
									if (next.length > 64) return;
									startRoute(e, edge.from, edge.to, next, Math.min(index, next.length - 1));
								}}
								onPointerMove={moveRoute}
								onPointerUp={endRoute}
								onPointerCancel={endRoute}
								onKeyDown={(e) => {
									if (e.key === "Enter") {
										e.preventDefault();
										onSelect({ type: "quest", id: edge.to });
										const current = authored || [];
										if (current.length >= 64) return;
										const next = insertWaypoint(current, current.length, world((p.x + q.x) / 2, (p.y + q.y) / 2));
										onRoute?.(edge.from, edge.to, next);
									}
								}}
							/>
							{selected &&
								authored?.map((at, i) => {
									const point = pixel(at);
									return (
										<circle
											key={i}
											className="link-waypoint"
											data-waypoint={i}
											role="button"
											tabIndex={0}
											aria-label={tUI("Link waypoint") + " " + (i + 1)}
											cx={point.x}
											cy={point.y}
											r={5 * clampZoom(camera.zoom)}
											style={{ strokeWidth: Math.max(0.3, 2 * clampZoom(camera.zoom)) }}
											onPointerDown={(e) => startRoute(e, edge.from, edge.to, [...authored], i)}
											onPointerMove={moveRoute}
											onPointerUp={endRoute}
											onPointerCancel={endRoute}
											onDoubleClick={() =>
												onRoute?.(
													edge.from,
													edge.to,
													authored.filter((_, n) => n !== i)
												)
											}
											onKeyDown={(e) => {
												e.stopPropagation();
												const dx = e.key === "ArrowLeft" ? -8 : e.key === "ArrowRight" ? 8 : 0,
													dy = e.key === "ArrowUp" ? -8 : e.key === "ArrowDown" ? 8 : 0;
												if (e.key === "Delete" || e.key === "Backspace") {
													e.preventDefault();
													onRoute?.(
														edge.from,
														edge.to,
														authored.filter((_, n) => n !== i)
													);
												} else if (dx || dy) {
													e.preventDefault();
													onRoute?.(
														edge.from,
														edge.to,
														authored.map((p, n) => (n === i ? { x: p.x + dx, y: p.y + dy } : p))
													);
												}
											}}>
											<title>{tUI("Remove waypoint") + " · Delete / " + tUI("Link waypoint")}</title>
										</circle>
									);
								})}
						</g>
					);
				})}
				{connection &&
					graph.points.has(connection.from) &&
					(() => {
						const start = pixel(graph.points.get(connection.from));
						start.y += ((allQuests.find((q) => q.id === connection.from)?.size || 32) * clampZoom(camera.zoom)) / 2;
						const end = connection.point,
							bend = Math.max(45 * clampZoom(camera.zoom), Math.abs(end.y - start.y) * 0.55);
						return (
							<path
								style={{ strokeWidth: 2.5 * clampZoom(camera.zoom) }}
								className={"connection-preview" + (connection.error ? " invalid" : "")}
								d={"M" + start.x + " " + start.y + " C" + start.x + " " + (start.y + bend) + "," + end.x + " " + (end.y - bend) + "," + end.x + " " + end.y}
							/>
						);
					})()}
			</svg>
			{connection && (
				<div className={"connection-hint" + (connection.error ? " invalid" : "")} role="status">
					<GitBranch size={14} />
					<span>{tUI(connection.error || "Drop on a quest to make it depend on this quest. Esc to cancel.")}</span>
					<button aria-label={tUI("Cancel connection")} onPointerDown={(e) => e.stopPropagation()} onClick={() => showCable(null)}>
						<X size={14} />
					</button>
				</div>
			)}
			<div className="nodes">
				{quests.map((q) => {
					const pt = graph.points.get(q.id);
					if (!pt) return null;
					const worldPosition = ghost?.id === q.id ? ghost : pt,
						p = pixel(worldPosition);
					if (Math.abs(p.x) > size.width + 250 || Math.abs(p.y) > size.height + 250) return null;
					const unavailable = q.kind === "information" && q.acknowledgement === "read_only";
					const nodeSize = resizeGhost?.id === q.id ? (resizeGhost.size ?? q.size ?? 32) : (q.size ?? 32);
					return (
						<div
							key={q.id}
							className={
								"graph-node shape-" +
								(q.presentation?.shape || "square") +
								(selection?.type === "quest" && selection.id === q.id ? " selected" : "") +
								(connection?.from === q.id ? " connecting" : "") +
								(connection?.to === q.id ? (connection.error ? " link-invalid" : " link-target") : "")
							}
							data-node-id={q.id}
							data-node-type="quest"
							style={{ left: p.x, top: p.y, width: nodeSize, height: nodeSize, transform: "translate(-50%,-50%) scale(" + clampZoom(camera.zoom) + ")" }}>
							<button className="node-select" type="button" aria-pressed={selection?.type === "quest" && selection.id === q.id} onClick={() => selectNode(q.id)}>
								<span className="node-icon">
									<ItemIcon id={q.icon} size={Math.max(4, (nodeSize - 6) * (q.presentation?.iconScale ?? 1))} />
								</span>
								<span className="sr-only">{text(q.title, language)}</span>
							</button>
							<button
								className="node-connector"
								data-link-port="true"
								aria-disabled={unavailable}
								aria-label={tUI("Connect quest") + " " + text(q.title, language)}
								title={tUI(unavailable ? "A read-only quest cannot unlock another quest." : "Drag to another quest to add a prerequisite")}
								style={{ "--port-scale": 1 } as React.CSSProperties}
								onClick={(e) => {
									e.stopPropagation();
									if (e.detail === 0 && !unavailable) {
										onSelect({ type: "quest", id: q.id });
										showCable({ from: q.id, point: { x: p.x, y: p.y + nodeSize / 2 }, to: null, error: "", moved: false });
									}
								}}>
								<Plus size={10} />
							</button>
							{q.objectives?.some((o) => o.event === "npc_interact") && <MessageCircle className="node-npc" size={13} />}
							{showQuestTitles && (
								<span className="node-title-below" title={q.id}>
									{text(q.title, language)}
								</span>
							)}
							{selection?.type === "quest" && selection.id === q.id && (
								<>
									{handle("quest", q.id, nodeSize, nodeSize, q.size ?? 32)}
									<span className="node-position">
										x: {worldPosition.x} &nbsp; y: {worldPosition.y}
									</span>
									<span className="selection-handles" aria-hidden="true" />
								</>
							)}
						</div>
					);
				})}
				{(category?.annotations || [])
					.filter((a) => a.style !== "background")
					.map((a) => {
						const pt = ghost?.id === a.id ? ghost : a,
							p = pixel(pt);
						if (Math.abs(p.x) > size.width + 240 || Math.abs(p.y) > size.height + 240) return null;
						const rich = annotationRich(a, language);
						const nodeWidth = resizeGhost?.id === a.id ? (resizeGhost.width ?? a.width ?? 170) : (a.width ?? 170);
						const nodeHeight = resizeGhost?.id === a.id ? (resizeGhost.height ?? a.height ?? 100) : (a.height ?? 100);
						return (
							<div
								role="button"
								tabIndex={0}
								aria-label={annotationText(a, language)}
								onKeyDown={(e) => {
									if (e.key === "Enter" || e.key === " ") {
										e.preventDefault();
										onSelect({ type: "annotation", id: a.id });
									}
								}}
								key={a.id}
								className={"annotation-node " + (a.style || "note") + (selection?.type === "annotation" && selection.id === a.id ? " selected" : "")}
								data-node-id={a.id}
								data-node-type="annotation"
								style={{
									left: p.x,
									top: p.y,
									width: nodeWidth,
									height: nodeHeight,
									textAlign: a.align || "center",
									borderColor: a.color || "#c6a986",
									color: a.color || "#c6a986",
									transform: "translate(-50%,-50%) scale(" + clampZoom(camera.zoom) + ")",
								}}>
								{rich ? <span dangerouslySetInnerHTML={{ __html: richToHtml(rich) }} /> : annotationText(a, language)}
								{selection?.type === "annotation" && selection.id === a.id && handle("annotation", a.id, a.width ?? 170, a.height ?? 100, 32)}
							</div>
						);
					})}
			</div>
			{quests.length === 0 && !category?.annotations?.length && (
				<div className="empty-state">
					<GitBranch size={36} />
					<h3>{tUI("Empty tree")}</h3>
					<p>{tUI("Add a quest to begin")}</p>
				</div>
			)}
		</div>
	);
}
