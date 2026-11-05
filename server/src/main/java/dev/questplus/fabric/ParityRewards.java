package dev.questplus.fabric;

import com.google.gson.*;
import dev.questplus.core.Localization;
import java.util.*;

final class ParityRewards {
    private ParityRewards() {}
    static void install() {
        QuestRewardExtensions.bind("questplus:stage",(player,reward)->true,(player,reward)->{
            var data=JsonParser.parseString(reward.data()).getAsJsonObject();
            QuestStages.set(player,data.get("stage").getAsString(),!data.get("remove").getAsBoolean());
        });
        QuestRewardExtensions.bind("questplus:toast",(player,reward)->true,(player,reward)->{
            var data=JsonParser.parseString(reward.data()).getAsJsonObject();String language=player.clientInformation().language();
            QuestNetwork.notice(player,localized(data.getAsJsonObject("title"),language),localized(data.getAsJsonObject("description"),language),data.get("icon").getAsString());
        });
    }
    private static String localized(JsonObject value,String locale) {
        Map<String,String> translations=new HashMap<>();value.entrySet().forEach(e->translations.put(e.getKey(),e.getValue().getAsString()));
        return Localization.text(translations,locale,Map.of());
    }
}
