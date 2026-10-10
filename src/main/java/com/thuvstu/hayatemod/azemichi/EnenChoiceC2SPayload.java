package com.thuvstu.hayatemod.azemichi;

import java.nio.ByteBuffer;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.custom.CustomPacketPayload;
import net.minecraft.network.protocol.custom.PayloadType;
import net.minecraft.network.syncher.StreamCodec;

import com.thuvstu.hayatemod.HayateMod;

/**
 * The client tells the server which answer the player picked for the open azemichi
 * question (sent from the choice keybinds, see the client's {@code EnenChoiceKeys}).
 *
 * <p>The server simply ignores the packet when the player has no question open,
 * so pressing the keys outside a run costs one tiny packet and nothing else.
 */
public record EnenChoiceC2SPayload(int choice) implements CustomPacketPayload {
	public static final PayloadType<EnenChoiceC2SPayload> TYPE = PayloadType.create(
			HayateMod.id("azemichi_choice"));

	public static final StreamCodec<ByteBuffer, EnenChoiceC2SPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, EnenChoiceC2SPayload::choice, EnenChoiceC2SPayload::new);

	@Override
	public PayloadType<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
