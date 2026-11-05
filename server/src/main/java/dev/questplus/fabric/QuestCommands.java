package dev.questplus.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import java.util.*;
import static net.minecraft.commands.Commands.*;

final class QuestCommands {
    private QuestCommands() {}
    @FunctionalInterface private interface Action { void run(CommandSourceStack source) throws Exception; }
    private static int execute(CommandContext<CommandSourceStack> context,Action action) {
        try { action.run(context.getSource()); return 1; }
        catch (Exception e) {
            QuestPlusMod.LOGGER.error("Quest Plus command failed: {}",Objects.toString(e.getMessage(),e.getClass().getSimpleName()),e);
            context.getSource().sendFailure(Messages.text(context.getSource().getPlayer(),"questplus.error",Objects.toString(e.getMessage(),e.getClass().getSimpleName())));
            return 0;
        }
    }
    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("questplus")
            .executes(c -> execute(c,s -> s.sendSuccess(() -> Messages.text(s.getPlayer(),"questplus.help"),false)))
            .then(literal("reload").requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .executes(c -> execute(c,s -> {
                    QuestPlusMod.runtime().reload();
                    QuestPlusMod.LOGGER.info("Quest Plus reload succeeded: {} quests",QuestPlusMod.runtime().registry().all().size());
                    s.sendSuccess(() -> Messages.text(s.getPlayer(),"questplus.reloaded",QuestPlusMod.runtime().registry().all().size()),true);
                })))
            .then(literal("editor").requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .executes(c -> execute(c,s -> QuestPlusMod.hub().createSession(s)))
                .then(literal("approve").then(argument("code",StringArgumentType.word())
                    .executes(c -> execute(c,s -> QuestPlusMod.hub().approve(s,StringArgumentType.getString(c,"code"))))))
                .then(literal("revoke").executes(c -> execute(c,s -> QuestPlusMod.hub().revoke(s)))))
            .then(literal("rollback").requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .executes(c -> execute(c,s -> QuestPlusMod.hub().rollback(s))))
            .then(literal("import").requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(literal("confirm").executes(c -> execute(c,s -> QuestPlusMod.hub().confirmImport(s))))
                .then(argument("pack",StringArgumentType.word())
                    .executes(c -> execute(c,s -> QuestPlusMod.hub().previewImport(s,StringArgumentType.getString(c,"pack"))))))
            .then(literal("debug").requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .executes(c -> execute(c,s -> s.sendSuccess(() -> Component.literal("Quest Plus 0.2.0 | quests=" + QuestPlusMod.runtime().registry().all().size() + " | teams="+QuestPlusMod.runtime().teams().allTeams().size()+" | server-authoritative"),false)))
                .then(argument("player",UuidArgument.uuid()).executes(c -> execute(c,s -> {
                    var p = QuestPlusMod.runtime().player(UuidArgument.getUuid(c,"player"));
                    s.sendSuccess(() -> Component.literal("team="+p.teamId+" quests=" + p.quests.size() + " claims=" + p.claims+" entitlements="+p.entitlements.keySet()),false);
                }))))
            .then(literal("progress")
                .executes(c -> execute(c,s -> {
                    var player = s.getPlayerOrException();
                    var progress = QuestPlusMod.runtime().state(player.getUUID());
                    for (var q : QuestPlusMod.runtime().visibleQuests(player.getUUID()).stream().sorted(Comparator.comparing(dev.questplus.core.QuestDefinition::id)).limit(50).toList()) {
                        var p = progress.quests.get(q.id());
                        s.sendSuccess(() -> Component.literal(q.id() + " | " + QuestPlusMod.runtime().title(player,q) + " | " + (p==null ? "0" : p.completed ? "completed" : p.counters.toString())),false);
                    }
                }))
                .then(questArgument().executes(c -> execute(c,s -> {
                    var player = s.getPlayerOrException(); var runtime = QuestPlusMod.runtime();
                    String id = StringArgumentType.getString(c,"quest");
                    runtime.visibleQuest(player.getUUID(),id);
                    var progress = runtime.state(player.getUUID()); var personal=runtime.player(player.getUUID()); var p = runtime.engine().progress(progress,id);
                    s.sendSuccess(() -> Component.literal(id + " | " + (p==null ? "0" : p.completed ? "completed" : p.counters.toString())
                        + " | cycles="+(p==null ? 0 : p.cycles)+" tier="+(p==null ? 0 : p.tier)+" restart="+(p!=null && p.waitingRestart)
                        + " | rewards="+personal.entitlements.values().stream().filter(r -> r.questId().equals(id)).count()+" | " + runtime.engine().diagnostic(progress,id).orElse("compatible")),false);
                }))))
            .then(literal("submit")
                .then(argument("quest",StringArgumentType.word()).suggests((c,builder)->{
                    try{
                        QuestPlusMod.runtime().visibleQuests(c.getSource().getPlayerOrException().getUUID())
                            .stream().map(dev.questplus.core.QuestDefinition::id)
                            .filter(id->id.startsWith(builder.getRemaining())).sorted().forEach(builder::suggest);
                    }catch(Exception ignored){}
                    return builder.buildFuture();
                }).then(argument("objective",StringArgumentType.word()).suggests((c,builder)->{
                    try{
                        String id=StringArgumentType.getString(c,"quest");
                        QuestPlusMod.runtime().visibleQuest(c.getSource().getPlayerOrException().getUUID(),id)
                            .objectives().stream().filter(o->Set.of("item_submit","item_turn_in").contains(o.event()))
                            .map(dev.questplus.core.QuestDefinition.Objective::id)
                            .filter(x->x.startsWith(builder.getRemaining())).forEach(builder::suggest);
                    }catch(Exception ignored){}
                    return builder.buildFuture();
                }).executes(c->execute(c,source->{
                    var player=source.getPlayerOrException();
                    String id=StringArgumentType.getString(c,"quest");
                    String objective=StringArgumentType.getString(c,"objective");
                    int submitted=QuestPlusMod.runtime().submitItem(player,id,objective);
                    source.sendSuccess(()->Component.literal("Submitted "+submitted+" items to "+id+" / "+objective),false);
                })))))
            .then(literal("claim").then(questArgument().executes(c -> execute(c,s -> {
                var player=s.getPlayerOrException(); String id=StringArgumentType.getString(c,"quest");
                QuestPlusMod.runtime().visibleQuest(player.getUUID(),id); QuestPlusMod.runtime().claim(player,id);
            }))))
            .then(literal("restart").then(questArgument().executes(c -> execute(c,s -> {
                var player=s.getPlayerOrException(); String id=StringArgumentType.getString(c,"quest");
                QuestPlusMod.runtime().visibleQuest(player.getUUID(),id); QuestPlusMod.runtime().mutate(player.getUUID(),id,"restart");
                s.sendSuccess(() -> Messages.text(s.getPlayer(),"questplus.restarted"),false);
            }))))
            .then(literal("trigger").then(questArgument().executes(c -> execute(c,s -> {
                var player=s.getPlayerOrException(); String id=StringArgumentType.getString(c,"quest");
                QuestPlusMod.runtime().visibleQuest(player.getUUID(),id);
                QuestPlusMod.runtime().event(player,new dev.questplus.core.QuestEvent("manual",id,Set.of(),1,QuestPlusMod.context(player)));
            }))))
            .then(teamCommands())
            .then(literal("quest")
                .requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(literal("grant").then(argument("player",UuidArgument.uuid()).then(questArgument().executes(c -> mutate(c,true)))))
                .then(literal("reset").then(argument("player",UuidArgument.uuid()).then(questArgument().executes(c -> mutate(c,false)))))));
    }
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack,String> questArgument() {
        return argument("quest",StringArgumentType.greedyString()).suggests((context,builder) -> {
            try { var source=context.getSource(); var player=source.getPlayer();
                var quests=player==null || source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
                    ? QuestPlusMod.runtime().registry().all() : QuestPlusMod.runtime().visibleQuests(player.getUUID());
                quests.stream().map(dev.questplus.core.QuestDefinition::id)
                .filter(id -> id.startsWith(builder.getRemaining())).sorted().forEach(builder::suggest); } catch (IllegalStateException ignored) {}
            catch (java.io.IOException ignored) {}
            return builder.buildFuture();
        });
    }
    private static int mutate(CommandContext<CommandSourceStack> context,boolean grant) {
        return execute(context,s -> {
            var runtime = QuestPlusMod.runtime(); UUID uuid = UuidArgument.getUuid(context,"player");
            String id = StringArgumentType.getString(context,"quest");
            runtime.mutate(uuid,id,grant ? "grant" : "reset");
            s.sendSuccess(() -> Messages.text(s.getPlayer(),"questplus.changed",id,uuid.toString()),true);
        });
    }
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> teamCommands() {
        return literal("team")
            .then(literal("create").then(argument("name",StringArgumentType.greedyString()).executes(c -> team(c,m -> m.create(actor(c),StringArgumentType.getString(c,"name"))))))
            .then(literal("invite").then(argument("player",UuidArgument.uuid()).executes(c -> team(c,m -> m.invite(actor(c),UuidArgument.getUuid(c,"player"),System.currentTimeMillis())))))
            .then(literal("join").then(argument("team",UuidArgument.uuid()).executes(c -> team(c,m -> m.join(actor(c),UuidArgument.getUuid(c,"team"),System.currentTimeMillis())))))
            .then(literal("decline").then(argument("team",UuidArgument.uuid()).executes(c -> team(c,m -> m.decline(actor(c),UuidArgument.getUuid(c,"team"))))))
            .then(literal("leave").executes(c -> team(c,m -> m.leave(actor(c)))))
            .then(literal("kick").then(argument("player",UuidArgument.uuid()).executes(c -> team(c,m -> m.kick(actor(c),UuidArgument.getUuid(c,"player"))))))
            .then(literal("transfer").then(argument("player",UuidArgument.uuid()).executes(c -> team(c,m -> m.transfer(actor(c),UuidArgument.getUuid(c,"player"))))))
            .then(literal("disband").executes(c -> team(c,m -> m.disband(actor(c)))))
            .then(literal("invitations").executes(c -> execute(c,s -> {
                var invitations=QuestPlusMod.runtime().teams().invitationsFor(s.getPlayerOrException().getUUID(),System.currentTimeMillis());
                if (invitations.isEmpty()) s.sendSuccess(() -> Messages.text(s.getPlayer(),"questplus.no_invitations"),false);
                for (var invite:invitations)
                    s.sendSuccess(() -> Messages.text(s.getPlayer(),"questplus.team_invitation",invite.name,invite.id.toString()),false);
            })))
            .then(literal("info").executes(c -> execute(c,s -> {
                var value=QuestPlusMod.runtime().teams().teamOf(s.getPlayerOrException().getUUID());
                s.sendSuccess(() -> value.<Component>map(t -> Component.literal(t.id+" | "+t.name+" | owner="+t.owner+" | members="+t.members+" | invites="+t.invitations.keySet()))
                    .orElseGet(() -> Messages.text(s.getPlayer(),"questplus.no_team")),false);
            })));
    }
    private static UUID actor(CommandContext<CommandSourceStack> context) throws java.io.IOException {
        var player=context.getSource().getPlayer();
        if (player==null) throw new IllegalArgumentException("Team management requires a player");
        QuestPlusMod.runtime().player(player.getUUID()); return player.getUUID();
    }
    private static int team(CommandContext<CommandSourceStack> context,QuestRuntime.TeamAction action) {
        return execute(context,s -> QuestPlusMod.runtime().changeTeam(action).whenComplete((ok,error) -> {
            if (error==null) s.sendSuccess(() -> Messages.text(s.getPlayer(),"questplus.team_saved"),false);
            else s.sendFailure(Messages.text(s.getPlayer(),"questplus.error","Team transaction failed; inspect the server log"));
        }));
    }
}
