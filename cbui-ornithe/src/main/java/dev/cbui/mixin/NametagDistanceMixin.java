package dev.cbui.mixin;

import dev.cbui.config.GlobalOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** "Nametag distance" setting: names above players and mobs are drawn only within this many blocks (vanilla is 64). */
@Mixin(RendererLivingEntity.class)
public abstract class NametagDistanceMixin {
  @Inject(method = "canRenderName", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$nameRange(EntityLivingBase entity, CallbackInfoReturnable<Boolean> cir) {
    int blocks = GlobalOptions.nametagDistance;
    if (blocks <= 0 || blocks >= 64) return;
    Minecraft mc = Minecraft.getMinecraft();
    if (mc.thePlayer == null || entity == mc.thePlayer) return;
    if (entity.getDistanceSqToEntity(mc.thePlayer) > (double) blocks * blocks) cir.setReturnValue(Boolean.FALSE);
  }
}
