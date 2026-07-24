package net.irisshaders.iris.mixin.sky;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Disables the sunrise / sunset effect when blindness is active or when submerged in a fluid.
 * <p>
 * Inspired by <a href="https://github.com/CaffeineMC/sodium-fabric/pull/710">this Sodium PR</a>, but this implementation
 * takes a far more conservative approach and only disables specific parts of sky rendering in high-fog
 * situations.
 */
@Mixin(SkyRenderer.class)
public class MixinDimensionSpecialEffects {
	@Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
	private void iris$getSunriseColor(PoseStack poseStack, float f, int i, CallbackInfo ci) {
		if (iris$doesMobEffectBlockSky()) {
			ci.cancel();
		}

		FogType fogType = Minecraft.getInstance().gameRenderer.getMainCamera().getFluidInCamera();

		if (fogType != FogType.NONE) {
			ci.cancel();
		}
	}

	// Mirrors LevelRenderer#doesMobEffectBlockSky, which is private and has no accessor available here.
	@Unique
	private static boolean iris$doesMobEffectBlockSky() {
		return Minecraft.getInstance().gameRenderer.getMainCamera().entity() instanceof LivingEntity livingEntity
			&& (livingEntity.hasEffect(MobEffects.BLINDNESS) || livingEntity.hasEffect(MobEffects.DARKNESS));
	}
}
