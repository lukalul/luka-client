package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.cbui.Cbui;
import dev.cbui.feature.skyclock.SkyClockModule;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Time Changer: the moon phase drawn for the client world. */
@Mixin(World.class)
public abstract class SkyClockWorldMixin {
  @ModifyReturnValue(method = "getMoonPhase", at = @At("RETURN"), require = 0)
  private int cbui$shownMoon(int phase) {
    try {
      SkyClockModule module = SkyClockModule.active();
      return module == null ? phase : module.moonPhase(this, phase);
    } catch (Throwable error) {
      Cbui.reportOnce("Time Changer moon phase", error);
      return phase;
    }
  }
}
