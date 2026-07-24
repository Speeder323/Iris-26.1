package net.irisshaders.iris.mixin;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shadows.frustum.fallback.NonCullingFrustum;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In 1.21.11, LevelRenderer (not Camera) creates the culling frustum in prepareCullFrustum.
 * Returns a non-culling frustum when the current pipeline disables frustum culling.
 */
@Mixin(LevelRenderer.class)
public class MixinCamera {
	@Inject(method = "prepareCullFrustum", at = @At("HEAD"), cancellable = true)
	private void iris$disableFrustum(Matrix4f modelViewMatrix, Matrix4f projectionMatrix, Vec3 cameraPosition, CallbackInfoReturnable<Frustum> cir) {
		if (Iris.getPipelineManager().getPipeline().map(WorldRenderingPipeline::shouldDisableFrustumCulling).orElse(false)) {
			NonCullingFrustum frustum = new NonCullingFrustum();
			frustum.prepare(cameraPosition.x, cameraPosition.y, cameraPosition.z);
			cir.setReturnValue(frustum);
		}
	}
}
