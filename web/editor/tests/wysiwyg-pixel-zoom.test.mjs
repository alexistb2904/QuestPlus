import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";
import { annotation, category, emptyProject, fileMap, importDocuments, quest, seedProjectLocale, validate } from "../src/core/project.js";
import { richToHtml, validRich } from "../src/core/rich-format.js";
import { minecraftPx } from "../src/ui/rich-dom.js";

const png = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aet0AAAAASUVORK5CYII=";
const read = (p) => fs.readFileSync(new URL(p, import.meta.url), "utf8");

test("pixel font sizes are validated and serialized for annotations and descriptions", () => {
	const parts = [
		{ type: "text", text: "Large quest text", sizePx: 46, font: "default", bold: true },
		{ type: "image", data: png, width: 640, height: 512 },
	];
	assert.ok(validRich(parts));
	assert.match(richToHtml(parts), /font-size:calc\(var\(--mc-px, 1px\) \* 46\)/);
	for (const css of ["calc(var(--mc-px, 1px) * 46)", "46px"]) assert.equal(minecraftPx(css), 46, css);
	assert.equal(minecraftPx("140%"), null);
	assert.match(richToHtml(parts), /width="640" height="512"/);
	assert.equal(validRich([{ type: "text", text: "x", sizePx: 10000 }]), true);
	assert.equal(validRich([{ type: "text", text: "x", sizePx: 0 }]), false);
	assert.equal(validRich([{ type: "image", data: png, width: 2049, height: 512 }]), true);
	assert.equal(validRich([{ type: "image", data: png, width: 100000, height: 100000 }]), true);
	const project = emptyProject();
	project.categories = [category("questplus:pix", "Pixel")];
	const questData = quest(project.categories[0], "questplus:pix/intro", "Intro");
	questData.description = { en_us: "Image" };
	questData.descriptionRich = { en_us: parts };
	project.quests = [questData];
	assert.deepEqual(validate(project), []);
	const doc = fileMap(project);
	const imported = importDocuments([...doc].filter(([name]) => /\.json5?$/.test(name)).map(([name, content]) => ({ name, content })));
	assert.deepEqual(imported.quests[0].descriptionRich.en_us, parts);
	seedProjectLocale(imported, "fr_fr");
	assert.deepEqual(imported.quests[0].descriptionRich.fr_fr, parts);
	imported.quests[0].descriptionRich.fr_fr[0].sizePx = 24;
	assert.equal(imported.quests[0].descriptionRich.en_us[0].sizePx, 46);
});

test("zoom controls scale every node and annotation without changing saved world dimensions", () => {
	const canvas = read("../src/components/TreeCanvas.tsx");
	assert.match(canvas, /width:\s*nodeSize,\s*height:\s*nodeSize/);
	assert.match(canvas, /width:\s*nodeWidth,\s*height:\s*nodeHeight/);
	assert.match(canvas, /transform:\s*["']translate\(-50%,-50%\) scale\(["']\s*\+\s*clampZoom\(camera\.zoom\)\s*\+\s*["']\)["']/);
	assert.match(canvas, /style=\{\{\s*left:\s*p\.x,\s*top:\s*p\.y,\s*width:\s*draft\.width,\s*height:\s*draft\.height,\s*transform:\s*["']translate\(-50%,-50%\) scale\(["']\s*\+\s*z\s*\+\s*["']\)["']/);
	assert.match(canvas, /showQuestTitles\s*=\s*false/);
	assert.match(canvas, /node-title-below/);
	const app = read("../src/App.tsx");
	assert.match(app, /useState\(\(\)\s*=>\s*localStorage\.getItem\("questplus\.quest-titles"\)\s*===\s*"true"\)/);
	assert.match(app, /aria-pressed=\{showQuestTitles\}/);
});

test("WYSIWYG allows direct pixel sizing and drag-resizing in both editing modes", () => {
	const editor = read("../src/components/RichEditor.tsx");
	for (const token of ["sizePx", "applyPixelSize", "image-grip", "onPointerMove", "onPointerUp", 'mode="description"', "onRichChange"]) assert.ok(editor.includes(token), token);
	assert.match(editor, /image\s*&&\s*\(\s*<div className="rich-image-tools">/);
	const renderer = read("../../../mod/client/src/main/java/dev/questplus/fabric/client/RichAnnotationRenderer.java");
	assert.match(renderer, /get\("sizePx"\)/);
	const registry = read("../../../mod/server/src/main/java/dev/questplus/core/QuestRegistry.java");
	assert.match(registry, /descriptionRich\(/);
	assert.match(registry, /sizePx/);
});

test("new quests default to 45px and new annotations scale their text to match", () => {
	assert.equal(quest(category("questplus:a", "A")).size, 45);
	const note = annotation("n");
	assert.deepEqual([note.width, note.height, note.rich.en_us[0].size], [240, 140, 1.4]);
	assert.ok(validRich(note.rich.en_us));
});
