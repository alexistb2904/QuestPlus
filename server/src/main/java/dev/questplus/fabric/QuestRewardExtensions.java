package dev.questplus.fabric;

import com.google.gson.JsonObject;
import dev.questplus.core.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.util.function.*;

public final class QuestRewardExtensions {
    private record Handler(BiPredicate<ServerPlayer,QuestDefinition.Reward> fits,BiConsumer<ServerPlayer,QuestDefinition.Reward> deliver) {}
    private static final Map<String,Handler> HANDLERS=new HashMap<>();
    private QuestRewardExtensions() {}
    public static void register(String id,Consumer<JsonObject> validator,BiPredicate<ServerPlayer,QuestDefinition.Reward> fits,BiConsumer<ServerPlayer,QuestDefinition.Reward> deliver) {
        Objects.requireNonNull(fits); Objects.requireNonNull(deliver);
        QuestExtensions.registerReward(id,validator); HANDLERS.put(id,new Handler(fits,deliver));
    }
    private static Handler handler(String id) { var handler=HANDLERS.get(id); if (handler==null) throw new IllegalArgumentException("Reward handler unavailable: "+id); return handler; }
    static boolean fits(ServerPlayer player,QuestDefinition.Reward reward) { return handler(reward.type()).fits().test(player,reward); }
    static void deliver(ServerPlayer player,QuestDefinition.Reward reward) { handler(reward.type()).deliver().accept(player,reward); }
}
