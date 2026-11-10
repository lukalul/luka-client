package dev.cbui.mixin;

import dev.cbui.Cbui;
import dev.cbui.feature.Features;
import dev.cbui.feature.polytime.PolyTimeModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class PolyTimeWorldMoonMixin {
  private static boolean cbui$moonHookUnavailable;

  @Inject(method = "getMoonPhase", at = @At("RETURN"), cancellable = true, require = 0)
  private void cbui$replaceClientMoon(CallbackInfoReturnable<Integer> result) {
    if (cbui$moonHookUnavailable || Minecraft.getMinecraft().theWorld != (Object) this) return;
    try {
      PolyTimeModule module = Features.get(PolyTimeModule.class);
      if (module == null || !module.enabled()) return;
      int replacement = module.selectedMoonPhase();
      if (replacement >= 0) result.setReturnValue(Integer.valueOf(replacement));
    } catch (Throwable error) {
      cbui$moonHookUnavailable = true;
      Cbui.reportOnce("PolyTime moon hook", error);
    }
  }
}
