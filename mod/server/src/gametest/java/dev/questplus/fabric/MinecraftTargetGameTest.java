package dev.questplus.fabric;

import dev.questplus.compat.CommonAdapter;
import dev.questplus.compat.TargetAudit;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class MinecraftTargetGameTest {
	@GameTest
	public void runsOnItsOwnTargetWithEveryServerMixinApplied(GameTestHelper helper) {
		TargetAudit.run(CommonAdapter.INSTANCE);
		helper.succeed();
	}
}
