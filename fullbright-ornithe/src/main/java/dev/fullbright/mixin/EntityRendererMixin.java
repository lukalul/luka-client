package dev.fullbright.mixin;

import dev.fullbright.Fullbright;
import java.util.Arrays;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the computed light map with a white one while Fullbright is on; vanilla's own code runs untouched when it is off. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Shadow private int[] lightmapColors;
    @Shadow private DynamicTexture lightmapTexture;
    @Shadow private boolean lightmapUpdateNeeded;
    /** True once the white light map has been uploaded; vanilla re-flags an update every tick, but nothing changes while it stays white. */
    @Unique private boolean fullbright$uploaded;

    @Inject(method = "updateLightmap", at = @At("HEAD"), cancellable = true, require = 0)
    private void fullbright$lightmap(float partialTicks, CallbackInfo ci) {
        try {
            if (Fullbright.LightmapRefresh.request) {
                Fullbright.LightmapRefresh.request = false;
                lightmapUpdateNeeded = true;
                fullbright$uploaded = false;
            }
            if (!Fullbright.enabled) {
                fullbright$uploaded = false;
                return;
            }
            if (!lightmapUpdateNeeded || fullbright$uploaded) {
                lightmapUpdateNeeded = false;
                ci.cancel();
                return;
            }
            fullbright$uploaded = true;
            Arrays.fill(lightmapColors, 0xFFFFFFFF);
            lightmapTexture.updateDynamicTexture();
            lightmapUpdateNeeded = false;
            ci.cancel();
        } catch (Throwable t) {
            Fullbright.report("light map", t);
        }
    }
}
