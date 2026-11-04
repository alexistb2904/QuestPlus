package dev.questplus.fabric.client;

import dev.questplus.fabric.QuestPlusMod;
import net.fabricmc.loader.api.FabricLoader;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;

/**
 * Optional Punchy! 3.1+ API bridge, isolated from its jar and classes.
 * FP PAL owns the tablet opening / idle / closing arms; Punchy! should
 * animate all other first-person interactions normally.
 */
final class PunchyTabletCompat {
    private PunchyTabletCompat(){}
    private static BooleanSupplier tabletOwnsHands(){
        return ()->QuestClientTabletFlow.nativeHandsActive();
    }
    static void register(){
        if(!FabricLoader.getInstance().isModLoaded("punchy"))return;
        try{
            Class<?> api=Class.forName("punchy.api.PunchyApi",false,PunchyTabletCompat.class.getClassLoader());
            int version=api.getField("VERSION").getInt(null);
            if(version<1)throw new IllegalStateException("Punchy API version "+version+" is unsupported");
            Method disable=api.getMethod("disable",String.class,BooleanSupplier.class);
            disable.invoke(null,"questplus",tabletOwnsHands());
            QuestPlusMod.LOGGER.info("Punchy compatibility: native tablet animations take priority while the tablet is held (API {})",version);
        }catch(ReflectiveOperationException|LinkageError|IllegalStateException ex){
            QuestPlusMod.LOGGER.warn("Punchy is installed but cannot hand over first-person rendering to QuestPlus. Upgrade Punchy! to 3.1+ for the PunchyApi.disable integration; or disable Punchy temporarily using F8.",ex);
        }
    }
}
