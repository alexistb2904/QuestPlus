import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { mkdtemp, rm } from "node:fs/promises";
import { createServer } from "node:net";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";

const files = [
	{ name: "config.json", content: '{"schemaVersion":1,"autosaveSeconds":5}' },
	{ name: "categories/demo.json", content: '{"id":"questplus:demo","title":{"en_us":"Demo"}}' },
	{ name: "quests/demo.json", content: '{"id":"questplus:demo","schemaVersion":2,"objectives":[]}' },
];

async function freePort() {
	return new Promise((resolve, reject) => {
		const server = createServer();
		server.once("error", reject);
		server.listen(0, "127.0.0.1", () => {
			const port = server.address().port;
			server.close(() => resolve(port));
		});
	});
}

test("a public Hub issues scoped session credentials", async () => {
	const dir = await mkdtemp(path.join(tmpdir(), "questplus-keyless-"));
	const port = await freePort();
	const base = "http://127.0.0.1:" + port;
	const child = spawn(process.execPath, ["server.mjs"], {
		cwd: new URL("..", import.meta.url),
		env: {
			...process.env,
			PORT: String(port),
			HUB_PUBLIC_URL: base,
			HUB_DATA_DIR: path.join(dir, "packs"),
			WEB_STATIC_DIR: dir,
		},
		stdio: ["ignore", "pipe", "pipe"],
	});
	const api = async (route, method = "GET", body, token) => {
		const response = await fetch(base + route, {
			method,
			headers: {
				...(body ? { "Content-Type": "application/json" } : {}),
				...(token ? { Authorization: "Bearer " + token } : {}),
			},
			body: body ? JSON.stringify(body) : undefined,
		});
		return { status: response.status, data: await response.json() };
	};
	try {
		let ready = false;
		for (let i = 0; i < 80; i++) {
			try {
				if ((await api("/health")).status === 200) {
					ready = true;
					break;
				}
			} catch {}
			await new Promise((resolve) => setTimeout(resolve, 50));
		}
		assert.ok(ready, "API starts with session authentication enabled");
		const created = await api("/api/server/sessions", "POST", { serverName: "Independent server", files });
		assert.equal(created.status, 201);
		assert.match(created.data.serverToken, /^[A-Za-z0-9_-]{40,}$/);
		const id = created.data.id;
		assert.equal((await api("/api/server/poll?session=" + id)).status, 401);
		assert.equal((await api("/api/server/poll?session=" + id, "GET", undefined, created.data.serverToken)).status, 200);
		assert.equal((await api("/api/server/revoke", "POST", { session: id }, created.data.serverToken)).status, 200);
		assert.equal((await api("/api/server/poll?session=" + id, "GET", undefined, created.data.serverToken)).status, 401);
	} finally {
		child.kill("SIGTERM");
		await rm(dir, { recursive: true, force: true });
	}
}, { timeout: 30000 });
