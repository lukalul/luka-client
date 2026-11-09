package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.cbui.feature.staff.XRayState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Staff X-Ray: lava keeps its faces next to rock (only against more lava it stays culled), so buried lava is meshed. */
@Mixin(BlockLiquid.class)
public abstract class XRayBlockLiquidMixin extends Block {
  protected XRayBlockLiquidMixin(Material material) {
    super(material);
  }

  @ModifyReturnValue(method = "shouldSideBeRendered", at = @At("RETURN"), require = 0)
  private boolean cbui$lavaFaces(boolean original, IBlockAccess world, BlockPos pos, EnumFacing side) {
    try {
      if (!original && XRayState.active && XRayState.isOre(this)) return world.getBlockState(pos).getBlock().getMaterial() != getMaterial();
    } catch (Throwable ignored) {
      // keep vanilla behaviour
    }
    return original;
  }
}
