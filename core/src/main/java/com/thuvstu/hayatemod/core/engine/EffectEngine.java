package com.thuvstu.hayatemod.core.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.thuvstu.hayatemod.core.content.ContentSet;
import com.thuvstu.hayatemod.core.content.model.Models.ExecutionLimits;
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
    private final ExecutionLimits executionLimits;
    private long budgetTick = Long.MIN_VALUE;
    private int actionsThisTick;
    private int effectsThisTick;
    private int tasksThisTick;

    private void refreshBudget() {
        long now = world.gameTime();
        if (budgetTick != now) {
            budgetTick = now;
            actionsThisTick = 0;
            effectsThisTick = 0;
            tasksThisTick = 0;
        }
    }

    private boolean takeAction() {
        refreshBudget();
        if (actionsThisTick >= executionLimits.actionsPerTick()) {
            return false;
        }
        actionsThisTick++;
        return true;
    }

    public int executedActionsThisTick() {
        refreshBudget();
        return actionsThisTick;
    }

    private int maximumObservedChainDepth;
    public int maximumObservedChainDepth() { return maximumObservedChainDepth; }
    public int evaluatedEffectsThisTick() { refreshBudget(); return effectsThisTick; }
    public int executedTasksThisTick() { refreshBudget(); return tasksThisTick; }

    public int pendingTaskCount() {
        return scheduled.size();
    }

    private int chainLimit() {
        return Math.max(0, Math.min(3, content.vocabulary().maxChainDepth()));
    }

    private int projectileLimit() {
        return Math.max(0, Math.min(256, content.vocabulary().maxProjectiles()));
    }

    private final ContentSet content;
    private final WorldAdapter world;
    private final StatusStore statuses = new StatusStore();
    private record CastBoostKey(UUID owner, String weapon, String slot) { }
    private record CastBoost(long reduction, long expires) { }
    private final Map<CastBoostKey, CastBoost> castBoosts = new HashMap<>();
    private final Map<UUID, PendingCast> pendingCasts = new HashMap<>();
    private static final class PendingCast {
        final WeaponCard card;
        final String slot;
        final BuildMods mods;
        final long due, total;
        UUID task;
        PendingCast(WeaponCard card, String slot, BuildMods mods, long due, long total) {
            this.card = card; this.slot = slot; this.mods = mods; this.due = due; this.total = total;
        }
    }

    private final ShieldStore shields;
    private final Map<UUID, Long> continuationEpochs = new HashMap<>();
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
        this.executionLimits = content.vocabulary().executionLimits();
        var combat = content.vocabulary().combatLimits();
        this.shields = new ShieldStore(combat.maxShieldAmount(), combat.maxShieldDurationTicks());
    }

    private long taskSequence;

    /** Queues bounded work; returns null if the queue is full. Rejected work never runs. */
    public UUID schedule(long delayTicks, Runnable task) {
        return schedule(delayTicks, EffectOrder.SYSTEM, task);
    }

    private UUID schedule(long delayTicks, EffectOrder.Key order, Runnable task) {
        if (scheduled.size() >= executionLimits.pendingTasks()) {
            return null;
        }
        long now = world.gameTime();
        long delay = Math.max(0, delayTicks);
        long due = now > Long.MAX_VALUE - delay ? Long.MAX_VALUE : now + delay;
        UUID id = UUID.randomUUID();
        scheduled.add(new ScheduledTask(id, due, order, taskSequence++, task));
        return id;
    }

    /** Cancels a scheduled task. Returns true when it was still pending. */
    public boolean cancel(UUID taskId) {
        return scheduled.removeIf(t -> t.id().equals(taskId));
    }

    /** Advances scheduled work and status expiry. The game calls this every tick. */
    public void tick() {
        long now = world.gameTime();
        refreshBudget();
        var due = new ArrayList<ScheduledTask>();
        scheduled.sort(java.util.Comparator.comparingLong(ScheduledTask::dueTick)
                .thenComparing(ScheduledTask::order, EffectOrder.COMPARATOR).thenComparingLong(ScheduledTask::sequence));
        var it = scheduled.iterator();
        while (it.hasNext()) {
            ScheduledTask t = it.next();
            if (t.dueTick() <= now && tasksThisTick < executionLimits.tasksPerTick()) {
                tasksThisTick++;
                due.add(t);
                it.remove();
            }
        }
        for (ScheduledTask t : due) {
            t.task().run();
        }
        statuses.cleanup(now);
        shields.cleanup(now);
        castBoosts.values().removeIf(boost -> boost.expires() <= now);
    }

    private record ScheduledTask(UUID id, long dueTick, EffectOrder.Key order, long sequence, Runnable task) {
    }

    /**
     * Cross-event loop guard (strike retaliation ping-pong, cast cycles).
     * Chain-depth limits alone cannot bound fresh-context re-entry, so nested
     * event delivery is capped absolutely.
     */
    private int maxEventDepth() {
        return chainLimit() + 2;
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
        NO_RESOURCE,
        CASTING,
        ALREADY_CASTING,
        LIMIT_REACHED
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
        card = SkillSnapshot.copy(card);
        SkillDef skill = card.skills().get(slot);
        if (skill == null || !Set.of("projectile_single", "melee_thrust", "heavy_slam",
                "summon_minions", "dash", "self_buff").contains(skill.core())) {
            return new CastOutcome(CastResult.UNSUPPORTED_CORE, 0);
        }
        if (skill.core().equals("summon_minions")
                && (!(skill.mods().get("enemy") instanceof String id) || id.isBlank())) {
            return new CastOutcome(CastResult.UNSUPPORTED_CORE, 0);
        }
        PendingCast active = pendingCasts.get(caster);
        if (active != null) return new CastOutcome(CastResult.ALREADY_CASTING, Math.max(0, active.due - world.gameTime()));
        long remaining = remainingCooldownTicks(caster, card, slot, mods);
        if (remaining > 0) return new CastOutcome(CastResult.ON_COOLDOWN, remaining);
        // Boss pseudo-slot "cast" is already timed by EncounterRunner's telegraph. Never wait twice.
        double seconds = Set.of("special", "heavy").contains(slot) ? modDouble(skill, "cast_time", 0) : 0;
        if (!Double.isFinite(seconds) || seconds < 0) return new CastOutcome(CastResult.UNSUPPORTED_CORE, 0);
        long baseTicks = (long) Math.ceil(Math.min(seconds, content.vocabulary().castingLimits().maxCastSeconds()) * 20);
        CastBoostKey key = new CastBoostKey(caster, card.id(), slot);
        long ticks = Math.max(0, baseTicks - nextCastReductionTicks(caster, card, slot));
        long now = world.gameTime();
        PendingCast pending = new PendingCast(card, slot, mods,
                now > Long.MAX_VALUE - ticks ? Long.MAX_VALUE : now + ticks, ticks);
        if (ticks > 0) {
            // Reserve the shared task budget before taking costs or consuming the one-shot boost.
            pending.task = schedule(ticks, () -> {
                if (pendingCasts.get(caster) != pending) return;
                pendingCasts.remove(caster);
                completeCast(caster, pending.card, pending.slot, pending.mods);
            });
            if (pending.task == null) return new CastOutcome(CastResult.LIMIT_REACHED, 0);
        }
        Object resource = skill.mods().get("resource");
        Object cost = skill.mods().get("resource_cost");
        if (resource instanceof String name && !name.isEmpty() && cost instanceof Number n
                && !world.tryConsumeResource(caster, name, n.doubleValue())) {
            if (pending.task != null) cancel(pending.task);
            return new CastOutcome(CastResult.NO_RESOURCE, 0);
        }
        lastCastTick.put(cooldownKey(caster, card, slot), now);
        if (baseTicks > 0) castBoosts.remove(key); // Instant native skills do not consume a future timed-cast boost.
        if (ticks > 0) {
            pendingCasts.put(caster, pending);
            return new CastOutcome(CastResult.CASTING, ticks);
        }
        completeCast(caster, card, slot, mods);
        return new CastOutcome(CastResult.OK, 0);
    }

    private void completeCast(UUID caster, WeaponCard card, String slot, BuildMods mods) {
        SkillDef skill = card.skills().get(slot);
        long now = world.gameTime();
        CastContext ctx = new CastContext(card.id() + ":" + slot, 0, Set.of(), caster, 1.0, mods, card);
        var effects = prepareTrigger(skill, ctx, caster, baseDamage(card, skill),
                snapshotStatuses(caster, now), now, "on_cast");
        executePrepared(card, caster, effects, now);
        castChild(card, slot, ctx);
    }

    public long castRemainingTicks(UUID owner) {
        PendingCast cast = pendingCasts.get(owner);
        return cast == null ? 0 : Math.max(0, cast.due - world.gameTime());
    }

    public long castTotalTicks(UUID owner) {
        PendingCast cast = pendingCasts.get(owner);
        return cast == null ? 0 : cast.total;
    }

    public String castingWeapon(UUID owner) {
        PendingCast cast = pendingCasts.get(owner);
        return cast == null ? "" : cast.card.id();
    }

    public String castingSlot(UUID owner) {
        PendingCast cast = pendingCasts.get(owner);
        return cast == null ? "" : cast.slot;
    }

    /** Interrupts without refund: costs, cooldown and any consumed boost commit at acceptance. */
    public boolean cancelCast(UUID owner) {
        PendingCast cast = pendingCasts.remove(owner);
        if (cast == null) return false;
        cancel(cast.task);
        return true;
    }

    public long nextCastReductionTicks(UUID owner, WeaponCard card, String slot) {
        CastBoostKey key = new CastBoostKey(owner, card.id(), slot);
        CastBoost boost = castBoosts.get(key);
        if (boost == null) return 0;
        if (boost.expires() <= world.gameTime()) { castBoosts.remove(key); return 0; }
        return boost.reduction();
    }

    private void reduceNextCast(UUID owner, WeaponCard card, ActionDef action) {
        if (!card.skills().containsKey(action.ref()) || !Double.isFinite(action.amount()) || action.amount() <= 0
                || action.durationTicks() <= 0) return;
        var limits = content.vocabulary().castingLimits();
        long ticks = (long) Math.ceil(Math.min(action.amount(), limits.maxCastSeconds()) * 20);
        if (ticks < nextCastReductionTicks(owner, card, action.ref())) return;
        long duration = Math.min(action.durationTicks(), limits.maxBoostDurationTicks());
        castBoosts.put(new CastBoostKey(owner, card.id(), action.ref()), new CastBoost(ticks, world.gameTime() > Long.MAX_VALUE - duration ? Long.MAX_VALUE : world.gameTime() + duration));
    }

    private String cooldownKey(UUID owner, WeaponCard card, String slot) {
        return owner + ":" + card.id() + ":" + slot;
    }

    public long remainingCooldownTicks(UUID owner, WeaponCard card, String slot, BuildMods mods) {
        Long last = lastCastTick.get(cooldownKey(owner, card, slot));
        if (last == null) {
            return 0;
        }
        return Math.max(0, cooldownTicks(card, slot, mods) - Math.max(0, world.gameTime() - last));
    }

    private void reduceCooldown(UUID owner, WeaponCard card, String slot, BuildMods mods, double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0 || !card.skills().containsKey(slot)) {
            return;
        }
        long remaining = remainingCooldownTicks(owner, card, slot, mods);
        if (remaining == 0) {
            return; // Never bank reductions for a future cast.
        }
        double limit = content.vocabulary().combatLimits().maxCooldownReductionSeconds();
        long ticks = (long) (Math.min(seconds, limit) * 20);
        String key = cooldownKey(owner, card, slot);
        lastCastTick.put(key, lastCastTick.get(key) - Math.min(remaining, ticks));
    }

    public double shieldAmount(UUID owner) {
        return shields.amount(owner, world.gameTime());
    }

    public long shieldRemainingTicks(UUID owner) {
        return shields.remainingTicks(owner, world.gameTime());
    }

    public double damageAfterShield(UUID owner, double damage) {
        return shields.preview(owner, damage, world.gameTime());
    }

    /** Called exactly once at the player's RPG HP funnel, after damage interception/scaling. */
    public double absorbShield(UUID owner, double damage) {
        return shields.absorb(owner, damage, world.gameTime());
    }

    /** Reset boundaries also invalidate pending effect continuations, preventing post-respawn recovery. */
    public void clearShield(UUID owner) {
        shields.clear(owner);
        cancelCast(owner);
        castBoosts.keySet().removeIf(key -> key.owner().equals(owner));
        continuationEpochs.merge(owner, 1L, Long::sum);
    }

    private UUID scheduleContinuation(long delay, UUID owner, EffectOrder.Key order, Runnable task) {
        long epoch = continuationEpochs.getOrDefault(owner, 0L);
        return schedule(delay, order, () -> {
            if (continuationEpochs.getOrDefault(owner, 0L) == epoch) {
                task.run();
            }
        });
    }

    /** Called by the adapter when a tracked projectile hits something. */
    public void onProjectileHit(UUID projectileId, UUID victim) {
        CastContext ctx = liveProjectiles.remove(projectileId);
        if (ctx == null) {
            return;
        }
        Resolved r = resolve(ctx);
        if (r == null) {
            return;
        }
        long now = world.gameTime();
        // §6.6: conditions are judged on the snapshot at event start, before delivery applies.
        Set<String> preStatuses = snapshotStatuses(victim, now);
        double base = scaledBase(r.card(), r.skill(), ctx.owner(), ctx.damageMult(), ctx.mods(), false);
        var effects = prepareTrigger(r.skill(), ctx, victim, base, preStatuses, now, "on_hit");
        world.dealDamage(ctx.owner(), victim, base, DamageKind.PROJECTILE, projectileId, ctx);
        if (isFire(r.skill())) {
            statuses.add(victim, "ignite", now + IGNITE_TICKS);
        }
        executePrepared(r.card(), ctx.owner(), effects, now);
    }

    /** Vanilla-attack replacement for weapon melee. */
    public void meleeStrike(UUID attacker, WeaponCard card, SkillDef skill, CastContext ctx) {
        meleeStrike(attacker, card, skill, ctx, ctx.mods());
    }

    public void meleeStrike(UUID attacker, WeaponCard card, SkillDef skill, CastContext ctx,
            BuildMods mods) {
        if (ctx.snapshot() == null) ctx = ctx.withSnapshot(SkillSnapshot.copy(card));
        double range = modDouble(skill, "range", 3.0) + 1.0;
        List<UUID> targets = world.targetsInArc(attacker, range, 0.5).stream().distinct().sorted().toList();
        double base = scaledBase(card, skill, attacker, ctx.damageMult(), mods, true);
        long now = world.gameTime();
        for (UUID target : targets) {
            Set<String> preStatuses = snapshotStatuses(target, now);
            var effects = prepareTrigger(skill, ctx, target, base, preStatuses, now, "on_hit");
            world.dealDamage(attacker, target, base, DamageKind.MELEE, null, ctx);
            executePrepared(card, attacker, effects, now);
        }
    }

    /** Point-blank AoE around the caster (heavy_slam delivery). */
    public void slamStrike(UUID attacker, WeaponCard card, SkillDef skill, CastContext ctx) {
        slamStrike(attacker, card, skill, ctx, ctx.mods());
    }

    public void slamStrike(UUID attacker, WeaponCard card, SkillDef skill, CastContext ctx,
            BuildMods mods) {
        if (ctx.snapshot() == null) ctx = ctx.withSnapshot(SkillSnapshot.copy(card));
        double radius = modDouble(skill, "radius", 6.0);
        List<UUID> targets = world.targetsInArc(attacker, radius, -1.0).stream().distinct().sorted().toList();
        double base = scaledBase(card, skill, attacker, ctx.damageMult(), mods, true);
        long now = world.gameTime();
        world.burstParticles(attacker, world.pos(attacker));
        for (UUID target : targets) {
            Set<String> preStatuses = snapshotStatuses(target, now);
            var effects = prepareTrigger(skill, ctx, target, base, preStatuses, now, "on_hit");
            world.dealDamage(attacker, target, base, DamageKind.MELEE, null, ctx);
            executePrepared(card, attacker, effects, now);
        }
    }

    /** Kill attribution: fires the killer weapon's on_kill effects once each. */
    public void onKill(WeaponCard card, UUID attacker, UUID victim) {
        onKill(card, attacker, victim, BuildMods.neutral());
    }

    public void onKill(WeaponCard card, UUID attacker, UUID victim, BuildMods mods) {
        dispatch(card, attacker, attacker, victim, mods, "on_kill");
    }

    /** Defender-side trigger: fires the victim weapon's on_damaged effects once each. */
    public void onDamaged(WeaponCard card, UUID victim, UUID attacker) {
        dispatch(card, victim, attacker, victim, BuildMods.neutral(), "on_damaged");
    }

    /** Join buff: fires the joining weapon's on_join effects once each. */
    public void onJoin(WeaponCard card, UUID player, BuildMods mods) {
        dispatch(card, player, player, player, mods, "on_join");
    }

    public void onKill(WeaponCard card, UUID attacker, UUID victim, BuildMods mods, CastContext cause) {
        if (cause != null && attacker.equals(cause.owner()) && cause.snapshot() != null) {
            card = cause.snapshot();
            mods = cause.mods();
        }
        dispatch(card, attacker, attacker, victim, mods, "on_kill", cause);
    }

    public void onDamaged(WeaponCard card, UUID victim, UUID attacker, BuildMods mods, CastContext cause) {
        dispatch(card, victim, attacker, victim, mods, "on_damaged", cause);
    }

    public void onDeath(WeaponCard card, UUID victim, UUID killer, BuildMods mods, CastContext cause) {
        dispatch(card, victim, killer, victim, mods, "on_death", cause);
    }

    private void dispatch(WeaponCard card, UUID owner, UUID attacker, UUID victim,
            BuildMods mods, String trigger) {
        dispatch(card, owner, attacker, victim, mods, trigger, null);
    }

    private void dispatch(WeaponCard card, UUID owner, UUID attacker, UUID victim,
            BuildMods mods, String trigger, CastContext cause) {
        long now = world.gameTime();
        Set<String> pre = snapshotStatuses(victim, now);
        List<EffectCandidate> candidates = new ArrayList<>();
        for (var entry : card.skills().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            SkillDef skill = entry.getValue();
            double base = scaledBase(card, skill, owner, 1.0, mods, false);
            CastContext ctx = new CastContext(card.id() + ":" + entry.getKey(), cause == null ? 0 : cause.chainDepth(),
                    cause == null ? Set.of() : cause.preventRecursive(), owner, 1.0, mods, SkillSnapshot.copy(card));
            collectTrigger(candidates, skill, ctx, victim, base, pre, trigger);
        }
        var prepared = prepareCandidates(candidates, now);
        executePrepared(card, attacker, prepared, now);
        if (!prepared.isEmpty()) {
            eventLog.accept(trigger + " " + card.id());
        }
    }

    /** Slot ids are stable even when cards were assembled from unordered maps. */
    private static List<SkillDef> orderedSkills(WeaponCard card) {
        return card.skills().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue).toList();
    }

    public static boolean hasTrigger(WeaponCard card, String trigger) {
        for (SkillDef skill : orderedSkills(card)) {
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
        dispatch(card, miner, miner, miner, mods, "on_break");
    }

    /** Deathburst: fires the victim weapon's on_death effects once each. */
    public void onDeath(WeaponCard card, UUID victim, UUID killer, BuildMods mods) {
        dispatch(card, victim, killer, victim, mods, "on_death");
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
        List<EffectCandidate> candidates = new ArrayList<>();
        for (var entry : card.skills().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
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
            CastContext ctx = new CastContext(card.id() + ":" + entry.getKey(), 0, Set.of(), owner, 1.0, mods, SkillSnapshot.copy(card));
            Set<UUID> targets = new java.util.TreeSet<>(world.targetsInArc(owner, radius, -1.0));
            targets.add(owner);
            for (int index = 0; index < skill.effects().size(); index++) {
                EffectDef effect = skill.effects().get(index);
                if (!effect.trigger().equals("on_timer")) continue;
                var order = EffectOrder.key(effect, ctx.skillId(), index);
                if (effect.scope().equals("area")) {
                    candidates.add(new EffectCandidate(effect, ctx, owner, base, snapshotStatuses(owner, now), order));
                } else {
                    for (UUID target : targets) {
                        candidates.add(new EffectCandidate(effect, ctx, target, base, snapshotStatuses(target, now), order));
                    }
                }
            }
            fired = true;
        }
        executePrepared(card, owner, prepareCandidates(candidates, now), now);
        if (fired) {
            eventLog.accept("timer " + card.id());
        }
    }

    private void summonMinions(UUID caster, String enemyId, int count) {
        Vec3 center = world.pos(caster);
        for (int i = 0; i < Math.min(projectileLimit(), count); i++) {
            double angle = (Math.PI * 2.0 * i) / Math.max(1, count);
            Vec3 spot = new Vec3(center.x() + Math.cos(angle) * 2.5, center.y(), center.z() + Math.sin(angle) * 2.5);
            world.spawnMinion(caster, enemyId, spot);
        }
    }

    // ---- internals ----

    /** Optional diagnostic stream. No trace objects are allocated until a consumer opts in. */
    public record EffectTrace(long tick, String trigger, String skill, String source, String id,
            int priority, UUID owner, UUID target, int depth, Set<String> history) {
        public EffectTrace { history = Set.copyOf(history); }
    }
    private java.util.function.Consumer<EffectTrace> effectTraceListener;
    public void setEffectTraceListener(java.util.function.Consumer<EffectTrace> listener) {
        effectTraceListener = listener;
    }

    private record EffectCandidate(EffectDef effect, CastContext context, UUID victim,
            double base, Set<String> statuses, EffectOrder.Key order) { }

    private record PreparedEffect(EffectDef effect, CastContext context, UUID victim,
            double base, Set<String> statuses, EffectOrder.Key order) { }

    private void collectTrigger(List<EffectCandidate> candidates, SkillDef skill, CastContext ctx, UUID victim,
            double base, Set<String> pre, String trigger) {
        for (int index = 0; index < skill.effects().size(); index++) {
            EffectDef effect = skill.effects().get(index);
            if (effect.trigger().equals(trigger)) {
                candidates.add(new EffectCandidate(effect, ctx, victim, base, pre,
                        EffectOrder.key(effect, ctx.skillId(), index)));
            }
        }
    }

    private List<PreparedEffect> prepareTrigger(SkillDef skill, CastContext ctx, UUID victim,
            double base, Set<String> pre, long now, String trigger) {
        List<EffectCandidate> candidates = new ArrayList<>();
        collectTrigger(candidates, skill, ctx, victim, base, pre, trigger);
        return prepareCandidates(candidates, now);
    }

    /** Sort before budget admission and chance rolls; execute only after every admitted condition is evaluated. */
    private List<PreparedEffect> prepareCandidates(List<EffectCandidate> candidates, long now) {
        List<PreparedEffect> prepared = new ArrayList<>();
        refreshBudget();
        if (eventDepth >= maxEventDepth()) return prepared;
        candidates.sort(java.util.Comparator.comparing(EffectCandidate::order, EffectOrder.COMPARATOR)
                .thenComparing(EffectCandidate::victim));
        for (EffectCandidate candidate : candidates) {
            if (effectsThisTick >= executionLimits.effectsPerTick()) break;
            EffectDef e = candidate.effect();
            CastContext ctx = candidate.context();
            if (ctx.chainDepth() > chainLimit()) continue;
            maximumObservedChainDepth = Math.max(maximumObservedChainDepth, ctx.chainDepth());
            int limit = Math.min(chainLimit(), Math.max(0, e.maxChainDepth()) + Math.max(0, ctx.mods().chainBonus()));
            if (ctx.chainDepth() > limit || e.preventRecursive().stream().anyMatch(ctx.preventRecursive()::contains)) continue;
            List<UUID> targets = e.scope().equals("area")
                    ? world.targetsInArc(ctx.owner(), e.radius() > 0 ? e.radius() : 6.0, -1.0).stream().distinct().sorted().toList()
                    : List.of(candidate.victim());
            for (UUID target : targets) {
                if (effectsThisTick >= executionLimits.effectsPerTick()) break;
                effectsThisTick++;
                Set<String> pre = target.equals(candidate.victim()) ? candidate.statuses() : snapshotStatuses(target, now);
                if (conditionsMet(e, ctx.owner(), target, pre)) {
                    prepared.add(new PreparedEffect(e, ctx, target, candidate.base(), Set.copyOf(pre), candidate.order()));
                }
            }
        }
        return prepared;
    }

    private void executePrepared(WeaponCard card, UUID attacker, List<PreparedEffect> effects, long now) {
        if (!enterEvent()) {
            return;
        }
        try {
            for (PreparedEffect p : effects) {
                if (executedActionsThisTick() >= executionLimits.actionsPerTick()) {
                    break;
                }
                if (effectTraceListener != null) {
                    effectTraceListener.accept(new EffectTrace(now, p.effect().trigger(), p.context().skillId(),
                            p.order().source(), p.order().id(), p.order().priority(), p.context().owner(), p.victim(),
                            p.context().chainDepth(), p.context().preventRecursive()));
                }
                runActions(p.effect(), card, p.context(), attacker, p.victim(), p.base(), p.statuses(), p.order(), now);
            }
        } finally {
            exitEvent();
        }
    }

    /** Runs one effect's actions; delay defers the remaining tail. */
    private void runActions(EffectDef e, WeaponCard card, CastContext ctx, UUID attacker,
            UUID victim, double base, Set<String> preStatuses, EffectOrder.Key order, long now) {
        List<ActionDef> acts = e.actions();
        for (int i = 0; i < acts.size(); i++) {
            if (executedActionsThisTick() >= executionLimits.actionsPerTick()) {
                return;
            }
            ActionDef a = acts.get(i);
            if (a.type().equals("delay")) {
                if (!takeAction()) {
                    return;
                }
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
                scheduleContinuation(ticks, ctx.owner(), order, () -> runTail(tail, card, ctx, attacker, victim, base, pre, world.gameTime()));
                return;
            }
            if (a.type().equals("repeat")) {
                if (!takeAction()) {
                    return;
                }
                List<ActionDef> head = new ArrayList<>();
                for (int j = 0; j < i; j++) {
                    String t = acts.get(j).type();
                    if (!t.equals("delay") && !t.equals("repeat")) {
                        head.add(acts.get(j));
                    }
                }
                int times = Math.max(1, Math.min(projectileLimit(), a.count()));
                long interval = (long) evalAmount(a, ctx.owner(), victim, base);
                if (interval <= 0) {
                    interval = 10;
                }
                for (int k = 1; k < times; k++) {
                    long delay = interval > Long.MAX_VALUE / k ? Long.MAX_VALUE : interval * k;
                    if (scheduleContinuation(delay, ctx.owner(), order,
                            () -> runTail(head, card, ctx, attacker, victim, base,
                                    new HashSet<>(preStatuses), world.gameTime())) == null) {
                        break;
                    }
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
                if (executedActionsThisTick() >= executionLimits.actionsPerTick()) {
                    break;
                }
                runAction(a, card, ctx, attacker, victim, base, now);
            }
        } finally {
            exitEvent();
        }
    }

    private void runAction(ActionDef a, WeaponCard card, CastContext ctx, UUID attacker,
            UUID victim, double base, long now) {
        if (!takeAction()) {
            return;
        }
        switch (a.type()) {
            case "spawn_projectiles" -> {
                if (ctx.chainDepth() >= chainLimit() || ctx.preventRecursive().contains(a.type())) {
                    break;
                }
                fireSplit(ctx, attacker, victim, a, base, now);
            }
            case "dash" -> dashMove(ctx.owner(), a.distance() > 0 ? a.distance() : 6.0);
            case "heal_self" ->
                world.healEntity(ctx.owner(), evalAmount(a, ctx.owner(), victim, base));
            case "grant_shield" -> shields.grant(ctx.owner(), a.amount(), a.durationTicks(), world.gameTime());
            case "heal_with_shield" -> {
                if (!Double.isFinite(a.amount()) || a.amount() <= 0 || !Double.isFinite(a.shieldRatio())
                        || a.shieldRatio() < 0 || a.shieldRatio() > 1 || a.durationTicks() <= 0) {
                    break;
                }
                double total = Math.min(a.amount(), content.vocabulary().combatLimits().maxShieldAmount());
                shields.grant(ctx.owner(), total * a.shieldRatio(), a.durationTicks(), world.gameTime());
                double healing = total * (1 - a.shieldRatio());
                if (healing > 0) {
                    world.healEntity(ctx.owner(), healing);
                }
            }
            case "reduce_next_cast" -> reduceNextCast(ctx.owner(), card, a);
            case "reduce_cooldown" -> reduceCooldown(ctx.owner(), card, a.ref(), ctx.mods(), a.amount());
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
                if (ctx.chainDepth() >= chainLimit()) break;
                double mult = a.damageMult() > 0 ? a.damageMult() : 1.0;
                world.dealDamage(ctx.owner(), victim, base * mult, DamageKind.MELEE, null, ctx.causedBy("strike"));
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
                if (ctx.chainDepth() >= chainLimit()) break;
                double mult = a.damageMult() > 0 ? a.damageMult() : 1.0;
                world.strikeLightning(ctx.owner(), victim, base * mult, ctx.causedBy("lightning"));
                eventLog.accept("lightning");
            }
            case "explosion" -> {
                if (ctx.chainDepth() >= chainLimit()) break;
                double power = a.damageMult() > 0 ? a.damageMult() : 2.0;
                world.explode(ctx.owner(), world.pos(victim), power, ctx.causedBy("explosion"));
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
                        && ctx.chainDepth() < chainLimit()
                        && card.skills().containsKey(a.ref())
                        && !ctx.preventRecursive().contains("cast:" + a.ref())) {
                    Set<String> prevent = new HashSet<>(ctx.preventRecursive());
                    prevent.add("cast:" + a.ref());
                    prevent.add("cast");
                    CastContext child = new CastContext(card.id() + ":" + a.ref(), ctx.chainDepth() + 1,
                            prevent, ctx.owner(), ctx.damageMult(), ctx.mods(), ctx.snapshot());
                    castChild(card, a.ref(), child);
                }
            }
            case "missile" -> {
                if (ctx.chainDepth() >= chainLimit() || ctx.preventRecursive().contains("missile")) {
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
            case "self_buff" -> { /* Effects run in the direct, paid on_cast event only. */ }
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
        for (int i = 0; i < Math.min(projectileLimit(), a.count()); i++) {
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
        for (int i = 0; i < Math.min(projectileLimit(), a.count()); i++) {
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
                case "self_has_shield" -> {
                    if (shieldAmount(owner) <= 0) {
                        return false;
                    }
                }
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

    private Resolved resolve(CastContext context) {
        String ctxSkillId = context.skillId();
        // Split at the last colon: "<weaponId>:<slot>". Weapon ids contain colons too.
        int last = ctxSkillId.lastIndexOf(':');
        if (last <= 0) {
            return null;
        }
        String weaponId = ctxSkillId.substring(0, last);
        String slot = ctxSkillId.substring(last + 1);
        WeaponCard card = context.snapshot() != null ? context.snapshot() : content.weapons().get(weaponId);
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
        shields.cleanup(now);
        castBoosts.values().removeIf(boost -> boost.expires() <= now);
    }
}
