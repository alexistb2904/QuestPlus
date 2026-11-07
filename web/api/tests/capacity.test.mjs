import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { mkdtemp, readFile, rm, utimes, writeFile } from "node:fs/promises";
import http from "node:http";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";

const files = [
	{ name: "config.json", content: '{"schemaVersion":1}' },
	{ name: "categories/demo.json", content: "{}" },
	{ name: "quests/demo.json", content: "{}" },
];
async function start(t, env = {}, directory) {
	const dir = directory || (await mkdtemp(path.join(tmpdir(), "questplus-capacity-")));
	const port = await new Promise((r) => {
		const s = http.createServer();
		s.listen(0, "127.0.0.1", () => {
			const p = s.address().port;
			s.close(() => r(p));
		});
	});
	const base = "http://127.0.0.1:" + port;
	const child = spawn(process.execPath, ["server.mjs"], {
		cwd: new URL("..", import.meta.url),
		env: { ...process.env, PORT: String(port), HUB_PUBLIC_URL: base, HUB_DATA_DIR: dir, ...env },
		stdio: "ignore",
	});
	const stop = async () => {
		if (child.exitCode !== null || child.signalCode) return;
		child.kill();
		await new Promise((r) => child.once("exit", r));
	};
	t.after(async () => {
		await stop();
		if (!directory) {
			assert.ok(path.resolve(dir).startsWith(path.resolve(tmpdir()) + path.sep + "questplus-capacity-"));
			await rm(dir, { recursive: true, force: true });
		}
	});
	const tokens = new Map();
	async function api(route, { body, auth, method = body ? "POST" : "GET" } = {}) {
		const sessionId = body?.session || new URL(route, base).searchParams.get("session");
		const credential = auth === undefined ? tokens.get(sessionId) : auth;
		const response = await fetch(base + route, {
			method,
			headers: { ...(credential ? { authorization: "Bearer " + credential } : {}), ...(body ? { "content-type": "application/json" } : {}) },
			body: body ? JSON.stringify(body) : undefined,
		});
		return { status: response.status, retry: response.headers.get("retry-after"), data: await response.json() };
	}
	let ready = false;
	for (let i = 0; i < 100; i++) {
		try {
			if ((await api("/health")).status === 200) {
				ready = true;
				break;
			}
		} catch {}
		await new Promise((r) => setTimeout(r, 20));
	}
	assert.ok(ready);
	const create = async (registry) => {
		const created = await api("/v1/server/sessions", { body: { serverName: "Test", files, registry: registry || { items: ["mod:gem"] } } });
		if (created.status === 201) tokens.set(created.data.id, created.data.serverToken);
		return created;
	};
	async function approve(id) {
		const pending = await api(`/v1/editor/sessions/${id}/request`, { body: {}, auth: "" });
		assert.equal(pending.status, 201);
		assert.equal((await api("/v1/server/approve", { body: { session: id, id: pending.data.requestId, code: pending.data.code } })).status, 200);
		const status = await api(`/v1/editor/sessions/${id}/status`, { body: pending.data, auth: "" });
		assert.equal(status.status, 200);
		return status.data;
	}
	return { api, create, approve, stop, dir, port };
}
test("session admission, authenticated cached data, throttling and revoke release capacity", async (t) => {
	const s = await start(t, { HUB_MAX_SESSIONS: "2" }),
		a = await s.create(),
		b = await s.create();
	assert.equal(a.status, 201);
	assert.equal(b.status, 201);
	const rejected = await s.create();
	assert.equal(rejected.status, 503);
	assert.ok(rejected.retry);
	const route = `/v1/editor/sessions/${a.data.id}/data`;
	assert.equal((await s.api(route, { auth: "" })).status, 403);
	const auth = (await s.approve(a.data.id)).editorToken;
	for (let i = 0; i < 6; i++) {
		const r = await s.api(route, { auth });
		assert.equal(r.status, 200);
		assert.deepEqual(r.data.registry, { items: ["mod:gem"] });
		assert.deepEqual(r.data.files, files);
	}
	const limited = await s.api(route, { auth });
	assert.equal(limited.status, 429);
	assert.ok(limited.retry);
	await s.api("/v1/server/revoke", { body: { session: a.data.id } });
	assert.equal((await s.create()).status, 201);
	assert.equal((await s.api(route, { auth })).status, 404);
});

test("distinct operator sessions behind one proxy do not share the approval rate bucket", async (t) => {
	const s = await start(t);
	for (let i = 0; i < 7; i++) {
		const created = await s.create();
		assert.equal(created.status, 201);
		const route = `/v1/editor/sessions/${created.data.id}`;
		assert.equal((await s.api(route + "/request", { body: {}, auth: "" })).status, 201);
		assert.equal((await s.api(route + "/data", { auth: "" })).status, 403);
	}
});
test("memory and registry bounds reject before retaining data", async (t) => {
	const s = await start(t, { HUB_MAX_STATE_BYTES: "10000" });
	const big = await s.create({ items: Array.from({ length: 2000 }, (_, i) => "mod:item" + i) });
	assert.equal(big.status, 503);
	assert.equal((await s.create()).status, 201);
	const registryLimit = await start(t);
	assert.equal((await registryLimit.create({ names: "a".repeat(5 * 1024 * 1024) })).status, 413);
	assert.equal((await s.api("/health")).status, 200);
});
test("publication reserves future snapshot, ACK atomically swaps it and releases old data", async (t) => {
	const probe = await start(t),
		session = await probe.create(),
		credentials = await probe.approve(session.data.id);
	const original = await probe.api(`/v1/editor/sessions/${session.data.id}/data`, { auth: credentials.editorToken });
	const charge = Buffer.byteLength(JSON.stringify(original.data)) * 3;
	const s = await start(t, { HUB_MAX_STATE_BYTES: String(charge * 3 + 100), HUB_MAX_SESSIONS: "3" });
	const a = await s.create(),
		b = await s.create();
	assert.equal(a.status, 201);
	assert.equal(b.status, 201);
	const auth = await s.approve(a.data.id),
		url = `/v1/editor/sessions/${a.data.id}`;
	const updated = files.map((f) => (f.name === "config.json" ? { ...f, content: " ".repeat(80) + '{"schemaVersion":2}' } : f));
	const publish = () => s.api(url + "/publish", { auth: auth.editorToken, body: { files: updated, baseRevision: auth.revision } });
	assert.equal((await publish()).status, 503);
	await s.api("/v1/server/revoke", { body: { session: b.data.id } });
	const job = await publish();
	assert.equal(job.status, 202);
	assert.equal((await publish()).status, 409);
	assert.equal((await s.create()).status, 503);
	assert.equal((await s.api(url + "/data", { auth: auth.editorToken })).data.revision, auth.revision);
	const ack = await s.api("/v1/server/ack", { body: { session: a.data.id, id: job.data.id, success: true } });
	assert.equal(ack.status, 200);
	const next = await s.api(url + "/data", { auth: auth.editorToken });
	assert.deepEqual(next.data.files, updated);
	assert.equal(next.data.revision, ack.data.revision);
	assert.deepEqual(next.data.registry, { items: ["mod:gem"] });
	assert.equal((await s.api("/v1/server/ack", { body: { session: a.data.id, id: job.data.id, success: true } })).status, 409);
	await s.api("/v1/server/revoke", { body: { session: a.data.id } });
	assert.equal((await s.create()).status, 201);
});
test("failed ACK preserves current cached data and releases the reserved publication", async (t) => {
	const s = await start(t),
		a = await s.create(),
		auth = await s.approve(a.data.id),
		url = `/v1/editor/sessions/${a.data.id}`;
	const job = await s.api(url + "/publish", { auth: auth.editorToken, body: { files, baseRevision: auth.revision } });
	assert.equal(job.status, 202);
	assert.equal((await s.api("/v1/server/ack", { body: { session: a.data.id, id: job.data.id, success: false, error: "Minecraft validation rejected" } })).status, 200);
	const current = await s.api(url + "/data", { auth: auth.editorToken });
	assert.equal(current.data.revision, auth.revision);
	assert.deepEqual(current.data.files, files);
	assert.equal((await s.api(url + "/publish", { auth: auth.editorToken, body: { files, baseRevision: auth.revision } })).status, 202);
});
test("revocation during an upload cannot publish into a removed session or leak capacity", async (t) => {
	const s = await start(t, { HUB_MAX_SESSIONS: "1" }),
		a = await s.create(),
		auth = await s.approve(a.data.id);
	const content = JSON.stringify({ files, baseRevision: auth.revision });
	let finish;
	const response = new Promise((resolve) => {
		const req = http.request(
			{
				host: "127.0.0.1",
				port: s.port,
				path: `/v1/editor/sessions/${a.data.id}/publish`,
				method: "POST",
				headers: { authorization: "Bearer " + auth.editorToken, "content-length": Buffer.byteLength(content) },
			},
			(res) => {
				res.resume();
				res.on("end", () => resolve(res.statusCode));
			}
		);
		req.on("error", () => resolve("reset"));
		req.write(content.slice(0, 20));
		finish = () => req.end(content.slice(20));
	});
	await new Promise((r) => setTimeout(r, 60));
	await s.api("/v1/server/revoke", { body: { session: a.data.id } });
	finish();
	assert.equal(await response, 404);
	assert.equal((await s.create()).status, 201);
});
test("persistent pack quotas survive restart and expired files release space", async (t) => {
	const s = await start(t, { HUB_MAX_PACKS: "1" });
	const pack = await s.api("/v1/packs", { body: { files } });
	assert.equal(pack.status, 201);
	assert.equal((await s.api("/v1/packs", { body: { files } })).status, 503);
	assert.equal((await s.api("/v1/packs/" + pack.data.id, { auth: "" })).status, 200);
	await s.stop();
	const restart = await start(t, { HUB_MAX_PACKS: "1" }, s.dir);
	assert.equal((await restart.api("/v1/packs", { body: { files } })).status, 503);
	await restart.stop();
	const old = new Date(Date.now() - 8 * 86400000),
		file = path.join(s.dir, pack.data.id + ".json");
	await utimes(file, old, old);
	const stillValid = await start(t, { HUB_MAX_PACKS: "1" }, s.dir);
	assert.equal((await stillValid.api("/v1/packs/" + pack.data.id, { auth: "" })).status, 200);
	await stillValid.stop();
	const record = JSON.parse(await readFile(file, "utf8"));
	await writeFile(file, JSON.stringify({ files: record.files, sha256: record.sha256, expires: Date.now() - 1 }));
	await utimes(file, old, old);
	const cleaned = await start(t, { HUB_MAX_PACKS: "1" }, s.dir);
	assert.equal((await cleaned.api("/v1/packs", { body: { files } })).status, 201);
});
test("pack byte quota limits concurrent uploads independently of the file count", async (t) => {
	const bytes = Buffer.byteLength(JSON.stringify({ expires: Date.now() + 7 * 86400000, sha256: "0".repeat(64), files }));
	const s = await start(t, { HUB_MAX_PACK_BYTES: String(bytes + 5), HUB_MAX_PACKS: "10" });
	const results = await Promise.all(Array.from({ length: 5 }, () => s.api("/v1/packs", { body: { files } })));
	assert.equal(results.filter((r) => r.status === 201).length, 1);
	assert.ok(results.filter((r) => r.status === 503).length === 4);
});
test("admitted sessions keep enough room for one same-sized publication", async (t) => {
	const s = await start(t, { HUB_MAX_STATE_BYTES: "4000" }),
		ids = [];
	for (let i = 0; i < 10; i++) {
		const r = await s.create();
		if (r.status === 503) break;
		assert.equal(r.status, 201);
		ids.push(r.data.id);
	}
	assert.ok(ids.length > 0 && ids.length < 10);
	const auth = await s.approve(ids[0]);
	assert.equal((await s.api(`/v1/editor/sessions/${ids[0]}/publish`, { auth: auth.editorToken, body: { files, baseRevision: auth.revision } })).status, 202);
});
test("request admission rejects excess incomplete bodies while health remains available", async (t) => {
	const s = await start(t),
		pending = [];
	t.after(() => pending.forEach((req) => req.destroy()));
	for (let i = 0; i < 64; i++) {
		const req = http.request({ host: "127.0.0.1", port: s.port, path: "/v1/server/revoke", method: "POST", headers: {} }, (res) =>
			res.resume()
		);
		req.on("error", () => {});
		req.write("{");
		pending.push(req);
	}
	await new Promise((r) => setTimeout(r, 150));
	assert.equal((await s.create()).status, 503);
	assert.equal((await s.api("/health")).status, 200);
	pending.forEach((req) => req.destroy());
	await new Promise((r) => setTimeout(r, 60));
	assert.equal((await s.create()).status, 201);
});
test("pending uploads cannot monopolize aggregate body capacity and abort releases it", async (t) => {
	const s = await start(t);
	const req = http.request(
		{ host: "127.0.0.1", port: s.port, path: "/v1/server/sessions", method: "POST", headers: { "content-length": 10 * 1024 * 1024 } },
		(res) => res.resume()
	);
	req.on("error", () => {});
	t.after(() => req.destroy());
	req.write("{");
	await new Promise((r) => setTimeout(r, 80));
	const response = await new Promise((resolve) => {
		const other = http.request(
			{ host: "127.0.0.1", port: s.port, path: "/v1/server/sessions", method: "POST", headers: { "content-length": 10 * 1024 * 1024 } },
			(res) => {
				res.resume();
				res.on("end", () => resolve(res.statusCode));
			}
		);
		other.on("error", () => resolve("reset"));
		other.end("{" + "x".repeat(10 * 1024 * 1024 - 1));
	});
	assert.equal(response, 503);
	assert.equal((await s.api("/health")).status, 200);
	req.destroy();
	await new Promise((r) => setTimeout(r, 50));
	assert.equal((await s.create()).status, 201);
});
