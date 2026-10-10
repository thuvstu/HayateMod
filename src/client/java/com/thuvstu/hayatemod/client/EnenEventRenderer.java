package com.thuvstu.hayatemod.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

import com.thuvstu.hayatemod.HayateMod;
import com.thuvstu.hayatemod.azemichi.EnenEventEntity;

/**
 * Renders the azemichi event marker. Same shape as the gale spirit renderer:
 * the baked model, a shadow radius, and the {@code createRenderState} factory
 * that 26.x asks every renderer for. The marker's identity (vending machine,
 * phone booth, ...) is its custom name, which vanilla draws above the entity.
 */
public class EnenEventRenderer
		extends MobRenderer<EnenEventEntity, LivingEntityRenderState, EnenEventModel> {
	private static final Identifier TEXTURE = HayateMod.id("textures/entity/azemichi_event.png");

	public EnenEventRenderer(EntityRendererProvider.Context context) {
		super(context, new EnenEventModel(EnenEventModel.createBoardLayer().bakeRoot()), 0.3F);
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
