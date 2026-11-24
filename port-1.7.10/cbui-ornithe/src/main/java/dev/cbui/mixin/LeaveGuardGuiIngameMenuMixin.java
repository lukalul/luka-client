package dev.cbui.mixin;

import dev.cbui.Cbui;
import dev.cbui.feature.leaveguard.LeaveGuardModule;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiIngameMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Confirm Disconnect: catches the leave button (id 1) before vanilla sends the quit packet. luka's own pause menu
 * extends GuiIngameMenu and hands id 1 to super, so it is covered too.
 */
@Mixin(GuiIngameMenu.class)
public abstract class LeaveGuardGuiIngameMenuMixin extends GuiScreen {
  @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$leavePressed(GuiButton button, CallbackInfo ci) {
    if (button == null || button.id != LeaveGuardModule.LEAVE_ID) return;
    try {
      LeaveGuardModule module = LeaveGuardModule.active();
      if (module != null && module.holdBack(this, button, this.buttonList)) ci.cancel();
    } catch (Throwable error) {
      Cbui.reportOnce("Confirm Disconnect", error);
    }
  }

  @Inject(method = "initGui", at = @At("RETURN"), require = 0)
  private void cbui$menuBuilt(CallbackInfo ci) {
    try {
      LeaveGuardModule module = LeaveGuardModule.any();
      if (module != null) module.menuRebuilt(this);
    } catch (Throwable error) {
      Cbui.reportOnce("Confirm Disconnect", error);
    }
  }

  @Inject(method = "updateScreen", at = @At("RETURN"), require = 0)
  private void cbui$menuTick(CallbackInfo ci) {
    try {
      LeaveGuardModule module = LeaveGuardModule.any();
      if (module != null) module.menuTick(this);
    } catch (Throwable error) {
      Cbui.reportOnce("Confirm Disconnect", error);
    }
  }
}
