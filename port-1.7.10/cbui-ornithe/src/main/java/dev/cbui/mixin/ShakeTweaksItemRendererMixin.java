package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.cbui.feature.shaketweaks.ShakeTweaksModule;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** renderItemInFirstPerson's third and fourth glRotatef calls are the hand following your view; they can be skipped. */
@Mixin(ItemRenderer.class)
public abstract class ShakeTweaksItemRendererMixin {
  @WrapWithCondition(
      method = "renderItemInFirstPerson",
      at = {@At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V", ordinal = 2, remap = false),
            @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V", ordinal = 3, remap = false)},
      require = 0)
  private boolean cbui$handFollowsLook(float angle, float x, float y, float z) {
    ShakeTweaksModule module = ShakeTweaksModule.active();
    return module == null || module.allowHandViewSway();
  }
}
