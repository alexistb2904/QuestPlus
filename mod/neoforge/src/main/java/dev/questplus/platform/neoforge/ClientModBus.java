package dev.questplus.platform.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** Client-only counterpart of {@link ModBus}. Never loaded on a dedicated server. */
public final class ClientModBus {
	public static ModBus.Queue<RegisterClientPayloadHandlersEvent> payloads;
	public static ModBus.Queue<RegisterKeyMappingsEvent> keys;
	public static ModBus.Queue<RegisterGuiLayersEvent> hud;
	public static ModBus.Queue<EntityRenderersEvent.RegisterRenderers> renderers;
	public static ModBus.Queue<EntityRenderersEvent.AddLayers> layers;
	public static ModBus.Queue<RegisterPictureInPictureRenderersEvent> pictureInPicture;

	private ClientModBus() {}

	static void attach(IEventBus bus) {
		payloads = new ModBus.Queue<>(bus, RegisterClientPayloadHandlersEvent.class);
		keys = new ModBus.Queue<>(bus, RegisterKeyMappingsEvent.class);
		hud = new ModBus.Queue<>(bus, RegisterGuiLayersEvent.class);
		renderers = new ModBus.Queue<>(bus, EntityRenderersEvent.RegisterRenderers.class);
		layers = new ModBus.Queue<>(bus, EntityRenderersEvent.AddLayers.class);
		pictureInPicture = new ModBus.Queue<>(bus, RegisterPictureInPictureRenderersEvent.class);
	}
}
