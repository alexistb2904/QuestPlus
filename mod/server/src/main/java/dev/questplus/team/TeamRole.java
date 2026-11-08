package dev.questplus.team;

public enum TeamRole {
	MEMBER, MANAGER, OWNER;

	public boolean canManage() {
		return this != MEMBER;
	}

	public boolean canKick(TeamRole target) {
		return (target == MEMBER && canManage()) || (target == MANAGER && this == OWNER);
	}
}
