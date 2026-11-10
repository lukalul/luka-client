package dev.cbui.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.cbui.feature.Features;
import dev.cbui.feature.polyzoom.PolyZoomModule;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Registers the zoom keys (even while the card is off), spends the wheel on zoom steps, and ticks the zoom animation. */
@Mixin(Minecraft.class)
public abstract class PolyZoomMinecraftMixin {
  @Inject(method = "runTick", at = @At("HEAD"), require = 0)
  private void cbui$pzKeys(CallbackInfo ci) {
    try {
      PolyZoomModule m = Features.get(PolyZoomModule.class);
      if (m != null) m.ensureKeys();
    } catch (Throwable ignored) { }
  }

  @ModifyExpressionValue(
      method = "runTick",
      at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Mouse;getEventDWheel()I", remap = false),
      require = 0)
  private int cbui$pzScroll(int wheel) {
    try {
      PolyZoomModule m = Features.get(PolyZoomModule.class);
      return m == null ? wheel : m.consumeScroll(wheel);
    } catch (Throwable ignored) { return wheel; }
  }

  @Inject(method = "runTick", at = @At("RETURN"), require = 0)
  private void cbui$pzTick(CallbackInfo ci) {
    try {
      PolyZoomModule m = Features.get(PolyZoomModule.class);
      if (m != null) m.clientTick();
    } catch (Throwable ignored) { }
  }
}
