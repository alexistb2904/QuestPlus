package dev.questplus.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerModelType;
import dev.questplus.fabric.client.mixin.TabletModelPartAccessor;
import org.joml.Quaternionf;
import java.util.List;

/** Vanilla skin and sleeves, posed using the local Blockbench rig. */
public final class TabletNativeArms {
	private static final class Meshes {
		static final PlayerModel WIDE = create(false), SLIM = create(true);
		private static PlayerModel create(boolean slim) {
			return new PlayerModel(
					LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot(), slim);
		}
	}
	private TabletNativeArms() {}

	public static boolean active() {
		return TabletNativeAnimation.active();
	}

	static void render(PoseStack pose, VertexConsumer vertices, int light, boolean right,
			String clip, double seconds) {
		var player = Minecraft.getInstance().player;
		if (player == null) return;
		var model = player.getSkin().model() == PlayerModelType.SLIM ? Meshes.SLIM : Meshes.WIDE;
		String name = right ? "rightArm" : "leftArm";
		float px = right ? 5f : -5f, py = 22f, pz = 0;
		var pos = TabletFirstPersonAnimation.boneValue(clip, seconds, name, "position");
		var rot = TabletFirstPersonAnimation.boneValue(clip, seconds, name, "rotation");
		var scale = TabletFirstPersonAnimation.boneValue(clip, seconds, name, "scale");
		pose.pushPose();
		pose.translate(-pos.x() / 16f, pos.y() / 16f, pos.z() / 16f);
		pose.translate(px / 16f, py / 16f, pz / 16f);
		pose.mulPose(new Quaternionf().rotationZYX((float)Math.toRadians(rot.z()),
				(float)-Math.toRadians(rot.y()),(float)-Math.toRadians(rot.x())));
		pose.scale(scale.x(),scale.y(),scale.z());
		pose.translate(-px / 16f, -py / 16f, -pz / 16f);
		pose.translate(px / 16f, py / 16f, pz / 16f);
		pose.scale(-1, -1, 1);
		// The existing rig's arm meshes use the vanilla skin UVs, not a baked arm texture.
		var arm = right ? model.rightArm : model.leftArm;
		var sleeve = right ? model.rightSleeve : model.leftSleeve;
		for (var cube : ((TabletModelPartAccessor)(Object)arm).questplus$cubes())
			cube.compile(pose.last(), vertices, light, OverlayTexture.NO_OVERLAY, -1);
		boolean sleeveEnabled = player.isModelPartShown(right
				? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE);
		if (sleeveEnabled)
			for (var cube : ((TabletModelPartAccessor)(Object)sleeve).questplus$cubes())
				cube.compile(pose.last(), vertices, light, OverlayTexture.NO_OVERLAY, -1);
		pose.popPose();
	}
}
