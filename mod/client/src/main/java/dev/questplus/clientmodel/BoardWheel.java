package dev.questplus.clientmodel;

public final class BoardWheel {
	private BoardWheel() {
	}

	public static int[] rows(int size, int slots, int pinned, long step) {
		int count = Math.max(0, Math.min(size, slots));
		var result = new int[count];
		int fixed = size > slots ? Math.clamp(pinned, 0, Math.max(0, slots - 1)) : count, pool = size - fixed;
		int start = pool > 0 ? (int) Math.floorMod(step, (long) pool) : 0;
		for (int i = 0; i < count; i++)
			result[i] = i < fixed ? i : fixed + (start + i - fixed) % pool;
		return result;
	}
}
