package dev.cbui.mixin;

import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Hitboxes: the field of view of this frame (zoom and FOV changes included), to size lines in screen pixels. */
@Mixin(EntityRenderer.class)
public interface HitboxesFovInvoker {
  @Invoker("getFOVModifier")
  float cbui$hitboxFov(float partialTicks, boolean useFovSetting);
}
