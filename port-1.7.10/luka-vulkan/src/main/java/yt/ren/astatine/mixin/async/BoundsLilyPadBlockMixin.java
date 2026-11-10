package yt.ren.astatine.mixin.async;

import net.minecraft.block.LilyPadBlock;
import org.spongepowered.asm.mixin.Mixin;

/** luka: empty on purpose; it lets AsyncBoundsPlugin rewrite this class's direct bounds field accesses (see BlockBounds). */
@Mixin(LilyPadBlock.class)
public abstract class BoundsLilyPadBlockMixin {
}
