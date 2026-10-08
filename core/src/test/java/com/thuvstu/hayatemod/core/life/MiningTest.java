package com.thuvstu.hayatemod.core.life;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.thuvstu.hayatemod.core.content.ContentPack;
import com.thuvstu.hayatemod.core.life.MiningLevels.MiningBook;

class MiningTest {
    private MiningBook book() {
        var pack = ContentPack.load(com.thuvstu.hayatemod.core.TestContent.dir());
        assertTrue(pack.ok(), "loader errors: " + pack.errors());
        return pack.set().mining();
    }

    @Test
    void thresholds() {
        MiningBook book = book();
        assertEquals(1, MiningLevels.levelForXp(0, book));
        assertEquals(1, MiningLevels.levelForXp(199, book));
        assertEquals(5, MiningLevels.levelForXp(200, book));
        assertEquals(10, MiningLevels.levelForXp(800, book));
        assertEquals(20, MiningLevels.levelForXp(4000, book));
    }

    @Test
    void unlocks() {
        MiningBook book = book();
        assertTrue(!MiningLevels.unlocksContain(book, 1, "iron"));
        assertTrue(MiningLevels.unlocksContain(book, 5, "iron"));
        assertTrue(MiningLevels.unlocksContain(book, 15, "gem_slots"));
        assertEquals(10, MiningLevels.xpFor("minecraft:iron_ore", book));
        assertEquals(50, MiningLevels.xpFor("minecraft:diamond_ore", book));
    }
}
