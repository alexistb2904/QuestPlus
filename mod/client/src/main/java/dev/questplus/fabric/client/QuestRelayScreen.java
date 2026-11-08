package dev.questplus.fabric.client;

import com.google.gson.*;
import dev.questplus.fabric.QuestBlockNetwork;
import dev.questplus.fabric.client.ui.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** A local draft of the relay settings; only Apply changes the server block. */
public final class QuestRelayScreen extends Screen {
    private record Panel(int x, int y, int width, int height, String title, String hint, String icon) {}
    private record Label(Component text, int x, int y, int width, int color) {}
    private QuestBlockNetwork.Snapshot snapshot;
    private JsonObject state, config;
    private String owner, ownerName, tab = "configuration";
    private QuestTextField stage, threshold;
    private QuestButton apply;
    private final List<Panel> panels = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private final List<String> linkedIcons = new ArrayList<>();
    private int left, top, panelWidth, panelHeight, scroll, header, bodyY, outputX, outputY, outputWidth, linkedPage;
    private int contentHeight, bodyBottom, bodyPanel, bodyLabel;
    private boolean editable, narrow, categoryMode, compact;
    private long catalogueRevision;
    private static Component tr(String key, Object... args) { return QuestBlockScreen.tr(key, args); }

    public QuestRelayScreen(QuestBlockNetwork.Snapshot packet) { super(tr("kind.2")); receive(packet); }
    public void receive(QuestBlockNetwork.Snapshot packet) {
        JsonObject next = JsonParser.parseString(packet.data()).getAsJsonObject();
        boolean changed = state == null || !state.get("config").equals(next.get("config"))
                || !state.get("owner").equals(next.get("owner")) || !state.get("editable").equals(next.get("editable"));
        snapshot = packet; state = next;
        if (changed) {
            config = next.getAsJsonObject("config").deepCopy();
            categoryMode = !config.get("category").getAsString().isEmpty();
            owner = next.get("owner").getAsString(); ownerName = displayOwner(owner);
            editable = next.get("editable").getAsBoolean();
            if (width > 0) rebuildWidgets();
        }
    }
    private String displayOwner(String id) {
        for (var value : state.getAsJsonArray("players")) {
            var player = value.getAsJsonObject();
            if (player.get("id").getAsString().equals(id)) return player.get("name").getAsString();
        }
        String name = state.has("ownerName") ? state.get("ownerName").getAsString() : "";
        return name.isBlank() || name.equals(id) ? tr("relay.unknown_player").getString() : name;
    }
    @Override protected void init() {
        clearWidgets(); panels.clear(); labels.clear(); linkedIcons.clear(); stage = null; threshold = null;
        catalogueRevision = QuestPlusClient.STATE.catalogueRevision;
        panelWidth = width - 20; narrow = panelWidth < 560;
        compact = height < 300; header = compact ? 44 : narrow ? 134 : 88; bodyY = header + 30;
        panelHeight = height - 20; left = 10; top = 10; bodyBottom = top + panelHeight - 50;
        contentHeight = Math.max(tab.equals("configuration") && narrow ? 584 : 300, bodyBottom - top - bodyY);
        scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight - (bodyBottom - top - bodyY)));
        int columns = narrow ? 2 : 4, cardWidth = (panelWidth - 20 - (columns - 1) * 6) / columns;
        String[] names = {"relay.scope", "owner", "relay.team", "relay.output_now"};
        for (int i = 0; i < (compact ? 0 : 4); i++) {
            int x = left + 10 + (i % columns) * (cardWidth + 6), y = top + 42 + (i / columns) * 44;
            panels.add(new Panel(x, y, cardWidth, 40, "", "", ""));
            label(tr(names[i]), x + 7, y + 5, cardWidth - 14, QuestSkin.DIM);
            if (i == 0) button(tr("scope." + config.get("scope").getAsString()), x + 4, y + 18, cardWidth - 8, 18, () -> {
                config.addProperty("scope", config.get("scope").getAsString().equals("PERSONAL") ? "TEAM" : "PERSONAL"); rebuild();
            });
            if (i == 1) button(Component.literal(ownerName), x + 4, y + 18, cardWidth - 8, 18, this::chooseOwner);
        }
        int tabWidth = (panelWidth - 20) / 3;
        String[] tabs = {"configuration", "linked", "advanced"};
        for (int i = 0; i < tabs.length; i++) {
            String value = tabs[i];
            var b = addRenderableWidget(new QuestButton(tr("relay.tab." + value, monitored().size()),
                    left + 10 + i * tabWidth, top + header, tabWidth - 3, 22, () -> {
                collect(); tab = value; scroll = 0; rebuildWidgets();
            })); b.selected(tab.equals(value));
        }
        bodyPanel = panels.size(); bodyLabel = labels.size(); int first = children().size();
        if (tab.equals("configuration")) configuration();
        else if (tab.equals("linked")) linked();
        else advanced();
        for (var child : children().subList(first, children().size())) if (child instanceof AbstractWidget widget)
            widget.visible = widget.getY() >= top + bodyY && widget.getBottom() <= bodyBottom;
        int footer = top + panelHeight - 32;
        apply = button(tr("save"), left + panelWidth - 170, footer, 76, 22, this::save).selected(true);
        updateApply();
        addRenderableWidget(new QuestButton(tr("close"), left + panelWidth - 88, footer, 76, 22, this::onClose));
    }
    private JsonArray quests() { return config.getAsJsonArray("quests"); }
    private List<String> monitored() {
        if (!categoryMode) return quests().asList().stream().map(JsonElement::getAsString).toList();
        return QuestPlusClient.STATE.quests.values().stream().filter(q -> q.category().equals(config.get("category").getAsString())).map(q -> q.id()).toList();
    }
    private void mode(boolean category) {
        if (categoryMode == category) return;
        collect(); categoryMode = category; config.addProperty("category", ""); config.add("quests", new JsonArray());
        if (config.get("condition").getAsString().equals("stage")) config.addProperty("condition", "completed");
        rebuildWidgets();
    }
    private void configuration() {
        int x = left + 16, y = top + bodyY - scroll, targetWidth = narrow ? panelWidth - 32 : (panelWidth - 44) * 42 / 100;
        int right = narrow ? x : x + targetWidth + 12, rightY = narrow ? y + 284 : y;
        int rightWidth = narrow ? targetWidth : panelWidth - targetWidth - 44;
        panels.add(new Panel(x, y, targetWidth, narrow ? 272 : contentHeight, "relay.target", "relay.target_hint", "minecraft:compass"));
        int half = (targetWidth - 40) / 2;
        button(tr("relay.target_quests"), x + 16, y + 52, half, 30, () -> mode(false))
                .selected(!categoryMode).icon(net.minecraft.world.item.Items.BOOK.getDefaultInstance());
        button(tr("relay.target_category"), x + 24 + half, y + 52, half, 30, () -> mode(true))
                .selected(categoryMode).icon(net.minecraft.world.item.Items.BOOKSHELF.getDefaultInstance());
        label(tr(categoryMode ? "relay.category_mode_hint" : "relay.quest_mode_hint"), x + 16, y + 98, targetWidth - 32, QuestSkin.DIM);
        label(tr("relay.selected_target"), x + 16, y + 124, targetWidth - 32, QuestSkin.DIM);
        String category = config.get("category").getAsString();
        String selectedTitle;
        if (config.get("condition").getAsString().equals("stage")) selectedTitle = category;
        else if (categoryMode && !category.isEmpty()) selectedTitle = QuestBlockPicker.categoryTitle(category);
        else if (!categoryMode && !quests().isEmpty()) {
            var quest = QuestPlusClient.STATE.quests.get(quests().get(0).getAsString());
            selectedTitle = quest == null ? tr("relay.unavailable_quest").getString() : QuestPlusClient.STATE.text(quest.title());
        } else selectedTitle = tr("relay.no_target").getString();
        label(Component.literal(selectedTitle), x + 42, y + 146, targetWidth - 58, QuestSkin.INK);
        Component targetDetail = tr("relay.linked_count", quests().size());
        if (config.get("condition").getAsString().equals("stage")) targetDetail = tr("relay.stage_target");
        else if (categoryMode) targetDetail = tr("relay.category_count", monitored().size());
        label(targetDetail, x + 42, y + 164, targetWidth - 58, QuestSkin.DIM);
        button(tr(categoryMode ? "relay.choose_category" : "pick_quest"), x + 16, y + 198, targetWidth - 32, 26,
                categoryMode ? this::chooseCategory : this::chooseQuest).active = editable && (categoryMode || quests().size() < 32);
        button(tr(categoryMode ? "relay.view_category" : "relay.manage_linked"), x + 16, y + 236, targetWidth - 32, 22, () -> {
            collect(); tab = "linked"; scroll = 0; rebuildWidgets();
        }).active = true;
        int triggerHeight = narrow ? 216 : contentHeight - 84;
        panels.add(new Panel(right, rightY, rightWidth, triggerHeight, "relay.trigger", "relay.trigger_hint", "minecraft:comparator"));
        String[] conditions = {"unlocked", "started", "completed", "claimed", "tier", "stage"};
        int cell = (rightWidth - 48) / 3;
        for (int i = 0; i < conditions.length; i++) {
            String value = conditions[i];
            button(tr("condition." + value), right + 16 + (i % 3) * (cell + 8), rightY + 52 + (i / 3) * 32, cell, 26, () -> {
                collect(); config.addProperty("condition", value); rebuildWidgets();
            }).selected(config.get("condition").getAsString().equals(value));
        }
        int ruleY = rightY + 122;
        button(tr("relay.any"), right + 16, ruleY, (rightWidth - 40) / 2, 22, () -> {
            config.addProperty("all", false); rebuild();
        }).selected(!config.get("all").getAsBoolean());
        button(tr("relay.all"), right + 24 + (rightWidth - 40) / 2, ruleY, (rightWidth - 40) / 2, 22, () -> {
            config.addProperty("all", true); rebuild();
        }).selected(config.get("all").getAsBoolean());
        if (config.get("condition").getAsString().equals("tier")) {
            label(tr("threshold"), right + 16, rightY + 160, rightWidth - 124, QuestSkin.DIM);
            threshold = field("threshold", config.get("threshold").getAsString(), right + rightWidth - 96, rightY + 153, 80, 7);
        } else label(tr(config.get("condition").getAsString().equals("stage") ? "relay.stage_hint" : "relay.rule_hint"),
                right + 16, rightY + 160, rightWidth - 32, QuestSkin.DIM);
        button(tr("output." + config.get("output").getAsString()), right + 16, rightY + 182, rightWidth - 32, 22, () -> {
            var values = List.of("binary", "pulse", "analog");
            config.addProperty("output", values.get((values.indexOf(config.get("output").getAsString()) + 1) % values.size())); rebuild();
        });
        outputX = right; outputY = rightY + triggerHeight + 12; outputWidth = rightWidth;
        panels.add(new Panel(outputX, outputY, outputWidth, 72, "relay.redstone", "relay.redstone_hint", "minecraft:redstone"));
    }
    private void linked() {
        int x = left + 16, y = top + bodyY - scroll, w = panelWidth - 32;
        panels.add(new Panel(x, y, w, contentHeight, "relay.tab.linked", categoryMode ? "relay.category_readonly" : "relay.linked_hint", "minecraft:book"));
        var ids = monitored();
        int count = 5, pages = Math.max(1, (ids.size() + count - 1) / count);
        linkedPage = Math.clamp(linkedPage, 0, pages - 1);
        for (int i = linkedPage * count; i < Math.min(ids.size(), (linkedPage + 1) * count); i++) {
            String id = ids.get(i); var quest = QuestPlusClient.STATE.quests.get(id);
            int rowY = y + 54 + (i - linkedPage * count) * 34;
            linkedIcons.add(quest == null ? "minecraft:barrier" : quest.icon());
            label(quest == null ? tr("relay.unavailable_quest") : Component.literal(QuestPlusClient.STATE.text(quest.title())),
                    x + 42, rowY + 2, w - 148, QuestSkin.INK);
            if (quest != null) label(Component.literal(QuestBlockPicker.categoryTitle(quest.category())), x + 42, rowY + 16, w - 148, QuestSkin.DIM);
            if (!categoryMode) button(tr("relay.remove"), x + w - 94, rowY, 78, 24, () -> {
                quests().remove(quests().asList().indexOf(new JsonPrimitive(id))); rebuild();
            });
        }
        if (ids.isEmpty()) label(tr("relay.no_target"), x + 16, y + 64, w - 32, QuestSkin.DIM);
        button(tr(categoryMode ? "relay.choose_category" : "pick_quest"), x + 16, y + 234, w - 32, 24,
                categoryMode ? this::chooseCategory : this::chooseQuest).active = editable && (categoryMode || quests().size() < 32);
        var previous = addRenderableWidget(new QuestButton(Component.literal("‹"), x + 16, y + 266, 30, 22, () -> { linkedPage--; rebuild(); }));
        previous.active = linkedPage > 0;
        label(Component.literal((linkedPage + 1) + " / " + pages), x + w / 2 - 20, y + 274, 50, QuestSkin.DIM);
        var next = addRenderableWidget(new QuestButton(Component.literal("›"), x + w - 46, y + 266, 30, 22, () -> { linkedPage++; rebuild(); }));
        next.active = linkedPage < pages - 1;
    }
    private void advanced() {
        int x = left + 16, y = top + bodyY - scroll, w = panelWidth - 32;
        panels.add(new Panel(x, y, w, contentHeight, "relay.tab.advanced", "relay.advanced_hint", "minecraft:repeater"));
        label(tr("relay.stage_name"), x + 16, y + 58, w - 32, QuestSkin.INK);
        stage = field("relay.stage_name", config.get("category").getAsString(), x + 16, y + 80, w - 32, 128);
        stage.setEditable(editable && config.get("condition").getAsString().equals("stage"));
        label(tr("relay.stage_hint"), x + 16, y + 118, w - 32, QuestSkin.DIM);
        label(tr("relay.scope_hint"), x + 16, y + 154, w - 32, QuestSkin.DIM);
        label(tr("relay.owner_hint"), x + 16, y + 190, w - 32, QuestSkin.DIM);
        if (compact) {
            button(tr("scope." + config.get("scope").getAsString()), x + 16, y + 218, w - 32, 24, () -> {
                config.addProperty("scope", config.get("scope").getAsString().equals("PERSONAL") ? "TEAM" : "PERSONAL"); rebuild();
            });
            button(Component.literal(ownerName), x + 16, y + 254, w - 32, 24, this::chooseOwner);
        }
    }
    private void chooseOwner() {
        collect();
        var choices = state.getAsJsonArray("players").asList().stream().map(JsonElement::getAsJsonObject)
                .map(p -> new QuestBlockPicker.Choice(p.get("id").getAsString(), p.get("name").getAsString())).toList();
        minecraft.gui.setScreen(new QuestBlockPicker(this, tr("owner"), choices, chosen -> {
            owner = chosen; ownerName = displayOwner(chosen);
        }));
    }
    private void chooseCategory() {
        collect(); minecraft.gui.setScreen(new QuestBlockPicker(this, tr("relay.choose_category"), QuestBlockPicker.categoryChoices(), chosen -> {
            categoryMode = true; quests().asList().clear(); config.addProperty("category", chosen);
            if (config.get("condition").getAsString().equals("stage")) config.addProperty("condition", "completed");
        }));
    }
    private void chooseQuest() {
        collect();
        var choices = QuestBlockPicker.questChoices().stream().filter(c -> !quests().contains(new JsonPrimitive(c.id()))).toList();
        minecraft.gui.setScreen(new QuestBlockPicker(this, tr("relay.choose_individual"), choices, chosen -> {
            categoryMode = false;
            if (quests().size() < 32) quests().add(chosen);
            config.addProperty("category", "");
            if (config.get("condition").getAsString().equals("stage")) config.addProperty("condition", "completed");
        }));
    }
    private QuestButton button(Component title, int x, int y, int w, int h, Runnable action) {
        var button = addRenderableWidget(new QuestButton(title, x, y, w, h, action)); button.active = editable; return button;
    }
    private QuestTextField field(String key, String value, int x, int y, int w, int max) {
        var field = addRenderableWidget(new QuestTextField(font, x, y, w, 22, tr(key)));
        field.setMaxLength(max); field.setValue(value); field.setEditable(editable); return field;
    }
    private void label(Component value, int x, int y, int w, int color) { labels.add(new Label(value, x, y, w, color)); }
    private void collect() {
        if (stage != null && config.get("condition").getAsString().equals("stage")) config.addProperty("category", stage.getValue().strip());
        if (threshold != null) try { config.addProperty("threshold", Integer.parseInt(threshold.getValue())); }
        catch (NumberFormatException ignored) { config.addProperty("threshold", 0); }
    }
    private void rebuild() { collect(); rebuildWidgets(); }
    private void save() {
        collect(); ClientPlayNetworking.send(new QuestBlockNetwork.Request(snapshot.pos(), snapshot.token(), "save", config.toString(), owner, 0));
    }
    private void updateApply() {
        String category = stage != null && config.get("condition").getAsString().equals("stage") ? stage.getValue() : config.get("category").getAsString();
        apply.active = editable && (categoryMode || config.get("condition").getAsString().equals("stage") ? !category.isBlank() : !quests().isEmpty());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        if (catalogueRevision != QuestPlusClient.STATE.catalogueRevision) rebuild();
        g.fill(0, 0, width, height, 0xb0101215);
        QuestSkin.panel(g, left, top, panelWidth, panelHeight, 0xff211a17, QuestSkin.HIGHLIGHT);
        g.centeredText(font, title, left + panelWidth / 2, top + 10, QuestSkin.INK);
        g.centeredText(font, Component.literal(ownerName), left + panelWidth / 2, top + 25, QuestSkin.DIM);
        for (int index = 0; index < panels.size(); index++) {
            if (index == bodyPanel) g.enableScissor(left + 14, top + bodyY, left + panelWidth - 14, bodyBottom);
            var p = panels.get(index);
            QuestSkin.panel(g, p.x, p.y, p.width, p.height, 0xff281f19, QuestSkin.EDGE);
            if (!p.title.isEmpty()) {
                QuestBlockPicker.drawIcon(g, p.icon, p.x + 14, p.y + 14);
                g.text(font, font.plainSubstrByWidth(tr(p.title, monitored().size()).getString(), p.width - 54), p.x + 38, p.y + 10, QuestSkin.INK, false);
                var hint = font.split(tr(p.hint), p.width - 54);
                for (int i = 0; i < Math.min(2, hint.size()); i++) g.text(font, hint.get(i), p.x + 38, p.y + 24 + i * 10, QuestSkin.DIM, false);
                g.fill(p.x + 16, p.y + 43, p.x + p.width - 16, p.y + 44, QuestSkin.EDGE);
            }
        }
        for (int i = bodyLabel; i < labels.size(); i++) {
            var line = labels.get(i);
            g.text(font, fit(line.text.getString(), line.width), line.x, line.y, line.color, false);
        }
        if (tab.equals("configuration")) {
            int signal = Math.clamp(state.get("signal").getAsInt(), 0, 15);
            g.text(font, signal + " / 15", outputX + 16, outputY + 53, QuestSkin.INK, false);
            int start = outputX + 74, available = outputWidth - 90;
            for (int i = 0; i < 15; i++) g.fill(start + i * available / 15, outputY + 51, start + (i + 1) * available / 15 - 2, outputY + 63,
                    i < signal ? 0xffd09653 : 0xff4a3c30);
            String category = config.get("category").getAsString(), icon = "minecraft:book";
            var data = QuestPlusClient.STATE.categories.get(category);
            if (categoryMode && data != null && data.has("icon")) icon = data.get("icon").getAsString();
            if (!categoryMode && !quests().isEmpty()) {
                var quest = QuestPlusClient.STATE.quests.get(quests().get(0).getAsString()); if (quest != null) icon = quest.icon();
            }
            QuestBlockPicker.drawIcon(g, icon, left + 32, top + bodyY - scroll + 145);
        } else if (tab.equals("linked")) for (int i = 0; i < linkedIcons.size(); i++)
            QuestBlockPicker.drawIcon(g, linkedIcons.get(i), left + 32, top + bodyY - scroll + 58 + i * 34);
        g.disableScissor();
        for (int i = 0; i < bodyLabel; i++) { var line = labels.get(i); g.text(font, fit(line.text.getString(), line.width), line.x, line.y, line.color, false); }
        int columns = narrow ? 2 : 4, cardWidth = (panelWidth - 20 - (columns - 1) * 6) / columns;
        String team = owner.equals(state.get("owner").getAsString()) && state.has("teamName") ? state.get("teamName").getAsString() : "";
        String teamText = config.get("scope").getAsString().equals("TEAM") ? (team.isBlank() ? tr("relay.owner_team").getString() : team) : tr("relay.none").getString();
        for (int i = 2; i < (compact ? 2 : 4); i++) {
            int x = left + 17 + (i % columns) * (cardWidth + 6), y = top + 66 + (i / columns) * 44;
            g.text(font, font.plainSubstrByWidth(i == 2 ? teamText : state.get("signal").getAsInt() + " / 15", cardWidth - 14), x, y, QuestSkin.INK, false);
        }
        int view = bodyBottom - top - bodyY, maximum = contentHeight - view;
        if (maximum > 0) {
            int track = view - 8, thumb = Math.max(18, track * view / contentHeight), sy = top + bodyY + 4 + scroll * (track - thumb) / maximum;
            g.fill(left + panelWidth - 9, top + bodyY + 4, left + panelWidth - 7, bodyBottom - 4, QuestSkin.EDGE);
            g.fill(left + panelWidth - 10, sy, left + panelWidth - 6, sy + thumb, QuestSkin.HIGHLIGHT);
        }
        int footer = top + panelHeight - 32;
        g.fill(left + 10, footer - 4, left + panelWidth - 10, footer - 3, QuestSkin.EDGE);
        boolean dirty = !config.equals(state.getAsJsonObject("config")) || !owner.equals(state.get("owner").getAsString())
                || threshold != null && !threshold.getValue().equals(config.get("threshold").getAsString())
                || stage != null && !stage.getValue().equals(config.get("category").getAsString());
        var status = !state.get("error").getAsString().isEmpty() ? tr("configuration_rejected")
                : dirty ? tr("relay.unsaved") : tr("status." + state.get("status").getAsString());
        g.text(font, font.plainSubstrByWidth(status.getString(), panelWidth - 192), left + 12, footer + 7, QuestSkin.INK, false);
        updateApply(); super.extractRenderState(g, mx, my, delta);
        Component hover = null;
        for (var widget : children()) if (widget instanceof QuestButton b && b.visible && b.isHoveredOrFocused()) {
            if (font.width(b.hoverText()) > b.getWidth() - 8) hover = b.hoverText();
            break;
        }
        if (hover == null && my >= top + bodyY && my < bodyBottom) for (var line : labels)
            if (mx >= line.x && mx < line.x + line.width && my >= line.y && my < line.y + 10 && font.width(line.text) > line.width) {
                hover = line.text; break;
            }
        if (hover != null) {
            var lines = font.split(hover, Math.min(260, width - 24));
            int w = Math.min(272, width - 12), h = lines.size() * 10 + 8;
            int x = Math.clamp(mx, 4, Math.max(4, width - w - 4)), y = Math.max(4, Math.min(my + 14, height - h - 4));
            g.nextStratum(); QuestSkin.panel(g, x, y, w, h, 0xff211a17, QuestSkin.HIGHLIGHT);
            for (int i = 0; i < lines.size(); i++) g.text(font, lines.get(i), x + 5, y + 4 + i * 10, QuestSkin.INK, false);
        }
    }
    private String fit(String text, int width) {
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        int next = Math.clamp(scroll - (int) (vertical * 24), 0, Math.max(0, contentHeight - (bodyBottom - top - bodyY)));
        if (next == scroll) return super.mouseScrolled(x, y, horizontal, vertical);
        scroll = next; rebuild(); return true;
    }
    @Override public void resize(int width, int height) { collect(); super.resize(width, height); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() {
        ClientPlayNetworking.send(new QuestBlockNetwork.Request(snapshot.pos(), snapshot.token(), "close", "", "", 0)); super.onClose();
    }
}
