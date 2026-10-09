package com.thuvstu.hayatemod.core.rules;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.thuvstu.hayatemod.core.TestContent;
import com.thuvstu.hayatemod.core.content.ContentPipeline;
import com.thuvstu.hayatemod.core.content.model.Models.*;

class RuleResolverTest {
    private RulePatch hp(double n) { return new RulePatch(n, null, null, null); }
    private Ruleset rules(RulePatch base, List<TagRule> tags, Map<String, RulePatch> ids) {
        return new Ruleset("test:r", true, base, tags, ids);
    }
    @Test void defaultsAreIdentity() {
        var r = new Ruleset("test:r", true);
        assertEquals(RuleResolver.Multipliers.identity(), RuleResolver.resolve(Map.of(r.id(), r), r.id(), "", Set.of()));
    }
    @Test void overrideIsReplacementAndRetainsUnspecifiedFields() {
        var r = rules(new RulePatch(2., 3., 4., 5.), List.of(new TagRule("rank:boss", hp(6))), Map.of());
        assertEquals(new RuleResolver.Multipliers(6, 3, 4, 5),
                RuleResolver.resolve(Map.of(r.id(), r), r.id(), "", Set.of("rank:boss")));
    }
    @Test void lastMatchingTagWinsRegardlessOfSetOrder() {
        var r = rules(hp(1), List.of(new TagRule("a", hp(2)), new TagRule("b", hp(3))), Map.of());
        assertEquals(3, RuleResolver.resolve(Map.of(r.id(), r), r.id(), "", Set.of("b", "a")).hp());
    }
    @Test void individualExceptionWinsOverTags() {
        var r = rules(hp(1), List.of(new TagRule("a", hp(2))), Map.of("boss", hp(4)));
        assertEquals(4, RuleResolver.resolve(Map.of(r.id(), r), r.id(), "boss", Set.of("a")).hp());
        assertEquals(2, RuleResolver.resolve(Map.of(r.id(), r), r.id(), "other", Set.of("a")).hp());
    }
    @Test void globalInheritanceCanBeDisabled() {
        var global = new Ruleset(RuleResolver.GLOBAL, false, new RulePatch(2., 3., 4., 5.), List.of(), Map.of());
        var r = rules(hp(6), List.of(), Map.of());
        var all = Map.of(global.id(), global, r.id(), r);
        assertEquals(new RuleResolver.Multipliers(6, 3, 4, 5), RuleResolver.resolve(all, r.id(), "", Set.of()));
        var isolated = new Ruleset(r.id(), false, hp(6), List.of(), Map.of());
        assertEquals(new RuleResolver.Multipliers(6, 1, 1, 1),
                RuleResolver.resolve(Map.of(global.id(), global, r.id(), isolated), r.id(), "", Set.of()));
    }
    @Test void noRecursiveGlobalInheritance() {
        var global = new Ruleset(RuleResolver.GLOBAL, true, hp(2), List.of(), Map.of());
        assertEquals(2, RuleResolver.resolve(Map.of(global.id(), global), global.id(), "", Set.of()).hp());
    }
    @Test void unknownRulesFailRatherThanSilentlyChangingDifficulty() {
        assertThrows(IllegalArgumentException.class, () -> RuleResolver.resolve(Map.of(), "missing", "", Set.of()));
    }
    @Test void rejectsInvalidRuntimeMultipliers() {
        for (double n : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, 101}) {
            assertThrows(IllegalArgumentException.class, () -> new RuleResolver.Multipliers(n, 1, 1, 1));
        }
    }
    @Test void guaranteedTokensNeverRoundToZeroAndNeverReset() {
        assertEquals(1, RuleResolver.rewardCount(1, .1, true));
        assertEquals(0, RuleResolver.rewardCount(1, .1, false));
        assertEquals(0, RuleResolver.rewardCount(0, 2, true));
        assertEquals(4, RuleResolver.rewardCount(3, 1.5, false));
        assertEquals(4096, RuleResolver.rewardCount(Integer.MAX_VALUE, 100, true));
    }
    @Test void actualHeroicPackChangesBossHpAndRewards() {
        var loaded = ContentPipeline.load(TestContent.dir());
        assertTrue(loaded.ok(), loaded.issues().toString());
        var enemy = loaded.set().enemies().values().stream().filter(e -> e.rank().equals("boss")).findFirst().orElseThrow();
        var rules = RuleResolver.resolve(loaded.set().rulesets(), "solommo:heroic", enemy);
        assertEquals(new RuleResolver.Multipliers(1.5, 1.2, 2, 1.5), rules);
        var normal = RuleResolver.resolve(loaded.set().rulesets(), "solommo:normal", enemy);
        var baseline = EnemyStats.derive(enemy.level(), enemy.rank(), loaded.set().reference(), loaded.set().tuning().rankTargets(), normal.hp(), normal.dps());
        var heroic = EnemyStats.derive(enemy.level(), enemy.rank(), loaded.set().reference(), loaded.set().tuning().rankTargets(), rules.hp(), rules.dps());
        assertEquals(baseline.maxHp() * 1.5, heroic.maxHp());
        assertEquals(baseline.dps() * 1.2, heroic.dps());
    }
}
