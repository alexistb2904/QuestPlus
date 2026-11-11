package dev.questplus.fabric.client.ui;

import dev.questplus.fabric.client.mixin.FontAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

public final class QuestSkin {
	/** The Minecraft map item: parchment grounds, ink text, state colours only where they carry meaning. */
	public static final int PAPER = 0xffd9c79e, FIELD = 0xffe8dab5, PANEL = 0xffcdb98a, LINE = 0xffb59b6a;
	public static final int INK = 0xff3a2817, DIM = 0xff574229, LIGHT = 0xfffff4d6;
	public static final int DONE = 0xff3f7a22, ACTIVE = 0xff3f64a6, CLAIM = 0xffb5362b, ALERT = 0xff8f2218;
	public static final int EDGE = LINE, HIGHLIGHT = INK, SELECTED = INK;

	private QuestSkin() {
	}

	/**
	 * The quest screens' own typeface. Minecraft's pixel face is only sharp at
	 * whole scales, and the tablet is seen in perspective; an outline face
	 * rasterised at the exact texel density of the pass being drawn stays smooth
	 * at any size, for no per-frame cost. Glyphs it lacks (CJK) fall back to the
	 * game's default font.
	 */
	private static final String[] FACES = { "ui", "bold", "title" };
	/**
	 * Glyph rasterisations per face. Font textures are sampled NEAREST, so a glyph
	 * drawn at another density than it was rasterised for drops or doubles texels:
	 * every text draw picks the sample matching the pixels it will land on.
	 */
	private static final int[] OVERSAMPLE = { 1, 2, 3, 4, 6, 8 };
	private static final FontDescription.Resource[][] SAMPLES = new FontDescription.Resource[FACES.length][OVERSAMPLE.length];
	public static final Style BOLD = Style.EMPTY.withFont(face("bold")), TITLE = Style.EMPTY.withFont(face("title"));
	/** Texels per logical pixel of the offscreen pass in progress; 0 means the window's GUI scale. */
	public static float renderScale;
	private static Font font;

	static {
		for (int f = 0; f < FACES.length; f++)
			for (int s = 0; s < OVERSAMPLE.length; s++)
				SAMPLES[f][s] = face(FACES[f] + "_" + OVERSAMPLE[s]);
	}

	/** Index of the rasterisation closest, by ratio, to {@code density} texels per glyph unit. */
	static int sample(double density) {
		int best = 0;
		double error = Double.MAX_VALUE;
		for (int s = 0; s < OVERSAMPLE.length; s++) {
			double e = Math.abs(Math.log(OVERSAMPLE[s] / Math.max(.25, density)));
			if (e < error) {
				error = e;
				best = s;
			}
		}
		return best;
	}

	private static int faceOf(FontDescription description) {
		return description.equals(FontDescription.DEFAULT) ? 0
				: description.equals(BOLD.getFont()) ? 1 : description.equals(TITLE.getFont()) ? 2 : -1;
	}

	/** Screen pixels per logical unit at the current pose, in the pass being drawn. */
	public static double density(GuiGraphicsExtractor g) {
		var m = g.pose();
		return Math.hypot(m.m00(), m.m01())
				* (renderScale > 0 ? 1 : Minecraft.getInstance().getWindow().getGuiScale());
	}

	/** The same text with its quest face switched to the sample for this draw's density. */
	public static Component at(GuiGraphicsExtractor g, Component text) {
		int s = sample(density(g));
		return text.copy().withStyle(style -> {
			int face = faceOf(style.getFont());
			return face < 0 ? style : style.withFont(SAMPLES[face][s]);
		});
	}

	private static FontDescription.Resource face(String path) {
		return new FontDescription.Resource(Identifier.fromNamespaceAndPath("questplus", path));
	}

	public static Font font() {
		if (font == null)
			font = new Font(new Font.Provider() {
				private Font.Provider base() {
					return ((FontAccessor) Minecraft.getInstance().font).questplus$provider();
				}

				@Override
				public GlyphSource glyphs(FontDescription description) {
					int face = faceOf(description);
					if (face < 0)
						return base().glyphs(description);
					// Unscaled draws: the pass density alone. Scaled draws go through at().
					float scale = renderScale > 0 ? renderScale : Minecraft.getInstance().getWindow().getGuiScale();
					return base().glyphs(SAMPLES[face][sample(scale)]);
				}

				@Override
				public EffectGlyph effect() {
					return base().effect();
				}
			});
		return font;
	}

	public static Component bold(String text) {
		return Component.literal(text).withStyle(BOLD);
	}

	public static Component title(String text) {
		return Component.literal(text).withStyle(TITLE);
	}

	/** Whole texture pixels keep the compact 500x300 screen font legible. */
	public static final float TEXT_SCALE = 1f;

	public static void text(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color, boolean shadow) {
		text(g, font, Component.literal(text), x, y, color, shadow);
	}

	public static void text(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int color,
			boolean shadow) {
		g.text(font, at(g, text), x, y, themedText(color), shadow);
	}

	/** No drop shadow: on parchment a shadow only muddies the face. */
	public static void centered(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int color) {
		g.text(font, at(g, text), x - font.width(text) / 2, y, themedText(color), false);
	}

	/** The text, or its start and an ellipsis when it would overflow. */
	public static String fit(Font font, String text, int width) {
		return font.width(text) <= width ? text
				: font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
	}

	public static void panel(GuiGraphicsExtractor g, int x, int y, int width, int height, int fill, int border) {
		if (width < 4 || height < 4)
			return;
		int r = x + width, b = y + height;
		g.fill(x + 1, y, r - 1, b, fill);
		g.fill(x, y + 1, x + 1, b - 1, fill);
		g.fill(r - 1, y + 1, r, b - 1, fill);
		g.fill(x + 2, y, r - 2, y + 1, border);
		g.fill(x + 2, b - 1, r - 2, b, border);
		g.fill(x, y + 2, x + 1, b - 2, border);
		g.fill(r - 1, y + 2, r, b - 2, border);
		g.fill(x + 1, y + 1, x + 2, y + 2, border);
		g.fill(r - 2, y + 1, r - 1, y + 2, border);
		g.fill(x + 1, b - 2, x + 2, b - 1, border);
		g.fill(r - 2, b - 2, r - 1, b - 1, border);
	}

	/** Every quest screen shares one window: a parchment page under an ink title bar. */
	public static void window(GuiGraphicsExtractor g, int x, int y, int width, int height, int bar) {
		panel(g, x, y, width, height, PAPER, INK);
		g.fill(x + 1, y + 1, x + width - 1, y + bar, INK);
	}

	/** The one large line a screen owns, set in the title cut rather than scaled up. */
	public static void big(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
		g.text(font, at(g, title(text)), x, y, themedText(color), false);
	}

	public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, double fraction, int color) {
		g.fill(x, y, x + w, y + h, LINE);
		g.fill(x, y, x + (int) Math.round(w * Math.clamp(fraction, 0, 1)), y + h, color);
	}

	/**
	 * Authored and pack colours were picked against a dark ground: darken the
	 * light ones until they read on parchment, keeping the hue.
	 */
	public static int onPaper(int color) {
		int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
		int luma = (r * 299 + g * 587 + b * 114) / 1000;
		return luma <= 84 ? color
				: (color & 0xff000000) | (r * 84 / luma << 16) | (g * 84 / luma << 8) | (b * 84 / luma);
	}

	public static FormattedCharSequence onPaper(FormattedCharSequence text) {
		return sink -> text.accept((index, style, codePoint) -> sink.accept(index, style.getColor() == null ? style
				: style.withColor(onPaper(style.getColor().getValue()) & 0xffffff), codePoint));
	}

	private static int themedText(int color) {
		return color == INK ? onPaper(dev.questplus.fabric.client.QuestResourceTheme.global("text_color", color))
				: color == DIM
						? onPaper(dev.questplus.fabric.client.QuestResourceTheme.global("disabled_text_color", color))
						: color;
	}
}
