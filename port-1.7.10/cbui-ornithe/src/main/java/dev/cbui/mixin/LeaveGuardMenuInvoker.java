package dev.cbui.mixin;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Confirm Disconnect: lets the module run the pause menu's own button action after the player confirmed. */
@Mixin(GuiIngameMenu.class)
public interface LeaveGuardMenuInvoker {
  @Invoker("actionPerformed")
  void cbui$press(GuiButton button);
}
