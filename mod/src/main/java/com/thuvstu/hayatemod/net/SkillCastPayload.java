package com.thuvstu.hayatemod.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client-to-server skill-use request. Slot is "special" (G / plain
 * right-click) or "heavy" (shift + right-click); the server validates.
 * Registered in main init so both physical sides know the type.
 */
public record SkillCastPayload(String slot) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SkillCastPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("hayatemod", "skill_cast"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SkillCastPayload> CODEC =
            StreamCodec.composite(
                    net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, SkillCastPayload::slot,
                    SkillCastPayload::new);

    public static void registerType() {
        PayloadTypeRegistry.playC2S().register(TYPE, CODEC);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
