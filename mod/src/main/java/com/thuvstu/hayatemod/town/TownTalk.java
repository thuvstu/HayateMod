package com.thuvstu.hayatemod.town;

import com.thuvstu.hayatemod.net.UiServer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Talking to hamlet folk opens their screen (survival-native UI entry).
 * Moka the keeper -> tavern, Don the smith -> workshop, Pipa the trader -> market.
 */
public final class TownTalk {
    private TownTalk() {
    }

    public static void register() {
        UseEntityCallback.EVENT.register(TownTalk::onUse);
    }

    private static InteractionResult onUse(Player player, Level world, InteractionHand hand, Entity entity,
            @Nullable EntityHitResult hitResult) {
        if (world.isClientSide() || !(player instanceof net.minecraft.server.level.ServerPlayer sp)) {
            return InteractionResult.PASS;
        }
        if (!entity.getTags().contains("solommo_townfolk") || !entity.hasCustomName()) {
            return InteractionResult.PASS;
        }
        String name = entity.getCustomName().getString();
        if (name.contains("モカ")) {
            UiServer.openTavern(sp);
            return InteractionResult.SUCCESS;
        }
        if (name.contains("ドン")) {
            UiServer.openCraft(sp);
            return InteractionResult.SUCCESS;
        }
        if (name.contains("ピパ")) {
            UiServer.openMarket(sp);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
