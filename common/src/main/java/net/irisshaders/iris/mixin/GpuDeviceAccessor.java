package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.systems.GpuDevice;

/**
 * Not a mixin on 1.21.11: {@link com.mojang.blaze3d.opengl.GlDevice} implements {@link GpuDevice} directly,
 * so there is no backend indirection to unwrap. Call sites should be changed to
 * {@code (GlDevice) RenderSystem.getDevice()}; this interface only exists so those call sites keep compiling
 * until they are updated, after which this file should be deleted (and removed from the mixin config).
 */
public interface GpuDeviceAccessor {
	GpuDevice getBackend();
}
