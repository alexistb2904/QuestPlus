package dev.questplus.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FtbProgressMigrationTest {
    @TempDir Path root;
    private QuestRegistry registry()throws Exception {
        Files.writeString(root.resolve("q.json5"),"""
            {schemaVersion:2,id:'test:migrated',category:'test:category',icon:'minecraft:stone',title:{en_us:'Migration'},progression:'once',
             objectives:[{id:'goal',event:'block_break',target:'minecraft:stone',count:5}],rewards:[{type:'experience',amount:10},{type:'item',item:'minecraft:diamond',count:1}]}
            """);return QuestRegistry.load(root,(kind,id)->{});
    }
    private JsonArray mappings(){return JsonParser.parseString("[{sourceQuest:'000000000000000a',targetQuest:'test:migrated',tasks:[{sourceId:'000000000000000b',objectiveId:'goal'}],rewards:[{sourceId:'000000000000000c',index:0},{sourceId:'000000000000000d',index:1}]}]").getAsJsonArray();}
    @Test void copiesProgressAndClaimsWithoutDeliveringOrMutatingSource()throws Exception {
        UUID player=UUID.randomUUID();var source=JsonParser.parseString("{version:1,task_progress:{'11':5},started:{'10':1},completed:{'10':2},claimed_rewards:{}}").getAsJsonObject();
        source.getAsJsonObject("claimed_rewards").addProperty(player.toString().replace("-","")+":000000000000000C",3);
        var current=new PlayerProgress();var result=FtbProgressMigration.prepare(registry(),source,mappings(),current,current,player,null,new HashMap<>(),10);
        assertTrue(current.quests.isEmpty());assertTrue(result.personal().quests.get("test:migrated").completed);
        assertEquals("delivered",result.personal().claims.get("test:migrated|ftb:000000000000000c|1"));
        assertEquals(1,result.personal().entitlements.size());assertEquals("item",result.personal().entitlements.values().iterator().next().rewards().getFirst().type());
        assertEquals(1,source.getAsJsonObject("claimed_rewards").size());assertEquals(registry().get("test:migrated").fingerprint(),result.personal().quests.get("test:migrated").fingerprint);
    }
    @Test void incompatibleOrRepeatedMigrationDoesNotOverwriteProgress()throws Exception {
        var source=JsonParser.parseString("{task_progress:{'11':2},started:{'10':1},completed:{},claimed_rewards:{}}").getAsJsonObject();var registry=registry();UUID player=UUID.randomUUID();var empty=new PlayerProgress();
        var first=FtbProgressMigration.prepare(registry,source,mappings(),empty,empty,player,null,new HashMap<>(),10);
        assertEquals(2L,first.personal().quests.get("test:migrated").counters.get("goal"));assertTrue(first.personal().entitlements.isEmpty());
        assertThrows(IllegalArgumentException.class,()->FtbProgressMigration.prepare(registry,source,mappings(),first.state(),first.personal(),player,null,new HashMap<>(),10));
        var missing=mappings();missing.get(0).getAsJsonObject().add("tasks",new JsonArray());
        assertThrows(IllegalArgumentException.class,()->FtbProgressMigration.prepare(registry,source,missing,empty,empty,player,null,new HashMap<>(),10));
    }
}
