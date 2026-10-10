package com.thuvstu.hayatemod.azemichi;

import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import com.thuvstu.hayatemod.HayateMod;

/**
 * The azemichi's per-world bookkeeping: the once-per-day day number, the clear
 * count (which picks the next goal) and whether Bishatatsu has been defeated.
 *
 * <p>26.x no longer exposes a mod-writable {@code CompoundTag} on the player
 * (saving is codec based), so the numbers live in an ordinary world
 * {@link SavedData} instead - same file layout as the scoreboard's.
 */
public final class EnenSaveData extends SavedData {
	public static final SavedDataType<EnenSaveData> TYPE = new SavedDataType<>(
			HayateMod.id("enen_save"), EnenSaveData::new,
			Packed.CODEC.xmap(EnenSaveData::new, EnenSaveData::getData),
			DataFixTypes.LEVEL);

	private Packed data;

	private EnenSaveData() {
		this(Packed.EMPTY);
	}

	public EnenSaveData(Packed data) {
		this.data = data;
	}

	public Packed getData() {
		return this.data;
	}

	public void setData(Packed data) {
		if (!data.equals(this.data)) {
			this.data = data;
			this.setDirty();
		}
	}

	/** One row per player who ever touched the alley. */
	public record Stats(long lastDay, int clears, boolean bossDefeated) {
		public static final Stats ZERO = new Stats(0L, 0, false);

		public static final Codec<Stats> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.optionalFieldOf("lastDay", 0L).forGetter(Stats::lastDay),
				Codec.INT.optionalFieldOf("clears", 0).forGetter(Stats::clears),
				Codec.BOOL.optionalFieldOf("bossDefeated", false).forGetter(Stats::bossDefeated)
		).apply(i, Stats::new));
	}

	/** The whole data file: player id to stats. */
	public record Packed(Map<UUID, Stats> players) {
		public static final Packed EMPTY = new Packed(Map.of());

		// Map keys must encode as strings (NBT object keys): UUIDUtil.CODEC is
		// an int array and blows up the world save with "Not a string".
		public static final Codec<Packed> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.unboundedMap(UUIDUtil.STRING_CODEC, Stats.CODEC)
						.optionalFieldOf("players", Map.of())
						.forGetter(Packed::players)
		).apply(i, Packed::new));
	}
}
