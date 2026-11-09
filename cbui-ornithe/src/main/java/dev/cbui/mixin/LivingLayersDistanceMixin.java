package dev.cbui.mixin;

import dev.cbui.config.GlobalOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "Entity detail distance" setting: players and mobs farther than this keep their body but lose the extra layers (armour, held items,
 * capes, hats). At that range they are a few pixels tall, and a crowd of them costs a lot of frame time.
 */
@Mixin(RendererLivingEntity.class)
public abstract class LivingLayersDistanceMixin {
  @Inject(method = "renderLayers", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$farLayers(EntityLivingBase entity, float a, float b, float c, float d, float e, float f, float g, CallbackInfo ci) {
    int blocks = GlobalOptions.layerDistance;
    if (blocks <= 0 || blocks >= 128) return;
    Minecraft mc = Minecraft.getMinecraft();
    if (mc.thePlayer == null || entity == mc.thePlayer || entity == mc.getRenderViewEntity()) return;
    if (entity.getDistanceSqToEntity(mc.thePlayer) > (double) blocks * blocks) ci.cancel();
  }
}
