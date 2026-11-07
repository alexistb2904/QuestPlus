import assert from "node:assert/strict";
import test from "node:test";
import { hubHttpError, isHubOverloaded } from "../src/core/hub-errors.js";

test("only overload responses preserve an existing authorization", () => {
	for (const status of [429, 503]) assert.equal(isHubOverloaded(hubHttpError(new Response("", { status }), { error: "Busy" })), true);
	for (const status of [401, 403, 404, 409, 500]) assert.equal(isHubOverloaded(hubHttpError(new Response("", { status }), { error: "Denied" })), false);
	assert.equal(isHubOverloaded(new Error("Disconnected")), false);
});
test("overload polling uses a bounded Retry-After without retrying mutations", () => {
	for (const [header, expected] of [
		["5", 5000],
		["60", 60000],
		["999999", 60000],
		["0", 5000],
		["invalid", 5000],
		["0.1", 1000],
	]) {
		const error = hubHttpError(new Response("", { status: 503, headers: { "Retry-After": header } }), { error: "Capacity reached" });
		assert.equal(error.retryAfterMs, expected);
		assert.equal(error.message, "Capacity reached");
		assert.equal(error.status, 503);
	}
});
