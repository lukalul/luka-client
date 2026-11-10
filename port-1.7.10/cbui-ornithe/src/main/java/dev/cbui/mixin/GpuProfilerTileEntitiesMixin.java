package dev.cbui.mixin;
import dev.cbui.Profiler;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(TileEntityRendererDispatcher.class)
public abstract class GpuProfilerTileEntitiesMixin {
 @Inject(method="renderTileEntity",at=@At("HEAD"),require=0) private void cbui$begin(CallbackInfo ci){Profiler.gpuBeginOnce(Profiler.GpuPhase.TILE_ENTITIES);}
}
