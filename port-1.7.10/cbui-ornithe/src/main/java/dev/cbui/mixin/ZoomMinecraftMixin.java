package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.cbui.Cbui;
import dev.cbui.feature.zoom.ZoomModule;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Zoom: a wheel notch spent on zoom steps never reaches the hotbar or the spectator menu. */
@Mixin(Minecraft.class)
public abstract class ZoomMinecraftMixin {
  @ModifyExpressionValue(method = "runTick",
      at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Mouse;getEventDWheel()I", remap = false), require = 0)
  private int cbui$zoomWheel(int delta) {
    if (delta == 0) return 0;
    ZoomModule zoom = ZoomModule.instance();
    if (zoom == null) return delta;
    try {
      return zoom.takeWheel(delta) ? 0 : delta;
    } catch (Throwable error) {
      Cbui.reportOnce("zoom wheel", error);
      return delta;
    }
  }
}
