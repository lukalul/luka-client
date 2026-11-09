package dev.cbui.mixin;

import dev.cbui.config.GlobalOptions;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "Lazy chunk loading" with vanilla chunk rendering: rebuilds and uploads new chunks on only every Nth frame. With Argentum
 * the terrain is Celeritas's, and {@link LazyChunksCeleritasMixin} does the work there instead, so this one stands aside.
 */
@Mixin(RenderGlobal.class)
public abstract class ChunkPaceMixin {
  private static int cbui$frame;
  private static final boolean CELERITAS = cbui$celeritas() || net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("astatine"); // the Vulkan renderer paces itself

  private static boolean cbui$celeritas() {
    try {
      Class.forName("org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager", false, ChunkPaceMixin.class.getClassLoader());
      return true;
    } catch (Throwable t) {
      return false;
    }
  }

  @Inject(method = "updateChunks(J)V", at = @At("HEAD"), cancellable = true, require = 0)
  private void cbui$pace(long finishTimeNano, CallbackInfo ci) {
    if (CELERITAS) return;
    int every = GlobalOptions.chunkPaceEvery();
    if (every > 1 && ++cbui$frame % every != 0) ci.cancel();
  }
}
