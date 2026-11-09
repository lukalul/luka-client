package dev.cbui.mixin;

import dev.cbui.feature.Features;
import dev.cbui.feature.confirmdisconnect.ConfirmDisconnectModule;
import dev.cbui.feature.confirmdisconnect.ConfirmDisconnectScreen;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sits in the pause menu's button handler and puts the confirmation in front of the disconnect action. */
@Mixin(GuiIngameMenu.class)
public abstract class ConfirmDisconnectGuiIngameMenuMixin extends GuiScreen {
  @Inject(method = "actionPerformed",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isIntegratedServerRunning()Z", ordinal = 0),
      cancellable = true, require = 0)
  private void cbui$askFirst(GuiButton pressed, CallbackInfo ci) {
    ConfirmDisconnectModule module = ConfirmDisconnectModule.active();
    if (module == null) return;
    try {
      GuiScreen shown = this.mc.currentScreen;
      boolean swallow = shown instanceof ConfirmDisconnectScreen;
      // plain comparisons on purpose: an enum switch would make javac emit a helper class that Mixin cannot load
      ConfirmDisconnectModule.Response response = module.onDisconnectPressed(this.mc.isSingleplayer(), !this.buttonList.isEmpty());
      if (response == ConfirmDisconnectModule.Response.ARM_BUTTON) {
        GuiButton first = (GuiButton) this.buttonList.get(0);
        first.enabled = false;
        first.displayString = "§cAre you sure?";
        swallow = true;
      } else if (response == ConfirmDisconnectModule.Response.OPEN_CONFIRM_SCREEN) {
        this.mc.displayGuiScreen(new ConfirmDisconnectScreen(shown));
        swallow = true;
      }
      if (swallow) ci.cancel();
    } catch (Throwable failure) {
      // fall through to vanilla's disconnect
    }
  }

  @Inject(method = "initGui", at = @At("HEAD"), require = 0)
  private void cbui$forgetArming(CallbackInfo ci) {
    try {
      ConfirmDisconnectModule module = Features.get(ConfirmDisconnectModule.class);
      if (module != null) module.armed = false;
    } catch (Throwable failure) {
      // nothing to reset
    }
  }

  @Inject(method = "updateScreen", at = @At("HEAD"), require = 0)
  private void cbui$countDelay(CallbackInfo ci) {
    ConfirmDisconnectModule module = ConfirmDisconnectModule.active();
    if (module == null) return;
    try {
      if (module.advanceDelay() && this.mc.currentScreen instanceof GuiIngameMenu && !this.buttonList.isEmpty()) {
        ((GuiButton) this.buttonList.get(0)).enabled = true;
      }
    } catch (Throwable failure) {
      // leave the button as it is
    }
  }
}
