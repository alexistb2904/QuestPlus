package dev.questplus.clientmodel;

/**
 * Shared world-space viewport for Quest Studio and the Minecraft tablet. The
 * renderer uniformly scales every diagram element at the camera zoom. Wheel
 * zoom preserves the world coordinate below the pointer.
 */
public final class QuestCamera {
	public static final double MIN_ZOOM = 0.2, MAX_ZOOM = 5, ZOOM_STEP = 1.15;
	private final double minZoom, maxZoom;
	private double zoom = 1, x, y;

	public QuestCamera(double minZoom, double maxZoom) {
		if (!Double.isFinite(minZoom) || !Double.isFinite(maxZoom) || minZoom <= 0 || maxZoom < minZoom)
			throw new IllegalArgumentException("Invalid zoom bounds");
		this.minZoom = minZoom;
		this.maxZoom = maxZoom;
	}

	public double zoom() {
		return zoom;
	}

	public double screenX(double worldX) {
		return x + worldX * zoom;
	}

	public double screenY(double worldY) {
		return y + worldY * zoom;
	}

	public double worldX(double screenX) {
		return (screenX - x) / zoom;
	}

	public double worldY(double screenY) {
		return (screenY - y) / zoom;
	}

	public void pan(double dx, double dy) {
		if (Double.isFinite(dx) && Double.isFinite(dy)) {
			x += dx;
			y += dy;
		}
	}

	public void zoomAt(double factor, double screenX, double screenY) {
		if (!Double.isFinite(factor) || factor <= 0 || !Double.isFinite(screenX) || !Double.isFinite(screenY))
			return;
		double wx = worldX(screenX), wy = worldY(screenY);
		double next = Math.clamp(zoom * factor, minZoom, maxZoom);
		zoom = next;
		x = screenX - wx * zoom;
		y = screenY - wy * zoom;
	}

	/** Fit centers all authored world anchors without changing the current zoom. */
	public void fitBounds(java.util.Collection<QuestLayout.Point> points, double left, double top, double width,
			double height, double paddingX, double paddingY) {
		if (!Double.isFinite(left) || !Double.isFinite(top) || width <= 0 || height <= 0 || !Double.isFinite(paddingX)
				|| !Double.isFinite(paddingY))
			return;
		if (points.isEmpty()) {
			x = left + width / 2;
			y = top + height / 2;
			return;
		}
		double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
		for (var point : points) {
			if (!Double.isFinite(point.x()) || !Double.isFinite(point.y()))
				continue;
			minX = Math.min(minX, point.x());
			minY = Math.min(minY, point.y());
			maxX = Math.max(maxX, point.x());
			maxY = Math.max(maxY, point.y());
		}
		if (!Double.isFinite(minX))
			return;
		x = left + width / 2 - (minX + maxX) * .5 * zoom;
		y = top + height / 2 - (minY + maxY) * .5 * zoom;
	}

	public void fit(QuestLayout layout, double left, double top, double width, double height, double padding) {
		if (!Double.isFinite(left) || !Double.isFinite(top) || !Double.isFinite(width) || !Double.isFinite(height)
				|| !Double.isFinite(padding) || width <= 0 || height <= 0 || padding < 0)
			return;
		fitBounds(layout.points().values(), left, top, width, height, padding, padding);
	}
}
