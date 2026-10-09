package com.thuvstu.hayatemod.core.engine;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Per-object lifecycle deduplication without retaining dead entities or conflating respawns by UUID. */
public final class WeakIdentitySet<T> {
    private final ReferenceQueue<T> queue = new ReferenceQueue<>();
    private final Set<Key<T>> seen = new HashSet<>();
    private static final class Key<T> extends WeakReference<T> {
        private final int hash;
        Key(T value, ReferenceQueue<T> queue) { super(value, queue); hash = System.identityHashCode(value); }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            T value = get();
            return this == other || (value != null && other instanceof Key<?> key && value == key.get());
        }
    }
    public boolean add(T value) {
        Objects.requireNonNull(value);
        for (var ref = queue.poll(); ref != null; ref = queue.poll()) seen.remove(ref);
        return seen.add(new Key<>(value, queue));
    }
    public void clear() { seen.clear(); while (queue.poll() != null) { } }
}
