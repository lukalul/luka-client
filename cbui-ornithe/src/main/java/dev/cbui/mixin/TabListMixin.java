package dev.cbui.mixin;

import dev.cbui.feature.tablist.TabListModule;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.network.NetworkPlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Tab List options. Each hook only hands a value to {@link TabListModule}, which decides. */
@Mixin(GuiPlayerTabOverlay.class)
public abstract class TabListMixin {
  /** Start of the list: the players are sorted once per frame. */
  @ModifyArg(
      method = "renderPlayerlist",
      at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Ordering;sortedCopy(Ljava/lang/Iterable;)Ljava/util/List;"),
      index = 0,
      require = 0)
  private Iterable<?> cbui$begin(Iterable<?> players) {
    TabListModule.begin();
    return players;
  }

  /** The first loop measures every name in row order: note which player each row will be. */
  @ModifyArg(
      method = "renderPlayerlist",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiPlayerTabOverlay;getPlayerName(Lnet/minecraft/client/network/NetworkPlayerInfo;)Ljava/lang/String;", ordinal = 0),
      index = 0,
      require = 0)
  private NetworkPlayerInfo cbui$record(NetworkPlayerInfo info) {
    TabListModule.record(info);
    return info;
  }

  /** 13 = the ping bars plus their gap in each column's width. */
  @ModifyConstant(method = "renderPlayerlist", constant = @Constant(intValue = 13), require = 0)
  private int cbui$pingColumn(int width) {
    return TabListModule.pingColumn(width);
  }

  @ModifyArg(
      method = "renderPlayerlist",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiPlayerTabOverlay;drawRect(IIIII)V"),
      index = 4,
      require = 0)
  private int cbui$box(int left, int top, int right, int bottom, int color) {
    return TabListModule.box(color);
  }

  @ModifyArg(
      method = "drawPing",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiPlayerTabOverlay;drawTexturedModalRect(IIIIII)V"),
      index = 4,
      require = 0)
  private int cbui$ping(int x, int y, int u, int v, int width, int height) {
    return TabListModule.ping(x, y, width);
  }
}
