package dev.questplus.core;

import com.google.gson.*;
import java.util.*;

/** Local administrator migration only. It never sends player data through Studio or grants live rewards. */
public final class FtbProgressMigration {
    private static final Gson JSON=new Gson();
    public record Result(PlayerProgress state,PlayerProgress personal,Map<String,String> sharedClaims,int quests,List<String> notices) {}
    private FtbProgressMigration() {}
    public static Result prepare(QuestRegistry registry,JsonObject source,JsonArray mappings,PlayerProgress currentState,PlayerProgress currentPersonal,
            UUID player,UUID teamScope,Map<String,List<QuestDefinition.Reward>> rolls,long now) {
        if(mappings.size()>10000)throw new IllegalArgumentException("Too many FTB mappings");
        if(source.has("rewards_blocked")&&source.get("rewards_blocked").getAsBoolean())throw new IllegalArgumentException("Unblock/review FTB rewards before migration");
        var tasks=numericMap(source,"task_progress");var completed=numericMap(source,"completed");var started=numericMap(source,"started");
        var repeats=numericMap(source,"repeatable");var cycles=numericMap(source,"completion_count");
        var claimed=source.has("claimed_rewards")?source.getAsJsonObject("claimed_rewards"):new JsonObject();
        var state=JSON.fromJson(JSON.toJson(currentState),PlayerProgress.class);var personal=JSON.fromJson(JSON.toJson(currentPersonal),PlayerProgress.class);
        var shared=new HashMap<String,String>();var notices=new ArrayList<String>();var seen=new HashSet<String>();int count=0;
        for(var value:mappings) {
            var mapping=value.getAsJsonObject();String sourceId=hex(mapping.get("sourceQuest").getAsString()),target=mapping.get("targetQuest").getAsString();
            if(!seen.add(target))throw new IllegalArgumentException("Duplicate FTB target mapping");
            var quest=registry.get(target);var taskMappings=mapping.getAsJsonArray("tasks");
            boolean done=completed.getOrDefault(sourceId,0L)>0;
            boolean active=done||started.containsKey(sourceId)||taskMappings.asList().stream().anyMatch(v->tasks.containsKey(hex(v.getAsJsonObject().get("sourceId").getAsString())));
            if(!active)continue;
            if(state.quests.containsKey(target)||personal.entitlements.values().stream().anyMatch(g->g.questId().equals(target))||personal.claims.keySet().stream().anyMatch(k->k.equals(target)||k.startsWith(target+"|")))
                throw new IllegalArgumentException("Target already has progress/rewards for "+target+"; migration would risk duplicating rewards");
            if(!Set.of("once","repeatable").contains(quest.progression()))throw new IllegalArgumentException("FTB progression cannot map to "+quest.progression());
            if(taskMappings.size()!=quest.objectives().size())throw new IllegalArgumentException("Missing source task IDs for "+target);
            var progress=new PlayerProgress.QuestProgress();progress.fingerprint=quest.fingerprint();progress.completed=done;progress.terminal=done&&quest.progression().equals("once");
            progress.attemptId=UUID.randomUUID().toString();progress.startedAt=Math.max(0,started.getOrDefault(sourceId,now));progress.cycles=cycles.getOrDefault(sourceId,done?1L:0L);
            var objectiveIds=new HashSet<String>();
            for(var task:taskMappings) {
                var item=task.getAsJsonObject();String id=item.get("objectiveId").getAsString();
                var objective=quest.objectives().stream().filter(o->o.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown mapped objective"));
                if(!objectiveIds.add(id))throw new IllegalArgumentException("Duplicate mapped objective");
                long amount=done?objective.count():Math.min(objective.count(),tasks.getOrDefault(hex(item.get("sourceId").getAsString()),0L));
                progress.counters.put(id,amount);progress.cumulative.put(id,amount);
            }
            if(done&&quest.progression().equals("repeatable")) {
                progress.waitingRestart=true;progress.startedAt=Math.max(0,repeats.getOrDefault(sourceId,now+quest.cooldownSeconds()*1000)-quest.cooldownSeconds()*1000);
            }
            state.quests.put(target,progress);count++;
            if(!done)continue;
            var rewardMappings=mapping.getAsJsonArray("rewards");if(rewardMappings.size()!=quest.rewards().size())throw new IllegalArgumentException("Missing reward mappings for "+target);
            var rewardIndices=new HashSet<Integer>();
            for(var rewardValue:rewardMappings) {
                var rewardMapping=rewardValue.getAsJsonObject();if(rewardMapping.get("sourceId").isJsonNull())throw new IllegalArgumentException("Missing source reward ID for "+target);
                int index=rewardMapping.get("index").getAsBigDecimal().intValueExact();if(index<0||index>=quest.rewards().size()||!rewardIndices.add(index))throw new IllegalArgumentException("Invalid reward index");
                String rewardId=hex(rewardMapping.get("sourceId").getAsString()),key=target+"|ftb:"+rewardId+"|"+progress.cycles;
                var authored=quest.rewards().get(index);boolean team=authored.policy().teamReward();
                String owner=(team?new UUID(0,0):player).toString().replace("-","");
                boolean delivered=claimed.entrySet().stream().anyMatch(e->e.getKey().equalsIgnoreCase(owner+":"+rewardId)||e.getKey().equalsIgnoreCase(rewardId));
                if(delivered){personal.claims.put(key,"delivered");if(team&&teamScope!=null)shared.put(key,"delivered");continue;}
                String rollKey=team?key:key+"|"+player;
                var rewards=rolls.computeIfAbsent(rollKey,ignored->RewardTables.resolve(List.of(authored))).stream().map(r->{
                    var policy=r.policy();return team&&teamScope!=null?r.withPolicy(new QuestDefinition.RewardPolicy(true,policy.autoClaim(),policy.excludeFromClaimAll(),teamScope)):r;
                }).toList();
                personal.entitle(key,new PlayerProgress.RewardGrant(target,rewards));
            }
        }
        if(teamScope==null)personal.quests=state.quests;
        state.validate();personal.validate();
        notices.add(count+" quests mapped using current server fingerprints; completed rewards already claimed in FTB are never delivered again");
        return new Result(state,personal,Map.copyOf(shared),count,List.copyOf(notices));
    }
    private static Map<String,Long> numericMap(JsonObject source,String field) {
        var result=new HashMap<String,Long>();if(!source.has(field))return result;
        if(!source.get(field).isJsonObject())throw new IllegalArgumentException("Unsupported FTB "+field+" representation");
        for(var entry:source.getAsJsonObject(field).entrySet()) {
            long value=entry.getValue().getAsBigDecimal().longValueExact();if(value<0)throw new IllegalArgumentException("Negative FTB progress");
            String raw=entry.getKey();String key=raw.matches("-?[0-9]+")?String.format("%016x",new java.math.BigInteger(raw).longValueExact()):hex(raw);
            if(result.putIfAbsent(key,value)!=null)throw new IllegalArgumentException("Duplicate FTB progress ID");
        }
        return result;
    }
    private static String hex(String value){if(!value.matches("[a-fA-F0-9]{1,16}"))throw new IllegalArgumentException("Invalid FTB source ID");return "0".repeat(16-value.length())+value.toLowerCase(Locale.ROOT);}
}
