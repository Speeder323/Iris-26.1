package net.irisshaders.iris.compat.sodium.mixin;

import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * No-op stub. Sodium 0.8.x has no UniformBufferManager - terrain uniforms are plain GL program
 * uniforms bound through ChunkShaderInterface when a chunk program is used, so there is no shared
 * uniform buffer state that needs to be duplicated between the shadow pass and the main pass.
 * This class is kept (as an empty mixin) because it is registered in mixins.iris.compat.sodium.json.
 */
@Mixin(SodiumWorldRenderer.class)
public class MixinUniformData {
}
