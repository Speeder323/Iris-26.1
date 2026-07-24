package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.systems.RenderPass;
import net.irisshaders.iris.mixinterface.CustomPass;
import net.irisshaders.iris.mixinterface.RenderPassInterface;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(RenderPass.class)
public interface MixinRenderPass_Stub extends RenderPassInterface {

	@Override
	default void iris$setCustomPass(CustomPass pass) {
		throw new UnsupportedOperationException();
	}

	@Override
	default CustomPass iris$getCustomPass() {
		throw new UnsupportedOperationException();
	}
}
