#!/usr/bin/env node
// Creates local Hub URLs without modifying existing user configuration.
import { mkdir, readFile, stat, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(process.argv[2] || path.join(path.dirname(fileURLToPath(import.meta.url)), "../../.."));
const run = path.join(root, "run");
const world = path.join(run, "server", "world");
const config = path.join(world, "questplus-hub.json");
const clientConfig = path.join(run, "client", "config", "questplus-hub.json");
const exists = async (file) => Boolean(await stat(file).catch(() => null));
await mkdir(run, { recursive: true });
await mkdir(world, { recursive: true });
await mkdir(path.dirname(clientConfig), { recursive: true });
for (const target of [config, clientConfig])
	if (await exists(target)) {
		try {
			const current = JSON.parse(await readFile(target, "utf8"));
			if (current.url !== "http://localhost:8787")
				console.warn(`[QuestPlus] ATTENTION: ${path.relative(root, target)} does not use the local Hub URL. It was NOT overwritten.`);
			else console.log(`[QuestPlus] Existing ${path.relative(root, target)} points at the local web API.`);
		} catch {
			console.warn(`[QuestPlus] ATTENTION: ${path.relative(root, target)} is invalid. It was NOT overwritten.`);
		}
	} else {
		await writeFile(target, JSON.stringify({ url: "http://localhost:8787" }, null, 2) + "\n", { flag: "wx", mode: 0o600 });
		console.log(`[QuestPlus] Created ${path.relative(root, target)} for LOCAL testing.`);
	}
console.log("[QuestPlus] Start the local API and restart Minecraft. The client config enables Quest Studio in solo worlds.");
