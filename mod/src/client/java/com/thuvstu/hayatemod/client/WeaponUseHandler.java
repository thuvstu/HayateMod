package com.thuvstu.hayatemod.client;

import com.thuvstu.hayatemod.item.ModItems;
import com.thuvstu.hayatemod.net.SkillCastPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionResult;

/**
 * Right-click with a SoloMMO weapon fires its special (same C2S request as
 * the G key; the server validates cooldowns and ownership).
 */
public final class WeaponUseHandler {
    private WeaponUseHandler() {
    }

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClientSide()) {
                return InteractionResult.PASS;
            }
            var stack = player.getItemInHand(hand);
            // Gate on the item type: the client holds no Content Pack, so the
            // server validates ownership/cooldown/special existence.
            if (!(stack.getItem() instanceof ModItems.WeaponItem)) {
                return InteractionResult.PASS;
            }
            if (ClientPlayNetworking.canSend(SkillCastPayload.TYPE)) {
                ClientPlayNetworking.send(
                        new SkillCastPayload(player.isShiftKeyDown() ? "heavy" : "special"));
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }
}
