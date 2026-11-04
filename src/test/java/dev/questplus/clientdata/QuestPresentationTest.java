package dev.questplus.clientdata;

import com.google.gson.JsonParser;
import dev.questplus.core.QuestDefinition;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class QuestPresentationTest {
    private static final QuestDefinition.Objective OBJECTIVE = new QuestDefinition.Objective("mine","break","stone",3,Map.of(),null);

    private static QuestDefinition quest(String progression,List<QuestDefinition.Tier> tiers) {
        return new QuestDefinition("questplus:test","questplus:general","minecraft:stone",Map.of(),Map.of(),List.of(OBJECTIVE),null,List.of(),progression,null,Map.of(),"fingerprint","all",tiers,"stop",0,0,false,"standard","manual");
    }
    private static com.google.gson.JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }

    @Test void infiniteAndRestartedRepeatableQuestsUseCurrentWorkNotHistoricalCompletion() {
        var infinite=quest("infinite",List.of());
        assertEquals("active",QuestPresentation.status(infinite,json("{completed:true,unlocked:true,cycles:4,counters:{mine:2}}")));
        assertEquals("available",QuestPresentation.status(infinite,json("{completed:true,unlocked:true,cycles:4,counters:{}}")));
        var repeatable=quest("repeatable",List.of());
        assertEquals("completed",QuestPresentation.status(repeatable,json("{completed:true,unlocked:true,waitingRestart:true}")));
        assertEquals("available",QuestPresentation.status(repeatable,json("{completed:false,unlocked:true,waitingRestart:false,counters:{}}")));
    }

    @Test void tieredProgressUsesCurrentTargetAndKeepsPartialLaterTierActive() {
        var tiers=List.of(new QuestDefinition.Tier("one",1,List.of()),new QuestDefinition.Tier("two",4,List.of()),new QuestDefinition.Tier("three",8,List.of()));
        var tiered=quest("tiered",tiers);
        assertEquals(3,QuestPresentation.target(tiered,OBJECTIVE,json("{tier:0}")));
        assertEquals(12,QuestPresentation.target(tiered,OBJECTIVE,json("{tier:1}")));
        assertEquals(24,QuestPresentation.target(tiered,OBJECTIVE,json("{tier:2}")));
        assertEquals(24,QuestPresentation.target(tiered,OBJECTIVE,json("{tier:99}")));
        assertEquals("active",QuestPresentation.status(tiered,json("{completed:true,unlocked:true,tier:1,counters:{mine:4}}")));
        assertEquals("completed",QuestPresentation.status(tiered,json("{terminal:true,unlocked:true,tier:3}")));
    }

    @Test void onceAndLockedStatesRemainDistinct() {
        var once=quest("once",List.of());
        assertEquals("completed",QuestPresentation.status(once,json("{completed:true,unlocked:true}")));
        assertEquals("locked",QuestPresentation.status(once,json("{unlocked:false,counters:{}}")));
        assertEquals("available",QuestPresentation.status(once,json("{unlocked:true,counters:{}}")));
        assertEquals("reward_ready",QuestPresentation.status(once,json("{completed:true,terminal:true,claimable:true,unlocked:true}")));
        assertEquals("completed",QuestPresentation.status(once,json("{completed:true,terminal:true,claimable:false,unlocked:true}")));
    }
}
