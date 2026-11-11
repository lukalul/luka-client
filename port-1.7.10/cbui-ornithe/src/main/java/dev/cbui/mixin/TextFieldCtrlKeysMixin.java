package dev.cbui.mixin;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ctrl+A, Ctrl+C, Ctrl+V and Ctrl+X in text fields on every renderer. 1.7.10's text field recognises them only by the
 * control character the old keyboard code typed with them (1, 3, 22, 24). The Vulkan renderer's keyboard (pylon, SDL)
 * types no character while Ctrl is held, so select all, copy, paste and cut did nothing there. When a key comes with no
 * character and Ctrl is down, the matching control character is passed on, as the old keyboard code would have.
 */
@Mixin(GuiTextField.class)
public abstract class TextFieldCtrlKeysMixin {
  @Inject(method = "textboxKeyTyped", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$ctrlKeys(char typed, int key, CallbackInfoReturnable<Boolean> cir) {
    if (typed != 0 || !GuiScreen.isCtrlKeyDown()) return;
    char control;
    switch (key) {
      case Keyboard.KEY_A: control = 1; break;
      case Keyboard.KEY_C: control = 3; break;
      case Keyboard.KEY_V: control = 22; break;
      case Keyboard.KEY_X: control = 24; break;
      default: return;
    }
    cir.setReturnValue(((GuiTextField) (Object) this).textboxKeyTyped(control, key));
  }
}
