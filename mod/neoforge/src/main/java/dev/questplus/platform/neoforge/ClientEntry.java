package dev.questplus.platform.neoforge;

import dev.questplus.fabric.client.QuestPlusClient;
import dev.questplus.fabric.client.TabletSurfaceRenderer;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only half of {@link QuestPlusNeoForge}, in its own class so dedicated servers never load it. */
final class ClientEntry {
	/** What Fabric's TabletHudMixin hides: crosshair, vanilla's hotbar-and-decorations, selected item name. */
	private static final Set<Identifier> TABLET_HIDDEN = Set.of(VanillaGuiLayers.CROSSHAIR, VanillaGuiLayers.HOTBAR,
			VanillaGuiLayers.PLAYER_HEALTH, VanillaGuiLayers.ARMOR_LEVEL, VanillaGuiLayers.FOOD_LEVEL, VanillaGuiLayers.VEHICLE_HEALTH,
			VanillaGuiLayers.AIR_LEVEL, VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND, VanillaGuiLayers.EXPERIENCE_LEVEL,
			VanillaGuiLayers.CONTEXTUAL_INFO_BAR, VanillaGuiLayers.SELECTED_ITEM_NAME, VanillaGuiLayers.SPECTATOR_TOOLTIP);

	private ClientEntry() {}

	static void attach(IEventBus modBus) {
		ClientModBus.attach(modBus);
	}

	static void initialize() {
		QuestPlusClient.initialize();
		NeoForge.EVENT_BUS.addListener((RenderGuiLayerEvent.Pre event) -> {
			if (TABLET_HIDDEN.contains(event.getName()) && TabletSurfaceRenderer.hidesHud()) event.setCanceled(true);
		});
	}
}
