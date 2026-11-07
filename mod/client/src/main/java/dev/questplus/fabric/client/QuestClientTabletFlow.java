package dev.questplus.fabric.client;

import dev.questplus.fabric.QuestPlusMod;
import dev.questplus.fabric.QuestTabletItem;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.world.InteractionResult;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;

/** Opens the surface UI only after PAL reaches the final two-handed pose. */
final class QuestClientTabletFlow {
	private enum Stage {
		CLOSED, CARRIED, STRIKING, OPENING, HELD, CLOSING
	}

	private static Stage stage = Stage.CLOSED;
	private static boolean nativeHands;
	private static boolean closeRequested;
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
		ClientPreAttackCallback.EVENT.register((client, player, clicks) -> {
			if (clicks > 0 && stage == Stage.CARRIED && shouldCarry(client)) {
				try {
					if (FirstPersonPalBridge.play("questplus.carry_attack")) {
						stage = Stage.STRIKING;
						strikingSince = System.nanoTime();
					}
				} catch (LinkageError | RuntimeException error) {
					QuestPlusMod.LOGGER.warn("Cannot play tablet attack animation", error);
				}
			}
			return false;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> useTablet(
				player.getItemInHand(hand).getItem() instanceof QuestTabletItem, level.isClientSide()));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> useTablet(
				player.getItemInHand(hand).getItem() instanceof QuestTabletItem, level.isClientSide()));
		ClientTickEvents.END_CLIENT_TICK.register(QuestClientTabletFlow::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			stop();
			reset();
		});
		// FP PAL draws the Blockbench tablet and arms; avoid rendering a second HUD model.
	}

	private static InteractionResult useTablet(boolean tablet, boolean clientSide) {
		if (!tablet)
			return InteractionResult.PASS;
		if (clientSide) {
			var client = Minecraft.getInstance();
			if (client.gui.screen() == null
					&& (stage == Stage.CLOSED || stage == Stage.CARRIED || stage == Stage.STRIKING))
				open(client);
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
				&& (client.gui.screen() == null
						|| client.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen)
				&& client.options.getCameraType() == CameraType.FIRST_PERSON
				&& client.player.getMainHandItem().getItem() instanceof QuestTabletItem && animationAvailable();
	}

	private static void carry(Minecraft client) {
		if (!shouldCarry(client))
			return;
		nativeHands = true;
		stage = Stage.CARRIED;
		try {
			if (!FirstPersonPalBridge.play("questplus.carry")) {
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
		if (client.gui.screen() instanceof QuestScreen quest) {
			quest.onClose();
			return;
		}
		if (stage == Stage.OPENING) {
			close();
			return;
		}
		if (stage == Stage.CLOSING || client.gui.screen() != null)
			return;
		open(client);
	}

	private static boolean animationAvailable() {
		var s = QuestPlusClient.settings;
		return s != null && s.tablet && s.animations && !s.reducedMotion
				&& FabricLoader.getInstance().isModLoaded("fp_pal")
				&& FabricLoader.getInstance().isModLoaded("player_animation_library");
	}

	private static void open(Minecraft client) {
		// Switch to first person before opening; FP PAL cannot draw the tablet in third person.
		if (client.options.getCameraType() != CameraType.FIRST_PERSON) {
			previousCamera = client.options.getCameraType();
			client.options.setCameraType(CameraType.FIRST_PERSON);
		}
		if (animationAvailable()) {
			boolean carried = shouldCarry(client);
			openingClip = carried ? "questplus.carry_open" : "questplus.open";
			closingClip = carried ? "questplus.carry_close" : "questplus.close";
			// Claim first-person hands BEFORE starting FP PAL. PunchyApi.disable
			// is evaluated by Punchy's renderer during the same frame and
			// must already see this state (including the opening animation).
			nativeHands = true;
			if (!QuestPlusClient.settings.tabletTransitions) {
				try {
					if (FirstPersonPalBridge.play("questplus.idle")) {
						stage = Stage.HELD;
						client.gui.setScreen(new QuestScreen());
						return;
					}
				} catch (LinkageError | RuntimeException error) {
					QuestPlusMod.LOGGER.warn("Cannot start FP PAL tablet idle", error);
				}
				stop();
				reset();
				client.gui.setScreen(new QuestScreen());
				return;
			}
			stage = Stage.OPENING;
			openingSince = System.nanoTime();
			try {
				if (FirstPersonPalBridge.play(openingClip)) {
					QuestPlusMod.LOGGER.info("Playing Blockbench first-person {}", openingClip);
					return;
				}
				QuestPlusMod.LOGGER.warn("FP PAL could not play {}. Loaded asset: {}", openingClip,
						FirstPersonPalBridge.loaded(openingClip));
			} catch (LinkageError | RuntimeException error) {
				QuestPlusMod.LOGGER.warn("Cannot start FP PAL animation; standard UI fallback", error);
			}
			nativeHands = false;
			stage = Stage.CLOSED;
		}
		nativeHands = false;
		stage = Stage.CLOSED;
		// No PAL animation is running, so do not leave the camera overridden.
		if (previousCamera != null) {
			client.options.setCameraType(previousCamera);
			previousCamera = null;
		}
		if (QuestPlusClient.settings.animations && QuestPlusClient.settings.tablet
				&& !QuestPlusClient.settings.reducedMotion) {
			QuestPlusMod.LOGGER.warn(
					"Quest Plus has fallen back to legacy GUI: fp_pal={}, player_animation_library={}.",
					FabricLoader.getInstance().isModLoaded("fp_pal"),
					FabricLoader.getInstance().isModLoaded("player_animation_library"));
		}
		client.gui.setScreen(new QuestScreen());
	}

	static void close() {
		if (!nativeHands || stage == Stage.CLOSED)
			return;
		if (!QuestPlusClient.settings.tabletTransitions) {
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
			if (!FirstPersonPalBridge.play(closingClip)) {
				stop();
				reset();
				return;
			}
		} catch (LinkageError | RuntimeException error) {
			QuestPlusMod.LOGGER.warn("Cannot play FP PAL close animation", error);
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
					|| client.gui.screen() instanceof QuestScreen;
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
					if (FirstPersonPalBridge.finished("questplus.carry_attack")
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
		// Recover even if a shader/FP PAL fails to supply any final frame.
		if (stage == Stage.OPENING && System.nanoTime() - openingSince > OPEN_WATCHDOG_NANOS) {
			QuestPlusMod.LOGGER.warn("Tablet opening animation timed out; recovering the normal GUI");
			stop();
			reset();
			if (client.player != null && client.gui.screen() == null)
				client.gui.setScreen(new QuestScreen());
			return;
		}
		// If a different screen (inventory, pause, etc.) appeared during
		// closing, release the FP pose immediately instead of drawing over it.
		if (stage == Stage.CLOSING && client.gui.screen() != null) {
			stop();
			reset();
			return;
		}
		// Shaders can interrupt FP PAL's last frame or its callback. Never
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
			if (stage == Stage.OPENING && FirstPersonPalBridge.finished(openingClip)) {
				if (client.gui.screen() != null) {
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
					if (!FirstPersonPalBridge.play("questplus.idle")) {
						stop();
						reset();
						return;
					}
				} catch (LinkageError | RuntimeException error) {
					QuestPlusMod.LOGGER.warn("Cannot start FP PAL tablet idle", error);
					stop();
					reset();
					return;
				}
				stage = Stage.HELD;
				client.gui.setScreen(new QuestScreen());
			} else if (stage == Stage.CLOSING && FirstPersonPalBridge.finished(closingClip)) {
				if (previousCamera == null && shouldCarry(client)) {
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
				// Another mod/screen replaced our GUI; clear the lingering pose.
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
				FirstPersonPalBridge.stop();
			} catch (LinkageError | RuntimeException error) {
				QuestPlusMod.LOGGER.warn("Cannot stop FP PAL animation", error);
			}
	}

	private static void reset() {
		QuestRecipeViewer.clear();
		stage = Stage.CLOSED;
		nativeHands = false;
		closeRequested = false;
		openingSince = closingSince = 0;
		TabletFaceAnchor.reset();
		DAMAGE.reset();
		if (previousCamera != null) {
			Minecraft.getInstance().options.setCameraType(previousCamera);
			previousCamera = null;
		}
	}

}
