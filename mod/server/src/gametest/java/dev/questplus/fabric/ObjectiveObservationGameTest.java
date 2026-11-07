package dev.questplus.fabric;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;

public final class ObjectiveObservationGameTest {
	@GameTest(maxTicks = 400)
	public void realEntityRaycastResetsOnOcclusionAndTargetChanges(GameTestHelper helper) throws Exception {
		verify(helper, "minecraft:stone", "minecraft:zombie", "observe_vanilla");
	}

	static void verify(GameTestHelper helper, String block, String entity, String name) throws Exception {
		var f = new ObjectiveTestFixture(helper, name, """
				[{id:'block',event:'observe_block',target:'%s',count:100},
				 {id:'entity',event:'observe_entity',target:'%s',count:100},
				 {id:'tag',event:'observe_entity',target:'#questplus-test:watchable',count:100}]
				""".formatted(block, entity));
		var player = f.player;
		var target = player.blockPosition().offset(0, 1, 4);
		var obstruction = player.blockPosition().offset(0, 1, 1);
		var level = helper.getLevel();
		level.setBlockAndUpdate(target, BuiltInRegistries.BLOCK.getOptional(Identifier.parse(block)).orElseThrow().defaultBlockState());
		player.setYRot(0);
		player.setYHeadRot(0);
		player.setXRot(0);
		helper.startSequence().thenWaitUntil(() -> helper.assertTrue(f.count("block") > 0, "Real block gaze counted"))
				.thenExecute(() -> {
					player.setYRot(180);
					player.setYHeadRot(180);
				}).thenWaitUntil(() -> f.expect("block", 0)).thenExecute(() -> {
					var mob = f.spawn(BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(entity)).orElseThrow());
					mob.setPos(player.getX(), player.getY(), player.getZ() + 3);
					mob.setNoGravity(true);
					player.setYRot(0);
					player.setYHeadRot(0);
				}).thenWaitUntil(() -> {
					helper.assertTrue(f.count("entity") > 0 && f.count("tag") > 0, "Exact and tagged entity gaze counted");
					f.expect("block", 0);
				}).thenExecute(() -> level.setBlockAndUpdate(obstruction, Blocks.GLASS.defaultBlockState()))
				.thenWaitUntil(() -> {
					f.expect("entity", 0);
					f.expect("tag", 0);
					f.expect("block", 0);
				}).thenExecute(() -> level.setBlockAndUpdate(obstruction, Blocks.AIR.defaultBlockState()))
				.thenWaitUntil(() -> helper.assertTrue(f.count("entity") > 0, "Removing obstruction restores gaze"))
				.thenExecute(f::close).thenSucceed();
	}
}
