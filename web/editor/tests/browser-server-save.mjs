async (page) => {
	const assert = (value, message) => {
		if (!value) throw Error(message);
	};
	const screenshots = "run/";
	let approved = false,
		ack = "queued",
		downloads = 0;
	const publishes = [];
	page.on("download", () => downloads++);
	await page.setViewportSize({ width: 1440, height: 1000 });
	await page.goto("http://127.0.0.1:5174/");
	await page.evaluate(() => {
		localStorage.removeItem("questplus.quest-studio.v1");
		localStorage.setItem("questplus.studio.uiLocale", "en");
	});
	const files = await page.evaluate(async () => {
		const { emptyProject, category, quest, annotation, fileMap } = await import("/src/core/project.js");
		const p = emptyProject();
		p.categories = ["first", "second"].map((id, i) => {
			const c = category("questplus:" + id, id === "first" ? "First category" : "Second category");
			c.annotations = [{ ...annotation("heading", 0, 0), style: "heading", width: 300, height: 60, text: { en_us: i ? "Second heading" : "First heading" } }];
			return c;
		});
		p.quests = p.categories.map((c) => ({ ...quest(c, c.id + "/quest", "Quest " + c.id), layout: { x: 0, y: 150 } }));
		return [...fileMap(p)].filter(([name]) => name.endsWith(".json")).map(([name, content]) => ({ name, content }));
	});
	await page.route("**/api/**", async (route) => {
		const request = route.request(),
			url = new URL(request.url());
		let body = {};
		let code = 200;
		if (url.pathname.endsWith("/request")) body = { requestId: "request", requestSecret: "test-secret", code: "123456" };
		else if (url.pathname.endsWith("/status")) body = approved ? { approved: true, editorToken: "browser-test-token", revision: "r1" } : { approved: false };
		else if (url.pathname.endsWith("/data")) {
			assert(request.headers().authorization === "Bearer browser-test-token", "load requires browser token");
			body = { files, registry: { items: ["minecraft:book", "test:crystal"] }, revision: "r1" };
		} else if (url.pathname.endsWith("/publish")) {
			assert(request.headers().authorization === "Bearer browser-test-token", "publish requires browser token");
			publishes.push(request.postDataJSON());
			body = { id: "job" + publishes.length };
		} else if (url.pathname.includes("/jobs/")) body = { status: ack, revision: "r" + (publishes.length + 1), error: ack === "failed" ? "Rejected test fixture" : undefined };
		else if (url.pathname === "/api/packs") body = { id: "shared-test-pack", url: "http://127.0.0.1:5174/?pack=shared-test-pack" };
		else body = { serverName: "Atelier — survie" };
		await route.fulfill({ status: code, contentType: "application/json", body: JSON.stringify(body) });
	});
	page.once("dialog", (dialog) => dialog.accept());
	await page.goto("http://127.0.0.1:5174/?hub=http://127.0.0.1:5174&session=browser-test");
	await page.getByRole("button", { name: "Connect this browser", exact: true }).click();
	await page.getByText("123456", { exact: true }).waitFor();
	assert(publishes.length === 0, "approval alone never publishes");
	await page.screenshot({ path: screenshots + "quest-editor-approval-desktop.png" });
	await page.setViewportSize({ width: 390, height: 844 });
	await page.screenshot({ path: screenshots + "quest-editor-approval-mobile.png" });
	assert(await page.getByRole("dialog").evaluate((el) => el.scrollWidth <= el.clientWidth), "mobile dialog has no horizontal overflow");
	approved = true;
	await page.getByRole("button", { name: "Load server quests", exact: true }).waitFor({ timeout: 10000 });
	await page.getByRole("button", { name: "Load server quests", exact: true }).click();
	await page.getByText("Up to date on server", { exact: true }).waitFor();
	await page.setViewportSize({ width: 1440, height: 1000 });
	await page.getByRole("button", { name: "Back to editor", exact: true }).click();
	await page.locator(".category-item").filter({ hasText: "Second category" }).click();
	await page.locator(".annotation-node").filter({ hasText: "Second heading" }).click();
	const editor = page.getByRole("textbox", { name: "Annotation en_us", exact: true });
	assert((await editor.innerText()) === "Second heading", "inspector edits the visible category annotation");
	await editor.fill("Edited second heading");
	await page.getByLabel("Text alignment", { exact: true }).selectOption("right");
	const note = page.locator(".annotation-node.selected");
	assert((await note.evaluate((el) => getComputedStyle(el).textAlign)) === "right", "right alignment displayed");
	const before = await note.boundingBox();
	await page.mouse.move(before.x + 30, before.y + 25);
	await page.mouse.down();
	await page.mouse.move(before.x + 100, before.y + 65, { steps: 8 });
	await page.mouse.up();
	await page.getByRole("button", { name: "Save to server", exact: true }).click();
	await page.waitForFunction(() => document.querySelector(".hub-toolbar-status")?.textContent.includes("Saving…"));
	assert(publishes.length === 1, "Save queues one publication");
	assert(publishes[0].baseRevision === "r1", "publication uses loaded revision");
	let cats = publishes[0].files.filter((f) => f.name.startsWith("categories/")).map((f) => JSON.parse(f.content));
	assert(cats[0].annotations[0].text.en_us === "First heading", "other category remains unchanged");
	assert(cats[1].annotations[0].text.en_us === "Edited second heading", "selected annotation text published");
	assert(cats[1].annotations[0].align === "right", "alignment published");
	assert(cats[1].annotations[0].x !== 0 && cats[1].annotations[0].y !== 0, "dragged position published");
	ack = "applied";
	await page.waitForFunction(() => document.querySelector(".hub-toolbar-status")?.textContent.includes("Saved on the server"), { timeout: 10000 });
	assert(downloads === 0, "Save does not download a ZIP");
	await page.getByRole("button", { name: "Atelier — survie", exact: true }).click();
	await page.screenshot({ path: screenshots + "quest-editor-connected-desktop.png" });
	await page.setViewportSize({ width: 390, height: 844 });
	await page.screenshot({ path: screenshots + "quest-editor-connected-mobile.png" });
	assert(await page.getByRole("dialog").evaluate((el) => el.scrollWidth <= el.clientWidth), "connected mobile dialog has no horizontal overflow");
	await page.setViewportSize({ width: 1440, height: 1000 });
	await page.getByRole("button", { name: "Back to editor", exact: true }).click();
	await editor.fill("Unsaved after rejection");
	ack = "failed";
	await page.keyboard.press("Control+s");
	await page.waitForFunction(() => document.querySelector(".hub-toolbar-status")?.textContent.includes("Action failed"), { timeout: 10000 });
	assert(publishes.length === 2 && publishes[1].baseRevision === "r2", "Ctrl+S uses the acknowledged revision");
	assert((await editor.innerText()) === "Unsaved after rejection", "rejected publication preserves editor changes");
	await page.locator(".hub-toolbar-status").click();
	assert(
		await page
			.getByRole("alert")
			.innerText()
			.then((t) => t.includes("Previous quests are kept")),
		"server rejection is explained"
	);
	await page.getByText("Share a pack or open a link", { exact: true }).click();
	await page.getByRole("button", { name: "Create share link", exact: true }).click();
	assert(
		await page
			.locator(".hub-share a")
			.getAttribute("href")
			.then((t) => t.startsWith("http://127.0.0.1:5174/?pack=")),
		"share link is clickable"
	);
	assert(publishes.length === 2, "sharing does not publish to Minecraft");
	await page.getByRole("button", { name: "Disconnect this browser", exact: true }).click();
	await page.getByRole("button", { name: "Back to editor", exact: true }).click();
	assert(downloads === 0, "all save actions avoid ZIP downloads");
	await page.unroute("**/api/**");
	await page.goto("http://127.0.0.1:5174/");
	await page.getByRole("button", { name: "Save draft", exact: true }).click();
	assert(downloads === 0, "offline draft save also avoids ZIP download");
	const result = {
		result: "passed",
		publications: publishes.length,
		downloads,
		checks: [
			"approval gate",
			"server load",
			"category-scoped annotation text and drag",
			"alignment export",
			"acknowledged Save",
			"Ctrl+S",
			"rejection preserves draft",
			"clickable sharing",
			"offline save",
			"desktop and 390px modal",
		],
	};
	await page.evaluate((result) => sessionStorage.setItem("__questServerSaveResult", JSON.stringify(result)), result);
	return result;
};
