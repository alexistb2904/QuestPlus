import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");
const file = (p) => readFileSync(path.join(root, p), "utf8");

test("only three runtime root components exist and both loaders' entrypoints stay thin", () => {
	for (const dir of ["web/editor", "web/api", "mod/client/src/main/java", "mod/client/src/main/resources", "mod/server/src/main/java", "mod/server/src/main/resources", "mod/fabric/src/main/java", "mod/neoforge/src/main/java"])
		assert.ok(existsSync(path.join(root, dir)), dir + " missing");
	assert.equal(existsSync(path.join(root, "hub")), false, "old standalone Hub must be gone");
	assert.equal(existsSync(path.join(root, "src")), false, "old unpartitioned source root must be gone");
	const entry = JSON.parse(file("mod/fabric/src/main/resources/fabric.mod.json"));
	assert.equal(entry.id, "questplus");
	assert.equal(entry.entrypoints.main[0], "dev.questplus.platform.fabric.QuestPlusFabric");
	assert.equal(entry.entrypoints.client[0], "dev.questplus.platform.fabric.QuestPlusFabricClient");
	assert.match(file("mod/fabric/src/main/java/dev/questplus/platform/fabric/QuestPlusFabric.java"), /QuestPlusMod\.initialize\(\)/);
	assert.match(file("mod/neoforge/src/main/resources/META-INF/neoforge.mods.toml"), /modId = "questplus"/);
	assert.match(file("mod/neoforge/src/main/java/dev/questplus/platform/neoforge/QuestPlusNeoForge.java"), /@Mod\("questplus"\)[\s\S]*QuestPlusMod\.initialize\(\)/);
	assert.ok(existsSync(path.join(root, "mod/client/src/main/resources/questplus.client.mixins.json")));
	assert.ok(existsSync(path.join(root, "mod/server/src/main/resources/questplus.mixins.json")));
});

test("one JAR per loader compiles the shared source sets including tests and GameTests", () => {
	const gradle = file("mod/build.gradle");
	const fabric = file("mod/gradle/minecraft-target.gradle");
	const neoforge = file("mod/gradle/neoforge-target.gradle");
	for (const [loader, script] of [["fabric", fabric], ["neoforge", neoforge]]) {
		for (const dir of ["client/src/main/java", "server/src/main/java", "client/src/test/java", "server/src/test/java", "server/src/gametest/java", `${loader}/src/main/java`])
			assert.ok(script.includes(dir), `${dir} missing from the ${loader} target`);
		assert.match(script, /processResources/);
		assert.match(script, /rootProject\.sharedResources/);
	}
	assert.ok(fabric.includes("client/src/gametest/java"));
	assert.match(fabric, /createSourceSet = true/);
	assert.match(neoforge, /clientSmokeTest/);
	assert.match(gradle, /web\/editor\/src\/data/);
	assert.match(gradle, /universalJar/);
	assert.match(gradle, /neoforgeJar/);
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
