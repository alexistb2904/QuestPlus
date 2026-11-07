package dev.questplus.fabric.client;

import java.util.concurrent.TimeUnit;

/**
 * Lightweight, deterministic player-held tablet timeline. Independent of other
 * mods' animation controllers and of the Minecraft simulation tick.
 */
final class TabletNativeAnimation {
	private static String current;
	private static long started;

	private TabletNativeAnimation() {}

	static boolean loaded(String clip) {
		return TabletFirstPersonAnimation.contains(clip);
	}

	static boolean play(String clip) {
		if (!loaded(clip))
			return false;
		current = clip;
		started = System.nanoTime();
		return true;
	}

	static boolean active() {
		return current != null && QuestClientTabletFlow.nativeHandsActive();
	}

	static String clip() {
		return current;
	}

	static double seconds() {
		return current == null ? 0 : Math.max(0, System.nanoTime() - started) / 1_000_000_000.0;
	}

	static boolean finished(String clip) {
		return clip.equals(current)
				&& seconds() >= TabletFirstPersonAnimation.duration(clip);
	}

	static TabletFirstPersonAnimation.Pose pose() {
		return current == null ? null : TabletFirstPersonAnimation.pose(current, seconds());
	}

	static TabletFirstPersonAnimation.Vector bone(String name, String channel) {
		return current == null ? TabletFirstPersonAnimation.Vector.ZERO
				: TabletFirstPersonAnimation.boneValue(current, seconds(), name, channel);
	}

	static void stop() {
		current = null;
		started = 0;
	}
}
