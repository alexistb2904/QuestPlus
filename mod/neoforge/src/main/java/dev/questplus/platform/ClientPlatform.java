package dev.questplus.platform;

import dev.questplus.platform.neoforge.ClientModBus;
import dev.questplus.platform.neoforge.NeoForgeClientAdapter;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge implementation of the client hooks. Same public methods as {@code mod/fabric}'s
 * (checked by {@code verifyPlatformApi}). Never loaded on a dedicated server.
 */
public final class ClientPlatform {
	private ClientPlatform() {}

	/** {@code handler} runs on the client thread. */
	public static <T extends CustomPacketPayload> void receive(CustomPacketPayload.Type<T> type, Consumer<T> handler) {
		ClientModBus.payloads.add(event -> event.register(type, (payload, context) -> handler.accept(payload)));
	}

	public static boolean canSend(CustomPacketPayload.Type<?> type) {
		var connection = Minecraft.getInstance().getConnection();
		return connection != null && connection.hasChannel(type);
	}

	public static void send(CustomPacketPayload payload) {
		ClientPacketDistributor.sendToServer(payload);
	}

	public static void onJoin(Consumer<Minecraft> listener) {
		NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> listener.accept(Minecraft.getInstance()));
	}

	public static void onDisconnect(Consumer<Minecraft> listener) {
		NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> listener.accept(Minecraft.getInstance()));
	}

	public static void onClientTick(Consumer<Minecraft> listener) {
		NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> listener.accept(Minecraft.getInstance()));
	}

	/** Return true to cancel the attack. */
	public static void onAttack(Predicate<Minecraft> listener) {
		NeoForge.EVENT_BUS.addListener((InputEvent.InteractionKeyMappingTriggered event) -> {
			if (event.isAttack() && listener.test(Minecraft.getInstance())) {
				event.setCanceled(true);
				event.setSwingHand(false);
			}
		});
	}

	public static KeyMapping registerKey(KeyMapping key) {
		ClientModBus.keys.add(event -> event.register(key));
		return key;
	}

	public static void hud(Identifier id, BiConsumer<GuiGraphicsExtractor, DeltaTracker> element) {
		ClientModBus.hud.add(event -> event.registerAboveAll(id, element::accept));
	}

	public static <T extends BlockEntity, S extends BlockEntityRenderState> void blockEntityRenderer(
			BlockEntityType<? extends T> type, BlockEntityRendererProvider<T, S> provider) {
		ClientModBus.renderers.add(event -> event.registerBlockEntityRenderer(type, provider));
	}

	public static <T extends Entity> void entityRenderer(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
		ClientModBus.renderers.add(event -> event.registerEntityRenderer(type, provider));
	}

	public static void avatarLayer(Function<AvatarRenderer<?>, RenderLayer<AvatarRenderState, PlayerModel>> layer) {
		ClientModBus.layers.add(event -> {
			var avatars = new java.util.LinkedHashSet<AvatarRenderer<?>>();
			for (var skin : event.getSkins()) {
				avatars.add(event.getPlayerRenderer(skin));
				avatars.add(event.getMannequinRenderer(skin));
			}
			for (var type : event.getEntityTypes())
				if (event.getRenderer(type) instanceof AvatarRenderer<?> avatar) avatars.add(avatar);
			avatars.remove(null);
			for (var avatar : avatars) avatar.addLayer(layer.apply(avatar));
		});
	}

	public static <S extends PictureInPictureRenderState> void pictureInPicture(Class<S> state,
			Supplier<PictureInPictureRenderer<S>> factory) {
		ClientModBus.pictureInPicture.add(event -> NeoForgeClientAdapter.INSTANCE.registerPictureInPicture(event, state, factory));
	}
}
