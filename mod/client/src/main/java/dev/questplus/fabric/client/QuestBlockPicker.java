package dev.questplus.fabric.client;

import dev.questplus.compat.ClientAdapter;
import com.google.gson.JsonObject;
import dev.questplus.fabric.QuestBlockNetwork;
import dev.questplus.fabric.client.ui.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;
import java.util.function.Consumer;
import static dev.questplus.fabric.client.QuestBlockChrome.*;

/** Category-first catalogue. Identifiers are kept only as selection values. */
final class QuestBlockPicker extends Screen {
    record Choice(String id, String title, String icon, String category) {
        Choice(String id, String title) { this(id, title, "", ""); }
    }
    private record Icon(String value, int x, int y) {}
    /** Rows are a control tall with a gap between them. The dialog never grows past seven. */
    private static final int PITCH = ROW + GAP, ROWS_Y = BAR + 8 + ROW + GAP, CAP = 18;
    private final net.minecraft.client.gui.Font font = QuestSkin.font();
    private final Screen parent;
    private final List<Choice> choices;
    private final Consumer<String> selected;
    private final List<Icon> icons = new ArrayList<>();
    private QuestTextField search;
    private int page, pages, left, top, panelWidth, panelHeight, count;
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
        // A dialog sized to its rows, not to the window: about 300 by 268 at most.
        panelWidth = Math.min(300, width - 16);
        count = Math.clamp((height - 16 - ROWS_Y - FOOTER - 6 + GAP) / PITCH, 1, 7);
        panelHeight = ROWS_Y + count * PITCH - GAP + 6 + FOOTER;
        left = (width - panelWidth) / 2; top = Math.max(8, (height - panelHeight) / 2);
        // The field starts after a search cap drawn by this screen, so it reads as an input, not a row.
        search = new QuestTextField(font, left + PAD + CAP - 4, top + BAR + 8, panelWidth - 2 * PAD - CAP + 4, ROW, QuestBlockScreen.tr("relay.search"));
        search.setMaxLength(128); search.setValue(query); search.setCentered(false);
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
        pages = Math.max(1, (filtered.size() + count - 1) / count);
        page = Math.clamp(page, 0, pages - 1);
        for (int i = page * count; i < Math.min(filtered.size(), (page + 1) * count); i++) {
            var choice = filtered.get(i); int y = top + ROWS_Y + (i - page * count) * PITCH;
            var row = addRenderableWidget(new QuestButton(Component.literal(choice.title), left + PAD, y, panelWidth - 2 * PAD, ROW, () -> {
                if (browsingCategories) { category = choice.id; page = 0; rows(); }
                else { selected.accept(choice.id); ClientAdapter.INSTANCE.setScreen(minecraft, parent); }
            }));
            if (!choice.icon.isEmpty()) {
                row.icon(item(choice.icon));
                // Pack images take the item's place inside the row. See extractRenderState.
                if (choice.icon.startsWith("data:")) icons.add(new Icon(choice.icon, row.getX() + 3, y + (ROW - 12) / 2));
            }
        }
        empty = filtered.isEmpty() ? QuestBlockScreen.tr("relay.no_results") : Component.empty();
        int bottom = footerY(top, panelHeight);
        Component back = category.isEmpty() ? QuestBlockScreen.tr("close") : QuestBlockScreen.tr("relay.back_categories");
        int backWidth = Math.min(panelWidth - 2 * PAD - (pages > 1 ? 2 * ROW + 48 : 0), Math.max(CLOSE, font.width(back) + 16));
        addRenderableWidget(new QuestButton(back, left + panelWidth - PAD - backWidth, bottom, backWidth, ROW, this::onClose));
        if (pages > 1) {
            var previous = addRenderableWidget(new QuestButton(Component.literal("‹"), left + PAD, bottom, ROW, ROW,
                    () -> { page--; rows(); })); previous.active = page > 0;
            var next = addRenderableWidget(new QuestButton(Component.literal("›"), left + PAD + ROW + 40, bottom, ROW, ROW,
                    () -> { page++; rows(); })); next.active = page < pages - 1;
        }
    }
    @Override public void onClose() {
        if (!category.isEmpty()) { category = ""; page = 0; query = ""; search.setValue(""); rows(); }
        else ClientAdapter.INSTANCE.setScreen(minecraft, parent);
    }
    private static ItemStack item(String value) {
        var id = Identifier.tryParse(value);
        return id != null && BuiltInRegistries.ITEM.containsKey(id)
                ? BuiltInRegistries.ITEM.getValue(id).getDefaultInstance() : Items.BOOK.getDefaultInstance();
    }
    static void drawIcon(GuiGraphicsExtractor g, String value, int x, int y) {
        if (RichAnnotationRenderer.drawIcon(g, value, x, y, 16)) return;
        g.item(item(value), x, y);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        window(g, font, width, height, left, top, panelWidth, panelHeight, null, title,
                heading.getString().equals(title.getString()) ? "" : heading.getString(), 0);
        int sx = left + PAD, sy = top + BAR + 8, edge = search.isFocused() ? QuestSkin.INK : QuestSkin.LINE;
        QuestSkin.panel(g, sx, sy, CAP + 2, ROW, QuestSkin.FIELD, edge);
        // A magnifier: ring and handle.
        int gx = sx + 5, gy = sy + 6;
        g.fill(gx + 1, gy, gx + 5, gy + 1, QuestSkin.DIM); g.fill(gx + 1, gy + 5, gx + 5, gy + 6, QuestSkin.DIM);
        g.fill(gx, gy + 1, gx + 1, gy + 5, QuestSkin.DIM); g.fill(gx + 5, gy + 1, gx + 6, gy + 5, QuestSkin.DIM);
        for (int i = 0; i < 3; i++) g.fill(gx + 5 + i, gy + 5 + i, gx + 7 + i, gy + 6 + i, QuestSkin.DIM);
        QuestSkin.centered(g, font, empty, left + panelWidth / 2, top + ROWS_Y + 6, QuestSkin.DIM);
        int bottom = footerY(top, panelHeight);
        g.fill(left + PAD, bottom - 6, left + panelWidth - PAD, bottom - 5, QuestSkin.LINE);
        if (pages > 1) QuestSkin.centered(g, font, Component.literal((page + 1) + " / " + pages), left + PAD + ROW + 20, bottom + 6, QuestSkin.DIM);
        super.extractRenderState(g, mx, my, delta);
        // Join the cap to the field: hide the field's own left edge.
        int fx = sx + CAP - 4;
        g.fill(fx, sy + 1, fx + 2, sy + ROW - 1, QuestSkin.FIELD);
        g.fill(fx, sy, fx + 2, sy + 1, edge); g.fill(fx, sy + ROW - 1, fx + 2, sy + ROW, edge);
        if (search.getValue().isEmpty() && !search.isFocused())
            QuestSkin.text(g, font, QuestSkin.fit(font, QuestBlockScreen.tr("relay.search").getString(), search.getWidth()),
                    search.getX(), search.getY() + (search.getHeight() - 8) / 2, QuestSkin.DIM, false);
        for (var icon : icons) {
            g.fill(icon.x, icon.y, icon.x + 12, icon.y + 12, QuestSkin.FIELD);
            if (RichAnnotationRenderer.drawIcon(g, icon.value, icon.x, icon.y, 12)) continue;
            // Not a decodable PNG: put back the book the row drew under the cover.
            g.pose().pushMatrix(); g.pose().translate(icon.x, icon.y); g.pose().scale(.75f);
            g.item(Items.BOOK.getDefaultInstance(), 0, 0); g.pose().popMatrix();
        }
        hoverBox(g, font, width, height, mx, my, buttonHover(children()));
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // The magnifier is drawn as part of the field, so it focuses the field too.
        int sx = left + PAD, sy = top + BAR + 8;
        if (event.x() >= sx && event.x() < sx + CAP && event.y() >= sy && event.y() < sy + ROW) {
            setFocused(search); return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean isPauseScreen() { return false; }
}
