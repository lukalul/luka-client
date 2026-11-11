package dev.cbui.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Modern key handling ("persistent key bindings", done the way Sarcio does it on 1.8.9): keys held while a screen
 * closes keep working.
 *
 * <p>1.7.10 only notices a key when it goes down. Hold W, open the inventory, close it again: W is still held but the
 * game thinks it is up until it is pressed again. When a screen closes and the game takes the focus back, the held state
 * of every keyboard-bound key binding is read from the keyboard as it is now. Only keyboard keys (codes 1 to 255): mouse
 * buttons are left alone, so attack and use never count as held without a new click (re-holding them made the game act
 * on a click that never happened, which anti-cheats look for). Only the held state is set, so nothing that counts
 * presses (drop, hotbar) fires again.
 */
@Mixin(Minecraft.class)
public abstract class KeyResyncMinecraftMixin {
  @Inject(method = "displayGuiScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setIngameFocus()V"), require = 0)
  private void cbui$resyncHeldKeys(CallbackInfo ci) {
    try {
      GameSettings settings = ((Minecraft) (Object) this).gameSettings;
      if (settings == null || settings.keyBindings == null) return;
      for (KeyBinding binding : settings.keyBindings) {
        if (binding == null) continue;
        int code = binding.getKeyCode();
        if (code > 0 && code < 256) KeyBinding.setKeyBindState(code, Keyboard.isKeyDown(code));
      }
    } catch (Throwable ignored) {
      // no keyboard yet or an unusual binding: the game's own handling stays as it was
    }
  }
}
