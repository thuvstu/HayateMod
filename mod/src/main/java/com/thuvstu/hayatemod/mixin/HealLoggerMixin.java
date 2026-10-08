package com.thuvstu.hayatemod.mixin;

import com.thuvstu.hayatemod.debug.DebugFlags;
import com.thuvstu.hayatemod.rpg.RpgHealth;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Redirects player {@code heal()} calls into the RPG pool (ADR-07).
 * Vanilla HP stays a display mirror, so every heal source (natural regen,
 * saturation, potions, golden apples) must pass through here.
 *
 * <p>Logging is gated behind {@code /s1 log on} (DebugFlags.damageLog).
 */
@Mixin(LivingEntity.class)
public class HealLoggerMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("hayatemod/debug");

    @Inject(method = "heal", at = @At("HEAD"), cancellable = true)
    private void hayatemod$logHeal(float amount, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide()) {
            return;
        }
        if (DebugFlags.damageLog) {
            LOGGER.info("[DMG][HEAL] entity={}<{}> amount={} hp-before={}",
                    self.getScoreboardName(), self.getType().getDescriptionId(), amount, self.getHealth());
        }
        if (self instanceof ServerPlayer player && RpgHealth.healHook(player, amount)) {
            ci.cancel();
        }
    }
}
