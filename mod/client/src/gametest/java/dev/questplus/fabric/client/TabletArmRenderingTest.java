package dev.questplus.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.util.LightCoordsUtil;
import java.lang.reflect.Proxy;

/**
 * Checks vanilla hand submissions and the native renderer's own geometry.
 */
final class TabletArmRenderingTest {

	static void verify(Minecraft client, boolean tablet) {
		try {
			var renderer = (AvatarRenderer<?>) client.getEntityRenderDispatcher().getRenderer(client.player);
			var model = renderer.getModel();
			var counts = new int[2];
			var vertices = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
					new Class<?>[] { VertexConsumer.class }, (proxy, method, args) -> {
						if (method.getName().equals("addVertex"))
							counts[0]++;
						return method.getReturnType() == void.class ? null : proxy;
					});
			var nodes = (SubmitNodeCollector) Proxy.newProxyInstance(SubmitNodeCollector.class.getClassLoader(),
					new Class<?>[] { SubmitNodeCollector.class }, (proxy, method, args) -> {
						if (method.getName().equals("submitModelPart"))
							counts[1]++;
						if (method.getName().equals("submitCustomGeometry"))
							((SubmitNodeCollector.CustomGeometryRenderer) args[2]).render(((PoseStack) args[0]).last(),
									vertices);
						return null;
					});
			var skin = client.player.getSkin().body().texturePath();
			var hand = AvatarRenderer.class.getDeclaredMethod("renderHand", PoseStack.class, SubmitNodeCollector.class,
					int.class, net.minecraft.resources.Identifier.class, ModelPart.class, boolean.class);
			hand.setAccessible(true);
			hand.invoke(renderer, new PoseStack(), nodes, LightCoordsUtil.FULL_BRIGHT, skin, model.rightArm, true);
			hand.invoke(renderer, new PoseStack(), nodes, LightCoordsUtil.FULL_BRIGHT, skin, model.leftArm, true);
			if (counts[1] != 2)
				throw new AssertionError("Vanilla hand submissions=" + counts[1]);
			counts[0] = 0;
			boolean submitted = TabletNativeFirstPersonRenderer.submit(new PoseStack(), nodes,
					new net.minecraft.client.renderer.entity.state.AvatarRenderState());
			if (submitted != tablet || tablet && counts[0] == 0)
				throw new AssertionError("Native hand submissions: tablet=" + tablet + ", vertices=" + counts[0]);
			if (tablet) {
				for (String name : new String[] { "rightArm", "leftArm" }) {
					counts[0] = 0;
					TabletNativeArms.render(new PoseStack(), vertices, LightCoordsUtil.FULL_BRIGHT,
							name.equals("rightArm"), "questplus.idle", 0);
					boolean sleeve = name.equals("rightArm") ? model.rightSleeve.visible : model.leftSleeve.visible;
					if (counts[0] != (sleeve ? 48 : 24))
						throw new AssertionError(
								"Expected one tablet arm and optional sleeve: " + name + ", vertices=" + counts[0]);
				}
			}
		} catch (ReflectiveOperationException error) {
			throw new AssertionError(error);
		}
	}
}
