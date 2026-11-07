package dev.questplus.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.google.gson.JsonParser;
import dev.questplus.fabric.client.mixin.GameRendererAccessor;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Uses the same screen bone, cube and perspective matrix as FP PAL's draw. */
public final class TabletFaceAnchor {
	public record Point(double x, double y) {
	}

	record Surface(float minX, float minY, float maxX, float maxY, float z, float pivotX, float pivotY, float pivotZ) {
		float width() {
			return maxX - minX;
		}

		float height() {
			return maxY - minY;
		}
	}

	private static final Surface SCREEN = loadScreen();
	private static Matrix4f inverse;
	private static long captured;

	private TabletFaceAnchor() {
	}

	static Surface sourceScreen() {
		return SCREEN;
	}

	static void reset() {
		inverse = null;
		captured = 0;
	}

	public static void capture(PoseStack pose) {
		if (!TabletSurfaceRenderer.active()) return;
		var projection = ((GameRendererAccessor) Minecraft.getInstance().gameRenderer).questplus$hudProjection();
		var matrix = projection.getMatrix(new Matrix4f()).mul(RenderSystem.getModelViewMatrixCopy())
				.mul(pose.last().pose());
		if (!Float.isFinite(matrix.determinant()) || Math.abs(matrix.determinant()) < 1e-9f)
			return;
		inverse = matrix.invert();
		captured = System.nanoTime();
	}

	/**
	 * Mouse coordinates are Minecraft GUI coordinates, independent of GUI scale.
	 */
	public static Point pointer(double x, double y) {
		if (inverse == null || System.nanoTime() - captured > 300_000_000L)
			return null;
		var window = Minecraft.getInstance().getWindow();
		float nx = (float) (2 * x / window.getGuiScaledWidth() - 1),
				ny = (float) (1 - 2 * y / window.getGuiScaledHeight());
		return intersect(inverse, nx, ny, -SCREEN.maxX() / 16f, SCREEN.minY() / 16f,
				SCREEN.z() / 16f, SCREEN.width() / 16f, SCREEN.height() / 16f);
	}

	/**
	 * Projects a texture point back to raw window pixels for real mouse integration
	 * tests.
	 */
	static Point windowPoint(double x, double y) {
		if (inverse == null || System.nanoTime() - captured > 300_000_000L)
			return null;
		var p = new Vector4f(
				(float) ((-SCREEN.maxX() + (1 - x / TabletSurfaceRenderer.WIDTH) * SCREEN.width()) / 16f),
				(float) ((SCREEN.minY() + (1 - y / TabletSurfaceRenderer.HEIGHT) * SCREEN.height()) / 16f),
				SCREEN.z() / 16f, 1).mul(new Matrix4f(inverse).invert());
		if (!Float.isFinite(p.w) || p.w <= 0)
			return null;
		var window = Minecraft.getInstance().getWindow();
		return new Point((p.x / p.w + 1) * window.getWidth() / 2.0, (1 - p.y / p.w) * window.getHeight() / 2.0);
	}

	static Point intersect(Matrix4f inverse, float nx, float ny, float x0, float y0, float z, float width,
			float height) {
		var a = new Vector4f(nx, ny, 0, 1).mul(inverse);
		var b = new Vector4f(nx, ny, 1, 1).mul(inverse);
		if (a.w == 0 || b.w == 0)
			return null;
		a.div(a.w);
		b.div(b.w);
		float dz = b.z - a.z;
		if (!Float.isFinite(dz) || Math.abs(dz) < 1e-7f)
			return null;
		float t = (z - a.z) / dz;
		if (!Float.isFinite(t) || t < 0 || t > 1)
			return null;
		double u = (a.x + (b.x - a.x) * t - x0) / width;
		double v = (a.y + (b.y - a.y) * t - y0) / height;
		if (!Double.isFinite(u) || !Double.isFinite(v) || u < 0 || u > 1 || v < 0 || v > 1)
			return null;
		return new Point((1 - u) * TabletSurfaceRenderer.WIDTH, (1 - v) * TabletSurfaceRenderer.HEIGHT);
	}

	private static Surface loadScreen() {
		try (var stream = TabletFaceAnchor.class
				.getResourceAsStream("/assets/questplus/fp_models/questplus_first_person.geo.json")) {
			if (stream == null)
				throw new IllegalStateException("Missing first-person geometry");
			var geo = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject()
					.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
			com.google.gson.JsonObject root = null, screen = null;
			for (var e : geo.getAsJsonArray("bones")) {
				var bone = e.getAsJsonObject();
				if ("tablet_root".equals(bone.get("name").getAsString()))
					root = bone;
				if ("screen".equals(bone.get("name").getAsString()))
					screen = bone;
			}
			if (root == null || screen == null || !screen.has("cubes"))
				throw new IllegalStateException("No screen/tablet root bones");
			var cube = screen.getAsJsonArray("cubes").get(0).getAsJsonObject();
			var origin = cube.getAsJsonArray("origin");
			var size = cube.getAsJsonArray("size");
			var pivot = root.getAsJsonArray("pivot");
			float x = origin.get(0).getAsFloat(), y = origin.get(1).getAsFloat(), z = origin.get(2).getAsFloat();
			float width = size.get(0).getAsFloat(), height = size.get(1).getAsFloat();
			if (width < 1 || height < 1)
				throw new IllegalStateException("Invalid screen face");
			return new Surface(x, y, x + width, y + height, z, pivot.get(0).getAsFloat(), pivot.get(1).getAsFloat(),
					pivot.get(2).getAsFloat());
		} catch (Exception e) {
			throw new IllegalStateException("Cannot parse Blockbench screen face", e);
		}
	}
}
