package dev.questplus.core;

import java.util.*;

public final class PlayerProgress {
	public int schemaVersion = 2;
	public Map<String, QuestProgress> quests = new HashMap<>();
	public transient LinkedHashSet<UUID> recentEvents = new LinkedHashSet<>();
	public transient long revision;
	public Map<String, String> claims = new HashMap<>();
	public UUID teamId;
	public long blockContributions;
	public String lastKnownName = "";
	public Map<String, RewardGrant> entitlements = new HashMap<>();
	public Set<String> legacyUnclaimed = new HashSet<>();
	public transient boolean migratedFromV1;

	public record RewardGrant(String questId, List<QuestDefinition.Reward> rewards) {
	}

	public void entitle(String key, RewardGrant grant) {
		if (claims.containsKey(key))
			return;
		if (grant.rewards().size() > 1 && grant.rewards().stream()
				.anyMatch(r -> !r.policy().equals(QuestDefinition.RewardPolicy.DEFAULT) || r.type().equals("item")
						&& com.google.gson.JsonParser.parseString(r.data()).getAsJsonObject().has("onlyOne")
						&& com.google.gson.JsonParser.parseString(r.data()).getAsJsonObject().get("onlyOne")
								.getAsBoolean())) {
			for (int i = 0; i < grant.rewards().size(); i++)
				entitle(key + "|reward:" + i, new RewardGrant(grant.questId(), List.of(grant.rewards().get(i))));
			return;
		}
		// Empty outcomes are display receipts, not a growing queue of claimable
		// rewards.
		entitlements.entrySet().removeIf(e -> e.getValue().questId().equals(grant.questId())
				&& e.getValue().rewards().isEmpty() && !claims.containsKey(e.getKey()));
		entitlements.putIfAbsent(key, grant);
	}

	public static final class QuestProgress {
		public Map<String, Long> counters = new HashMap<>();
		public boolean completed;
		public String fingerprint;
		public Map<String, Long> cumulative = new HashMap<>();
		public long cycles;
		public int tier;
		public String attemptId;
		public long startedAt;
		public boolean waitingRestart;
		public boolean terminal;
		public Set<String> reachedTiers = new HashSet<>();
	}

	/**
	 * Detach mutable state on the Minecraft thread without doing JSON
	 * serialization. The autosave worker may then serialize and validate this
	 * independent snapshot. Critical reward/team transactions deliberately keep
	 * their existing durable path.
	 */
	public PlayerProgress copyForAutosave() {
		PlayerProgress copy = new PlayerProgress();
		copy.schemaVersion = schemaVersion;
		copy.teamId = teamId;
		copy.blockContributions = blockContributions;
		copy.lastKnownName = lastKnownName;
		copy.revision = revision;
		copy.migratedFromV1 = migratedFromV1;
		copy.claims = new HashMap<>(claims);
		copy.legacyUnclaimed = new HashSet<>(legacyUnclaimed);
		copy.quests = new HashMap<>(quests.size());
		for (var entry : quests.entrySet()) {
			QuestProgress source = entry.getValue(), target = new QuestProgress();
			target.counters = new HashMap<>(source.counters);
			target.cumulative = new HashMap<>(source.cumulative);
			target.completed = source.completed;
			target.fingerprint = source.fingerprint;
			target.cycles = source.cycles;
			target.tier = source.tier;
			target.attemptId = source.attemptId;
			target.startedAt = source.startedAt;
			target.waitingRestart = source.waitingRestart;
			target.terminal = source.terminal;
			target.reachedTiers = new HashSet<>(source.reachedTiers);
			copy.quests.put(entry.getKey(), target);
		}
		copy.entitlements = new HashMap<>(entitlements.size());
		for (var entry : entitlements.entrySet()) {
			var source = entry.getValue();
			List<QuestDefinition.Reward> rewards = new ArrayList<>(source.rewards().size());
			for (var reward : source.rewards()) {
				rewards.add(new QuestDefinition.Reward(reward.type(), reward.item(), reward.count(), reward.amount(),
						reward.data(), new HashMap<>(reward.text()), reward.policy()));
			}
			copy.entitlements.put(entry.getKey(), new RewardGrant(source.questId(), List.copyOf(rewards)));
		}
		return copy;
	}

	public void validate() {
		if (blockContributions < 0) throw new IllegalArgumentException("Invalid block contributions");
		if (lastKnownName == null || lastKnownName.length() > 64) throw new IllegalArgumentException("Invalid player name");
		if (schemaVersion != 2 || quests == null || claims == null || entitlements == null || legacyUnclaimed == null)
			throw new IllegalArgumentException("Invalid player progress");
		quests.forEach((id, p) -> {
			if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || p == null || p.counters == null
					|| p.fingerprint == null || p.fingerprint.isBlank())
				throw new IllegalArgumentException("Invalid quest progress: " + id);
			p.counters.forEach((k, v) -> {
				if (k == null || k.isBlank() || v == null || v < 0 || v > 1_000_000_000L)
					throw new IllegalArgumentException("Invalid counter");
			});
			if (p.cumulative == null || p.reachedTiers == null || p.cycles < 0 || p.tier < 0 || p.startedAt < 0)
				throw new IllegalArgumentException("Invalid cycle state");
			p.cumulative.forEach((k, v) -> {
				if (k == null || k.isBlank() || v == null || v < 0 || v > 1_000_000_000_000L)
					throw new IllegalArgumentException("Invalid cumulative counter");
			});
			if (p.attemptId != null)
				UUID.fromString(p.attemptId);
			if (p.reachedTiers.stream().anyMatch(t -> t == null || !t.matches("[a-z0-9_.-]+")))
				throw new IllegalArgumentException("Invalid tier id");
		});
		claims.forEach((k, v) -> {
			if (k == null || k.isBlank() || !Set.of("reserved", "delivered").contains(v))
				throw new IllegalArgumentException("Invalid claim");
		});
		entitlements.forEach((key, grant) -> {
			if (key == null || key.isBlank() || grant == null || grant.questId() == null
					|| !grant.questId().matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || grant.rewards() == null
					|| grant.rewards().size() > 64)
				throw new IllegalArgumentException("Invalid entitlement");
			grant.rewards().forEach(QuestExtensions::validateSavedReward);
		});
		if (legacyUnclaimed.stream().anyMatch(id -> id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")))
			throw new IllegalArgumentException("Invalid legacy entitlement");
	}
}
