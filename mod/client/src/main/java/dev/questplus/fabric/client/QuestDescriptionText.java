package dev.questplus.fabric.client;

import dev.questplus.clientmodel.QuestLinks;
import dev.questplus.fabric.client.ui.QuestSkin;
import net.minecraft.ChatFormatting;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.net.URI;
import java.util.BitSet;
import java.util.List;

/** Minecraft legacy styles with an escape for literal section signs. */
final class QuestDescriptionText {
	private static final String CODES = "0123456789abcdeflmnor";

	private QuestDescriptionText() {
	}

	record Prepared(Component component, String plain, int literalSection) {
		FormattedCharSequence restore(FormattedCharSequence input) {
			if (literalSection < 0)
				return input;
			return sink -> input.accept((index, style, codePoint) -> sink.accept(index, style,
					codePoint == literalSection ? '§' : codePoint));
		}
	}

	static Prepared prepare(String input) {
		return prepare(input, false);
	}

	/** {@code links} marks https addresses for {@link #linkAt}. Only for text whose screen handles the click. */
	static Prepared prepare(String input, boolean links) {
		List<QuestLinks.Link> found = links ? QuestLinks.find(input) : List.of();
		if (input.indexOf('§') < 0 && found.isEmpty())
			return new Prepared(Component.literal(input), input, -1);
		BitSet used = new BitSet();
		input.codePoints().forEach(used::set);
		int replacement = used.nextClearBit(0xe000);
		if (replacement > 0xf8ff)
			replacement = used.nextClearBit(0xf0000);
		if (replacement > 0xffffd)
			replacement = used.nextClearBit(0x100000);
		if (replacement > 0x10fffd)
			replacement = used.nextClearBit(0x10000);
		// Quest JSON is capped at 1 MiB, leaving a supplementary code point available.
		if (replacement > 0x10ffff)
			throw new IllegalArgumentException("No escape character available");
		MutableComponent formatted = Component.empty();
		StringBuilder run = new StringBuilder(), plain = new StringBuilder();
		Style style = Style.EMPTY;
		int next = 0;
		URI link = null;
		for (int i = 0; i < input.length(); i++) {
			if (link != null && i == found.get(next).end()) {
				flush(formatted, run, style, link);
				link = null;
				next++;
			}
			if (next < found.size() && i == found.get(next).start()) {
				flush(formatted, run, style, null);
				link = found.get(next).uri();
			}
			char c = input.charAt(i);
			if (c == '§') {
				if (i + 1 < input.length()) {
					char code = Character.toLowerCase(input.charAt(i + 1));
					if (CODES.indexOf(code) >= 0) {
						flush(formatted, run, style, link);
						ChatFormatting formatting = ChatFormatting.getByCode(code);
						style = formatting == ChatFormatting.RESET ? Style.EMPTY : style.applyLegacyFormat(formatting);
						i++;
						continue;
					}
					if (code == '§')
						i++;
				}
				// TODO(text): measure literal § separately if wrapping becomes pixel-sensitive.
				run.appendCodePoint(replacement);
				plain.append('§');
			} else {
				run.append(c);
				plain.append(c);
			}
		}
		flush(formatted, run, style, link);
		return new Prepared(formatted, plain.toString(), replacement);
	}

	private static void flush(MutableComponent out, StringBuilder run, Style style, URI link) {
		if (run.isEmpty())
			return;
		out.append(Component.literal(run.toString()).withStyle(link == null ? style
				: style.withClickEvent(new ClickEvent.OpenUrl(link)).withUnderlined(true)
						.withColor(QuestSkin.ACTIVE & 0xffffff)));
		run.setLength(0);
	}

	/** The link drawn under {@code x} pixels of a line made by {@link #prepare(String, boolean)}, or null. */
	static URI linkAt(StringSplitter widths, FormattedCharSequence line, double x) {
		URI[] hit = new URI[1];
		float[] edge = new float[1];
		if (x >= 0)
			line.accept((index, style, codePoint) -> {
				edge[0] += widths.stringWidth(sink -> sink.accept(0, style, codePoint));
				if (x >= edge[0])
					return true;
				if (style.getClickEvent() instanceof ClickEvent.OpenUrl open)
					hit[0] = open.uri();
				return false;
			});
		return hit[0];
	}
}
