package dev.cbui.mixin;

import dev.cbui.config.GlobalOptions;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clears distance fog and the fog inside water and lava. Blindness is a game mechanic, so its fog is left alone. */
@Mixin(EntityRenderer.class)
public abstract class AnimationsFogMixin {
  @Shadow private float farPlaneDistance;

  @Inject(method = "setupFog", at = @At("RETURN"), require = 0)
  private void cbui$clearFog(int startCoords, float partialTicks, CallbackInfo callback) {
    boolean terrain = GlobalOptions.animationEnabled("terrain_fog"), liquid = GlobalOptions.animationEnabled("fluid_fog");
    boolean adjusted = GlobalOptions.fogStart != 75 || GlobalOptions.fogEnd != 100;
    dev.cbui.config.AnimationOptions.distanceFogCleared = false;
    dev.cbui.config.AnimationOptions.fogAdjusted = false;
    if (terrain && liquid && !adjusted) return;
    Minecraft mc = Minecraft.getMinecraft();
    Entity viewer = mc.getRenderViewEntity();
    if (viewer == null || mc.theWorld == null) return;
    if (viewer instanceof EntityLivingBase && ((EntityLivingBase) viewer).isPotionActive(Potion.blindness)) return;
    Block block = dev.cbui.feature.viewoverlays.ViewOverlaysModule.fogBlock(ActiveRenderInfo.getBlockAtEntityViewpoint(mc.theWorld, viewer, partialTicks));
    Material material = block.getMaterial();
    if (material == Material.water || material == Material.lava) {
      if (!liquid) GlStateManager.setFogDensity(0.0F);
    } else if (!terrain && startCoords != -1) {
      dev.cbui.config.AnimationOptions.distanceFogCleared = true;
      GlStateManager.setFogStart(100000.0F);
      GlStateManager.setFogEnd(100001.0F);
    } else if (adjusted && startCoords != -1 && !mc.theWorld.provider.doesXZShowFog((int) viewer.posX, (int) viewer.posZ)) {
      float start = farPlaneDistance * GlobalOptions.fogStart / 100F;
      float end = Math.max(start + 1F, farPlaneDistance * GlobalOptions.fogEnd / 100F);
      GlStateManager.setFogStart(start);
      GlStateManager.setFogEnd(end);
      dev.cbui.config.AnimationOptions.fogStartNow = start;
      dev.cbui.config.AnimationOptions.fogEndNow = end;
      dev.cbui.config.AnimationOptions.fogAdjusted = true;
    }
  }
}
