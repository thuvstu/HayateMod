package com.thuvstu.hayatemod.core.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.thuvstu.hayatemod.core.content.ContentSet;
import com.thuvstu.hayatemod.core.content.model.Models.ActionDef;
import com.thuvstu.hayatemod.core.content.model.Models.ConditionDef;
import com.thuvstu.hayatemod.core.content.model.Models.EffectDef;
import com.thuvstu.hayatemod.core.content.model.Models.SkillDef;
import com.thuvstu.hayatemod.core.content.model.Models.BuildMods;
import com.thuvstu.hayatemod.core.content.model.Models.WeaponCard;
import com.thuvstu.hayatemod.core.engine.WorldAdapter.DamageKind;

/**
 * Data-driven skill execution (§6.3, §6.6).
 *
 * <p>Delivery semantics (engine constants, content-ified later — see ADR-08):
 * fire-element projectile hits apply {@code ignite} for 100 ticks.
 * Placeholders: level/rank curves are 1.0, item curve is {@code 1 + 0.05 * itemLevel}.
 */
public final class EffectEngine {
    public static final long IGNITE_TICKS = 100L;

    private final ContentSet content;
    private final WorldAdapter world;
    private final StatusStore statuses = new StatusStore();
    private final Map<UUID, CastContext> liveProjectiles = new HashMap<>();
    private final Map<String, Long> lastCastTick = new HashMap<>();
    private final List<ScheduledTask> scheduled = new ArrayList<>();
    private final Map<String, Double> vars = new HashMap<>();
    private final Map<String, Formula.Node> formulaCache = new HashMap<>();
    private int eventDepth;
    private java.util.function.Consumer<String> eventLog = s -> {
    };

    /** Game-side observability hook (tests and debug logs). No-op by default. */
    public void setEventListener(java.util.function.Consumer<String> listener) {
        eventLog = listener != null ? listener : s -> {
        };
    }

    public EffectEngine(ContentSet content, WorldAdapter world) {
        this.content = content;
        this.world = world;
    }

    /** Queues engine work after a delay (telegraph windups, delayed bursts). Returns a task id. */
    public UUID schedule(long delayTicks, Runnable task) {
        UUID id = UUID.randomUUID();
        scheduled.add(new ScheduledTask(id, world.gameTime() + delayTicks, task));
        return id;
    }

    /** Cancels a scheduled task. Returns true when it was still pending. */
    public boolean cancel(UUID taskId) {
        return scheduled.removeIf(t -> t.id().equals(taskId));
    }

    /** Advances scheduled work and status expiry. The game calls this every tick. */
    public void tick() {
        long now = world.gameTime();
        var due = new ArrayList<ScheduledTask>();
        var it = scheduled.iterator();
        while (it.hasNext()) {
            ScheduledTask t = it.next();
            if (t.dueTick() <= now) {
                due.add(t);
                it.remove();
            }
        }
        for (ScheduledTask t : due) {
            t.task().run();
        }
        statuses.cleanup(now);
    }

    private record ScheduledTask(UUID id, long dueTick, Runnable task) {
    }

    /**
     * Cross-event loop guard (strike retaliation ping-pong, cast cycles).
     * Chain-depth limits alone cannot bound fresh-context re-entry, so nested
     * event delivery is capped absolutely.
     */
    private int maxEventDepth() {
        int limit = content.vocabulary() != null ? content.vocabulary().maxChainDepth() : 3;
        return limit + 2;
    }

    private boolean enterEvent() {
        if (eventDepth >= maxEventDepth()) {
            return false;
        }
        eventDepth++;
        return true;
    }

    private void exitEvent() {
        eventDepth--;
    }

    private String varKey(UUID owner, String name) {
        return owner + ":" + name;
    }

    /**
     * Resolves an action amount: plain number, or the {@code formula}
     * evaluated with lastDamage/health/mana/stamina/var_* bindings.
     */
    double evalAmount(ActionDef a, UUID owner, UUID victim, double base) {
        String f = a.formula();
        if (f == null || f.isEmpty()) {
            return a.amount();
        }
        try {
            Formula.Node node = formulaCache.computeIfAbsent(f, key -> {
                try {
                    return Formula.parse(key);
                } catch (Formula.FormulaException e) {
                    return null;
                }
            });
            if (node == null) {
                return a.amount();
            }
            Map<String, Double> bindings = new HashMap<>();
            bindings.put("lastDamage", base);
            bindings.put("health", world.healthRatio(victim) * 100.0);
            bindings.put("mana", world.resourceLevel(owner, "mana"));
            bindings.put("stamina", world.resourceLevel(owner, "stamina"));
            String prefix = owner.toString() + ":";
            for (var entry : vars.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    bindings.put("var_" + entry.getKey().substring(prefix.length()),
                            entry.getValue());
                }
            }
            return node.eval(bindings);
        } catch (RuntimeException e) {
            return a.amount();
        }
    }

    public enum CastResult {
        OK,
        ON_COOLDOWN,
        UNSUPPORTED_CORE,
        NO_RESOURCE
    }

    public record CastOutcome(CastResult result, long remainingTicks) {
    }

    /** Cooldown ticks for a skill slot (shared by casting and HUD display). */
    public long cooldownTicks(WeaponCard card, String slot, BuildMods mods) {
        SkillDef skill = card.skills().get(slot);
        if (skill == null) {
            return 0;
        }
        return (long) (modDouble(skill, "cooldown", 3.0) * 20.0 * mods.cooldownMult());
    }

    /** Base damage before action multipliers. */
    public double baseDamage(WeaponCard card, SkillDef skill) {
        double coreBase = content.tuning() == null ? 1.0
                : content.tuning().coreBase().getOrDefault(skill.core(), 1.0);
        double itemCurve = 1.0 + 0.05 * card.itemLevel();
        return coreBase * itemCurve;
    }

    private double scaledBase(WeaponCard card, SkillDef skill, UUID attacker, double mult, BuildMods mods,
            boolean melee) {
        double m = mult * mods.damageMult() * (melee ? mods.meleeMult() : 1.0);
        return baseDamage(card, skill) * m * world.powerMultiplier(attacker);
    }

    /** Casts a weapon's named skill slot (e.g. "special"). */
    public CastOutcome castSkill(UUID caster, WeaponCard card, String slot) {
        return castSkill(caster, card, slot, BuildMods.neutral());
    }

    public CastOutcome castSkill(UUID caster, WeaponCard card, String slot, BuildMods mods) {
        SkillDef skill = card.skills().get(slot);
        if (skill == null) {
            return new CastOutcome(CastResult.UNSUPPORTED_CORE, 0);
        }
        long now = world.gameTime();
        String key = caster + ":" + card.id() + ":" + slot;
        long cooldownTicks = cooldownTicks(card, slot, mods);
        Long last = lastCastTick.get(key);
        if (last != null && now - last < cooldownTicks) {
            return new CastOutcome(CastResult.ON_COOLDOWN, cooldownTicks - (now - last));
        }
        Object resource = skill.mods().get("resource");
        Object cost = skill.mods().get("resource_cost");
        if (resource instanceof String name && !name.isEmpty() && cost instanceof Number n) {
            if (!world.tryConsumeResource(caster, name, n.doubleValue())) {
                return new CastOutcome(CastResult.NO_RESOURCE, 0);
            }
        }
        CastContext ctx = new CastContext(card.id() + ":" + slot, 0, Set.of(), caster, 1.0, mods);
        switch (skill.core()) {
            case "projectile_single" -> {
                lastCastTick.put(key, now);
                Vec3 dir = world.facing(caster);
                UUID bolt = world.spawnBolt(caster, world.eyePos(caster), dir, 2.0, ctx);
                liveProjectiles.put(bolt, ctx);
                eventLog.accept("cast " + card.id() + ":" + slot);
                return new CastOutcome(CastResult.OK, 0);
            }
            case "melee_thrust" -> {
                lastCastTick.put(key, now);
                meleeStrike(caster, card, skill, ctx);
                eventLog.accept("cast " + card.id() + ":" + slot);
                return new CastOutcome(CastResult.OK, 0);
            }
            case "heavy_slam" -> {
                lastCastTick.put(key, now);
                slamStrike(caster, card, skill, ctx);
                eventLog.accept("cast " + card.id() + ":" + slot);
                return new CastOutcome(CastResult.OK, 0);
            }
            case "summon_minions" -> {
                Object enemy = skill.mods().get("enemy");
                if (!(enemy instanceof String enemyId) || enemyId.isEmpty()) {
                    return new CastOutcome(CastResult.UNSUPPORTED_CORE, 0);
                }
                lastCastTick.put(key, now);
                summonMinions(caster, enemyId, (int) modDouble(skill, "count", 1));
                eventLog.accept("summon " + enemyId);
                return new CastOutcome(CastResult.OK, 0);
            }
            case "dash" -> {
                lastCastTick.put(key, now);
                dashMove(caster, modDouble(skill, "distance", 6.0));
                eventLog.accept("dash");
                return new CastOutcome(CastResult.OK, 0);
            }
            default -> {
                return new CastOutcome(CastResult.UNSUPPORTED_CORE, 0);
            }
        }
    }

    /** Called by the adapter when a tracked projectile hits something. */
    public void onProjectileHit(UUID projectileId, UUID victim) {
        CastContext ctx = liveProjectiles.remove(projectileId);
        if (ctx == null) {
            return;
        }
        Resolved r = resolve(ctx.skillId());
        if (r == null) {
            return;
        }
        long now = world.gameTime();
        // §6.6: conditions are judged on the snapshot at event start, before delivery applies.
        Set<String> preStatuses = snapshotStatuses(victim, now);
        double base = scaledBase(r.card(), r.skill(), ctx.owner(), ctx.damageMult(), ctx.mods(), false);
        world.dealDamage(ctx.owner(), victim, base, DamageKind.PROJECTILE, projectileId);
        if (isFire(r.skill())) {
            statuses.add(victim, "ignite", now + IGNITE_TICKS);
        }
        applyTrigger(r.card(), r.skill(), ctx, ctx.owner(), victim, base, preStatuses, now, "on_hit");
    }

    /** Vanilla-attack replacement for weapon melee. */
    public void meleeStrike(UUID attacker, WeaponCard card, SkillDef skill, CastContext ctx) {
        meleeStrike(attacker, card, skill, ctx, BuildMods.neutral());
    }

    public void meleeStrike(UUID attacker, WeaponCard card, SkillDef skill, CastContext ctx,
            BuildMods mods) {
        double range = modDouble(skill, "range", 3.0) + 1.0;
        List<UUID> targets = world.targetsInArc(attacker, range, 0.5);
        double base = scaledBase(card, skill, attacker, 1.0, mods, true);
        long now = world.gameTime();
        for (UUID target : targets) {
            Set<String> preStatuses = snapshotStatuses(target, now);
            world.dealDamage(attacker, target, base, DamageKind.MELEE, null);
            applyTrigger(card, skill, ctx, attacker, target, base, preStatuses, now, "on_hit");
        }
    }

    /** Point-blank AoE around the caster (heavy_slam delivery). */
    public void slamStrike(UUID attacker, WeaponCard card, SkillDef skill, CastContext ctx) {
        slamStrike(attacker, card, skill, ctx, BuildMods.neutral());
    }

    public void slamStrike(UUID attacker, WeaponCard card, SkillDef skill, CastContext ctx,
            BuildMods mods) {
        double radius = modDouble(skill, "radius", 6.0);
        List<UUID> targets = world.targetsInArc(attacker, radius, -1.0);
        double base = scaledBase(card, skill, attacker, 1.0, mods, true);
        long now = world.gameTime();
        world.burstParticles(attacker, world.pos(attacker));
        for (UUID target : targets) {
            Set<String> preStatuses = snapshotStatuses(target, now);
            world.dealDamage(attacker, target, base, DamageKind.MELEE, null);
            applyTrigger(card, skill, ctx, attacker, target, base, preStatuses, now, "on_hit");
        }
    }

    /** Kill attribution: fires the killer weapon's on_kill effects once each. */
    public void onKill(WeaponCard card, UUID attacker, UUID victim) {
        onKill(card, attacker, victim, BuildMods.neutral());
    }

    public void onKill(WeaponCard card, UUID attacker, UUID victim, BuildMods mods) {
        long now = world.gameTime();
        Set<String> preStatuses = snapshotStatuses(victim, now);
        boolean fired = false;
        for (SkillDef skill : card.skills().values()) {
            boolean hasKill = skill.effects().stream().anyMatch(e -> e.trigger().equals("on_kill"));
            if (!hasKill) {
                continue;
            }
            double base = scaledBase(card, skill, attacker, 1.0, mods, false);
            CastContext ctx = new CastContext(card.id() + ":kill", 0, Set.of(), attacker, 1.0, mods);
            applyTrigger(card, skill, ctx, attacker, victim, base, preStatuses, now, "on_kill");
            fired = true;
        }
        if (fired) {
            eventLog.accept("kill " + card.id());
        }
    }

    /** Defender-side trigger: fires the victim weapon's on_damaged effects once each. */
    public void onDamaged(WeaponCard card, UUID victim, UUID attacker) {
        if (!hasTrigger(card, "on_damaged")) {
            return;
        }        long now = world.gameTime();
        Set<String> preStatuses = snapshotStatuses(victim, now);
        boolean fired = false;
        for (SkillDef skill : card.skills().values()) {
            boolean has = skill.effects().stream().anyMatch(e -> e.trigger().equals("on_damaged"));
            if (!has) {
                continue;
            }
            double base = scaledBase(card, skill, victim, 1.0, BuildMods.neutral(), false);
            CastContext ctx = new CastContext(card.id() + ":hurt", 0, Set.of(), victim, 1.0,
                    BuildMods.neutral());
            applyTrigger(card, skill, ctx, attacker, victim, base, preStatuses, now, "on_damaged");
            fired = true;
        }
        if (fired) {
            eventLog.accept("damaged " + card.id());
        }
    }

    /** Join buff: fires the joining weapon's on_join effects once each. */
    public void onJoin(WeaponCard card, UUID player, BuildMods mods) {
        if (!hasTrigger(card, "on_join")) {
            return;
        }
        long now = world.gameTime();
        boolean fired = false;
        for (SkillDef skill : card.skills().values()) {
            boolean has = skill.effects().stream().anyMatch(e -> e.trigger().equals("on_join"));
            if (!has) {
                continue;
            }
            double base = scaledBase(card, skill, player, 1.0, mods, false);
            CastContext ctx = new CastContext(card.id() + ":join", 0, Set.of(), player, 1.0, mods);
            applyTrigger(card, skill, ctx, player, player, base, Set.of(), now, "on_join");
            fired = true;
        }
        if (fired) {
            eventLog.accept("join " + card.id());
        }
    }

    public static boolean hasTrigger(WeaponCard card, String trigger) {
        for (SkillDef skill : card.skills().values()) {
            for (EffectDef e : skill.effects()) {
                if (e.trigger().equals(trigger)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Block-break proc: fires the miner weapon's on_break effects once each. */
    public void onBreak(WeaponCard card, UUID miner, BuildMods mods) {
        if (!hasTrigger(card, "on_break")) {
            return;
        }
        long now = world.gameTime();
        boolean fired = false;
        for (SkillDef skill : card.skills().values()) {
            boolean has = skill.effects().stream().anyMatch(e -> e.trigger().equals("on_break"));
            if (!has) {
                continue;
            }
            double base = scaledBase(card, skill, miner, 1.0, mods, false);
            CastContext ctx = new CastContext(card.id() + ":break", 0, Set.of(), miner, 1.0, mods);
            applyTrigger(card, skill, ctx, miner, miner, base, Set.of(), now, "on_break");
            fired = true;
        }
        if (fired) {
            eventLog.accept("break " + card.id());
        }
    }

    /** Deathburst: fires the victim weapon's on_death effects once each. */
    public void onDeath(WeaponCard card, UUID victim, UUID killer, BuildMods mods) {
        if (!hasTrigger(card, "on_death")) {
            return;
        }
        long now = world.gameTime();
        boolean fired = false;
        for (SkillDef skill : card.skills().values()) {
            boolean has = skill.effects().stream().anyMatch(e -> e.trigger().equals("on_death"));
            if (!has) {
                continue;
            }
            double base = scaledBase(card, skill, victim, 1.0, mods, false);
            CastContext ctx = new CastContext(card.id() + ":death", 0, Set.of(), victim, 1.0, mods);
            applyTrigger(card, skill, ctx, killer, victim, base, Set.of(), now, "on_death");
            fired = true;
        }
        if (fired) {
            eventLog.accept("death " + card.id());
        }
    }

    /**
     * Aura tick: fires on_timer effects whose skill interval elapsed. The game
     * calls this per player (see McAdapter); interval comes from skill mods.
     */
    public void onTimer(WeaponCard card, UUID owner, BuildMods mods) {
        if (!hasTrigger(card, "on_timer")) {
            return;
        }
        long now = world.gameTime();
        boolean fired = false;
        for (var entry : card.skills().entrySet()) {
            SkillDef skill = entry.getValue();
            boolean has = skill.effects().stream().anyMatch(e -> e.trigger().equals("on_timer"));
            if (!has) {
                continue;
            }
            double interval = modDouble(skill, "interval", 5.0);
            String key = owner + ":" + card.id() + ":timer:" + entry.getKey();
            Long last = lastCastTick.get(key);
            if (last != null && now - last < (long) (interval * 20.0)) {
                continue;
            }
            lastCastTick.put(key, now);
            double base = scaledBase(card, skill, owner, 1.0, mods, false);
            double radius = modDouble(skill, "radius", 4.0);
            CastContext ctx = new CastContext(card.id() + ":timer", 0, Set.of(), owner, 1.0, mods);
            for (UUID target : world.targetsInArc(owner, radius, -1.0)) {
                Set<String> pre = snapshotStatuses(target, now);
                applyTrigger(card, skill, ctx, owner, target, base, pre, now, "on_timer");
            }
            // Self-scoped effects (heal_self) run even with no one in range.
            applyTrigger(card, skill, ctx, owner, owner, base, Set.of(), now, "on_timer");
            fired = true;
        }
        if (fired) {
            eventLog.accept("timer " + card.id());
        }
    }

    private void summonMinions(UUID caster, String enemyId, int count) {
        Vec3 center = world.pos(caster);
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0 * i) / Math.max(1, count);
            Vec3 spot = new Vec3(center.x() + Math.cos(angle) * 2.5, center.y(), center.z() + Math.sin(angle) * 2.5);
            world.spawnMinion(caster, enemyId, spot);
        }
    }

    // ---- internals ----

    private void applyTrigger(WeaponCard card, SkillDef skill, CastContext ctx, UUID attacker,
            UUID victim, double base, Set<String> preStatuses, long now, String trigger) {
        int limit = content.vocabulary() != null ? content.vocabulary().maxChainDepth() : 3;
        if (ctx.chainDepth() > limit + ctx.mods().chainBonus()) {
            return;
        }
        if (!enterEvent()) {
            return;
        }
        try {
            for (EffectDef e : skill.effects()) {
                if (!e.trigger().equals(trigger)) {
                    continue;
                }
                if (e.scope().equals("area")) {
                    double radius = e.radius() > 0 ? e.radius() : 6.0;
                    for (UUID target : world.targetsInArc(ctx.owner(), radius, -1.0)) {
                        Set<String> pre = snapshotStatuses(target, now);
                        if (!conditionsMet(e, ctx.owner(), target, pre)) {
                            continue;
                        }
                        runActions(e, card, ctx, attacker, target, base, pre, now);
                    }
                    continue;
                }
                if (!conditionsMet(e, ctx.owner(), victim, preStatuses)) {
                    continue;
                }
                runActions(e, card, ctx, attacker, victim, base, preStatuses, now);
            }
        } finally {
            exitEvent();
        }
    }

    /** Runs one effect's actions; delay defers the remaining tail. */
    private void runActions(EffectDef e, WeaponCard card, CastContext ctx, UUID attacker,
            UUID victim, double base, Set<String> preStatuses, long now) {
        List<ActionDef> acts = e.actions();
        for (int i = 0; i < acts.size(); i++) {
            ActionDef a = acts.get(i);
            if (a.type().equals("delay")) {
                List<ActionDef> tail = List.copyOf(acts.subList(i + 1, acts.size()));
                if (tail.stream().anyMatch(t -> t.type().equals("delay"))) {
                    eventLog.accept("delay skips nested delay");
                    return;
                }
                long ticks = (long) evalAmount(a, ctx.owner(), victim, base);
                if (ticks <= 0) {
                    ticks = 20;
                }
                Set<String> pre = new HashSet<>(preStatuses);
                schedule(ticks, () -> runTail(tail, card, ctx, attacker, victim, base, pre, now));
                return;
            }
            if (a.type().equals("repeat")) {
                List<ActionDef> head = new ArrayList<>();
                for (int j = 0; j < i; j++) {
                    String t = acts.get(j).type();
                    if (!t.equals("delay") && !t.equals("repeat")) {
                        head.add(acts.get(j));
                    }
                }
                int times = Math.max(1, a.count());
                long interval = (long) evalAmount(a, ctx.owner(), victim, base);
                if (interval <= 0) {
                    interval = 10;
                }
                for (int k = 1; k < times; k++) {
                    schedule(interval * k,
                            () -> runTail(head, card, ctx, attacker, victim, base,
                                    new HashSet<>(preStatuses), now));
                }
                continue;
            }
            runAction(a, card, ctx, attacker, victim, base, now);
        }
    }

    private void runTail(List<ActionDef> tail, WeaponCard card, CastContext ctx, UUID attacker,
            UUID victim, double base, Set<String> pre, long now) {
        if (!enterEvent()) {
            return;
        }
        try {
            for (ActionDef a : tail) {
                runAction(a, card, ctx, attacker, victim, base, now);
            }
        } finally {
            exitEvent();
        }
    }

    private void runAction(ActionDef a, WeaponCard card, CastContext ctx, UUID attacker,
            UUID victim, double base, long now) {
        switch (a.type()) {
            case "spawn_projectiles" -> {
                if (ctx.preventRecursive().contains(a.type())) {
                    break;
                }
                fireSplit(ctx, attacker, victim, a, base, now);
            }
            case "dash" -> dashMove(ctx.owner(), a.distance() > 0 ? a.distance() : 6.0);
            case "heal_self" ->
                world.healEntity(ctx.owner(), evalAmount(a, ctx.owner(), victim, base));
            case "apply_status" -> {
                String status = a.status() != null ? a.status() : "";
                if (!status.isEmpty()) {
                    statuses.add(victim, status, now + IGNITE_TICKS);
                    eventLog.accept("status " + status);
                }
            }
            case "blink" -> {
                world.burstParticles(ctx.owner(), world.pos(ctx.owner()));
                world.moveEntity(ctx.owner(), world.pos(victim));
                world.burstParticles(ctx.owner(), world.pos(ctx.owner()));
                eventLog.accept("blink");
            }
            case "leap" -> {
                Vec3 dir = world.facing(ctx.owner());
                double d = a.distance() > 0 ? a.distance() : 6.0;
                world.launch(ctx.owner(),
                        new Vec3(dir.x() * d * 0.45, 0.85, dir.z() * d * 0.45));
                eventLog.accept("leap");
            }
            case "knockback" -> {
                double power = a.damageMult() > 0 ? a.damageMult() : 1.0;
                Vec3 away = world.directionTo(ctx.owner(), victim);
                if (away == null) {
                    away = new Vec3(0, 1, 0);
                }
                Vec3 flat = new Vec3(away.x(), 0, away.z()).normalize();
                world.launch(victim, new Vec3(flat.x() * power, 0.7 * power,
                        flat.z() * power));
                eventLog.accept("knockback");
            }
            case "strike" -> {
                double mult = a.damageMult() > 0 ? a.damageMult() : 1.0;
                world.dealDamage(ctx.owner(), victim, base * mult, DamageKind.MELEE, null);
                eventLog.accept("strike");
            }
            case "ignite" -> {
                int seconds = (int) evalAmount(a, ctx.owner(), victim, base);
                if (seconds <= 0) {
                    seconds = 5;
                }
                world.setFireTicks(victim, seconds * 20);
                eventLog.accept("ignite");
            }
            case "extinguish" -> {
                world.setFireTicks(victim, 0);
                eventLog.accept("extinguish");
            }
            case "potion" -> {
                int seconds = (int) evalAmount(a, ctx.owner(), victim, base);
                if (seconds <= 0) {
                    seconds = 10;
                }
                world.addEffect(victim, a.effect(), seconds, Math.max(0, (int) a.damageMult()));
                eventLog.accept("potion " + a.effect());
            }
            case "cleanse" -> {
                world.cleanseEffects(victim);
                eventLog.accept("cleanse");
            }
            case "summon" -> {
                if (a.ref() != null && !a.ref().isEmpty()) {
                    world.spawnMinion(ctx.owner(), a.ref(), world.pos(ctx.owner()));
                    eventLog.accept("summon " + a.ref());
                }
            }
            case "lightning" -> {
                double mult = a.damageMult() > 0 ? a.damageMult() : 1.0;
                world.strikeLightning(ctx.owner(), victim, base * mult);
                eventLog.accept("lightning");
            }
            case "explosion" -> {
                double power = a.damageMult() > 0 ? a.damageMult() : 2.0;
                world.explode(ctx.owner(), world.pos(victim), power);
                eventLog.accept("explosion");
            }
            case "particles" -> {
                world.playParticles(a.effect(), world.pos(victim));
                eventLog.accept("particles " + a.effect());
            }
            case "sound" -> {
                world.playSound(a.effect(), world.pos(victim));
                eventLog.accept("sound " + a.effect());
            }
            case "message" -> {
                if (a.ref() != null && !a.ref().isEmpty()) {
                    world.announce(ctx.owner(), a.ref());
                    eventLog.accept("message");
                }
            }
            case "feed" -> {
                world.feed(ctx.owner(), (int) evalAmount(a, ctx.owner(), victim, base),
                        (float) a.damageMult());
                eventLog.accept("feed");
            }
            case "xp" -> {
                world.grantXp(ctx.owner(), (int) evalAmount(a, ctx.owner(), victim, base));
                eventLog.accept("xp");
            }
            case "dropitem" -> {
                if (a.ref() != null && !a.ref().isEmpty()) {
                    world.dropItemAt(victim, a.ref(), Math.max(1, a.count()));
                    eventLog.accept("dropitem " + a.ref());
                }
            }
            case "giveitem" -> {
                if (a.ref() != null && !a.ref().isEmpty()) {
                    world.giveItem(ctx.owner(), a.ref(), Math.max(1, a.count()));
                    eventLog.accept("giveitem " + a.ref());
                }
            }
            case "setvar" -> {
                if (a.ref() != null && !a.ref().isEmpty()) {
                    vars.put(varKey(ctx.owner(), a.ref()),
                            evalAmount(a, ctx.owner(), victim, base));
                    eventLog.accept("setvar " + a.ref());
                }
            }
            case "addvar" -> {
                if (a.ref() != null && !a.ref().isEmpty()) {
                    vars.merge(varKey(ctx.owner(), a.ref()),
                            evalAmount(a, ctx.owner(), victim, base), Double::sum);
                    eventLog.accept("addvar " + a.ref());
                }
            }
            case "cast" -> {
                if (a.ref() != null && !a.ref().isEmpty()
                        && card.skills().containsKey(a.ref())
                        && !ctx.preventRecursive().contains("cast:" + a.ref())) {
                    Set<String> prevent = new HashSet<>(ctx.preventRecursive());
                    prevent.add("cast:" + a.ref());
                    CastContext child = new CastContext(ctx.skillId(), ctx.chainDepth() + 1,
                            prevent, ctx.owner(), ctx.damageMult(), ctx.mods());
                    castChild(card, a.ref(), child);
                }
            }
            case "missile" -> {
                if (ctx.preventRecursive().contains("missile")) {
                    break;
                }
                fireMissile(ctx, attacker, victim, a, base, now);
            }
            case "delay" -> {
                // Handled in runActions; a lone delay is a no-op.
            }
            case "repeat" -> {
                // Handled in runActions; a lone repeat is a no-op.
            }
            default -> {
            }
        }
    }

    private void castChild(WeaponCard card, String slot, CastContext child) {
        SkillDef skill = card.skills().get(slot);
        if (skill == null) {
            return;
        }
        eventLog.accept("cast " + card.id() + ":" + slot);
        switch (skill.core()) {
            case "melee_thrust" -> meleeStrike(child.owner(), card, skill, child);
            case "heavy_slam" -> slamStrike(child.owner(), card, skill, child);
            case "projectile_single" -> {
                Vec3 dir = world.facing(child.owner());
                UUID bolt = world.spawnBolt(child.owner(), world.eyePos(child.owner()), dir, 2.0,
                        child);
                liveProjectiles.put(bolt, child);
            }
            case "summon_minions" -> {
                Object enemy = skill.mods().get("enemy");
                if (enemy instanceof String enemyId && !enemyId.isEmpty()) {
                    summonMinions(child.owner(), enemyId, (int) modDouble(skill, "count", 1));
                }
            }
            case "dash" -> dashMove(child.owner(), modDouble(skill, "distance", 6.0));
            default -> {
            }
        }
    }

    private void dashMove(UUID caster, double distance) {        Vec3 origin = world.pos(caster);
        Vec3 dir = world.facing(caster);
        Vec3 dest = origin.add(new Vec3(dir.x(), 0, dir.z()).normalize().scale(distance));
        world.burstParticles(caster, origin);
        world.moveEntity(caster, new Vec3(dest.x(), origin.y(), dest.z()));
        world.burstParticles(caster, world.pos(caster));
        eventLog.accept("dash " + String.format("%.1f", distance));
    }

    private void fireSplit(CastContext ctx, UUID attacker, UUID victim, ActionDef a, double base,
            long now) {
        double radius = a.radius() > 0 ? a.radius() : 6.0;
        Vec3 origin = world.pos(victim);
        int spawned = 0;
        for (int i = 0; i < a.count(); i++) {
            Vec3 aim;
            if (a.target().equals("attacker") && attacker != null && !attacker.equals(victim)) {
                aim = world.directionTo(victim, attacker);
            } else if (a.target().equals("look")) {
                aim = world.facing(ctx.owner());
            } else {
                aim = world.aimAtNearest(ctx.owner(), origin, radius, victim);
            }
            if (aim == null) {
                break;
            }
            Set<String> prevent = new HashSet<>(ctx.preventRecursive());
            prevent.add(a.type());
            CastContext child = ctx.child(prevent, a.damageMult());
            UUID bolt = world.spawnBolt(ctx.owner(), origin, aim, 2.0, child);
            liveProjectiles.put(bolt, child);
            spawned++;
        }
        if (spawned > 0) {
            eventLog.accept("split x" + spawned);
        }
    }

    /**
     * Homing missile (mythic parity): locks one target at fire time, then the
     * game steers the bolt every tick. Uses the same hit pipeline as bolts.
     */
    private void fireMissile(CastContext ctx, UUID attacker, UUID victim, ActionDef a,
            double base, long now) {
        double radius = a.radius() > 0 ? a.radius() : 6.0;
        Vec3 origin = world.pos(victim);
        double speed = a.distance() > 0 ? a.distance() : 1.4;
        long lifetime = (long) evalAmount(a, ctx.owner(), victim, base);
        if (lifetime <= 0) {
            lifetime = 100;
        }
        int spawned = 0;
        for (int i = 0; i < a.count(); i++) {
            UUID lock;
            Vec3 aim;
            if (a.target().equals("attacker") && attacker != null && !attacker.equals(victim)) {
                lock = attacker;
                aim = world.directionTo(victim, attacker);
            } else {
                lock = world.nearestLiving(ctx.owner(), origin, radius, victim);
                if (lock != null) {
                    aim = world.directionTo(victim, lock);
                    if (aim == null) {
                        aim = world.facing(ctx.owner());
                    }
                } else {
                    aim = world.facing(ctx.owner());
                }
            }
            if (aim == null) {
                break;
            }
            Set<String> prevent = new HashSet<>(ctx.preventRecursive());
            prevent.add("missile");
            CastContext child = ctx.child(prevent, a.damageMult());
            UUID bolt = world.spawnMissile(ctx.owner(), origin, aim, speed, lifetime, lock,
                    child);
            liveProjectiles.put(bolt, child);
            spawned++;
        }
        if (spawned > 0) {
            eventLog.accept("missile x" + spawned);
        }
    }

    private boolean conditionsMet(EffectDef e, UUID owner, UUID victim, Set<String> preStatuses) {
        for (ConditionDef c : e.conditions()) {
            switch (c.type()) {
                case "target_has_status" -> {
                    if (c.status() == null || !preStatuses.contains(c.status())) {
                        return false;
                    }
                }
                case "chance" -> {
                    if (!world.rollChance(c.value())) {
                        return false;
                    }
                }
                case "sneaking" -> {
                    if (!world.isSneaking(owner)) {
                        return false;
                    }
                }
                case "health_below" -> {
                    if (!(world.healthRatio(victim) < c.value())) {
                        return false;
                    }
                }
                case "holding" -> {
                    if (c.name() == null || c.name().isEmpty()
                            || !world.heldWeaponId(owner).equals(c.name())) {
                        return false;
                    }
                }
                case "has_effect" -> {
                    if (c.name() == null || c.name().isEmpty()
                            || !world.hasStatusEffect(victim, c.name())) {
                        return false;
                    }
                }
                case "variable" -> {
                    double v = vars.getOrDefault(varKey(owner, c.name() != null ? c.name() : ""),
                            0.0);
                    if (!(v >= c.value() && (c.max() <= 0 || v <= c.max()))) {
                        return false;
                    }
                }
                case "altitude" -> {
                    if (!(world.pos(victim).y() >= c.value())) {
                        return false;
                    }
                }
                case "burning" -> {
                    if (!world.isBurning(victim)) {
                        return false;
                    }
                }
                case "day", "night", "raining" -> {
                    if (!world.worldFlag(c.type())) {
                        return false;
                    }
                }
                case "entity_type" -> {
                    if (c.name() == null || c.name().isEmpty()
                            || !world.entityTypeId(victim).equals(c.name())) {
                        return false;
                    }
                }
                default -> {
                    return false;
                }
            }
        }
        return true;
    }

    private Set<String> snapshotStatuses(UUID victim, long now) {
        Set<String> pre = new HashSet<>();
        if (statuses.has(victim, "ignite", now) || world.isBurning(victim)) {
            pre.add("ignite");
        }
        return pre;
    }

    public boolean isTracking(UUID projectileId) {
        return liveProjectiles.containsKey(projectileId);
    }

    /** Drops tracking for an expired/missed projectile (no hit attribution). */
    public void forgetProjectile(UUID projectileId) {
        liveProjectiles.remove(projectileId);
    }

    private boolean isFire(SkillDef skill) {
        return skill.mods().getOrDefault("element", "").equals("fire");
    }

    private double modDouble(SkillDef skill, String key, double def) {
        Object v = skill.mods().get(key);
        return v instanceof Number n ? n.doubleValue() : def;
    }

    private record Resolved(WeaponCard card, SkillDef skill) {
    }

    private Resolved resolve(String ctxSkillId) {
        // Split at the last colon: "<weaponId>:<slot>". Weapon ids contain colons too.
        int last = ctxSkillId.lastIndexOf(':');
        if (last <= 0) {
            return null;
        }
        String weaponId = ctxSkillId.substring(0, last);
        String slot = ctxSkillId.substring(last + 1);
        WeaponCard card = content.weapons().get(weaponId);
        if (card != null && card.skills().get(slot) != null) {
            return new Resolved(card, card.skills().get(slot));
        }
        return null;
    }

    public int liveProjectileCount() {
        return liveProjectiles.size();
    }

    public void cleanup(long now) {
        statuses.cleanup(now);
    }
}
