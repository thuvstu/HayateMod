package com.thuvstu.hayatemod.rpg;

import com.thuvstu.hayatemod.codex.CodexStore;
import com.thuvstu.hayatemod.content.ContentHolder;
import com.thuvstu.hayatemod.core.content.model.Models.LootTable;
import com.thuvstu.hayatemod.item.ModItems;
import com.thuvstu.hayatemod.item.WeaponStack;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loot rolls. Session/summoned entities never drop; {@code solommo_enemy:<id>}
 * tags bind a body to its EnemyData table; wild zombies fall back to the
 * cinder table (spike mapping until spawners bind enemies properly).
 */
public final class DropHooks {
    private static final Logger LOGGER = LoggerFactory.getLogger("hayatemod/rpg");

    private DropHooks() {
    }

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(DropHooks::onAfterDeath);
    }

    private static void onAfterDeath(LivingEntity entity, DamageSource source) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer killer = killerOf(source);
        if (killer == null || !ContentHolder.ready()) {
            return;
        }
        LootTable table = tableFor(entity);
        if (table == null) {
            return;
        }
        rollTable(entity, level, table, killer);
    }

    /** Shared roll used by wild kills and the encounter runner (boss rewards). */
    public static void rollTable(LivingEntity entity, ServerLevel level, LootTable table,
            ServerPlayer killer) {
        var random = level.random;
        var roll = com.thuvstu.hayatemod.core.rules.LootRoller.roll(table, DifficultyState.snapshot(entity),
                random::nextDouble, random::nextInt);
        if (!roll.equipment().isEmpty()) {
            var card = ContentHolder.get().weapons().get(roll.equipment());
            ItemStack stack = WeaponStack.make(roll.equipment(), card != null ? card.itemLevel() : 1);
            if (!stack.isEmpty()) {
                entity.spawnAtLocation(level, stack);
                CodexStore.record(killer.getUUID(), roll.equipment());
                LOGGER.info("[DROP] {} dropped {} for {}", entity.getScoreboardName(), roll.equipment(), killer.getScoreboardName());
            }
        }
        spawnCount(entity, level, "pity_shard", roll.pity());
        for (var material : roll.materials()) spawnCount(entity, level, material.id(), material.count());
        for (String rune : roll.runes()) entity.spawnAtLocation(level, WeaponStack.makeRune(rune));
    }

    private static void spawnCount(LivingEntity entity, ServerLevel level, String id, int count) {
        // Never create oversized stacks, even when difficulty increases guaranteed rewards.
        for (int left = Math.min(4096, count); left > 0; left -= 64) {
            int size = Math.min(64, left);
            ItemStack stack = id.equals("pity_shard") ? new ItemStack(ModItems.PITY_SHARD, size) : materialStack(id, size);
            if (!stack.isEmpty()) entity.spawnAtLocation(level, stack);
        }
    }

    /** Material id (bare or solommo:) to a stack. Unknown ids yield EMPTY. */
    public static ItemStack materialStack(String id, int n) {
        return switch (id) {
            case "craft_material" -> new ItemStack(ModItems.CRAFT_MATERIAL, n);
            case "solommo:cinder_iron", "cinder_iron" -> new ItemStack(ModItems.CINDER_IRON, n);
            case "solommo:slag_steel", "slag_steel" -> new ItemStack(ModItems.SLAG_STEEL, n);
            case "solommo:ember_glass", "ember_glass" -> new ItemStack(ModItems.EMBER_GLASS, n);
            default -> ItemStack.EMPTY;
        };
    }

    private static ServerPlayer killerOf(DamageSource source) {
        Entity e = source.getEntity();
        if (e instanceof ServerPlayer p) {
            return p;
        }
        if (e instanceof SmallFireball bolt && bolt.getOwner() instanceof ServerPlayer p) {
            return p;
        }
        return null;
    }

    private static LootTable tableFor(LivingEntity entity) {
        if (entity.getTags().contains("solommo_summoned")) {
            return null;
        }
        if (entity.getTags().contains("solommo_boss")) {
            return null; // The encounter runner rolls boss rewards itself.
        }
        for (String tag : entity.getTags()) {
            if (tag.startsWith("solommo_enemy:")) {
                String enemyId = tag.substring("solommo_enemy:".length());
                var enemy = ContentHolder.get().enemies().get(enemyId);
                if (enemy != null) {
                    return ContentHolder.get().loot().get(enemy.loot());
                }
                return null;
            }
        }
        if (entity instanceof Zombie) {
            return ContentHolder.get().loot().get("solommo:cinder_husk_loot");
        }
        return null;
    }
}
