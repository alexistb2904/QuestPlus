package dev.questplus.fabric.client;

import dev.questplus.platform.ClientPlatform;
import dev.questplus.compat.ClientAdapter;
import com.google.gson.*;
import dev.questplus.core.QuestDefinition;
import dev.questplus.fabric.QuestBlockNetwork;
import dev.questplus.fabric.client.ui.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import java.util.*;
import static dev.questplus.fabric.client.QuestBlockChrome.*;

/** A local station draft. Progress and resource balances always come from the server. */
public final class QuestStationScreen extends Screen {
    private final net.minecraft.client.gui.Font font = QuestSkin.font();
    private QuestBlockNetwork.Snapshot snapshot;
    private JsonObject state, config, pending;
    private String owner, pendingOwner;
    private boolean dirty, editable, ownerSettings;
    /** Section box heights. Offsets below are from the window top. */
    private static final int QUEST_BOX = 138, BUFFER_BOX = 34, AUTOMATION_BOX = 68, SIDES_BOX = 80;
    private int left, top, panelWidth, panelHeight, scroll, columnWidth, rightWidth, right, questY, settingsY, bufferY;
    private QuestButton returnItems, apply, ownerButton;

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
        // A dialog sized to its content: 440 wide, two columns, stacked below 400.
        panelWidth = Math.min(440, width - 16); boolean narrow = panelWidth < 400;
        int inner = panelWidth - 2 * PAD;
        columnWidth = narrow ? inner : (inner - 8) * 55 / 100;
        rightWidth = narrow ? inner : inner - columnWidth - 8;
        int body = BAR + ROW + 16;
        questY = body; bufferY = body + QUEST_BOX + 8;
        settingsY = narrow ? bufferY + BUFFER_BOX + 8 : body;
        int bodyEnd = narrow ? settingsY + AUTOMATION_BOX + 8 + SIDES_BOX : bufferY + BUFFER_BOX;
        panelHeight = ownerSettings ? BAR + 92 + 6 + FOOTER : bodyEnd + 6 + FOOTER;
        scroll = Math.clamp(scroll, 0, Math.max(0, panelHeight + 16 - height));
        left = (width - panelWidth) / 2; top = Math.max(8, (height - panelHeight) / 2) - scroll;
        right = narrow ? left + PAD : left + PAD + columnWidth + 8;
        ownerButton = addRenderableWidget(QuestBlockChrome.owner(t("owner", ownerName()), left, top, panelWidth,
                () -> { ownerSettings = !ownerSettings; scroll = 0; rebuildWidgets(); }));
        ownerButton.active = editable;
        int x = left + PAD;
        if (ownerSettings) {
            var ownerField = addRenderableWidget(new QuestTextField(font, x, top + BAR + 24, inner, ROW, QuestBlockScreen.tr("owner")));
            ownerField.setMaxLength(36); ownerField.setValue(owner); ownerField.setEditable(editable);
            ownerField.setResponder(value -> { owner = value.strip(); dirty = modified(); if (apply != null) apply.active = editable && dirty && objective() != null; });
            button(t("choose_player"), x, top + BAR + 48, inner, ROW, () -> {
                var choices = state.getAsJsonArray("players").asList().stream().map(JsonElement::getAsJsonObject)
                        .map(p -> new QuestBlockPicker.Choice(p.get("id").getAsString(), p.get("name").getAsString())).toList();
                ClientAdapter.INSTANCE.setScreen(minecraft, new QuestBlockPicker(this, t("choose_player"), choices, id -> { owner = id; changed(); }));
            });
            button(t("back"), x, top + BAR + 72, inner, ROW, () -> { ownerSettings = false; rebuildWidgets(); });
        } else {
            int half = (inner - GAP) / 2;
            for (int i = 0; i < 2; i++) {
                String scope = i == 0 ? "PERSONAL" : "TEAM";
                button(QuestBlockScreen.tr("scope." + scope), x + i * (half + GAP), top + BAR + 8, half, ROW,
                        () -> { config.addProperty("scope", scope); changed(); }).selected(config.get("scope").getAsString().equals(scope));
            }
            var q = quest(); var o = objective();
            int boxX = x + 8, boxWidth = columnWidth - 16, qy = top + questY;
            button(q == null ? t("choose_quest") : Component.literal(QuestPlusClient.STATE.text(q.title())),
                    boxX, qy + 18, boxWidth, 22, this::pickQuest).icon(itemIcon(q == null ? "minecraft:book" : q.icon()));
            var chooseObjective = button(o == null ? t(q == null ? "choose_objective" : "no_deposit_objective") : Component.literal(objectiveTitle(o)),
                    boxX, qy + 70, boxWidth, 22, this::pickObjective);
            chooseObjective.icon(itemIcon(o == null ? "minecraft:hopper" : icon(o)));
            chooseObjective.active = editable && q != null && q.objectives().stream().anyMatch(QuestStationScreen::consuming);
            int sy = top + settingsY, settingsInner = rightWidth - 16;
            toggle("automation", right + 8, sy + 18, settingsInner);
            toggle("advanceTiers", right + 8, sy + 18 + ROW + GAP, settingsInner);
            int sides = sy + AUTOMATION_BOX + 8, cell = (settingsInner - 2 * GAP) / 3;
            for (int i = 0; i < 6; i++) {
                var side = Direction.values()[i];
                int mask = 1 << side.ordinal();
                button(QuestBlockScreen.tr("face." + side.getName()), right + 8 + i % 3 * (cell + GAP),
                        sides + 30 + i / 3 * (ROW + GAP), cell, ROW,
                        () -> { config.addProperty("faces", config.get("faces").getAsInt() ^ mask); changed(); })
                        .selected((config.get("faces").getAsInt() & mask) != 0);
            }
            int returnWidth = Math.min(boxWidth - 64, font.width(t("return_items")) + 16);
            returnItems = button(t("return_items"), x + columnWidth - 8 - returnWidth, top + bufferY + 7, returnWidth, ROW, () -> send("return_items"));
            returnItems.active = editable && state.get("items").getAsInt() > 0;
        }
        int bottom = footerY(top, panelHeight);
        apply = button(QuestBlockScreen.tr("save"), applyX(left, panelWidth), bottom, APPLY, ROW, () -> send("save"));
        apply.selected(true); apply.active = editable && dirty && objective() != null;
        addRenderableWidget(new QuestButton(QuestBlockScreen.tr("close"), closeX(left, panelWidth), bottom, CLOSE, ROW, this::onClose));
    }
    private QuestButton button(Component label, int x, int y, int w, int h, Runnable action) {
        var b = addRenderableWidget(new QuestButton(label, x, y, w, h, action)); b.active = editable; return b;
    }
    private void toggle(String key, int x, int y, int w) {
        boolean on = config.get(key).getAsBoolean();
        button(QuestBlockScreen.tr(key, QuestBlockScreen.tr(on ? "yes" : "no")), x, y, w, ROW,
                () -> { config.addProperty(key, !on); changed(); }).selected(on);
    }
    private void pickQuest() {
        var choices = QuestBlockPicker.questChoices();
        ClientAdapter.INSTANCE.setScreen(minecraft, new QuestBlockPicker(this, t("choose_quest"), choices, id -> {
            var ids = new JsonArray(); ids.add(id); config.add("quests", ids);
            config.addProperty("objective", QuestPlusClient.STATE.quests.get(id).objectives().stream()
                    .filter(QuestStationScreen::consuming).map(QuestDefinition.Objective::id).findFirst().orElse("")); changed();
        }));
    }
    private void pickObjective() {
        var q = quest(); if (q == null) return;
        var choices = q.objectives().stream().filter(QuestStationScreen::consuming)
                .map(o -> new QuestBlockPicker.Choice(o.id(), objectiveTitle(o), icon(o), "")).toList();
        ClientAdapter.INSTANCE.setScreen(minecraft, new QuestBlockPicker(this, t("choose_objective"), choices,
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
        ClientPlatform.send(new QuestBlockNetwork.Request(snapshot.pos(), snapshot.token(), action, config.toString(), owner, 0));
    }
    private void text(GuiGraphicsExtractor g, Component value, int x, int y, int w, int color) {
        QuestSkin.text(g, font, QuestSkin.fit(font, value.getString(), w), x, y, color, false);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        // The saved team, which the scope pair below does not show. Nothing otherwise, so the title centres.
        boolean team = state.getAsJsonObject("config").get("scope").getAsString().equals("TEAM");
        window(g, font, width, height, left, top, panelWidth, panelHeight, new ItemStack(dev.questplus.fabric.QuestWorldBlocks.STATION),
                title, team && state.has("teamName") && !state.get("teamName").getAsString().isEmpty()
                        ? QuestBlockScreen.tr("relay.team").getString() + " · " + state.get("teamName").getAsString() : "",
                ownerButton.getWidth() + 8);
        int x = left + PAD;
        if (ownerSettings) {
            text(g, t("owner_uuid"), x, top + BAR + 10, panelWidth - 2 * PAD, QuestSkin.INK);
        } else {
            int qy = top + questY, by = top + bufferY, sy = top + settingsY, w = columnWidth - 16;
            QuestSkin.panel(g, x, qy, columnWidth, QUEST_BOX, QuestSkin.PANEL, QuestSkin.LINE);
            text(g, t("quest"), x + 8, qy + 6, w, QuestSkin.HIGHLIGHT);
            text(g, t("objective"), x + 8, qy + 58, w, QuestSkin.HIGHLIGHT);
            var q = quest(); var o = objective();
            text(g, q == null ? t("choose_hint") : Component.literal(QuestBlockPicker.categoryTitle(q.category())),
                    x + 8, qy + 44, w, QuestSkin.DIM);
            var objective = row(1); var quest = row(0);
            String count = objective == null ? "—" : objective.get("count").getAsString();
            text(g, t("progress", count), x + 8, qy + 98, w, QuestSkin.INK);
            bar(g, x + 8, qy + 110, w, objective == null ? 0 : objective.get("fraction").getAsDouble());
            text(g, t("quest_progress", quest == null ? "—" : quest.get("progress").getAsString()),
                    x + 8, qy + 123, w, QuestSkin.DIM);
            QuestSkin.panel(g, x, by, columnWidth, BUFFER_BOX, QuestSkin.PANEL, QuestSkin.LINE);
            int amountsWidth = w - returnItems.getWidth() - 2 * GAP;
            text(g, t("buffer"), x + 8, by + 6, amountsWidth, QuestSkin.HIGHLIGHT);
            var amounts = new ArrayList<String>();
            if (state.get("items").getAsInt() > 0 || o != null && o.event().equals("item_turn_in")) amounts.add(state.get("items").getAsInt() + " " + t("items").getString());
            if (state.get("fluid").getAsLong() > 0 || o != null && o.event().equals("fluid_submit")) amounts.add(state.get("fluid").getAsLong() + " mB");
            if (state.get("energy").getAsLong() > 0 || o != null && o.event().equals("energy_submit")) amounts.add(state.get("energy").getAsLong() + " FE");
            text(g, Component.literal(amounts.isEmpty() ? "—" : String.join(" · ", amounts)), x + 8, by + 18, amountsWidth, QuestSkin.INK);
            QuestSkin.panel(g, right, sy, rightWidth, AUTOMATION_BOX, QuestSkin.PANEL, QuestSkin.LINE);
            text(g, t("automation"), right + 8, sy + 6, rightWidth - 16, QuestSkin.HIGHLIGHT);
            int sides = sy + AUTOMATION_BOX + 8;
            QuestSkin.panel(g, right, sides, rightWidth, SIDES_BOX, QuestSkin.PANEL, QuestSkin.LINE);
            text(g, t("input_sides"), right + 8, sides + 6, rightWidth - 16, QuestSkin.HIGHLIGHT);
            text(g, t("selected_sides"), right + 8, sides + 17, rightWidth - 16, QuestSkin.DIM);
        }
        var questProgress = row(0);
        var status = !state.get("error").getAsString().isEmpty() ? QuestBlockScreen.tr("configuration_rejected")
                : !ownerSettings && quest() != null && objective() == null ? t("no_deposit_hint")
                : dirty ? t("unsaved") : questProgress != null && questProgress.get("fraction").getAsDouble() >= 1
                ? Component.translatable("questplus.client.status.completed") : QuestBlockScreen.tr("status." + state.get("status").getAsString());
        footer(g, font, left, top, panelWidth, panelHeight, status,
                dirty || !state.get("error").getAsString().isEmpty() ? QuestSkin.ALERT : QuestSkin.DIM, APPLY + GAP + CLOSE);
        super.extractRenderState(g, mx, my, delta);
        hoverBox(g, font, width, height, mx, my, buttonHover(children()));
        int maximum = Math.max(0, panelHeight + 16 - height);
        if (scroll < maximum) {
            int sx = left + panelWidth + 2, track = height - 16;
            int thumb = Math.max(12, track * track / panelHeight), y = 8 + (track - thumb) * scroll / maximum;
            g.fill(sx, 8, sx + 4, height - 8, 0xff6b5236);
            g.fill(sx, y, sx + 4, y + thumb, QuestSkin.FIELD);
        }
    }
    static void bar(GuiGraphicsExtractor g, int x, int y, int w, double fraction) {
        QuestSkin.panel(g, x, y, w, 9, QuestSkin.FIELD, QuestSkin.INK);
        int fill = (int)Math.round((w - 4) * Math.clamp(fraction, 0, 1));
        for (int i = 0; i < w - 4; i += 9) {
            int end = Math.min(i + 7, w - 4);
            g.fill(x + 2 + i, y + 2, x + 2 + end, y + 7, QuestSkin.LINE);
            if (i < fill) g.fill(x + 2 + i, y + 2, x + 2 + Math.min(end, fill), y + 7, fraction >= 1 ? QuestSkin.DONE : QuestSkin.ACTIVE);
        }
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        int next = Math.clamp(scroll - (int)(vertical * 24), 0, Math.max(0, panelHeight + 16 - height));
        if (next == scroll) return super.mouseScrolled(x, y, horizontal, vertical);
        scroll = next; rebuildWidgets(); return true;
    }
    @Override public void onClose() {
        ClientPlatform.send(new QuestBlockNetwork.Request(snapshot.pos(), snapshot.token(), "close", "", "", 0));
        super.onClose();
    }
    @Override public boolean isPauseScreen() { return false; }
}
