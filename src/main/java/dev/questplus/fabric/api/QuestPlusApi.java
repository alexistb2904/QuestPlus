package dev.questplus.fabric.api;

import dev.questplus.core.QuestEvent;
import dev.questplus.core.QuestExtensions;
import dev.questplus.core.QuestDefinition;
import dev.questplus.fabric.QuestRewardExtensions;
import com.google.gson.*;
import java.util.Map;
import java.util.function.*;
import dev.questplus.fabric.QuestPlusMod;
import net.minecraft.server.level.ServerPlayer;

/** Server-thread entry point for trusted mods that supply normalized events. */
public final class QuestPlusApi {
    private QuestPlusApi() {}
    public static void submit(ServerPlayer responsible,QuestEvent event) { QuestPlusMod.runtime().event(responsible,event); }
    public static void registerEvent(String id,String registry,boolean state) { QuestExtensions.registerEvent(id,registry,state); }
    public static void registerCondition(String id,Consumer<JsonElement> validator,BiPredicate<JsonElement,Map<String,Object>> predicate) { QuestExtensions.registerCondition(id,validator,predicate); }
    public static void registerReward(String id,Consumer<JsonObject> validator,BiPredicate<ServerPlayer,QuestDefinition.Reward> fits,BiConsumer<ServerPlayer,QuestDefinition.Reward> deliver) {
        QuestRewardExtensions.register(id,validator,fits,deliver);
    }
}
