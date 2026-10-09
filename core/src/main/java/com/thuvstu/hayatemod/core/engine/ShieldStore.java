package com.thuvstu.hayatemod.core.engine;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Temporary, non-additive shields in damage units. Expiry never depends on a cleanup tick. */
public final class ShieldStore {
    public record Shield(double amount, long expiresAt) {
    }

    private final Map<UUID, Shield> shields = new HashMap<>();
    private final double cap;
    private final int maxDuration;

    public ShieldStore(double cap, int maxDuration) {
        if (!Double.isFinite(cap) || cap <= 0 || maxDuration <= 0) {
            throw new IllegalArgumentException("shield limits must be finite and positive");
        }
        this.cap = cap;
        this.maxDuration = maxDuration;
    }

    /** Stronger/equal grants replace; weaker grants neither stack nor extend a stronger shield. */
    public void grant(UUID owner, double amount, int duration, long now) {
        if (!Double.isFinite(amount) || amount <= 0 || duration <= 0) {
            return;
        }
        double bounded = Math.min(cap, amount);
        if (bounded < amount(owner, now)) {
            return;
        }
        long ticks = Math.min(maxDuration, duration);
        long expires = now > Long.MAX_VALUE - ticks ? Long.MAX_VALUE : now + ticks;
        shields.put(owner, new Shield(bounded, expires));
    }

    public double amount(UUID owner, long now) {
        Shield shield = current(owner, now);
        return shield == null ? 0 : shield.amount();
    }

    public long remainingTicks(UUID owner, long now) {
        Shield shield = current(owner, now);
        return shield == null ? 0 : shield.expiresAt() - now;
    }

    /** Non-mutating damage preview, for lethal/second-wind decisions. */
    public double preview(UUID owner, double damage, long now) {
        if (!Double.isFinite(damage) || damage <= 0) {
            return damage;
        }
        return Math.max(0, damage - amount(owner, now));
    }

    /** Consumes at most the incoming damage, returns the damage still owed to HP. */
    public double absorb(UUID owner, double damage, long now) {
        if (!Double.isFinite(damage) || damage <= 0) {
            return damage;
        }
        Shield shield = current(owner, now);
        if (shield == null) {
            return damage;
        }
        double absorbed = Math.min(shield.amount(), damage);
        double remaining = shield.amount() - absorbed;
        if (remaining <= 0) {
            clear(owner);
        } else {
            shields.put(owner, new Shield(remaining, shield.expiresAt()));
        }
        return damage - absorbed;
    }

    public void clear(UUID owner) {
        shields.remove(owner);
    }

    public void cleanup(long now) {
        shields.values().removeIf(shield -> shield.expiresAt() <= now);
    }

    private Shield current(UUID owner, long now) {
        Shield shield = shields.get(owner);
        if (shield != null && shield.expiresAt() <= now) {
            clear(owner);
            return null;
        }
        return shield;
    }
}
