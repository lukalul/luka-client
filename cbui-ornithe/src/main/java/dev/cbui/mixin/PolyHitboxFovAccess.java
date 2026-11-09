package dev.cbui.mixin;

import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** PolyHitbox: the rendered field of view (private in 1.8.9) sets how wide a screen pixel is in the world, for the line width. */
@Mixin(EntityRenderer.class)
public interface PolyHitboxFovAccess {
  @Invoker("getFOVModifier")
  float cbui$fov(float partialTicks, boolean useFovSetting);
}
