package com.thuvstu.hayatemod.core.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.thuvstu.hayatemod.core.content.ContentPack;
import com.thuvstu.hayatemod.core.content.model.Models.BuildMods;
import com.thuvstu.hayatemod.core.content.model.Models.EffectDef;

class BuildTest {
    @Test
    void combineKeystones() {
        var pack = ContentPack.load(com.thuvstu.hayatemod.core.TestContent.dir());
        assertTrue(pack.ok(), "loader errors: " + pack.errors());
        var set = pack.set();
        var mods = BuildMods.combine(List.of(
                set.keystones().get("solommo:chainlord"),
                set.keystones().get("solommo:overchannel"),
                set.keystones().get("solommo:quicksilver")));
        assertEquals(1, mods.chainBonus());
        assertEquals(1.25, mods.damageMult(), 1e-9);
        assertEquals(0.75, mods.cooldownMult(), 1e-9);
        assertEquals(1.0, mods.meleeMult(), 1e-9);
    }

    @Test
    void withPointsScales() {
        var base = BuildMods.neutral();
        var mods = BuildMods.withPoints(base, 10, 10, 10);
        assertEquals(1.2, mods.damageMult(), 1e-9);
        assertEquals(0.9, mods.cooldownMult(), 1e-9);
        assertEquals(1.2, mods.meleeMult(), 1e-9);
        var capped = BuildMods.withPoints(base, 99, 99, 99);
        assertEquals(1.5, capped.damageMult(), 1e-9);
        assertEquals(0.8, capped.cooldownMult(), 1e-9);
        assertEquals(1.5, capped.meleeMult(), 1e-9);
    }

    @Test
    void mergerAppendsRuneEffects() {
        var pack = ContentPack.load(com.thuvstu.hayatemod.core.TestContent.dir());
        var card = pack.set().weapons().get("solommo:ember_branch");
        var rune = pack.set().runes().get("solommo:cinder_rune");
        var merged = WeaponSkillMerger.withRuneEffects(card.skills().get("special"), rune.effects());
        assertEquals(card.skills().get("special").effects().size() + 1, merged.effects().size());
        assertEquals(card.skills().get("special").core(), merged.core());
    }

    @Test
    void tagDerivation() {
        var real = ContentPack.load(com.thuvstu.hayatemod.core.TestContent.dir()).set();
        var tags = TagDeriver.itemTags(real.weapons().get("solommo:ember_branch"));
        for (String expected : List.of("element:fire", "delivery:projectile", "delivery:melee",
                "status:ignite", "content:field_clear")) {
            assertTrue(tags.contains(expected), "missing " + expected + " in " + tags);
        }
        var sig = TagDeriver.buildTags("solommo:knight", Set.of("projectile_single"),
                List.of("solommo:cinder_rune"), List.of("solommo:chainlord"));
        var matched = TagDeriver.matchSet(sig, Set.of("projectile_single", "melee_thrust"));
        assertTrue(TagDeriver.matchCount(tags, matched) >= 2, "sig=" + matched);
    }

    @Test
    void runeCountFromData() {
        var pack = ContentPack.load(com.thuvstu.hayatemod.core.TestContent.dir());
        assertEquals(6, pack.set().runes().size());
        for (EffectDef e : pack.set().runes().get("solommo:pyre_rune").effects()) {
            assertEquals("on_hit", e.trigger());
        }
    }

    @Test
    void keystoneCountFromData() {
        var pack = ContentPack.load(com.thuvstu.hayatemod.core.TestContent.dir());
        assertEquals(4, pack.set().keystones().size());
        assertEquals(1, pack.set().keystones().get("solommo:chainlord").chainBonus());
    }
}
