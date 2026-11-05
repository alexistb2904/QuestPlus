package dev.questplus.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SubmissionCoreTest {
    @TempDir Path root;

    private void quest(String id,String mode,String target,int count) throws Exception {
        Files.writeString(root.resolve(id+".json5"),"""
        {
          schemaVersion: 2,
          id: 'questplus:%s', category: 'questplus:test', icon:'minecraft:iron_ingot',
          title:{en_us:'Submission %s'},description:{en_us:'Hand-in test'},
          objectives:[{id:'deposit',event:'%s',target:'%s',count:%d}],
          rewards:[], progression:'once'
        }
        """.formatted(id,id,mode,target,count));
    }
    private QuestRegistry registry() throws Exception { return QuestRegistry.load(root,(kind,target)->{}); }
    private QuestEvent event(String quest,String objective,String type,String target,long count,Set<String> tags){
        return new QuestEvent(type,target,tags,count,Map.of(
            "submissionQuest",quest,"submissionObjective",objective));
    }

    @Test void depositsMustTargetExactlyOneQuestAndObjective() throws Exception {
        quest("a","item_turn_in","minecraft:iron_ingot",5);
        quest("b","item_turn_in","minecraft:iron_ingot",5);
        var engine=new QuestEngine(registry());
        var progress=new PlayerProgress();
        assertTrue(engine.apply(progress,event("questplus:a","deposit","item_turn_in","minecraft:iron_ingot",3,Set.of())).isEmpty());
        assertEquals(3L,progress.quests.get("questplus:a").counters.get("deposit"));
        assertFalse(progress.quests.containsKey("questplus:b"));
        assertTrue(engine.apply(progress,event("questplus:b","wrong","item_turn_in","minecraft:iron_ingot",5,Set.of())).isEmpty());
        assertFalse(progress.quests.containsKey("questplus:b"));
        assertEquals(List.of("questplus:a"),engine.apply(progress,event("questplus:a","deposit","item_turn_in","minecraft:iron_ingot",2,Set.of())));
        assertEquals(List.of("questplus:b"),engine.apply(progress,event("questplus:b","deposit","item_turn_in","minecraft:iron_ingot",5,Set.of())));
        assertTrue(progress.quests.get("questplus:a").terminal);
        progress.validate();
    }

    @Test void tagHandInUsesOnlyMatchingTagAndRespectsReplayProtection() throws Exception {
        quest("tag","item_turn_in","#minecraft:ingots/iron",4);
        var engine=new QuestEngine(registry());
        var progress=new PlayerProgress();
        var event=event("questplus:tag","deposit","item_turn_in","minecraft:air",2,Set.of("minecraft:ingots/iron"));
        assertTrue(engine.apply(progress,event).isEmpty());
        assertTrue(engine.apply(progress,event).isEmpty());
        assertEquals(2L,progress.quests.get("questplus:tag").counters.get("deposit"));
        assertEquals(List.of("questplus:tag"),engine.apply(progress,event("questplus:tag","deposit","item_turn_in","minecraft:air",2,Set.of("minecraft:ingots/iron"))));
    }

    @Test void nonConsumingSubmitAlsoRequiresMatchingScope() throws Exception {
        quest("verify","item_submit","minecraft:diamond",3);
        var engine=new QuestEngine(registry());
        var progress=new PlayerProgress();
        assertTrue(engine.apply(progress,new QuestEvent("item_submit","minecraft:diamond",Set.of(),3,Map.of())).isEmpty());
        assertFalse(progress.quests.containsKey("questplus:verify"));
        assertEquals(List.of("questplus:verify"),engine.apply(progress,event("questplus:verify","deposit","item_submit","minecraft:diamond",3,Set.of())));
    }
}
