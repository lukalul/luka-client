package dev.cbui.mixin;

import net.minecraft.client.gui.GuiIngame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the Boss bar HUD module run the game's own boss bar drawing inside its box. */
@Mixin(GuiIngame.class)
public interface GuiIngameBossInvoker {
  @Invoker("renderBossHealth")
  void cbui$renderBossHealth();
}
