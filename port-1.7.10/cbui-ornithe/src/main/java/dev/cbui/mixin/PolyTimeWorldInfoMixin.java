package dev.cbui.mixin;

import dev.cbui.Cbui;
import dev.cbui.feature.Features;
import dev.cbui.feature.polytime.PolyTimeModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.storage.WorldInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldInfo.class)
public abstract class PolyTimeWorldInfoMixin {
  private static boolean cbui$timeHookUnavailable;

  @Inject(method = "getWorldTime", at = @At("RETURN"), cancellable = true, require = 0)
  private void cbui$replaceClientClock(CallbackInfoReturnable<Long> result) {
    if (cbui$timeHookUnavailable) return;
    try {
      Minecraft game = Minecraft.getMinecraft();
      if (game.theWorld == null || game.theWorld.getWorldInfo() != (Object) this) return;
      PolyTimeModule module = Features.get(PolyTimeModule.class);
      if (module != null && module.enabled()) result.setReturnValue(module.clientTime(result.getReturnValue().longValue()));
    } catch (Throwable error) {
      cbui$timeHookUnavailable = true;
      Cbui.reportOnce("PolyTime time hook", error);
    }
  }
}
