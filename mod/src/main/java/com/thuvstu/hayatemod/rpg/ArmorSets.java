package com.thuvstu.hayatemod.rpg;

import com.thuvstu.hayatemod.item.ModItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

/**
 * Cinder mail set bonus: full set refreshes fire resistance. Checked
 * once per second per survival player.
 */
public final class ArmorSets {
    private ArmorSets() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ArmorSets::onTick);
    }

    private static void onTick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        for (var level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                if (player.isCreative() || player.isSpectator() || !fullSet(player)) {
                    continue;
                }
                player.addEffect(new MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 60, 0, false,
                        false));
            }
        }
    }

    static boolean fullSet(ServerPlayer player) {
        return is(player, net.minecraft.world.entity.EquipmentSlot.HEAD, ModItems.CINDER_HELM)
                && is(player, net.minecraft.world.entity.EquipmentSlot.CHEST, ModItems.CINDER_CHEST)
                && is(player, net.minecraft.world.entity.EquipmentSlot.LEGS, ModItems.CINDER_LEGS)
                && is(player, net.minecraft.world.entity.EquipmentSlot.FEET, ModItems.CINDER_BOOTS);
    }

    private static boolean is(ServerPlayer player,
            net.minecraft.world.entity.EquipmentSlot slot, net.minecraft.world.item.Item item) {
        ItemStack stack = player.getItemBySlot(slot);
        return !stack.isEmpty() && stack.is(item);
    }
}
