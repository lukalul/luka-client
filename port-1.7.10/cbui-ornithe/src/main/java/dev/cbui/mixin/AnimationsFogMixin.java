package dev.cbui.mixin;

import dev.cbui.config.GlobalOptions;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.EntityRenderer;
import dev.luka.compat.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clears or moves distance fog and clears the fog inside water and lava. Blindness is a game mechanic, so its fog is left alone. */
@Mixin(EntityRenderer.class)
public abstract class AnimationsFogMixin {
  @Shadow private float farPlaneDistance;

  @Inject(method = "setupFog", at = @At("RETURN"), require = 0)
  private void cbui$clearFog(int startCoords, float partialTicks, CallbackInfo callback) {
    boolean terrain = GlobalOptions.animationEnabled("terrain_fog"), liquid = GlobalOptions.animationEnabled("fluid_fog");
    dev.cbui.config.AnimationOptions.distanceFogCleared = false;
    boolean adjusted = GlobalOptions.fogStart != 75 || GlobalOptions.fogEnd != 100;
    if (terrain && liquid && !adjusted) return;
    Minecraft mc = Minecraft.getMinecraft();
    EntityLivingBase viewer = mc.renderViewEntity;
    if (viewer == null || mc.theWorld == null) return;
    if (viewer.isPotionActive(Potion.blindness)) return;
    Block block = dev.cbui.feature.viewoverlays.ViewOverlaysModule.fogBlock(ActiveRenderInfo.getBlockAtEntityViewpoint(mc.theWorld, viewer, partialTicks));
    Material material = block.getMaterial();
    if (material == Material.water || material == Material.lava) {
      if (!liquid) GlStateManager.setFogDensity(0.0F);
    } else if (!terrain && startCoords != -1) {
      dev.cbui.config.AnimationOptions.distanceFogCleared = true;
      GlStateManager.setFogStart(100000.0F);
      GlStateManager.setFogEnd(100001.0F);
    } else if (adjusted && startCoords != -1 && !mc.theWorld.provider.doesXZShowFog((int) viewer.posX, (int) viewer.posZ)) {
      float far = cbui$fogDistance(mc, viewer, partialTicks);
      float start = far * GlobalOptions.fogStart / 100F;
      GlStateManager.setFogStart(start);
      GlStateManager.setFogEnd(Math.max(start + 1F, far * GlobalOptions.fogEnd / 100F));
    }
  }

  /** The distance the game fogs to: the far plane, pulled in by void fog when the player is deep down in survival. */
  private float cbui$fogDistance(Minecraft mc, EntityLivingBase viewer, float partialTicks) {
    float far = farPlaneDistance;
    boolean creative = viewer instanceof EntityPlayer && ((EntityPlayer) viewer).capabilities.isCreativeMode;
    if (creative || !mc.theWorld.provider.getWorldHasVoidParticles()) return far;
    double depth = ((viewer.getBrightnessForRender(partialTicks) & 0xF00000) >> 20) / 16.0
        + (viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * partialTicks + 4.0) / 32.0;
    if (depth >= 1.0) return far;
    if (depth < 0.0) depth = 0.0;
    return Math.min(far, Math.max(5.0F, 100.0F * (float) (depth * depth)));
  }
}
