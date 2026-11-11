package dev.questplus.fabric.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Tablet-styled control with native keyboard focus and narration, without
 * vanilla button sprites.
 */
public final class QuestButton extends AbstractWidget {
	private static final int BAR_EDGE = 0xff6b5236;
	private final Runnable action;
	private boolean selected, pressed, bar, tab, accent, truncated;
	private ItemStack icon = ItemStack.EMPTY;
	private final Component defaultHoverText;
	private Component hoverText;

	public QuestButton(Component label, int x, int y, int width, int height, Runnable action) {
		super(x, y, width, height, label);
		this.action = action;
		this.defaultHoverText = label;
		// Native tooltips can escape the 500x300 tablet surface.
		// The screen instead renders a bounded hover box.
	}

	public QuestButton selected(boolean value) {
		selected = value;
		return this;
	}

	/** A control sitting on the ink top bar: the selected one becomes a piece of the page. */
	public QuestButton onBar() {
		bar = true;
		return this;
	}

	/** A tab on the ink bar: bare text at rest, a filled tab on hover, the page itself when selected. */
	public QuestButton tab() {
		tab = true;
		return onBar();
	}

	/** The screen's one reward action, in claim red. */
	public QuestButton accent(boolean value) {
		accent = value;
		return this;
	}

	public QuestButton icon(ItemStack value) {
		icon = value == null ? ItemStack.EMPTY : value;
		return this;
	}

	public QuestButton hoverText(Component value) {
		hoverText = value;
		return this;
	}

	/**
	 * A hover box earns its place only when it says more than the control already
	 * shows: an explicit note, a cut label, or the name of a glyph control.
	 */
	public Component hoverText() {
		if (hoverText != null)
			return hoverText;
		if (truncated)
			return getMessage();
		return getMessage().getString().startsWith(defaultHoverText.getString()) ? Component.empty() : defaultHoverText;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		int x = getX(), y = getY();
		boolean hot = isHoveredOrFocused();
		boolean inverse = active && (bar ? !selected : selected || accent);
		int fill = !active ? 0
				: bar ? (selected ? QuestSkin.PAPER : tab && hot ? BAR_EDGE : 0)
						: accent ? (hot ? 0xff96291f : QuestSkin.CLAIM)
								: selected ? (hot ? QuestSkin.DIM : QuestSkin.INK)
										: pressed ? QuestSkin.PANEL : QuestSkin.FIELD;
		int edge = !active ? QuestSkin.LINE
				: bar ? (selected ? (tab && hot ? QuestSkin.INK : QuestSkin.PAPER)
						: tab ? (hot ? BAR_EDGE : 0) : hot ? QuestSkin.FIELD : BAR_EDGE)
						: hot || selected || accent ? QuestSkin.INK : QuestSkin.LINE;
		QuestSkin.panel(g, x, y, width, height, fill, edge);
		var font = QuestSkin.font();
		int iconWidth = icon.isEmpty() ? 0 : 15;
		if (!icon.isEmpty()) {
			g.pose().pushMatrix();
			g.pose().translate(x + 3, y + (height - 12) / 2f);
			g.pose().scale(.75f);
			g.item(icon, 0, 0);
			g.pose().popMatrix();
		}
		int available = Math.max(0, (int) ((width - 8 - iconWidth) / QuestSkin.TEXT_SCALE));
		String full = getMessage().getString(), label = font.plainSubstrByWidth(full, available);
		truncated = !label.equals(full);
		if (truncated && available >= font.width("…"))
			label = font.plainSubstrByWidth(full, available - font.width("…")) + "…";
		int textX = x + iconWidth + (int) ((width - iconWidth - font.width(label) * QuestSkin.TEXT_SCALE) / 2);
		QuestSkin.text(g, font, label, textX, y + (height - 7) / 2,
				inverse ? (accent ? QuestSkin.LIGHT : QuestSkin.FIELD) : active ? QuestSkin.INK : bar ? QuestSkin.LINE : QuestSkin.DIM,
				false);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		pressed = true;
		action.run();
	}

	@Override
	public void onRelease(MouseButtonEvent event) {
		pressed = false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!active || !visible || !isFocused())
			return false;
		if (!event.isSelection())
			return false;
		playDownSound(Minecraft.getInstance().getSoundManager());
		action.run();
		return true;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
