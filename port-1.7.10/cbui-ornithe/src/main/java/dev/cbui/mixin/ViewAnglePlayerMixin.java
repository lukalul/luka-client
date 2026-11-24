package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.cbui.Cbui;
import dev.cbui.feature.viewangle.ViewAngleModule;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** FOV Changer: the player's FOV multiplier (speed, sprint, flight, bow) comes from the module. */
@Mixin(EntityPlayerSP.class)
public abstract class ViewAnglePlayerMixin {
  @ModifyReturnValue(method = "getFOVMultiplier", at = @At("RETURN"), require = 0)
  private float cbui$viewAngleMultiplier(float vanilla) {
    ViewAngleModule module = ViewAngleModule.active();
    if (module == null) return vanilla;
    try {
      return module.playerMultiplier((EntityPlayer) (Object) this, vanilla);
    } catch (Throwable error) {
      Cbui.reportOnce("FOV changer multiplier", error);
      return vanilla;
    }
  }
}
