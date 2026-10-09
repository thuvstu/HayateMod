package com.thuvstu.hayatemod.core.engine;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.thuvstu.hayatemod.core.TestContent;
import com.thuvstu.hayatemod.core.content.ContentPack;
import com.thuvstu.hayatemod.core.content.model.Models.*;

class EffectOrderTest {
    final UUID owner = new UUID(0, 1), target = new UUID(0, 2), other = new UUID(0, 3);
    EffectEngineTest.FakeWorld world;
    EffectEngine engine;
    final List<EffectEngine.EffectTrace> traces = new ArrayList<>();
    @BeforeEach void setup() {
        world = new EffectEngineTest.FakeWorld();
        world.place(owner, new Vec3(0,0,0), new Vec3(1,0,0));
        world.place(target, new Vec3(2,0,0), new Vec3(-1,0,0));
        world.arcTargets = List.of(target);
        resetEngine();
    }
    void resetEngine() {
        engine = new EffectEngine(ContentPack.load(TestContent.dir()).set(), world);
        engine.setEffectTraceListener(traces::add);
    }
    ActionDef action(String type, double amount) {
        return new ActionDef(type, 2, 1, "", 0, List.of(), 0, amount, "", "", "flag", "");
    }
    EffectDef effect(String id, String source, int priority, ActionDef... actions) {
        return new EffectDef("on_join", List.of(), List.of(actions), List.of(), 3, "", 0, id, source, priority);
    }
    EffectDef heal(String id, String source, int priority, int amount) { return effect(id, source, priority, action("heal_self", amount)); }
    SkillDef skill(EffectDef... effects) { return new SkillDef("melee_thrust", Map.of(), List.of(effects)); }
    WeaponCard card(Map<String,SkillDef> skills) { return new WeaponCard("test:order", "Order", "sword", "unique", 1, List.of(), List.of(), skills, ""); }
    WeaponCard card(EffectDef... effects) { return card(Map.of("primary", skill(effects))); }
    void join(WeaponCard card) { engine.onJoin(card, owner, BuildMods.neutral()); }
    List<Double> amounts() { return world.heals.stream().map(s -> Double.parseDouble(s.substring(s.lastIndexOf(':')+1))).toList(); }
    void tick(int amount) { world.advance(amount); engine.tick(); }

    @Test void priorityPrecedesDeclarationOrderAndEffectId() {
        join(card(heal("a", "weapon:test", 10, 1), heal("z", "weapon:test", -10, 2)));
        assertEquals(List.of(2.,1.), amounts());
        assertEquals(List.of("z","a"), traces.stream().map(EffectEngine.EffectTrace::id).toList());
    }
    @Test void weaponBeforeRuneAtEqualPriorityButExplicitPriorityWins() {
        join(card(heal("a", "rune:a", 0, 1), heal("z", "weapon:z", 0, 2), heal("z", "rune:z", -1, 3)));
        assertEquals(List.of(3.,2.,1.), amounts());
    }
    @Test void sourceIdThenEffectIdBreakTies() {
        join(card(heal("a", "weapon:z", 0, 1), heal("z", "weapon:a", 0, 2), heal("a", "weapon:a", 0, 3)));
        assertEquals(List.of(3.,2.,1.), amounts());
    }
    @Test void priorityIsGlobalAcrossSlotsBeforeEvaluationBudgetAdmission() {
        var lows = new ArrayList<EffectDef>();
        for (int i=0; i<300; i++) lows.add(heal("low"+i, "weapon:test", 10, 1));
        join(card(Map.of("a", new SkillDef("melee_thrust", Map.of(), lows), "z", skill(heal("high", "rune:z", -10, 9)))));
        assertEquals(256, amounts().size());
        assertEquals(9., amounts().getFirst());
        assertEquals("z", traces.getFirst().skill().substring(traces.getFirst().skill().lastIndexOf(':')+1));
    }
    @Test void actionBudgetAlsoFollowsPriorityRatherThanOriginalOrder() {
        var low = new EffectDef("on_join", List.of(), Collections.nCopies(512, action("heal_self", 1)), List.of(), 3, "", 0, "a", "weapon:test", 10);
        join(card(low, heal("z", "weapon:test", -10, 9)));
        assertEquals(512, amounts().size());
        assertEquals(9., amounts().getFirst());
    }
    @Test void chanceRollsAreMadeInTheSameOrderedSequence() {
        world = new EffectEngineTest.FakeWorld() { int calls; @Override public boolean rollChance(double p) { return calls++ == 0; } };
        resetEngine();
        var chance = List.of(new ConditionDef("chance", "", .5, "", 0));
        var low = new EffectDef("on_join", chance, List.of(action("heal_self", 1)), List.of(), 3, "", 0, "a", "weapon:test", 5);
        var high = new EffectDef("on_join", chance, List.of(action("heal_self", 2)), List.of(), 3, "", 0, "z", "weapon:test", -5);
        join(card(low, high));
        assertEquals(List.of(2.), amounts());
    }
    @Test void priorityDoesNotMakeLaterConditionsSeeEarlierMutations() {
        var setter = effect("setter", "weapon:test", -10, action("setvar", 1));
        var conditional = new EffectDef("on_join", List.of(new ConditionDef("variable", "", 1, "flag", 0)),
                List.of(action("heal_self", 2)), List.of(), 3, "", 0, "heal", "weapon:test", 10);
        join(card(conditional, setter));
        assertTrue(amounts().isEmpty());
        join(card(conditional, setter));
        assertEquals(List.of(2.), amounts());
    }
    @Test void sameDueTimeContinuationPriorityCrossesEventBoundaries() {
        join(card(effect("low", "weapon:test", 10, action("delay", 20), action("heal_self", 1))));
        join(card(effect("high", "weapon:test", -10, action("delay", 20), action("heal_self", 2))));
        tick(20);
        assertEquals(List.of(2.,1.), amounts());
    }
    @Test void olderDueTimeWinsOverPriorityWhenSeveralTasksAreOverdue() {
        join(card(effect("high", "weapon:test", -10, action("delay", 20), action("heal_self", 2))));
        join(card(effect("low", "weapon:test", 10, action("delay", 10), action("heal_self", 1))));
        tick(30);
        assertEquals(List.of(1.,2.), amounts());
    }
    @Test void repeatRetainsTheOriginatingEffectPriority() {
        join(card(effect("low", "weapon:test", 10, action("heal_self", 1), action("repeat", 20))));
        join(card(effect("high", "weapon:test", -10, action("heal_self", 2), action("repeat", 20))));
        tick(20);
        assertEquals(List.of(1.,2.,2.,1.), amounts());
    }
    @Test void delayedConditionsAreNotReevaluatedAgainstFutureState() {
        world.health.put(owner, .1);
        var delayed = new EffectDef("on_join", List.of(new ConditionDef("health_below", "", .5, "", 0)),
                List.of(action("delay", 20), action("heal_self", 2)), List.of(), 3, "", 0, "delayed", "weapon:test", -10);
        join(card(delayed));
        world.health.put(owner, 1.);
        tick(20);
        assertEquals(List.of(2.), amounts());
    }
    @Test void genericTasksRetainFifoAtEqualDeadline() {
        List<Integer> order = new ArrayList<>();
        for (int i=0; i<50; i++) { int value=i; engine.schedule(1, () -> order.add(value)); }
        tick(1);
        assertEquals(java.util.stream.IntStream.range(0,50).boxed().toList(), order);
    }
    @Test void timerOrdersAllSlotsAndTargetsWithoutDuplicatingArea() {
        world.arcTargets = List.of(other, target, target);
        var low = new EffectDef("on_timer", List.of(), List.of(action("heal_self", 1)), List.of(), 3, "", 0, "low", "weapon:test", 10);
        var high = new EffectDef("on_timer", List.of(), List.of(action("heal_self", 2)), List.of(), 3, "area", 4, "high", "weapon:test", -10);
        engine.onTimer(card(Map.of("a", new SkillDef("melee_thrust", Map.of("interval",1), List.of(low)),
                "z", new SkillDef("melee_thrust", Map.of("interval",1), List.of(high)))), owner, BuildMods.neutral());
        assertEquals(List.of(2.,2.,1.,1.,1.), amounts());
        assertEquals(List.of(target,other), traces.subList(0,2).stream().map(EffectEngine.EffectTrace::target).toList());
    }
    @Test void unnamedCompatibilityEffectsKeepNumericalArrayOrder() {
        var effects = new ArrayList<EffectDef>();
        for (int i=0; i<12; i++) effects.add(new EffectDef("on_join", List.of(), List.of(action("heal_self", i)), List.of(), 3, "", 0));
        join(card(effects.toArray(EffectDef[]::new)));
        assertEquals(java.util.stream.IntStream.range(0,12).mapToDouble(i->i).boxed().toList(), amounts());
    }
    @Test void shuffledDeclarationsProduceSameExecutionForNamedEffects() {
        var effects = new ArrayList<EffectDef>();
        for (int i=0; i<50; i++) effects.add(heal(String.format("e%02d", i), "weapon:test", i%3, i));
        List<Double> expected = null;
        for (int seed=0; seed<12; seed++) {
            Collections.shuffle(effects, new Random(seed));
            world.heals.clear(); resetEngine();
            join(card(effects.toArray(EffectDef[]::new)));
            if (expected == null) expected = amounts(); else assertEquals(expected, amounts());
        }
    }
    @Test void descriptionsUseTheSamePriorityOrderWithinASlotAndRune() {
        var low=heal("a","weapon:test",10,1); var high=heal("z","weapon:test",-10,2);
        var lines=com.thuvstu.hayatemod.core.describe.Describer.describeWeapon(card(low,high));
        assertEquals(com.thuvstu.hayatemod.core.describe.Describer.describeWeapon(card(high)).get(3),lines.get(3));
        var runeLines=com.thuvstu.hayatemod.core.describe.Describer.describeRune(new RuneDef("test:rune","Rune","",List.of(low,high)));
        assertEquals(com.thuvstu.hayatemod.core.describe.Describer.describeRune(new RuneDef("test:rune","Rune","",List.of(high))).get(1),runeLines.get(1));
    }

    @Test void tracePreservesImmutableCausalHistory() {
        var original = heal("identity", "rune:test", -2, 1);
        var copy = SkillSnapshot.copy(card(original));
        assertEquals(original, copy.skills().get("primary").effects().getFirst());
        join(copy);
        assertEquals("rune:test", traces.getFirst().source());
        assertThrows(UnsupportedOperationException.class, () -> traces.getFirst().history().add("cast"));
    }
}
