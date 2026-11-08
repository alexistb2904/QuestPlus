package dev.questplus.fabric;

import com.google.gson.Gson;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.*;
import java.util.*;

public final class TeamIntegrationTest {
	@GameTest(maxTicks = 5000)
	public void optionalAndAlternativeStagesDoNotBlockEligibleTeamRewards(GameTestHelper helper) throws Exception {
		var runtime = QuestPlusMod.runtime();
		var server = helper.getLevel().getServer();
		var a = player(helper);
		var b = player(helper);
		UUID aid = a.getUUID(), bid = b.getUUID();
		UUID[] team = new UUID[1];
		var directory = server.getWorldPath(LevelResource.ROOT).resolve("questplus/quests");
		for (String mode : List.of("all", "any"))
			Files.writeString(directory.resolve("team-eligibility-" + mode + ".json5"), """
					{schemaVersion:2,id:'questplus:team_eligibility_%s',category:'%s',icon:'minecraft:stone',
					 title:{en_us:'Stage alternatives'},progression:'once',objectiveMode:'%s',objectives:[
					 {id:'stage',event:'manual',target:'questplus:team_eligibility_%s',count:1,optional:%s,
					 conditions:{'questplus:stage':'questplus:optional_team_stage'}},
					 {id:'open',event:'manual',target:'questplus:team_eligibility_%s',count:1}],rewards:[{type:'experience',amount:1}]}
					""".formatted(mode, runtime.categories().getFirst().id(), mode, mode, mode.equals("all"), mode));
		runtime.reload();
		Runnable idle = () -> {
			if (runtime.isBusy(aid) || runtime.isBusy(bid)) {
				java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);
				helper.fail("Await stage eligibility transaction");
			}
		};
		var sequence = helper.startSequence()
				.thenExecute(step(() -> runtime.changeTeam(m -> m.create(aid, "Stage alternatives"))))
				.thenWaitUntil(idle).thenExecute(step(() -> {
					team[0] = runtime.player(aid).teamId;
					runtime.changeTeam(m -> m.invite(aid, bid, System.currentTimeMillis()));
				})).thenWaitUntil(idle)
				.thenExecute(step(() -> runtime.changeTeam(m -> m.join(bid, team[0], System.currentTimeMillis()))))
				.thenWaitUntil(idle);
		for (String mode : List.of("all", "any")) {
			String quest = "questplus:team_eligibility_" + mode;
			sequence.thenExecute(step(() -> runtime.interactObjective(a, quest, "open")))
					.thenWaitUntil(idle).thenExecute(step(() -> {
						helper.assertTrue(runtime.player(bid).entitlements.containsKey(quest),
								"Optional stage or eligible alternative does not require another player's stage");
						runtime.claim(b, quest);
					})).thenWaitUntil(idle);
		}
		sequence.thenExecute(step(() -> {
			helper.assertTrue(b.totalExperience == 2, "Both eligible rewards delivered");
			for (String mode : List.of("all", "any"))
				Files.delete(directory.resolve("team-eligibility-" + mode + ".json5"));
			runtime.reload();
		})).thenSucceed();
	}

	@GameTest(maxTicks = 10000)
	public void rolesSharingSubmissionsAndPersonalStages(GameTestHelper helper) throws Exception {
		var runtime = QuestPlusMod.runtime();
		var server = helper.getLevel().getServer();
		var dispatcher = server.getCommands().getDispatcher();
		var a = player(helper);
		var b = player(helper);
		var c = player(helper);
		UUID aid = a.getUUID(), bid = b.getUUID(), cid = c.getUUID();
		UUID[] team = new UUID[1];
		String personal = "questplus:team_refactor_items", shared = "questplus:team_refactor_shared",
				staged = "questplus:team_refactor_staged";
		var directory = server.getWorldPath(LevelResource.ROOT).resolve("questplus/quests");
		String category = runtime.categories().getFirst().id();
		Files.writeString(directory.resolve("team-refactor-items.json5"), """
				{schemaVersion:2,id:'questplus:team_refactor_items',category:'%s',icon:'minecraft:stone',title:{en_us:'Personal hand-in'},
				 progression:'once',objectives:[{id:'stone',event:'item_turn_in',target:'minecraft:stone',count:2}],
				 rewards:[{type:'experience',amount:7,teamReward:true}]}
				""".formatted(category));
		Files.writeString(directory.resolve("team-refactor-shared.json5"), """
				{schemaVersion:2,id:'questplus:team_refactor_shared',category:'%s',icon:'minecraft:stone',title:{en_us:'Shared reward'},
				 progression:'once',objectives:[{id:'done',event:'manual',target:'questplus:team_refactor_shared',count:1}],
				 rewards:[{type:'experience',amount:3,teamReward:true}]}
				""".formatted(category));
		Files.writeString(directory.resolve("team-refactor-staged.json5"), """
				{schemaVersion:2,id:'questplus:team_refactor_staged',category:'%s',icon:'minecraft:stone',title:{en_us:'Stage permission'},
				 progression:'once',objectives:[{id:'done',event:'manual',target:'questplus:team_refactor_staged',count:1,
				 conditions:{'questplus:stage':'questplus:team_refactor'}}],
				 rewards:[{type:'experience',amount:5}]}
				""".formatted(category));
		runtime.reload();
		Runnable idle = () -> {
			if (List.of(aid, bid, cid).stream().anyMatch(runtime::isBusy)) {
				java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);
				helper.fail("Await durable refactored team operation");
			}
		};
		helper.startSequence()
				.thenExecute(step(() -> runtime.changeTeam(m -> m.create(aid, "Refactor team"))))
				.thenWaitUntil(idle).thenExecute(step(() -> {
					team[0] = runtime.player(aid).teamId;
					runtime.changeTeam(m -> m.invite(aid, bid, System.currentTimeMillis()));
				})).thenWaitUntil(idle)
				.thenExecute(step(() -> runtime.changeTeam(m -> m.join(bid, team[0], System.currentTimeMillis()))))
				.thenWaitUntil(idle).thenExecute(step(() -> {
					helper.assertTrue(dispatcher.execute("questplus team promote " + bid, a.createCommandSourceStack()) == 1,
							"Owner promotes a member through the command path");
					rejected(helper, () -> runtime.changeTeam(m -> m.rename(aid, "Concurrent change")));
				})).thenWaitUntil(idle).thenExecute(step(() -> {
					helper.assertTrue(runtime.teams().get(team[0]).role(bid) == dev.questplus.team.TeamRole.MANAGER,
							"Manager role committed");
					rejected(helper, () -> runtime.changeTeam(m -> m.kick(bid, aid)));
					rejected(helper, () -> runtime.changeTeam(m -> m.transfer(bid, aid)));
					rejected(helper, () -> runtime.changeTeam(m -> m.disband(bid)));
					runtime.changeTeam(m -> m.invite(bid, cid, System.currentTimeMillis()));
				})).thenWaitUntil(idle)
				.thenExecute(step(() -> runtime.changeTeam(m -> m.revoke(bid, cid))))
				.thenWaitUntil(idle).thenExecute(step(() -> {
					rejected(helper, () -> runtime.changeTeam(m -> m.join(cid, team[0], System.currentTimeMillis())));
					runtime.changeTeam(m -> m.rename(bid, "Managed team"));
				})).thenWaitUntil(idle)
				.thenExecute(step(() -> runtime.interactObjective(a, shared, "done")))
				.thenWaitUntil(idle)
				.thenExecute(step(() -> runtime.changeTeam(m -> m.sharedQuestProgress(bid, false))))
				.thenWaitUntil(idle).thenExecute(step(() -> {
					helper.assertTrue(dev.questplus.fabric.api.QuestPlusApi.getTeamId(b).equals(team[0])
							&& dev.questplus.fabric.api.QuestPlusApi.getProgressScope(b) == dev.questplus.team.ProgressScope.PERSONAL,
							"Membership no longer implies collective progress");
					helper.assertTrue(dev.questplus.fabric.api.QuestPlusApi.getTeamRole(b) == dev.questplus.team.TeamRole.MANAGER,
							"Extension API exposes the effective team grade");
					helper.assertTrue(runtime.state(aid) != runtime.state(bid), "Personal state is independent");
					b.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 2));
					helper.assertTrue(runtime.submitItem(b, personal, "stone") == 2, "Personal consuming hand-in");
				})).thenWaitUntil(idle).thenExecute(step(() -> {
					helper.assertTrue(b.getInventory().getItem(0).isEmpty(), "Hand-in consumes exactly two items");
					helper.assertTrue(!runtime.state(aid).quests.containsKey(personal), "Personal hand-in never advances teammate");
					helper.assertTrue(runtime.player(bid).entitlements.get(personal).rewards().getFirst().policy().teamScope() == null,
							"Personal completion has no shared reward scope");
					helper.assertTrue(!runtime.player(aid).entitlements.containsKey(personal), "No teammate reward for personal hand-in");
					runtime.claim(b, personal);
				})).thenWaitUntil(idle).thenExecute(step(() -> {
					helper.assertTrue(b.totalExperience == 7, "Personal reward delivered once");
					runtime.claim(a, shared);
				})).thenWaitUntil(idle).thenExecute(step(() -> {
					rejected(helper, () -> runtime.claim(b, shared));
					helper.assertTrue(a.totalExperience == 3, "Original shared reward scope survives disabling sharing");
					runtime.changeTeam(m -> m.sharedQuestProgress(bid, true));
				})).thenWaitUntil(idle).thenExecute(step(() -> {
					helper.assertTrue(runtime.state(aid) == runtime.state(bid), "Sharing re-enabled");
					helper.assertTrue(dev.questplus.fabric.api.QuestPlusApi.getProgress(a, personal, dev.questplus.team.ProgressScope.TEAM).completed()
							&& !dev.questplus.fabric.api.QuestPlusApi.getProgress(a, personal, dev.questplus.team.ProgressScope.PERSONAL).completed(),
							"Explicit API scopes read separate histories");
					helper.assertTrue(runtime.state(aid).quests.get(personal).completed, "Personal history is merged");
					helper.assertTrue(!runtime.player(aid).entitlements.containsKey(personal), "Mode change creates no retrospective rewards");
					rejected(helper, () -> runtime.claim(b, personal));
					QuestStages.set(a, "questplus:team_refactor", true);
					runtime.interactObjective(a, staged, "done");
				})).thenWaitUntil(idle).thenExecute(step(() -> {
					helper.assertTrue(runtime.player(aid).entitlements.containsKey(staged), "Eligible contributor entitled");
					helper.assertTrue(!runtime.player(bid).entitlements.containsKey(staged), "Personal stage is never borrowed from teammate");
					QuestStages.set(a, "questplus:team_refactor", false);
					rejected(helper, () -> runtime.claim(a, staged));
					QuestStages.set(a, "questplus:team_refactor", true);
					runtime.claim(a, staged);
				})).thenWaitUntil(idle)
				.thenExecute(step(() -> runtime.changeTeam(m -> m.transfer(aid, bid))))
				.thenWaitUntil(idle).thenExecute(step(() -> {
					helper.assertTrue(runtime.teams().get(team[0]).role(aid) == dev.questplus.team.TeamRole.MANAGER,
							"Previous owner becomes a manager");
					rejected(helper, () -> runtime.changeTeam(m -> m.kick(aid, bid)));
					server.getPlayerList().remove(a);
					runtime.disconnect(aid);
					runtime.changeTeam(m -> m.kick(bid, aid));
				})).thenWaitUntil(idle).thenExecute(step(() -> {
					helper.assertTrue(runtime.player(aid).teamId == null && runtime.player(aid).quests.get(personal).completed,
							"Offline kick preserves progress and removes membership");
					rejected(helper, () -> runtime.changeTeam(m -> m.rename(aid, "Forbidden")));
					runtime.changeTeam(m -> m.disband(bid));
				})).thenWaitUntil(idle).thenExecute(step(() -> {
					helper.assertTrue(runtime.player(bid).teamId == null, "Disband removes owner membership");
					for (String name : List.of("items", "shared", "staged"))
						Files.delete(directory.resolve("team-refactor-" + name + ".json5"));
					runtime.reload();
				})).thenSucceed();
	}

	private static void rejected(GameTestHelper helper, Step action) throws Exception {
		try {
			action.run();
		} catch (IllegalArgumentException | IllegalStateException expected) {
			return;
		}
		helper.fail("Unauthorized or duplicate team operation was accepted");
	}

	@FunctionalInterface
	private interface Step {
		void run() throws Exception;
	}

	private static Runnable step(Step action) {
		return () -> {
			try {
				action.run();
			} catch (RuntimeException e) {
				throw e;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		};
	}

	private static ServerPlayer player(GameTestHelper helper) {
		var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
				new com.mojang.authlib.GameProfile(UUID.randomUUID(), "team_" + UUID.randomUUID().toString().substring(0, 8)),
				net.minecraft.server.level.ClientInformation.createDefault());
		var connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player,
				CommonListenerCookie.createInitial(player.getGameProfile(), false));
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	@GameTest(maxTicks = 10000)
	public void retroactiveTeams(GameTestHelper helper) throws Exception {
		var runtime = QuestPlusMod.runtime();
		var server = helper.getLevel().getServer();
		var dispatcher = server.getCommands().getDispatcher();
		LegacyGameTestFixtures.install(server);
		runtime.reload();
		var a = player(helper);
		var b = player(helper);
		UUID aid = a.getUUID(), bid = b.getUUID();
		UUID[] team = new UUID[1];
		int[] xp = new int[1];
		var fixture = server.getWorldPath(LevelResource.ROOT).resolve("questplus/quests/team-integration.json");
		Files.writeString(fixture,
				"""
						{"schemaVersion":2,"id":"questplus:team_integration","category":"questplus:survival","icon":"minecraft:dirt",
						 "title":{"en_us":"Team fixture"},"description":{"en_us":"Test"},"progression":"once",
						 "requires":{"and":[{"quest":"questplus:survival/logs"},{"quest":"questplus:combat/skeletons"}]},
						 "objectives":[{"id":"dirt","event":"block_break","target":"minecraft:dirt","count":2}],"rewards":[{"type":"experience","amount":7}]}
						""");
		runtime.reload();
		runtime.mutate(aid, "questplus:survival/logs", "grant");
		runtime.mutate(bid, "questplus:combat/skeletons", "grant");
		Runnable idle = () -> {
			if (runtime.isBusy(aid) || runtime.isBusy(bid)) {
				// Unpaced GameTests must give the disk writer time to complete.
				java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);
				helper.fail("Await durable team operation");
			}
		};
		var sequence = helper.startSequence();
		sequence.thenWaitUntil(idle).thenExecute(step(() -> {
			helper.assertTrue(
					dispatcher.execute("questplus team create Integration", a.createCommandSourceStack()) == 1,
					"Create team command");
		})).thenWaitUntil(step(() -> {
			idle.run();
			helper.assertTrue(runtime.player(aid).teamId != null, "Team ID persisted");
			team[0] = runtime.player(aid).teamId;
		})).thenExecute(step(() -> {
			helper.assertTrue(dispatcher.execute("questplus team invite " + b.getGameProfile().name(), a.createCommandSourceStack()) == 1,
					"Owner invitation");
		})).thenWaitUntil(idle).thenExecute(step(() -> {
			helper.assertTrue(dispatcher.execute("questplus team join Integration", b.createCommandSourceStack()) == 1,
					"Invitation accepted");
		})).thenWaitUntil(step(() -> {
			idle.run();
			helper.assertTrue(team[0].equals(runtime.player(bid).teamId), "Membership committed");
		})).thenExecute(step(() -> {
			var shared = runtime.state(aid);
			helper.assertTrue(shared == runtime.state(bid), "Team is sole progress authority");
			helper.assertTrue(
					shared.quests.get("questplus:survival/logs").completed
							&& shared.quests.get("questplus:combat/skeletons").completed,
					"Historical completion union");
			helper.assertTrue(
					!runtime.player(bid).entitlements.containsKey("questplus:survival/logs")
							&& !runtime.player(aid).entitlements.containsKey("questplus:combat/skeletons"),
					"Merge grants no historical reward");
			helper.assertTrue(
					dispatcher.execute("questplus claim questplus:survival/logs", b.createCommandSourceStack()) == 0,
					"Imported completion cannot be claimed");
			helper.assertTrue(dispatcher.execute("questplus team kick " + aid, b.createCommandSourceStack()) == 0,
					"Member cannot kick owner");
			server.services().nameToIdCache().add(new net.minecraft.server.players.NameAndId(b.getGameProfile()));
			server.getPlayerList().remove(b);
			runtime.disconnect(bid);
			BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
			for (int i = 0; i < 2; i++) {
				helper.getLevel().setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
				a.gameMode.destroyBlock(pos);
			}
		})).thenWaitUntil(step(() -> {
			idle.run();
			helper.assertTrue(runtime.player(bid).entitlements.containsKey("questplus:team_integration"),
					"Offline member entitled to new collective completion");
		})).thenExecute(step(() -> {
			helper.assertTrue(runtime.state(bid).quests.get("questplus:team_integration").completed,
					"Offline member sees shared completion");
			helper.assertTrue(QuestCommands.resolvePlayer(server, b.getGameProfile().name().toUpperCase(Locale.ROOT)).equals(bid),
					"Offline player name resolves to saved UUID");
			helper.assertTrue(QuestCommands.resolvePlayer(server, bid.toString()).equals(bid), "UUID remains supported");
			xp[0] = a.totalExperience;
			helper.assertTrue(
					dispatcher.execute("questplus claim questplus:team_integration", a.createCommandSourceStack()) == 1,
					"Collective quest personal claim");
		})).thenWaitUntil(step(() -> {
			idle.run();
			helper.assertTrue("delivered".equals(runtime.player(aid).claims.get("questplus:team_integration")),
					"Claim reserved then delivered");
			helper.assertTrue(a.totalExperience == xp[0] + 7, "Collective reward once");
		})).thenExecute(step(() -> runtime.changeTeam(m -> m.leave(bid)))).thenWaitUntil(step(() -> {
			idle.run();
			helper.assertTrue(runtime.player(bid).teamId == null, "Offline member separation");
		})).thenExecute(step(() -> {
			helper.assertTrue(runtime.player(bid).quests.get("questplus:team_integration").completed,
					"Leave keeps snapshot");
			runtime.mutate(bid, "questplus:mining/stone", "grant");
		})).thenWaitUntil(idle).thenExecute(step(() -> {
			helper.assertTrue(!runtime.state(aid).quests.containsKey("questplus:mining/stone"),
					"Departed member stops sharing progress");
			runtime.changeTeam(m -> m.create(bid, "Other"));
		})).thenWaitUntil(idle).thenExecute(step(() -> {
			boolean denied = false;
			try {
				runtime.changeTeam(m -> m.join(bid, team[0], System.currentTimeMillis()));
			} catch (IllegalArgumentException expected) {
				denied = true;
			}
			helper.assertTrue(denied, "Player cannot join two teams");
			runtime.changeTeam(m -> m.disband(bid));
		})).thenWaitUntil(idle)
				.thenExecute(step(() -> runtime.changeTeam(m -> m.invite(aid, bid, System.currentTimeMillis()))))
				.thenWaitUntil(idle)
				.thenExecute(step(() -> runtime.changeTeam(m -> m.join(bid, team[0], System.currentTimeMillis()))))
				.thenWaitUntil(idle).thenExecute(step(() -> {
					helper.assertTrue(runtime.state(aid).quests.get("questplus:mining/stone").completed,
							"Rejoin retroactively imports independent completion");
					helper.assertTrue(!runtime.player(aid).entitlements.containsKey("questplus:mining/stone"),
							"Rejoin has no retroactive reward");
					helper.assertTrue("delivered".equals(runtime.player(aid).claims.get("questplus:team_integration")),
							"Rejoin retains beneficiary ledger");
					runtime.changeTeam(m -> m.transfer(aid, bid));
				})).thenWaitUntil(idle).thenExecute(step(() -> runtime.changeTeam(m -> m.kick(bid, aid))))
				.thenWaitUntil(idle).thenExecute(step(() -> {
					helper.assertTrue(
							runtime.player(aid).teamId == null
									&& runtime.player(aid).quests.get("questplus:team_integration").completed,
							"Kick keeps snapshot");
					runtime.changeTeam(m -> m.disband(bid));
				})).thenWaitUntil(idle).thenExecute(step(() -> {
					helper.assertTrue(runtime.player(bid).teamId == null, "Dissolution separates offline owner");
					Files.writeString(Path.of(System.getProperty("questplus.integration.teams")),
							new Gson().toJson(Map.of("root",
									server.getWorldPath(LevelResource.ROOT).resolve("data/questplus").toAbsolutePath()
											.toString(),
									"a", aid.toString(), "b", bid.toString(), "team", team[0].toString())));
				})).thenSucceed();
	}
}
