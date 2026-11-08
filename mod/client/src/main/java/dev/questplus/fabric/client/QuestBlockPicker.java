package dev.questplus.fabric.client;

import com.google.gson.JsonObject;
import dev.questplus.fabric.QuestBlockNetwork;
import dev.questplus.fabric.client.ui.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import java.util.*;
import java.util.function.Consumer;

/** Category-first catalogue; identifiers are kept only as selection values. */
final class QuestBlockPicker extends Screen {
    record Choice(String id, String title, String icon, String category) {
        Choice(String id, String title) { this(id, title, "", ""); }
    }
    private record Icon(String value, int x, int y) {}
    private final Screen parent;
    private final List<Choice> choices;
    private final Consumer<String> selected;
    private final List<Icon> icons = new ArrayList<>();
    private QuestTextField search;
    private int page, left, top, panelWidth, panelHeight;
    private String category = "", query = "";
    private boolean browsingCategories;
    private Component heading, empty;

    QuestBlockPicker(Screen parent, List<Choice> choices, Consumer<String> selected) {
        this(parent, QuestBlockScreen.tr("pick_quest"), choices, selected);
    }
    QuestBlockPicker(Screen parent, Component title, List<Choice> choices, Consumer<String> selected) {
        super(title); this.parent = parent; this.choices = choices; this.selected = selected;
    }
    static List<Choice> questChoices() {
        return QuestPlusClient.STATE.quests.values().stream().map(q -> new Choice(q.id(),
                QuestPlusClient.STATE.text(q.title()), q.icon(), q.category())).toList();
    }
    static List<Choice> categoryChoices() {
        return QuestPlusClient.STATE.categories.entrySet().stream().map(e -> new Choice(e.getKey(),
                categoryTitle(e.getKey()), e.getValue().has("icon") ? e.getValue().get("icon").getAsString() : "minecraft:book", "")).toList();
    }
    static String categoryTitle(String id) {
        JsonObject data = QuestPlusClient.STATE.categories.get(id);
        if (data == null || !data.has("title")) return QuestBlockScreen.tr("relay.other_category").getString();
        Map<String, String> names = new HashMap<>();
        data.getAsJsonObject("title").entrySet().forEach(e -> names.put(e.getKey(), e.getValue().getAsString()));
        return QuestPlusClient.STATE.text(names);
    }
    boolean receive(QuestBlockNetwork.Snapshot packet) {
        if (parent instanceof QuestStationScreen station) station.receive(packet);
        else if (parent instanceof QuestBoardScreen board) board.receive(packet);
        else if (parent instanceof QuestRelayScreen relay) relay.receive(packet);
        else if (parent instanceof QuestBlockScreen block) block.receive(packet);
        else return false;
        return true;
    }
    @Override protected void init() {
        panelWidth = width - 40; panelHeight = height - 40;
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        search = new QuestTextField(font, left + 10, top + 28, panelWidth - 20, 21, QuestBlockScreen.tr("relay.search"));
        search.setMaxLength(128); search.setValue(query);
        search.setHint(QuestBlockScreen.tr("relay.search"));
        search.setResponder(value -> { query = value; page = 0; rows(); }); rows();
    }
    private void rows() {
        clearWidgets(); icons.clear(); addRenderableWidget(search);
        String text = query.strip().toLowerCase(Locale.ROOT);
        boolean grouped = choices.stream().anyMatch(c -> !c.category.isEmpty());
        browsingCategories = grouped && category.isEmpty() && text.isEmpty();
        List<Choice> filtered;
        if (browsingCategories) {
            var ids = new LinkedHashSet<String>();
            for (String id : QuestPlusClient.STATE.categories.keySet())
                if (choices.stream().anyMatch(c -> c.category.equals(id))) ids.add(id);
            choices.forEach(c -> ids.add(c.category));
            filtered = ids.stream().map(id -> {
                var data = QuestPlusClient.STATE.categories.get(id);
                return new Choice(id, categoryTitle(id), data != null && data.has("icon") ? data.get("icon").getAsString() : "minecraft:book", "");
            }).toList();
        } else filtered = choices.stream().filter(c -> (category.isEmpty() || c.category.equals(category))
                && (c.title.toLowerCase(Locale.ROOT).contains(text)
                || !c.category.isEmpty() && categoryTitle(c.category).toLowerCase(Locale.ROOT).contains(text))).toList();
        heading = browsingCategories ? QuestBlockScreen.tr("relay.choose_category")
                : category.isEmpty() ? title : Component.literal(categoryTitle(category));
        int count = Math.max(1, (panelHeight - 104) / 30), pages = Math.max(1, (filtered.size() + count - 1) / count);
        page = Math.clamp(page, 0, pages - 1);
        for (int i = page * count; i < Math.min(filtered.size(), (page + 1) * count); i++) {
            var choice = filtered.get(i); int y = top + 66 + (i - page * count) * 30;
            boolean icon = !choice.icon.isEmpty();
            if (icon) icons.add(new Icon(choice.icon, left + 13, y + 5));
            addRenderableWidget(new QuestButton(Component.literal(choice.title), left + (icon ? 34 : 10), y,
                    panelWidth - (icon ? 44 : 20), 26, () -> {
                if (browsingCategories) { category = choice.id; page = 0; rows(); }
                else { selected.accept(choice.id); minecraft.gui.setScreen(parent); }
            }));
        }
        empty = filtered.isEmpty() ? QuestBlockScreen.tr("relay.no_results") : Component.empty();
        int bottom = top + panelHeight - 28;
        var previous = addRenderableWidget(new QuestButton(Component.literal("‹"), left + 10, bottom, 28, 20,
                () -> { page--; rows(); })); previous.active = page > 0;
        addRenderableWidget(new QuestButton(category.isEmpty() ? QuestBlockScreen.tr("close") : QuestBlockScreen.tr("relay.back_categories"),
                left + 44, bottom, panelWidth - 88, 20, this::onClose));
        var next = addRenderableWidget(new QuestButton(Component.literal("›"), left + panelWidth - 38, bottom, 28, 20,
                () -> { page++; rows(); })); next.active = page < pages - 1;
    }
    @Override public void onClose() {
        if (!category.isEmpty()) { category = ""; page = 0; query = ""; search.setValue(""); rows(); }
        else minecraft.gui.setScreen(parent);
    }
    static void drawIcon(GuiGraphicsExtractor g, String value, int x, int y) {
        if (RichAnnotationRenderer.drawIcon(g, value, x, y, 16)) return;
        var id = Identifier.tryParse(value);
        g.item(id != null && BuiltInRegistries.ITEM.containsKey(id)
                ? BuiltInRegistries.ITEM.getValue(id).getDefaultInstance() : Items.BOOK.getDefaultInstance(), x, y);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xb0101215);
        QuestSkin.panel(g, left, top, panelWidth, panelHeight, 0xff211a17, QuestSkin.EDGE);
        g.centeredText(font, title, left + panelWidth / 2, top + 10, QuestSkin.INK);
        g.text(font, font.plainSubstrByWidth(heading.getString(), panelWidth - 20), left + 10, top + 54, QuestSkin.DIM, false);
        g.centeredText(font, empty, left + panelWidth / 2, top + 87, QuestSkin.DIM);
        super.extractRenderState(g, mx, my, delta);
        for (var icon : icons) drawIcon(g, icon.value, icon.x, icon.y);
    }
    @Override public boolean isPauseScreen() { return false; }
}
