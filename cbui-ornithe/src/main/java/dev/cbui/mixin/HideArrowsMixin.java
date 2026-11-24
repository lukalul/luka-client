package dev.cbui.mixin;

import dev.cbui.config.GlobalOptions;
import net.minecraft.client.renderer.entity.RenderArrow;
import net.minecraft.entity.projectile.EntityArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "Hide arrows in the ground" and "Hide arrows in flight" (Performance, World). Arrows stuck in players and mobs are a separate
 * switch (HideStuckArrowsMixin). The arrows still exist and still hit; they are only not drawn.
 *
 * <p>Priority 500 puts this check before other mods' hooks on the same method: on 1.8.9 Argentum's Celeritas draws arrows
 * itself in a batch and cancels the game's drawing first, so a later hide check never ran (rig, 2026-11-13).
 */
@Mixin(value = RenderArrow.class, priority = 500)
public abstract class HideArrowsMixin {
  @Inject(method = "doRender(Lnet/minecraft/entity/projectile/EntityArrow;DDDFF)V", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$hideArrows(EntityArrow arrow, double x, double y, double z, float yaw, float partialTicks, CallbackInfo ci) {
    if (!GlobalOptions.hideGroundArrows && !GlobalOptions.hideFlyingArrows) return;
    boolean inGround = ((EntityArrowAccessor) arrow).cbui$inGround();
    if (inGround ? GlobalOptions.hideGroundArrows : GlobalOptions.hideFlyingArrows) ci.cancel();
  }
}
