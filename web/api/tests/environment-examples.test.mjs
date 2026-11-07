import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

test("deployment examples use the shared origin and public build URLs", () => {
	for (const file of ["../../.env.example", "../.env.example"]) {
		const env = Object.fromEntries(readFileSync(new URL(file, import.meta.url), "utf8")
			.split(/\r?\n/).filter((line) => line && !line.startsWith("#"))
			.map((line) => { const i = line.indexOf("="); return [line.slice(0, i), line.slice(i + 1)]; }));
		assert.equal(env.HUB_PUBLIC_URL, "https://quest.alexistb.com");
		assert.equal(env.VITE_QUESTPLUS_HUB_URL, env.HUB_PUBLIC_URL);
		assert.equal(env.VITE_QUESTPLUS_DOCS_URL, env.HUB_PUBLIC_URL + "/docs/");
	}
});
