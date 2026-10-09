package com.thuvstu.hayatemod.core.sim;

import java.util.*;
import com.thuvstu.hayatemod.core.build.WeaponSkillMerger;
import com.thuvstu.hayatemod.core.content.ContentSet;
import com.thuvstu.hayatemod.core.content.model.Models.*;
import com.thuvstu.hayatemod.core.engine.*;

/** Seeded core-only stress harness, not a Minecraft physics/kill-attribution simulator. */
public final class EffectFuzzer {
    private EffectFuzzer() { }
    public record Distribution(int min, int p50, int p95, int max) {
        static Distribution of(List<Integer> values) {
            var sorted = values.stream().sorted().toList();
            return new Distribution(sorted.getFirst(), sorted.get((sorted.size()-1)/2),
                    sorted.get((int) Math.ceil(sorted.size() * .95)-1), sorted.getLast());
        }
    }
    public record Report(long seed, int samples, int ticksPerSample, Distribution effectsPerTick,
            Distribution actionsPerTick, Distribution tasksPerTick, Distribution chainDepth,
            int maximumPendingTasks, int unfinishedSamples) { }

    public static Report run(ContentSet content, long seed, int samples, int ticks) {
        if (samples < 1 || ticks < 1 || ticks > 2000 || (long) samples * ticks > 1000000 || content.weapons().isEmpty()) {
            throw new IllegalArgumentException("fuzz work must be 1..1000000 ticks, at most 2000 ticks/sample");
        }
        Random random = new Random(seed);
        var weapons = content.weapons().values().stream().sorted(Comparator.comparing(WeaponCard::id)).toList();
        var runePool = content.runes().values().stream().sorted(Comparator.comparing(RuneDef::id)).toList();
        var keyPool = content.keystones().values().stream().sorted(Comparator.comparing(KeystoneDef::id)).toList();
        List<Integer> effects = new ArrayList<>(), actions = new ArrayList<>(), tasks = new ArrayList<>(), depths = new ArrayList<>();
        int maximumPending = 0, unfinished = 0;
        var limits = content.vocabulary().executionLimits();
        for (int sample=0; sample<samples; sample++) {
            WeaponCard base = weapons.get(random.nextInt(weapons.size()));
            var runes = new ArrayList<>(runePool); Collections.shuffle(runes, random);
            var additions = runes.subList(0, random.nextInt(Math.min(2, runes.size()) + 1)).stream()
                    .flatMap(r -> r.effects().stream()).toList();
            Map<String, SkillDef> skills = new TreeMap<>();
            base.skills().forEach((slot, skill) -> skills.put(slot, WeaponSkillMerger.withRuneEffects(skill, additions)));
            var card = new WeaponCard(base.id(), base.name(), base.family(), base.rarity(), base.itemLevel(),
                    base.tagsExtra(), base.roleHint(), Map.copyOf(skills), base.designNote());
            var keys = new ArrayList<>(keyPool); Collections.shuffle(keys, random);
            var mods = BuildMods.combine(keys.subList(0, random.nextInt(Math.min(3, keys.size()) + 1)));
            var world = new FuzzWorld(random.nextLong(), card.id());
            var engine = new EffectEngine(content, world);
            for (int tick=0; tick<ticks; tick++) {
                world.now = tick;
                engine.tick();
                if (tick == 0) engine.onJoin(card, world.owner, mods);
                if (tick % 10 == 0 && skills.containsKey("primary")) {
                    engine.meleeStrike(world.owner, card, skills.get("primary"),
                            new CastContext(card.id()+":primary", 0, Set.of(), world.owner, 1, mods));
                }
                if (tick % 20 == 0) {
                    engine.castSkill(world.owner, card, "special", mods);
                    engine.castSkill(world.owner, card, "heavy", mods);
                    engine.onKill(card, world.owner, world.targets.getFirst(), mods);
                    engine.onDamaged(card, world.owner, world.targets.getFirst());
                    engine.onBreak(card, world.owner, mods);
                }
                engine.onTimer(card, world.owner, mods);
                int hits = Math.min(128, world.bolts.size());
                for (int i=0; i<hits; i++) engine.onProjectileHit(world.bolts.removeFirst(), world.targets.get(i % world.targets.size()));
                int e = engine.evaluatedEffectsThisTick(), a = engine.executedActionsThisTick(), t = engine.executedTasksThisTick();
                if (e > limits.effectsPerTick() || a > limits.actionsPerTick() || t > limits.tasksPerTick()
                        || engine.pendingTaskCount() > limits.pendingTasks() || engine.maximumObservedChainDepth() > 3) {
                    throw new IllegalStateException("budget violation: seed="+seed+" sample="+sample+" weapon="+card.id()+" tick="+tick);
                }
                effects.add(e); actions.add(a); tasks.add(t);
                maximumPending = Math.max(maximumPending, engine.pendingTaskCount());
            }
            depths.add(engine.maximumObservedChainDepth());
            if (engine.pendingTaskCount() > 0 || !world.bolts.isEmpty()) unfinished++;
        }
        return new Report(seed, samples, ticks, Distribution.of(effects), Distribution.of(actions), Distribution.of(tasks),
                Distribution.of(depths), maximumPending, unfinished);
    }

    /** Three stationary targets, inexhaustible resources, randomized initial conditions; cosmetic ports are inert. */
    private static final class FuzzWorld implements WorldAdapter {
        final UUID owner = new UUID(0,1);
        final List<UUID> targets = List.of(new UUID(0,2), new UUID(0,3), new UUID(0,4));
        final ArrayDeque<UUID> bolts = new ArrayDeque<>();
        final Random random;
        final String weapon;
        final boolean burning, sneaking;
        long now, serial = 10;
        FuzzWorld(long seed, String weapon) {
            random = new Random(seed); this.weapon = weapon;
            burning = random.nextBoolean(); sneaking = random.nextBoolean();
        }
        public long gameTime() { return now; }
        public Vec3 pos(UUID entity) { return new Vec3(entity.equals(owner) ? 0 : 2, 70, 0); }
        public Vec3 eyePos(UUID entity) { return pos(entity); }
        public Vec3 facing(UUID entity) { return new Vec3(1,0,0); }
        public UUID spawnBolt(UUID owner, Vec3 origin, Vec3 dir, double speed, CastContext context) {
            UUID id = new UUID(0, serial++); bolts.add(id); return id;
        }
        public UUID spawnMissile(UUID owner, Vec3 origin, Vec3 dir, double speed, long life, UUID target, CastContext context) {
            return spawnBolt(owner, origin, dir, speed, context);
        }
        public void dealDamage(UUID attacker, UUID target, double amount, DamageKind kind, UUID direct) { finite(amount); }
        public Vec3 aimAtNearest(UUID owner, Vec3 origin, double radius, UUID exclude) { return new Vec3(1,0,0); }
        public List<UUID> targetsInArc(UUID attacker, double range, double cosine) { return targets; }
        public UUID spawnMinion(UUID owner, String enemy, Vec3 pos) { return new UUID(0, serial++); }
        public void ringParticles(UUID anchor, Vec3 pos, double radius) { }
        public void burstParticles(UUID anchor, Vec3 pos) { }
        public boolean isBurning(UUID entity) { return burning; }
        public Vec3 directionTo(UUID from, UUID to) { return new Vec3(1,0,0); }
        public void healEntity(UUID entity, double amount) { finite(amount); }
        public void moveEntity(UUID entity, Vec3 dest) { finite(dest.x()); finite(dest.y()); finite(dest.z()); }
        public void launch(UUID entity, Vec3 velocity) { moveEntity(entity, velocity); }
        public boolean isSneaking(UUID entity) { return sneaking; }
        public double healthRatio(UUID entity) { return .25; }
        public boolean rollChance(double p) { return random.nextDouble() < p; }
        public void setFireTicks(UUID entity, int ticks) { }
        public void addEffect(UUID entity, String effect, int seconds, int amplifier) { }
        public void cleanseEffects(UUID entity) { }
        public boolean hasStatusEffect(UUID entity, String effect) { return burning; }
        public void playParticles(String particle, Vec3 pos) { }
        public void playSound(String sound, Vec3 pos) { }
        public void announce(UUID entity, String text) { }
        public void feed(UUID entity, int food, float saturation) { }
        public void grantXp(UUID entity, int points) { }
        public void giveItem(UUID entity, String item, int count) { }
        public void dropItemAt(UUID entity, String item, int count) { }
        public void strikeLightning(UUID owner, UUID target, double damage) { finite(damage); }
        public void explode(UUID owner, Vec3 pos, double power) { finite(power); }
        public String heldWeaponId(UUID entity) { return weapon; }
        public String entityTypeId(UUID entity) { return "entity.minecraft.zombie"; }
        public boolean worldFlag(String name) { return name.equals("day") || name.equals("raining"); }
        public boolean tryConsumeResource(UUID owner, String name, double cost) { finite(cost); return true; }
        public double resourceLevel(UUID owner, String name) { return 100; }
        public UUID nearestLiving(UUID owner, Vec3 origin, double radius, UUID exclude) { return targets.getFirst(); }
        private static void finite(double value) { if (!Double.isFinite(value)) throw new IllegalStateException("non-finite effect output"); }
    }
}
