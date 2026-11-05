package dev.questplus.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StudioRewardRoundtripTest {
    @Test void actualStudioJson5ExportLoadsAndResolvesInTheServer(@TempDir Path root) throws Exception {
        Files.copy(Path.of("server/src/test/resources/studio-rewards.json5"),root.resolve("studio.json5"));
        var references=new HashSet<String>();
        var registry=QuestRegistry.load(root,(kind,id)->references.add(kind+":"+id));
        var quest=registry.get("questplus:studio_roundtrip");
        assertEquals(6,quest.rewards().size());
        assertEquals("tiered",quest.progression());
        assertEquals(1,quest.tiers().size());
        assertTrue(references.contains("item:minecraft:diamond"));
        assertTrue(references.contains("advancement_complete:minecraft:story/mine_stone"));
        var resolved=RewardTables.resolve(quest.rewards());
        assertTrue(resolved.stream().noneMatch(r->Set.of("questplus:random","questplus:loot","questplus:all").contains(r.type())));
        assertEquals("item",RewardTables.choose(resolved.getFirst(),0).type());
        resolved.forEach(QuestExtensions::validateSavedReward);
    }
}
