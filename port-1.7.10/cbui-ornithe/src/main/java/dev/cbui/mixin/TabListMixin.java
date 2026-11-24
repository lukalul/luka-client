package dev.cbui.mixin;

import dev.cbui.feature.tablist.TabListModule;
import net.minecraft.client.gui.GuiIngame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * Tab List options. 1.7.10 draws the tab list at the end of the in-game overlay; each hook only hands a value to
 * {@link TabListModule}, which leaves every box that is not the list's own unchanged.
 */
@Mixin(GuiIngame.class)
public abstract class TabListMixin {
  @ModifyArg(
      method = "renderGameOverlay",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiIngame;drawRect(IIIII)V"),
      index = 4,
      require = 0)
  private int cbui$box(int left, int top, int right, int bottom, int color) {
    return TabListModule.box(color);
  }

  /** The only icon drawn after the list reads a player's ping is that player's ping bars. */
  @ModifyArg(
      method = "renderGameOverlay",
      slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/GuiPlayerInfo;responseTime:I")),
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiIngame;drawTexturedModalRect(IIIIII)V"),
      index = 4,
      require = 0)
  private int cbui$ping(int x, int y, int u, int v, int width, int height) {
    return TabListModule.ping(x, y, width);
  }
}
