package com.thuvstu.hayatemod.town;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.thuvstu.hayatemod.dungeon.ArenaManager;
import com.thuvstu.hayatemod.rpg.McAdapter;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hamlet hub (Phase 6): terrain-following platform, stalls, ambient
 * villagers, town teleport, town-gating for loadouts/dungeon entry,
 * and a minimal field spawner so the solo loop stays alive.
 */
public final class TownManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("hayatemod/town");

    private static final int TX = 200;
    private static final int TZ = 200;
    private static final int HALF = 12;
    private static final double GATE_RADIUS = 64.0;
    private static final Map<UUID, Vec3> RETURNS = new HashMap<>();
    private static long tickCounter;

    private TownManager() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(TownManager::onTick);
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register(
                TownManager::onWildKill);
    }

    /** Kill-pity: every PITY_KILLS wild kills guarantees a watcher visit. */
    private static final int PITY_KILLS = 15;
    private static final Map<UUID, Integer> KILLS = new HashMap<>();

    private static void onWildKill(LivingEntity entity,
            net.minecraft.world.damagesource.DamageSource source) {
        if (!entity.getTags().contains("solommo_wild")) {
            return;
        }
        if (source.getEntity() instanceof ServerPlayer player) {
            KILLS.merge(player.getUUID(), 1, Integer::sum);
        }
    }

    private static int townX() {
        var set = com.thuvstu.hayatemod.content.ContentHolder.get();
        return set != null && set.world() != null ? set.world().town().x() : TX;
    }

    private static int townZ() {
        var set = com.thuvstu.hayatemod.content.ContentHolder.get();
        return set != null && set.world() != null ? set.world().town().z() : TZ;
    }

    private static int townHalf() {
        var set = com.thuvstu.hayatemod.content.ContentHolder.get();
        return set != null && set.world() != null ? set.world().town().halfSize() : HALF;
    }

    private static double gateRadius() {
        var set = com.thuvstu.hayatemod.content.ContentHolder.get();
        return set != null && set.world() != null ? set.world().townGateRadius() : GATE_RADIUS;
    }

    public static Vec3 center() {
        // Gate checks have no level; use the cached site once ensureTown ran,
        // else the content hint (the site search recenters on first build).
        BlockPos site = com.thuvstu.hayatemod.build.GroundSites.peekTown().orElse(null);
        if (site != null) {
            return new Vec3(site.getX(), site.getY(), site.getZ());
        }
        return new Vec3(townX(), 64, townZ());
    }

    /** Returns true when the player may use town-gated operations. */
    public static boolean requireTown(ServerPlayer player) {
        if (isInTown(player)) {
            return true;
        }
        player.sendSystemMessage(Component.literal("[街] 街の範囲内で実行してください（/solommo town）"));
        return false;
    }

    public static boolean isInTown(ServerPlayer player) {
        Vec3 c = center();
        Vec3 p = player.position();
        double dx = p.x - c.x;
        double dz = p.z - c.z;
        return dx * dx + dz * dz <= gateRadius() * gateRadius();
    }

    public static void teleportTown(ServerPlayer player) {
        ensureTown(player.level());
        RETURNS.put(player.getUUID(), player.position());
        Vec3 c = center();
        player.teleportTo(c.x, c.y + 1, c.z);
        com.thuvstu.hayatemod.progress.AdvancementHelper.grant(player, "town");
        player.sendSystemMessage(Component.literal("[街] 陽炎の集落へようこそ。/solommo back で戻ります"));
    }

    public static void teleportBack(ServerPlayer player) {
        Vec3 back = RETURNS.remove(player.getUUID());
        if (back == null) {
            player.sendSystemMessage(Component.literal("[街] 戻り先がありません"));
            return;
        }
        player.teleportTo(back.x, back.y, back.z);
    }

    public static void ensureTown(ServerLevel level) {
        BlockPos origin = com.thuvstu.hayatemod.build.GroundSites.townSiteSpawn(level, townX(),
                townZ(), townHalf(), new BlockPos(townX(), 64, townZ()));
        int surface = origin.getY();
        if (level.getBlockState(origin).is(Blocks.COBBLESTONE)
                && countFolk(level) >= 3) {
            return;
        }
        LOGGER.info("[town] building hamlet at {}", origin);
        var set = com.thuvstu.hayatemod.content.ContentHolder.get();
        var template = set != null && set.structures() != null
                ? set.structures().get("solommo:hamlet")
                : null;
        if (template == null) {
            LOGGER.error("[town] missing structure 'solommo:hamlet'; town build aborted");
            return;
        }
        com.thuvstu.hayatemod.build.StructureBuilder.place(level, origin, template);
        folk(level, origin, "酒場主人モカ", -6, -4);
        folk(level, origin, "鍛冶屋ドン", 6, -4);
        folk(level, origin, "行商人ピパ", 0, 5);
    }

    private static int countFolk(ServerLevel level) {
        int n = 0;
        for (Entity e : level.getEntities(EntityTypeTest.forClass(Entity.class),
                e -> e.getTags().contains("solommo_townfolk"))) {
            n++;
        }
        return n;
    }

    private static void folk(ServerLevel level, BlockPos origin, String name, int ox, int oz) {
        Villager v = EntityType.VILLAGER.spawn(level, m -> {
            m.setCustomName(Component.literal(name));
            m.setCustomNameVisible(true);
            m.setPersistenceRequired();
            m.addTag("solommo_townfolk");
        }, origin.offset(ox, 0, oz), EntitySpawnReason.COMMAND, false, false);
        if (v != null) {
            LOGGER.info("[town] folk spawned: {}", name);
        }
    }

    // ---- field spawner (keeps the solo loop alive) ----

    private static void onTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter % 200 == 0) {
            despawnFar(server);
        }
        if (tickCounter % 2400 != 0) {
            return;
        }
        ServerLevel overworld = server.overworld();
        // Ambient folk top-up.
        ensureTown(overworld);
        for (ServerPlayer player : overworld.players()) {
            if (player.isCreative() || player.isSpectator()) {
                continue;
            }
            if (nearArenaOrTown(player)) {
                continue;
            }
            int wild = 0;
            for (LivingEntity e : overworld.getEntities(
                    EntityTypeTest.forClass(LivingEntity.class),
                    e -> e.getTags().contains("solommo_wild"))) {
                if (e.position().distanceTo(player.position()) <= 64.0) {
                    wild++;
                }
            }
            boolean night = overworld.getDayTime() % 24000L >= 12000L;
            if (wild >= (night ? 5 : 3)) {
                continue;
            }
            int pity = KILLS.getOrDefault(player.getUUID(), 0);
            if (pity >= PITY_KILLS) {
                KILLS.put(player.getUUID(), 0);
                if (spawnPack(overworld, player, "solommo:pyre_watcher", 1)) {
                    player.sendSystemMessage(
                            Component.literal("強大な気配が近づいている…"), true);
                    LOGGER.info("[town][field] pity watcher for {}", player.getScoreboardName());
                }
                continue;
            }
            // Pack hunt: 2-4 trash (night +1), biome-weighted, brute leads at night.
            int pack = 2 + (int) (Math.random() * 2) + (night ? 1 : 0);
            for (int i = 0; i < pack; i++) {
                String id = pickTrash(overworld, player, night && i == 0);
                if (!spawnPack(overworld, player, id, 1)) {
                    break;
                }
            }
        }
    }

    private static String pickTrash(ServerLevel level, ServerPlayer player, boolean bruteLead) {
        if (bruteLead && Math.random() < 0.35) {
            return "solommo:slag_brute";
        }
        String biome = level.getBiome(BlockPos.containing(player.position())).unwrapKey()
                .map(k -> k.identifier().getPath()).orElse("");
        if (biome.contains("desert") || biome.contains("badlands") || biome.contains("savanna")) {
            return "solommo:cinder_husk";
        }
        if (biome.contains("forest") || biome.contains("taiga") || biome.contains("jungle")
                || biome.contains("swamp")) {
            return "solommo:ash_crawler";
        }
        return "solommo:cinder_imp";
    }

    /** Spawns n members around the player (24-40m). Returns true if any landed. */
    private static boolean spawnPack(ServerLevel overworld, ServerPlayer player, String id, int n) {
        boolean any = false;
        for (int m = 0; m < n; m++) {
            for (int attempt = 0; attempt < 3; attempt++) {
                double angle = Math.random() * Math.PI * 2.0;
                double dist = 24.0 + Math.random() * 16.0;
                int x = (int) (player.getX() + Math.cos(angle) * dist);
                int z = (int) (player.getZ() + Math.sin(angle) * dist);
                int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos pos = new BlockPos(x, y, z);
                if (overworld.getBlockState(pos).isAir()
                        && overworld.getBlockState(pos.above()).isAir()) {
                    spawnWild(overworld, id, pos);
                    any = true;
                    break;
                }
            }
        }
        return any;
    }

    private static boolean nearArenaOrTown(ServerPlayer player) {
        Vec3 p = player.position();
        if (dist2D(p, new Vec3(ArenaManager.origin().getX(), p.y, ArenaManager.origin().getZ())) < 48.0) {
            return true;
        }
        Vec3 c = center();
        return dist2D(p, new Vec3(c.x, p.y, c.z)) < 48.0;
    }

    private static double dist2D(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void despawnFar(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        for (LivingEntity e : new ArrayList<>(overworld.getEntities(
                EntityTypeTest.forClass(LivingEntity.class),
                e -> e.getTags().contains("solommo_wild")))) {
            boolean near = false;
            for (ServerPlayer p : overworld.players()) {
                if (e.position().distanceTo(p.position()) <= 96.0) {
                    near = true;
                    break;
                }
            }
            if (!near) {
                e.discard();
            }
        }
        // Wild vanilla scaling sweep (no entity-load event in this Fabric version).
        for (LivingEntity e : overworld.getEntities(
                EntityTypeTest.forClass(LivingEntity.class), LivingEntity::isAlive)) {
            com.thuvstu.hayatemod.rpg.McAdapter.scaleWildHostile(e);
        }
    }

    private static void spawnWild(ServerLevel level, String enemyId, BlockPos pos) {
        try {
            McAdapter.spawnWild(level, enemyId, pos);
            LOGGER.info("[town][field] wild {} spawned", enemyId);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("[town][field] {}", e.getMessage());
        }
    }
}
