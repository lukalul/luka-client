package dev.customcolors.mixin;

import dev.customcolors.CustomColors;
import dev.customcolors.LightmapBridge;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Priority above the default so Fullbright, which also cancels updateLightmap, gets the first say. */
@Mixin(value = EntityRenderer.class, priority = 1100)
public abstract class EntityRendererMixin {
    @Shadow private Minecraft mc;
    @Shadow private boolean lightmapUpdateNeeded;
    @Shadow private int[] lightmapColors;
    @Shadow private DynamicTexture lightmapTexture;
    @Shadow private float torchFlickerX;
    @Shadow private float fogColorRed;
    @Shadow private float fogColorGreen;
    @Shadow private float fogColorBlue;

    @Shadow
    private float getNightVisionBrightness(EntityLivingBase entity, float partialTicks) {
        throw new AssertionError();
    }

    @Inject(method = "updateLightmap", at = @At("HEAD"), cancellable = true)
    private void customcolors$lightmap(float partialTicks, CallbackInfo ci) {
        if (!lightmapUpdateNeeded) return;
        float nightVision = mc.thePlayer != null && mc.thePlayer.isPotionActive(Potion.nightVision)
                ? getNightVisionBrightness(mc.thePlayer, partialTicks) : 0.0F;
        if (LightmapBridge.compute(mc.theWorld, lightmapColors, torchFlickerX, nightVision)) {
            lightmapTexture.updateDynamicTexture();
            lightmapUpdateNeeded = false;
            ci.cancel();
        }
    }

    /** Right after vanilla picked the water and lava fog colours. */
    @Inject(method = "updateFogColor", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/EntityRenderer;fogColor2:F", ordinal = 0))
    private void customcolors$fluidFog(float partialTicks, CallbackInfo ci) {
        Entity view = mc.getRenderViewEntity();
        if (view == null || mc.theWorld == null) return;
        Block block = ActiveRenderInfo.getBlockAtEntityViewpoint(mc.theWorld, view, partialTicks);
        int color;
        float extra = 0.0F;
        if (block.getMaterial() == Material.water && CustomColors.hasUnderwater()) {
            color = CustomColors.underwater(mc.theWorld);
            extra = EnchantmentHelper.getRespiration(view) * 0.2F;
        } else if (block.getMaterial() == Material.lava && CustomColors.hasUnderlava()) {
            color = CustomColors.underlava(mc.theWorld);
        } else {
            return;
        }
        fogColorRed = (color >> 16 & 255) / 255.0F + extra;
        fogColorGreen = (color >> 8 & 255) / 255.0F + extra;
        fogColorBlue = (color & 255) / 255.0F + extra;
    }
}
