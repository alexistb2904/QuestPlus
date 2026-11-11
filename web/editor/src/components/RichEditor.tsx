import { AArrowDown, AArrowUp, Bold, ChevronDown, Highlighter, ImagePlus, Italic, Palette, RemoveFormatting, Strikethrough, Trash2, Underline } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { DESCRIPTION_COLORS, descriptionHtml, descriptionParts, descriptionText } from "../core/description-format.js";
import { richToHtml } from "../core/rich-format.js";
import type { RichPart } from "../types";
import { minecraftPx, plainFallback, richFromDom, toEmbeddedImage } from "../ui/rich-dom.js";
import { tUI } from "../ui/ui-i18n.js";

const NORMAL_PX = 9;
const SIZES = [6, 7, 8, 9, 10, 11, 12, 13, 14, 16, 18, 20, 24, 28, 32, 36, 48, 64, 72, 96, 128];

type Props = { content: RichPart[]; onChange: (parts: RichPart[], plain: string) => void; locale: string; mode?: "annotation" | "description"; label?: string };
export function RichEditor({ content, onChange, locale, mode = "annotation", label }: Props) {
	const ref = useRef<HTMLDivElement>(null),
		savedRange = useRef<Range | null>(null),
		selectedImage = useRef<HTMLImageElement | null>(null),
		lastEmitted = useRef("");
	const [image, setImage] = useState<{ width: number; height: number } | null>(null);
	const [imageBounds, setImageBounds] = useState<{ left: number; top: number } | null>(null);
	const [pixelSize, setPixelSize] = useState(NORMAL_PX);
	const [hasSelection, setHasSelection] = useState(false);
	const [bubble, setBubble] = useState<{ x: number; y: number; below: boolean } | null>(null);
	const dragImage = useRef<{ pointer: number; x: number; y: number; width: number; height: number } | null>(null);
	const [colorOpen, setColorOpen] = useState(false);
	const colorNames = [
		"Black",
		"Dark blue",
		"Dark green",
		"Dark aqua",
		"Dark red",
		"Dark purple",
		"Gold",
		"Gray",
		"Dark gray",
		"Blue",
		"Green",
		"Aqua",
		"Red",
		"Light purple",
		"Yellow",
		"White",
	];
	const [active, setActive] = useState<Record<string, boolean>>({});
	const isDescription = mode === "description";
	const renderHtml = (parts: RichPart[]) =>
		isDescription && !parts.some((p) => p.type === "image" || (p.type === "text" && p.sizePx !== undefined)) ? descriptionHtml(parts) : richToHtml(parts);
	const html = renderHtml(content);
	useEffect(() => {
		if (ref.current && html !== lastEmitted.current) {
			ref.current.innerHTML = html;
			lastEmitted.current = html;
			savedRange.current = null;
			selectedImage.current = null;
			setImage(null);
		}
	}, [html, locale]);
	const read = useCallback(() => {
		if (!ref.current) return;
		const clone = ref.current.cloneNode(true) as HTMLElement;
		clone.querySelectorAll("font[color]").forEach((el) => {
			const font = el as HTMLElement;
			font.style.color = font.getAttribute("color") || "";
		});
		const parts = richFromDom(clone);
		if (parts) {
			lastEmitted.current = renderHtml(parts);
			onChange(parts, plainFallback(parts));
		}
	}, [onChange, isDescription]);
	const selectionSize = () => {
		const start = savedRange.current?.startContainer;
		for (let el = start?.nodeType === Node.ELEMENT_NODE ? (start as HTMLElement) : start?.parentElement; el && el !== ref.current; el = el.parentElement) {
			const px = minecraftPx(el.style.fontSize);
			if (px) return px;
			if (el.style.fontSize.endsWith("%")) return Math.round((NORMAL_PX * parseFloat(el.style.fontSize)) / 100) || NORMAL_PX;
		}
		return NORMAL_PX;
	};
	const placeBubble = (range: Range, at?: { x: number; y: number }) => {
		const rect = range.getBoundingClientRect();
		const below = !!at || rect.top < 64;
		const x = at ? at.x : rect.left + rect.width / 2;
		const half = Math.min(180, window.innerWidth / 2);
		setBubble({ x: Math.min(Math.max(x, half), window.innerWidth - half), y: at ? at.y : below ? rect.bottom : rect.top, below });
	};
	const captureSelection = () => {
		const s = window.getSelection();
		if (s?.rangeCount && ref.current?.contains(s.anchorNode)) {
			const range = s.getRangeAt(0);
			savedRange.current = range.cloneRange();
			setActive(Object.fromEntries(["bold", "italic", "underline", "strikeThrough"].map((name) => [name, document.queryCommandState(name)])));
			setPixelSize(selectionSize());
			setHasSelection(!range.collapsed);
			if (range.collapsed) setBubble(null);
			else placeBubble(range);
		}
	};
	const restore = () => {
		const s = window.getSelection(),
			r = savedRange.current;
		if (!s || !r || !ref.current?.contains(r.commonAncestorContainer)) return;
		try {
			s.removeAllRanges();
			s.addRange(r.cloneRange());
		} catch {}
	};
	const command = (name: string, value?: string) => {
		ref.current?.focus();
		restore();
		if (name === "foreColor") document.execCommand("styleWithCSS", false, "true");
		document.execCommand(name, false, value || null);
		read();
		captureSelection();
	};
	const applyPixelSize = (value: number) => {
		const px = Math.min(128, Math.max(1, Math.round(value) || NORMAL_PX));
		ref.current?.focus();
		restore();
		const selection = window.getSelection();
		if (!selection?.rangeCount || !ref.current?.contains(selection.anchorNode)) return;
		const range = selection.getRangeAt(0);
		if (range.collapsed) return;
		const span = document.createElement("span");
		span.style.fontSize = "calc(var(--mc-px, 1px) * " + px + ")";
		try {
			span.appendChild(range.extractContents());
			span.querySelectorAll<HTMLElement>("[style]").forEach((el) => el.style.removeProperty("font-size"));
			range.insertNode(span);
			range.selectNodeContents(span);
			selection.removeAllRanges();
			selection.addRange(range);
		} catch {
			return;
		}
		read();
		captureSelection();
	};
	const stepSize = (direction: 1 | -1) => {
		const current = selectionSize();
		const next = direction > 0 ? SIZES.find((v) => v > current) : [...SIZES].reverse().find((v) => v < current);
		if (next) applyPixelSize(next);
	};
	const insert = async (file: File) => {
		try {
			const data = await toEmbeddedImage(file);
			const tag = document.createElement("img");
			tag.src = data.data;
			tag.width = data.width;
			tag.height = data.height;
			tag.alt = tUI("Embedded image");
			ref.current?.focus();
			restore();
			const s = window.getSelection(),
				r = s?.rangeCount ? s.getRangeAt(0) : null;
			if (r && ref.current?.contains(r.commonAncestorContainer)) {
				r.deleteContents();
				r.insertNode(tag);
				r.setStartAfter(tag);
				r.collapse(true);
				s!.removeAllRanges();
				s!.addRange(r);
			} else ref.current?.append(tag);
			read();
		} catch (e) {
			alert((e as Error).message || tUI("Unsupported image"));
		}
	};
	const updateImageBounds = (el: HTMLImageElement) => {
		if (!ref.current) return;
		const rect = el.getBoundingClientRect(),
			host = ref.current.parentElement?.getBoundingClientRect();
		if (host) setImageBounds({ left: rect.right - host.left - 7, top: rect.bottom - host.top - 7 });
	};
	const chooseImage = (el: HTMLImageElement) => {
		selectedImage.current = el;
		setImage({ width: el.width, height: el.height });
		updateImageBounds(el);
	};
	const imageSize = (prop: "width" | "height", value: number) => {
		const img = selectedImage.current;
		if (!img || !ref.current?.contains(img)) return;
		const v = Math.max(1, Math.round(value) || 1);
		img.setAttribute(prop, String(v));
		setImage({ width: img.width, height: img.height });
		updateImageBounds(img);
		read();
	};
	const formatButtons = [
		["bold", "Bold", Bold],
		["italic", "Italic", Italic],
		["underline", "Underline", Underline],
		["strikeThrough", "Strikethrough", Strikethrough],
	] as const;
	const sizeHint = hasSelection ? "" : " · " + tUI("Select text to change its size");
	const sizeControls = (
		<div className="rich-size" role="group" aria-label={tUI("Text size")}>
			<button
				type="button"
				disabled={!hasSelection}
				onClick={() => stepSize(-1)}
				title={tUI("Decrease text size") + " (Ctrl+Shift+<)" + sizeHint}
				aria-label={tUI("Decrease text size")}>
				<AArrowDown size={15} />
			</button>
			<select
				disabled={!hasSelection}
				aria-label={tUI("Text size")}
				title={tUI("Text size") + sizeHint}
				value={pixelSize}
				onChange={(e) => applyPixelSize(Number(e.target.value))}>
				{[...new Set([...SIZES, pixelSize])]
					.sort((a, b) => a - b)
					.map((v) => (
						<option key={v} value={v}>
							{v === NORMAL_PX ? v + " · " + tUI("Normal") : v}
						</option>
					))}
			</select>
			<button
				type="button"
				disabled={!hasSelection}
				onClick={() => stepSize(1)}
				title={tUI("Increase text size") + " (Ctrl+Shift+>)" + sizeHint}
				aria-label={tUI("Increase text size")}>
				<AArrowUp size={15} />
			</button>
		</div>
	);
	const formatToggles = formatButtons.map(([name, title, Icon]) => (
		<button type="button" key={name} onClick={() => command(name)} title={tUI(title)} aria-label={tUI(title)} aria-pressed={!!active[name]}>
			<Icon size={15} />
		</button>
	));
	const colorTrigger = (
		<button
			type="button"
			className="rich-color-trigger"
			title={tUI("Text color")}
			aria-label={tUI("Text color")}
			aria-expanded={colorOpen}
			onClick={() => setColorOpen((v) => !v)}>
			<Palette size={15} />
			<ChevronDown size={12} />
		</button>
	);
	const highlightButton = (
		<button type="button" onClick={() => command("hiliteColor", "#ffe178")} title={tUI("Highlight")} aria-label={tUI("Highlight")}>
			<Highlighter size={15} />
		</button>
	);
	const keepEditorFocus = (e: React.PointerEvent) => {
		if ((e.target as HTMLElement).closest("button")) e.preventDefault();
	};
	const palette = isDescription && colorOpen && (
		<div
			className="rich-palette"
			role="group"
			aria-label={tUI("Description colors")}
			onPointerDown={(e) => e.preventDefault()}
			onKeyDown={(e) => {
				if (e.key === "Escape") {
					e.stopPropagation();
					setColorOpen(false);
				}
			}}>
			{Object.entries(DESCRIPTION_COLORS).map(([code, color], index) => (
				<button
					key={code}
					type="button"
					style={{ backgroundColor: color }}
					title={tUI(colorNames[index])}
					aria-label={tUI(colorNames[index])}
					onClick={() => {
						command("foreColor", color);
						setColorOpen(false);
					}}
				/>
			))}
		</div>
	);
	return (
		<div
			className={"rich-studio" + (isDescription ? " description-editor" : "")}
			onBlur={(e) => {
				if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setBubble(null);
			}}>
			<div className="rich-toolbar" role="toolbar" aria-label={tUI("Text formatting")} onPointerDown={keepEditorFocus}>
				{formatToggles}
				{isDescription ? (
					<div className="rich-color">{colorTrigger}</div>
				) : (
					<>
						{highlightButton}
						<select aria-label={tUI("Font")} defaultValue="default" onChange={(e) => command("fontName", e.target.value)}>
							<option value="default">Minecraft</option>
							<option value="uniform">Uniform</option>
							<option value="alt">Alt</option>
							<option value="illageralt">Illager</option>
						</select>
					</>
				)}
				{sizeControls}
				<button type="button" onClick={() => command("removeFormat")} title={tUI("Clear formatting")} aria-label={tUI("Clear formatting")}>
					<RemoveFormatting size={15} />
				</button>
				<label className="rich-upload">
					<ImagePlus size={15} />
					{tUI("Image")}
					<input
						type="file"
						accept="image/png,image/jpeg,image/webp,image/gif"
						hidden
						onChange={(e) => {
							const file = e.target.files?.[0];
							e.target.value = "";
							if (file) void insert(file);
						}}
					/>
				</label>
			</div>
			{!bubble && palette}
			{bubble && (
				<div
					className={"rich-bubble" + (bubble.below ? " below" : "")}
					role="toolbar"
					aria-label={tUI("Text formatting")}
					style={{ left: bubble.x, top: bubble.y }}
					onPointerDown={keepEditorFocus}
					onKeyDown={(e) => {
						if (e.key === "Escape") {
							e.stopPropagation();
							setBubble(null);
							ref.current?.focus();
						}
					}}>
					<div className="rich-bubble-row">
						{formatToggles}
						{sizeControls}
						{isDescription ? colorTrigger : highlightButton}
					</div>
					{palette}
				</div>
			)}
			{image && (
				<div className="rich-image-tools">
					<strong>{tUI("Image")}</strong>
					<label>
						W <input aria-label={tUI("Image width")} type="number" min="1" value={image.width} onChange={(e) => imageSize("width", Number(e.target.value))} />
					</label>
					<label>
						H{" "}
						<input
							aria-label={tUI("Image height")}
							type="number"
							min="8"
							max="2048"
							value={image.height}
							onChange={(e) => imageSize("height", Number(e.target.value))}
						/>
					</label>
					<button
						aria-label={tUI("Remove image")}
						onClick={() => {
							selectedImage.current?.remove();
							selectedImage.current = null;
							setImage(null);
							setImageBounds(null);
							read();
						}}>
						<Trash2 size={15} />
					</button>
				</div>
			)}
			{image && imageBounds && (
				<button
					type="button"
					className="rich-image-grip"
					aria-label={tUI("Drag to resize image")}
					title={tUI("Drag to resize image")}
					style={{ left: imageBounds.left, top: imageBounds.top }}
					onPointerDown={(e) => {
						if (!selectedImage.current) return;
						e.preventDefault();
						e.stopPropagation();
						const img = selectedImage.current;
						dragImage.current = { pointer: e.pointerId, x: e.clientX, y: e.clientY, width: img.width, height: img.height };
						e.currentTarget.setPointerCapture(e.pointerId);
					}}
					onPointerMove={(e) => {
						const start = dragImage.current,
							el = selectedImage.current;
						if (!start || !el || start.pointer !== e.pointerId) return;
						const width = Math.max(1, Math.round(start.width + (e.clientX - start.x)));
						const height = e.altKey ? Math.max(1, Math.round(start.height + e.clientY - start.y)) : Math.max(1, Math.round((start.height * width) / start.width));
						el.width = width;
						el.height = height;
						setImage({ width, height });
						updateImageBounds(el);
					}}
					onPointerUp={(e) => {
						if (dragImage.current?.pointer === e.pointerId) {
							dragImage.current = null;
							read();
							try {
								e.currentTarget.releasePointerCapture(e.pointerId);
							} catch {}
						}
					}}
					onPointerCancel={() => {
						dragImage.current = null;
						read();
					}}
				/>
			)}
			<div
				ref={ref}
				contentEditable
				role="textbox"
				aria-label={label || (isDescription ? "Description " : "Annotation ") + locale}
				aria-multiline="true"
				data-placeholder={isDescription ? tUI("Write a description…") : undefined}
				className="rich-edit"
				suppressContentEditableWarning
				onInput={read}
				onKeyUp={captureSelection}
				onMouseUp={captureSelection}
				onScroll={() => setBubble(null)}
				onKeyDown={(e) => {
					if (!(e.ctrlKey || e.metaKey) || ![">", "<", "]", "["].includes(e.key)) return;
					e.preventDefault();
					captureSelection();
					stepSize(e.key === ">" || e.key === "]" ? 1 : -1);
				}}
				onContextMenu={(e) => {
					const s = window.getSelection();
					if (!s?.rangeCount || s.isCollapsed || !ref.current?.contains(s.anchorNode)) return;
					e.preventDefault();
					captureSelection();
					placeBubble(s.getRangeAt(0), { x: e.clientX, y: e.clientY });
				}}
				onClick={(e) => {
					const img = (e.target as HTMLElement).closest("img");
					if (img) chooseImage(img as HTMLImageElement);
					else if (image) {
						setImage(null);
						setImageBounds(null);
					}
				}}
				onPaste={(e) => {
					const images = Array.from(e.clipboardData.files).filter((f) => f.type.startsWith("image/"));
					if (images.length) {
						e.preventDefault();
						void insert(images[0]);
					} else {
						e.preventDefault();
						document.execCommand("insertText", false, e.clipboardData.getData("text/plain"));
						read();
					}
				}}
			/>
		</div>
	);
}

export function DescriptionEditor({
	value,
	rich,
	locale,
	onChange,
	onRichChange,
}: {
	value?: string;
	rich?: RichPart[];
	locale: string;
	onChange: (value: string) => void;
	onRichChange?: (parts: RichPart[]) => void;
}) {
	return (
		<RichEditor
			mode="description"
			locale={locale}
			label={tUI("Description") + " · " + locale}
			content={rich?.length ? rich : descriptionParts(value || "")}
			onChange={(parts) => {
				onChange(descriptionText(parts) || (parts.some((p) => p.type === "image") ? "Image" : ""));
				onRichChange?.(parts);
			}}
		/>
	);
}
