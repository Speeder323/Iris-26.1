package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.opengl.GlRenderPass;
import org.spongepowered.asm.mixin.Mixin;

/**
 * No-op on 1.21.11: {@link com.mojang.blaze3d.systems.RenderPass} is an interface implemented directly by
 * {@link GlRenderPass}, so there is no wrapper/backend split to bridge (see MixinRenderPass and
 * MixinRenderPass_Stub). This class should be removed from the mixin config along with this file.
 */
@Mixin(GlRenderPass.class)
public class MixinRenderPass3 {
}
