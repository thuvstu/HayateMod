package com.thuvstu.hayatemod.core.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class ShieldStoreTest {
    private final UUID owner = new UUID(0, 1);
    private final ShieldStore store = new ShieldStore(200, 1200);

    @Test
    void absorbsExactlyOnceAndPreservesFractionalRemainder() {
        store.grant(owner, 10.5, 100, 0);
        assertEquals(0, store.absorb(owner, 4.25, 1));
        assertEquals(6.25, store.amount(owner, 1));
        assertEquals(3.75, store.absorb(owner, 10, 2));
        assertEquals(0, store.amount(owner, 2));
        assertEquals(10, store.absorb(owner, 10, 3));
    }

    @Test
    void previewDoesNotConsumeShield() {
        store.grant(owner, 90, 100, 0);
        assertEquals(10, store.preview(owner, 100, 1));
        assertEquals(10, store.preview(owner, 100, 1));
        assertEquals(90, store.amount(owner, 1));
        assertEquals(10, store.absorb(owner, 100, 1));
        assertEquals(0, store.amount(owner, 1));
    }

    @Test
    void expiresAtBoundaryWithoutNeedingCleanup() {
        store.grant(owner, 90, 100, 10);
        assertEquals(90, store.amount(owner, 109));
        assertEquals(1, store.remainingTicks(owner, 109));
        assertEquals(30, store.absorb(owner, 30, 110));
        assertEquals(0, store.remainingTicks(owner, 110));
    }

    @Test
    void equalGrantRefreshesButNeverAdds() {
        store.grant(owner, 90, 100, 0);
        store.grant(owner, 90, 100, 30);
        assertEquals(90, store.amount(owner, 30));
        assertEquals(100, store.remainingTicks(owner, 30));
    }

    @Test
    void weakerGrantDoesNotExtendStrongerShield() {
        store.grant(owner, 90, 100, 0);
        store.grant(owner, 30, 1000, 50);
        assertEquals(90, store.amount(owner, 50));
        assertEquals(50, store.remainingTicks(owner, 50));
    }

    @Test
    void strongerGrantReplacesRemainingShieldAfterDamage() {
        store.grant(owner, 90, 100, 0);
        store.absorb(owner, 70, 1);
        store.grant(owner, 60, 120, 2);
        assertEquals(60, store.amount(owner, 2));
        assertEquals(120, store.remainingTicks(owner, 2));
    }

    @Test
    void capsAmountAndDuration() {
        store.grant(owner, 100000, Integer.MAX_VALUE, 0);
        assertEquals(200, store.amount(owner, 0));
        assertEquals(1200, store.remainingTicks(owner, 0));
    }

    @Test
    void ignoresInvalidGrantsWithoutDestroyingExistingShield() {
        store.grant(owner, 50, 100, 0);
        for (double bad : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            store.grant(owner, bad, 100, 1);
        }
        store.grant(owner, 100, 0, 1);
        assertEquals(50, store.amount(owner, 1));
        assertEquals(99, store.remainingTicks(owner, 1));
    }

    @Test
    void invalidDamageDoesNotConsumeShield() {
        store.grant(owner, 50, 100, 0);
        assertEquals(-1, store.absorb(owner, -1, 1));
        assertTrue(Double.isNaN(store.absorb(owner, Double.NaN, 1)));
        assertEquals(50, store.amount(owner, 1));
    }

    @Test
    void clearAndCleanupRemoveShields() {
        UUID other = new UUID(0, 2);
        store.grant(owner, 90, 100, 0);
        store.grant(other, 40, 200, 0);
        store.cleanup(100);
        assertEquals(0, store.amount(owner, 100));
        assertEquals(40, store.amount(other, 100));
        store.clear(other);
        assertEquals(0, store.amount(other, 100));
    }

    @Test
    void expiryAdditionCannotOverflow() {
        store.grant(owner, 10, 100, Long.MAX_VALUE - 10);
        assertEquals(10, store.remainingTicks(owner, Long.MAX_VALUE - 10));
        assertEquals(0, store.amount(owner, Long.MAX_VALUE));
    }
}
