package dev.customsky.mixin;

import dev.customsky.CustomSky;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderGlobal.class)
public abstract class RenderGlobalMixin {
    /** Right before the sun and moon, so they stay on top of the layers. */
    @Inject(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;pushMatrix()V", ordinal = 1))
    private void customsky$layers(float partialTicks, int pass, CallbackInfo ci) {
        if (!CustomSky.active()) return;
        CustomSky.render(partialTicks);
        GlStateManager.tryBlendFuncSeparate(770, 1, 1, 0);
    }

    @Redirect(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureManager;bindTexture(Lnet/minecraft/util/ResourceLocation;)V", ordinal = 0))
    private void customsky$sun(TextureManager textures, ResourceLocation location) {
        textures.bindTexture(CustomSky.celestial(location, true));
    }

    @Redirect(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureManager;bindTexture(Lnet/minecraft/util/ResourceLocation;)V", ordinal = 1))
    private void customsky$moon(TextureManager textures, ResourceLocation location) {
        textures.bindTexture(CustomSky.celestial(location, false));
    }

    /** A pack's sky brings its own stars. */
    @Redirect(method = "renderSky(FI)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;getStarBrightness(F)F"))
    private float customsky$stars(WorldClient world, float partialTicks) {
        return CustomSky.active() ? 0.0F : world.getStarBrightness(partialTicks);
    }

    /** The End's starfield box is replaced entirely when the pack has an End sky. */
    @Inject(method = "renderSkyEnd", at = @At("HEAD"), cancellable = true)
    private void customsky$end(CallbackInfo ci) {
        if (!CustomSky.active()) return;
        GlStateManager.disableFog();
        GlStateManager.disableAlpha();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.depthMask(false);
        CustomSky.render(0.0F);
        GlStateManager.depthMask(true);
        GlStateManager.enableAlpha();
        ci.cancel();
    }
}
