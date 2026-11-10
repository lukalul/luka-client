package dev.hcfopt.mixin;

import dev.hcfopt.HcfOptHooks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(World.class)
abstract class WorldMixin {
    @Redirect(method = "updateEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/tileentity/TileEntity;updateEntity()V"), require = 0)
    private void hcfopt$filterTileTick(TileEntity tile) {
        if (!HcfOptHooks.skipTileTick(tile)) tile.updateEntity();
    }
}
