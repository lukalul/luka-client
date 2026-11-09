package dev.cbui.mixin;

import dev.cbui.Cbui;
import dev.cbui.feature.Features;
import dev.cbui.feature.soundtweaks.SoundTweaksModule;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PositionedSound.class)
public abstract class SoundTweaksPositionedSoundMixin {
  @Shadow protected ResourceLocation positionedSoundLocation;
  private static boolean cbui$volumeHookBroken;

  @Inject(method = "getVolume", at = @At("RETURN"), cancellable = true, require = 0)
  private void cbui$rebalanceVolume(CallbackInfoReturnable<Float> result) {
    if (cbui$volumeHookBroken) return;
    try {
      SoundTweaksModule module = Features.get(SoundTweaksModule.class);
      if (module != null && module.enabled())
        result.setReturnValue(result.getReturnValue() * module.volumeMultiplier(positionedSoundLocation));
    } catch (Throwable failure) {
      cbui$volumeHookBroken = true;
      Cbui.reportOnce("SoundTweaks volume hook", failure);
    }
  }
}
