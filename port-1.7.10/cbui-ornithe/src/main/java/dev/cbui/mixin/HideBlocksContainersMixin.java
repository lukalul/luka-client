package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.cbui.feature.hideblocks.HideBlocksModule;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBeacon;
import net.minecraft.block.BlockBrewingStand;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockEnderChest;
import net.minecraft.block.BlockFlowerPot;
import net.minecraft.block.BlockHopper;
import net.minecraft.block.BlockLiquid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The blocks that answer getRenderType themselves instead of inheriting Block's answer. */
@Mixin({BlockChest.class, BlockEnderChest.class, BlockLiquid.class, BlockHopper.class, BlockBeacon.class, BlockBrewingStand.class, BlockFlowerPot.class})
public abstract class HideBlocksContainersMixin {
  @ModifyReturnValue(method = "getRenderType", at = @At("RETURN"), require = 0)
  private int cbui$hide(int type) {
    return HideBlocksModule.hides((Block) (Object) this) ? -1 : type;
  }
}
