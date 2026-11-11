package dev.questplus.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

/**
 * The first-person tablet is submitted to Minecraft's own render collector.
 * This class does not replace the world renderer or depend on a player animation mod.
 */
public final class TabletNativeFirstPersonRenderer {
	private static final Identifier BODY = Identifier.fromNamespaceAndPath("questplus",
			"textures/fp/questplus_first_person.png");

	private TabletNativeFirstPersonRenderer() {}

	public static boolean submit(PoseStack base, SubmitNodeCollector collector, int light) {
		var mc = Minecraft.getInstance();
		if (mc.player == null || !TabletNativeArms.active()
				|| !mc.options.getCameraType().isFirstPerson())
			return false;
		String clip = TabletNativeAnimation.clip();
		if (clip == null) return false;
		if (TabletShaderCompat.translucentHandPass()) return true;
		double seconds = TabletNativeAnimation.seconds();
		var frame = TabletFirstPersonAnimation.pose(clip, seconds);
		var skin = mc.player.getSkin().body().texturePath();

		base.pushPose();
		// Move the whole rig so the hands, screen and captured pointer plane stay aligned.
		base.translate(0, 0, TabletFirstPersonAnimation.proximityOffset(clip, seconds,
				QuestPlusClient.settings.tabletProximity));
		// Camera-centred Blockbench rig, in the same camera frame in which vanilla submits hands.
		base.translate(0, -.52f, -.72f);
		base.translate(0, -9.230422f / 16f, 12.931625f / 16f);
		var root = TabletFirstPersonAnimation.boneValue(clip, seconds, "fp_player_arms", "rotation");
		var rootPosition = TabletFirstPersonAnimation.boneValue(clip, seconds, "fp_player_arms", "position");
		base.translate(-rootPosition.x() / 16f, rootPosition.y() / 16f, rootPosition.z() / 16f);
		base.mulPose(new Matrix4f().rotationZYX((float)Math.toRadians(root.z()),
				(float)-Math.toRadians(root.y()), (float)-Math.toRadians(root.x())));

		for (boolean right : new boolean[]{true, false}) {
			base.pushPose();
			collector.submitCustomGeometry(base, RenderTypes.entityCutout(skin), (framePose, vertices) -> {
				var snapshot = restore(framePose);
				TabletNativeArms.render(snapshot, vertices, light, right, clip, seconds);
			});
			base.popPose();
		}

		if (frame.tabletVisible()) {
			boolean surface = TabletSurfaceRenderer.hasSurface();
			// The interactive atlas previously held this exact static body texture.
			// Keep the original skin when rendering only the carried item.
			collector.submitCustomGeometry(base, RenderTypes.entityCutout(
					surface ? TabletRenderer.texture() : BODY), (framePose, vertices) ->
					TabletNativeGeometry.render(restore(framePose), vertices, light,
							TabletNativeGeometry.Pass.BODY, clip, seconds, surface));
			if (surface) {
				var renderType = TabletShaderCompat.shadersActive()
						? RenderTypes.entityCutout(TabletSurfaceRenderer.TEXTURE)
						: QuestPlusClient.settings.screenEmissive
							// Eyes skip the cardinal face shading that dimmed the "emissive" screen by a third.
							? RenderTypes.eyes(TabletSurfaceRenderer.TEXTURE)
							: RenderTypes.entityTranslucent(TabletSurfaceRenderer.TEXTURE);
				collector.submitCustomGeometry(base, renderType, (framePose, vertices) ->
						TabletNativeGeometry.render(restore(framePose), vertices, light,
								TabletNativeGeometry.Pass.SCREEN, clip, seconds, true));
			}
			collector.submitCustomGeometry(base, TabletIdleEffects.renderType(), (framePose, vertices) ->
					TabletNativeGeometry.render(restore(framePose), vertices, light,
							TabletNativeGeometry.Pass.EFFECTS, clip, seconds, surface));
		}
		base.popPose();
		return true;
	}

	private static PoseStack restore(PoseStack.Pose captured) {
		var pose = new PoseStack();
		pose.pushPose();
		pose.last().set(captured);
		return pose;
	}
}
