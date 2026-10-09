package com.thuvstu.hayatemod.core.engine;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.thuvstu.hayatemod.core.TestContent;
import com.thuvstu.hayatemod.core.content.ContentPack;
import com.thuvstu.hayatemod.core.content.model.Models.*;
import com.thuvstu.hayatemod.core.build.WeaponSkillMerger;

class CausalDamageTest {
    static final com.thuvstu.hayatemod.core.content.ContentSet CONTENT = ContentPack.load(TestContent.dir()).set();
    final UUID owner = new UUID(0,1), target = new UUID(0,2);
    ActionDef action(String type) { return new ActionDef(type, 1, 1, "look", 3, List.of(), 0, 1, "", "", "", ""); }
    EffectDef effect(String trigger, String action) { return new EffectDef(trigger, List.of(), List.of(action(action)), List.of(), 3, "", 0); }
    WeaponCard card(EffectDef... effects) { return new WeaponCard("test:causal", "Test", "sword", "rare", 1, List.of(), List.of(), Map.of("primary",new SkillDef("melee_thrust", Map.of(), List.of(effects))), ""); }
    CastContext context(WeaponCard card) { return new CastContext(card.id()+":primary", 0, Set.of(), owner, 1, BuildMods.neutral(), SkillSnapshot.copy(card)); }
    class ReentrantWorld extends EffectEngineTest.FakeWorld {
        EffectEngine engine;
        WeaponCard defender;
        boolean kills;
        final List<CastContext> causes = new ArrayList<>();
        ReentrantWorld() {
            place(owner,new Vec3(0,0,0),new Vec3(1,0,0));
            place(target,new Vec3(1,0,0),new Vec3(-1,0,0));
            arcTargets=List.of(target);
            engine = new EffectEngine(CONTENT,this);
        }
        @Override public void dealDamage(UUID attacker, UUID victim, double amount, DamageKind kind, UUID direct, CastContext cause) {
            causes.add(cause);
            if (kills) engine.onKill(cause.snapshot(), attacker, victim, cause.mods(), cause);
            else if (defender != null) engine.onDamaged(defender, victim, attacker, BuildMods.neutral(), cause);
        }
        @Override public void strikeLightning(UUID attacker, UUID victim, double amount, CastContext cause) {
            dealDamage(attacker,victim,amount,DamageKind.MELEE,null,cause);
        }
        @Override public void explode(UUID attacker, Vec3 pos, double amount, CastContext cause) {
            dealDamage(attacker,target,amount,DamageKind.MELEE,null,cause);
        }
        void hit(WeaponCard card) { engine.meleeStrike(owner,card,card.skills().get("primary"),context(card)); }
    }
    @Test void synchronousRetaliationCannotResetDepthOrExceedAbsoluteCeiling() {
        var world = new ReentrantWorld();
        world.defender=card(effect("on_damaged","strike"));
        world.hit(card());
        assertEquals(List.of(0,1,2,3),world.causes.stream().map(CastContext::chainDepth).toList());
        assertEquals(Set.of("strike"),world.causes.getLast().preventRecursive());
        assertEquals(target,world.causes.getLast().owner());
    }
    @Test void killProcsInheritDepthAndTerminate() {
        var world = new ReentrantWorld(); world.kills=true;
        world.hit(card(effect("on_kill","strike")));
        assertEquals(List.of(0,1,2,3),world.causes.stream().map(CastContext::chainDepth).toList());
    }
    @Test void preventRecursiveSurvivesTheDamageBoundary() {
        var world = new ReentrantWorld();
        var guarded = new EffectDef("on_damaged",List.of(),List.of(action("strike")),List.of("strike"),3,"",0);
        world.defender=card(guarded);
        world.hit(card());
        assertEquals(List.of(0,1),world.causes.stream().map(CastContext::chainDepth).toList());
    }
    @Test void lightningAndExplosionCarryTheirOwnCausalEdges() {
        for (String type : List.of("lightning","explosion","strike")) {
            var world = new ReentrantWorld();
            world.hit(card(effect("on_hit",type)));
            assertEquals(2,world.causes.size());
            assertEquals(1,world.causes.getLast().chainDepth());
            assertEquals(Set.of(type),world.causes.getLast().preventRecursive());
        }
    }
    @Test void projectileKillUsesAcceptedSnapshotEvenWhenItIsNotInTheContentPack() {
        var world = new ReentrantWorld(); world.kills=true;
        var base=card(effect("on_kill","heal_self"));
        var card=new WeaponCard(base.id(),base.name(),base.family(),base.rarity(),1,List.of(),List.of(),
                Map.of("special",new SkillDef("projectile_single",Map.of(),base.skills().get("primary").effects())),"");
        world.engine.castSkill(owner,card,"special");
        world.engine.onProjectileHit(world.bolts.getFirst().id(),target);
        assertEquals(1,world.heals.size());
        assertEquals(card.id(),world.causes.getFirst().snapshot().id());
    }
    @Test void killIgnoresNewHeldWeaponWhenAcceptedSnapshotIsAvailable() {
        var world=new ReentrantWorld();
        var accepted=card(effect("on_kill","heal_self"));
        world.engine.onKill(card(),owner,target,BuildMods.neutral(),context(accepted));
        assertEquals(1,world.heals.size());
    }

    @Test void passiveRunesAreAttachedOnceRatherThanOncePerSlot() {
        var original=card();
        var three = new WeaponCard(original.id(),original.name(),original.family(),original.rarity(),1,List.of(),List.of(),
                Map.of("primary",original.skills().get("primary"),"special",original.skills().get("primary"),"heavy",original.skills().get("primary")),"");
        var merged=WeaponSkillMerger.withPassiveRunes(three,List.of(effect("on_damaged","heal_self")));
        var world=new ReentrantWorld(); world.defender=merged;
        world.hit(card());
        assertEquals(1,world.heals.size());
        assertTrue(three.skills().get("primary").effects().isEmpty());
    }
    @Test void deathTriggerRetainsCauseButUsesDefendersCardAndOwner() {
        var world=new ReentrantWorld();
        var traces=new ArrayList<EffectEngine.EffectTrace>(); world.engine.setEffectTraceListener(traces::add);
        var defender=card(effect("on_death","heal_self"));
        world.engine.onDeath(defender,target,owner,BuildMods.neutral(),context(card()).causedBy("lightning"));
        assertEquals(target,traces.getFirst().owner());
        assertEquals(1,traces.getFirst().depth());
        assertEquals(Set.of("lightning"),traces.getFirst().history());
    }
    @Test void seededPriorityAndSynchronousReentryStressIsBoundedAndRepeatable() {
        for (int seed=0; seed<64; seed++) {
            List<Integer> expected=null;
            for (int repeat=0; repeat<2; repeat++) {
                var random=new Random(seed);
                var effects=new ArrayList<EffectDef>();
                String[] types={"strike","lightning","explosion","heal_self"};
                for (int i=0; i<16; i++) {
                    var e=effect(seed%2==0 ? "on_kill" : "on_damaged",types[random.nextInt(types.length)]);
                    effects.add(new EffectDef(e.trigger(),e.conditions(),e.actions(),List.of(),3,"",0,"e"+i,"weapon:test",random.nextInt(21)-10));
                }
                Collections.shuffle(effects,new Random(repeat));
                var card=card(effects.toArray(EffectDef[]::new));
                var world=new ReentrantWorld(); world.kills=seed%2==0; world.defender=card;
                world.hit(card);
                assertTrue(world.causes.stream().allMatch(c -> c.chainDepth()<=3));
                assertTrue(world.engine.executedActionsThisTick()<=512);
                assertTrue(world.engine.evaluatedEffectsThisTick()<=256);
                var actual=List.of(world.causes.size(),world.heals.size(),world.engine.executedActionsThisTick(),world.engine.evaluatedEffectsThisTick());
                if (expected==null) expected=actual; else assertEquals(expected,actual,"seed="+seed);
            }
        }
    }

    @Test void scopedBoundaryRestoresParentAfterNestedExceptionAndMatchesTarget() {
        var scope=new DamageScope(); var parent=context(card()); var child=parent.causedBy("strike");
        scope.run(target,parent,()->{
            assertSame(parent,scope.currentFor(target)); assertNull(scope.currentFor(owner));
            assertThrows(IllegalStateException.class,()->scope.run(owner,child,()->{
                assertSame(child,scope.currentFor(owner)); assertNull(scope.currentFor(target));
                throw new IllegalStateException();
            }));
            assertSame(parent,scope.currentFor(target));
            scope.run(target,null,()->{ assertTrue(scope.appliesTo(target)); assertNull(scope.current()); });
            assertSame(parent,scope.current());
        });
        assertNull(scope.current()); assertFalse(scope.appliesTo(target));
    }
    @Test void scopesDoNotLeakBetweenThreads() throws Exception {
        var scope=new DamageScope(); var future=new java.util.concurrent.atomic.AtomicReference<CastContext>();
        scope.run(target,context(card()),()->{
            Thread t=new Thread(()->future.set(scope.current())); t.start();
            try { t.join(); } catch (InterruptedException e) { throw new RuntimeException(e); }
        });
        assertNull(future.get());
    }
    @Test void deathDeduplicationUsesObjectLifetimeNotLogicalEquality() {
        var seen=new WeakIdentitySet<String>();
        var first=new String("same-uuid"); var respawn=new String("same-uuid");
        assertTrue(seen.add(first)); assertFalse(seen.add(first)); assertTrue(seen.add(respawn));
        seen.clear(); assertTrue(seen.add(first));
    }
}
