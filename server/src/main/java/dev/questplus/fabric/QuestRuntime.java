package dev.questplus.fabric;

import com.google.gson.JsonArray;

import dev.questplus.core.*;
import dev.questplus.clientdata.ClientProjection;
import dev.questplus.persistence.JsonProgressStore;
import dev.questplus.team.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Mutable state belongs to the Minecraft server thread; workers only receive serialized snapshots. */
public final class QuestRuntime implements AutoCloseable {
    private final MinecraftServer server;
    private final Path definitions;
    private final JsonProgressStore store;
    private final Map<UUID,PlayerProgress> players = new HashMap<>();
    private final Set<UUID> dirty = new HashSet<>();
    private final Set<UUID> blocked = new HashSet<>();
    private final Set<UUID> claiming = new HashSet<>();
    private final Set<UUID> busy = new HashSet<>();
    private final Set<UUID> dirtyTeams = new HashSet<>();
    private record DeferredEvent(ServerPlayer player,QuestEvent event) {}
    private final ArrayDeque<DeferredEvent> deferred = new ArrayDeque<>();
    private final List<TeamWrite> pendingTeams = new ArrayList<>();
    private static final class TeamWrite {
        final TeamManager.Change change; final CompletableFuture<Void> io; boolean applied;
        TeamWrite(TeamManager.Change change,CompletableFuture<Void> io) { this.change=change; this.io=io; }
    }
    private TeamManager teams;
    private boolean storageFailed;
    private String teamRewardPolicy="all_members";
    private QuestRegistry registry;
    private List<ClientProjection.Category> categories = List.of();
    private long catalogGeneration;
    private QuestEngine engine;
    private int autosaveTicks;
    private int ticks;
    private boolean closing;

    public QuestRuntime(MinecraftServer server) throws IOException {
        this.server = server;
        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        definitions = world.resolve("questplus");
        Defaults.install(definitions);
        store = new JsonProgressStore(world.resolve("data/questplus"));
        try { reload(); teams=new TeamManager(store,registry,players); } catch (IOException | RuntimeException e) { store.close(); throw e; }
    }

    public void reload() throws IOException {
        assertThread();
        var config = Defaults.readObject(definitions.resolve("config.json"));
        if (!Set.of("schemaVersion","autosaveSeconds","teamRewardPolicy").containsAll(config.keySet()) || !config.has("schemaVersion") || !config.has("autosaveSeconds")
                || !config.get("schemaVersion").isJsonPrimitive() || !config.get("schemaVersion").getAsJsonPrimitive().isNumber()
                || config.get("schemaVersion").getAsBigDecimal().intValueExact() != 1)
            throw new IllegalArgumentException("Unsupported config.json");
        if (!config.get("autosaveSeconds").isJsonPrimitive() || !config.get("autosaveSeconds").getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("autosaveSeconds must be an integer");
        int seconds = config.get("autosaveSeconds").getAsBigDecimal().intValueExact();
        if (seconds < 1 || seconds > 300) throw new IllegalArgumentException("autosaveSeconds must be 1..300");
        String policy=config.has("teamRewardPolicy") ? config.get("teamRewardPolicy").getAsString() : "all_members";
        if (!Set.of("all_members","contributor").contains(policy)) throw new IllegalArgumentException("Invalid teamRewardPolicy");
        QuestRegistry candidate = QuestRegistry.load(definitions.resolve("quests"), this::validateRegistryReference);
        Set<String> categoryIds = new HashSet<>();
        List<ClientProjection.Category> candidateCategories = new ArrayList<>();
        try (var paths = Files.walk(definitions.resolve("categories"))) {
            for (Path path : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).toList()) {
                var category = Defaults.readObject(path);
                if (!Set.of("id","title","description","icon","annotations","order","images","linkRoutes").containsAll(category.keySet()) || !category.keySet().containsAll(Set.of("id","title","description")))
                    throw new IllegalArgumentException("Invalid category " + path);
                String id = category.get("id").getAsString();
                if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || !categoryIds.add(id)) throw new IllegalArgumentException("Invalid/duplicate category " + id);
                for (String field : List.of("title","description")) {
                    var values = category.getAsJsonObject(field);
                    if (!values.has("en_us")) throw new IllegalArgumentException("Missing category en_us: " + path);
                    for (var value : values.entrySet()) if (!value.getValue().isJsonPrimitive()
                            || !value.getValue().getAsJsonPrimitive().isString() || value.getValue().getAsString().isBlank())
                        throw new IllegalArgumentException("Invalid category translation: " + path);
                }
                if (category.has("icon") && (!category.get("icon").isJsonPrimitive() || !category.get("icon").getAsJsonPrimitive().isString()
                        || !category.get("icon").getAsString().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")))
                    throw new IllegalArgumentException("Invalid category icon: " + path);
                int order=Integer.MAX_VALUE;
                if (category.has("order")) {
                    try {
                        if (!category.get("order").isJsonPrimitive() || !category.get("order").getAsJsonPrimitive().isNumber())
                            throw new IllegalArgumentException("Not a number");
                        order=category.get("order").getAsBigDecimal().intValueExact();
                        if (order<0 || order>1_000_000) throw new IllegalArgumentException("Outside range");
                    } catch (RuntimeException invalid) {
                        throw new IllegalArgumentException("Invalid category order in "+path+" (expected integer 0..1000000)",invalid);
                    }
                }
                String icon=category.has("icon") ? category.get("icon").getAsString() : "minecraft:book";
                validateRegistryReference("icon",icon);
                Map<String,String> title=new HashMap<>(),description=new HashMap<>();
                category.getAsJsonObject("title").entrySet().forEach(e -> title.put(e.getKey(),e.getValue().getAsString()));
                category.getAsJsonObject("description").entrySet().forEach(e -> description.put(e.getKey(),e.getValue().getAsString()));
                List<String> images=new ArrayList<>();
                if(category.has("images")){
                    if(!category.get("images").isJsonArray())
                        throw new IllegalArgumentException("Invalid category images: "+path);
                    for(var item:category.getAsJsonArray("images")){
                        if(!item.isJsonPrimitive()||!item.getAsJsonPrimitive().isString())
                            throw new IllegalArgumentException("Invalid pooled image: "+path);
                        String data=item.getAsString();
                        if(!data.matches("data:image/(png|jpeg|gif);base64,[A-Za-z0-9+/]+={0,2}"))
                            throw new IllegalArgumentException("Invalid pooled image: "+path);
                        images.add(data);
                    }
                }
                List<ClientProjection.Annotation> annotations=new ArrayList<>();
                if (category.has("annotations")) {
                    if (!category.get("annotations").isJsonArray())
                        throw new IllegalArgumentException("Invalid annotations in "+path);
                    Set<String> annotationIds=new HashSet<>();
                    for (var element:category.getAsJsonArray("annotations")) {
                        if (!element.isJsonObject()) throw new IllegalArgumentException("Invalid annotation in "+path);
                        var note=element.getAsJsonObject();
                        if (!Set.of("id","x","y","text","style","color","rich","width","height").containsAll(note.keySet())
                                || !note.keySet().containsAll(Set.of("id","x","y","text")))
                            throw new IllegalArgumentException("Invalid annotation fields in "+path);
                        String aid=note.get("id").getAsString();
                        if (!aid.matches("[a-z0-9_.-]+") || !annotationIds.add(aid))
                            throw new IllegalArgumentException("Invalid annotation ID: "+aid);
                        double x=note.get("x").getAsDouble(),y=note.get("y").getAsDouble();
                        if (!Double.isFinite(x) || !Double.isFinite(y) || Math.abs(x)>100000 || Math.abs(y)>100000)
                            throw new IllegalArgumentException("Invalid annotation position: "+aid);
                        Integer width=note.has("width") ? note.get("width").getAsInt() : null;
                        Integer height=note.has("height") ? note.get("height").getAsInt() : null;
                        if(width!=null&&(width<=0)||height!=null&&(height<=0))
                            throw new IllegalArgumentException("Annotation dimensions must be positive: "+aid);
                        String style=note.has("style") ? note.get("style").getAsString() : "note";
                        String color=note.has("color") ? note.get("color").getAsString() : "#c6a986";
                        if (!Set.of("note","heading","label","background").contains(style) || !color.matches("#[0-9a-fA-F]{6}"))
                            throw new IllegalArgumentException("Invalid annotation style/color: "+aid);
                        if (style.equals("background")) ClientProjection.validateBackground(note,aid);
                        if (!note.get("text").isJsonObject())
                            throw new IllegalArgumentException("Annotation text must be a locale map: "+aid);
                        Map<String,String> noteText=new HashMap<>();
                        for (var entry:note.getAsJsonObject("text").entrySet()) {
                            if (!entry.getKey().matches("[a-z]{2,3}_[a-z]{2,4}") || !entry.getValue().isJsonPrimitive()
                                || !entry.getValue().getAsJsonPrimitive().isString())
                                throw new IllegalArgumentException("Invalid annotation language/text: "+aid);
                            if (!entry.getValue().getAsString().isBlank())
                                noteText.put(entry.getKey(),entry.getValue().getAsString());
                        }
                        Map<String,JsonArray> rich=new HashMap<>();
                        if(note.has("rich")) {
                            if(!note.get("rich").isJsonObject()) throw new IllegalArgumentException("Invalid rich note: "+aid);
                            for(var locale:note.getAsJsonObject("rich").entrySet()) {
                                if(!locale.getKey().matches("[a-z]{2,3}_[a-z]{2,4}") || !locale.getValue().isJsonArray()
                                   )
                                    throw new IllegalArgumentException("Invalid rich language/parts: "+aid);
                                for(var part:locale.getValue().getAsJsonArray()) {
                                    if(!part.isJsonObject()) throw new IllegalArgumentException("Invalid rich part: "+aid);
                                    var item=part.getAsJsonObject();
                                    String type=item.has("type") ? item.get("type").getAsString() : "";
                                    if(type.equals("text")) {
                                        if(!Set.of("type","text","font","size","sizePx","color","highlight","bold","italic","underline","strike").containsAll(item.keySet())
                                                || !item.has("text") || !item.get("text").isJsonPrimitive()
                                                )
                                            throw new IllegalArgumentException("Invalid rich text: "+aid);
                                        if(item.has("font") && !Set.of("default","uniform","alt","illageralt").contains(item.get("font").getAsString()))
                                            throw new IllegalArgumentException("Invalid rich font: "+aid);
                                        if(item.has("size") && (item.get("size").getAsDouble()<0.75 || item.get("size").getAsDouble()>2
                                                || !Double.isFinite(item.get("size").getAsDouble())))
                                            throw new IllegalArgumentException("Invalid rich size: "+aid);
                                        if(item.has("sizePx")&&(item.get("sizePx").getAsInt()<=0))
                                            throw new IllegalArgumentException("Invalid rich text pixel size: "+aid);
                                        for(String key:List.of("bold","italic","underline","strike"))
                                            if(item.has(key) && (!item.get(key).isJsonPrimitive() || !item.get(key).getAsJsonPrimitive().isBoolean()))
                                                throw new IllegalArgumentException("Invalid rich formatting: "+aid);
                                        for(String key:List.of("color","highlight"))
                                            if(item.has(key) && !item.get(key).getAsString().matches("#[0-9a-fA-F]{6}"))
                                                throw new IllegalArgumentException("Invalid rich color: "+aid);
                                    } else if(type.equals("image")) {
                                        if(!Set.of("type","data","ref","width","height").containsAll(item.keySet()) ||
                                                !item.keySet().containsAll(Set.of("width","height")) ||
                                                item.has("data")==item.has("ref"))
                                            throw new IllegalArgumentException("Invalid rich image: "+aid);
                                        if(item.get("width").getAsInt()<=0
                                            || item.get("height").getAsInt()<=0)
                                            throw new IllegalArgumentException("Invalid rich image dimensions: "+aid);
                                        if(item.has("ref")){
                                            if(!item.get("ref").isJsonPrimitive()||!item.get("ref").getAsJsonPrimitive().isNumber())
                                                throw new IllegalArgumentException("Invalid image index: "+aid);
                                            int ref=item.get("ref").getAsInt();
                                            if(ref<0||ref>=images.size())throw new IllegalArgumentException("Missing image reference "+ref+": "+aid);
                                        }else{
                                            String data=item.get("data").getAsString();
                                            if(!data.matches("data:image/(png|jpeg|gif);base64,[A-Za-z0-9+/]+={0,2}"))
                                                throw new IllegalArgumentException("Invalid rich image size/data: "+aid);
                                        }
                                    } else throw new IllegalArgumentException("Unknown rich part: "+aid);
                                }
                                rich.put(locale.getKey(),locale.getValue().getAsJsonArray());
                            }
                        }
                        boolean hasRich=rich.values().stream().anyMatch(parts -> {
                            for (var part:parts) {
                                if (!part.isJsonObject()) continue;
                                var value=part.getAsJsonObject();
                                if ("image".equals(value.get("type").getAsString()) ||
                                    "text".equals(value.get("type").getAsString()) && !value.get("text").getAsString().isBlank()) return true;
                            }
                            return false;
                        });
                        if (!style.equals("background") && noteText.isEmpty() && !hasRich) throw new IllegalArgumentException("Annotation has no translated content: "+aid);
                        annotations.add(new ClientProjection.Annotation(aid,x,y,Map.copyOf(noteText),style,color,Map.copyOf(rich),width,height));
                    }
                }
                List<ClientProjection.LinkRoute> routes=ClientProjection.routes(category.get("linkRoutes"),id,candidate);
                candidateCategories.add(new ClientProjection.Category(id,Map.copyOf(title),Map.copyOf(description),icon,List.copyOf(annotations),order,List.copyOf(images),routes));
            }
        }
        for (var quest : candidate.all()) if (!categoryIds.contains(quest.category())) throw new IllegalArgumentException("Unknown category: " + quest.category());
        registry = candidate;
        categories = candidateCategories.stream().sorted(Comparator.comparingInt(ClientProjection.Category::order).thenComparing(ClientProjection.Category::id)).toList();
        catalogGeneration++;
        engine = new QuestEngine(candidate);
        if (teams!=null) teams.registry(candidate);
        teamRewardPolicy=policy;
        autosaveTicks = seconds * 20;
        QuestNetwork.invalidateAll(server);
    }

    private void validateRegistryReference(String kind, String target) {
        String registryKind=kind.equals("icon") || kind.equals("tool") || kind.equals("item") ? "item"
            : Set.of("dimension","biome","effect").contains(kind) ? kind : QuestExtensions.event(kind).registry();
        if (registryKind.equals("marker")) {
            Identifier.parse(target.startsWith("#") ? target.substring(1) : target);
            if (kind.equals("travel_distance") && !target.equals("questplus:travel") || kind.equals("location") && !target.equals("questplus:position")
                    || kind.equals("player_death") && !target.equals("questplus:death")) throw new IllegalArgumentException("Invalid built-in event target: "+kind+" "+target);
            return;
        }
        if (registryKind.equals("dimension")) {
            if (server.levelKeys().stream().noneMatch(k -> k.identifier().toString().equals(target)))
                throw new IllegalArgumentException("Unknown dimension " + target);
            return;
        }
        Registry<?> values = switch (registryKind) {
            case "block" -> BuiltInRegistries.BLOCK;
            case "entity" -> BuiltInRegistries.ENTITY_TYPE;
            case "effect" -> BuiltInRegistries.MOB_EFFECT;
            case "biome" -> server.registryAccess().lookupOrThrow(Registries.BIOME);
            case "structure" -> server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            default -> BuiltInRegistries.ITEM;
        };
        Identifier id = Identifier.parse(target.startsWith("#") ? target.substring(1) : target);
        if (target.startsWith("#")) {
            if (values.getTags().noneMatch(t -> t.key().location().equals(id))) throw new IllegalArgumentException("Unknown " + kind + " tag " + target);
        } else if (!values.containsKey(id)) {
            if (kind.equals("icon")) QuestPlusMod.LOGGER.warn("Quest Plus: icon {} unavailable on this Minecraft version; using client fallback", target);
            else throw new IllegalArgumentException("Unknown " + kind + " id " + target);
        }
        if ((kind.equals("item") || kind.equals("inventory_count")) && id.equals(Identifier.parse("minecraft:air"))) throw new IllegalArgumentException("AIR cannot be an item reward/inventory objective");
    }

    public QuestRegistry registry() { return registry; }
    public List<ClientProjection.Category> categories() { return categories; }
    public long catalogGeneration() { return catalogGeneration; }
    public QuestEngine engine() { return engine; }
    public List<QuestDefinition> visibleQuests(UUID id) throws IOException {
        return ClientProjection.visible(registry.all(),state(id));
    }
    public QuestDefinition visibleQuest(UUID player,String id) throws IOException {
        return visibleQuests(player).stream().filter(q -> q.id().equals(id)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown quest"));
    }
    public PlayerProgress player(UUID id) throws IOException {
        assertThread();
        if (storageFailed) throw new IOException("Quest Plus storage requires recovery; restart after inspecting the transaction log");
        if (blocked.contains(id)) throw new IOException("Quest Plus save unavailable for " + id + "; inspect server log");
        var value = players.get(id);
        if (value == null) {
            try { value = store.load(id); players.put(id,value); }
            catch (IOException | RuntimeException e) { blocked.add(id); throw e; }
        }
        for (String quest:List.copyOf(value.legacyUnclaimed)) if (registry.find(quest).isPresent()) {
            if (!value.claims.containsKey(quest)) value.entitlements.putIfAbsent(quest,new PlayerProgress.RewardGrant(quest,registry.get(quest).rewards()));
            value.legacyUnclaimed.remove(quest); dirty.add(id);
        }
        return value;
    }
    public TeamManager teams() { return teams; }
    public boolean isBusy(UUID player) { return busy.contains(player); }
    public PlayerProgress state(UUID id) throws IOException {
        var personal=player(id);
        return personal.teamId==null ? personal : teams.get(personal.teamId).progress;
    }
    private Set<UUID> group(UUID id) throws IOException {
        var personal=player(id);
        return personal.teamId==null ? Set.of(id) : Set.copyOf(teams.get(personal.teamId).members);
    }
    private void ensureFree(Collection<UUID> ids) {
        if (closing || storageFailed) throw new IllegalStateException("Quest Plus is stopping or storage is unavailable");
        if (ids.stream().anyMatch(busy::contains)) throw new IllegalStateException("Quest Plus operation is still saving; try again shortly");
    }

    public void event(ServerPlayer player, QuestEvent event) {
        assertThread();
        // Manual acknowledgements are valid even in creative/spectator; only gameplay counters are restricted.
        if (closing || storageFailed || (!event.type().equals("manual") && (player.isSpectator() || player.isCreative()))
            || blocked.contains(player.getUUID())) return;
        if (busy.contains(player.getUUID())) {
            if (deferred.size()>=4096) throw new IllegalStateException("Quest Plus deferred event capacity exceeded");
            deferred.addLast(new DeferredEvent(player,event)); return;
        }
        try {
            UUID uuid=player.getUUID(); PlayerProgress progress = state(uuid);
            if (!event.type().equals("player_death") && registry.candidates(event).isEmpty()) return;
            long revision = progress.revision;
            var completed = engine.applyDetailed(progress,event);
            if (progress.revision != revision) changed(uuid);
            if (!completed.isEmpty()) {
                Set<UUID> recipients=teamRewardPolicy.equals("contributor") ? Set.of(uuid) : group(uuid);
                for (UUID recipient:recipients) for (var award:completed) {
                    var personal=player(recipient);
                    if (!award.rewards().isEmpty() && !personal.claims.containsKey(award.key())) personal.entitlements.putIfAbsent(award.key(),new PlayerProgress.RewardGrant(award.questId(),award.rewards()));
                    dirty.add(recipient);
                }
                Set<UUID> members=group(uuid); Map<UUID,PlayerProgress> snapshots=new HashMap<>();
                for (UUID member:members) snapshots.put(member,player(member));
                var team=player(uuid).teamId;
                persistCurrent(snapshots,team==null ? Map.of() : Map.of(team,teams.get(team)),members);
                for (var award:completed) for (UUID member:members) {
                    var online=server.getPlayerList().getPlayer(member);
                    if (online!=null) online.sendSystemMessage(award.rewards().isEmpty()
                        ? Messages.text(online,"questplus.completed_no_reward",title(online,registry.get(award.questId())))
                        : Messages.text(online,"questplus.completed",title(online,registry.get(award.questId())),award.questId()));
                }
            }
        } catch (IOException | RuntimeException e) {
            QuestPlusMod.LOGGER.error("Quest event rejected for {}", player.getUUID(),e);
        }
    }

    public String title(ServerPlayer player, QuestDefinition quest) {
        return Localization.text(quest.title(), player == null ? "en_us" : player.clientInformation().language(),Map.of());
    }
    public void changed(UUID id) {
        assertThread(); var p=players.get(id);
        if (p!=null && p.teamId!=null) dirtyTeams.add(p.teamId); else dirty.add(id);
        if (p!=null && p.teamId!=null) teams.get(p.teamId).members.forEach(QuestNetwork::invalidate);
        else QuestNetwork.invalidate(id);
    }
    public void tick() {
        if (storageFailed) return;
        int count=Math.min(512,deferred.size());
        for (int i=0;i<count;i++) { var item=deferred.removeFirst(); event(item.player(),item.event()); }
        if (++ticks % autosaveTicks == 0) flushDirty();
    }
    private void flushDirty() {
        Map<UUID,PlayerProgress> snapshots=new HashMap<>();
        for (UUID id:List.copyOf(dirty)) if (!busy.contains(id)) snapshots.put(id,players.get(id));
        if (!snapshots.isEmpty()) {
            dirty.removeAll(snapshots.keySet());
            store.saveBatch(snapshots).whenComplete((ok,error) -> {
                if (error != null) {
                    QuestPlusMod.LOGGER.error("Quest save batch failed for {} UUIDs",snapshots.size(),error);
                    if (!closing) server.execute(() -> dirty.addAll(snapshots.keySet()));
                }
            });
        }
        for (UUID id:List.copyOf(dirtyTeams)) {
            TeamData team=teams.get(id);
            if (team.members.stream().anyMatch(busy::contains)) continue;
            dirtyTeams.remove(id);
            store.transaction(Map.of(),Map.of(id,team)).whenComplete((ok,error) -> {
                if (error!=null) server.execute(() -> failStorage(error));
            });
        }
    }
    private void failStorage(Throwable error) { storageFailed=true; QuestPlusMod.LOGGER.error("Quest Plus storage transaction failed; progress is frozen until recovery",error); }
    private void release(Collection<UUID> ids) { busy.removeAll(ids); }
    private void persistCurrent(Map<UUID,PlayerProgress> personal,Map<UUID,TeamData> collective,Set<UUID> affected) {
        busy.addAll(affected); dirty.removeAll(personal.keySet()); dirtyTeams.removeAll(collective.keySet());
        store.transaction(personal,collective).whenComplete((ok,error) -> server.execute(() -> {
            if (error!=null) failStorage(error);
            release(affected);
        }));
    }
    @FunctionalInterface public interface TeamAction { TeamManager.Change prepare(TeamManager manager) throws IOException; }
    public CompletableFuture<Void> changeTeam(TeamAction action) throws IOException {
        assertThread(); ensureFree(Set.of());
        TeamManager.Change change=action.prepare(teams);
        Set<UUID> affected=new HashSet<>(change.affectedPlayers());
        Set<UUID> notify=new HashSet<>(affected);
        for (var team:change.teams().values()) {
            affected.addAll(team.members);
            notify.addAll(team.invitations.keySet());
            try { var old=teams.get(team.id); affected.addAll(old.members); notify.addAll(old.invitations.keySet()); } catch (IllegalArgumentException newTeam) {}
        }
        notify.addAll(affected);
        ensureFree(affected); busy.addAll(affected);
        var io=store.transaction(change.players(),change.teams());
        var write=new TeamWrite(change,io); pendingTeams.add(write);
        CompletableFuture<Void> result=new CompletableFuture<>();
        io.whenComplete((ok,error) -> server.execute(() -> {
            if (!write.applied && error==null) {
                teams.apply(change); write.applied=true;
                notify.forEach(QuestNetwork::invalidate);
                dirty.removeAll(change.players().keySet()); dirtyTeams.removeAll(change.teams().keySet());
            }
            if (error!=null) { failStorage(error); result.completeExceptionally(error); } else result.complete(null);
            pendingTeams.remove(write); release(affected);
        }));
        return result;
    }
    public void mutate(UUID id,String quest,String action) throws IOException {
        ensureFree(group(id)); var progress=state(id);
        if (action.equals("reset")) engine.reset(progress,quest);
        else if (action.equals("restart")) engine.restart(progress,quest);
        else {
            var award=engine.grantDetailed(progress,quest);
            Set<UUID> members=group(id); Map<UUID,PlayerProgress> snapshots=new HashMap<>();
            for (UUID recipient:members) {
                var p=player(recipient);
                if (!award.rewards().isEmpty() && !p.claims.containsKey(award.key())) p.entitlements.putIfAbsent(award.key(),new PlayerProgress.RewardGrant(quest,award.rewards()));
                snapshots.put(recipient,p);
            }
            var team=player(id).teamId;
            persistCurrent(snapshots,team==null ? Map.of() : Map.of(team,teams.get(team)),members);
        }
        changed(id);
    }
    private CompletableFuture<Void> save(UUID id) { return store.save(id,players.get(id)); }
    public void disconnect(UUID id) {
        if (!players.containsKey(id)) return;
        dirty.add(id);
        // shortcut: keep cached UUID states for this server session; add eviction when large-server profiling warrants it.
    }

    /** One client request, sequential durable reservations. No double claims and
     * no bypass of the existing free-inventory/storage safety checks. */
    public void claimAll(ServerPlayer player) throws IOException {
        assertThread();
        UUID uuid=player.getUUID();
        Set<String> visible=visibleQuests(uuid).stream().map(QuestDefinition::id).collect(java.util.stream.Collectors.toSet());
        var state=player(uuid);
        List<String> ids=state.entitlements.entrySet().stream()
            .filter(e->visible.contains(e.getValue().questId()) && !state.claims.containsKey(e.getKey()))
            .sorted(Map.Entry.comparingByKey()).map(e->e.getValue().questId()).toList();
        if(ids.isEmpty()) { player.sendSystemMessage(Messages.text(player,"questplus.error","No rewards to claim")); return; }
        claimNext(player,ids,0);
    }
    private void claimNext(ServerPlayer player,List<String> ids,int index) throws IOException {
        if(index>=ids.size()){
            player.sendSystemMessage(Messages.text(player,"questplus.claimed",ids.size()+" rewards"));
            return;
        }
        claim(player,ids.get(index),() -> {
            try { claimNext(player,ids,index+1); }
            catch(IOException | RuntimeException error) {
                QuestPlusMod.LOGGER.warn("Claim all stopped at reward {}: {}",index,error.getMessage());
                player.sendSystemMessage(Messages.text(player,"questplus.error","Claim all stopped: "+error.getMessage()));
            }
        });
    }
    public void claim(ServerPlayer player, String id) throws IOException { claim(player,id,null); }
    private void claim(ServerPlayer player, String id,Runnable onDelivered) throws IOException {
        assertThread();
        UUID uuid = player.getUUID();
        ensureFree(group(uuid)); var progress = player(uuid);
        var quest = registry.get(id);
        String key=progress.entitlements.entrySet().stream().filter(e -> e.getValue().questId().equals(id) && !progress.claims.containsKey(e.getKey())).map(Map.Entry::getKey).sorted().findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No unclaimed reward entitlement: " + id));
        var rewards=progress.entitlements.get(key).rewards();
        if (claiming.contains(uuid)) throw new IllegalArgumentException("Reward already reserved; pending claims require administrator review");
        if (!fits(player,rewards)) throw new IllegalArgumentException("Inventory needs more free space");
        progress.claims.put(key,"reserved");
        QuestNetwork.invalidate(uuid);
        Set<UUID> affected=group(uuid); busy.addAll(affected);
        claiming.add(uuid);
        save(uuid).whenComplete((ok,error) -> server.execute(() -> {
            claiming.remove(uuid);
            release(affected);
            if (closing) return;
            if (error != null) {
                dirty.add(uuid);
                QuestPlusMod.LOGGER.error("Reward reservation failed for {} {}",uuid,id,error);
                player.sendSystemMessage(Messages.text(player,"questplus.error","Reward reservation failed; administrator review required"));
                return;
            }
            // A durable reservation is never replayed: JSON and vanilla inventory cannot commit atomically.
            if (server.getPlayerList().getPlayer(uuid) != player || !fits(player,rewards)) {
                player.sendSystemMessage(Messages.text(player,"questplus.error","Reward reserved; disconnected or inventory changed. Administrator review required"));
                return;
            }
            try {
                for (var reward : rewards) {
                    if (reward.type().equals("experience")) player.giveExperiencePoints(reward.amount());
                    else if (reward.type().equals("item")) for (var stack : stacks(reward)) {
                        if (!player.getInventory().add(stack) || !stack.isEmpty()) throw new IllegalStateException("Inventory changed during reward delivery");
                    }
                    else QuestRewardExtensions.deliver(player,reward);
                }
                progress.claims.put(key,"delivered");
                progress.entitlements.remove(key);
                QuestNetwork.invalidate(uuid);
                dirty.add(uuid);
                if(onDelivered==null)player.sendSystemMessage(Messages.text(player,"questplus.claimed",title(player,quest)));
                else onDelivered.run();
            } catch (RuntimeException ex) { QuestPlusMod.LOGGER.error("Reserved reward requires manual recovery for {} {}",uuid,id,ex); }
        }));
    }

    private static List<ItemStack> stacks(QuestDefinition.Reward reward) {
        var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(reward.item()));
        List<ItemStack> stacks = new ArrayList<>();
        int left = reward.count();
        while (left > 0) {
            ItemStack stack = new ItemStack(item);
            int count = Math.min(left,stack.getMaxStackSize());
            stack.setCount(count); stacks.add(stack); left -= count;
        }
        return stacks;
    }
    private static boolean fits(ServerPlayer player,List<QuestDefinition.Reward> rewards) {
        for (var reward:rewards) if (!Set.of("item","experience").contains(reward.type()) && !QuestRewardExtensions.fits(player,reward)) return false;
        List<ItemStack> slots = player.getInventory().getNonEquipmentItems().stream().map(ItemStack::copy).toList();
        var simulated = new ArrayList<>(slots);
        for (var reward : rewards) if (reward.type().equals("item")) for (ItemStack input : stacks(reward)) {
            int left = input.getCount();
            for (int i=0; i<simulated.size() && left>0; i++) {
                ItemStack slot = simulated.get(i);
                if (slot.isEmpty()) { simulated.set(i,input.copyWithCount(left)); left=0; }
                else if (ItemStack.isSameItemSameComponents(slot,input)) {
                    int add = Math.min(left,Math.max(0,slot.getMaxStackSize()-slot.getCount()));
                    slot.grow(add); left-=add;
                }
            }
            if (left>0) return false;
        }
        return true;
    }
    private void assertThread() { if (!server.isSameThread()) throw new IllegalStateException("Quest Plus requires the server thread"); }
    @Override public void close() throws IOException {
        closing = true;
        IOException error=null;
        for (var write:List.copyOf(pendingTeams)) {
            try { write.io.get(30,TimeUnit.SECONDS); if (!write.applied) { teams.apply(write.change); write.applied=true; } }
            catch (Exception e) { error=new IOException("Pending team transaction failed at shutdown",e); }
        }
        try {
            Map<UUID,TeamData> snapshots=new HashMap<>(); teams.allTeams().forEach(t -> snapshots.put(t.id,t));
            if (!snapshots.isEmpty()) store.transaction(Map.of(),snapshots).get(30,TimeUnit.SECONDS);
        } catch (Exception e) { if (error==null) error=new IOException("Final team save failed",e); else error.addSuppressed(e); }
        try { store.saveAndClose(players); } catch (IOException e) { if (error==null) error=e; else error.addSuppressed(e); }
        if (error!=null) throw error;
    }
}
