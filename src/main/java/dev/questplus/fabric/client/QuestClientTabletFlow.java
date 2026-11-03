package dev.questplus.fabric.client;

import dev.questplus.fabric.QuestPlusMod;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

/** Opens the surface UI only after PAL reaches the final two-handed pose. */
final class QuestClientTabletFlow {
    private enum Stage { CLOSED, OPENING, HELD, CLOSING }
    private static Stage stage=Stage.CLOSED;
    private static boolean nativeHands;
    private static boolean closeRequested;
    private QuestClientTabletFlow() {}

    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(QuestClientTabletFlow::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client) -> { stop();reset(); });
        // FP PAL itself renders the Blockbench model from fp_models/ and textures/fp/.
        // No separate HUD tablet should be drawn here.
    }
    static boolean nativeHandsActive() { return nativeHands && stage!=Stage.CLOSED; }
    static boolean thirdPersonHolding() { return nativeHands && (stage==Stage.OPENING || stage==Stage.HELD); }

    static void toggle(Minecraft client) {
        if (client.gui.screen() instanceof QuestScreen quest) { quest.onClose(); return; }
        if (stage==Stage.OPENING) { close(); return; }
        if (stage==Stage.CLOSING || client.gui.screen()!=null) return;
        open(client);
    }

    private static boolean animationAvailable() {
        var s=QuestPlusClient.settings;
        return s!=null && s.tablet && s.animations && !s.reducedMotion
            && FabricLoader.getInstance().isModLoaded("fp_pal")
            && FabricLoader.getInstance().isModLoaded("player_animation_library");
    }
    private static void open(Minecraft client) {
        if (animationAvailable()) {
            try {
                if (FirstPersonPalBridge.play("questplus.open")) {
                    nativeHands=true;stage=Stage.OPENING;
                    QuestPlusMod.LOGGER.info("Playing Blockbench first-person questplus.open (1.2 s)");
                    return;
                }
                QuestPlusMod.LOGGER.warn("FP PAL could not play questplus:questplus.open. Loaded asset: {}",
                    FirstPersonPalBridge.loaded("questplus.open"));
            } catch (LinkageError | RuntimeException error) {
                QuestPlusMod.LOGGER.warn("Cannot start FP PAL animation; standard UI fallback",error);
            }
        }
        nativeHands=false;stage=Stage.CLOSED;
        if (QuestPlusClient.settings.animations && QuestPlusClient.settings.tablet
            && !QuestPlusClient.settings.reducedMotion) {
            QuestPlusMod.LOGGER.warn("Quest Plus has fallen back to legacy GUI: fp_pal={}, player_animation_library={}.",
                FabricLoader.getInstance().isModLoaded("fp_pal"),
                FabricLoader.getInstance().isModLoaded("player_animation_library"));
        }
        client.gui.setScreen(new QuestScreen());
    }
    static void close() {
        if (!nativeHands || stage==Stage.CLOSED) return;
        if (stage==Stage.OPENING) {
            closeRequested=true;
            return;
        }
        if (stage==Stage.CLOSING) return;
        try {
            if (!FirstPersonPalBridge.play("questplus.close")) { stop();reset();return; }
        } catch (LinkageError | RuntimeException error) {
            QuestPlusMod.LOGGER.warn("Cannot play FP PAL close animation",error);
            stop();reset();return;
        }
        stage=Stage.CLOSING;
    }
    private static void tick(Minecraft client) {
        if (stage==Stage.CLOSED) return;
        if (client.player==null || !client.player.isAlive() || client.player.isSpectator()) {
            stop();reset();return;
        }
        if (stage==Stage.OPENING && FirstPersonPalBridge.finished("questplus.open")) {
            if (client.gui.screen()!=null) { stop();reset();return; }
            if (closeRequested) {
                stage=Stage.HELD;
                close();
                return;
            }
            try {
                if (!FirstPersonPalBridge.play("questplus.idle")) { stop();reset();return; }
            } catch (LinkageError | RuntimeException error) {
                QuestPlusMod.LOGGER.warn("Cannot start FP PAL tablet idle",error);
                stop();reset();return;
            }
            stage=Stage.HELD;
            client.gui.setScreen(new QuestScreen());
        } else if (stage==Stage.CLOSING && FirstPersonPalBridge.finished("questplus.close")) {
            stop();reset();
        } else if (stage==Stage.HELD && !(client.gui.screen() instanceof QuestScreen)) {
            // Another mod/screen replaced our GUI; clear the lingering pose.
            stop();reset();
        }
    }
    private static void stop() {
        if (nativeHands) try { FirstPersonPalBridge.stop(); }
        catch (LinkageError | RuntimeException error) {
            QuestPlusMod.LOGGER.warn("Cannot stop FP PAL animation",error);
        }
    }
    private static void reset() { stage=Stage.CLOSED;nativeHands=false;closeRequested=false;TabletFaceAnchor.reset(); }

}
