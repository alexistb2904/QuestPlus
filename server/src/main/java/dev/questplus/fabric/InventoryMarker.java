package dev.questplus.fabric;

import dev.questplus.fabric.mixin.PlayerListSaveInvoker;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

final class InventoryMarker {
    private static final String PREFIX="questplus.inventory.";
    private final MinecraftServer server;
    private final Path directory;
    InventoryMarker(MinecraftServer server) { this.server=server; directory=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR); }
    private static String marker(Collection<String> tags) throws IOException {
        var values=tags.stream().filter(tag->tag.startsWith(PREFIX)).toList();
        if(values.size()>1)throw new IOException("Multiple inventory markers");
        if(values.isEmpty())return "";
        String value=values.getFirst().substring(PREFIX.length());
        try { UUID.fromString(value); } catch(IllegalArgumentException error) { throw new IOException("Invalid inventory marker",error); }
        return value;
    }
    String saved(UUID player) throws IOException {
        Path file=directory.resolve(player+".dat");
        var nbt=NbtIo.readCompressed(file,NbtAccounter.create(32*1024*1024));
        var tags=new ArrayList<String>();
        for(var tag:nbt.getListOrEmpty("Tags")) tags.add(tag.asString().orElseThrow(()->new IOException("Invalid player tag")));
        return marker(tags);
    }
    String live(ServerPlayer player) throws IOException { return marker(player.entityTags()); }
    void set(ServerPlayer player,String previous,UUID operation) throws IOException {
        if(!live(player).equals(previous))throw new IOException("Inventory marker changed");
        if(!previous.isEmpty() && !player.removeTag(PREFIX+previous))throw new IOException("Cannot remove old marker");
        if(!player.addTag(PREFIX+operation))throw new IOException("Cannot add inventory marker");
    }
    // TODO(Codex P0): compare every saved inventory component and inject serialization/power-loss faults; marker readback alone cannot prove those boundaries.
    void save(ServerPlayer player,String expected) throws IOException {
        ((PlayerListSaveInvoker)server.getPlayerList()).questplus$playerIo().save(player);
        Path file=directory.resolve(player.getUUID()+".dat");
        try(var channel=FileChannel.open(file,StandardOpenOption.WRITE)) { channel.force(true); }
        if(!saved(player.getUUID()).equals(expected))throw new IOException("Player save marker did not persist");
    }
}
