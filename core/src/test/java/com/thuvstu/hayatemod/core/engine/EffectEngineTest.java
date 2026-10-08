package com.thuvstu.hayatemod.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.thuvstu.hayatemod.core.content.ContentPack;
import com.thuvstu.hayatemod.core.content.ContentSet;
import com.thuvstu.hayatemod.core.content.model.Models;
import com.thuvstu.hayatemod.core.engine.EffectEngine.CastOutcome;
import com.thuvstu.hayatemod.core.engine.EffectEngine.CastResult;
import com.thuvstu.hayatemod.core.engine.WorldAdapter.DamageKind;

/**
 * Effect engine tests on the real ember_branch data with a fake world.
 * base = coreBase(5.0) * itemCurve(1 + 0.05*20 = 2.0) = 10.0; split child = 10 * 0.45 = 4.5.
 */
class EffectEngineTest {
    private ContentSet content;
    private FakeWorld world;
    private EffectEngine engine;
    private UUID player;
    private UUID victim;
    private UUID bystander;

    @BeforeEach
    void setup() {
        ContentPack.LoadedPack pack = ContentPack.load(com.thuvstu.hayatemod.core.TestContent.dir());
        assertTrue(pack.ok(), "loader errors: " + pack.errors());
        content = pack.set();
        world = new FakeWorld();
        engine = new EffectEngine(content, world);
        player = UUID.randomUUID();
        victim = UUID.randomUUID();
        bystander = UUID.randomUUID();
        world.place(player, new Vec3(0, 0, 0), new Vec3(1, 0, 0));
        world.place(victim, new Vec3(3, 0, 0), new Vec3(-1, 0, 0));
        world.place(bystander, new Vec3(5, 0, 0), new Vec3(-1, 0, 0));
    }

    private CastOutcome castSpecial() {
        return engine.castSkill(player, content.weapons().get("solommo:ember_branch"), "special");
    }

    @Test
    void castAndCooldown() {
        assertEquals(CastResult.OK, castSpecial().result());
        assertEquals(1, world.bolts.size());
        assertEquals(CastResult.ON_COOLDOWN, castSpecial().result());
    }

    @Test
    void firstHitIgnitesButDoesNotSplit() {
        castSpecial();
        UUID bolt = world.bolts.get(0).id();
        engine.onProjectileHit(bolt, victim);
        assertEquals(List.of(new FakeWorld.Damage(player, victim, 10.0, DamageKind.PROJECTILE, bolt)),
                world.damages);
        // Ignite applied by delivery, but the first hit must not split (snapshot rule).
        // Proof of ignite: the next hit splits.
        assertEquals(1, world.bolts.size(), "no split on first hit (snapshot rule)");
    }

    @Test
    void secondHitOnBurningVictimSplits() {
        castSpecial();
        engine.onProjectileHit(world.bolts.get(0).id(), victim);
        world.advance(61); // past cooldown, still ignited (100t)
        assertEquals(CastResult.OK, castSpecial().result());
        engine.onProjectileHit(world.bolts.get(1).id(), victim);
        assertEquals(2 + 2, world.bolts.size(), "two split bolts spawned");
        // Child bolts deal base * 0.45 and do not split further (prevent_recursive).
        UUID child = world.bolts.get(2).id();
        int damagesBefore = world.damages.size();
        engine.onProjectileHit(child, bystander);
        assertEquals(damagesBefore + 1, world.damages.size());
        FakeWorld.Damage last = world.damages.get(world.damages.size() - 1);
        assertEquals(4.5, last.amount(), 1e-9);
        assertEquals(2 + 2, world.bolts.size(), "no recursive split");
    }

    @Test
    void meleeHitsArcTargets() {
        world.arcTargets = List.of(victim, bystander);
        engine.castSkill(player, content.weapons().get("solommo:ember_branch"), "primary");
        long meleeHits = world.damages.stream().filter(d -> d.kind() == DamageKind.MELEE).count();
        assertEquals(2, meleeHits);
    }

    @Test
    void scheduledTaskRunsAfterDelay() {
        int[] ran = {0};
        engine.schedule(10, () -> ran[0]++);
        world.advance(9);
        engine.tick();
        assertEquals(0, ran[0]);
        world.advance(1);
        engine.tick();
        assertEquals(1, ran[0]);
    }

    @Test
    void heavySlamHitsFullCircle() {
        var card = new com.thuvstu.hayatemod.core.content.model.Models.WeaponCard("test:slam", "Slam",
                "mace", "magic", 10, List.of(), List.of(),
                Map.of("special", new com.thuvstu.hayatemod.core.content.model.Models.SkillDef(
                        "heavy_slam", Map.of("radius", 6.0), List.of())),
                "");
        world.arcTargets = List.of(victim, bystander);
        var out = engine.castSkill(player, card, "special");
        assertEquals(CastResult.OK, out.result());
        assertEquals(2, world.damages.size());
        assertEquals(1, world.bursts);
    }

    @Test
    void summonSpawnsCountMinions() {
        var card = new com.thuvstu.hayatemod.core.content.model.Models.WeaponCard("test:sum", "Sum",
                "staff", "magic", 10, List.of(), List.of(),
                Map.of("special", new com.thuvstu.hayatemod.core.content.model.Models.SkillDef(
                        "summon_minions",
                        Map.of("count", 2, "enemy", "solommo:cinder_husk"), List.of())),
                "");
        var out = engine.castSkill(player, card, "special");
        assertEquals(CastResult.OK, out.result());
        assertEquals(2, world.minions.size());
        assertEquals("solommo:cinder_husk", world.minions.get(0).enemyId());
    }

    @Test
    void onKillFiresKillEffects() {
        var card = content.weapons().get("solommo:ember_branch");
        engine.onKill(card, player, victim);
        assertEquals(2, world.bolts.size(), "on_kill spawns two splinters");
    }

    @Test
    void heroicMultiplierScalesPlayerDamage() {
        world.power = 10.0;
        castSpecial();
        engine.onProjectileHit(world.bolts.get(0).id(), victim);
        FakeWorld.Damage hit = world.damages.get(0);
        assertEquals(100.0, hit.amount(), 1e-9);
    }

    @Test
    void cooldownMultShortensCooldown() {
        var quick = new com.thuvstu.hayatemod.core.content.model.Models.BuildMods(0, 1.0, 0.5, 1.0);
        var card = content.weapons().get("solommo:ember_branch");
        assertEquals(CastResult.OK, engine.castSkill(player, card, "special", quick).result());
        world.advance(30); // half of 60t
        assertEquals(CastResult.OK, engine.castSkill(player, card, "special", quick).result());
        assertEquals(CastResult.ON_COOLDOWN,
                engine.castSkill(player, card, "special",
                        com.thuvstu.hayatemod.core.content.model.Models.BuildMods.neutral()).result());
    }

    @Test
    void eventListenerObservesCasts() {
        var events = new ArrayList<String>();
        engine.setEventListener(events::add);
        castSpecial();
        engine.onProjectileHit(world.bolts.get(0).id(), victim);
        world.advance(61);
        castSpecial();
        engine.onProjectileHit(world.bolts.get(1).id(), victim);
        assertTrue(events.contains("cast solommo:ember_branch:special"));
        assertTrue(events.contains("split x2"), "events: " + events);
    }

    @Test
    void onDamagedRetaliatesAtAttacker() {
        var card = new com.thuvstu.hayatemod.core.content.model.Models.WeaponCard("test:thorn", "Thorn",
                "sword", "magic", 10, List.of(), List.of(),
                Map.of("primary", new com.thuvstu.hayatemod.core.content.model.Models.SkillDef(
                        "melee_thrust", Map.of(),
                        List.of(new com.thuvstu.hayatemod.core.content.model.Models.EffectDef(
                                "on_damaged", List.of(),
                                List.of(new com.thuvstu.hayatemod.core.content.model.Models.ActionDef(
                                        "spawn_projectiles", 1, 1.0, "attacker", 6.0, List.of(), 0.0,
                                        0.0, "", "", "", "")),
                                List.of(), 3, "", 0.0)))),
                "");
        engine.onDamaged(card, victim, player);
        assertEquals(1, world.bolts.size(), "retaliation spawns one bolt");
        assertEquals(victim, world.bolts.get(0).owner());
    }

    @Test
    void dashCoreMovesCaster() {
        var card = new com.thuvstu.hayatemod.core.content.model.Models.WeaponCard("test:dash", "Dash",
                "sword", "magic", 10, List.of(), List.of(),
                Map.of("special", new com.thuvstu.hayatemod.core.content.model.Models.SkillDef(
                        "dash", Map.of("distance", 8.0, "cooldown", 1.0), List.of())),
                "");
        assertEquals(CastResult.OK, engine.castSkill(player, card, "special").result());
        assertEquals(new Vec3(8, 0, 0), world.pos(player));
    }

    @Test
    void healSelfOnKill() {
        var card = new com.thuvstu.hayatemod.core.content.model.Models.WeaponCard("test:siphon",
                "Siphon", "sword", "magic", 10, List.of(), List.of(),
                Map.of("primary", new com.thuvstu.hayatemod.core.content.model.Models.SkillDef(
                        "melee_thrust", Map.of(),
                        List.of(new com.thuvstu.hayatemod.core.content.model.Models.EffectDef(
                                "on_kill", List.of(),
                                List.of(new com.thuvstu.hayatemod.core.content.model.Models.ActionDef(
                                        "heal_self", 0, 0.0, "", 0.0, List.of(), 0.0, 15.0, "", "", "", "")),
                                List.of(), 3, "", 0.0)))),
                "");
        engine.onKill(card, player, victim);
        assertEquals(List.of(player + ":15.0"), world.heals);
    }

    // ---- wave-1 variety (mythic reference) ----

    private static Models.WeaponCard fxCard(String slot, Map<String, Object> mods, String trigger,
            List<Models.ConditionDef> conds, Models.ActionDef... acts) {
        return new Models.WeaponCard("test:fx", "Fx", "sword", "magic", 10, List.of(), List.of(),
                Map.of(slot, new Models.SkillDef("melee_thrust", mods,
                        List.of(new Models.EffectDef(trigger, conds, List.of(acts), List.of(),
                                3, "", 0.0)))),
                "");
    }

    private static Models.ActionDef act(String type, int count, double mult, String target,
            double radius, double distance, double amount, String status) {
        return new Models.ActionDef(type, count, mult, target, radius, List.of(), distance,
                amount, status, "", "", "");
    }

    private static Models.ActionDef actRef(String type, String ref) {
        return new Models.ActionDef(type, 0, 0, "", 0, List.of(), 0, 0, "", "", ref, "");
    }

    private static Models.ActionDef actFormula(String type, double amount, String formula) {
        return new Models.ActionDef(type, 0, 0, "", 0, List.of(), 0, amount, "", "", "", formula);
    }

    @Test
    void onBreakFiresChanceHeal() {
        var card = fxCard("primary", Map.of(), "on_break",
                List.of(new Models.ConditionDef("chance", null, 0.25, "", 0)),
                act("heal_self", 0, 0, "", 0, 0, 4, ""));
        world.chanceResult = true;
        engine.onBreak(card, player, Models.BuildMods.neutral());
        assertEquals(List.of(player + ":4.0"), world.heals);
        world.chanceResult = false;
        engine.onBreak(card, player, Models.BuildMods.neutral());
        assertEquals(1, world.heals.size(), "failed roll fires nothing");
    }

    @Test
    void onDeathBurstsProjectiles() {
        var card = fxCard("primary", Map.of(), "on_death", List.of(),
                act("spawn_projectiles", 2, 1.0, "", 6.0, 0, 0, ""));
        engine.onDeath(card, victim, player, Models.BuildMods.neutral());
        assertEquals(2, world.bolts.size());
        assertEquals(victim, world.bolts.get(0).owner());
    }

    @Test
    void onTimerAuraAppliesStatusWithInterval() {
        var events = new ArrayList<String>();
        engine.setEventListener(events::add);
        var card = fxCard("primary", Map.of("interval", 5.0, "radius", 4.0), "on_timer",
                List.of(), act("apply_status", 0, 0, "", 0, 0, 0, "ignite"));
        world.arcTargets = List.of(victim);
        engine.onTimer(card, player, Models.BuildMods.neutral());
        assertTrue(events.contains("status ignite"), "events: " + events);
        events.clear();
        engine.onTimer(card, player, Models.BuildMods.neutral());
        assertTrue(events.isEmpty(), "interval gates the second tick: " + events);
        world.advance(100);
        engine.onTimer(card, player, Models.BuildMods.neutral());
        assertTrue(events.contains("status ignite"));
    }

    @Test
    void sneakingGatesAmbush() {
        var card = fxCard("primary", Map.of(), "on_hit",
                List.of(new Models.ConditionDef("sneaking", null, 0, "", 0)),
                act("spawn_projectiles", 1, 1.0, "", 6.0, 0, 0, ""));
        world.arcTargets = List.of(victim);
        engine.meleeStrike(player, card, card.skills().get("primary"),
                new CastContext("test:fx:primary", 0, Set.of(), player, 1.0,
                        Models.BuildMods.neutral()));
        assertEquals(0, world.bolts.size(), "standing fires nothing");
        world.sneaking.add(player);
        engine.meleeStrike(player, card, card.skills().get("primary"),
                new CastContext("test:fx:primary", 0, Set.of(), player, 1.0,
                        Models.BuildMods.neutral()));
        assertEquals(1, world.bolts.size(), "sneaking ambushes");
    }

    @Test
    void healthBelowGatesExecute() {
        var card = fxCard("primary", Map.of(), "on_hit",
                List.of(new Models.ConditionDef("health_below", null, 0.3, "", 0)),
                act("knockback", 0, 2.0, "", 0, 0, 0, ""));
        world.arcTargets = List.of(victim);
        world.health.put(victim, 0.9);
        engine.meleeStrike(player, card, card.skills().get("primary"),
                new CastContext("test:fx:primary", 0, Set.of(), player, 1.0,
                        Models.BuildMods.neutral()));
        assertTrue(world.launches.isEmpty());
        world.health.put(victim, 0.2);
        engine.meleeStrike(player, card, card.skills().get("primary"),
                new CastContext("test:fx:primary", 0, Set.of(), player, 1.0,
                        Models.BuildMods.neutral()));
        assertEquals(1, world.launches.size(), "execute knockback: " + world.launches);
    }

    @Test
    void lookTargetShootsStraight() {
        var card = fxCard("primary", Map.of(), "on_hit", List.of(),
                act("spawn_projectiles", 1, 1.0, "look", 6.0, 0, 0, ""));
        world.arcTargets = List.of(victim);
        engine.meleeStrike(player, card, card.skills().get("primary"),
                new CastContext("test:fx:primary", 0, Set.of(), player, 1.0,
                        Models.BuildMods.neutral()));
        assertEquals(1, world.bolts.size());
        assertEquals(new Vec3(1, 0, 0), world.bolts.get(0).dir());
    }

    @Test
    void blinkMovesOwnerToVictim() {
        var card = fxCard("primary", Map.of(), "on_hit", List.of(),
                act("blink", 0, 0, "", 0, 0, 0, ""));
        world.arcTargets = List.of(victim);
        engine.meleeStrike(player, card, card.skills().get("primary"),
                new CastContext("test:fx:primary", 0, Set.of(), player, 1.0,
                        Models.BuildMods.neutral()));
        assertEquals(world.pos(victim), world.pos(player));
    }

    @Test
    void leapLaunchesOwner() {
        var card = fxCard("primary", Map.of(), "on_hit", List.of(),
                act("leap", 0, 0, "", 0, 7.0, 0, ""));
        world.arcTargets = List.of(victim);
        engine.meleeStrike(player, card, card.skills().get("primary"),
                new CastContext("test:fx:primary", 0, Set.of(), player, 1.0,
                        Models.BuildMods.neutral()));
        assertEquals(1, world.launches.size());
        assertTrue(world.launches.get(0).startsWith(player.toString() + ":"),
                world.launches.toString());
    }

    // ---- wave-2 full port ----

    @Test
    void strikeDealsDirectDamage() {
        var card = fxCard("primary", Map.of(), "on_hit", List.of(),
                act("strike", 0, 2.0, "", 0, 0, 0, ""));
        world.arcTargets = List.of(victim);
        engine.meleeStrike(player, card, card.skills().get("primary"),
                new CastContext("test:fx:primary", 0, Set.of(), player, 1.0,
                        Models.BuildMods.neutral()));
        long strikes = world.damages.stream().filter(d -> d.kind() == DamageKind.MELEE).count();
        assertEquals(2, strikes, "melee hit + strike: " + world.damages);
    }

    @Test
    void retaliationLoopTerminates() {
        var card = fxCard("primary", Map.of(), "on_damaged", List.of(),
                act("strike", 0, 1.0, "", 0, 0, 0, ""));
        // Fake funnel: engine damage re-enters the victim's on_damaged, like the mod.
        final EffectEngine[] ref = new EffectEngine[1];
        var looping = new FakeWorld() {
            @Override
            public void dealDamage(UUID attacker, UUID target, double amount, DamageKind kind,
                    UUID direct) {
                super.dealDamage(attacker, target, amount, kind, direct);
                ref[0].onDamaged(card, target, attacker);
            }
        };
        looping.place(player, new Vec3(0, 0, 0), new Vec3(1, 0, 0));
        looping.place(victim, new Vec3(3, 0, 0), new Vec3(-1, 0, 0));
        ref[0] = new EffectEngine(content, looping);
        ref[0].onDamaged(card, victim, player);
        assertTrue(looping.damages.size() <= 12,
                "depth guard bounds ping-pong: " + looping.damages.size());
        assertTrue(looping.damages.size() > 1, "at least one retaliation fired");
    }

    @Test
    void igniteAndExtinguish() {
        var card = fxCard("primary", Map.of(), "on_hit", List.of(),
                act("ignite", 0, 0, "", 0, 0, 5, ""));
        world.arcTargets = List.of(victim);
        engine.meleeStrike(player, card, card.skills().get("primary"),
                new CastContext("test:fx:primary", 0, Set.of(), player, 1.0,
                        Models.BuildMods.neutral()));
        assertEquals(List.of(victim + ":100"), world.fires);
        var card2 = fxCard("primary", Map.of(), "on_hit", List.of(),
                act("extinguish", 0, 0, "", 0, 0, 0, ""));
        engine.meleeStrike(player, card2, card2.skills().get("primary"),
                new CastContext("test:fx:primary", 0, Set.of(), player, 1.0,
                        Models.BuildMods.neutral()));
        assertEquals(List.of(victim + ":100", victim + ":0"), world.fires);
    }

    @Test
    void potionAndCleanse() {
        var card = fxCard("primary", Map.of(), "on_hit", List.of(),
                new Models.ActionDef("potion", 0, 1, "", 0, List.of(), 0, 30, "", "speed", "",
                        ""));
        world.arcTargets = List.of(victim);
        engine.meleeStrike(player, card, card.skills().get("primary"),
                new CastContext("test:fx:primary", 0, Set.of(), player, 1.0,
                        Models.BuildMods.neutral()));
        assertEquals(List.of(victim + ":speed:30:1"), world.fx);
        var card2 = fxCard("primary", Map.of(), "on_hit", List.of(),
                act("cleanse", 0, 0, "", 0, 0, 0, ""));
        engine.meleeStrike(player, card2, card2.skills().get("primary"),
                new CastContext("test:fx:primary", 0, Set.of(), player, 1.0,
                        Models.BuildMods.neutral()));
        assertEquals(List.of(victim), world.cleansed);
    }

    @Test
    void summonSpawnsMinion() {
        var card = fxCard("primary", Map.of(), "on_kill", List.of(),
                actRef("summon", "solommo:cinder_husk"));
        engine.onKill(card, player, victim);
        assertEquals(1, world.minions.size());
        assertEquals("solommo:cinder_husk", world.minions.get(0).enemyId());
    }

    @Test
    void lightningAndExplosion() {
        var card = fxCard("primary", Map.of(), "on_hit", List.of(),
                act("lightning", 0, 1.5, "", 0, 0, 0, ""));
        world.arcTargets = List.of(victim);
        engine.meleeStrike(player, card, card.skills().get("primary"),
                new CastContext("test:fx:primary", 0, Set.of(), player, 1.0,
                        Models.BuildMods.neutral()));
        assertEquals(1, world.lightnings.size());
        var card2 = fxCard("primary", Map.of(), "on_hit", List.of(),
                act("explosion", 0, 3.0, "", 0, 0, 0, ""));
        engine.meleeStrike(player, card2, card2.skills().get("primary"),
                new CastContext("test:fx:primary", 0, Set.of(), player, 1.0,
                        Models.BuildMods.neutral()));
        assertEquals(List.of("3.0"), world.explosions);
    }

    @Test
    void presentationAndUtility() {
        var card = fxCard("primary", Map.of(), "on_hit", List.of(),
                new Models.ActionDef("particles", 0, 0, "", 0, List.of(), 0, 0, "", "flame",
                        "", ""),
                new Models.ActionDef("sound", 0, 0, "", 0, List.of(), 0, 0, "",
                        "entity_player_levelup", "", ""),
                actRef("message", "hello"),
                new Models.ActionDef("feed", 0, 0.5, "", 0, List.of(), 0, 6, "", "", "", ""),
                new Models.ActionDef("xp", 0, 0, "", 0, List.of(), 0, 7, "", "", "", ""),
                new Models.ActionDef("dropitem", 2, 0, "", 0, List.of(), 0, 0, "", "",
                        "pity_shard", ""),
                new Models.ActionDef("giveitem", 3, 0, "", 0, List.of(), 0, 0, "", "",
                        "emerald", ""));
        world.arcTargets = List.of(victim);
        engine.meleeStrike(player, card, card.skills().get("primary"),
                new CastContext("test:fx:primary", 0, Set.of(), player, 1.0,
                        Models.BuildMods.neutral()));
        assertEquals(List.of("flame"), world.particlesPlayed);
        assertEquals(List.of("entity_player_levelup"), world.soundsPlayed);
        assertEquals(List.of(player + ":hello"), world.announced);
        assertEquals(List.of(player + ":6"), world.fed);
        assertEquals(List.of(player + ":7"), world.xpGiven);
        assertEquals(List.of("pity_shard:2"), world.dropped);
        assertEquals(List.of(player + ":emerald:3"), world.given);
    }

    @Test
    void variablesGateEffects() {
        var setter = new Models.WeaponCard("test:fx", "Fx", "sword", "magic", 10, List.of(),
                List.of(),
                Map.of("primary", new Models.SkillDef("melee_thrust", Map.of(),
                        List.of(new Models.EffectDef("on_hit", List.of(),
                                List.of(new Models.ActionDef("setvar", 0, 0, "", 0, List.of(), 0,
                                        3, "", "", "rage", "")),
                                List.of(), 3, "", 0.0)))),
                "");
        var adder = new Models.WeaponCard("test:fx", "Fx", "sword", "magic", 10, List.of(),
                List.of(),
                Map.of("primary", new Models.SkillDef("melee_thrust", Map.of(),
                        List.of(new Models.EffectDef("on_hit", List.of(),
                                List.of(new Models.ActionDef("addvar", 0, 0, "", 0,
                                        List.of(), 0, 4, "", "", "rage", "")),
                                List.of(), 3, "", 0.0)))),
                "");
        world.arcTargets = List.of(victim);
        var ctx = new CastContext("test:fx:primary", 0, Set.of(), player, 1.0,
                Models.BuildMods.neutral());
        engine.meleeStrike(player, setter, setter.skills().get("primary"), ctx);
        var gated = new Models.WeaponCard("test:fx", "Fx", "sword", "magic", 10, List.of(),
                List.of(),
                Map.of("primary", new Models.SkillDef("melee_thrust", Map.of(),
                        List.of(new Models.EffectDef("on_hit",
                                List.of(new Models.ConditionDef("variable", null, 5, "rage",
                                        0)),
                                List.of(act("heal_self", 0, 0, "", 0, 0, 9, "")),
                                List.of(), 3, "", 0.0)))),
                "");
        engine.meleeStrike(player, gated, gated.skills().get("primary"), ctx);
        assertTrue(world.heals.isEmpty(), "rage=3 below threshold");
        engine.meleeStrike(player, adder, adder.skills().get("primary"), ctx);
        engine.meleeStrike(player, gated, gated.skills().get("primary"), ctx);
        assertEquals(List.of(player + ":9.0"), world.heals, "rage=7 passes");
    }

    @Test
    void castActionFiresSlot() {
        var card = new Models.WeaponCard("test:fx", "Fx", "sword", "magic", 10, List.of(),
                List.of(),
                Map.of(
                        "primary", new Models.SkillDef("melee_thrust", Map.of(),
                                List.of(new Models.EffectDef("on_hit", List.of(),
                                        List.of(actRef("cast", "special")), List.of(), 3, "",
                                        0.0))),
                        "special", new Models.SkillDef("melee_thrust", Map.of(), List.of())),
                "");
        world.arcTargets = List.of(victim);
        engine.meleeStrike(player, card, card.skills().get("primary"),
                new CastContext("test:fx:primary", 0, Set.of(), player, 1.0,
                        Models.BuildMods.neutral()));
        assertEquals(2, world.damages.size(), "melee hit + casted melee: " + world.damages);
    }

    @Test
    void delayDefersTail() {
        var card = fxCard("primary", Map.of(), "on_hit", List.of(),
                act("heal_self", 0, 0, "", 0, 0, 5, ""),
                act("delay", 0, 0, "", 0, 0, 10, ""),
                act("heal_self", 0, 0, "", 0, 0, 7, ""));
        world.arcTargets = List.of(victim);
        engine.meleeStrike(player, card, card.skills().get("primary"),
                new CastContext("test:fx:primary", 0, Set.of(), player, 1.0,
                        Models.BuildMods.neutral()));
        assertEquals(List.of(player + ":5.0"), world.heals);
        world.advance(10);
        engine.tick();
        assertEquals(List.of(player + ":5.0", player + ":7.0"), world.heals);
    }

    @Test
    void areaScopeHitsEveryone() {
        var effect = new Models.EffectDef("on_hit", List.of(),
                List.of(act("strike", 0, 1.0, "", 0, 0, 0, "")), List.of(), 3, "area", 6.0);
        var card = new Models.WeaponCard("test:fx", "Fx", "sword", "magic", 10, List.of(),
                List.of(),
                Map.of("primary", new Models.SkillDef("melee_thrust", Map.of(), List.of(effect))),
                "");
        world.arcTargets = List.of(victim, bystander);
        engine.meleeStrike(player, card, card.skills().get("primary"),
                new CastContext("test:fx:primary", 0, Set.of(), player, 1.0,
                        Models.BuildMods.neutral()));
        // 2 melee hits + 2 area strikes per melee target.
        assertEquals(6, world.damages.size(), "damages: " + world.damages);
    }

    @Test
    void onJoinFiresWelcome() {
        var card = fxCard("primary", Map.of(), "on_join", List.of(),
                act("heal_self", 0, 0, "", 0, 0, 12, ""));
        engine.onJoin(card, player, Models.BuildMods.neutral());
        assertEquals(List.of(player + ":12.0"), world.heals);
    }

    // ----丸パクリv1: resource / formula / repeat / missile ----

    private static Models.WeaponCard resCard(String resource, double cost) {
        return new Models.WeaponCard("test:res", "Res", "sword", "magic", 10, List.of(),
                List.of(),
                Map.of("special", new Models.SkillDef("melee_thrust",
                        Map.of("resource", resource, "resource_cost", cost,
                                "cooldown", 0.05),
                        List.of())),
                "");
    }

    @Test
    void resourceGateDeniesAndConsumes() {
        var card = resCard("mana", 20.0);
        world.consumeResult = false;
        var out = engine.castSkill(player, card, "special");
        assertEquals(CastResult.NO_RESOURCE, out.result());
        assertEquals(List.of(player + ":mana:20.0"), world.consumed);
        world.consumeResult = true;
        world.advance(1);
        assertEquals(CastResult.OK, engine.castSkill(player, card, "special").result());
    }

    @Test
    void formulaHealUsesBindings() {
        world.resources.put(player + ":mana", 50.0);
        var card = fxCard("primary", Map.of(), "on_hit", List.of(),
                actFormula("heal_self", 0, "10+mana*0.2"));
        world.arcTargets = List.of(victim);
        engine.meleeStrike(player, card, card.skills().get("primary"),
                new CastContext("test:fx:primary", 0, Set.of(), player, 1.0,
                        Models.BuildMods.neutral()));
        assertEquals(List.of(player + ":20.0"), world.heals);
    }

    @Test
    void repeatRerunsHead() {
        var card = fxCard("primary", Map.of(), "on_hit", List.of(),
                act("heal_self", 0, 0, "", 0, 0, 5, ""),
                new Models.ActionDef("repeat", 3, 0, "", 0, List.of(), 0, 20, "", "", "", ""));
        world.arcTargets = List.of(victim);
        engine.meleeStrike(player, card, card.skills().get("primary"),
                new CastContext("test:fx:primary", 0, Set.of(), player, 1.0,
                        Models.BuildMods.neutral()));
        assertEquals(List.of(player + ":5.0"), world.heals, "first pass immediate");
        world.advance(20);
        engine.tick();
        assertEquals(2, world.heals.size(), "first repeat fired");
        world.advance(20);
        engine.tick();
        assertEquals(3, world.heals.size(), "second repeat fired");
    }

    @Test
    void missileLocksAndFlies() {
        var card = fxCard("primary", Map.of(), "on_hit", List.of(),
                new Models.ActionDef("missile", 2, 1.0, "", 8.0, List.of(), 1.6, 120, "", "",
                        "", ""));
        world.arcTargets = List.of(victim);
        world.nearestResult = bystander;
        engine.meleeStrike(player, card, card.skills().get("primary"),
                new CastContext("test:fx:primary", 0, Set.of(), player, 1.0,
                        Models.BuildMods.neutral()));
        assertEquals(2, world.missileShots.size());
        var m = world.missileShots.get(0);
        assertEquals(bystander, m.target());
        assertEquals(1.6, m.speed(), 1e-9);
        assertEquals(120, m.lifetime());
        assertEquals(player, m.owner());
    }

    @Test
    void missileFallsBackToFacing() {
        var card = fxCard("primary", Map.of(), "on_hit", List.of(),
                new Models.ActionDef("missile", 1, 1.0, "", 8.0, List.of(), 0, 0, "", "", "",
                        ""));
        world.arcTargets = List.of(victim);
        world.nearestResult = null;
        engine.meleeStrike(player, card, card.skills().get("primary"),
                new CastContext("test:fx:primary", 0, Set.of(), player, 1.0,
                        Models.BuildMods.neutral()));
        assertEquals(1, world.missileShots.size());
        var m = world.missileShots.get(0);
        assertEquals(null, m.target());
        assertEquals(new Vec3(1, 0, 0), m.dir(), "straight along facing");
        assertEquals(1.4, m.speed(), 1e-9);
    }

    @Test
    void holdingGatesSynergy() {
        var card = fxCard("primary", Map.of(), "on_hit",
                List.of(new Models.ConditionDef("holding", null, 0, "solommo:ember_edge", 0)),
                act("heal_self", 0, 0, "", 0, 0, 2, ""));
        world.arcTargets = List.of(victim);
        var ctx = new CastContext("test:fx:primary", 0, Set.of(), player, 1.0,
                Models.BuildMods.neutral());
        world.held.put(player, "solommo:ashbrand");
        engine.meleeStrike(player, card, card.skills().get("primary"), ctx);
        assertTrue(world.heals.isEmpty());
        world.held.put(player, "solommo:ember_edge");
        engine.meleeStrike(player, card, card.skills().get("primary"), ctx);
        assertEquals(List.of(player + ":2.0"), world.heals);
    }

    @Test
    void worldStateConditions() {
        var ctx = new CastContext("test:fx:primary", 0, Set.of(), player, 1.0,
                Models.BuildMods.neutral());
        world.arcTargets = List.of(victim);
        // altitude: victim at y=0, threshold 64 -> blocked; lower it -> fires.
        var alt = fxCard("primary", Map.of(), "on_hit",
                List.of(new Models.ConditionDef("altitude", null, 64, "", 0)),
                act("heal_self", 0, 0, "", 0, 0, 1, ""));
        engine.meleeStrike(player, alt, alt.skills().get("primary"), ctx);
        assertTrue(world.heals.isEmpty());
        world.place(victim, new Vec3(3, 70, 0), new Vec3(-1, 0, 0));
        engine.meleeStrike(player, alt, alt.skills().get("primary"), ctx);
        assertEquals(List.of(player + ":1.0"), world.heals);
        // burning target.
        var burn = fxCard("primary", Map.of(), "on_hit",
                List.of(new Models.ConditionDef("burning", null, 0, "", 0)),
                act("heal_self", 0, 0, "", 0, 0, 2, ""));
        engine.meleeStrike(player, burn, burn.skills().get("primary"), ctx);
        assertEquals(1, world.heals.size());
        world.burning.add(victim);
        engine.meleeStrike(player, burn, burn.skills().get("primary"), ctx);
        assertEquals(List.of(player + ":1.0", player + ":2.0"), world.heals);
        // day flag.
        var day = fxCard("primary", Map.of(), "on_hit",
                List.of(new Models.ConditionDef("day", null, 0, "", 0)),
                act("heal_self", 0, 0, "", 0, 0, 3, ""));
        engine.meleeStrike(player, day, day.skills().get("primary"), ctx);
        assertEquals(2, world.heals.size());
        world.flags.put("day", true);
        engine.meleeStrike(player, day, day.skills().get("primary"), ctx);
        assertEquals(List.of(player + ":1.0", player + ":2.0", player + ":3.0"), world.heals);
        // entity type.
        var typ = fxCard("primary", Map.of(), "on_hit",
                List.of(new Models.ConditionDef("entity_type", null, 0, "entity.minecraft.zombie",
                        0)),
                act("heal_self", 0, 0, "", 0, 0, 4, ""));
        engine.meleeStrike(player, typ, typ.skills().get("primary"), ctx);
        assertEquals(3, world.heals.size());
        world.types.put(victim, "entity.minecraft.zombie");
        engine.meleeStrike(player, typ, typ.skills().get("primary"), ctx);
        assertEquals(4, world.heals.size());
        assertEquals(player + ":4.0", world.heals.get(3));
        // vanilla effect.
        var has = fxCard("primary", Map.of(), "on_hit",
                List.of(new Models.ConditionDef("has_effect", null, 0, "speed", 0)),
                act("heal_self", 0, 0, "", 0, 0, 5, ""));
        engine.meleeStrike(player, has, has.skills().get("primary"), ctx);
        assertEquals(4, world.heals.size());
        world.addEffect(victim, "speed", 30, 0);
        engine.meleeStrike(player, has, has.skills().get("primary"), ctx);
        assertEquals(5, world.heals.size());
    }

    static class FakeWorld implements WorldAdapter {
        record Bolt(UUID id, UUID owner, Vec3 origin, Vec3 dir, double speed, CastContext ctx) {
        }

        record Damage(UUID attacker, UUID target, double amount, DamageKind kind, UUID direct) {
        }

        record Minion(UUID id, UUID owner, String enemyId, Vec3 pos) {
        }

        final List<Bolt> bolts = new ArrayList<>();
        final List<Damage> damages = new ArrayList<>();
        final List<Minion> minions = new ArrayList<>();
        final List<Vec3> rings = new ArrayList<>();
        final List<String> heals = new ArrayList<>();
        int bursts;
        final Map<UUID, Vec3> positions = new HashMap<>();
        final Map<UUID, Vec3> facings = new HashMap<>();
        final Set<UUID> burning = new HashSet<>();
        List<UUID> arcTargets = List.of();
        Vec3 aimResult = new Vec3(1, 0, 0);
        double power = 1.0;
        long now;

        void place(UUID id, Vec3 pos, Vec3 facing) {
            positions.put(id, pos);
            facings.put(id, facing);
        }

        void advance(long ticks) {
            now += ticks;
        }

        @Override
        public long gameTime() {
            return now;
        }

        @Override
        public Vec3 eyePos(UUID entity) {
            return positions.get(entity);
        }

        @Override
        public Vec3 facing(UUID entity) {
            return facings.get(entity);
        }

        @Override
        public Vec3 pos(UUID entity) {
            return positions.get(entity);
        }

        @Override
        public UUID spawnBolt(UUID owner, Vec3 origin, Vec3 direction, double speed, CastContext ctx) {
            UUID id = UUID.randomUUID();
            bolts.add(new Bolt(id, owner, origin, direction, speed, ctx));
            return id;
        }

        @Override
        public void dealDamage(UUID attacker, UUID target, double amount, DamageKind kind, UUID direct) {
            damages.add(new Damage(attacker, target, amount, kind, direct));
        }

        @Override
        public UUID spawnMinion(UUID owner, String enemyId, Vec3 pos) {
            UUID id = UUID.randomUUID();
            minions.add(new Minion(id, owner, enemyId, pos));
            positions.put(id, pos);
            return id;
        }

        @Override
        public void ringParticles(UUID anchor, Vec3 center, double radius) {
            rings.add(center);
        }

        @Override
        public void burstParticles(UUID anchor, Vec3 center) {
            bursts++;
        }

        @Override
        public Vec3 aimAtNearest(UUID owner, Vec3 origin, double radius, UUID exclude) {
            return aimResult;
        }

        @Override
        public List<UUID> targetsInArc(UUID attacker, double range, double halfAngleCos) {
            return arcTargets;
        }

        @Override
        public boolean isBurning(UUID entity) {
            return burning.contains(entity);
        }

        @Override
        public double powerMultiplier(UUID attacker) {
            return power;
        }

        @Override
        public Vec3 directionTo(UUID from, UUID to) {
            Vec3 a = positions.get(from);
            Vec3 b = positions.get(to);
            if (a == null || b == null) {
                return new Vec3(1, 0, 0);
            }
            return new Vec3(b.x() - a.x(), 0, b.z() - a.z()).normalize();
        }

        @Override
        public void healEntity(UUID target, double amount) {
            heals.add(target + ":" + amount);
        }

        @Override
        public void moveEntity(UUID entity, Vec3 dest) {
            positions.put(entity, dest);
        }

        final List<String> launches = new ArrayList<>();
        final Set<UUID> sneaking = new HashSet<>();
        final Map<UUID, Double> health = new HashMap<>();
        boolean chanceResult = true;

        @Override
        public void launch(UUID entity, Vec3 velocity) {
            launches.add(entity + ":" + velocity.x() + "," + velocity.y() + "," + velocity.z());
        }

        @Override
        public boolean isSneaking(UUID entity) {
            return sneaking.contains(entity);
        }

        @Override
        public double healthRatio(UUID entity) {
            return health.getOrDefault(entity, 1.0);
        }

        @Override
        public boolean rollChance(double p) {
            return chanceResult && p > 0.0;
        }

        final List<String> fires = new ArrayList<>();
        final List<String> fx = new ArrayList<>();
        final List<UUID> cleansed = new ArrayList<>();
        final Map<UUID, Set<String>> vanillaFx = new HashMap<>();
        final List<String> particlesPlayed = new ArrayList<>();
        final List<String> soundsPlayed = new ArrayList<>();
        final List<String> announced = new ArrayList<>();
        final List<String> fed = new ArrayList<>();
        final List<String> xpGiven = new ArrayList<>();
        final List<String> given = new ArrayList<>();
        final List<String> dropped = new ArrayList<>();
        final List<String> lightnings = new ArrayList<>();
        final List<String> explosions = new ArrayList<>();
        final Map<UUID, String> held = new HashMap<>();
        final Map<UUID, String> types = new HashMap<>();
        final Map<String, Boolean> flags = new HashMap<>();

        @Override
        public void setFireTicks(UUID entity, int ticks) {
            fires.add(entity + ":" + ticks);
        }

        @Override
        public void addEffect(UUID entity, String effect, int seconds, int amplifier) {
            fx.add(entity + ":" + effect + ":" + seconds + ":" + amplifier);
            vanillaFx.computeIfAbsent(entity, k -> new HashSet<>()).add(effect);
        }

        @Override
        public void cleanseEffects(UUID entity) {
            cleansed.add(entity);
            vanillaFx.remove(entity);
        }

        @Override
        public boolean hasStatusEffect(UUID entity, String effect) {
            if (vanillaFx.containsKey(entity)) {
                return true;
            }
            return effect.equals("ignite") && burning.contains(entity);
        }

        @Override
        public void playParticles(String particle, Vec3 pos) {
            particlesPlayed.add(particle);
        }

        @Override
        public void playSound(String sound, Vec3 pos) {
            soundsPlayed.add(sound);
        }

        @Override
        public void announce(UUID entity, String text) {
            announced.add(entity + ":" + text);
        }

        @Override
        public void feed(UUID entity, int food, float saturation) {
            fed.add(entity + ":" + food);
        }

        @Override
        public void grantXp(UUID entity, int points) {
            xpGiven.add(entity + ":" + points);
        }

        @Override
        public void giveItem(UUID entity, String item, int count) {
            given.add(entity + ":" + item + ":" + count);
        }

        @Override
        public void dropItemAt(UUID anchor, String item, int count) {
            dropped.add(item + ":" + count);
        }

        @Override
        public void strikeLightning(UUID owner, UUID target, double damage) {
            lightnings.add(owner + "->" + target + ":" + damage);
        }

        @Override
        public void explode(UUID owner, Vec3 pos, double power) {
            explosions.add(power + "");
        }

        @Override
        public String heldWeaponId(UUID entity) {
            return held.getOrDefault(entity, "");
        }

        @Override
        public String entityTypeId(UUID entity) {
            return types.getOrDefault(entity, "entity.minecraft.pig");
        }

        @Override
        public boolean worldFlag(String kind) {
            return flags.getOrDefault(kind, false);
        }

        UUID nearestResult;
        final List<String> missiles = new ArrayList<>();
        boolean consumeResult = true;
        final List<String> consumed = new ArrayList<>();
        final Map<String, Double> resources = new HashMap<>();

        record Missile(UUID id, UUID owner, Vec3 origin, Vec3 dir, double speed, long lifetime,
                UUID target, CastContext ctx) {
        }

        final List<Missile> missileShots = new ArrayList<>();

        @Override
        public UUID nearestLiving(UUID owner, Vec3 origin, double radius, UUID exclude) {
            return nearestResult;
        }

        @Override
        public UUID spawnMissile(UUID owner, Vec3 origin, Vec3 direction, double speed,
                long lifetimeTicks, UUID target, CastContext ctx) {
            UUID id = UUID.randomUUID();
            missileShots.add(new Missile(id, owner, origin, direction, speed, lifetimeTicks,
                    target, ctx));
            missiles.add(owner + "->" + target);
            return id;
        }

        @Override
        public boolean tryConsumeResource(UUID owner, String name, double cost) {
            consumed.add(owner + ":" + name + ":" + cost);
            return consumeResult;
        }

        @Override
        public double resourceLevel(UUID owner, String name) {
            return resources.getOrDefault(owner + ":" + name, 100.0);
        }
    }
}
