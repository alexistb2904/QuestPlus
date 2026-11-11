package dev.questplus.fabric.client;

import com.google.gson.JsonObject;
import dev.questplus.clientdata.QuestPresentation;
import dev.questplus.core.QuestDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;

final class QuestHud {
	/** Texts are already cut to the panel width: nothing is measured while drawing. */
	private record ObjectiveLine(String label, String modifier, String count, int countWidth, long current,
			long target) {
		int height() {
			return 19 + (modifier.isBlank() ? 0 : 11);
		}
	}

	private record Panel(QuestDefinition quest, String name, String status, List<ObjectiveLine> lines, int height,
			ItemStack icon, String caption, String more) {
	}

	// Quest text changes with server state, locale or panel width, not every rendered frame.
	private static long cachedRevision = Long.MIN_VALUE;
	private static int cachedWidth = -1;
	private static String cachedLanguage;
	private static List<String> cachedPins = List.of();
	private static List<Panel> cachedPanels = List.of();

	private QuestHud() {
	}

	static void draw(GuiGraphicsExtractor g) {
		var mc = Minecraft.getInstance();
		var settings = QuestPlusClient.settings;
		var state = QuestPlusClient.STATE;
		if (mc.player == null || TabletSurfaceRenderer.hidesHud() || mc.gui.screen() instanceof QuestScreen)
			return;

		if (settings.hud && state.ready && !settings.pinned.isEmpty()) {
			float scale = hudScale(g.guiWidth(), g.guiHeight());
			int virtualHeight = (int) (g.guiHeight() / scale), virtualWidth = (int) (g.guiWidth() / scale);
			int width = Math.min(170, virtualWidth - 14);
			if (cachedRevision != state.hudRevision || cachedWidth != width
					|| !Objects.equals(cachedLanguage, mc.options.languageCode) || !samePins(settings.pinned)) {
				cachedPanels = buildPanels(mc, settings, state, width);
				cachedPins = List.copyOf(settings.pinned);
				cachedLanguage = mc.options.languageCode;
				cachedRevision = state.hudRevision;
				cachedWidth = width;
			}
			int maxHeight = Math.max(48, Math.min(virtualHeight - 92, (int) (virtualHeight * .42)));
			int total = 0, count = 0;
			for (Panel panel : cachedPanels) {
				if (count > 0 && total + panel.height() + 5 > maxHeight)
					break;
				total += panel.height() + 5;
				count++;
			}
			if (count > 0) {
				g.pose().pushMatrix();
				g.pose().scale(scale);
				drawPanels(g, mc, cachedPanels, count, total - 4, settings.hudPosition, virtualWidth, virtualHeight);
				g.pose().popMatrix();
			}
		}
		QuestToast.draw(g);
	}

	private static boolean samePins(Set<String> pinned) {
		if (cachedPins.size() != pinned.size())
			return false;
		int i = 0;
		for (String id : pinned)
			if (!Objects.equals(cachedPins.get(i++), id))
				return false;
		return true;
	}

	private static ObjectiveLine line(Minecraft mc, int w, String label, String modifier, long current, long target) {
		String count = target > 0 ? current + "/" + target : "";
		int countWidth = mc.font.width(count);
		return new ObjectiveLine(mc.font.plainSubstrByWidth(label, Math.max(1, w - 14 - countWidth)),
				modifier.isBlank() ? "" : mc.font.plainSubstrByWidth("› " + modifier, w - 16), count, countWidth,
				current, target);
	}

	private static List<Panel> buildPanels(Minecraft mc, ClientSettings settings, QuestClientState state, int w) {
		List<Panel> panels = new ArrayList<>();
		for (String id : settings.pinned) {
			var q = state.quests.get(id);
			if (q == null)
				continue;
			JsonObject progress = state.progress(id), counters = progress.getAsJsonObject("counters");
			List<ObjectiveLine> objectives = new ArrayList<>();
			if (q.kind().equals("information")) {
				if (!QuestClientState.flag(progress, "completed"))
					objectives.add(line(mc, w, QuestPlusClient
							.tr(q.acknowledgement().equals("manual") ? "information_review" : "information_read")
							.getString(), "", 0, 0));
			} else {
				for (var objective : q.objectives()) {
					if (objectives.size() >= 2)
						break;
					long count = counters != null && counters.has(objective.id())
							? counters.get(objective.id()).getAsLong()
							: 0;
					String base = state.text(objective.text());
					if (base.isBlank())
						base = QuestPresentation.objectiveText(objective, mc.options.languageCode,
								QuestObjectiveNames.target(objective), QuestPresentation.target(q, objective, progress));
					String modifier = objective.conditions() == null ? ""
							: QuestConditionText.describe(objective.conditions(), qid -> {
								var other = state.quests.get(qid);
								return other == null ? QuestPlusClient.tr("unknown_quest").getString()
										: state.text(other.title());
							});
					objectives.add(line(mc, w, base, modifier, count, QuestPresentation.target(q, objective, progress)));
				}
			}
			int height = 25 + objectives.stream().mapToInt(ObjectiveLine::height).sum()
					+ (q.objectives().size() > 2 ? 9 : 0);
			String status = QuestPresentation.status(q, progress);
			String caption = status.equals("reward_ready") ? QuestPlusClient.tr("hud.claim").getString()
					: status.equals("completed") ? QuestPlusClient.tr("hud.completed").getString()
							: q.progression().equals("once") ? ""
									: QuestPlusClient.tr("progression." + q.progression()).getString();
			var identifier = Identifier.tryParse(q.icon());
			ItemStack icon = identifier != null && BuiltInRegistries.ITEM.containsKey(identifier)
					? new ItemStack(BuiltInRegistries.ITEM.getValue(identifier))
					: new ItemStack(Items.BOOK);
			panels.add(new Panel(q, mc.font.plainSubstrByWidth(state.text(q.title()), Math.max(1, w - 32)), status,
					List.copyOf(objectives), height, icon,
					caption.isEmpty() ? "" : mc.font.plainSubstrByWidth(caption, w - 30),
					q.objectives().size() > 2
							? QuestPlusClient.tr("hud.more", q.objectives().size() - 2).getString()
							: null));
			if (panels.size() >= ClientSettings.MAX_PINNED)
				break;
		}
		return List.copyOf(panels);
	}

	static float hudScale(int guiWidth, int guiHeight) {
		return (float) Math.clamp(Math.min(guiWidth / 640.0, guiHeight / 390.0), 0.68, 1.0);
	}

	private static void drawPanels(GuiGraphicsExtractor g, Minecraft mc, List<Panel> panels, int panelCount, int height,
			int position, int virtualWidth, int virtualHeight) {
		int w = Math.min(170, virtualWidth - 14);
		int x = position % 3 == 0 ? 7 : position % 3 == 1 ? (virtualWidth - w) / 2 : virtualWidth - w - 7;
		int y = position / 3 == 0 ? 28 : position / 3 == 1 ? (virtualHeight - height) / 2 : virtualHeight - height - 43;
		y = Math.max(5, y);
		for (int i = 0; i < panelCount; i++) {
			Panel panel = panels.get(i);
			boolean reward = panel.status.equals("reward_ready");
			boolean done = panel.status.equals("completed");
			int accent = reward ? 0xffd9b177 : done ? 0xff8d9d87 : 0xffa89986;
			g.fill(x + 1, y + 1, x + w + 1, y + panel.height + 1, 0x22000000);
			g.fill(x, y, x + w, y + panel.height, 0x99151718);
			g.fill(x, y, x + 2, y + panel.height, accent);
			if (!RichAnnotationRenderer.drawIcon(g, panel.quest.icon(), x + 5, y + 3, 16))
				g.item(panel.icon, x + 5, y + 3);
			g.text(mc.font, panel.name, x + 25, y + 3, 0xfff0eae0, false);
			if (!panel.caption.isEmpty())
				g.text(mc.font, panel.caption, x + 25, y + 13, accent, false);

			int row = y + 25;
			for (ObjectiveLine line : panel.lines) {
				g.text(mc.font, line.label, x + 7, row, 0xffd3d1ca, false);
				if (!line.count.isEmpty())
					g.text(mc.font, line.count, x + w - 7 - line.countWidth, row, 0xffa9a7a1, false);
				if (!line.modifier.isBlank())
					g.text(mc.font, line.modifier, x + 9, row + 10, 0xffbfb3a4, false);
				if (!line.count.isEmpty()) {
					int barY = row + (line.modifier.isBlank() ? 11 : 22);
					g.fill(x + 7, barY, x + w - 7, barY + 1, 0xff454341);
					int width = w - 14, progress = (int) (width
							* Math.clamp((double) line.current / Math.max(1, line.target), 0, 1));
					if (progress > 0)
						g.fill(x + 7, barY, x + 7 + progress, barY + 1, accent);
				}
				row += line.height();
			}
			if (panel.more != null)
				g.text(mc.font, panel.more, x + 7, row - 2, 0xff979591, false);
			y += panel.height + 4;
		}
	}
}
