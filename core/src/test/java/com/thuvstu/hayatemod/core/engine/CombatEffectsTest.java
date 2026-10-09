package com.thuvstu.hayatemod.core.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.thuvstu.hayatemod.core.TestContent;
import com.thuvstu.hayatemod.core.content.ContentPack;
import com.thuvstu.hayatemod.core.content.ContentSet;
import com.thuvstu.hayatemod.core.content.model.Models.*;
import com.thuvstu.hayatemod.core.engine.EffectEngine.CastResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CombatEffectsTest {
    private final UUID owner = new UUID(0, 1);
    private final UUID target = new UUID(0, 2);
    private EffectEngineTest.FakeWorld world;
    private EffectEngine engine;
    private ContentSet content;

    @BeforeEach
    void setup() {
        content = ContentPack.load(TestContent.dir()).set();
        world = new EffectEngineTest.FakeWorld();
        world.place(owner, new Vec3(0, 0, 0), new Vec3(1, 0, 0));
        world.place(target, new Vec3(2, 0, 0), new Vec3(-1, 0, 0));
        engine = new EffectEngine(content, world);
    }

    private WeaponCard aegis() {
        return content.weapons().get("solommo:cinder_aegis");
    }

    private WeaponCard reaver() {
        return content.weapons().get("solommo:echo_reaver");
    }

    private void hit(WeaponCard card) {
        world.arcTargets = List.of(target);
        engine.meleeStrike(owner, card, card.skills().get("primary"),
                new CastContext(card.id() + ":primary", 0, Set.of(), owner, 1, BuildMods.neutral()));
    }

    private ActionDef action(String type, double amount, int ticks, double ratio, String ref) {
        return new ActionDef(type, 0, 1, "", 0, List.of(), 0, amount, "", "", ref, "", ticks, ratio);
    }

    private EffectDef effect(String trigger, ActionDef... actions) {
        return new EffectDef(trigger, List.of(), List.of(actions), List.of(), 3, "", 0);
    }

    private WeaponCard custom(SkillDef special) {
        return new WeaponCard("test:recovery", "Recovery", "sword", "unique", 1,
                List.of(), List.of(), Map.of("special", special), "");
    }

    @Test
    void paidCastGrantsShieldWithoutAnyTargetOrDamage() {
        assertEquals(CastResult.OK, engine.castSkill(owner, aegis(), "special").result());
        assertEquals(90, engine.shieldAmount(owner));
        assertEquals(100, engine.shieldRemainingTicks(owner));
        assertTrue(world.damages.isEmpty());
        assertEquals(List.of(owner + ":stamina:20.0"), world.consumed);
        assertEquals(200, engine.remainingCooldownTicks(owner, aegis(), "special", BuildMods.neutral()));
    }

    @Test
    void cooldownDenialDoesNotRefreshShieldOrConsumeResource() {
        engine.castSkill(owner, aegis(), "special");
        world.advance(40);
        assertEquals(CastResult.ON_COOLDOWN, engine.castSkill(owner, aegis(), "special").result());
        assertEquals(60, engine.shieldRemainingTicks(owner));
        assertEquals(1, world.consumed.size());
    }

    @Test
    void resourceDenialDoesNotGrantShieldOrStartCooldown() {
        world.consumeResult = false;
        assertEquals(CastResult.NO_RESOURCE, engine.castSkill(owner, aegis(), "special").result());
        assertEquals(0, engine.shieldAmount(owner));
        assertEquals(0, engine.remainingCooldownTicks(owner, aegis(), "special", BuildMods.neutral()));
        world.consumeResult = true;
        assertEquals(CastResult.OK, engine.castSkill(owner, aegis(), "special").result());
    }

    @Test
    void unsupportedDeliveryDoesNotConsumeResourcesOrRunOnCast() {
        var card = custom(new SkillDef("unsupported", Map.of("resource", "mana", "resource_cost", 20),
                List.of(effect("on_cast", action("grant_shield", 50, 100, 0, "")))));
        assertEquals(CastResult.UNSUPPORTED_CORE, engine.castSkill(owner, card, "special").result());
        assertTrue(world.consumed.isEmpty());
        assertEquals(0, engine.shieldAmount(owner));
    }

    @Test
    void healingIsSplitNotDuplicated() {
        engine.castSkill(owner, aegis(), "heavy");
        assertEquals(List.of(owner + ":40.0"), world.heals);
        assertEquals(60, engine.shieldAmount(owner));
        assertEquals(120, engine.shieldRemainingTicks(owner));
    }

    @Test
    void conversionDoesNotReplaceStrongerExistingShieldButStillHeals() {
        engine.castSkill(owner, aegis(), "special");
        world.advance(10);
        engine.castSkill(owner, aegis(), "heavy");
        assertEquals(90, engine.shieldAmount(owner));
        assertEquals(90, engine.shieldRemainingTicks(owner));
        assertEquals(List.of(owner + ":40.0"), world.heals);
    }

    @Test
    void shieldEnablesFollowupUntilBroken() {
        hit(aegis());
        assertEquals(1, world.damages.size());
        engine.castSkill(owner, aegis(), "special");
        hit(aegis());
        assertEquals(3, world.damages.size());
        assertEquals(10, engine.absorbShield(owner, 100));
        hit(aegis());
        assertEquals(4, world.damages.size());
    }

    @Test
    void expiredShieldCannotEnableFollowupEvenBeforeEngineTick() {
        engine.castSkill(owner, aegis(), "special");
        world.advance(100);
        hit(aegis());
        assertEquals(1, world.damages.size());
        assertEquals(20, engine.absorbShield(owner, 20));
    }

    @Test
    void onCastConditionsUseSnapshotBeforeEarlierShieldAction() {
        var shield = effect("on_cast", action("grant_shield", 50, 100, 0, ""));
        var heal = new EffectDef("on_cast", List.of(new ConditionDef("self_has_shield", "", 0, "", 0)),
                List.of(action("heal_self", 10, 0, 0, "")), List.of(), 0, "", 0);
        var card = custom(new SkillDef("self_buff", Map.of(), List.of(shield, heal)));
        engine.castSkill(owner, card, "special");
        assertEquals(50, engine.shieldAmount(owner));
        assertTrue(world.heals.isEmpty());
    }

    @Test
    void childCastsDoNotGenerateFreeOnCastRecovery() {
        var heavy = aegis().skills().get("heavy");
        var primary = new SkillDef("melee_thrust", Map.of(),
                List.of(effect("on_hit", action("cast", 0, 0, 0, "heavy"))));
        var card = new WeaponCard("test:child", "Child", "sword", "unique", 1, List.of(), List.of(),
                Map.of("primary", primary, "heavy", heavy), "");
        hit(card);
        assertEquals(0, engine.shieldAmount(owner));
        assertTrue(world.heals.isEmpty());
    }

    @Test
    void delayedGrantDurationStartsAtExecutionTime() {
        var card = custom(new SkillDef("self_buff", Map.of(), List.of(effect("on_cast",
                action("delay", 40, 0, 0, ""), action("grant_shield", 50, 100, 0, "")))));
        engine.castSkill(owner, card, "special");
        assertEquals(0, engine.shieldAmount(owner));
        world.advance(40);
        engine.tick();
        assertEquals(100, engine.shieldRemainingTicks(owner));
    }

    @Test
    void resetInvalidatesDelayedRecoveryWithoutAffectingAnotherOwner() {
        var card = custom(new SkillDef("self_buff", Map.of(), List.of(effect("on_cast",
                action("delay", 40, 0, 0, ""), action("heal_with_shield", 100, 100, 0.5, "")))));
        engine.castSkill(owner, card, "special");
        engine.castSkill(target, card, "special");
        engine.clearShield(owner);
        world.advance(40);
        engine.tick();
        assertEquals(0, engine.shieldAmount(owner));
        assertEquals(50, engine.shieldAmount(target));
        assertEquals(List.of(target + ":50.0"), world.heals);
    }

    @Test
    void resetInvalidatesRepeatedShieldGrant() {
        var repeat = new ActionDef("repeat", 2, 1, "", 0, List.of(), 0, 20, "", "", "", "");
        var card = custom(new SkillDef("self_buff", Map.of(), List.of(effect("on_cast",
                action("grant_shield", 50, 100, 0, ""), repeat))));
        engine.castSkill(owner, card, "special");
        engine.clearShield(owner);
        world.advance(20);
        engine.tick();
        assertEquals(0, engine.shieldAmount(owner));
    }

    @Test
    void killReducesActiveSpecialCooldownOnly() {
        engine.castSkill(owner, reaver(), "special");
        engine.castSkill(owner, reaver(), "heavy");
        world.advance(20);
        engine.onKill(reaver(), owner, target);
        assertEquals(100, engine.remainingCooldownTicks(owner, reaver(), "special", BuildMods.neutral()));
        assertEquals(220, engine.remainingCooldownTicks(owner, reaver(), "heavy", BuildMods.neutral()));
    }

    @Test
    void repeatedKillsCannotBankReductionForTheNextCast() {
        engine.castSkill(owner, reaver(), "special");
        for (int i = 0; i < 8; i++) {
            engine.onKill(reaver(), owner, new UUID(0, i + 10));
        }
        assertEquals(0, engine.remainingCooldownTicks(owner, reaver(), "special", BuildMods.neutral()));
        assertEquals(CastResult.OK, engine.castSkill(owner, reaver(), "special").result());
        assertEquals(160, engine.remainingCooldownTicks(owner, reaver(), "special", BuildMods.neutral()));
    }

    @Test
    void killsBeforeFirstCastDoNotChangeItsCooldown() {
        engine.onKill(reaver(), owner, target);
        engine.castSkill(owner, reaver(), "special");
        assertEquals(160, engine.remainingCooldownTicks(owner, reaver(), "special", BuildMods.neutral()));
    }

    @Test
    void cooldownReductionIsPerOwnerAndPerWeapon() {
        engine.castSkill(owner, reaver(), "special");
        engine.castSkill(target, reaver(), "special");
        engine.castSkill(owner, aegis(), "special");
        engine.onKill(reaver(), owner, target);
        assertEquals(120, engine.remainingCooldownTicks(owner, reaver(), "special", BuildMods.neutral()));
        assertEquals(160, engine.remainingCooldownTicks(target, reaver(), "special", BuildMods.neutral()));
        assertEquals(200, engine.remainingCooldownTicks(owner, aegis(), "special", BuildMods.neutral()));
    }

    @Test
    void newEffectsRemainInsideTheSharedActionBudget() {
        var healing = action("heal_with_shield", 10, 100, 0.5, "");
        var effect = new EffectDef("on_cast", List.of(), java.util.Collections.nCopies(1000, healing),
                List.of(), 0, "", 0);
        var card = custom(new SkillDef("self_buff", Map.of(), List.of(effect)));
        engine.castSkill(owner, card, "special");
        assertEquals(content.vocabulary().executionLimits().actionsPerTick(), world.heals.size());
        assertEquals(5, engine.shieldAmount(owner), "repeated grants never add");
    }

    @Test
    void clearShieldUsedForRestoreAndDisconnectRemovesBothAmountAndTimer() {
        engine.castSkill(owner, aegis(), "special");
        engine.clearShield(owner);
        assertEquals(0, engine.shieldAmount(owner));
        assertEquals(0, engine.shieldRemainingTicks(owner));
    }
}
