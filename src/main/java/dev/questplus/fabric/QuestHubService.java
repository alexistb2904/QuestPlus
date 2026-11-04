package dev.questplus.fabric;

import com.google.gson.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/** Opt-in outbound-only integration with a self-hosted QuestPlus Hub.
 * Credentials never enter the Minecraft play packets or browser. */
final class QuestHubService implements AutoCloseable {
    private static final Gson JSON=new Gson();
    private final MinecraftServer server;
    private final Path world;
    private final HttpClient http;
    private URI origin;
    private String apiKey,session,revisionSignature,lastPending,lastJobId;
    private UUID owner;
    private long lastPoll;
    private boolean inFlight,closed;
    private JsonObject pendingApproval,lastAck;
    private final Map<UUID,PendingImport> imports=new HashMap<>();
    private record PendingImport(String id,JsonArray files,long expires){}
    QuestHubService(MinecraftServer server) {
        this.server=server;
        world=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).followRedirects(HttpClient.Redirect.NEVER).build();
        Path config=world.resolve("questplus-hub.json");
        try {
            if(!Files.exists(config)){
                QuestPlusMod.LOGGER.info("QuestPlus Hub is disabled. Configure world/questplus-hub.json to enable.");
                return;
            }
            JsonObject cfg=Defaults.readObject(config);
            String url=cfg.get("url").getAsString();
            apiKey=cfg.get("serverKey").getAsString();
            if(apiKey.length()<32)throw new IllegalArgumentException("Hub key must contain at least 32 characters");
            origin=URI.create(url);
            if(origin.getUserInfo()!=null||origin.getQuery()!=null||origin.getFragment()!=null||origin.getPath()!=null&&!origin.getPath().equals("")&&!origin.getPath().equals("/"))
                throw new IllegalArgumentException("Hub URL must be its origin only");
            boolean local=origin.getHost()!=null&&Set.of("localhost","127.0.0.1").contains(origin.getHost());
            if(!"https".equalsIgnoreCase(origin.getScheme())&&!(local&&"http".equalsIgnoreCase(origin.getScheme())))
                throw new IllegalArgumentException("QuestPlus Hub requires HTTPS (except localhost)");
            QuestPlusMod.LOGGER.info("QuestPlus Hub configured at {}",origin);
        }catch(Exception error){
            origin=null;apiKey=null;
            QuestPlusMod.LOGGER.error("Invalid QuestPlus Hub configuration",error);
        }
    }
    boolean configured(){return origin!=null&&!closed;}
    private String endpoint(String p){return origin.toString().replaceAll("/$","")+p;}
    private void notify(CommandSourceStack source,String message){
        source.sendSuccess(()->Component.literal("[QuestPlus Hub] "+message),false);
    }
    private void fail(CommandSourceStack source,String message){
        source.sendFailure(Component.literal("[QuestPlus Hub] "+message));
    }
    private void messageOwner(String message){
        ServerPlayer player=owner==null?null:server.getPlayerList().getPlayer(owner);
        if(player!=null)player.sendSystemMessage(Component.literal("[QuestPlus Hub] "+message));
    }
    private String signature(JsonArray documents) {
        try{
            MessageDigest hash=MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(hash.digest(JSON.toJson(documents).getBytes(StandardCharsets.UTF_8)));
        }catch(Exception ex){throw new IllegalStateException(ex);}
    }
    private JsonObject registry(){
        JsonObject result=new JsonObject();
        addIds(result,"items",BuiltInRegistries.ITEM.keySet());
        addIds(result,"blocks",BuiltInRegistries.BLOCK.keySet());
        addIds(result,"entities",BuiltInRegistries.ENTITY_TYPE.keySet());
        addIds(result,"effects",BuiltInRegistries.MOB_EFFECT.keySet());
        addIds(result,"enchantments",server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).keySet());
        addIds(result,"biomes",server.registryAccess().lookupOrThrow(Registries.BIOME).keySet());
        addIds(result,"structures",server.registryAccess().lookupOrThrow(Registries.STRUCTURE).keySet());
        addIds(result,"dimensions",server.levelKeys().stream().map(k->k.identifier()).toList());
        JsonObject tags=new JsonObject();
        tagIds(tags,"items",BuiltInRegistries.ITEM);
        tagIds(tags,"blocks",BuiltInRegistries.BLOCK);
        tagIds(tags,"entities",BuiltInRegistries.ENTITY_TYPE);
        result.add("tags",tags);
        return result;
    }
    private void addIds(JsonObject output,String name,Collection<?> ids){
        JsonArray a=new JsonArray();ids.stream().map(Object::toString).sorted().forEach(a::add);output.add(name,a);
    }
    private <T> void tagIds(JsonObject output,String name,Registry<T> registry){
        JsonArray a=new JsonArray();
        registry.getTags().map(tag->"#"+tag.key().location().toString()).sorted().forEach(a::add);
        output.add(name,a);
    }
    private void request(String method,String path,JsonObject data,Consumer<JsonObject> onSuccess,Consumer<String> onError){
        if(!configured()){onError.accept("Hub not configured");return;}
        HttpRequest.Builder builder=HttpRequest.newBuilder(URI.create(endpoint(path)))
            .timeout(Duration.ofSeconds(18)).header("Authorization","Bearer "+apiKey).header("Accept","application/json");
        if(data!=null)builder.header("Content-Type","application/json; charset=utf-8");
        HttpRequest req=builder.method(method,data==null?HttpRequest.BodyPublishers.noBody():
            HttpRequest.BodyPublishers.ofString(JSON.toJson(data),StandardCharsets.UTF_8)).build();
        http.sendAsync(req,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            .whenComplete((response,error)->server.execute(()->{
                if(closed)return;
                if(error!=null){onError.accept(error.getCause()==null?error.toString():error.getCause().toString());return;}
                try{
                    JsonObject body=JsonParser.parseString(response.body()).getAsJsonObject();
                    if(response.statusCode()/100!=2){onError.accept(body.has("error")?body.get("error").getAsString():"HTTP "+response.statusCode());return;}
                    onSuccess.accept(body);
                }catch(Exception e){onError.accept("Invalid Hub response: "+e.getMessage());}
            }));
    }
    void createSession(CommandSourceStack source){
        if(!configured()){fail(source,"Configure world/questplus-hub.json (HTTPS URL and server key) first");return;}
        ServerPlayer player;
        try{player=source.getPlayerOrException();}catch(Exception e){fail(source,"Open the editor as an in-game operator");return;}
        try{
            JsonArray files=QuestHubFiles.snapshot(world.resolve("questplus"));
            JsonObject payload=new JsonObject();
            payload.addProperty("serverName",server.getServerMotd());
            payload.add("files",files);payload.add("registry",registry());
            String localSignature=signature(files);
            UUID actor=player.getUUID();
            request("POST","/v1/server/sessions",payload,result->{
                owner=actor;session=result.get("id").getAsString();lastPending=null;pendingApproval=null;
                lastJobId=null;lastAck=null;revisionSignature=localSignature;lastPoll=0;
                String url=result.get("url").getAsString();
                messageOwner("Editor link (requires in-game approval): "+url);
            },e->fail(source,"Could not create session: "+e));
            notify(source,"Creating a secured editor session…");
        }catch(Exception error){fail(source,error.getMessage());}
    }
    void approve(CommandSourceStack source,String code) {
        if(session==null||pendingApproval==null||!pendingApproval.get("code").getAsString().equals(code)){
            fail(source,"No matching pending browser request");return;
        }
        try{if(!source.getPlayerOrException().getUUID().equals(owner)){fail(source,"Only the session owner can approve");return;}}
        catch(Exception e){fail(source,"Approval requires the original in-game operator");return;}
        JsonObject payload=new JsonObject();payload.addProperty("session",session);
        payload.addProperty("id",pendingApproval.get("id").getAsString());payload.addProperty("code",code);
        request("POST","/v1/server/approve",payload,result->{
            pendingApproval=null;messageOwner("Browser authorized. Saving in Quest Studio will publish to this server.");
        },e->fail(source,"Approval denied: "+e));
    }
    void revoke(CommandSourceStack source){
        if(session==null){notify(source,"No active session");return;}
        String id=session;session=null;pendingApproval=null;lastAck=null;lastJobId=null;
        JsonObject payload=new JsonObject();payload.addProperty("session",id);
        request("POST","/v1/server/revoke",payload,result->notify(source,"Session revoked"),e->fail(source,e));
    }
    void tick(){
        if(!configured()||session==null||inFlight||System.currentTimeMillis()-lastPoll<2500)return;
        lastPoll=System.currentTimeMillis();inFlight=true;
        String id=session;
        request("GET","/v1/server/poll?session="+id,null,data->{
            inFlight=false;if(!Objects.equals(id,session))return;
            JsonElement approval=data.get("pending");
            if(approval!=null&&approval.isJsonObject()){
                JsonObject p=approval.getAsJsonObject();
                String pendingId=p.get("id").getAsString();
                if(!pendingId.equals(lastPending)){
                    lastPending=pendingId;pendingApproval=p;
                    messageOwner("Browser approval requested. Compare the code in Quest Studio: "+p.get("code").getAsString());
                    messageOwner("Authorize: /questplus editor approve "+p.get("code").getAsString());
                }
            }
            JsonElement publish=data.get("job");
            if(publish!=null&&publish.isJsonObject()){
                JsonObject job=publish.getAsJsonObject();
                String jobId=job.get("id").getAsString();
                if(!jobId.equals(lastJobId)){
                    lastJobId=jobId;
                    lastAck=new JsonObject();lastAck.addProperty("session",session);lastAck.addProperty("id",jobId);
                    try{
                        JsonArray actual=QuestHubFiles.snapshot(world.resolve("questplus"));
                        if(!signature(actual).equals(revisionSignature))throw new IllegalStateException("Local quest files changed; open a new editor session");
                        QuestHubFiles.apply(world,job.getAsJsonArray("files"),QuestPlusMod.runtime());
                        revisionSignature=signature(QuestHubFiles.snapshot(world.resolve("questplus")));
                        lastAck.addProperty("success",true);
                        messageOwner("Quests published successfully; online players received an updated snapshot.");
                    }catch(Exception e){
                        lastAck.addProperty("success",false);lastAck.addProperty("error",e.getMessage());
                        messageOwner("Publication rejected, previous quests retained: "+e.getMessage());
                    }
                }
            }
            if(lastAck!=null)sendAck();
        },e->{inFlight=false;if(e.contains("expired")){session=null;messageOwner("Editor session expired");}else QuestPlusMod.LOGGER.warn("QuestPlus Hub poll failed: {}",e);});
    }
    private void sendAck(){
        if(lastAck==null)return;JsonObject ack=lastAck.deepCopy();
        request("POST","/v1/server/ack",ack,result->{
            if(lastAck!=null&&lastAck.get("id").getAsString().equals(ack.get("id").getAsString()))lastAck=null;
        },e->QuestPlusMod.LOGGER.warn("QuestPlus Hub acknowledgement retry pending: {}",e));
    }
    void previewImport(CommandSourceStack source,String packId) {
        if(!configured()){fail(source,"Hub is not configured");return;}
        if(!packId.matches("[A-Za-z0-9_-]{32}")){fail(source,"Invalid pack ID");return;}
        UUID id;
        try{id=source.getPlayerOrException().getUUID();}catch(Exception ex){fail(source,"Import requires an in-game operator");return;}
        request("GET","/v1/server/packs/"+packId,null,result->{
            try{
                JsonArray files=result.getAsJsonArray("files");QuestHubFiles.validate(files);
                long quests=0,categories=0;for(var el:files){String name=el.getAsJsonObject().get("name").getAsString();if(name.startsWith("quests/"))quests++;if(name.startsWith("categories/"))categories++;}
                imports.put(id,new PendingImport(packId,files,System.currentTimeMillis()+120_000));
                notify(source,"Pack "+packId+": "+quests+" quests, "+categories+" categories. Confirm within 2 minutes: /questplus import confirm");
            }catch(Exception e){fail(source,"Pack rejected: "+e.getMessage());}
        },e->fail(source,"Import failed: "+e));
    }
    void confirmImport(CommandSourceStack source){
        UUID id;
        try{id=source.getPlayerOrException().getUUID();}catch(Exception e){fail(source,"Requires an operator");return;}
        PendingImport pending=imports.remove(id);
        if(pending==null||pending.expires()<System.currentTimeMillis()){fail(source,"No pending import or approval expired");return;}
        try{
            QuestHubFiles.apply(world,pending.files(),QuestPlusMod.runtime());
            session=null;pendingApproval=null;lastAck=null;
            notify(source,"Pack "+pending.id()+" installed. Multiplayer clients have been resynchronized.");
        }catch(Exception e){fail(source,"Pack rejected, existing quests restored: "+e.getMessage());}
    }
    @Override public void close(){
        closed=true;session=null;imports.clear();pendingApproval=null;lastAck=null;
    }
}
