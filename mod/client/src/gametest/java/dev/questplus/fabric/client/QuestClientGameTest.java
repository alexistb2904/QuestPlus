package dev.questplus.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.questplus.fabric.client.ui.QuestButton;
import net.fabricmc.fabric.api.client.gametest.v1.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.*;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;

/**
 * Exercises real client initialization, packet transfer, rendering and
 * authoritative mutations.
 */
public final class QuestClientGameTest implements FabricClientGameTest {
	public static net.minecraft.world.entity.player.PlayerSkin skin;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (Boolean.getBoolean("questplus.teamClientTest")) {
			TeamClientGameTest.run(context);
			return;
		}
		if (Boolean.getBoolean("questplus.tabletThirdPersonOnly")) {
			try (var world = context.worldBuilder().create()) {
				context.waitFor(client -> QuestPlusClient.STATE.ready, 600);
				remotePoseTrial(context, world.getServer());
			}
			return;
		}
		if (Boolean.getBoolean("questplus.tabletOptions")) {
			TabletOptionsGameTest.run(context);
			return;
		}
		if (Boolean.getBoolean("questplus.recipeReturn")) {
			RecipeReturnGameTest.run(context);
			return;
		}
		if (Boolean.getBoolean("questplus.tabletCarryOnly")) {
			TabletCarryGameTest.run(context);
			return;
		}
		if (Boolean.getBoolean("questplus.tabletCompatibility")) {
			TabletCompatibilityTest.run(context);
			return;
		}
		try (var world = context.worldBuilder().create()) {
			context.waitFor(client -> QuestPlusClient.STATE.ready, 600);
			context.runOnClient(client -> {
				if (QuestPlusClient.STATE.quests.isEmpty())
					throw new AssertionError("Empty initial catalogue");
			});
			context.setScreen(QuestScreen::new);
			context.waitTicks(30);
			context.takeScreenshot("questplus-tablet-initial");
			rewardFixture(context, world.getServer());
			if (FabricLoader.getInstance().isModLoaded("techreborn"))
				ResourceClientGameTest.run(context, world.getServer());
			diagramFixture(context);
			context.runOnClient(client -> {
				client.gui.screen().keyPressed(new KeyEvent(InputConstants.KEY_F6, 0, 0));
				client.gui.screen().keyPressed(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
			});
			context.waitTicks(3);
			context.takeScreenshot("questplus-details");
			context.runOnClient(
					client -> client.gui.screen().keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0)));
			for (int[] resolution : new int[][] { { 1280, 800 }, { 1024, 768 }, { 1600, 900 } }) {
				context.getInput().resizeWindow(resolution[0], resolution[1]);
				context.waitTicks(5);
				context.runOnClient(client -> {
					var screen = (QuestScreen) client.gui.screen();
					screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, 2);
					screen.keyPressed(new KeyEvent(InputConstants.KEY_HOME, 0, 0));
				});
				context.takeScreenshot("questplus-" + resolution[0] + "x" + resolution[1]);
			}
			clickButton(context, "team");
			context.waitTicks(3);
			context.runOnClient(client -> {
				var screen = (QuestScreen) client.gui.screen();
				screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst()
						.orElseThrow().setValue("Client test team");
			});
			clickButton(context, "create");
			context.waitFor(client -> QuestPlusClient.STATE.team != null, 300);
			world.getServer().runOnServer(server -> {
				for (int i = 0; i < 12; i++) {
					var profile = new com.mojang.authlib.GameProfile(new java.util.UUID(42, i + 1), "Picker" + i);
					var player = new net.minecraft.server.level.ServerPlayer(server, server.overworld(), profile,
							net.minecraft.server.level.ClientInformation.createDefault());
					var connection = new net.minecraft.network.Connection(
							net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
					new io.netty.channel.embedded.EmbeddedChannel(connection);
					server.getPlayerList().placeNewPlayer(connection, player,
							net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false));
				}
			});
			context.waitFor(client -> client.getConnection().getPlayerInfo("Picker11") != null, 100);
			context.runOnClient(client -> {
				var screen = (QuestScreen) client.gui.screen();
				var input = screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
						.findFirst().orElseThrow();
				input.setValue("Picker1");
				if (screen.matchingPlayers().size() != 3)
					throw new AssertionError("Online player autocomplete did not filter names");
			});
			context.takeScreenshot("questplus-team-online-picker");
			context.runOnClient(client -> {
				var screen = (QuestScreen) client.gui.screen();
				var input = screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
						.findFirst().orElseThrow();
				input.setValue("");
				var viewport = dev.questplus.clientmodel.TabletUiViewport.fit(client.getWindow().getGuiScaledWidth(),
						client.getWindow().getGuiScaledHeight(), QuestPlusClient.settings.scale);
				double x = viewport.offsetX() + 25 * viewport.scale(), y = viewport.offsetY() + 95 * viewport.scale();
				for (int i = 0; i < 6; i++)
					screen.mouseScrolled(x, y, 0, -1);
				screen.mouseClicked(
						new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
				if (!input.getValue().equals("Picker4"))
					throw new AssertionError("Scrolling and selecting an online player failed: " + input.getValue());
			});
			clickButton(context, "invite");
			world.getServer().waitFor(server -> {
				var team = dev.questplus.fabric.QuestPlusMod.runtime().teams().allTeams().stream()
						.filter(t -> t.name.equals("Client test team")).findFirst();
				return team.isPresent() && team.get().invitations.containsKey(new java.util.UUID(42, 5));
			}, 300);
			context.takeScreenshot("questplus-team");
			clickButton(context, "settings");
			context.waitTicks(3);
			context.takeScreenshot("questplus-settings");
			context.runOnClient(client -> client.gui.screen().onClose());
			context.waitTicks(12);
			context.runOnClient(client -> {
				if (client.gui.screen() != null)
					throw new AssertionError("Screen did not close");
			});

			if (QuestPlusClient.settings.tablet) {
				context.runOnClient(client -> {
					QuestPlusClient.settings.tabletTransitions = false;
					QuestClientTabletFlow.toggle(client);
					if (!(client.gui.screen() instanceof QuestScreen) || !QuestClientTabletFlow.nativeHandsActive())
						throw new AssertionError("Instant opening must show the native idle tablet");
					client.gui.screen().onClose();
					if (client.gui.screen() != null || QuestClientTabletFlow.nativeHandsActive())
						throw new AssertionError("Instant closing must release the native tablet");
					QuestPlusClient.settings.tabletTransitions = true;
				});
				int oldFov = context.computeOnClient(client -> client.options.fov().get());
				int oldScale = context.computeOnClient(client -> client.options.guiScale().get());
				double oldInterfaceScale = context.computeOnClient(client -> QuestPlusClient.settings.scale);
				int cycle = 0;
				for (int[] setup : new int[][] { { 1280, 800, 45, 2 }, { 1024, 768, 70, 3 }, { 1600, 900, 100, 1 },
						{ 1280, 720, 30, 2 }, { 1600, 900, 85, 4 } }) {
					cycle++;
					int currentCycle = cycle;
					context.runOnClient(client -> {
						QuestPlusClient.settings.scale = 1;
						boolean slim = currentCycle % 2 == 0;
						skin = java.util.stream.IntStream.range(0, 100)
								.mapToObj(i -> net.minecraft.client.resources.DefaultPlayerSkin
										.get(new java.util.UUID(0, i)))
								.filter(s -> (s
										.model() == net.minecraft.world.entity.player.PlayerModelType.SLIM) == slim)
								.findFirst().orElseThrow();
					});
					context.getInput().resizeWindow(setup[0], setup[1]);
					context.runOnClient(client -> {
						client.options.fov().set(setup[2]);
						client.options.guiScale().set(setup[3]);
					});
					context.getInput().pressKey(InputConstants.KEY_J);
					context.waitTicks(4);
					context.takeScreenshot("questplus-native-" + cycle + "-pocket");
					context.waitTicks(8);
					context.takeScreenshot("questplus-native-" + cycle + "-opening");
					context.runOnClient(client -> {
						var startPose = TabletFirstPersonAnimation.pose("questplus.open", .1);
						var endPose = TabletFirstPersonAnimation.pose("questplus.open", .9);
						if (startPose.rightRotation().equals(endPose.rightRotation())
								|| startPose.tabletPosition().equals(endPose.tabletPosition()))
							throw new AssertionError("Native Blockbench frames did not interpolate");
						if (!"questplus.open".equals(TabletNativeAnimation.clip()))
							throw new AssertionError("Unexpected native opening clip");
					});
					context.waitTicks(8);
					context.takeScreenshot("questplus-native-" + cycle + "-settling");
					context.waitFor(
							client -> client.gui.screen() instanceof QuestScreen && TabletSurfaceRenderer.active(),
							120);
					context.waitTicks(3);
					context.takeScreenshot("questplus-native-" + cycle + "-idle");
					context.runOnClient(client -> TabletArmRenderingTest.verify(client, true));
					if (cycle == 1) {
						context.waitTicks(18);
						context.takeScreenshot("questplus-native-idle-effects-later");
						context.runOnClient(client -> {
							boolean previous = QuestPlusClient.settings.reducedMotion;
							try {
								QuestPlusClient.settings.reducedMotion = true;
								if (TabletIdleEffects.frame().animated())
									throw new AssertionError("Reduced motion left tablet effects animated");
							} finally {
								QuestPlusClient.settings.reducedMotion = previous;
							}
						});
					}
					context.runOnClient(client -> {
						if (!TabletSurfaceRenderer.active())
							throw new AssertionError("Native tablet UI did not attach at cycle " + currentCycle);
						if (!(client.gui.screen() instanceof QuestScreen screen) || TabletSurfaceRenderer.WIDTH != 500
								|| TabletSurfaceRenderer.HEIGHT != 300)
							throw new AssertionError("Native UI surface dimensions changed");
						boolean projected = false;
						for (int y = client.getWindow().getGuiScaledHeight() / 10; y < client.getWindow()
								.getGuiScaledHeight(); y += client.getWindow().getGuiScaledHeight() / 10)
							for (int x = client.getWindow().getGuiScaledWidth() / 10; x < client.getWindow()
									.getGuiScaledWidth(); x += client.getWindow().getGuiScaledWidth() / 10)
								if (TabletFaceAnchor.pointer(x, y) != null)
									projected = true;
						if (!projected)
							throw new AssertionError(
									"No mouse coordinate projected onto the screen bone at cycle " + currentCycle);
					});
					clickFace(context, 45, 13);
					context.runOnClient(client -> {
						client.gui.screen().keyPressed(new KeyEvent(InputConstants.KEY_F6, 0, 0));
						client.gui.screen().keyPressed(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
					});
					context.waitTicks(2);
					context.takeScreenshot("questplus-native-" + cycle + "-quest-interaction");
					if (cycle == 1) {
						var p = context.computeOnClient(client -> TabletFaceAnchor.windowPoint(460, 220));
						context.getInput().setCursorPos(p.x(), p.y());
						context.getInput().scroll(-5);
						context.waitTicks(3);
						context.takeScreenshot("questplus-native-rewards");
						categoryScrollTrial(context);
						context.waitTicks(3);
						context.runOnClient(client -> {
							client.gui.screen().keyPressed(new KeyEvent(InputConstants.KEY_F6, 0, 0));
							client.gui.screen().keyPressed(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
						});
					}
					clickFace(context, 125, 13);
					context.runOnClient(client -> {
						if (!hasButtonLabel(client, (QuestScreen) client.gui.screen(), "invite"))
							throw new AssertionError("The projected Team tab click did not open Team");
					});
					if (cycle == 1) {
						clickFace(context, 200, 78);
						context.getInput().typeChars("00000000-0000-0000-0000-000000000001");
						context.runOnClient(client -> {
							var box = ((QuestScreen) client.gui.screen()).children().stream()
									.filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst()
									.orElseThrow();
							if (!box.getValue().equals("00000000-0000-0000-0000-000000000001"))
								throw new AssertionError("Projected text input lost keyboard focus");
						});
						context.takeScreenshot("questplus-native-centered-input");
					}
					context.takeScreenshot("questplus-native-" + cycle + "-team");
					clickFace(context, 205, 13);
					context.runOnClient(client -> {
						if (!hasButtonLabel(client, (QuestScreen) client.gui.screen(), "scale",
								Math.round(QuestPlusClient.settings.scale * 100)))
							throw new AssertionError("The projected Settings tab click did not open Settings");
					});
					context.takeScreenshot("questplus-native-" + cycle + "-settings");
					if (cycle == 1) {
						context.runOnClient(client -> QuestPlusClient.settings.blur = false);
						context.waitTicks(3);
						context.takeScreenshot("questplus-native-blur-off");
						context.runOnClient(client -> QuestPlusClient.settings.blur = true);
						context.waitTicks(3);
						context.takeScreenshot("questplus-native-blur-on");
					}
					clickButton(context, "scale", 100);
					context.runOnClient(client -> {
						if (QuestPlusClient.settings.scale != 1.25
								|| client.gui.screen().width != TabletSurfaceRenderer.logicalWidth())
							throw new AssertionError("Interface scale did not resize the logical canvas");
					});
					context.takeScreenshot("questplus-native-" + cycle + "-scaled");
					clickFace(context, 45, 13);
					context.runOnClient(client -> {
						if (!hasButtonLabel(client, (QuestScreen) client.gui.screen(), "close_details"))
							throw new AssertionError("The projected Quests tab click did not return to Quests");
					});
					context.takeScreenshot("questplus-native-" + cycle + "-scaled-details");
					clickFace(context, 300, 59);
					context.runOnClient(client -> {
						if (hasButtonLabel(client, (QuestScreen) client.gui.screen(), "close_details")
								|| !hasButtonLabel(client, (QuestScreen) client.gui.screen(), "filter.all"))
							throw new AssertionError(
									"The detail close button collided with diagram controls at 125% scale");
					});
					context.getInput().pressKey(InputConstants.KEY_J);
					context.waitTicks(12);
					context.takeScreenshot("questplus-native-" + cycle + "-closing");
					context.waitFor(client -> client.gui.screen() == null && !QuestClientTabletFlow.nativeHandsActive(),
							180);
					context.waitTicks(2);
					context.runOnClient(client -> {
						if (client.gui.screen() != null || QuestClientTabletFlow.nativeHandsActive())
							throw new AssertionError(
									"Tablet close did not restore the game view at cycle " + currentCycle);
					});
					context.takeScreenshot("questplus-native-" + cycle + "-closed");
					if (cycle == 1)
						for (int frame = 0; frame < 4; frame++) {
							context.waitTick();
							context.takeScreenshot("questplus-native-close-tail-" + frame);
						}
				}
				context.getInput().resizeWindow(1280, 800);
				skin = null;
				QuestPlusClient.settings.scale = oldInterfaceScale;
				context.getInput().pressKey(InputConstants.KEY_J);
				context.waitTicks(2);
				context.getInput().pressKey(InputConstants.KEY_J);
				context.waitFor(client -> !QuestClientTabletFlow.nativeHandsActive() && client.gui.screen() == null,
						180);
				context.takeScreenshot("questplus-native-interrupted-closed");
				context.runOnClient(client -> {
					client.options.fov().set(oldFov);
					client.options.guiScale().set(oldScale);
				});
				remotePoseTrial(context, world.getServer());
			}
			world.getServer().runCommand("questplus reload");
			context.waitFor(client -> QuestPlusClient.STATE.generation >= 2, 300);
			if (QuestPlusClient.settings.tablet) {
				context.getInput().pressKey(InputConstants.KEY_J);
				context.waitFor(client -> TabletSurfaceRenderer.active(), 120);
				context.waitTicks(3);
			}
		}
		context.runOnClient(client -> {
			if (QuestPlusClient.STATE.ready || !QuestPlusClient.STATE.quests.isEmpty())
				throw new AssertionError("Disconnected data retained");
			if (QuestClientTabletFlow.nativeHandsActive())
				throw new AssertionError("Disconnected tablet pose retained");
			if (TabletNativeAnimation.active())
				throw new AssertionError("Disconnected native tablet timeline retained");
		});
	}

	private static void rewardFixture(ClientGameTestContext context,
			net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
		final String id = "questplus:client_choice_fixture";
		String[] category = { null };
		int before = context.computeOnClient(client -> client.player.totalExperience);
		server.runOnServer(instance -> {
			try {
				var runtime = dev.questplus.fabric.QuestPlusMod.runtime();
				category[0] = runtime.categories().getFirst().id();
				var bytes = new java.io.ByteArrayOutputStream();
				var png = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
				for (int y = 0; y < 16; y++)
					for (int x = 0; x < 16; x++)
						png.setRGB(x, y, 0xffff00ff);
				javax.imageio.ImageIO.write(png, "png", bytes);
				String customIcon = "data:image/png;base64,"
						+ java.util.Base64.getEncoder().encodeToString(bytes.toByteArray());
				Files.writeString(
						instance.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
								.resolve("questplus/quests/client-choice.json5"),
						"""
								{schemaVersion:1,id:'questplus:client_choice_fixture',category:'%s',icon:'%s',title:{en_us:'Reward choices'},progression:'once',presentation:{shape:'hexagon',subtitle:{en_us:'Server validated'},iconScale:0.8},
								 objectives:[{id:'check',event:'manual',target:'questplus:client_choice_marker',count:1}],
								 rewards:[
								  {type:'questplus:choice',data:{options:[{type:'experience',amount:7},{type:'experience_levels',amount:2}]}},
								  {type:'questplus:choice',data:{options:[{type:'experience',amount:11},{type:'experience_levels',amount:3}]}},
								  {type:'questplus:random',data:{options:[{type:'experience',amount:1,weight:1}]}},
								  {type:'questplus:stage',data:{stage:'questplus:client_reward',remove:false}},
								  {type:'questplus:toast',data:{title:{en_us:'Custom reward'},description:{en_us:'Stage unlocked'},icon:'minecraft:diamond'}}
								 ]}
								"""
								.formatted(category[0], customIcon));
				runtime.reload();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		context.waitFor(client -> QuestPlusClient.STATE.quests.containsKey(id), 300);
		context.runOnClient(client -> {
			if (!QuestPlusClient.STATE.quests.get(id).icon().startsWith("data:image/png;base64,"))
				throw new AssertionError("Custom quest icon was not projected");
		});
		boolean nativeTablet = QuestPlusClient.settings.tablet;
		if (nativeTablet) {
			context.setScreen(() -> null);
			context.getInput().pressKey(InputConstants.KEY_J);
			context.waitFor(client -> TabletSurfaceRenderer.active(), 120);
			context.waitTicks(5);
		}
		context.setScreen(() -> {
			var screen = new QuestScreen();
			try {
				var selected = QuestScreen.class.getDeclaredField("selected");
				selected.setAccessible(true);
				selected.set(screen, id);
				var cat = QuestScreen.class.getDeclaredField("category");
				cat.setAccessible(true);
				cat.set(screen, category[0]);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			return screen;
		});
		context.waitTicks(5);
		clickButton(context, "interact");
		context.waitFor(client -> QuestPlusClient.STATE.progress(id).has("rewardState")
				&& !QuestClientState.flag(QuestPlusClient.STATE.progress(id), "saving"), 300);
		String token = context.computeOnClient(
				client -> QuestPlusClient.STATE.progress(id).getAsJsonObject("rewardState").get("token").getAsString());
		clickButton(context, "choose");
		context.waitTicks(2);
		context.takeScreenshot("questplus-reward-choice-options");
		clickButton(context, "xp", 7);
		clickButton(context, "confirm_choice");
		context.waitFor(client -> QuestPlusClient.STATE.progress(id).getAsJsonObject("rewardState")
				.getAsJsonArray("rewards").get(0).getAsJsonObject().get("type").getAsString().equals("experience")
				&& !QuestClientState.flag(QuestPlusClient.STATE.progress(id), "saving"), 300);
		clickButton(context, "xp", 11);
		clickButton(context, "confirm_choice");
		context.waitFor(client -> QuestClientState.flag(QuestPlusClient.STATE.progress(id), "claimable"), 300);
		context.waitTicks(3);
		context.takeScreenshot("questplus-reward-server-result");
		context.runOnClient(client -> QuestToast.clear());
		clickButton(context, "claim");
		context.waitFor(
				client -> "delivered".equals(
						QuestPlusClient.STATE.progress(id).getAsJsonObject("rewardState").get("status").getAsString()),
				300);
		context.waitFor(client -> client.player.totalExperience == before + 19, 300);
		context.waitFor(client -> {
			try {
				var active = QuestToast.class.getDeclaredField("active");
				active.setAccessible(true);
				return String.valueOf(active.get(null)).contains("Custom reward");
			} catch (Exception error) {
				throw new RuntimeException(error);
			}
		}, 300);
		context.waitTicks(8);
		context.takeScreenshot("questplus-reward-claimed");
		context.runOnClient(client -> QuestPlusClient.chooseReward(id, token, 0, 1));
		context.waitFor(client -> "action_rejected".equals(QuestPlusClient.STATE.error), 300);
		server.runOnServer(instance -> {
			if (!dev.questplus.fabric.QuestStages.stages(instance.getPlayerList().getPlayers().getFirst())
					.contains("questplus:client_reward"))
				throw new AssertionError("Stage reward was not delivered by server");
			if (instance.getPlayerList().getPlayers().getFirst().totalExperience != before + 19)
				throw new AssertionError("Replayed choice delivered duplicate XP");
			try {
				Files.delete(instance.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
						.resolve("questplus/quests/client-choice.json5"));
				dev.questplus.fabric.QuestPlusMod.runtime().reload();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		if (nativeTablet) {
			context.runOnClient(client -> client.gui.screen().onClose());
			context.waitFor(client -> client.gui.screen() == null && !QuestClientTabletFlow.nativeHandsActive(), 180);
		}
		context.setScreen(QuestScreen::new);
		context.runOnClient(client -> QuestPlusClient.sync());
		context.waitFor(client -> !QuestPlusClient.STATE.quests.containsKey(id), 300);
	}

	private static void diagramFixture(ClientGameTestContext context) {
		var originalCategories = new java.util.LinkedHashMap<String, com.google.gson.JsonObject>();
		var originalQuests = new java.util.LinkedHashMap<String, dev.questplus.core.QuestDefinition>();
		context.runOnClient(client -> {
			var state = QuestPlusClient.STATE;
			originalCategories.putAll(state.categories);
			originalQuests.putAll(state.quests);
			dev.questplus.clientmodel.QuestLayout.Edge edge = null;
			String group = null;
			for (String id : state.categories.keySet()) {
				var layout = dev.questplus.clientmodel.QuestLayout
						.compute(state.quests.values().stream().filter(q -> id.equals(q.category())).toList());
				if (!layout.edges().isEmpty()) {
					edge = layout.edges().getFirst();
					group = id;
					break;
				}
			}
			if (edge == null)
				throw new AssertionError("Missing quest dependency for diagram rendering test");
			var source = state.quests.get(edge.from());
			var target = state.quests.get(edge.to());
			var points = dev.questplus.clientmodel.QuestLayout.compute(java.util.List.of(source, target)).points();
			var from = points.get(source.id());
			var to = points.get(target.id());
			var category = state.categories.get(group).deepCopy();
			var background = new com.google.gson.JsonObject();
			background.addProperty("id", "test_background");
			background.addProperty("style", "background");
			background.addProperty("x", (from.x() + to.x()) / 2);
			background.addProperty("y", (from.y() + to.y()) / 2);
			background.addProperty("width", (int) Math.ceil(Math.abs(from.x() - to.x()) + 120));
			background.addProperty("height", (int) Math.ceil(Math.abs(from.y() - to.y()) + 120));
			background.addProperty("color", "#aa8060");
			background.add("text", new com.google.gson.JsonObject());
			var annotations = new com.google.gson.JsonArray();
			annotations.add(background);
			category.add("annotations", annotations);
			var waypoint = new com.google.gson.JsonObject();
			waypoint.addProperty("x", (from.x() + to.x()) / 2 + 30);
			waypoint.addProperty("y", (from.y() + to.y()) / 2);
			var route = new com.google.gson.JsonObject();
			route.addProperty("from", source.id());
			route.addProperty("to", target.id());
			var waypoints = new com.google.gson.JsonArray();
			waypoints.add(waypoint);
			route.add("points", waypoints);
			var routes = new com.google.gson.JsonArray();
			routes.add(route);
			category.add("linkRoutes", routes);
			state.categories.clear();
			state.categories.put(group, category);
			state.quests.clear();
			state.quests.put(source.id(), source);
			state.quests.put(target.id(), target);
			state.catalogueRevision++;
		});
		try {
			context.setScreen(QuestScreen::new);
			context.waitTicks(5);
			context.takeScreenshot("questplus-diagram-background-and-route");
		} finally {
			context.runOnClient(client -> {
				var state = QuestPlusClient.STATE;
				state.categories.clear();
				state.categories.putAll(originalCategories);
				state.quests.clear();
				state.quests.putAll(originalQuests);
				state.catalogueRevision++;
			});
			context.setScreen(QuestScreen::new);
			context.waitTicks(3);
		}
	}

	private static void categoryScrollTrial(ClientGameTestContext context) {
		var original = context
				.computeOnClient(client -> new java.util.LinkedHashMap<>(QuestPlusClient.STATE.categories));
		try {
			context.runOnClient(client -> {
				for (int i = 0; i < 20; i++) {
					var category = original.values().iterator().next().deepCopy();
					category.addProperty("id", "questplus:test_category_" + i);
					QuestPlusClient.STATE.categories.put(category.get("id").getAsString(), category);
				}
				QuestPlusClient.STATE.catalogueRevision++;
			});
			context.waitTicks(3);
			var point = context.computeOnClient(client -> TabletFaceAnchor.windowPoint(17, 170));
			context.getInput().setCursorPos(point.x(), point.y());
			for (int i = 0; i < 7; i++) {
				context.getInput().scroll(-1);
				context.waitTick();
			}
			context.runOnClient(client -> {
				try {
					var field = QuestScreen.class.getDeclaredField("categoryScroll");
					field.setAccessible(true);
					if (field.getInt(client.gui.screen()) != 7)
						throw new AssertionError("Category rail did not scroll through projected mouse input: scroll="
								+ field.getInt(client.gui.screen()) + ", target=" + point + ", pointer="
								+ TabletFaceAnchor.pointer(client.mouseHandler.getScaledXPos(client.getWindow()),
										client.mouseHandler.getScaledYPos(client.getWindow())));
				} catch (ReflectiveOperationException failure) {
					throw new AssertionError(failure);
				}
			});
			context.takeScreenshot("questplus-native-categories-scrolled");
			clickFace(context, 17, 61);
			context.runOnClient(client -> {
				try {
					var field = QuestScreen.class.getDeclaredField("category");
					field.setAccessible(true);
					var expected = new java.util.ArrayList<>(QuestPlusClient.STATE.categories.keySet()).get(7);
					if (!expected.equals(field.get(client.gui.screen())))
						throw new AssertionError("Scrolled category hitbox does not match its visible icon");
				} catch (ReflectiveOperationException failure) {
					throw new AssertionError(failure);
				}
			});
		} finally {
			context.runOnClient(client -> {
				QuestPlusClient.STATE.categories.clear();
				QuestPlusClient.STATE.categories.putAll(original);
				QuestPlusClient.STATE.catalogueRevision++;
			});
		}
	}

	/**
	 * Real rendering and pose packets, using two client-side remote avatars rather
	 * than two connections.
	 */
	static void remotePoseTrial(ClientGameTestContext context,
			net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
		var holderId = new java.util.UUID(0, 101);
		var witnessId = new java.util.UUID(0, 102);
		context.runOnClient(client -> {
			client.options.fov().set(70);
			client.options.guiScale().set(2);
			skin = net.minecraft.client.resources.DefaultPlayerSkin.get(new java.util.UUID(0, 0));
			int id = 9000;
			for (var profile : java.util.List.of(new com.mojang.authlib.GameProfile(holderId, "Tablet holder"),
					new com.mojang.authlib.GameProfile(witnessId, "Witness"))) {
				var player = new net.minecraft.client.player.RemotePlayer(client.level, profile);
				player.setId(id++);
				player.setPos(client.player.getX() + (profile.id().equals(holderId) ? -.85 : .85), client.player.getY(),
						client.player.getZ() + 3.2);
				player.setYRot(180);
				player.setXRot(0);
				player.yHeadRot = 180;
				player.yBodyRot = 180;
				player.setOldPosAndRot();
				if (profile.id().equals(holderId))
					player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
							new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM
									.getValue(net.minecraft.resources.Identifier.parse("questplus:tablet"))));
				client.level.addEntity(player);
			}
		});
		context.getInput().lookAt(0, 0);
		context.waitTicks(5);
		assertRemoteCarry(context);
		context.takeScreenshot("questplus-remote-carried-front");
		var carryView = context.computeOnClient(client -> client.player.position());
		context.runOnClient(client -> {
			client.player.setPos(carryView.add(-3, 0, 3.2));
			client.player.setOldPosAndRot();
		});
		context.getInput().lookAt(-90, 0);
		context.waitTicks(3);
		context.takeScreenshot("questplus-remote-carried-side");
		context.runOnClient(client -> {
			client.player.setPos(carryView);
			client.player.setOldPosAndRot();
		});
		context.getInput().lookAt(0, 0);
		sendPose(server, holderId, true);
		context.waitTicks(12);
		context.takeScreenshot("questplus-remote-opening");
		context.waitTicks(18);
		context.takeScreenshot("questplus-remote-idle");
		assertRemoteArms(context);
		context.runOnClient(client -> {
			var witness = client.level.getEntity(9001);
			witness.setPos(carryView.add(8, 0, 3.2));
			witness.setOldPosAndRot();
			client.player.setPos(carryView.add(-.85, 0, 1.3));
			client.player.setOldPosAndRot();
		});
		context.waitTicks(3);
		context.takeScreenshot("questplus-remote-grip-front");
		context.runOnClient(client -> {
			client.player.setPos(carryView.add(1.15, 0, 3.2));
			client.player.setOldPosAndRot();
		});
		context.getInput().lookAt(90, 0);
		context.waitTicks(3);
		context.takeScreenshot("questplus-remote-grip-side");
		var side = context.computeOnClient(client -> client.player.position().add(3, 0, 3.2));
		context.runOnClient(client -> {
			client.player.setPos(side);
			client.player.setOldPosAndRot();
		});
		context.getInput().lookAt(90, 0);
		context.waitTicks(3);
		context.takeScreenshot("questplus-remote-side");
		context.runOnClient(client -> {
			client.player.setPos(side.add(-3, 0, -3.2));
			client.player.setOldPosAndRot();
		});
		context.getInput().lookAt(0, 0);
		context.getInput().pressKey(InputConstants.KEY_J);
		context.waitFor(client -> TabletSurfaceRenderer.active(), 120);
		context.waitTicks(3);
		context.takeScreenshot("questplus-local-and-remote-holding");
		context.getInput().pressKey(InputConstants.KEY_J);
		context.waitFor(client -> !QuestClientTabletFlow.nativeHandsActive(), 180);
		assertRemoteArms(context);
		sendPose(server, holderId, false);
		context.waitTicks(12);
		context.takeScreenshot("questplus-remote-closing");
		context.waitTicks(18);
		context.takeScreenshot("questplus-remote-closed");
		assertRemoteCarry(context);
		context.runOnClient(client -> ((net.minecraft.client.player.RemotePlayer) client.level.getEntity(9000))
				.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY));
		context.waitTicks(8);
		context.takeScreenshot("questplus-remote-model-restored");
		context.runOnClient(client -> {
			client.level.removeEntity(9000, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
			client.level.removeEntity(9001, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
			skin = null;
		});
	}

	private static void assertRemoteCarry(ClientGameTestContext context) {
		context.runOnClient(client -> {
			var holder = (net.minecraft.client.player.RemotePlayer) client.level.getEntity(9000);
			@SuppressWarnings("unchecked")
			var renderer = (net.minecraft.client.renderer.entity.player.AvatarRenderer<net.minecraft.client.player.RemotePlayer>) (Object) client
					.getEntityRenderDispatcher().getRenderer(holder);
			var state = renderer.createRenderState();
			renderer.extractRenderState(holder, state, 1);
			var model = renderer.getModel();
			model.setupAnim(state);
			if (!state.getMainHandItemState().isEmpty() || Math
					.abs(model.rightArm.xRot - Math.toRadians(TabletThirdPersonAnimation.carry().right().x())) > .001)
				throw new AssertionError("Closed tablet retained the generic item pose");
		});
	}

	private static void assertRemoteArms(ClientGameTestContext context) {
		context.runOnClient(client -> {
			var holder = (net.minecraft.client.player.RemotePlayer) client.level.getEntity(9000);
			var witness = (net.minecraft.client.player.RemotePlayer) client.level.getEntity(9001);
			@SuppressWarnings("unchecked")
			var renderer = (net.minecraft.client.renderer.entity.player.AvatarRenderer<net.minecraft.client.player.RemotePlayer>) (Object) client
					.getEntityRenderDispatcher().getRenderer(holder);
			var state = renderer.createRenderState();
			renderer.extractRenderState(holder, state, 1);
			var model = renderer.getModel();
			model.setupAnim(state);
			if (!state.getMainHandItemState().isEmpty())
				throw new AssertionError("The item duplicated the animated remote tablet");
			if (Math.abs(model.rightArm.xRot - Math.toRadians(-53)) > .05)
				throw new AssertionError("Remote holder does not match the exported Blockbench arm");
			renderer.extractRenderState(witness, state, 1);
			model.setupAnim(state);
			if (Math.abs(model.rightArm.xRot) > .2 || Math.abs(model.leftArm.xRot) > .2 || model.rightSleeve.xRot != 0
					|| model.leftSleeve.xRot != 0)
				throw new AssertionError("Tablet pose leaked into the witness or sleeve child transforms");
		});
	}

	private static void sendPose(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server,
			java.util.UUID id, boolean open) {
		server.runOnServer(instance -> instance.getPlayerList().getPlayers()
				.forEach(player -> net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
						new dev.questplus.fabric.TabletPoseNetwork.State(id, open))));
	}

	static void clickButton(ClientGameTestContext context, String key, Object... args) {
		context.waitFor(client -> client.gui.screen() instanceof QuestScreen screen && screen.children().stream()
				.filter(QuestButton.class::isInstance).map(QuestButton.class::cast).anyMatch(b -> b.active && b.visible
						&& b.getMessage().getString().equals(QuestPlusClient.tr(key, args).getString())),
				120);
		var point = context.computeOnClient(client -> {
			var screen = (QuestScreen) client.gui.screen();
			var button = screen.children().stream().filter(QuestButton.class::isInstance).map(QuestButton.class::cast)
					.filter(b -> b.getMessage().getString().equals(QuestPlusClient.tr(key, args).getString()))
					.findFirst().orElseThrow();
			if (TabletSurfaceRenderer.active())
				return TabletFaceAnchor.windowPoint(
						(button.getX() + button.getWidth() / 2.0) * TabletSurfaceRenderer.uiScale(),
						(button.getY() + button.getHeight() / 2.0) * TabletSurfaceRenderer.uiScale());
			var viewport = dev.questplus.clientmodel.TabletUiViewport.fit(client.getWindow().getGuiScaledWidth(),
					client.getWindow().getGuiScaledHeight(), QuestPlusClient.settings.scale);
			return new TabletFaceAnchor.Point(
					viewport.screenX(button.getX() + button.getWidth() / 2.0) * client.getWindow().getWidth()
							/ client.getWindow().getGuiScaledWidth(),
					viewport.screenY(button.getY() + button.getHeight() / 2.0) * client.getWindow().getHeight()
							/ client.getWindow().getGuiScaledHeight());
		});
		clickWindow(context, point);
	}

	private static void clickFace(ClientGameTestContext context, double logicalX, double logicalY) {
		var point = context.computeOnClient(client -> TabletFaceAnchor
				.windowPoint(logicalX * TabletSurfaceRenderer.uiScale(), logicalY * TabletSurfaceRenderer.uiScale()));
		if (point == null)
			throw new AssertionError("Cannot project tablet UI coordinate to the Minecraft window");
		clickWindow(context, point);
	}

	private static void clickWindow(ClientGameTestContext context, TabletFaceAnchor.Point point) {
		context.getInput().setCursorPos(point.x(), point.y());
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
	}

	private static boolean hasButtonLabel(net.minecraft.client.Minecraft client, QuestScreen screen, String key,
			Object... args) {
		String expected = QuestPlusClient.tr(key, args).getString();
		return screen.children().stream().filter(QuestButton.class::isInstance).map(QuestButton.class::cast)
				.anyMatch(button -> button.getMessage().getString().startsWith(expected));
	}
}
