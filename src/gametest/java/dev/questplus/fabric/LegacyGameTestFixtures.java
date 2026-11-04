package dev.questplus.fabric;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

/** Install legacy examples explicitly for integration tests only, never real worlds. */
final class LegacyGameTestFixtures {
    private LegacyGameTestFixtures() {}
    static void install(MinecraftServer server) throws IOException {
        Path root=server.getWorldPath(LevelResource.ROOT).resolve("questplus");
        for(String category:List.of("combat","exploration","mining","survival")){
            write(root,"categories/"+category+".json");
        }
        for(String path:List.of(
            "quests/survival/logs.json","quests/survival/welcome.json","quests/survival/composite_sequence.json",
            "quests/mining/stone.json","quests/mining/stone_infinite.json","quests/mining/stone_milestones.json","quests/mining/iron_ore.json",
            "quests/combat/skeletons.json","quests/combat/skeleton_repeat.json","quests/combat/iron_and_zombie.json","quests/combat/wither.json",
            "quests/exploration/diamond.json","quests/exploration/three_realms.json")){
            write(root,path);
        }
    }
    private static void write(Path root,String relative) throws IOException {
        var target=root.resolve(relative);
        if(Files.exists(target))return;
        try(var stream=LegacyGameTestFixtures.class.getResourceAsStream("/defaults/"+relative)){
            if(stream==null)throw new IOException("Missing test fixture "+relative);
            Files.createDirectories(target.getParent());
            Files.copy(stream,target);
        }
    }
}
