package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.cbui.Cbui;
import dev.cbui.feature.skyhorizon.SkyHorizonModule;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** the sky horizon card: the client world reports a horizon of 0 while "Darken only below the world" is on. */
@Mixin(World.class)
public abstract class SkyHorizonWorldMixin {
  @ModifyReturnValue(method = "getHorizon", at = @At("RETURN"), require = 0)
  private double cbui$skyHorizon(double horizon) {
    if (!((Object) this instanceof WorldClient)) return horizon;
    try {
      SkyHorizonModule module = SkyHorizonModule.active();
      return module == null ? horizon : module.horizon(horizon);
    } catch (Throwable error) {
      Cbui.reportOnce("smooth skies horizon", error);
      return horizon;
    }
  }
}
