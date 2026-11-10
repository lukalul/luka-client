package dev.cbui.mixin;

import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets a primitive be thrown away instead of drawn (1.8's WorldRenderer.finishDrawing). */
@Mixin(Tessellator.class)
public interface TessellatorAccessor {
  @Invoker("reset") void cbui$reset();
  @Accessor("isDrawing") void cbui$setDrawing(boolean drawing);
}
