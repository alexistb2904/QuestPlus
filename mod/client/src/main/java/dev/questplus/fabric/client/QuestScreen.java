package dev.questplus.fabric.client;

import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import dev.questplus.clientmodel.*;
import dev.questplus.core.QuestDefinition;
import dev.questplus.clientdata.QuestPresentation;
import dev.questplus.team.TeamRole;
import dev.questplus.fabric.client.ui.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import java.util.*;
import static dev.questplus.fabric.client.QuestPlusClient.*;

public final class QuestScreen extends Screen {
	private record ViewState(Map<String, QuestCamera> cameras, String category, String selected, String tab,
			String filter, String query, int detailsScroll, int categoryScroll, int teamPage,
			Set<String> collapsedGroups) {
	}

	private static ViewState savedView;
	private final net.minecraft.client.gui.Font font = QuestSkin.font();
	private final QuestClientState state = STATE;
	private final Map<String, QuestCamera> cameras = new HashMap<>();
	private final Map<String, QuestLayout> layouts = new HashMap<>();
	private final Map<String, ItemStack> icons = new HashMap<>();
	private QuestLayout layout = QuestLayout.compute(List.of());
	private Map<QuestLayout.Edge, List<QuestLayout.Point>> routes = Map.of();
	private QuestCamera camera = new QuestCamera(QuestCamera.MIN_ZOOM, QuestCamera.MAX_ZOOM);
	private String category, selected, tab = "quests", filter = "all", confirmation;
	private String submitObjective, manualObjective;
	private boolean rewardView, potentialRewards;
	private int rewardPage, selectedOption = -1, choiceSlot = -1, playerScroll;
	private boolean playerPicker;
	private boolean teamOptions;
	private String choiceToken;
	private long pendingChoiceAt, rewardRevealAt;
	private String rewardRevealKey;
	private String rewardTitle = "", rewardDescription = "";

	private record RewardCard(JsonObject reward, String label) {
	}

	private List<RewardCard> rewardCards = List.of();

	private record CategoryRow(String group, JsonObject category) {
	}

	private final Set<String> collapsedGroups = new HashSet<>();
	private List<CategoryRow> categoryRows = List.of();
	private long categoryRowsRevision = -1;
	private final Map<String, Integer> categoryBadges = new HashMap<>();
	private long badgeRevision = -1;
	private int badgeNewUnlocks = -1;

	/**
	 * Index category markers on progress deltas, never scan all quests per rendered
	 * category.
	 */
	private void updateCategoryBadges() {
		if (badgeRevision == state.hudRevision && badgeNewUnlocks == QuestPlusClient.NEW_UNLOCKS.size())
			return;
		categoryBadges.clear();
		for (var q : state.quests.values()) {
			int flags = (QuestClientState.flag(state.progress(q.id()), "claimable") ? 1 : 0)
					| (QuestPlusClient.NEW_UNLOCKS.contains(q.id()) ? 2 : 0);
			if (flags != 0)
				categoryBadges.merge(q.category(), flags, (a, b) -> a | b);
		}
		badgeRevision = state.hudRevision;
		badgeNewUnlocks = QuestPlusClient.NEW_UNLOCKS.size();
	}

	private List<CategoryRow> categoryRows() {
		if (categoryRowsRevision == state.catalogueRevision)
			return categoryRows;
		var grouped = new LinkedHashMap<String, List<JsonObject>>();
		for (var value : state.categories.values())
			grouped.computeIfAbsent(value.has("group") ? value.get("group").getAsString() : "",
					ignored -> new ArrayList<>()).add(value);
		var rows = new ArrayList<CategoryRow>();
		grouped.forEach((name, values) -> {
			if (!name.isBlank())
				rows.add(new CategoryRow(name, null));
			if (!collapsedGroups.contains(name))
				for (var value : values)
					rows.add(new CategoryRow(name, value));
		});
		categoryRows = List.copyOf(rows);
		categoryRowsRevision = state.catalogueRevision;
		return categoryRows;
	}

	private EditBox search, input;
	private String query = "";
	private int left, top, right, bottom, treeLeft, treeTop, treeRight, treeBottom, detailsScroll, categoryScroll,
			teamPage;
	private boolean dragging, dragged;
	private double downX, downY;
	private long catalogue = -1, sequence = -1;
	private JsonObject lastTeam;
	private JsonArray lastInvitations;
	private final long opened = System.nanoTime();
	private QuestButton questAction, claimAllButton;
	private final List<QuestButton> hoverButtons = new ArrayList<>();
	private String hoverTitle;
	private String pendingManualQuest;
	private long pendingManualAt;
	private String currentAction;
	private boolean overlayDetails;
	private int detailLeft, panelTop, railTop, journalScroll, detailsContentTop;

	private record JournalRow(String id, String title, String status, double fraction) {
		int height() {
			return 20;
		}
	}

	private List<JournalRow> journal = List.of();
	private long journalSequence = -2, journalCatalogue = -2;
	private String journalLanguage, journalCategory, journalQuery;
	private final Set<String> filtered = new HashSet<>();
	private final Map<String, String> titles = new HashMap<>(), statuses = new HashMap<>();
	private List<String> visibleNodes = List.of();
	private List<QuestLayout.Edge> visibleEdges = List.of();
	private long filteredSequence = -2, filteredCatalogue = -2;
	private String filteredLanguage, filteredFilter;
	private double cachedLeft = Double.NaN, cachedTop, cachedRight, cachedBottom;

	private record DetailRow(List<String> lines, ItemStack icon, String progress, List<FormattedCharSequence> formatted,
			JsonObject rich, int richHeight, boolean recipe, boolean heading, double fraction) {
		DetailRow(List<String> lines, ItemStack icon, String progress) {
			this(lines, icon, progress, null, null, 0, false, false, -1);
		}

		DetailRow(List<String> lines, ItemStack icon, String progress, List<FormattedCharSequence> formatted) {
			this(lines, icon, progress, formatted, null, 0, false, false, -1);
		}

		int lineCount() {
			return formatted == null ? lines.size() : formatted.size();
		}
	}

	private List<DetailRow> detailLines = List.of();
	private final List<QuestRecipeViewer.Target> recipeTargets = new ArrayList<>();
	private String detailQuest, detailLanguage;
	private long detailSequence = -2;
	private int detailWidth;
	private long closingAt;
	private float visualOffset;
	private boolean attachedToScreenFace;
	// Keep the same 500×300 widget space in 3D and scaled 2D; never resize widgets.
	private double screenScale = 1, screenOffsetX, screenOffsetY;
	private Double pendingInterfaceScale;
	private String keyboardQuest;
	private long completedNodes;

	public double animationProgress() {
		if (QuestClientTabletFlow.nativeHandsActive())
			return 1;
		if (!settings.animations || settings.reducedMotion)
			return closingAt == 0 ? 1 : 0;
		double p = Math.clamp((System.nanoTime() - (closingAt == 0 ? opened : closingAt)) / 250_000_000.0, 0, 1);
		p = p * p * (3 - 2 * p);
		return closingAt == 0 ? p : 1 - p;
	}

	@Override
	public void onClose() {
		if (QuestClientTabletFlow.nativeHandsActive()) {
			QuestClientTabletFlow.close();
			super.onClose();
		} else if (!settings.animations || settings.reducedMotion)
			super.onClose();
		else if (closingAt == 0)
			closingAt = System.nanoTime();
	}

	public QuestScreen() {
		super(Component.literal("Quest Plus"));
		if (savedView != null) {
			cameras.putAll(savedView.cameras());
			category = savedView.category();
			selected = savedView.selected();
			tab = savedView.tab();
			filter = savedView.filter();
			query = savedView.query();
			detailsScroll = savedView.detailsScroll();
			categoryScroll = savedView.categoryScroll();
			teamPage = savedView.teamPage();
			collapsedGroups.addAll(savedView.collapsedGroups());
		}
	}

	@Override
	public void removed() {
		if (state.ready)
			savedView = new ViewState(new HashMap<>(cameras), category, selected, tab, filter, query, detailsScroll,
					categoryScroll, teamPage, Set.copyOf(collapsedGroups));
		super.removed();
	}

	static void clearSavedView() {
		savedView = null;
	}

	private Component t(String key, Object... args) {
		return tr(key, args);
	}

	private QuestButton button(String key, int x, int y, int w, Runnable action) {
		QuestButton result = addRenderableWidget(new QuestButton(t(key), x, y, w, 15, action));
		hoverButtons.add(result);
		return result;
	}

	@Override
	protected void init() {
		recipeTargets.clear();
		QuestResourceTheme.reload();
		// Glyph widths follow the resource pack and GUI scale: wrap rich notes again.
		RichAnnotationRenderer.clearLayouts();
		attachedToScreenFace = TabletSurfaceRenderer.active();
		if (attachedToScreenFace) {
			width = Math.max(1, TabletSurfaceRenderer.logicalWidth());
			height = Math.max(1, TabletSurfaceRenderer.logicalHeight());
			screenScale = 1;
			screenOffsetX = 0;
			screenOffsetY = 0;
		} else {
			int hostWidth = Math.max(1, minecraft.getWindow().getGuiScaledWidth());
			int hostHeight = Math.max(1, minecraft.getWindow().getGuiScaledHeight());
			width = TabletUiViewport.WIDTH;
			height = TabletUiViewport.HEIGHT;
			var viewport = TabletUiViewport.fit(hostWidth, hostHeight, settings.scale);
			screenScale = viewport.scale();
			screenOffsetX = viewport.offsetX();
			screenOffsetY = viewport.offsetY();
		}
		left = 0;
		top = 0;
		right = width;
		bottom = height;
		int fw = width, fh = height;
		// The right panel opens only for a selected quest or a search; otherwise the chart
		// takes the whole width.
		boolean narrow = fw < 480, panel = sidebarOpen();
		overlayDetails = panel && narrow;
		treeLeft = left + 38; // the category rail
		treeTop = top + 75;
		treeRight = narrow || !panel ? right - 4 : right - 168;
		treeBottom = bottom - 4;
		detailLeft = (overlayDetails ? treeLeft : treeRight) + 8;
		panelTop = top + 27;
		railTop = top + 29;
		cachedLeft = Double.NaN;
		questAction = null;
		claimAllButton = null;
		hoverButtons.clear();
		// Typing must survive a rebuild: the new search field takes over focus and caret.
		int searchCursor = search != null && getFocused() == search && search.isFocused() ? search.getCursorPosition()
				: -1;
		clearFocus();
		clearWidgets();
		int tabWidth = Math.min(76, (fw - 60) / 3), tabStep = tabWidth + 6;
		// The tabs sit on the ink bar, 6px apart, and end flush with it: the selected one joins the page.
		// GameTests click (45,13), (125,13) and (205,13); each stays inside its tab.
		button("quests", left + 5, top + 8, tabWidth, () -> switchTab("quests")).selected(tab.equals("quests")).tab();
		button("team", left + 5 + tabStep, top + 8, tabWidth, () -> switchTab("team")).selected(tab.equals("team"))
				.tab();
		button("settings", left + 5 + tabStep * 2, top + 8, tabWidth, () -> switchTab("settings"))
				.selected(tab.equals("settings")).tab();
		button("close", right - 20, top + 4, 15, this::onClose).onBar().setMessage(Component.literal("×"));
		if (tab.equals("quests")) {
			int searchX = left + 5 + tabStep * 3 + 6;
			int searchWidth = right - searchX - 30;
			search = addRenderableWidget(new QuestTextField(font, searchX, top + 4, searchWidth, 15, t("search")));
			search.setMaxLength(128);
			search.setHint(t("search"));
			search.setValue(query);
			search.setResponder(this::searchChanged);
			if (searchCursor >= 0) {
				setFocused(search);
				search.setCursorPosition(searchCursor);
				search.setHighlightPos(search.getCursorPosition());
			}
			if (!overlayDetails) {
				// One chip per status: the current filter is always visible and one click away.
				var filters = List.of("all", "available", "active", "completed");
				int room = treeRight - treeLeft - 58, chipX = treeLeft + 2;
				boolean fits = filters.stream().mapToInt(f -> font.width(t("filter." + f)) + 12).sum() <= room;
				for (String f : filters) {
					int chip = fits ? font.width(t("filter." + f)) + 10 : room / 4 - 2;
					button("filter." + f, chipX, top + 58, chip, () -> {
						filter = f;
						init();
					}).selected(filter.equals(f));
					chipX += chip + 2;
				}
				button("zoom_out", treeRight - 51, top + 58, 15, () -> camera.zoomAt(1 / QuestCamera.ZOOM_STEP,
						(treeLeft + treeRight) / 2.0, (treeTop + treeBottom) / 2.0)).setMessage(Component.literal("−"));
				button("zoom_in", treeRight - 34, top + 58, 15, () -> camera.zoomAt(QuestCamera.ZOOM_STEP,
						(treeLeft + treeRight) / 2.0, (treeTop + treeBottom) / 2.0)).setMessage(Component.literal("+"));
				button("center_tree", treeRight - 17, top + 58, 15, this::fit).setMessage(Component.literal("o"));
				// Claim all lives in the category band, so it stays one click away with the panel closed.
				int claimWidth = font.width(t("claim_all")) + 12;
				QuestButton claimAll = button("claim_all", treeRight - 6 - claimWidth, top + 32, claimWidth,
						() -> action("claim_all", ""));
				claimAllButton = claimAll.accent(true);
				updateClaimAll();
			}
			if (selected != null && state.quests.containsKey(selected))
				detailsButtons();
		} else if (tab.equals("team"))
			teamWidgets();
		else
			settingsWidgets();
	}

	private void switchTab(String next) {
		tab = next;
		confirmation = null;
		init();
	}

	/** The right panel shows the selected quest, else the search results; with neither it is closed. */
	private boolean sidebarOpen() {
		return selected != null || !query.isEmpty();
	}

	/** Laid out by the last init(): beside the chart, or over it on a narrow screen. */
	private boolean panelShown() {
		return overlayDetails || treeRight < right - 4;
	}

	private void searchChanged(String value) {
		// EditBox reports empty inserts (e.g. pasting an empty clipboard) as changes too.
		if (value.equals(query))
			return;
		boolean wasOpen = sidebarOpen(), dropDetails = selected != null && !value.isEmpty();
		query = value;
		journalScroll = 0;
		// Typing asks for the results list; rebuild only when the panel opens, closes or swaps.
		if (dropDetails) {
			selected = null;
			rewardView = false;
		}
		if (wasOpen != sidebarOpen() || dropDetails)
			init();
	}

	private void select(String id, boolean centre) {
		var quest = state.quests.get(id);
		if (quest == null)
			return;
		if (!Objects.equals(quest.category(), category))
			chooseCategory(quest.category());
		// A search result may be one the status chip hides; show every quest rather than open a blank spot.
		String s = status(id);
		if (!filter.equals("all") && !s.equals(filter) && !(filter.equals("completed") && s.equals("reward_ready")))
			filter = "all";
		selected = id;
		rewardView = false;
		detailsScroll = 0;
		init();
		// Pan, never refit: keep the quest inside the chart the open panel leaves.
		var p = layout.points().get(id);
		if (p == null)
			return;
		double sx = camera.screenX(p.x()), sy = camera.screenY(p.y());
		double r = nodeRadius(quest) * camera.zoom() + 6;
		boolean fits = 2 * r < treeRight - treeLeft && 2 * r < treeBottom - treeTop;
		double tx = centre || !fits ? (treeLeft + treeRight) / 2.0 : Math.clamp(sx, treeLeft + r, treeRight - r);
		double ty = centre || !fits ? (treeTop + treeBottom) / 2.0 : Math.clamp(sy, treeTop + r, treeBottom - r);
		camera.pan(tx - sx, ty - sy);
	}

	private void refreshCatalogue() {
		if (catalogue == state.catalogueRevision)
			return;
		catalogue = state.catalogueRevision;
		layouts.clear();
		icons.clear();
		if (!state.categories.containsKey(category))
			category = state.categories.keySet().stream().findFirst().orElse(null);
		if (!state.quests.containsKey(selected))
			selected = null;
		chooseCategory(category);
		init();
	}

	private void chooseCategory(String id) {
		category = id;
		QuestPlusClient.NEW_UNLOCKS
				.removeIf(qid -> state.quests.containsKey(qid) && Objects.equals(state.quests.get(qid).category(), id));
		filteredCatalogue = -2;
		layout = layouts.computeIfAbsent(Objects.toString(id, ""), ignored -> QuestLayout
				.compute(state.quests.values().stream().filter(q -> Objects.equals(q.category(), id)).toList()));
		Map<QuestLayout.Edge, List<QuestLayout.Point>> selectedRoutes = new HashMap<>();
		var data = state.categories.get(id);
		if (data != null && data.has("linkRoutes"))
			for (var entry : data.getAsJsonArray("linkRoutes")) {
				var route = entry.getAsJsonObject();
				List<QuestLayout.Point> points = new ArrayList<>();
				for (var point : route.getAsJsonArray("points")) {
					var value = point.getAsJsonObject();
					points.add(new QuestLayout.Point(value.get("x").getAsDouble(), value.get("y").getAsDouble()));
				}
				selectedRoutes.put(new QuestLayout.Edge(route.get("from").getAsString(), route.get("to").getAsString()),
						List.copyOf(points));
			}
		routes = Map.copyOf(selectedRoutes);
		boolean fresh = !cameras.containsKey(id);
		camera = cameras.computeIfAbsent(id, ignored -> new QuestCamera(QuestCamera.MIN_ZOOM, QuestCamera.MAX_ZOOM));
		if (fresh)
			fit(true);
	}

	private void fit() {
		fit(false);
	}

	private void fit(boolean initial) {
		// Fit what is drawn, tightly: every quest marker and every annotation's own box.
		// One wide annotation must not pad the whole chart and shrink the quests.
		List<QuestLayout.Point> points = new ArrayList<>(layout.points().values());
		double padX = 20, padY = 20;
		for (var quest : state.quests.values())
			if (Objects.equals(quest.category(), category)) {
				padX = Math.max(padX, quest.size() / 2.0 + 4);
				padY = Math.max(padY, quest.size() / 2.0 + 4);
			}
		var data = state.categories.get(category);
		if (data != null && data.has("annotations")) {
			for (var item : data.getAsJsonArray("annotations")) {
				if (!item.isJsonObject())
					continue;
				var note = item.getAsJsonObject();
				if (!note.has("x") || !note.has("y"))
					continue;
				double halfW = Math.max(0, (note.has("width") ? note.get("width").getAsDouble() : 170) / 2 - padX);
				double halfH = Math.max(0, (note.has("height") ? note.get("height").getAsDouble() : 100) / 2 - padY);
				points.add(new QuestLayout.Point(note.get("x").getAsDouble() - halfW, note.get("y").getAsDouble() - halfH));
				points.add(new QuestLayout.Point(note.get("x").getAsDouble() + halfW, note.get("y").getAsDouble() + halfH));
			}
		}
		if (initial)
			camera.fitAll(points, treeLeft, treeTop, treeRight - treeLeft, treeBottom - treeTop, padX, padY);
		else
			camera.fitBounds(points, treeLeft, treeTop, treeRight - treeLeft, treeBottom - treeTop, padX, padY);
	}

	@Override
	public void tick() {
		if (closingAt != 0 && animationProgress() <= 0) {
			super.onClose();
			return;
		}
		// Rebuild after the click event, not in the middle of widget dispatch.
		// The setting and the viewport must change together in one tick.
		if (pendingInterfaceScale != null) {
			double next = pendingInterfaceScale;
			pendingInterfaceScale = null;
			settings.scale = next;
			settings.save();
			init();
		}
		refreshCatalogue();
		if (sequence != state.sequence) {
			sequence = state.sequence;
			updateClaimAll();
			if (tab.equals("team")
					&& (!Objects.equals(lastTeam, state.team) || !Objects.equals(lastInvitations, state.invitations))) {
				confirmation = null;
				playerPicker = false;
				init();
			}
			else if (tab.equals("quests") && selected != null) {
				if (rewardView)
					init();
				else
					updateQuestAction();
			}
		}
		if (pendingChoiceAt != 0 && (state.error != null || System.currentTimeMillis() - pendingChoiceAt > 5000)) {
			pendingChoiceAt = 0;
			if (rewardView)
				init();
			sync();
		}
		if (pendingManualQuest != null && System.currentTimeMillis() - pendingManualAt > 2500L) {
			pendingManualQuest = null;
			updateQuestAction();
		}
	}

	private String status(String id) {
		return QuestPresentation.status(state.quests.get(id), state.progress(id));
	}

	private boolean shown(String id) {
		return filtered.contains(id);
	}

	private int color(String id) {
		int fallback = statusColor(statuses.get(id));
		String property = switch (statuses.get(id)) {
		case "completed", "reward_ready" -> "quest_completed_color";
		case "active" -> "quest_started_color";
		case "available" -> "quest_not_started_color";
		default -> "quest_locked_color";
		};
		return QuestResourceTheme.color(state.quests.get(id), property, fallback);
	}

	private static int statusColor(String status) {
		return switch (status) {
		case "completed" -> QuestSkin.DONE;
		case "reward_ready" -> QuestSkin.CLAIM;
		case "active" -> QuestSkin.ACTIVE;
		case "available" -> QuestSkin.FIELD;
		default -> QuestSkin.PANEL;
		};
	}

	private ItemStack icon(String id) {
		return icons.computeIfAbsent(id, key -> {
			var resource = Identifier.tryParse(key); // A visible barrier item is the fallback for unknown IDs (e.g. a
														// 26.4 item on 26.3).
			// Never resolve an unknown ID to AIR or paint a blank item slot.
			return resource != null && BuiltInRegistries.ITEM.containsKey(resource)
					? new ItemStack(BuiltInRegistries.ITEM.getValue(resource))
					: new ItemStack(Items.BARRIER);
		});
	}

	private void drawIcon(GuiGraphicsExtractor graphics, String id, int x, int y) {
		if (!RichAnnotationRenderer.drawIcon(graphics, id, x, y, 16))
			graphics.item(icon(id), x, y);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
		// Preserve the real first-person tablet frame; an opaque GUI backdrop would
		// hide it.
		if (QuestClientTabletFlow.nativeHandsActive())
			return;
		if (TabletShaderCompat.backgroundBlurEnabled() && settings.blurIntensity > 0)
			extractBlurredBackground(g);
		g.fill(0, 0, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(), 0x660d100e);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		if (attachedToScreenFace)
			return;
		g.pose().pushMatrix();
		try {
			g.pose().translate((float) screenOffsetX, (float) screenOffsetY);
			g.pose().scale((float) screenScale);
			int ux = (int) Math.round((mx - screenOffsetX) / screenScale);
			int uy = (int) Math.round((my - screenOffsetY) / screenScale);
			extractTablet(g, ux, uy, delta);
		} finally {
			g.pose().popMatrix();
		}
		QuestToast.draw(g);
	}

	void extractTablet(GuiGraphicsExtractor g, int mx, int my, float delta) {
		hoverTitle = null;
		visualOffset = QuestClientTabletFlow.nativeHandsActive() ? 0
				: (float) ((1 - animationProgress()) * (height - top));
		g.pose().pushMatrix();
		g.pose().translate(0, visualOffset);
		my -= (int) visualOffset;
		if (settings.tablet) {
			// FP PAL already draws the arms and 3D tablet; do not render them twice.
			if (!QuestClientTabletFlow.nativeHandsActive()) {
				// Only the 2D fallback needs a frame; the native tablet supplies its own.
				QuestSkin.panel(g, left - 5, top - 5, right - left + 10, bottom - top + 10, 0xff211914, 0xff9a6c4b);
				g.fill(left - 3, top - 3, right + 3, bottom + 3, 0xff191815);
			}
		} else {
			g.fill(left - 5, top - 5, right + 5, bottom + 5, 0xff342820);
			g.outline(left - 5, top - 5, right - left + 10, bottom - top + 10, 0xff756553);
		}
		if (attachedToScreenFace)
			g.enableScissor(left, top, right, bottom);
		QuestSkin.panel(g, left, top, right - left, bottom - top, QuestSkin.PAPER, QuestSkin.INK);
		g.fill(left + 1, top + 1, right - 1, top + 23, QuestSkin.INK);
		if (state.ready && tab.equals("quests")) {
			updateRenderCache();
			drawCategoryHeader(g, mx, my);
		}
		if (!state.ready) {
			QuestSkin.centered(g, font, t(state.error == null ? "loading" : "error." + state.error), (left + right) / 2,
					(top + bottom) / 2 - 24, QuestSkin.INK);
			if (state.errorDetail != null) {
				int ey = (top + bottom) / 2 - 8, available = Math.max(80, right - left - 36);
				for (String line : wrap(state.errorDetail, available)) {
					QuestSkin.text(g, font, line, left + 18, ey, QuestSkin.ALERT, false);
					ey += 11;
					if (ey > bottom - 16)
						break;
				}
			}
		} else if (tab.equals("quests")) {
			drawCategories(g, mx, my);
			if (!overlayDetails)
				drawTree(g, mx, my);
			boolean panel = panelShown();
			if (panel) {
				int px = overlayDetails ? treeLeft : treeRight;
				g.fill(px, top + 25, right - 1, bottom - 1, QuestSkin.PANEL);
				g.fill(px, top + 25, px + 1, bottom - 1, QuestSkin.INK);
			}
			if (selected != null)
				drawDetails(g);
			else if (panel)
				drawJournal(g, mx, my);
		} else if (tab.equals("team"))
			drawTeam(g);
		else
			QuestSkin.text(g, font, t("settings_hint"), left + 14, bottom - 22, QuestSkin.DIM, false);
		if (state.ready && state.error != null) {
			String message = state.errorDetail == null ? t("error." + state.error).getString() : state.errorDetail;
			g.fill(left + 1, bottom - 15, right - 1, bottom - 1, QuestSkin.ALERT);
			QuestSkin.text(g, font, font.plainSubstrByWidth(message, Math.max(1, right - left - 30)), left + 12,
					bottom - 12, QuestSkin.LIGHT, false);
		}
		super.extractRenderState(g, mx, my, delta);
		drawPlayerPicker(g);
		if (tab.equals("quests") && selected != null && !rewardView && QuestRecipeViewer.available())
			for (var target : recipeTargets)
				if (target.contains(mx, my, detailsContentTop, bottom - 5))
					hoverTitle = t("show_recipe").getString();
		drawBoundedHover(g, mx, my);
		if (attachedToScreenFace)
			QuestToast.draw(g, width, height);
		if (attachedToScreenFace)
			g.disableScissor();
		g.pose().popMatrix();
	}

	/** Draw the hover in the tablet's logical pixels, after all widgets. */
	private void drawBoundedHover(GuiGraphicsExtractor g, int mx, int my) {
		if (mx < left || mx >= right || my < top || my >= bottom)
			return;
		String label = hoverTitle;
		for (QuestButton button : hoverButtons) {
			if (button.visible && button.active && mx >= button.getX() && mx < button.getX() + button.getWidth()
					&& my >= button.getY() && my < button.getY() + button.getHeight()) {
				label = button.hoverText().getString();
			}
		}
		if (label == null || label.isBlank())
			return;
		int contentWidth = Math.min(214, Math.max(20, right - left - 20));
		List<String> rows = wrap(label, contentWidth);
		if (rows.isEmpty())
			return;
		int maxRows = Math.max(1, Math.min(14, (bottom - top - 12) / 11));
		if (rows.size() > maxRows) {
			rows = new ArrayList<>(rows.subList(0, maxRows));
			String last = rows.get(maxRows - 1);
			rows.set(maxRows - 1, font.plainSubstrByWidth(last, Math.max(1, contentWidth - font.width("…"))) + "…");
		}
		int textWidth = rows.stream().mapToInt(font::width).max().orElse(0);
		int boxW = Math.min(right - left - 8, textWidth + 12), boxH = rows.size() * 11 + 8;
		var place = QuestTooltipLayout.place(mx, my, boxW, boxH, left + 4, top + 4, right - 4, bottom - 4);
		g.nextStratum();
		QuestSkin.panel(g, place.x(), place.y(), boxW, boxH, 0xf43a2817, QuestSkin.FIELD);
		for (int i = 0; i < rows.size(); i++)
			QuestSkin.text(g, font, rows.get(i), place.x() + 6, place.y() + 4 + i * 11, QuestSkin.FIELD, false);
	}

	private void drawCategories(GuiGraphicsExtractor g, int mx, int my) {
		updateCategoryBadges();
		var categories = categoryRows();
		// A 2px paper strip under the ink bar: the selected paper tab joins the page, not a seam.
		g.fill(left + 1, top + 25, treeLeft, bottom - 1, QuestSkin.LINE);
		int count = railRows();
		categoryScroll = Math.clamp(categoryScroll, 0, Math.max(0, categories.size() - count));
		for (int i = categoryScroll; i < Math.min(categories.size(), categoryScroll + count); i++) {
			var row = categories.get(i);
			int x = left + 9, y = railTop + (i - categoryScroll) * 26;
			if (row.category() == null) {
				QuestSkin.panel(g, x, y, 22, 22, QuestSkin.PANEL, QuestSkin.INK);
				g.item(icon("minecraft:bookshelf"), x + 3, y + 3);
				g.fill(x + 13, y + 12, x + 21, y + 21, QuestSkin.PAPER);
				g.text(font, collapsedGroups.contains(row.group()) ? "+" : "−", x + 15, y + 13, QuestSkin.INK,
						false);
				if (mx >= x && mx < x + 22 && my >= y && my < y + 22)
					hoverTitle = row.group();
				continue;
			}
			var c = row.category();
			boolean active = c.get("id").getAsString().equals(category);
			QuestSkin.panel(g, x, y, 22, 22, active ? QuestSkin.INK : QuestSkin.PAPER, QuestSkin.INK);
			drawIcon(g, c.has("icon") ? c.get("icon").getAsString() : "minecraft:book", x + 3, y + 3);
			String catId = c.get("id").getAsString();
			int markers = categoryBadges.getOrDefault(catId, 0);
			boolean claimable = (markers & 1) != 0;
			boolean fresh = (markers & 2) != 0;
			if (claimable || fresh) {
				g.fill(x + 15, y + 15, x + 22, y + 22, active ? QuestSkin.PAPER : QuestSkin.INK);
				g.fill(x + 16, y + 16, x + 21, y + 21, claimable ? QuestSkin.CLAIM : QuestSkin.ACTIVE);
			}
			if (mx >= x && mx < x + 22 && my >= y && my < y + 22)
				hoverTitle = (c.has("group") && !c.get("group").getAsString().isBlank()
						? c.get("group").getAsString() + " / "
						: "") + localized(c.getAsJsonObject("title"))
						+ (claimable ? " · " + t("claim").getString()
								: fresh ? " · " + t("toast.unlocked").getString() : "");
		}
		if (categories.size() > count) {
			if (categoryScroll > 0)
				g.fill(left + 14, railTop - 3, left + 26, railTop - 2, QuestSkin.INK);
			if (categoryScroll + count < categories.size())
				g.fill(left + 14, railTop + count * 26 - 3, left + 26, railTop + count * 26 - 2, QuestSkin.INK);
		}
	}

	private int railRows() {
		return Math.max(1, (bottom - 6 - railTop) / 26);
	}

	private String localized(JsonObject map) {
		Map<String, String> values = new HashMap<>();
		map.entrySet().forEach(e -> values.put(e.getKey(), e.getValue().getAsString()));
		return state.text(values);
	}

	private String annotationLabel(JsonObject map) {
		if (map == null)
			return "";
		Map<String, String> values = new HashMap<>();
		map.entrySet().forEach(e -> {
			if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isString())
				values.put(e.getKey(), e.getValue().getAsString());
		});
		// "Note" is the studio's initial placeholder, not an authored translation.
		Map<String, String> authored = new HashMap<>();
		values.forEach((key, value) -> {
			if (!value.isBlank() && !value.trim().equals("Note"))
				authored.put(key, value);
		});
		return state.text(authored.isEmpty() ? values : authored);
	}

	private void drawCategoryHeader(GuiGraphicsExtractor g, int mx, int my) {
		var c = state.categories.get(category);
		if (c == null)
			return;
		int x = treeLeft, w = treeRight - treeLeft, y = top + 23;
		g.fill(x, y + 2, x + w, y + 33, QuestSkin.PANEL);
		g.fill(x, y + 33, x + w, y + 34, QuestSkin.INK);
		drawIcon(g, c.has("icon") ? c.get("icon").getAsString() : "minecraft:book", x + 6, y + 8);
		// Claim all sits at the band's right end; the progress block gives way first.
		int claim = claimAllButton != null && claimAllButton.visible ? claimAllButton.getWidth() + 8 : 0;
		boolean progress = w - claim > 240;
		int room = Math.max(1, w - 34 - (progress ? 90 : 0) - claim);
		String title = localized(c.getAsJsonObject("title"));
		// The category name is the screen's one big line; it drops to body size rather than truncate.
		boolean big = font.width(QuestSkin.title(title)) <= room;
		if (big)
			QuestSkin.big(g, font, title, x + 28, y + 5, QuestSkin.INK);
		else
			QuestSkin.text(g, font, QuestSkin.bold(QuestSkin.fit(font, title, room * 9 / 10)), x + 28, y + 7,
					QuestSkin.INK, false);
		QuestSkin.text(g, font, QuestSkin.fit(font, localized(c.getAsJsonObject("description")), room), x + 28,
				y + (big ? 21 : 19), QuestSkin.DIM, false);
		long done = completedNodes;
		if (progress) {
			int bar = 80, bx = x + w - bar - 6 - claim;
			String count = done + " / " + layout.points().size();
			QuestSkin.text(g, font, count, bx + bar - font.width(count), y + 8, QuestSkin.INK, false);
			QuestSkin.bar(g, bx, y + 20, bar, 3, layout.points().isEmpty() ? 0 : (double) done / layout.points().size(),
					QuestSkin.DONE);
		}
		if (!overlayDetails && mx >= x && mx < x + w - claim && my >= y && my < y + 33)
			hoverTitle = (c.has("group") && !c.get("group").getAsString().isBlank()
					? c.get("group").getAsString() + " / "
					: "") + localized(c.getAsJsonObject("title")) + "\n" + localized(c.getAsJsonObject("description"));
	}

	private void drawPaper(GuiGraphicsExtractor g) {
		// Map paper: flat parchment, a fleck of grain, and the ruled border of a framed map.
		g.fill(treeLeft, treeTop, treeRight, treeBottom, QuestSkin.PAPER);
		for (int yy = treeTop + 5; yy < treeBottom - 3; yy += 9)
			for (int xx = treeLeft + 5; xx < treeRight - 3; xx += 17) {
				int seed = (xx * 19 + yy * 31) & 15;
				if (seed < 6)
					g.fill(xx, yy, Math.min(treeRight - 2, xx + 2 + seed), yy + 1, 0xffd0bd92);
			}
		g.outline(treeLeft + 1, treeTop + 1, treeRight - treeLeft - 2, treeBottom - treeTop - 2, QuestSkin.LINE);
		int c = QuestSkin.DIM;
		for (int sx : new int[] { treeLeft + 3, treeRight - 9 })
			for (int sy : new int[] { treeTop + 3, treeBottom - 9 }) {
				g.fill(sx, sy, sx + 6, sy + 1, c);
				g.fill(sx, sy, sx + 1, sy + 6, c);
				g.fill(sx + 3, sy + 3, sx + 5, sy + 5, c);
			}
	}

	private void drawTree(GuiGraphicsExtractor g, int mx, int my) {
		updateRenderCache();
		g.enableScissor(treeLeft, treeTop, treeRight, treeBottom);
		drawPaper(g);
		g.nextStratum();
		drawBackgrounds(g);
		g.nextStratum();
		drawAnnotations(g);
		int edgeCount = 0;
		for (var edge : visibleEdges) {
			if ((edgeCount++ & 7) == 0)
				g.nextStratum();
			var a = layout.points().get(edge.from());
			var b = layout.points().get(edge.to());
			int x1 = (int) camera.screenX(a.x()), y1 = (int) camera.screenY(a.y())
					+ (int) Math.round(nodeRadius(state.quests.get(edge.from())) * camera.zoom());
			int x2 = (int) camera.screenX(b.x()), y2 = (int) camera.screenY(b.y())
					- (int) Math.round(nodeRadius(state.quests.get(edge.to())) * camera.zoom());
			boolean completed = QuestClientState.flag(state.progress(edge.from()), "completed");
			int c = QuestSkin.onPaper(QuestResourceTheme.color(state.quests.get(edge.from()),
					completed ? "dependency_line_completed_color" : "dependency_line_uncompleted_color",
					completed ? QuestSkin.INK : QuestSkin.DIM));
			if (routes.containsKey(edge) && !routes.get(edge).isEmpty()) {
				int previousX = x1, previousY = y1;
				for (var point : routes.get(edge)) {
					int x = (int) camera.screenX(point.x()), y = (int) camera.screenY(point.y());
					drawSegment(g, previousX, previousY, x, y, c, camera.zoom(), !completed);
					previousX = x;
					previousY = y;
				}
				drawSegment(g, previousX, previousY, x2, y2, c, camera.zoom(), !completed);
				g.pose().pushMatrix();
				g.pose().translate(x2, y2);
				g.pose().rotate((float) Math.atan2(y2 - previousY, x2 - previousX));
				g.pose().scale((float) camera.zoom());
				g.fill(-4, -2, -3, 3, c);
				g.fill(-3, -1, -2, 2, c);
				g.fill(-2, 0, 0, 1, c);
				g.pose().popMatrix();
				continue;
			}
			if (Math.max(x1, x2) < treeLeft || Math.min(x1, x2) > treeRight || Math.max(y1, y2) < treeTop
					|| Math.min(y1, y2) > treeBottom)
				continue;
			int middle = (y1 + y2) / 2;
			drawSegment(g, x1, y1, x1, middle, c, camera.zoom(), !completed);
			drawSegment(g, x1, middle, x2, middle, c, camera.zoom(), !completed);
			drawSegment(g, x2, middle, x2, y2, c, camera.zoom(), !completed);
			g.pose().pushMatrix();
			g.pose().translate(x2, y2);
			g.pose().scale((float) camera.zoom());
			g.fill(-2, -3, 3, -2, c);
			g.fill(-1, -2, 2, -1, c);
			g.pose().popMatrix();
		}
		boolean any = false;
		int nodeCount = 0;
		for (String id : visibleNodes) {
			// Bound Minecraft's intersection search without dropping overlapping authored
			// nodes.
			if ((nodeCount++ & 7) == 0)
				g.nextStratum();
			any = true;
			var p = layout.points().get(id);
			int x = (int) camera.screenX(p.x()), y = (int) camera.screenY(p.y()), r = nodeRadius(state.quests.get(id));
			// Scale the complete node, not just the distance to neighbouring nodes.
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			g.pose().scale((float) camera.zoom());
			g.pose().translate(-x, -y);
			String shape = state.quests.get(id).presentation().shape(), status = statuses.get(id);
			drawQuestShape(g, shape, x, y, r, color(id));
			// A reward waiting is circled in red; the selection rule sits outside it.
			if (status.equals("reward_ready"))
				QuestSkin.panel(g, x - r - 2, y - r - 2, 2 * r + 4, 2 * r + 4, 0, QuestSkin.CLAIM);
			if (id.equals(selected) || id.equals(keyboardQuest)) {
				QuestSkin.panel(g, x - r - 4, y - r - 4, 2 * r + 8, 2 * r + 8, 0, QuestSkin.INK);
				QuestSkin.panel(g, x - r - 5, y - r - 5, 2 * r + 10, 2 * r + 10, 0, QuestSkin.INK);
			}
			float iconSize = (float) (r * state.quests.get(id).presentation().iconScale());
			int half = (int) (iconSize / 2) + 1;
			// Item icons keep a parchment plate so they read on any state colour.
			if (!status.equals("available") && !status.equals("locked"))
				g.fill(x - half, y - half, x + half, y + half, QuestSkin.FIELD);
			g.pose().pushMatrix();
			g.pose().translate(x - iconSize / 2f, y - iconSize / 2f);
			g.pose().scale(iconSize / 16f);
			drawIcon(g, state.quests.get(id).icon(), 0, 0);
			g.pose().popMatrix();
			if (status.equals("locked")) {
				// Fog: uncharted quests sit under a hatched veil, a mark as well as a colour.
				int fog = shape.equals("square") ? r - 1 : half;
				g.fill(x - fog, y - fog, x + fog, y + fog, 0xa0cdb98a);
				for (int line = y - fog + 1; line < y + fog; line += 3)
					g.fill(x - fog, line, x + fog, line + 1, QuestSkin.LINE);
			}
			String progression = state.quests.get(id).progression();
			if (!progression.equals("once")) {
				String marker = progression.equals("repeatable") ? "R" : progression.equals("infinite") ? "∞" : "◆";
				g.fill(x + r - 9, y - r + 1, x + r - 1, y - r + 10, QuestSkin.FIELD);
				QuestSkin.text(g, font, marker, x + r - 7, y - r + 2, QuestSkin.INK, false);
			}
			if (status.equals("reward_ready")) {
				g.fill(x + r - 6, y + r - 7, x + r + 3, y + r + 3, QuestSkin.INK);
				QuestSkin.text(g, font, "!", x + r - 3, y + r - 6, QuestSkin.LIGHT, false);
			} else if (status.equals("completed")) {
				g.fill(x + r - 5, y + r - 6, x + r + 3, y + r + 2, QuestSkin.INK);
				g.fill(x + r - 4, y + r - 3, x + r - 2, y + r - 1, QuestSkin.LIGHT);
				g.fill(x + r - 2, y + r - 2, x + r, y + r, QuestSkin.LIGHT);
				g.fill(x + r, y + r - 5, x + r + 2, y + r - 2, QuestSkin.LIGHT);
			}
			g.pose().popMatrix();
			double hitRadius = r * camera.zoom();
			if (inTree(mx, my) && mx >= x - hitRadius && mx <= x + hitRadius && my >= y - hitRadius
					&& my <= y + hitRadius)
				hoverTitle = titles.get(id) + " · " + t("status." + statuses.get(id)).getString() + "\n"
						+ state.text(state.quests.get(id).presentation().subtitle())
						+ (progression.equals("once") ? "" : " · " + t("progression." + progression).getString());
		}
		if (!any && layout.points().isEmpty())
			QuestSkin.centered(g, font, t("empty"), (treeLeft + treeRight) / 2, (treeTop + treeBottom) / 2,
					QuestSkin.DIM);
		g.disableScissor();
	}

	private static void drawSegment(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color, double zoom,
			boolean dashed) {
		double length = Math.hypot((double) x2 - x1, (double) y2 - y1);
		if (length == 0)
			return;
		g.pose().pushMatrix();
		g.pose().translate(x1, y1);
		g.pose().rotate((float) Math.atan2((double) y2 - y1, (double) x2 - x1));
		g.pose().scale((float) zoom);
		// The coordinates were already camera-transformed; scale line thickness
		// while keeping the two endpoints at their exact screen positions.
		int units = Math.max(1, (int) Math.ceil(length / zoom));
		// Explored trails are solid and heavier; the rest are dashed, unless the
		// dashes would cost more fills than a long zoomed-out edge is worth.
		if (!dashed || units > 448)
			g.fill(0, dashed ? 0 : -1, units, 1, color);
		else
			for (int at = 0; at < units; at += 7)
				g.fill(at, 0, Math.min(units, at + 4), 1, color);
		g.pose().popMatrix();
	}

	private void drawBackgrounds(GuiGraphicsExtractor g) {
		var data = state.categories.get(category);
		if (data == null || !data.has("annotations"))
			return;
		for (var entry : data.getAsJsonArray("annotations")) {
			var note = entry.getAsJsonObject();
			if (!note.has("style") || !note.get("style").getAsString().equals("background"))
				continue;
			int x = (int) Math.round(camera.screenX(note.get("x").getAsDouble()));
			int y = (int) Math.round(camera.screenY(note.get("y").getAsDouble()));
			int w = Math.max(1, note.get("width").getAsInt());
			int h = Math.max(1, note.get("height").getAsInt());
			double halfW = w * camera.zoom() / 2, halfH = h * camera.zoom() / 2;
			if (x + halfW < treeLeft || x - halfW > treeRight || y + halfH < treeTop || y - halfH > treeBottom)
				continue;
			int rgb = Integer.parseInt(note.get("color").getAsString().substring(1), 16);
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			g.pose().scale((float) camera.zoom());
			g.pose().translate(-x, -y);
			int l = x - w / 2, t = y - h / 2;
			g.fill(l, t, l + w, t + h, 0x30000000 | rgb);
			g.outline(l, t, w, h, 0xaa000000 | QuestSkin.onPaper(rgb) & 0xffffff);
			g.pose().popMatrix();
		}
	}

	/** Annotation coordinates use the same world-space camera as quest nodes. */
	private void drawAnnotations(GuiGraphicsExtractor g) {
		var data = state.categories.get(category);
		if (data == null || !data.has("annotations"))
			return;
		JsonArray imagePool = data.has("images") ? data.getAsJsonArray("images") : new JsonArray();
		for (var entry : data.getAsJsonArray("annotations")) {
			var note = entry.getAsJsonObject();
			if (note.has("style") && note.get("style").getAsString().equals("background"))
				continue;
			int x = (int) camera.screenX(note.get("x").getAsDouble());
			int y = (int) camera.screenY(note.get("y").getAsDouble());
			int extentX = note.has("width") ? Math.max(1, note.get("width").getAsInt()) / 2 : 200;
			int extentY = note.has("height") ? Math.max(1, note.get("height").getAsInt()) / 2 : 150;
			if (x < treeLeft - extentX * camera.zoom() || x > treeRight + extentX * camera.zoom()
					|| y < treeTop - extentY * camera.zoom() || y > treeBottom + extentY * camera.zoom())
				continue;
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			g.pose().scale((float) camera.zoom());
			g.pose().translate(-x, -y);
			try {
				if (RichAnnotationRenderer.render(g, font, note, imagePool, minecraft.options.languageCode, x, y))
					continue;
				String label = annotationLabel(note.getAsJsonObject("text"));
				String style = note.has("style") ? note.get("style").getAsString() : "note";
				String hex = note.has("color") ? note.get("color").getAsString() : "#c6a986";
				int color = QuestSkin.onPaper(0xff000000 | Integer.parseInt(hex.substring(1), 16));
				int w = note.has("width") ? Math.max(1, note.get("width").getAsInt())
						: Math.max(35, font.width(label) + 12);
				int h = note.has("height") ? Math.max(1, note.get("height").getAsInt()) : 20;
				String visible = font.plainSubstrByWidth(label, Math.max(1, w - 12));
				String align = note.has("align") ? note.get("align").getAsString() : "center";
				int tx = switch (align) {
				case "left" -> x - w / 2 + 6;
				case "right" -> x + w / 2 - 6 - font.width(visible);
				default -> x - font.width(visible) / 2;
				};
				if (!style.equals("heading") && !style.equals("label"))
					QuestSkin.panel(g, x - w / 2, y - h / 2, w, h, 0xdde8dab5, color);
				QuestSkin.text(g, font, style.equals("heading") ? QuestSkin.bold(visible) : Component.literal(visible), tx,
						y - 4, color, false);
			} finally {
				g.pose().popMatrix();
			}
		}
	}

	private void drawQuestShape(GuiGraphicsExtractor g, String shape, int x, int y, int radius, int edge) {
		QuestResourceTheme.ensureShape(shape);
		// The state colour fills the marker; the ink outline keeps it readable on parchment.
		if (shape.equals("square")) {
			QuestSkin.panel(g, x - radius, y - radius, radius * 2, radius * 2, edge, QuestSkin.INK);
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(x - radius, y - radius);
		g.pose().scale(radius / 16f);
		for (var span : QuestShape.spans(shape)) {
			int start = span.start(), end = span.end(), row = span.row();
			g.fill(start, row, end, row + 1, QuestSkin.INK);
			if (end - start > 2 && row > 0 && row < 31)
				g.fill(start + 1, row, end - 1, row + 1, edge);
		}
		g.pose().popMatrix();
	}

	private int nodeRadius(QuestDefinition quest) {
		int size = quest == null || quest.size() == 0 ? 32 : Math.max(1, quest.size());
		return size / 2;
	}

	private Optional<String> hitQuestAtScreen(double x, double y) {
		return visibleNodes.stream().filter(id -> {
			var p = layout.points().get(id);
			double r = nodeRadius(state.quests.get(id)) * camera.zoom();
			var shape = state.quests.get(id).presentation().shape();
			QuestResourceTheme.ensureShape(shape);
			return r > 0
					&& QuestShape.contains(shape, (x - camera.screenX(p.x())) / r, (y - camera.screenY(p.y())) / r);
		}).findFirst();
	}

	private void updateRenderCache() {
		String language = minecraft.options.languageCode;
		// The search never hides chart nodes; it filters the panel's list. Only the status chips filter here.
		if (filteredSequence != state.sequence || filteredCatalogue != catalogue
				|| !Objects.equals(filteredFilter, filter) || !Objects.equals(filteredLanguage, language)) {
			filteredSequence = state.sequence;
			filteredCatalogue = catalogue;
			filteredFilter = filter;
			filteredLanguage = language;
			filtered.clear();
			titles.clear();
			statuses.clear();
			completedNodes = 0;
			for (String id : layout.points().keySet()) {
				var q = state.quests.get(id);
				String title = state.text(q.title()), status = status(id);
				titles.put(id, title);
				statuses.put(id, status);
				if (status.equals("completed") || status.equals("reward_ready"))
					completedNodes++;
				if (filter.equals("all") || status.equals(filter)
						|| filter.equals("completed") && status.equals("reward_ready"))
					filtered.add(id);
			}
			cachedLeft = Double.NaN;
		}
		double l = camera.worldX(treeLeft), r = camera.worldX(treeRight), t = camera.worldY(treeTop),
				b = camera.worldY(treeBottom);
		if (l == cachedLeft && r == cachedRight && t == cachedTop && b == cachedBottom)
			return;
		cachedLeft = l;
		cachedRight = r;
		cachedTop = t;
		cachedBottom = b;
		visibleNodes = layout.points().entrySet().stream().filter(e -> {
			if (!filtered.contains(e.getKey()))
				return false;
			double sx = camera.screenX(e.getValue().x()), sy = camera.screenY(e.getValue().y());
			double nr = nodeRadius(state.quests.get(e.getKey())) * camera.zoom();
			return sx + nr >= treeLeft && sx - nr <= treeRight && sy + nr >= treeTop && sy - nr <= treeBottom;
		}).map(Map.Entry::getKey).toList();
		visibleEdges = layout.edges().stream()
				.filter(e -> filtered.contains(e.from()) && filtered.contains(e.to())
						&& !state.quests.get(e.to()).presentation().hideDependencyLines()
						&& !state.quests.get(e.from()).presentation().hideDependentLines())
				.filter(e -> {
					var a = layout.points().get(e.from());
					var z = layout.points().get(e.to());
					return routes.containsKey(e) || Math.max(a.x(), z.x()) >= l && Math.min(a.x(), z.x()) <= r
							&& Math.max(a.y(), z.y()) >= t && Math.min(a.y(), z.y()) <= b;
				}).toList();
	}

	private void updateClaimAll() {
		// No reward waiting, no control: the category band takes the room back. Open details have their
		// own claim button, and a screen carries one claim-red button at most.
		if (claimAllButton != null)
			claimAllButton.visible = claimAllButton.active = selected == null && state.progress.values().stream()
					.anyMatch(p -> QuestClientState.flag(p, "claimableAny"));
	}

	/**
	 * The search results: every quest of every category whose title, or whose
	 * category's title, contains the query; the category on screen first. Rebuilt
	 * on query, progress or catalogue changes only.
	 */
	private void updateJournal() {
		String language = minecraft.options.languageCode;
		if (journalSequence == state.sequence && journalCatalogue == state.catalogueRevision
				&& language.equals(journalLanguage) && Objects.equals(category, journalCategory)
				&& query.equals(journalQuery))
			return;
		journalSequence = state.sequence;
		journalCatalogue = state.catalogueRevision;
		journalLanguage = language;
		journalCategory = category;
		journalQuery = query;
		String needle = query.trim().toLowerCase(Locale.ROOT);
		var groups = new LinkedHashMap<String, List<JournalRow>>();
		var names = new HashMap<String, String>();
		groups.put(Objects.toString(category, ""), new ArrayList<>());
		state.categories.keySet().forEach(id -> groups.putIfAbsent(id, new ArrayList<>()));
		for (var q : state.quests.values()) {
			String key = Objects.toString(q.category(), "");
			String group = names.computeIfAbsent(key, ignored -> {
				var c = state.categories.get(key);
				return c == null || !c.has("title") ? key : localized(c.getAsJsonObject("title"));
			});
			String title = state.text(q.title());
			if (!title.toLowerCase(Locale.ROOT).contains(needle) && !group.toLowerCase(Locale.ROOT).contains(needle))
				continue;
			var p = state.progress(q.id());
			// The same flag drives "Claim all": the button never shows without its rows.
			String status = QuestClientState.flag(p, "claimableAny") ? "reward_ready" : QuestPresentation.status(q, p);
			double fraction = 0;
			JsonObject counters = p.getAsJsonObject("counters");
			if (counters != null && !q.objectives().isEmpty()) {
				for (var o : q.objectives())
					if (counters.has(o.id()))
						fraction += Math.min(1, counters.get(o.id()).getAsDouble()
								/ Math.max(1, QuestPresentation.target(q, o, p)));
				fraction /= q.objectives().size();
			}
			groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new JournalRow(q.id(), title, status, fraction));
		}
		var flat = new ArrayList<JournalRow>();
		groups.forEach((key, rows) -> {
			if (rows.isEmpty())
				return;
			flat.add(new JournalRow(null, names.get(key) + " · " + rows.size(), "", 0));
			flat.addAll(rows);
		});
		journal = List.copyOf(flat);
	}

	private void drawJournal(GuiGraphicsExtractor g, int mx, int my) {
		updateJournal();
		int x = detailLeft, w = right - x - 8, y = panelTop, end = bottom - 5;
		if (journal.isEmpty()) {
			for (String line : wrap(t("search.empty").getString(), w)) {
				QuestSkin.text(g, font, line, x, y + 4, QuestSkin.DIM, false);
				y += 10;
			}
			return;
		}
		int total = journal.stream().mapToInt(JournalRow::height).sum();
		journalScroll = Math.clamp(journalScroll, 0, Math.max(0, total - (end - y)));
		g.enableScissor(x - 2, y, right - 1, end);
		int rowY = y - journalScroll;
		for (var row : journal) {
			int h = row.height();
			if (rowY + h > y && rowY < end) {
				if (row.id() == null) {
					QuestSkin.text(g, font, QuestSkin.bold(QuestSkin.fit(font, row.title(), w * 9 / 10)), x, rowY + 8,
							QuestSkin.DIM, false);
					g.fill(x, rowY + 18, x + w, rowY + 19, QuestSkin.LINE);
				} else {
					if (mx >= x && mx < x + w && my >= Math.max(y, rowY) && my < Math.min(end, rowY + h)) {
						g.fill(x - 2, rowY + 1, x + w + 2, rowY + h, QuestSkin.FIELD);
						hoverTitle = row.title() + " · " + t("status." + row.status()).getString();
					}
					g.pose().pushMatrix();
					g.pose().translate(x, rowY + 5);
					g.pose().scale(.75f);
					drawIcon(g, state.quests.get(row.id()).icon(), 0, 0);
					g.pose().popMatrix();
					boolean active = row.status().equals("active");
					QuestSkin.text(g, font, QuestSkin.fit(font, row.title(), w - 27), x + 16, rowY + (active ? 4 : 7),
							QuestSkin.INK, false);
					if (active)
						QuestSkin.bar(g, x + 16, rowY + 15, w - 27, 2, row.fraction(), QuestSkin.ACTIVE);
					// State is a swatch with the chart's mark and, on hover, words; never a colour alone.
					int sx = x + w - 7, sy = rowY + 7;
					boolean done = row.status().equals("completed");
					QuestSkin.panel(g, sx, sy, 7, 7, done ? QuestSkin.INK : statusColor(row.status()), QuestSkin.INK);
					switch (row.status()) {
					case "completed" -> {
						g.fill(sx + 1, sy + 3, sx + 2, sy + 4, QuestSkin.LIGHT);
						g.fill(sx + 2, sy + 4, sx + 3, sy + 5, QuestSkin.LIGHT);
						g.fill(sx + 3, sy + 3, sx + 4, sy + 5, QuestSkin.LIGHT);
						g.fill(sx + 4, sy + 2, sx + 5, sy + 3, QuestSkin.LIGHT);
						g.fill(sx + 5, sy + 1, sx + 6, sy + 2, QuestSkin.LIGHT);
					}
					case "reward_ready" -> {
						g.fill(sx + 3, sy + 1, sx + 4, sy + 4, QuestSkin.LIGHT);
						g.fill(sx + 3, sy + 5, sx + 4, sy + 6, QuestSkin.LIGHT);
					}
					case "active", "available" -> {
					}
					default -> {
						for (int i = 0; i < 5; i++)
							g.fill(sx + 1 + i, sy + 5 - i, sx + 2 + i, sy + 6 - i, QuestSkin.LINE);
					}
					}
				}
			}
			rowY += h;
		}
		g.disableScissor();
	}

	private boolean clickJournal(double x, double y) {
		if (selected != null || !panelShown() || x < detailLeft || x >= right - 8 || y < panelTop || y >= bottom - 5)
			return false;
		int rowY = panelTop - journalScroll;
		for (var row : journal) {
			if (row.id() != null && y >= rowY && y < rowY + row.height()) {
				select(row.id(), true);
				return true;
			}
			rowY += row.height();
		}
		return false;
	}

	private void detailsButtons() {
		int x = detailLeft, w = right - x - 8;
		if (rewardView) {
			rewardButtons();
			return;
		}
		button("close_details", x, panelTop, w, () -> {
			selected = null;
			init();
		});
		button("rewards", x + w / 2 + 2, panelTop + 18, w / 2 - 2, () -> {
			rewardView = true;
			potentialRewards = false;
			rewardPage = 0;
			init();
		});
		var pinButton = button(settings.pinned.contains(selected) ? "unpin" : "pin", x, panelTop + 18, w / 2 - 2, () -> {
			if (!settings.pinned.remove(selected) && settings.pinned.size() < ClientSettings.MAX_PINNED)
				settings.pinned.add(selected);
			settings.save();
			init();
		});
		pinButton.active = settings.pinned.contains(selected) || settings.pinned.size() < ClientSettings.MAX_PINNED;
		questAction = button("claim", x, panelTop + 36, w, () -> {
			if (currentAction == null)
				return;
			if (currentAction.equals("choose")) {
				rewardView = true;
				potentialRewards = false;
				rewardPage = 0;
				init();
				return;
			}
			if (Set.of("trigger", "interact").contains(currentAction)) {
				pendingManualQuest = selected;
				pendingManualAt = System.currentTimeMillis();
			}
			action(currentAction, currentAction.equals("submit") ? selected + "|" + submitObjective
					: currentAction.equals("interact") ? selected + "|" + manualObjective : selected);
			updateQuestAction();
		});
		updateQuestAction();
	}

	private void updateQuestAction() {
		if (questAction == null || selected == null)
			return;
		var p = state.progress(selected);
		var q = state.quests.get(selected);
		if (q == null)
			return;
		submitObjective = null;
		manualObjective = null;
		if (!QuestClientState.flag(p, "terminal") && !QuestClientState.flag(p, "waitingRestart")
				&& QuestClientState.flag(p, "unlocked")) {
			JsonObject counters = p == null ? null : p.getAsJsonObject("counters");
			int tierIndex = (int) QuestClientState.count(p, "tier");
			long factor = q.progression().equals("tiered") && !q.tiers().isEmpty()
					? q.tiers().get(Math.min(tierIndex, q.tiers().size() - 1)).threshold()
					: 1L;
			for (var objective : q.objectives()) {
				long progress = counters != null && counters.has(objective.id())
						? counters.get(objective.id()).getAsLong()
						: 0;
				if (progress >= objective.count() * factor)
					continue;
				if (Set.of("item_submit", "item_turn_in", "xp_submit_levels", "xp_submit_points", "fluid_submit",
						"energy_submit").contains(objective.event()))
					submitObjective = objective.id();
				if (objective.event().equals("manual"))
					manualObjective = objective.id();
				if (q.objectiveMode().equals("sequence") && !objective.optional() || submitObjective != null
						|| manualObjective != null)
					break;
			}
		}
		currentAction = p.has("rewardState")
				&& p.getAsJsonObject("rewardState").get("status").getAsString().equals("choice")
						? "choose"
						: QuestClientState.flag(p, "claimable") ? "claim"
								: QuestClientState.flag(p, "waitingRestart") ? "restart"
										: manualObjective != null ? "interact"
												: (q.kind().equals("information")
														&& q.acknowledgement().equals("manual")
														|| q.objectives().stream()
																.anyMatch(o -> o.event().equals("manual")
																		&& o.target().equals(q.id())))
														&& !QuestClientState.flag(p, "terminal")
														&& !QuestClientState.flag(p, "waitingRestart") ? "trigger"
																: submitObjective != null ? "submit" : null;
		questAction.visible = currentAction != null;
		boolean pending = currentAction != null && Set.of("trigger", "interact").contains(currentAction)
				&& Objects.equals(pendingManualQuest, selected) && System.currentTimeMillis() - pendingManualAt < 2500L;
		questAction.active = currentAction != null && !pending && !QuestClientState.flag(p, "saving")
				&& (Set.of("claim", "choose").contains(currentAction) || QuestClientState.flag(p, "unlocked"));
		questAction.accent(currentAction != null && Set.of("claim", "choose").contains(currentAction));
		if (currentAction != null)
			questAction.setMessage(t(pending || QuestClientState.flag(p, "saving") ? "processing" : currentAction));
	}

	private void drawDetails(GuiGraphicsExtractor g) {
		recipeTargets.clear();
		if (rewardView) {
			drawRewardView(g);
			return;
		}
		var q = state.quests.get(selected);
		if (q == null)
			return;
		int x = detailLeft, w = right - x - 8, end = bottom - 5;
		// The name and state stay put above the scrolling text. No action, no reserved row.
		int y = panelTop + (questAction != null && questAction.visible ? 58 : 40);
		var name = font.split(QuestSkin.bold(state.text(q.title())), w);
		for (int i = 0; i < Math.min(3, name.size()); i++, y += 11)
			g.text(font, name.get(i), x, y, QuestSkin.INK, false);
		// State is a swatch and words, never a colour alone.
		String questStatus = status(q.id());
		QuestSkin.panel(g, x, y + 3, 7, 7, statusColor(questStatus), QuestSkin.INK);
		QuestSkin.text(g, font, QuestSkin.fit(font, t("status." + questStatus).getString() + " · "
				+ t("progression." + q.progression()).getString(), w - 11), x + 11, y + 3, QuestSkin.DIM, false);
		y += 18;
		detailsContentTop = y;
		g.enableScissor(x, y, right - 1, end);
		if (!Objects.equals(detailQuest, selected) || detailSequence != state.sequence || detailWidth != w
				|| !Objects.equals(detailLanguage, minecraft.options.languageCode)) {
			detailQuest = selected;
			detailSequence = state.sequence;
			detailWidth = w;
			detailLanguage = minecraft.options.languageCode;
			List<DetailRow> lines = new ArrayList<>();
			if (!q.presentation().subtitle().isEmpty())
				append(lines, state.text(q.presentation().subtitle()), w);
			if (q.presentation().optional())
				append(lines, t("optional").getString(), w);
			var p = state.progress(q.id());
			String description = state.text(q.description());
			List<String> labels = q.objectives().stream().map(o -> objectiveLabelForDisplay(q, o, p)).toList();
			var richDescription = q.descriptionRich() == null ? null
					: selectRichDescription(q.descriptionRich(), minecraft.options.languageCode);
			if (richDescription != null && !richDescription.isEmpty()) {
				JsonArray inline = new JsonArray();
				for (var entry : richDescription) {
					if (!entry.isJsonObject())
						continue;
					var part = entry.getAsJsonObject();
					if (!part.has("type"))
						continue;
					if ("image".equals(part.get("type").getAsString())) {
						if (!inline.isEmpty()) {
							appendRichRow(lines, inline, w);
							inline = new JsonArray();
						}
						var single = new JsonArray();
						single.add(part.deepCopy());
						appendRichRow(lines, single, w);
					} else if ("text".equals(part.get("type").getAsString()))
						inline.add(part.deepCopy());
				}
				if (!inline.isEmpty())
					appendRichRow(lines, inline, w);
			} else {
				var formattedDescription = QuestDescriptionText.prepare(description);
				String plainDescription = formattedDescription.plain();
				if (!QuestPresentation.redundantDescription(plainDescription, labels) && !plainDescription.isBlank()) {
					var formatted = font.split(formattedDescription.component(), Math.max(1, w)).stream()
							.map(formattedDescription::restore).toList();
					lines.add(new DetailRow(List.of(), ItemStack.EMPTY, null, formatted));
				}
			}
			if (!q.narrative().isEmpty())
				append(lines, state.text(q.narrative()), w);
			var counters = p.getAsJsonObject("counters");
			if (!q.kind().equals("information")) {
				heading(lines, t("objectives").getString() + " · " + t("mode." + q.objectiveMode()).getString()
						+ (q.objectiveMode().equals("at_least") ? " " + q.objectiveThreshold() : ""), w);
				for (var o : q.objectives()) {
					long value = counters != null && counters.has(o.id()) ? counters.get(o.id()).getAsLong() : 0;
					String label = objectiveLabelForDisplay(q, o, p)
							+ (o.optional() ? " · " + t("optional").getString() : "");
					if (Set.of("fluid_transfer", "energy_transfer").contains(o.event()))
						label += " · " + t("external_integration").getString();
					var recipe = recipeForObjective(o);
					lines.add(new DetailRow(wrap(label, Math.max(1, w - 32)),
							recipe.isEmpty() ? iconForObjective(q, o) : recipe,
							value + " / " + QuestPresentation.target(q, o, p)
									+ (o.event().equals("fluid_transfer") ? " mB"
											: o.event().equals("energy_transfer") ? " FE" : ""),
							null, null, 0, !recipe.isEmpty(), false,
							(double) value / Math.max(1, QuestPresentation.target(q, o, p))));
				}
			} else if (!QuestClientState.flag(p, "completed"))
				append(lines,
						t(q.acknowledgement().equals("manual") ? "information_review" : "information_read").getString(),
						w);
			if (q.requires() != null) {
				heading(lines, t("requires").getString(), w);
				append(lines, condition(q.requires()), w);
			}
			if (q.progression().equals("tiered"))
				append(lines, t("tier_count", QuestClientState.count(p, "tier")).getString(), w);
			else if (!q.progression().equals("once") && QuestClientState.count(p, "cycles") > 0)
				append(lines, t("repeat_count", QuestClientState.count(p, "cycles")).getString(), w);
			heading(lines, t("rewards").getString(), w);
			var preview = state.rewardPreviews.getOrDefault(q.id(), new JsonArray());
			for (var r : preview)
				appendTile(lines, rewardLabel(r.getAsJsonObject()), w, previewIcon(r.getAsJsonObject()));
			var tierPreviews = state.tierRewardPreviews.getOrDefault(q.id(), new JsonArray());
			for (int i = 0; i < tierPreviews.size(); i++) {
				heading(lines, t("tier", q.tiers().get(i).threshold()).getString(), w);
				for (var r : tierPreviews.get(i).getAsJsonArray())
					appendTile(lines, rewardLabel(r.getAsJsonObject()), w, previewIcon(r.getAsJsonObject()));
			}
			if (q.timeLimitSeconds() > 0)
				append(lines, t("time_limit", q.timeLimitSeconds()).getString(), w);
			if (q.noDeath())
				append(lines, t("no_death").getString(), w);
			detailLines = List.copyOf(lines);
		}
		int total = detailLines.stream().mapToInt(this::detailHeight).sum();
		detailsScroll = Math.clamp(detailsScroll, 0, Math.max(0, total - (end - y)));
		int rowY = y - detailsScroll;
		for (var row : detailLines) {
			int h = detailHeight(row);
			if (rowY + h > y && rowY < end) {
				int textX = x;
				if (row.rich() != null) {
					RichAnnotationRenderer.render(g, font, row.rich(), null, "en_us", x + w / 2, rowY + h / 2);
					rowY += h;
					continue;
				}
				if (row.heading()) {
					// More air above a heading than below it; the rule ties it to its rows.
					QuestSkin.text(g, font, QuestSkin.bold(row.lines().get(0)), x, rowY + 9, QuestSkin.DIM, false);
					g.fill(x, rowY + 19, x + w, rowY + 20, QuestSkin.LINE);
					rowY += h;
					continue;
				}
				if (!row.icon().isEmpty()) {
					QuestSkin.panel(g, x, rowY, w, h - 4, QuestSkin.FIELD, QuestSkin.LINE);
					g.item(row.icon(), x + 5, rowY + 4);
					if (row.recipe())
						recipeTargets.add(new QuestRecipeViewer.Target(row.icon(), x + 5, rowY + 4));
					textX += 26;
				}
				for (int i = 0; i < row.lineCount(); i++) {
					// A lone label sits on the icon's centre line; wrapped or counted rows start at the top padding.
					int textY = rowY + (row.icon().isEmpty() ? 0
							: row.lineCount() == 1 && row.progress() == null ? 8 : 5) + i * 11;
					if (row.formatted() == null)
						QuestSkin.text(g, font, row.lines().get(i), textX, textY, QuestSkin.INK, false);
					else {
						g.pose().pushMatrix();
						g.pose().translate(textX, textY);
						g.pose().scale(QuestSkin.TEXT_SCALE);
						g.text(font, QuestSkin.onPaper(row.formatted().get(i)), 0, 0, QuestSkin.INK, false);
						g.pose().popMatrix();
					}
				}
				if (row.progress() != null) {
					int count = font.width(row.progress()), py = rowY + 6 + row.lineCount() * 11;
					QuestSkin.text(g, font, row.progress(), x + w - 6 - count, py, QuestSkin.DIM, false);
					if (row.fraction() >= 0)
						QuestSkin.bar(g, x + 26, py + 3, Math.max(1, w - 38 - count), 2, row.fraction(),
								row.fraction() >= 1 ? QuestSkin.DONE : QuestSkin.ACTIVE);
				}
			}
			rowY += h;
		}
		g.disableScissor();
	}

	private int detailHeight(DetailRow row) {
		if (row.rich() != null)
			return row.richHeight();
		if (row.heading())
			return 24;
		return row.icon().isEmpty() ? row.lineCount() * 11 + 5
				: Math.max(28, row.lineCount() * 11 + 14 + (row.progress() != null ? 11 : 0));
	}

	private JsonArray selectRichDescription(Map<String, JsonArray> values, String language) {
		var value = values.get(language);
		if (value != null && !value.isEmpty())
			return value;
		value = values.get("en_us");
		if (value != null && !value.isEmpty())
			return value;
		return values.entrySet().stream().filter(e -> e.getValue() != null && !e.getValue().isEmpty())
				.sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).findFirst().orElse(null);
	}

	private void appendRichRow(List<DetailRow> lines, JsonArray parts, int width) {
		int height = 0;
		int textPixels = 0, maxLine = 11, breaks = 1;
		for (var el : parts) {
			var part = el.getAsJsonObject();
			if ("image".equals(part.get("type").getAsString())) {
				height = 12 + Math.min(280, part.get("height").getAsInt());
				break;
			}
			float scale = part.has("sizePx") ? Math.clamp(part.get("sizePx").getAsFloat() / 9f, 6f / 9f, 128f / 9f)
					: part.has("size") ? Math.clamp(part.get("size").getAsFloat(), .75f, 2f) : 1f;
			maxLine = Math.max(maxLine, (int) Math.ceil(11 * scale));
			String value = part.has("text") ? part.get("text").getAsString() : "";
			for (String fragment : value.split("\\n", -1))
				textPixels += (int) Math.ceil(font.width(fragment) * scale);
			breaks += (int) value.chars().filter(c -> c == '\n').count();
		}
		if (height == 0)
			height = Math.min(3000,
					12 + Math.max(breaks, (int) Math.ceil(textPixels / (double) Math.max(1, Math.min(400, width) - 15)))
							* maxLine);
		var note = new JsonObject();
		note.addProperty("style", "label");
		note.addProperty("width", Math.max(60, Math.min(400, width)));
		note.addProperty("height", Math.min(300, Math.max(20, height - 8)));
		var rich = new JsonObject();
		rich.add("en_us", parts);
		note.add("rich", rich);
		lines.add(new DetailRow(List.of(), ItemStack.EMPTY, null, null, note, height, false, false, -1));
	}

	private int richDetailHeight(JsonObject part, int width) {
		if ("image".equals(part.get("type").getAsString()))
			return 12 + Math.min(280, part.has("height") ? part.get("height").getAsInt() : 80);
		String value = part.has("text") ? part.get("text").getAsString() : "";
		float scale = part.has("sizePx") ? Math.clamp(part.get("sizePx").getAsFloat() / 9f, 6f / 9f, 128f / 9f)
				: part.has("size") ? Math.clamp(part.get("size").getAsFloat(), .75f, 2f) : 1f;
		int lines = 0;
		for (String segment : value.split("\\n", -1))
			lines += Math.max(1, (int) Math.ceil(font.width(segment) * scale / Math.max(1, Math.min(400, width) - 15)));
		return Math.min(3000, 12 + Math.max(1, lines) * (int) Math.ceil(11 * scale));
	}

	private List<String> wrap(String text, int width) {
		List<String> lines = new ArrayList<>();
		for (var line : font.split(Component.literal(text), Math.max(1, width))) {
			StringBuilder b = new StringBuilder();
			line.accept((i, style, cp) -> {
				b.appendCodePoint(cp);
				return true;
			});
			lines.add(b.toString());
		}
		return lines;
	}

	private void append(List<DetailRow> rows, String text, int width) {
		if (!text.isBlank())
			rows.add(new DetailRow(wrap(text, width), ItemStack.EMPTY, null));
	}

	private void heading(List<DetailRow> rows, String text, int width) {
		rows.add(new DetailRow(List.of(QuestSkin.fit(font, text, width * 9 / 10)), ItemStack.EMPTY, null, null, null, 0,
				false, true, -1));
	}

	private void appendTile(List<DetailRow> rows, String text, int width, ItemStack item) {
		appendTile(rows, text, width, item, null);
	}

	private void appendTile(List<DetailRow> rows, String text, int width, ItemStack item, String progress) {
		rows.add(new DetailRow(wrap(text, Math.max(1, width - 32)), item, progress));
	}

	private ItemStack iconForObjective(QuestDefinition q, QuestDefinition.Objective o) {
		return QuestObjectiveNames.icon(o, () -> icon(q.icon()));
	}

	private ItemStack recipeForObjective(QuestDefinition.Objective objective) {
		String registry;
		try {
			registry = dev.questplus.core.QuestExtensions.event(objective.event()).registry();
		} catch (IllegalArgumentException unknown) {
			return ItemStack.EMPTY;
		}
		if (!registry.equals("item") && !registry.equals("block"))
			return ItemStack.EMPTY;
		var target = objective.target();
		var id = Identifier.tryParse(target.startsWith("#") ? target.substring(1) : target);
		if (id == null)
			return ItemStack.EMPTY;
		if (registry.equals("item")) {
			if (!target.startsWith("#"))
				return BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
			var tag = BuiltInRegistries.ITEM.get(TagKey.create(Registries.ITEM, id));
			if (tag.isPresent())
				for (var item : tag.get())
					if (item.value() != Items.AIR)
						return new ItemStack(item.value());
		} else {
			if (!target.startsWith("#"))
				return BuiltInRegistries.BLOCK.getOptional(id).map(block -> new ItemStack(block))
						.orElse(ItemStack.EMPTY);
			var tag = BuiltInRegistries.BLOCK.get(TagKey.create(Registries.BLOCK, id));
			if (tag.isPresent())
				for (var block : tag.get())
					if (block.value().asItem() != Items.AIR)
						return new ItemStack(block.value());
		}
		return ItemStack.EMPTY;
	}

	private String objectiveLabelForDisplay(QuestDefinition quest, QuestDefinition.Objective o, JsonObject progress) {
		// The equipment marker describes itself via the condition. Old worlds
		// may still contain generated four-slot text: prefer the short, current
		// requirement instead of stacking it with a second gear description.
		if ("equipment_check".equals(o.event()) && o.conditions() != null) {
			String gear = QuestConditionText.describe(o.conditions(), this::questName);
			if (!gear.isBlank())
				return gear;
		}
		String custom = state.text(o.text());
		String label = custom.isBlank() ? QuestPresentation.objectiveText(o, minecraft.options.languageCode,
				QuestObjectiveNames.target(o), QuestPresentation.target(quest, o, progress)) : custom;
		return QuestConditionText.withConditions(label, o.conditions(), this::questName);
	}

	private ItemStack previewIcon(JsonObject reward) {
		if (reward.has("icon"))
			return icon(reward.get("icon").getAsString());
		if (reward.has("item"))
			return icon(reward.get("item").getAsString());
		return new ItemStack(Set.of("experience", "experience_levels").contains(reward.get("type").getAsString())
				? Items.EXPERIENCE_BOTTLE
				: Items.CHEST);
	}

	private String rewardLabel(JsonObject reward) {
		String type = reward.get("type").getAsString();
		String label = type.equals("item")
				? reward.get("count").getAsInt() + " × " + previewIcon(reward).getHoverName().getString()
				: type.equals("experience") ? t("xp", reward.get("amount").getAsInt()).getString()
						: type.equals("experience_levels") ? t("xp_levels", reward.get("amount").getAsInt()).getString()
								: Set.of("questplus:choice", "questplus:random", "questplus:loot", "questplus:all",
										"questplus:advancement", "questplus:message", "questplus:command",
										"questplus:stage", "questplus:toast").contains(type)
												? t("reward_type." + type).getString()
												: type;
		if (reward.has("advancement"))
			label += " · " + reward.get("advancement").getAsString();
		if (reward.has("message"))
			label += " · " + reward.get("message").getAsString();
		if (reward.has("stage"))
			label += " · " + (reward.get("remove").getAsBoolean() ? "− " : "+ ") + reward.get("stage").getAsString();
		if (type.equals("questplus:toast"))
			label += " · " + rewardText(reward, "title") + " · " + rewardText(reward, "description");
		String title = rewardText(reward, "text");
		if (!title.isBlank())
			return title + " · " + label;
		return label;
	}

	private String rewardText(JsonObject reward, String field) {
		if (!reward.has(field) || !reward.get(field).isJsonObject())
			return "";
		Map<String, String> translations = new HashMap<>();
		reward.getAsJsonObject(field).entrySet().forEach(e -> translations.put(e.getKey(), e.getValue().getAsString()));
		return state.text(translations);
	}

	private void rewardButtons() {
		int x = detailLeft, w = right - x - 8;
		button("back", x, panelTop, w, () -> {
			rewardView = false;
			init();
		});
		var p = state.progress(selected);
		JsonObject receipt = p.has("rewardState") ? p.getAsJsonObject("rewardState") : null;
		button(potentialRewards ? "actual_rewards" : "possible_rewards", x, panelTop + 18, w, () -> {
			potentialRewards = !potentialRewards;
			rewardPage = 0;
			init();
		}).active = receipt != null;
		JsonArray rewards = receipt != null && !potentialRewards ? receipt.getAsJsonArray("rewards")
				: state.rewardPreviews.getOrDefault(selected, new JsonArray());
		int slot = -1;
		if (receipt != null && !potentialRewards && receipt.get("status").getAsString().equals("choice"))
			for (int i = 0; i < rewards.size(); i++)
				if (rewards.get(i).getAsJsonObject().get("type").getAsString().equals("questplus:choice")) {
					slot = i;
					break;
				}
		String token = receipt == null ? null : receipt.get("token").getAsString();
		JsonObject table = slot >= 0 ? rewards.get(slot).getAsJsonObject() : null;
		rewardTitle = table == null ? "" : rewardText(table, "text");
		rewardDescription = table == null ? "" : rewardText(table, "description");
		if (slot != choiceSlot || !Objects.equals(token, choiceToken)) {
			choiceSlot = slot;
			choiceToken = token;
			selectedOption = -1;
			pendingChoiceAt = 0;
			rewardPage = 0;
		}
		String revealKey = token + ":" + (receipt == null ? "preview" : receipt.get("status").getAsString());
		if (!Objects.equals(rewardRevealKey, revealKey)) {
			rewardRevealKey = revealKey;
			rewardRevealAt = System.currentTimeMillis();
		}
		List<RewardCard> cards = new ArrayList<>();
		if (slot >= 0)
			for (var option : rewards.get(slot).getAsJsonObject().getAsJsonArray("options"))
				cards.add(new RewardCard(option.getAsJsonObject(), rewardLabel(option.getAsJsonObject())));
		else {
			for (var value : rewards)
				appendRewardCards(cards, value.getAsJsonObject());
			if (receipt == null || potentialRewards)
				for (var tier : state.tierRewardPreviews.getOrDefault(selected, new JsonArray()))
					for (var value : tier.getAsJsonArray())
						appendRewardCards(cards, value.getAsJsonObject());
		}
		rewardCards = List.copyOf(cards);
		int pages = Math.max(1, (cards.size() + 3) / 4);
		rewardPage = Math.clamp(rewardPage, 0, pages - 1);
		boolean saving = QuestClientState.flag(p, "saving") || pendingChoiceAt != 0;
		for (int i = rewardPage * 4; i < Math.min(cards.size(), rewardPage * 4 + 4); i++) {
			int option = i;
			var card = cards.get(i);
			var widget = addRenderableWidget(
					new QuestButton(Component.literal(card.label()), x, panelTop + 76 + (i % 4) * 25, w, 23, () -> {
						selectedOption = option;
						init();
					}));
			widget.icon(previewIcon(card.reward()))
					.hoverText(Component
							.literal(card.label() + (rewardDescription.isBlank() ? "" : "\n" + rewardDescription)))
					.selected(slot >= 0 && selectedOption == i);
			widget.active = slot >= 0 && !saving;
			hoverButtons.add(widget);
		}
		// One page needs no pager.
		if (pages > 1) {
			button("previous", x, panelTop + 178, w * 2 / 5, () -> {
				rewardPage--;
				init();
			}).active = rewardPage > 0;
			button("next", x + w - w * 2 / 5, panelTop + 178, w * 2 / 5, () -> {
				rewardPage++;
				init();
			}).active = rewardPage + 1 < pages;
		}
		if (slot >= 0) {
			var confirm = button(saving ? "processing" : "confirm_choice", x, panelTop + 197, w, () -> {
				pendingChoiceAt = System.currentTimeMillis();
				chooseReward(selected, choiceToken, choiceSlot, selectedOption);
				init();
			});
			confirm.active = !saving && selectedOption >= 0;
		} else if (!potentialRewards && QuestClientState.flag(p, "claimable"))
			button("claim", x, panelTop + 197, w, () -> action("claim", selected)).accent(true);
	}

	private void appendRewardCards(List<RewardCard> cards, JsonObject reward) {
		if (!reward.has("options")) {
			cards.add(new RewardCard(reward, rewardLabel(reward)));
			return;
		}
		String type = reward.get("type").getAsString();
		double total = reward.has("emptyWeight") ? reward.get("emptyWeight").getAsDouble() : 0;
		for (var option : reward.getAsJsonArray("options"))
			if (option.getAsJsonObject().has("weight"))
				total += option.getAsJsonObject().get("weight").getAsDouble();
		for (var value : reward.getAsJsonArray("options")) {
			var option = value.getAsJsonObject();
			String chance = "";
			if (type.equals("questplus:all"))
				chance = " · 100%";
			else if (option.has("weight"))
				chance = String.format(Locale.ROOT, " · %.2f%%",
						total == 0 ? 0
								: option.get("weight").getAsDouble() == 0 ? 100
										: option.get("weight").getAsDouble() * 100 / total);
			cards.add(new RewardCard(option, rewardLabel(option) + chance));
		}
		if (reward.has("emptyWeight") && reward.get("emptyWeight").getAsInt() > 0) {
			var empty = new JsonObject();
			empty.addProperty("type", "empty");
			cards.add(new RewardCard(empty, t("no_rewards").getString()
					+ String.format(Locale.ROOT, " · %.2f%%", reward.get("emptyWeight").getAsDouble() * 100 / total)));
		}
	}

	private void drawRewardView(GuiGraphicsExtractor g) {
		int x = detailLeft, w = right - x - 8;
		var p = state.progress(selected);
		var receipt = p.has("rewardState") ? p.getAsJsonObject("rewardState") : null;
		String status = QuestClientState.flag(p, "saving") || pendingChoiceAt != 0 ? "processing"
				: receipt == null || potentialRewards ? "possible_rewards"
						: receipt.get("status").getAsString().equals("choice") ? "choose" : "actual_rewards";
		String title = rewardTitle.isBlank() || status.equals("processing") ? t(status).getString() : rewardTitle;
		QuestSkin.text(g, font, QuestSkin.bold(QuestSkin.fit(font, title, w * 9 / 10)), x, panelTop + 40,
				QuestSkin.INK, false);
		String hint = receipt == null || potentialRewards ? t("per_roll").getString()
				: t("reward_status." + receipt.get("status").getAsString()).getString();
		if (!rewardDescription.isBlank())
			hint = rewardDescription;
		List<String> hintLines = wrap(hint, (int) (w / QuestSkin.TEXT_SCALE));
		for (int i = 0; i < Math.min(2, hintLines.size()); i++)
			QuestSkin.text(g, font, hintLines.get(i), x, panelTop + 52 + i * 11, QuestSkin.DIM, false);
		if (rewardCards.size() > 4)
			QuestSkin.centered(g, font, Component.literal((rewardPage + 1) + " / " + (rewardCards.size() + 3) / 4),
					x + w / 2, panelTop + 182, QuestSkin.DIM);
		if (settings.animations && !settings.reducedMotion && receipt != null && !potentialRewards) {
			double reveal = Math.clamp((System.currentTimeMillis() - rewardRevealAt) / 400.0, 0, 1);
			if (reveal < 1)
				g.fill(x, panelTop + 73, x + (int) (w * reveal), panelTop + 74, QuestSkin.SELECTED);
		}
		if (rewardCards.isEmpty())
			QuestSkin.text(g, font, t("no_rewards").getString(), x, panelTop + 80, QuestSkin.DIM, false);
	}

	private String questName(String id) {
		var quest = state.quests.get(id);
		return quest == null ? t("unknown_quest").getString() : state.text(quest.title());
	}

	private String condition(QuestDefinition.Condition c) {
		return QuestConditionText.describe(c, this::questName);
	}

	private void teamWidgets() {
		input = null;
		lastTeam = state.team;
		lastInvitations = state.invitations;
		int x = left + 14, y = top + 40, w = right - left - 28;
		if (confirmation != null) {
			String[] parts = confirmation.split("\n", 2);
			button("confirm", right - 170, top + 90, 74, () -> {
				action(parts[0], parts[1]);
				confirmation = null;
				init();
			});
			button("cancel", right - 90, top + 90, 76, () -> {
				confirmation = null;
				init();
			});
			return;
		}
		if (state.team == null) {
			teamOptions = false;
			input = addRenderableWidget(new QuestTextField(font, x, y, w - 85, 18, t("team_name")));
			input.setMaxLength(64);
			input.setHint(t("team_name"));
			button("create", right - 90, y, 76, () -> action("create", input.getValue()));
			y += 30;
			int capacity = Math.max(1, (bottom - y - 38) / 25), start = teamPage * capacity;
			teamPage = Math.clamp(teamPage, 0, Math.max(0, (state.invitations.size() - 1) / capacity));
			start = teamPage * capacity;
			for (int i = start; i < Math.min(state.invitations.size(), start + capacity); i++) {
				var invitation = state.invitations.get(i).getAsJsonObject();
				String id = invitation.get("id").getAsString();
				int yy = y + (i - start) * 25;
				button("join", right - 155, yy, 66, () -> action("join", id));
				button("decline", right - 85, yy, 71, () -> action("decline", id));
			}
			if (state.invitations.size() > capacity)
				pages(capacity, state.invitations.size());
		} else {
			TeamRole role = teamRole();
			boolean owner = role == TeamRole.OWNER;
			teamOptions &= role.canManage();
			if (role.canManage())
				button(teamOptions ? "back" : "team_options", right - 242, top + 28, 112, () -> {
					teamOptions = !teamOptions;
					teamPage = 0;
					playerPicker = false;
					init();
				});
			if (teamOptions) {
				input = addRenderableWidget(new QuestTextField(font, x, top + 69, w - 85, 18, t("team_name")));
				input.setMaxLength(64);
				input.setValue(state.team.get("name").getAsString());
				button("rename", right - 90, top + 69, 76, () -> action("rename", input.getValue()));
				boolean shared = QuestClientState.flag(state.team, "sharedQuestProgress");
				button(shared ? "sharing_on" : "sharing_off", x, top + 98, w,
						() -> confirm(shared ? "share_off" : "share_on", ""));
				var invitations = state.team.getAsJsonArray("invitations");
				int capacity = Math.max(1, (bottom - top - 175) / 23);
				teamPage = Math.clamp(teamPage, 0, Math.max(0, (invitations.size() - 1) / capacity));
				int start = teamPage * capacity;
				for (int i = start; i < Math.min(invitations.size(), start + capacity); i++) {
					String id = invitations.get(i).getAsJsonObject().get("id").getAsString();
					button("revoke", right - 104, top + 151 + (i - start) * 23, 90, () -> action("revoke", id));
				}
				if (invitations.size() > capacity)
					pages(capacity, invitations.size());
			} else if (role.canManage()) {
				input = addRenderableWidget(new QuestTextField(font, x, top + 69, w - 85, 18, t("player_name")));
				input.setMaxLength(36);
				input.setHint(t("player_name"));
				input.setResponder(value -> {
					playerScroll = 0;
					playerPicker = true;
				});
				button("invite", right - 90, top + 69, 76, () -> {
					String value = input.getValue();
					var player = minecraft.getConnection().getPlayerInfoIgnoreCase(value);
					action("invite", player == null ? value : player.getProfile().id().toString());
					playerPicker = false;
				});
			}
			var members = state.team.getAsJsonArray("members");
			int capacity = teamMemberCapacity();
			if (!teamOptions)
				teamPage = Math.clamp(teamPage, 0, Math.max(0, (members.size() - 1) / capacity));
			int start = teamPage * capacity;
			if (!teamOptions)
				for (int i = start; i < Math.min(members.size(), start + capacity); i++) {
					var member = members.get(i).getAsJsonObject();
					String id = member.get("id").getAsString();
					TeamRole target = TeamRole.valueOf(member.get("role").getAsString());
					int yy = top + 102 + (i - start) * 32;
					if (role.canKick(target))
						button("kick", right - 143, yy, 58, () -> confirm("kick", id));
					if (owner && target != TeamRole.OWNER) {
						String change = target == TeamRole.MANAGER ? "demote" : "promote";
						button(change, right - 225, yy, 78, () -> confirm(change, id));
						button("transfer", right - 81, yy, 67, () -> confirm("transfer", id));
					}
				}
			if (!teamOptions && members.size() > capacity)
				pages(capacity, members.size());
			button(owner ? "disband" : "leave", right - 126, top + 28, 112,
					() -> confirm(owner ? "disband" : "leave", ""));
			if (QuestClientState.flag(state.team, "saving"))
				for (var widget : children())
					if (widget instanceof AbstractWidget control && control.getY() >= top + 24)
						control.active = false;
		}
	}

	private TeamRole teamRole() {
		return TeamRole.valueOf(state.team.get("role").getAsString());
	}

	private int teamMemberCapacity() {
		return Math.max(1, (bottom - top - 124) / 32);
	}

	private void pages(int capacity, int total) {
		int y = bottom - 22;
		button("previous", right - 126, y, 52, () -> {
			teamPage = Math.max(0, teamPage - 1);
			init();
		});
		button("next", right - 70, y, 56, () -> {
			teamPage = Math.min((total - 1) / capacity, teamPage + 1);
			init();
		});
	}

	private void confirm(String action, String argument) {
		confirmation = action + "\n" + argument;
		init();
	}

	private void drawTeam(GuiGraphicsExtractor g) {
		int x = left + 14;
		if (confirmation != null) {
			String[] parts = confirmation.split("\n", 2);
			QuestSkin.text(g, font, t("confirm_hint"), x, top + 44, QuestSkin.DIM, false);
			String target = parts[1];
			if (state.team != null)
				for (var entry : state.team.getAsJsonArray("members")) {
					var member = entry.getAsJsonObject();
					if (member.get("id").getAsString().equals(target) && member.has("name"))
						target = member.get("name").getAsString();
				}
			QuestSkin.text(g, font, t(parts[0]).getString() + (target.isBlank() ? "" : ": " + target), x,
					top + 62, QuestSkin.INK, false);
			return;
		}
		if (state.team == null) {
			int capacity = Math.max(1, (bottom - (top + 70) - 38) / 25), start = teamPage * capacity;
			for (int i = start; i < Math.min(state.invitations.size(), start + capacity); i++) {
				var invitation = state.invitations.get(i).getAsJsonObject();
				QuestSkin.text(g, font,
						font.plainSubstrByWidth(invitation.get("name").getAsString(), Math.max(30, right - left - 183)),
						x, top + 75 + (i - start) * 25, QuestSkin.INK, false);
			}
			if (state.invitations.isEmpty())
				QuestSkin.text(g, font, t("no_invitations"), x, top + 79, QuestSkin.DIM, false);
		} else {
			QuestSkin.big(g, font, QuestSkin.fit(font, state.team.get("name").getAsString(), (right - left - 270) * 3 / 5),
					x, top + 28, QuestSkin.INK);
			String ownerName = state.team.get("owner").getAsString();
			for (var member : state.team.getAsJsonArray("members")) {
				var m = member.getAsJsonObject();
				if (m.get("id").getAsString().equals(ownerName) && m.has("name")) {
					ownerName = m.get("name").getAsString();
					break;
				}
			}
			QuestSkin.text(g, font, t("owner", ownerName), x, top + 47, QuestSkin.DIM, false);
			QuestSkin.text(g, font, t(QuestClientState.flag(state.team, "sharedQuestProgress") ? "scope_team" : "scope_personal"),
					x, top + 57, QuestSkin.DIM, false);
			if (teamOptions) {
				QuestSkin.text(g, font, t("sharing_hint"), x, top + 120, QuestSkin.DIM, false);
				QuestSkin.text(g, font, t("sent_invitations"), x, top + 136, QuestSkin.INK, false);
				var invitations = state.team.getAsJsonArray("invitations");
				int capacity = Math.max(1, (bottom - top - 175) / 23), start = teamPage * capacity;
				for (int i = start; i < Math.min(invitations.size(), start + capacity); i++) {
					var invite = invitations.get(i).getAsJsonObject();
					String name = invite.has("name") ? invite.get("name").getAsString() : invite.get("id").getAsString();
					QuestSkin.text(g, font, font.plainSubstrByWidth(name, right - left - 125), x,
							top + 155 + (i - start) * 23, QuestSkin.INK, false);
				}
				return;
			}
			var members = state.team.getAsJsonArray("members");
			int capacity = teamMemberCapacity(), start = teamPage * capacity;
			for (int i = start; i < Math.min(members.size(), start + capacity); i++) {
				var m = members.get(i).getAsJsonObject();
				String name = m.has("name") ? m.get("name").getAsString() : m.get("id").getAsString();
				var player = minecraft.getConnection().getPlayerInfo(UUID.fromString(m.get("id").getAsString()));
				int rowY = top + 100 + (i - start) * 32;
				g.fill(x, rowY - 7, right - 14, rowY - 6, QuestSkin.LINE);
				if (player != null)
					PlayerFaceExtractor.extractRenderState(g, player.getSkin(), x, rowY, 16);
				// Presence is a mark: filled when online, hollow when not.
				QuestSkin.panel(g, x + 20, rowY + 1, 6, 6, QuestClientState.flag(m, "online") ? QuestSkin.DONE : 0,
						QuestSkin.INK);
				QuestSkin.text(g, font,
						font.plainSubstrByWidth(name,
								Math.max(30, right - left - (teamRole() == TeamRole.OWNER ? 269 : 187))),
						x + 30, rowY, QuestSkin.INK, false);
				QuestSkin.text(g, font, t("role_" + m.get("role").getAsString().toLowerCase(Locale.ROOT)),
						x + 30, top + 111 + (i - start) * 32, QuestSkin.DIM, false);
			}
		}
	}

	List<net.minecraft.client.multiplayer.PlayerInfo> matchingPlayers() {
		if (minecraft.getConnection() == null || input == null)
			return List.of();
		String query = input.getValue().toLowerCase(Locale.ROOT);
		return minecraft.getConnection().getListedOnlinePlayers().stream()
				.filter(player -> !player.getProfile().id().equals(minecraft.player.getUUID()))
				.filter(player -> player.getProfile().name().toLowerCase(Locale.ROOT).contains(query))
				.sorted(Comparator.comparing(player -> player.getProfile().name(), String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	private int playerPickerRows() {
		return Math.max(0, Math.min(6, (bottom - top - 92) / 20));
	}

	private void drawPlayerPicker(GuiGraphicsExtractor g) {
		if (!tab.equals("team") || state.team == null || !playerPicker || input == null)
			return;
		var players = matchingPlayers();
		int rows = Math.min(playerPickerRows(), Math.max(0, players.size() - playerScroll));
		for (int i = 0; i < rows; i++) {
			var player = players.get(playerScroll + i);
			int y = top + 88 + i * 20;
			g.fill(left + 14, y, right - 99, y + 20, QuestSkin.FIELD);
			g.fill(left + 14, y + 19, right - 99, y + 20, QuestSkin.LINE);
			PlayerFaceExtractor.extractRenderState(g, player.getSkin(), left + 17, y + 2, 16);
			QuestSkin.text(g, font, player.getProfile().name(), left + 38, y + 6, QuestSkin.INK, false);
		}
	}

	private void settingsWidgets() {
		int x = left + 14, y = top + 28, w = (right - left - 36) / 2;
		// Leave room for all eight rows at the largest interface scale.
		int available = bottom - y - 32;
		int row = Math.max(16, Math.min(20, (available - 15) / 7));
		String[] names = { "tablet", "blur", "animations", "reduced_motion", "notifications", "hud" };
		boolean[] values = { settings.tablet, TabletShaderCompat.backgroundBlurEnabled(), settings.animations,
				settings.reducedMotion, settings.notifications, settings.hud };
		for (int i = 0; i < names.length; i++) {
			final int n = i;
			QuestButton b = button(names[i], x + (i % 2) * (w + 8), y + (i / 2) * row, w, () -> {
				switch (n) {
				case 0 -> settings.tablet = !settings.tablet;
				case 1 -> settings.blur = !settings.blur;
				case 2 -> settings.animations = !settings.animations;
				case 3 -> settings.reducedMotion = !settings.reducedMotion;
				case 4 -> settings.notifications = !settings.notifications;
				case 5 -> settings.hud = !settings.hud;
				}
				settings.save();
				init();
			});
			b.selected(values[i]).setMessage(t(names[i]).copy().append(": ").append(t(values[i] ? "on" : "off")));
			if (n == 1 && TabletShaderCompat.shadersActive()) {
				b.active = false;
				b.hoverText(t("blur_shader_disabled"));
			}
		}
		QuestButton hudAnchor = button("hud_position", x, y + 3 * row, w, () -> {
			settings.hudPosition = (settings.hudPosition + 1) % 9;
			settings.save();
			init();
		});
		Component hudLabel = t("hud_position").copy().append(": ").append(t("position." + settings.hudPosition));
		hudAnchor.setMessage(hudLabel);
		hudAnchor.hoverText(hudLabel);
		QuestButton notificationAnchor = button("notification_position", x + w + 8, y + 3 * row, w, () -> {
			settings.notificationPosition = (settings.notificationPosition + 1) % 9;
			settings.save();
			init();
		});
		Component notificationLabel = t("notification_position").copy().append(": ")
				.append(t("position." + settings.notificationPosition));
		notificationAnchor.setMessage(notificationLabel);
		notificationAnchor.hoverText(notificationLabel);
		button("scale", x, y + 4 * row, w, () -> {
			pendingInterfaceScale = settings.scale >= 1.25 ? 0.75 : settings.scale + 0.25;
		}).setMessage(t("scale", Math.round(settings.scale * 100)));
		button("blur_intensity", x + w + 8, y + 4 * row, w, () -> {
			settings.blurIntensity = settings.blurIntensity >= 0.99 ? 0 : Math.min(1, settings.blurIntensity + 0.2);
			settings.save();
			init();
		}).setMessage(t("blur_intensity", Math.round(settings.blurIntensity * 100)));
		button("stay_open_on_damage", x, y + 5 * row, w, () -> {
			settings.stayOpenOnDamage = !settings.stayOpenOnDamage;
			settings.save();
			init();
		}).selected(settings.stayOpenOnDamage).setMessage(
				t("stay_open_on_damage").copy().append(": ").append(t(settings.stayOpenOnDamage ? "on" : "off")));
		button("tablet_transitions", x + w + 8, y + 5 * row, w, () -> {
			settings.tabletTransitions = !settings.tabletTransitions;
			settings.save();
			init();
		}).selected(settings.tabletTransitions).setMessage(
				t("tablet_transitions").copy().append(": ").append(t(settings.tabletTransitions ? "on" : "off")));
		// These options affect the illuminated first-person 3D screen only.
		// The 2D fallback does not use the 3D backlight.
		if (settings.tablet) {
			QuestButton emissive = button("screen_emissive", x, y + 6 * row, w, () -> {
				settings.screenEmissive = !settings.screenEmissive;
				settings.save();
				init();
			});
			Component emissiveLabel = t("screen_emissive").copy().append(": ")
					.append(t(settings.screenEmissive ? "on" : "off"));
			emissive.selected(settings.screenEmissive).setMessage(emissiveLabel);
			emissive.hoverText(emissiveLabel);

			QuestButton brightness = button("screen_brightness", x + w + 8, y + 6 * row, w, () -> {
				int steps = (int) Math.round(settings.screenBrightness * 10);
				settings.screenBrightness = (steps + 1) % 11 / 10.0;
				settings.save();
				init();
			});
			Component brightnessLabel = t("screen_brightness", Math.round(settings.screenBrightness * 100));
			brightness.setMessage(brightnessLabel);
			brightness.hoverText(brightnessLabel);
			QuestButton resolution = button("tree_resolution", x, y + 7 * row, w, () -> {
				settings.treeResolution = settings.treeResolution >= 8 ? 1 : settings.treeResolution + 1;
				settings.save();
				init();
			});
			resolution.setMessage(t("tree_resolution", settings.treeResolution));
			resolution.hoverText(t("tree_resolution_hint", TabletSurfaceRenderer.WIDTH * settings.treeResolution,
					TabletSurfaceRenderer.HEIGHT * settings.treeResolution));
			QuestButton proximity = button("tablet_proximity", x + w + 8, y + 7 * row, w, () -> {
				settings.tabletProximity = settings.tabletProximity >= 130 ? 80 : settings.tabletProximity + 10;
				settings.save();
				init();
			});
			proximity.setMessage(t("tablet_proximity", settings.tabletProximity));
			proximity.hoverText(t("tablet_proximity_hint"));
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (attachedToScreenFace)
			event = tabletEvent(event);
		else
			event = new MouseButtonEvent(twoDX(event.x()), twoDY(event.y()), event.buttonInfo());
		event = new MouseButtonEvent(event.x(), event.y() - visualOffset, event.buttonInfo());
		if (tab.equals("team") && state.team != null && input != null
				&& event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			double x = event.x(), y = event.y();
			if (playerPicker && x >= left + 14 && x < right - 99 && y >= top + 88
					&& y < top + 88 + playerPickerRows() * 20) {
				var players = matchingPlayers();
				int index = playerScroll + (int) ((y - top - 88) / 20);
				if (index < players.size()) {
					input.setValue(players.get(index).getProfile().name());
					playerPicker = false;
				}
				return true;
			}
			playerPicker = x >= left + 14 && x < right - 99 && y >= top + 69 && y < top + 87;
		}
		if (super.mouseClicked(event, doubleClick))
			return true;
		if (!tab.equals("quests") || event.button() != InputConstants.MOUSE_BUTTON_LEFT)
			return false;
		double x = event.x(), y = event.y();
		if (selected != null && !rewardView && QuestRecipeViewer.available())
			for (var target : recipeTargets)
				if (target.contains(x, y, detailsContentTop, bottom - 5))
					return QuestRecipeViewer.open(target.stack());
		if (clickJournal(x, y))
			return true;
		if (x >= left + 8 && x < treeLeft - 5 && y >= railTop && y < railTop + railRows() * 26) {
			int index = categoryScroll + (int) ((y - railTop) / 26);
			var rows = categoryRows();
			if (index < rows.size()) {
				var row = rows.get(index);
				if (row.category() == null) {
					if (!collapsedGroups.add(row.group()))
						collapsedGroups.remove(row.group());
					categoryRowsRevision = -1;
				} else {
					selected = null;
					chooseCategory(row.category().get("id").getAsString());
				}
				init();
			}
			return true;
		}
		if (!overlayDetails && inTree(x, y)) {
			dragging = true;
			dragged = false;
			downX = x;
			downY = y;
			return true;
		}
		return false;
	}

	private boolean inTree(double x, double y) {
		return x >= treeLeft && x < treeRight && y >= treeTop && y < treeBottom;
	}

	private double twoDX(double x) {
		return (x - screenOffsetX) / screenScale;
	}

	private double twoDY(double y) {
		return (y - screenOffsetY) / screenScale;
	}

	private MouseButtonEvent tabletEvent(MouseButtonEvent event) {
		var point = TabletFaceAnchor.pointer(event.x(), event.y());
		return new MouseButtonEvent(point == null ? -10000 : point.x() / TabletSurfaceRenderer.uiScale(),
				point == null ? -10000 : point.y() / TabletSurfaceRenderer.uiScale(), event.buttonInfo());
	}

	@Override
	public void mouseMoved(double x, double y) {
		if (attachedToScreenFace) {
			var point = TabletFaceAnchor.pointer(x, y);
			x = point == null ? -10000 : point.x() / TabletSurfaceRenderer.uiScale();
			y = point == null ? -10000 : point.y() / TabletSurfaceRenderer.uiScale();
		} else {
			x = twoDX(x);
			y = twoDY(y);
		}
		super.mouseMoved(x, y);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (attachedToScreenFace) {
			var previous = TabletFaceAnchor.pointer(event.x() - dx, event.y() - dy);
			event = tabletEvent(event);
			if (previous == null || event.x() < 0) {
				dx = 0;
				dy = 0;
			} else {
				dx = event.x() - previous.x() / TabletSurfaceRenderer.uiScale();
				dy = event.y() - previous.y() / TabletSurfaceRenderer.uiScale();
			}
		} else {
			dx /= screenScale;
			dy /= screenScale;
			event = new MouseButtonEvent(twoDX(event.x()), twoDY(event.y()), event.buttonInfo());
		}
		event = new MouseButtonEvent(event.x(), event.y() - visualOffset, event.buttonInfo());
		if (dragging) {
			if (Math.hypot(event.x() - downX, event.y() - downY) > 3)
				dragged = true;
			if (dragged)
				camera.pan(dx, dy);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (attachedToScreenFace)
			event = tabletEvent(event);
		else
			event = new MouseButtonEvent(twoDX(event.x()), twoDY(event.y()), event.buttonInfo());
		event = new MouseButtonEvent(event.x(), event.y() - visualOffset, event.buttonInfo());
		if (dragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			dragging = false;
			if (!dragged && inTree(event.x(), event.y()))
				hitQuestAtScreen(event.x(), event.y()).filter(this::shown).ifPresent(id -> select(id, false));
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
		if (attachedToScreenFace) {
			var point = TabletFaceAnchor.pointer(x, y);
			if (point == null)
				return false;
			x = point.x() / TabletSurfaceRenderer.uiScale();
			y = point.y() / TabletSurfaceRenderer.uiScale();
		}
		if (!attachedToScreenFace) {
			x = twoDX(x);
			y = twoDY(y);
		}
		y -= visualOffset;
		if (tab.equals("team") && playerPicker && x >= left + 14 && x < right - 99 && y >= top + 88
				&& y < top + 88 + playerPickerRows() * 20) {
			playerScroll = Math.clamp(playerScroll - (int) Math.signum(vertical), 0,
					Math.max(0, matchingPlayers().size() - playerPickerRows()));
			return true;
		}
		if (tab.equals("quests")) {
			if (x >= left + 7 && x < treeLeft && y >= railTop && y < bottom) {
				int count = railRows();
				categoryScroll = Math.clamp(categoryScroll - (int) Math.signum(vertical), 0,
						Math.max(0, categoryRows().size() - count));
				return true;
			}
			if (selected != null && x >= detailLeft) {
				detailsScroll -= (int) (vertical * 24);
				return true;
			}
			if (x >= detailLeft && panelShown()) {
				journalScroll -= (int) (vertical * 18);
				return true;
			}
			if (inTree(x, y)) {
				if (horizontal != 0)
					camera.pan(-horizontal * 36, 0);
				if (vertical != 0)
					camera.zoomAt(Math.pow(QuestCamera.ZOOM_STEP, vertical), x, y);
				return true;
			}
		}
		return super.mouseScrolled(x, y, horizontal, vertical);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_F6 && tab.equals("quests")) {
			updateRenderCache();
			var ids = filtered.stream().sorted().toList();
			if (!ids.isEmpty()) {
				keyboardQuest = ids.get((ids.indexOf(keyboardQuest) + 1) % ids.size());
				clearFocus();
				triggerImmediateNarration(false);
			}
			return true;
		}
		if (event.key() == InputConstants.KEY_RETURN && keyboardQuest != null && !(getFocused() instanceof EditBox)) {
			select(keyboardQuest, false);
			return true;
		}
		if (event.key() == InputConstants.KEY_RETURN && search != null && getFocused() == search && selected == null
				&& !query.isEmpty()) {
			// Enter in the search opens the first result, so the list is usable without a mouse.
			updateJournal();
			journal.stream().filter(row -> row.id() != null).findFirst().ifPresent(row -> select(row.id(), true));
			return true;
		}
		if (event.key() != InputConstants.KEY_ESCAPE && getFocused() instanceof EditBox box && box.isFocused())
			return super.keyPressed(event);
		switch (event.key()) {
		case InputConstants.KEY_LEFT -> camera.pan(24, 0);
		case InputConstants.KEY_RIGHT -> camera.pan(-24, 0);
		case InputConstants.KEY_UP -> camera.pan(0, 24);
		case InputConstants.KEY_DOWN -> camera.pan(0, -24);
		case InputConstants.KEY_HOME -> fit();
		case InputConstants.KEY_EQUALS, InputConstants.KEY_ADD -> camera.zoomAt(QuestCamera.ZOOM_STEP,
				(treeLeft + treeRight) / 2.0, (treeTop + treeBottom) / 2.0);
		case InputConstants.KEY_MINUS -> camera.zoomAt(1 / QuestCamera.ZOOM_STEP, (treeLeft + treeRight) / 2.0,
				(treeTop + treeBottom) / 2.0);
		case InputConstants.KEY_J -> onClose();
		case InputConstants.KEY_ESCAPE -> {
			// Details first, then the search list, then the tablet itself.
			if (selected != null) {
				selected = null;
				rewardView = false;
				init();
			} else if (tab.equals("quests") && !query.isEmpty()) {
				query = "";
				journalScroll = 0;
				init();
			} else
				onClose();
		}
		default -> {
			return super.keyPressed(event);
		}
		}
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public Component getNarrationMessage() {
		var quest = state.quests.get(keyboardQuest);
		return quest == null ? super.getNarrationMessage()
				: Component.literal(state.text(quest.title()) + ". " + t("status." + status(quest.id())).getString());
	}
}
