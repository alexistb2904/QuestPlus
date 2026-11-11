package dev.questplus.fabric.client;

/** Package-private tablet state for the NeoForge client smoke test (Fabric's client tests share this package). */
public final class SmokeTestAccess {
	private SmokeTestAccess() {}

	/** The first-person tablet is carried and drawn by the native arm renderer, as Fabric's TabletCarryGameTest asserts. */
	public static boolean carryingTablet() {
		return QuestClientTabletFlow.carrying() && TabletNativeArms.active();
	}
}
