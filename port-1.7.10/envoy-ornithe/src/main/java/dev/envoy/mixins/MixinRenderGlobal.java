package dev.envoy.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.envoy.render.CustomModelRenderer;
import dev.envoy.render.GlowRenderer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.EntityLivingBase;

/**
 * Hooks the Glowing capture into {@code RenderGlobal.renderEntities}, right when the camera and the
 * interpolated positions are available. Same injection point Eterion uses.
 */
@Mixin(RenderGlobal.class)
public abstract class MixinRenderGlobal {

    @Inject(method = "renderEntities", at = @At("HEAD"), require = 0)
    private void envoy$resetCustomEntityFrame(EntityLivingBase viewer, ICamera camera, float frameDelta, CallbackInfo callback) {
        CustomModelRenderer.beginFrame();
    }

    @Inject(
        method = "renderEntities",
        require = 0,
        at = @At(
            value = "INVOKE_STRING",
            target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V",
            args = "ldc=blockentities"))
    private void envoy$flushDeferredModels(EntityLivingBase viewer, ICamera camera, float frameDelta, CallbackInfo callback) {
        CustomModelRenderer.renderMissed(viewer, camera, frameDelta);
    }

    @Inject(
        method = "renderEntities",
        require = 0,
        at = @At(
            value = "INVOKE_STRING",
            target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V",
            args = "ldc=entities"))
    private void envoy$composeGlowBuffer(EntityLivingBase viewer, ICamera camera, float frameDelta, CallbackInfo ci) {
        GlowRenderer.renderEntitiesToFramebuffer(viewer, camera, frameDelta);
    }
}
