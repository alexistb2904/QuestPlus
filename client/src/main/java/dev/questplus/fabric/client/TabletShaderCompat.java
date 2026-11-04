package dev.questplus.fabric.client;

import dev.questplus.fabric.QuestPlusMod;
import net.fabricmc.loader.api.FabricLoader;
import java.lang.reflect.Method;

/**
 * Optional Iris integration: no hard Iris dependency in the distributable JAR.
 * Shader packs are external post processors and may treat emissive/translucent
 * first-person geometry as bloom, glass or mirror materials.
 */
public final class TabletShaderCompat {
    private record IrisAccess(Object instance,Method method){}
    private static final IrisAccess IRIS=findIris();
    private static boolean warned;
    private TabletShaderCompat(){}
    private static IrisAccess findIris() {
        if(!FabricLoader.getInstance().isModLoaded("iris"))return null;
        try{
            Class<?> api=Class.forName("net.irisshaders.iris.api.v0.IrisApi",false,TabletShaderCompat.class.getClassLoader());
            Object instance=api.getMethod("getInstance").invoke(null);
            return new IrisAccess(instance,api.getMethod("isShaderPackInUse"));
        }catch(ReflectiveOperationException|LinkageError ex){
            QuestPlusMod.LOGGER.warn("Iris loaded but shader API is not available, using vanilla tablet rendering",ex);
            return null;
        }
    }
    public static boolean shaderPackActive() {
        if(IRIS==null)return false;
        try{
            return Boolean.TRUE.equals(IRIS.method().invoke(IRIS.instance()));
        }catch(ReflectiveOperationException|LinkageError ex){
            if(!warned){warned=true;QuestPlusMod.LOGGER.warn("Iris shader probe failed; keeping normal tablet rendering",ex);}
            return false;
        }
    }
    public static boolean safeRendering(){
        return QuestPlusClient.settings!=null && QuestPlusClient.settings.shaderSafeMode && shaderPackActive();
    }
    /** Clamp ONLY the 3D atlas, never user configured vanilla/2D brightness. */
    public static double screenBrightness(double requested,boolean shaderSafe){
        return Math.clamp(requested,0,shaderSafe?0.78:1);
    }
}
