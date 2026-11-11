package dev.questplus.fabric.client;

import dev.questplus.compat.ClientAdapter;
import com.google.gson.*;
import dev.questplus.fabric.QuestBlockNetwork;
import dev.questplus.fabric.client.ui.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import static dev.questplus.fabric.client.QuestBlockChrome.*;

/** A local draft of the relay settings; only Apply changes the server block. */
public final class QuestRelayScreen extends Screen {
    /** A section box; {@code head} is the offset of the rule under its title and hint, 0 for none. */
    private record Panel(int x, int y, int width, int height, String title, String hint, String icon, int head) {}
    private record Label(Component text, int x, int y, int width, int color) {}
    private record Icon(String value, int x, int y) {}
    /** Tabs sit under the ink bar; the scrolling body starts under them. */
    private static final int TABS_Y = BAR + 8, BODY_Y = TABS_Y + ROW + 8, REDSTONE = 34;
    private final net.minecraft.client.gui.Font font = QuestSkin.font();
    private QuestBlockNetwork.Snapshot snapshot;
    private JsonObject state, config;
    private String owner, ownerName, tab = "configuration";
    private QuestTextField stage, threshold;
    private QuestButton apply, ownerButton;
    private final List<Panel> panels = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private final List<Icon> icons = new ArrayList<>();
    private int left, top, panelWidth, panelHeight, scroll, outputX, outputY, outputWidth, linkedPage;
    private int contentHeight, bodyBottom;
    private boolean editable, narrow, categoryMode;
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
    /** Offset of the rule under a section's title and its hint, which may take two lines. */
    private int head(String hint, int width) {
        return 31 + 11 * (Math.clamp(font.split(tr(hint), width - 38).size(), 1, 2) - 1);
    }
    @Override protected void init() {
        clearWidgets(); panels.clear(); labels.clear(); icons.clear(); stage = null; threshold = null;
        catalogueRevision = QuestPlusClient.STATE.catalogueRevision;
        // A dialog sized to the configuration tab, 460 wide; columns stack below 400.
        panelWidth = Math.min(460, width - 16); narrow = panelWidth < 400;
        int inner = panelWidth - 2 * PAD, targetWidth = narrow ? inner : (inner - 8) * 40 / 100;
        int triggerWidth = narrow ? inner : inner - targetWidth - 8;
        int target = head("relay.target_hint", targetWidth) + 6 + 132, trigger = head("relay.trigger_hint", triggerWidth) + 6 + 152;
        int configuration = ROW + 8 + (narrow ? target + 8 + trigger : Math.max(target, trigger)) + 8 + REDSTONE;
        panelHeight = Math.min(BODY_Y + configuration + 4 + FOOTER, height - 16);
        left = (width - panelWidth) / 2; top = Math.max(8, (height - panelHeight) / 2);
        bodyBottom = top + panelHeight - FOOTER - 4;
        int view = bodyBottom - top - BODY_Y;
        ownerButton = addRenderableWidget(QuestBlockChrome.owner(tr("owner_name", ownerName), left, top, panelWidth, this::chooseOwner));
        ownerButton.active = editable;
        int tabWidth = (inner - 2 * GAP) / 3;
        String[] tabs = {"configuration", "linked", "advanced"};
        for (int i = 0; i < tabs.length; i++) {
            String value = tabs[i];
            var b = addRenderableWidget(new QuestButton(tr("relay.tab." + value, monitored().size()),
                    left + PAD + i * (tabWidth + GAP), top + TABS_Y, tabWidth, ROW, () -> {
                collect(); tab = value; scroll = 0; rebuildWidgets();
            })); b.selected(tab.equals(value));
        }
        int content = switch (tab) {
            case "configuration" -> configuration;
            case "linked" -> head(categoryMode ? "relay.category_readonly" : "relay.linked_hint", inner) + 6 + 180;
            default -> head("relay.advanced_hint", inner) + 6 + 88;
        };
        contentHeight = Math.max(content, view);
        scroll = Math.clamp(scroll, 0, Math.max(0, contentHeight - view));
        int first = children().size();
        if (tab.equals("configuration")) configuration(targetWidth, triggerWidth, target, trigger);
        else if (tab.equals("linked")) linked();
        else advanced();
        for (var child : children().subList(first, children().size())) if (child instanceof AbstractWidget widget)
            widget.visible = widget.getY() >= top + BODY_Y && widget.getBottom() <= bodyBottom;
        int footer = footerY(top, panelHeight);
        apply = button(tr("save"), applyX(left, panelWidth), footer, APPLY, ROW, this::save).selected(true);
        updateApply();
        addRenderableWidget(new QuestButton(tr("close"), closeX(left, panelWidth), footer, CLOSE, ROW, this::onClose));
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
    private void configuration(int targetWidth, int triggerWidth, int targetHeight, int triggerHeight) {
        int x = left + PAD, y = top + BODY_Y - scroll, inner = panelWidth - 2 * PAD;
        // The caption says what the pair chooses: the progress scope.
        int caption = Math.min(font.width(tr("relay.scope")) + 8, inner / 4), half = (inner - caption - GAP) / 2;
        label(tr("relay.scope"), x, y + 6, caption - 8, QuestSkin.DIM);
        for (int i = 0; i < 2; i++) {
            String scope = i == 0 ? "PERSONAL" : "TEAM";
            button(tr("scope." + scope), x + caption + i * (half + GAP), y, half, ROW, () -> {
                config.addProperty("scope", scope); rebuild();
            }).selected(config.get("scope").getAsString().equals(scope));
        }
        y += ROW + 8;
        if (!narrow) targetHeight = triggerHeight = Math.max(targetHeight, triggerHeight);
        int head = head("relay.target_hint", targetWidth), c = y + head + 6, w = targetWidth - 16;
        panels.add(new Panel(x, y, targetWidth, targetHeight, "relay.target", "relay.target_hint", "minecraft:compass", head));
        int mode = (w - GAP) / 2;
        button(tr("relay.target_quests"), x + 8, c, mode, ROW, () -> mode(false))
                .selected(!categoryMode).icon(net.minecraft.world.item.Items.BOOK.getDefaultInstance());
        button(tr("relay.target_category"), x + 8 + mode + GAP, c, mode, ROW, () -> mode(true))
                .selected(categoryMode).icon(net.minecraft.world.item.Items.BOOKSHELF.getDefaultInstance());
        label(tr(categoryMode ? "relay.category_mode_hint" : "relay.quest_mode_hint"), x + 8, c + 24, w, QuestSkin.DIM);
        label(tr("relay.selected_target"), x + 8, c + 40, w, QuestSkin.DIM);
        String category = config.get("category").getAsString();
        String selectedTitle, icon = "minecraft:book";
        if (config.get("condition").getAsString().equals("stage")) selectedTitle = category;
        else if (categoryMode && !category.isEmpty()) selectedTitle = QuestBlockPicker.categoryTitle(category);
        else if (!categoryMode && !quests().isEmpty()) {
            var quest = QuestPlusClient.STATE.quests.get(quests().get(0).getAsString());
            selectedTitle = quest == null ? tr("relay.unavailable_quest").getString() : QuestPlusClient.STATE.text(quest.title());
        } else selectedTitle = tr("relay.no_target").getString();
        var data = QuestPlusClient.STATE.categories.get(category);
        if (categoryMode && data != null && data.has("icon")) icon = data.get("icon").getAsString();
        if (!categoryMode && !quests().isEmpty()) {
            var quest = QuestPlusClient.STATE.quests.get(quests().get(0).getAsString()); if (quest != null) icon = quest.icon();
        }
        icons.add(new Icon(icon, x + 8, c + 55));
        label(Component.literal(selectedTitle), x + 30, c + 54, w - 22, QuestSkin.INK);
        Component targetDetail = tr("relay.linked_count", quests().size());
        if (config.get("condition").getAsString().equals("stage")) targetDetail = tr("relay.stage_target");
        else if (categoryMode) targetDetail = tr("relay.category_count", monitored().size());
        label(targetDetail, x + 30, c + 65, w - 22, QuestSkin.DIM);
        button(tr(categoryMode ? "relay.choose_category" : "pick_quest"), x + 8, c + 80, w, ROW,
                categoryMode ? this::chooseCategory : this::chooseQuest).active = editable && (categoryMode || quests().size() < 32);
        button(tr(categoryMode ? "relay.view_category" : "relay.manage_linked"), x + 8, c + 104, w, ROW, () -> {
            collect(); tab = "linked"; scroll = 0; rebuildWidgets();
        }).active = true;
        int right = narrow ? x : x + targetWidth + 8, rightY = narrow ? y + targetHeight + 8 : y;
        head = head("relay.trigger_hint", triggerWidth); c = rightY + head + 6; w = triggerWidth - 16;
        panels.add(new Panel(right, rightY, triggerWidth, triggerHeight, "relay.trigger", "relay.trigger_hint", "minecraft:comparator", head));
        String[] conditions = {"unlocked", "started", "completed", "claimed", "tier", "stage"};
        int cell = (w - GAP) / 2;
        for (int i = 0; i < conditions.length; i++) {
            String value = conditions[i];
            button(tr("condition." + value), right + 8 + (i % 2) * (cell + GAP), c + (i / 2) * (ROW + GAP), cell, ROW, () -> {
                collect(); config.addProperty("condition", value); rebuildWidgets();
            }).selected(config.get("condition").getAsString().equals(value));
        }
        int ruleY = c + 74;
        button(tr("relay.any"), right + 8, ruleY, cell, ROW, () -> {
            config.addProperty("all", false); rebuild();
        }).selected(!config.get("all").getAsBoolean());
        button(tr("relay.all"), right + 8 + cell + GAP, ruleY, cell, ROW, () -> {
            config.addProperty("all", true); rebuild();
        }).selected(config.get("all").getAsBoolean());
        if (config.get("condition").getAsString().equals("tier")) {
            label(tr("threshold"), right + 8, c + 104, w - 92, QuestSkin.DIM);
            threshold = field("threshold", config.get("threshold").getAsString(), right + triggerWidth - 88, c + 99, 80, 7);
        } else label(tr(config.get("condition").getAsString().equals("stage") ? "relay.stage_hint" : "relay.rule_hint"),
                right + 8, c + 104, w, QuestSkin.DIM);
        button(tr("output." + config.get("output").getAsString()), right + 8, c + 124, w, ROW, () -> {
            var values = List.of("binary", "pulse", "analog");
            config.addProperty("output", values.get((values.indexOf(config.get("output").getAsString()) + 1) % values.size())); rebuild();
        });
        outputX = x; outputY = rightY + triggerHeight + 8; outputWidth = inner;
        panels.add(new Panel(outputX, outputY, outputWidth, REDSTONE, "relay.redstone", "relay.redstone_hint", "minecraft:redstone", 0));
    }
    private void linked() {
        int x = left + PAD, y = top + BODY_Y - scroll, w = panelWidth - 2 * PAD;
        String hint = categoryMode ? "relay.category_readonly" : "relay.linked_hint";
        int head = head(hint, w), c = y + head + 6;
        panels.add(new Panel(x, y, w, contentHeight, "relay.tab.linked", hint, "minecraft:book", head));
        var ids = monitored();
        int count = 5, pages = Math.max(1, (ids.size() + count - 1) / count);
        linkedPage = Math.clamp(linkedPage, 0, pages - 1);
        for (int i = linkedPage * count; i < Math.min(ids.size(), (linkedPage + 1) * count); i++) {
            String id = ids.get(i); var quest = QuestPlusClient.STATE.quests.get(id);
            int rowY = c + (i - linkedPage * count) * 30;
            icons.add(new Icon(quest == null ? "minecraft:barrier" : quest.icon(), x + 8, rowY + 4));
            int text = w - 38 - (categoryMode ? 0 : 84);
            label(quest == null ? tr("relay.unavailable_quest") : Component.literal(QuestPlusClient.STATE.text(quest.title())),
                    x + 30, rowY + 1, text, QuestSkin.INK);
            if (quest != null) label(Component.literal(QuestBlockPicker.categoryTitle(quest.category())), x + 30, rowY + 12, text, QuestSkin.DIM);
            if (!categoryMode) button(tr("relay.remove"), x + w - 8 - 76, rowY + 2, 76, ROW, () -> {
                quests().remove(quests().asList().indexOf(new JsonPrimitive(id))); rebuild();
            });
        }
        if (ids.isEmpty()) label(tr("relay.no_target"), x + 8, c + 4, w - 16, QuestSkin.DIM);
        int pager = c + 152, arrows = x + w - 8 - 2 * ROW - 48;
        button(tr(categoryMode ? "relay.choose_category" : "pick_quest"), x + 8, pager, arrows - 8 - x - 8, ROW,
                categoryMode ? this::chooseCategory : this::chooseQuest).active = editable && (categoryMode || quests().size() < 32);
        var previous = addRenderableWidget(new QuestButton(Component.literal("‹"), arrows, pager, ROW, ROW, () -> { linkedPage--; rebuild(); }));
        previous.active = linkedPage > 0;
        String number = (linkedPage + 1) + " / " + pages;
        label(Component.literal(number), arrows + ROW + 24 - font.width(number) / 2, pager + 6, 44, QuestSkin.DIM);
        var next = addRenderableWidget(new QuestButton(Component.literal("›"), arrows + ROW + 48, pager, ROW, ROW, () -> { linkedPage++; rebuild(); }));
        next.active = linkedPage < pages - 1;
    }
    private void advanced() {
        int x = left + PAD, y = top + BODY_Y - scroll, w = panelWidth - 2 * PAD;
        int head = head("relay.advanced_hint", w), c = y + head + 6;
        panels.add(new Panel(x, y, w, contentHeight, "relay.tab.advanced", "relay.advanced_hint", "minecraft:repeater", head));
        label(tr("relay.stage_name"), x + 8, c, w - 16, QuestSkin.INK);
        stage = field("relay.stage_name", config.get("category").getAsString(), x + 8, c + 12, w - 16, 128);
        stage.setEditable(editable && config.get("condition").getAsString().equals("stage"));
        label(tr("relay.stage_hint"), x + 8, c + 38, w - 16, QuestSkin.DIM);
        label(tr("relay.scope_hint"), x + 8, c + 54, w - 16, QuestSkin.DIM);
        label(tr("relay.owner_hint"), x + 8, c + 70, w - 16, QuestSkin.DIM);
    }
    private void chooseOwner() {
        collect();
        var choices = state.getAsJsonArray("players").asList().stream().map(JsonElement::getAsJsonObject)
                .map(p -> new QuestBlockPicker.Choice(p.get("id").getAsString(), p.get("name").getAsString())).toList();
        ClientAdapter.INSTANCE.setScreen(minecraft, new QuestBlockPicker(this, tr("owner"), choices, chosen -> {
            owner = chosen; ownerName = displayOwner(chosen);
        }));
    }
    private void chooseCategory() {
        collect(); ClientAdapter.INSTANCE.setScreen(minecraft, new QuestBlockPicker(this, tr("relay.choose_category"), QuestBlockPicker.categoryChoices(), chosen -> {
            categoryMode = true; quests().asList().clear(); config.addProperty("category", chosen);
            if (config.get("condition").getAsString().equals("stage")) config.addProperty("condition", "completed");
        }));
    }
    private void chooseQuest() {
        collect();
        var choices = QuestBlockPicker.questChoices().stream().filter(c -> !quests().contains(new JsonPrimitive(c.id()))).toList();
        ClientAdapter.INSTANCE.setScreen(minecraft, new QuestBlockPicker(this, tr("relay.choose_individual"), choices, chosen -> {
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
        var field = addRenderableWidget(new QuestTextField(font, x, y, w, ROW, tr(key)));
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
        int signal = Math.clamp(state.get("signal").getAsInt(), 0, 15);
        String team = owner.equals(state.get("owner").getAsString()) && state.has("teamName") ? state.get("teamName").getAsString() : "";
        String teamText = config.get("scope").getAsString().equals("TEAM") ? (team.isBlank() ? tr("relay.owner_team").getString() : team) : tr("relay.none").getString();
        String context = Component.translatableWithFallback("questplus.blocks.relay.context", "%s: %s · %s: %s",
                tr("relay.team"), teamText, tr("relay.output_now"), signal + " / 15").getString();
        window(g, font, width, height, left, top, panelWidth, panelHeight, new ItemStack(dev.questplus.fabric.QuestWorldBlocks.RELAY),
                title, context, ownerButton.getWidth() + 8);
        g.enableScissor(left + PAD, top + BODY_Y, left + panelWidth - PAD, bodyBottom);
        for (var p : panels) {
            QuestSkin.panel(g, p.x, p.y, p.width, p.height, QuestSkin.PANEL, QuestSkin.LINE);
            QuestBlockPicker.drawIcon(g, p.icon, p.x + 8, p.y + (p.head == 0 ? (p.height - 16) / 2 : 8));
            int text = p.head == 0 ? p.width * 45 / 100 - 38 : p.width - 38;
            QuestSkin.text(g, font, fit(tr(p.title, monitored().size()).getString(), text), p.x + 30, p.y + 6, QuestSkin.INK, false);
            var hint = font.getSplitter().splitLines(tr(p.hint).getString(), text, net.minecraft.network.chat.Style.EMPTY);
            int lines = p.head == 0 ? 1 : (p.head - 20) / 11;
            for (int i = 0; i < Math.min(lines, hint.size()); i++) QuestSkin.text(g, font,
                    i + 1 == lines && hint.size() > lines ? fit(hint.get(i).getString() + "…", text) : hint.get(i).getString(),
                    p.x + 30, p.y + 17 + i * 11, QuestSkin.DIM, false);
            if (p.head > 0) g.fill(p.x + 8, p.y + p.head, p.x + p.width - 8, p.y + p.head + 1, QuestSkin.EDGE);
        }
        for (var line : labels) QuestSkin.text(g, font, fit(line.text.getString(), line.width), line.x, line.y, line.color, false);
        if (tab.equals("configuration")) {
            int start = outputX + outputWidth * 45 / 100, cells = outputX + outputWidth - 8 - start - 40;
            QuestSkin.text(g, font, signal + " / 15", start, outputY + 13, QuestSkin.INK, false);
            for (int i = 0; i < 15; i++) g.fill(start + 40 + i * cells / 15, outputY + 11, start + 40 + (i + 1) * cells / 15 - 2, outputY + 23,
                    i < signal ? QuestSkin.INK : QuestSkin.LINE);
        }
        for (var icon : icons) QuestBlockPicker.drawIcon(g, icon.value, icon.x, icon.y);
        g.disableScissor();
        int view = bodyBottom - top - BODY_Y, maximum = contentHeight - view;
        if (maximum > 0) {
            int track = view - 8, thumb = Math.max(18, track * view / contentHeight), sy = top + BODY_Y + 4 + scroll * (track - thumb) / maximum;
            g.fill(left + panelWidth - 7, top + BODY_Y + 4, left + panelWidth - 5, bodyBottom - 4, QuestSkin.EDGE);
            g.fill(left + panelWidth - 8, sy, left + panelWidth - 4, sy + thumb, QuestSkin.HIGHLIGHT);
        }
        boolean dirty = !config.equals(state.getAsJsonObject("config")) || !owner.equals(state.get("owner").getAsString())
                || threshold != null && !threshold.getValue().equals(config.get("threshold").getAsString())
                || stage != null && !stage.getValue().equals(config.get("category").getAsString());
        var status = !state.get("error").getAsString().isEmpty() ? tr("configuration_rejected")
                : dirty ? tr("relay.unsaved") : tr("status." + state.get("status").getAsString());
        footer(g, font, left, top, panelWidth, panelHeight, status,
                dirty || !state.get("error").getAsString().isEmpty() ? QuestSkin.ALERT : QuestSkin.DIM, APPLY + GAP + CLOSE);
        updateApply(); super.extractRenderState(g, mx, my, delta);
        Component hover = buttonHover(children());
        if (hover == null && my >= top + BODY_Y && my < bodyBottom) for (var line : labels)
            if (mx >= line.x && mx < line.x + line.width && my >= line.y && my < line.y + 10 && font.width(line.text) > line.width) {
                hover = line.text; break;
            }
        // The header context line is the only place the team shows; a cut one hovers in full.
        int contextX = left + PAD + 22, contextRoom = ownerButton.getX() - 8 - contextX;
        if (hover == null && mx >= contextX && mx < contextX + contextRoom && my >= top + 16 && my < top + 28
                && font.width(context) > contextRoom) hover = Component.literal(context);
        hoverBox(g, font, width, height, mx, my, hover);
    }
    private String fit(String text, int width) {
        return QuestSkin.fit(font, text, width);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        int next = Math.clamp(scroll - (int) (vertical * 24), 0, Math.max(0, contentHeight - (bodyBottom - top - BODY_Y)));
        if (next == scroll) return super.mouseScrolled(x, y, horizontal, vertical);
        scroll = next; rebuild(); return true;
    }
    @Override public void resize(int width, int height) { collect(); super.resize(width, height); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() {
        ClientPlayNetworking.send(new QuestBlockNetwork.Request(snapshot.pos(), snapshot.token(), "close", "", "", 0)); super.onClose();
    }
}
