package dev.questplus.fabric.client;

import com.google.gson.*;
import dev.questplus.core.QuestDefinition;
import dev.questplus.fabric.QuestBlockNetwork;
import dev.questplus.fabric.client.ui.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import java.util.*;

/** A local station draft; progress and resource balances always come from the server. */
public final class QuestStationScreen extends Screen {
    private QuestBlockNetwork.Snapshot snapshot;
    private JsonObject state, config, pending;
    private String owner, pendingOwner;
    private boolean dirty, editable, ownerSettings;
    private int left, top, panelWidth, panelHeight, scroll, columnWidth, right, settingsY, bufferY;
    private QuestButton returnItems, apply;

    public QuestStationScreen(QuestBlockNetwork.Snapshot packet) { super(t("title")); receive(packet); }
    static Component t(String key, Object... args) { return Component.translatable("questplus.station." + key, args); }
    public void receive(QuestBlockNetwork.Snapshot packet) {
        var next = JsonParser.parseString(packet.data()).getAsJsonObject();
        boolean acknowledged = pending != null && pending.equals(next.getAsJsonObject("config"))
                && pendingOwner.equals(next.get("owner").getAsString()) && pending.equals(config) && pendingOwner.equals(owner);
        boolean permissionsChanged = state != null && editable != next.get("editable").getAsBoolean();
        boolean configurationChanged = !dirty && config != null && (!config.equals(next.getAsJsonObject("config"))
                || !owner.equals(next.get("owner").getAsString()));
        snapshot = packet; state = next; editable = state.get("editable").getAsBoolean();
        if (config == null || acknowledged || !dirty) {
            config = state.getAsJsonObject("config").deepCopy(); owner = state.get("owner").getAsString();
            dirty = false; pending = null;
        }
        if (width > 0 && (acknowledged || permissionsChanged || configurationChanged)) rebuildWidgets();
        if (returnItems != null) returnItems.active = editable && state.get("items").getAsInt() > 0;
    }
    private QuestDefinition quest() {
        var ids = config.getAsJsonArray("quests");
        return ids.isEmpty() ? null : QuestPlusClient.STATE.quests.get(ids.get(0).getAsString());
    }
    private QuestDefinition.Objective objective() {
        var q = quest(); return q == null ? null : q.objectives().stream()
                .filter(o -> o.id().equals(config.get("objective").getAsString())).findFirst().orElse(null);
    }
    private static boolean consuming(QuestDefinition.Objective o) {
        return Set.of("item_turn_in", "fluid_submit", "energy_submit").contains(o.event());
    }
    static String objectiveTitle(QuestDefinition.Objective o) {
        if (!o.text().isEmpty()) return QuestPlusClient.STATE.text(o.text());
        return t("deliver", QuestObjectiveNames.target(o)).getString();
    }
    private boolean modified() { return !config.equals(state.getAsJsonObject("config")) || !owner.equals(state.get("owner").getAsString()); }
    private void changed() { dirty = modified(); rebuildWidgets(); }
    @Override protected void init() {
        clearWidgets(); returnItems = null;
        panelWidth = Math.min(520, width - 16); boolean narrow = panelWidth < 440;
        panelHeight = ownerSettings ? 232 : narrow ? 600 : 370;
        scroll = Math.clamp(scroll, 0, Math.max(0, panelHeight + 16 - height));
        left = (width - panelWidth) / 2; top = Math.max(8, (height - panelHeight) / 2) - scroll;
        int inner = panelWidth - 24;
        button(t("owner", ownerName()), left + panelWidth - 166, top + 12, 154, 22,
                () -> { ownerSettings = !ownerSettings; scroll = 0; rebuildWidgets(); });
        if (ownerSettings) {
            var ownerField = addRenderableWidget(new QuestTextField(font, left + 12, top + 78, inner, 22, QuestBlockScreen.tr("owner")));
            ownerField.setMaxLength(36); ownerField.setValue(owner); ownerField.setEditable(editable);
            ownerField.setResponder(value -> { owner = value.strip(); dirty = modified(); if (apply != null) apply.active = editable && dirty && objective() != null; });
            button(t("choose_player"), left + 12, top + 110, inner, 24, () -> {
                var choices = state.getAsJsonArray("players").asList().stream().map(JsonElement::getAsJsonObject)
                        .map(p -> new QuestBlockPicker.Choice(p.get("id").getAsString(), p.get("name").getAsString())).toList();
                minecraft.gui.setScreen(new QuestBlockPicker(this, t("choose_player"), choices, id -> { owner = id; changed(); }));
            });
            button(t("back"), left + 12, top + 142, inner, 22, () -> { ownerSettings = false; rebuildWidgets(); });
        } else {
            int half = (inner - 6) / 2;
            for (int i = 0; i < 2; i++) {
                String scope = i == 0 ? "PERSONAL" : "TEAM";
                button(QuestBlockScreen.tr("scope." + scope), left + 12 + i * (half + 6), top + 44, half, 24,
                        () -> { config.addProperty("scope", scope); changed(); }).selected(config.get("scope").getAsString().equals(scope));
            }
            columnWidth = narrow ? inner : (inner - 12) * 56 / 100;
            right = narrow ? left + 12 : left + 24 + columnWidth;
            settingsY = top + (narrow ? 334 : 80); bufferY = top + 264;
            int settingsWidth = narrow ? inner : inner - columnWidth - 12;
            var q = quest(); var o = objective();
            button(q == null ? t("choose_quest") : Component.literal(QuestPlusClient.STATE.text(q.title())),
                    left + 20, top + 104, columnWidth - 16, 34, this::pickQuest).icon(itemIcon(q == null ? "minecraft:book" : q.icon()));
            var chooseObjective = button(o == null ? t(q == null ? "choose_objective" : "no_deposit_objective") : Component.literal(objectiveTitle(o)),
                    left + 20, top + 168, columnWidth - 16, 34, this::pickObjective);
            chooseObjective.icon(itemIcon(o == null ? "minecraft:hopper" : icon(o)));
            chooseObjective.active = editable && q != null && q.objectives().stream().anyMatch(QuestStationScreen::consuming);
            toggle("automation", right + 8, settingsY + 25, settingsWidth - 16);
            toggle("advanceTiers", right + 8, settingsY + 55, settingsWidth - 16);
            for (int i = 0; i < 6; i++) {
                var side = Direction.values()[i]; int cell = (settingsWidth - 20) / 3;
                int mask = 1 << side.ordinal();
                button(QuestBlockScreen.tr("face." + side.getName()), right + 8 + i % 3 * (cell + 2),
                        settingsY + 139 + i / 3 * 26, cell, 24,
                        () -> { config.addProperty("faces", config.get("faces").getAsInt() ^ mask); changed(); })
                        .selected((config.get("faces").getAsInt() & mask) != 0);
            }
            returnItems = button(t("return_items"), left + 20, bufferY + 23, columnWidth - 16, 20, () -> send("return_items"));
            returnItems.active = editable && state.get("items").getAsInt() > 0;
        }
        int bottom = top + panelHeight - 30;
        apply = button(QuestBlockScreen.tr("save"), left + panelWidth - 178, bottom, 88, 22, () -> send("save"));
        apply.selected(true); apply.active = editable && dirty && objective() != null;
        addRenderableWidget(new QuestButton(QuestBlockScreen.tr("close"), left + panelWidth - 84, bottom, 72, 22, this::onClose));
    }
    private QuestButton button(Component label, int x, int y, int w, int h, Runnable action) {
        var b = addRenderableWidget(new QuestButton(label, x, y, w, h, action)); b.active = editable; return b;
    }
    private void toggle(String key, int x, int y, int w) {
        boolean on = config.get(key).getAsBoolean();
        button(QuestBlockScreen.tr(key, QuestBlockScreen.tr(on ? "yes" : "no")), x, y, w, 24,
                () -> { config.addProperty(key, !on); changed(); }).selected(on);
    }
    private void pickQuest() {
        var choices = QuestBlockPicker.questChoices();
        minecraft.gui.setScreen(new QuestBlockPicker(this, t("choose_quest"), choices, id -> {
            var ids = new JsonArray(); ids.add(id); config.add("quests", ids);
            config.addProperty("objective", QuestPlusClient.STATE.quests.get(id).objectives().stream()
                    .filter(QuestStationScreen::consuming).map(QuestDefinition.Objective::id).findFirst().orElse("")); changed();
        }));
    }
    private void pickObjective() {
        var q = quest(); if (q == null) return;
        var choices = q.objectives().stream().filter(QuestStationScreen::consuming)
                .map(o -> new QuestBlockPicker.Choice(o.id(), objectiveTitle(o), icon(o), "")).toList();
        minecraft.gui.setScreen(new QuestBlockPicker(this, t("choose_objective"), choices,
                id -> { config.addProperty("objective", id); changed(); }));
    }
    private static String icon(QuestDefinition.Objective o) {
        return switch (o.event()) { case "fluid_submit" -> o.target() + "_bucket"; case "energy_submit" -> "minecraft:redstone";
            default -> o.target(); };
    }
    private static ItemStack itemIcon(String value) {
        var id = Identifier.tryParse(value);
        return id != null && BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.getValue(id).getDefaultInstance() : Items.BOOK.getDefaultInstance();
    }
    private String ownerName() {
        for (var value : state.getAsJsonArray("players")) {
            var p = value.getAsJsonObject(); if (p.get("id").getAsString().equals(owner)) return p.get("name").getAsString();
        }
        return owner.equals(state.get("owner").getAsString()) && state.has("ownerName")
                ? state.get("ownerName").getAsString() : t("other_player").getString();
    }
    private boolean linked() {
        var saved = state.getAsJsonObject("config");
        return owner.equals(state.get("owner").getAsString()) && config.get("scope").equals(saved.get("scope"))
                && config.get("quests").equals(saved.get("quests")) && config.get("objective").equals(saved.get("objective"));
    }
    private JsonObject row(int index) {
        var rows = state.getAsJsonArray("rows"); return linked() && rows.size() > index ? rows.get(index).getAsJsonObject() : null;
    }
    private void send(String action) {
        if (action.equals("save")) { pending = config.deepCopy(); pendingOwner = owner; }
        ClientPlayNetworking.send(new QuestBlockNetwork.Request(snapshot.pos(), snapshot.token(), action, config.toString(), owner, 0));
    }
    private void text(GuiGraphicsExtractor g, Component value, int x, int y, int w, int color) {
        g.text(font, font.plainSubstrByWidth(value.getString(), w), x, y, color, false);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xb0101215);
        QuestSkin.panel(g, left, top, panelWidth, panelHeight, 0xff211a17, QuestSkin.EDGE);
        text(g, title, left + 12, top + 18, panelWidth - 190, QuestSkin.INK);
        if (ownerSettings) {
            text(g, t("owner_uuid"), left + 12, top + 58, panelWidth - 24, QuestSkin.INK);
        } else {
            int settingsWidth = panelWidth - 24 - (right - left - 12);
            QuestSkin.panel(g, left + 12, top + 80, columnWidth, 174, 0xff2b201a, QuestSkin.EDGE);
            text(g, t("quest"), left + 22, top + 90, columnWidth - 20, QuestSkin.HIGHLIGHT);
            text(g, t("objective"), left + 22, top + 154, columnWidth - 20, QuestSkin.HIGHLIGHT);
            var q = quest(); var o = objective();
            text(g, q == null ? t("choose_hint") : Component.literal(QuestBlockPicker.categoryTitle(q.category())),
                    left + 22, top + 141, columnWidth - 20, QuestSkin.DIM);
            var objective = row(1); var quest = row(0);
            String count = objective == null ? "—" : objective.get("count").getAsString();
            text(g, t("progress", count), left + 22, top + 216, columnWidth - 20, QuestSkin.INK);
            bar(g, left + 22, top + 232, columnWidth - 20, objective == null ? 0 : objective.get("fraction").getAsDouble());
            text(g, t("quest_progress", quest == null ? "—" : quest.get("progress").getAsString()),
                    left + 22, top + 244, columnWidth - 20, QuestSkin.DIM);
            text(g, t("buffer"), left + 20, bufferY + 2, columnWidth - 16, QuestSkin.HIGHLIGHT);
            var amounts = new ArrayList<String>();
            if (state.get("items").getAsInt() > 0 || o != null && o.event().equals("item_turn_in")) amounts.add(state.get("items").getAsInt() + " " + t("items").getString());
            if (state.get("fluid").getAsLong() > 0 || o != null && o.event().equals("fluid_submit")) amounts.add(state.get("fluid").getAsLong() + " mB");
            if (state.get("energy").getAsLong() > 0 || o != null && o.event().equals("energy_submit")) amounts.add(state.get("energy").getAsLong() + " FE");
            text(g, Component.literal(amounts.isEmpty() ? "—" : String.join(" · ", amounts)), left + 20, bufferY + 13, columnWidth - 16, QuestSkin.INK);
            QuestSkin.panel(g, right, settingsY, settingsWidth, 91, 0xff2b201a, QuestSkin.EDGE);
            text(g, t("automation"), right + 8, settingsY + 10, settingsWidth - 16, QuestSkin.HIGHLIGHT);
            QuestSkin.panel(g, right, settingsY + 103, settingsWidth, 95, 0xff2b201a, QuestSkin.EDGE);
            text(g, t("input_sides"), right + 8, settingsY + 114, settingsWidth - 16, QuestSkin.HIGHLIGHT);
            text(g, t("selected_sides"), right + 8, settingsY + 126, settingsWidth - 16, QuestSkin.DIM);
        }
        var questProgress = row(0);
        var status = !state.get("error").getAsString().isEmpty() ? QuestBlockScreen.tr("configuration_rejected")
                : !ownerSettings && quest() != null && objective() == null ? t("no_deposit_hint")
                : dirty ? t("unsaved") : questProgress != null && questProgress.get("fraction").getAsDouble() >= 1
                ? Component.translatable("questplus.client.status.completed") : QuestBlockScreen.tr("status." + state.get("status").getAsString());
        text(g, status, left + 12, top + panelHeight - 47, panelWidth - 24,
                state.get("error").getAsString().isEmpty() ? QuestSkin.DIM : 0xffffa08b);
        super.extractRenderState(g, mx, my, delta);
        int maximum = Math.max(0, panelHeight + 16 - height);
        if (scroll < maximum) {
            int x = left + panelWidth + 2, track = height - 16;
            int thumb = Math.max(12, track * track / panelHeight), y = 8 + (track - thumb) * scroll / maximum;
            g.fill(x, 8, x + 4, height - 8, 0xff453c32);
            g.fill(x, y, x + 4, y + thumb, QuestSkin.HIGHLIGHT);
        }
    }
    static void bar(GuiGraphicsExtractor g, int x, int y, int w, double fraction) {
        QuestSkin.panel(g, x, y, w, 9, 0xff171713, QuestSkin.EDGE);
        int fill = (int)Math.round((w - 4) * Math.clamp(fraction, 0, 1));
        for (int i = 0; i < w - 4; i += 9) {
            int end = Math.min(i + 7, w - 4);
            g.fill(x + 2 + i, y + 2, x + 2 + end, y + 7, 0xff453c32);
            if (i < fill) g.fill(x + 2 + i, y + 2, x + 2 + Math.min(end, fill), y + 7, 0xffd4ad70);
        }
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        int next = Math.clamp(scroll - (int)(vertical * 24), 0, Math.max(0, panelHeight + 16 - height));
        if (next == scroll) return super.mouseScrolled(x, y, horizontal, vertical);
        scroll = next; rebuildWidgets(); return true;
    }
    @Override public void onClose() {
        ClientPlayNetworking.send(new QuestBlockNetwork.Request(snapshot.pos(), snapshot.token(), "close", "", "", 0));
        super.onClose();
    }
    @Override public boolean isPauseScreen() { return false; }
}
