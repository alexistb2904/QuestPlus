import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { mkdtemp, rm } from "node:fs/promises";
import http from "node:http";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";

const listen = (server) => new Promise((resolve) => server.listen(0, "127.0.0.1", () => resolve(server.address().port)));

async function start(t, webhook) {
	const dir = await mkdtemp(path.join(tmpdir(), "questplus-feedback-"));
	const probe = http.createServer(),
		port = await listen(probe);
	await new Promise((resolve) => probe.close(resolve));
	const base = "http://127.0.0.1:" + port;
	const child = spawn(process.execPath, ["server.mjs"], {
		cwd: new URL("..", import.meta.url),
		stdio: "ignore",
		env: { ...process.env, PORT: String(port), HUB_PUBLIC_URL: base, HUB_DATA_DIR: dir, FEEDBACK_DISCORD_WEBHOOK_URL: webhook },
	});
	t.after(async () => {
		if (child.exitCode === null && !child.signalCode) {
			child.kill();
			await new Promise((resolve) => child.once("exit", resolve));
		}
		await rm(dir, { recursive: true, force: true });
	});
	for (let i = 0; i < 100; i++) {
		try {
			if ((await fetch(base + "/health")).ok) break;
		} catch {}
		await new Promise((resolve) => setTimeout(resolve, 20));
	}
	return (body) =>
		fetch(base + "/api/feedback", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body) }).then((r) => r.status);
}

test("feedback is validated, relayed to Discord without mentions, and rate limited", async (t) => {
	const received = [];
	const discord = http.createServer((req, res) => {
		let data = "";
		req.on("data", (c) => (data += c));
		req.on("end", () => {
			received.push(JSON.parse(data));
			res.writeHead(204).end();
		});
	});
	const port = await listen(discord);
	t.after(() => discord.close());
	const send = await start(t, "http://127.0.0.1:" + port + "/webhook");

	assert.equal(await send({ rating: "meh", message: "hi" }), 400);
	assert.equal(await send({ rating: "good", message: "   " }), 400);
	assert.equal(await send({ rating: "good", message: "x".repeat(2001) }), 400);
	assert.equal(await send({ rating: "bad", message: "@everyone broken", source: "docs", page: "/docs/guide/install.html" }), 202);
	assert.equal(received.length, 1);
	assert.deepEqual(received[0].allowed_mentions, { parse: [] });
	assert.equal(received[0].embeds[0].description, "@everyone broken");
	assert.match(received[0].embeds[0].title, /Documentation/);
	assert.equal(received[0].embeds[0].fields[0].value, "/docs/guide/install.html");
	for (let i = 0; i < 4; i++) await send({ rating: "good", message: "nice" });
	assert.equal(await send({ rating: "good", message: "nice" }), 429);
});

test("feedback is refused when no webhook is configured", async (t) => {
	const send = await start(t, "");
	assert.equal(await send({ rating: "good", message: "nice" }), 503);
});
