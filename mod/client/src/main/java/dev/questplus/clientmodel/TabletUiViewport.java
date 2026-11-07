package dev.questplus.clientmodel;

/**
 * Maps the native 500×300 tablet UI into a 2D GUI without changing widget
 * geometry. Full layout / pointer transforms are recomputed together after a
 * scale change.
 */
public record TabletUiViewport(double scale, double offsetX, double offsetY) {

	public static final int WIDTH = 500, HEIGHT = 300;
	public static TabletUiViewport fit(int guiWidth, int guiHeight, double preference) {
		if (guiWidth <= 0 || guiHeight <= 0)
			throw new IllegalArgumentException("Invalid GUI dimensions");
		if (!Double.isFinite(preference) || preference <= 0)
			throw new IllegalArgumentException("Invalid GUI scale");
		double usableWidth = Math.max(1, guiWidth - 12.0);
		double usableHeight = Math.max(1, guiHeight - 12.0);
		double scale = Math.min(preference, Math.min(usableWidth / WIDTH, usableHeight / HEIGHT));
		if (scale <= 0 || !Double.isFinite(scale))
			throw new IllegalArgumentException("Invalid viewport fit");
		return new TabletUiViewport(scale, (guiWidth - WIDTH * scale) / 2.0, (guiHeight - HEIGHT * scale) / 2.0);
	}

	public double screenX(double x) {
		return offsetX + x * scale;
	}

	public double screenY(double y) {
		return offsetY + y * scale;
	}

	public double localX(double x) {
		return (x - offsetX) / scale;
	}

	public double localY(double y) {
		return (y - offsetY) / scale;
	}

	public double localDelta(double pixels) {
		return pixels / scale;
	}
}
