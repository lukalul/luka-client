package dev.cbui.mixin;

import dev.cbui.feature.oldanimations.OldAnimationsHooks;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Old Animations: the own player model follows the eased camera height. */
@Mixin(RenderPlayer.class)
public abstract class OldAnimationsRenderPlayerMixin {
  @ModifyArg(method = "doRender(Lnet/minecraft/client/entity/AbstractClientPlayer;DDDFF)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V"),
      index = 2, require = 0)
  private double cbui$oldSneakModelY(EntityLivingBase entity, double dx, double dy, double dz, float yaw, float partialTicks) {
    try { return OldAnimationsHooks.playerModelY(entity, dy, partialTicks); } catch (Throwable ignored) { return dy; }
  }
}
