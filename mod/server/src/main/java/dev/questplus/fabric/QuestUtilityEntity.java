package dev.questplus.fabric;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import dev.questplus.core.*;
import dev.questplus.persistence.BlockAccount;
import dev.questplus.team.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class QuestUtilityEntity extends BlockEntity implements WorldlyContainer {
    UUID owner, boundTeam;
    QuestBlockConfig config=QuestBlockConfig.DEFAULT;
    private ItemStack stack=ItemStack.EMPTY;
    long energy;
    Object optionalEnergyStorage;
    int signal, configuredTier;
    boolean resizing;
    private boolean registered, initialized, persisting, lastCondition;
    private long nextRetry, generation=-1;
    private QuestDefinition.Objective cachedObjective;
    private String moveToken;
    public String status="unconfigured";
    public record Row(String title, String icon, String progress, String state, String player, String detail, double fraction, String count) {}
    public List<Row> rows=List.of();
    public List<Row> leaders=List.of();
    public long contentRevision;
    public int boardWidth=1,boardHeight=1;
    public BlockPos boardAnchor;
    final SingleStackStorage items=new SingleStackStorage() {
        protected ItemStack getStack() { return stack; }
        protected void setStack(ItemStack value) { stack=value; }
        protected boolean canInsert(ItemVariant v) { return accepts(v.toStack()); }
        protected boolean canExtract(ItemVariant v) { return input(null); }
        protected int getCapacity(ItemVariant v) { return (int)Math.min(v.getItem().getDefaultMaxStackSize(), remaining("item_turn_in")); }
        protected void onFinalCommit() { resourcesChanged(); }
    };
    final SingleVariantStorage<FluidVariant> fluids=new SingleVariantStorage<>() {
        protected FluidVariant getBlankVariant() { return FluidVariant.blank(); }
        protected long getCapacity(FluidVariant variant) { return Math.min(FluidConstants.BUCKET,remaining("fluid_submit")*81); }
        protected boolean canInsert(FluidVariant v) {
            var objective=objective();
            return objective!=null && objective.event().equals("fluid_submit")
                    && BuiltInRegistries.FLUID.getKey(v.getFluid()).toString().equals(objective.target())
                    && remaining("fluid_submit")>0;
        }
        protected boolean canExtract(FluidVariant v) {return input(null);}
        protected void onFinalCommit() { resourcesChanged(); }
    };
    public QuestUtilityEntity(BlockPos pos, BlockState state) { super(QuestWorldBlocks.ENTITY,pos,state);boardAnchor=pos; }
    int kind() { return ((QuestUtilityBlock)getBlockState().getBlock()).kind; }
    public String stationQuestId() { return kind()==1 && config.quests().size()==1 ? config.quests().getFirst() : ""; }
    public boolean boardLeaderboard(){return config.boardMode().equals("leaderboard");}
    private UUID accountId() {
        return UUID.nameUUIDFromBytes((level.dimension().identifier()+"/"+worldPosition.asLong()).getBytes(StandardCharsets.UTF_8));
    }
    Map<String,Object> context() {
        var ctx=new HashMap<String,Object>();
        var online=owner==null ? null : level.getServer().getPlayerList().getPlayer(owner);
        if(online!=null) { ctx.putAll(QuestPlusMod.context(online)); ctx.put("playerPresent",true); }
        ctx.put("dimension",level.dimension().identifier().toString()); ctx.put("time",System.currentTimeMillis());
        ctx.put("position",Map.of("x",worldPosition.getX()+.5,"y",worldPosition.getY()+.5,"z",worldPosition.getZ()+.5));
        ctx.put("biome",level.getBiome(worldPosition).unwrapKey().map(k->k.identifier().toString()).orElse(""));
        return ctx;
    }
    void claim(UUID id) {
        if(owner!=null) return;
        if(kind()==1 && !initialized) {
            try {
                var account=QuestPlusMod.runtime().loadBlock(accountId());
                if(account!=null) {
                    restore(account.data());
                    moveToken=null;
                    if(!hasResources()) {owner=null;boundTeam=null;config=QuestBlockConfig.DEFAULT;}
                    persist();
                }
                initialized=true;
                if(owner!=null) { QuestWorldBlocks.loaded(this); return; }
            } catch(IOException e) { status="storage_error";return; }
        }
        owner=id; initialized=true;
        try { if(kind()==1) persist(); } catch(IOException e) { status="storage_error"; }
        QuestWorldBlocks.unloaded(this); QuestWorldBlocks.loaded(this); setChanged();
    }
    void claim(UUID id,ItemStack placed) {
        if(kind()!=1){claim(id);return;}
        var tag=placed.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        var sourceId=tag.getString("questplus_account");var token=tag.getString("questplus_move");
        if(sourceId.isEmpty()||token.isEmpty()){claim(id);return;}
        try {
            var rt=QuestPlusMod.runtime();var source=rt.loadBlock(UUID.fromString(sourceId.get()));
            if(source==null||!source.data().has("moveToken")||!source.data().get("moveToken").getAsString().equals(token.get())) {claim(id);return;}
            var existing=rt.loadBlock(accountId());
            if(!source.id().equals(accountId()) && existing!=null) {
                var data=existing.data();
                var ops=RegistryOps.create(JsonOps.INSTANCE,level.registryAccess());
                if(!ItemStack.OPTIONAL_CODEC.parse(ops,data.get("item")).getOrThrow().isEmpty()
                        || data.get("fluidAmount").getAsLong()>0 || data.get("energy").getAsLong()>0)
                    throw new IOException("Destination retains another station's resources");
            }
            restore(source.data());moveToken=null;
            var destination=account(stack,fluids.amount,energy);
            if(source.id().equals(destination.id()))rt.saveBlock(destination);
            else {
                var empty=source.data().deepCopy();empty.remove("moveToken");empty.remove("owner");empty.remove("team");
                empty.addProperty("config",QuestBlockConfig.DEFAULT.encode());empty.addProperty("tier",0);
                empty.add("item",ItemStack.OPTIONAL_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE,level.registryAccess()),ItemStack.EMPTY).getOrThrow());
                empty.addProperty("fluidAmount",0);empty.addProperty("energy",0);
                rt.saveBlocks(Map.of(destination.id(),destination,source.id(),new BlockAccount(1,source.id(),source.dimension(),source.position(),empty)));
            }
            initialized=true;QuestWorldBlocks.loaded(this);super.setChanged();
        } catch(Exception error){status="storage_error";QuestPlusMod.LOGGER.error("Station relocation requires recovery at {}",worldPosition,error);}
    }
    ItemStack packForMove() throws IOException {
        if(!initialized)serverTick();
        if(!initialized)throw new IOException("Station has not loaded its account");
        if(moveToken!=null) {
            var current=QuestPlusMod.runtime().loadBlock(accountId());
            if(current==null || !current.data().has("moveToken") || !current.data().get("moveToken").getAsString().equals(moveToken))
                throw new IOException("Station account has already moved");
        }
        moveToken=UUID.randomUUID().toString();persist();
        var item=new ItemStack(QuestWorldBlocks.STATION);var tag=new net.minecraft.nbt.CompoundTag();
        tag.putString("questplus_account",accountId().toString());tag.putString("questplus_move",moveToken);
        item.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));
        return item;
    }
    void serverTick() {
        if(!registered) {
            try {
                QuestPlusMod.runtime();
                if(kind()==1 && !initialized) {
                    var account=QuestPlusMod.runtime().loadBlock(accountId());
                    if(account!=null) {
                        if(!account.dimension().equals(level.dimension().identifier().toString()) || account.position()!=worldPosition.asLong())
                            throw new IOException("Block account position mismatch");
                        restore(account.data());
                    }
                    initialized=true;
                }
                registered=true; QuestWorldBlocks.loaded(this);
            } catch(Exception error) { status="storage_error"; return; }
        }
        if(kind()==1 && level.getGameTime()>=nextRetry && config.automation() && hasResources()) {
            nextRetry=level.getGameTime()+100; QuestWorldBlocks.enqueue(this);
        }
    }
    @Override public void setRemoved() { QuestWorldBlocks.unloaded(this); registered=false; super.setRemoved(); }
    boolean input(Direction side) {
        return kind()==1 && moveToken==null && !isRemoved() && initialized && level!=null && !level.isClientSide()
                && level.getServer().isSameThread() && (side==null || (config.faces() & 1<<side.ordinal())!=0);
    }
    private QuestDefinition.Objective objective() {
        if(owner==null || config.quests().size()!=1) return null;
        try {
            var runtime=QuestPlusMod.runtime();
            if(generation!=runtime.catalogGeneration()) {
                cachedObjective=runtime.registry().get(config.quests().getFirst()).objectives().stream()
                        .filter(o->o.id().equals(config.objective())).findFirst().orElse(null);
                generation=runtime.catalogGeneration();
            }
            return cachedObjective;
        } catch(RuntimeException error) { return null; }
    }
    long remaining(String event) {
        if(!input(null) || !config.automation()) return 0;
        var o=objective(); if(o==null || !o.event().equals(event)) return 0;
        try {
            var rt=QuestPlusMod.runtime();
            var p=rt.state(owner,config.scope()).quests.get(config.quests().getFirst());
            if(!config.advanceTiers() && p!=null && p.tier>configuredTier) return 0;
            return rt.storageRemaining(owner,config.scope(),boundTeam,config.quests().getFirst(),o.id(),context());
        } catch(Exception error) { return 0; }
    }
    private boolean accepts(ItemStack item) {
        var o=objective();
        if(item.isEmpty() || o==null || remaining("item_turn_in")<=0) return false;
        return ItemComponents.targets(o).stream().anyMatch(t->t.startsWith("#")
                ? item.is(net.minecraft.tags.TagKey.create(Registries.ITEM,Identifier.parse(t.substring(1))))
                : BuiltInRegistries.ITEM.getKey(item.getItem()).toString().equals(t))
                && ItemComponents.matches(JsonParser.parseString(o.data()).getAsJsonObject(),QuestItemStacks.components(item,level.registryAccess()));
    }
    int energyRatio() {
        var o=objective(); if(o==null) return 1;
        var data=JsonParser.parseString(o.data()).getAsJsonObject();
        return data.has("fePerEnergy") ? data.get("fePerEnergy").getAsInt() : 1;
    }
    private boolean hasResources() { return !stack.isEmpty() || fluids.amount>0 || energy>0; }
    void resourcesChanged() {
        if(persisting || level==null || level.isClientSide() || isRemoved() || moveToken!=null) return;
        try { persist(); status="ready"; QuestWorldBlocks.enqueue(this); }
        catch(IOException error) { status="storage_error"; QuestPlusMod.LOGGER.error("Block resource save failed at {}",worldPosition,error); }
        super.setChanged();
    }
    BlockAccount account(ItemStack item, long fluidAmount, long energyAmount) {
        var data=new JsonObject();
        if(owner!=null) data.addProperty("owner",owner.toString());
        if(boundTeam!=null) data.addProperty("team",boundTeam.toString());
        data.addProperty("config",config.encode()); data.addProperty("tier",configuredTier);
        var ops=RegistryOps.create(JsonOps.INSTANCE,level.registryAccess());
        data.add("item",ItemStack.OPTIONAL_CODEC.encodeStart(ops,item).getOrThrow());
        data.add("fluid",FluidVariant.CODEC.encodeStart(ops,fluids.variant).getOrThrow());
        data.addProperty("fluidAmount",fluidAmount); data.addProperty("energy",energyAmount);
        if(moveToken!=null)data.addProperty("moveToken",moveToken);
        return new BlockAccount(1,accountId(),level.dimension().identifier().toString(),worldPosition.asLong(),data);
    }
    private void restore(JsonObject data) {
        owner=data.has("owner") ? UUID.fromString(data.get("owner").getAsString()) : null;
        boundTeam=data.has("team") ? UUID.fromString(data.get("team").getAsString()) : null;
        config=QuestBlockConfig.parse(data.get("config").getAsString()); configuredTier=data.get("tier").getAsInt();
        var ops=RegistryOps.create(JsonOps.INSTANCE,level.registryAccess());
        stack=ItemStack.OPTIONAL_CODEC.parse(ops,data.get("item")).getOrThrow();
        fluids.variant=FluidVariant.CODEC.parse(ops,data.get("fluid")).getOrThrow();
        fluids.amount=data.get("fluidAmount").getAsLong(); energy=data.get("energy").getAsLong();
        moveToken=data.has("moveToken")?data.get("moveToken").getAsString():null;
        if(fluids.amount<0 || fluids.amount>FluidConstants.BUCKET || energy<0 || energy>1000000)
            throw new IllegalArgumentException("Invalid stored block resources");
    }
    void persist() throws IOException { QuestPlusMod.runtime().saveBlock(account(stack,fluids.amount,energy)); }
    void refresh() {
        if(owner==null) return;
        try {
            var rt=QuestPlusMod.runtime();
            if(config.scope()==ProgressScope.TEAM && (!Objects.equals(boundTeam,rt.player(owner).teamId)
                    || !rt.teams().get(boundTeam).sharedQuestProgress)) throw new IllegalArgumentException("team_unavailable");
            if(kind()==1) { process(); updateStationDisplay(); }
            else if(kind()==0) updateBoard();
            else updateRelay();
        } catch(Exception error) {
            status=error instanceof IOException ? "storage_error" : "blocked";
            if(kind()==2) setSignal(0);
            if(kind()==0 || kind()==1) publishRows(List.of());
        }
    }
    private void process() throws IOException {
        var o=objective(); if(o==null) { status="unconfigured"; return; }
        long remaining=remaining(o.event());
        if(remaining==0) { status=config.automation()?"blocked":"disabled"; return; }
        long amount= switch(o.event()) {
            case "item_turn_in"->accepts(stack) ? Math.min(64,stack.getCount()) : 0;
            case "fluid_submit"->BuiltInRegistries.FLUID.getKey(fluids.variant.getFluid()).toString().equals(o.target()) ? fluids.amount/81 : 0;
            case "energy_submit"->Math.multiplyExact(energy,(long)energyRatio()); default->0;
        };
        amount=Math.min(amount,remaining); if(amount<=0) { status="waiting"; return; }
        if(o.event().equals("energy_submit")) amount=amount/energyRatio()*energyRatio();
        if(amount==0) return;
        ItemStack after=stack.copy(); long fluidAfter=fluids.amount,energyAfter=energy;
        String target=o.target().startsWith("#") ? "minecraft:air" : o.target(); Set<String> tags=Set.of();
        if(o.event().equals("item_turn_in")) {
            target=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            tags=stack.typeHolder().tags().map(t->t.location().toString()).collect(java.util.stream.Collectors.toSet());
            after.shrink((int)amount);
        } else if(o.event().equals("fluid_submit")) fluidAfter-=amount*81;
        else energyAfter-=amount/energyRatio();
        var ctx=context(); ctx.put("submissionQuest",config.quests().getFirst()); ctx.put("submissionObjective",o.id());
        if(o.event().equals("item_turn_in")) ctx.put("itemComponents",QuestItemStacks.components(stack,level.registryAccess()));
        QuestPlusMod.runtime().submitStorage(owner,config.scope(),boundTeam,new QuestEvent(o.event(),target,tags,amount,ctx),
                account(after,fluidAfter,energyAfter));
        stack=after; fluids.amount=fluidAfter; energy=energyAfter;
        if(fluids.amount==0) fluids.variant=FluidVariant.blank();
        status="active"; nextRetry=level.getGameTime()+5; super.setChanged();
        setSignal((int)Math.min(15,Math.max(1,15-remaining(o.event())*15/Math.max(1,o.count()))));
    }
    private void updateStationDisplay() throws IOException {
        var o=objective();
        if(o==null) { publishRows(List.of()); return; }
        var rt=QuestPlusMod.runtime();var q=rt.registry().get(config.quests().getFirst());var p=rt.state(owner,config.scope());
        var v=p.quests.get(q.id());int tier=v==null?0:v.tier;
        long factor=q.progression().equals("tiered")?q.tiers().get(Math.min(tier,q.tiers().size()-1)).threshold():1;
        long target=Math.multiplyExact(o.count(),factor);
        long current=v!=null&&(v.completed||v.terminal)?target:Math.min(target,v==null?0:v.counters.getOrDefault(o.id(),0L));
        publishRows(List.of(boardRow(q,p),new Row(Localization.text(o.text(),"en_us",Map.of()),o.target(),
                Math.round(100.0*current/target)+"%",status,"",o.event(),(double)current/target,current+" / "+target)));
    }
    public boolean canConfigure(ServerPlayer actor) {
        if(actor.isSpectator() || actor.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(worldPosition))>64 || !level.mayInteract(actor,worldPosition)) return false;
        return canManage(actor);
    }
    private boolean canManage(ServerPlayer actor) {
        if(actor.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) || Objects.equals(owner,actor.getUUID())) return true;
        if(config.scope()!=ProgressScope.TEAM || boundTeam==null) return false;
        try { return QuestPlusMod.runtime().teams().get(boundTeam).role(actor.getUUID())!=TeamRole.MEMBER; }
        catch(IllegalArgumentException error) { return false; }
    }
    void configure(ServerPlayer actor, QuestBlockConfig value, UUID newOwner, int size) throws IOException {
        if(moveToken!=null)throw new IllegalArgumentException("Station is packed for relocation");
        if(!canConfigure(actor)) throw new IllegalArgumentException("Block configuration denied");
        if(newOwner==null) newOwner=owner;
        if(!Objects.equals(owner,newOwner) && !Objects.equals(owner,actor.getUUID())
                && !actor.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
            throw new IllegalArgumentException("Only owner/operator can transfer ownership");
        var rt=QuestPlusMod.runtime();
        UUID team=value.scope()==ProgressScope.TEAM ? rt.player(newOwner).teamId : null;
        if(value.scope()==ProgressScope.TEAM && (team==null || !rt.teams().get(team).sharedQuestProgress))
            throw new IllegalArgumentException("Shared progress unavailable");
        var visible=dev.questplus.clientdata.ClientProjection.visible(rt.registry().all(),rt.state(newOwner,value.scope()));
        for(String id:value.quests()) if(visible.stream().noneMatch(q->q.id().equals(id))) throw new IllegalArgumentException("Quest unavailable");
        if(kind()==1 && (value.quests().size()!=1 || rt.registry().get(value.quests().getFirst()).objectives().stream()
                .noneMatch(o->o.id().equals(value.objective()) && Set.of("item_turn_in","fluid_submit","energy_submit").contains(o.event()))))
            throw new IllegalArgumentException("Select one consuming objective");
        var parts=kind()==0&&QuestBoardParts.modular(getBlockState())?QuestBoardParts.connected(this):List.of(this);
        for(var part:parts)if(!part.canManage(actor)||!level.mayInteract(actor,part.worldPosition))throw new IllegalArgumentException("Connected board configuration denied");
        if(kind()==0&&QuestBoardParts.modular(getBlockState())&&!Objects.equals(owner,newOwner))QuestBoardParts.validateTransfer(parts,newOwner);
        if(kind()==0 && !QuestBoardParts.modular(getBlockState()) && size!=getBlockState().getValue(QuestUtilityBlock.SIZE)) resize(size);
        var old=config; UUID oldOwner=owner,oldTeam=boundTeam;
        QuestWorldBlocks.unloaded(this); config=value; owner=newOwner; boundTeam=team; generation=-1;
        var p=value.quests().isEmpty()?null:rt.state(owner,value.scope()).quests.get(value.quests().getFirst());
        configuredTier=p==null?0:p.tier;
        try { if(kind()==1) persist(); }
        catch(IOException error) { config=old;owner=oldOwner;boundTeam=oldTeam;QuestWorldBlocks.loaded(this);throw error; }
        if(kind()==2 && !old.output().equals(value.output())) {lastCondition=false;setSignal(0);}
        QuestWorldBlocks.loaded(this); super.setChanged(); refresh();
        if(kind()==0&&QuestBoardParts.modular(getBlockState())) {
            for(var part:parts)if(part!=this) {
                QuestWorldBlocks.unloaded(part);part.config=value;part.owner=newOwner;part.boundTeam=team;
                QuestWorldBlocks.loaded(part);part.setChanged();part.refresh();
            }
            QuestBoardParts.rebuild(this);
        }
    }
    private void resize(int size) {
        if(size<0 || size>3) throw new IllegalArgumentException("Invalid board size");
        var state=getBlockState(); int old=state.getValue(QuestUtilityBlock.SIZE); var facing=state.getValue(QuestUtilityBlock.FACING);
        if(!QuestUtilityBlock.room(level,worldPosition,facing,size,old)) throw new IllegalArgumentException("Not enough supported space");
        // Expand first; removing old tiles afterwards must not trigger multiblock destruction.
        resizing=true;
        try {
            QuestUtilityBlock.assemble(level,worldPosition,facing,size);
            for(int y=0;y<QuestWorldBlocks.HEIGHT[old];y++) for(int x=0;x<QuestWorldBlocks.WIDTH[old];x++)
                if(x>=QuestWorldBlocks.WIDTH[size] || y>=QuestWorldBlocks.HEIGHT[size])
                    level.setBlock(QuestUtilityBlock.at(worldPosition,facing,x,y),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2|16);
        } finally { resizing=false; }
    }
    private List<QuestDefinition> selected(PlayerProgress p) {
        var visible=dev.questplus.clientdata.ClientProjection.visible(QuestPlusMod.runtime().registry().all(),p);
        return visible.stream().filter(q->config.quests().contains(q.id()) || !config.category().isEmpty() && q.category().equals(config.category())).toList();
    }
    private void updateRelay() throws IOException {
        var rt=QuestPlusMod.runtime(); var p=rt.state(owner,config.scope()); var qs=selected(p);
        boolean unlocked=config.condition().equals("unlocked");
        var ctx=unlocked||config.condition().equals("stage")?context():Map.<String,Object>of();
        var complete=unlocked?QuestEngine.prerequisiteState(p):Set.<String>of();
        boolean match;
        double fraction;
        if(config.condition().equals("stage")) {
            match=ctx.get("stages") instanceof Set<?> stages && stages.contains(config.category()); fraction=match?1:0;
        } else {
            var values=qs.stream().mapToDouble(q->{var v=p.quests.get(q.id()); return switch(config.condition()) {
                case "unlocked" -> q.requires()==null || q.requires().matches(ctx,complete) ? 1 : 0;
                case "started" -> v!=null && (v.attemptId!=null || v.completed) ? 1 : 0;
                case "completed" -> v!=null && (v.completed || v.terminal) ? 1 : 0;
                case "claimed" -> rtClaimed(rt,q.id()) ? 1 : 0;
                case "tier" -> v==null?0:Math.min(1,(double)v.reachedTiers.size()/config.threshold());
                default -> 0;
            };}).toArray();
            match=values.length>0 && (config.all()?Arrays.stream(values).allMatch(v->v>=1):Arrays.stream(values).anyMatch(v->v>=1));
            fraction=qs.isEmpty()?0:config.condition().equals("tier")?Arrays.stream(values).average().orElse(0)
                    :qs.stream().mapToDouble(q->fraction(q,p)).average().orElse(0);
        }
        int out=config.output().equals("analog")?(int)Math.floor(Math.max(0,Math.min(1,fraction))*15):match?15:0;
        if(config.output().equals("pulse")) {
            if(match!=lastCondition) { setSignal(15);level.scheduleTick(worldPosition,QuestWorldBlocks.RELAY,4); }
        } else setSignal(out);
        if(lastCondition!=match)super.setChanged();
        lastCondition=match; status="ready";
    }
    private boolean rtClaimed(QuestRuntime rt,String quest) {
        try { var p=rt.player(owner);return p.entitlements.entrySet().stream().filter(e->e.getValue().questId().equals(quest))
                .anyMatch(e->"delivered".equals(p.claims.get(e.getKey()))); }
        catch(IOException e) { return false; }
    }
    static double fraction(QuestDefinition q,PlayerProgress p) {
        var v=p.quests.get(q.id()); if(v==null)return 0; if(v.completed||v.terminal)return 1;
        long factor=q.progression().equals("tiered")?q.tiers().get(Math.min(v.tier,q.tiers().size()-1)).threshold():1;
        return q.objectives().stream().filter(o->!o.optional()).mapToDouble(o->Math.min(1,(double)v.counters.getOrDefault(o.id(),0L)/(o.count()*factor))).average().orElse(0);
    }
    private void updateBoard() throws IOException {
        if(QuestBoardParts.modular(getBlockState())&&boardWidth==0)return;
        var rt=QuestPlusMod.runtime(); var p=rt.state(owner,config.scope()); var result=new ArrayList<Row>();
        var ranking=QuestWorldBlocks.ranking((ServerLevel)level,config.leaderboard());
        result.addAll(ranking);
        boolean leadersChanged=!leaders.equals(ranking);leaders=ranking;
        if(!config.boardMode().equals("leaderboard")) {
            result.clear();
            var visible=dev.questplus.clientdata.ClientProjection.visible(rt.registry().all(),p);
            var order=new LinkedHashSet<QuestDefinition>();
            if(!config.boardMode().equals("automatic"))for(String id:config.quests())
                visible.stream().filter(q->q.id().equals(id)).findFirst().ifPresent(order::add);
            if(!config.boardMode().equals("pinned")) for(var q:visible) {
                var v=p.quests.get(q.id()); if(v!=null && v.attemptId!=null && !v.terminal && !v.completed) order.add(q);
            }
            for(var q:order) {
                var v=p.quests.get(q.id()); double f=fraction(q,p);
                if(!config.category().isEmpty()&&!config.category().equals(q.category()))continue;
                if(!config.all()&&v!=null&&(v.completed||v.terminal))continue;
                result.add(boardRow(q,p));
                if(result.size()==32)break;
            }
        }
        publishRows(result);
        if(leadersChanged) {contentRevision++;level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
        status="ready";
    }
    Row boardRow(QuestDefinition q,PlayerProgress p) {
        var v=p.quests.get(q.id());double f=fraction(q,p);
        int tier=v==null?0:v.tier;
        long factor=q.progression().equals("tiered")?q.tiers().get(Math.min(tier,q.tiers().size()-1)).threshold():1;
        long target=q.objectives().stream().filter(o->!o.optional()).mapToLong(o->o.count()*factor).sum();
        long current=v!=null&&(v.completed||v.terminal)?target:q.objectives().stream().filter(o->!o.optional())
                .mapToLong(o->Math.min(o.count()*factor,v==null?0:v.counters.getOrDefault(o.id(),0L))).sum();
        var category=QuestPlusMod.runtime().categories().stream().filter(c->c.id().equals(q.category())).findFirst();
        String detail=category.map(c->Localization.text(c.title(),"en_us",Map.of())).orElse(q.category())+" · Tier "+(tier+1);
        String title=Localization.text(q.title(),"en_us",Map.of());
        return new Row(title.substring(0,Math.min(128,title.length())),q.icon(),Math.round(f*100)+"%",
                v!=null&&(v.completed||v.terminal)?"completed":f>0?"started":"available","",detail.substring(0,Math.min(96,detail.length())),f,current+" / "+target);
    }
    private void publishRows(List<Row> value) {
        if(rows.equals(value))return; rows=List.copyOf(value);contentRevision++;
        level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);
    }
    void setSignal(int value) {
        value=Math.max(0,Math.min(15,value)); if(signal==value)return;signal=value;super.setChanged();
        if(kind()==2)level.setBlock(worldPosition,getBlockState().setValue(QuestUtilityBlock.POWERED,value>0),2);
        level.updateNeighborsAt(worldPosition,getBlockState().getBlock());
        level.updateNeighbourForOutputSignal(worldPosition,getBlockState().getBlock());
    }
    @Override protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if(owner!=null)out.putString("owner",owner.toString());
        if(boundTeam!=null)out.putString("team",boundTeam.toString());
        out.putString("config",config.encode());out.putInt("signal",signal);out.putBoolean("last",lastCondition);
        if(kind()==0) {out.putInt("boardWidth",boardWidth);out.putInt("boardHeight",boardHeight);out.putLong("boardAnchor",boardAnchor.asLong());}
    }
    @Override protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        owner=in.getString("owner").map(UUID::fromString).orElse(null);boundTeam=in.getString("team").map(UUID::fromString).orElse(null);
        config=QuestBlockConfig.parse(in.getStringOr("config",QuestBlockConfig.DEFAULT.encode()));
        signal=Math.max(0,Math.min(15,in.getIntOr("signal",0)));lastCondition=in.getBooleanOr("last",false);
        String encoded=in.getStringOr("boardRows","[]");
        if(encoded.length()<=524288) rows=List.of(QuestBlockConfig.JSON.fromJson(encoded,Row[].class));
        String ranking=in.getStringOr("boardLeaders","[]");
        if(ranking.length()<=524288)leaders=List.of(QuestBlockConfig.JSON.fromJson(ranking,Row[].class));
        contentRevision++;
        boardWidth=in.getIntOr("boardWidth",1);boardHeight=in.getIntOr("boardHeight",1);
        boardAnchor=BlockPos.of(in.getLongOr("boardAnchor",worldPosition.asLong()));
    }
    @Override public net.minecraft.nbt.CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag=new net.minecraft.nbt.CompoundTag();tag.putString("boardRows",QuestBlockConfig.JSON.toJson(rows));
        tag.putInt("boardWidth",boardWidth);tag.putInt("boardHeight",boardHeight);tag.putLong("boardAnchor",boardAnchor.asLong());
        tag.putString("boardLeaders",QuestBlockConfig.JSON.toJson(leaders));tag.putString("config",config.encode());return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState next) {
        if(kind()==1 && moveToken==null && level!=null && !level.isClientSide() && initialized && !next.is(QuestWorldBlocks.STATION)) {
            // Commit the empty account before returning buffered resources to the world.
            try {
                var drop=stack.copy();QuestPlusMod.runtime().saveBlock(account(ItemStack.EMPTY,fluids.amount,energy));
                stack=ItemStack.EMPTY;
                if(!drop.isEmpty())net.minecraft.world.level.block.Block.popResource(level,worldPosition,drop);
            } catch(IOException e) { QuestPlusMod.LOGGER.error("Retained station account for recovery at {}",worldPosition,e); }
        }
        super.preRemoveSideEffects(pos,next);
    }
    boolean insertHeld(ServerPlayer actor,InteractionHand hand,Direction side) {
        if(actor.isCreative()||actor.isSpectator()||!input(side))return false;
        if(net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorageUtil.interactWithFluidStorage(fluids,actor,hand))return true;
        var held=actor.getItemInHand(hand);
        try(var tx=Transaction.openOuter()) {
            long count=items.insert(ItemVariant.of(held),held.getCount(),tx);
            if(count<=0)return false;held.shrink((int)count);tx.commit();return true;
        }
    }
    @Override public int getContainerSize() { return kind()==1?1:0; }
    @Override public boolean isEmpty() { return stack.isEmpty(); }
    @Override public ItemStack getItem(int slot) { return slot==0?stack:ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot,int count) {
        if(slot!=0 || count<=0 || moveToken!=null)return ItemStack.EMPTY;var result=stack.split(count);setChanged();return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return removeItem(slot,64); }
    @Override public void setItem(int slot,ItemStack value) { if(slot==0){stack=value;setChanged();} }
    @Override public void setChanged() { super.setChanged(); if(kind()==1 && initialized && !persisting)resourcesChanged(); }
    @Override public boolean stillValid(Player player) { return player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(worldPosition))<=64 && !isRemoved(); }
    @Override public boolean canPlaceItem(int slot,ItemStack value) { return slot==0 && accepts(value); }
    @Override public int getMaxStackSize() { return (int)Math.min(64,remaining("item_turn_in")); }
    @Override public int[] getSlotsForFace(Direction side) { return input(side)?new int[]{0}:new int[0]; }
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack value,Direction side) { return input(side)&&canPlaceItem(slot,value); }
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack value,Direction side) { return input(side)&&!config.automation(); }
    @Override public void clearContent() { stack=ItemStack.EMPTY;setChanged(); }
}
