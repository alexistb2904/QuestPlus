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
                int permission=data.has("permissionLevel")?data.get("permissionLevel").getAsInt():2;
                var source=player.createCommandSourceStack().withPermission(switch(permission){case 0->net.minecraft.server.permissions.PermissionSet.NO_PERMISSIONS;case 1->LevelBasedPermissionSet.MODERATOR;default->LevelBasedPermissionSet.GAMEMASTER;});
                if(!data.has("silent")||data.get("silent").getAsBoolean())source=source.withSuppressedOutput();
                player.level().getServer().getCommands().performPrefixedCommand(source,command);
            });
    }
    static void validate(JsonObject data){
        if(!Set.of("command","permissionLevel","silent").containsAll(data.keySet())
            ||!data.has("command")||!data.get("command").isJsonPrimitive()
            ||!data.get("command").getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Command reward requires one command string");
        if(data.has("permissionLevel")&&(!data.get("permissionLevel").isJsonPrimitive()||!data.getAsJsonPrimitive("permissionLevel").isNumber()
            ||data.get("permissionLevel").getAsBigDecimal().intValueExact()<0||data.get("permissionLevel").getAsInt()>2))throw new IllegalArgumentException("Command permission must be 0..2");
        if(data.has("silent")&&(!data.get("silent").isJsonPrimitive()||!data.getAsJsonPrimitive("silent").isBoolean()))throw new IllegalArgumentException("Invalid command output setting");
        String cmd=data.get("command").getAsString();
        if(cmd.length()<1||cmd.length()>240||cmd.isBlank()||cmd.contains("\n")||cmd.contains("\r")
            ||cmd.indexOf((char)0)>=0||cmd.startsWith("//"))
            throw new IllegalArgumentException("Invalid command reward");
    }
}
