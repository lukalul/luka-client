package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.cbui.Cbui;
import dev.cbui.feature.skyclock.SkyClockModule;
import net.minecraft.world.storage.WorldInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Time Changer: the client world's time of day as the player chose it. */
@Mixin(WorldInfo.class)
public abstract class SkyClockWorldInfoMixin {
  @ModifyReturnValue(method = "getWorldTime", at = @At("RETURN"), require = 0)
  private long cbui$shownTime(long stored) {
    try {
      SkyClockModule module = SkyClockModule.active();
      return module == null ? stored : module.worldTime(this, stored);
    } catch (Throwable error) {
      Cbui.reportOnce("Time Changer world time", error);
      return stored;
    }
  }
}
