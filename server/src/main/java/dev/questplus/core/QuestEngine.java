package dev.questplus.core;

import java.util.*;
import java.util.function.LongSupplier;

public final class QuestEngine {
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
        long now=clock.getAsLong(); Set<String> completed=completed(player);
        Map<String,Integer> sequenceSteps=new HashMap<>();
        Set<String> touched=new LinkedHashSet<>();
        for (var candidate:registry.candidates(event)) {
            var q=candidate.quest(); var objective=candidate.objective(); var p=player.quests.get(q.id());
            if (event.type().equals("item_submit") || event.type().equals("item_turn_in")) {
                if (!q.id().equals(event.context().get("submissionQuest")) || !objective.id().equals(event.context().get("submissionObjective"))) continue;
            }
            if ((p!=null && (terminal(q,p) || p.waitingRestart)) || diagnostic(player,q.id()).isPresent()
                || (q.requires()!=null && !q.requires().matches(event.context(),completed))) continue;
            if (p!=null && p.attemptId==null && p.startedAt>0 && now-p.startedAt<q.cooldownSeconds()*1000) continue;
            if (p!=null && q.timeLimitSeconds()>0 && p.attemptId!=null && now-p.startedAt>=q.timeLimitSeconds()*1000) { newAttempt(p); player.revision++; }
            if (objective.conditions()!=null && !objective.conditions().matches(event.context(),completed)) continue;
            boolean state=QuestExtensions.event(objective.event()).state();
            long amount=event.amount();
            if (state) {
                Object values=Set.of("xp_levels","custom_stat").contains(objective.event())?event.context().get("stateValues"):event.context().get("inventory");
                amount=values instanceof Map<?,?> m && m.get(objective.target()) instanceof Number n ? Math.max(0,n.longValue()) : 0;
            }
            if (p==null && amount==0) continue;
            if (q.objectiveMode().equals("sequence")) {
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
        for (String id:touched) {
            var q=registry.get(id); var p=player.quests.get(id);
            refreshStates(player,q,p,event.context(),completed,sequenceSteps.getOrDefault(id,Integer.MAX_VALUE));
            if (q.progression().equals("tiered")) {
                while (p.tier<q.tiers().size() && satisfied(q,p,q.tiers().get(p.tier).threshold())) {
                    var tier=q.tiers().get(p.tier++); p.reachedTiers.add(tier.id()); p.completed=true;
                    result.add(new Completion(id,id+"|"+p.attemptId+"|"+tier.id(),tier.rewards())); player.revision++;
                }
                if (p.tier==q.tiers().size()) {
                    if (q.afterLast().equals("repeat")) { p.cycles++; newAttempt(p); p.startedAt=now; }
                    else p.terminal=true;
                }
            } else if (isSatisfied(q,p)) {
                p.completed=true; p.cycles++;
                String key=q.progression().equals("once") ? id : id+"|"+p.attemptId;
                result.add(new Completion(id,key,q.rewards())); player.revision++;
                switch (q.progression()) {
                    case "once" -> p.terminal=true;
                    case "repeatable" -> { p.waitingRestart=true; p.startedAt=now; }
                    case "infinite" -> { newAttempt(p); p.startedAt=now; }
                    default -> throw new IllegalStateException("Invalid progression");
                }
            }
        }
        return List.copyOf(result);
    }
    private static void refreshStates(PlayerProgress owner,QuestDefinition q,PlayerProgress.QuestProgress p,Map<String,Object> context,Set<String> completed,int accessibleStep) {
        Map<?,?> inventory=context.get("inventory") instanceof Map<?,?> m?m:Map.of();
        Map<?,?> states=context.get("stateValues") instanceof Map<?,?> m?m:Map.of();
        for (int index=0;index<q.objectives().size();index++) {
            var objective=q.objectives().get(index);
            boolean isInventory=objective.event().equals("inventory_count");
            if ((!isInventory&&!Set.of("xp_levels","custom_stat").contains(objective.event())) || (q.objectiveMode().equals("sequence") && index>accessibleStep)) continue;
            Map<?,?> values=isInventory?inventory:states;
            if(!values.containsKey(objective.target()))continue;
            Object value=values.get(objective.target());
            long count=value instanceof Number n ? Math.max(0,n.longValue()) : 0;
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
        long successes=q.objectives().stream().filter(o->!o.optional())
            .filter(o->p.counters.getOrDefault(o.id(),0L)>=o.count()*factor).count();
        long required=q.objectives().stream().filter(o->!o.optional()).count();
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
    private static Set<String> completed(PlayerProgress player) { Set<String> result=new HashSet<>(); player.quests.forEach((id,p) -> { if (p.completed) result.add(id); }); return result; }
}
