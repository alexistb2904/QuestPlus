import assert from "node:assert/strict";
import { spawn, spawnSync } from "node:child_process";
import { createServer } from "node:http";
import { copyFileSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");

test("web readiness distinguishes a stopped API, a conflicting port, and a working editor", async (t) => {
	let healthy = false;
	const server = createServer((req, res) => {
		res.setHeader("Content-Type", req.url === "/health" ? "application/json" : "text/html");
		res.end(req.url === "/health" ? JSON.stringify({ status: "ok", service: healthy ? "QuestPlus Hub" : "Other" }) : '<html><div id="root"></div></html>');
	});
	try {
		await new Promise((resolve, reject) => server.once("error", reject).listen(8787, "127.0.0.1", resolve));
	} catch (error) {
		if (error.code === "EADDRINUSE") return t.skip("Local web API already running");
		throw error;
	}
	const ready = () => new Promise((resolve, reject) => {
		const child = spawn(process.execPath, [path.join(root, "scripts/local-stack.mjs"), "web-ready"], { stdio: "ignore" });
		child.once("error", reject);
		child.once("exit", resolve);
	});
	try {
		assert.equal(await ready(), 2);
		healthy = true;
		assert.equal(await ready(), 0);
	} finally {
		await new Promise(resolve => server.close(resolve));
	}
	assert.equal(await ready(), 1);
});

test("solo client prepares and waits for its web API, reuses it, and still launches when unavailable", { skip: process.platform !== "win32" }, () => {
	const temp = mkdtempSync(path.join(os.tmpdir(), "questplus solo "));
	try {
		mkdirSync(path.join(temp, "scripts"));
		mkdirSync(path.join(temp, "run"));
		copyFileSync(path.join(root, "scripts/dev-client.bat"), path.join(temp, "scripts/dev-client.bat"));
		writeFileSync(path.join(temp, "scripts/_qa.bat"), '@echo off\r\necho %1 >> "%~dp0..\\events.txt"\r\nexit /b 0\r\n');
		writeFileSync(path.join(temp, "scripts/_gradle.bat"), '@echo off\r\necho %1 >> "%~dp0..\\events.txt"\r\nexit /b 0\r\n');
		writeFileSync(path.join(temp, "scripts/local-stack.mjs"), `
import { appendFileSync } from 'node:fs';
appendFileSync(new URL('../events.txt', import.meta.url), process.argv[2] + '\\n');
process.exitCode = Number(process.argv[2] === 'web-ready' ? process.env.SOLO_READY : process.env.SOLO_WAIT);
`);
		for (const [ready, wait] of [["1", "0"], ["0", "0"], ["2", "1"], ["1", "1"]]) {
			writeFileSync(path.join(temp, "events.txt"), "");
			const result = spawnSync("cmd.exe", ["/d", "/c", "scripts\\dev-client.bat --nopause"], {
				cwd: temp, encoding: "utf8", timeout: 15000,
				env: { ...process.env, QUESTPLUS_SKIP_TEST_ASSETS: "1", SOLO_READY: ready, SOLO_WAIT: wait },
			});
			assert.equal(result.status, 0, result.stdout + result.stderr);
			const events = readFileSync(path.join(temp, "events.txt"), "utf8").trim().split(/\r?\n/).map(line => line.trim());
			assert.equal(events[0], "setup");
			assert.equal(events.at(-1), "runClient");
			assert.equal(events.includes("wait-web"), ready === "1");
			assert.equal(events.includes("api-run"), ready === "1");
			if (ready === "1") assert.ok(events.indexOf("wait-web") < events.indexOf("runClient"));
			if (ready === "2" || wait === "1") assert.match(result.stdout, /WARNING.*Quest Studio/);
		}
	} finally {
		rmSync(temp, { recursive: true, force: true });
	}
});
