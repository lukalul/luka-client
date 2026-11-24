package dev.customcolors.mixin;

import dev.customcolors.CustomColorsMod;
import dev.customcolors.light.LightmapPainter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Builds the light table from the pack light map of the current dimension. Priority 1100 so luka's
 * Fullbright, which replaces the same table at the same spot, runs first and wins while it is on.
 */
@Mixin(value = EntityRenderer.class, priority = 1100)
public abstract class CcLightmapMixin {
    @Shadow private Minecraft mc;
    @Shadow @Final private int[] lightmapColors;
    @Shadow @Final private DynamicTexture lightmapTexture;
    @Shadow private boolean lightmapUpdateNeeded;
    @Shadow float torchFlickerX;

    @Shadow
    private float getNightVisionBrightness(EntityPlayer player, float partialTicks) {
        throw new AssertionError();
    }

    @Inject(method = "updateLightmap", at = @At("HEAD"), cancellable = true, require = 0)
    private void customcolors$paint(float partialTicks, CallbackInfo ci) {
        if (!lightmapUpdateNeeded) {
            return;
        }
        try {
            World world = mc.theWorld;
            EntityPlayer player = mc.thePlayer;
            if (world == null || player == null) {
                return;
            }
            float nightVision = 0.0F;
            if (player.isPotionActive(Potion.nightVision)) {
                nightVision = getNightVisionBrightness(player, partialTicks);
            }
            if (LightmapPainter.paint(lightmapColors, world, torchFlickerX, nightVision, mc.gameSettings.gammaSetting)) {
                lightmapTexture.updateDynamicTexture();
                lightmapUpdateNeeded = false;
                ci.cancel();
            }
        } catch (Throwable t) {
            CustomColorsMod.reportOnce("light map", t);
        }
    }
}
