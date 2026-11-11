package dev.questplus.fabric.client;

import dev.questplus.compat.ClientAdapter;
import dev.questplus.fabric.QuestPlusMod;
import dev.questplus.fabric.QuestRuntime;
import dev.questplus.fabric.client.ui.QuestButton;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Real tablet controls, C2S requests, durable server mutations and S2C refreshes. */
final class TeamClientGameTest {
	static void run(ClientGameTestContext context) {
		try (var world = context.worldBuilder().create()) {
			var server = world.getServer();
			context.waitFor(client -> QuestPlusClient.STATE.ready, 600);
			UUID owner = context.computeOnClient(client -> client.player.getUUID());
			UUID member = new UUID(81, 1), other = new UUID(81, 2);
			context.runOnClient(client -> {
				QuestPlusClient.settings.tablet = false;
				QuestPlusClient.settings.animations = false;
			});
			context.getInput().resizeWindow(1280, 800);
			context.setScreen(QuestScreen::new);
			click(context, "team");
			text(context, "Team UI test");
			click(context, "create");
			ready(context, "OWNER", 1);
			UUID team = context.computeOnClient(client -> UUID.fromString(QuestPlusClient.STATE.team.get("id").getAsString()));
			for (UUID id : List.of(member, other)) {
				change(context, server, m -> m.invite(owner, id, System.currentTimeMillis()));
				change(context, server, m -> m.join(id, team, System.currentTimeMillis()));
			}
			ready(context, "OWNER", 3);
			for (int i = 3; i <= 9; i++) {
				UUID id = new UUID(81, i);
				change(context, server, m -> m.invite(owner, id, System.currentTimeMillis()));
				change(context, server, m -> m.join(id, team, System.currentTimeMillis()));
			}
			ready(context, "OWNER", 10);
			click(context, "next");
			ready(context, "OWNER", 10);
			context.takeScreenshot("teams-paginated-owner-controls");
			for (int i = 3; i <= 9; i++) {
				UUID id = new UUID(81, i);
				change(context, server, m -> m.kick(owner, id));
			}
			ready(context, "OWNER", 3);
			buttons(context, "previous", 0);
			buttons(context, "promote", 2);
			buttons(context, "kick", 2);
			click(context, "promote");
			click(context, "confirm");
			role(context, member, "MANAGER");
			click(context, "demote");
			click(context, "confirm");
			role(context, member, "MEMBER");
			click(context, "promote");
			click(context, "confirm");
			role(context, member, "MANAGER");
			context.takeScreenshot("teams-owner-grades-2d");
			click(context, "team_options");
			text(context, "Renamed from tablet");
			click(context, "rename");
			context.waitFor(client -> "Renamed from tablet".equals(QuestPlusClient.STATE.team.get("name").getAsString())
					&& !QuestClientState.flag(QuestPlusClient.STATE.team, "saving"), 300);
			click(context, "sharing_on");
			buttons(context, "kick", 0);
			click(context, "confirm");
			context.waitFor(client -> !QuestClientState.flag(QuestPlusClient.STATE.team, "sharedQuestProgress")
					&& !QuestClientState.flag(QuestPlusClient.STATE.team, "saving"), 300);
			click(context, "sharing_off");
			click(context, "cancel");
			buttons(context, "sharing_off", 1);
			context.takeScreenshot("teams-options-personal");
			click(context, "sharing_off");
			click(context, "confirm");
			context.waitFor(client -> QuestClientState.flag(QuestPlusClient.STATE.team, "sharedQuestProgress")
					&& !QuestClientState.flag(QuestPlusClient.STATE.team, "saving"), 300);
			click(context, "back");
			text(context, new UUID(81, 99).toString());
			click(context, "invite");
			context.waitFor(client -> QuestPlusClient.STATE.team.getAsJsonArray("invitations").size() == 1
					&& !QuestClientState.flag(QuestPlusClient.STATE.team, "saving"), 300);
			click(context, "team_options");
			click(context, "revoke");
			context.waitFor(client -> QuestPlusClient.STATE.team.getAsJsonArray("invitations").isEmpty()
					&& !QuestClientState.flag(QuestPlusClient.STATE.team, "saving"), 300);
			click(context, "back");
			context.runOnClient(client -> {
				ClientAdapter.INSTANCE.setScreen(client, null);
				QuestPlusClient.settings.tablet = true;
				QuestClientTabletFlow.toggle(client);
			});
			context.waitTicks(15);
			context.takeScreenshot("teams-owner-grades-3d");
			click(context, "transfer");
			click(context, "confirm");
			ready(context, "MANAGER", 3);
			buttons(context, "promote", 0);
			buttons(context, "demote", 0);
			buttons(context, "transfer", 0);
			buttons(context, "disband", 0);
			buttons(context, "kick", 1);
			buttons(context, "team_options", 1);
			context.takeScreenshot("teams-manager-permissions-3d");
			click(context, "kick");
			click(context, "confirm");
			ready(context, "MANAGER", 2);
			server.runOnServer(s -> {
				try {
					if (QuestPlusMod.runtime().player(other).teamId != null)
						throw new AssertionError("Offline member kick did not commit");
				} catch (java.io.IOException error) {
					throw new AssertionError(error);
				}
			});
			change(context, server, m -> m.setManager(member, owner, false));
			ready(context, "MEMBER", 2);
			buttons(context, "invite", 0);
			buttons(context, "team_options", 0);
			buttons(context, "kick", 0);
			buttons(context, "leave", 1);
			context.takeScreenshot("teams-member-permissions-3d");
			// A stale or forged request must still be refused by the server.
			context.runOnClient(client -> QuestPlusClient.action("promote", member.toString()));
			context.waitFor(client -> QuestPlusClient.STATE.error != null, 300);
			role(context, owner, "MEMBER");
			change(context, server, m -> m.setManager(member, owner, true));
			ready(context, "MANAGER", 2);
			click(context, "team_options");
			change(context, server, m -> m.kick(member, owner));
			context.waitFor(client -> QuestPlusClient.STATE.team == null, 300);
			context.waitTicks(3);
			buttons(context, "create", 1);
			buttons(context, "rename", 0);
			context.takeScreenshot("teams-kicked-live-refresh");
			change(context, server, m -> m.invite(member, owner, System.currentTimeMillis()));
			context.waitFor(client -> QuestPlusClient.STATE.invitations.size() == 1, 300);
			click(context, "join");
			ready(context, "MEMBER", 2);
			change(context, server, m -> m.transfer(member, owner));
			ready(context, "OWNER", 2);
			click(context, "disband");
			click(context, "confirm");
			context.waitFor(client -> QuestPlusClient.STATE.team == null, 300);
			context.waitTicks(3);
			buttons(context, "create", 1);
			context.takeScreenshot("teams-disbanded-live-refresh");
			context.runOnClient(client -> ClientAdapter.INSTANCE.screen(client).onClose());
		}
	}

	private static void change(ClientGameTestContext context, TestServerContext server, QuestRuntime.TeamAction action) {
		var pending = new AtomicReference<CompletableFuture<Void>>();
		server.runOnServer(s -> {
			try {
				pending.set(QuestPlusMod.runtime().changeTeam(action));
			} catch (java.io.IOException error) {
				throw new AssertionError(error);
			}
		});
		dev.questplus.compat.ClientGameTestShims.waitForServer(context, server, s -> pending.get().isDone(), 300);
		pending.get().join();
	}

	private static void ready(ClientGameTestContext context, String role, int members) {
		context.waitFor(client -> QuestPlusClient.STATE.team != null
				&& role.equals(QuestPlusClient.STATE.team.get("role").getAsString())
				&& QuestPlusClient.STATE.team.getAsJsonArray("members").size() == members
				&& !QuestClientState.flag(QuestPlusClient.STATE.team, "saving"), 300);
		context.waitTicks(3);
		context.runOnClient(client -> {
			var controls = ClientAdapter.INSTANCE.screen(client).children().stream().filter(AbstractWidget.class::isInstance)
					.map(AbstractWidget.class::cast).filter(c -> c.visible).toList();
			for (int i = 0; i < controls.size(); i++)
				for (int j = i + 1; j < controls.size(); j++) {
					var a = controls.get(i);
					var b = controls.get(j);
					if (a.getX() < b.getRight() && b.getX() < a.getRight()
							&& a.getY() < b.getBottom() && b.getY() < a.getBottom())
						throw new AssertionError("Overlapping team controls: " + a.getMessage().getString()
								+ " / " + b.getMessage().getString());
				}
		});
	}

	private static void role(ClientGameTestContext context, UUID id, String role) {
		context.waitFor(client -> QuestPlusClient.STATE.team != null
				&& !QuestClientState.flag(QuestPlusClient.STATE.team, "saving")
				&& QuestPlusClient.STATE.team.getAsJsonArray("members").asList().stream()
				.anyMatch(v -> id.toString().equals(v.getAsJsonObject().get("id").getAsString())
						&& role.equals(v.getAsJsonObject().get("role").getAsString())), 300);
		context.waitTicks(3);
	}

	private static void text(ClientGameTestContext context, String value) {
		context.runOnClient(client -> ClientAdapter.INSTANCE.screen(client).children().stream().filter(EditBox.class::isInstance)
				.map(EditBox.class::cast).findFirst().orElseThrow().setValue(value));
	}

	private static void buttons(ClientGameTestContext context, String key, long expected) {
		context.runOnClient(client -> {
			long count = ClientAdapter.INSTANCE.screen(client).children().stream().filter(QuestButton.class::isInstance)
					.map(QuestButton.class::cast)
					.filter(b -> b.active && b.getMessage().getString().equals(QuestPlusClient.tr(key).getString())).count();
			if (count != expected)
				throw new AssertionError(key + ": expected " + expected + " controls, got " + count);
		});
	}

	private static void click(ClientGameTestContext context, String key) {
		context.waitTicks(3);
		context.runOnClient(client -> {
			if (ClientAdapter.INSTANCE.screen(client).getFocused() != null)
				ClientAdapter.INSTANCE.screen(client).getFocused().setFocused(false);
		});
		QuestClientGameTest.clickButton(context, key);
	}
}
