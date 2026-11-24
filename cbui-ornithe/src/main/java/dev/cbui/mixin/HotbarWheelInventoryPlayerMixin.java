package dev.cbui.mixin;

import dev.cbui.Cbui;
import dev.cbui.feature.hotbarwheel.HotbarWheelModule;
import net.minecraft.entity.player.InventoryPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** the hotbar wheel card: when an option is on, the module picks the slot for a wheel event and the vanilla step is skipped. */
@Mixin(InventoryPlayer.class)
public abstract class HotbarWheelInventoryPlayerMixin {
  @Shadow public int currentItem;

  @Inject(method = "changeCurrentItem", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$hotbarWheel(int delta, CallbackInfo ci) {
    try {
      HotbarWheelModule module = HotbarWheelModule.active();
      if (module == null || !module.replacesVanilla()) return;
      currentItem = module.slotAfter(currentItem, delta);
      ci.cancel();
    } catch (Throwable error) {
      Cbui.reportOnce("scroll tweaks wheel", error);
    }
  }
}
