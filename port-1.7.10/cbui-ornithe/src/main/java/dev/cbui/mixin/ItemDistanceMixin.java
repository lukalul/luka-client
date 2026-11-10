package dev.cbui.mixin;

import dev.cbui.config.GlobalOptions;
import dev.cbui.render.EntityCull;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** "Dropped item distance" setting: dropped items and experience orbs farther than this are not drawn at all (vanilla draws them out to 64 blocks). */
@Mixin(Entity.class)
public abstract class ItemDistanceMixin {
  @Inject(method = "isInRangeToRenderDist", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$itemRange(double distanceSq, CallbackInfoReturnable<Boolean> cir) {
    Object self = this;
    if (!(self instanceof EntityItem || self instanceof EntityXPOrb)) return;
    int blocks = GlobalOptions.itemDistance;
    boolean tooFar = blocks < 64 && blocks > 0 && distanceSq > (double) blocks * blocks;
    if (tooFar || EntityCull.skip((Entity) self)) cir.setReturnValue(Boolean.FALSE);
  }
}
