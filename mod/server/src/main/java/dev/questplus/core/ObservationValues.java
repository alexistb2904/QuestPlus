package dev.questplus.core;

import java.util.*;

/** Full authoritative zero values without allocating an entry for every idle target. */
public final class ObservationValues extends AbstractMap<String, Long> {
    private final Set<String> targets;
    private final Map<String, Long> values;

    public ObservationValues(Set<String> targets, Map<String, Long> values) {
        this.targets = Set.copyOf(targets);
        this.values = Map.copyOf(values);
    }

    @Override public boolean containsKey(Object key) { return targets.contains(key); }
    @Override public Long get(Object key) { return targets.contains(key) ? values.getOrDefault(key, 0L) : null; }
    @Override public int size() { return targets.size(); }
    @Override public Set<Entry<String, Long>> entrySet() {
        return new AbstractSet<>() {
            @Override public int size() { return targets.size(); }
            @Override public Iterator<Entry<String, Long>> iterator() {
                var keys = targets.iterator();
                return new Iterator<>() {
                    public boolean hasNext() { return keys.hasNext(); }
                    public Entry<String, Long> next() {
                        String key = keys.next();
                        return Map.entry(key, values.getOrDefault(key, 0L));
                    }
                };
            }
        };
    }
}
