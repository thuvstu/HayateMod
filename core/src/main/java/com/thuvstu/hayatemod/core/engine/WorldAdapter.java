package com.thuvstu.hayatemod.core.engine;

import java.util.List;
import java.util.UUID;

/**
 * Game-side services the effect engine needs. Implemented by the mod (or a
 * test fake). Keeps {@code core} free of Minecraft classes.
 */
public interface WorldAdapter {
    /** Current game time in ticks. */
    long gameTime();

    /** Eye position of an entity. */
    Vec3 eyePos(UUID entity);

    /** Facing (look) direction of an entity, normalized. */
    Vec3 facing(UUID entity);

    /** Feet position of an entity. */
    Vec3 pos(UUID entity);

    /**
     * Spawns a skill projectile and returns its id. The engine tracks the id
     * to attribute later hits to {@code ctx}.
     */
    UUID spawnBolt(UUID owner, Vec3 origin, Vec3 direction, double speed, CastContext ctx);

    /**
     * Applies engine-computed damage (bypasses vanilla damage; funnel still observes it).
     * {@code direct} optionally attributes the hit through another entity (projectiles).
     */
    void dealDamage(UUID attacker, UUID target, double amount, DamageKind kind, UUID direct);

    /** Context-aware bridge; legacy/simulation adapters may opt out of synchronous reentry. */
    default void dealDamage(UUID attacker, UUID target, double amount, DamageKind kind, UUID direct, CastContext context) {
        dealDamage(attacker, target, amount, kind, direct);
    }

    /** Direction from origin toward the nearest damageable entity within radius, or null. */
    Vec3 aimAtNearest(UUID owner, Vec3 origin, double radius, UUID exclude);

    /** Damageable entities in a forward arc (melee). */
    List<UUID> targetsInArc(UUID attacker, double range, double halfAngleCos);

    /** Spawns an enemy minion from EnemyData and returns its id. */
    UUID spawnMinion(UUID owner, String enemyId, Vec3 pos);

    /** One snapshot of a telegraph ring (runner re-issues during windup). */
    void ringParticles(UUID anchor, Vec3 center, double radius);

    /** Impact burst. */
    void burstParticles(UUID anchor, Vec3 center);

    /** Vanilla burning state (bridges to the {@code ignite} status). */
    boolean isBurning(UUID entity);

    /** Normalized direction from one entity to another (retaliation aims). */
    Vec3 directionTo(UUID from, UUID to);

    /** Heals through the RPG pool (players) or vanilla (others). */
    void healEntity(UUID target, double amount);

    /** Kinematic move (dash, NPC stepping without pathfinding). */
    void moveEntity(UUID entity, Vec3 dest);

    /** Imparts velocity (leap, knockback). */
    void launch(UUID entity, Vec3 velocity);

    /** Whether the entity is sneaking (condition). */
    boolean isSneaking(UUID entity);

    /** Current health fraction in [0, 1] (conditions). Unknown entities report 1.0. */
    double healthRatio(UUID entity);

    /** Random roll in [0, 1) below p (chance conditions). */
    boolean rollChance(double p);

    /** Vanilla fire ticks on an entity (0 clears). */
    void setFireTicks(UUID entity, int ticks);

    /** Applies a vanilla status effect by id (seconds, amplifier). Unknown ids are ignored. */
    void addEffect(UUID entity, String effect, int seconds, int amplifier);

    /** Removes all vanilla status effects. */
    void cleanseEffects(UUID entity);

    /** Whether the entity carries a vanilla status effect (or engine ignite). */
    boolean hasStatusEffect(UUID entity, String effect);

    /** Cosmetic particles by id at a position. Unknown ids are ignored. */
    void playParticles(String particle, Vec3 pos);

    /** Sound by id at a position. Unknown ids are ignored. */
    void playSound(String sound, Vec3 pos);

    /** Action-bar/chat line to a player. Non-players ignore it. */
    void announce(UUID entity, String text);

    /** Feeds a player (food points, saturation). Non-players ignore it. */
    void feed(UUID entity, int food, float saturation);

    /** Grants vanilla experience points. Non-players ignore it. */
    void grantXp(UUID entity, int points);

    /** Gives mod currency items (pity_shard/craft_material/emerald). Others are ignored. */
    void giveItem(UUID entity, String item, int count);

    /** Drops mod currency items at the anchor's feet. Others are ignored. */
    void dropItemAt(UUID anchor, String item, int count);

    /** Visual-only lightning strike dealing direct damage around the target. */
    void strikeLightning(UUID owner, UUID target, double damage);
    default void strikeLightning(UUID owner, UUID target, double damage, CastContext context) {
        strikeLightning(owner, target, damage);
    }

    /** Explosion without block damage. Power is clamped by the game. */
    void explode(UUID owner, Vec3 pos, double power);
    default void explode(UUID owner, Vec3 pos, double power, CastContext context) {
        explode(owner, pos, power);
    }

    /** Weapon id held in the main hand ("" when none). */
    String heldWeaponId(UUID entity);

    /** Entity type id (e.g. entity.minecraft.zombie). */
    String entityTypeId(UUID entity);

    /** Sky/time flags: "day", "night", "raining". */
    boolean worldFlag(String kind);

    /**
     * Tries to spend a resource (stamina/mana). Returns false when the pool
     * is short; nothing is deducted then. Non-players always succeed.
     */
    boolean tryConsumeResource(UUID owner, String name, double cost);

    /** Current resource level (0-100). Unknown pools report full. */
    double resourceLevel(UUID owner, String name);

    /** Nearest damageable entity within radius, or null. */
    UUID nearestLiving(UUID owner, Vec3 origin, double radius, UUID exclude);

    /**
     * Spawns a steering (homing) projectile. The game retargets it every
     * tick toward {@code target} (may be null = flies straight) until
     * {@code lifetimeTicks} elapses. Hits feed the same ctx pipeline.
     */
    UUID spawnMissile(UUID owner, Vec3 origin, Vec3 direction, double speed, long lifetimeTicks,
            UUID target, CastContext ctx);

    /**
     * Attacker-side damage scaling. Heroic baseline: players hit harder from
     * the start (data: {@code heroic_player_mult}); everyone else 1.0.
     */
    default double powerMultiplier(UUID attacker) {
        return 1.0;
    }

    enum DamageKind {
        MELEE,
        PROJECTILE
    }
}
