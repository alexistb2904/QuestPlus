import assert from "node:assert/strict";
import test from "node:test";
import { COMPAT_ASSETS, PROFILES, validateGpuLog } from "../../../scripts/test-tablet-compatibility.mjs";

test("GPU result cannot pass on missing cases or a watchdog/shader fallback", () => {
	const log = "TABLET COMPAT PASS trial\n".repeat(16) + "TABLET MOVEMENT AND REMOTE POSE PASS";
	assert.doesNotThrow(() => validateGpuLog(log));
	assert.throws(() => validateGpuLog("TABLET COMPAT PASS trial"));
	assert.throws(() => validateGpuLog(log + "\nTablet close animation timed out"));
	assert.throws(() => validateGpuLog(log + "\nShaderCompileException"));
	assert.throws(() => validateGpuLog(log + "\nFailed to create shader rendering pipeline, disabling shaders!"));
	assert.throws(() => validateGpuLog(log, "punchy"));
	assert.doesNotThrow(() => validateGpuLog(log + "\nTABLET PUNCHY REBIND PASS", "punchy"));
});

test("GPU profiles cover popular hand/body mods independently and seven shader families", () => {
	assert.equal(COMPAT_ASSETS.filter((a) => a.type === "shader").length, 7);
	for (const ids of Object.values(PROFILES)) {
		assert.ok(ids.includes("iris") && ids.includes("sodium"));
		assert.ok(!(ids.includes("punchymoves") && ids.includes("not-enough-animations")));
		for (const id of ids)
			assert.ok(
				COMPAT_ASSETS.some((a) => a.id === id),
				id
			);
	}
	assert.ok(PROFILES.firstperson.includes("not-enough-animations"));
	assert.ok(PROFILES.punchy.includes("fluid-foolish"));
});
