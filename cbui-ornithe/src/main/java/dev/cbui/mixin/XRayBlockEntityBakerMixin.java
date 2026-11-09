package dev.cbui.mixin;

import dev.cbui.feature.staff.XRayState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumWorldBlockLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Staff X-Ray: Argentum bakes chests into the terrain mesh instead of drawing a tile entity. An ore-like chest (the "Chests" category) is baked into the
 * translucent pass at full brightness, like the ore blocks. No-op while X-Ray is off.
 */
@Pseudo
@Mixin(targets = "dev.rdh.argentum.impl.render.blockentity.BlockEntityBaker", remap = false)
public abstract class XRayBlockEntityBakerMixin {
  @Shadow private int light;

  @ModifyVariable(method = "bake", at = @At("HEAD"), argsOnly = true, index = 3, require = 0)
  private IBlockState cbui$begin(IBlockState state) {
    boolean forced = false;
    try {
      forced = XRayState.active && XRayState.isOre(state.getBlock());
    } catch (Throwable ignored) {
      // keep vanilla behaviour
    }
    XRayState.setForced(forced);
    return state;
  }

  @Inject(method = "bake", at = @At("RETURN"), require = 0)
  private void cbui$end(CallbackInfoReturnable<Boolean> cir) {
    XRayState.setForced(false);
  }

  @ModifyArg(method = "bake",
      at = @At(value = "INVOKE", target = "Lorg/embeddedt/embeddium/impl/render/chunk/RenderPassConfiguration;getMaterialForRenderType(Ljava/lang/Object;)Lorg/embeddedt/embeddium/impl/render/chunk/terrain/material/Material;"),
      index = 0, require = 0)
  private Object cbui$translucent(Object layer) {
    return XRayState.isForced() ? EnumWorldBlockLayer.TRANSLUCENT : layer;
  }

  @Inject(method = "push", at = @At("HEAD"), require = 0)
  private void cbui$bright(CallbackInfo ci) {
    if (XRayState.isForced()) light = (15 << 4) | (15 << 20);
  }
}
