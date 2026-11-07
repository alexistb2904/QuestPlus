import { Bold, ChevronDown, Highlighter, ImagePlus, Italic, Palette, Strikethrough, Trash2, Underline } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { DESCRIPTION_COLORS, descriptionHtml, descriptionParts, descriptionText } from "../core/description-format.js";
import { richToHtml } from "../core/rich-format.js";
import type { RichPart } from "../types";
import { plainFallback, richFromDom, toEmbeddedImage } from "../ui/rich-dom.js";
import { tUI } from "../ui/ui-i18n.js";

type Props = { content: RichPart[]; onChange: (parts: RichPart[], plain: string) => void; locale: string; mode?: "annotation" | "description"; label?: string };
export function RichEditor({ content, onChange, locale, mode = "annotation", label }: Props) {
	const ref = useRef<HTMLDivElement>(null),
		savedRange = useRef<Range | null>(null),
		selectedImage = useRef<HTMLImageElement | null>(null),
		lastEmitted = useRef("");
	const [image, setImage] = useState<{ width: number; height: number } | null>(null);
	const [imageBounds, setImageBounds] = useState<{ left: number; top: number } | null>(null);
	const [pixelSize, setPixelSize] = useState(16);
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
	const captureSelection = () => {
		const s = window.getSelection();
		if (s?.rangeCount && ref.current?.contains(s.anchorNode)) {
			savedRange.current = s.getRangeAt(0).cloneRange();
			setActive(Object.fromEntries(["bold", "italic", "underline", "strikeThrough"].map((name) => [name, document.queryCommandState(name)])));
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
	const applyPixelSize = () => {
		const px = Math.max(1, Math.round(pixelSize) || 16);
		ref.current?.focus();
		restore();
		const selection = window.getSelection();
		if (!selection?.rangeCount || !ref.current?.contains(selection.anchorNode)) return;
		const range = selection.getRangeAt(0);
		if (range.collapsed) return;
		const span = document.createElement("span");
		span.style.fontSize = px + "px";
		try {
			span.appendChild(range.extractContents());
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
	return (
		<div className={"rich-studio" + (isDescription ? " description-editor" : "")}>
			<div
				className="rich-toolbar"
				role="toolbar"
				aria-label={tUI("Text formatting")}
				onPointerDown={(e) => {
					if ((e.target as HTMLElement).closest("button")) e.preventDefault();
				}}>
				{formatButtons.map(([name, title, Icon]) => (
					<button type="button" key={name} onClick={() => command(name)} title={tUI(title)} aria-label={tUI(title)} aria-pressed={!!active[name]}>
						<Icon size={15} />
					</button>
				))}
				{isDescription ? (
					<div className="rich-color">
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
					</div>
				) : (
					<>
						<button type="button" onClick={() => command("hiliteColor", "#ffe178")} title={tUI("Highlight")} aria-label={tUI("Highlight")}>
							<Highlighter size={15} />
						</button>
						<select aria-label={tUI("Font")} defaultValue="default" onChange={(e) => command("fontName", e.target.value)}>
							<option value="default">Minecraft</option>
							<option value="uniform">Uniform</option>
							<option value="alt">Alt</option>
							<option value="illageralt">Illager</option>
						</select>
					</>
				)}
				<label className="rich-pixel-size" title={tUI("Text size in pixels")}>
					<span>px</span>
					<input
						aria-label={tUI("Text size in pixels")}
						type="number"
						min="1"
						step="1"
						value={pixelSize}
						onChange={(e) => setPixelSize(Number(e.target.value))}
						onKeyDown={(e) => {
							if (e.key === "Enter") {
								e.preventDefault();
								applyPixelSize();
							}
						}}
					/>
					<button type="button" onClick={applyPixelSize} aria-label={tUI("Apply text size")}>
						{tUI("Apply")}
					</button>
				</label>
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
			{isDescription && colorOpen && (
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
