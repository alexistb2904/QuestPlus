package dev.questplus.core;
import java.util.*;
public final class PlayerProgress {
    public int schemaVersion = 2;
    public Map<String, QuestProgress> quests = new HashMap<>();
    public transient LinkedHashSet<UUID> recentEvents = new LinkedHashSet<>();
    public transient long revision;
    public Map<String, String> claims = new HashMap<>();
    public UUID teamId;
    public Map<String,RewardGrant> entitlements = new HashMap<>();
    public Set<String> legacyUnclaimed = new HashSet<>();
    public transient boolean migratedFromV1;
    public record RewardGrant(String questId,List<QuestDefinition.Reward> rewards) {}
    public static final class QuestProgress {
        public Map<String,Long> counters = new HashMap<>();
        public boolean completed;
        public String fingerprint;
        public Map<String,Long> cumulative = new HashMap<>();
        public long cycles;
        public int tier;
        public String attemptId;
        public long startedAt;
        public boolean waitingRestart;
        public boolean terminal;
        public Set<String> reachedTiers = new HashSet<>();
    }
    public void validate() {
        if (schemaVersion != 2 || quests == null || claims == null || entitlements == null || legacyUnclaimed == null) throw new IllegalArgumentException("Invalid player progress");
        quests.forEach((id,p) -> {
            if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || p == null || p.counters == null || p.fingerprint == null || p.fingerprint.isBlank()) throw new IllegalArgumentException("Invalid quest progress: " + id);
            p.counters.forEach((k,v) -> { if (k == null || k.isBlank() || v == null || v < 0 || v > 1_000_000_000L) throw new IllegalArgumentException("Invalid counter"); });
            if (p.cumulative == null || p.reachedTiers == null || p.cycles < 0 || p.tier < 0 || p.startedAt < 0) throw new IllegalArgumentException("Invalid cycle state");
            p.cumulative.forEach((k,v) -> { if (k == null || k.isBlank() || v == null || v < 0 || v > 1_000_000_000_000L) throw new IllegalArgumentException("Invalid cumulative counter"); });
            if (p.attemptId != null) UUID.fromString(p.attemptId);
            if (p.reachedTiers.stream().anyMatch(t -> t == null || !t.matches("[a-z0-9_.-]+"))) throw new IllegalArgumentException("Invalid tier id");
        });
        claims.forEach((k,v) -> { if (k == null || k.isBlank() || !Set.of("reserved", "delivered").contains(v)) throw new IllegalArgumentException("Invalid claim"); });
        entitlements.forEach((key,grant) -> {
            if (key == null || key.isBlank() || grant == null || grant.questId() == null || !grant.questId().matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || grant.rewards() == null || grant.rewards().size()>64)
                throw new IllegalArgumentException("Invalid entitlement");
            grant.rewards().forEach(QuestExtensions::validateSavedReward);
        });
        if (legacyUnclaimed.stream().anyMatch(id -> id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))) throw new IllegalArgumentException("Invalid legacy entitlement");
    }
}
