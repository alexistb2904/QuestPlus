package dev.questplus.fabric.client;

import com.github.razorplay01.fp_pal.accessors.IFpAnimatedPlayer;
import com.github.razorplay01.fp_pal.firstperson.FpArmRenderUtil;
import com.github.razorplay01.fp_pal.firstperson.FpRenderBuffer;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import dev.questplus.fabric.client.mixin.TabletModelPartAccessor;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class TabletNativeArms {
	private static final float[] RIGHT = pivot("rightArm"), LEFT = pivot("leftArm");

	private static final class Meshes {
		static final PlayerModel WIDE = create(false), SLIM = create(true);

		private static PlayerModel create(boolean slim) {
			return new PlayerModel(
					LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot(),
					slim);
		}
	}

	private TabletNativeArms() {
	}

	public static boolean active() {
		var player = Minecraft.getInstance().player;
		if (!QuestClientTabletFlow.nativeHandsActive() || !(player instanceof IFpAnimatedPlayer animated))
			return false;
		var controller = animated.fpPal$getFpAnimationController();
		var id = controller == null ? null : controller.getCurrentFpAnimation();
		return id != null && id.getNamespace().equals("questplus")
				&& (id.getPath().equals("questplus.open") || id.getPath().equals("questplus.idle")
						|| id.getPath().equals("questplus.close") || id.getPath().startsWith("questplus.carry"));
	}

	public static void render(PoseStack pose, PlayerAnimBone bone, PlayerModel model, Identifier skin, int light,
			FpRenderBuffer buffer) {
		boolean right = bone.getName().equals("rightArm");
		float[] pivot = right ? RIGHT : LEFT;
		var meshes = Minecraft.getInstance().player.getSkin().model() == PlayerModelType.SLIM ? Meshes.SLIM
				: Meshes.WIDE;
		ModelPart arm = right ? meshes.rightArm : meshes.leftArm;
		ModelPart sleeve = right ? meshes.rightSleeve : meshes.leftSleeve;
		var cubes = java.util.List.copyOf(((TabletModelPartAccessor) (Object) arm).questplus$cubes());
		var sleeveCubes = (right ? model.rightSleeve : model.leftSleeve).visible
				? java.util.List.copyOf(((TabletModelPartAccessor) (Object) sleeve).questplus$cubes())
				: java.util.List.<ModelPart.Cube>of();
		pose.pushPose();
		try {
			FpArmRenderUtil.applyBoneTransform(pose, bone, pivot);
			pose.translate(pivot[0] / 16f, pivot[1] / 16f, pivot[2] / 16f);
			pose.scale(-1, -1, 1);
			buffer.drawGeometry(pose, RenderTypes.entityCutout(skin), (matrix, vertices) -> {
				for (var cube : cubes)
					cube.compile(matrix.last(), vertices, light, OverlayTexture.NO_OVERLAY, -1);
				for (var cube : sleeveCubes)
					cube.compile(matrix.last(), vertices, light, OverlayTexture.NO_OVERLAY, -1);
			});
		} finally {
			pose.popPose();
		}
	}

	private static float[] pivot(String name) {
		try (var stream = TabletNativeArms.class
				.getResourceAsStream("/assets/questplus/fp_models/questplus_first_person.geo.json")) {
			if (stream == null)
				throw new IllegalStateException("Missing first-person geometry");
			var bones = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject()
					.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
			for (var element : bones) {
				var bone = element.getAsJsonObject();
				if (name.equals(bone.get("name").getAsString())) {
					var p = bone.getAsJsonArray("pivot");
					return new float[] { -p.get(0).getAsFloat(), p.get(1).getAsFloat(), p.get(2).getAsFloat() };
				}
			}
			throw new IllegalStateException("Missing first-person arm bone " + name);
		} catch (Exception error) {
			throw new IllegalStateException("Cannot read first-person arm pivot " + name, error);
		}
	}
}
