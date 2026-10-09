package com.thuvstu.hayatemod.core.engine;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.thuvstu.hayatemod.core.TestContent;
import com.thuvstu.hayatemod.core.content.ContentPack;
import com.thuvstu.hayatemod.core.content.model.Models.*;

class SkillSnapshotTest {
    final UUID owner = new UUID(0,1), target = new UUID(0,2);
    private ActionDef heal(double amount) { return new ActionDef("heal_self", 0, 1, "", 0, List.of(), 0, amount, "", "", "", ""); }
    private EffectDef hit(ActionDef action) { return new EffectDef("on_hit", List.of(), List.of(action), List.of(), 3, "", 0); }
    private WeaponCard card(List<EffectDef> effects, Map<String,Object> mods) {
        return new WeaponCard("test:forged", "snapshot", "staff", "unique", 1, List.of(), List.of(),
                Map.of("special", new SkillDef("projectile_single", mods, effects)), "");
    }
    private EffectEngineTest.FakeWorld world() {
        var world = new EffectEngineTest.FakeWorld();
        world.place(owner, new Vec3(0,0,0), new Vec3(1,0,0));
        world.place(target, new Vec3(2,0,0), new Vec3(-1,0,0));
        return world;
    }
    @Test void acceptedCastAndProjectileKeepEffectsWithoutContentDictionaryEntry() {
        var world = world();
        var engine = new EffectEngine(ContentPack.load(TestContent.dir()).set(), world);
        List<EffectDef> effects = new ArrayList<>(List.of(hit(heal(7))));
        Map<String,Object> mods = new HashMap<>(Map.of("cast_time", .5));
        engine.castSkill(owner, card(effects, mods), "special");
        effects.clear(); effects.add(hit(heal(999))); mods.put("cast_time", 30);
        world.advance(10); engine.tick();
        assertEquals(1, world.bolts.size());
        engine.onProjectileHit(world.bolts.getFirst().id(), target);
        assertEquals(List.of(owner + ":7.0"), world.heals);
        assertEquals(1, world.damages.size());
    }
    @Test void instantProjectilesAlsoKeepTheirDefinitionAfterLaunch() {
        var world = world();
        var engine = new EffectEngine(ContentPack.load(TestContent.dir()).set(), world);
        List<EffectDef> effects = new ArrayList<>(List.of(hit(heal(7))));
        engine.castSkill(owner, card(effects, Map.of()), "special");
        effects.clear();
        engine.onProjectileHit(world.bolts.getFirst().id(), target);
        assertEquals(List.of(owner + ":7.0"), world.heals);
    }
    @Test void splitProjectileRetainsSameSnapshotAndHistory() {
        var world = world();
        var engine = new EffectEngine(ContentPack.load(TestContent.dir()).set(), world);
        var split = new ActionDef("spawn_projectiles", 1, .5, "look", 4, List.of(), 0, 0, "", "", "", "");
        var guarded = new EffectDef("on_hit", List.of(), List.of(split), List.of("spawn_projectiles"), 3, "", 0);
        engine.castSkill(owner, card(List.of(guarded, hit(heal(7))), Map.of()), "special");
        engine.onProjectileHit(world.bolts.getFirst().id(), target);
        assertEquals(2, world.bolts.size());
        engine.onProjectileHit(world.bolts.getLast().id(), target);
        assertEquals(2, world.bolts.size());
        assertEquals(2, world.heals.size());
        assertNotNull(world.bolts.getLast().ctx().snapshot());
        assertTrue(world.bolts.getLast().ctx().preventRecursive().contains("spawn_projectiles"));
    }
    @Test void snapshotCollectionsAreImmutable() {
        var copy = SkillSnapshot.copy(card(List.of(hit(heal(7))), Map.of("cast_time", 1)));
        assertThrows(UnsupportedOperationException.class, () -> copy.skills().clear());
        assertThrows(UnsupportedOperationException.class, () -> copy.skills().get("special").effects().clear());
        assertThrows(UnsupportedOperationException.class, () -> copy.skills().get("special").mods().clear());
    }
}
