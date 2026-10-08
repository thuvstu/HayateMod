package com.thuvstu.hayatemod.core.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ContentPackTest {
    @Test
    void repoContentLoadsWithoutErrors() {
        ContentPack.LoadedPack pack = ContentPack.load(com.thuvstu.hayatemod.core.TestContent.dir());
        assertTrue(pack.errors().isEmpty(), "loader errors: " + pack.errors());
        assertEquals(13, pack.set().weapons().size());
        assertEquals(13, pack.set().skills().size());
        assertEquals(7, pack.set().enemies().size());
        assertEquals(6, pack.set().runes().size());
        assertEquals(4, pack.set().keystones().size());
        assertEquals(2, pack.set().encounters().size());
        assertEquals(3, pack.set().npcs().size());
        assertEquals(1, pack.set().arenas().size());
        assertTrue(pack.set().vocabulary().cores().contains("summon_minions"));
    }
}
