package dev.cbui.mixin;

import dev.cbui.feature.hideblocks.HideBlocksModule;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TileEntityRendererDispatcher.class)
public abstract class HideBlocksTileMixin {
  @Inject(method = "renderTileEntity", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$hide(TileEntity tile, float partialTicks, CallbackInfo callback) {
    if (HideBlocksModule.anySolid() && HideBlocksModule.hides(tile.getBlockType())) callback.cancel();
  }
}
