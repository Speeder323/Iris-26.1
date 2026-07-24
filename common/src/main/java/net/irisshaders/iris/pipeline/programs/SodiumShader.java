package net.irisshaders.iris.pipeline.programs;

import com.google.common.collect.ImmutableSet;
import com.mojang.blaze3d.opengl.GlSampler;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlBuffer;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.GlUniformBlock;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.GlUniformFloat2v;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.GlUniformFloat3v;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.GlUniformInt;
import net.caffeinemc.mods.sodium.client.gl.shader.uniform.GlUniformMatrix4f;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ShaderBindingContext;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.features.FeatureFlags;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.gl.blending.BufferBlendOverride;
import net.irisshaders.iris.gl.blending.DepthColorStorage;
import net.irisshaders.iris.gl.program.ProgramImages;
import net.irisshaders.iris.gl.program.ProgramSamplers;
import net.irisshaders.iris.gl.program.ProgramUniforms;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.mixin.texture.TextureAtlasAccessor;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.samplers.IrisSamplers;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.builtin.BuiltinReplacementUniforms;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.irisshaders.iris.vertices.ImmediateState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL33C;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public class SodiumShader implements ChunkShaderInterface {
	private final GlUniformMatrix4f uniformModelViewMatrix;
	private final GlUniformMatrix4f uniformModelViewMatrixInv;
	private final GlUniformMatrix4f uniformProjectionMatrix;
	private final GlUniformMatrix4f uniformProjectionMatrixInv;
	private final GlUniformMatrix3f uniformNormalMatrix;
	private final GlUniformFloat3v uniformRegionOffset;
	private final GlUniformFloat2v uniformTexCoordShrink;
	private final GlUniformFloat2v uniformTexelSize;
	private final GlUniformInt uniformCurrentTime;
	private final GlUniformBlock uniformChunkData;
	private final ProgramImages images;
	private final ProgramSamplers samplers;
	private final ProgramUniforms uniforms;
	private final CustomUniforms customUniforms;
	private final BlendModeOverride blendModeOverride;
	private final List<BufferBlendOverride> bufferBlendOverrides;
	private final float alphaTest;
	private final boolean containsTessellation;
	private final boolean anisotropySupported;
	private final boolean isShadowPass;

	public SodiumShader(IrisRenderingPipeline pipeline, SodiumPrograms.Pass pass, ShaderBindingContext context,
						int handle, BlendModeOverride blendModeOverride,
						List<BufferBlendOverride> bufferBlendOverrides,
						CustomUniforms customUniforms, Supplier<ImmutableSet<Integer>> flipState, float alphaTest,
						boolean containsTessellation) {
		this.anisotropySupported = pipeline.hasFeature(FeatureFlags.TEXTURE_FILTERING);

		this.uniformModelViewMatrix = context.bindUniformOptional("iris_ModelViewMatrix", GlUniformMatrix4f::new);
		this.uniformModelViewMatrixInv = context.bindUniformOptional("iris_ModelViewMatrixInverse", GlUniformMatrix4f::new);
		this.uniformNormalMatrix = context.bindUniformOptional("iris_NormalMatrix", GlUniformMatrix3f::new);
		this.uniformProjectionMatrix = context.bindUniformOptional("iris_ProjectionMatrix", GlUniformMatrix4f::new);
		this.uniformProjectionMatrixInv = context.bindUniformOptional("iris_ProjectionMatrixInv", GlUniformMatrix4f::new);
		this.uniformRegionOffset = context.bindUniformOptional("u_RegionOffset", GlUniformFloat3v::new);
		this.uniformTexCoordShrink = context.bindUniformOptional("u_TexCoordShrink", GlUniformFloat2v::new);
		this.uniformCurrentTime = context.bindUniformOptional("iris_CurrentTime", GlUniformInt::new);
		this.uniformTexelSize = context.bindUniformOptional("iris_TexelSize", GlUniformFloat2v::new);
		this.uniformChunkData = context.bindUniformBlockOptional("iris_ChunkData", 0);

		this.alphaTest = alphaTest;
		this.containsTessellation = containsTessellation;

		this.isShadowPass = pass == SodiumPrograms.Pass.SHADOW || pass == SodiumPrograms.Pass.SHADOW_CUTOUT;

		this.uniforms = buildUniforms(pass, handle, customUniforms);
		this.customUniforms = customUniforms;
		this.samplers = buildSamplers(pipeline, pass, handle, isShadowPass, flipState);
		this.images = buildImages(pipeline, pass, handle, isShadowPass, flipState);

		this.blendModeOverride = blendModeOverride;
		this.bufferBlendOverrides = bufferBlendOverrides;
	}

	private ProgramUniforms buildUniforms(SodiumPrograms.Pass pass, int handle, CustomUniforms customUniforms) {
		ProgramUniforms.Builder builder = ProgramUniforms.builder(pass.name().toLowerCase(Locale.ROOT), handle);
		CommonUniforms.addDynamicUniforms(builder, FogMode.PER_VERTEX);
		customUniforms.assignTo(builder);
		BuiltinReplacementUniforms.addBuiltinReplacementUniforms(builder);
		customUniforms.mapholderToPass(builder, this);
		return builder.buildUniforms();
	}

	private ProgramSamplers buildSamplers(IrisRenderingPipeline pipeline, SodiumPrograms.Pass pass, int handle,
										  boolean isShadowPass, Supplier<ImmutableSet<Integer>> flipState) {
		ProgramSamplers.Builder builder = ProgramSamplers.builder(handle, IrisSamplers.SODIUM_RESERVED_TEXTURE_UNITS);
		pipeline.addGbufferOrShadowSamplers(builder, ProgramImages.builder(handle),
			flipState, isShadowPass, true, true, false);
		return builder.build();
	}

	private ProgramImages buildImages(IrisRenderingPipeline pipeline, SodiumPrograms.Pass pass, int handle,
									  boolean isShadowPass, Supplier<ImmutableSet<Integer>> flipState) {
		ProgramImages.Builder builder = ProgramImages.builder(handle);
		pipeline.addGbufferOrShadowSamplers(ProgramSamplers.builder(handle, IrisSamplers.SODIUM_RESERVED_TEXTURE_UNITS),
			builder, flipState, isShadowPass, true, true, false);
		return builder.build();
	}

	@Override
	public void setRegionOffset(float x, float y, float z) {
		if (this.uniformRegionOffset != null) {
			this.uniformRegionOffset.set(x, y, z);
		}
	}

	@Override
	public void setChunkData(GlBuffer data, int time) {
		if (this.uniformChunkData != null) {
			this.uniformChunkData.bindBuffer(data);
		}

		if (this.uniformCurrentTime != null) {
			this.uniformCurrentTime.set(time);
		}
	}

	@Override
	public void setModelViewMatrix(Matrix4fc matrix) {
		if (this.uniformModelViewMatrix != null) {
			this.uniformModelViewMatrix.set(matrix);
		}

		Matrix4f invertedMatrix = matrix.invert(new Matrix4f());

		if (this.uniformModelViewMatrixInv != null) {
			this.uniformModelViewMatrixInv.set(invertedMatrix);
		}

		if (this.uniformNormalMatrix != null) {
			Matrix3f normalMatrix = invertedMatrix.transpose3x3(new Matrix3f());
			this.uniformNormalMatrix.set(normalMatrix);
		}
	}

	@Override
	public void setProjectionMatrix(Matrix4fc matrix) {
		if (this.uniformProjectionMatrix != null) {
			this.uniformProjectionMatrix.set(matrix);
		}

		if (this.uniformProjectionMatrixInv != null) {
			this.uniformProjectionMatrixInv.set(matrix.invert(new Matrix4f()));
		}
	}

	@Override
	public void setupState(TerrainRenderPass pass, FogParameters fogParameters, GpuSampler gpuSampler) {
		DepthColorStorage.unlockDepthColor();

		applyBlendModes();

		if (Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline irp) {
			irp.onSetAlbedoTex(pass.getAtlas());
		}

		updateUniforms();
		images.update();

		if (isShadowPass) {
			GlStateManager._disableCull();
		}

		AbstractTexture textureAtlas = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
		double subTexelPrecision = 1 << RenderDevice.INSTANCE.getSubTexelPrecisionBits();
		double subTexelOffset = 1.0F / 32768.0F;

		if (this.uniformTexCoordShrink != null) {
			this.uniformTexCoordShrink.set(
				(float) (subTexelOffset - 1.0 / ((TextureAtlasAccessor) textureAtlas).callGetWidth() / subTexelPrecision),
				(float) (subTexelOffset - 1.0 / ((TextureAtlasAccessor) textureAtlas).callGetHeight() / subTexelPrecision)
			);
		}

		if (this.uniformTexelSize != null) {
			this.uniformTexelSize.set((float) (1.0 / textureAtlas.getTexture().getWidth(0)), (float) (1.0 / textureAtlas.getTexture().getHeight(0)));
		}

		int maxAnisotropy = Minecraft.getInstance().options.textureFiltering().get() == TextureFilteringMethod.ANISOTROPIC
			? Minecraft.getInstance().options.maxAnisotropyValue()
			: 1;
		bindTextures(pass.getAtlas(), (GlSampler) IrisSamplers.getTerrainCache(maxAnisotropy));

		if (containsTessellation) {
			ImmediateState.usingTessellation = true;
		}
	}

	private void bindTextures(GpuTextureView atlas, GlSampler sampler) {
		IrisRenderSystem.bindTextureToUnit(GL20C.GL_TEXTURE_2D, 0, atlas.texture().iris$getGlId());
		GlStateManager._activeTexture(GL20C.GL_TEXTURE0);
		GlStateManager._texParameter(GL20C.GL_TEXTURE_2D, GL12C.GL_TEXTURE_BASE_LEVEL, atlas.baseMipLevel());
		GlStateManager._texParameter(GL20C.GL_TEXTURE_2D, GL12C.GL_TEXTURE_MAX_LEVEL, atlas.baseMipLevel() + atlas.mipLevels() - 1);
		GL33C.glBindSampler(0, sampler.getId());

		GpuTextureView lightmap = Minecraft.getInstance().gameRenderer.lightTexture().getTextureView();
		GL33C.glBindSampler(IrisSamplers.LIGHTMAP_TEXTURE_UNIT, ((GlSampler) RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, false)).getId());

		IrisRenderSystem.bindTextureToUnit(GL20C.GL_TEXTURE_2D, IrisSamplers.LIGHTMAP_TEXTURE_UNIT, lightmap.texture().iris$getGlId());
		GlStateManager._activeTexture(GL20C.GL_TEXTURE0 + IrisSamplers.LIGHTMAP_TEXTURE_UNIT);
	}

	private void applyBlendModes() {
		if (blendModeOverride != null) {
			blendModeOverride.apply();
		}
		bufferBlendOverrides.forEach(BufferBlendOverride::apply);
	}

	private void updateUniforms() {
		CapturedRenderingState.INSTANCE.setCurrentAlphaTest(alphaTest);
		samplers.update();
		uniforms.update();
		customUniforms.push(this);
	}

	@Override
	public void resetState() {
		ProgramUniforms.clearActiveUniforms();
		ProgramSamplers.clearActiveSamplers();
		BlendModeOverride.restore();
		ImmediateState.usingTessellation = false;
	}
}
