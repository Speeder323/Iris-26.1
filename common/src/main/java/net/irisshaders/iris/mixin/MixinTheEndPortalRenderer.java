package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.blockentity.AbstractEndPortalRenderer;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;
import net.minecraft.client.renderer.blockentity.state.EndPortalRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import org.joml.Matrix3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractEndPortalRenderer.class)
public abstract class MixinTheEndPortalRenderer {
	@Unique
	private static final float RED = 0.075f;

	@Unique
	private static final float GREEN = 0.15f;

	@Unique
	private static final float BLUE = 0.2f;

	@Shadow
	protected abstract float getOffsetUp();

	@Shadow
	protected abstract float getOffsetDown();

	@Inject(method = "renderType", at = @At("HEAD"), cancellable = true)
	private void iris$renderType(CallbackInfoReturnable<RenderType> cir) {
		if (Iris.getCurrentPack().isPresent()) {
			cir.setReturnValue(RenderTypes.entitySolid(TheEndPortalRenderer.END_PORTAL_LOCATION));
		}
	}

	@Inject(method = {
		"method_73539",
		"lambda$submit$0"
	}, at = @At("HEAD"), cancellable = true, require = 1)
	private void iris$onRender(EndPortalRenderState renderState, PoseStack.Pose pose, VertexConsumer buffer, CallbackInfo ci) {
		if (Iris.getCurrentPack().isEmpty()) {
			return;
		}

		int overlay = OverlayTexture.NO_OVERLAY;
		int light = LightTexture.FULL_BRIGHT;

		ci.cancel();

		Matrix3f normal = pose.normal();

		// animation with a period of 100 seconds.
		// note that texture coordinates are wrapping, not clamping.
		float progress = (SystemTimeUniforms.TIMER.getFrameTimeCounter() * 0.01f) % 1f;

		float topHeight = this.getOffsetUp();
		float bottomHeight = this.getOffsetDown();

		this.quad(renderState, buffer, pose, normal, Direction.UP, progress, overlay, light,
			0.0f, topHeight, 1.0f,
			1.0f, topHeight, 1.0f,
			1.0f, topHeight, 0.0f,
			0.0f, topHeight, 0.0f);
		this.quad(renderState, buffer, pose, normal, Direction.DOWN, progress, overlay, light,
			0.0f, bottomHeight, 1.0f,
			0.0f, bottomHeight, 0.0f,
			1.0f, bottomHeight, 0.0f,
			1.0f, bottomHeight, 1.0f);
		this.quad(renderState, buffer, pose, normal, Direction.NORTH, progress, overlay, light,
			0.0f, topHeight, 0.0f,
			1.0f, topHeight, 0.0f,
			1.0f, bottomHeight, 0.0f,
			0.0f, bottomHeight, 0.0f);
		this.quad(renderState, buffer, pose, normal, Direction.WEST, progress, overlay, light,
			0.0f, topHeight, 1.0f,
			0.0f, topHeight, 0.0f,
			0.0f, bottomHeight, 0.0f,
			0.0f, bottomHeight, 1.0f);
		this.quad(renderState, buffer, pose, normal, Direction.SOUTH, progress, overlay, light,
			0.0f, topHeight, 1.0f,
			0.0f, bottomHeight, 1.0f,
			1.0f, bottomHeight, 1.0f,
			1.0f, topHeight, 1.0f);
		this.quad(renderState, buffer, pose, normal, Direction.EAST, progress, overlay, light,
			1.0f, topHeight, 1.0f,
			1.0f, bottomHeight, 1.0f,
			1.0f, bottomHeight, 0.0f,
			1.0f, topHeight, 0.0f);
	}

	@Unique
	private void quad(EndPortalRenderState renderState, VertexConsumer vertexConsumer, PoseStack.Pose pose, Matrix3f normal,
					  Direction direction, float progress, int overlay, int light,
					  float x1, float y1, float z1,
					  float x2, float y2, float z2,
					  float x3, float y3, float z3,
					  float x4, float y4, float z4) {
		if (!renderState.facesToShow.contains(direction)) {
			return;
		}

		float nx = direction.getStepX();
		float ny = direction.getStepY();
		float nz = direction.getStepZ();

		vertexConsumer.addVertex(pose, x1, y1, z1).setColor(RED, GREEN, BLUE, 1.0f)
			.setUv(0.0F + progress, 0.0F + progress).setOverlay(overlay).setLight(light)
			.setNormal(pose, nx, ny, nz);

		vertexConsumer.addVertex(pose, x2, y2, z2).setColor(RED, GREEN, BLUE, 1.0f)
			.setUv(0.0F + progress, 0.2F + progress).setOverlay(overlay).setLight(light)
			.setNormal(pose, nx, ny, nz);

		vertexConsumer.addVertex(pose, x3, y3, z3).setColor(RED, GREEN, BLUE, 1.0f)
			.setUv(0.2F + progress, 0.2F + progress).setOverlay(overlay).setLight(light)
			.setNormal(pose, nx, ny, nz);

		vertexConsumer.addVertex(pose, x4, y4, z4).setColor(RED, GREEN, BLUE, 1.0f)
			.setUv(0.2F + progress, 0.0F + progress).setOverlay(overlay).setLight(light)
			.setNormal(pose, nx, ny, nz);
	}
}
