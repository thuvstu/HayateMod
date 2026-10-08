package com.thuvstu.hayatemod.core.engine;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Status effects with tick-based expiry (e.g. ignite). Snapshot semantics: callers pass game time. */
public final class StatusStore {
    private final Map<UUID, Map<String, Long>> data = new HashMap<>();

    public void add(UUID target, String status, long expiresTick) {
        data.computeIfAbsent(target, k -> new HashMap<>()).put(status, expiresTick);
    }

    public boolean has(UUID target, String status, long now) {
        Map<String, Long> m = data.get(target);
        if (m == null) {
            return false;
        }
        Long exp = m.get(status);
        return exp != null && exp > now;
    }

    public void cleanup(long now) {
        Iterator<Map.Entry<UUID, Map<String, Long>>> it = data.entrySet().iterator();
        while (it.hasNext()) {
            Map<String, Long> m = it.next().getValue();
            m.values().removeIf(exp -> exp <= now);
            if (m.isEmpty()) {
                it.remove();
            }
        }
    }
}
