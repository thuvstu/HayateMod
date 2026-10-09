package com.thuvstu.hayatemod.core.engine;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.thuvstu.hayatemod.core.TestContent;
import com.thuvstu.hayatemod.core.content.ContentPack;
import com.thuvstu.hayatemod.core.content.model.Models.*;
import com.thuvstu.hayatemod.core.engine.EffectEngine.CastResult;

class CastingTest {
    final UUID owner = new UUID(0, 1), other = new UUID(0, 2);
    EffectEngineTest.FakeWorld world;
    EffectEngine engine;
    WeaponCard card;
    @BeforeEach void setup() {
        var content = ContentPack.load(TestContent.dir()).set();
        world = new EffectEngineTest.FakeWorld();
        world.place(owner, new Vec3(0,0,0), new Vec3(1,0,0));
        world.place(other, new Vec3(2,0,0), new Vec3(1,0,0));
        engine = new EffectEngine(content, world);
        card = content.weapons().get("solommo:ashen_chant");
    }
    void advance(int ticks) { world.advance(ticks); engine.tick(); }
    void kill() { engine.onKill(card, owner, other); }
    @Test void deliveryWaitsUntilExactBoundaryAndRunsOnce() {
        assertEquals(CastResult.CASTING, engine.castSkill(owner, card, "special").result());
        assertEquals(30, engine.castTotalTicks(owner));
        assertEquals(1, world.consumed.size());
        assertTrue(world.bolts.isEmpty());
        advance(29);
        assertTrue(world.bolts.isEmpty());
        advance(1);
        assertEquals(1, world.bolts.size());
        engine.tick();
        assertEquals(1, world.bolts.size());
        assertEquals("", engine.castingSlot(owner));
    }
    @Test void onCastRecoveryWaitsUntilCompletion() {
        engine.castSkill(owner, card, "heavy");
        assertTrue(world.heals.isEmpty());
        advance(10);
        assertEquals(List.of(owner + ":25.0"), world.heals);
    }
    @Test void secondSlotCannotOverlapOrSpendResourcesDuringCasting() {
        engine.castSkill(owner, card, "special");
        assertEquals(CastResult.ALREADY_CASTING, engine.castSkill(owner, card, "heavy").result());
        assertEquals(1, world.consumed.size());
    }
    @Test void cancellationHasNoEffectAndDoesNotRefundOrClearCooldown() {
        engine.castSkill(owner, card, "heavy");
        assertTrue(engine.cancelCast(owner));
        assertFalse(engine.cancelCast(owner));
        advance(20);
        assertTrue(world.heals.isEmpty());
        assertEquals(CastResult.ON_COOLDOWN, engine.castSkill(owner, card, "heavy").result());
        assertEquals(1, world.consumed.size());
    }
    @Test void resetCancelsCastingAndUnspentBoost() {
        kill();
        engine.castSkill(owner, card, "heavy");
        engine.clearShield(owner);
        advance(100);
        assertTrue(world.heals.isEmpty());
        assertEquals(0, engine.nextCastReductionTicks(owner, card, "special"));
    }
    @Test void killShortensNextCastRatherThanCooldown() {
        kill();
        assertEquals(20, engine.nextCastReductionTicks(owner, card, "special"));
        var result = engine.castSkill(owner, card, "special");
        assertEquals(10, result.remainingTicks());
        assertEquals(100, engine.remainingCooldownTicks(owner, card, "special", BuildMods.neutral()));
        assertEquals(0, engine.nextCastReductionTicks(owner, card, "special"));
        advance(10);
        assertEquals(1, world.bolts.size());
    }
    @Test void killsDoNotStackMultipleCharges() {
        kill(); kill(); kill();
        assertEquals(10, engine.castSkill(owner, card, "special").remainingTicks());
        advance(100);
        assertEquals(30, engine.castSkill(owner, card, "special").remainingTicks());
    }
    @Test void expiryDoesNotDependOnCleanup() {
        kill();
        world.advance(200);
        assertEquals(30, engine.castSkill(owner, card, "special").remainingTicks());
    }
    @Test void deniedCostPreservesBoostAndDoesNotLeaveTask() {
        kill();
        world.consumeResult = false;
        assertEquals(CastResult.NO_RESOURCE, engine.castSkill(owner, card, "special").result());
        assertEquals(0, engine.pendingTaskCount());
        assertEquals(20, engine.nextCastReductionTicks(owner, card, "special"));
        world.consumeResult = true;
        assertEquals(10, engine.castSkill(owner, card, "special").remainingTicks());
    }
    @Test void lateKillCannotAccelerateAnAlreadyStartedCast() {
        engine.castSkill(owner, card, "special");
        advance(5); kill();
        assertEquals(25, engine.castRemainingTicks(owner));
        advance(95);
        assertEquals(10, engine.castSkill(owner, card, "special").remainingTicks());
    }
    @Test void boostAndCastingArePerOwner() {
        kill();
        assertEquals(30, engine.castSkill(other, card, "special").remainingTicks());
        assertEquals(10, engine.castSkill(owner, card, "special").remainingTicks());
    }
    @Test void boostDoesNotAffectHeavySlot() {
        kill();
        assertEquals(10, engine.castSkill(owner, card, "heavy").remainingTicks());
        assertEquals(20, engine.nextCastReductionTicks(owner, card, "special"));
    }
    @Test void fullQueueRejectsWithoutCostOrBoostConsumption() {
        for (int i=0; i<1024; i++) engine.schedule(1000, () -> {});
        kill();
        assertEquals(CastResult.LIMIT_REACHED, engine.castSkill(owner, card, "special").result());
        assertTrue(world.consumed.isEmpty());
        assertEquals(20, engine.nextCastReductionTicks(owner, card, "special"));
    }
    @Test void completedCastsUseSharedTickTaskBudget() {
        // Other due work may delay release, but cannot release twice or bypass the task budget.
        for (int i=0; i<128; i++) engine.schedule(30, () -> {});
        engine.castSkill(owner, card, "special");
        advance(30);
        assertEquals(0, world.bolts.size());
        advance(1);
        assertEquals(1, world.bolts.size());
    }
    @Test void bossPseudoCastDoesNotDoubleItsTelegraph() {
        var boss = new WeaponCard("boss:test", "boss", "boss", "boss", 1, List.of(), List.of(),
                Map.of("cast", new SkillDef("projectile_single", Map.of("cast_time", 2), List.of())), "");
        assertEquals(CastResult.OK, engine.castSkill(owner, boss, "cast").result());
        assertEquals(1, world.bolts.size());
    }
    @Test void nativeInstantSkillDoesNotConsumeBoost() {
        kill();
        var skills = new java.util.HashMap<>(card.skills());
        skills.put("special", new SkillDef("self_buff", Map.of(), List.of()));
        var instant = new WeaponCard(card.id(), card.name(), card.family(), card.rarity(), card.itemLevel(),
                card.tagsExtra(), card.roleHint(), skills, "");
        assertEquals(CastResult.OK, engine.castSkill(owner, instant, "special").result());
        assertEquals(20, engine.nextCastReductionTicks(owner, card, "special"));
    }
}
