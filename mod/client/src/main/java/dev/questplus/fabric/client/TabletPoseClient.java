package dev.questplus.fabric.client;

import dev.questplus.compat.ClientAdapter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.questplus.fabric.TabletPoseNetwork;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Unit;
import java.util.*;

public final class TabletPoseClient {
	static final float TABLET_SCALE = .4f;
	static final org.joml.Vector3f TABLET_GRIP = new org.joml.Vector3f(-11.5f / 16, 0, .2f / 16);
	static float gripY(TabletThirdPersonAnimation.Pose frame) {
		return 3.5f / 16 * Math.clamp(-frame.right().x() / 53, 0, 1);
	}
	private static final Map<UUID, PoseTime> POSES = new HashMap<>();
	private static boolean sent;
	private static int heartbeat;
	private static Object level;

	private TabletPoseClient() {
	}

	public static void register() {
		registerModelCompatibility();
		ClientPlayNetworking.registerGlobalReceiver(TabletPoseNetwork.State.TYPE,
				(packet, ctx) -> ctx.client().execute(() -> {
					changeLevel(ctx.client().level);
					transition(packet.player(), packet.open());
				}));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			POSES.clear();
			level = null;
			sent = false;
			heartbeat = 0;
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			changeLevel(client.level);
			long time = System.nanoTime();
			boolean animate = animated();
			POSES.values().removeIf(p -> p.finishedClosed(time, animate));
			if (client.player == null || !ClientPlayNetworking.canSend(TabletPoseNetwork.Request.TYPE))
				return;
			boolean now = (ClientAdapter.INSTANCE.screen(client) instanceof QuestScreen || QuestClientTabletFlow.thirdPersonHolding())
					&& client.player.isAlive() && !client.player.isSpectator();
			if (now != sent || (now && ++heartbeat >= 100)) {
				if (now != sent)
					transition(client.player.getUUID(), now);
				ClientPlayNetworking.send(new TabletPoseNetwork.Request(now));
				sent = now;
				heartbeat = 0;
			}
		});
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?>avatar)
				helper.register(new Layer(avatar));
		});
	}

	private static void registerModelCompatibility() {
		if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("entity_model_features")) return;
		try {
			var api = Class.forName("traben.entity_model_features.EMFAnimationApi");
			var entity = Class.forName("traben.entity_texture_features.utils.ETFEntity");
			var uuid = entity.getMethod("etf$getUuid");
			java.util.function.Function<Object, Boolean> holding = value -> {
				try {
					var id = (UUID) uuid.invoke(value);
					if (POSES.containsKey(id)) return true;
					var client = Minecraft.getInstance();
					var player = client.level == null ? null : client.level.getPlayerByUUID(id);
					return player != null && player.getMainHandItem().getItem() instanceof dev.questplus.fabric.QuestTabletItem;
				} catch (ReflectiveOperationException error) {
					return false;
				}
			};
			api.getMethod("registerVanillaModelCondition", java.util.function.Function.class).invoke(null, holding);
		} catch (ReflectiveOperationException | LinkageError error) {
			dev.questplus.fabric.QuestPlusMod.LOGGER.warn("Entity Model Features tablet compatibility unavailable", error);
		}
	}

	private static void changeLevel(Object current) {
		if (level != current) {
			POSES.clear();
			level = current;
			sent = false;
			heartbeat = 0;
		}
	}

	private static boolean animated() {
		var settings = QuestPlusClient.settings;
		return settings != null && settings.animatedTabletTransitions();
	}

	private static void transition(UUID id, boolean open) {
		if (!open && !POSES.containsKey(id))
			return;
		POSES.computeIfAbsent(id, ignored -> new PoseTime()).set(open, System.nanoTime(), animated());
	}

	record Frame(TabletThirdPersonAnimation.Pose pose, float weight, boolean carried) {
		Frame(TabletThirdPersonAnimation.Pose pose, float weight) {
			this(pose, weight, false);
		}
	}

	private static Frame sample(AvatarRenderState state) {
		var client = Minecraft.getInstance();
		var entity = client.level == null ? null : client.level.getEntity(state.id);
		if (entity == null || !entity.isAlive() || state.isSpectator)
			return null;
		var pose = POSES.get(entity.getUUID());
		boolean carrying = entity instanceof net.minecraft.world.entity.player.Player player
				&& player.getMainHandItem().getItem() instanceof dev.questplus.fabric.QuestTabletItem;
		var frame = pose == null ? null : pose.sample(System.nanoTime(), animated(), carrying);
		return frame != null ? frame : carrying ? new Frame(TabletThirdPersonAnimation.carry(), 1, true) : null;
	}

	public static void apply(PlayerModel model, AvatarRenderState state) {
		var pose = sample(state);
		if (pose == null)
			return;
		// The animated tablet layer replaces only this item, avoiding a second tablet
		// in the wrist.
		if (state.rightHandItemStack.getItem() instanceof dev.questplus.fabric.QuestTabletItem)
			state.rightHandItemState.clear();
		if (state.leftHandItemStack.getItem() instanceof dev.questplus.fabric.QuestTabletItem)
			state.leftHandItemState.clear();
		var value = pose.pose();
		float weight = pose.weight();
		arm(model.rightArm, value.right(), value.rightPosition(), weight);
		if (pose.carried())
			return;
		arm(model.leftArm, value.left(), value.leftPosition(), weight);
		// Bring both wrists inward to match the smaller tablet during two-handed
		// reading.
		float inward = (float) Math.toRadians(20) * Math.clamp(-value.right().x() / 53, 0, 1) * weight;
		model.rightArm.zRot -= inward;
		model.leftArm.zRot += inward;
		model.head.xRot = blend(model.head.xRot, (float) Math.toRadians(value.head().x()), weight);
		model.head.yRot = blend(model.head.yRot, (float) Math.toRadians(value.head().y()), weight);
		model.head.zRot = blend(model.head.zRot, (float) Math.toRadians(value.head().z()), weight);
	}

	private static float blend(float from, float to, float weight) {
		return from + (to - from) * weight;
	}

	static void arm(net.minecraft.client.model.geom.ModelPart arm, TabletThirdPersonAnimation.Vector rotation,
			TabletThirdPersonAnimation.Vector position, float weight) {
		arm.xRot = blend(arm.xRot, (float) Math.toRadians(rotation.x()), weight);
		arm.yRot = blend(arm.yRot, (float) Math.toRadians(rotation.y()), weight);
		arm.zRot = blend(arm.zRot, (float) Math.toRadians(rotation.z()), weight);
		arm.x += position.x() * weight;
		arm.y -= position.y() * weight;
		arm.z += position.z() * weight;
	}

	static void tabletTransform(PoseStack pose, net.minecraft.client.model.geom.ModelPart rightArm,
			TabletThirdPersonAnimation.Pose frame) {
		var hand = new PoseStack();
		rightArm.translateAndRotate(hand);
		var palm = hand.last().pose().transformPosition(-1 / 16f, 9 / 16f, 0, new org.joml.Vector3f());
		pose.translate(palm.x, palm.y, palm.z);
		var rotation = frame.rotation();
		var scale = frame.scale();
		pose.last().rotate(Axis.ZP.rotationDegrees(rotation.z()));
		pose.last().rotate(Axis.YP.rotationDegrees(-rotation.y()));
		pose.last().rotate(Axis.XP.rotationDegrees(-rotation.x()));
		pose.scale(-TABLET_SCALE * scale.x(), TABLET_SCALE * scale.y(), TABLET_SCALE * scale.z());
		// Scale around the grip, so the tablet edge stays inside the player's palm in
		// every pose.
		pose.translate(-TABLET_GRIP.x, -gripY(frame), -TABLET_GRIP.z);
	}

	static final class PoseTime {
		private static final double BLEND_SECONDS = TabletThirdPersonAnimation.OPEN_SECONDS / 6;
		private double from;
		private boolean open;
		private long started;
		private TabletThirdPersonAnimation.Pose transitionFrom;

		void set(boolean next, long now, boolean animate) {
			if (next == open)
				return;
			Frame previous = sample(now, animate);
			from = progress(now, animate);
			open = next;
			started = now;
			transitionFrom = animate && previous != null ? previous.pose() : null;
		}

		double progress(long now, boolean animate) {
			if (!animate)
				return open ? 1 : 0;
			double elapsed = Math.max(0, now - started) / 1_000_000_000.0 / TabletThirdPersonAnimation.OPEN_SECONDS;
			return Math.clamp(from + (open ? elapsed : -elapsed), 0, 1);
		}

		boolean finishedClosed(long now, boolean animate) {
			return !open && progress(now, animate) <= 0;
		}

		Frame sample(long now, boolean animate) {
			return sample(now, animate, false);
		}

		Frame sample(long now, boolean animate, boolean carrying) {
			double value = progress(now, animate);
			if (value <= 0 && !open)
				return null;
			boolean idle = open && value >= 1;
			double idleTime = animate
					? Math.max(0,
							(now - started) / 1_000_000_000.0 - (1 - from) * TabletThirdPersonAnimation.OPEN_SECONDS)
					: 0;
			double start = carrying ? TabletThirdPersonAnimation.CARRY_SECONDS : 0;
			var pose = TabletThirdPersonAnimation.sample(idle,
					idle ? idleTime : start + value * (TabletThirdPersonAnimation.OPEN_SECONDS - start));
			if (transitionFrom != null && animate) {
				double t = Math.clamp((now - started) / 1_000_000_000.0 / BLEND_SECONDS, 0, 1);
				pose = transitionFrom.blend(pose, t * t * (3 - 2 * t));
			}
			float weight = animate && !carrying
					? (float) Math.clamp(value * TabletThirdPersonAnimation.OPEN_SECONDS / BLEND_SECONDS, 0, 1)
					: 1;
			weight = weight * weight * (3 - 2 * weight);
			return new Frame(pose, weight);
		}
	}

	private static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		private final net.minecraft.client.model.Model.Simple model = TabletRenderer.worldModel();

		Layer(AvatarRenderer<?> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack pose, SubmitNodeCollector nodes, int light, AvatarRenderState state, float yaw,
				float pitch) {
			var sampled = sample(state);
			if (state.isInvisible || sampled == null || sampled.pose().scale().x() <= 0)
				return;
			var frame = sampled.pose();
			pose.pushPose();
			tabletTransform(pose, getParentModel().rightArm, frame);
			ClientAdapter.INSTANCE.submitModel(nodes, model, Unit.INSTANCE, pose, TabletRenderer.texture(), light,
					OverlayTexture.NO_OVERLAY, 0);
			var effects = TabletIdleEffects.frame();
			pose.scale(1, -1, 1);
			nodes.submitCustomGeometry(pose, TabletIdleEffects.renderType(),
					(p, c) -> TabletIdleEffects.draw(p, c, effects));
			pose.popPose();
		}
	}
}
