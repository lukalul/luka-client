package dev.cbui.mixin;

import dev.cbui.feature.damagetint.DamageTintModule;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderDragon;
import net.minecraft.entity.boss.EntityDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The dragon paints its own hurt flash, so it needs a separate hook from other living entities. */
@Mixin(RenderDragon.class)
public abstract class DamageTintRenderDragonMixin {
  @Inject(
      method = "renderModel(Lnet/minecraft/entity/boss/EntityDragon;FFFFFF)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;color(FFFF)V", shift = At.Shift.AFTER),
      require = 0)
  private void cbui$recolourDragonFlash(EntityDragon dragon, float limbAngle, float limbDistance,
      float partialTicks, float yaw, float pitch, float scale, CallbackInfo ci) {
    DamageTintModule module = DamageTintModule.active();
    if (module == null) return;
    try {
      int argb = module.colorFor(DamageTintModule.variantOf(dragon));
      GlStateManager.color(DamageTintModule.channel(argb, 16), DamageTintModule.channel(argb, 8),
          DamageTintModule.channel(argb, 0), module.dragonAlphaFor(argb, dragon.hurtTime));
    } catch (Throwable failure) {
      // keep vanilla's flash
    }
  }
}
