import { questRefs } from "./game-layout.js";

export function edgePoints(start, end, waypoints) {
	return waypoints?.length ? [start, ...waypoints, end] : [start, { x: start.x, y: (start.y + end.y) / 2 }, { x: end.x, y: (start.y + end.y) / 2 }, end];
}
/** Persist only user-authored bend points, never inferred orthogonal elbows. */
export function insertWaypoint(points, index, point) {
	const next = (points || []).map((p) => ({ x: p.x, y: p.y }));
	next.splice(Math.max(0, Math.min(next.length, index)), 0, { x: point.x, y: point.y });
	return next;
}
export function nearestSegment(points, point) {
	let best = 0,
		distance = Infinity;
	for (let i = 0; i < points.length - 1; i++) {
		const a = points[i],
			b = points[i + 1],
			dx = b.x - a.x,
			dy = b.y - a.y;
		const t = Math.max(0, Math.min(1, ((point.x - a.x) * dx + (point.y - a.y) * dy) / (dx * dx + dy * dy || 1)));
		const d = Math.hypot(point.x - a.x - t * dx, point.y - a.y - t * dy);
		if (d < distance) {
			distance = d;
			best = i;
		}
	}
	return best;
}
export function backgroundRect(a, b) {
	return {
		x: Math.round((a.x + b.x) / 2),
		y: Math.round((a.y + b.y) / 2),
		width: Math.max(1, Math.round(Math.abs(b.x - a.x))),
		height: Math.max(1, Math.round(Math.abs(b.y - a.y))),
	};
}
export function isCategoryLink(category, quests, route) {
	const from = quests.find((q) => q.id === route.from),
		to = quests.find((q) => q.id === route.to);
	return !!from && !!to && from.category === category.id && to.category === category.id && questRefs(to.requires).includes(from.id);
}
export function pruneLinkRoutes(project) {
	for (const category of project.categories) {
		if (category.linkRoutes) category.linkRoutes = category.linkRoutes.filter((route) => isCategoryLink(category, project.quests, route));
	}
}
