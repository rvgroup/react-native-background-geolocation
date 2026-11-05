package com.marianhello.utils;

import java.util.LinkedHashSet;

/** Bounded, atomic deduplication of recent location event time buckets. */
public final class RecentLocationKeys {
    private final int capacity;
    private final LinkedHashSet<Long> keys = new LinkedHashSet<>();

    public RecentLocationKeys(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    public synchronized boolean add(long key) {
        if (!keys.add(key)) return false;
        if (keys.size() > capacity) keys.remove(keys.iterator().next());
        return true;
    }

    public synchronized void clear() {
        keys.clear();
    }
}
