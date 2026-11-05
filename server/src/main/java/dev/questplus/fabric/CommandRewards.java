package dev.questplus.fabric;

import com.google.gson.*;
import dev.questplus.core.QuestDefinition;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import java.util.Set;

/**
 * Trusted-pack-only command rewards.
 * Disabled by default, enabled explicitly in the world questplus/config.json.
 * Commands are never received from client actions and never interpolate player text.
 * Executes as the claiming player with at most GAMEMASTER permissions, not OWNER.
 */
final class CommandRewards {
    private CommandRewards() {}
    static void install(){
        QuestRewardExtensions.register("questplus:command",CommandRewards::validate,
            (player,reward)->QuestPlusMod.runtime().commandRewardsEnabled(),
            (player,reward)->{
                if(!QuestPlusMod.runtime().commandRewardsEnabled())
                    throw new IllegalStateException("Command rewards disabled by world configuration");
                JsonObject data=JsonParser.parseString(reward.data()).getAsJsonObject();
                validate(data);
                String command=data.get("command").getAsString();
                if(command.startsWith("/"))command=command.substring(1);
                var source=player.createCommandSourceStack()
                    .withPermission(LevelBasedPermissionSet.GAMEMASTER)
                    .withSuppressedOutput();
                player.level().getServer().getCommands().performPrefixedCommand(source,command);
            });
    }
    static void validate(JsonObject data){
        if(!Set.of("command").containsAll(data.keySet())||data.size()!=1
            ||!data.has("command")||!data.get("command").isJsonPrimitive()
            ||!data.get("command").getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Command reward requires one command string");
        String cmd=data.get("command").getAsString();
        if(cmd.length()<1||cmd.length()>240||cmd.isBlank()||cmd.contains("\n")||cmd.contains("\r")
            ||cmd.indexOf((char)0)>=0||cmd.startsWith("//"))
            throw new IllegalArgumentException("Invalid command reward");
    }
}
