package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cbui.config.GlobalOptions;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "Lazy chunk loading" for Celeritas (the terrain renderer inside Argentum), which replaces vanilla's chunk updater.
 *
 * <p>Two knobs, both scaled by the level:
 * <ul>
 *   <li>Building: fewer new chunks are handed to the builder threads at once, so they take less CPU from the game and
 *       new terrain trickles in over more frames (Lunar's trade-off: a little slower, steadier FPS).</li>
 *   <li>Showing: at most a few finished chunks are taken off the results queue per frame; the rest stay queued, in
 *       order, for the next frames. Celeritas otherwise uploads everything that finished in one frame, which is the
 *       spike when you join a server or teleport.</li>
 * </ul>
 * Block changes next to the player are built as "important" work with no budget, so the first knob never slows them.
 */
@Pseudo
@Mixin(targets = "org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager", remap = false)
public abstract class LazyChunksCeleritasMixin {
  @Unique private double cbui$tokens;
  @Unique private long cbui$last = System.nanoTime();

  /** The budget for normal chunk builds (the important-work collector uses a fixed unlimited budget, not this call). */
  @ModifyExpressionValue(
      method = "updateChunks(Z)V",
      at = @At(value = "INVOKE", target = "Lorg/embeddedt/embeddium/impl/render/chunk/compile/executor/ChunkBuilder;getSchedulingBudget()I", remap = false),
      remap = false,
      require = 0)
  private int cbui$lazyBudget(int budget) {
    int level = GlobalOptions.chunkPace;
    if (level <= 0 || budget <= 1) return budget;
    float keep = level == 1 ? 0.75f : level == 2 ? 0.5f : 0.25f;
    return Math.max(1, (int) (budget * keep));
  }

  @Inject(method = "collectChunkBuildResults()Ljava/util/ArrayList;", at = @At("HEAD"), remap = false, require = 0)
  private void cbui$lazyReset(CallbackInfoReturnable<?> cir) {
    int level = GlobalOptions.chunkPace;
    if (level <= 0) return;
    // a limit per second: a limit per frame does nothing at thousands of frames a second
    long now = System.nanoTime();
    double rate = level == 1 ? 3000.0 : level == 2 ? 1500.0 : 700.0;
    cbui$tokens = Math.min(cbui$tokens + (now - cbui$last) * 1e-9 * rate, rate * 0.05);
    cbui$last = now;
  }

  /** Reports the queue as empty once this frame has taken its share; what is left is picked up on the next frames. */
  @WrapOperation(
      method = "collectChunkBuildResults()Ljava/util/ArrayList;",
      at = @At(value = "INVOKE", target = "Ljava/util/concurrent/ConcurrentLinkedDeque;poll()Ljava/lang/Object;", remap = false),
      remap = false,
      require = 0)
  private Object cbui$lazyPoll(ConcurrentLinkedDeque<?> queue, Operation<Object> original) {
    boolean paced = GlobalOptions.chunkPace > 0;
    if (paced && cbui$tokens < 1.0) return null;
    Object result = original.call(queue);
    if (paced && result != null) cbui$tokens -= 1.0;
    if (result != null) dev.cbui.render.ChunkLoadStats.onShown(result);
    return result;
  }
}
