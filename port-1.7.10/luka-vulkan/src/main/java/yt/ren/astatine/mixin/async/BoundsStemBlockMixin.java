package yt.ren.astatine.mixin.async;

import net.minecraft.block.StemBlock;
import org.spongepowered.asm.mixin.Mixin;

/** luka: empty on purpose; it lets AsyncBoundsPlugin rewrite this class's direct bounds field accesses (see BlockBounds). */
@Mixin(StemBlock.class)
public abstract class BoundsStemBlockMixin {
}
