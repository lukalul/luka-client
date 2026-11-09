package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.cbui.feature.staff.XRayState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumWorldBlockLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.objectweb.asm.Opcodes;

/** Staff X-Ray: lava (a fluid, meshed by renderFluid) is put into the translucent pass like the ore blocks. */
@Pseudo
@Mixin(targets = "dev.rdh.argentum.impl.render.terrain.compile.ArgentumChunkBuildContext", remap = false)
public abstract class XRayChunkBuildContextMixin {
  private boolean cbui$lava;

  @ModifyVariable(method = "renderFluid", at = @At("HEAD"), argsOnly = true, index = 1, require = 0)
  private IBlockState cbui$begin(IBlockState state) {
    boolean lava = false;
    try {
      lava = XRayState.active && XRayState.category(state.getBlock()) == XRayState.LAVA && XRayState.isOre(state.getBlock());
    } catch (Throwable ignored) {
      // keep vanilla behaviour
    }
    cbui$lava = lava;
    return state;
  }

  /** Argentum downgrades a quad's material by the sprite's transparency (translucent to cutout/solid); forced ore quads must stay translucent. */
  @ModifyExpressionValue(method = "selectMaterial",
      at = @At(value = "FIELD", target = "Ldev/rdh/argentum/impl/render/terrain/compile/ArgentumChunkBuildContext;renderPassOptimization:Z", opcode = Opcodes.GETFIELD),
      require = 0)
  private boolean cbui$keepMaterial(boolean optimize) {
    return optimize && !XRayState.isForced();
  }

  @ModifyVariable(method = "renderFluid", at = @At("HEAD"), argsOnly = true, index = 4, require = 0)
  private EnumWorldBlockLayer cbui$layer(EnumWorldBlockLayer layer) {
    return cbui$lava ? EnumWorldBlockLayer.TRANSLUCENT : layer;
  }
}
