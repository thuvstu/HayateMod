package com.thuvstu.hayatemod.rpg;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.thuvstu.hayatemod.content.ContentHolder;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Player RPG health (ADR-07): vanilla damage is cancelled and applied to an
 * independent pool; vanilla hearts mirror the fraction for display.
 * On RPG death the vanilla kill path runs ({@link LivingEntity#kill}).
 *
 * <p>Phase 2/3 scope: players only, flat reference HP (Lv1), passthrough
 * amounts (armor rules arrive with the mitigation pass).
 */
public final class RpgHealth {
    private static final Logger LOGGER = LoggerFactory.getLogger("hayatemod/rpg");

    private static final Map<UUID, Double> HP = new HashMap<>();

    private RpgHealth() {
    }

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(RpgHealth::onAllowDamage);
    }

    public static double maxHp() {
        var set = ContentHolder.get();
        int wantLevel = 10;
        if (set != null && set.tuning() != null) {
            wantLevel = set.tuning().heroicReferenceLevel();
        }
        if (set != null) {
            for (var e : set.reference()) {
                if (e.level() == wantLevel) {
                    return e.hp();
                }
            }
        }
        return 100.0;
    }

    public static double get(UUID player) {
        return HP.getOrDefault(player, maxHp());
    }

    private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayer player)) {
            return true;
        }
        // S1 shield runs first and may already have cancelled (returns false stops the chain).
        double max = maxHp();
        double cur = HP.getOrDefault(player.getUUID(), max);
        var engine = McAdapter.engine();
        double effective = engine == null ? amount : engine.absorbShield(player.getUUID(), amount);
        if (effective <= 0) {
            return false;
        }
        double next = cur - effective;
        if (next <= 0.0) {
            // Lethal: pin vanilla HP near zero and let the normal fatal path run so
            // ALLOW_DEATH hooks (debug wipetest, dungeon wipe) observe the death.
            HP.put(player.getUUID(), 0.0);
            if (engine != null) {
                engine.clearShield(player.getUUID());
            }
            player.setHealth(0.01F);
            LOGGER.info("[RPG][DEATH] {} src={} amount={}", player.getScoreboardName(),
                    source.getMsgId(), amount);
            return true;
        }
        HP.put(player.getUUID(), next);
        player.setHealth(mirror(next, max));
        LOGGER.info("[RPG][HIT] {} src={} amount={} rpg={}/{}", player.getScoreboardName(),
                source.getMsgId(), amount, String.format("%.1f", next), String.format("%.0f", max));
        return false;
    }

    /** Full restore (checkpoints, wipe recovery, debug). */
    public static void restore(ServerPlayer player) {
        clearShield(player.getUUID());
        double max = maxHp();
        HP.put(player.getUUID(), max);
        player.setHealth(20.0F);
    }

    /**
     * Redirects vanilla {@code heal()} into the RPG pool. Returns true when
     * consumed (caller must cancel the vanilla heal).
     */
    public static boolean healHook(ServerPlayer player, float amount) {
        double max = maxHp();
        double next = Math.min(max, HP.getOrDefault(player.getUUID(), max) + amount);
        HP.put(player.getUUID(), next);
        player.setHealth(mirror(next, max));
        return true;
    }

    private static float mirror(double hp, double max) {
        return (float) Math.max(1.0, Math.min(20.0, hp / max * 20.0));
    }

    /** Test/debug reset. */
    static void reset(UUID player) {
        HP.remove(player);
        clearShield(player);
    }

    private static void clearShield(UUID player) {
        if (McAdapter.engine() != null) {
            McAdapter.engine().clearShield(player);
        }
    }
}
