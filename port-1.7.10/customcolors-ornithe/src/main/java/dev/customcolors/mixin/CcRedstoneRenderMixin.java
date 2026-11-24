package dev.customcolors.mixin;

import dev.customcolors.CustomColorsMod;
import dev.customcolors.color.BlockTints;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Redstone wire tint per power level from the pack's redstone colormap. On 1.7.10 the tint is set while
 * the wire is drawn; only that first colour call changes, the white overlay pass after it stays white.
 */
@Mixin(RenderBlocks.class)
public abstract class CcRedstoneRenderMixin {
    @Shadow public IBlockAccess blockAccess;
    /** Tint chosen for the wire being drawn; meaningful only while {@code customcolors$wireTinted}. */
    @Unique private int customcolors$wireColour;
    @Unique private boolean customcolors$wireTinted;

    @Inject(method = "renderBlockRedstoneWire", at = @At("HEAD"), require = 0)
    private void customcolors$pick(Block block, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        try {
            int c = blockAccess == null ? -1 : BlockTints.redstone(blockAccess.getBlockMetadata(x, y, z));
            customcolors$wireTinted = c >= 0;
            customcolors$wireColour = c;
        } catch (Throwable t) {
            customcolors$wireTinted = false;
            CustomColorsMod.reportOnce("redstone colour", t);
        }
    }

    @ModifyArg(method = "renderBlockRedstoneWire",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V", ordinal = 0),
        index = 0, require = 0)
    private float customcolors$red(float red) {
        return customcolors$wireTinted ? ((customcolors$wireColour >> 16) & 255) / 255.0F : red;
    }

    @ModifyArg(method = "renderBlockRedstoneWire",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V", ordinal = 0),
        index = 1, require = 0)
    private float customcolors$green(float green) {
        return customcolors$wireTinted ? ((customcolors$wireColour >> 8) & 255) / 255.0F : green;
    }

    @ModifyArg(method = "renderBlockRedstoneWire",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V", ordinal = 0),
        index = 2, require = 0)
    private float customcolors$blue(float blue) {
        return customcolors$wireTinted ? (customcolors$wireColour & 255) / 255.0F : blue;
    }
}
