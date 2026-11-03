package dev.questplus.fabric.client;

import dev.questplus.fabric.QuestPlusMod;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Animate the actual Minecraft first-person arms BEFORE opening the GUI.
 * A standalone HUD draw shows only the tablet prop during the transitions.
 */
final class QuestClientTabletFlow {
    private enum Stage { CLOSED, OPENING, HELD, CLOSING }
    private static Stage stage=Stage.CLOSED;
    private static long started;
    private static boolean nativeHands;
    private QuestClientTabletFlow() {}

    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(QuestClientTabletFlow::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client) -> reset());
        HudElementRegistry.addLast(Identifier.parse("questplus:tablet_transition"),
            (graphics,delta) -> drawTransition(graphics));
    }
    static boolean nativeHandsActive() { return nativeHands && stage!=Stage.CLOSED; }
    static boolean thirdPersonHolding() { return nativeHands && (stage==Stage.OPENING || stage==Stage.HELD); }
    static double idleSeconds() { return Math.max(0,(System.nanoTime()-started)/1_000_000_000.0); }

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
                    nativeHands=true;stage=Stage.OPENING;started=System.nanoTime();
                    return;
                }
            } catch (LinkageError | RuntimeException error) {
                QuestPlusMod.LOGGER.warn("Cannot start FP PAL animation; standard UI fallback",error);
            }
        }
        nativeHands=false;stage=Stage.CLOSED;
        client.gui.setScreen(new QuestScreen());
    }
    static void close() {
        if (!nativeHands || stage==Stage.CLOSED) return;
        if (stage==Stage.OPENING) {
            // A quick second press should not jump to the full two-handed closing pose.
            stop();reset();return;
        }
        if (stage==Stage.CLOSING) return;
        try {
            if (!FirstPersonPalBridge.play("questplus.close")) { stop();reset();return; }
        } catch (LinkageError | RuntimeException error) {
            QuestPlusMod.LOGGER.warn("Cannot play FP PAL close animation",error);
            stop();reset();return;
        }
        stage=Stage.CLOSING; started=System.nanoTime();
    }
    private static void tick(Minecraft client) {
        if (stage==Stage.CLOSED) return;
        if (client.player==null || !client.player.isAlive() || client.player.isSpectator()) {
            stop();reset();return;
        }
        long elapsed=System.nanoTime()-started;
        if (stage==Stage.OPENING && elapsed>=TabletFirstPersonAnimation.OPEN_NANOS) {
            if (client.gui.screen()!=null) { stop();reset();return; }
            try {
                if (!FirstPersonPalBridge.play("questplus.idle")) { stop();reset();return; }
            } catch (LinkageError | RuntimeException error) {
                QuestPlusMod.LOGGER.warn("Cannot start FP PAL tablet idle",error);
                stop();reset();return;
            }
            stage=Stage.HELD;started=System.nanoTime();
            client.gui.setScreen(new QuestScreen());
        } else if (stage==Stage.CLOSING && elapsed>=TabletFirstPersonAnimation.OPEN_NANOS) {
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
    private static void reset() { stage=Stage.CLOSED;nativeHands=false;started=0; }

    private static void drawTransition(GuiGraphicsExtractor graphics) {
        if (!nativeHands || (stage!=Stage.OPENING && stage!=Stage.CLOSING)) return;
        var client=Minecraft.getInstance();
        if (client.gui.screen()!=null || client.player==null || !QuestPlusClient.settings.tablet) return;
        var pose=TabletFirstPersonAnimation.pose(stage==Stage.OPENING ? "questplus.open" : "questplus.close",
            (System.nanoTime()-started)/1_000_000_000.0);
        int width=client.getWindow().getGuiScaledWidth(),height=client.getWindow().getGuiScaledHeight();
        int margin=Math.max(12,(int)(Math.min(width,height)*.20));
        int w=Math.min(width-margin*2,(int)(760*QuestPlusClient.settings.scale));
        int h=Math.min(height-margin*2,(int)(w*.6));
        w=Math.min(w,(int)(h/.6));
        TabletRenderer.draw(graphics,(width-w)/2,(height-h)/2,
            (width+w)/2,(height+h)/2,false,pose);
    }
}
