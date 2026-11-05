package dev.questplus.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RewardTablesTest {
    @Test void weightedPoolResolvesToConcreteEntitlement() {
        JsonObject table=JsonParser.parseString("""
          {"rolls":3,"options":[
            {"weight":2,"type":"item","item":"minecraft:emerald","count":4},
            {"weight":1,"type":"experience_levels","amount":10}
          ]}
        """).getAsJsonObject();
        RewardTables.validate(table);
        var original=new QuestDefinition.Reward("questplus:random",null,0,0,table.toString(),Map.of());
        var resolved=RewardTables.resolve(List.of(original));
        assertEquals(3,resolved.size());
        assertTrue(resolved.stream().allMatch(r->r.type().equals("item")||r.type().equals("experience_levels")));
        for(var reward:resolved)QuestExtensions.validateSavedReward(reward);
        assertTrue(resolved.stream().noneMatch(r->r.type().equals("questplus:random")));
    }

    @Test void rejectsScriptsInvalidWeightsAndUnknownRewardTypes() {
        for(String json:List.of(
          "{\"options\":[{\"weight\":0,\"type\":\"item\",\"item\":\"minecraft:diamond\",\"count\":1}]}",
          "{\"options\":[{\"weight\":1,\"type\":\"command\",\"command\":\"op @s\"}]}",
          "{\"rolls\":17,\"options\":[{\"weight\":1,\"type\":\"experience\",\"amount\":3}]}"
        )) assertThrows(RuntimeException.class,()->RewardTables.validate(JsonParser.parseString(json).getAsJsonObject()));
    }
}
