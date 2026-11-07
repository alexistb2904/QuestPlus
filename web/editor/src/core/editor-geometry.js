// World coordinates are shared with Minecraft. Zoom scales the ENTIRE diagram:
// nodes, typography, media, background rectangles, and cables.
export const SCALE_X = 1,
	SCALE_Y = 1;
export const MIN_ZOOM = 0.2,
	MAX_ZOOM = 5,
	ZOOM_STEP = 1.15;
/** One normal mouse-wheel notch equals one Minecraft mouseScrolled step.
 * Smaller pixel deltas from trackpads remain continuous. */
export function wheelZoomFactor(delta, mode = 0) {
	const steps = mode === 1 ? -delta / 3 : mode === 2 ? -delta * 5 : -delta / 100;
	return Math.pow(ZOOM_STEP, Math.max(-20, Math.min(20, steps)));
}
export function clampZoom(value) {
	return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, Number.isFinite(value) ? value : 1));
}
export function screenPoint(point, camera, size) {
	const z = clampZoom(camera.zoom);
	return { x: (point.x - camera.x) * z + size.width / 2, y: (point.y - camera.y) * z + size.height / 2 };
}
export function worldPoint(point, camera, size) {
	const z = clampZoom(camera.zoom);
	return { x: (point.x - size.width / 2) / z + camera.x, y: (point.y - size.height / 2) / z + camera.y };
}
export function zoomEditorAt(camera, nextZoom, pointer, size) {
	const next = clampZoom(nextZoom),
		world = worldPoint(pointer, camera, size);
	return { x: world.x - (pointer.x - size.width / 2) / next, y: world.y - (pointer.y - size.height / 2) / next, zoom: next };
}
export function fitEditor(points, width, height, _padding = undefined, activeZoom = 1) {
	const list = [...points];
	if (!list.length) return { x: 0, y: 0, zoom: clampZoom(activeZoom) };
	const xs = list.map((p) => p.x),
		ys = list.map((p) => p.y);
	return { x: (Math.min(...xs) + Math.max(...xs)) / 2, y: (Math.min(...ys) + Math.max(...ys)) / 2, zoom: clampZoom(activeZoom) };
}
export function snapEditor(point, gridSize) {
	return { x: Math.round(point.x / gridSize) * gridSize, y: Math.round(point.y / gridSize) * gridSize };
}
