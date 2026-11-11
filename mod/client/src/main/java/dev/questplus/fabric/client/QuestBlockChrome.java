package dev.questplus.fabric.client;

import dev.questplus.fabric.client.ui.QuestButton;
import dev.questplus.fabric.client.ui.QuestSkin;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class QuestBlockChrome {
    static final int BAR = 32, PAD = 10, ROW = 20, GAP = 4, FOOTER = 34, APPLY = 80, CLOSE = 72;

    private QuestBlockChrome() {
    }

    static QuestButton owner(Component label, int left, int top, int width, Runnable action) {
        int w = Math.clamp(QuestSkin.font().width(label) + 31, 72, Math.max(72, width / 2 - PAD));
        return new QuestButton(label, left + width - PAD - w, top + (BAR - ROW) / 2, w, ROW, action)
                .onBar().icon(Items.PLAYER_HEAD.getDefaultInstance());
    }

    static int footerY(int top, int height) {
        return top + height - FOOTER + 6;
    }

    static int closeX(int left, int width) {
        return left + width - PAD - CLOSE;
    }

    static int applyX(int left, int width) {
        return closeX(left, width) - GAP - APPLY;
    }

    static void window(GuiGraphicsExtractor g, Font font, int screenWidth, int screenHeight, int x, int y, int width,
            int height, ItemStack icon, Component title, String context, int reserved) {
        g.fill(0, 0, screenWidth, screenHeight, 0xb0101215);
        QuestSkin.window(g, x, y, width, height, BAR);
        int textX = x + PAD;
        if (icon != null && !icon.isEmpty()) {
            g.item(icon, textX, y + (BAR - 16) / 2);
            textX += 22;
        }
        int room = Math.max(0, x + width - PAD - reserved - textX);
        Component bold = title.copy().withStyle(QuestSkin.BOLD);
        if (font.width(bold) > room)
            bold = QuestSkin.bold(font.substrByWidth(bold, Math.max(0, room - font.width(QuestSkin.bold("…"))))
                    .getString() + "…");
        boolean line = !context.isEmpty();
        QuestSkin.text(g, font, bold, textX, y + (line ? 6 : 12), QuestSkin.FIELD, false);
        if (line)
            QuestSkin.text(g, font, QuestSkin.fit(font, context, room), textX, y + 18, QuestSkin.LINE, false);
    }

    static Component buttonHover(Iterable<? extends GuiEventListener> children) {
        for (var child : children)
            if (child instanceof QuestButton b && b.visible && b.isHoveredOrFocused())
                return b.hoverText().getString().isEmpty() ? null : b.hoverText();
        return null;
    }

    static void hoverBox(GuiGraphicsExtractor g, Font font, int screenWidth, int screenHeight, int mx, int my,
            Component text) {
        if (text == null)
            return;
        var lines = font.split(text, Math.min(260, screenWidth - 24));
        int w = Math.min(272, screenWidth - 12), h = lines.size() * 10 + 8;
        int x = Math.clamp(mx, 4, Math.max(4, screenWidth - w - 4)), y = Math.max(4, Math.min(my + 14, screenHeight - h - 4));
        g.nextStratum();
        QuestSkin.panel(g, x, y, w, h, 0xf43a2817, QuestSkin.FIELD);
        for (int i = 0; i < lines.size(); i++)
            g.text(font, lines.get(i), x + 5, y + 4 + i * 10, QuestSkin.FIELD, false);
    }

    static void footer(GuiGraphicsExtractor g, Font font, int x, int y, int width, int height, Component status,
            int color, int actions) {
        int buttons = footerY(y, height);
        g.fill(x + PAD, buttons - 6, x + width - PAD, buttons - 5, QuestSkin.LINE);
        int room = Math.max(0, width - 2 * PAD - actions - 2 * GAP);
        String full = status.getString();
        var lines = font.getSplitter().splitLines(full, room, Style.EMPTY);
        if (lines.size() <= 1) {
            QuestSkin.text(g, font, full, x + PAD, buttons + 6, color, false);
            return;
        }
        String first = lines.getFirst().getString();
        QuestSkin.text(g, font, first, x + PAD, buttons + 1, color, false);
        QuestSkin.text(g, font, QuestSkin.fit(font, full.substring(Math.min(full.length(), first.length())).strip(), room),
                x + PAD, buttons + 11, color, false);
    }
}
