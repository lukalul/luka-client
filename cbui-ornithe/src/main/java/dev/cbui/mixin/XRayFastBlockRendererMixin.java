package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.cbui.feature.staff.XRayLight;
import dev.cbui.feature.staff.XRayState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumWorldBlockLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Staff X-Ray, meshing side (Argentum's terrain block renderer, runs on the chunk worker threads). While X-Ray is active, an ore-like block
 * gets all six faces meshed (also next to opaque neighbours), is routed into the translucent pass (drawn last with normal blending; the faint
 * terrain writes no depth, so it shows through) and is lit at full brightness. Everything is a no-op while X-Ray is off.
 */
@Pseudo
@Mixin(targets = "dev.rdh.argentum.impl.render.terrain.compile.pipeline.FastBlockRenderer", remap = false)
public abstract class XRayFastBlockRendererMixin {
  private boolean cbui$ore;

  @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, index = 1, require = 0)
  private IBlockState cbui$begin(IBlockState state) {
    boolean ore = false;
    try {
      ore = XRayState.active && XRayState.isOre(state.getBlock());
    } catch (Throwable ignored) {
      // keep vanilla behaviour
    }
    cbui$ore = ore;
    XRayState.setForced(ore);
    return state;
  }

  @Inject(method = "render", at = @At("RETURN"), require = 0)
  private void cbui$end(CallbackInfo ci) {
    if (cbui$ore) {
      cbui$ore = false;
      XRayState.setForced(false);
    }
  }

  @ModifyExpressionValue(method = "render",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/unmapped/C_81592558;m_28922194(Lnet/minecraft/unmapped/C_46108969;Lnet/minecraft/unmapped/C_32594356;Lnet/minecraft/unmapped/C_28786548;)Z"),
      require = 0)
  private boolean cbui$forceFace(boolean original) {
    return original || cbui$ore;
  }

  @Inject(method = "isHiddenByNeighbor", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$neverHidden(CallbackInfoReturnable<Boolean> cir) {
    if (cbui$ore) cir.setReturnValue(Boolean.FALSE);
  }

  @ModifyArg(method = "render",
      at = @At(value = "INVOKE", target = "Lorg/embeddedt/embeddium/impl/render/chunk/RenderPassConfiguration;getMaterialForRenderType(Ljava/lang/Object;)Lorg/embeddedt/embeddium/impl/render/chunk/terrain/material/Material;"),
      index = 0, require = 0)
  private Object cbui$translucent(Object layer) {
    return cbui$ore ? EnumWorldBlockLayer.TRANSLUCENT : layer;
  }

  @Inject(method = "writeQuad", at = @At("HEAD"), require = 0)
  private void cbui$bright(CallbackInfo ci) {
    if (cbui$ore) XRayLight.fullbright(this);
  }
}
