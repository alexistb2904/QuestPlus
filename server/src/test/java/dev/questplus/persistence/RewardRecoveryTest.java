package dev.questplus.persistence;

import dev.questplus.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RewardRecoveryTest {
    @TempDir Path root;
    @Test void interruptedChoiceWriteRecoversConcreteSelectionWithoutRerollOrReplay() throws Exception {
        UUID id=UUID.randomUUID();
        var choice=new QuestDefinition.Reward("questplus:choice",null,0,0,
            "{\"options\":[{\"type\":\"experience\",\"amount\":7},{\"type\":\"experience_levels\",\"amount\":2}]}",Map.of());
        var random=new QuestDefinition.Reward("questplus:random",null,0,0,
            "{\"rolls\":8,\"options\":[{\"type\":\"experience\",\"amount\":1,\"weight\":1},{\"type\":\"experience\",\"amount\":2,\"weight\":1}]}",Map.of());
        var state=new PlayerProgress();
        var grant=new PlayerProgress.RewardGrant("questplus:test",RewardTables.resolve(List.of(choice,random)));
        state.entitlements.put("cycle",grant);
        try(var store=new JsonProgressStore(root)){store.save(id,state).join();}
        var selected=RewardTables.choose(grant,0,1);
        state.entitlements.put("cycle",selected);
        try(var store=new JsonProgressStore(root,point->{if(point.equals("journal"))throw new IOException("Simulated crash");})) {
            assertThrows(java.util.concurrent.CompletionException.class,()->store.transaction(Map.of(id,state),Map.of()).join());
        }
        try(var recovered=new JsonProgressStore(root)) {
            var persisted=recovered.load(id).entitlements.get("cycle");
            assertEquals(selected,persisted);
            assertEquals(grant.rewards().subList(1,9),persisted.rewards().subList(1,9));
            assertThrows(IllegalArgumentException.class,()->RewardTables.choose(persisted,0,0));
        }
    }
}
