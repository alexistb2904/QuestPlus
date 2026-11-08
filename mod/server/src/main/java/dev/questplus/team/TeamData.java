package dev.questplus.team;

import dev.questplus.core.PlayerProgress;
import java.util.*;

/** Persisted team state. Its progress has no beneficiary ledger. */
public final class TeamData {
	public int schemaVersion = 1;
	public UUID id;
	public String name;
	public UUID owner;
	public Set<UUID> members = new HashSet<>();
	public Set<UUID> managers = new HashSet<>();
	public Map<UUID, Long> invitations = new HashMap<>();
	public PlayerProgress progress = new PlayerProgress();
	public Map<String, String> rewardClaims = new HashMap<>();
	public boolean disbanded;
	public boolean sharedQuestProgress = true;

	public TeamRole role(UUID player) {
		if (disbanded || !members.contains(player))
			throw new IllegalArgumentException("Player is not a team member");
		return owner.equals(player) ? TeamRole.OWNER : managers.contains(player) ? TeamRole.MANAGER : TeamRole.MEMBER;
	}

	public void validate() {
		if (schemaVersion != 1 || id == null || owner == null || name == null || name.isBlank() || name.length() > 64
				|| name.chars().anyMatch(Character::isISOControl)
				|| members == null || members.size() > 64 || members.contains(null) || managers == null
				|| !members.containsAll(managers) || managers.contains(owner) || invitations == null
				|| invitations.size() > 64 || progress == null || rewardClaims == null)
			throw new IllegalArgumentException("Invalid team");
		if (disbanded ? !members.isEmpty() || !invitations.isEmpty() : !members.contains(owner))
			throw new IllegalArgumentException("Team must have an owner/member or be disbanded");
		invitations.forEach((player, expiry) -> {
			if (player == null || expiry == null || expiry < 0 || members.contains(player))
				throw new IllegalArgumentException("Invalid team invitation");
		});
		progress.validate();
		rewardClaims.forEach((key, status) -> {
			if (key == null || key.isBlank() || !Set.of("reserved", "delivered").contains(status))
				throw new IllegalArgumentException("Invalid shared reward claim");
		});
		if (!progress.claims.isEmpty() || !progress.entitlements.isEmpty() || !progress.legacyUnclaimed.isEmpty()
				|| progress.teamId != null)
			throw new IllegalArgumentException("Collective progress cannot contain personal ledgers or membership");
	}
}
