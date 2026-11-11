/** Bounded FTB/vanilla SNBT reader. No executable expressions or lossy long numbers. */
export function parseSnbt(source, { preserveLongs = false } = {}) {
	if (source.length > 2_000_000) throw Error("SNBT too large");
	let i = 0,
		nodes = 0;
	const ws = () => {
		let newline = false;
		while (i < source.length) {
			if (/\s/.test(source[i])) {
				newline ||= source[i] === "\n" || source[i] === "\r";
				i++;
			} else if (source[i] === "#") {
				while (i < source.length && source[i] !== "\n" && source[i] !== "\r") i++;
			} else break;
		}
		return newline;
	};
	const separator = (end) => {
		const newline = ws();
		if (source[i] === end) {
			i++;
			return true;
		}
		if (source[i] === ",") {
			i++;
			ws();
			if (source[i] === end) {
				i++;
				return true;
			}
			return false;
		}
		if (newline) return false;
		throw Error("Expected comma or newline at " + i);
	};
	const value = (depth = 0) => {
		if (depth > 64 || ++nodes > 200000) throw Error("SNBT complexity limit");
		ws();
		const c = source[i];
		if (c === '"' || c === "'") {
			i++;
			let out = "";
			while (i < source.length) {
				const ch = source[i++];
				if (ch === c) return out;
				if (ch === "\\") {
					const next = source[i++];
					const escapes = { n: "\n", r: "\r", t: "\t", b: "\b", f: "\f" };
					if (next === c || next === "\\" || next === '"' || next === "'") out += next;
					else if (Object.hasOwn(escapes, next)) out += escapes[next];
					else if (next === "u" && /^[a-f0-9]{4}$/i.test(source.slice(i, i + 4))) {
						out += String.fromCharCode(parseInt(source.slice(i, i + 4), 16));
						i += 4;
					} else throw Error("Invalid SNBT escape");
				} else out += ch;
			}
			throw Error("Unterminated string");
		}
		if (c === "{") {
			i++;
			const out = Object.create(null);
			ws();
			if (source[i] === "}") {
				i++;
				return out;
			}
			while (true) {
				ws();
				const key = value(depth + 1);
				if (typeof key !== "string" || ["__proto__", "constructor", "prototype"].includes(key)) throw Error("Invalid compound key");
				ws();
				if (source[i++] !== ":") throw Error("Expected colon");
				if (Object.hasOwn(out, key)) throw Error("Duplicate compound key");
				out[key] = value(depth + 1);
				if (separator("}")) return out;
			}
		}
		if (c === "[") {
			i++;
			ws();
			const typed = /^([BIL]);/i.exec(source.slice(i, i + 2));
			if (typed) i += 2;
			const out = [];
			ws();
			if (source[i] !== "]")
				while (true) {
					out.push(value(depth + 1));
					if (separator("]")) break;
				}
			else i++;
			if (
				typed &&
				out.some(
					(v) =>
						!Number.isSafeInteger(v) ||
						(typed[1].toUpperCase() === "B" && (v < -128 || v > 127)) ||
						(typed[1].toUpperCase() === "I" && (v < -2147483648 || v > 2147483647))
				)
			)
				throw Error("Invalid typed array");
			return out;
		}
		const match = /^[A-Za-z0-9_.+\-]+/.exec(source.slice(i));
		if (!match) throw Error("Unexpected SNBT token at " + i);
		i += match[0].length;
		const token = match[0];
		if (token === "true" || token === "false") return token === "true";
		if (/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?[bBsSlLfFdD]?$/.test(token)) {
			const number = Number(token.replace(/[bBsSlLfFdD]$/, ""));
			if (preserveLongs && /^[+-]?\d+[lL]?$/.test(token) && !Number.isSafeInteger(number)) return "@long:" + token.replace(/[lL]$/, "");
			if (!Number.isFinite(number) || (Number.isInteger(number) && !Number.isSafeInteger(number))) throw Error("Unsafe SNBT number");
			return number;
		}
		return token;
	};
	const result = value();
	ws();
	if (i !== source.length) throw Error("Trailing SNBT content");
	return result;
}
