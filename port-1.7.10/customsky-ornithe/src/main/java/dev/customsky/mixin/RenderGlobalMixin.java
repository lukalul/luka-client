package dev.customsky.mixin;

import dev.customsky.CustomSky;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import dev.luka.compat.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * On 1.7.10 renderSky also holds the End branch, so its calls come first in the method: glPushMatrix ordinal 0 is the End cube loop,
 * 1 the sunrise fan and 2 the sun and moon; bindTexture ordinal 0 is the End sky, 1 the sun and 2 the moon.
 */
@Mixin(RenderGlobal.class)
public abstract class RenderGlobalMixin {
    /** Right before the sun and moon, so they stay on top of the layers. */
    @Inject(method = "renderSky(F)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glPushMatrix()V", ordinal = 2, remap = false))
    private void customsky$layers(float partialTicks, CallbackInfo ci) {
        if (!CustomSky.active()) return;
        CustomSky.render(partialTicks);
        GlStateManager.tryBlendFuncSeparate(770, 1, 1, 0);
    }

    @Redirect(method = "renderSky(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureManager;bindTexture(Lnet/minecraft/util/ResourceLocation;)V", ordinal = 1))
    private void customsky$sun(TextureManager textures, ResourceLocation location) {
        textures.bindTexture(CustomSky.celestial(location, true));
    }

    @Redirect(method = "renderSky(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureManager;bindTexture(Lnet/minecraft/util/ResourceLocation;)V", ordinal = 2))
    private void customsky$moon(TextureManager textures, ResourceLocation location) {
        textures.bindTexture(CustomSky.celestial(location, false));
    }

    /** A pack's sky brings its own stars. */
    @Redirect(method = "renderSky(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;getStarBrightness(F)F"))
    private float customsky$stars(WorldClient world, float partialTicks) {
        return CustomSky.active() ? 0.0F : world.getStarBrightness(partialTicks);
    }

    /** The End's starfield box is replaced entirely when the pack has an End sky. */
    @Inject(method = "renderSky(F)V", at = @At("HEAD"), cancellable = true)
    private void customsky$end(float partialTicks, CallbackInfo ci) {
        WorldClient world = Minecraft.getMinecraft().theWorld;
        if (world == null || world.provider.dimensionId != 1 || !CustomSky.active()) return;
        GlStateManager.disableFog();
        GlStateManager.disableAlpha();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.depthMask(false);
        CustomSky.render(partialTicks);
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        ci.cancel();
    }
}
