package dev.cbui.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.cbui.config.GlobalOptions;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDeadBush;
import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.BlockFlower;
import net.minecraft.block.BlockMushroom;
import net.minecraft.block.BlockTallGrass;
import net.minecraft.block.BlockVine;
import net.minecraft.block.state.IBlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hide foliage, meshing side (Argentum's terrain block renderer, runs on the chunk worker threads): tall grass, ferns, flowers, dead
 * bushes, vines and mushrooms are not added to the chunk mesh while the option is on. Toggling reloads the chunks.
 */
@Pseudo
@Mixin(targets = "dev.rdh.argentum.impl.render.terrain.compile.pipeline.FastBlockRenderer", remap = false)
public abstract class FoliageFastBlockRendererMixin {
  @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$hideFoliage(CallbackInfo ci, @Local(ordinal = 0, argsOnly = true) IBlockState state) {
    try {
      if (!GlobalOptions.hideFoliage || state == null) return;
      Block block = state.getBlock();
      if (block instanceof BlockTallGrass || block instanceof BlockDoublePlant || block instanceof BlockFlower
          || block instanceof BlockDeadBush || block instanceof BlockVine || block instanceof BlockMushroom) ci.cancel();
    } catch (Throwable ignored) {
      // draw it normally
    }
  }
}
