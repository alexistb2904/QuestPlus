package dev.questplus.fabric.client;

import dev.questplus.platform.Platform;
import dev.questplus.platform.ClientPlatform;
import dev.questplus.compat.ClientAdapter;
import dev.questplus.fabric.QuestPlusMod;
import dev.questplus.fabric.QuestTabletItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;

/** Opens the interactive surface after the native two-handed Blockbench animation. */
final class QuestClientTabletFlow {
	private enum Stage {
		CLOSED, CARRIED, STRIKING, OPENING, HELD, CLOSING
	}

	private static Stage stage = Stage.CLOSED;
	private static boolean nativeHands;
	private static boolean closeRequested, reopenRequested;
	private static long openingSince, closingSince;
	private static long strikingSince;
	private static String openingClip = "questplus.open", closingClip = "questplus.close";
	private static final long OPEN_WATCHDOG_NANOS = 3_500_000_000L;
	private static final long CLOSE_WATCHDOG_NANOS = 2_500_000_000L;
	private static CameraType previousCamera;
	private static final TabletDamageGuard DAMAGE = new TabletDamageGuard();

	private QuestClientTabletFlow() {
	}

	static void register() {
		ClientPlatform.onAttack(client -> {
			if (stage == Stage.CARRIED && shouldCarry(client)) {
				try {
					if (TabletNativeAnimation.play("questplus.carry_attack")) {
						stage = Stage.STRIKING;
						strikingSince = System.nanoTime();
					}
				} catch (LinkageError | RuntimeException error) {
					QuestPlusMod.LOGGER.warn("Cannot play tablet attack animation", error);
				}
			}
			return false;
		});
		Platform.onUseItem(QuestClientTabletFlow::useTablet);
		Platform.onUseBlock(QuestClientTabletFlow::useTablet);
		Platform.onUseEntity(QuestClientTabletFlow::useTablet);
		ClientPlatform.onClientTick(QuestClientTabletFlow::tick);
		ClientPlatform.onDisconnect(client -> {
			stop();
			reset();
		});
	}

	private static InteractionResult useTablet(Player player, Level level, InteractionHand hand) {
		return useTablet(player.getItemInHand(hand).getItem() instanceof QuestTabletItem, level.isClientSide());
	}

	private static InteractionResult useTablet(boolean tablet, boolean clientSide) {
		if (!tablet)
			return InteractionResult.PASS;
		if (clientSide) {
			var client = Minecraft.getInstance();
			if (ClientAdapter.INSTANCE.screen(client) == null) {
				if (stage == Stage.CLOSING)
					reopenRequested = true;
				else if (stage == Stage.CLOSED || stage == Stage.CARRIED || stage == Stage.STRIKING)
					open(client);
			}
		}
		return InteractionResult.SUCCESS;
	}

	static boolean carrying() {
		return stage == Stage.CARRIED;
	}

	static boolean striking() {
		return stage == Stage.STRIKING;
	}

	private static boolean shouldCarry(Minecraft client) {
		return client.player != null && client.player.isAlive() && !client.player.isSpectator()
				&& (ClientAdapter.INSTANCE.screen(client) == null
						|| ClientAdapter.INSTANCE.screen(client) instanceof net.minecraft.client.gui.screens.ChatScreen)
				&& client.options.getCameraType() == CameraType.FIRST_PERSON
				&& client.player.getMainHandItem().getItem() instanceof QuestTabletItem && tabletAvailable();
	}

	private static void carry(Minecraft client) {
		if (!shouldCarry(client))
			return;
		nativeHands = true;
		stage = Stage.CARRIED;
		try {
			if (!TabletNativeAnimation.play("questplus.carry")) {
				stop();
				reset();
			}
		} catch (LinkageError | RuntimeException error) {
			QuestPlusMod.LOGGER.warn("Cannot play tablet carry pose", error);
			stop();
			reset();
		}
	}

	static boolean nativeHandsActive() {
		return nativeHands && stage != Stage.CLOSED;
	}

	static boolean closing() {
		return stage == Stage.CLOSING;
	}

	static boolean thirdPersonHolding() {
		return nativeHands && (stage == Stage.OPENING || stage == Stage.HELD);
	}

	static void toggle(Minecraft client) {
		if (ClientAdapter.INSTANCE.screen(client) instanceof QuestScreen quest) {
			quest.onClose();
			return;
		}
		if (stage == Stage.OPENING) {
			close();
			return;
		}
		if (ClientAdapter.INSTANCE.screen(client) != null)
			return;
		if (stage == Stage.CLOSING) {
			reopenRequested = true;
			return;
		}
		open(client);
	}

	private static boolean tabletAvailable() {
		var s = QuestPlusClient.settings;
		return s != null && s.tablet && TabletNativeGeometry.hasScreen();
	}

	private static void open(Minecraft client) {
		// Switch to first person before opening. Native cannot draw the tablet in third
		// person.
		if (tabletAvailable() && client.options.getCameraType() != CameraType.FIRST_PERSON) {
			previousCamera = client.options.getCameraType();
			client.options.setCameraType(CameraType.FIRST_PERSON);
		}
		if (tabletAvailable()) {
			boolean carried = shouldCarry(client);
			openingClip = carried ? "questplus.carry_open" : "questplus.open";
			closingClip = carried ? "questplus.carry_close" : "questplus.close";
			// Claim first-person hands BEFORE starting native. PunchyApi.disable
			// is evaluated by Punchy's renderer during the same frame and
			// must already see this state (including the opening animation).
			nativeHands = true;
			if (!QuestPlusClient.settings.animatedTabletTransitions()) {
				try {
					if (TabletNativeAnimation.play("questplus.idle")) {
						stage = Stage.HELD;
						ClientAdapter.INSTANCE.setScreen(client, new QuestScreen());
						return;
					}
				} catch (LinkageError | RuntimeException error) {
					QuestPlusMod.LOGGER.warn("Cannot start native tablet idle", error);
				}
				stop();
				reset();
				ClientAdapter.INSTANCE.setScreen(client, new QuestScreen());
				return;
			}
			stage = Stage.OPENING;
			openingSince = System.nanoTime();
			try {
				if (TabletNativeAnimation.play(openingClip)) {
					QuestPlusMod.LOGGER.info("Playing Blockbench first-person {}", openingClip);
					return;
				}
				QuestPlusMod.LOGGER.warn("native could not play {}. Loaded asset: {}", openingClip,
						TabletNativeAnimation.loaded(openingClip));
			} catch (LinkageError | RuntimeException error) {
				QuestPlusMod.LOGGER.warn("Cannot start native animation; standard UI fallback", error);
			}
			nativeHands = false;
			stage = Stage.CLOSED;
		}
		nativeHands = false;
		stage = Stage.CLOSED;
		// No native animation is running, so do not leave the camera overridden.
		if (previousCamera != null) {
			client.options.setCameraType(previousCamera);
			previousCamera = null;
		}
		QuestPlusMod.LOGGER.warn("Quest Plus native tablet unavailable; using flat GUI fallback");
		ClientAdapter.INSTANCE.setScreen(client, new QuestScreen());
	}

	static void close() {
		if (!nativeHands || stage == Stage.CLOSED)
			return;
		if (!QuestPlusClient.settings.animatedTabletTransitions()) {
			stop();
			reset();
			return;
		}
		if (stage == Stage.OPENING) {
			closeRequested = true;
			return;
		}
		if (stage == Stage.CLOSING)
			return;
		stage = Stage.CLOSING;
		closingSince = System.nanoTime();
		try {
			if (!TabletNativeAnimation.play(closingClip)) {
				stop();
				reset();
				return;
			}
		} catch (LinkageError | RuntimeException error) {
			QuestPlusMod.LOGGER.warn("Cannot play native close animation", error);
			stop();
			reset();
			return;
		}
	}

	private static void tick(Minecraft client) {
		if (client.player == null)
			DAMAGE.reset();
		else {
			boolean tabletActive = stage == Stage.OPENING || stage == Stage.HELD
					|| ClientAdapter.INSTANCE.screen(client) instanceof QuestScreen;
			boolean allow = QuestPlusClient.settings != null && QuestPlusClient.settings.stayOpenOnDamage;
			if (DAMAGE.observe(tabletActive, allow, client.player.getHealth(), client.player.getAbsorptionAmount())) {
				// A hit during the opening animation interrupts that pose
				// and immediately plays the closing animation instead.
				var quest = QuestRecipeViewer.tabletScreen();
				if (quest != null)
					quest.onClose();
				else if (stage == Stage.OPENING) {
					stage = Stage.HELD;
					close();
				} else if (stage == Stage.HELD)
					close();
			}
		}
		if (stage == Stage.CARRIED || stage == Stage.STRIKING) {
			if (!shouldCarry(client)) {
				stop();
				reset();
			} else if (stage == Stage.STRIKING)
				try {
					if (TabletNativeAnimation.finished("questplus.carry_attack")
							|| System.nanoTime() - strikingSince > 1_000_000_000L)
						carry(client);
				} catch (LinkageError | RuntimeException error) {
					QuestPlusMod.LOGGER.warn("Tablet attack animation was interrupted", error);
					stop();
					reset();
				}
			return;
		}
		if (stage == Stage.CLOSED) {
			carry(client);
			return;
		}
		// Recover even if a shader/native fails to supply any final frame.
		if (stage == Stage.OPENING && System.nanoTime() - openingSince > OPEN_WATCHDOG_NANOS) {
			QuestPlusMod.LOGGER.warn("Tablet opening animation timed out; recovering the normal GUI");
			stop();
			reset();
			if (client.player != null && ClientAdapter.INSTANCE.screen(client) == null)
				ClientAdapter.INSTANCE.setScreen(client, new QuestScreen());
			return;
		}
		// If a different screen (inventory, pause, etc.) appeared during
		// closing, release the FP pose immediately instead of drawing over it.
		if (stage == Stage.CLOSING && ClientAdapter.INSTANCE.screen(client) != null) {
			stop();
			reset();
			return;
		}
		// Shaders can interrupt native's last frame or its callback. Never
		// retain the CLOSING pose indefinitely: it previously hid all HUD
		// and inventory elements until the player reconnected.
		if (stage == Stage.CLOSING && System.nanoTime() - closingSince > CLOSE_WATCHDOG_NANOS) {
			QuestPlusMod.LOGGER.warn("Tablet close animation timed out; restoring vanilla HUD and first-person state");
			stop();
			reset();
			return;
		}
		if (client.player == null || !client.player.isAlive() || client.player.isSpectator()) {
			stop();
			reset();
			return;
		}
		try {
			if (stage == Stage.OPENING && TabletNativeAnimation.finished(openingClip)) {
				if (ClientAdapter.INSTANCE.screen(client) != null) {
					stop();
					reset();
					return;
				}
				if (closeRequested) {
					stage = Stage.HELD;
					close();
					return;
				}
				try {
					if (!TabletNativeAnimation.play("questplus.idle")) {
						stop();
						reset();
						return;
					}
				} catch (LinkageError | RuntimeException error) {
					QuestPlusMod.LOGGER.warn("Cannot start native tablet idle", error);
					stop();
					reset();
					return;
				}
				stage = Stage.HELD;
				ClientAdapter.INSTANCE.setScreen(client, new QuestScreen());
			} else if (stage == Stage.CLOSING && TabletNativeAnimation.finished(closingClip)) {
				if (reopenRequested) {
					reset();
					open(client);
				} else if (previousCamera == null && shouldCarry(client)) {
					closeRequested = false;
					QuestRecipeViewer.clear();
					TabletFaceAnchor.reset();
					DAMAGE.reset();
					carry(client);
				} else {
					stop();
					reset();
				}
			} else if (stage == Stage.HELD && QuestRecipeViewer.tabletScreen() == null) {
				// Another mod/screen replaced our GUI. Clear the lingering pose.
				stop();
				reset();
			}
		} catch (LinkageError | RuntimeException error) {
			QuestPlusMod.LOGGER.warn("Tablet animation was interrupted; restoring vanilla GUI state", error);
			stop();
			reset();
		}
	}

	private static void stop() {
		if (nativeHands)
			try {
				TabletNativeAnimation.stop();
			} catch (LinkageError | RuntimeException error) {
				QuestPlusMod.LOGGER.warn("Cannot stop native animation", error);
			}
	}

	private static void reset() {
		QuestRecipeViewer.clear();
		stage = Stage.CLOSED;
		nativeHands = false;
		closeRequested = false;
		reopenRequested = false;
		openingSince = closingSince = 0;
		TabletFaceAnchor.reset();
		DAMAGE.reset();
		if (previousCamera != null) {
			Minecraft.getInstance().options.setCameraType(previousCamera);
			previousCamera = null;
		}
	}

}
