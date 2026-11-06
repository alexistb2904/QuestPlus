package dev.questplus.core;

import java.util.*;
import java.util.function.LongSupplier;

public final class QuestEngine {
    private static final Set<String> SUBMISSION_EVENTS=Set.of(
        "item_submit","item_turn_in","xp_submit_levels","xp_submit_points","fluid_submit","energy_submit");
    private static final Set<String> CONTEXT_STATE_EVENTS=Set.of(
        "xp_levels","xp_points","custom_stat","advancement_complete","observe_block","observe_entity","stage");

    public record Completion(String questId,String key,List<QuestDefinition.Reward> rewards) {
        public Completion { rewards=RewardTables.resolve(rewards); }
    }
    private final QuestRegistry registry;
    private final LongSupplier clock;
    public QuestEngine(QuestRegistry registry) { this(registry,System::currentTimeMillis); }
    public QuestEngine(QuestRegistry registry,LongSupplier clock) { this.registry=Objects.requireNonNull(registry); this.clock=Objects.requireNonNull(clock); }

    public List<String> apply(PlayerProgress player,QuestEvent event) { return applyDetailed(player,event).stream().map(Completion::questId).toList(); }
    public List<Completion> applyDetailed(PlayerProgress player,QuestEvent event) {
        if (player.recentEvents==null) player.recentEvents=new LinkedHashSet<>();
        if (!player.recentEvents.add(event.eventId())) return List.of();
        if (player.recentEvents.size()>256) player.recentEvents.remove(player.recentEvents.iterator().next());
        if (event.type().equals("player_death")) {
            player.quests.forEach((id,p) -> registry.find(id).filter(q -> q.noDeath() && !terminal(q,p) && !p.waitingRestart && p.attemptId!=null).ifPresent(q -> {
                newAttempt(p); player.revision++;
            }));
            return List.of();
        }
        long now=clock.getAsLong(); Set<String> completed=prerequisiteState(player);
        Map<String,Integer> sequenceSteps=new HashMap<>();
        Set<String> touched=new LinkedHashSet<>();
        for (var candidate:registry.candidates(event)) {
            var q=candidate.quest(); var objective=candidate.objective(); var p=player.quests.get(q.id());
            if(!QuestExtensions.matchesObjective(objective,event))continue;
            if (SUBMISSION_EVENTS.contains(event.type())) {
                if (!q.id().equals(event.context().get("submissionQuest")) || !objective.id().equals(event.context().get("submissionObjective"))) continue;
            }
            if(event.type().equals("manual") && event.context().containsKey("manualQuest")
                && (!q.id().equals(event.context().get("manualQuest")) || !objective.id().equals(event.context().get("manualObjective"))))continue;
            if ((p!=null && (terminal(q,p) || p.waitingRestart)) || diagnostic(player,q.id()).isPresent()
                || (q.requires()!=null && !q.progressBeforeUnlock() && !q.requires().matches(event.context(),completed))) continue;
            if (p!=null && p.attemptId==null && p.startedAt>0 && now-p.startedAt<q.cooldownSeconds()*1000) continue;
            if (p!=null && q.timeLimitSeconds()>0 && p.attemptId!=null && now-p.startedAt>=q.timeLimitSeconds()*1000) { newAttempt(p); player.revision++; }
            if (objective.conditions()!=null && !objective.conditions().matches(event.context(),completed)) continue;
            boolean state=QuestExtensions.event(objective.event()).state();
            long amount=event.amount();
            if (state) {
                Object values=CONTEXT_STATE_EVENTS.contains(objective.event())?event.context().get("stateValues"):event.context().get("inventory");
                amount=values instanceof Map<?,?> m && m.get(objective.target()) instanceof Number n ? Math.max(0,n.longValue()) : 0;
                if(objective.event().equals("inventory_count"))amount=ItemComponents.inventoryCount(objective,event.context(),amount);
            }
            if (p==null && amount==0) continue;
            if (q.objectiveMode().equals("sequence") && !objective.optional()) {
                final var sequenceState=p;
                int step=sequenceSteps.computeIfAbsent(q.id(),ignored -> sequenceState==null ? 0 : firstIncomplete(q,sequenceState));
                if (step>=q.objectives().size() || q.objectives().get(step)!=objective) continue;
            }
            if (p==null) { p=fresh(q); player.quests.put(q.id(),p); }
            if (p.attemptId==null) { p.attemptId=UUID.randomUUID().toString(); p.startedAt=now; player.revision++; }
            long maximum=q.progression().equals("tiered") ? objective.count()*q.tiers().getLast().threshold() : objective.count();
            long old=p.counters.getOrDefault(objective.id(),0L);
            long next=state ? Math.min(amount,maximum) : old+Math.min(amount,Math.max(0,maximum-old));
            if (next!=old) { p.counters.put(objective.id(),next); player.revision++; }
            long cumulative=p.cumulative.getOrDefault(objective.id(),0L);
            long nextCumulative=state ? Math.max(cumulative,next) : cumulative+Math.min(amount,1_000_000_000_000L-cumulative);
            if (nextCumulative!=cumulative) { p.cumulative.put(objective.id(),nextCumulative); player.revision++; }
            touched.add(q.id());
        }
        List<Completion> result=new ArrayList<>();
        Set<String> evaluated=new HashSet<>();
        while(!touched.isEmpty()){
            List<String> batch=List.copyOf(touched);
            touched.clear();
            int previousAwards=result.size();
            for(String id:batch){
                if(evaluated.contains(id))continue;
                var q=registry.get(id);
                var p=player.quests.get(id);
                if(p==null || terminal(q,p) || p.waitingRestart)continue;
                // Evaluate the prerequisite state only once for this quest. refreshStates
                // updates counters but cannot create/remove a started/completed marker.
                Set<String> unlocks=prerequisiteState(player);
                refreshStates(player,q,p,event,unlocks,sequenceSteps.getOrDefault(id,Integer.MAX_VALUE));
                // Flexible quests can track progress while locked but never grant rewards early.
                if(q.requires()!=null && !q.requires().matches(event.context(),unlocks))continue;
                evaluated.add(id);
                if(q.progression().equals("tiered")){
                    while(p.tier<q.tiers().size() && satisfied(q,p,q.tiers().get(p.tier).threshold())){
                        var tier=q.tiers().get(p.tier++);p.reachedTiers.add(tier.id());p.completed=true;
                        result.add(new Completion(id,id+"|"+p.attemptId+"|"+tier.id(),tier.rewards()));player.revision++;
                    }
                    if(p.tier==q.tiers().size()){
                        if(q.afterLast().equals("repeat")){p.cycles++;newAttempt(p);p.startedAt=now;}
                        else p.terminal=true;
                    }
                }else if(isSatisfied(q,p)){
                    p.completed=true;p.cycles++;
                    String key=q.progression().equals("once")?id:id+"|"+p.attemptId;
                    result.add(new Completion(id,key,q.rewards()));player.revision++;
                    switch(q.progression()){
                        case "once" -> p.terminal=true;
                        case "repeatable" -> {p.waitingRestart=true;p.startedAt=now;}
                        case "infinite" -> {newAttempt(p);p.startedAt=now;}
                        default -> throw new IllegalStateException("Invalid progression");
                    }
                }
            }
            // Grant cascades in the same authoritative transaction: completing a prerequisite
            // can unlock already-satisfied flexible quests without another gameplay event.
            if(result.size()>previousAwards){
                var unlocked=prerequisiteState(player);
                for(var q:registry.all()){
                    if(!q.progressBeforeUnlock()||evaluated.contains(q.id()))continue;
                    var p=player.quests.get(q.id());
                    if(p==null||terminal(q,p)||p.waitingRestart||diagnostic(player,q.id()).isPresent())continue;
                    if(q.requires()!=null && !q.requires().matches(event.context(),unlocked))continue;
                    if(isSatisfied(q,p))touched.add(q.id());
                }
            }
        }
        return List.copyOf(result);
    }
    private static void refreshStates(PlayerProgress owner,QuestDefinition q,PlayerProgress.QuestProgress p,QuestEvent event,Set<String> completed,int accessibleStep) {
        var context=event.context();
        Map<?,?> inventory=context.get("inventory") instanceof Map<?,?> m?m:Map.of();
        Map<?,?> states=context.get("stateValues") instanceof Map<?,?> m?m:Map.of();
        for (int index=0;index<q.objectives().size();index++) {
            var objective=q.objectives().get(index);
            boolean isInventory=objective.event().equals("inventory_count");
            if ((!isInventory&&!CONTEXT_STATE_EVENTS.contains(objective.event())) || (q.objectiveMode().equals("sequence") && !objective.optional() && index>accessibleStep)) continue;
            if(!isInventory&&!objective.event().equals(event.type()))continue;
            Map<?,?> values=isInventory?inventory:states;
            if(!values.containsKey(objective.target())&&!(isInventory&&!objective.data().equals("{}")&&context.get("itemStacks") instanceof List<?>))continue;
            Object value=values.get(objective.target());
            long count=value instanceof Number n ? Math.max(0,n.longValue()) : 0;
            if(isInventory)count=ItemComponents.inventoryCount(objective,context,count);
            if (objective.conditions()!=null && !objective.conditions().matches(context,completed)) count=0;
            long maximum=objective.count()*(q.progression().equals("tiered") ? q.tiers().getLast().threshold() : 1);
            long next=Math.min(maximum,count);
            if (p.counters.getOrDefault(objective.id(),0L)!=next) owner.revision++;
            p.counters.put(objective.id(),next);
        }
    }
    public static boolean isSatisfied(QuestDefinition q,PlayerProgress.QuestProgress p) {
        return satisfied(q,p,q.progression().equals("tiered") ? q.tiers().get(Math.min(p.tier,q.tiers().size()-1)).threshold() : 1);
    }
    private static boolean satisfied(QuestDefinition q,PlayerProgress.QuestProgress p,long factor) {
        int successes=0,required=0;
        for(var objective:q.objectives()){
            if(objective.optional())continue;
            required++;
            if(p.counters.getOrDefault(objective.id(),0L)>=objective.count()*factor)successes++;
        }
        return switch(q.objectiveMode()){
            case "any" -> successes>=1;
            case "at_least" -> successes>=q.objectiveThreshold();
            default -> successes==required;
        };
    }
    private static int firstIncomplete(QuestDefinition q,PlayerProgress.QuestProgress p) {
        long factor=q.progression().equals("tiered") ? q.tiers().get(Math.min(p.tier,q.tiers().size()-1)).threshold() : 1;
        for (int i=0;i<q.objectives().size();i++) { var o=q.objectives().get(i); if (!o.optional() && p.counters.getOrDefault(o.id(),0L)<o.count()*factor) return i; }
        return q.objectives().size();
    }
    private static void newAttempt(PlayerProgress.QuestProgress p) { p.counters.clear(); p.tier=0; p.attemptId=null; p.startedAt=0; p.waitingRestart=false; }
    public void restart(PlayerProgress player,String id) {
        var q=registry.get(id); var p=player.quests.get(id);
        if (!q.progression().equals("repeatable") || p==null || !p.waitingRestart) throw new IllegalArgumentException("Quest is not awaiting restart");
        if (clock.getAsLong()-p.startedAt<q.cooldownSeconds()*1000) throw new IllegalArgumentException("Quest cooldown has not elapsed");
        newAttempt(p); player.revision++;
    }
    public Optional<String> diagnostic(PlayerProgress player,String id) {
        var q=registry.get(id); var p=player.quests.get(id);
        return p!=null && !terminal(q,p) && !q.fingerprint().equals(p.fingerprint)
            ? Optional.of("Quest objectives changed for "+id+"; existing progress retained") : Optional.empty();
    }
    public Completion grantDetailed(PlayerProgress player,String id) {
        var q=registry.get(id); if (diagnostic(player,id).isPresent()) throw new IllegalStateException(diagnostic(player,id).get());
        var p=player.quests.computeIfAbsent(id,ignored -> fresh(q));
        if (p.attemptId==null) p.attemptId=UUID.randomUUID().toString();
        q.objectives().forEach(o -> p.counters.put(o.id(),o.count())); p.completed=true;
        if (q.progression().equals("once")) p.terminal=true; else if (q.progression().equals("repeatable")) p.waitingRestart=true;
        player.revision++;
        return new Completion(id,q.progression().equals("once") ? id : id+"|"+p.attemptId,q.rewards());
    }
    public void grant(PlayerProgress player,String id) { grantDetailed(player,id); }
    public void reset(PlayerProgress player,String id) { registry.get(id); player.quests.remove(id); player.revision++; }
    public PlayerProgress.QuestProgress progress(PlayerProgress player,String id) { registry.get(id); return player.quests.get(id); }
    private static boolean terminal(QuestDefinition q,PlayerProgress.QuestProgress p) { return p.terminal || (q.progression().equals("once") && p.completed); }
    private static PlayerProgress.QuestProgress fresh(QuestDefinition q) { var p=new PlayerProgress.QuestProgress(); p.fingerprint=q.fingerprint(); return p; }
    public static Set<String> prerequisiteState(PlayerProgress player) {
        Set<String> result=new HashSet<>();
        player.quests.forEach((id,p)->{
            if(p.completed)result.add(id);
            boolean started=p.completed||p.attemptId!=null||p.startedAt>0;
            if(!started)for(long count:p.counters.values())if(count>0){started=true;break;}
            if(started)result.add("@started:"+id);
        });
        return result;
    }
}
