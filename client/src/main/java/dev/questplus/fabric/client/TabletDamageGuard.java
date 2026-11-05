package dev.questplus.fabric.client;

/**
 * Detects a real loss of health or absorption while the tablet is active.
 * Observe every client tick, including the first opening-animation tick,
 * so an existing injury does not instantly close a newly opened tablet.
 */
final class TabletDamageGuard {
    private boolean armed;
    private float health;
    private float absorption;

    boolean observe(boolean tabletActive,boolean allowWhileHurt,float newHealth,float newAbsorption) {
        if(!tabletActive || !Float.isFinite(newHealth) || !Float.isFinite(newAbsorption)){
            armed=false;
            return false;
        }
        if(!armed){
            armed=true;
            health=newHealth;
            absorption=newAbsorption;
            return false;
        }
        boolean damaged=newHealth<health-.001f || newAbsorption<absorption-.001f;
        health=newHealth;
        absorption=newAbsorption;
        return damaged && !allowWhileHurt;
    }

    void reset() { armed=false; }
}
