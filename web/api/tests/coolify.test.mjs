import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

test("Coolify exposes one proxy and persists API data", () => {
	const compose = readFileSync(new URL("../../compose.yml", import.meta.url), "utf8");
	const proxy = readFileSync(new URL("../../nginx.conf", import.meta.url), "utf8");
	assert.match(compose, /questplus-packs:\/app\/data\/packs/);
	assert.match(compose, /HUB_PUBLIC_URL: \$\{HUB_PUBLIC_URL/);
	assert.doesNotMatch(compose, /\n\s+ports:/);
	assert.match(proxy, /location \/docs\//);
	assert.match(proxy, /proxy_pass http:\/\/api:8787/);
});
