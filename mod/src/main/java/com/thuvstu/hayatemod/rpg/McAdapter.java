package com.thuvstu.hayatemod.rpg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.thuvstu.hayatemod.build.PlayerBuilds;
import com.thuvstu.hayatemod.content.ContentHolder;
import com.thuvstu.hayatemod.core.content.model.Models.EnemyData;
import com.thuvstu.hayatemod.core.content.model.Models.WeaponCard;
import com.thuvstu.hayatemod.core.build.WeaponSkillMerger;
import com.thuvstu.hayatemod.core.engine.DamageScope;
import com.thuvstu.hayatemod.core.engine.WeakIdentitySet;
import com.thuvstu.hayatemod.core.engine.CastContext;
import com.thuvstu.hayatemod.core.engine.EffectEngine;
import com.thuvstu.hayatemod.core.engine.Vec3;
import com.thuvstu.hayatemod.core.engine.WorldAdapter;
import com.thuvstu.hayatemod.core.rules.EnemyStats;
import com.thuvstu.hayatemod.item.WeaponStack;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridges {@link EffectEngine} and the game. Owns the engine instance,
 * converts tracked-bolt hits into engine events (cancelling vanilla bolt
 * damage), and replaces weapon melee with engine strikes.
 */
public final class McAdapter implements WorldAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger("hayatemod/rpg");

    private static MinecraftServer server;
    private static EffectEngine engine;
    private static McAdapter instance;
    private static final DamageScope DAMAGE_SCOPE = new DamageScope();
    private static final WeakIdentitySet<LivingEntity> DEATH_HANDLED = new WeakIdentitySet<>();
    private static final Map<UUID, UUID> BOLT_OWNERS = new HashMap<>();
    private static final Map<String, EntityType<? extends Mob>> SPECIES = Map.of(
            "husk", EntityType.HUSK,
            "zombie", EntityType.ZOMBIE,
            "spider", EntityType.SPIDER,
            "blaze", EntityType.BLAZE);

    private McAdapter() {
    }

    public static void init(MinecraftServer srv) {
        server = srv;
        instance = new McAdapter();
        engine = new EffectEngine(ContentHolder.get(), instance);
        DEATH_HANDLED.clear();
        engine.setEventListener(msg -> LOGGER.info("[RPG][FX] {}", msg));
    }

    public static EffectEngine engine() {
        return engine;
    }

    public static WorldAdapter adapter() {
        return instance;
    }

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(McAdapter::onAllowDamage);
        ServerLivingEntityEvents.AFTER_DEATH.register(McAdapter::onAfterDeath);
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, srv) -> onJoin(handler.getPlayer()));
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) -> {
            if (engine != null) {
                engine.clearShield(handler.getPlayer().getUUID());
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (engine != null) {
                // Validate before completing due casts, not after firing from an unequipped weapon.
                for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                    String casting = engine.castingWeapon(player.getUUID());
                    if (!casting.isEmpty()) {
                        var held = WeaponStack.resolve(player.getMainHandItem());
                        if (!player.isAlive() || player.isSpectator() || held == null || !casting.equals(held.id())) {
                            engine.cancelCast(player.getUUID());
                        } else {
                            DifficultyState.markCombat(player);
                        }
                    }
                }
                engine.tick();
                if (s.getTickCount() % 5 == 0) {
                    for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                        com.thuvstu.hayatemod.net.UiServer.sendCombatState(player);
                    }
                }
            }
            pruneBoltOwners();
            fireTimers(s);
            regenPools(s);
            for (ServerLevel level : s.getAllLevels()) {
                tickMissiles(level);
            }
        });
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register(
                (level, player, pos, state, blockEntity) -> {
                    if (player instanceof ServerPlayer sp) {
                        onBlockBreak(sp);
                    }
                });
    }

    /** Per-player stamina/mana pools (mythic parity: 100 max, stamina fast, mana slow). */
    private static final Map<UUID, double[]> POOLS = new HashMap<>();
    private static final double POOL_MAX = 100.0;
    private static final double STAMINA_REGEN = 0.75;
    private static final double MANA_REGEN = 0.1;

    private static double[] pool(UUID id) {
        return POOLS.computeIfAbsent(id, k -> new double[] {POOL_MAX, POOL_MAX});
    }

    private static int poolIndex(String name) {
        if (name.equals("stamina")) {
            return 0;
        }
        return 1;
    }

    private static void regenPools(MinecraftServer s) {
        for (ServerLevel level : s.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                if (player.isCreative() || player.isSpectator()) {
                    continue;
                }
                double[] p = pool(player.getUUID());
                p[0] = Math.min(POOL_MAX, p[0] + STAMINA_REGEN);
                p[1] = Math.min(POOL_MAX, p[1] + MANA_REGEN);
            }
        }
    }

    /** Steering missiles: bolt -> lock target + expiry. */
    private record MissileLock(UUID target, double speed, long untilTick) {
    }

    private static final Map<UUID, MissileLock> MISSILES = new HashMap<>();

    private static void tickMissiles(ServerLevel level) {
        long now = level.getGameTime();
        var it = MISSILES.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            Entity bolt = level.getEntity(entry.getKey());
            MissileLock lock = entry.getValue();
            if (!(bolt instanceof SmallFireball) || now > lock.untilTick()) {
                if (bolt != null) {
                    bolt.discard();
                }
                if (engine != null) {
                    engine.forgetProjectile(entry.getKey());
                }
                BOLT_OWNERS.remove(entry.getKey());
                it.remove();
                continue;
            }
            Entity target = lock.target() != null ? level.getEntity(lock.target()) : null;
            if (!(target instanceof LivingEntity living) || !living.isAlive()) {
                continue;
            }
            net.minecraft.world.phys.Vec3 want = living.position().add(0, 1.0, 0)
                    .subtract(bolt.position()).normalize().scale(lock.speed());
            net.minecraft.world.phys.Vec3 cur = bolt.getDeltaMovement();
            net.minecraft.world.phys.Vec3 next = new net.minecraft.world.phys.Vec3(
                    cur.x + (want.x - cur.x) * 0.3,
                    cur.y + (want.y - cur.y) * 0.3,
                    cur.z + (want.z - cur.z) * 0.3);
            if (next.length() < 1e-6) {
                next = want;
            }
            bolt.setDeltaMovement(next.normalize().scale(lock.speed()));
        }
    }
    /** Per-second aura ticks for held weapons with on_timer effects. */
    private static void fireTimers(MinecraftServer s) {
        if (engine == null || s.getTickCount() % 20 != 0) {
            return;
        }
        for (ServerLevel level : s.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                if (player.isCreative() || player.isSpectator()) {
                    continue;
                }
                var card = WeaponStack.resolve(player.getMainHandItem());
                if (card != null && EffectEngine.hasTrigger(card, "on_timer")) {
                    engine.onTimer(card, player.getUUID(),
                            PlayerBuilds.mods(player.getUUID()));
                }
            }
        }
    }

    /** Join buff: fires the joining weapon's on_join effects once each. */
    private static void onJoin(ServerPlayer player) {
        if (engine == null || player.isCreative() || player.isSpectator()) {
            return;
        }
        var card = WeaponStack.resolve(player.getMainHandItem());
        if (card != null && EffectEngine.hasTrigger(card, "on_join")) {
            engine.onJoin(card, player.getUUID(), PlayerBuilds.mods(player.getUUID()));
        }
    }

    private static void onBlockBreak(ServerPlayer player) {
        if (engine == null || player.isCreative() || player.isSpectator()) {
            return;
        }
        var card = WeaponStack.resolve(player.getMainHandItem());
        if (card != null && EffectEngine.hasTrigger(card, "on_break")) {
            engine.onBreak(card, player.getUUID(), PlayerBuilds.mods(player.getUUID()));
        }
    }

    private static final java.util.Set<String> WILD_HOSTILES = java.util.Set.of(
            "entity.minecraft.zombie", "entity.minecraft.husk", "entity.minecraft.drowned",
            "entity.minecraft.skeleton", "entity.minecraft.stray", "entity.minecraft.bogged",
            "entity.minecraft.spider", "entity.minecraft.cave_spider", "entity.minecraft.creeper",
            "entity.minecraft.blaze");

    /**
     * Wild vanilla hostile scaling (idempotent via tag). Called from the field
     * tick for loaded mobs, since this Fabric version has no entity-load event.
     */
    public static void scaleWildHostile(LivingEntity entity) {
        for (String tag : entity.getTags()) {
            if (tag.startsWith("solommo_")) {
                return;
            }
        }
        if (!WILD_HOSTILES.contains(entity.getType().getDescriptionId())) {
            return;
        }
        if (entity.getTags().contains("solommo_wild_scaled")) {
            return;
        }
        entity.addTag("solommo_wild_scaled");
        var tuning = ContentHolder.ready() ? ContentHolder.get().tuning() : null;
        var rules = DifficultyState.resolveWild(entity.getType().getDescriptionId()
                .substring(entity.getType().getDescriptionId().lastIndexOf('.') + 1));
        DifficultyState.bind(entity, rules);
        double mult = (tuning != null ? tuning.wildHpMult() : 2.0) * rules.hp();
        var attr = entity.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(attr.getBaseValue() * mult);
        }
    }

    private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (amount > 0) {
            if (entity instanceof ServerPlayer player) DifficultyState.markCombat(player);
            if (source.getEntity() instanceof ServerPlayer player) DifficultyState.markCombat(player);
        }
        if (DAMAGE_SCOPE.appliesTo(entity.getUUID())) {
            return finishIncomingDamage(entity, source, amount, DAMAGE_SCOPE.currentFor(entity.getUUID()));
        }
        if (entity.getTags().contains("solommo_boss")) {
            LOGGER.info("[RPG][BOSS-HIT] src={} amount={} hp-before={}", source.getMsgId(), amount,
                    String.format("%.0f", entity.getHealth()));
        }
        Entity direct = source.getDirectEntity();
        if (direct instanceof SmallFireball bolt && engine != null
                && engine.isTracking(bolt.getUUID())) {
            engine.onProjectileHit(bolt.getUUID(), entity.getUUID());
            BOLT_OWNERS.remove(bolt.getUUID());
            bolt.discard();
            return false;
        }
        Entity attacker = source.getEntity();
        if (attacker instanceof ServerPlayer player && engine != null && entity != player) {
            var held = player.getMainHandItem();
            var card = WeaponStack.resolve(held);
            if (card != null) {
                RpgSkills.meleeWith(player, card);
                return false;
            }
        }
        if (attacker instanceof LivingEntity mob && !(attacker instanceof ServerPlayer)
                && !mob.getTags().contains("solommo_summoned")) {
            double mult = DifficultyState.snapshot(mob).dps();
            if (entity instanceof ServerPlayer && !mob.getTags().contains("solommo_session")
                    && WILD_HOSTILES.contains(mob.getType().getDescriptionId())) {
                var tuning = ContentHolder.ready() ? ContentHolder.get().tuning() : null;
                mult *= tuning != null ? tuning.wildDamageMult() : 1.5;
            }
            if (mult != 1.0) {
                float scaled = amount * (float) mult;
                DAMAGE_SCOPE.run(entity.getUUID(), null, () -> entity.hurtServer(levelOf(entity), source, scaled));
                return false;
            }
        }
        return finishIncomingDamage(entity, source, amount, null);
    }

    private static WeaponCard passiveCard(ServerPlayer player) {
        var card = WeaponStack.resolve(player.getMainHandItem());
        return card == null ? null : WeaponSkillMerger.withPassiveRunes(card, PlayerBuilds.runeEffects(player));
    }

    private static boolean finishIncomingDamage(LivingEntity entity, DamageSource source, float amount, CastContext cause) {
        if (amount <= 0) return true;
        if (entity instanceof ServerPlayer victim && engine != null) {
            var card = passiveCard(victim);
            double effective = engine.damageAfterShield(victim.getUUID(), amount);
            if (card != null && effective > 0 && EffectEngine.hasTrigger(card, "on_damaged")
                    && RpgHealth.get(victim.getUUID()) - effective > 0) {
                Entity src = source.getEntity();
                engine.onDamaged(card, victim.getUUID(),
                        cause != null ? cause.owner() : src != null ? src.getUUID() : victim.getUUID(),
                        PlayerBuilds.mods(victim.getUUID()), cause);
            }
        }
        if (entity instanceof ServerPlayer && !entity.isAlive()) return false;
        if (entity instanceof ServerPlayer victim && engine != null
                && RpgHealth.get(victim.getUUID()) - engine.damageAfterShield(victim.getUUID(), amount) <= 0 && trySecondWind(victim)) {
            return false;
        }
        return true;
    }

    /** Totem-style second wind: lethal RPG-pool hits restore once per 10 minutes. */
    private static final Map<UUID, Long> LAST_WIND = new HashMap<>();
    private static final long WIND_COOLDOWN_TICKS = 12000L;

    private static boolean trySecondWind(ServerPlayer victim) {
        if (server == null) {
            return false;
        }
        long now = server.overworld().getGameTime();
        Long last = LAST_WIND.get(victim.getUUID());
        if (last != null && now - last < WIND_COOLDOWN_TICKS) {
            return false;
        }
        LAST_WIND.put(victim.getUUID(), now);
        RpgHealth.restore(victim);
        victim.setRemainingFireTicks(0);
        victim.sendSystemMessage(Component.literal("[不屈] セカンドウィンド！ 立ち上がった"), true);
        ServerLevel level = levelOf(victim);
        victim.level().playSound(null, victim.blockPosition(),
                net.minecraft.sounds.SoundEvents.TOTEM_USE,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                victim.getX(), victim.getY() + 1.0, victim.getZ(), 40, 0.5, 0.5, 0.5, 0.2);
        LOGGER.info("[RPG][WIND] second wind for {}", victim.getScoreboardName());
        return true;
    }

    private static void onAfterDeath(LivingEntity entity, DamageSource source) {
        if (engine == null || entity.level().isClientSide() || !DEATH_HANDLED.add(entity)) return;
        CastContext cause = DAMAGE_SCOPE.currentFor(entity.getUUID());
        ServerPlayer attacker = cause != null && findStatic(cause.owner()) instanceof ServerPlayer p ? p : resolveKiller(source);
        var held = entity instanceof ServerPlayer victim ? passiveCard(victim) : null;
        if (entity instanceof ServerPlayer) engine.clearShield(entity.getUUID());
        // Preserve kill-before-death ordering, but environmental/non-player kills must not suppress on_death.
        if (attacker != null) {
            var card = cause != null && cause.snapshot() != null ? cause.snapshot() : passiveCard(attacker);
            if (card != null) {
                engine.onKill(card, attacker.getUUID(), entity.getUUID(),
                        cause != null ? cause.mods() : PlayerBuilds.mods(attacker.getUUID()), cause);
            }
            if (entity.getTags().contains("solommo_enemy:solommo:pyre_watcher")) {
                com.thuvstu.hayatemod.progress.AdvancementHelper.grant(attacker, "watcher_down");
                PlayerBuilds.grantSp(attacker.getUUID(), 1);
                attacker.sendSystemMessage(Component.literal("[solommo] スキルポイント+1"), true);
            }
        }
        if (entity instanceof ServerPlayer victim && held != null) {
            UUID killer = cause != null ? cause.owner() : source.getEntity() != null ? source.getEntity().getUUID() : victim.getUUID();
            engine.onDeath(held, victim.getUUID(), killer, PlayerBuilds.mods(victim.getUUID()), cause);
        }
    }

    private static ServerPlayer resolveKiller(DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer p) {
            return p;
        }
        Entity direct = source.getDirectEntity();
        if (direct != null) {
            UUID owner = BOLT_OWNERS.get(direct.getUUID());
            if (owner != null) {
                Entity e = findStatic(owner);
                if (e instanceof ServerPlayer p) {
                    return p;
                }
            }
        }
        return null;
    }

    private static void pruneBoltOwners() {
        BOLT_OWNERS.keySet().removeIf(id -> findStatic(id) == null);
    }

    private static Entity findStatic(UUID id) {
        if (server == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(id);
            if (e != null) {
                return e;
            }
        }
        return null;
    }

    // ---- WorldAdapter ----

    @Override
    public long gameTime() {
        return server.getTickCount();
    }

    @Override
    public Vec3 eyePos(UUID entity) {
        return toCore(require(entity).getEyePosition());
    }

    @Override
    public Vec3 facing(UUID entity) {
        return toCore(require(entity).getLookAngle());
    }

    @Override
    public Vec3 pos(UUID entity) {
        return toCore(require(entity).position());
    }
    @Override
    public UUID spawnBolt(UUID owner, Vec3 origin, Vec3 direction, double speed, CastContext ctx) {
        Entity ownerEntity = require(owner);
        if (!(ownerEntity instanceof LivingEntity living)) {
            throw new IllegalStateException("bolt owner is not living: " + owner);
        }
        ServerLevel level = levelOf(ownerEntity);
        net.minecraft.world.phys.Vec3 dir =
                new net.minecraft.world.phys.Vec3(direction.x(), direction.y(), direction.z())
                        .normalize().scale(speed);
        SmallFireball bolt = new SmallFireball(level, living, dir);
        bolt.setPos(origin.x(), origin.y(), origin.z());
        bolt.addTag("solommo_session");
        level.addFreshEntity(bolt);
        BOLT_OWNERS.put(bolt.getUUID(), owner);
        return bolt.getUUID();
    }

    @Override
    public void dealDamage(UUID attacker, UUID target, double amount, DamageKind kind, UUID direct) {
        dealDamage(attacker, target, amount, kind, direct, DAMAGE_SCOPE.current());
    }

    @Override
    public void dealDamage(UUID attacker, UUID target, double amount, DamageKind kind, UUID direct, CastContext context) {
        Entity a = find(attacker);
        Entity t = find(target);
        if (!(t instanceof LivingEntity victim)) {
            return;
        }
        ServerLevel level = levelOf(t);
        DamageSource src;
        if (kind == DamageKind.MELEE && a instanceof Player p) {
            src = level.damageSources().playerAttack(p);
        } else if (direct != null) {
            Entity d = find(direct);
            Entity atk = a != null ? a : t;
            src = level.damageSources().indirectMagic(d != null ? d : atk, atk);
        } else {
            src = level.damageSources().generic();
        }
        double scaled = a != null && !(a instanceof Player) ? amount * DifficultyState.snapshot(a).dps() : amount;
        DAMAGE_SCOPE.run(target, context, () -> victim.hurtServer(level, src, (float) scaled));
    }

    @Override
    public UUID spawnMinion(UUID owner, String enemyId, Vec3 pos) {
        Entity ownerEntity = require(owner);
        ServerLevel level = levelOf(ownerEntity);
        return spawnEnemy(level, enemyId,
                new BlockPos((int) Math.floor(pos.x()), (int) Math.floor(pos.y()),
                        (int) Math.floor(pos.z())),
                true, true);
    }

    /** Debug/test spawn with loot binding (not summoned → drops apply). */
    public static UUID spawnDebugEnemy(ServerPlayer player, String enemyId) {
        net.minecraft.world.phys.Vec3 spot =
                player.position().add(player.getLookAngle().scale(3.0));
        return spawnEnemy(player.level(), enemyId, BlockPos.containing(spot), false, true);
    }

    /** Wild field spawn: loot binding, no session tag (survives arena sweeps). */
    public static UUID spawnWild(ServerLevel level, String enemyId, BlockPos pos) {
        UUID id = spawnEnemy(level, enemyId, pos, false, false);
        Entity e = level.getEntity(id);
        if (e != null) {
            e.addTag("solommo_wild");
        }
        return id;
    }

    private static UUID spawnEnemy(ServerLevel level, String enemyId, BlockPos pos, boolean summoned,
            boolean sessionTag) {
        if (!ContentHolder.ready()) {
            throw new IllegalStateException("content not loaded");
        }
        EnemyData data = ContentHolder.get().enemies().get(enemyId);
        if (data == null) {
            throw new IllegalArgumentException("unknown enemy '" + enemyId + "'");
        }
        var tuning = ContentHolder.get().tuning();
        var ref = ContentHolder.get().reference();
        var rules = summoned ? com.thuvstu.hayatemod.core.rules.RuleResolver.Multipliers.identity()
                : DifficultyState.resolve(data);
        EnemyStats.DerivedStats stats = EnemyStats.derive(data.level(), data.rank(), ref,
                tuning != null ? tuning.rankTargets() : Map.of(), rules.hp(), rules.dps());
        EntityType<? extends Mob> type = SPECIES.getOrDefault(data.species(), EntityType.ZOMBIE);
        Mob mob = spawnTyped(type, level, m -> {
            Objects.requireNonNull(m.getAttribute(Attributes.MAX_HEALTH))
                    .setBaseValue(stats.maxHp());
            m.setHealth((float) stats.maxHp());
            m.setCustomName(Component.literal(data.name() + " Lv" + data.level()));
            m.setCustomNameVisible(true);
            m.setPersistenceRequired();
            if (sessionTag) {
                m.addTag("solommo_session");
            }
            m.addTag("solommo_enemy:" + enemyId);
            DifficultyState.bind(m, rules);
            if (summoned) {
                m.addTag("solommo_summoned");
            }
        }, pos);
        LOGGER.info("[RPG][SPAWN] {} hp={} summoned={}", enemyId, (long) stats.maxHp(), summoned);
        return mob.getUUID();
    }

    private static <T extends Mob> T spawnTyped(EntityType<T> type, ServerLevel level,
            java.util.function.Consumer<T> setup, BlockPos pos) {
        return type.spawn(level, setup, pos, EntitySpawnReason.MOB_SUMMONED, false, false);
    }

    @Override
    public void ringParticles(UUID anchor, Vec3 center, double radius) {
        Entity a = find(anchor);
        if (!(a instanceof LivingEntity living)) {
            return;
        }
        ServerLevel level = levelOf(living);
        double y = center.y() + 0.15;
        for (int i = 0; i < 24; i++) {
            double ang = (Math.PI * 2.0 * i) / 24.0;
            level.sendParticles(ParticleTypes.FLAME, center.x() + Math.cos(ang) * radius, y,
                    center.z() + Math.sin(ang) * radius, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    @Override
    public void burstParticles(UUID anchor, Vec3 center) {
        Entity a = find(anchor);
        if (!(a instanceof LivingEntity living)) {
            return;
        }
        ServerLevel level = levelOf(living);
        for (int i = 0; i < 40; i++) {
            double ang = level.random.nextDouble() * Math.PI * 2.0;
            level.sendParticles(ParticleTypes.LAVA, center.x(), center.y() + 0.3, center.z(), 1,
                    Math.cos(ang) * 2.0, 1.5, Math.sin(ang) * 2.0, 0.2);
        }
    }

    @Override
    public Vec3 aimAtNearest(UUID owner, Vec3 origin, double radius, UUID exclude) {
        Entity ownerEntity = find(owner);
        if (ownerEntity == null) {
            return null;
        }
        ServerLevel level = levelOf(ownerEntity);
        Entity best = null;
        double bestDist = radius;
        for (LivingEntity e : level.getEntities(EntityTypeTest.forClass(LivingEntity.class),
                e -> e.isAlive() && !e.getUUID().equals(owner) && !e.getUUID().equals(exclude)
                        && !(e instanceof ServerPlayer p && (p.isCreative() || p.isSpectator())))) {
            double d = e.position().distanceTo(new net.minecraft.world.phys.Vec3(origin.x(), origin.y(), origin.z()));
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        if (best == null) {
            return null;
        }
        net.minecraft.world.phys.Vec3 delta = best.position().add(0, 1.0, 0).subtract(new net.minecraft.world.phys.Vec3(origin.x(), origin.y(), origin.z()));
        if (delta.length() < 1e-6) {
            return null;
        }
        return toCore(delta.normalize());
    }

    @Override
    public List<UUID> targetsInArc(UUID attacker, double range, double halfAngleCos) {
        Entity a = find(attacker);
        if (a == null) {
            return List.of();
        }
        ServerLevel level = levelOf(a);
        net.minecraft.world.phys.Vec3 facing = a.getLookAngle();
        net.minecraft.world.phys.Vec3 center = a.position();
        List<UUID> out = new ArrayList<>();
        for (LivingEntity e : level.getEntities(EntityTypeTest.forClass(LivingEntity.class),
                e -> e.isAlive() && !e.getUUID().equals(attacker)
                        && !(e instanceof ServerPlayer p && (p.isCreative() || p.isSpectator())))) {
            net.minecraft.world.phys.Vec3 to = e.position().subtract(center);
            double dist = to.length();
            if (dist > range) {
                continue;
            }
            if (dist < 1e-6 || to.normalize().dot(facing) >= halfAngleCos) {
                out.add(e.getUUID());
            }
        }
        return out;
    }

    @Override
    public boolean isBurning(UUID entity) {
        Entity e = find(entity);
        return e != null && e.isOnFire();
    }

    @Override
    public Vec3 directionTo(UUID from, UUID to) {
        Entity a = find(from);
        Entity b = find(to);
        if (a == null || b == null) {
            return new Vec3(1, 0, 0);
        }
        net.minecraft.world.phys.Vec3 d = b.position().subtract(a.position());
        d = new net.minecraft.world.phys.Vec3(d.x, 0, d.z);
        if (d.length() < 1e-6) {
            return new Vec3(1, 0, 0);
        }
        return toCore(d.normalize());
    }

    @Override
    public void healEntity(UUID target, double amount) {
        Entity e = find(target);
        if (e instanceof ServerPlayer sp) {
            RpgHealth.healHook(sp, (float) amount);
        } else if (e instanceof LivingEntity living) {
            living.heal((float) amount);
        }
    }

    @Override
    public void moveEntity(UUID entity, Vec3 dest) {
        Entity e = find(entity);
        if (e != null) {
            e.teleportTo(dest.x(), dest.y(), dest.z());
        }
    }

    @Override
    public void launch(UUID entity, Vec3 velocity) {
        Entity e = find(entity);
        if (e != null) {
            e.setDeltaMovement(e.getDeltaMovement().add(velocity.x(), velocity.y(), velocity.z()));
            if (e instanceof ServerPlayer sp) {
                sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(
                        e.getId(), e.getDeltaMovement()));
            }
        }
    }

    @Override
    public boolean isSneaking(UUID entity) {
        Entity e = find(entity);
        return e != null && e.isShiftKeyDown();
    }

    @Override
    public double healthRatio(UUID entity) {
        Entity e = find(entity);
        if (e instanceof ServerPlayer sp) {
            double max = RpgHealth.maxHp();
            return max > 0 ? RpgHealth.get(sp.getUUID()) / max : 1.0;
        }
        if (e instanceof LivingEntity living) {
            float max = living.getMaxHealth();
            return max > 0 ? living.getHealth() / max : 1.0;
        }
        return 1.0;
    }

    @Override
    public boolean tryConsumeResource(UUID owner, String name, double cost) {
        if (!name.equals("stamina") && !name.equals("mana")) {
            return true;
        }
        Entity e = find(owner);
        if (!(e instanceof ServerPlayer)) {
            return true;
        }
        if (cost <= 0) {
            return true;
        }
        double[] p = pool(owner);
        int i = poolIndex(name);
        if (p[i] < cost) {
            return false;
        }
        p[i] -= cost;
        return true;
    }

    @Override
    public double resourceLevel(UUID owner, String name) {
        if (!name.equals("stamina") && !name.equals("mana")) {
            return 0.0;
        }
        Entity e = find(owner);
        if (!(e instanceof ServerPlayer)) {
            return POOL_MAX;
        }
        return pool(owner)[poolIndex(name)];
    }

    @Override
    public UUID nearestLiving(UUID owner, Vec3 origin, double radius, UUID exclude) {
        Entity ownerEntity = find(owner);
        if (ownerEntity == null) {
            return null;
        }
        ServerLevel level = levelOf(ownerEntity);
        net.minecraft.world.phys.Vec3 at =
                new net.minecraft.world.phys.Vec3(origin.x(), origin.y(), origin.z());
        Entity best = null;
        double bestDist = radius;
        for (LivingEntity e : level.getEntities(EntityTypeTest.forClass(LivingEntity.class),
                e -> e.isAlive() && !e.getUUID().equals(owner) && !e.getUUID().equals(exclude)
                        && !(e instanceof ServerPlayer p
                                && (p.isCreative() || p.isSpectator())))) {
            double d = e.position().distanceTo(at);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best != null ? best.getUUID() : null;
    }

    @Override
    public UUID spawnMissile(UUID owner, Vec3 origin, Vec3 direction, double speed,
            long lifetimeTicks, UUID target, CastContext ctx) {
        Entity ownerEntity = find(owner);
        if (!(ownerEntity instanceof LivingEntity living)) {
            throw new IllegalStateException("missile owner is not living: " + owner);
        }
        ServerLevel level = levelOf(ownerEntity);
        net.minecraft.world.phys.Vec3 dir =
                new net.minecraft.world.phys.Vec3(direction.x(), direction.y(), direction.z())
                        .normalize().scale(speed);
        SmallFireball bolt = new SmallFireball(level, living, dir);
        bolt.setPos(origin.x(), origin.y(), origin.z());
        bolt.addTag("solommo_session");
        bolt.addTag("solommo_missile");
        level.addFreshEntity(bolt);
        BOLT_OWNERS.put(bolt.getUUID(), owner);
        MISSILES.put(bolt.getUUID(),
                new MissileLock(target, speed, level.getGameTime() + lifetimeTicks));
        return bolt.getUUID();
    }

    @Override
    public boolean rollChance(double p) {
        if (p <= 0.0) {
            return false;
        }
        if (p >= 1.0) {
            return true;
        }
        return server.overworld().getRandom().nextDouble() < p;
    }

    // ---- wave-2 game services (mirrors core.validate.Sets; unknown ids ignored) ----

    private static final java.util.Map<String, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> EFFECTS =
            Map.ofEntries(
                    Map.entry("speed", net.minecraft.world.effect.MobEffects.SPEED),
                    Map.entry("slowness", net.minecraft.world.effect.MobEffects.SLOWNESS),
                    Map.entry("haste", net.minecraft.world.effect.MobEffects.HASTE),
                    Map.entry("strength", net.minecraft.world.effect.MobEffects.STRENGTH),
                    Map.entry("regeneration", net.minecraft.world.effect.MobEffects.REGENERATION),
                    Map.entry("resistance", net.minecraft.world.effect.MobEffects.RESISTANCE),
                    Map.entry("fire_resistance", net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE),
                    Map.entry("weakness", net.minecraft.world.effect.MobEffects.WEAKNESS),
                    Map.entry("poison", net.minecraft.world.effect.MobEffects.POISON),
                    Map.entry("absorption", net.minecraft.world.effect.MobEffects.ABSORPTION),
                    Map.entry("glowing", net.minecraft.world.effect.MobEffects.GLOWING),
                    Map.entry("night_vision", net.minecraft.world.effect.MobEffects.NIGHT_VISION));

    private static final java.util.Map<String, net.minecraft.core.particles.ParticleOptions> PARTICLES =
            Map.ofEntries(
                    Map.entry("flame", ParticleTypes.FLAME),
                    Map.entry("soul_fire_flame", ParticleTypes.SOUL_FIRE_FLAME),
                    Map.entry("enchant", ParticleTypes.ENCHANT),
                    Map.entry("crit", ParticleTypes.CRIT),
                    Map.entry("cloud", ParticleTypes.CLOUD),
                    Map.entry("lava", ParticleTypes.LAVA),
                    Map.entry("portal", ParticleTypes.PORTAL),
                    Map.entry("witch", ParticleTypes.WITCH),
                    Map.entry("note", ParticleTypes.NOTE),
                    Map.entry("heart", ParticleTypes.HEART),
                    Map.entry("smoke", ParticleTypes.SMOKE),
                    Map.entry("explosion", ParticleTypes.EXPLOSION));

    private static net.minecraft.sounds.SoundEvent soundOf(String id) {
        return switch (id) {
            case "entity_player_levelup" -> net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP;
            case "entity_generic_explode" ->
                    net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value();
            case "entity_lightning_bolt_thunder" ->
                    net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER;
            case "entity_enderman_teleport" -> net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT;
            case "block_anvil_land" -> net.minecraft.sounds.SoundEvents.ANVIL_LAND;
            case "entity_firework_rocket_launch" ->
                    net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LAUNCH;
            case "block_beacon_activate" -> net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE;
            case "item_firecharge_use" -> net.minecraft.sounds.SoundEvents.FIRECHARGE_USE;
            default -> null;
        };
    }

    private static net.minecraft.world.item.Item giftItem(String id) {
        return switch (id) {
            case "craft_material" -> com.thuvstu.hayatemod.item.ModItems.CRAFT_MATERIAL;
            case "emerald" -> net.minecraft.world.item.Items.EMERALD;
            default -> null;
        };
    }

    @Override
    public void setFireTicks(UUID entity, int ticks) {
        Entity e = find(entity);
        if (e != null) {
            e.setRemainingFireTicks(Math.max(0, ticks));
        }
    }

    @Override
    public void addEffect(UUID entity, String effect, int seconds, int amplifier) {
        Entity e = find(entity);
        var holder = EFFECTS.get(effect);
        if (e instanceof LivingEntity living && holder != null) {
            living.addEffect(new net.minecraft.world.effect.MobEffectInstance(holder, seconds * 20,
                    Math.max(0, amplifier)));
        }
    }

    @Override
    public void cleanseEffects(UUID entity) {
        Entity e = find(entity);
        if (e instanceof LivingEntity living) {
            living.removeAllEffects();
        }
    }

    @Override
    public boolean hasStatusEffect(UUID entity, String effect) {
        Entity e = find(entity);
        if (!(e instanceof LivingEntity living)) {
            return false;
        }
        var holder = EFFECTS.get(effect);
        if (holder != null && living.hasEffect(holder)) {
            return true;
        }
        return effect.equals("ignite") && living.isOnFire();
    }

    @Override
    public void playParticles(String particle, Vec3 pos) {
        var type = PARTICLES.get(particle);
        if (type == null || server == null) {
            return;
        }
        ServerLevel level = server.overworld();
        level.sendParticles(type, pos.x(), pos.y() + 1.0, pos.z(), 12, 0.5, 0.5, 0.5, 0.05);
    }

    @Override
    public void playSound(String sound, Vec3 pos) {
        var event = soundOf(sound);
        if (event == null || server == null) {
            return;
        }
        ServerLevel level = server.overworld();
        level.playSound(null, pos.x(), pos.y(), pos.z(), event,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    @Override
    public void announce(UUID entity, String text) {
        Entity e = find(entity);
        if (e instanceof ServerPlayer sp) {
            sp.sendSystemMessage(Component.literal(text), true);
        }
    }

    @Override
    public void feed(UUID entity, int food, float saturation) {
        Entity e = find(entity);
        if (e instanceof ServerPlayer sp) {
            sp.getFoodData().eat(food, Math.max(0, saturation));
        }
    }

    @Override
    public void grantXp(UUID entity, int points) {
        Entity e = find(entity);
        if (e instanceof ServerPlayer sp && points > 0) {
            sp.giveExperiencePoints(points);
        }
    }

    @Override
    public void giveItem(UUID entity, String item, int count) {
        Entity e = find(entity);
        net.minecraft.world.item.Item gift = giftItem(item);
        if (!(e instanceof ServerPlayer sp) || gift == null || count <= 0) {
            return;
        }
        net.minecraft.world.item.ItemStack rest =
                new net.minecraft.world.item.ItemStack(gift, count);
        sp.getInventory().add(rest);
        if (!rest.isEmpty()) {
            sp.drop(rest, false);
        }
    }

    @Override
    public void dropItemAt(UUID anchor, String item, int count) {
        Entity e = find(anchor);
        net.minecraft.world.item.Item gift = giftItem(item);
        if (e == null || gift == null || count <= 0) {
            return;
        }
        ServerLevel level = levelOf(e);
        net.minecraft.world.phys.Vec3 p = e.position();
        level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, p.x,
                p.y + 0.5, p.z, new net.minecraft.world.item.ItemStack(gift, count)));
    }

    @Override
    public void strikeLightning(UUID owner, UUID target, double damage, CastContext context) {
        DAMAGE_SCOPE.run(target, context, () -> strikeLightning(owner, target, damage));
    }

    @Override
    public void strikeLightning(UUID owner, UUID target, double damage) {
        Entity t = find(target);
        if (t == null) {
            return;
        }
        ServerLevel level = levelOf(t);
        net.minecraft.world.phys.Vec3 p = t.position();
        net.minecraft.world.entity.LightningBolt bolt = new net.minecraft.world.entity.LightningBolt(
                EntityType.LIGHTNING_BOLT, level);
        bolt.setPos(p.x, p.y, p.z);
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
        dealDamage(owner, target, damage, DamageKind.MELEE, null);
    }

    @Override
    public void explode(UUID owner, Vec3 pos, double power, CastContext context) {
        DAMAGE_SCOPE.run(null, context, () -> explode(owner, pos, power));
    }

    @Override
    public void explode(UUID owner, Vec3 pos, double power) {
        if (server == null) {
            return;
        }
        Entity ownerEntity = find(owner);
        if (ownerEntity == null) return;
        ServerLevel level = levelOf(ownerEntity);
        double clamped = Math.min(4.0, Math.max(1.0, power));
        // Never call vanilla explode here: it also deals unattributed damage, duplicating our hits.
        level.sendParticles(ParticleTypes.EXPLOSION, pos.x(), pos.y(), pos.z(), 1, 0, 0, 0, 0);
        level.playSound(null, BlockPos.containing(pos.x(), pos.y(), pos.z()),
                net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
        for (LivingEntity e : level.getEntities(EntityTypeTest.forClass(LivingEntity.class),
                e -> e.isAlive() && !e.getUUID().equals(owner)
                        && !(e instanceof ServerPlayer p
                                && (p.isCreative() || p.isSpectator()))).stream()
                        .sorted(java.util.Comparator.comparing(Entity::getUUID)).toList()) {
            if (e.position().distanceTo(
                    new net.minecraft.world.phys.Vec3(pos.x(), pos.y(), pos.z())) <= clamped * 2.0) {
                var away = e.position().subtract(pos.x(), pos.y(), pos.z()).normalize().scale(clamped * .4);
                launch(e.getUUID(), new Vec3(away.x, .2, away.z));
                dealDamage(owner, e.getUUID(), clamped * 2.0, DamageKind.MELEE, null);
            }
        }
        if (ownerEntity != null) {
            LOGGER.info("[RPG][FX] explosion power {} at {}", clamped, pos);
        }
    }

    @Override
    public String heldWeaponId(UUID entity) {
        Entity e = find(entity);
        if (e instanceof ServerPlayer sp) {
            String id = sp.getMainHandItem().get(
                    com.thuvstu.hayatemod.item.ModItems.WEAPON_ID);
            return id != null ? id : "";
        }
        return "";
    }

    @Override
    public String entityTypeId(UUID entity) {
        Entity e = find(entity);
        return e != null ? e.getType().getDescriptionId() : "";
    }

    @Override
    public boolean worldFlag(String kind) {
        if (server == null) {
            return false;
        }
        ServerLevel level = server.overworld();
        return switch (kind) {
            case "day" -> level.getDayTime() % 24000L < 12000L;
            case "night" -> level.getDayTime() % 24000L >= 12000L;
            case "raining" -> level.isRaining();
            default -> false;
        };
    }

    @Override
    public double powerMultiplier(UUID attacker) {
        Entity e = find(attacker);
        if (!(e instanceof ServerPlayer)) {
            return 1.0;
        }
        var tuning = ContentHolder.ready() ? ContentHolder.get().tuning() : null;
        return tuning != null ? tuning.heroicPlayerMult() : 10.0;
    }

    // ---- helpers ----

    private Entity find(UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(id);
            if (e != null) {
                return e;
            }
        }
        return null;
    }

    private Entity require(UUID id) {
        Entity e = find(id);
        if (e == null) {
            throw new IllegalStateException("entity not found: " + id);
        }
        return e;
    }

    private static ServerLevel levelOf(Entity e) {
        if (e.level() instanceof ServerLevel sl) {
            return sl;
        }
        throw new IllegalStateException("entity not on server level: " + e);
    }

    private static Vec3 toCore(net.minecraft.world.phys.Vec3 v) {
        return new Vec3(v.x, v.y, v.z);
    }
}
