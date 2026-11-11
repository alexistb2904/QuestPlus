import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");
const read = (p) => readFileSync(path.join(root, p), "utf8");

test("Windows developer entry point dispatches commands and rejects unknown actions", () => {
	const dev = read("dev.bat");
	for (const command of ["client", "server", "studio", "api", "build", "compile", "test", "server-test", "client-test", "doctor", "setup", "web-test", "stack"])
		assert.ok(dev.includes('"%~1"=="' + command + '"'), command + " missing");
	if (process.platform === "win32") {
		const result = spawnSync("cmd.exe", ["/d", "/c", path.join(root, "dev.bat"), "invalid"], { cwd: os.tmpdir(), encoding: "utf8" });
		assert.equal(result.status, 2, result.stdout + result.stderr);
		assert.match(result.stdout, /Usage: dev\.bat/);
		const failed = spawnSync("cmd.exe", ["/d", "/c", path.join(root, "dev.bat"), "doctor"], { cwd: root, env: { ...process.env, PATH: "" }, encoding: "utf8" });
		assert.notEqual(failed.status, 0, failed.stdout);
	}
});

test("compile persists the selected version before Gradle and exports only a successful JAR", { skip: process.platform !== "win32" }, () => {
	const temp = mkdtempSync(path.join(os.tmpdir(), "questplus compile "));
	try {
		mkdirSync(path.join(temp, "scripts"));
		mkdirSync(path.join(temp, "mod/build/libs"), { recursive: true });
		copyFileSync(path.join(root, "dev.bat"), path.join(temp, "dev.bat"));
		copyFileSync(path.join(root, "scripts/Build-Mod.ps1"), path.join(temp, "scripts/Build-Mod.ps1"));
		const properties = "# Project settings\r\nminecraft_version=26.3\r\nmod_version=0.2.0\r\nmaven_group=dev.questplus\r\n";
		writeFileSync(path.join(temp, "mod/gradle.properties"), properties);
		writeFileSync(path.join(temp, "scripts/_gradle.bat"), '@echo off\r\necho %* > "%~dp0..\\args.txt"\r\ncopy /y "%~dp0..\\mod\\gradle.properties" "%~dp0..\\build-properties.txt" >nul\r\nexit /b %BUILD_TEST_EXIT%\r\n');
		const run = (input, code = "0") => spawnSync("cmd.exe", ["/d", "/c", ".\\dev.bat compile"], {
			cwd: temp, input: input + "\r\n", encoding: "utf8", timeout: 15000,
			env: { ...process.env, BUILD_TEST_EXIT: code },
		});
		for (const [input, version] of [["", "0.2.0"], ["0.3.0-beta.1+test", "0.3.0-beta.1+test"], ["", "0.3.0-beta.1+test"]]) {
			writeFileSync(path.join(temp, `mod/build/libs/questplus-${version}.jar`), version);
			writeFileSync(path.join(temp, `mod/build/libs/questplus-${version}-neoforge.jar`), version + " neoforge");
			const result = run(input);
			assert.equal(result.status, 0, result.stdout + result.stderr);
			const expectedProperties = properties.replace("mod_version=0.2.0", `mod_version=${version}`);
			assert.equal(readFileSync(path.join(temp, "mod/gradle.properties"), "utf8"), expectedProperties);
			assert.equal(readFileSync(path.join(temp, "build-properties.txt"), "utf8"), expectedProperties);
			assert.equal(readFileSync(path.join(temp, "args.txt"), "utf8").trim(), "universalJar neoforgeJar");
			assert.equal(readFileSync(path.join(temp, `output/questplus-${version}.jar`), "utf8"), version);
			assert.equal(readFileSync(path.join(temp, `output/questplus-${version}-neoforge.jar`), "utf8"), version + " neoforge");
		}
		for (const invalid of ["../bad", '0.3.0" & echo injected', "0.3.0|bad"]) {
			rmSync(path.join(temp, "args.txt"));
			assert.notEqual(run(invalid).status, 0);
			assert.equal(existsSync(path.join(temp, "args.txt")), false, "Invalid version must not reach Gradle");
			assert.equal(readFileSync(path.join(temp, "mod/gradle.properties"), "utf8"), properties.replace("mod_version=0.2.0", "mod_version=0.3.0-beta.1+test"));
			writeFileSync(path.join(temp, "args.txt"), "");
		}
		writeFileSync(path.join(temp, "mod/build/libs/questplus-0.4.0.jar"), "stale");
		assert.notEqual(run("0.4.0", "7").status, 0);
		assert.equal(existsSync(path.join(temp, "output/questplus-0.4.0.jar")), false);
		assert.notEqual(run("0.5.0").status, 0, "Missing JAR must fail");
		assert.equal(readFileSync(path.join(temp, "output/questplus-0.2.0.jar"), "utf8"), "0.2.0");
		assert.equal(readFileSync(path.join(temp, "mod/gradle.properties"), "utf8"), properties.replace("mod_version=0.2.0", "mod_version=0.5.0"));
	} finally {
		rmSync(temp, { recursive: true, force: true });
	}
});

test("development launchers point to the relocated mod and web services", () => {
	const qa = read("scripts/_qa.bat");
	assert.match(qa, /mod\\gradlew\.bat/);
	assert.match(qa, /web\\api/);
	assert.match(qa, /web\\editor/);
	assert.match(read("scripts/_gradle.bat"), /mod\\gradlew\.bat/);
});

test("local setup creates URL-only config without overwriting an existing server config", () => {
	const temp = mkdtempSync(path.join(os.tmpdir(), "questplus-windows-qa-"));
	try {
		const script = path.join(root, "web/api/tools/setup-local.mjs");
		const run = () => spawnSync(process.execPath, [script, temp], { encoding: "utf8", timeout: 10000 });
		const first = run();
		assert.equal(first.status, 0, first.stderr);
		const config = path.join(temp, "run/server/world/questplus-hub.json");
		const clientConfig = path.join(temp, "run/client/config/questplus-hub.json");
		assert.deepEqual(JSON.parse(readFileSync(config, "utf8")), { url: "http://localhost:8787" });
		assert.deepEqual(JSON.parse(readFileSync(clientConfig, "utf8")), { url: "http://localhost:8787" });
		assert.equal(run().status, 0);
		writeFileSync(config, '{"url":"https://private.example"}');
		assert.equal(run().status, 0);
		assert.equal(readFileSync(config, "utf8"), '{"url":"https://private.example"}');
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
