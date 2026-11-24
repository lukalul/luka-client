package dev.cbui.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import dev.cbui.Cbui;
import dev.cbui.input.HeldKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After a screen closes and the game takes focus back, keys still physically held count as held again (see
 * {@link HeldKeys}). Remembers at the start of the call whether a screen was open, so it acts only on a real close and
 * on the final outcome, after the other HEAD hooks on this method have picked the screen.
 */
@Mixin(Minecraft.class)
public abstract class HeldKeysMinecraftMixin {
  @Inject(method = "displayGuiScreen", at = @At("HEAD"), require = 0)
  private void cbui$heldKeysBefore(GuiScreen screen, CallbackInfo ci, @Share("screenWasOpen") LocalBooleanRef screenWasOpen) {
    screenWasOpen.set(((Minecraft) (Object) this).currentScreen != null);
  }

  @Inject(method = "displayGuiScreen",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setIngameFocus()V", shift = At.Shift.AFTER),
      require = 0)
  private void cbui$heldKeysAfter(GuiScreen screen, CallbackInfo ci, @Share("screenWasOpen") LocalBooleanRef screenWasOpen) {
    if (!screenWasOpen.get()) return;
    try {
      HeldKeys.matchKeyboard((Minecraft) (Object) this);
    } catch (Throwable error) {
      Cbui.reportOnce("held keys after screen", error);
    }
  }
}
