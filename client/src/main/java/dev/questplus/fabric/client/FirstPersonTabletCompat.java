package dev.questplus.fabric.client;

import dev.questplus.fabric.QuestPlusMod;
import net.fabricmc.loader.api.FabricLoader;
import java.lang.reflect.Proxy;

/** Use First Person Model's activation hook without changing the player's enabled setting. */
final class FirstPersonTabletCompat {
    private FirstPersonTabletCompat() {}
    static void register() {
        if (!FabricLoader.getInstance().isModLoaded("firstperson")) return;
        try {
            var loader=FirstPersonTabletCompat.class.getClassLoader();
            var api=Class.forName("dev.tr7zw.firstperson.api.FirstPersonAPI",false,loader);
            var handler=Class.forName("dev.tr7zw.firstperson.api.ActivationHandler",false,loader);
            Object hook=Proxy.newProxyInstance(loader,new Class<?>[]{handler},(proxy,method,args) -> switch(method.getName()) {
                case "preventFirstperson" -> QuestClientTabletFlow.nativeHandsActive();
                case "toString" -> "QuestPlus tablet";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy==args[0];
                default -> throw new UnsupportedOperationException(method.toString());
            });
            api.getMethod("registerPlayerHandler",Object.class).invoke(null,hook);
            QuestPlusMod.LOGGER.info("First Person Model compatibility: FP PAL owns the camera while the tablet is active");
        } catch (ReflectiveOperationException|LinkageError ex) {
            QuestPlusMod.LOGGER.warn("First Person Model activation API is unavailable",ex);
        }
    }
}
