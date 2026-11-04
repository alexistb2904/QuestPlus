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
    /** A shader is active only when Iris says a pack is actually in use. */
    public static boolean shadersActive(){ return shaderPackActive(); }
    /** Self illumination uses a matte lit screen with shaders, original emissive rendering without. */
    public static boolean matteIllumination(boolean shaderActive,boolean selfIlluminated) {
        return shaderActive && selfIlluminated;
    }
    public static boolean illuminatedScreen(){
        return QuestPlusClient.settings!=null
            && matteIllumination(shadersActive(),QuestPlusClient.settings.screenEmissive);
    }
    /** Shader blur is always disabled, even if Self Illumination is off. */
    public static boolean backgroundBlur(boolean userEnabled,boolean shaderActive) {
        return userEnabled && !shaderActive;
    }
    public static boolean backgroundBlurEnabled(){
        return QuestPlusClient.settings!=null
            && backgroundBlur(QuestPlusClient.settings.blur,shadersActive());
    }
    /** Only matte illuminated 3D screens need the shader brightness guard. */
    public static double screenBrightness(double requested,boolean matteIllumination) {
        return Math.clamp(requested,0,matteIllumination?0.78:1);
    }
}
