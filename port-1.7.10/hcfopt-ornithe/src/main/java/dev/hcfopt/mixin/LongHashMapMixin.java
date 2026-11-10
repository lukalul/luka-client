package dev.hcfopt.mixin;

import net.minecraft.util.LongHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The game keeps its loaded chunks in this map and folds a chunk's key down to x ^ z before hashing it, so with a few
 * hundred chunks loaded most of them share a handful of buckets and every block or light lookup walks a long chain.
 * Mixing both halves of the key properly spreads the chunks out, one or two entries to a bucket.
 */
@Mixin(LongHashMap.class)
public abstract class LongHashMapMixin {
  @Redirect(
      method = "getHashedKey",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/LongHashMap;hash(I)I"),
      require = 0)
  private static int hcfopt$spread(int folded, long key) {
    key ^= key >>> 33;
    key *= 0xff51afd7ed558ccdL;
    key ^= key >>> 33;
    key *= 0xc4ceb9fe1a85ec53L;
    key ^= key >>> 33;
    return (int) key;
  }
}
