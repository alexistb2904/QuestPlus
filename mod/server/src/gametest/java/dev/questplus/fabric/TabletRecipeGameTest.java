package dev.questplus.fabric;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;

public final class TabletRecipeGameTest {
    @GameTest
    public void tabletCraftsWithDifferentPlanks(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipes = level.getServer().getRecipeManager();
        var tablet = BuiltInRegistries.ITEM.getValue(Identifier.parse("questplus:tablet"));
        for (var planks : List.of(Items.OAK_PLANKS, Items.SPRUCE_PLANKS)) {
            var grid = new ArrayList<>(List.of(
                    new ItemStack(Items.IRON_INGOT), new ItemStack(Items.AMETHYST_SHARD), new ItemStack(Items.IRON_INGOT),
                    new ItemStack(planks), new ItemStack(Items.BOOK), new ItemStack(planks),
                    new ItemStack(Items.IRON_INGOT), new ItemStack(Items.REDSTONE), new ItemStack(Items.IRON_INGOT)));
            var input = CraftingInput.of(3, 3, grid);
            var recipe = recipes.getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
            var result = recipe.value().assemble(input);
            helper.assertTrue(result.is(tablet) && result.getCount() == 1, "Recipe produces one tablet");
            grid.set(4, ItemStack.EMPTY);
            helper.assertTrue(!recipe.value().matches(CraftingInput.of(3, 3, grid), level), "Book is required");
        }
        helper.succeed();
    }
}
