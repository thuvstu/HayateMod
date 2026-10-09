package com.thuvstu.hayatemod.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

import com.thuvstu.hayatemod.HayateMod;
import com.thuvstu.hayatemod.entity.GaleSpiritEntity;

/**
 * Renders {@link GaleSpiritEntity}.
 *
 * <p>Only three members are needed: the model (baked straight from the layer
 * definition - no {@code ModelLayerRegistry} round trip), the shadow radius, and the
 * {@code createRenderState} factory that 26.x asks every renderer for.
 */
public class GaleSpiritRenderer
		extends MobRenderer<GaleSpiritEntity, LivingEntityRenderState, GaleSpiritModel> {
	private static final Identifier TEXTURE = HayateMod.id("textures/entity/gale_spirit.png");

	public GaleSpiritRenderer(EntityRendererProvider.Context context) {
		super(context, new GaleSpiritModel(GaleSpiritModel.createBodyLayer().bakeRoot()), 0.35F);
	}

	@Override
	public LivingEntityRenderState createRenderState() {
		return new LivingEntityRenderState();
	}

	@Override
	public Identifier getTextureLocation(LivingEntityRenderState state) {
		return TEXTURE;
	}
}
