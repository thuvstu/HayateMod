package com.thuvstu.hayatemod.core.content;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class StrictParsingTest {
    @Test
    void omittedOptionalValuesKeepDefaultsWithoutErrors() {
        var errors = new ArrayList<ContentError>();
        Map<String, Object> m = Map.of();
        assertEquals("default", Maps.optStr(m, "s", "default", "test", "root", errors));
        assertEquals(3, Maps.optInt(m, "i", 3, "test", "root", errors));
        assertEquals(0.5, Maps.optDouble(m, "d", 0.5, "test", "root", errors));
        assertTrue(Maps.optBool(m, "b", true, "test", "root", errors));
        assertTrue(Maps.optList(m, "l", "test", "root", errors).isEmpty());
        assertTrue(Maps.optMap(m, "m", "test", "root", errors).isEmpty());
        assertTrue(errors.isEmpty());
    }

    @Test
    void everyOptionalTypeRejectsExplicitNull() {
        var errors = new ArrayList<ContentError>();
        Map<String, Object> m = new HashMap<>();
        m.put("value", null);
        Maps.optStr(m, "value", "", "test", "root", errors);
        Maps.optInt(m, "value", 0, "test", "root", errors);
        Maps.optDouble(m, "value", 0, "test", "root", errors);
        Maps.optBool(m, "value", false, "test", "root", errors);
        Maps.optList(m, "value", "test", "root", errors);
        Maps.optMap(m, "value", "test", "root", errors);
        assertEquals(6, errors.size());
        assertTrue(errors.stream().allMatch(e -> e.file().equals("test") && e.path().equals("root.value")));
    }

    @Test
    void noStringNumberBooleanOrIntegerCoercions() {
        var errors = new ArrayList<ContentError>();
        Maps.optStr(Map.of("v", 12), "v", "", "test", "root", errors);
        Maps.optInt(Map.of("v", 1.5), "v", 0, "test", "root", errors);
        Maps.optInt(Map.of("v", Long.MAX_VALUE), "v", 0, "test", "root", errors);
        Maps.optDouble(Map.of("v", "1.5"), "v", 0, "test", "root", errors);
        Maps.optBool(Map.of("v", "true"), "v", false, "test", "root", errors);
        Maps.optList(Map.of("v", "one"), "v", "test", "root", errors);
        Maps.optMap(Map.of("v", List.of()), "v", "test", "root", errors);
        assertEquals(7, errors.size());
    }

    @Test
    void directAccessorsRejectNonFiniteNumbers() {
        var errors = new ArrayList<ContentError>();
        assertNull(Maps.reqDouble(Map.of("v", Double.NaN), "v", "test", "root", errors));
        assertEquals(10, Maps.optDouble(Map.of("v", Double.POSITIVE_INFINITY), "v", 10,
                "test", "root", errors));
        assertEquals(2, errors.size());
    }

    @Test
    void wrongEffectAndModifierShapesAreReported() {
        var errors = new ArrayList<ContentError>();
        Parsers.skill(Map.of("core", "melee_thrust", "effects", "wrong", "mods", List.of()),
                "test", "skills.primary", errors);
        assertEquals(2, errors.size());
        assertTrue(errors.stream().anyMatch(e -> e.path().equals("skills.primary.effects")));
        assertTrue(errors.stream().anyMatch(e -> e.path().equals("skills.primary.mods")));
    }

    @Test
    void modifiersRejectWrongScalarTypesAndUnknownKeys() {
        var errors = new ArrayList<ContentError>();
        Parsers.skill(Map.of("core", "melee_thrust", "mods",
                Map.of("range", "four", "element", 42, "count", 1.5, "rang", 3)),
                "test", "skills.primary", errors);
        assertEquals(4, errors.size());
        assertTrue(errors.stream().allMatch(e -> e.path().startsWith("skills.primary.mods.")));
    }

    @Test
    void unknownFlagsAndWrongDepthAreReported() {
        var errors = new ArrayList<ContentError>();
        Parsers.effect(Map.of("trigger", "on_hit", "actions", List.of(), "flags",
                Map.of("max_chain_depth", "three", "max_chain_dept", 3)), "test", "effects[0]", errors);
        assertEquals(2, errors.size());
    }

    @Test
    void lethalRatioMustBeNumericWhenPresent() {
        var errors = new ArrayList<ContentError>();
        Parsers.ability("test:a", Map.of("core", "heavy_slam", "lethal_ratio", "1.4"),
                "test", "ability", errors);
        assertEquals(1, errors.size());
        assertEquals("ability.lethal_ratio", errors.getFirst().path());
    }

    @Test
    void explicitNullExchangeIsSupportedButOtherWrongTypesAreNot() {
        var pity = new HashMap<String, Object>();
        pity.put("exchange", null);
        var errors = new ArrayList<ContentError>();
        var table = Parsers.loot(Map.of("id", "test:loot", "pity", pity), "test", errors);
        assertNotNull(table);
        assertNull(table.exchangeCost());
        assertTrue(errors.isEmpty());
        pity.put("exchange", "disabled");
        Parsers.loot(Map.of("id", "test:loot", "pity", pity), "test", errors);
        assertEquals(1, errors.size());
        assertEquals("pity.exchange", errors.getFirst().path());
    }

    @Test
    void exchangeRequiresTypedCostAndItem() {
        var errors = new ArrayList<ContentError>();
        Parsers.loot(Map.of("id", "test:loot", "pity",
                Map.of("per_kill", "one", "exchange", Map.of("cost", "ten", "item", 123))), "test", errors);
        assertEquals(3, errors.size());
    }

    @Test
    void unknownNestedWorldAndBalanceKeysAreReported() {
        var errors = new ArrayList<ContentError>();
        Parsers.worldLayout(Map.of("town", Map.of("gate_raduis", 50)), "world", errors);
        Parsers.damageTuning(Map.of("critical", Map.of("chance_caps", 0.5)), "balance", errors);
        Parsers.reference(Map.of("reference", List.of(Map.of("level", 1, "hp_ref", 100,
                "dps_ref", 10, "mit_ref", 0.2, "hps_ref", 5, "typo", true))), "reference", errors);
        assertEquals(3, errors.size());
        assertTrue(errors.stream().allMatch(e -> e.message().contains("unknown key")));
    }
    @Test
    void rulesetRejectsUnknownKeysAndWrongOptionalTypes() {
        var errors = new ArrayList<ContentError>();
        Parsers.ruleset(Map.of("id", "test:normal", "extends_global", "yes",
                "enemy", Map.of("hp_mult", "two"), "tag_overrides", List.of(Map.of("tag", 1)),
                "typo", true), "test", errors);
        assertEquals(4, errors.size());
    }

    @Test
    void referenceTuningNotesRemainSupportedAndTyped() {
        var errors = new ArrayList<ContentError>();
        Parsers.reference(Map.of("reference", List.of(), "tuning_notes",
                Map.of("role_dps_factor", Map.of("tank", 0.6), "npc_proficiency_factor", 0.9)), "test", errors);
        assertTrue(errors.isEmpty());
        Parsers.reference(Map.of("reference", List.of(), "tuning_notes",
                Map.of("npc_proficiency_factor", "high")), "test", errors);
        assertEquals(1, errors.size());
    }

}
