package dev.cbui.mixin;

import dev.cbui.feature.oldanimations.OldAnimationsModule;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Old Animations + Argentum: Argentum's baked item icons must not bake the modern glint when the 1.7 glint is on. */
@Pseudo
@Mixin(targets = "dev/rdh/argentum/impl/render/gui/hud/item/GuiItemIcons", remap = false)
public abstract class OldAnimationsGuiItemIconsMixin {
  @Dynamic
  @Inject(method = "canBake", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
  private static void cbui$skipLegacyGlint(ItemStack item, CallbackInfoReturnable<Boolean> cir) {
    try {
      OldAnimationsModule m = OldAnimationsModule.active();
      if (item != null && item.hasEffect() && m != null && m.glintRendering.get()) cir.setReturnValue(false);
    } catch (Throwable ignored) { }
  }
}
