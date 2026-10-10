package com.thuvstu.hayatemod.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * The azemichi event marker: a wooden post with a paper signboard on top.
 *
 * <p>Model space is the vanilla one: Y grows downwards, one unit is 1/16 of a
 * block, y=24 is the ground. The post runs from y=24 (feet) up to y=8, and the
 * board floats at y=4..12.
 *
 * <p>Texture layout on the 64x32 sheet (the vanilla cube unroll):
 * <ul>
 *     <li>post (2x16x2 at texOffs(0,0)): occupies x=0..7, y=0..17</li>
 *     <li>board (10x8x2 at texOffs(16,0)): occupies x=16..51, y=0..9 - and its
 *         10x8 front face is x=30..39, y=2..9, where the sign's art is drawn</li>
 * </ul>
 */
public class EnenEventModel extends EntityModel<LivingEntityRenderState> {
	private static final float POST_TOP = 8.0F;
	private static final float BOARD_TOP = 4.0F;

	private final ModelPart post;
	private final ModelPart board;

	public EnenEventModel(ModelPart root) {
		super(root);
		this.post = root.getChild("post");
		this.board = root.getChild("board");
	}

	public static LayerDefinition createBoardLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		root.addOrReplaceChild("post", new CubeListBuilder()
						.texOffs(0, 0)
						.addBox(-1.0F, POST_TOP, -1.0F, 2.0F, 16.0F, 2.0F),
				PartPose.offset(0.0F, 0.0F, 0.0F));
		root.addOrReplaceChild("board", new CubeListBuilder()
						.texOffs(16, 0)
						.addBox(-5.0F, BOARD_TOP, -1.0F, 10.0F, 8.0F, 2.0F),
				PartPose.offset(0.0F, 0.0F, 0.0F));

		return LayerDefinition.create(mesh, 64, 32);
	}

	@Override
	public void setupAnim(LivingEntityRenderState state) {
		super.setupAnim(state);
		// The signboard is static: the encounter "talks" through its name, not its body.
	}
}
