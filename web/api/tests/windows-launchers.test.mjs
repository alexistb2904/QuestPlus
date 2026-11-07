import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");
const read = (p) => readFileSync(path.join(root, p), "utf8");

test("Windows developer entry point dispatches commands and rejects unknown actions", () => {
	const dev = read("dev.bat");
	for (const command of ["client", "server", "studio", "api", "build", "test", "server-test", "client-test", "doctor", "setup", "web-test", "stack"])
		assert.ok(dev.includes('"%~1"=="' + command + '"'), command + " missing");
	if (process.platform === "win32") {
		const result = spawnSync("cmd.exe", ["/d", "/c", path.join(root, "dev.bat"), "invalid"], { cwd: os.tmpdir(), encoding: "utf8" });
		assert.equal(result.status, 2, result.stdout + result.stderr);
		assert.match(result.stdout, /Usage: dev\.bat/);
		const failed = spawnSync("cmd.exe", ["/d", "/c", path.join(root, "dev.bat"), "doctor"], { cwd: root, env: { ...process.env, PATH: "" }, encoding: "utf8" });
		assert.notEqual(failed.status, 0, failed.stdout);
	}
});

test("development launchers point to the relocated mod and web services", () => {
	const qa = read("scripts/_qa.bat");
	assert.match(qa, /mod\\gradlew\.bat/);
	assert.match(qa, /web\\api/);
	assert.match(qa, /web\\editor/);
	assert.match(read("scripts/_gradle.bat"), /mod\\gradlew\.bat/);
});

test("local setup creates an untracked key, reuses it, and never overwrites an existing server config", () => {
	const temp = mkdtempSync(path.join(os.tmpdir(), "questplus-windows-qa-"));
	try {
		const script = path.join(root, "web/api/tools/setup-local.mjs");
		const run = () => spawnSync(process.execPath, [script, temp], { encoding: "utf8", timeout: 10000 });
		const first = run();
		assert.equal(first.status, 0, first.stderr);
		const secret = readFileSync(path.join(temp, "run/questplus-web-local.key"), "utf8").trim();
		assert.match(secret, /^[0-9a-f]{64}$/);
		assert.ok(!first.stdout.includes(secret), "Secret must not be printed");
		const config = path.join(temp, "run/server/world/questplus-hub.json");
		assert.deepEqual(JSON.parse(readFileSync(config, "utf8")), { url: "http://localhost:8787", serverKey: secret });
		assert.equal(run().status, 0);
		assert.equal(readFileSync(path.join(temp, "run/questplus-web-local.key"), "utf8").trim(), secret);
		writeFileSync(config, '{"url":"https://private.example","serverKey":"user-owned"}');
		assert.equal(run().status, 0);
		assert.equal(readFileSync(config, "utf8"), '{"url":"https://private.example","serverKey":"user-owned"}');
	} finally {
		rmSync(temp, { recursive: true, force: true });
	}
});

test("one-click stack orchestrates three child consoles and checks readiness before Minecraft client", () => {
	const starter = read("scripts/dev-stack.bat");
	const component = read("scripts/_stack-component.bat");
	const qa = read("scripts/_qa.bat");
	const helper = read("scripts/local-stack.mjs");
	assert.match(starter, /preflight/);
	assert.match(starter, /wait-web/);
	assert.match(starter, /wait-minecraft/);
	assert.match(starter, /QuestPlus WEB/);
	assert.match(starter, /QuestPlus Minecraft SERVER/);
	assert.match(starter, /QuestPlus Minecraft CLIENT/);
	assert.ok(starter.indexOf("wait-web") < starter.indexOf("wait-minecraft"));
	assert.ok(starter.indexOf("wait-minecraft") < starter.indexOf("QuestPlus Minecraft CLIENT"));
	assert.match(component, /api-run/);
	assert.match(component, /dev-server\.bat/);
	assert.match(component, /dev-client\.bat/);
	assert.match(qa, /QUESTPLUS_NO_BROWSER/);
	assert.match(helper, /checkConfig/);
	assert.match(helper, /httpWebReady/);
	assert.match(helper, /tcpReady/);
	assert.match(helper, /Port 8787 already in use/);
	assert.match(helper, /Port 25565 already in use/);
});

test("latest Fabric jar and test addons are installed before Minecraft launches", () => {
	const server = read("scripts/dev-server.bat");
	const client = read("scripts/dev-client.bat");
	const installer = read("scripts/install-client-test-assets.mjs");
	assert.match(server, /--rerun-tasks jar/);
	assert.ok(server.indexOf("--rerun-tasks jar") < server.indexOf("runServer"));
	assert.match(server, /run\\server\\mods/);
	assert.match(client, /install-client-test-assets\.mjs/);
	assert.doesNotMatch(client, /install-client-test-assets\.mjs"\s+"%~dp0"/);
	assert.match(read("scripts/_qa.bat"), /install-client-test-assets\.mjs" --update/);
	assert.ok(client.indexOf("install-client-test-assets.mjs") < client.indexOf("runClient"));
	assert.match(client, /QUESTPLUS_SKIP_TEST_ASSETS/);
	for (const name of ["iris", "sodium", "punchy-fpa", "punchymoves", "fluid-foolish", "makeup-ultra-fast-shaders", "complementary-reimagined", "bliss-shader"])
		assert.ok(installer.includes("id:'" + name + "'"), "Missing asset " + name);
	assert.match(installer, /sha512/);
});
