package dev.questplus.fabric;

import com.google.gson.*;
import dev.questplus.core.QuestRegistry;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Server-thread only, rollback-safe import/export for Quest Studio documents.
 * Player progress lives under world/data/questplus and is NEVER replaced. */
final class QuestHubFiles {
    private QuestHubFiles(){}
    private static final int MAX_FILES=2000, MAX_TOTAL=12*1024*1024;
    private static boolean allowed(String name) {
        return name.equals("config.json") || name.equals("queststudio-compatibility.json")
            || name.matches("(quests|categories)/[a-z0-9_./-]+\\.json")
                && !Arrays.asList(name.split("/")).contains("..");
    }
    static JsonArray snapshot(Path definitions) throws IOException {
        JsonArray files=new JsonArray();
        for(String folder:List.of("config.json","quests","categories")){
            Path root=definitions.resolve(folder);
            if(!Files.exists(root))continue;
            try(var walk=Files.isDirectory(root)?Files.walk(root):java.util.stream.Stream.of(root)) {
                for(Path entry:walk.filter(Files::isRegularFile).sorted().toList()){
                    if(Files.isSymbolicLink(entry))throw new IOException("Symlink not allowed in quest definitions");
                    String name=definitions.relativize(entry).toString().replace('\\','/');
                    if(!allowed(name))continue;
                    JsonObject doc=new JsonObject();
                    doc.addProperty("name",name);
                    doc.addProperty("content",Files.readString(entry,StandardCharsets.UTF_8));
                    files.add(doc);
                }
            }
        }
        validate(files);
        return files;
    }
    static void validate(JsonArray documents) throws IOException {
        if(documents==null||documents.size()>MAX_FILES)throw new IOException("Invalid file count");
        int size=0;boolean hasConfig=false,hasQuest=false,hasCategory=false;
        Set<String> seen=new HashSet<>();
        for(var element:documents){
            if(!element.isJsonObject())throw new IOException("Invalid document");
            JsonObject doc=element.getAsJsonObject();
            if(!doc.has("name")||!doc.has("content"))throw new IOException("Missing document fields");
            String name=doc.get("name").getAsString(),content=doc.get("content").getAsString();
            if(!allowed(name)||!seen.add(name))throw new IOException("Unsafe or duplicate file: "+name);
            size+=content.getBytes(StandardCharsets.UTF_8).length;
            if(size>MAX_TOTAL)throw new IOException("Quest pack exceeds 12 MiB");
            if(name.equals("config.json"))hasConfig=true;
            if(name.startsWith("quests/"))hasQuest=true;
            if(name.startsWith("categories/"))hasCategory=true;
            try{QuestRegistry.parseStrict(new StringReader(content));}
            catch(RuntimeException ex){throw new IOException("Invalid JSON in "+name,ex);}
        }
        if(!hasConfig||!hasQuest||!hasCategory)throw new IOException("Pack requires config, categories and quests");
    }
    static void apply(Path world,JsonArray documents,QuestRuntime runtime) throws IOException {
        validate(documents);
        Path definitions=world.resolve("questplus");
        Path backups=world.resolve("questplus-hub-backups");
        Files.createDirectories(backups);
        Path stage=Files.createTempDirectory(world,".questplus-hub-stage-");
        boolean moved=false;
        Path backup=backups.resolve(System.currentTimeMillis()+"-"+UUID.randomUUID());
        try{
            copyTree(definitions,stage);
            deleteTree(stage.resolve("quests"));deleteTree(stage.resolve("categories"));
            Files.createDirectories(stage.resolve("quests"));Files.createDirectories(stage.resolve("categories"));
            for(var el:documents){
                JsonObject f=el.getAsJsonObject();String name=f.get("name").getAsString();
                if(name.equals("queststudio-compatibility.json"))continue;
                Path target=stage.resolve(name).normalize();
                if(!target.startsWith(stage))throw new IOException("Invalid destination");
                Files.createDirectories(target.getParent());
                Files.writeString(target,f.get("content").getAsString(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            }
            // Save a complete backup before activating the new definitions.
            move(definitions,backup);moved=true;
            try{
                move(stage,definitions);
                runtime.reload();
            }catch(Exception rejected){
                Path bad=backups.resolve("rejected-"+UUID.randomUUID());
                if(Files.exists(definitions))move(definitions,bad);
                move(backup,definitions);
                try{runtime.reload();}catch(Exception restore){rejected.addSuppressed(restore);}
                throw new IOException("Publication rejected and previous definitions restored: "+rejected.getMessage(),rejected);
            }
        }finally{
            if(Files.exists(stage))deleteTree(stage);
            if(!moved&&Files.exists(backup)&&!Files.exists(definitions))move(backup,definitions);
        }
    }
    private static void move(Path from,Path to) throws IOException {
        try{Files.move(from,to,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException e){Files.move(from,to);}
    }
    private static void copyTree(Path from,Path to) throws IOException{
        if(!Files.exists(from))throw new IOException("Missing definitions");
        try(var walk=Files.walk(from)){
            for(Path entry:walk.toList()){
                if(Files.isSymbolicLink(entry))throw new IOException("Symlinks are not allowed");
                Path dest=to.resolve(from.relativize(entry));
                if(Files.isDirectory(entry))Files.createDirectories(dest);
                else Files.copy(entry,dest);
            }
        }
    }
    private static void deleteTree(Path root) throws IOException{
        if(!Files.exists(root))return;
        try(var stream=Files.walk(root)){
            for(Path entry:stream.sorted(Comparator.reverseOrder()).toList())Files.delete(entry);
        }
    }
}
