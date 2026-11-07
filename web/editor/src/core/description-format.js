import { richToHtml } from "./rich-format.js";
// Quest descriptions keep their existing locale-string schema; § codes are Minecraft formatting.
export const DESCRIPTION_COLORS = {
	0: "#000000",
	1: "#0000aa",
	2: "#00aa00",
	3: "#00aaaa",
	4: "#aa0000",
	5: "#aa00aa",
	6: "#ffaa00",
	7: "#aaaaaa",
	8: "#555555",
	9: "#5555ff",
	a: "#55ff55",
	b: "#55ffff",
	c: "#ff5555",
	d: "#ff55ff",
	e: "#ffff55",
	f: "#ffffff",
};
const colorCodes = Object.fromEntries(Object.entries(DESCRIPTION_COLORS).map(([code, color]) => [color, code]));
const formatKeys = { l: "bold", o: "italic", n: "underline", m: "strike" };

export function descriptionParts(value) {
	const result = [];
	let style = {},
		buffer = "";
	const flush = () => {
		if (buffer) {
			result.push({ type: "text", text: buffer, font: "default", ...style });
			buffer = "";
		}
	};
	const input = String(value ?? "");
	for (let i = 0; i < input.length; i++) {
		if (input[i] !== "§" || i + 1 >= input.length) {
			buffer += input[i];
			continue;
		}
		const code = input[i + 1].toLowerCase();
		if (code === "§") {
			buffer += "§";
			i++;
			continue;
		}
		if (Object.hasOwn(DESCRIPTION_COLORS, code)) {
			flush();
			style = { color: DESCRIPTION_COLORS[code] };
			i++;
			continue;
		}
		if (Object.hasOwn(formatKeys, code)) {
			flush();
			style = { ...style, [formatKeys[code]]: true };
			i++;
			continue;
		}
		if (code === "r") {
			flush();
			style = {};
			i++;
			continue;
		}
		buffer += "§" + input[++i];
	}
	flush();
	return result.length ? result : [{ type: "text", text: "", font: "default" }];
}

export function descriptionText(parts) {
	let result = "",
		current = "";
	for (const part of parts || []) {
		if (part.type !== "text" || !part.text) continue;
		let next = "";
		const color = colorCodes[String(part.color || "").toLowerCase()];
		if (color) next += "§" + color;
		for (const [code, key] of Object.entries(formatKeys)) if (part[key]) next += "§" + code;
		if (next !== current) {
			if (current) result += "§r";
			result += next;
			current = next;
		}
		result += String(part.text).replace(/§/g, "§§");
	}
	return result;
}

// Descriptions can be larger than annotation parts; render each text chunk without truncation.
export function descriptionHtml(parts) {
	return (parts || [])
		.map((part) => {
			if (part.type !== "text") return "";
			let out = "";
			for (let offset = 0; offset < part.text.length; ) {
				let end = Math.min(offset + 2048, part.text.length);
				if (end < part.text.length && /[\uD800-\uDBFF]/.test(part.text[end - 1])) end--;
				out += richToHtml([{ ...part, text: part.text.slice(offset, end) }]);
				offset = end;
			}
			return out;
		})
		.join("");
}
function colorValue(value) {
	if (/^#[0-9a-f]{6}$/i.test(value || "")) return value.toLowerCase();
	const match = String(value || "").match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/i);
	return match
		? "#" +
				match
					.slice(1, 4)
					.map((x) => Number(x).toString(16).padStart(2, "0"))
					.join("")
		: null;
}
// Read browser editing markup directly; descriptions are not constrained by annotation media limits.
export function descriptionFromDom(root) {
	const parts = [];
	const push = (text, style) => {
		if (!text) return;
		const part = { type: "text", text, font: "default", ...style },
			last = parts.at(-1);
		if (last && JSON.stringify({ ...last, text: "" }) === JSON.stringify({ ...part, text: "" })) last.text += text;
		else parts.push(part);
	};
	const walk = (node, style) => {
		if (node.nodeType === 3) {
			push(node.textContent || "", style);
			return;
		}
		if (node.nodeType !== 1) return;
		const el = node,
			tag = el.tagName.toLowerCase();
		if (tag === "br") {
			push("\n", style);
			return;
		}
		if (tag === "img") return;
		const next = { ...style },
			weight = el.style.fontWeight,
			decoration = el.style.textDecoration;
		if (["b", "strong"].includes(tag) || weight === "bold" || Number(weight) >= 600) next.bold = true;
		else if (weight === "normal" || weight === "400") delete next.bold;
		if (["i", "em"].includes(tag) || el.style.fontStyle === "italic") next.italic = true;
		else if (el.style.fontStyle === "normal") delete next.italic;
		if (tag === "u" || decoration.includes("underline")) next.underline = true;
		if (["s", "strike", "del"].includes(tag) || decoration.includes("line-through")) next.strike = true;
		if (decoration === "none") {
			delete next.underline;
			delete next.strike;
		}
		const color = colorValue(el.style.color || el.getAttribute("color"));
		if (color) next.color = color;
		const before = parts.length;
		for (const child of el.childNodes) walk(child, next);
		if ((tag === "div" || tag === "p") && parts.length > before) push("\n", next);
	};
	for (const child of root.childNodes) walk(child, {});
	return parts;
}
