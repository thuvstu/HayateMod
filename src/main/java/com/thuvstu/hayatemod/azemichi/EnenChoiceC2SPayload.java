package com.thuvstu.hayatemod.azemichi;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.thuvstu.hayatemod.HayateMod;

/**
 * The client tells the server which answer the player picked for the open azemichi
 * question (sent from the choice keybinds, see the client's {@code EnenChoiceKeys}).
 *
 * <p>The server simply ignores the packet when the player has no question open,
 * so pressing the keys outside a run costs one tiny packet and nothing else.
 *
 * <p>26.x payload note: a payload "type" is just an id now; the codec is handed
 * to the Fabric registry separately, and play packets travel in a
 * {@link RegistryFriendlyByteBuf}.
 */
public record EnenChoiceC2SPayload(int choice) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<EnenChoiceC2SPayload> TYPE =
			new CustomPacketPayload.Type<>(HayateMod.id("azemichi_choice"));

	public static final StreamCodec<RegistryFriendlyByteBuf, EnenChoiceC2SPayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.VAR_INT, EnenChoiceC2SPayload::choice, EnenChoiceC2SPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
