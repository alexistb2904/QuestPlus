package dev.questplus.clientmodel;

/** World-to-screen transform, independent of GUI scale and window dimensions. */
public final class QuestCamera {
    private final double minZoom, maxZoom;
    private double zoom = 1, x, y;
    public QuestCamera(double minZoom, double maxZoom) {
        if (!Double.isFinite(minZoom) || !Double.isFinite(maxZoom) || minZoom <= 0 || maxZoom < minZoom)
            throw new IllegalArgumentException("Invalid zoom bounds");
        this.minZoom = minZoom; this.maxZoom = maxZoom; zoom = Math.clamp(1, minZoom, maxZoom);
    }
    public double zoom() { return zoom; }
    public double screenX(double worldX) { return x + worldX * zoom; }
    public double screenY(double worldY) { return y + worldY * zoom; }
    public double worldX(double screenX) { return (screenX - x) / zoom; }
    public double worldY(double screenY) { return (screenY - y) / zoom; }
    public void pan(double dx, double dy) {
        if (Double.isFinite(dx) && Double.isFinite(dy)) { x += dx; y += dy; }
    }
    public void zoomAt(double factor, double screenX, double screenY) {
        if (!Double.isFinite(factor) || factor <= 0 || !Double.isFinite(screenX) || !Double.isFinite(screenY)) return;
        double worldX = worldX(screenX), worldY = worldY(screenY);
        zoom = Math.clamp(zoom * factor, minZoom, maxZoom);
        x = screenX - worldX * zoom; y = screenY - worldY * zoom;
    }
    public void fit(QuestLayout layout, double left, double top, double width, double height, double padding) {
        if (!Double.isFinite(left) || !Double.isFinite(top) || !Double.isFinite(width) || !Double.isFinite(height)
                || !Double.isFinite(padding) || width <= 0 || height <= 0 || padding < 0) return;
        if (layout.points().isEmpty()) { zoom = Math.clamp(1, minZoom, maxZoom); x = left + width / 2; y = top + height / 2; return; }
        double minX = Double.POSITIVE_INFINITY, minY = minX, maxX = Double.NEGATIVE_INFINITY, maxY = maxX;
        for (var point : layout.points().values()) {
            minX = Math.min(minX, point.x()); minY = Math.min(minY, point.y());
            maxX = Math.max(maxX, point.x()); maxY = Math.max(maxY, point.y());
        }
        double halfX = maxX / 2 - minX / 2 + QuestLayout.NODE_SIZE / 2;
        double halfY = maxY / 2 - minY / 2 + QuestLayout.NODE_SIZE / 2;
        zoom = Math.clamp(Math.min(Math.max(1, width - 2 * padding) / 2 / halfX, Math.max(1, height - 2 * padding) / 2 / halfY), minZoom, maxZoom);
        x = left + width / 2 - (minX / 2 + maxX / 2) * zoom;
        y = top + height / 2 - (minY / 2 + maxY / 2) * zoom;
    }
}
