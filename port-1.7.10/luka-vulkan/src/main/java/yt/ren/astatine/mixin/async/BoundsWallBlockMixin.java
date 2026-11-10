package yt.ren.astatine.mixin.async;

import net.minecraft.block.WallBlock;
import org.spongepowered.asm.mixin.Mixin;

/** luka: empty on purpose; it lets AsyncBoundsPlugin rewrite this class's direct bounds field accesses (see BlockBounds). */
@Mixin(WallBlock.class)
public abstract class BoundsWallBlockMixin {
}
