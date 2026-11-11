package dev.questplus.fabric.client;

import com.google.gson.*;
import dev.questplus.clientdata.QuestPresentation;
import dev.questplus.core.QuestDefinition;
import dev.questplus.fabric.QuestGiver;
import dev.questplus.fabric.QuestGiverNetwork;
import dev.questplus.fabric.client.ui.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;
import static dev.questplus.fabric.client.QuestBlockChrome.*;

/**
 * An NPC hands out its quest: the NPC itself in a portrait at the left, its
 * words at the right, then what it needs and what it pays. Declining only
 * closes the dialog; the offer stays open for later.
 */
public final class QuestGiverScreen extends Screen {
	private static final Gson JSON = new Gson();
	/** The minimum height still frames the whole NPC in its portrait. */
	private static final int MAX_WIDTH = 420, MAX_HEIGHT = 300, MIN_HEIGHT = 196, PORTRAIT = 92, ACTION = 88, TILE = 22,
			CELL = 24;
	/** Up to this many rewards read as labelled tiles; more become an icon grid named in the hover box. */
	private static final int REWARD_TILES = 4;
	private final net.minecraft.client.gui.Font font = QuestSkin.font();
	private QuestGiverNetwork.Dialog dialog;
	private JsonObject data;
	private int page, left, top, w, h, scroll, contentHeight;
	private String hover;

	/** One laid-out piece of the speech column; {@code x}/{@code y} are relative to the column. */
	private interface Block {
		int height();

		void draw(GuiGraphicsExtractor g, int x, int y, int width, int mx, int my);
	}

	private final List<Block> blocks = new ArrayList<>();

	public QuestGiverScreen(QuestGiverNetwork.Dialog packet) {
		super(Component.translatable("entity.questplus.quest_giver"));
		receive(packet);
	}

	private static Component tr(String key, Object... args) {
		return Component.translatable("questplus.npc." + key, args);
	}

	public int entity() {
		return dialog.entity();
	}

	public void receive(QuestGiverNetwork.Dialog packet) {
		dialog = packet;
		data = JsonParser.parseString(packet.data()).getAsJsonObject();
		page = Math.clamp(page, 0, Math.max(0, quests().size() - 1));
		if (width > 0)
			rebuildWidgets();
	}

	private JsonArray quests() {
		return data.getAsJsonArray("quests");
	}

	private JsonObject current() {
		return quests().isEmpty() ? null : quests().get(page).getAsJsonObject();
	}

	private String npcName() {
		return data.get("npc").getAsString();
	}

	private void send(String action) {
		var entry = current();
		ClientPlayNetworking.send(new QuestGiverNetwork.Request(dialog.entity(), dialog.token(), action,
				entry == null ? "" : entry.getAsJsonObject("quest").get("id").getAsString()));
	}

	/** The same NPC always picks the same line for the same quest, so it reads like a character, not a lottery. */
	private Component line(String key, int variants, String salt) {
		return tr(key + "." + Math.floorMod((npcName() + salt).hashCode(), variants));
	}

	private boolean portrait() {
		return w >= 330;
	}

	private int columnX() {
		return portrait() ? left + PAD + PORTRAIT + 10 : left + PAD;
	}

	private int columnWidth() {
		// Leaves room for the 2px scroll rail at the right.
		return left + w - PAD - 6 - columnX();
	}

	private int bodyTop() {
		return top + BAR + PAD;
	}

	private int bodyBottom() {
		return footerY(top, h) - 10 - (data.get("error").getAsString().isEmpty() ? 0 : 13);
	}

	@Override
	protected void init() {
		w = Math.min(MAX_WIDTH, width - 16);
		left = (width - w) / 2;
		var entry = current();
		layout(entry);
		// Short exchanges get a short window; long offers use the full height and scroll.
		h = Math.min(Math.min(MAX_HEIGHT, height - 16), Math.max(MIN_HEIGHT, BAR + PAD + contentHeight + 10 + FOOTER + 8));
		top = (height - h) / 2;
		addRenderableWidget(new QuestButton(Component.literal("✕"), left + w - PAD - ROW, top + (BAR - ROW) / 2, ROW, ROW,
				this::onClose).onBar().hoverText(tr("close")));
		int footer = footerY(top, h), right = left + w - PAD;
		switch (entry == null ? "" : entry.get("status").getAsString()) {
		case "offer" -> {
			addRenderableWidget(new QuestButton(tr("accept"), right - ACTION, footer, ACTION, ROW, () -> send("accept")))
					.selected(true);
			addRenderableWidget(new QuestButton(tr("decline"), right - 2 * ACTION - GAP, footer, ACTION, ROW, this::onClose));
		}
		case "ready" -> addRenderableWidget(
				new QuestButton(tr("claim"), right - 2 * ACTION - GAP, footer, 2 * ACTION + GAP, ROW, () -> send("claim")))
				.accent(true);
		default -> addRenderableWidget(new QuestButton(tr("farewell"), right - ACTION, footer, ACTION, ROW, this::onClose));
		}
		if (quests().size() > 1) {
			addRenderableWidget(new QuestButton(Component.literal("‹"), left + PAD, footer, ROW, ROW, () -> turn(-1)))
					.hoverText(tr("previous"));
			addRenderableWidget(new QuestButton(Component.literal("›"), left + PAD + ROW + 44, footer, ROW, ROW, () -> turn(1)))
					.hoverText(tr("next"));
		}
		scroll = Math.clamp(scroll, 0, maxScroll());
	}

	private void turn(int step) {
		page = Math.floorMod(page + step, quests().size());
		scroll = 0;
		rebuildWidgets();
	}

	private void layout(JsonObject entry) {
		blocks.clear();
		int wrap = columnWidth();
		if (entry == null) {
			farewell(wrap);
		} else {
			var quest = JSON.fromJson(entry.get("quest"), QuestDefinition.class);
			var progress = entry.getAsJsonObject("progress");
			String status = entry.get("status").getAsString();
			speech(line("greet." + status, status.equals("offer") ? 3 : 2, quest.id()), QuestSkin.DIM, wrap);
			gap(3);
			title(QuestPlusClient.STATE.text(quest.title()), wrap);
			String description = QuestPlusClient.STATE.text(quest.description());
			if (!description.isBlank()) {
				var prepared = QuestDescriptionText.prepare(description);
				for (var row : font.split(prepared.component(), wrap))
					paragraph(QuestSkin.onPaper(prepared.restore(row)), QuestSkin.INK);
			}
			objectives(quest, progress, status);
			rewards(entry.getAsJsonObject("quest").getAsJsonArray("rewardPreview"), wrap);
		}
		contentHeight = blocks.stream().mapToInt(Block::height).sum();
	}

	/** Nothing on offer: the NPC says why, in character. */
	private void farewell(int wrap) {
		var completed = data.has("completed") ? data.getAsJsonArray("completed") : new JsonArray();
		int locked = data.has("locked") ? data.get("locked").getAsInt() : 0;
		int resting = data.has("resting") ? data.get("resting").getAsInt() : 0;
		if (!completed.isEmpty()) {
			String last = completed.get(completed.size() - 1).getAsString();
			speech(completed.size() == 1 ? tr("done.one", last) : tr("done.many", last, completed.size() - 1), QuestSkin.INK, wrap);
			gap(4);
		}
		if (resting > 0)
			speech(tr("resting"), QuestSkin.INK, wrap);
		else if (locked > 0)
			speech(tr("locked"), QuestSkin.INK, wrap);
		else if (!completed.isEmpty())
			speech(line("done.after", 3, ""), QuestSkin.INK, wrap);
		else
			speech(line("nothing", 2, ""), QuestSkin.INK, wrap);
	}

	private void objectives(QuestDefinition quest, JsonObject progress, String status) {
		if (quest.objectives().isEmpty())
			return;
		heading(tr("need"));
		var counters = progress.getAsJsonObject("counters");
		boolean tracked = !status.equals("offer");
		for (var o : quest.objectives()) {
			long target = QuestPresentation.target(quest, o, progress);
			long current = Math.min(target, counters.has(o.id()) ? counters.get(o.id()).getAsLong() : 0);
			String custom = QuestPlusClient.STATE.text(o.text());
			String label = custom.isBlank()
					? QuestPresentation.objectiveText(o, minecraft.options.languageCode, QuestObjectiveNames.target(o), target)
					: custom;
			var icon = QuestObjectiveNames.icon(o, () -> item(quest.icon()));
			tile(icon, label, tracked ? current + " / " + target : "", tracked ? (double) current / Math.max(1, target) : -1);
		}
	}

	private void rewards(JsonArray rewards, int wrap) {
		if (rewards == null || rewards.isEmpty())
			return;
		heading(tr("reward"));
		if (rewards.size() <= REWARD_TILES) {
			for (var reward : rewards)
				tile(rewardIcon(reward.getAsJsonObject()), rewardLabel(reward.getAsJsonObject()), "", -1);
			return;
		}
		int columns = Math.max(1, (wrap + 2) / CELL);
		List<JsonObject> all = rewards.asList().stream().map(JsonElement::getAsJsonObject).toList();
		for (int start = 0; start < all.size(); start += columns) {
			var row = all.subList(start, Math.min(all.size(), start + columns));
			blocks.add(new Block() {
				public int height() {
					return CELL;
				}

				public void draw(GuiGraphicsExtractor g, int x, int y, int width, int mx, int my) {
					for (int i = 0; i < row.size(); i++) {
						var reward = row.get(i);
						int cx = x + i * CELL;
						boolean hot = mx >= cx && mx < cx + CELL - 2 && my >= y && my < y + CELL - 2;
						QuestSkin.panel(g, cx, y, CELL - 2, CELL - 2, QuestSkin.FIELD, hot ? QuestSkin.INK : QuestSkin.LINE);
						var icon = rewardIcon(reward);
						g.item(icon, cx + 3, y + 3);
						// The game's own font for counts, like every inventory slot; amounts read without hovering.
						int amount = reward.has("count") ? reward.get("count").getAsInt()
								: reward.has("amount") ? reward.get("amount").getAsInt() : 1;
						if (amount > 1)
							g.itemDecorations(minecraft.font, icon, cx + 3, y + 3,
									amount > 999 ? amount / 1000 + "k" : String.valueOf(amount));
						if (hot)
							hover = rewardLabel(reward);
					}
				}
			});
		}
	}

	private void speech(Component text, int color, int wrap) {
		for (var row : font.split(text, wrap))
			paragraph(row, color);
	}

	private void paragraph(FormattedCharSequence row, int color) {
		blocks.add(new Block() {
			public int height() {
				return 11;
			}

			public void draw(GuiGraphicsExtractor g, int x, int y, int width, int mx, int my) {
				g.text(font, row, x, y + 1, color, false);
			}
		});
	}

	/** The screen's one Title line, wrapped rather than cut when the quest name is long. */
	private void title(String text, int wrap) {
		for (var row : font.getSplitter().splitLines(QuestSkin.title(text), wrap, Style.EMPTY)) {
			String part = row.getString();
			blocks.add(new Block() {
				public int height() {
					return 19;
				}

				public void draw(GuiGraphicsExtractor g, int x, int y, int width, int mx, int my) {
					QuestSkin.big(g, font, part, x, y + 2, QuestSkin.INK);
				}
			});
		}
		gap(2);
	}

	/** Bold dim heading over a 1px rule, with more air above than below. */
	private void heading(Component text) {
		blocks.add(new Block() {
			public int height() {
				return 22;
			}

			public void draw(GuiGraphicsExtractor g, int x, int y, int width, int mx, int my) {
				QuestSkin.text(g, font, QuestSkin.bold(text.getString()), x, y + 8, QuestSkin.DIM, false);
				g.fill(x, y + 19, x + width, y + 20, QuestSkin.LINE);
			}
		});
	}

	/** A row tile: icon, label cut with an ellipsis, and when tracked a count over a 2px progress bar. */
	private void tile(ItemStack icon, String label, String count, double fraction) {
		blocks.add(new Block() {
			public int height() {
				return TILE + 2;
			}

			public void draw(GuiGraphicsExtractor g, int x, int y, int width, int mx, int my) {
				boolean hot = mx >= x && mx < x + width && my >= y && my < y + TILE;
				QuestSkin.panel(g, x, y, width, TILE, QuestSkin.FIELD, hot ? QuestSkin.INK : QuestSkin.LINE);
				g.item(icon, x + 3, y + 3);
				int countWidth = count.isEmpty() ? 0 : font.width(count) + 6;
				String shown = QuestSkin.fit(font, label, width - 26 - countWidth);
				QuestSkin.text(g, font, shown, x + 22, y + 7, QuestSkin.INK, false);
				if (!count.isEmpty())
					QuestSkin.text(g, font, count, x + width - 4 - font.width(count), y + 7, QuestSkin.DIM, false);
				if (fraction >= 0)
					QuestSkin.bar(g, x + 22, y + TILE - 4, width - 26, 2, fraction,
							fraction >= 1 ? QuestSkin.DONE : QuestSkin.ACTIVE);
				if (hot && !shown.equals(label))
					hover = label;
			}
		});
	}

	private void gap(int size) {
		blocks.add(new Block() {
			public int height() {
				return size;
			}

			public void draw(GuiGraphicsExtractor g, int x, int y, int width, int mx, int my) {
			}
		});
	}

	private static ItemStack item(String id) {
		var key = Identifier.tryParse(id);
		return key != null && BuiltInRegistries.ITEM.containsKey(key) ? new ItemStack(BuiltInRegistries.ITEM.getValue(key))
				: new ItemStack(Items.BARRIER);
	}

	private static ItemStack rewardIcon(JsonObject reward) {
		if (reward.has("icon"))
			return item(reward.get("icon").getAsString());
		if (reward.has("item"))
			return item(reward.get("item").getAsString());
		return new ItemStack(Set.of("experience", "experience_levels").contains(reward.get("type").getAsString())
				? Items.EXPERIENCE_BOTTLE
				: Items.CHEST);
	}

	private static String rewardLabel(JsonObject reward) {
		String type = reward.get("type").getAsString();
		String label = switch (type) {
		case "item" -> reward.get("count").getAsInt() + " × " + rewardIcon(reward).getHoverName().getString();
		case "experience" -> QuestPlusClient.tr("xp", reward.get("amount").getAsInt()).getString();
		case "experience_levels" -> QuestPlusClient.tr("xp_levels", reward.get("amount").getAsInt()).getString();
		default -> type.startsWith("questplus:") ? QuestPlusClient.tr("reward_type." + type).getString() : type;
		};
		if (reward.has("text") && reward.get("text").isJsonObject()) {
			Map<String, String> text = new HashMap<>();
			reward.getAsJsonObject("text").entrySet().forEach(e -> text.put(e.getKey(), e.getValue().getAsString()));
			String title = QuestPlusClient.STATE.text(text);
			if (!title.isBlank())
				return title + " · " + label;
		}
		return label;
	}

	private int maxScroll() {
		return Math.max(0, contentHeight - (bodyBottom() - bodyTop()));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		hover = null;
		String context = quests().size() > 1 ? tr("request", page + 1, quests().size()).getString() : "";
		window(g, font, width, height, left, top, w, h, ItemStack.EMPTY, Component.literal(npcName()), context, ROW + GAP);
		int top0 = bodyTop(), bottom = bodyBottom();
		if (portrait()) {
			int px = left + PAD, ph = footerY(top, h) - 10 - top0;
			QuestSkin.panel(g, px, top0, PORTRAIT, ph, QuestSkin.PANEL, QuestSkin.LINE);
			if (minecraft.level != null && minecraft.level.getEntity(dialog.entity()) instanceof QuestGiver giver)
				QuestGiverRenderer.portrait(g, giver, px + 2, top0 + 2, px + PORTRAIT - 2, top0 + ph - 2,
						Math.min(54, (ph - 16) / 2), mx, my);
		}
		int x = columnX(), cw = columnWidth(), y = top0 - scroll;
		boolean inside = mx >= x && mx < x + cw && my >= top0 && my < bottom;
		g.enableScissor(x, top0, x + cw + 1, bottom);
		for (var block : blocks) {
			if (y + block.height() > top0 && y < bottom)
				block.draw(g, x, y, cw, inside ? mx : -1, inside ? my : -1);
			y += block.height();
		}
		g.disableScissor();
		int maximum = maxScroll();
		if (maximum > 0) {
			// A thin rail at the column's right edge says there is more to read.
			int rail = bottom - top0, thumb = Math.max(12, rail * rail / contentHeight);
			int at = top0 + (rail - thumb) * scroll / maximum;
			g.fill(x + cw + 4, top0, x + cw + 6, bottom, QuestSkin.LINE);
			g.fill(x + cw + 4, at, x + cw + 6, at + thumb, QuestSkin.DIM);
		}
		int foot = footerY(top, h);
		g.fill(left + PAD, foot - 6, left + w - PAD, foot - 5, QuestSkin.LINE);
		String error = data.get("error").getAsString();
		if (!error.isEmpty())
			QuestSkin.text(g, font, QuestSkin.fit(font, error, cw), x, foot - 18, QuestSkin.ALERT, false);
		if (quests().size() > 1)
			QuestSkin.centered(g, font, Component.literal(page + 1 + " / " + quests().size()), left + PAD + ROW + 22, foot + 6,
					QuestSkin.DIM);
		super.extractRenderState(g, mx, my, delta);
		var button = buttonHover(children());
		hoverBox(g, font, width, height, mx, my, button != null ? button : hover == null ? null : Component.literal(hover));
	}

	@Override
	public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
		scroll = Math.clamp(scroll - (int) (vertical * 14), 0, maxScroll());
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		if (ClientPlayNetworking.canSend(QuestGiverNetwork.Request.TYPE))
			send("close");
		super.onClose();
	}
}
