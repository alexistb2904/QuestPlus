import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");
const file = (p) => readFileSync(path.join(root, p), "utf8");

test("only three runtime root components exist and Fabric entrypoints remain stable", () => {
	for (const dir of ["web/editor", "web/api", "mod/client/src/main/java", "mod/client/src/main/resources", "mod/server/src/main/java", "mod/server/src/main/resources"])
		assert.ok(existsSync(path.join(root, dir)), dir + " missing");
	assert.equal(existsSync(path.join(root, "hub")), false, "old standalone Hub must be gone");
	assert.equal(existsSync(path.join(root, "src")), false, "old unpartitioned source root must be gone");
	const entry = JSON.parse(file("mod/server/src/main/resources/fabric.mod.json"));
	assert.equal(entry.id, "questplus");
	assert.equal(entry.entrypoints.main[0], "dev.questplus.fabric.QuestPlusMod");
	assert.equal(entry.entrypoints.client[0], "dev.questplus.fabric.client.QuestPlusClient");
	assert.ok(existsSync(path.join(root, "mod/client/src/main/resources/questplus.client.mixins.json")));
	assert.ok(existsSync(path.join(root, "mod/server/src/main/resources/questplus.mixins.json")));
});

test("one universal Java artifact compiles both source sets including tests and GameTests", () => {
	const gradle = file("mod/build.gradle");
	for (const dir of ["client/src/main/java", "server/src/main/java", "client/src/test/java", "server/src/test/java", "client/src/gametest/java", "server/src/gametest/java"])
		assert.ok(gradle.includes(dir), dir + " missing from Gradle");
	assert.match(gradle, /processResources/);
	assert.match(gradle, /web\/editor\/src\/data/);
	assert.match(gradle, /createSourceSet = true/);
	assert.doesNotMatch(gradle, /include\(['"]client['"]\)|include\(['"]server['"]\)/);
});

test("web service hosts Studio and the API as one deployment with restricted assets", () => {
	const server = file("web/api/server.mjs");
	const docker = file("web/api/Dockerfile");
	const compose = file("web/api/compose.yml");
	assert.match(server, /async function serveStudio/);
	assert.match(server, /if\s*\(req\.method\s*===\s*["']GET["']\s*&&\s*!p\.startsWith\(["']\/v1\/["']\)\)/);
	assert.match(server, /const ALLOWED_ORIGIN\s*=\s*process\.env\.STUDIO_ORIGIN\s*\|\|\s*new URL\(PUBLIC_URL\)\.origin/);
	assert.match(docker, /RUN npm run build/);
	assert.match(docker, /COPY --from=frontend \/build\/editor\/dist \/app\/public/);
	assert.match(compose, /context: \.\./);
	assert.match(compose, /127\.0\.0\.1:8787:8787/);
	assert.doesNotMatch(compose, /working-directory: hub/);
});

test("production web runtime has an explicit heap and container memory budget", () => {
	assert.match(file("web/api/Dockerfile"), /NODE_OPTIONS=--max-old-space-size=384/);
	const compose = file("web/api/compose.yml");
	assert.match(compose, /mem_limit:/);
	assert.match(compose, /HUB_MAX_STATE_BYTES:/);
	assert.match(compose, /HUB_MAX_PACK_BYTES:/);
});

test("server alone is authorized to apply changes; browser receives no server token", () => {
	const java = file("mod/server/src/main/java/dev/questplus/fabric/QuestHubService.java");
	const files = file("mod/server/src/main/java/dev/questplus/fabric/QuestHubFiles.java");
	const client = file("web/editor/src/components/HubPanel.tsx");
	assert.match(java, /QuestHubFiles\.apply/);
	assert.match(java, /\/api\/server\/approve/);
	assert.match(files, /runtime\.reload\(\)/);
	assert.match(files, /previous definitions restored/);
	assert.doesNotMatch(client, /serverToken|serverAuth/);
	assert.doesNotMatch(client, /localStorage\.setItem|sessionStorage\.setItem/);
});
