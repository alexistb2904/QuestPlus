package dev.questplus.platform;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.model.player.PlayerModel;
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

/**
 * Fabric implementation of the client hooks. {@code mod/neoforge} declares the same public methods
 * (checked by {@code verifyPlatformApi}). Never loaded on a dedicated server.
 */
public final class ClientPlatform {
	private ClientPlatform() {}

	/** {@code handler} runs on the client thread. */
	public static <T extends CustomPacketPayload> void receive(CustomPacketPayload.Type<T> type, Consumer<T> handler) {
		ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> context.client().execute(() -> handler.accept(payload)));
	}

	public static boolean canSend(CustomPacketPayload.Type<?> type) {
		return ClientPlayNetworking.canSend(type);
	}

	public static void send(CustomPacketPayload payload) {
		ClientPlayNetworking.send(payload);
	}

	public static void onJoin(Consumer<Minecraft> listener) {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> listener.accept(client));
	}

	public static void onDisconnect(Consumer<Minecraft> listener) {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> listener.accept(client));
	}

	public static void onClientTick(Consumer<Minecraft> listener) {
		ClientTickEvents.END_CLIENT_TICK.register(listener::accept);
	}

	/** Return true to cancel the attack. */
	public static void onAttack(Predicate<Minecraft> listener) {
		ClientPreAttackCallback.EVENT.register((client, player, clicks) -> clicks > 0 && listener.test(client));
	}

	public static KeyMapping registerKey(KeyMapping key) {
		return KeyMappingHelper.registerKeyMapping(key);
	}

	public static void hud(Identifier id, BiConsumer<GuiGraphicsExtractor, DeltaTracker> element) {
		HudElementRegistry.addLast(id, element::accept);
	}

	public static <T extends BlockEntity, S extends BlockEntityRenderState> void blockEntityRenderer(
			BlockEntityType<? extends T> type, BlockEntityRendererProvider<T, S> provider) {
		BlockEntityRendererRegistry.register(type, provider);
	}

	public static <T extends Entity> void entityRenderer(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
		EntityRendererRegistry.register(type, provider);
	}

	public static void avatarLayer(Function<AvatarRenderer<?>, RenderLayer<AvatarRenderState, PlayerModel>> layer) {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) helper.register(layer.apply(avatar));
		});
	}

	public static <S extends PictureInPictureRenderState> void pictureInPicture(Class<S> state,
			Supplier<PictureInPictureRenderer<S>> factory) {
		PictureInPictureRendererRegistry.register(context -> factory.get());
	}
}
