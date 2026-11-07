export function hubHttpError(response, data) {
	const seconds = Number(response.headers.get("Retry-After"));
	return Object.assign(new Error(data?.error || "HTTP " + response.status), {
		status: response.status,
		retryAfterMs: Number.isFinite(seconds) && seconds > 0 ? Math.min(60000, Math.max(1000, seconds * 1000)) : 5000,
	});
}
export const isHubOverloaded = (error) => error?.status === 429 || error?.status === 503;
