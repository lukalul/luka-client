package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.cbui.feature.shaketweaks.ShakeTweaksModule;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemRenderer.class)
public abstract class ShakeTweaksItemRendererMixin {
  @WrapWithCondition(
      method = "renderItemInFirstPerson",
      at = @At(value = "INVOKE",
          target = "Lnet/minecraft/client/renderer/ItemRenderer;rotateWithPlayerRotations(Lnet/minecraft/client/entity/EntityPlayerSP;F)V"),
      require = 0)
  private boolean cbui$handFollowsLook(ItemRenderer renderer, EntityPlayerSP player, float partialTicks) {
    ShakeTweaksModule module = ShakeTweaksModule.active();
    return module == null || module.allowHandViewSway();
  }
}
