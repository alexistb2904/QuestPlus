package dev.questplus.core;

import com.google.gson.*;
import dev.questplus.persistence.JsonProgressStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FtbParityTest {
    @TempDir Path root;
    private static JsonObject json(String value){return JsonParser.parseString(value).getAsJsonObject();}
    @Test void componentMatchingRetainsExactAndPartialSemantics() {
        var partial=json("{components:{'minecraft:custom_data':{grade:2}},matchComponents:'partial'}");
        var actual=json("{'minecraft:custom_data':{grade:2,extra:true},'minecraft:damage':3}");
        assertTrue(ItemComponents.matches(partial,actual));
        partial.addProperty("matchComponents","exact");assertFalse(ItemComponents.matches(partial,actual));
        partial.add("components",actual);assertTrue(ItemComponents.matches(partial,actual));
        assertThrows(IllegalArgumentException.class,()->ItemComponents.validate(json("{components:{invalid:1}}")));
    }
    @Test void componentInventoryCountersRefreshFromMatchingStacksOnly() throws Exception {
        Files.writeString(root.resolve("q.json5"),"""
            {schemaVersion:2,id:'test:components',category:'test:category',icon:'minecraft:stone',title:{en_us:'Components'},progression:'once',
             objectives:[{id:'items',event:'inventory_count',target:'minecraft:stone',count:2,data:{components:{'minecraft:custom_data':{grade:2}},matchComponents:'partial'}}],rewards:[]}
            """);
        var registry=QuestRegistry.load(root,(kind,id)->{});var engine=new QuestEngine(registry);var progress=new PlayerProgress();
        var context=new HashMap<String,Object>();context.put("inventory",Map.of("minecraft:stone",64L));
        context.put("itemStacks",List.of(Map.of("item","minecraft:stone","count",64,"components",json("{'minecraft:custom_data':{grade:1}}"))));
        assertTrue(engine.apply(progress,new QuestEvent("inventory_count","minecraft:stone",Set.of(),64,context)).isEmpty());
        assertFalse(progress.quests.containsKey("test:components"));
        context.put("itemStacks",List.of(Map.of("item","minecraft:stone","count",1,"components",json("{'minecraft:custom_data':{grade:2}}"))));
        engine.apply(progress,new QuestEvent("inventory_count","minecraft:stone",Set.of(),1,context));
        assertEquals(1L,progress.quests.get("test:components").counters.get("items"));
        context.put("itemStacks",List.of());engine.apply(progress,new QuestEvent("inventory_count","minecraft:stone",Set.of(),0,context));
        assertEquals(0L,progress.quests.get("test:components").counters.get("items"));
    }
    @Test void choicesAndRollsKeepTheDeliveryPolicyAndComponents() {
        var data=json("{options:[{type:'item',item:'minecraft:stone',count:1,components:{'minecraft:custom_data':{grade:2}}},{type:'experience',amount:2}]}");
        var policy=new QuestDefinition.RewardPolicy(true,"no_toast",true,UUID.randomUUID());
        var reward=new QuestDefinition.Reward("questplus:choice",null,0,0,data.toString(),Map.of(),policy);
        var chosen=RewardTables.choose(reward,0);assertEquals(policy,chosen.policy());assertTrue(chosen.data().contains("grade"));
        for(var option:data.getAsJsonArray("options"))option.getAsJsonObject().addProperty("weight",1);
        var all=new QuestDefinition.Reward("questplus:all",null,0,0,data.toString(),Map.of(),policy);
        assertTrue(RewardTables.resolve(List.of(all)).stream().allMatch(r->r.policy().equals(policy)));
    }
    @Test void policyEntitlementsSplitWithoutChangingLegacyKeysAndSurviveDisk() throws Exception {
        var plain=new QuestDefinition.Reward("experience",null,0,1);
        var shared=plain.withPolicy(new QuestDefinition.RewardPolicy(true,"enabled",false,UUID.randomUUID()));
        var progress=new PlayerProgress();progress.entitle("test:legacy",new PlayerProgress.RewardGrant("test:legacy",List.of(plain,plain)));
        assertTrue(progress.entitlements.containsKey("test:legacy"));
        progress.entitle("test:new",new PlayerProgress.RewardGrant("test:new",List.of(plain,shared)));
        assertEquals(3,progress.entitlements.size());
        UUID id=UUID.randomUUID();try(var store=new JsonProgressStore(root)){store.save(id,progress).join();assertEquals(progress.entitlements,store.load(id).entitlements);}
    }
    @Test void vanillaExperienceBoundariesAndSpentPointsDoNotUseLifetimeTotals() {
        assertEquals(7,Experience.base(1));assertEquals(352,Experience.base(16));assertEquals(1507,Experience.base(31));
        for(int level:List.of(0,1,15,16,17,30,31,32,100,1000))assertEquals(level,Experience.level(Math.toIntExact(Experience.base(level))));
        assertEquals(11,Experience.points(1,.5f));assertEquals(0,Experience.level(6));
        assertTrue(Experience.base(Experience.level(Integer.MAX_VALUE))<=Integer.MAX_VALUE);
    }
    @Test void crateTablesRequireRealPoolsAndRejectChoices() {
        var list=JsonParser.parseString("[{name:'stone',crate:true,reward:{type:'questplus:all',data:{options:[{type:'item',item:'minecraft:stone',count:1,weight:1}]}}}]").getAsJsonArray();
        var tables=new NamedRewardTables(list);assertEquals("questplus:all",tables.crate("stone").get("type").getAsString());
        assertThrows(IllegalArgumentException.class,()->tables.crate("missing"));
        list.get(0).getAsJsonObject().getAsJsonObject("reward").addProperty("type","questplus:choice");
        assertThrows(IllegalArgumentException.class,()->new NamedRewardTables(list));
    }
    @Test void guaranteedOptionsBonusAndCrateDropsDoNotRerollAtClaim() {
        var reward=new QuestDefinition.Reward("questplus:loot",null,0,0,"{rolls:0,emptyWeight:1,options:[{weight:0,type:'item',item:'minecraft:stone',count:1,randomBonus:3}]}",Map.of());
        var result=RewardTables.resolve(List.of(reward));assertEquals(1,result.size());assertTrue(result.getFirst().count()>=1&&result.getFirst().count()<=4);
        assertFalse(result.getFirst().data().contains("randomBonus"));assertEquals(result,RewardTables.resolve(result));
        var empty=new QuestDefinition.Reward("questplus:random",null,0,0,"{rolls:1,options:[{weight:0,type:'experience',amount:1}]}",Map.of());assertTrue(RewardTables.resolve(List.of(empty)).isEmpty());
        var tables=new NamedRewardTables(JsonParser.parseString("[{name:'rare',crate:true,crateColor:'#ff0000',crateGlow:true,crateDrops:{passive:0,monster:1,boss:5},reward:{type:'questplus:all',data:{options:[]}}}]").getAsJsonArray());
        assertNull(tables.drop("passive",0,new Random(1)));var item=tables.drop("boss",0,new Random(1));assertEquals("rare",item.getAsJsonObject("components").getAsJsonObject("minecraft:custom_data").get("questplus_table").getAsString());
        assertTrue(item.getAsJsonObject("components").get("minecraft:enchantment_glint_override").getAsBoolean());
    }
    @Test void blockCoordinateBoundsUseFloorAndAlternativeItemsDoNotDoubleCount() throws Exception {
        QuestExtensions.validateCondition("position",json("{blockCoordinates:true,xMin:-1,xMax:0}"));
        assertTrue(QuestExtensions.testCondition("position","{blockCoordinates:true,xMin:-1,xMax:0}",Map.of("position",Map.of("x",-.1))));
        assertFalse(QuestExtensions.testCondition("position","{blockCoordinates:true,xMin:-1,xMax:0}",Map.of("position",Map.of("x",1.0))));
        Files.writeString(root.resolve("q.json5"),"""
            {schemaVersion:2,id:'test:filter',category:'test:c',icon:'minecraft:stone',title:{en_us:'Filter'},progression:'once',objectives:[{id:'items',event:'inventory_count',target:'minecraft:stone',count:5,data:{targets:['minecraft:stone','#minecraft:logs']}}],rewards:[]}
            """);
        var engine=new QuestEngine(QuestRegistry.load(root,(kind,id)->{}));var state=new PlayerProgress();
        var stack=Map.<String,Object>of("item","minecraft:stone","count",3,"tags",Set.of("minecraft:logs"),"components",new JsonObject());
        engine.apply(state,new QuestEvent("inventory_count","minecraft:stone",Set.of("minecraft:logs"),3,Map.of("itemStacks",List.of(stack))));
        assertEquals(3L,state.quests.get("test:filter").counters.get("items"));
        engine.apply(state,new QuestEvent("inventory_count","minecraft:stone",Set.of(),0,Map.of("itemStacks",List.of())));assertEquals(0L,state.quests.get("test:filter").counters.get("items"));
    }
}
