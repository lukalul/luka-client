package dev.cbui.mixin;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Nametags: lets the card send a sneaking entity's label through vanilla's (restyled) label routine. */
@Mixin(Render.class)
public interface LabelsRenderInvoker {
  @Invoker("renderLivingLabel")
  void cbui$drawLivingLabel(Entity owner, String text, double x, double y, double z, int maxDistance);
}
