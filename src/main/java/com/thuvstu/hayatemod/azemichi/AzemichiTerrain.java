package com.thuvstu.hayatemod.azemichi;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import com.thuvstu.hayatemod.block.GaleLampBlock;
import com.thuvstu.hayatemod.block.ModBlocks;

/**
 * Builds the azemichi itself: a long, straight, one-way dirt path between two rice
 * paddies, with trees and sugarcane on the fields, a vermilion torii at the goal,
 * and a small plaza beyond it.
 *
 * <p>Everything is plain {@code setBlock} calls (no jigsaw), so a whole 2000-block
 * alley is generated in a couple of seconds. Each row touches its (at most two)
 * chunks through {@code getChunk} first, so the writes land in loaded chunks.
 *
 * <p>Layout of a cross section (x, distance from the path centre):
 *
 * <pre>
 *  -12 .. -6   field: grass, occasional dirt, trees, sugarcane, flowers
 *   -5         embankment: grass
 *   -4 .. -2   paddy: a 1-deep water basin or a wheat plot on farmland
 *    -1 .. 1   the path: grass path on dirt
 * </pre>
 *
 * Water safety: every paddy cell is bordered by solid blocks at the same height
 * (embankment, path, or other paddy plots), and the paddy zone never touches the
 * first/last row of the corridor, so a source block can never flow out.
 */
public final class AzemichiTerrain {
	/** The corridor spans centerX +/- HALF_WIDTH; the session uses this to know "on the path". */
	public static final int HALF_WIDTH = 12;

	private AzemichiTerrain() {
	}

	/** The whole corridor from {@code startZ} to {@code capZ}, plus a short lead-in. */
	public static void buildCorridor(Level level, int centerX, int surfaceY, int startZ, int capZ,
			RandomSource random) {
		int firstZ = startZ - 4;
		int lastZ = capZ + 2;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockState water = Blocks.WATER.defaultBlockState();

		for (int z = firstZ; z <= lastZ; z++) {
			level.getChunk((centerX - HALF_WIDTH) >> 4, z >> 4);
			level.getChunk((centerX + HALF_WIDTH) >> 4, z >> 4);

			for (int dx = -HALF_WIDTH; dx <= HALF_WIDTH; dx++) {
				int x = centerX + dx;
				int depth = Math.abs(dx);
				boolean inPaddyZone = z >= startZ + 6 && z <= capZ - 6;
				boolean paddy = depth >= 2 && depth <= 4 && inPaddyZone;

				BlockState top;
				if (depth <= 1) {
					top = Blocks.DIRT_PATH.defaultBlockState();
				} else if (paddy) {
					top = random.nextBoolean() ? water
							: Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7, random.nextInt(8));
				} else if (depth == 5) {
					top = Blocks.GRASS_BLOCK.defaultBlockState();
				} else {
					top = random.nextInt(4) == 0 ? Blocks.DIRT.defaultBlockState()
							: Blocks.GRASS_BLOCK.defaultBlockState();
				}
				set(level, pos, x, surfaceY, z, top);

				if (depth <= 1) {
					set(level, pos, x, surfaceY - 1, z, Blocks.DIRT.defaultBlockState());
					set(level, pos, x, surfaceY - 2, z, Blocks.DIRT.defaultBlockState());
					set(level, pos, x, surfaceY - 3, z, Blocks.DIRT.defaultBlockState());
				} else if (paddy) {
					set(level, pos, x, surfaceY - 1, z, Blocks.FARMLAND.defaultBlockState());
					set(level, pos, x, surfaceY - 2, z, Blocks.DIRT.defaultBlockState());
				} else {
					set(level, pos, x, surfaceY - 1, z, Blocks.DIRT.defaultBlockState());
				}

				// Headroom: natural overhangs must not cover the path or the fields.
				int headroom = depth <= 1 ? 5 : (depth <= 5 ? 1 : 6);
				for (int y = surfaceY + 1; y <= surfaceY + headroom; y++) {
					setIfNotAir(level, pos, x, y, z, air);
				}
			}
		}

		plantFeatures(level, centerX, surfaceY, startZ, capZ, random);
	}

	private static void plantFeatures(Level level, int centerX, int surfaceY, int startZ, int capZ,
			RandomSource random) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int length = capZ - startZ;

		// A tree every ~25-40 m on the outer strip.
		for (int z = startZ + 20; z < capZ - 20; z += 25 + random.nextInt(15)) {
			int side = random.nextBoolean() ? 1 : -1;
			plantOak(level, pos, centerX + side * (7 + random.nextInt(4)), surfaceY, z);
		}

		// Sugarcane clusters on the paddy embankment.
		for (int i = 0; i < length / 8; i++) {
			int z = startZ + 10 + random.nextInt(Math.max(1, length - 20));
			int x = centerX + (random.nextBoolean() ? 5 : -5);
			int height = 2 + random.nextInt(2);
			for (int y = 0; y < height; y++) {
				set(level, pos, x, surfaceY + 1 + y, z, Blocks.SUGAR_CANE.defaultBlockState());
			}
		}

		// Flowers on the fields.
		for (int i = 0; i < length / 4; i++) {
			int z = startZ + random.nextInt(Math.max(1, length));
			int side = random.nextBoolean() ? 1 : -1;
			int x = centerX + side * (6 + random.nextInt(6));
			set(level, pos, x, surfaceY + 1, z, random.nextBoolean()
					? Blocks.POPPY.defaultBlockState() : Blocks.DANDELION.defaultBlockState());
		}

		// A pair of lit gale lamps flanking the entrance.
		int lampZ = startZ - 2;
		set(level, pos, centerX - 4, surfaceY + 1, lampZ, ModBlocks.GALE_BLOCK.defaultBlockState());
		set(level, pos, centerX - 4, surfaceY + 2, lampZ,
				ModBlocks.GALE_LAMP.defaultBlockState().setValue(GaleLampBlock.LIT, true));
		set(level, pos, centerX + 4, surfaceY + 1, lampZ, ModBlocks.GALE_BLOCK.defaultBlockState());
		set(level, pos, centerX + 4, surfaceY + 2, lampZ,
				ModBlocks.GALE_LAMP.defaultBlockState().setValue(GaleLampBlock.LIT, true));
	}

	/** A small hand-rolled oak: four logs, two leaf layers, one cap. */
	private static void plantOak(Level level, BlockPos.MutableBlockPos pos, int x, int surfaceY, int z) {
		int trunk = 4;
		for (int y = 1; y <= trunk; y++) {
			set(level, pos, x, surfaceY + y, z, Blocks.OAK_LOG.defaultBlockState());
		}
		BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.DISTANCE, 2);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (dx == 0 && dz == 0) {
					continue; // the trunk continues up through here
				}
				boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
				for (int dy = 0; dy <= (corner ? 0 : 1); dy++) {
					set(level, pos, x + dx, surfaceY + trunk + dy, z + dz, leaves);
				}
			}
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (Math.abs(dx) + Math.abs(dz) == 2) {
					continue; // a plus shape, not a square
				}
				set(level, pos, x + dx, surfaceY + trunk + 2, z + dz, leaves);
			}
		}
	}

	/** The vermilion torii marking the goal; rebuilds itself (clears its own spot). */
	public static void buildTorii(Level level, int centerX, int surfaceY, int goalZ) {
		clearTorii(level, centerX, surfaceY, goalZ);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState red = Blocks.WOOL.red().defaultBlockState();
		int y = surfaceY + 1;

		// pillars
		for (int dy = 0; dy <= 2; dy++) {
			set(level, pos, centerX - 2, y + dy, goalZ, red);
			set(level, pos, centerX + 2, y + dy, goalZ, red);
		}
		// nuki (lower crossbar) and the small lintel block in the middle
		for (int dx = -2; dx <= 2; dx++) {
			set(level, pos, centerX + dx, y + 2, goalZ, red);
		}
		set(level, pos, centerX, y + 3, goalZ, red);
		// kasagi (top beam)
		for (int dx = -3; dx <= 3; dx++) {
			set(level, pos, centerX + dx, y + 4, goalZ, red);
		}
	}

	/** Pokes the air back where a torii used to stand (the goal moved). */
	public static void clearTorii(Level level, int centerX, int surfaceY, int goalZ) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -4; dx <= 4; dx++) {
				for (int y = surfaceY + 1; y <= surfaceY + 5; y++) {
					set(level, pos, centerX + dx, y, goalZ + dz, air);
				}
			}
		}
	}

	/** The flat grass plaza beyond the corridor cap: where a cleared run ends. */
	public static void buildPlaza(Level level, int centerX, int surfaceY, int capZ) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int z = capZ + 2; z <= capZ + 10; z++) {
			level.getChunk((centerX - 6) >> 4, z >> 4);
			level.getChunk((centerX + 6) >> 4, z >> 4);
			for (int dx = -6; dx <= 6; dx++) {
				int x = centerX + dx;
				set(level, pos, x, surfaceY, z, Blocks.GRASS_BLOCK.defaultBlockState());
				set(level, pos, x, surfaceY - 1, z, Blocks.DIRT.defaultBlockState());
				for (int y = surfaceY + 1; y <= surfaceY + 3; y++) {
					setIfNotAir(level, pos, x, y, z, air);
				}
			}
		}
	}

	// ----------------------------------------------------------------- helpers

	private static void set(Level level, BlockPos.MutableBlockPos pos, int x, int y, int z, BlockState state) {
		// flags: UPDATE_NEIGHBORS | UPDATE_CLIENTS, no update limit
		level.setBlock(pos.set(x, y, z), state, 3, 0);
	}

	private static void setIfNotAir(Level level, BlockPos.MutableBlockPos pos, int x, int y, int z,
			BlockState air) {
		pos.set(x, y, z);
		if (!level.getBlockState(pos).isAir()) {
			level.setBlock(pos, air, 3, 0);
		}
	}
}
