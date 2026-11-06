package dev.questplus.fabric.client;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public final class QuestJeiPlugin implements IModPlugin {
    private static IJeiRuntime runtime;
    @Override public Identifier getPluginUid() { return Identifier.parse("questplus:recipes"); }
    @Override public void onRuntimeAvailable(IJeiRuntime value) { runtime=value; }
    @Override public void onRuntimeUnavailable() { runtime=null; }
    static boolean open(ItemStack stack) {
        if(runtime==null)return false;
        var focus=runtime.getJeiHelpers().getFocusFactory().createFocus(
            RecipeIngredientRole.OUTPUT,VanillaTypes.ITEM_STACK,stack.copy());
        runtime.getRecipesGui().show(focus);
        return true;
    }
}
