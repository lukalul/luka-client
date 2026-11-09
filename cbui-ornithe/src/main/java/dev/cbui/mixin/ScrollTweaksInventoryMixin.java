package dev.cbui.mixin;

import dev.cbui.feature.scrolltweaks.ScrollTweaksModule;
import net.minecraft.entity.player.InventoryPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Intercepts hotbar wheel movement; the module decides the new slot, and vanilla runs untouched when no option is set. */
@Mixin(InventoryPlayer.class)
public abstract class ScrollTweaksInventoryMixin {
  @Shadow public int currentItem;

  @Inject(method = "changeCurrentItem", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$wheelOverride(int notch, CallbackInfo ci) {
    ScrollTweaksModule module = ScrollTweaksModule.active();
    if (module == null) return;
    int slot;
    try {
      slot = module.slotAfterWheel(currentItem, notch);
    } catch (Throwable failure) {
      return;
    }
    if (slot == ScrollTweaksModule.LEAVE_TO_VANILLA) return;
    currentItem = slot;
    ci.cancel();
  }
}
