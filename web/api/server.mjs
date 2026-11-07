import JSON5 from 'json5';
import { createHash, randomBytes, timingSafeEqual } from 'node:crypto';
import { createReadStream } from 'node:fs';
import { mkdir, open, opendir, readFile, stat, unlink, writeFile } from 'node:fs/promises';
import http from 'node:http';
import path from 'node:path';
import { pipeline } from 'node:stream/promises';
import { fileURLToPath } from 'node:url';

const PORT = Number(process.env.PORT || 8787);
const BIND = process.env.HUB_BIND || "127.0.0.1";
const PUBLIC_URL = (process.env.HUB_PUBLIC_URL || "http://localhost:" + PORT).replace(/\/$/, "");
// React and the API are served from the SAME origin in production.
const ALLOWED_ORIGIN = process.env.STUDIO_ORIGIN || new URL(PUBLIC_URL).origin;
const STUDIO_URL = (process.env.STUDIO_URL || PUBLIC_URL + "/").replace(/\/$/, "/");
const STATIC_DIR = path.resolve(process.env.WEB_STATIC_DIR || path.join(path.dirname(fileURLToPath(import.meta.url)), "../editor/dist"));
const SERVER_KEY = process.env.HUB_SERVER_KEY || "";
const DATA_DIR = path.resolve(process.env.HUB_DATA_DIR || "./data/packs");
const MAX_BYTES = 12 * 1024 * 1024,
	SESSION_MS = 30 * 60 * 1000,
	PACK_MS = 7 * 24 * 60 * 60 * 1000;
const limit = (name, fallback) => {
	const value = Number(process.env[name] || fallback);
	if (!Number.isSafeInteger(value) || value < 1) throw Error("Invalid " + name);
	return value;
};
const MAX_SESSIONS = limit("HUB_MAX_SESSIONS", 128),
	MAX_STATE = limit("HUB_MAX_STATE_BYTES", 64 * 1024 * 1024);
const MAX_PACKS = limit("HUB_MAX_PACKS", 256),
	MAX_PACK_BYTES = limit("HUB_MAX_PACK_BYTES", 256 * 1024 * 1024);
const MAX_BODY = MAX_BYTES + 1024 * 1024,
	MAX_BODY_IN_FLIGHT = 16 * 1024 * 1024,
	MAX_OUTPUT_IN_FLIGHT = 32 * 1024 * 1024;
if (!SERVER_KEY || SERVER_KEY.length < 32) throw Error("Set HUB_SERVER_KEY to a unique random secret (32+ characters)");
if (!PUBLIC_URL.startsWith("https://") && !/^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(PUBLIC_URL)) throw Error("HUB_PUBLIC_URL must use HTTPS except for local development");
const sessions = new Map(),
	rates = new Map();
let stateBytes = 0,
	bodyBytes = 0,
	outputBytes = 0,
	requests = 0,
	sessionCreates = 0,
	packCount = 0,
	packBytes = 0,
	packWrites = 0,
	cleaning = false;
const token = (bytes = 24) => randomBytes(bytes).toString("base64url");
const digest = (value) => createHash("sha256").update(JSON.stringify(value)).digest("hex");
const equal = (a, b) => {
	const x = Buffer.from(String(a || "")),
		y = Buffer.from(String(b || ""));
	return x.length === y.length && timingSafeEqual(x, y);
};
const fail = (code, message) => Object.assign(new Error(message), { status: code });
function reserveOutput(res, bytes) {
	if (bytes > 4096) {
		if (outputBytes + bytes > MAX_OUTPUT_IN_FLIGHT) throw fail(503, "Response capacity reached; retry shortly");
		outputBytes += bytes;
		let released = false;
		const release = () => {
			if (!released) {
				released = true;
				outputBytes -= bytes;
			}
		};
		res.once("finish", release);
		res.once("close", release);
	}
}
function send(res, status, data) {
	if (res.destroyed) return;
	reserveOutput(res, data.length);
	res.writeHead(status, { "Content-Type": "application/json; charset=utf-8", "Content-Length": data.length, "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff" });
	res.end(data);
}
const json = (res, status, value) => send(res, status, Buffer.from(JSON.stringify(value)));
function reserveState(bytes) {
	if (stateBytes + bytes > MAX_STATE) throw fail(503, "Editor memory capacity reached; retry after a session closes");
	stateBytes += bytes;
}
function sessionRoom(bytes) {
	let largest = bytes;
	for (const s of sessions.values()) largest = Math.max(largest, s.bytes, s.job?.bytes || 0);
	// Keep room for one publication of the largest admitted catalogue.
	if (stateBytes + bytes + largest > MAX_STATE) throw fail(503, "Editor memory capacity reached; retry after a session closes");
}
function removeSession(id) {
	const s = sessions.get(id);
	if (s) {
		stateBytes -= s.bytes + (s.job?.bytes || 0);
		sessions.delete(id);
	}
}
function sessionData(files, registry, revision, serverName) {
	return Buffer.from(
		'{"files":' + JSON.stringify(files) + ',"registry":' + registry + ',"revision":' + JSON.stringify(revision) + ',"serverName":' + JSON.stringify(serverName) + "}"
	);
}
const allowedFile = (p) =>
	/^(?:config\.json|queststudio-compatibility\.json|(?:quests|categories)\/[a-z0-9_./-]+\.json5?)$/.test(p) && !p.split("/").includes("..") && !p.startsWith("/");
function documents(value) {
	if (!Array.isArray(value) || value.length > 2000) throw fail(400, "Invalid documents");
	const seen = new Set();
	let total = 0,
		quest = 0,
		category = 0;
	for (const f of value) {
		if (!f || typeof f.name !== "string" || typeof f.content !== "string" || !allowedFile(f.name) || seen.has(f.name)) throw fail(400, "Unsafe or duplicate document");
		seen.add(f.name);
		total += Buffer.byteLength(f.content);
		if (total > MAX_BYTES) throw fail(413, "Pack too large");
		if (f.name.startsWith("quests/")) quest++;
		if (f.name.startsWith("categories/")) category++;
		try {
			(f.name.endsWith(".json5") ? JSON5 : JSON).parse(f.content);
		} catch {
			throw fail(400, "Invalid JSON document: " + f.name);
		}
	}
	if (!seen.has("config.json") || !quest || !category) throw fail(400, "Missing quests, categories or config");
	return value;
}
const active = (s) => s && Date.now() < s.expires;
async function expiredPack(file, bytes, now) {
	const handle = await open(file, "r");
	let prefix;
	try {
		const buffer = Buffer.alloc(256),
			result = await handle.read(buffer, 0, buffer.length, 0);
		prefix = buffer.subarray(0, result.bytesRead).toString("utf8");
	} finally {
		await handle.close();
	}
	const match = prefix.match(/^\{"expires":(\d+),"sha256":"[a-f0-9]{64}","files":/);
	if (match) return Number(match[1]) <= now;
	// Legacy records put expiry after the files. Only read an old candidate, within the shared I/O budget.
	if (bytes > MAX_BODY || bodyBytes + bytes > MAX_BODY_IN_FLIGHT) return false;
	bodyBytes += bytes;
	try {
		return JSON.parse(await readFile(file, "utf8")).expires <= now;
	} catch (error) {
		if (error instanceof SyntaxError) return false;
		throw error;
	} finally {
		bodyBytes -= bytes;
	}
}
const requireServer = (req) => {
	if (!equal(req.headers.authorization, "Bearer " + SERVER_KEY)) throw fail(401, "Invalid server credentials");
};
const bearer = (req) => String(req.headers.authorization || "").replace(/^Bearer /, "");
function editor(s, req) {
	if (!active(s)) throw fail(404, "Session expired");
	if (!s.auth || !equal(s.auth, bearer(req))) throw fail(403, "Editor not authorized");
	s.expires = Date.now() + SESSION_MS;
}
const clean = async () => {
	const now = Date.now();
	for (const [key, s] of sessions) if (s.expires <= now) removeSession(key);
	for (const [key, rate] of rates) if (rate.until <= now) rates.delete(key);
	if (cleaning || packWrites) return;
	cleaning = true;
	try {
		let count = 0,
			bytes = 0;
		for await (const entry of await opendir(DATA_DIR))
			if (/^[A-Za-z0-9_-]{32}\.json$/.test(entry.name)) {
				const file = path.join(DATA_DIR, entry.name);
				try {
					const info = await stat(file);
					if (info.mtimeMs + PACK_MS <= now && (await expiredPack(file, info.size, now))) {
						await unlink(file);
						continue;
					}
					count++;
					bytes += info.size;
				} catch (error) {
					if (error.code !== "ENOENT") throw error;
				}
			}
		packCount = count;
		packBytes = bytes;
	} finally {
		cleaning = false;
	}
};
const rateLimit = (req, action, max, windowMs, subject) => {
	const key = action + ":" + (subject || String(req.socket.remoteAddress || "local"));
	const now = Date.now();
	let entry = rates.get(key);
	if (!entry || entry.until <= now) {
		if (!entry && rates.size >= 4096) {
			for (const [id, value] of rates) if (value.until <= now) rates.delete(id);
			if (rates.size >= 4096) throw fail(503, "Rate limiter capacity reached");
		}
		entry = { count: 0, until: now + windowMs };
		rates.set(key, entry);
	}
	if (++entry.count > max) throw Object.assign(fail(429, "Rate limit exceeded"), { retryAfter: Math.max(1, Math.ceil((entry.until - now) / 1000)) });
};
async function body(req) {
	const declared = Number(req.headers["content-length"] || 0);
	if (declared > MAX_BODY) throw fail(413, "Body too large");
	if (bodyBytes + declared > MAX_BODY_IN_FLIGHT) throw fail(503, "Upload capacity reached; retry shortly");
	let reserved = declared,
		size = 0;
	bodyBytes += reserved;
	const chunks = [];
	const timer = setTimeout(() => req.destroy(), 15000);
	timer.unref();
	try {
		for await (const chunk of req) {
			size += chunk.length;
			if (size > MAX_BODY) throw fail(413, "Body too large");
			if (size > reserved) {
				const extra = size - reserved;
				if (bodyBytes + extra > MAX_BODY_IN_FLIGHT) throw fail(503, "Upload capacity reached; retry shortly");
				bodyBytes += extra;
				reserved += extra;
			}
			chunks.push(chunk);
		}
		try {
			return JSON.parse(Buffer.concat(chunks).toString("utf8"));
		} catch {
			throw fail(400, "Invalid JSON body");
		}
	} finally {
		bodyBytes -= reserved;
		clearTimeout(timer);
	}
}
const sessionInfo = (s) => ({ expires: s.expires, revision: s.revision, serverName: s.serverName, approvalRequired: true });
async function readPack(id) {
	if (!/^[A-Za-z0-9_-]{32}$/.test(id)) throw fail(404, "Unknown pack");
	let stored;
	try {
		const file = path.join(DATA_DIR, id + ".json"),
			bytes = (await stat(file)).size;
		if (bytes > MAX_BODY) throw fail(413, "Stored pack too large");
		if (bodyBytes + bytes > MAX_BODY_IN_FLIGHT) throw fail(503, "Pack read capacity reached; retry shortly");
		bodyBytes += bytes;
		try {
			stored = JSON.parse(await readFile(file, "utf8"));
		} finally {
			bodyBytes -= bytes;
		}
	} catch (error) {
		if (error.status) throw error;
		throw fail(404, "Unknown pack");
	}
	if (stored.expires <= Date.now()) throw fail(410, "Pack expired");
	return stored;
}
const fileTypes = {
	".html": "text/html; charset=utf-8",
	".js": "text/javascript; charset=utf-8",
	".css": "text/css; charset=utf-8",
	".json": "application/json; charset=utf-8",
	".svg": "image/svg+xml",
	".png": "image/png",
	".jpg": "image/jpeg",
	".jpeg": "image/jpeg",
	".webp": "image/webp",
	".gif": "image/gif",
	".ico": "image/x-icon",
	".woff2": "font/woff2",
	".ttf": "font/ttf",
};
async function serveStudio(res, p) {
	let route;
	try {
		route = decodeURIComponent(p);
	} catch {
		throw fail(400, "Invalid path encoding");
	}
	if (route.includes("\\") || route.includes("\\0") || route.split("/").includes("..") || route.startsWith("//")) throw fail(400, "Unsafe static path");
	if (route === "/" || route === "/index.html") route = "/index.html";
	if (!/^\/(?:index\.html|assets\/[a-zA-Z0-9_./-]+|fonts\/[a-zA-Z0-9_./-]+|textures\/[a-zA-Z0-9_./-]+|[a-zA-Z0-9_-]+\.(?:png|webp|svg|ico|json))$/.test(route))
		throw fail(404, "Unknown static resource");
	const file = path.resolve(STATIC_DIR, "." + route);
	if (!file.startsWith(STATIC_DIR + path.sep)) throw fail(404, "Unknown static resource");
	let info;
	try {
		info = await stat(file);
		if (!info.isFile()) throw Error("Not a file");
	} catch {
		throw fail(404, "Quest Studio has not been built or asset is missing");
	}
	reserveOutput(res, info.size);
	const ext = path.extname(file);
	res.writeHead(200, {
		"Content-Type": fileTypes[ext] || "application/octet-stream",
		"Content-Length": info.size,
		"X-Content-Type-Options": "nosniff",
		"Cache-Control": route === "/index.html" ? "no-cache" : "public, max-age=3600",
	});
	await pipeline(createReadStream(file), res);
}
async function handler(req, res) {
	try {
		const origin = req.headers.origin;
		if (origin) {
			if (origin !== ALLOWED_ORIGIN) throw fail(403, "Origin not permitted");
			res.setHeader("Access-Control-Allow-Origin", ALLOWED_ORIGIN);
			res.setHeader("Vary", "Origin");
		}
		res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
		res.setHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
		if (req.method === "OPTIONS") {
			res.writeHead(204);
			return res.end();
		}
		const url = new URL(req.url, "http://localhost"),
			p = url.pathname.startsWith("/api/") ? "/v1/" + url.pathname.slice(5) : url.pathname;
		if (req.method === "GET" && p === "/health") return json(res, 200, { status: "ok", service: "QuestPlus Hub", version: 1 });
		if (requests >= 64) throw fail(503, "Request capacity reached; retry shortly");
		requests++;
		let released = false;
		const timer = setTimeout(() => res.destroy(), 15000);
		timer.unref();
		const release = () => {
			if (!released) {
				released = true;
				requests--;
				clearTimeout(timer);
			}
		};
		res.once("finish", release);
		res.once("close", release);
		// Server -> hub: the API key is NEVER sent to a browser.
		if (p.startsWith("/v1/server/")) {
			requireServer(req);
			if (req.method === "POST" && p === "/v1/server/sessions") {
				if (sessionCreates >= 2) throw fail(503, "Session upload capacity reached; retry shortly");
				sessionCreates++;
				try {
					for (const [id, s] of sessions) if (!active(s)) removeSession(id);
					if (sessions.size >= MAX_SESSIONS) throw fail(503, "Editor session capacity reached; retry after a session closes");
					sessionRoom(Number(req.headers["content-length"] || 0) * 3);
					const input = await body(req),
						id = token(),
						files = documents(input.files);
					if (typeof input.serverName !== "string" || input.serverName.length > 100) throw fail(400, "Invalid server name");
					if (input.registry !== undefined && (!input.registry || typeof input.registry !== "object" || Array.isArray(input.registry)))
						throw fail(400, "Invalid registry");
					const registry = JSON.stringify(input.registry || {});
					if (Buffer.byteLength(registry) > 5 * 1024 * 1024) throw fail(413, "Registry exceeds 5 MiB");
					if (sessions.size >= MAX_SESSIONS) throw fail(503, "Editor session capacity reached; retry after a session closes");
					const s = {
						id,
						serverName: input.serverName,
						files,
						registry,
						revision: digest(files),
						expires: Date.now() + SESSION_MS,
						pending: null,
						auth: null,
						job: null,
						jobStatus: null,
					};
					// Account for the cached bytes plus retained strings/objects; this is not a heap measurement.
					s.data = sessionData(files, registry, s.revision, s.serverName);
					s.bytes = s.data.length * 3;
					sessionRoom(s.bytes);
					reserveState(s.bytes);
					sessions.set(id, s);
					return json(res, 201, { id, url: STUDIO_URL + "?hub=" + encodeURIComponent(PUBLIC_URL) + "&session=" + encodeURIComponent(id), expires: s.expires });
				} finally {
					sessionCreates--;
				}
			}
			if (req.method === "GET" && p === "/v1/server/poll") {
				const s = sessions.get(url.searchParams.get("session"));
				if (!active(s)) throw fail(404, "Session expired");
				rateLimit(req, "session", 240, 60_000, s.id);
				return json(res, 200, {
					pending: s.pending && !s.pending.approved ? { id: s.pending.id, code: s.pending.code } : null,
					job: s.job && !s.job.taken ? { id: s.job.id, files: s.job.files, baseRevision: s.job.baseRevision } : null,
				});
			}
			if (req.method === "POST" && p === "/v1/server/approve") {
				const { session, id, code } = await body(req),
					s = sessions.get(session);
				if (!active(s) || !s.pending || s.pending.id !== id || s.pending.code !== code || s.pending.approved) throw fail(409, "No pending approval");
				s.pending.approved = true;
				s.auth = token(32);
				return json(res, 200, { approved: true });
			}
			if (req.method === "POST" && p === "/v1/server/revoke") {
				const input = await body(req),
					s = sessions.get(input.session);
				if (s) removeSession(input.session);
				return json(res, 200, { revoked: true });
			}
			if (req.method === "POST" && p === "/v1/server/ack") {
				const { session, id, success, error } = await body(req),
					s = sessions.get(session);
				if (!active(s) || !s.job || s.job.id !== id) throw fail(409, "No matching publication");
				if (s.jobStatus?.status !== "pending") throw fail(409, "Already acknowledged");
				s.jobStatus = success ? { status: "applied", revision: s.job.revision } : { status: "failed", error: String(error || "Rejected").slice(0, 500) };
				if (success) {
					stateBytes -= s.bytes;
					s.files = s.job.files;
					s.revision = s.jobStatus.revision;
					s.data = s.job.data;
					s.bytes = s.job.bytes;
				} else stateBytes -= s.job.bytes;
				delete s.job.files;
				delete s.job.data;
				delete s.job.bytes;
				delete s.job.revision;
				s.job.taken = true;
				return json(res, 200, s.jobStatus);
			}
			if (req.method === "GET" && p.startsWith("/v1/server/packs/")) {
				const pack = await readPack(p.split("/").pop());
				return json(res, 200, { files: pack.files, sha256: pack.sha256 });
			}
			throw fail(404, "Unknown server endpoint");
		}
		// Browser: a session link alone NEVER confers edit rights.
		if (req.method === "GET" && /^\/v1\/editor\/sessions\/[A-Za-z0-9_-]+$/.test(p)) {
			const s = sessions.get(p.split("/")[4]);
			if (!active(s)) throw fail(404, "Session expired");
			return json(res, 200, sessionInfo(s));
		}
		if (req.method === "POST" && /^\/v1\/editor\/sessions\/[A-Za-z0-9_-]+\/request$/.test(p)) {
			const s = sessions.get(p.split("/")[4]);
			if (!active(s)) throw fail(404, "Session expired");
			rateLimit(req, "approval", 6, 60_000, s.id);
			if (s.pending && !s.pending.approved) throw fail(409, "Approval already pending");
			const pending = { id: token(), secret: token(32), code: String((randomBytes(4).readUInt32BE(0) % 900000) + 100000), approved: false };
			s.pending = pending;
			return json(res, 201, { requestId: pending.id, requestSecret: pending.secret, code: pending.code });
		}
		if (req.method === "POST" && /^\/v1\/editor\/sessions\/[A-Za-z0-9_-]+\/status$/.test(p)) {
			const s = sessions.get(p.split("/")[4]);
			if (!active(s)) throw fail(404, "Session expired");
			const { requestId, requestSecret } = await body(req);
			if (!s.pending || !equal(s.pending.id, requestId) || !equal(s.pending.secret, requestSecret)) throw fail(403, "Invalid approval request");
			return json(res, 200, s.pending.approved ? { approved: true, editorToken: s.auth, revision: s.revision } : { approved: false });
		}
		if (req.method === "GET" && /^\/v1\/editor\/sessions\/[A-Za-z0-9_-]+\/ping$/.test(p)) {
			const s = sessions.get(p.split("/")[4]);
			editor(s, req);
			rateLimit(req, "session", 240, 60_000, s.id);
			return json(res, 200, { expires: s.expires, revision: s.revision });
		}
		if (req.method === "GET" && /^\/v1\/editor\/sessions\/[A-Za-z0-9_-]+\/data$/.test(p)) {
			const s = sessions.get(p.split("/")[4]);
			editor(s, req);
			rateLimit(req, "data", 6, 60_000, s.id);
			return send(res, 200, s.data);
		}
		if (req.method === "POST" && /^\/v1\/editor\/sessions\/[A-Za-z0-9_-]+\/publish$/.test(p)) {
			const s = sessions.get(p.split("/")[4]);
			editor(s, req);
			if (s.jobStatus?.status === "pending") throw fail(409, "Another publication is in progress");
			rateLimit(req, "publish", 6, 60_000, s.id);
			if (stateBytes + (Number(req.headers["content-length"] || 0) + Buffer.byteLength(s.registry)) * 3 > MAX_STATE)
				throw fail(503, "Editor memory capacity reached; retry after a session closes");
			const input = await body(req);
			if (sessions.get(s.id) !== s) throw fail(404, "Session expired");
			editor(s, req);
			const files = documents(input.files);
			if (input.baseRevision !== s.revision) throw fail(409, "Server revision changed; reload before publishing");
			if (s.jobStatus?.status === "pending") throw fail(409, "Another publication is in progress");
			const id = token(),
				revision = digest(files),
				data = sessionData(files, s.registry, revision, s.serverName),
				bytes = data.length * 3;
			reserveState(bytes);
			s.job = { id, files, baseRevision: s.revision, taken: false, revision, data, bytes };
			s.jobStatus = { status: "pending" };
			return json(res, 202, { id, status: "pending" });
		}
		if (req.method === "GET" && /^\/v1\/editor\/sessions\/[A-Za-z0-9_-]+\/jobs\/[A-Za-z0-9_-]+$/.test(p)) {
			const split = p.split("/"),
				s = sessions.get(split[4]);
			editor(s, req);
			rateLimit(req, "session", 240, 60_000, s.id);
			if (!s.job || s.job.id !== split[6]) throw fail(404, "Unknown publication");
			return json(res, 200, s.jobStatus);
		}
		// Anyone can publish an unlisted immutable pack; the ID is a 192-bit read capability.
		// This does not permit editing any server. Limit uploads to protect public deployments.
		if (req.method === "POST" && p === "/v1/packs") {
			if (cleaning || packWrites >= 2 || packCount >= MAX_PACKS || packBytes >= MAX_PACK_BYTES) throw fail(503, "Pack storage capacity reached; retry later");
			if (packBytes + Number(req.headers["content-length"] || 0) > MAX_PACK_BYTES) throw fail(503, "Pack storage capacity reached; retry later");
			rateLimit(req, "packs", 8, 60 * 60_000);
			packWrites++;
			try {
				const input = await body(req),
					files = documents(input.files);
				const id = token(),
					record = { expires: Date.now() + PACK_MS, sha256: digest(files), files };
				const encoded = JSON.stringify(record),
					bytes = Buffer.byteLength(encoded);
				if (bytes > MAX_BODY) throw fail(413, "Encoded pack too large");
				if (packCount >= MAX_PACKS || packBytes + bytes > MAX_PACK_BYTES) throw fail(503, "Pack storage capacity reached; retry later");
				packCount++;
				packBytes += bytes;
				try {
					await writeFile(path.join(DATA_DIR, id + ".json"), encoded, { flag: "wx", mode: 0o600 });
				} catch (error) {
					if (error.code === "EEXIST") {
						packCount--;
						packBytes -= bytes;
					} else {
						try {
							await unlink(path.join(DATA_DIR, id + ".json"));
							packCount--;
							packBytes -= bytes;
						} catch (cleanup) {
							if (cleanup.code === "ENOENT") {
								packCount--;
								packBytes -= bytes;
							}
						}
					}
					throw error;
				}
				return json(res, 201, { id, url: STUDIO_URL + "?pack=" + id, expires: record.expires, sha256: record.sha256 });
			} finally {
				packWrites--;
			}
		}
		if (req.method === "GET" && /^\/v1\/packs\/[A-Za-z0-9_-]+$/.test(p)) {
			rateLimit(req, "readpacks", 90, 60_000);
			const pack = await readPack(p.split("/").pop());
			return json(res, 200, { files: pack.files, sha256: pack.sha256, expires: pack.expires });
		}
		if (req.method === "GET" && !p.startsWith("/v1/")) return await serveStudio(res, p);
		throw fail(404, "Unknown endpoint");
	} catch (error) {
		if (res.headersSent || res.destroyed) {
			res.destroy();
			return;
		}
		if (error.status === 429 || error.status === 503) res.setHeader("Retry-After", String(error.retryAfter || 5));
		if (!req.complete) {
			// Drain bounded rejected uploads before closing, otherwise unread TCP data resets the error response.
			res.setHeader("Connection", "close");
			const timer = setTimeout(() => req.destroy(), 15000);
			timer.unref();
			try {
				let discarded = 0;
				for await (const chunk of req) {
					discarded += chunk.length;
					if (discarded > MAX_BODY) break;
				}
			} catch {
			} finally {
				clearTimeout(timer);
			}
		}
		json(res, error.status || 500, { error: error.status ? error.message : "Internal server error" });
		if (!error.status) console.error(error);
	}
}
await mkdir(DATA_DIR, { recursive: true });
await clean();
setInterval(() => {
	clean().catch(console.error);
}, 60_000).unref();
const server = http.createServer({ headersTimeout: 10000, requestTimeout: 20000, keepAliveTimeout: 5000 }, handler);
server.maxConnections = 256;
server.listen(PORT, BIND, () => console.log("QuestPlus Web listening on port " + PORT));
