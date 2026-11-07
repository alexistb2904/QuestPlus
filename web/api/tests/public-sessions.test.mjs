import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { mkdtemp, rm } from "node:fs/promises";
import http from "node:http";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";

const files = [
	{ name: "config.json", content: '{"schemaVersion":1}' },
	{ name: "categories/demo.json", content: "{}" },
	{ name: "quests/demo.json", content: "{}" },
];
async function start(t, env = {}) {
	const dir = await mkdtemp(path.join(tmpdir(), "questplus-public-auth-"));
	const port = await new Promise(resolve => {
		const server = http.createServer();
		server.listen(0, "127.0.0.1", () => { const port = server.address().port; server.close(() => resolve(port)); });
	});
	const base = "http://127.0.0.1:" + port;
	const child = spawn(process.execPath, ["server.mjs"], {
		cwd: new URL("..", import.meta.url), stdio: "ignore",
		env: { ...process.env, PORT: String(port), HUB_PUBLIC_URL: base, HUB_DATA_DIR: dir, ...env },
	});
	t.after(async () => {
		if (child.exitCode === null && !child.signalCode) { child.kill(); await new Promise(resolve => child.once("exit", resolve)); }
		await rm(dir, { recursive: true, force: true });
	});
	async function api(route, { body, auth, headers, method = body ? "POST" : "GET" } = {}) {
		const response = await fetch(base + route, {
			method, headers: { ...(auth ? { authorization: "Bearer " + auth } : {}), ...(body ? { "content-type": "application/json" } : {}), ...headers },
			body: body ? JSON.stringify(body) : undefined,
		});
		return { status: response.status, retry: response.headers.get("retry-after"), data: await response.json() };
	}
	let ready = false;
	for (let i = 0; i < 100 && child.exitCode === null; i++) {
		try { if ((await api("/health")).status === 200) { ready = true; break; } } catch {}
		await new Promise(resolve => setTimeout(resolve, 20));
	}
	assert.ok(ready, "Public API starts with session-scoped authentication");
	const create = (options = {}) => api("/api/server/sessions", { ...options, body: { files, serverName: "Public Minecraft server" } });
	return { api, create };
}

test("any server can create a private session with its own token, but only its operator can authorize publication", async t => {
	const { api, create } = await start(t);
	const a = await create(), b = await create();
	assert.equal(a.status, 201);
	assert.equal(b.status, 201);
	assert.match(a.data.serverToken, /^[A-Za-z0-9_-]{43}$/);
	assert.notEqual(a.data.serverToken, b.data.serverToken);
	assert.ok(!a.data.url.includes(a.data.serverToken));
	const id = a.data.id, route = "/api/editor/sessions/" + id;
	const pending = await api(route + "/request", { body: {} });
	const approval = { session: id, id: pending.data.requestId, code: pending.data.code };
	assert.equal((await api("/api/server/poll?session=" + id)).status, 401);
	assert.equal((await api("/api/server/poll?session=" + id, { auth: id })).status, 401);
	assert.equal((await api("/api/server/poll?session=" + id, { auth: b.data.serverToken })).status, 401);
	assert.equal((await api("/api/server/approve", { body: approval, auth: b.data.serverToken })).status, 401);
	assert.equal((await api("/api/server/revoke", { body: { session: id }, auth: b.data.serverToken })).status, 401);
	assert.equal((await api("/api/server/ack", { body: { session: id, id: "fake", success: true }, auth: b.data.serverToken })).status, 401);
	assert.equal((await api(route + "/data", { auth: a.data.serverToken })).status, 403);
	assert.equal((await api(route + "/publish", { auth: a.data.serverToken, body: { files, baseRevision: "fake" } })).status, 403);
	const poll = await api("/api/server/poll?session=" + id, { auth: a.data.serverToken });
	assert.equal(poll.status, 200);
	assert.equal(poll.data.pending.code, pending.data.code);
	assert.equal((await api(route + "/status", { body: pending.data })).data.approved, false);
	assert.equal((await api("/api/server/approve", { body: { ...approval, code: "000000" }, auth: a.data.serverToken })).status, 409);
	assert.equal((await api("/api/server/approve", { body: approval, auth: a.data.serverToken })).status, 200);
	const status = await api(route + "/status", { body: pending.data });
	const auth = status.data.editorToken;
	assert.ok(auth && auth !== a.data.serverToken);
	assert.equal((await api("/api/server/poll?session=" + id, { auth })).status, 401);
	const data = await api(route + "/data", { auth });
	assert.deepEqual(data.data.files, files);
	for (const value of [(await api(route)).data, pending.data, status.data, data.data, (await api(route + "/ping", { auth })).data])
		assert.ok(!JSON.stringify(value).includes(a.data.serverToken), "Server credential must never be in browser responses");
	const published = await api(route + "/publish", { auth, body: { files, baseRevision: data.data.revision } });
	assert.equal(published.status, 202);
	assert.equal((await api("/api/server/ack", { auth: b.data.serverToken, body: { session: id, id: published.data.id, success: true } })).status, 401);
	assert.equal((await api("/api/server/ack", { auth: a.data.serverToken, body: { session: id, id: published.data.id, success: true } })).status, 200);
	assert.equal((await api(route + "/jobs/" + published.data.id, { auth })).data.status, "applied");
	assert.equal((await api("/api/server/revoke", { auth: a.data.serverToken, body: { session: id } })).status, 200);
	assert.equal((await api(route + "/data", { auth })).status, 404);
	assert.equal((await api("/api/server/poll?session=" + id, { auth: a.data.serverToken })).status, 401);
	assert.equal((await api("/api/server/poll?session=" + b.data.id, { auth: b.data.serverToken })).status, 200);
});

test("session creation rejects supplied credentials to enforce token-only authentication", async t => {
	const { api, create } = await start(t);
	const created = await create();
	assert.equal(created.status, 201);
	assert.equal((await create({ auth: "unexpected-authentication-header" })).status, 401);
	assert.equal((await api("/api/server/poll?session=" + created.data.id, { auth: created.data.serverToken })).status, 200);
	assert.equal((await api("/api/server/poll?session=" + created.data.id, { auth: "unrelated-token" })).status, 401);
});

test("public session creation is limited before upload and forwarded addresses are trusted only for configured proxy hops", async t => {
	const direct = await start(t, { HUB_SESSION_CREATES_PER_MINUTE: "2" });
	assert.equal((await direct.create({ headers: { "x-forwarded-for": "192.0.2.1" } })).status, 201);
	assert.equal((await direct.create({ headers: { "x-forwarded-for": "192.0.2.2" } })).status, 201);
	const limited = await direct.create({ headers: { "x-forwarded-for": "192.0.2.3" } });
	assert.equal(limited.status, 429);
	assert.ok(limited.retry);
	const proxy = await start(t, { HUB_SESSION_CREATES_PER_MINUTE: "1", HUB_TRUST_PROXY_HOPS: "2" });
	assert.equal((await proxy.create({ headers: { "x-forwarded-for": "192.0.2.1, 198.51.100.1" } })).status, 201);
	assert.equal((await proxy.create({ headers: { "x-forwarded-for": "192.0.2.2, 198.51.100.1" } })).status, 201);
	assert.equal((await proxy.create({ headers: { "x-forwarded-for": "203.0.113.9, 192.0.2.1, 198.51.100.1" } })).status, 429);
});
