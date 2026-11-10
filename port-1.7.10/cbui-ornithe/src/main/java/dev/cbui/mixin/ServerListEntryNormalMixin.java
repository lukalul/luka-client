package dev.cbui.mixin;

import dev.cbui.social.ServerFriends;
import net.minecraft.client.gui.ServerListEntryNormal;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks the servers in the multiplayer list that luka friends are on right now. */
@Mixin(ServerListEntryNormal.class)
public abstract class ServerListEntryNormalMixin {
  @Inject(method = "drawEntry", at = @At("TAIL"), require = 0)
  private void cbui$friendsHere(int slotIndex, int x, int y, int listWidth, int slotHeight, Tessellator tessellator, int mouseX, int mouseY, boolean isSelected, CallbackInfo ci) {
    ServerFriends.draw(((ServerListEntryNormal) (Object) this).getServerData(), x, y);
  }
}
