package dev.bakeplus.mixin;

import dev.bakeplus.BakePlusConfig;
import net.minecraft.tileentity.TileEntityMobSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.minecraft.unmapped.C_49528123", remap = false)
public abstract class TileEntityMobSpawnerRendererMixin {
    @Inject(method = "m_41904860", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void bakeplus$staticMob(TileEntityMobSpawner spawner, double x, double y, double z,
                                    float partialTicks, int destroyStage, CallbackInfo ci) {
        if (!BakePlusConfig.staticMobSpawners) return;
        if (x * x + y * y + z * z > 16.0D * 16.0D) {
            ci.cancel();
            return;
        }
        // Closer than 16 blocks vanilla draws the mob itself: our own copy of its transform ended up at the camera (Argentum's entity batching).
    }
}
