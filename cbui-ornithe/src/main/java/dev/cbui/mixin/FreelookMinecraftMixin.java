package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.cbui.feature.freelook.FreelookModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the freelook key registered even when the card is off, and applies the chosen policy to the vanilla F5 key. */
@Mixin(Minecraft.class)
public abstract class FreelookMinecraftMixin {
  @Inject(method = "runTick", at = @At("HEAD"), require = 0)
  private void cbui$keepKeyAlive(CallbackInfo ci) {
    try {
      FreelookModule module = FreelookModule.instance();
      if (module != null) module.backgroundTick();
    } catch (Throwable failure) {
      // freelook just stays idle this tick
    }
  }

  /** The first write to thirdPersonView inside runTick is the F5 cycle; returning false keeps the previous view. */
  @WrapWithCondition(
      method = "runTick",
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;thirdPersonView:I", opcode = 181, ordinal = 0),
      require = 0)
  private boolean cbui$gateViewCycle(GameSettings settings, int newView) {
    try {
      FreelookModule module = FreelookModule.instance();
      return module == null || module.allowPerspectiveWrite(newView);
    } catch (Throwable failure) {
      return true;
    }
  }
}
