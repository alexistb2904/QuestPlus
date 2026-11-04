package dev.questplus.fabric.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.BitSet;

/** Minecraft legacy styles with an escape for literal section signs. */
final class QuestDescriptionText {
    private static final String CODES="0123456789abcdeflmnor";
    private QuestDescriptionText() {}

    record Prepared(Component component,String plain,int literalSection) {
        FormattedCharSequence restore(FormattedCharSequence input) {
            if (literalSection<0) return input;
            return sink -> input.accept((index,style,codePoint) ->
                sink.accept(index,style,codePoint==literalSection ? '§' : codePoint));
        }
    }

    static Prepared prepare(String input) {
        if (input.indexOf('§')<0) return new Prepared(Component.literal(input),input,-1);
        BitSet used=new BitSet();
        input.codePoints().forEach(used::set);
        int replacement=used.nextClearBit(0xe000);
        if (replacement>0xf8ff) replacement=used.nextClearBit(0xf0000);
        if (replacement>0xffffd) replacement=used.nextClearBit(0x100000);
        if (replacement>0x10fffd) replacement=used.nextClearBit(0x10000);
        // Quest JSON is capped at 1 MiB, leaving a supplementary code point available.
        if (replacement>0x10ffff) throw new IllegalArgumentException("No escape character available");
        MutableComponent formatted=Component.empty();
        StringBuilder run=new StringBuilder(),plain=new StringBuilder();
        Style style=Style.EMPTY;
        for (int i=0;i<input.length();i++) {
            char c=input.charAt(i);
            if (c=='§') {
                if (i+1<input.length()) {
                    char code=Character.toLowerCase(input.charAt(i+1));
                    if (CODES.indexOf(code)>=0) {
                        if (!run.isEmpty()) { formatted.append(Component.literal(run.toString()).withStyle(style)); run.setLength(0); }
                        ChatFormatting formatting=ChatFormatting.getByCode(code);
                        style=formatting==ChatFormatting.RESET ? Style.EMPTY : style.applyLegacyFormat(formatting);
                        i++; continue;
                    }
                    if (code=='§') i++;
                }
                // shortcut: the placeholder can measure differently than §, change to literal-aware wrapping if exact glyph width is needed.
                run.appendCodePoint(replacement); plain.append('§');
            } else { run.append(c); plain.append(c); }
        }
        if (!run.isEmpty()) formatted.append(Component.literal(run.toString()).withStyle(style));
        return new Prepared(formatted,plain.toString(),replacement);
    }
}
