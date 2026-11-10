package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.cbui.feature.staff.XRayState;
import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Staff X-Ray, meshing side. While X-Ray is active an ore-like block goes into the translucent pass, draws all of its faces
 * (even buried ones) and is lit at full brightness.
 */
@Mixin(Block.class)
public abstract class XRayBlockMixin {
  private static final int FULL_BRIGHT = (15 << 20) | (15 << 4);

  @ModifyReturnValue(method = "getRenderBlockPass", at = @At("RETURN"), require = 0)
  private int cbui$orePass(int pass) {
    return XRayState.forced((Block) (Object) this) ? 1 : pass;
  }

  @Inject(method = "shouldSideBeRendered", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$oreFaces(IBlockAccess world, int x, int y, int z, int side, CallbackInfoReturnable<Boolean> cir) {
    if (XRayState.forced((Block) (Object) this)) cir.setReturnValue(Boolean.TRUE);
  }

  @Inject(method = "getMixedBrightnessForBlock", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$oreLight(IBlockAccess world, int x, int y, int z, CallbackInfoReturnable<Integer> cir) {
    if (XRayState.forced((Block) (Object) this)) cir.setReturnValue(FULL_BRIGHT);
  }
}
