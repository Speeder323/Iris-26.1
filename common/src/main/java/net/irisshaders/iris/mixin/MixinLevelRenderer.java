package net.irisshaders.iris.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.MojLambdas;
import net.irisshaders.iris.NeoLambdas;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.compat.dh.DHCompat;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.layer.IsOutlineRenderStateShard;
import net.irisshaders.iris.layer.OuterWrappedRenderType;
import net.irisshaders.iris.pathways.HandRenderer;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shadows.frustum.fallback.NonCullingFrustum;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.uniforms.IrisTimeUniforms;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL43C;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.joml.Vector4f;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Unique
	private WorldRenderingPipeline pipeline;

	@Shadow
	private RenderBuffers renderBuffers;

	@Shadow
	@Final
	private LevelTargetBundle targets;
	@Shadow
	@Final
	private LevelRenderState levelRenderState;

	@Shadow
	@Final
	private CloudRenderer cloudRenderer;
	@Shadow
	private @Nullable ClientLevel level;
	private boolean warned;

	@Unique
	private boolean disableFrustumCulling;

	@WrapOperation(method = {"method_75413", "lambda$addLateDebugPass$0"}, require = 1, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V"))
	private void skip(CommandEncoder instance, GpuTexture texture, double v, Operation<Void> original) {
		if (!IrisApi.getInstance().isShaderPackInUse()) {
			original.call(instance, texture, v);
		}
	}

	@Inject(method = "prepareCullFrustum", at = @At("HEAD"), cancellable = true)
	private void iris$disableFrustum(Matrix4f modelViewMatrix, Matrix4f projectionMatrix, Vec3 cameraPosition, CallbackInfoReturnable<Frustum> cir) {
		if (this.disableFrustumCulling) {
			NonCullingFrustum frustum = new NonCullingFrustum();
			frustum.prepare(cameraPosition.x, cameraPosition.y, cameraPosition.z);
			cir.setReturnValue(frustum);
		}
	}

	// Begin shader rendering after buffers have been cleared.
	// At this point we've ensured that Minecraft's main framebuffer is cleared.
	// This is important or else very odd issues will happen with shaders that have a final pass that doesn't write to
	// all pixels.
	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void iris$setupPipeline(GraphicsResourceAllocator resourceAllocator, DeltaTracker deltaTracker, boolean renderOutline, Camera camera, Matrix4f modelViewMatrix, Matrix4f projectionMatrix, Matrix4f cullingMatrix, GpuBufferSlice terrainFog, Vector4f fogColor, boolean shouldRenderSky, CallbackInfo ci) {
		DHCompat.checkFrame();

		IrisTimeUniforms.updateTime();
		CapturedRenderingState.INSTANCE.setGbufferModelView(modelViewMatrix);
		CapturedRenderingState.INSTANCE.setGbufferProjection(projectionMatrix);
		float fakeTickDelta = deltaTracker.getGameTimeDeltaPartialTick(false);
		CapturedRenderingState.INSTANCE.setTickDelta(fakeTickDelta);
		if (((CloudRendererAccessor) this.cloudRenderer).getTexture() != null) {
			CapturedRenderingState.INSTANCE.setCloudTime((this.level.getGameTime() % (((CloudRendererAccessor) this.cloudRenderer).getTexture().width() * 400) + fakeTickDelta) * 0.03F);
		} else {
			CapturedRenderingState.INSTANCE.setCloudTime(0);
		}

		pipeline = Iris.getPipelineManager().preparePipeline(Iris.getCurrentDimension());

		this.disableFrustumCulling = pipeline.shouldDisableFrustumCulling();

    	pipeline.beginLevelRendering();
		pipeline.setPhase(WorldRenderingPhase.NONE);
		IrisRenderSystem.backupAndDisableCullingState(pipeline.shouldDisableOcclusionCulling());

		if (Iris.shouldActivateWireframe() && this.minecraft.isLocalServer()) {
			IrisRenderSystem.setPolygonMode(GL43C.GL_LINE);
		}
	}

	// Begin shader rendering after buffers have been cleared.
	// At this point we've ensured that Minecraft's main framebuffer is cleared.
	// This is important or else very odd issues will happen with shaders that have a final pass that doesn't write to
	// all pixels.
	@Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/framegraph/FramePass;executes(Ljava/lang/Runnable;)V", ordinal = 0, shift = At.Shift.AFTER))
	private void iris$beginLevelRender(GraphicsResourceAllocator resourceAllocator, DeltaTracker deltaTracker, boolean renderOutline, Camera camera, Matrix4f modelViewMatrix, Matrix4f projectionMatrix, Matrix4f cullingMatrix, GpuBufferSlice terrainFog, Vector4f fogColor, boolean shouldRenderSky, CallbackInfo ci, @Local FrameGraphBuilder frameGraphBuilder, @Local(ordinal = 0) FramePass clearPass) {
		FramePass framePass = frameGraphBuilder.addPass("iris_setup");
		this.targets.main = framePass.readsAndWrites(this.targets.main);
		framePass.requires(clearPass);
		framePass.executes(() -> {
			GpuBufferSlice params = RenderSystem.getShaderFog();
			pipeline.onBeginClear();
			RenderSystem.setShaderFog(params);
		});
	}


	// Inject a bit early so that we can end our rendering before mods like VoxelMap (which inject at RETURN)
	// render their waypoint beams.
	@Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4fStack;popMatrix()Lorg/joml/Matrix4fStack;"))
	private void iris$endLevelRender(GraphicsResourceAllocator resourceAllocator, DeltaTracker deltaTracker, boolean renderOutline, Camera camera, Matrix4f modelViewMatrix, Matrix4f projectionMatrix, Matrix4f cullingMatrix, GpuBufferSlice terrainFog, Vector4f fogColor, boolean shouldRenderSky, CallbackInfo ci) {
		HandRenderer.INSTANCE.renderTranslucent(modelViewMatrix, deltaTracker.getGameTimeDeltaPartialTick(true), camera, levelRenderState.cameraRenderState, this.minecraft.gameRenderer, pipeline);
		Profiler.get().popPush("iris_final");

		if (Iris.shouldActivateWireframe() && this.minecraft.isLocalServer()) {
			IrisRenderSystem.setPolygonMode(GL43C.GL_FILL);
		}
		pipeline.finalizeLevelRendering();
		pipeline = null;

		if (!warned) {
			warned = true;
			Iris.getUpdateChecker().getBetaInfo().ifPresent(info ->
				Minecraft.getInstance().gui.getChat().addMessage(Component.literal("A new beta is out for Iris " + info.betaTag + ". Please redownload it.").withStyle(ChatFormatting.BOLD, ChatFormatting.RED)));
		}

		IrisRenderSystem.restoreCullingState();

	}

	// Setup shadow terrain & render shadows before the main terrain setup. We need to do things in this order to
	// avoid breaking other mods such as Light Overlay: https://github.com/IrisShaders/Iris/issues/1356
	@Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;prepareCullFrustum(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/client/renderer/culling/Frustum;", shift = At.Shift.AFTER))
	private void iris$renderTerrainShadows(GraphicsResourceAllocator resourceAllocator, DeltaTracker deltaTracker, boolean renderOutline, Camera camera, Matrix4f modelViewMatrix, Matrix4f projectionMatrix, Matrix4f cullingMatrix, GpuBufferSlice terrainFog, Vector4f fogColor, boolean shouldRenderSky, CallbackInfo ci) {
		pipeline.renderShadows((LevelRendererAccessor) this, camera, this.levelRenderState.cameraRenderState);
	}

	@Inject(method = { MojLambdas.RENDER_SKY, NeoLambdas.NEO_RENDER_SKY }, require = 1, at = @At(value = "HEAD"))
	private static void iris$beginSky(CallbackInfo ci) {
		// Use CUSTOM_SKY until levelFogColor is called as a heuristic to catch FabricSkyboxes.
		Iris.getPipelineManager().getPipeline().ifPresent(p -> p.setPhase(WorldRenderingPhase.CUSTOM_SKY));

		// We've changed the phase, but vanilla doesn't update the shader program at this point before rendering stuff,
		// so we need to manually refresh the shader program so that the correct shader override gets applied.
		// TODO: Move the injection instead
	}

	@Inject(method = { MojLambdas.RENDER_SKY, NeoLambdas.NEO_RENDER_SKY }, require = 1, at = @At(value = "RETURN"))
	private static void iris$endSky(CallbackInfo ci) {
		Iris.getPipelineManager().getPipeline().ifPresent(p -> p.setPhase(WorldRenderingPhase.NONE));
	}

	@Inject(method = { MojLambdas.RENDER_CLOUDS, NeoLambdas.NEO_RENDER_CLOUDS }, require = 1, at = @At(value = "HEAD"))
	private void iris$beginClouds(CallbackInfo ci) {
		pipeline.setPhase(WorldRenderingPhase.CLOUDS);
	}

	@Inject(method = { MojLambdas.RENDER_CLOUDS, NeoLambdas.NEO_RENDER_CLOUDS }, require = 1, at = @At("RETURN"))
	private void iris$endClouds(CallbackInfo ci) {
		pipeline.setPhase(WorldRenderingPhase.NONE);
	}


	@WrapOperation(method = { MojLambdas.RENDER_MAIN_PASS, NeoLambdas.NEO_RENDER_MAIN_PASS }, require = 1, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/blaze3d/textures/GpuSampler;)V"))
	private void iris$beginTerrainLayer(ChunkSectionsToRender instance, ChunkSectionLayerGroup chunkSectionLayerGroup, GpuSampler gpuSampler, Operation<Void> original) {
		pipeline.setPhase(WorldRenderingPhase.fromTerrainRenderType(chunkSectionLayerGroup));
		original.call(instance, chunkSectionLayerGroup, gpuSampler);
		pipeline.setPhase(WorldRenderingPhase.NONE);
	}

	@Inject(method = { MojLambdas.RENDER_WEATHER, NeoLambdas.NEO_RENDER_WEATHER }, require = 1, at = @At(value = "HEAD"))
	private void iris$beginWeather(CallbackInfo ci) {
		pipeline.setPhase(WorldRenderingPhase.RAIN_SNOW);
	}


	@Inject(method = { MojLambdas.RENDER_WEATHER, NeoLambdas.NEO_RENDER_WEATHER }, require = 1, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/WorldBorderRenderer;render(Lnet/minecraft/client/renderer/state/WorldBorderRenderState;Lnet/minecraft/world/phys/Vec3;DD)V"))
	private void iris$beginWorldBorder(CallbackInfo ci) {
		pipeline.setPhase(WorldRenderingPhase.WORLD_BORDER);
	}

	@Inject(method = { MojLambdas.RENDER_WEATHER, NeoLambdas.NEO_RENDER_WEATHER }, require = 1, at = @At(value = "RETURN"))
	private void iris$endWeather(CallbackInfo ci) {
		pipeline.setPhase(WorldRenderingPhase.NONE);
	}

	@ModifyArg(method = "renderBlockOutline",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;getBuffer(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
	private RenderType iris$beginBlockOutline(RenderType type) {
		return new OuterWrappedRenderType("iris:is_outline", type, IsOutlineRenderStateShard.INSTANCE);
	}

	// TODO this needs to be more consistent.
	@Inject(method = { MojLambdas.RENDER_MAIN_PASS, NeoLambdas.NEO_RENDER_MAIN_PASS }, require = 1, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch()V", ordinal = 1))
	private void iris$beginTranslucents(CallbackInfo ci, @Local(ordinal = 0, argsOnly = true) Matrix4f modelMatrix) {
		pipeline.beginHand();
		HandRenderer.INSTANCE.renderSolid(modelMatrix, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true), Minecraft.getInstance().gameRenderer.getMainCamera(), levelRenderState.cameraRenderState, Minecraft.getInstance().gameRenderer, pipeline);
		Profiler.get().popPush("iris_pre_translucent");
		pipeline.beginTranslucents();
	}
}
