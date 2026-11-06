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
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
    private final InventoryMarker inventoryMarker;
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
    private final FtbMigrationService migrations;
    private boolean storageFailed;
    private String teamRewardPolicy="all_members";
    private boolean commandRewardsEnabled;
    private boolean dropLootCrates;
    private Map<String,Integer> crateNoDrop=Map.of("passive",4000,"monster",600,"boss",0);
    private QuestRegistry registry;
    private NamedRewardTables rewardTables;
    private List<ClientProjection.Category> categories = List.of();
    private long catalogGeneration;
    private QuestEngine engine;
    private int autosaveTicks;
    private int ticks;
    /** Bound automatic reward checks per tick instead of traversing every connected player at once. */
    private int autoClaimCursor;
    private static final int AUTO_CLAIM_PLAYERS_PER_TICK = 16;
    private boolean closing;

    public QuestRuntime(MinecraftServer server) throws IOException {
        this.server = server;
        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        definitions = world.resolve("questplus");
        Defaults.install(definitions);
        inventoryMarker=new InventoryMarker(server);
        store = JsonProgressStore.withSavedMarker(world.resolve("data/questplus"),new JsonProgressStore.SavedMarker() {
            public String read(UUID id) throws IOException {return inventoryMarker.saved(id);}
            public void verifyInventory(UUID id,com.google.gson.JsonArray expected) throws IOException {inventoryMarker.verifySavedInventory(id,expected);}
            public void verifyExperience(UUID id,com.google.gson.JsonObject expected) throws IOException {inventoryMarker.verifySavedExperience(id,expected);}
        });
        try { reload(); teams=new TeamManager(store,registry,players);migrations=new FtbMigrationService(this,server,world); } catch (IOException | RuntimeException e) { store.close(); throw e; }
    }
    FtbMigrationService migrations(){return migrations;}

    public void reload() throws IOException {
        assertThread();
        var config = Defaults.readObject(definitions.resolve("config.json"));
        if (!Set.of("schemaVersion","autosaveSeconds","teamRewardPolicy","enableCommandRewards","dropLootCrates","crateNoDrop").containsAll(config.keySet()) || !config.has("schemaVersion") || !config.has("autosaveSeconds")
                || !config.get("schemaVersion").isJsonPrimitive() || !config.get("schemaVersion").getAsJsonPrimitive().isNumber()
                || config.get("schemaVersion").getAsBigDecimal().intValueExact() != 1)
            throw new IllegalArgumentException("Unsupported config.json");
        if (!config.get("autosaveSeconds").isJsonPrimitive() || !config.get("autosaveSeconds").getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("autosaveSeconds must be an integer");
        int seconds = config.get("autosaveSeconds").getAsBigDecimal().intValueExact();
        if (seconds < 1 || seconds > 300) throw new IllegalArgumentException("autosaveSeconds must be 1..300");
        String policy=config.has("teamRewardPolicy") ? config.get("teamRewardPolicy").getAsString() : "all_members";
        if (!Set.of("all_members","contributor").contains(policy)) throw new IllegalArgumentException("Invalid teamRewardPolicy");
        if(config.has("enableCommandRewards") && (!config.get("enableCommandRewards").isJsonPrimitive() ||
            !config.get("enableCommandRewards").getAsJsonPrimitive().isBoolean()))
            throw new IllegalArgumentException("enableCommandRewards must be a boolean");
        boolean allowCommands=config.has("enableCommandRewards") && config.get("enableCommandRewards").getAsBoolean();
        if(config.has("dropLootCrates")&&(!config.get("dropLootCrates").isJsonPrimitive()||!config.getAsJsonPrimitive("dropLootCrates").isBoolean()))throw new IllegalArgumentException("dropLootCrates must be boolean");
        boolean candidateDrops=config.has("dropLootCrates")&&config.get("dropLootCrates").getAsBoolean();
        Map<String,Integer> candidateNoDrop=new HashMap<>(Map.of("passive",4000,"monster",600,"boss",0));
        if(config.has("crateNoDrop")){var weights=config.getAsJsonObject("crateNoDrop");NamedRewardTables.validateDropWeights(weights);weights.entrySet().forEach(e->candidateNoDrop.put(e.getKey(),e.getValue().getAsInt()));}
        var candidateTables=NamedRewardTables.load(definitions.resolve("quests"));
        for(var table:candidateTables.all().values()){var values=new JsonArray();values.add(table);QuestRegistry.rewards(values,this::validateRegistryReference);}
        QuestRegistry candidate = QuestRegistry.load(definitions.resolve("quests"), this::validateRegistryReference);
        if(!allowCommands && candidate.all().stream().anyMatch(q->
            q.rewards().stream().anyMatch(r->r.type().equals("questplus:command")) ||
            q.tiers().stream().flatMap(t->t.rewards().stream()).anyMatch(r->r.type().equals("questplus:command"))))
            throw new IllegalArgumentException("Command rewards require enableCommandRewards=true in trusted world config");
        Set<String> categoryIds = new HashSet<>();
        List<ClientProjection.Category> candidateCategories = new ArrayList<>();
        try (var paths = Files.walk(definitions.resolve("categories"))) {
            for (Path path : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json") || p.toString().endsWith(".json5")).toList()) {
                var category = Defaults.readObject(path);
                if (!Set.of("id","title","description","icon","annotations","order","images","linkRoutes","group").containsAll(category.keySet()) || !category.keySet().containsAll(Set.of("id","title","description")))
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
                if(dev.questplus.core.QuestIcon.isImage(icon))dev.questplus.core.QuestIcon.validateImage(icon);else validateRegistryReference("icon",icon);
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
                        if (!Set.of("id","x","y","text","style","color","rich","width","height","align").containsAll(note.keySet())
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
                        annotations.add(new ClientProjection.Annotation(aid,x,y,Map.copyOf(noteText),style,color,Map.copyOf(rich),width,height,ClientProjection.annotationAlignment(note)));
                    }
                }
                String group="";
                if(category.has("group")) {
                    var value=category.get("group");
                    if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString()||value.getAsString().length()>160||value.getAsString().chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid chapter group");
                    group=value.getAsString().trim();
                }
                List<ClientProjection.LinkRoute> routes=ClientProjection.routes(category.get("linkRoutes"),id,candidate);
                candidateCategories.add(new ClientProjection.Category(id,Map.copyOf(title),Map.copyOf(description),icon,List.copyOf(annotations),order,List.copyOf(images),routes,group));
            }
        }
        for (var quest : candidate.all()) if (!categoryIds.contains(quest.category())) throw new IllegalArgumentException("Unknown category: " + quest.category());
        registry = candidate; rewardTables=candidateTables;
        categories = candidateCategories.stream().sorted(Comparator.comparingInt(ClientProjection.Category::order).thenComparing(ClientProjection.Category::id)).toList();
        catalogGeneration++;
        engine = new QuestEngine(candidate);
        if (teams!=null) teams.registry(candidate);
        teamRewardPolicy=policy;
        commandRewardsEnabled=allowCommands;
        dropLootCrates=candidateDrops;crateNoDrop=Map.copyOf(candidateNoDrop);
        autosaveTicks = seconds * 20;
        QuestNetwork.invalidateAll(server);
    }

    private void validateRegistryReference(String kind, String target) {
        if(kind.equals("item_components")){QuestItemStacks.decode(com.google.gson.JsonParser.parseString(target).getAsJsonObject(),server.registryAccess());return;}
        if(kind.equals("advancement_criterion")) {
            var data=com.google.gson.JsonParser.parseString(target).getAsJsonObject();var advancement=server.getAdvancements().get(Identifier.parse(data.get("advancement").getAsString()));
            if(advancement==null||!advancement.value().criteria().containsKey(data.get("criterion").getAsString()))throw new IllegalArgumentException("Unknown advancement criterion");return;
        }
        String registryKind=kind.equals("icon") || kind.equals("tool") || kind.equals("item") ? "item"
            : Set.of("dimension","biome","effect").contains(kind) ? kind : QuestExtensions.event(kind).registry();
        if (kind.equals("advancement_complete")) {
            Identifier advancement=Identifier.parse(target);
            if(server.getAdvancements().get(advancement)==null)
                throw new IllegalArgumentException("Unknown advancement "+target);
            return;
        }
        if (kind.equals("custom_stat")) {
            Identifier stat=Identifier.parse(target);
            if (!BuiltInRegistries.CUSTOM_STAT.containsKey(stat))
                throw new IllegalArgumentException("Unknown custom statistic "+target);
            return;
        }
        if (registryKind.equals("marker")) {
            Identifier.parse(target.startsWith("#") ? target.substring(1) : target);
            if (kind.equals("travel_distance") && !target.equals("questplus:travel") || kind.equals("location") && !target.equals("questplus:position")
                    || kind.equals("player_death") && !target.equals("questplus:death") || kind.equals("energy_transfer") && !target.equals("questplus:fe")) throw new IllegalArgumentException("Invalid built-in event target: "+kind+" "+target);
            return;
        }
        if (registryKind.equals("dimension")) {
            if (server.levelKeys().stream().noneMatch(k -> k.identifier().toString().equals(target)))
                throw new IllegalArgumentException("Unknown dimension " + target);
            return;
        }
        Registry<?> values = switch (registryKind) {
            case "block" -> BuiltInRegistries.BLOCK;
            case "fluid" -> BuiltInRegistries.FLUID;
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

    public boolean commandRewardsEnabled(){return commandRewardsEnabled;}
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
            if (!value.claims.containsKey(quest)) value.entitlements.putIfAbsent(quest,new PlayerProgress.RewardGrant(quest,RewardTables.resolve(registry.get(quest).rewards())));
            value.legacyUnclaimed.remove(quest); dirty.add(id);
        }
        if(teams!=null)for(var entry:value.entitlements.entrySet()) {
            UUID scope=entry.getValue().rewards().isEmpty()?null:entry.getValue().rewards().getFirst().policy().teamScope();
            if(scope!=null) {
                String status=teams.rewardScope(scope).rewardClaims.get(entry.getKey());
                if(status!=null&&!status.equals(value.claims.get(entry.getKey()))){value.claims.put(entry.getKey(),status);dirty.add(id);}
            }
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

    /** Server-authoritative player hand-in. The selected quest and objective are encoded in
     * event context: an item submitted to one quest cannot advance a different quest.
     * Inventory is modified only on the server thread, after all checks succeed.
     */
    public int submitItem(ServerPlayer actor, String questId, String objectiveId) throws IOException {
        assertThread();
        if(actor.isSpectator() || actor.isCreative())throw new IllegalArgumentException("Submission requires survival/adventure mode");
        UUID uuid=actor.getUUID();
        ensureFree(group(uuid));
        QuestDefinition quest=visibleQuest(uuid,questId);
        QuestDefinition.Objective objective=quest.objectives().stream()
            .filter(o->o.id().equals(objectiveId)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown objective"));
        boolean experience=Set.of("xp_submit_levels","xp_submit_points").contains(objective.event());
        boolean resource=Set.of("fluid_submit","energy_submit").contains(objective.event());
        boolean consuming=objective.event().equals("item_turn_in")||experience||resource;
        if(!consuming && !objective.event().equals("item_submit"))
            throw new IllegalArgumentException("Objective is not a submission task");
        PlayerProgress state=state(uuid);
        PlayerProgress.QuestProgress progress=state.quests.get(questId);
        if(progress!=null&&(progress.terminal || progress.waitingRestart || quest.progression().equals("once")&&progress.completed))
            throw new IllegalArgumentException("Quest already completed or awaiting restart");
        if(engine.diagnostic(state,questId).isPresent())throw new IllegalArgumentException("Quest progress needs migration");
        Map<String,Object> ctx=QuestPlusMod.context(actor);
        Set<String> completed=QuestEngine.prerequisiteState(state);
        if(quest.requires()!=null && !quest.progressBeforeUnlock() && !quest.requires().matches(ctx,completed))
            throw new IllegalArgumentException("Quest prerequisites are not satisfied");
        if(objective.conditions()!=null && !objective.conditions().matches(ctx,completed))
            throw new IllegalArgumentException("Objective conditions are not satisfied");
        if(progress!=null && quest.timeLimitSeconds()>0 && progress.attemptId!=null &&
            System.currentTimeMillis()-progress.startedAt>=quest.timeLimitSeconds()*1000)
            throw new IllegalArgumentException("Quest timer expired");
        if(progress!=null && progress.attemptId==null && progress.startedAt>0 &&
            System.currentTimeMillis()-progress.startedAt<quest.cooldownSeconds()*1000)
            throw new IllegalArgumentException("Quest cooldown is still active");
        long factor=quest.progression().equals("tiered")
            ? quest.tiers().get(Math.min(progress==null?0:progress.tier,quest.tiers().size()-1)).threshold() : 1L;
        if(quest.objectiveMode().equals("sequence") && !objective.optional()) {
            for(var preceding:quest.objectives()) {
                if(preceding==objective)break;
                if(preceding.optional())continue;
                if(progress==null || progress.counters.getOrDefault(preceding.id(),0L)<preceding.count()*factor)
                    throw new IllegalArgumentException("Complete earlier steps first");
            }
        }
        long maximum=objective.count()*factor;
        long previous=progress==null?0:progress.counters.getOrDefault(objectiveId,0L);
        long remaining=maximum-previous;
        if(remaining<=0)throw new IllegalArgumentException("Objective already supplied");
        if(resource) {
            var inventory=new ArrayList<ItemStack>();for(int i=0;i<37;i++)inventory.add((i==36?actor.getOffhandItem():actor.getInventory().getItem(i)).copy());
            var data=com.google.gson.JsonParser.parseString(objective.data()).getAsJsonObject();
            var plan=QuestResourceContainers.extract(inventory,objective.event().equals("fluid_submit")?objective.target():null,remaining,data.has("fePerEnergy")?data.get("fePerEnergy").getAsInt():1);
            ctx.put("submissionQuest",questId);ctx.put("submissionObjective",objectiveId);
            durableSubmission(actor,new QuestEvent(objective.event(),objective.target(),Set.of(),plan.amount(),ctx),stack->false,plan.amount(),questId,objectiveId,previous,null,plan.stacks());
            return plan.amount();
        }
        if(experience) {
            boolean points=objective.event().equals("xp_submit_points");
            int available=points?Experience.points(actor.experienceLevel,actor.experienceProgress):actor.experienceLevel;
            int amount=(int)Math.min(remaining,available);if(amount<=0)throw new IllegalArgumentException("Not enough experience to submit");
            var after=InventoryMarker.experience(actor);
            if(points){int total=available-amount,level=Experience.level(total);after.addProperty("level",level);after.addProperty("progress",(float)(total-Experience.base(level))/Experience.capacity(level));after.addProperty("total",total);}
            else after.addProperty("level",actor.experienceLevel-amount);
            ctx.put("submissionQuest",questId);ctx.put("submissionObjective",objectiveId);
            durableSubmission(actor,new QuestEvent(objective.event(),objective.target(),Set.of(),amount,ctx),stack->false,amount,questId,objectiveId,previous,after);
            return amount;
        }
        String target=objective.target();
        net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag=target.startsWith("#")
            ? net.minecraft.tags.TagKey.create(Registries.ITEM,Identifier.parse(target.substring(1))) : null;
        java.util.function.Predicate<ItemStack> matches=stack->!stack.isEmpty() &&
            ItemComponents.targets(objective).stream().anyMatch(candidate->candidate.startsWith("#")?stack.is(TagKey.create(Registries.ITEM,Identifier.parse(candidate.substring(1)))):BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(candidate))&&QuestItemStacks.matches(objective,stack,actor);
        long available=0;
        for(int i=0;i<36;i++){ItemStack stack=actor.getInventory().getItem(i);if(matches.test(stack))available+=stack.getCount();}
        ItemStack offhand=actor.getOffhandItem();
        if(matches.test(offhand))available+=offhand.getCount();
        if(available<=0 || !consuming && available<maximum)
            throw new IllegalArgumentException("Not enough required items to submit");
        int amount=(int)Math.min(remaining,Math.min(available,2304L));
        if(!consuming && amount<remaining)throw new IllegalArgumentException("Non-consuming submission requires the full objective amount");
        // A non-consuming submission is a one-shot full inventory verification.
        // A consuming hand-in may accumulate partial deposits.
        ctx.put("submissionQuest",questId);ctx.put("submissionObjective",objectiveId);
        String eventTarget=tag==null?target:"minecraft:air";
        Set<String> tags=tag==null?Set.of():Set.of(target.substring(1));
        var submission=new QuestEvent(objective.event(),eventTarget,tags,amount,ctx);
        if(consuming) durableSubmission(actor,submission,matches,amount,questId,objectiveId,previous);
        else event(actor,submission);
        QuestNetwork.invalidate(uuid);
        return amount;
    }

    private void durableSubmission(ServerPlayer actor,QuestEvent event,java.util.function.Predicate<ItemStack> matches,
            int amount,String questId,String objectiveId,long previous) throws IOException {
        durableSubmission(actor,event,matches,amount,questId,objectiveId,previous,null);
    }
    private void durableSubmission(ServerPlayer actor,QuestEvent event,java.util.function.Predicate<ItemStack> matches,
            int amount,String questId,String objectiveId,long previous,com.google.gson.JsonObject experienceAfter) throws IOException {
        durableSubmission(actor,event,matches,amount,questId,objectiveId,previous,experienceAfter,null);
    }
    private void durableSubmission(ServerPlayer actor,QuestEvent event,java.util.function.Predicate<ItemStack> matches,
            int amount,String questId,String objectiveId,long previous,com.google.gson.JsonObject experienceAfter,List<ItemStack> plannedInventory) throws IOException {
        UUID id=actor.getUUID(); Set<UUID> members=group(id);
        Map<UUID,PlayerProgress> personal=new HashMap<>();
        for(UUID member:members)personal.put(member,JsonProgressStore.detachedCopy(player(member)));
        UUID teamId=player(id).teamId;
        TeamData team=teamId==null?null:new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(teams.get(teamId)),TeamData.class);
        if(team!=null)team.progress=JsonProgressStore.detachedCopy(teams.get(teamId).progress);
        PlayerProgress progress=team==null?personal.get(id):team.progress;
        var awards=engine.applyDetailed(progress,event);
        var entry=progress.quests.get(questId);
        if(entry==null || entry.counters.getOrDefault(objectiveId,0L)<=previous && awards.stream().noneMatch(award->award.questId().equals(questId)))
            throw new IllegalArgumentException("Submission did not advance the objective");
        Set<UUID> recipients=teamRewardPolicy.equals("contributor")?Set.of(id):members;
        for(UUID recipient:recipients)for(var award:awards)
            entitleAward(personal.get(recipient),award,teamId);
        List<ItemStack> before=new ArrayList<>(),after=new ArrayList<>();
        for(int i=0;i<37;i++) { var stack=(i==36?actor.getOffhandItem():actor.getInventory().getItem(i)).copy(); before.add(stack); after.add(stack.copy()); }
        if(plannedInventory!=null){if(plannedInventory.size()!=37)throw new IllegalArgumentException("Invalid inventory plan");after=new ArrayList<>(plannedInventory.stream().map(ItemStack::copy).toList());}
        int left=experienceAfter==null&&plannedInventory==null?amount:0;
        for(var stack:after)if(left>0 && matches.test(stack)) { int remove=Math.min(left,stack.getCount()); stack.shrink(remove); left-=remove; }
        if(left!=0)throw new IllegalStateException("Inventory changed during submission");
        commitInventory(actor,before,after,personal,team==null?Map.of():Map.of(teamId,team),experienceAfter);
        for(var award:awards)for(UUID member:members) {
            var online=server.getPlayerList().getPlayer(member);
            if(online!=null&&!ServerPlayNetworking.canSend(online,QuestNetwork.State.TYPE))online.sendSystemMessage(award.rewards().isEmpty()
                ? Messages.text(online,"questplus.completed_no_reward",title(online,registry.get(award.questId())))
                : Messages.text(online,"questplus.completed",title(online,registry.get(award.questId())),award.questId()));
        }
    }

    private void commitInventory(ServerPlayer actor,List<ItemStack> before,List<ItemStack> after,Map<UUID,PlayerProgress> personal,Map<UUID,TeamData> collective) throws IOException {
        commitInventory(actor,before,after,personal,collective,null);
    }
    private void commitInventory(ServerPlayer actor,List<ItemStack> before,List<ItemStack> after,Map<UUID,PlayerProgress> personal,Map<UUID,TeamData> collective,com.google.gson.JsonObject experienceAfter) throws IOException {
        var experienceBefore=experienceAfter==null?null:InventoryMarker.experience(actor);
        var ops=RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,server.registryAccess());
        var beforeJson=new JsonArray();var afterJson=new JsonArray();
        for(int i=0;i<37;i++){beforeJson.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops,before.get(i)).getOrThrow());afterJson.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops,after.get(i)).getOrThrow());}
        try {
            String old=inventoryMarker.live(actor);UUID baseline=UUID.randomUUID();
            inventoryMarker.set(actor,old,baseline);inventoryMarker.save(actor,baseline.toString(),beforeJson);
            UUID operation=UUID.randomUUID();
            if(experienceBefore!=null)inventoryMarker.verifySavedExperience(actor.getUUID(),experienceBefore);
            store.prepareInventory(operation,actor.getUUID(),baseline.toString(),beforeJson,afterJson,personal,collective,experienceBefore,experienceAfter).join();
            inventoryMarker.set(actor,baseline.toString(),operation);
            for(int i=0;i<36;i++)actor.getInventory().setItem(i,after.get(i));
            actor.getInventory().setItem(40,after.get(36));actor.getInventory().setChanged();
            if(experienceAfter!=null)InventoryMarker.experience(actor,experienceAfter);
            inventoryMarker.save(actor,operation.toString(),afterJson);store.commitInventory(operation).join();
            personal.forEach(players::put);collective.forEach((id,team)->teams.get(id).progress=team.progress);
            dirty.removeAll(personal.keySet());dirtyTeams.removeAll(collective.keySet());personal.keySet().forEach(QuestNetwork::invalidate);
        }catch(Exception error){failStorage(error);throw new IOException("Inventory transaction requires storage recovery",error);}
    }
    public void openCrate(ServerPlayer actor,net.minecraft.world.InteractionHand hand) throws IOException {
        assertThread();ensureFree(group(actor.getUUID()));
        if(actor.isCreative()||actor.isSpectator())throw new IllegalArgumentException("Open crates in survival/adventure mode");
        var stack=actor.getItemInHand(hand);
        if(!BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("questplus:loot_crate"))throw new IllegalArgumentException("Not a loot crate");
        var data=stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        String name=data.getString("questplus_table").orElseThrow(()->new IllegalArgumentException("Loot crate has no table"));
        var list=new JsonArray();list.add(rewardTables.crate(name));
        var rewards=RewardTables.resolve(QuestRegistry.rewards(list,this::validateRegistryReference)).stream()
            .map(r->r.withPolicy(new QuestDefinition.RewardPolicy(false,"enabled",false))).toList();
        var personal=JsonProgressStore.detachedCopy(player(actor.getUUID()));
        personal.entitle("questplus:crate|"+UUID.randomUUID(),new PlayerProgress.RewardGrant("questplus:crate",rewards));
        var before=new ArrayList<ItemStack>();var after=new ArrayList<ItemStack>();int selected=-1;
        for(int i=0;i<37;i++) {
            var item=i==36?actor.getOffhandItem():actor.getInventory().getItem(i);
            if(item==stack)selected=i;before.add(item.copy());after.add(item.copy());
        }
        if(selected<0)throw new IllegalStateException("Crate moved before opening");after.get(selected).shrink(1);
        commitInventory(actor,before,after,Map.of(actor.getUUID(),personal),Map.of());
        actor.sendSystemMessage(net.minecraft.network.chat.Component.translatableWithFallback("questplus.crate.opened","Loot crate opened; rewards are saved and delivered when inventory space is available."));
    }
    void dropCrate(net.minecraft.world.entity.LivingEntity entity) {
        assertThread();if(!dropLootCrates||storageFailed||entity instanceof ServerPlayer)return;
        boolean boss=entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon||entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss
            ||entity.typeHolder().is(TagKey.create(Registries.ENTITY_TYPE,Identifier.parse("c:bosses")));
        String kind=boss?"boss":entity instanceof net.minecraft.world.entity.monster.Enemy?"monster":"passive";
        var item=rewardTables.drop(kind,crateNoDrop.get(kind),java.util.concurrent.ThreadLocalRandom.current());
        if(item!=null&&entity.level() instanceof net.minecraft.server.level.ServerLevel level)entity.spawnAtLocation(level,QuestItemStacks.decode(item,server.registryAccess()));
    }

    public void interactObjective(ServerPlayer actor,String questId,String objectiveId) throws IOException {
        assertThread();ensureFree(group(actor.getUUID()));
        var quest=visibleQuest(actor.getUUID(),questId);
        var progress=state(actor.getUUID()).quests.get(questId);
        long factor=quest.progression().equals("tiered")?quest.tiers().get(Math.min(progress==null?0:progress.tier,quest.tiers().size()-1)).threshold():1L;
        var objective=quest.objectives().stream().filter(o->o.event().equals("manual") && (objectiveId==null
            ? progress==null || progress.counters.getOrDefault(o.id(),0L)<o.count()*factor : o.id().equals(objectiveId)))
            .findFirst().orElseThrow(()->new IllegalArgumentException("Not an available manual objective"));
        var context=QuestPlusMod.context(actor);
        context.put("manualQuest",questId);context.put("manualObjective",objective.id());
        event(actor,new QuestEvent("manual",objective.target(),Set.of(),1,context));
    }
    public void event(ServerPlayer player, QuestEvent event) {
        assertThread();
        // Manual acknowledgements are valid even in creative/spectator; only gameplay counters are restricted.
        if (closing || storageFailed || (!event.type().equals("manual") && (player.isSpectator() || player.isCreative()))
            || blocked.contains(player.getUUID())) return;
        if (!event.type().equals("player_death") && registry.candidates(event).isEmpty()) return;
        if (busy.contains(player.getUUID())) {
            // Polls are sampled again; queued stale snapshots can overflow or complete a lost gaze.
            if(Set.of("inventory_count","xp_levels","xp_points","custom_stat","advancement_complete","observe_block","observe_entity","stage").contains(event.type()))return;
            if (deferred.size()>=4096) throw new IllegalStateException("Quest Plus deferred event capacity exceeded");
            deferred.addLast(new DeferredEvent(player,event)); return;
        }
        try {
            UUID uuid=player.getUUID(); PlayerProgress progress = state(uuid);
            long revision = progress.revision;
            var completed = engine.applyDetailed(progress,event);
            if (progress.revision != revision) changed(uuid);
            if (!completed.isEmpty()) {
                Set<UUID> recipients=teamRewardPolicy.equals("contributor") ? Set.of(uuid) : group(uuid);
                for (UUID recipient:recipients) for (var award:completed) {
                    var personal=player(recipient);
                    if (!personal.claims.containsKey(award.key())) entitleAward(personal,award,player(uuid).teamId);
                    dirty.add(recipient);
                }
                Set<UUID> members=group(uuid); Map<UUID,PlayerProgress> snapshots=new HashMap<>();
                for (UUID member:members) snapshots.put(member,player(member));
                var team=player(uuid).teamId;
                persistCurrent(snapshots,team==null ? Map.of() : Map.of(team,teams.get(team)),members);
                for (var award:completed) for (UUID member:members) {
                    var online=server.getPlayerList().getPlayer(member);
                    if (online!=null&&!ServerPlayNetworking.canSend(online,QuestNetwork.State.TYPE)) online.sendSystemMessage(award.rewards().isEmpty()
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
        // Keep the tick-time budget independent of the number of online players.
        // Failed auto-claims are retried on the next round (including when inventory space becomes available).
        var online=server.getPlayerList().getPlayers();
        if (!online.isEmpty()) {
            int autoChecks=Math.min(AUTO_CLAIM_PLAYERS_PER_TICK,online.size());
            for (int i=0;i<autoChecks;i++) {
                var actor=online.get(Math.floorMod(autoClaimCursor++,online.size()));
                UUID id=actor.getUUID();
                if(busy.contains(id)||claiming.contains(id)||blocked.contains(id))continue;
                try {
                    var personal=player(id);
                    Map.Entry<String,PlayerProgress.RewardGrant> next=null;
                    for(var entry:personal.entitlements.entrySet()) {
                        if(personal.claims.containsKey(entry.getKey())||entry.getValue().rewards().isEmpty()
                            ||entry.getValue().rewards().stream().anyMatch(r->r.policy().autoClaim().equals("disabled")||r.type().equals("questplus:choice")))continue;
                        if(next==null||entry.getKey().compareTo(next.getKey())<0)next=entry;
                    }
                    if(next!=null){
                        boolean toast=next.getValue().rewards().stream().anyMatch(r->r.policy().autoClaim().equals("enabled"));
                        claim(actor,next.getValue().questId(),next.getKey(),toast?null:()->{});
                    }
                }catch(IOException|IllegalArgumentException|IllegalStateException unavailable) { /* Retry after inventory space or the current transaction changes. */ }
            }
        }
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
            TeamData team=teams.rewardScope(id);
            if (team.members.stream().anyMatch(busy::contains)) continue;
            dirtyTeams.remove(id);
            store.transaction(Map.of(),Map.of(id,team)).whenComplete((ok,error) -> {
                if (error!=null) server.execute(() -> failStorage(error));
            });
        }
    }
    private void failStorage(Throwable error) { storageFailed=true; QuestPlusMod.LOGGER.error("Quest Plus storage transaction failed; progress is frozen until recovery",error); }
    private void release(Collection<UUID> ids) { busy.removeAll(ids); }
    private CompletableFuture<Void> persistCurrent(Map<UUID,PlayerProgress> personal,Map<UUID,TeamData> collective,Set<UUID> affected) {
        var result=new CompletableFuture<Void>();
        busy.addAll(affected); dirty.removeAll(personal.keySet()); dirtyTeams.removeAll(collective.keySet());
        store.transaction(personal,collective).whenComplete((ok,error) -> server.execute(() -> {
            if (error!=null) failStorage(error);
            release(affected);
            affected.forEach(QuestNetwork::invalidate);
            if(error==null)result.complete(null); else result.completeExceptionally(error);
        }));
        return result;
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
                if (!p.claims.containsKey(award.key())) entitleAward(p,award,player(id).teamId);
                snapshots.put(recipient,p);
            }
            var team=player(id).teamId;
            persistCurrent(snapshots,team==null ? Map.of() : Map.of(team,teams.get(team)),members);
        }
        changed(id);
    }
    private static void entitleAward(PlayerProgress personal,QuestEngine.Completion award,UUID teamId) {
        var rewards=award.rewards().stream().map(r->{
            var policy=r.policy();
            return policy.teamReward()&&teamId!=null?r.withPolicy(new QuestDefinition.RewardPolicy(true,policy.autoClaim(),policy.excludeFromClaimAll(),teamId)):r;
        }).toList();
        personal.entitle(award.key(),new PlayerProgress.RewardGrant(award.questId(),rewards));
    }
    private CompletableFuture<Void> save(UUID id) { return store.save(id,players.get(id)); }
    public void disconnect(UUID id) {
        if (!players.containsKey(id)) return;
        dirty.add(id);
        // shortcut: keep cached UUID states for this server session; add eviction when large-server profiling warrants it.
    }

    /** One client request, sequential durable reservations. No double claims and
     * no bypass of the existing free-inventory/storage safety checks. */
    public void claimAll(ServerPlayer player) throws IOException { claimAll(player,null); }
    public void claimAllFromTablet(ServerPlayer player) throws IOException {
        claimAll(player,()->QuestNetwork.notice(player,Messages.text(player,"questplus.client.toast.claim_all").getString(),"","minecraft:chest"));
    }
    private void claimAll(ServerPlayer player,Runnable onDelivered) throws IOException {
        assertThread();
        UUID uuid=player.getUUID();
        Set<String> visible=visibleQuests(uuid).stream().map(QuestDefinition::id).collect(java.util.stream.Collectors.toSet());
        var state=player(uuid);
        List<String> ids=state.entitlements.entrySet().stream()
            .filter(e->visible.contains(e.getValue().questId()) && !state.claims.containsKey(e.getKey())
                && !e.getValue().rewards().isEmpty() && e.getValue().rewards().stream().noneMatch(r->r.type().equals("questplus:choice")||r.policy().excludeFromClaimAll()))
            .sorted(Map.Entry.comparingByKey()).map(Map.Entry::getKey).toList();
        if(ids.isEmpty()) { if(onDelivered==null)player.sendSystemMessage(Messages.text(player,"questplus.error","No rewards to claim"));else QuestNetwork.notice(player,Messages.text(player,"questplus.client.claim_all_empty").getString(),"","minecraft:chest"); return; }
        claimNext(player,ids,0,onDelivered);
    }
    private void claimNext(ServerPlayer player,List<String> ids,int index,Runnable onDelivered) throws IOException {
        if(index>=ids.size()){
            if(onDelivered==null)player.sendSystemMessage(Messages.text(player,"questplus.claimed",ids.size()+" rewards"));else onDelivered.run();
            return;
        }
        String key=ids.get(index);
        var grant=player(player.getUUID()).entitlements.get(key);
        if(grant==null || player(player.getUUID()).claims.containsKey(key)){claimNext(player,ids,index+1,onDelivered);return;}
        claim(player,grant.questId(),key,() -> {
            try { claimNext(player,ids,index+1,onDelivered); }
            catch(IOException | RuntimeException error) {
                QuestPlusMod.LOGGER.warn("Claim all stopped at reward {}: {}",index,error.getMessage());
                player.sendSystemMessage(Messages.text(player,"questplus.error","Claim all stopped: "+error.getMessage()));
            }
        });
    }
    /**
     * Persist one player-selected reward before any claim reservation.
     * Choices are personal entitlements even when quest progress is shared by a team.
     * Subsequent claims cannot reroll this choice.
     */
    public void chooseReward(ServerPlayer actor,String questId,int zeroBasedOption) throws IOException {
        var personal=player(actor.getUUID());
        var entry=personal.entitlements.entrySet().stream()
            .filter(e->e.getValue().questId().equals(questId) && !personal.claims.containsKey(e.getKey())
                && e.getValue().rewards().stream().anyMatch(r->r.type().equals("questplus:choice")))
            .sorted(Map.Entry.comparingByKey()).findFirst()
            .orElseThrow(()->new IllegalArgumentException("No unresolved choice reward"));
        int slot=0;
        while(!entry.getValue().rewards().get(slot).type().equals("questplus:choice"))slot++;
        chooseReward(actor,questId,ClientProjection.rewardToken(entry.getKey()),slot,zeroBasedOption);
    }
    public void chooseReward(ServerPlayer actor,String questId,String token,int slot,int option) throws IOException {
        chooseReward(actor,questId,token,slot,option,true);
    }
    public void chooseRewardFromTablet(ServerPlayer actor,String questId,String token,int slot,int option) throws IOException {
        chooseReward(actor,questId,token,slot,option,false);
    }
    private void chooseReward(ServerPlayer actor,String questId,String token,int slot,int option,boolean commandResponse) throws IOException {
        assertThread();
        UUID uuid=actor.getUUID();
        Set<UUID> affected=group(uuid);
        ensureFree(affected);
        visibleQuest(uuid,questId);
        var personal=player(uuid);
        var entry=personal.entitlements.entrySet().stream()
            .filter(e->e.getValue().questId().equals(questId) && !personal.claims.containsKey(e.getKey())
                && ClientProjection.rewardToken(e.getKey()).equals(token))
            .findFirst().orElseThrow(()->new IllegalArgumentException("Reward selection expired"));
        var selected=RewardTables.choose(entry.getValue(),slot,option);
        selected.rewards().forEach(QuestExtensions::validateSavedReward);
        personal.entitlements.put(entry.getKey(),selected);
        persistCurrent(Map.of(uuid,personal),Map.of(),affected).whenComplete((ok,error)-> {
            if(error==null){if(commandResponse)actor.sendSystemMessage(Messages.text(actor,"questplus.choice_saved"));else QuestNetwork.notice(actor,Messages.text(actor,"questplus.client.toast.choice_saved").getString(),"","minecraft:chest");}
            else QuestNetwork.error(actor,"choice_save_failed");
        });
        affected.forEach(QuestNetwork::invalidate);
    }
    public Map<String,PlayerProgress.RewardGrant> reservedClaims(UUID id) throws IOException {
        assertThread(); var progress=player(id); var result=new TreeMap<String,PlayerProgress.RewardGrant>();
        progress.claims.forEach((key,status)->{if(status.equals("reserved"))result.put(key,progress.entitlements.get(key));});
        return result;
    }
    public void resolveReservedClaim(UUID id,String key,String outcome,String reason,String operator) throws IOException {
        assertThread(); ensureFree(group(id));
        if(claiming.contains(id))throw new IllegalStateException("Reward delivery is in progress");
        if(!Set.of("delivered","discard").contains(outcome) || reason.isBlank() || reason.length()>1024 || operator.isBlank())
            throw new IllegalArgumentException("Invalid resolution or reason");
        var current=player(id);
        if(!"reserved".equals(current.claims.get(key)))throw new IllegalArgumentException("Claim is not reserved");
        var after=JsonProgressStore.detachedCopy(current);
        after.claims.put(key,"delivered");
        var audit=new JsonProgressStore.ClaimAudit(UUID.randomUUID(),id,key,operator,outcome,reason,System.currentTimeMillis());
        var grant=current.entitlements.get(key);
        UUID scope=grant==null||grant.rewards().isEmpty()?null:grant.rewards().getFirst().policy().teamScope();
        TeamData shared=scope==null?null:teams.rewardScope(scope);
        TeamData sharedAfter=shared==null?null:TeamManager.copy(shared);
        if(sharedAfter!=null)sharedAfter.rewardClaims.put(key,"delivered");
        try { store.resolveClaim(id,after,audit,sharedAfter==null?Map.of():Map.of(scope,sharedAfter)).join(); }
        catch(java.util.concurrent.CompletionException error) { failStorage(error); throw new IOException("Claim resolution failed; storage frozen",error); }
        current.claims.put(key,"delivered"); if(shared!=null)shared.rewardClaims.put(key,"delivered"); QuestNetwork.invalidate(id);
        QuestPlusMod.LOGGER.warn("Quest Plus claim resolved: player={} claim={} outcome={} operator={} reason={}",id,key,outcome,operator,reason);
    }
    public void claim(ServerPlayer player, String id) throws IOException { claim(player,id,null,null); }
    public void claimFromTablet(ServerPlayer player,String id) throws IOException {
        var quest=registry.find(id).orElse(null);
        claim(player,id,null,()->QuestNetwork.notice(player,Messages.text(player,"questplus.client.toast.claimed").getString(),quest==null?"Loot crate":title(player,quest),quest==null?"minecraft:chest":quest.icon()));
    }
    private void claim(ServerPlayer player, String id,String requestedKey,Runnable onDelivered) throws IOException {
        assertThread();
        UUID uuid = player.getUUID();
        ensureFree(group(uuid)); var progress = player(uuid);
        var quest = registry.find(id).orElse(null);
        if(quest==null&&!id.equals("questplus:crate"))throw new IllegalArgumentException("Unknown quest "+id);
        String key=progress.entitlements.entrySet().stream().filter(e -> e.getValue().questId().equals(id) && (requestedKey==null || requestedKey.equals(e.getKey())) && !e.getValue().rewards().isEmpty() && !progress.claims.containsKey(e.getKey())).map(Map.Entry::getKey).sorted().findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No unclaimed reward entitlement: " + id));
        var rewards=progress.entitlements.get(key).rewards();
        if(rewards.stream().anyMatch(r->r.type().equals("questplus:choice"))) throw new IllegalArgumentException("Choose your reward first: /questplus choose <quest> <option number>");
        if (claiming.contains(uuid)) throw new IllegalArgumentException("Reward already reserved; pending claims require administrator review");
        if (!fits(player,rewards)) throw new IllegalArgumentException("Inventory needs more free space");
        UUID rewardScope=rewards.getFirst().policy().teamScope();
        TeamData shared=rewardScope==null?null:teams.rewardScope(rewardScope);
        if(shared!=null&&shared.rewardClaims.containsKey(key))throw new IllegalArgumentException("Team reward already reserved or claimed");
        progress.claims.put(key,"reserved");
        if(shared!=null)shared.rewardClaims.put(key,"reserved");
        QuestNetwork.invalidate(uuid);
        Set<UUID> affected=group(uuid); busy.addAll(affected);
        claiming.add(uuid);
        store.transaction(Map.of(uuid,progress),shared==null?Map.of():Map.of(rewardScope,shared)).whenComplete((ok,error) -> server.execute(() -> {
            claiming.remove(uuid);
            release(affected);
            if (closing) return;
            if (error != null) {
                dirty.add(uuid);
                failStorage(error);
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
                    else if (reward.type().equals("experience_levels")) player.giveExperienceLevels(reward.amount());
                    else if (reward.type().equals("item")) for (var stack : QuestItemStacks.rewards(reward,server.registryAccess())) {
                        if (!player.getInventory().add(stack) || !stack.isEmpty()) throw new IllegalStateException("Inventory changed during reward delivery");
                    }
                    else if(reward.type().equals("questplus:message")) {
                        String message=com.google.gson.JsonParser.parseString(reward.data()).getAsJsonObject()
                            .get("message").getAsString();
                        QuestNetwork.notice(player,quest==null?"QuestPlus":title(player,quest),message,quest==null?"minecraft:chest":quest.icon());
                    }
                    else if(reward.type().equals("questplus:advancement")) {
                        String advancementId=com.google.gson.JsonParser.parseString(reward.data()).getAsJsonObject()
                            .get("advancement").getAsString();
                        var holder=server.getAdvancements().get(Identifier.parse(advancementId));
                        if(holder==null)throw new IllegalStateException("Missing advancement for reserved reward "+advancementId);
                        var progressEntry=player.getAdvancements().getOrStartProgress(holder);
                        List<String> remaining=new ArrayList<>();
                        var data=com.google.gson.JsonParser.parseString(reward.data()).getAsJsonObject();
                        if(data.has("criterion"))remaining.add(data.get("criterion").getAsString());else progressEntry.getRemainingCriteria().forEach(remaining::add);
                        for(String criterion:remaining)player.getAdvancements().award(holder,criterion);
                    }
                    else QuestRewardExtensions.deliver(player,reward);
                }
                progress.claims.put(key,"delivered");
                if(shared!=null){shared.rewardClaims.put(key,"delivered");dirtyTeams.add(rewardScope);}
                // Keep only the latest receipt per quest; the compact claims ledger prevents replay.
                progress.entitlements.entrySet().removeIf(e->!e.getKey().equals(key) && e.getValue().questId().equals(id)
                    && "delivered".equals(progress.claims.get(e.getKey())));
                QuestNetwork.invalidate(uuid);
                dirty.add(uuid);
                if(onDelivered==null)player.sendSystemMessage(Messages.text(player,"questplus.claimed",quest==null?"Loot crate":title(player,quest)));
                else onDelivered.run();
            } catch (RuntimeException ex) { QuestPlusMod.LOGGER.error("Reserved reward requires manual recovery for {} {}",uuid,id,ex); }
        }));
    }

    private static boolean fits(ServerPlayer player,List<QuestDefinition.Reward> rewards) {
        for(var reward:rewards) {
            if(reward.type().equals("questplus:advancement")) {
                var data=com.google.gson.JsonParser.parseString(reward.data()).getAsJsonObject();var advancement=player.level().getServer().getAdvancements().get(Identifier.parse(data.get("advancement").getAsString()));
                if(advancement==null||data.has("criterion")&&!advancement.value().criteria().containsKey(data.get("criterion").getAsString()))return false;
            } else if(!Set.of("item","experience","experience_levels","questplus:message").contains(reward.type()) &&
                    !QuestRewardExtensions.fits(player,reward))return false;
        }
        List<ItemStack> slots = player.getInventory().getNonEquipmentItems().stream().map(ItemStack::copy).toList();
        var simulated = new ArrayList<>(slots);
        for (var reward : rewards) if (reward.type().equals("item")) for (ItemStack input : QuestItemStacks.rewards(reward,player.registryAccess())) {
            var data=com.google.gson.JsonParser.parseString(reward.data()).getAsJsonObject();
            if(data.has("onlyOne")&&data.get("onlyOne").getAsBoolean()&&(slots.stream().anyMatch(stack->!stack.isEmpty()&&ItemStack.isSameItemSameComponents(stack,input))||ItemStack.isSameItemSameComponents(player.getOffhandItem(),input)))return false;
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
            for(UUID id:dirtyTeams){var team=teams.rewardScope(id);if(team!=null)snapshots.put(id,team);}
            if (!snapshots.isEmpty()) store.transaction(Map.of(),snapshots).get(30,TimeUnit.SECONDS);
        } catch (Exception e) { if (error==null) error=new IOException("Final team save failed",e); else error.addSuppressed(e); }
        try { store.saveAndClose(players); } catch (IOException e) { if (error==null) error=e; else error.addSuppressed(e); }
        if (error!=null) throw error;
    }
}
