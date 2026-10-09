package com.thuvstu.hayatemod.dungeon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.thuvstu.hayatemod.codex.CodexStore;
import com.thuvstu.hayatemod.content.ContentHolder;
import com.thuvstu.hayatemod.core.content.model.Models.AbilityDef;
import com.thuvstu.hayatemod.core.content.model.Models.EnemyData;
import com.thuvstu.hayatemod.core.content.model.Models.EncounterData;
import com.thuvstu.hayatemod.core.content.model.Models.PhaseDef;
import com.thuvstu.hayatemod.core.content.model.Models.SkillDef;
import com.thuvstu.hayatemod.core.content.model.Models.TimelineEntry;
import com.thuvstu.hayatemod.core.content.model.Models.WeaponCard;
import com.thuvstu.hayatemod.core.rules.EnemyStats;
import com.thuvstu.hayatemod.core.rules.PhaseLogic;
import com.thuvstu.hayatemod.item.WeaponStack;
import com.thuvstu.hayatemod.net.UiServer;
import com.thuvstu.hayatemod.rpg.DropHooks;
import com.thuvstu.hayatemod.rpg.McAdapter;
import com.thuvstu.hayatemod.rpg.RpgHealth;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single-session encounter runner (Phase 4). Drives the flame_golem timeline:
 * phase resolution, scheduled telegraphs, NPC signals, checkpoints, wipe
 * recovery and boss rewards. Boss ability damage reuses the effect engine
 * through synthetic weapon cards; lethal/split math stays here.
 */
public final class EncounterRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger("hayatemod/dungeon");

    private static boolean active;
    private static UUID playerId;
    private static Vec3 returnPos;
    private static ServerLevel level;
    private static EncounterData encounter;
    private static UUID bossId;
    private static double bossMaxHp;
    private static String phaseId;
    private static long phaseStartTick;
    private static long loopIndex;
    private static final Set<String> FIRED = new HashSet<>();
    private static final List<Windup> WINDUP = new ArrayList<>();
    private static ServerBossEvent bossBar;
    private static final List<String> REACHED = new ArrayList<>();
    private static boolean pendingRevive;
    private static boolean ended;
    private static int wipes;
    private static long startTick;
    private static long interruptReadyTick;
    private static int stressLeft;
    private static long lastToggleTick;

    private EncounterRunner() {
    }

    private record Windup(UUID taskId, AbilityDef ability, Vec3 center, long executeTick, boolean warned) {
    }

    public static boolean isActive() {
        return active;
    }

    static ServerLevel sessionLevel() {
        return active ? level : null;
    }

    static UUID sessionBoss() {
        return active ? bossId : null;
    }

    static boolean isLethalSignal(String signal) {
        return signal.equals("STACK");
    }

    /** Signal test anchor for S3 (stack point, boss, or first safe spot). */
    static Vec3 signalPoint(String signal) {
        if (level == null) {
            return Vec3.ZERO;
        }
        return switch (signal) {
            case "STACK" -> stackPoint();
            case "MOVE_TO_SAFE_SPOT", "AVOID_AREA" -> {
                List<Vec3> spots = safeSpotWorlds();
                yield spots.isEmpty() ? ArenaManager.entryPos() : spots.get(0);
            }
            default -> {
                LivingEntity boss = bossId != null ? EntityTypeTestHelper.living(level, bossId) : null;
                yield boss != null ? boss.position() : ArenaManager.entryPos();
            }
        };
    }

    private static Vec3 stackPoint() {
        Object raw = encounter.arenaMarkers().get("stack_point");
        if (raw == null) {
            var arena = ContentHolder.get().arenas().get(encounter.arenaTemplate());
            if (arena != null) {
                raw = arena.markers().get("stack_point");
            }
        }
        if (raw instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Number) {
            @SuppressWarnings("unchecked")
            List<Number> nums = (List<Number>) list;
            return ArenaManager.markerWorld(nums);
        }
        return ArenaManager.entryPos();
    }

    private static com.thuvstu.hayatemod.core.engine.Vec3 toCore(Vec3 v) {
        return new com.thuvstu.hayatemod.core.engine.Vec3(v.x, v.y, v.z);
    }

    static List<Vec3> safeSpotWorlds() {
        List<Vec3> out = new ArrayList<>();
        if (encounter == null) {
            return out;
        }
        Object raw = encounter.arenaMarkers().get("safe_spots");
        if (raw == null) {
            var arena = ContentHolder.get().arenas().get(encounter.arenaTemplate());
            if (arena != null) {
                raw = arena.markers().get("safe_spots");
            }
        }
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof List<?> point && !point.isEmpty() && point.get(0) instanceof Number) {
                    @SuppressWarnings("unchecked")
                    List<Number> nums = (List<Number>) point;
                    out.add(ArenaManager.markerWorld(nums));
                }
            }
        }
        return out;
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(EncounterRunner::onTick);
        ServerLivingEntityEvents.AFTER_DEATH.register(EncounterRunner::onAfterDeath);
    }

    // ---- session control ----

    public static void enter(ServerPlayer player) {
        enter(player, "solommo:flame_golem");
    }

    public static void enter(ServerPlayer player, String encounterId) {
        if (!ContentHolder.ready() || McAdapter.engine() == null) {
            player.sendSystemMessage(Component.literal("[dungeon] engine not ready"));
            return;
        }
        if (active) {
            player.sendSystemMessage(Component.literal("[dungeon] session already active"));
            return;
        }
        // NPC hire fee (currency sink, §5.14).
        if (WeaponStack.countOf(player.getInventory(),
                net.minecraft.world.item.Items.EMERALD) < 5) {
            player.sendSystemMessage(Component.literal("[dungeon] 雇用費: エメラルド5 (NPCパーティ)"));
            return;
        }
        if (!(player.level() instanceof ServerLevel sl)
                || sl != player.level().getServer().overworld()) {
            player.sendSystemMessage(Component.literal("[dungeon] 酒場（オーバーワールド）から入場してください"));
            return;
        }
        EncounterData enc = ContentHolder.get().encounters().get(encounterId);
        EnemyData foe = enc != null ? ContentHolder.get().enemies().get(encounterId) : null;
        if (enc == null || foe == null) {
            player.sendSystemMessage(Component.literal("[dungeon] unknown encounter '" + encounterId + "'"));
            return;
        }
        level = sl;
        var arenaTpl = ContentHolder.get().arenas().get(enc.arenaTemplate());
        String structureId = arenaTpl != null && arenaTpl.structure() != null
                && !arenaTpl.structure().isEmpty()
                        ? arenaTpl.structure()
                        : "solommo:flame_arena";
        if (!ArenaManager.ensureArena(level, structureId)) {
            player.sendSystemMessage(Component.literal("[dungeon] アリーナ構造が見つかりません: " + structureId));
            level = null;
            encounter = null;
            return;
        }
        WeaponStack.removeItems(player.getInventory(),
                net.minecraft.world.item.Items.EMERALD, 5);
        returnPos = player.position();
        playerId = player.getUUID();
        encounter = enc;
        NpcParty.spawn(level, ArenaManager.entryPos());
        bossId = spawnBoss(level, foe);
        BossEvent.BossBarColor color = foe.rank().equals("elite") ? BossEvent.BossBarColor.YELLOW
                : foe.rank().equals("boss") ? BossEvent.BossBarColor.PURPLE
                        : BossEvent.BossBarColor.RED;
        bossBar = new ServerBossEvent(Component.literal(foe.name()), color,
                BossEvent.BossBarOverlay.PROGRESS);
        bossBar.addPlayer(player);
        player.teleportTo(ArenaManager.entryPos().x, ArenaManager.entryPos().y,
                ArenaManager.entryPos().z);
        RpgHealth.restore(player);
        phaseId = null;
        FIRED.clear();
        WINDUP.clear();
        REACHED.clear();
        pendingRevive = false;
        ended = false;
        wipes = 0;
        startTick = level.getGameTime();
        interruptReadyTick = 0;
        active = true;
        com.thuvstu.hayatemod.progress.AdvancementHelper.grant(player, "dungeon_enter");
        player.sendSystemMessage(Component.literal(
                "[dungeon] " + foe.name() + "戦開始。目安" + enc.targetMinutes() + "分。"
                        + "/solommo dungeon exit で撤退。"));
        player.sendSystemMessage(Component.literal(
                "[dungeon] " + introLine(enc, foe)), true);
        LOGGER.info("[DUNGEON] session started for {} ({})", player.getScoreboardName(), foe.id());
    }

    private static String introLine(EncounterData enc, EnemyData foe) {
        return switch (enc.id()) {
            case "solommo:slag_colossus" -> "炉心が唸る…スラグ・コロッサスが目覚めた！";
            case "solommo:flame_golem" -> "灰が舞い上がる…フレイム・ゴーレムが立ち上がった！";
            default -> foe.name() + "が現れた！";
        };
    }

    public static void exit(ServerPlayer player, String reason) {
        if (!active) {
            return;
        }
        LivingEntity boss = bossId != null ? EntityTypeTestHelper.living(level, bossId) : null;
        if (boss != null) {
            boss.discard();
        }
        NpcParty.despawn(level);
        if (bossBar != null) {
            bossBar.removeAllPlayers();
            bossBar = null;
        }
        int swept = ArenaManager.sweep(level);
        List<String> residue = ArenaManager.residue(level);
        Vec3 back = returnPos != null ? returnPos : player.position();
        player.teleportTo(back.x, back.y, back.z);
        active = false;
        bossId = null;
        LOGGER.info("[DUNGEON] session ended ({}) swept={} residue={}", reason, swept, residue);
        player.sendSystemMessage(Component.literal(
                "[dungeon] 撤退 (" + reason + ")。残骸: " + (residue.isEmpty() ? "なし" : residue)));
    }

    public static void stress(ServerPlayer player, int rounds) {
        if (active) {
            player.sendSystemMessage(Component.literal("[dungeon] session active; exit first"));
            return;
        }
        stressLeft = rounds;
        lastToggleTick = -100;
        player.sendSystemMessage(
                Component.literal("[dungeon] S2 stress: " + rounds + " enter/exit cycles"));
    }

    private static UUID spawnBoss(ServerLevel world, EnemyData foe) {
        var tuning = ContentHolder.get().tuning();
        var rules = com.thuvstu.hayatemod.rpg.DifficultyState.resolve(foe);
        EnemyStats.DerivedStats stats = EnemyStats.derive(foe.level(), foe.rank(),
                ContentHolder.get().reference(),
                tuning != null ? tuning.rankTargets() : Map.of(), rules.hp(), rules.dps());
        bossMaxHp = stats.maxHp();
        boolean manual = foe.bossBody().equals("iron_golem");
        Mob boss;
        if (manual) {
            IronGolem golem = EntityType.IRON_GOLEM.spawn(world, m -> {
                setupBoss(m, foe);
                m.setNoAi(true);
                m.addTag("solommo_noai_boss");
            }, new BlockPos(ArenaManager.origin().getX(), ArenaManager.origin().getY() + 1,
                    ArenaManager.origin().getZ()), EntitySpawnReason.MOB_SUMMONED, false, false);
            boss = golem;
        } else {
            Ravager ravager = EntityType.RAVAGER.spawn(world, m -> setupBoss(m, foe),
                    new BlockPos(ArenaManager.origin().getX(), ArenaManager.origin().getY() + 1,
                            ArenaManager.origin().getZ()),
                    EntitySpawnReason.MOB_SUMMONED, false, false);
            boss = ravager;
        }
        LOGGER.info("[DUNGEON] boss spawned {} hp={}", foe.id(), (long) bossMaxHp);
        return boss.getUUID();
    }

    private static void setupBoss(Mob mob, EnemyData foe) {
        Objects.requireNonNull(mob.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(bossMaxHp);
        mob.setHealth((float) bossMaxHp);
        mob.setCustomName(Component.literal(foe.name()));
        mob.setCustomNameVisible(true);
        mob.setPersistenceRequired();
        mob.addTag("solommo_session");
        mob.addTag("solommo_boss");
        mob.addTag("solommo_enemy:" + foe.id());
        com.thuvstu.hayatemod.rpg.DifficultyState.bind(mob, com.thuvstu.hayatemod.rpg.DifficultyState.resolve(foe));
    }

    // ---- tick ----

    private static void onTick(MinecraftServer server) {
        if (stressLeft > 0) {
            tickStress(server);
            return;
        }
        if (!active || level == null) {
            return;
        }
        ServerPlayer player = EntityTypeTestHelper.serverPlayer(level, playerId);
        if (player == null) {
            exitToServer("player missing");
            return;
        }
        if (pendingRevive && player.isAlive()) {
            Vec3 entry = ArenaManager.entryPos();
            player.teleportTo(entry.x, entry.y, entry.z);
            RpgHealth.restore(player);
            pendingRevive = false;
            player.sendSystemMessage(Component.literal("[dungeon] チェックポイントから再開"));
        }
        LivingEntity boss = EntityTypeTestHelper.living(level, bossId);
        if (bossBar != null && boss != null) {
            bossBar.setProgress((float) (boss.getHealth() / boss.getMaxHealth()));
        }
        NpcParty.tick(level, playerId, bossId);
        if (level.getGameTime() % 20 == 0) {
            UiServer.sendParty(player, level);
        }
        if (boss == null || !boss.isAlive()) {
            return; // Victory handled in onAfterDeath.
        }
        if (boss.getTags().contains("solommo_noai_boss") && boss instanceof Mob mobBoss) {
            chaseNearest(mobBoss, player);
        }
        long now = level.getGameTime();
        double frac = boss.getHealth() / boss.getMaxHealth();
        PhaseDef phase = PhaseLogic.phaseFor(encounter.phases(), frac);
        if (phase != null && !phase.id().equals(phaseId)) {
            phaseId = phase.id();
            phaseStartTick = now;
            loopIndex = 0;
            FIRED.clear();
            WINDUP.removeIf(w -> {
                McAdapter.engine().cancel(w.taskId());
                return true;
            });
            if (encounter.checkpoints().contains(phaseId) && !REACHED.contains(phaseId)) {
                REACHED.add(phaseId);
            }
            if (bossBar != null) {
                LivingEntity bossRef = EntityTypeTestHelper.living(level, bossId);
                String name = bossRef != null && bossRef.hasCustomName()
                        ? bossRef.getCustomName().getString()
                        : "ボス";
                bossBar.setName(Component.literal(name + " - " + phaseId.toUpperCase()));
            }
            LOGGER.info("[DUNGEON] phase -> {} (hp {}%)", phaseId, Math.round(frac * 100));
            ServerPlayer phasePlayer = EntityTypeTestHelper.serverPlayer(level, playerId);
            if (phasePlayer != null) {
                phasePlayer.sendSystemMessage(Component.literal(
                        "[dungeon] 形態変化：" + phase.id().toUpperCase()
                                + (encounter.checkpoints().contains(phaseId) ? "（チェックポイント）" : "")));
            }
            for (String ability : phase.onEnter()) {
                AbilityDef def = resolveAbility(ability);
                if (def != null) {
                    executeAbility(def, null);
                }
            }
        }
        if (phase == null) {
            return;
        }
        long elapsed = now - phaseStartTick;
        if (phase.loopPeriodSeconds() > 0
                && elapsed >= (long) phase.loopPeriodSeconds() * 20 * (loopIndex + 1)) {
            loopIndex++;
            FIRED.clear();
        }
        for (TimelineEntry t : phase.timeline()) {
            String key = phaseId + ":" + loopIndex + ":" + t.atSeconds() + ":" + t.ability();
            if (!FIRED.contains(key) && elapsed >= (long) t.atSeconds() * 20) {
                FIRED.add(key);
                AbilityDef def = resolveAbility(t.ability());
                if (def != null) {
                    fireAbility(def);
                }
            }
        }
        for (Windup w : new ArrayList<>(WINDUP)) {
            if (!w.warned() && now >= w.executeTick() - 40) {
                WINDUP.set(WINDUP.indexOf(w),
                        new Windup(w.taskId(), w.ability(), w.center(), w.executeTick(), true));
                McAdapter.adapter().ringParticles(bossId, toCore(w.center()), abilityRadius(w.ability()));
            } else if (now >= w.executeTick() - 40 && (now / 2) % 2 == 0) {
                McAdapter.adapter().ringParticles(bossId, toCore(w.center()), abilityRadius(w.ability()));
            }
        }
    }

    private static void tickStress(MinecraftServer server) {
        ServerLevel world = server.overworld();
        ServerPlayer player = null;
        for (ServerPlayer p : world.players()) {
            player = p;
            break;
        }
        if (player == null) {
            stressLeft = 0;
            LOGGER.info("[DUNGEON][S2] stress aborted (no player)");
            return;
        }
        long now = world.getGameTime();
        if (now - lastToggleTick < 5) {
            return;
        }
        lastToggleTick = now;
        if (!active) {
            enter(player);
            if (!active) {
                stressLeft = 0;
                LOGGER.info("[DUNGEON][S2] stress aborted (enter failed)");
            }
        } else {
            exit(player, "stress");
            List<String> residue = ArenaManager.residue(world);
            LOGGER.info("[DUNGEON][S2] cycle done, residue={}", residue);
            stressLeft--;
            if (stressLeft <= 0) {
                LOGGER.info("[DUNGEON][S2] PASS: {} cycles, final residue={}", "20", residue);
                player.sendSystemMessage(Component.literal(
                        "[dungeon] S2 stress complete. residue=" + residue));
            }
        }
    }

    private static void exitToServer(String reason) {
        if (level == null) {
            active = false;
            return;
        }
        LivingEntity boss = bossId != null ? EntityTypeTestHelper.living(level, bossId) : null;
        if (boss != null) {
            boss.discard();
        }
        NpcParty.despawn(level);
        if (bossBar != null) {
            bossBar.removeAllPlayers();
            bossBar = null;
        }
        ArenaManager.sweep(level);
        active = false;
        bossId = null;
        LOGGER.info("[DUNGEON] session ended ({})", reason);
    }

    private static void chaseNearest(Mob boss, ServerPlayer player) {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        if (player.isAlive()) {
            best = player;
            bestDist = boss.position().distanceTo(player.position());
        }
        for (NpcParty.Member m : NpcParty.members()) {
            LivingEntity e = EntityTypeTestHelper.living(level, m.id());
            if (e == null || m.downed()) {
                continue;
            }
            double d = boss.position().distanceTo(e.position());
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        if (best != null && bestDist > 3.0) {
            NpcParty.stepToward(boss, best.position());
        }
    }

    // ---- abilities ----

    private static AbilityDef resolveAbility(String id) {
        AbilityDef local = encounter.abilities().get(id);
        if (local != null) {
            return local;
        }
        return ContentHolder.get().skills().get(id);
    }

    private static double abilityRadius(AbilityDef a) {
        Object r = a.mods().get("radius");
        return r instanceof Number n ? n.doubleValue() : 6.0;
    }

    private static Vec3 abilityCenter(AbilityDef a, LivingEntity boss) {
        if (a.center() != null && a.center().startsWith("marker:")) {
            String marker = a.center().substring("marker:".length());
            Object raw = encounter.arenaMarkers().get(marker);
            if (raw == null) {
                var arena = ContentHolder.get().arenas().get(encounter.arenaTemplate());
                if (arena != null) {
                    raw = arena.markers().get(marker);
                }
            }
            if (raw instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Number) {
                @SuppressWarnings("unchecked")
                List<Number> point = (List<Number>) list;
                return ArenaManager.markerWorld(point);
            }
        }
        return boss.position();
    }

    private static void fireAbility(AbilityDef a) {
        LivingEntity boss = EntityTypeTestHelper.living(level, bossId);
        if (boss == null) {
            return;
        }
        Vec3 center = abilityCenter(a, boss);
        if (a.signal() != null && !a.signal().isEmpty()) {
            NpcParty.orderSignal(level, a.signal(), center, bossId, a.lethal());
        }
        Object cast = a.mods().get("cast_time");
        double castSec = cast instanceof Number n ? n.doubleValue() : 0.0;
        if (castSec > 0 && tryInterrupt(boss)) {
            return;
        }
        if (castSec > 0) {
            long executeTick = level.getGameTime() + (long) (castSec * 20.0);
            if (a.lethal()) {
                ServerPlayer p = EntityTypeTestHelper.serverPlayer(level, playerId);
                if (p != null) {
                    p.sendSystemMessage(Component.literal("[dungeon] 致死級の攻撃が来る！ "
                            + (a.signal() != null ? a.signal() : "")));
                }
            }
            UUID task = McAdapter.engine().schedule((long) (castSec * 20.0),
                    () -> executeAbility(a, center));
            if (task == null) {
                LOGGER.warn("[DUNGEON] skipped {}: effect task queue is full", a.id());
                return;
            }
            WINDUP.add(new Windup(task, a, center, executeTick, false));
            LOGGER.info("[DUNGEON] windup {} ({}s)", a.id(), castSec);
        } else {
            executeAbility(a, center);
        }
    }

    /** Tania interrupts the cast when in range and off cooldown. */
    private static boolean tryInterrupt(LivingEntity boss) {
        long now = level.getGameTime();
        if (now < interruptReadyTick) {
            return false;
        }
        for (NpcParty.Member m : NpcParty.members()) {
            if (!m.data.capabilities().contains("interrupt") || m.downed) {
                continue;
            }
            LivingEntity e = EntityTypeTestHelper.living(level, m.id);
            if (e != null && e.position().distanceTo(boss.position()) <= 6.0) {
                interruptReadyTick = now + 600;
                LivingEntity bossRef = boss;
                ServerPlayer p = EntityTypeTestHelper.serverPlayer(level, playerId);
                if (p != null) {
                    p.sendSystemMessage(Component.literal(
                            "[dungeon] " + m.data.name() + " が詠唱を中断した！"));
                }
                LOGGER.info("[DUNGEON][INTERRUPT] {} interrupted the cast", m.data.name());
                return true;
            }
        }
        return false;
    }

    private static void executeAbility(AbilityDef a, Vec3 center) {
        LivingEntity boss = EntityTypeTestHelper.living(level, bossId);
        if (boss == null || !boss.isAlive()) {
            return;
        }
        if (center == null) {
            center = abilityCenter(a, boss);
        }
        if (a.lethal()) {
            detonateLethal(a, boss, center);
            return;
        }
        McAdapter.engine().castSkill(boss.getUUID(), pseudoCard(a), "cast");
        LOGGER.info("[DUNGEON] cast {}", a.id());
    }

    private static void detonateLethal(AbilityDef a, LivingEntity boss, Vec3 center) {
        double radius = abilityRadius(a);
        double total = a.lethalRatio() * RpgHealth.maxHp()
                * com.thuvstu.hayatemod.rpg.DifficultyState.snapshot(boss).dps();
        List<LivingEntity> inArea = new ArrayList<>();
        ServerPlayer player = EntityTypeTestHelper.serverPlayer(level, playerId);
        if (player != null && player.isAlive() && !player.isCreative()
                && player.position().distanceTo(center) <= radius) {
            inArea.add(player);
        }
        for (NpcParty.Member m : NpcParty.members()) {
            LivingEntity e = EntityTypeTestHelper.living(level, m.id);
            if (e != null && !m.downed && e.position().distanceTo(center) <= radius) {
                inArea.add(e);
            }
        }
        McAdapter.adapter().burstParticles(bossId, toCore(center));
        if (a.splitDamage()) {
            double each = total / Math.max(1, inArea.size());
            for (LivingEntity target : inArea) {
                target.hurtServer(level, level.damageSources().generic(), (float) each);
            }
            LOGGER.info("[DUNGEON][LETHAL] {} split {} among {} (each {:.0f})", a.id(), (int) total,
                    inArea.size(), each);
        } else {
            for (LivingEntity target : inArea) {
                target.hurtServer(level, level.damageSources().generic(), (float) total);
            }
            LOGGER.info("[DUNGEON][LETHAL] {} flat {} to {} in radius", a.id(), (int) total,
                    inArea.size());
        }
    }

    private static WeaponCard pseudoCard(AbilityDef a) {
        String id = "boss:" + encounter.id() + ":" + a.id();
        return new WeaponCard(id, a.name(), "boss", "boss", bossLevel(),
                List.of(), List.of(),
                Map.of("cast", new SkillDef(a.core(), a.mods(), List.of())), "");
    }

    private static int bossLevel() {
        EnemyData foe = ContentHolder.get().enemies().get(encounter.id());
        return foe != null ? foe.level() : 20;
    }

    // ---- death & wipe ----

    private static void onAfterDeath(LivingEntity entity, DamageSource source) {
        if (!active || level == null) {
            return;
        }
        if (bossId != null && entity.getUUID().equals(bossId)) {
            onVictory(entity);
        } else if (playerId != null && entity.getUUID().equals(playerId)) {
            onWipe();
        }
    }

    private static void onVictory(LivingEntity boss) {
        ended = true;
        long seconds = (boss.level().getGameTime() - startTick) / 20;
        ServerPlayer player = EntityTypeTestHelper.serverPlayer(level, playerId);
        EnemyData foe = encounter != null
                ? ContentHolder.get().enemies().get(encounter.id())
                : null;
        var table = foe != null ? ContentHolder.get().loot().get(foe.loot()) : null;
        if (table != null && player != null) {
            DropHooks.rollTable(boss, level, table, player);
            String keystone =
                    com.thuvstu.hayatemod.build.PlayerBuilds.grantRandomKeystone(player.getUUID());
            if (keystone != null) {
                player.sendSystemMessage(Component.literal(
                        "[dungeon] キーストーン入手: " + keystone + "（/solommo keystone で装備）"));
            }
            com.thuvstu.hayatemod.build.PlayerBuilds.grantSp(player.getUUID(), 2);
            player.sendSystemMessage(Component.literal(
                    "[dungeon] スキルポイント+2（/solommo sp で強化）"));
        }
        if (bossBar != null) {
            bossBar.setProgress(0.0F);
        }
        LOGGER.info("[DUNGEON] VICTORY in {}s, wipes={}", seconds, wipes);
        if (player != null && encounter != null) {
            String adv = switch (encounter.id()) {
                case "solommo:slag_colossus" -> "colossus_down";
                case "solommo:flame_golem" -> "golem_down";
                default -> null;
            };
            if (adv != null) {
                com.thuvstu.hayatemod.progress.AdvancementHelper.grant(player, adv);
            }
        }
        if (player != null) {
            player.sendSystemMessage(Component.literal(
                    "[dungeon] 撃破！ (" + seconds + "秒, ワイプ" + wipes + "回) 報酬を回収して exit してください"));
        }
    }

    private static void onWipe() {
        wipes++;
        pendingRevive = true;
        LivingEntity boss = EntityTypeTestHelper.living(level, bossId);
        if (boss != null) {
            boss.setHealth(boss.getMaxHealth());
            boss.teleportTo(ArenaManager.origin().getX(), ArenaManager.origin().getY() + 1,
                    ArenaManager.origin().getZ());
        }
        NpcParty.reviveAll(level);
        String restart = !REACHED.isEmpty() ? REACHED.get(REACHED.size() - 1) : null;
        phaseId = null;
        FIRED.clear();
        WINDUP.removeIf(w -> {
            McAdapter.engine().cancel(w.taskId());
            return true;
        });
        if (restart != null) {
            for (PhaseDef p : encounter.phases()) {
                if (p.id().equals(restart)) {
                    phaseId = restart;
                    phaseStartTick = level.getGameTime();
                    loopIndex = 0;
                    break;
                }
            }
        }
        LOGGER.info("[DUNGEON][WIPE] #{}, restart at {}", wipes,
                restart != null ? restart : "start");
    }
}
