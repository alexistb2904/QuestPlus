package dev.questplus.fabric.client;

import net.minecraft.client.StringSplitter;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuestDescriptionTextTest {
	private record Glyph(int codePoint, Style style) {
	}

	private static List<Glyph> glyphs(QuestDescriptionText.Prepared input) {
		return collect(input.restore(sink -> StringDecomposer.iterateFormatted(input.component(), Style.EMPTY, sink)));
	}

	private static List<Glyph> collect(FormattedCharSequence text) {
		List<Glyph> out = new ArrayList<>();
		text.accept((index, style, cp) -> {
			out.add(new Glyph(cp, style));
			return true;
		});
		return out;
	}

	private static String text(List<Glyph> glyphs) {
		StringBuilder out = new StringBuilder();
		glyphs.forEach(glyph -> out.appendCodePoint(glyph.codePoint()));
		return out.toString();
	}

	@Test
	void formatsColorStylesAndResetWithoutChangingTheStoredString() {
		String input = "A§lB§oC§rD§cE§nF§1G§mH";
		var prepared = QuestDescriptionText.prepare(input);
		var chars = glyphs(prepared);
		assertEquals("ABCDEFGH", text(chars));
		assertEquals("ABCDEFGH", prepared.plain());
		assertTrue(chars.get(1).style().isBold());
		assertTrue(chars.get(2).style().isBold());
		assertTrue(chars.get(2).style().isItalic());
		assertFalse(chars.get(3).style().isBold());
		assertNull(chars.get(3).style().getColor());
		assertEquals(0xff5555, chars.get(4).style().getColor().getValue());
		assertTrue(chars.get(5).style().isUnderlined());
		assertFalse(chars.get(6).style().isUnderlined(), "a new color resets existing styles");
		assertEquals(0x0000aa, chars.get(6).style().getColor().getValue());
		assertTrue(chars.get(7).style().isStrikethrough());
		assertEquals("A§lB§oC§rD§cE§nF§1G§mH", input);
	}

	@Test
	void coversAllMinecraftColorsAndUppercaseCodes() {
		int[] colors = { 0x000000, 0x0000aa, 0x00aa00, 0x00aaaa, 0xaa0000, 0xaa00aa, 0xffaa00, 0xaaaaaa, 0x555555,
				0x5555ff, 0x55ff55, 0x55ffff, 0xff5555, 0xff55ff, 0xffff55, 0xffffff };
		for (int i = 0; i < colors.length; i++) {
			String code = "0123456789ABCDEF".substring(i, i + 1);
			var chars = glyphs(QuestDescriptionText.prepare("§" + code + "x"));
			assertEquals(colors[i], chars.getFirst().style().getColor().getValue(), code);
		}
	}

	@Test
	void preservesLiteralSectionSignsUnknownCodesAndUnicode() {
		String input = "A §§l B §q C §k D § \uD83D\uDDFA\uFE0F\nfin §";
		var prepared = QuestDescriptionText.prepare(input);
		String expected = "A §l B §q C §k D § \uD83D\uDDFA\uFE0F\nfin §";
		assertEquals(expected, prepared.plain());
		assertEquals(expected, text(glyphs(prepared)));
		assertTrue(glyphs(prepared).stream().noneMatch(g -> g.style().isObfuscated()));
		assertEquals("Plain text", QuestDescriptionText.prepare("Plain text").plain());
		assertEquals("\ue000§x", QuestDescriptionText.prepare("\ue000§x").plain());
	}

	@Test
	void privateUseCharactersCannotExhaustTheLiteralEscape() {
		StringBuilder input = new StringBuilder("§§");
		for (int cp = 0xe000; cp <= 0xf8ff; cp++)
			input.appendCodePoint(cp);
		for (int cp = 0xf0000; cp <= 0xffffd; cp++)
			input.appendCodePoint(cp);
		for (int cp = 0x100000; cp <= 0x10fffd; cp++)
			input.appendCodePoint(cp);
		var prepared = QuestDescriptionText.prepare(input.toString());
		assertEquals(input.substring(1), prepared.plain());
		assertEquals(0x10000, prepared.literalSection());
	}

	@Test
	void linksAreMarkedAndFoundUnderThePointer() {
		String input = "§lSee https://minecraft.wiki/w/Portal. Not http://x.y or ftp://example.com";
		String plain = input.substring(2);
		var prepared = QuestDescriptionText.prepare(input, true);
		var chars = glyphs(prepared);
		assertEquals(plain, text(chars));
		URI uri = URI.create("https://minecraft.wiki/w/Portal");
		int start = plain.indexOf("https"), end = start + uri.toString().length();
		for (int i = 0; i < chars.size(); i++) {
			assertEquals(i >= start && i < end, chars.get(i).style().getClickEvent() != null, "glyph " + i);
			assertTrue(chars.get(i).style().isBold(), "the link keeps the surrounding style");
		}
		var widths = new StringSplitter((cp, style) -> 1f);
		FormattedCharSequence line = sink -> StringDecomposer.iterateFormatted(prepared.component(), Style.EMPTY, sink);
		assertNull(QuestDescriptionText.linkAt(widths, line, start - .5));
		assertEquals(uri, QuestDescriptionText.linkAt(widths, line, start + .5));
		assertEquals(uri, QuestDescriptionText.linkAt(widths, line, end - .5));
		assertNull(QuestDescriptionText.linkAt(widths, line, end + .5));
		assertNull(QuestDescriptionText.linkAt(widths, line, -1));
		assertNull(QuestDescriptionText.linkAt(widths, line, 1000));
		assertTrue(glyphs(QuestDescriptionText.prepare(input)).stream().noneMatch(g -> g.style().getClickEvent() != null),
				"text on a screen without link clicks stays plain");
	}

	@Test
	void wrappedLinesKeepStyleAndRestoreEscapedSectionSigns() {
		var prepared = QuestDescriptionText.prepare("§lABCD§§lEFGH§rIJ");
		var splitter = new StringSplitter((cp, style) -> 1f);
		var lines = splitter.splitLines(prepared.component(), 4, Style.EMPTY);
		assertTrue(lines.size() > 1);
		List<Glyph> all = new ArrayList<>();
		for (var line : lines)
			all.addAll(collect(prepared.restore(Language.getInstance().getVisualOrder(line))));
		assertEquals("ABCD§lEFGHIJ", text(all));
		for (int i = 0; i < 10; i++)
			assertTrue(all.get(i).style().isBold(), "bold lost at glyph " + i);
		assertFalse(all.get(10).style().isBold());
		assertFalse(all.get(11).style().isBold());
	}
}
