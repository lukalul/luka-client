package dev.cbui.mixin;

import dev.cbui.social.ServerFriends;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.gui.ServerListEntryNormal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks the servers in the multiplayer list that luka friends are on right now. */
@Mixin(ServerListEntryNormal.class)
public abstract class ServerListEntryNormalMixin {
  @Shadow @Final private ServerData server;

  @Inject(method = "drawEntry", at = @At("TAIL"), require = 0)
  private void cbui$friendsHere(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY, boolean isSelected, CallbackInfo ci) {
    ServerFriends.draw(this.server, x, y);
  }
}
