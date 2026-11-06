package dev.questplus.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RewardTablesTest {
    @Test void translatedNativeOptionsValidateResolveAndCheckInstalledRegistries(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
        var data=JsonParser.parseString("""
            {"description":{"en_us":"Choose wisely","fr_fr":"Choisissez bien"},"options":[
             {"type":"questplus:advancement","data":{"advancement":"minecraft:story/mine_stone"},"text":{"en_us":"Stone Age"}},
             {"type":"questplus:message","data":{"message":"Well done"}}
            ]}
            """).getAsJsonObject();
        RewardTables.validateChoice(data);
        var choice=new QuestDefinition.Reward("questplus:choice",null,0,0,data.toString(),Map.of());
        assertEquals("Stone Age",RewardTables.choose(choice,0).text().get("en_us"));
        assertEquals("questplus:message",RewardTables.choose(choice,1).type());
        java.nio.file.Files.writeString(root.resolve("q.json"),"""
            {"schemaVersion":1,"id":"questplus:test","category":"questplus:test","icon":"minecraft:stone","title":{"en_us":"Test"},
             "progression":"once","objectives":[{"id":"x","event":"manual","target":"questplus:test","count":1}],
             "rewards":[{"type":"questplus:choice","data":%s}]}
            """.formatted(data));
        var checked=new ArrayList<String>();
        QuestRegistry.load(root,(kind,id)->checked.add(kind+":"+id));
        assertTrue(checked.contains("advancement_complete:minecraft:story/mine_stone"));
        assertThrows(IllegalArgumentException.class,()->QuestRegistry.load(root,(kind,id)->{
            if(kind.equals("advancement_complete"))throw new IllegalArgumentException("Missing advancement");
        }));
    }
    @Test void explicitSlotsAreImmutableAndDoNotConsumeAnotherChoiceOnReplay() {
        var choice=new QuestDefinition.Reward("questplus:choice",null,0,0,
            "{\"options\":[{\"type\":\"experience\",\"amount\":7},{\"type\":\"experience_levels\",\"amount\":2}]}",Map.of());
        var grant=new PlayerProgress.RewardGrant("questplus:test",List.of(choice,choice));
        var selected=RewardTables.choose(grant,1,0);
        assertEquals("questplus:choice",selected.rewards().get(0).type());
        assertEquals("experience",selected.rewards().get(1).type());
        assertThrows(IllegalArgumentException.class,()->RewardTables.choose(selected,1,1));
        assertThrows(IllegalArgumentException.class,()->RewardTables.choose(grant,-1,0));
        assertEquals("questplus:choice",grant.rewards().get(1).type());
    }
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

    @Test void lootAndAllPoolsHaveDifferentClaimSemantics() {
        var items=JsonParser.parseString("""
            {"options":[{"weight":1,"type":"item","item":"minecraft:diamond","count":2},
                        {"weight":1,"type":"experience_levels","amount":5}]}
            """).getAsJsonObject();
        RewardTables.validateAll(items);
        var all=new QuestDefinition.Reward("questplus:all",null,0,0,items.toString(),Map.of());
        assertEquals(2,RewardTables.resolve(List.of(all)).size());

        var loot=items.deepCopy();
        loot.addProperty("emptyWeight",0);
        RewardTables.validateLoot(loot);
        var chosen=new QuestDefinition.Reward("questplus:loot",null,0,0,loot.toString(),Map.of());
        assertEquals(1,RewardTables.resolve(List.of(chosen)).size());
        loot.addProperty("emptyWeight",-1);
        assertThrows(IllegalArgumentException.class,()->RewardTables.validateLoot(loot));
    }
    @Test void choiceAcceptsOneSafeOptionAndResolvesExactlyOne() {
        var data=JsonParser.parseString("""
          {"options":[
             {"type":"item","item":"minecraft:diamond","count":2},
             {"type":"experience_levels","amount":10}
          ]}
        """).getAsJsonObject();
        RewardTables.validateChoice(data);
        var source=new QuestDefinition.Reward("questplus:choice",null,0,0,data.toString(),Map.of());
        var completion=RewardTables.resolve(List.of(source));
        assertEquals("questplus:choice",completion.getFirst().type()); // unresolved until player selection
        var item=RewardTables.choose(source,0);
        assertEquals("item",item.type());
        assertEquals("minecraft:diamond",item.item());
        assertEquals(2,item.count());
        var levels=RewardTables.choose(source,1);
        assertEquals("experience_levels",levels.type());
        assertEquals(10,levels.amount());
        QuestExtensions.validateSavedReward(item);
        QuestExtensions.validateSavedReward(levels);
        assertThrows(IllegalArgumentException.class,()->RewardTables.choose(source,2));
        data.getAsJsonArray("options").remove(1);
        RewardTables.validateChoice(data);
        data.getAsJsonArray("options").remove(0);
        assertThrows(IllegalArgumentException.class,()->RewardTables.validateChoice(data));
    }
    @Test void choiceCannotGrantArbitraryCommands() {
        var data=JsonParser.parseString("""
          {"options":[
            {"type":"item","item":"minecraft:diamond","count":2},
            {"type":"questplus:command","command":"op @s"}
          ]}
        """).getAsJsonObject();
        assertThrows(IllegalArgumentException.class,()->RewardTables.validateChoice(data));
    }
    @Test void rejectsScriptsInvalidWeightsAndUnknownRewardTypes() {
        for(String json:List.of(
          "{\"options\":[{\"weight\":-1,\"type\":\"item\",\"item\":\"minecraft:diamond\",\"count\":1}]}",
          "{\"options\":[{\"weight\":1,\"type\":\"command\",\"command\":\"op @s\"}]}",
          "{\"rolls\":17,\"options\":[{\"weight\":1,\"type\":\"experience\",\"amount\":3}]}"
        )) assertThrows(RuntimeException.class,()->RewardTables.validate(JsonParser.parseString(json).getAsJsonObject()));
    }
}
