package dev.questplus.fabric;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** The table identifier is an ordinary persistent custom-data component, usable by vanilla loot tables. */
final class QuestCrateItem extends Item {
    private QuestCrateItem(Properties properties){super(properties);}
    static void register() {
        var key=ResourceKey.create(Registries.ITEM,Identifier.parse("questplus:loot_crate"));
        Registry.register(BuiltInRegistries.ITEM,key,new QuestCrateItem(new Item.Properties().setId(key).stacksTo(64)));
    }
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand) {
        if(player instanceof ServerPlayer actor) {
            try{QuestPlusMod.runtime().openCrate(actor,hand);}
            catch(Exception error){actor.sendSystemMessage(net.minecraft.network.chat.Component.literal("QuestPlus: "+error.getMessage()));return InteractionResult.FAIL;}
        }
        return InteractionResult.SUCCESS;
    }
}
