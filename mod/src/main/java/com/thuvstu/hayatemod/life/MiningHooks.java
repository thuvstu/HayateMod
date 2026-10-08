package com.thuvstu.hayatemod.life;

import java.util.Map;

import com.thuvstu.hayatemod.content.ContentHolder;
import com.thuvstu.hayatemod.core.life.MiningLevels;
import com.thuvstu.hayatemod.item.ModItems;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mining life skill hooks (Phase 6). XP per ore, iron locked below Lv5,
 * gem-slot bonus from Lv15. Vanilla drops are untouched.
 */
public final class MiningHooks {
    private static final Logger LOGGER = LoggerFactory.getLogger("hayatemod/life");

    private MiningHooks() {
    }

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register(MiningHooks::beforeBreak);
        PlayerBlockBreakEvents.AFTER.register(MiningHooks::afterBreak);
    }

    private static boolean beforeBreak(net.minecraft.world.level.Level world,
            net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
        if (world.isClientSide() || !(player instanceof ServerPlayer sp) || !ContentHolder.ready()
                || ContentHolder.get().mining() == null) {
            return true;
        }
        String id = blockId(state);
        if ((id.equals("minecraft:iron_ore") || id.equals("minecraft:deepslate_iron_ore"))) {
            int level = MiningLevels.levelForXp(LifeSkills.miningXp(sp.getUUID()),
                    ContentHolder.get().mining());
            if (!MiningLevels.unlocksContain(ContentHolder.get().mining(), level, "iron")) {
                sp.sendSystemMessage(Component.literal("[採掘] 鉄鉱脈は採掘Lv5から（現在Lv" + level + ")"));
                return false;
            }
        }
        return true;
    }

    private static void afterBreak(net.minecraft.world.level.Level world,
            net.minecraft.world.entity.player.Player player, net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
        if (world.isClientSide() || !(player instanceof ServerPlayer sp) || !ContentHolder.ready()
                || ContentHolder.get().mining() == null) {
            return;
        }
        var book = ContentHolder.get().mining();
        String id = blockId(state);
        int xp = MiningLevels.xpFor(id, book);
        if (xp <= 0) {
            return;
        }
        int before = MiningLevels.levelForXp(LifeSkills.miningXp(sp.getUUID()), book);
        LifeSkills.addMiningXp(sp.getUUID(), xp);
        int after = MiningLevels.levelForXp(LifeSkills.miningXp(sp.getUUID()), book);
        if (after > before) {
            sp.sendSystemMessage(Component.literal("[採掘] Lv" + after + " 到達！"));
            LOGGER.info("[life] {} mining Lv{} ({}xp)", sp.getScoreboardName(), after,
                    LifeSkills.miningXp(sp.getUUID()));
        }
        if (MiningLevels.levelForXp(LifeSkills.miningXp(sp.getUUID()), book) >= book.gemMinLevel()
                && book.gemOres().contains(id)) {
            sp.getInventory().add(new ItemStack(ModItems.CRAFT_MATERIAL, book.gemBonusMaterials()));
            sp.sendSystemMessage(Component.literal("[採掘] 宝石枠素材を回収！"));
        }
    }

    private static String blockId(net.minecraft.world.level.block.state.BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    /** Trade map: content item id -> vanilla/modded item. Pity shards are excluded by design. */
    public static Map<String, net.minecraft.world.level.ItemLike> tradables() {
        return Map.of(
                "solommo:craft_material", ModItems.CRAFT_MATERIAL,
                "minecraft:coal", net.minecraft.world.item.Items.COAL,
                "minecraft:iron_ingot", net.minecraft.world.item.Items.IRON_INGOT,
                "minecraft:gold_ingot", net.minecraft.world.item.Items.GOLD_INGOT,
                "minecraft:diamond", net.minecraft.world.item.Items.DIAMOND);
    }
}
