package dev.cbui.mixin;

import dev.cbui.feature.blockoverlay.BlockOverlayRenderer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Block Overlay: draws in place of vanilla's selection box. */
@Mixin(RenderGlobal.class)
public abstract class BlockOverlayRenderGlobalMixin {
  @Inject(method = "drawSelectionBox", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$overlay(EntityPlayer player, MovingObjectPosition hit, int mode, float partialTicks, CallbackInfo ci) {
    if (BlockOverlayRenderer.draw(player, hit, mode, partialTicks)) ci.cancel();
  }
}
