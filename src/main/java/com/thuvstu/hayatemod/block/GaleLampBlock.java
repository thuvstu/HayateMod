package com.thuvstu.hayatemod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A lamp that can be toggled by right-clicking it.
 *
 * <p>Two things make this work:
 * <ul>
 *     <li>the {@code lit} block state property (see {@link #createBlockStateDefinition})</li>
 *     <li>the light level callback registered through
 *     {@code BlockBehaviour.Properties#lightLevel(...)} in {@link ModBlocks}</li>
 * </ul>
 */
public class GaleLampBlock extends Block {
	public static final BooleanProperty LIT = BooleanProperty.create("lit");

	public GaleLampBlock(Properties settings) {
		super(settings);

		// Set the default state of the block to be unlit.
		registerDefaultState(defaultBlockState().setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIT);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (!player.getAbilities().mayBuild) {
			// Skip if the player isn't allowed to modify the level.
			return InteractionResult.PASS;
		}

		boolean lit = state.getValue(LIT);
		level.setBlockAndUpdate(pos, state.setValue(LIT, !lit));

		// Play a click sound to emphasise the interaction.
		level.playSound(player, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 1.0F, lit ? 0.6F : 0.9F);

		return InteractionResult.SUCCESS;
	}

	public static int getLuminance(BlockState state) {
		return state.getValue(LIT) ? 15 : 0;
	}
}
