package dev.cbui.mixin;

import dev.cbui.config.GlobalOptions;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** "Block entity distance" setting: block entities (chests, portals, spawners...) stop being drawn beyond this many blocks. */
@Mixin(TileEntity.class)
public abstract class TileEntityDistanceMixin {
  @Inject(method = "getMaxRenderDistanceSquared()D", at = @At("RETURN"), cancellable = true, require = 0)
  private void cbui$limit(CallbackInfoReturnable<Double> cir) {
    int blocks = GlobalOptions.tileDistance;
    if (blocks > 0 && blocks < 64) cir.setReturnValue(Math.min(cir.getReturnValue(), (double) blocks * blocks));
  }
}
