import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import net from "node:net";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";

const files = [
	{ name: "config.json", content: '{"schemaVersion":1,"autosaveSeconds":5}' },
	{ name: "categories/demo.json", content: '{"id":"questplus:demo","title":{"en_us":"Demo"},"description":{"en_us":"Demo"}}' },
	{ name: "quests/demo.json", content: '{"id":"questplus:demo","schemaVersion":2,"objectives":[]}' },
];
const port = () =>
	new Promise((resolve, reject) => {
		const server = net.createServer();
		server.once("error", reject);
		server.listen(0, "127.0.0.1", () => {
			const address = server.address();
			server.close(() => resolve(address.port));
		});
	});
async function api(base, route, { method = "GET", body, auth, origin } = {}) {
	const response = await fetch(base + route, {
		method,
		headers: {
			...(auth ? { Authorization: "Bearer " + auth } : {}),
			...(origin ? { Origin: origin } : {}),
			...(body ? { "Content-Type": "application/json" } : {}),
		},
		body: body ? JSON.stringify(body) : undefined,
	});
	return { status: response.status, data: await response.json() };
}
test(
	"Hub enforces browser approval and immutable packs before Minecraft publish",
	async () => {
		const dir = await mkdtemp(path.join(tmpdir(), "questplus-web-test-")),
			PORT = await port();
		const base = "http://127.0.0.1:" + PORT;
		const publicRoot = path.join(dir, "public");
		await mkdir(path.join(publicRoot, "assets"), { recursive: true });
		await writeFile(path.join(publicRoot, "index.html"), '<html><div id="root">Quest Studio</div></html>');
		await writeFile(path.join(publicRoot, "assets", "app.js"), 'console.log("studio");' + "//".padEnd(4000, "x"));
		const child = spawn(process.execPath, ["server.mjs"], {
			cwd: new URL("..", import.meta.url),
			env: {
				...process.env,
				PORT: String(PORT),
				HUB_PUBLIC_URL: base,
				HUB_DATA_DIR: path.join(dir, "packs"),
				WEB_STATIC_DIR: publicRoot,
				STUDIO_ORIGIN: base,
				STUDIO_URL: base + "/",
			},
			stdio: ["ignore", "pipe", "pipe"],
		});
		let logs = "";
		child.stdout.on("data", (chunk) => (logs += chunk));
		try {
			let ready = false;
			for (let i = 0; i < 80; i++) {
				try {
					const r = await api(base, "/health");
					if (r.status === 200) {
						ready = true;
						break;
					}
				} catch {}
				await new Promise((ok) => setTimeout(ok, 50));
			}
			assert.ok(ready, "web starts");
			const page = await fetch(base + "/");
			assert.equal(page.status, 200);
			assert.match(await page.text(), /id="root"/);
			const js = await fetch(base + "/assets/app.js");
			assert.equal(js.status, 200);
			assert.match(js.headers.get("content-type"), /javascript/);
			// Hashed assets are compressed, cached forever and revalidate without a body.
			assert.equal(js.headers.get("content-encoding"), "gzip");
			assert.match(js.headers.get("cache-control"), /immutable/);
			assert.match(await js.text(), /^console\.log\("studio"\);/);
			assert.equal((await fetch(base + "/assets/app.js", { headers: { "if-none-match": js.headers.get("etag") } })).status, 304);
			assert.equal(page.headers.get("cache-control"), "no-cache");
			assert.equal((await fetch(base + "/../hidden.txt")).status, 404);
			assert.equal((await fetch(base + "/v1/notfound")).status, 404);
			assert.equal((await fetch(base + "/api/notfound")).status, 404);
			assert.equal((await api(base, "/api/server/poll?session=unknown")).status, 401);
			assert.equal((await api(base, "/api/server/revoke", { method: "POST", body: { session: "unknown" } })).status, 401);
			assert.equal((await api(base, "/api/packs", { method: "POST", origin: "https://evil.example", body: { files } })).status, 403);
			for (let i = 0; i < 40 && !logs.includes("POST /api/packs 403"); i++) await new Promise((ok) => setTimeout(ok, 25));
			assert.match(logs, /POST \/api\/packs 403 \d+ms origin=https:\/\/evil\.example error="Origin https:\/\/evil\.example not permitted/);
			assert.match(logs, /GET \/api\/server\/poll 401/);
			assert.doesNotMatch(logs, /\/health|assets\/app\.js/);
			const assets = { names: { "mymod:crystal": { fr_fr: "Cristal" } }, textures: { "mymod:crystal": "data:image/png;base64,iVBORw0KGgo=" } };
			const start = await api(base, "/api/server/sessions", {
				method: "POST",
				body: { serverName: "Test server", files, registry: { items: ["mymod:crystal"], assets } },
			});
			assert.equal(start.status, 201);
			const id = start.data.id;
			const primaryToken = start.data.serverToken;

			// A second server creates an isolated session with its own credential.
			const publicStart = await api(base, "/api/server/sessions", {
				method: "POST",
				body: { serverName: "Independent public server", files },
			});
			assert.equal(publicStart.status, 201);
			const publicId = publicStart.data.id;
			const publicToken = publicStart.data.serverToken;
			assert.match(publicToken, /^[A-Za-z0-9_-]{40,}$/);
			assert.notEqual(publicId, id);
			assert.equal((await api(base, "/api/server/poll?session=" + publicId)).status, 401);
			assert.equal((await api(base, "/api/server/poll?session=" + publicId, { auth: primaryToken })).status, 401);
			assert.equal((await api(base, "/api/server/poll?session=" + publicId, { auth: publicToken })).status, 200);
			assert.equal((await api(base, "/api/server/poll?session=" + id, { auth: publicToken })).status, 401);
			const publicPending = await api(base, "/api/editor/sessions/" + publicId + "/request", { method: "POST", body: {} });
			assert.equal(publicPending.status, 201);
			assert.equal((await api(base, "/api/server/poll?session=" + publicId, { auth: publicToken })).data.pending.code, publicPending.data.code);
			const approveBody = { session: publicId, id: publicPending.data.requestId, code: publicPending.data.code };
			assert.equal((await api(base, "/api/server/approve", { method: "POST", auth: primaryToken, body: approveBody })).status, 401);
			assert.equal((await api(base, "/api/server/approve", { method: "POST", auth: publicToken, body: approveBody })).status, 200);
			const publicStatus = await api(base, "/api/editor/sessions/" + publicId + "/status", {
				method: "POST",
				body: { requestId: publicPending.data.requestId, requestSecret: publicPending.data.requestSecret },
			});
			assert.ok(publicStatus.data.editorToken);
			const browserToken = publicStatus.data.editorToken;
			const publicData = await api(base, "/api/editor/sessions/" + publicId + "/data", { auth: browserToken });
			assert.equal(publicData.status, 200);
			const publicPublish = await api(base, "/api/editor/sessions/" + publicId + "/publish", {
				method: "POST",
				auth: browserToken,
				body: { files, baseRevision: publicData.data.revision },
			});
			assert.equal(publicPublish.status, 202);
			const publicJob = (await api(base, "/api/server/poll?session=" + publicId, { auth: publicToken })).data.job;
			assert.equal(publicJob.id, publicPublish.data.id);
			assert.equal((await api(base, "/api/server/ack", {
				method: "POST", auth: publicToken, body: { session: publicId, id: publicJob.id, success: true },
			})).status, 200);
			assert.equal((await api(base, "/api/editor/sessions/" + publicId + "/jobs/" + publicJob.id, { auth: browserToken })).data.status, "applied");
			assert.equal((await api(base, "/api/server/revoke", {
				method: "POST", auth: publicToken, body: { session: publicId },
			})).status, 200);
			assert.equal((await api(base, "/api/editor/sessions/" + publicId + "/data", { auth: browserToken })).status, 404);
			assert.equal(new URL(start.data.url).origin, base);
			assert.equal(new URL(start.data.url).searchParams.get("hub"), base);
			assert.ok(start.data.url.includes("session=" + id));
			const pending = await api(base, "/api/editor/sessions/" + id + "/request", { method: "POST", body: {} });
			assert.equal(pending.status, 201);
			const poll = await api(base, "/api/server/poll?session=" + id, { auth: primaryToken });
			assert.equal(poll.data.pending.code, pending.data.code);
			assert.equal((await api(base, "/api/editor/sessions/" + id + "/data")).status, 403);
			const status = async () =>
				api(base, "/api/editor/sessions/" + id + "/status", {
					method: "POST",
					body: {
						requestId: pending.data.requestId,
						requestSecret: pending.data.requestSecret,
					},
				});
			assert.equal((await status()).data.approved, false);
			assert.equal(
				(
					await api(base, "/api/server/approve", {
						method: "POST",
						auth: primaryToken,
						body: {
							session: id,
							id: pending.data.requestId,
							code: "000000",
						},
					})
				).status,
				409
			);
			assert.equal(
				(
					await api(base, "/api/server/approve", {
						method: "POST",
						auth: primaryToken,
						body: {
							session: id,
							id: pending.data.requestId,
							code: pending.data.code,
						},
					})
				).status,
				200
			);
			const auth = (await status()).data.editorToken;
			assert.ok(auth?.length > 32);
			const data = await api(base, "/api/editor/sessions/" + id + "/data", { auth });
			assert.deepEqual(data.data.files, files);
			assert.deepEqual(data.data.registry.items, ["mymod:crystal"]);
			assert.deepEqual(data.data.registry.assets, assets);
			assert.equal((await api(base, "/api/editor/sessions/" + id + "/publish", { method: "POST", auth, body: { files, baseRevision: "wrong" } })).status, 409);
			const updated = files.map((f) => (f.name === "config.json" ? { ...f, content: '{"schemaVersion":1,"autosaveSeconds":12}' } : f));
			const publish = await api(base, "/api/editor/sessions/" + id + "/publish", { method: "POST", auth, body: { files: updated, baseRevision: data.data.revision } });
			assert.equal(publish.status, 202);
			const job = (await api(base, "/api/server/poll?session=" + id, { auth: primaryToken })).data.job;
			assert.equal(job.id, publish.data.id);
			assert.deepEqual(job.files, updated);
			assert.equal((await api(base, "/api/server/ack", { method: "POST", auth: primaryToken, body: { session: id, id: job.id, success: true } })).status, 200);
			const complete = await api(base, "/api/editor/sessions/" + id + "/jobs/" + job.id, { auth });
			assert.equal(complete.data.status, "applied");
			assert.notEqual(complete.data.revision, data.data.revision);
			const json5Files = files.map((f) =>
				f.name.startsWith("quests/") ? { name: "quests/demo.json5", content: "// Authored by Studio\n{id:'questplus:demo',schemaVersion:2,objectives:[],}" } : f
			);
			assert.equal((await api(base, "/api/packs", { method: "POST", body: { files: json5Files } })).status, 201);
			assert.equal(
				(await api(base, "/api/packs", { method: "POST", body: { files: json5Files.map((f) => (f.name === "config.json" ? { ...f, content: "{schemaVersion:1}" } : f)) } }))
					.status,
				400
			);
			assert.equal(
				(await api(base, "/api/packs", { method: "POST", body: { files: json5Files.map((f) => (f.name.endsWith(".json5") ? { ...f, content: "{broken" } : f)) } })).status,
				400
			);
			const saved = await api(base, "/api/packs", { method: "POST", body: { files } });
			assert.equal(saved.status, 201);
			const read = await api(base, "/api/packs/" + saved.data.id);
			assert.equal(read.status, 200);
			assert.deepEqual(read.data, { expires: saved.data.expires, sha256: saved.data.sha256, files });
			assert.equal((await api(base, "/api/packs/" + saved.data.id.slice(0, 5))).status, 404);
			const injection = await api(base, "/api/packs", { method: "POST", body: { files: [...files, { name: "quests/../world.json", content: "{}" }] } });
			assert.equal(injection.status, 400);
			const revoke = await api(base, "/api/server/revoke", { method: "POST", auth: primaryToken, body: { session: id } });
			assert.equal(revoke.status, 200);
			assert.equal((await api(base, "/api/editor/sessions/" + id + "/data", { auth })).status, 404);
		} finally {
			child.kill("SIGTERM");
			await rm(dir, { recursive: true, force: true });
		}
	},
	{ timeout: 30000 }
);
