package com.thuvstu.hayatemod.dungeon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.thuvstu.hayatemod.content.ContentHolder;
import com.thuvstu.hayatemod.core.content.model.Models.NpcData;
import com.thuvstu.hayatemod.core.engine.WorldAdapter.DamageKind;
import com.thuvstu.hayatemod.rpg.McAdapter;
import com.thuvstu.hayatemod.rpg.RpgHealth;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Session NPC party (design §5.11). Villagers with no vanilla AI, driven by a
 * small tick loop: follow, melee with role DPS, healer tops the player,
 *定型 signal orders with proficiency-based fumbles on non-lethal mechanics.
 *
 * <p>Numbers are placeholders (ADR-11); the structure (signals, downed,
 * checkpoint revive) is the point of this phase.
 */
public final class NpcParty {
    private static final Logger LOGGER = LoggerFactory.getLogger("hayatemod/dungeon");

    private static final double STEP = 0.22;
    private static final long ATTACK_PERIOD = 20L;
    private static final long HEAL_PERIOD = 60L;
    private static final long ORDER_TIMEOUT = 200L;

    private static final List<Member> MEMBERS = new ArrayList<>();

    private NpcParty() {
    }

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DEATH.register(NpcParty::onAllowDeath);
    }

    public static final class Member {
        final UUID id;
        final NpcData data;
        final double maxHp;        Vec3 moveGoal;
        long orderTick;
        UUID focusId;
        long attackTick;
        long healTick;
        boolean downed;

        Member(UUID id, NpcData data, double maxHp) {
            this.id = id;
            this.data = data;
            this.maxHp = maxHp;
        }

        public UUID id() {
            return id;
        }

        public NpcData data() {
            return data;
        }

        public boolean downed() {
            return downed;
        }
    }

    public static List<Member> members() {
        return List.copyOf(MEMBERS);
    }

    public static boolean active() {
        return !MEMBERS.isEmpty();
    }

    public static void spawn(ServerLevel level, Vec3 around) {
        despawn(level);
        if (!ContentHolder.ready()) {
            return;
        }
        int i = 0;
        for (NpcData npc : ContentHolder.get().npcs().values()) {
            double maxHp = switch (npc.role()) {
                case "tank" -> 60.0;
                case "healer" -> 40.0;
                default -> 40.0;
            };
            // Spawn on the floor: NoAI mobs never fall, so never spawn them airborne.
            Vec3 spot = new Vec3(around.x + i * 2 - 2, ArenaManager.floorY(), around.z + 2);
            Villager v = EntityType.VILLAGER.spawn(level, m -> {
                m.setNoAi(true);
                Objects.requireNonNull(m.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(maxHp);
                m.setHealth((float) maxHp);
                m.setCustomName(Component.literal(npc.name() + "〈" + npc.role() + "〉"));
                m.setCustomNameVisible(true);
                m.setPersistenceRequired();
                m.addTag("solommo_session");
                m.addTag("solommo_npc");
            }, BlockPos.containing(spot), EntitySpawnReason.MOB_SUMMONED, false, false);
            if (v != null) {
                MEMBERS.add(new Member(v.getUUID(), npc, maxHp));
            }
            i++;
        }
        LOGGER.info("[DUNGEON] party spawned ({} members)", MEMBERS.size());
    }

    public static void despawn(ServerLevel level) {
        for (Member m : MEMBERS) {
            EntityTypeTestHelper.discard(level, m.id);
        }
        MEMBERS.clear();
    }

    public static void reviveAll(ServerLevel level) {
        for (Member m : MEMBERS) {
            LivingEntity e = EntityTypeTestHelper.living(level, m.id);
            if (e != null) {
                e.setHealth((float) m.maxHp);
                e.setInvisible(false);
                m.downed = false;
                m.moveGoal = null;
                m.focusId = null;
            }
        }
    }

    private static boolean onAllowDeath(LivingEntity entity, DamageSource source, float amount) {
        if (!entity.getTags().contains("solommo_npc")) {
            return true;
        }
        for (Member m : MEMBERS) {
            if (m.id.equals(entity.getUUID())) {
                m.downed = true;
                entity.setInvisible(true);
                LOGGER.info("[DUNGEON][DOWN] {} is down (no permadeath, revive at checkpoint)",
                        m.data.name());
                return false;
            }
        }
        return true;
    }

    /**
     * 定型 signal order. {@code center} is the signal point (stack point, safe
     * spot, ...); {@code lethal} abilities are never fumbled (§5.11).
     */
    public static void orderSignal(ServerLevel level, String signal, Vec3 center, UUID bossId,
            boolean lethal) {
        for (Member m : MEMBERS) {
            LivingEntity e = EntityTypeTestHelper.living(level, m.id);
            if (e == null || m.downed) {
                continue;
            }
            switch (signal) {
                case "STACK", "MOVE_TO_SAFE_SPOT", "AVOID_AREA" -> moveOrder(e, m, center, lethal);
                case "SPREAD" -> {
                    Vec3 away = spreadPoint(e, center);
                    moveOrder(e, m, away, lethal);
                }
                case "FOCUS_ADDS" -> {
                    m.focusId = nearestAdd(level, e, bossId);
                    m.moveGoal = null;
                    LOGGER.info("[DUNGEON][ORDER] {} FOCUS_ADDS -> {}", m.data.name(), m.focusId);
                }
                case "BRACE" -> {
                    brace(level, m, lethal);
                }
                case "TANK_SWAP" -> LOGGER.info("[DUNGEON][ORDER] TANK_SWAP no-op (single tank)");
                default -> LOGGER.info("[DUNGEON][ORDER] unknown signal {}", signal);
            }
        }
    }

    private static void moveOrder(LivingEntity e, Member m, Vec3 goal, boolean lethal) {
        if (!lethal && fumble(m)) {
            Vec3 bad = new Vec3(e.position().x + 3, ArenaManager.floorY(), e.position().z);
            m.moveGoal = clampArena(bad);
            m.orderTick = e.level().getGameTime();
            LOGGER.info("[DUNGEON][FUMBLE] {} missed the order (proficiency {})", m.data.name(),
                    m.data.proficiency());
            return;
        }
        m.moveGoal = clampArena(goal);
        m.orderTick = e.level().getGameTime();
    }

    /** Non-lethal mistake roll: {@code mistake_rate = (1 - p) x 0.25}. */
    private static boolean fumble(Member m) {
        double rate = (1.0 - m.data.proficiency()) * 0.25;
        return Math.random() < rate;
    }

    private static void brace(ServerLevel level, Member m, boolean lethal) {
        if (!lethal && fumble(m)) {
            LOGGER.info("[DUNGEON][FUMBLE] {} failed to brace", m.data.name());
            return;
        }
        LivingEntity healer = findHealer(level);
        LivingEntity self = EntityTypeTestHelper.living(level, m.id);
        if (self != null && healer != null && !self.getUUID().equals(healer.getUUID())) {
            m.moveGoal = healer.position();
            m.orderTick = level.getGameTime();
        }
        burstHeal(level);
    }

    private static void burstHeal(ServerLevel level) {
        double amount = RpgHealth.maxHp() * 0.3;
        for (ServerPlayer p : level.players()) {
            RpgHealth.healHook(p, (float) amount);
        }
        LivingEntity healer = findHealer(level);
        if (healer != null) {
            healer.swing(InteractionHand.MAIN_HAND);
        }
    }

    public static void tick(ServerLevel level, UUID playerId, UUID bossId) {
        long now = level.getGameTime();
        LivingEntity boss = bossId != null ? EntityTypeTestHelper.living(level, bossId) : null;
        for (Member m : MEMBERS) {
            LivingEntity e = EntityTypeTestHelper.living(level, m.id);
            if (e == null || m.downed || !(e instanceof Mob mob)) {
                continue;
            }
            if (m.moveGoal != null) {
                if (stepToward(mob, m.moveGoal)) {
                    m.moveGoal = null;
                } else if (now - m.orderTick > ORDER_TIMEOUT) {
                    // Final fallback: snap into place (§5.11).
                    mob.teleportTo(m.moveGoal.x, m.moveGoal.y, m.moveGoal.z);
                    m.moveGoal = null;
                    LOGGER.info("[DUNGEON][SNAP] {} snapped to goal", m.data.name());
                } else {
                    continue;
                }
            }
            if (m.data.role().equals("healer")) {
                tickHealer(level, mob, m, playerId, boss, now);
            } else {
                tickFighter(level, mob, m, playerId, boss, now);
            }
        }
    }

    private static void tickHealer(ServerLevel level, Mob mob, Member m, UUID playerId,
            LivingEntity boss, long now) {
        LivingEntity player = EntityTypeTestHelper.living(level, playerId);
        boolean acted = false;
        if (player instanceof ServerPlayer sp
                && RpgHealth.get(playerId) < RpgHealth.maxHp() * 0.7 && now >= m.healTick) {
            RpgHealth.healHook(sp, (float) (RpgHealth.maxHp() * 0.3));
            acted = true;
            LOGGER.info("[DUNGEON][HEAL] {} healed {}", m.data.name(), sp.getScoreboardName());
        } else {
            LivingEntity lowest = lowestAlly(level, m.id);
            if (lowest != null && lowest.getHealth() < lowest.getMaxHealth() * 0.7 && now >= m.healTick) {
                lowest.setHealth((float) Math.min(lowest.getMaxHealth(),
                        lowest.getHealth() + lowest.getMaxHealth() * 0.5));
                acted = true;
            }
        }
        if (acted) {
            mob.swing(InteractionHand.MAIN_HAND);
            m.healTick = now + HEAL_PERIOD;
        }
        // Healer keeps its distance: backs off when the boss closes in.
        if (boss != null && boss.isAlive()
                && mob.position().distanceTo(boss.position()) < 8.0) {
            Vec3 away = mob.position().subtract(boss.position());
            away = new Vec3(away.x, 0, away.z);
            if (away.length() < 1e-6) {
                away = new Vec3(1, 0, 0);
            }
            stepToward(mob, mob.position().add(away.normalize().scale(2.0)));
        } else if (player != null && mob.position().distanceTo(player.position()) > 6.0) {
            stepToward(mob, player.position());
        }
    }

    private static LivingEntity lowestAlly(ServerLevel level, UUID self) {
        LivingEntity best = null;
        for (Member m : MEMBERS) {
            if (m.id.equals(self) || m.downed) {
                continue;
            }
            LivingEntity e = EntityTypeTestHelper.living(level, m.id);
            if (e != null && (best == null
                    || e.getHealth() / e.getMaxHealth() < best.getHealth() / best.getMaxHealth())) {
                best = e;
            }
        }
        return best;
    }

    private static void tickFighter(ServerLevel level, Mob mob, Member m, UUID playerId, LivingEntity boss,
            long now) {
        LivingEntity target = null;
        if (m.focusId != null) {
            LivingEntity f = EntityTypeTestHelper.living(level, m.focusId);
            if (f != null && f.isAlive()) {
                target = f;
            } else {
                m.focusId = null;
            }
        }
        if (target == null) {
            target = boss != null && boss.isAlive() ? boss : null;
        }
        if (target == null) {
            LivingEntity player = EntityTypeTestHelper.living(level, playerId);
            if (player != null && mob.position().distanceTo(player.position()) > 4.0) {
                stepToward(mob, player.position());
            }
            return;
        }
        double dist = mob.position().distanceTo(target.position());
        if (dist > 3.5) {
            stepToward(mob, target.position());
            return;
        }
        if (now >= m.attackTick) {
            // Spike-tuned to reference DPS (tank 0.6x, dps 1.0x of DPS_ref per second).
            double base = m.data.role().equals("tank") ? 54.0 : 90.0;
            double dmg = base * (0.5 + 0.5 * m.data.proficiency());
            Vec3 toTarget = target.position().subtract(mob.position());
            mob.setYRot((float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z)));
            mob.swing(InteractionHand.MAIN_HAND);
            McAdapter.adapter().dealDamage(mob.getUUID(), target.getUUID(), dmg, DamageKind.MELEE,
                    null);
            m.attackTick = now + ATTACK_PERIOD;
        }
    }

    /** Steps toward the goal; returns true when arrived. Y is pinned to the arena floor. */
    static boolean stepToward(Mob mob, Vec3 goal) {
        Vec3 cur = mob.position();
        Vec3 flat = new Vec3(goal.x - cur.x, 0, goal.z - cur.z);
        double dist = flat.length();
        if (dist < 0.5) {
            return true;
        }
        mob.setYRot((float) Math.toDegrees(Math.atan2(-flat.x, flat.z)));
        double step = Math.min(STEP, dist);
        Vec3 next = cur.add(flat.normalize().scale(step));
        next = clampArena(next);
        mob.teleportTo(next.x, ArenaManager.floorY(), next.z);
        return false;
    }

    private static Vec3 spreadPoint(LivingEntity e, Vec3 threat) {
        Vec3 away = e.position().subtract(threat);
        away = new Vec3(away.x, 0, away.z);
        if (away.length() < 1e-6) {
            away = new Vec3(1, 0, 0);
        }
        return clampArena(e.position().add(away.normalize().scale(8.0)));
    }

    private static Vec3 clampArena(Vec3 v) {
        double x = Math.max(ArenaManager.origin().getX() - 11,
                Math.min(ArenaManager.origin().getX() + 11, v.x));
        double z = Math.max(ArenaManager.origin().getZ() - 11,
                Math.min(ArenaManager.origin().getZ() + 11, v.z));
        return new Vec3(x, v.y, z);
    }

    private static UUID nearestAdd(ServerLevel level, LivingEntity self, UUID bossId) {
        UUID best = null;
        double bestDist = 20.0;
        for (LivingEntity e : ArenaManager.sessionMobs(level, bossId)) {
            if (e.getUUID().equals(self.getUUID())) {
                continue;
            }
            double d = self.position().distanceTo(e.position());
            if (d < bestDist) {
                bestDist = d;
                best = e.getUUID();
            }
        }
        return best;
    }

    private static LivingEntity findHealer(ServerLevel level) {
        for (Member m : MEMBERS) {
            if (m.data.role().equals("healer") && !m.downed) {
                LivingEntity e = EntityTypeTestHelper.living(level, m.id);
                if (e != null) {
                    return e;
                }
            }
        }
        return null;
    }
}
