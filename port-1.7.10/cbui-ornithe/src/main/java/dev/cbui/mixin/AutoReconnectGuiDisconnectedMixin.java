package dev.cbui.mixin;

import dev.cbui.feature.Features;
import dev.cbui.feature.autoreconnect.AutoReconnectModule;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The reconnect countdown on the disconnect screen; Escape stops it. */
@Mixin(GuiDisconnected.class)
public abstract class AutoReconnectGuiDisconnectedMixin extends GuiScreen {
  @Inject(method = "drawScreen", at = @At("TAIL"), require = 0)
  private void cbui$countdown(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
    try {
      AutoReconnectModule m = Features.get(AutoReconnectModule.class);
      if (m == null || !m.enabled()) return;
      int s = m.secondsLeft();
      String text = s >= 0 ? "§aReconnecting in " + s + "s (" + (m.tries() + 1) + "/" + m.maxTries() + ")  §7Esc cancels"
          : m.tries() >= m.maxTries() ? "§cGave up reconnecting" : null;
      if (text != null) this.drawCenteredString(this.fontRendererObj, text, this.width / 2, 12, 0xFFFFFF);
    } catch (Throwable ignored) { }
  }

  @Inject(method = "keyTyped", at = @At("HEAD"), require = 0)
  private void cbui$cancel(char c, int key, CallbackInfo ci) {
    try {
      AutoReconnectModule m = Features.get(AutoReconnectModule.class);
      if (m != null && key == 1) m.cancel();
    } catch (Throwable ignored) { }
  }
}
