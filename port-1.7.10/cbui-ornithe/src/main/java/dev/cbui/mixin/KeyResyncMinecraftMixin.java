package dev.cbui.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Modern key handling: keys held while a screen closes keep working.
 *
 * <p>1.7.10 only notices a key when it goes down. Hold W, open the inventory, close it again: W is still held but the
 * game thinks it is up until it is pressed again. When the game takes the focus back, every key binding's held state is
 * read from the keyboard and mouse as they are now. Only the held state is set, so nothing that counts presses (drop,
 * attack, hotbar) fires again.
 */
@Mixin(Minecraft.class)
public abstract class KeyResyncMinecraftMixin {
  @Inject(method = "setIngameFocus", at = @At("TAIL"), require = 0)
  private void cbui$resyncHeldKeys(CallbackInfo ci) {
    try {
      Minecraft mc = (Minecraft) (Object) this;
      if (mc.currentScreen != null || !mc.inGameHasFocus) return;
      GameSettings settings = mc.gameSettings;
      if (settings == null || settings.keyBindings == null) return;
      for (KeyBinding binding : settings.keyBindings) {
        if (binding == null) continue;
        int code = binding.getKeyCode();
        if (code == 0) continue;
        boolean down = code < 0 ? Mouse.isButtonDown(code + 100) : Keyboard.isKeyDown(code);
        KeyBinding.setKeyBindState(code, down);
      }
    } catch (Throwable ignored) {
      // no keyboard yet or an unusual binding: the game's own handling stays as it was
    }
  }
}
