package dev.cbui.mixin;

import dev.cbui.feature.viewoverlays.ViewOverlaysModule;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Overlay Tweaks' Clear water / Clear lava: fog and fog colour see the camera's block as air while the camera is in a liquid
 * the player cleared, so the view inside it is as far and as clear as in the open. Only the fog's idea of where the camera
 * is changes; the liquid blocks and their surface are drawn as usual.
 */
@Mixin(EntityRenderer.class)
public abstract class ClearLiquidFogMixin {
  @Redirect(method = {"setupFog", "updateFogColor"}, at = @At(value = "INVOKE",
      target = "Lnet/minecraft/client/renderer/ActiveRenderInfo;getBlockAtEntityViewpoint(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;F)Lnet/minecraft/block/Block;"),
      require = 0)
  private Block cbui$fogBlock(World world, Entity viewer, float partialTicks) {
    return ViewOverlaysModule.fogBlock(ActiveRenderInfo.getBlockAtEntityViewpoint(world, viewer, partialTicks));
  }
}
