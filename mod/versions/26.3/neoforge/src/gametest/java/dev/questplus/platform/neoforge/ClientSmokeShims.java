package dev.questplus.platform.neoforge;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;

/** Client APIs {@link ClientSmokeTest} needs that moved between Minecraft versions. Test-only. */
final class ClientSmokeShims {
	private ClientSmokeShims() {}

	static RenderTarget mainTarget(Minecraft client) {
		return client.gameRenderer.mainRenderTarget();
	}
}
