package com.thuvstu.hayatemod.mixin;

import com.thuvstu.hayatemod.content.ContentHolder;
import com.thuvstu.hayatemod.core.life.MiningLevels;
import com.thuvstu.hayatemod.life.LifeSkills;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fishing life skill (Phase 6): successful retrieves award XP from
 * {@code content/life_skills/fishing.yaml}. Return codes: 0 miss, 1+ catch.
 */
@Mixin(FishingHook.class)
public class FishCatchMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("hayatemod/life");

    @Inject(method = "retrieve", at = @At("RETURN"))
    private void hayatemod$onRetrieve(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        FishingHook self = (FishingHook) (Object) this;
        if (self.level().isClientSide()) {
            return;
        }
        if (cir.getReturnValue() < 1) {
            return;
        }
        Player owner = self.getPlayerOwner();
        if (!(owner instanceof ServerPlayer player) || !ContentHolder.ready()
                || ContentHolder.get().fishing() == null) {
            return;
        }
        var book = ContentHolder.get().fishing();
        int before = MiningLevels.levelForRows(LifeSkills.fishingXp(player.getUUID()), book.levels());
        LifeSkills.addFishingXp(player.getUUID(), book.catchXp());
        int after = MiningLevels.levelForRows(LifeSkills.fishingXp(player.getUUID()), book.levels());
        if (after > before) {
            player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("[釣り] Lv" + after + " 到達！"));
            LOGGER.info("[life] {} fishing Lv{}", player.getScoreboardName(), after);
        }
    }
}
