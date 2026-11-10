package yt.ren.astatine.mixin.async;

import net.minecraft.block.PistonBaseBlock;
import org.spongepowered.asm.mixin.Mixin;

/** luka: empty on purpose; it lets AsyncBoundsPlugin rewrite this class's direct bounds field accesses (see BlockBounds). */
@Mixin(PistonBaseBlock.class)
public abstract class BoundsPistonBaseBlockMixin {
}
