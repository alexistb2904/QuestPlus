package dev.questplus.fabric;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Physical menu item; opening the local screen never grants server authority. */
public final class QuestTabletItem extends Item {
    private QuestTabletItem(Properties properties) { super(properties); }
    static void register() {
        var key=ResourceKey.create(Registries.ITEM,Identifier.parse("questplus:tablet"));
        Registry.register(BuiltInRegistries.ITEM,key,new QuestTabletItem(new Item.Properties().setId(key).stacksTo(1)));
        UseBlockCallback.EVENT.register((player,level,hand,hit) ->
            !level.isClientSide() && player.getItemInHand(hand).getItem() instanceof QuestTabletItem
                ? InteractionResult.SUCCESS : InteractionResult.PASS);
    }
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand) {
        return InteractionResult.SUCCESS;
    }
}
