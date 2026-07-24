package net.irisshaders.iris.pathways;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.mixin.GameRendererAccessor;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public class HandRenderer {
	public static final HandRenderer INSTANCE = new HandRenderer();
	public static final float DEPTH = 0.125F;
	private final RenderBuffers bufferSource = new RenderBuffers(Runtime.getRuntime().availableProcessors());
	private final PerspectiveProjectionMatrixBuffer cachedProjectionMatrixBuffer = new PerspectiveProjectionMatrixBuffer("hand (Iris)");
	private boolean ACTIVE;
	private boolean renderingSolid;
	private SubmitNodeStorage submitNodeCollector;
	private FeatureRenderDispatcher featureRenderDispatcher;

	public HandRenderer() {
		submitNodeCollector = new SubmitNodeStorage();
		featureRenderDispatcher = new FeatureRenderDispatcher(submitNodeCollector, Minecraft.getInstance().getBlockRenderer(), bufferSource.bufferSource(), Minecraft.getInstance().getAtlasManager(), bufferSource.outlineBufferSource(), bufferSource.crumblingBufferSource(), Minecraft.getInstance().font);
	}

	private PoseStack setupGlState(GameRenderer gameRenderer, Camera camera, Matrix4fc modelMatrix, float tickDelta) {
		final PoseStack poseStack = new PoseStack();

		// We need to scale the matrix by 0.125 so the hand doesn't clip through blocks.
		// The hand uses vanilla's dedicated hand FOV (ignoring the FOV slider), not the world projection.
		Matrix4f scaleMatrix = new Matrix4f().scale(1F, 1F, DEPTH);
		scaleMatrix.mul(gameRenderer.getProjectionMatrix(((GameRendererAccessor) gameRenderer).invokeGetFov(camera, tickDelta, false)));
		RenderSystem.setProjectionMatrix(cachedProjectionMatrixBuffer.getBuffer(scaleMatrix), ProjectionType.PERSPECTIVE);

		poseStack.setIdentity();
		((GameRendererAccessor) gameRenderer).invokeBobHurt(poseStack, tickDelta);
		if (Minecraft.getInstance().options.bobView().get()) {
			((GameRendererAccessor) gameRenderer).invokeBobView(poseStack, tickDelta);
		}

		return poseStack;
	}

	private boolean canRender(Camera camera, GameRenderer gameRenderer) {
		return !(camera.isDetached()
			|| !(camera.entity() instanceof Player)
			|| gameRenderer.isPanoramicMode()
			|| Minecraft.getInstance().options.hideGui
			|| (camera.entity() instanceof LivingEntity && ((LivingEntity) camera.entity()).isSleeping())
			|| Minecraft.getInstance().gameMode.getPlayerMode() == GameType.SPECTATOR);
	}

	public boolean isHandTranslucent(ItemStack itemStack) {
		Item item = itemStack.getItem();

		if (item instanceof BlockItem) {
			return ItemBlockRenderTypes.getChunkRenderType(((BlockItem) item).getBlock().defaultBlockState()) == ChunkSectionLayer.TRANSLUCENT;
		}

		return false;
	}

	public void renderSolid(Matrix4fc modelMatrix, float tickDelta, Camera camera, CameraRenderState cameraState, GameRenderer gameRenderer, WorldRenderingPipeline pipeline) {
		if (!canRender(camera, gameRenderer) || !gameRenderer.itemInHandRenderer.iris$isAnyHandSolid() || !Iris.isPackInUseQuick()) {
			return;
		}

		RenderSystem.backupProjectionMatrix();

		ACTIVE = true;

		PoseStack poseStack = setupGlState(gameRenderer, camera, modelMatrix, tickDelta);

		pipeline.setPhase(WorldRenderingPhase.HAND_SOLID);

		poseStack.pushPose();

		Profiler.get().push("iris_hand");

		renderingSolid = true;

		RenderSystem.getModelViewStack().pushMatrix();
		RenderSystem.getModelViewStack().set(poseStack.last().pose());

		gameRenderer.itemInHandRenderer.iris$renderHandsWithCustomRenderer(this, tickDelta, new PoseStack(), this.submitNodeCollector, Minecraft.getInstance().player, Minecraft.getInstance().getEntityRenderDispatcher().getPackedLightCoords(camera.entity(), tickDelta));

		Profiler.get().pop();

		RenderSystem.restoreProjectionMatrix();

		poseStack.popPose();
		RenderSystem.getModelViewStack().popMatrix();

		renderingSolid = false;

		pipeline.setPhase(WorldRenderingPhase.NONE);

		ACTIVE = false;
	}

	public void renderTranslucent(Matrix4fc modelMatrix, float tickDelta, Camera camera, CameraRenderState cameraState, GameRenderer gameRenderer, WorldRenderingPipeline pipeline) {
		if (!canRender(camera, gameRenderer) || !gameRenderer.itemInHandRenderer.iris$isAnyHandTranslucent() || !Iris.isPackInUseQuick()) {
			submitNodeCollector.endFrame();
			return;
		}

		RenderSystem.backupProjectionMatrix();

		ACTIVE = true;

		pipeline.setPhase(WorldRenderingPhase.HAND_TRANSLUCENT);

		PoseStack poseStack = setupGlState(gameRenderer, camera, modelMatrix, tickDelta);

		poseStack.pushPose();

		Profiler.get().push("iris_hand_translucent");

		RenderSystem.getModelViewStack().pushMatrix();
		RenderSystem.getModelViewStack().set(poseStack.last().pose());

		gameRenderer.itemInHandRenderer.iris$renderHandsWithCustomRenderer(this, tickDelta, new PoseStack(), submitNodeCollector, Minecraft.getInstance().player, Minecraft.getInstance().getEntityRenderDispatcher().getPackedLightCoords(camera.entity(), tickDelta));

		poseStack.popPose();

		Profiler.get().pop();
		submitNodeCollector.endFrame();

		RenderSystem.restoreProjectionMatrix();

		RenderSystem.getModelViewStack().popMatrix();

		pipeline.setPhase(WorldRenderingPhase.NONE);

		ACTIVE = false;
	}

	public boolean isActive() {
		return ACTIVE;
	}

	public boolean isRenderingSolid() {
		return renderingSolid;
	}

	public void destroy() {

	}

	public void endRender() {
		featureRenderDispatcher.renderAllFeatures();
		bufferSource.bufferSource().endBatch();
	}
}
