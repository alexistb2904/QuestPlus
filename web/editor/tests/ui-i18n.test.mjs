import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";
import { detectUiLocale, getUiLocale, setUiLocale, storedUiLocale, tUI, UI_LANGUAGES } from "../src/ui/ui-i18n.js";

test("browser language detection supports English, French, Spanish and Chinese", () => {
	assert.deepEqual(UI_LANGUAGES, ["en", "fr", "es", "zh"]);
	assert.equal(detectUiLocale(["fr-FR", "en-US"]), "fr");
	assert.equal(detectUiLocale(["es-MX"]), "es");
	assert.equal(detectUiLocale(["zh-Hans-CN"]), "zh");
	assert.equal(detectUiLocale(["ja-JP", "en-GB"]), "en");
	assert.equal(detectUiLocale(["de-DE"]), "en");
});
test("explicit UI override is saved independently from quest content language", () => {
	const map = new Map(),
		storage = { getItem: (key) => map.get(key), setItem: (key, value) => map.set(key, value) };
	assert.equal(storedUiLocale(storage, ["zh-CN"]), "zh");
	assert.equal(setUiLocale("fr", { save: true, storage }), "fr");
	assert.equal(storedUiLocale(storage, ["es-ES"]), "fr");
	assert.equal(tUI("Library"), "Bibliothèque");
	assert.equal(tUI("Quest tree"), "Arbre de quêtes");
	assert.equal(tUI("Create quest"), "Créer une quête");
	setUiLocale("es");
	assert.equal(tUI("Library"), "Biblioteca");
	assert.equal(tUI("Prerequisites"), "Requisitos previos");
	setUiLocale("zh");
	assert.equal(tUI("Library"), "资料库");
	assert.equal(tUI("Quest tree"), "任务树");
	setUiLocale("en");
	assert.equal(tUI("Library"), "Library");
	assert.equal(tUI("Prérequis"), "Prerequisites");
	assert.equal(getUiLocale(), "en");
});
test("React app keeps interface language separate from Minecraft content locales", () => {
	const html = fs.readFileSync(new URL("../index.html", import.meta.url), "utf8");
	const app = fs.readFileSync(new URL("../src/App.tsx", import.meta.url), "utf8");
	const types = fs.readFileSync(new URL("../src/types.ts", import.meta.url), "utf8");
	assert.match(html, /id="root"/);
	assert.match(app, /id="ui-language"/);
	assert.match(app, /id="preview-language"/);
	assert.match(app, /setUiLocale/);
	assert.match(app, /localesOf/);
	assert.match(types, /en_us/);
	assert.match(types, /fr_fr/);
	assert.match(types, /ja_jp/);
	assert.ok(!app.includes("previewMode"));
});
