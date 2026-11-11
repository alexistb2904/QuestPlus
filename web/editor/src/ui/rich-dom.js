import { mediaPattern, richText, richToHtml, validRich } from "../core/rich-format.js";
const DEFAULT = { font: "default", size: 1 };
function hex(v) {
	if (/^#[0-9a-f]{6}$/i.test(v || "")) return v.toLowerCase();
	const x = String(v || "").match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/i);
	return x
		? "#" +
				x
					.slice(1, 4)
					.map((k) => Number(k).toString(16).padStart(2, "0"))
					.join("")
		: null;
}
function normalizedSize(v) {
	if (!v) return null;
	const pt = parseFloat(v);
	if (!Number.isFinite(pt)) return null;
	if (String(v).endsWith("%")) return Math.max(0.75, Math.min(2, pt / 100));
	if (String(v).endsWith("px")) return Math.max(0.75, Math.min(2, pt / 14));
	return null;
}
export function minecraftPx(v) {
	const m = String(v || "").match(/\*\s*([\d.]+)\s*\)\s*$/) || String(v || "").match(/^([\d.]+)px$/);
	const px = m ? Math.round(parseFloat(m[1])) : NaN;
	return Number.isFinite(px) && px > 0 ? px : null;
}
export function richFromDom(root) {
	const parts = [];
	function pushText(txt, style) {
		if (!txt) return;
		const data = { type: "text", text: txt, ...style };
		const last = parts.at(-1);
		if (last && last.type === "text" && JSON.stringify({ ...last, text: "" }) === JSON.stringify({ ...data, text: "" })) {
			last.text += txt;
			return;
		}
		parts.push(data);
	}
	function walk(node, style) {
		if (node.nodeType === Node.TEXT_NODE) {
			pushText(node.textContent, style);
			return;
		}
		if (node.nodeType !== Node.ELEMENT_NODE) return;
		const el = node,
			tag = el.tagName.toLowerCase();
		if (tag === "img") {
			const src = el.getAttribute("src") || "";
			if (mediaPattern.test(src))
				parts.push({ type: "image", data: src, width: Math.max(1, Number(el.getAttribute("width")) || 120), height: Math.max(1, Number(el.getAttribute("height")) || 64) });
			return;
		}
		if (tag === "br") {
			pushText("\n", style);
			return;
		}
		const s = { ...style };
		if (["b", "strong"].includes(tag) || el.style.fontWeight === "bold") s.bold = true;
		if (["i", "em"].includes(tag) || el.style.fontStyle === "italic") s.italic = true;
		if (tag === "u" || el.style.textDecoration.includes("underline")) s.underline = true;
		if (["s", "strike", "del"].includes(tag) || el.style.textDecoration.includes("line-through")) s.strike = true;
		if (tag === "mark") s.highlight = "#ffe178";
		if (el.style.backgroundColor) s.highlight = hex(el.style.backgroundColor) || s.highlight;
		if (el.style.color) s.color = hex(el.style.color) || s.color;
		const face = el.getAttribute("face") || el.style.fontFamily || el.dataset.font;
		if (face) {
			const v = face.toLowerCase();
			if (v.includes("uniform") || v.includes("mono")) s.font = "uniform";
			else if (v.includes("illager")) s.font = "illageralt";
			else if (v.includes("alt")) s.font = "alt";
			else if (v.includes("default")) s.font = "default";
		}
		const size = normalizedSize(el.style.fontSize) || (tag === "font" ? { 1: 0.75, 2: 0.85, 3: 1, 4: 1.3, 5: 1.6, 6: 1.85, 7: 2 }[Number(el.getAttribute("size"))] : null);
		const px = minecraftPx(el.style.fontSize);
		if (px) {
			s.sizePx = px;
			delete s.size;
		} else if (size) {
			s.size = size;
			delete s.sizePx;
		}
		const before = parts.length;
		for (const child of el.childNodes) walk(child, s);
		if ((tag === "div" || tag === "p") && parts.length > before) pushText("\n", s);
	}
	for (const child of root.childNodes) walk(child, DEFAULT);
	const trimmed = parts.filter((p) => p.type !== "text" || p.text.length);
	return validRich(trimmed) ? trimmed : null;
}
export function setRichEditor(editor, parts) {
	editor.innerHTML = richToHtml(parts);
}
export async function toEmbeddedImage(file) {
	if (!file || !["image/png", "image/jpeg", "image/webp", "image/gif"].includes(file.type)) throw Error("Only PNG, JPEG, WebP or GIF images are supported.");
	let data = await new Promise((resolve, reject) => {
		const reader = new FileReader();
		reader.onerror = () => reject(Error("Cannot read image"));
		reader.onload = () => resolve(reader.result);
		reader.readAsDataURL(file);
	});
	if (!mediaPattern.test(data) && file.type !== "image/webp") throw Error("Invalid image data.");
	const img = new Image();
	await new Promise((resolve, reject) => {
		img.onload = resolve;
		img.onerror = reject;
		img.src = data;
	});
	if (file.type === "image/webp") {
		const canvas = document.createElement("canvas");
		canvas.width = img.naturalWidth;
		canvas.height = img.naturalHeight;
		canvas.getContext("2d").drawImage(img, 0, 0);
		data = canvas.toDataURL("image/png");
	}
	if (file.type !== "image/gif" && file.size > 2 * 1024 * 1024) {
		const max = 1; // keep the original pixel dimensions
		const canvas = document.createElement("canvas");
		canvas.width = Math.max(1, Math.round(img.naturalWidth * max));
		canvas.height = Math.max(1, Math.round(img.naturalHeight * max));
		const ctx = canvas.getContext("2d");
		if (!ctx) throw Error("Image compression unavailable");
		ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
		const hasAlpha = (() => {
			const pixels = ctx.getImageData(0, 0, canvas.width, canvas.height).data;
			for (let i = 3; i < pixels.length; i += 4) if (pixels[i] !== 255) return true;
			return false;
		})();
		data = canvas.toDataURL(hasAlpha ? "image/png" : "image/jpeg", hasAlpha ? undefined : 0.88);
	}
	const scale = 1;
	return { data, width: Math.max(8, Math.round(img.naturalWidth * scale)), height: Math.max(8, Math.round(img.naturalHeight * scale)) };
}
export function plainFallback(parts) {
	return richText(parts) || ((parts || []).some((p) => p.type === "image") ? "Image" : "Annotation");
}
