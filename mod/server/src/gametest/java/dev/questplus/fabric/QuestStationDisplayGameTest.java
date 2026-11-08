package dev.questplus.fabric;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import java.nio.file.Files;

public final class QuestStationDisplayGameTest {
    @GameTest(maxTicks=1200)
    public void displayTracksSelectedObjectiveAndClearsRemovedQuest(GameTestHelper h) throws Exception {
        var path=QuestWorldBlocksGameTest.fixture(h,"display","""
                {id:'stone',event:'item_turn_in',target:'minecraft:stone',count:8},
                {id:'dirt',event:'item_turn_in',target:'minecraft:dirt',count:4}
                """);
        var actor=QuestWorldBlocksGameTest.player(h);var level=h.getLevel();var rt=QuestPlusMod.runtime();
        var pos=h.absolutePos(new BlockPos(2,1,2));level.setBlock(pos,QuestWorldBlocks.STATION.defaultBlockState(),3);
        var be=(QuestUtilityEntity)level.getBlockEntity(pos);be.claim(actor.getUUID());be.serverTick();
        be.configure(actor,QuestWorldBlocksGameTest.config("questplus:blocks_display","stone"),null,0);
        rt.setObjectiveProgress(actor,"questplus:blocks_display","stone",4);be.refresh();
        h.assertTrue(be.rows.size()==2,"Quest and selected objective have separate display rows");
        h.assertTrue(be.rows.get(0).fraction()==.25,"Quest percentage includes both objectives");
        h.assertTrue(be.rows.get(1).fraction()==.5 && be.rows.get(1).count().equals("4 / 8"),"Selected objective shows exact counts");
        var tag=be.getUpdateTag(level.registryAccess());
        h.assertTrue(be.stationQuestId().equals("questplus:blocks_display")&&tag.getString("config").orElseThrow().contains(be.stationQuestId()),"Quest identity is synced for client localization");
        h.assertTrue(tag.getString("boardRows").orElseThrow().contains("4 / 8"),"Watching clients receive display updates");
        h.assertTrue(!tag.contains("item")&&!tag.contains("claims")&&!tag.contains("owner"),"Display sync excludes stored resources and private progress records");
        rt.setObjectiveProgress(actor,"questplus:blocks_display","stone",8);be.refresh();
        h.assertTrue(be.rows.get(1).fraction()==1 && be.rows.get(0).fraction()==.5,"Completing one objective does not complete the quest display");
        Files.delete(path);rt.reload();be.refresh();
        h.assertTrue(be.rows.isEmpty(),"Removing the quest clears stale screen content");h.succeed();
    }
}
