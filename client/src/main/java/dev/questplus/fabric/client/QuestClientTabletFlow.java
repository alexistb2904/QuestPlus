package dev.questplus.fabric.client;

import dev.questplus.fabric.QuestPlusMod;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;

/** Opens the surface UI only after PAL reaches the final two-handed pose. */
final class QuestClientTabletFlow {
    private enum Stage { CLOSED, OPENING, HELD, CLOSING }
    private static Stage stage=Stage.CLOSED;
    private static boolean nativeHands;
    private static boolean closeRequested;
    private static long openingSince,closingSince;
    private static final long OPEN_WATCHDOG_NANOS=3_500_000_000L;
    private static final long CLOSE_WATCHDOG_NANOS=2_500_000_000L;
    private static CameraType previousCamera;
    private static final TabletDamageGuard DAMAGE=new TabletDamageGuard();
    private QuestClientTabletFlow() {}

    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(QuestClientTabletFlow::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client) -> { stop();reset(); });
        // FP PAL itself renders the Blockbench model from fp_models/ and textures/fp/.
        // No separate HUD tablet should be drawn here.
    }
    static boolean nativeHandsActive() { return nativeHands && stage!=Stage.CLOSED; }
    static boolean closing() { return stage==Stage.CLOSING; }
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
        // FP PAL only renders in first person. Switch before the open animation to prevent
        // a blurred screen without a tablet when J was pressed in third person.
        if (client.options.getCameraType()!=CameraType.FIRST_PERSON) {
            previousCamera=client.options.getCameraType();
            client.options.setCameraType(CameraType.FIRST_PERSON);
        }
        if (animationAvailable()) {
            // Claim first-person hands BEFORE starting FP PAL. PunchyApi.disable
            // is evaluated by Punchy's renderer during the same frame and
            // must already see this state (including the opening animation).
            nativeHands=true;stage=Stage.OPENING;openingSince=System.nanoTime();
            try {
                if (FirstPersonPalBridge.play("questplus.open")) {
                    QuestPlusMod.LOGGER.info("Playing Blockbench first-person questplus.open (1.0 s)");
                    return;
                }
                QuestPlusMod.LOGGER.warn("FP PAL could not play questplus:questplus.open. Loaded asset: {}",
                    FirstPersonPalBridge.loaded("questplus.open"));
            } catch (LinkageError | RuntimeException error) {
                QuestPlusMod.LOGGER.warn("Cannot start FP PAL animation; standard UI fallback",error);
            }
            nativeHands=false;stage=Stage.CLOSED;
        }
        nativeHands=false;stage=Stage.CLOSED;
        // No PAL animation is running, so do not leave the camera overridden.
        if (previousCamera!=null) {
            client.options.setCameraType(previousCamera);
            previousCamera=null;
        }
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
        stage=Stage.CLOSING;
        closingSince=System.nanoTime();
        try {
            if (!FirstPersonPalBridge.play("questplus.close")) { stop();reset();return; }
        } catch (LinkageError | RuntimeException error) {
            QuestPlusMod.LOGGER.warn("Cannot play FP PAL close animation",error);
            stop();reset();return;
        }
    }
    private static void tick(Minecraft client) {
        if(client.player==null) DAMAGE.reset();
        else {
            boolean tabletActive=stage==Stage.OPENING || stage==Stage.HELD
                || client.gui.screen() instanceof QuestScreen;
            boolean allow=QuestPlusClient.settings!=null && QuestPlusClient.settings.stayOpenOnDamage;
            if(DAMAGE.observe(tabletActive,allow,
                client.player.getHealth(),client.player.getAbsorptionAmount())) {
                // A screen that is already visible owns its own closure.
                // A hit during the opening animation interrupts that pose
                // and immediately plays the closing animation instead.
                if(client.gui.screen() instanceof QuestScreen quest) quest.onClose();
                else if(stage==Stage.OPENING) {stage=Stage.HELD;close();}
                else if(stage==Stage.HELD) close();
            }
        }
        if (stage==Stage.CLOSED) return;
        // Recover even if a shader/FP PAL fails to supply any final frame.
        if (stage==Stage.OPENING && System.nanoTime()-openingSince>OPEN_WATCHDOG_NANOS) {
            QuestPlusMod.LOGGER.warn("Tablet opening animation timed out; recovering the normal GUI");
            stop();reset();
            if (client.player!=null && client.gui.screen()==null)
                client.gui.setScreen(new QuestScreen());
            return;
        }
        // If a different screen (inventory, pause, etc.) appeared during
        // closing, release the FP pose immediately instead of drawing over it.
        if (stage==Stage.CLOSING && client.gui.screen()!=null) {
            stop();reset();return;
        }
        // Shaders can interrupt FP PAL's last frame or its callback. Never
        // retain the CLOSING pose indefinitely: it previously hid all HUD
        // and inventory elements until the player reconnected.
        if (stage==Stage.CLOSING && System.nanoTime()-closingSince>CLOSE_WATCHDOG_NANOS) {
            QuestPlusMod.LOGGER.warn("Tablet close animation timed out; restoring vanilla HUD and first-person state");
            stop();reset();return;
        }
        if (client.player==null || !client.player.isAlive() || client.player.isSpectator()) {
            stop();reset();return;
        }
        try {
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
        } catch (LinkageError | RuntimeException error) {
            QuestPlusMod.LOGGER.warn("Tablet animation was interrupted; restoring vanilla GUI state",error);
            stop();reset();
        }
    }
    private static void stop() {
        if (nativeHands) try { FirstPersonPalBridge.stop(); }
        catch (LinkageError | RuntimeException error) {
            QuestPlusMod.LOGGER.warn("Cannot stop FP PAL animation",error);
        }
    }
    private static void reset() {
        stage=Stage.CLOSED;nativeHands=false;closeRequested=false;openingSince=closingSince=0;
        TabletFaceAnchor.reset();DAMAGE.reset();
        if (previousCamera!=null) {
            Minecraft.getInstance().options.setCameraType(previousCamera);
            previousCamera=null;
        }
    }

}
