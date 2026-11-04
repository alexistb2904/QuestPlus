package dev.questplus.fabric.client;

/** Interruptible pose transition using monotonic time. */
public final class TabletAnimationController {
    private static final long DURATION = TabletFirstPersonAnimation.OPEN_NANOS;
    private float from, target;
    private long started;

    public void set(boolean open, long now, boolean animated) {
        float next = open ? 1 : 0;
        if (!animated) { from = target = next; started = now; return; }
        if (target == next) return;
        from = value(now, animated);
        target = next;
        started = now;
    }
    public void settle(long now) { from = target; started = now; }
    public float value(long now, boolean animated) {
        if (!animated) return target;
        float time = Math.clamp((float)(now - started) / DURATION, 0, 1);
        float eased = time * time * (3 - 2 * time);
        return from + (target - from) * eased;
    }
    public boolean finishedClosed(long now, boolean animated) { return target == 0 && value(now, animated) <= 0; }
}
