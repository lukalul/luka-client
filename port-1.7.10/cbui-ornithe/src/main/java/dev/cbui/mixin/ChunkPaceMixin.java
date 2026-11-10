package dev.cbui.mixin;

import dev.cbui.config.GlobalOptions;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** "Lazy chunk loading": chunk rebuilds run only on every Nth frame. Answering true tells the render loop there is nothing left to update. */
@Mixin(RenderGlobal.class)
public abstract class ChunkPaceMixin {
  private static int cbui$frame;

  @Inject(method = "updateRenderers", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$pace(EntityLivingBase viewer, boolean fast, CallbackInfoReturnable<Boolean> cir) {
    int every = GlobalOptions.chunkPaceEvery();
    if (every > 1 && ++cbui$frame % every != 0) cir.setReturnValue(Boolean.TRUE);
  }
}
