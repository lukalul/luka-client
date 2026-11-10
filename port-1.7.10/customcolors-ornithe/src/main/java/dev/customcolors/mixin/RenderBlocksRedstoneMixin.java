package dev.customcolors.mixin;

import dev.customcolors.CustomColors;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** On 1.7.10 the wire's colour is worked out inside RenderBlocks; BlockRedstoneWire.colorMultiplier is a constant that drawing ignores. */
@Mixin(RenderBlocks.class)
public abstract class RenderBlocksRedstoneMixin {
    @Shadow public IBlockAccess blockAccess;

    @Redirect(method = "renderBlockRedstoneWire(Lnet/minecraft/block/Block;III)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;setColorOpaque_F(FFF)V"))
    private void customcolors$wire(Tessellator tessellator, float red, float green, float blue, Block block, int x, int y, int z) {
        // the same call also resets to white for the overlay texture; the powered tint is never pure white
        int color = red == 1.0F && green == 1.0F && blue == 1.0F ? -1 : CustomColors.redstone(blockAccess.getBlockMetadata(x, y, z));
        if (color == -1) tessellator.setColorOpaque_F(red, green, blue);
        else tessellator.setColorOpaque_F((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F, (color & 255) / 255.0F);
    }
}
