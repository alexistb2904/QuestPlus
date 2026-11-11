package dev.questplus.fabric;

import dev.questplus.compat.GameTestShims;

import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Files;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;

public final class ObjectiveConditionsGameTest {
	@GameTest(maxTicks = 100)
	public void actualHealthEquipmentAndModdedItem(GameTestHelper helper) throws Exception {
		var level = helper.getLevel();
		var server = level.getServer();
		var runtime = QuestPlusMod.runtime();
		var file = server.getWorldPath(LevelResource.ROOT).resolve("questplus/quests/conditions-gametest.json");
		ServerPlayer player = null;
		Files.writeString(file, """
				{"schemaVersion":1,"id":"questplus:conditions_gametest","category":"%s",
				 "icon":"minecraft:stone","title":{"en_us":"Conditions"},"progression":"once","rewards":[],
				 "objectives":[
				  {"id":"kill","event":"entity_kill","target":"minecraft:zombie","count":2,
				   "conditions":{"and":[{"tool":"minecraft:diamond_sword"},{"healthAtLeast":60},
				      {"equipment":{"slot":"chest","item":"minecraft:diamond_chestplate"}}]}},
				  {"id":"pickup","event":"item_obtained","target":"questplus-test:battery","count":2}]}
				""".formatted(runtime.categories().getFirst().id()));
		try {
			runtime.reload();
			player = new ServerPlayer(server, level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "conditions-test"),
					net.minecraft.server.level.ClientInformation.createDefault());
			var connection = new Connection(PacketFlow.SERVERBOUND);
			new EmbeddedChannel(connection);
			server.getPlayerList().placeNewPlayer(connection, player,
					CommonListenerCookie.createInitial(player.getGameProfile(), false));
			player.setGameMode(GameType.SURVIVAL);
			player.getInventory().clearContent();
			player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(80);
			player.setHealth(60);
			player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
			var progress = runtime.player(player.getUUID());
			var zombie = helper.spawn(GameTestShims.ZOMBIE, new BlockPos(3, 2, 3));
			zombie.hurtServer(level, level.damageSources().playerAttack(player), 1000);
			helper.assertTrue(!progress.quests.containsKey("questplus:conditions_gametest"),
					"Wrong weapon must not count even with enough health and armor");
			player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
			player.setHealth(59);
			zombie = helper.spawn(GameTestShims.ZOMBIE, new BlockPos(3, 2, 3));
			zombie.hurtServer(level, level.damageSources().playerAttack(player), 1000);
			helper.assertTrue(!progress.quests.containsKey("questplus:conditions_gametest"),
					"Health below 30 hearts must not count");
			player.setHealth(60);
			zombie = helper.spawn(GameTestShims.ZOMBIE, new BlockPos(3, 2, 3));
			zombie.hurtServer(level, level.damageSources().playerAttack(player), 1000);
			helper.assertTrue(progress.quests.get("questplus:conditions_gametest").counters.get("kill") == 1,
					"Kill with matching weapon, armor and 30 hearts counts once");
			var item = new ItemEntity(level, player.getX(), player.getY(), player.getZ(), new ItemStack(LoaderTestSupport.battery));
			item.setNoPickUpDelay();
			level.addFreshEntity(item);
			item.playerTouch(player);
			helper.assertTrue(progress.quests.get("questplus:conditions_gametest").counters.get("pickup") == 1,
					"Actual pickup of an item registered by another mod counts");
		} finally {
			if (player != null)
				server.getPlayerList().remove(player);
			Files.deleteIfExists(file);
			runtime.reload();
		}
		helper.succeed();
	}
}
