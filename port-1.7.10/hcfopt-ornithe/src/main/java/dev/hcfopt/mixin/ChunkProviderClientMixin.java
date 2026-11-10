package dev.hcfopt.mixin;

import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.util.LongHashMap;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two things in the client's chunk list.
 *
 * <p>Every tick the game lets each loaded chunk catch up on sky light, and stops after 5 ms. It reads the wall clock
 * for that, which on Windows may only move every 15.6 ms, so the 5 ms limit often ends up as 15 to 30 ms on the game
 * thread. A clock that moves every tick of the timer makes the limit real, and 2 ms keeps the frame short; the
 * chunks that did not get their turn do it on a later tick, as they already do when the limit is hit.
 *
 * <p>Most block and light reads ask for the same chunk as the read before. Remembering the last chunk handed out
 * skips the map for those.
 */
@Mixin(ChunkProviderClient.class)
public abstract class ChunkProviderClientMixin {
  @Unique private Chunk hcfopt$last;

  @Redirect(method = "unloadQueuedChunks",
      at = @At(value = "INVOKE", target = "Ljava/lang/System;currentTimeMillis()J"), require = 0)
  private long hcfopt$fineClock() {
    return System.nanoTime() / 1000000L;
  }

  @ModifyConstant(method = "unloadQueuedChunks", constant = @Constant(longValue = 5L), require = 0)
  private long hcfopt$lightBudget(long ms) {
    return 2L;
  }

  @Redirect(method = "provideChunk",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/LongHashMap;getValueByKey(J)Ljava/lang/Object;"), require = 0)
  private Object hcfopt$lastChunk(LongHashMap map, long key) {
    Chunk last = hcfopt$last;
    if (last != null && ChunkCoordIntPair.chunkXZ2Int(last.xPosition, last.zPosition) == key) return last;
    Object found = map.getValueByKey(key);
    if (found != null) hcfopt$last = (Chunk) found;
    return found;
  }

  @Inject(method = "unloadChunk", at = @At("RETURN"), require = 0)
  private void hcfopt$forgetUnloaded(int x, int z, CallbackInfo ci) {
    hcfopt$last = null;
  }

  @Inject(method = "loadChunk", at = @At("RETURN"), require = 0)
  private void hcfopt$forgetReplaced(int x, int z, CallbackInfoReturnable<Chunk> cir) {
    hcfopt$last = null;
  }
}
