package com.thuvstu.hayatemod.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/**
 * The gale spirit's model: a hovering cube with a head and two trailing ribbons.
 *
 * <p>Model space is the vanilla one, which is worth writing down because it trips
 * everybody up: Y grows <em>downwards</em>, one unit is 1/16 of a block, and y=24 is
 * the ground. That is why the body sits at y=12 (halfway up a block) and the head
 * above it at y=4.
 *
 * <p>26.x splits rendering into a render state, so models animate from
 * {@link LivingEntityRenderState} rather than from the entity itself. That is also
 * why the model lives on the client source set and is never touched by the server.
 */
public class GaleSpiritModel extends EntityModel<LivingEntityRenderState> {
	private static final float BODY_Y = 12.0F;
	private static final float HEAD_Y = 4.0F;

	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart leftRibbon;
	private final ModelPart rightRibbon;

	public GaleSpiritModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.head = root.getChild("head");
		this.leftRibbon = root.getChild("left_ribbon");
		this.rightRibbon = root.getChild("right_ribbon");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		root.addOrReplaceChild("body", new CubeListBuilder()
						.texOffs(0, 0)
						.addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F),
				PartPose.offset(0.0F, BODY_Y, 0.0F));
		root.addOrReplaceChild("head", new CubeListBuilder()
						.texOffs(32, 0)
						.addBox(-3.5F, -3.5F, -3.5F, 7.0F, 7.0F, 7.0F),
				PartPose.offset(0.0F, HEAD_Y, 0.0F));

		CubeListBuilder leftRibbon = new CubeListBuilder()
				.texOffs(0, 16)
				.addBox(-1.0F, -5.0F, -2.0F, 2.0F, 10.0F, 4.0F);
		CubeListBuilder rightRibbon = new CubeListBuilder()
				.texOffs(0, 16)
				.addBox(-1.0F, -5.0F, -2.0F, 2.0F, 10.0F, 4.0F);
		root.addOrReplaceChild("left_ribbon", leftRibbon, PartPose.offset(-5.0F, BODY_Y, 0.0F));
		root.addOrReplaceChild("right_ribbon", rightRibbon, PartPose.offset(5.0F, BODY_Y, 0.0F));

		return LayerDefinition.create(mesh, 64, 32);
	}

	@Override
	public void setupAnim(LivingEntityRenderState state) {
		super.setupAnim(state);

		// a slow hover, plus a flap on the ribbons
		float bob = Mth.sin(state.ageInTicks * 0.16F) * 0.09F;
		float flap = Mth.cos(state.ageInTicks * 0.22F) * 0.35F;

		this.body.y = BODY_Y + bob;
		this.head.y = HEAD_Y + bob;
		this.leftRibbon.y = BODY_Y + bob;
		this.rightRibbon.y = BODY_Y + bob;
		this.leftRibbon.zRot = flap;
		this.rightRibbon.zRot = -flap;

		this.head.yRot = state.yRot * ((float) Math.PI / 180.0F);
		this.head.xRot = state.xRot * ((float) Math.PI / 180.0F);
	}
}
