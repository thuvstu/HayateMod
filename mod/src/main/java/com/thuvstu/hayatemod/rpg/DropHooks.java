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
            ServerPlayer killer) {        var random = level.random;
        for (var drop : table.direct()) {
            if (random.nextFloat() < drop.p()) {
                var card = ContentHolder.get().weapons().get(drop.item());
                ItemStack stack = WeaponStack.make(drop.item(), card != null ? card.itemLevel() : 1);
                if (stack.isEmpty()) {
                    LOGGER.warn("[DROP] cannot make '{}'", drop.item());
                    continue;
                }
                entity.spawnAtLocation(level, stack);
                CodexStore.record(killer.getUUID(), drop.item());
                LOGGER.info("[DROP] {} dropped {} for {}", entity.getScoreboardName(), drop.item(),
                        killer.getScoreboardName());
            }
        }
        if (table.perKill() > 0) {
            entity.spawnAtLocation(level, new ItemStack(ModItems.PITY_SHARD, table.perKill()));
        }
        for (var mat : table.materials()) {
            int n = mat.min() + random.nextInt(mat.max() - mat.min() + 1);
            if (n <= 0) {
                continue;
            }
            ItemStack stack = materialStack(mat.item(), n);
            if (!stack.isEmpty()) {
                entity.spawnAtLocation(level, stack);
            }
        }
        for (var rune : table.runes()) {
            if (random.nextFloat() < rune.p()) {
                entity.spawnAtLocation(level, WeaponStack.makeRune(rune.id()));
                LOGGER.info("[DROP] {} dropped rune {} for {}", entity.getScoreboardName(), rune.id(),
                        killer.getScoreboardName());
            }
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
