package com.thuvstu.hayatemod.core.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.thuvstu.hayatemod.core.TestContent;
import com.thuvstu.hayatemod.core.content.ContentPack;
import com.thuvstu.hayatemod.core.content.model.Models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EffectSafetyTest {
    private EffectEngineTest.FakeWorld world;
    private EffectEngine engine;
    private final UUID owner = new UUID(0, 1);
    private final UUID target = new UUID(0, 2);

    @BeforeEach
    void setup() {
        world = new EffectEngineTest.FakeWorld();
        world.place(owner, new Vec3(0, 0, 0), new Vec3(1, 0, 0));
        world.place(target, new Vec3(2, 0, 0), new Vec3(-1, 0, 0));
        world.arcTargets = List.of(target);
        engine = new EffectEngine(ContentPack.load(TestContent.dir()).set(), world);
    }

    private ActionDef action(String type, int count, double amount, String ref) {
        return new ActionDef(type, count, 1, "look", 4, List.of(), 0, amount, "ignite", "", ref, "");
    }

    private EffectDef effect(String trigger, List<ConditionDef> conditions, ActionDef... actions) {
        return new EffectDef(trigger, conditions, List.of(actions), List.of(), 3, "", 0);
    }

    private WeaponCard card(EffectDef... effects) {
        return new WeaponCard("test:safety", "Safety", "sword", "magic", 1, List.of(), List.of(),
                Map.of("primary", new SkillDef("melee_thrust", Map.of(), List.of(effects))), "");
    }

    private void hit(WeaponCard card, int depth, Set<String> history) {
        engine.meleeStrike(owner, card, card.skills().get("primary"),
                new CastContext(card.id() + ":primary", depth, history, owner, 1, BuildMods.neutral()));
    }

    @Test
    void synchronousAndDelayedActionsShareTheSameTickBudget() {
        ActionDef heal = action("heal_self", 0, 1, "");
        var effects = new EffectDef("on_join", List.of(),
                Collections.nCopies(ExecutionLimits.defaults().actionsPerTick() + 20, heal), List.of(), 3, "", 0);
        var card = card(effects);
        engine.schedule(0, () -> engine.onJoin(card, owner, BuildMods.neutral()));
        engine.onJoin(card, owner, BuildMods.neutral());
        engine.tick();
        engine.tick();
        assertEquals(ExecutionLimits.defaults().actionsPerTick(), world.heals.size());
        assertEquals(ExecutionLimits.defaults().actionsPerTick(), engine.executedActionsThisTick());
        world.advance(1);
        engine.onJoin(card, owner, BuildMods.neutral());
        assertEquals(2 * ExecutionLimits.defaults().actionsPerTick(), world.heals.size());
    }

    @Test
    void taskQueueIsBoundedAndDrainsAcrossTicksWithoutLoss() {
        int[] runs = {0};
        for (int i = 0; i < ExecutionLimits.defaults().pendingTasks(); i++) {
            assertNotNull(engine.schedule(0, () -> runs[0]++));
        }
        assertNull(engine.schedule(0, () -> fail("rejected task must not execute")));
        engine.tick();
        engine.tick();
        assertEquals(ExecutionLimits.defaults().tasksPerTick(), runs[0]);
        while (engine.pendingTaskCount() > 0) {
            world.advance(1);
            engine.tick();
        }
        assertEquals(ExecutionLimits.defaults().pendingTasks(), runs[0]);
    }

    @Test
    void taskCancellationFreesCapacity() {
        UUID first = engine.schedule(0, () -> fail("cancelled task"));
        assertTrue(engine.cancel(first));
        assertFalse(engine.cancel(first));
        assertEquals(0, engine.pendingTaskCount());
    }

    @Test
    void hugeDelayCannotOverflowAndFireImmediately() {
        world.advance(100);
        engine.schedule(Long.MAX_VALUE, () -> fail("overflowed delay"));
        engine.tick();
        assertEquals(1, engine.pendingTaskCount());
    }

    @Test
    void hugeRepeatCountIsClampedBeforeScheduling() {
        hit(card(effect("on_hit", List.of(), action("heal_self", 0, 1, ""),
                action("repeat", Integer.MAX_VALUE, 1, ""))), 0, Set.of());
        assertEquals(7, engine.pendingTaskCount());
    }

    @Test
    void laterConditionsDoNotSeeEarlierVariableMutations() {
        var card = card(
                effect("on_hit", List.of(), action("setvar", 0, 1, "flag")),
                effect("on_hit", List.of(new ConditionDef("variable", "", 1, "flag", 0)),
                        action("heal_self", 0, 5, "")));
        hit(card, 0, Set.of());
        assertTrue(world.heals.isEmpty());
        hit(card, 0, Set.of());
        assertEquals(1, world.heals.size(), "the next event sees the changed variable");
    }

    @Test
    void deliveryDamageCannotChangeHealthConditionForTheSameHit() {
        var damaging = new EffectEngineTest.FakeWorld() {
            @Override
            public void dealDamage(UUID attacker, UUID victim, double amount,
                    WorldAdapter.DamageKind kind, UUID direct) {
                super.dealDamage(attacker, victim, amount, kind, direct);
                health.put(victim, 0.1);
            }
        };
        damaging.arcTargets = List.of(target);
        engine = new EffectEngine(ContentPack.load(TestContent.dir()).set(), damaging);
        var card = card(effect("on_hit", List.of(new ConditionDef("health_below", "", 0.5, "", 0)),
                action("heal_self", 0, 5, "")));
        hit(card, 0, Set.of());
        assertTrue(damaging.heals.isEmpty());
        hit(card, 0, Set.of());
        assertEquals(1, damaging.heals.size());
    }

    @Test
    void conditionsAcrossSlotsUseOneEventSnapshot() {
        var setter = effect("on_join", List.of(), action("setvar", 0, 1, "flag"));
        var conditional = effect("on_join", List.of(new ConditionDef("variable", "", 1, "flag", 0)),
                action("heal_self", 0, 5, ""));
        var skills = new HashMap<String, SkillDef>();
        skills.put("a", new SkillDef("melee_thrust", Map.of(), List.of(setter)));
        skills.put("z", new SkillDef("melee_thrust", Map.of(), List.of(conditional)));
        var card = new WeaponCard("test:slots", "Slots", "sword", "magic", 1,
                List.of(), List.of(), skills, "");
        engine.onJoin(card, owner, BuildMods.neutral());
        assertTrue(world.heals.isEmpty());
    }

    @Test
    void perEffectDepthAndCausalHistorySuppressWholeEffect() {
        var guarded = new EffectDef("on_hit", List.of(), List.of(action("heal_self", 0, 1, "")),
                List.of("cast"), 0, "", 0);
        hit(card(guarded), 1, Set.of());
        hit(card(guarded), 0, Set.of("cast"));
        assertTrue(world.heals.isEmpty());
        hit(card(guarded), 0, Set.of());
        assertEquals(1, world.heals.size());
    }

    @Test
    void projectilesCannotExceedAbsoluteChainDepth() {
        hit(card(effect("on_hit", List.of(), action("spawn_projectiles", 8, 0, ""))), 3, Set.of());
        assertTrue(world.bolts.isEmpty());
    }

    @Test
    void castContextOwnsAnImmutableHistory() {
        var history = new HashSet<String>();
        var context = new CastContext("test:primary", 0, history, owner, 1, BuildMods.neutral());
        history.add("cast");
        assertTrue(context.preventRecursive().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> context.preventRecursive().add("cast"));
    }

    @Test
    void fixedSeedBurstNeverExceedsBudget() {
        var random = new java.util.Random(0x5AFE);
        for (int tick = 0; tick < 20; tick++) {
            int before = world.heals.size();
            for (int event = 0; event < 100; event++) {
                var actions = new ArrayList<ActionDef>();
                for (int i = 0, count = random.nextInt(24); i < count; i++) {
                    actions.add(action(random.nextBoolean() ? "heal_self" : "repeat",
                            random.nextInt(40), 1, ""));
                }
                engine.onJoin(card(effect("on_join", List.of(), actions.toArray(ActionDef[]::new))),
                        owner, BuildMods.neutral());
            }
            engine.tick();
            assertTrue(engine.executedActionsThisTick() <= ExecutionLimits.defaults().actionsPerTick());
            assertTrue(world.heals.size() - before <= ExecutionLimits.defaults().actionsPerTick());
            assertTrue(engine.pendingTaskCount() <= ExecutionLimits.defaults().pendingTasks());
            world.advance(1);
        }
    }
    @Test
    void missingSkillWithSocketedRunesStaysMissing() {
        assertNull(com.thuvstu.hayatemod.core.build.WeaponSkillMerger.withRuneEffects(null,
                List.of(effect("on_hit", List.of(), action("heal_self", 0, 1, "")))));
    }

    @Test
    void meleeRetainsContextDamageAndBuildModifiers() {
        var card = card();
        var mods = new BuildMods(0, 2, 1, 3);
        engine.meleeStrike(owner, card, card.skills().get("primary"),
                new CastContext(card.id() + ":primary", 0, Set.of(), owner, 0.5, mods));
        assertEquals(engine.baseDamage(card, card.skills().get("primary")) * 3,
                world.damages.getFirst().amount(), 1e-9);
    }

    @Test
    void timerAreaRunsOncePerTargetNotOncePerNearbyEntity() {
        UUID second = new UUID(0, 3);
        world.arcTargets = List.of(target, second);
        var effect = new EffectDef("on_timer", List.of(), List.of(action("heal_self", 0, 1, "")),
                List.of(), 3, "area", 4);
        engine.onTimer(card(effect), owner, BuildMods.neutral());
        assertEquals(2, world.heals.size());
    }

    @Test
    void slotExecutionOrderIsStable() {
        var skills = new java.util.LinkedHashMap<String, SkillDef>();
        skills.put("z", new SkillDef("melee_thrust", Map.of(),
                List.of(effect("on_join", List.of(), action("heal_self", 0, 2, "")))));
        skills.put("a", new SkillDef("melee_thrust", Map.of(),
                List.of(effect("on_join", List.of(), action("heal_self", 0, 1, "")))));
        var card = new WeaponCard("test:order", "Order", "sword", "magic", 1,
                List.of(), List.of(), skills, "");
        engine.onJoin(card, owner, BuildMods.neutral());
        assertEquals(List.of(owner + ":1.0", owner + ":2.0"), world.heals);
    }

    @Test
    void runtimeBudgetComesFromContent(@org.junit.jupiter.api.io.TempDir java.nio.file.Path temp)
            throws java.io.IOException {
        var source = TestContent.dir();
        try (var paths = java.nio.file.Files.walk(source)) {
            for (var path : paths.toList()) {
                var destination = temp.resolve(source.relativize(path));
                if (java.nio.file.Files.isDirectory(path)) {
                    java.nio.file.Files.createDirectories(destination);
                } else {
                    java.nio.file.Files.copy(path, destination);
                }
            }
        }
        var vocabulary = temp.resolve("vocabulary/core.yaml");
        java.nio.file.Files.writeString(vocabulary, java.nio.file.Files.readString(vocabulary)
                .replace("max_actions_per_tick: 512", "max_actions_per_tick: 3"));
        var loaded = com.thuvstu.hayatemod.core.content.ContentPipeline.load(temp);
        assertTrue(loaded.ok(), loaded.issues().toString());
        engine = new EffectEngine(loaded.set(), world);
        var effect = new EffectDef("on_join", List.of(),
                Collections.nCopies(10, action("heal_self", 0, 1, "")), List.of(), 3, "", 0);
        engine.onJoin(card(effect), owner, BuildMods.neutral());
        assertEquals(3, world.heals.size());
        java.nio.file.Files.writeString(vocabulary, java.nio.file.Files.readString(vocabulary)
                .replace("max_actions_per_tick: 3", "max_actions_per_tick: 0"));
        assertFalse(com.thuvstu.hayatemod.core.content.ContentPipeline.load(temp).ok());
    }

}
