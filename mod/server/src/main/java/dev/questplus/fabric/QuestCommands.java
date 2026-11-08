package dev.questplus.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import java.util.*;
import static net.minecraft.commands.Commands.*;

final class QuestCommands {
	private QuestCommands() {
	}

	@FunctionalInterface
	private interface Action {
		void run(CommandSourceStack source) throws Exception;
	}

	private static int execute(CommandContext<CommandSourceStack> context, Action action) {
		try {
			action.run(context.getSource());
			return 1;
		} catch (Exception e) {
			QuestPlusMod.LOGGER.error("Quest Plus command failed: {}",
					Objects.toString(e.getMessage(), e.getClass().getSimpleName()), e);
			context.getSource().sendFailure(Messages.text(context.getSource().getPlayer(), "questplus.error",
					Objects.toString(e.getMessage(), e.getClass().getSimpleName())));
			return 0;
		}
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher
				.register(
						literal("questplus")
								.executes(c -> execute(c,
										s -> s.sendSuccess(() -> Messages.text(s.getPlayer(), "questplus.help"),
												false)))
								.then(literal("reload")
										.requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
										.executes(c -> execute(c, s -> {
											QuestPlusMod.runtime().reload();
											QuestPlusMod.LOGGER.info("Quest Plus reload succeeded: {} quests",
													QuestPlusMod.runtime().registry().all().size());
											s.sendSuccess(() -> Messages.text(s.getPlayer(), "questplus.reloaded",
													QuestPlusMod.runtime().registry().all().size()), true);
										})))
								.then(literal("editor")
										.requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
										.executes(c -> execute(c, s -> QuestPlusMod.hub().createSession(s)))
										.then(literal("world").executes(
												c -> execute(c, s -> QuestPlusMod.hub().createSession(s, false))))
										.then(literal("default").executes(
												c -> execute(c, s -> QuestPlusMod.hub().createSession(s, true))))
										.then(literal("approve").then(
												argument("code", StringArgumentType.word()).executes(c -> execute(c,
														s -> QuestPlusMod.hub().approve(s,
																StringArgumentType.getString(c, "code"))))))
										.then(literal("revoke")
												.executes(c -> execute(c, s -> QuestPlusMod.hub().revoke(s)))))
								.then(literal("retrieve")
										.requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
										.executes(c -> execute(c, s -> QuestPlusMod.hub().previewRetrieve(s)))
										.then(literal("confirm")
												.executes(c -> execute(c, s -> QuestPlusMod.hub().confirmRetrieve(s)))))
								.then(literal("rollback")
										.requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
										.executes(c -> execute(c, s -> QuestPlusMod.hub().rollback(s))))
								.then(literal("import")
										.requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
										.then(literal("confirm")
												.executes(c -> execute(c, s -> QuestPlusMod.hub().confirmImport(s))))
										.then(argument("pack", StringArgumentType.word()).executes(c -> execute(c,
												s -> QuestPlusMod.hub().previewImport(s,
														StringArgumentType.getString(c, "pack"))))))
								.then(migrationCommand())
								.then(literal("debug")
										.requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
										.executes(
												c -> execute(c,
														s -> s.sendSuccess(
																() -> Component.literal("Quest Plus 0.2.0 | quests="
																		+ QuestPlusMod.runtime().registry().all().size()
																		+ " | teams="
																		+ QuestPlusMod.runtime().teams().allTeams()
																				.size()
																		+ " | server-authoritative"),
																false)))
										.then(playerArgument().executes(c -> execute(c, s -> {
											var p = QuestPlusMod.runtime().player(playerId(c));
											s.sendSuccess(() -> Component.literal(
													"team=" + p.teamId + " quests=" + p.quests.size() + " claims="
															+ p.claims + " entitlements=" + p.entitlements.keySet()),
													false);
										}))))
								.then(literal("recovery")
										.requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
										.then(literal("inspect").then(
												playerArgument().executes(c -> execute(c, s -> {
													UUID id = playerId(c);
													var claims = QuestPlusMod.runtime().reservedClaims(id);
													s.sendSuccess(
															() -> Component.literal(
																	"Reserved claims for " + id + ": " + claims),
															false);
												}))))
										.then(literal("resolve").then(playerArgument()
												.then(argument("claim", StringArgumentType.string())
														.then(argument("outcome", StringArgumentType.word()).then(
																argument("reason", StringArgumentType.greedyString())
																		.executes(c -> execute(c, s -> {
																			UUID id = playerId(c);
																			String key = StringArgumentType.getString(c,
																					"claim");
																			String outcome = StringArgumentType
																					.getString(c, "outcome");
																			QuestPlusMod.runtime().resolveReservedClaim(
																					id, key, outcome, StringArgumentType
																							.getString(c, "reason"),
																					s.getTextName());
																			s.sendSuccess(
																					() -> Component
																							.literal("Resolved claim "
																									+ key + " for " + id
																									+ " as " + outcome),
																					true);
																		}))))))))
								.then(literal("progress").executes(c -> execute(c, s -> {
									var player = s.getPlayerOrException();
									var progress = QuestPlusMod.runtime().state(player.getUUID());
									for (var q : QuestPlusMod.runtime().visibleQuests(player.getUUID()).stream()
											.sorted(Comparator.comparing(dev.questplus.core.QuestDefinition::id))
											.limit(50).toList()) {
										var p = progress.quests.get(q.id());
										s.sendSuccess(
												() -> Component.literal(q.id() + " | "
														+ QuestPlusMod.runtime().title(player, q) + " | "
														+ (p == null ? "0"
																: p.completed ? "completed" : p.counters.toString())),
												false);
									}
								})).then(literal("player")
										.requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
										.then(playerArgument().executes(c -> execute(c, s -> {
												var progress = QuestPlusMod.runtime().state(playerId(c));
												progress.quests.entrySet().stream().sorted(Map.Entry.comparingByKey()).limit(100)
													.forEach(entry -> s.sendSuccess(() -> Component.literal(entry.getKey() + " | "
																	+ (entry.getValue().completed ? "completed" : entry.getValue().counters)), false));
										})))).then(questArgument().executes(c -> execute(c, s -> {
									var player = s.getPlayerOrException();
									var runtime = QuestPlusMod.runtime();
									String id = StringArgumentType.getString(c, "quest");
									runtime.visibleQuest(player.getUUID(), id);
									var progress = runtime.state(player.getUUID());
									var personal = runtime.player(player.getUUID());
									var p = runtime.engine().progress(progress, id);
									s.sendSuccess(
											() -> Component.literal(id + " | "
													+ (p == null ? "0"
															: p.completed ? "completed" : p.counters.toString())
													+ " | cycles=" + (p == null ? 0 : p.cycles) + " tier="
													+ (p == null ? 0 : p.tier) + " restart="
													+ (p != null && p.waitingRestart) + " | rewards="
													+ personal.entitlements.values().stream()
															.filter(r -> r.questId().equals(id)).count()
													+ " | "
													+ runtime.engine().diagnostic(progress, id).orElse("compatible")),
											false);
								}))))
								.then(literal("diagnose")
										.requires(
												src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
										.then(questArgument().executes(c -> execute(c, src -> {
											var actor = src.getPlayerOrException();
											var runtime = QuestPlusMod.runtime();
											String id = StringArgumentType.getString(c, "quest");
											var quest = runtime.visibleQuest(actor.getUUID(), id);
											var state = runtime.state(actor.getUUID());
											var progress = state.quests.get(id);
											Set<String> completed = new HashSet<>();
											state.quests.forEach((name, entry) -> {
												if (entry.completed)
													completed.add(name);
											});
											var context = QuestPlusMod.context(actor);
											boolean unlocked = quest.requires() == null
													|| quest.requires().matches(context, completed);
											src.sendSuccess(() -> Component.literal("QuestPlus diagnostic " + id
													+ " | prerequisites=" + unlocked + " | state="
													+ (progress == null ? "not started"
															: progress.completed ? "completed" : "in progress")
													+ " | " + runtime.engine().diagnostic(state, id)
															.orElse("schema compatible")),
													false);
											for (var objective : quest.objectives().stream().limit(32).toList()) {
												long got = progress == null ? 0
														: progress.counters.getOrDefault(objective.id(), 0L);
												boolean conditions = objective.conditions() == null
														|| objective.conditions().matches(context, completed);
												src.sendSuccess(() -> Component.literal(objective.id() + " ["
														+ objective.event() + "] " + objective.target() + " " + got
														+ "/" + objective.count() + " | conditions=" + conditions
														+ (objective.optional() ? " | optional" : "")), false);
											}
										}))))
								.then(literal("submit")
										.then(argument("quest", StringArgumentType.word()).suggests((c, builder) -> {
											try {
												QuestPlusMod.runtime()
														.visibleQuests(c.getSource().getPlayerOrException().getUUID())
														.stream().map(dev.questplus.core.QuestDefinition::id)
														.filter(id -> id.startsWith(builder.getRemaining())).sorted()
														.forEach(builder::suggest);
											} catch (Exception ignored) {
											}
											return builder.buildFuture();
										}).then(argument("objective", StringArgumentType.word())
												.suggests((c, builder) -> {
													try {
														String id = StringArgumentType.getString(c, "quest");
														QuestPlusMod.runtime().visibleQuest(
																c.getSource().getPlayerOrException().getUUID(), id)
																.objectives().stream()
																.filter(o -> Set
																		.of("item_submit", "item_turn_in",
																				"xp_submit_levels", "xp_submit_points",
																				"fluid_submit", "energy_submit")
																		.contains(o.event()))
																.map(dev.questplus.core.QuestDefinition.Objective::id)
																.filter(x -> x.startsWith(builder.getRemaining()))
																.forEach(builder::suggest);
													} catch (Exception ignored) {
													}
													return builder.buildFuture();
												}).executes(c -> execute(c, source -> {
													var player = source.getPlayerOrException();
													String id = StringArgumentType.getString(c, "quest");
													String objective = StringArgumentType.getString(c, "objective");
													int submitted = QuestPlusMod.runtime().submitItem(player, id,
															objective);
													source.sendSuccess(() -> Component.literal("Submitted " + submitted
															+ " items to " + id + " / " + objective), false);
												})))))
								.then(literal("choose").then(
										questArgument().then(argument("option", IntegerArgumentType.integer(1, 32))
												.executes(c -> execute(c, source -> {
													var actor = source.getPlayerOrException();
													String id = StringArgumentType.getString(c, "quest");
													int oneBased = IntegerArgumentType.getInteger(c, "option");
													QuestPlusMod.runtime().chooseReward(actor, id, oneBased - 1);
												})))))
								.then(literal("claim").then(questArgument().executes(c -> execute(c, s -> {
									var player = s.getPlayerOrException();
									String id = StringArgumentType.getString(c, "quest");
									QuestPlusMod.runtime().visibleQuest(player.getUUID(), id);
									QuestPlusMod.runtime().claim(player, id);
								})))).then(literal("restart").then(questArgument().executes(c -> execute(c, s -> {
									var player = s.getPlayerOrException();
									String id = StringArgumentType.getString(c, "quest");
									QuestPlusMod.runtime().visibleQuest(player.getUUID(), id);
									QuestPlusMod.runtime().mutate(player.getUUID(), id, "restart");
									s.sendSuccess(() -> Messages.text(s.getPlayer(), "questplus.restarted"), false);
								})))).then(literal("trigger").then(questArgument().executes(c -> execute(c, s -> {
									var player = s.getPlayerOrException();
									String id = StringArgumentType.getString(c, "quest");
									QuestPlusMod.runtime().interactObjective(player, id, null);
								})))).then(teamCommands())
								.then(literal("quest")
										.requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
										.then(literal("grant").then(playerArgument()
												.then(questArgument().executes(c -> mutate(c, true)))))
										.then(literal("reset").then(playerArgument()
												.then(questArgument().executes(c -> mutate(c, false)))))));
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> migrationCommand() {
		var command = literal("migrate-ftb")
				.requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
		command.then(literal("preview").then(argument("source", UuidArgument.uuid())
				.then(playerArgument().executes(c -> execute(c, s -> {
					String review = QuestPlusMod.runtime().migrations().preview(s.getTextName(),
							UuidArgument.getUuid(c, "source"), playerId(c));
					s.sendSuccess(() -> Component.literal(review), false);
				})))));
		command.then(literal("confirm").executes(
				c -> execute(c, s -> migrationResult(s, QuestPlusMod.runtime().migrations().confirm(s.getTextName()),
						"FTB progress imported; private rollback backup saved."))));
		command.then(literal("rollback").then(argument("operation", UuidArgument.uuid()).executes(c -> execute(c,
				s -> migrationResult(s,
						QuestPlusMod.runtime().migrations().rollback(UuidArgument.getUuid(c, "operation")),
						"FTB migration rolled back.")))));
		return command;
	}

	private static void migrationResult(CommandSourceStack source, java.util.concurrent.CompletableFuture<Void> result,
			String message) {
		result.whenComplete((ok, error) -> {
			if (error != null)
				source.sendFailure(Component.literal("FTB migration failed: " + error.getMessage()));
			else
				source.sendSuccess(() -> Component.literal(message), false);
		});
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> questArgument() {
		return argument("quest", StringArgumentType.greedyString()).suggests((context, builder) -> {
			try {
				var source = context.getSource();
				var player = source.getPlayer();
				var quests = player == null || source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
						? QuestPlusMod.runtime().registry().all()
						: QuestPlusMod.runtime().visibleQuests(player.getUUID());
				quests.stream().map(dev.questplus.core.QuestDefinition::id)
						.filter(id -> id.startsWith(builder.getRemaining())).sorted().forEach(builder::suggest);
			} catch (IllegalStateException ignored) {
			} catch (java.io.IOException ignored) {
			}
			return builder.buildFuture();
		});
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> playerArgument() {
		return argument("player", StringArgumentType.word()).suggests((context, builder) -> {
			String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
			var server = context.getSource().getServer();
			var names = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
			names.addAll(Arrays.asList(server.getPlayerNames()));
			knownPlayers(server).forEach(profile -> names.add(profile.name()));
			names.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).forEach(builder::suggest);
			return builder.buildFuture();
		});
	}

	private static UUID playerId(CommandContext<CommandSourceStack> context) {
		return resolvePlayer(context.getSource().getServer(), StringArgumentType.getString(context, "player"));
	}

	static UUID resolvePlayer(net.minecraft.server.MinecraftServer server, String value) {
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException ignored) {
			var online = server.getPlayerList().getPlayers().stream()
					.filter(player -> player.getGameProfile().name().equalsIgnoreCase(value)).findFirst();
			if (online.isPresent())
				return online.get().getUUID();
			return knownPlayers(server).stream().filter(profile -> profile.name().equalsIgnoreCase(value))
					.map(net.minecraft.server.players.NameAndId::id).findFirst()
					.orElseThrow(() -> new IllegalArgumentException("Unknown player: " + value));
		}
	}

	private static List<net.minecraft.server.players.NameAndId> knownPlayers(net.minecraft.server.MinecraftServer server) {
		var directory = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/questplus/players");
		if (!java.nio.file.Files.isDirectory(directory))
			return List.of();
		try (var files = java.nio.file.Files.list(directory)) {
			return files.map(path -> path.getFileName().toString()).filter(name -> name.endsWith(".json"))
					.map(name -> {
						try {
							return server.services().nameToIdCache().get(UUID.fromString(name.substring(0, name.length() - 5))).orElse(null);
						} catch (IllegalArgumentException ignored) {
							return null;
						}
					}).filter(Objects::nonNull).toList();
		} catch (java.io.IOException error) {
			QuestPlusMod.LOGGER.warn("Cannot list known Quest Plus players", error);
			return List.of();
		}
	}

	private static int mutate(CommandContext<CommandSourceStack> context, boolean grant) {
		return execute(context, s -> {
			var runtime = QuestPlusMod.runtime();
			UUID uuid = playerId(context);
			String id = StringArgumentType.getString(context, "quest");
			runtime.mutate(uuid, id, grant ? "grant" : "reset");
			s.sendSuccess(() -> Messages.text(s.getPlayer(), "questplus.changed", id, uuid.toString()), true);
		});
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> teamCommands() {
		return literal("team")
				.then(literal("create").then(argument("name", StringArgumentType.greedyString())
						.executes(c -> team(c, m -> m.create(actor(c), StringArgumentType.getString(c, "name"))))))
				.then(literal("invite").then(playerArgument().executes(c -> team(c,
						m -> m.invite(actor(c), playerId(c), System.currentTimeMillis())))))
				.then(literal("join").then(teamArgument().executes(c -> team(c,
						m -> m.join(actor(c), invitedTeam(c), System.currentTimeMillis())))))
				.then(literal("decline").then(teamArgument()
						.executes(c -> team(c, m -> m.decline(actor(c), invitedTeam(c))))))
				.then(literal("leave").executes(c -> team(c, m -> m.leave(actor(c)))))
				.then(literal("kick").then(playerArgument()
						.executes(c -> team(c, m -> m.kick(actor(c), playerId(c))))))
				.then(literal("promote").then(playerArgument()
						.executes(c -> team(c, m -> m.setManager(actor(c), playerId(c), true)))))
				.then(literal("demote").then(playerArgument()
						.executes(c -> team(c, m -> m.setManager(actor(c), playerId(c), false)))))
				.then(literal("transfer").then(playerArgument()
						.executes(c -> team(c, m -> m.transfer(actor(c), playerId(c))))))
				.then(literal("disband").executes(c -> team(c, m -> m.disband(actor(c)))))
				.then(literal("invitations").executes(c -> execute(c, s -> {
					var invitations = QuestPlusMod.runtime().teams().invitationsFor(s.getPlayerOrException().getUUID(),
							System.currentTimeMillis());
					if (invitations.isEmpty())
						s.sendSuccess(() -> Messages.text(s.getPlayer(), "questplus.no_invitations"), false);
					for (var invite : invitations)
						s.sendSuccess(() -> Messages.text(s.getPlayer(), "questplus.team_invitation", invite.name,
								invite.id.toString()), false);
				}))).then(literal("info").executes(c -> execute(c, s -> {
					var value = QuestPlusMod.runtime().teams().teamOf(s.getPlayerOrException().getUUID());
					s.sendSuccess(() -> value
							.<Component>map(t -> Component.literal(t.id + " | " + t.name + " | owner=" + t.owner
									+ " | members=" + t.members + " | managers=" + t.managers + " | invites=" + t.invitations.keySet()))
							.orElseGet(() -> Messages.text(s.getPlayer(), "questplus.no_team")), false);
				})));
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> teamArgument() {
		return argument("team", StringArgumentType.string()).suggests((context, builder) -> {
			try {
				var invitations = QuestPlusMod.runtime().teams().invitationsFor(actor(context), System.currentTimeMillis());
				for (var invitation : invitations) {
					String name = invitation.name.contains(" ") ? "\"" + invitation.name.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" : invitation.name;
					if (name.toLowerCase(Locale.ROOT).startsWith(builder.getRemaining().toLowerCase(Locale.ROOT)))
						builder.suggest(name);
				}
			} catch (Exception ignored) {
			}
			return builder.buildFuture();
		});
	}

	private static UUID invitedTeam(CommandContext<CommandSourceStack> context) throws java.io.IOException {
		String value = StringArgumentType.getString(context, "team");
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException ignored) {
			var matches = QuestPlusMod.runtime().teams().invitationsFor(actor(context), System.currentTimeMillis()).stream()
					.filter(team -> team.name.equalsIgnoreCase(value)).toList();
			if (matches.size() != 1)
				throw new IllegalArgumentException(matches.isEmpty() ? "Unknown team invitation: " + value : "Ambiguous team name; use its UUID");
			return matches.getFirst().id;
		}
	}

	private static UUID actor(CommandContext<CommandSourceStack> context) throws java.io.IOException {
		var player = context.getSource().getPlayer();
		if (player == null)
			throw new IllegalArgumentException("Team management requires a player");
		QuestPlusMod.runtime().player(player.getUUID());
		return player.getUUID();
	}

	private static int team(CommandContext<CommandSourceStack> context, QuestRuntime.TeamAction action) {
		return execute(context, s -> QuestPlusMod.runtime().changeTeam(action).whenComplete((ok, error) -> {
			if (error == null)
				s.sendSuccess(() -> Messages.text(s.getPlayer(), "questplus.team_saved"), false);
			else
				s.sendFailure(Messages.text(s.getPlayer(), "questplus.error",
						"Team transaction failed; inspect the server log"));
		}));
	}
}
