package dev.cbui.mixin;
import dev.cbui.Profiler;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GuiScreen.class)
public abstract class GpuProfilerScreenMixin {
 @Inject(method="drawScreen",at=@At("HEAD"),require=0) private void cbui$begin(CallbackInfo ci){Profiler.gpuBegin(Profiler.GpuPhase.SCREEN);}
 @Inject(method="drawScreen",at=@At("RETURN"),require=0) private void cbui$end(CallbackInfo ci){Profiler.gpuEnd(Profiler.GpuPhase.SCREEN);}
}
