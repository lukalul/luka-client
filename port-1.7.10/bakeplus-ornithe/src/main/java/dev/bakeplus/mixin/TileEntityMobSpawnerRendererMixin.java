package dev.bakeplus.mixin;

import dev.bakeplus.BakePlusConfig;
import net.minecraft.client.renderer.tileentity.TileEntityMobSpawnerRenderer;
import net.minecraft.tileentity.TileEntityMobSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TileEntityMobSpawnerRenderer.class)
public abstract class TileEntityMobSpawnerRendererMixin {
    @Inject(method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntityMobSpawner;DDDF)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void bakeplus$staticMob(TileEntityMobSpawner spawner, double x, double y, double z,
                                    float partialTicks, CallbackInfo ci) {
        if (!BakePlusConfig.staticMobSpawners) return;
        if (x * x + y * y + z * z > 16.0D * 16.0D) {
            ci.cancel();
            return;
        }
        // Closer than 16 blocks vanilla draws the mob itself: our own copy of its transform ended up at the camera (Argentum's entity batching).
    }
}
