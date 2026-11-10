package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.cbui.feature.staff.XRayState;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Staff X-Ray: smooth lighting is skipped for ore-like blocks so their faces are not darkened by the rock around them. */
@Mixin(RenderBlocks.class)
public abstract class XRayRenderBlocksMixin {
  @ModifyExpressionValue(method = "renderStandardBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getLightValue()I"), require = 0)
  private int cbui$noAoOnOres(int light, @Local(argsOnly = true) Block block) {
    return XRayState.forced(block) ? 15 : light;
  }
}
