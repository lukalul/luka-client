package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.cbui.feature.staff.XRayState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Staff X-Ray: lava keeps its faces next to rock (only against more lava it stays culled), and is drawn in the translucent pass. */
@Mixin(BlockLiquid.class)
public abstract class XRayBlockLiquidMixin extends Block {
  protected XRayBlockLiquidMixin(Material material) {
    super(material);
  }

  @ModifyReturnValue(method = "shouldSideBeRendered", at = @At("RETURN"), require = 0)
  private boolean cbui$lavaFaces(boolean original, IBlockAccess world, int x, int y, int z, int side) {
    try {
      if (!original && XRayState.active && XRayState.isOre(this)) return world.getBlock(x, y, z).getMaterial() != getMaterial();
    } catch (Throwable ignored) {
      // keep vanilla behaviour
    }
    return original;
  }

  @ModifyReturnValue(method = "getRenderBlockPass", at = @At("RETURN"), require = 0)
  private int cbui$lavaPass(int pass) {
    return XRayState.active && XRayState.isOre(this) ? 1 : pass;
  }
}
