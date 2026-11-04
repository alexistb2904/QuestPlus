package dev.questplus.clientmodel;

/** World-to-screen transform, independent of GUI scale and window dimensions. */
public final class QuestCamera {
    private double x, y;
    public QuestCamera(double minZoom, double maxZoom) {
        if (!Double.isFinite(minZoom) || !Double.isFinite(maxZoom) || minZoom <= 0 || maxZoom < minZoom)
            throw new IllegalArgumentException("Invalid zoom bounds");
        // Bound arguments retained for backward compatibility; scale is always 1.
    }
    public double zoom() { return 1; }
    // One authored world unit always equals one GUI pixel: navigation pans
    // the tree, but never changes node sizes or the distances between anchors.
    public double screenX(double worldX) { return x + worldX; }
    public double screenY(double worldY) { return y + worldY; }
    public double worldX(double screenX) { return screenX - x; }
    public double worldY(double screenY) { return screenY - y; }
    public void pan(double dx, double dy) {
        if (Double.isFinite(dx) && Double.isFinite(dy)) { x += dx; y += dy; }
    }
    /** Legacy entry point: preserve constant art size and spacing. */
    public void zoomAt(double factor,double screenX,double screenY) { /* Pan-only mode. */ }
    /** Fit the same authored world coordinates used by Quest Studio, including annotations.
     * Screen-space padding remains constant instead of scaling with the wheel zoom. */
    public void fitBounds(java.util.Collection<QuestLayout.Point> points,double left,double top,
                          double width,double height,double paddingX,double paddingY) {
        if(!Double.isFinite(left)||!Double.isFinite(top)||width<=0||height<=0
            || !Double.isFinite(paddingX)||!Double.isFinite(paddingY))return;
        if(points.isEmpty()){
            x=left+width/2;y=top+height/2;return;
        }
        double minX=Double.POSITIVE_INFINITY,minY=Double.POSITIVE_INFINITY;
        double maxX=Double.NEGATIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
        for(var point:points){
            if(!Double.isFinite(point.x())||!Double.isFinite(point.y()))continue;
            minX=Math.min(minX,point.x());minY=Math.min(minY,point.y());
            maxX=Math.max(maxX,point.x());maxY=Math.max(maxY,point.y());
        }
        if(!Double.isFinite(minX))return;
        // Center the authored bounding box without squeezing it to fit.
        x=left+width/2-(minX+maxX)*.5;
        y=top+height/2-(minY+maxY)*.5;
    }
    public void fit(QuestLayout layout, double left, double top, double width, double height, double padding) {
        if (!Double.isFinite(left) || !Double.isFinite(top) || !Double.isFinite(width) || !Double.isFinite(height)
                || !Double.isFinite(padding) || width <= 0 || height <= 0 || padding < 0) return;
        if (layout.points().isEmpty()) { x = left + width / 2; y = top + height / 2; return; }
        double minX = Double.POSITIVE_INFINITY, minY = minX, maxX = Double.NEGATIVE_INFINITY, maxY = maxX;
        for (var point : layout.points().values()) {
            minX = Math.min(minX, point.x()); minY = Math.min(minY, point.y());
            maxX = Math.max(maxX, point.x()); maxY = Math.max(maxY, point.y());
        }
        x = left + width / 2 - (minX / 2 + maxX / 2);
        y = top + height / 2 - (minY / 2 + maxY / 2);
    }
}
