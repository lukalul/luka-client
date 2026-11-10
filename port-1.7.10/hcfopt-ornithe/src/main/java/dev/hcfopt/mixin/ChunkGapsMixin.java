package dev.hcfopt.mixin;

import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.IChunkProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sky light gaps of a chunk are only filled in once its four neighbours have arrived.
 *
 * <p>A chunk compares each column's height with the columns next to it, and where a neighbour is lower it relights
 * the gap down to the neighbour's lowest point. The game only does this when the chunks around exist, but on the
 * client every chunk "exists": one the server has not sent yet is the empty placeholder, whose lowest point is 0.
 * So a new chunk at the edge of the loaded land relights whole columns from the surface down to the bottom of the
 * world, one flood fill per block, which shows up as a 10 ms stall. That work is thrown away when the real
 * neighbour arrives and changes the heights again. Waiting for the neighbours skips it; the chunk keeps its flags
 * and does the work on a later tick, with the real heights. -Dluka.lightwait=false gives the game's own behaviour.
 */
@Mixin(Chunk.class)
public abstract class ChunkGapsMixin {
  @Unique private static final boolean HCFOPT$WAIT = !"false".equalsIgnoreCase(System.getProperty("luka.lightwait"));

  @Shadow public World worldObj;
  @Shadow @Final public int xPosition;
  @Shadow @Final public int zPosition;

  @Inject(method = "recheckGaps", at = @At("HEAD"), cancellable = true, require = 0)
  private void hcfopt$waitForNeighbours(boolean client, CallbackInfo ci) {
    if (!HCFOPT$WAIT) return;
    World world = this.worldObj;
    if (world == null || !world.isRemote) return;
    IChunkProvider provider = world.getChunkProvider();
    if (!(provider instanceof ChunkProviderClient)) return;
    int x = this.xPosition, z = this.zPosition;
    if (hcfopt$missing(provider, x + 1, z) || hcfopt$missing(provider, x - 1, z)
        || hcfopt$missing(provider, x, z + 1) || hcfopt$missing(provider, x, z - 1)) {
      ci.cancel();
    }
  }

  @Unique
  private static boolean hcfopt$missing(IChunkProvider provider, int x, int z) {
    return provider.provideChunk(x, z) instanceof EmptyChunk;
  }
}
